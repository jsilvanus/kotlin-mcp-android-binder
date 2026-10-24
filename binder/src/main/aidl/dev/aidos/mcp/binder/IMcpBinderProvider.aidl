package dev.aidos.mcp.binder;
import dev.aidos.mcp.binder.IMcpBinderCallback;
import dev.aidos.mcp.binder.IMcpBinderSession;
interface IMcpBinderProvider {
    IMcpBinderSession openSession(IMcpBinderCallback callback);
}
