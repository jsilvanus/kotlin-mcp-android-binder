# Design baseline (M0)

## Compatibility decision

- Official MCP Kotlin SDK: **0.14.0**, using the public Transport contract from kotlin-sdk-core.
- Kotlin: **2.3.10**.
- Android Gradle Plugin: **8.13.2**; Gradle: **8.13**.
- Android minSdk: **26**; compileSdk: **36**; sample targetSdk: **35**.
- JVM bytecode target: **17**.
- Public package: dev.aidos.mcp.binder.
- Module layout: binder Android library, plus independent sample provider and sample client applications.
- License: Apache-2.0 for this project. The MCP SDK is a separate MIT-licensed dependency; its source is not copied here.

The SDK is pinned rather than using a floating version. Upgrade only with compilation and cross-app integration tests.

## Transport contract

The library implements the SDK Transport interface and sends standard MCP JSON-RPC messages serialized with the SDK McpJson. AIDL is only a message transport; it does not define MCP methods, tool schemas, or a second protocol.

- IMcpBinderProvider.openSession(callback) creates one isolated session.
- IMcpBinderSession.sendMessage(json) carries client-to-provider JSON-RPC messages.
- IMcpBinderCallback.onMessage(json) carries provider-to-client messages.
- Message and close callbacks are oneway AIDL calls so tool execution is not performed on a synchronous Binder reply path.
- A separate transport instance and server session are created per caller session.

The first version uses one JSON message per Binder call. Messages are UTF-8-size limited (256 KiB by default). Chunking and streaming large payloads are out of scope; MCP content should reference external resources for large data.

## Security model

- The client binds to an explicit ComponentName; implicit discovery is not implemented.
- The provider base class denies all callers unless a subclass explicitly authorizes them.
- The provider captures Binder.getCallingUid() inside openSession, then resolves package names for that UID. Caller-supplied package-name claims are never accepted.
- Every session Binder method checks that the caller UID matches the UID that opened the session.
- Providers should declare an exported-service permission with protectionLevel="signature" where consumers share a signing identity. If apps use different signatures, use an explicit, stronger deployment-specific permission and authorization strategy.
- Provider-level authorization remains mandatory; an Android permission is an additional boundary, not a replacement for per-tool policy.
- The sample only authorizes the known sample-client package and uses a signature permission.
- The base library does not treat MCP tool listing as authorization to execute a tool.

## Lifecycle and error model

- start() binds with a finite timeout.
- close() is idempotent, releases the binding, and closes the remote session.
- Provider observes client callback Binder death and closes the session.
- Unexpected disconnection closes the local transport and invokes its close callback.
- Malformed JSON and oversize messages are reported through the transport error callback; oversize inbound messages close the session.
- Side-effecting requests are never automatically retried after an ambiguous Binder failure.
- This is a first prototype. Further work is needed around concurrent start calls, request cancellation semantics, back-pressure, reconnect policy, and more exhaustive race/failure tests before production use.

## M1 proof

The provider sample exposes a harmless get_status tool returning binder-ok. The consumer instrumentation test runs against the separately installed provider app, performs MCP initialization, lists tools, calls the tool, and verifies the result. CI builds both apps and runs this test in an Android emulator.
