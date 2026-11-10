# Integration guide

How to serve MCP tools to other apps (provider) and call another app's tools (client). Messages are standard MCP JSON-RPC; Binder only bootstraps a session and hands over a socket pair, so there is no 1 MiB transaction limit.

## Provider app

1. Depend on `fi.italeino.mcp:binder-android` (publish locally with `./gradlew publishToMavenLocal` until a registry release exists).
2. Subclass `McpProviderService` and supply a `ProviderHost`:

```kotlin
class MyMcpService : McpProviderService() {
    override val host: ProviderHost by lazy {
        // Deny by default. Package AND signing certificate: a package name alone is spoofable.
        val policy = CallerPolicy.allowlist(mapOf("com.example.client" to setOf("<sha256 of its signing cert>")))
        ProviderHost(policy) { caller -> Server(Implementation("my-provider", "1.0"), ServerOptions(...)).apply { addTool(...) } }
    }
}
```

3. Declare the service. It must be exported for another app to bind; the real gate is the policy, not the flag:

```xml
<service android:name=".MyMcpService" android:exported="true" />
```

For sensitive tools also add a signature-level permission on the service, and authorise inside each tool: the transport decides who may connect, never what a connected caller may do.

To let the user decide at run time instead of a fixed allowlist, implement `CallerPolicy` over your own store (see Amp's `ApprovalPolicy`): refuse unknown callers, record them as pending, and let the user approve a package plus certificate.

## Client app

```kotlin
val trust = ProviderTrust.pinned("com.example.provider", setOf("<sha256 of provider cert>"))
val transport = McpBinderClient.connect(context, ComponentName("com.example.provider", "com.example.provider.MyMcpService"), trust)
val client = Client(Implementation("my-client", "1.0")).also { it.connect(transport) }
```

- Android 11+ needs `<queries><package android:name="com.example.provider"/></queries>` in the client manifest.
- `ProviderTrust` has no default. `ProviderTrust.AnyProvider` exists for development only.
- To read a provider's certificate digest when the user adopts it: `SigningCerts.of(packageManager, packageName)`.

## Errors

| Situation | What you get |
|---|---|
| Provider missing, not exported, not visible, died, no answer in `connectTimeoutMillis` | `ProviderUnavailableException` (an `IOException`) |
| Provider refused this app | `ProviderUnavailableException`, message contains "refused" |
| Provider fails the pin | `ProviderUnavailableException`, nothing is bound or sent |
| Provider process dies mid-call | The pending SDK request fails; the transport closes |
| Peer stops reading | After `sendTimeoutMillis` the link is closed |
| Oversize or malformed frame | The link is closed; no allocation is made from a hostile length |

A failed or closed session is replaced by calling `connect` again. A call that failed after dispatch has an unknown outcome and is never retried for you.

## Lifecycle

- The client binds on `connect` and unbinds when the transport closes.
- A bound provider is kept alive only while bound; treat death as normal.
- Request timeouts and cancellation are the SDK's (`RequestOptions`, `notifications/cancelled`).

## Compatibility

| Component | Version |
|---|---|
| Official Kotlin MCP SDK | 0.15.0 (pinned) |
| minSdk / compileSdk | 28 / 35 |
| Kotlin / AGP / Gradle | 2.4.0 / 8.7.3 / 8.14 |
| Build JDK | 17 or 21 (JDK 25 fails D8) |
| Tested on device | API 36 emulator (local); API 28 and 34 in CI |

## Limitations

- Same-UID code in the provider is inside the trust boundary.
- A provider reinstalled with the same key stays trusted: the pin is the key, not the version.
- No idle unbinding and no automatic reconnect.
- The AIDL (`IMcpProvider`) is the frozen wire contract; the Kotlin API is tracked by `apiCheck`.
