package fi.italeino.mcp.sample.provider

import fi.italeino.mcp.binder.CallerPolicy
import fi.italeino.mcp.binder.ProviderHost
import fi.italeino.mcp.binder.TransportLimits
import fi.italeino.mcp.binder.android.McpProviderService
import fi.italeino.mcp.binder.android.SigningCerts
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

private const val SAMPLE_CLIENT = "fi.italeino.mcp.sample.client"

internal fun sampleServer(): Server = Server(
    Implementation("sample-provider", "0.0.1"),
    ServerOptions(ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false))),
).apply {
    // Side-effect free on purpose; real providers must authorise sensitive tools themselves.
    addTool("get_status", "Reports that the provider is alive", ToolSchema()) {
        CallToolResult(content = listOf(TextContent("provider ok, pid ${android.os.Process.myPid()}")))
    }
    // Test aids: a result far larger than Binder's ~1 MiB transaction limit, and a call that stays in flight.
    addTool("big", "Returns `size` characters", ToolSchema()) { req ->
        val size = (req.arguments?.get("size") as? JsonPrimitive)?.intOrNull ?: 1
        CallToolResult(content = listOf(TextContent("x".repeat(size))))
    }
    addTool("slow", "Answers after `ms` milliseconds", ToolSchema()) { req ->
        delay((req.arguments?.get("ms") as? JsonPrimitive)?.longOrNull ?: 0)
        CallToolResult(content = listOf(TextContent("done")))
    }
}

class SampleProviderService : McpProviderService() {
    // Large results are the point of the socket transport.
    override val maxFrameBytes: Int get() = 32 * 1024 * 1024

    override val host: ProviderHost by lazy {
        // Only the sample client, and only when signed with the same key as this app.
        val policy = CallerPolicy.allowlist(mapOf(SAMPLE_CLIENT to SigningCerts.of(packageManager, packageName)))
        ProviderHost(policy, TransportLimits(maxFrameBytes = maxFrameBytes)) { sampleServer() }
    }
}

/** Same server, but its allowlist names a package nobody uses: every real caller must be refused. */
class SampleDenyingService : McpProviderService() {
    override val host: ProviderHost by lazy {
        val policy = CallerPolicy.allowlist(mapOf("com.example.nobody" to SigningCerts.of(packageManager, packageName)))
        ProviderHost(policy) { sampleServer() }
    }
}
