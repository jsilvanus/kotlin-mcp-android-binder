package fi.italeino.mcp.sample.client

import android.content.ComponentName
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import fi.italeino.mcp.binder.android.McpBinderClient
import fi.italeino.mcp.binder.android.ProviderTrust
import fi.italeino.mcp.binder.android.ProviderUnavailableException
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Runs in the client app's process against the separately installed provider app, so every call
 * crosses a real process boundary: real Binder, real socket pair, real getCallingUid() and
 * PackageManager signature lookups. Install both debug APKs first (see README).
 */
class CrossAppTest {
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val pkg = "fi.italeino.mcp.sample.provider"
    private val provider = ComponentName(pkg, "$pkg.SampleProviderService")
    private val trust get() = ProviderTrust.sameSignatureAs(ctx, pkg)

    @Before @After
    fun resetProvider() = shell("am force-stop $pkg")

    private fun shell(cmd: String) {
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
    }

    private suspend fun connect(component: ComponentName = provider, t: ProviderTrust = trust): Client {
        val client = Client(Implementation("test-client", "0"))
        client.connect(McpBinderClient.connect(ctx, component, t))
        return client
    }

    private suspend fun Client.text(tool: String, args: Map<String, Any?> = emptyMap()) =
        (callTool(tool, args).content.single() as TextContent).text

    @Test
    fun toolCallCrossesProcesses() = runBlocking {
        withTimeout(20_000) {
            val client = connect()
            val reply = client.text("get_status")
            assertTrue(reply.startsWith("provider ok, pid "), reply)
            assertNotEquals(android.os.Process.myPid(), reply.substringAfterLast(' ').toInt())
            client.close()
        }
    }

    @Test
    fun tenMegabyteResultFarPastBinderLimit() = runBlocking {
        withTimeout(60_000) {
            val client = connect()
            assertEquals(10 * 1024 * 1024, client.text("big", mapOf("size" to 10 * 1024 * 1024)).length)
            client.close()
        }
    }

    @Test
    fun concurrentRequests() = runBlocking {
        withTimeout(30_000) {
            val client = connect()
            val replies = (1..10).map { async { client.text("slow", mapOf("ms" to 300)) } }.awaitAll()
            assertEquals(List(10) { "done" }, replies)
            client.close()
        }
    }

    @Test
    fun repeatedConnectAndClose() = runBlocking {
        withTimeout(60_000) {
            repeat(25) { connect().also { c -> assertTrue(c.text("get_status").startsWith("provider ok")) }.close() }
        }
    }

    @Test
    fun providerKilledMidCallFailsPromptlyThenReconnects() = runBlocking {
        withTimeout(60_000) {
            val client = connect()
            val inFlight = async { runCatching { client.text("slow", mapOf("ms" to 30_000)) } }
            kotlinx.coroutines.delay(1_000)
            shell("am force-stop $pkg")
            val outcome = withTimeout(10_000) { inFlight.await() } // must fail, not hang for 30 s
            assertTrue(outcome.isFailure, "call survived its provider")
            // The next session works against a fresh provider process.
            val again = connect()
            assertTrue(again.text("get_status").startsWith("provider ok"))
            again.close()
        }
    }

    @Test
    fun providerAllowlistRefusesUnlistedCaller() = runBlocking {
        val denying = ComponentName(pkg, "$pkg.SampleDenyingService")
        val e = assertFailsWith<ProviderUnavailableException> { withTimeout(20_000) { connect(denying) } }
        assertTrue(e.message!!.contains("refused"), e.message)
    }

    @Test
    fun clientRefusesProviderWithWrongPin() = runBlocking {
        val wrongPin = ProviderTrust.pinned(pkg, setOf("00".repeat(32)))
        assertFailsWith<ProviderUnavailableException> { withTimeout(20_000) { connect(t = wrongPin) } }
        // Same component, but claimed to be a different package: also refused before any bind.
        assertFailsWith<ProviderUnavailableException> {
            withTimeout(20_000) { connect(t = ProviderTrust.pinned("com.evil", setOf("aa"))) }
        }
        Unit
    }

    @Test
    fun missingProviderFailsCleanly() = runBlocking {
        val missing = ComponentName("com.example.not.installed", "com.example.not.installed.Svc")
        assertFailsWith<ProviderUnavailableException> {
            withTimeout(20_000) { connect(missing, ProviderTrust.AnyProvider) }
        }
        Unit
    }
}
