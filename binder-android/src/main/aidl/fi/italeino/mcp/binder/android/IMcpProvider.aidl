package fi.italeino.mcp.binder.android;

import fi.italeino.mcp.binder.android.IMcpEndpoint;

interface IMcpProvider {
    /**
     * Opens a session. [client] is where the provider sends its messages; the returned endpoint is
     * where the client sends its own. Throws SecurityException if the caller is not admitted.
     */
    IMcpEndpoint connect(IMcpEndpoint client);
}
