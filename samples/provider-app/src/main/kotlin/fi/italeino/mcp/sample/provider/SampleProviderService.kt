package fi.italeino.mcp.sample.provider

import fi.italeino.mcp.binder.CallerPolicy
import fi.italeino.mcp.binder.ProviderHost
import fi.italeino.mcp.binder.android.McpProviderService
import fi.italeino.mcp.binder.android.SigningCerts
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema

class SampleProviderService : McpProviderService() {
    override val host: ProviderHost by lazy {
        // Only the sample client, and only when signed with the same key as this app.
        val policy = CallerPolicy.allowlist(
            mapOf("fi.italeino.mcp.sample.client" to SigningCerts.of(packageManager, packageName)),
        )
        ProviderHost(policy) {
            Server(
                Implementation("sample-provider", "0.0.1"),
                ServerOptions(ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false))),
            ).apply {
                // Side-effect free on purpose; real providers must authorise sensitive tools themselves.
                addTool("get_status", "Reports that the provider is alive", ToolSchema()) {
                    CallToolResult(content = listOf(TextContent("provider ok, pid ${android.os.Process.myPid()}")))
                }
            }
        }
    }
}
