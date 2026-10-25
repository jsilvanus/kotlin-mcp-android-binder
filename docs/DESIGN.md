# Design note (M0): compatibility and structure

## Official SDK

- Artifact: `io.modelcontextprotocol:kotlin-sdk-client` and `kotlin-sdk-server`, **pinned to 0.15.0** (Aidos `mcp-core` uses the same version). The SDK is pre-1.0 and import paths have moved before, so bumps are deliberate and run the full test suite.
- Android uses the SDK's JVM artifact; there is no Android-specific artifact. Verified here: client and server both run on the Robolectric (Android) runtime and on a plain JVM.
- Contract implemented: `Transport` (`start`, `send`, `close`, `onMessage`, `onError`, `onClose`) via `AbstractTransport`. Server side uses `Server.createSession(transport)`, client side `Client.connect(transport)`.
- `ReadBuffer.deserializeMessage` is `internal` in 0.15.0, so decoding uses the public `McpJson` with `JSONRPCMessage.serializer()`; encoding uses the public `serializeMessage`. One frame is one JSON-RPC message (no newline framing needed over Binder).

## Toolchain

Kotlin 2.4.0, Gradle 8.14, AGP 8.7.3, Java 17 bytecode, `minSdk` 28 (first API with `PackageInfo.signingInfo`, needed for certificate verification), `compileSdk` 35.

## Modules

| Module | Android? | Contents |
|---|---|---|
| `binder-core` | no | `BinderTransport`, the `Link` seam, limits, `CallerPolicy`, `ProviderHost`. All protocol, limit, admission and lifecycle logic, tested on a JVM against the real SDK. |
| `binder-android` | yes | The AIDL contract, `BinderLink`, `McpProviderService`, `McpBinderClient`, `SigningCerts`. Thin: it only turns Binder into a `Link` and captures caller identity. |
| `samples:provider-app`, `samples:client-app` | yes | Two apps proving the flow with a harmless `get_status` tool. |

The AIDL contract lives in the library (not a host app) so providers and consumers share one definition.

## Wire contract

```
oneway interface IMcpEndpoint { void send(in byte[] frame); void close(); }
interface IMcpProvider        { IMcpEndpoint connect(IMcpEndpoint client); }
```

The client binds an explicitly named service and calls `connect` with its own endpoint; the provider returns its endpoint. After that each side sends frames to the other. `send` is `oneway`, so a Binder thread only enqueues and tool work never runs on a transaction thread; oneway calls from one process to one binder object stay ordered. The provider reads `Binder.getCallingUid()` synchronously inside `connect`, then asks its `CallerPolicy`.

## Behaviour (M2 items already covered)

- Size: frames over `maxFrameBytes` (default 512 KiB, under Binder's ~1 MiB shared buffer) are refused locally on send; an oversize or malformed inbound frame closes the transport. No chunking yet.
- Backpressure: inbound frames wait in a bounded queue (64); overflow closes the transport (fail closed).
- Close: idempotent, `onClose` fires once, peer death arrives as close, and in-flight SDK requests fail instead of hanging.
- No retries: a send that fails after dispatch is reported as `LinkClosedException`, whose outcome is ambiguous by definition.
- Authorisation: `CallerPolicy.DenyAll` is the default. `CallerPolicy.allowlist` needs package and signing certificate together; package alone is spoofable. Session count is capped.

## Not done / not proven

- No cross-process or on-device run. The Robolectric tests call the AIDL stubs in one process, so they do not exercise real Binder, linkToDeath, `getCallingUid()` or `PackageManager` lookups.
- Timeouts and cancellation rely on the SDK's request timeout and `close()`; there are no transport-level timeouts yet.
- Provider process recreation and repeated connect/disconnect on a device are untested.
- Message chunking above the frame limit.
- No permission-based gate is shipped; sensitive providers should add a signature-level permission on the service in addition to the policy.
