package fi.italeino.mcp.sample.client

import android.app.Activity
import android.content.ComponentName
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import fi.italeino.mcp.binder.android.McpBinderClient
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class MainActivity : Activity() {
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val output = TextView(this).apply { text = "Press the button to call get_status in the provider app." }
        val button = Button(this).apply { text = "Call get_status" }
        setContentView(LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(button); addView(output) })
        button.setOnClickListener {
            scope.launch { output.text = runCatching { callProvider() }.getOrElse { "failed: $it" } }
        }
    }

    private suspend fun callProvider(): String = withContext(Dispatchers.Default) {
        withTimeout(15_000) {
            val provider = ComponentName("fi.italeino.mcp.sample.provider", "fi.italeino.mcp.sample.provider.SampleProviderService")
            val client = Client(Implementation("sample-client", "0.0.1"))
            client.connect(McpBinderClient.connect(this@MainActivity, provider))
            try {
                val result = client.callTool("get_status", emptyMap())
                (result.content.firstOrNull() as? TextContent)?.text ?: "no text"
            } finally {
                client.close()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }
}
