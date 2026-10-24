# Project plan

## Goal

Build a small, reusable Android-specific transport that lets separate Android applications exchange Model Context Protocol (MCP) JSON-RPC messages over Android Binder IPC, using the official Kotlin MCP SDK for MCP protocol and client/server behaviour.

The project is a transport adapter, not a competing MCP implementation. It must keep Android dependencies out of the official SDK and avoid coupling to Aidos or Agentic Messenger.

## Principles

- **Reuse the official SDK:** depend on its public transport and protocol APIs; do not fork or copy the SDK.
- **Keep scope narrow:** Binder transport and the minimum Android service integration needed to demonstrate it.
- **Secure by default:** an exported provider must not implicitly trust arbitrary callers. Document and test Android permissions, caller UID/package/signing identity, and provider-specific authorization.
- **Make lifecycle explicit:** binding, disconnection, Binder death, cancellation, timeout and process recreation are expected states, not exceptional afterthoughts.
- **Keep operations asynchronous:** do not run tool work on Binder transaction threads; define how requests and callbacks are correlated.
- **Be testable:** test protocol behaviour independently from Android lifecycle behaviour, then test the complete flow across two apps/processes.
- **Prefer interoperability:** use standard MCP JSON-RPC messages and semantics over a transport-specific tool API.
- **No claims of production readiness until proven:** documentation and release status must reflect actual test coverage.

## Initial architecture

1. **Official Kotlin MCP SDK** — owns MCP protocol types, client/server logic, tool schemas and protocol behaviour.
2. **Android Binder transport module** — implements/adapts the SDK transport contract and maps messages to/from Binder IPC.
3. **Provider service integration** — a small Android-facing API that exposes an MCP server through an explicitly declared service.
4. **Client integration** — binds to a known provider, connects an MCP client, sends messages and receives correlated responses.
5. **Examples** — separate provider and consumer applications to prove cross-app operation with both UIs closed after setup.

The first implementation should use the simplest reliable AIDL contract that supports asynchronous requests and responses. Avoid inventing a second MCP protocol or exposing provider internals. Keep the transport API independent of app-specific tool definitions.

## Security requirements

- Require explicit component targeting; do not rely on unrestricted implicit service discovery for privileged calls.
- Do not treat a manifest permission with protectionLevel=normal as sufficient authorization for sensitive tools.
- Capture Binder caller identity synchronously inside the Binder entry point, before dispatching work to coroutines or other threads.
- Validate caller UID and package association; where appropriate, verify signing certificates or use a documented allowlist/permission strategy.
- Fail closed when identity cannot be verified or authorization fails.
- Document that provider tools are capabilities: each provider remains responsible for authorizing sensitive operations and user confirmation.
- Never log credentials, sensitive arguments, or full tool payloads by default.
- Add tests for unauthorized callers, malformed messages, oversized input, repeated requests and callback failures.

## Reliability requirements

- Define request/response correlation and behaviour for JSON-RPC requests, responses and notifications.
- Define cancellation and timeout behaviour without leaving pending callbacks or jobs behind.
- Handle bind failure, connection loss, Binder death, client process death and provider process death.
- Ensure close is idempotent and releases bindings, channels, callbacks and coroutine jobs.
- Avoid unbounded queues or pending-request maps; set sensible limits and clean up every terminal path.
- Do not silently retry side-effecting tool calls. If a transport dies after dispatch, report an ambiguous outcome unless the operation has an explicit idempotency strategy.
- Test repeated connect/disconnect and provider process recreation.

## Compatibility and release policy

- Track supported Android API levels and official Kotlin MCP SDK versions in the README.
- Use a tested SDK version rather than assuming compatibility from compilation alone.
- Prefer semantic versioning once a public API exists. Keep the pre-1.0 API explicitly experimental.
- Keep the library Apache-2.0 licensed. Preserve third-party notices for any dependency or code legitimately reused.
- Do not copy implementation code from other projects unless ownership/licence provenance is clear and notices are preserved.

## Definition of done for the first useful release

- A client app can initialise an MCP session and call a sample tool in a separate provider app over Binder.
- Tool listing and at least one request/response path are covered by automated tests.
- Disconnect, timeout, cancellation and process death fail cleanly without leaked bindings or pending calls.
- Authorization is deny-by-default for protected sample tools and tested from an unauthorized caller.
- CI builds the library and sample apps and runs available unit/instrumentation tests.
- README explains setup, manifest declarations, permissions, lifecycle limits, supported versions and known limitations.
