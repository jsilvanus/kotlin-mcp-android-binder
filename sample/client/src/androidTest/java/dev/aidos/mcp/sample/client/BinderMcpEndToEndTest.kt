package dev.aidos.mcp.sample.client

import android.content.ComponentName
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.aidos.mcp.binder.BinderMcpTransport
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BinderMcpEndToEndTest {
    @Test
    fun initializesAndCallsToolInSeparateProviderApp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val transport = BinderMcpTransport(
            context = context,
            providerComponent = ComponentName(
                "dev.aidos.mcp.sample.provider",
                "dev.aidos.mcp.sample.provider.StatusMcpService",
            ),
        )
        val client = Client(
            clientInfo = Implementation(name = "binder-sample-client", version = "0.1.0"),
        )
        try {
            Log.i(TAG, "Starting MCP connect")
            client.connect(transport)
            Log.i(TAG, "MCP connect completed; listing tools")
            val tools = client.listTools().tools
            Log.i(TAG, "Listed tools: ${tools.map { it.name }}")
            assertTrue("get_status tool should be listed", tools.any { it.name == "get_status" })
            val result = client.callTool("get_status", emptyMap())
            Log.i(TAG, "get_status returned: ${result.content}")
            assertTrue(
                "Expected provider response binder-ok, got ${result.content}",
                result.content.any { it is TextContent && it.text == "binder-ok" },
            )
        } finally {
            client.close()
        }
    }

    private companion object {
        const val TAG = "McpBinderE2E"
    }
}