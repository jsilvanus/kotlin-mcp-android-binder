package dev.aidos.mcp.binder

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Client-side MCP transport. Bind to an explicit provider component; do not use implicit
 * service discovery for a capability-bearing endpoint. The provider must authorize callers.
 */
public class BinderMcpTransport(
    context: Context,
    private val providerComponent: ComponentName,
    private val connectTimeoutMillis: Long = DEFAULT_CONNECT_TIMEOUT_MILLIS,
    private val maxMessageBytes: Int = DEFAULT_MAX_MESSAGE_BYTES,
) : Transport {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val closed = AtomicBoolean(false)
    private val startLock = Any()
    private var startDeferred: CompletableDeferred<Unit>? = null
    @Volatile private var bound = false
    @Volatile private var session: IMcpBinderSession? = null
    @Volatile private var messageHandler: (suspend (JSONRPCMessage) -> Unit)? = null
    @Volatile private var errorHandler: ((Throwable) -> Unit)? = null
    @Volatile private var closeHandler: (() -> Unit)? = null

    private val incoming = object : IMcpBinderCallback.Stub() {
        override fun onMessage(json: String) {
            if (!isWithinLimit(json)) {
                reportError(IllegalArgumentException("Inbound MCP message exceeds configured size limit"))
                closeFromPeer("Message size limit exceeded")
                return
            }
            scope.launch {
                try {
                    val message = McpJson.decodeFromString<JSONRPCMessage>(json)
                    messageHandler?.invoke(message)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    reportError(failure)
                }
            }
        }

        override fun onClosed(reason: String) {
            closeFromPeer(reason)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (closed.get()) return
            try {
                val provider = IMcpBinderProvider.Stub.asInterface(binder)
                    ?: error("Provider returned an invalid Binder")
                session = provider.openSession(incoming)
                startDeferred?.complete(Unit)
            } catch (failure: Exception) {
                startDeferred?.completeExceptionally(failure)
                reportError(failure)
                releaseBinding()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            closeFromPeer("Provider service disconnected")
        }

        override fun onBindingDied(name: ComponentName?) {
            closeFromPeer("Provider binding died")
        }

        override fun onNullBinding(name: ComponentName?) {
            closeFromPeer("Provider returned a null binding")
        }
    }

    override suspend fun start() {
        if (session != null) return
        check(!closed.get()) { "Transport is closed" }
        val deferred = synchronized(startLock) {
            startDeferred ?: CompletableDeferred<Unit>().also { startDeferred = it }
        }
        if (!bound) {
            val intent = Intent().setComponent(providerComponent)
            bound = try {
                appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            } catch (failure: Exception) {
                deferred.completeExceptionally(failure)
                throw failure
            }
            if (!bound) {
                val failure = IllegalStateException("Could not bind to MCP provider $providerComponent")
                deferred.completeExceptionally(failure)
                throw failure
            }
        }
        try {
            withTimeout(connectTimeoutMillis) { deferred.await() }
        } catch (cancelled: CancellationException) {
            if (!deferred.isCompleted) releaseBinding()
            throw cancelled
        }
    }

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        check(!closed.get()) { "Transport is closed" }
        val json = McpJson.encodeToString(message)
        require(isWithinLimit(json)) { "Outbound MCP message exceeds configured size limit" }
        val activeSession = session ?: error("Transport has not been started")
        try {
            activeSession.sendMessage(json)
        } catch (failure: RemoteException) {
            reportError(failure)
            closeFromPeer("Binder send failed")
            throw failure
        }
    }

    override suspend fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            session?.close()
        } catch (failure: Exception) {
            reportError(failure)
        } finally {
            session = null
            releaseBinding()
            scope.cancel()
            closeHandler?.invoke()
        }
    }

    override fun onClose(block: () -> Unit) { closeHandler = block }
    override fun onError(block: (Throwable) -> Unit) { errorHandler = block }
    override fun onMessage(block: suspend (JSONRPCMessage) -> Unit) { messageHandler = block }

    private fun closeFromPeer(reason: String) {
        if (!closed.compareAndSet(false, true)) return
        startDeferred?.completeExceptionally(IllegalStateException(reason))
        session = null
        releaseBinding()
        reportError(IllegalStateException(reason))
        closeHandler?.invoke()
        scope.cancel()
    }

    private fun reportError(failure: Throwable) { errorHandler?.invoke(failure) }

    private fun releaseBinding() {
        if (bound) {
            bound = false
            try {
                appContext.unbindService(connection)
            } catch (_: IllegalArgumentException) {
                // Already unbound by Android or a terminal connection path.
            }
        }
    }

    private fun isWithinLimit(json: String): Boolean =
        json.toByteArray(Charsets.UTF_8).size <= maxMessageBytes

    public companion object {
        public const val DEFAULT_MAX_MESSAGE_BYTES: Int = 256 * 1024
        public const val DEFAULT_CONNECT_TIMEOUT_MILLIS: Long = 10_000
    }
}
