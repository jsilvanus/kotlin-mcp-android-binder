package fi.italeino.mcp.binder

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BinderTransportTest {
    private fun connected(
        policy: CallerPolicy = CallerPolicy { true },
        limits: TransportLimits = TransportLimits(),
        release: CompletableDeferred<Unit>? = null,
    ): Triple<Client, ProviderHost, InMemoryLink> = runBlocking {
        val (clientEnd, providerEnd) = InMemoryLink.pair()
        val host = ProviderHost(policy, limits) { sampleServer(release) }
        host.accept(trustedCaller, providerEnd)
        val client = Client(Implementation("sample-client", "0.0.1"))
        withTimeout(10_000) { client.connect(BinderTransport(clientEnd, limits)) }
        Triple(client, host, clientEnd)
    }

    @Test
    fun `initialises lists tools and calls a tool over the link`() = runBlocking {
        val (client, host, _) = connected()
        withTimeout(10_000) {
            val tools = client.listTools().tools.map { it.name }
            assertTrue("get_status" in tools, "tools: $tools")
            val result = client.callTool("get_status", emptyMap())
            assertEquals("ok", (result.content.single() as TextContent).text)
        }
        client.close()
        host.shutdown()
    }

    @Test
    fun `concurrent requests are correlated`() = runBlocking {
        val (client, host, _) = connected()
        withTimeout(10_000) {
            val results = (1..20).map { async { client.callTool("get_status", emptyMap()) } }.map { it.await() }
            assertEquals(20, results.size)
        }
        client.close()
        host.shutdown()
    }

    @Test
    fun `provider death fails the in-flight request instead of hanging`() = runBlocking {
        val never = CompletableDeferred<Unit>()
        val (client, host, clientEnd) = connected(release = never)
        withTimeout(10_000) {
            val pending = async { runCatching { client.callTool("slow", emptyMap()) } }
            // Wait until the request has actually crossed the link.
            while (clientEnd.sent.get() < 3) kotlinx.coroutines.delay(5)
            clientEnd.killPeer()
            val outcome = pending.await()
            assertTrue(outcome.isFailure, "request must fail when the peer dies")
        }
        never.complete(Unit)
        host.shutdown()
    }

    @Test
    fun `provider refuses an unauthorised caller before any session exists`() = runBlocking {
        val (_, providerEnd) = InMemoryLink.pair()
        val host = ProviderHost(CallerPolicy.DenyAll) { sampleServer() }
        assertFailsWith<ConnectionRefusedException> { host.accept(trustedCaller, providerEnd) }
        assertEquals(0, host.activeSessions)
    }

    @Test
    fun `default policy denies`() {
        assertTrue(!CallerPolicy.DenyAll.isAllowed(trustedCaller))
    }

    @Test
    fun `allowlist needs package and certificate together and fails closed`() {
        val policy = CallerPolicy.allowlist(mapOf("com.example.client" to setOf("ABC123")))
        assertTrue(policy.isAllowed(trustedCaller), "digest comparison is case-insensitive")
        assertTrue(!policy.isAllowed(trustedCaller.copy(signingCertSha256 = setOf("other"))))
        assertTrue(!policy.isAllowed(trustedCaller.copy(signingCertSha256 = emptySet())), "unverified cert")
        assertTrue(!policy.isAllowed(trustedCaller.copy(packages = setOf("com.evil"))), "right cert, wrong package")
        assertTrue(!policy.isAllowed(trustedCaller.copy(packages = emptySet())), "unverified package")
        assertTrue(!CallerPolicy.allowlist(mapOf("com.example.client" to emptySet())).isAllowed(trustedCaller))
    }

    @Test
    fun `session cap refuses extra callers`() = runBlocking {
        val host = ProviderHost(CallerPolicy { true }, maxSessions = 1) { sampleServer() }
        host.accept(trustedCaller, InMemoryLink.pair().second)
        assertFailsWith<ConnectionRefusedException> { host.accept(trustedCaller, InMemoryLink.pair().second) }
        host.shutdown()
    }

    @Test
    fun `oversize outbound message is refused locally`() = runBlocking {
        val (client, host, _) = connected(limits = TransportLimits(maxFrameBytes = 2_000))
        withTimeout(10_000) {
            val big = mapOf("blob" to "x".repeat(5_000))
            assertTrue(runCatching { client.callTool("get_status", big) }.isFailure)
            // The session is still usable: refusing one message must not tear it down.
            val ok = client.callTool("get_status", emptyMap())
            assertEquals("ok", (ok.content.single() as TextContent).text)
        }
        client.close()
        host.shutdown()
    }

    @Test
    fun `oversize and malformed inbound frames close the transport with an error`() = runBlocking {
        for (frame in listOf(ByteArray(3_000) { 'a'.code.toByte() }, "not json".encodeToByteArray())) {
            val (a, b) = InMemoryLink.pair()
            val transport = BinderTransport(b, TransportLimits(maxFrameBytes = 2_000))
            val errors = AtomicInteger()
            val closes = AtomicInteger()
            transport.onError { errors.incrementAndGet() }
            transport.onClose { closes.incrementAndGet() }
            transport.onMessage { }
            transport.start()
            a.send(frame)
            withTimeout(5_000) { while (closes.get() == 0) kotlinx.coroutines.delay(5) }
            assertEquals(1, errors.get())
            assertEquals(1, closes.get())
            assertFailsWith<LinkClosedException> { a.send(frame) }
        }
    }

    @Test
    fun `inbound queue overflow fails closed`() = runBlocking {
        val (a, b) = InMemoryLink.pair()
        val transport = BinderTransport(b, TransportLimits(maxQueuedInbound = 2))
        val gate = CompletableDeferred<Unit>()
        val closed = CompletableDeferred<Unit>()
        transport.onMessage { gate.await() }
        transport.onError { }
        transport.onClose { closed.complete(Unit) }
        transport.start()
        val ping = """{"jsonrpc":"2.0","method":"notifications/initialized"}""".encodeToByteArray()
        repeat(10) { runCatching { a.send(ping) } }
        withTimeout(5_000) { closed.await() }
        gate.complete(Unit)
    }

    @Test
    fun `close is idempotent and fires onClose once`() = runBlocking {
        val (_, b) = InMemoryLink.pair()
        val transport = BinderTransport(b)
        val closes = AtomicInteger()
        transport.onClose { closes.incrementAndGet() }
        transport.onMessage { }
        transport.start()
        transport.close()
        transport.close()
        assertEquals(1, closes.get())
        assertFailsWith<LinkClosedException> {
            transport.send(io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification("x"))
        }
    }

    @Test
    fun `provider releases sessions when the client closes`() = runBlocking {
        val (client, host, _) = connected()
        assertEquals(1, host.activeSessions)
        client.close()
        withTimeout(5_000) { while (host.activeSessions != 0) kotlinx.coroutines.delay(5) }
        host.shutdown()
    }
}
