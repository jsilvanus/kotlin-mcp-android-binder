package dev.aidos.mcp.binder;
oneway interface IMcpBinderCallback {
    void onMessage(String json);
    void onClosed(String reason);
}
