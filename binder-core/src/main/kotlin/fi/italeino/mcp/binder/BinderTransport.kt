package fi.italeino.mcp.binder

import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.shared.serializeMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** Limits that keep a misbehaving peer from exhausting memory. Binder's hard cap is about 1 MiB per process. */
public data class TransportLimits(
    /** Largest single JSON-RPC message in either direction, in UTF-8 bytes. Chunking is not implemented. */
    val maxFrameBytes: Int = 512 * 1024,
    /** Frames received but not yet handed to the SDK. Overflow closes the transport (fail closed). */
    val maxQueuedInbound: Int = 64,
) {
    init {
        require(maxFrameBytes > 0 && maxQueuedInbound > 0) { "limits must be positive" }
    }
}

/** Raised through `onError` before the transport closes itself. */
public class TransportLimitException(message: String) : Exception(message)

/**
 * The official Kotlin MCP SDK [io.modelcontextprotocol.kotlin.sdk.shared.Transport] over a [Link].
 *
 * One instance serves one peer, for either an SDK `Client` or `Server`. Message encoding is the
 * SDK's own `serializeMessage`/`deserializeMessage`, so the wire format is plain MCP JSON-RPC.
 *
 * Lifecycle: [start] once, [close] any number of times. After the link ends or a limit is violated
 * the transport closes, `onClose` fires exactly once, and later sends fail with [LinkClosedException].
 * Nothing is retried.
 */
public class BinderTransport(
    private val link: Link,
    private val limits: TransportLimits = TransportLimits(),
) : AbstractTransport() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val inbound = Channel<ByteArray>(limits.maxQueuedInbound)
    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    init {
        // Listen from construction, not from start(): the peer may send the moment it is
        // connected, and a frame with no listener would be lost. The bounded queue holds it.
        link.open(object : LinkListener {
            // Binder threads: validate size and enqueue only. No parsing, no tool work.
            override fun onFrame(frame: ByteArray) {
                if (closed.get()) return
                if (frame.size > limits.maxFrameBytes) {
                    fail(TransportLimitException("inbound frame of ${frame.size} bytes exceeds ${limits.maxFrameBytes}"))
                } else if (!inbound.trySend(frame).isSuccess) {
                    fail(TransportLimitException("inbound queue full (${limits.maxQueuedInbound})"))
                }
            }

            override fun onClosed(cause: Throwable?) {
                shutdown(cause, notifyPeer = false)
            }
        })
    }

    override suspend fun start() {
        check(started.compareAndSet(false, true)) { "BinderTransport already started" }
        scope.launch {
            // Messages are handled in arrival order; the SDK decides what runs concurrently.
            for (frame in inbound) {
                val message = try {
                    // ReadBuffer's deserializeMessage is internal in 0.15.0; McpJson is the SDK's public codec.
                    McpJson.decodeFromString(JSONRPCMessage.serializer(), frame.decodeToString())
                } catch (e: Exception) {
                    fail(e)
                    return@launch
                }
                try {
                    _onMessage(message)
                } catch (e: Exception) {
                    // A throwing handler is the SDK's problem, not a reason to drop later messages.
                    _onError(e)
                }
            }
        }
    }

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        if (closed.get()) throw LinkClosedException("transport is closed")
        val frame = serializeMessage(message).encodeToByteArray()
        // Refuse locally: an oversize transaction would fail opaquely inside Binder.
        if (frame.size > limits.maxFrameBytes) {
            throw TransportLimitException("outbound frame of ${frame.size} bytes exceeds ${limits.maxFrameBytes}")
        }
        link.send(frame)
    }

    override suspend fun close() {
        shutdown(null, notifyPeer = true)
    }

    private fun fail(cause: Throwable) {
        shutdown(cause, notifyPeer = true)
    }

    private fun shutdown(cause: Throwable?, notifyPeer: Boolean) {
        if (!closed.compareAndSet(false, true)) return
        if (cause != null) runCatching { _onError(cause) }
        inbound.close()
        if (notifyPeer) runCatching { link.close() }
        scope.cancel()
        invokeOnCloseCallback()
    }
}
