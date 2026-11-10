# Design note (M0): compatibility and structure

## Official SDK

- Artifact: `io.modelcontextprotocol:kotlin-sdk-client` and `kotlin-sdk-server`, **pinned to 0.15.0** (Aidos `mcp-core` uses the same version). The SDK is pre-1.0 and import paths have moved before, so bumps are deliberate and run the full test suite.
- Android uses the SDK's JVM artifact; there is no Android-specific artifact. Verified here: client and server both run on the Robolectric (Android) runtime and on a plain JVM.
- Contract implemented: `Transport` (`start`, `send`, `close`, `onMessage`, `onError`, `onClose`) via `AbstractTransport`. Server side uses `Server.createSession(transport)`, client side `Client.connect(transport)`.
- `ReadBuffer.deserializeMessage` is `internal` in 0.15.0, so decoding uses the public `McpJson` with `JSONRPCMessage.serializer()`; encoding uses the public `serializeMessage`. One frame is one JSON-RPC message (length-prefixed on the socket).

## Toolchain

Kotlin 2.4.0, Gradle 8.14, AGP 8.7.3, Java 17 bytecode, `minSdk` 28 (first API with `PackageInfo.signingInfo`, needed for certificate verification), `compileSdk` 35.

## Modules

| Module | Android? | Contents |
|---|---|---|
| `binder-core` | no | `BinderTransport`, the `Link` seam, `StreamLink`, limits, `CallerPolicy`, `ProviderHost`. All protocol, limit, admission and lifecycle logic, tested on a JVM against the real SDK. |
| `binder-android` | yes | The AIDL contract, `McpProviderService`, `McpBinderClient`, `ProviderTrust`, `SigningCerts`. Thin: it only hands over a socket as a `Link` and captures caller identity. |
| `samples:provider-app`, `samples:client-app` | yes | Two apps proving the flow with a harmless `get_status` tool. |

The AIDL contract lives in the library (not a host app) so providers and consumers share one definition.

## Wire contract

```
interface IMcpProvider { ParcelFileDescriptor connect(); }
```

Binder only bootstraps the session. The client binds an explicitly named service and calls `connect()`. The provider reads `Binder.getCallingUid()` synchronously inside it, asks its `CallerPolicy`, creates a socket pair (`ParcelFileDescriptor.createSocketPair()`), keeps one end and returns the other. All MCP messages then travel over that socket as length-prefixed frames (4-byte big-endian length, then UTF-8 JSON-RPC), implemented by `StreamLink` in `binder-core`.

Why a socket instead of one Binder transaction per message:

| | Per-message oneway Binder | Socket pair (this repo) |
|---|---|---|
| Size | ~1 MiB shared buffer per process, `TransactionTooLargeException` | No transaction limit; a 10 MiB tool result is a device test |
| Backpressure | None; a flood of oneway calls exhausts the buffer | Kernel socket buffer blocks the writer; the reader blocks while the transport is saturated |
| Death | `linkToDeath` only | Socket EOF (the kernel closes a dead process's descriptors) plus `linkToDeath` on the client |

Binder is still what supplies the verified caller UID, the explicit service target and process priority. Verified on API 36 (emulator) between two `untrusted_app` processes; other API levels are untested.

## Behaviour

- Size: the length prefix is checked against `maxFrameBytes` (default 16 MiB) before any allocation; an oversize or negative length closes the link. Outbound oversize is refused locally.
- Backpressure: `StreamLink` has a dedicated reader thread that waits while the transport's bounded inbound queue (64) is full. Links without such a thread (in-memory test links) close the transport on overflow instead.
- Send timeout: a send blocked for `sendTimeoutMillis` (default 30 s) means the peer stopped reading; the transport closes the link, which fails the send and all pending requests.
- Close: idempotent, `onClose` fires once, peer death arrives as close, and in-flight SDK requests fail instead of hanging (device-tested by killing the provider mid-call).
- Connect: `McpBinderClient.connect` has a timeout (default 10 s) and fails with `ProviderUnavailableException` for a missing, unexported, refusing, untrusted or dying provider. Calling it again is the reconnect path; each call is an independent session.
- No retries: a send that fails after dispatch is reported as `LinkClosedException`, whose outcome is ambiguous by definition.
- Per-request timeouts and cancellation are the SDK's (`RequestOptions`, `notifications/cancelled`); an in-flight request cap is enforced by the transport.

## Security model (M3)

Threats considered, and what stops each:

| Threat | Mitigation |
|---|---|
| Unauthorised app connects to a provider | `CallerPolicy`, deny by default. The identity comes from `getCallingUid()` captured on the Binder thread, resolved to packages and signing certificates. The allowlist needs package and certificate together. Device-tested with a provider that allows nobody. |
| Malicious app claims a provider's package or component name | The client takes an explicit `ComponentName` (never an implicit intent) and a mandatory `ProviderTrust`: the package must be signed with a pinned certificate, checked before binding and again after connecting. `ProviderTrust.AnyProvider` is the visible opt-out. Device-tested with a wrong pin. |
| Hostile peer exhausts memory | Frame length checked before allocation; bounded inbound queue with backpressure; in-flight request cap; session cap on the provider. |
| Stalled peer blocks us | Send timeout closes the link. |
| Provider or client dies mid-call | Close surfaces to the SDK; pending requests fail. Side-effecting calls are never retried. |
| Sensitive data in logs | The library logs nothing. |

Residual risks, deliberately not covered here:

- Same-UID code (anything in the provider's own app) is inside the trust boundary.
- A provider reinstalled with the same key is trusted: the pin is the key, not the version.
- The window between the second pin check and the first frame is small but not zero.
- `hello`-style agent identity and per-tool authorisation are the provider's job (`Server` tools), not the transport's.
- A `normal`-level permission is not a security boundary. Providers with sensitive tools should also declare a signature-level permission on the service.

## Tests

- `binder-core` (JVM): protocol, limits, policy, and `StreamLink` over real loopback sockets, including a 10 MiB frame, ordering, a lying length prefix, peer disappearance and a stalled peer.
- `samples:client-app` `connectedDebugAndroidTest` (device or emulator, provider app installed first): cross-process call, 10 MiB result, concurrent requests, 25 reconnects, provider killed mid-call then reconnect, refusal by the provider, wrong client pin, missing provider.

## Not done / not proven

- Only API 36 has been run. API 28 to 35 are untested.
- A third-party app with a different signing key was not used as the denied caller; refusal is tested with an allowlist that names another package.
- Provider-initiated reconnect and idle unbinding are not implemented.
- No permission-based gate is shipped on the samples.
