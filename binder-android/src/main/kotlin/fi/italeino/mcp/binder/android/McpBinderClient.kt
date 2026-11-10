package fi.italeino.mcp.binder.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.DeadObjectException
import android.os.IBinder
import android.os.RemoteException
import fi.italeino.mcp.binder.BinderTransport
import fi.italeino.mcp.binder.TransportLimits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The provider could not be reached or did not pass [ProviderTrust]. No MCP message was sent to it. */
public class ProviderUnavailableException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Client side: binds to one explicitly named provider service and returns a transport for an SDK `Client`. */
public object McpBinderClient {
    /**
     * [provider] is an explicit component, never an implicit action: an implicit intent could be
     * answered by an impostor. [trust] decides whether the package behind it is acceptable; it has no
     * default so skipping it is a visible choice. The binding is released when the returned transport
     * closes. Calling this again after a failure or close is the reconnect path: each call is an
     * independent session.
     *
     * Fails with [ProviderUnavailableException] if the provider is missing, not exported, refuses this
     * app, fails [trust], dies while connecting, or does not answer within [connectTimeoutMillis].
     *
     * Usage: `Client(info).connect(McpBinderClient.connect(context, component, trust))`.
     */
    public suspend fun connect(
        context: Context,
        provider: ComponentName,
        trust: ProviderTrust,
        limits: TransportLimits = TransportLimits(),
        connectTimeoutMillis: Long = 10_000,
    ): BinderTransport {
        val app = context.applicationContext
        if (!trust.verify(app.packageManager, provider.packageName)) {
            throw ProviderUnavailableException("${provider.packageName} is not the trusted provider (package or signing certificate mismatch)")
        }
        val binding = Binding(app)
        try {
            return withTimeout(connectTimeoutMillis) {
                val service = binding.bind(provider)
                // Re-check now that we are connected: closes the window in which the package could change.
                if (!trust.verify(app.packageManager, provider.packageName)) {
                    throw ProviderUnavailableException("provider changed while connecting")
                }
                // Off the main thread: ServiceConnection callbacks run there, the Binder call need not.
                val pfd = withContext(Dispatchers.IO) {
                    try {
                        IMcpProvider.Stub.asInterface(service).connect()
                    } catch (e: SecurityException) {
                        throw ProviderUnavailableException("provider refused this app: ${e.message}", e)
                    } catch (e: DeadObjectException) {
                        throw ProviderUnavailableException("provider died while connecting", e)
                    } catch (e: RemoteException) {
                        throw ProviderUnavailableException("connect failed", e)
                    }
                }
                val link = socketLink(pfd, limits.maxFrameBytes, "mcp-client-${provider.shortClassName}")
                val death = IBinder.DeathRecipient { link.peerGone() }
                try {
                    service.linkToDeath(death, 0)
                } catch (e: RemoteException) {
                    link.close()
                    throw ProviderUnavailableException("provider already gone", e)
                }
                BinderTransport(link, limits).also { transport ->
                    transport.onClose {
                        runCatching { service.unlinkToDeath(death, 0) }
                        binding.unbind()
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            binding.unbind()
            throw ProviderUnavailableException("no answer from $provider within $connectTimeoutMillis ms", e)
        } catch (e: Throwable) {
            binding.unbind()
            throw e
        }
    }

    private class Binding(private val app: Context) {
        private var connection: ServiceConnection? = null
        private var bound = false

        suspend fun bind(provider: ComponentName): IBinder = suspendCancellableCoroutine { cont ->
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    if (cont.isActive) cont.resume(service)
                }

                // Process death after connecting shows up as socket EOF and through the death recipient.
                override fun onServiceDisconnected(name: ComponentName) = Unit

                override fun onNullBinding(name: ComponentName) {
                    if (cont.isActive) cont.resumeWithException(ProviderUnavailableException("provider returned no binder"))
                }

                override fun onBindingDied(name: ComponentName) {
                    if (cont.isActive) cont.resumeWithException(ProviderUnavailableException("provider binding died"))
                }
            }
            synchronized(this) { connection = conn }
            val ok = try {
                app.bindService(Intent().setComponent(provider), conn, Context.BIND_AUTO_CREATE)
            } catch (e: SecurityException) {
                false
            }
            synchronized(this) { bound = ok }
            if (!ok) {
                cont.resumeWithException(
                    ProviderUnavailableException("cannot bind $provider (missing, not exported, not visible to this app, or no permission)"),
                )
            }
            cont.invokeOnCancellation { unbind() }
        }

        fun unbind() {
            val c = synchronized(this) {
                val c = connection
                connection = null
                val wasBound = bound
                bound = false
                if (wasBound) c else null
            }
            if (c != null) runCatching { app.unbindService(c) }
        }
    }
}
