package dev.aidos.mcp.binder;
interface IMcpBinderSession {
    oneway void sendMessage(String json);
    oneway void close();
}
