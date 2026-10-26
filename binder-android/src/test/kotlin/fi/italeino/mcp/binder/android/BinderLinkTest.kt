package fi.italeino.mcp.binder.android

import fi.italeino.mcp.binder.BinderTransport
import fi.italeino.mcp.binder.CallerIdentity
import fi.italeino.mcp.binder.CallerPolicy
import fi.italeino.mcp.binder.ProviderHost
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Exercises the AIDL wiring, LocalEndpoint buffering and BinderLink in one process. Binder calls here
 * are direct, so this does NOT prove cross-process behaviour, death notification or caller identity;
 * those need the two sample apps on a device.
 */
@RunWith(RobolectricTestRunner::class)
class BinderLinkTest {
    private val caller = CallerIdentity(10_001, setOf("com.example.client"), setOf("abc"))

    private fun host(policy: CallerPolicy) = ProviderHost(policy) {
        Server(
            Implementation("p", "0"),
            ServerOptions(ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false))),
        ).apply {
            addTool("get_status", "alive", ToolSchema()) { CallToolResult(content = listOf(TextContent("ok"))) }
        }
    }

    @Test
    fun `client calls a tool through the AIDL stubs`() = runBlocking {
        val provider = ProviderBinder({ host(CallerPolicy { true }) }.let { f -> val h = f(); { h } }) { caller }
        val local = LocalEndpoint()
        val remote = provider.connect(local)
        val client = Client(Implementation("c", "0"))
        withTimeout(10_000) {
            client.connect(BinderTransport(BinderLink(local, remote)))
            assertEquals("ok", (client.callTool("get_status", emptyMap()).content.single() as TextContent).text)
        }
        client.close()
    }

    @Test
    fun `unauthorised caller gets a SecurityException`() {
        val h = host(CallerPolicy.DenyAll)
        val provider = ProviderBinder({ h }) { caller }
        assertFailsWith<SecurityException> { provider.connect(LocalEndpoint()) }
        assertEquals(0, h.activeSessions)
    }
}
