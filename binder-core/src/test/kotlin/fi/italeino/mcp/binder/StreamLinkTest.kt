package fi.italeino.mcp.binder

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** StreamLink over real loopback sockets: the same blocking-socket semantics as a Binder-passed socket pair. */
class StreamLinkTest {
    private fun socketPair(): Pair<Socket, Socket> = ServerSocket(0).use { server ->
        val a = Socket("127.0.0.1", server.localPort)
        a to server.accept()
    }

    private fun link(s: Socket, max: Int = 1 shl 20) =
        StreamLink(s.getInputStream(), s.getOutputStream(), max, shutdown = {
            runCatching { s.shutdownInput() }; runCatching { s.shutdownOutput() }; runCatching { s.close() }
        })

    private class Collector : LinkListener {
        val frames = CopyOnWriteArrayList<ByteArray>()
        val closed = CountDownLatch(1)
        @Volatile var cause: Throwable? = null
        val gotFrame = java.util.concurrent.Semaphore(0)
        override fun onFrame(frame: ByteArray) { frames.add(frame); gotFrame.release() }
        override fun onClosed(cause: Throwable?) { this.cause = cause; closed.countDown() }
    }

    @Test
    fun `large frames and many frames arrive intact and in order`() {
        val (a, b) = socketPair()
        val la = link(a, 32 shl 20); val lb = link(b, 32 shl 20)
        val rx = Collector(); la.open(Collector()); lb.open(rx)
        val big = ByteArray(10 * 1024 * 1024) { (it % 251).toByte() } // far past Binder's ~1 MiB limit
        la.send(big)
        repeat(2000) { la.send("m$it".encodeToByteArray()) }
        assertTrue(rx.gotFrame.tryAcquire(2001, 20, TimeUnit.SECONDS))
        assertTrue(big.contentEquals(rx.frames[0]))
        assertEquals((0 until 2000).map { "m$it" }, rx.frames.drop(1).map { it.decodeToString() })
        la.close(); lb.close()
    }

    @Test
    fun `oversize length prefix is refused before allocating`() {
        val (a, b) = socketPair()
        val rx = Collector(); link(b, max = 1024).open(rx)
        a.getOutputStream().apply { write(byteArrayOf(0x7f, 0xff.toByte(), 0xff.toByte(), 0xff.toByte())); flush() } // ~2 GiB claim
        assertTrue(rx.closed.await(5, TimeUnit.SECONDS))
        assertTrue(rx.cause is TransportLimitException, "cause: ${rx.cause}")
        a.close()
    }

    @Test
    fun `peer disappearing closes the link and fails send`() {
        val (a, b) = socketPair()
        val la = link(a); val rx = Collector(); la.open(rx)
        b.close()
        assertTrue(rx.closed.await(5, TimeUnit.SECONDS))
        assertFailsWith<LinkClosedException> { la.send(byteArrayOf(1)) }
    }

    @Test
    fun `peerGone notifies the listener, unlike local close`() {
        val (a, b) = socketPair()
        val la = link(a); val rx = Collector(); la.open(rx)
        la.peerGone()
        assertTrue(rx.closed.await(5, TimeUnit.SECONDS))
        assertTrue(rx.cause is LinkClosedException)
        b.close()
    }

    @Test
    fun `local close does not call onClosed and unblocks the reader`() {
        val (a, b) = socketPair()
        val la = link(a); val rx = Collector(); la.open(rx)
        la.close(); la.close()
        assertTrue(!rx.closed.await(300, TimeUnit.MILLISECONDS))
        b.close()
    }

    @Test
    fun `sdk client and server work end to end over sockets`() = runBlocking {
        val (a, b) = socketPair()
        val host = ProviderHost(CallerPolicy { true }) { sampleServer() }
        host.accept(trustedCaller, link(b))
        val client = Client(Implementation("c", "0"))
        withTimeout(15_000) {
            client.connect(BinderTransport(link(a)))
            val result = client.callTool("get_status", emptyMap())
            assertEquals("ok", (result.content.firstOrNull() as? TextContent)?.text, "result: $result")
        }
        client.close()
        host.shutdown()
    }

    @Test
    fun `a stalled peer cuts the link instead of blocking forever`() = runBlocking {
        val (a, b) = socketPair() // b never reads
        val transport = BinderTransport(link(a), TransportLimits(sendTimeoutMillis = 500))
        transport.start()
        val closed = CompletableDeferred<Unit>()
        transport.onClose { closed.complete(Unit) }
        val msg = io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification("notifications/x", kotlinx.serialization.json.buildJsonObject {
            put("pad", kotlinx.serialization.json.JsonPrimitive("x".repeat(512 * 1024)))
        })
        val result = runCatching { repeat(2000) { transport.send(msg) } } // overflows socket buffers
        assertNotNull(result.exceptionOrNull())
        withTimeout(5_000) { closed.await() }
        b.close()
    }
}
