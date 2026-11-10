package fi.italeino.mcp.binder

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A [Link] over a pair of byte streams (on Android: a socket pair handed over Binder).
 *
 * Frames are length-prefixed (4-byte big-endian length, then the payload). Unlike a Binder
 * transaction there is no 1 MiB ceiling, and the kernel socket buffer gives real backpressure:
 * a writer blocks while the reader is behind, and the reader blocks while [BinderTransport] is
 * saturated, so memory stays bounded without dropping messages.
 *
 * The length is checked against [maxFrameBytes] *before* any allocation, so a hostile peer cannot
 * make us reserve memory by lying about the size.
 *
 * @param shutdown Must unblock a thread stuck in a read or write on the streams (on Android
 *   `Os.shutdown` then close; plain `close()` does not wake a blocked `read` on every platform).
 */
public class StreamLink(
    input: InputStream,
    output: OutputStream,
    private val maxFrameBytes: Int,
    private val shutdown: () -> Unit,
    private val threadName: String = "mcp-stream-link",
) : Link {
    private val input = DataInputStream(input.buffered())
    private val output = DataOutputStream(output.buffered())
    private val writeLock = Any()
    private val closed = AtomicBoolean(false)
    private val opened = AtomicBoolean(false)
    @Volatile private var listener: LinkListener? = null

    override val blocksWhenSaturated: Boolean get() = true

    init {
        require(maxFrameBytes > 0) { "maxFrameBytes must be positive" }
    }

    override fun open(listener: LinkListener) {
        check(opened.compareAndSet(false, true)) { "StreamLink already opened" }
        this.listener = listener
        Thread({ readLoop(listener) }, threadName).apply { isDaemon = true }.start()
    }

    private fun readLoop(listener: LinkListener) {
        var cause: Throwable? = null
        try {
            while (true) {
                val length = try {
                    input.readInt()
                } catch (e: EOFException) {
                    break // clean EOF between frames: the peer closed or died
                }
                if (length < 0 || length > maxFrameBytes) {
                    cause = TransportLimitException("inbound frame length $length exceeds $maxFrameBytes")
                    break
                }
                val frame = ByteArray(length)
                input.readFully(frame)
                listener.onFrame(frame) // may block: that is the backpressure
            }
        } catch (e: IOException) {
            cause = e
        } catch (e: RuntimeException) {
            cause = e
        }
        endLink(cause)
    }

    override fun send(frame: ByteArray) {
        if (closed.get()) throw LinkClosedException("link is closed")
        if (frame.size > maxFrameBytes) throw TransportLimitException("outbound frame of ${frame.size} bytes exceeds $maxFrameBytes")
        try {
            synchronized(writeLock) {
                output.writeInt(frame.size)
                output.write(frame)
                output.flush()
            }
        } catch (e: IOException) {
            endLink(e)
            throw LinkClosedException("peer gone while sending", e)
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) release()
    }

    /**
     * The peer is known to be gone (e.g. its process died) before the reader noticed. Unlike
     * [close], this tells the listener, so pending requests fail instead of waiting.
     */
    public fun peerGone(cause: Throwable? = null) {
        endLink(cause ?: LinkClosedException("peer process died"))
    }

    /** The reader stopped: tell the transport once, unless we closed locally first. */
    private fun endLink(cause: Throwable?) {
        if (closed.compareAndSet(false, true)) {
            release()
            listener?.onClosed(cause)
        }
    }

    private fun release() {
        runCatching { shutdown() }
    }
}
