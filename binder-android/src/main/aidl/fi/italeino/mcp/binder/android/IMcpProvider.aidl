package fi.italeino.mcp.binder.android;

import android.os.ParcelFileDescriptor;

/**
 * Binder only bootstraps the session. The MCP messages themselves travel over a socket whose other
 * end is returned here, so they are not subject to the ~1 MiB transaction limit and get kernel
 * backpressure. Both sides detect the peer going away from socket EOF (the kernel closes a dead
 * process's descriptors) and, on the client, from the death of this binder.
 */
interface IMcpProvider {
    /**
     * Opens a session. Returns the client's end of a connected socket pair carrying length-prefixed
     * MCP JSON-RPC frames. Throws SecurityException if the caller is not admitted.
     */
    ParcelFileDescriptor connect();
}
