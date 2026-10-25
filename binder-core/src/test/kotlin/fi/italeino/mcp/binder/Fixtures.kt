package fi.italeino.mcp.binder

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import java.util.concurrent.atomic.AtomicBoolean

/** Two ends of an in-memory pipe. Delivery happens on the sender's thread, like a Binder callback. */
class InMemoryLink private constructor(private val name: String) : Link {
    @Volatile private var listener: LinkListener? = null
    @Volatile private var peer: InMemoryLink? = null
    private val closed = AtomicBoolean(false)
    val sent = java.util.concurrent.atomic.AtomicInteger()

    override fun open(listener: LinkListener) {
        this.listener = listener
    }

    override fun send(frame: ByteArray) {
        val p = peer
        if (closed.get() || p == null || p.closed.get()) throw LinkClosedException("$name: peer gone")
        sent.incrementAndGet()
        p.listener?.onFrame(frame)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        peer?.remoteClosed()
    }

    /** Simulates the peer process dying: no close handshake, only this side's death notification. */
    fun killPeer() {
        val p = peer ?: return
        if (!p.closed.compareAndSet(false, true)) return
        remoteClosed()
    }

    private fun remoteClosed() {
        if (closed.compareAndSet(false, true)) listener?.onClosed(null)
    }

    companion object {
        fun pair(): Pair<InMemoryLink, InMemoryLink> {
            val a = InMemoryLink("client")
            val b = InMemoryLink("provider")
            a.peer = b
            b.peer = a
            return a to b
        }
    }
}

val trustedCaller = CallerIdentity(10_001, setOf("com.example.client"), setOf("abc123"))

/** The sample provider: `get_status` is harmless; `slow` lets tests hold a request in flight. */
fun sampleServer(release: kotlinx.coroutines.CompletableDeferred<Unit>? = null): Server {
    val server = Server(
        Implementation("sample-provider", "0.0.1"),
        ServerOptions(ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false))),
    )
    server.addTool("get_status", "Reports that the provider is alive", ToolSchema()) {
        CallToolResult(content = listOf(TextContent("ok")))
    }
    server.addTool("slow", "Waits until the test releases it", ToolSchema()) {
        release?.await()
        CallToolResult(content = listOf(TextContent("done")))
    }
    return server
}
