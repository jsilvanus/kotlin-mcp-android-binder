package fi.italeino.mcp.binder.android

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import fi.italeino.mcp.binder.StreamLink

/** Wraps one end of a socket pair as a [Link]. Closing shuts the socket down so blocked reads and writes return. */
internal fun socketLink(pfd: ParcelFileDescriptor, maxFrameBytes: Int, name: String): StreamLink =
    StreamLink(
        input = ParcelFileDescriptor.AutoCloseInputStream(pfd),
        output = ParcelFileDescriptor.AutoCloseOutputStream(pfd),
        maxFrameBytes = maxFrameBytes,
        shutdown = {
            // shutdown() wakes a thread blocked in read/write on the same socket; close() alone may not.
            runCatching { Os.shutdown(pfd.fileDescriptor, OsConstants.SHUT_RDWR) }
            runCatching { pfd.close() }
        },
        threadName = name,
    )
