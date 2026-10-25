package fi.italeino.mcp.binder.android

import android.os.DeadObjectException
import android.os.IBinder
import android.os.RemoteException
import fi.italeino.mcp.binder.Link
import fi.italeino.mcp.binder.LinkClosedException
import fi.italeino.mcp.binder.LinkListener
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The endpoint we hand to the peer. It exists before the [BinderLink] does (the peer needs it to
 * answer `connect`), so early frames and an early close are held until the link attaches.
 */
internal class LocalEndpoint : IMcpEndpoint.Stub() {
    private val lock = Any()
    private var listener: LinkListener? = null
    private val pending = ArrayDeque<ByteArray>()
    private var peerClosed = false

    override fun send(frame: ByteArray) {
        synchronized(lock) {
            val l = listener
            when {
                l != null -> l.onFrame(frame) // only enqueues, see BinderTransport
                pending.size < MAX_PENDING -> pending.addLast(frame)
                else -> peerClosed = true // peer flooded us before attach: fail closed
            }
        }
    }

    override fun close() {
        val l = synchronized(lock) { peerClosed = true; listener }
        l?.onClosed(null)
    }

    /** Delivers anything held so far, in order, then everything after. */
    fun attach(l: LinkListener) {
        val closedAlready: Boolean
        synchronized(lock) {
            listener = l
            while (pending.isNotEmpty()) l.onFrame(pending.removeFirst())
            closedAlready = peerClosed
        }
        if (closedAlready) l.onClosed(null)
    }

    private companion object {
        const val MAX_PENDING = 64
    }
}

/**
 * A [Link] over Binder: frames arrive through [local], go out through [remote]. Peer process death
 * arrives through linkToDeath and surfaces as [LinkListener.onClosed].
 */
internal class BinderLink(
    private val local: LocalEndpoint,
    private val remote: IMcpEndpoint,
    private val onRelease: () -> Unit = {},
) : Link {
    @Volatile private var listener: LinkListener? = null
    private val closed = AtomicBoolean(false)
    private val death = IBinder.DeathRecipient { terminate(LinkClosedException("peer process died")) }

    init {
        try {
            remote.asBinder().linkToDeath(death, 0)
        } catch (e: RemoteException) {
            closed.set(true)
        }
    }

    override fun open(listener: LinkListener) {
        val relay = object : LinkListener {
            override fun onFrame(frame: ByteArray) = listener.onFrame(frame)
            override fun onClosed(cause: Throwable?) = terminate(cause)
        }
        this.listener = listener
        if (closed.get()) {
            listener.onClosed(LinkClosedException("peer already gone"))
        } else {
            local.attach(relay)
        }
    }

    override fun send(frame: ByteArray) {
        if (closed.get()) throw LinkClosedException("link is closed")
        try {
            remote.send(frame)
        } catch (e: DeadObjectException) {
            terminate(e)
            throw LinkClosedException("peer died", e)
        } catch (e: RemoteException) {
            // Includes TransactionTooLargeException; the frame limit should keep us under it.
            throw LinkClosedException("send failed", e)
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            runCatching { remote.close() }
            release()
        }
    }

    private fun terminate(cause: Throwable?) {
        if (closed.compareAndSet(false, true)) {
            release()
            listener?.onClosed(cause)
        }
    }

    private fun release() {
        runCatching { remote.asBinder().unlinkToDeath(death, 0) }
        onRelease()
    }
}
