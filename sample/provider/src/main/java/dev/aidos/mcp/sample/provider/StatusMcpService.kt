package dev.aidos.mcp.sample.provider

import dev.aidos.mcp.binder.BinderCaller
import dev.aidos.mcp.binder.BinderServerTransport
import dev.aidos.mcp.binder.McpBinderProviderService
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent

class StatusMcpService : McpBinderProviderService() {
    override fun authorizeCaller(caller: BinderCaller): Boolean =
        caller.packageNames.contains("dev.aidos.mcp.sample.client")

    override suspend fun onMcpSession(transport: BinderServerTransport, caller: BinderCaller) {
        val server = Server(
            serverInfo = Implementation(name = "binder-sample-provider", version = "0.1.0"),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = false),
                ),
            ),
        )
        server.addTool(
            name = "get_status",
            description = "Returns a harmless fixed status string for transport testing.",
        ) {
            CallToolResult(content = listOf(TextContent("binder-ok")))
        }
        server.createSession(transport)
    }
}
