package fi.italeino.mcp.binder.android;

/**
 * One direction of an MCP session. Each side hands the other one of these.
 *
 * oneway: a Binder thread only enqueues, so tool work can never block (or deadlock) a transaction
 * thread. Oneway calls from one process to one binder object are delivered in order.
 */
oneway interface IMcpEndpoint {
    /** One UTF-8 JSON-RPC message. The receiver enforces its own size limit. */
    void send(in byte[] frame);

    /** The sender is done with the session. */
    void close();
}
