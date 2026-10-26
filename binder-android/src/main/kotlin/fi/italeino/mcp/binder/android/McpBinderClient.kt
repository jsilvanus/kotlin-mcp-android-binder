package fi.italeino.mcp.binder.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import fi.italeino.mcp.binder.BinderTransport
import fi.italeino.mcp.binder.LinkClosedException
import fi.italeino.mcp.binder.TransportLimits
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Client side: binds to one explicitly named provider service and returns a transport for an SDK `Client`. */
public object McpBinderClient {
    /**
     * [provider] is an explicit component, never an implicit action: an implicit intent could be
     * answered by an impostor. The binding is released when the returned transport closes.
     * Usage: `Client(info).connect(McpBinderClient.connect(context, component))`.
     */
    public suspend fun connect(
        context: Context,
        provider: ComponentName,
        limits: TransportLimits = TransportLimits(),
    ): BinderTransport = suspendCancellableCoroutine { cont ->
        val app = context.applicationContext
        lateinit var connection: ServiceConnection
        var bound = false
        fun unbind() {
            if (bound) {
                bound = false
                runCatching { app.unbindService(connection) }
            }
        }
        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                if (!cont.isActive) return unbind()
                try {
                    val local = LocalEndpoint()
                    val remote = IMcpProvider.Stub.asInterface(service).connect(local)
                    cont.resume(BinderTransport(BinderLink(local, remote, onRelease = ::unbind), limits))
                } catch (e: Exception) {
                    unbind()
                    cont.resumeWithException(e)
                }
            }

            // Provider process died: BinderLink's death recipient reports it; nothing to resume here.
            override fun onServiceDisconnected(name: ComponentName) = Unit

            override fun onNullBinding(name: ComponentName) {
                unbind()
                if (cont.isActive) cont.resumeWithException(LinkClosedException("provider returned no binder"))
            }

            override fun onBindingDied(name: ComponentName) {
                unbind()
                if (cont.isActive) cont.resumeWithException(LinkClosedException("provider binding died"))
            }
        }
        val intent = Intent().setComponent(provider)
        bound = app.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        if (!bound) {
            cont.resumeWithException(LinkClosedException("cannot bind $provider (missing, not exported, or no permission)"))
        }
        cont.invokeOnCancellation { unbind() }
    }
}
