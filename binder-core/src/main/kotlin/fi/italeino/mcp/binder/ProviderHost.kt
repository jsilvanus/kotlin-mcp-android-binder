package fi.italeino.mcp.binder

import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Thrown to the caller when [CallerPolicy] or the session cap refuses a connection. */
public class ConnectionRefusedException(message: String) : SecurityException(message)

/**
 * Provider side: admits callers, then serves one SDK `Server` per accepted caller.
 *
 * Android-free so admission rules can be tested on a JVM. The Android service calls [accept] from its
 * Binder entry point with an identity captured on that thread.
 *
 * @param serverFactory builds a fresh `Server` per session; tools and their own authorisation live there.
 */
public class ProviderHost(
    private val policy: CallerPolicy = CallerPolicy.DenyAll,
    private val limits: TransportLimits = TransportLimits(),
    private val maxSessions: Int = 8,
    private val serverFactory: (CallerIdentity) -> Server,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sessions = ConcurrentHashMap.newKeySet<BinderTransport>()
    private val shutDown = AtomicBoolean(false)

    /** Throws [ConnectionRefusedException] without touching [link] if the caller is not admitted. */
    public fun accept(caller: CallerIdentity, link: Link) {
        if (shutDown.get()) throw ConnectionRefusedException("provider is shut down")
        if (!policy.isAllowed(caller)) throw ConnectionRefusedException("caller uid ${caller.uid} is not authorised")
        val transport = BinderTransport(link, limits)
        // Reserve before connecting so a burst cannot overshoot the cap.
        synchronized(sessions) {
            if (sessions.size >= maxSessions) throw ConnectionRefusedException("too many sessions")
            sessions.add(transport)
        }
        transport.onClose { sessions.remove(transport) }
        scope.launch {
            try {
                serverFactory(caller).createSession(transport)
            } catch (e: Exception) {
                transport.close()
            }
        }
    }

    public val activeSessions: Int get() = sessions.size

    public suspend fun shutdown() {
        if (!shutDown.compareAndSet(false, true)) return
        sessions.toList().forEach { it.close() }
        scope.cancel()
    }
}
