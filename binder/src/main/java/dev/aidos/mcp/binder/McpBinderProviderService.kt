package dev.aidos.mcp.binder

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.RemoteException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Base class for an app exporting an MCP server over Binder.
 * Subclasses must explicitly implement authorizeCaller; the default denies every caller.
 */
public abstract class McpBinderProviderService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nextSessionId = AtomicLong()
    private val sessions = ConcurrentHashMap<Long, BinderServerTransport>()

    protected open fun authorizeCaller(caller: BinderCaller): Boolean = false
    protected abstract suspend fun onMcpSession(transport: BinderServerTransport, caller: BinderCaller)

    override fun onBind(intent: Intent?): IBinder = providerBinder

    private val providerBinder = object : IMcpBinderProvider.Stub() {
        override fun openSession(callback: IMcpBinderCallback): IMcpBinderSession {
            // Capture identity before dispatching work to a coroutine.
            val callingUid = Binder.getCallingUid()
            val packages = packageManager.getPackagesForUid(callingUid)?.toSet().orEmpty()
            val caller = BinderCaller(callingUid, packages)
            if (packages.isEmpty() || !authorizeCaller(caller)) {
                throw SecurityException("Caller is not authorized to open an MCP session")
            }

            val id = nextSessionId.incrementAndGet()
            val transport = BinderServerTransport(callback)
            sessions[id] = transport
            val ownerUid = callingUid

            val sessionBinder = object : IMcpBinderSession.Stub() {
                override fun sendMessage(json: String) {
                    enforceOwner(ownerUid)
                    transport.receive(json)
                }
                override fun close() {
                    enforceOwner(ownerUid)
                    sessions.remove(id)
                    transport.closeFromPeer("Client closed the session")
                }
                private fun enforceOwner(expectedUid: Int) {
                    if (Binder.getCallingUid() != expectedUid) {
                        throw SecurityException("MCP session belongs to a different UID")
                    }
                }
            }

            try {
                callback.asBinder().linkToDeath({
                    sessions.remove(id)
                    transport.closeFromPeer("Client process died")
                }, 0)
            } catch (failure: RemoteException) {
                sessions.remove(id)
                transport.closeFromPeer("Client Binder already died")
                throw IllegalStateException("Client disconnected while opening session", failure)
            }

            scope.launch {
                try {
                    onMcpSession(transport, caller)
                } catch (failure: Exception) {
                    transport.closeFromPeer("Provider failed to initialise MCP session")
                }
            }
            return sessionBinder
        }
    }

    override fun onDestroy() {
        sessions.values.forEach { it.closeFromPeer("Provider service destroyed") }
        sessions.clear()
        scope.cancel()
        super.onDestroy()
    }
}
