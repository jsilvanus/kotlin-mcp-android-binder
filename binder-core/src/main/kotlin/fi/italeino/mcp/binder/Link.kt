package fi.italeino.mcp.binder

/**
 * One end of a bidirectional, ordered, frame-preserving byte pipe to a single peer.
 *
 * The Android module implements this over Binder; tests implement it in memory. Keeping the MCP
 * transport behind this seam lets protocol, limit and lifecycle behaviour be tested on a JVM.
 *
 * Implementations must be thread-safe. [LinkListener] callbacks arrive on arbitrary threads
 * (Binder threads on Android) and must never be blocked by tool work, so [BinderTransport] only
 * enqueues from them.
 */
public interface Link {
    /** Installs the listener. Called once, by [BinderTransport]'s constructor, before it is started. */
    public fun open(listener: LinkListener)

    /**
     * Sends one frame to the peer. Throws [LinkClosedException] if the peer or link is gone;
     * a throw after dispatch began means the outcome is ambiguous (see [LinkClosedException]).
     */
    public fun send(frame: ByteArray)

    /** Idempotent. Releases the link and notifies the peer on a best-effort basis. */
    public fun close()
}

public interface LinkListener {
    public fun onFrame(frame: ByteArray)

    /** The link ended: peer closed, peer process died, or the binding was lost. Fires at most once. */
    public fun onClosed(cause: Throwable?)
}

/**
 * The peer is unreachable. When thrown from [Link.send] the frame may or may not have been
 * processed by the peer; the transport never retries it because the request may have had side effects.
 */
public class LinkClosedException(message: String, cause: Throwable? = null) : Exception(message, cause)
