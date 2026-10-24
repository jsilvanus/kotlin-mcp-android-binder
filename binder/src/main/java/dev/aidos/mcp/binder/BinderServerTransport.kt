package dev.aidos.mcp.binder

import android.os.RemoteException
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** Provider-side transport for one caller session. */
public class BinderServerTransport internal constructor(
    private val callback: IMcpBinderCallback,
    private val maxMessageBytes: Int = BinderMcpTransport.DEFAULT_MAX_MESSAGE_BYTES,
) : Transport {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val closed = AtomicBoolean(false)
    @Volatile private var messageHandler: (suspend (JSONRPCMessage) -> Unit)? = null
    @Volatile private var errorHandler: ((Throwable) -> Unit)? = null
    @Volatile private var closeHandler: (() -> Unit)? = null
    private val handlerReady = CompletableDeferred<suspend (JSONRPCMessage) -> Unit>()
    private val incomingMessages = Channel<String>(capacity = BinderMcpTransport.MAX_PENDING_MESSAGES)

    init {
        scope.launch {
            for (json in incomingMessages) {
                try {
                    val handler = handlerReady.await()
                    handler(McpJson.decodeFromString<JSONRPCMessage>(json))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    reportError(failure)
                }
            }
        }
    }

    override suspend fun start() {
        check(!closed.get()) { "Transport is closed" }
    }

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        check(!closed.get()) { "Transport is closed" }
        val json = McpJson.encodeToString(message)
        require(isWithinLimit(json)) { "Outbound MCP message exceeds configured size limit" }
        try {
            callback.onMessage(json)
        } catch (failure: RemoteException) {
            reportError(failure)
            closeFromPeer("Remote callback failed")
            throw failure
        }
    }

    /** Called by the AIDL session stub after checking the Binder caller UID. */
    internal fun receive(json: String) {
        if (closed.get()) return
        if (!isWithinLimit(json)) {
            reportError(IllegalArgumentException("Inbound MCP message exceeds configured size limit"))
            closeFromPeer("Message size limit exceeded")
            return
        }
        if (incomingMessages.trySend(json).isFailure) {
            reportError(IllegalStateException("Inbound MCP message queue is full"))
            closeFromPeer("Inbound message queue is full")
        }
    }

    override suspend fun close() { closeInternal("Transport closed", notifyPeer = true) }
    internal fun closeFromPeer(reason: String) { closeInternal(reason, notifyPeer = false) }

    override fun onClose(block: () -> Unit) { closeHandler = block }
    override fun onError(block: (Throwable) -> Unit) { errorHandler = block }
    override fun onMessage(block: suspend (JSONRPCMessage) -> Unit) {
        messageHandler = block
        handlerReady.complete(block)
    }

    private fun closeInternal(reason: String, notifyPeer: Boolean) {
        if (!closed.compareAndSet(false, true)) return
        if (notifyPeer) {
            try {
                callback.onClosed(reason)
            } catch (failure: RemoteException) {
                reportError(failure)
            }
        }
        incomingMessages.close()
        closeHandler?.invoke()
        scope.cancel()
    }

    private fun reportError(failure: Throwable) { errorHandler?.invoke(failure) }
    private fun isWithinLimit(json: String): Boolean =
        json.toByteArray(Charsets.UTF_8).size <= maxMessageBytes
}
