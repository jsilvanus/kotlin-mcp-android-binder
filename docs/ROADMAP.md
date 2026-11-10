# Roadmap

This roadmap is intentionally milestone-based. A milestone is complete only when its acceptance criteria are demonstrated in code and tests; dates are not commitments.

## M0 — Repository and design baseline

**Status: done** (see DESIGN.md)

- [x] Create standalone repository.
- [x] Select Apache-2.0.
- [x] Document scope, architecture, plan and roadmap.
- [x] Inspect the official Kotlin MCP SDK's current public Transport API and supported Android/JVM targets.
- [x] Record a compatibility decision: SDK version, minimum Android API, Kotlin/AGP versions.
- [x] Decide the public package name, module layout and whether the initial AIDL contract belongs in the library or in a host app.

**Exit criteria:** a short design note resolves API compatibility and module structure before implementation begins.

## M1 — Minimal cross-app proof of concept

**Status: done** (demonstrated on an API 36 emulator across two apps)

- [x] Create the Android library module and a minimal AIDL service contract.
- [x] Implement client-side and provider-side Binder adapters for MCP JSON-RPC messages.
- [x] Wire the adapters into the official SDK without copying or forking SDK code.
- [x] Add a sample provider exposing a harmless get_status tool.
- [x] Add a separate client app that discovers/connects to the provider and invokes the tool.
- [x] Demonstrate operation across separate application processes (emulator, API 36).
- [x] Build CI, and a happy-path test over the real SDK (JVM and Robolectric, in-process).
- [x] Cross-app instrumentation tests on an emulator (`samples:client-app` `connectedDebugAndroidTest`).

**Exit criteria:** MCP initialisation and one tool call succeed across two apps using Binder.

## M2 — Protocol correctness and lifecycle

**Status: done on API 36; other API levels untested**

- [x] Verify request/response correlation and concurrent requests (JVM).
- [x] Implement explicit timeouts and cancellation semantics (connect timeout, send timeout, SDK per-request timeout and cancellation).
- [x] Handle bind failure, disconnect, Binder death and provider/client process death.
- [x] Idempotent close, peer death surfaces as close (JVM). Reconnect on a device: done (25 repeated connects, reconnect after provider kill).
- [x] Bound message size and inbound queue; closing on every terminal path (JVM). In-flight request cap done (`TransportLimits.maxInFlightRequests`, refused with a JSON-RPC error, JVM test); send timeout done. Frame limit is now 16 MiB over a socket instead of Binder's ~1 MiB.
- [x] Add malformed-message, timeout, reconnect and repeated-connect tests. (Cancellation relies on the SDK.)

**Exit criteria:** lifecycle failures produce bounded, documented errors and do not leak jobs, bindings or pending callbacks.

## M3 — Security hardening

**Status: done for the documented model; see DESIGN.md. Not done: a real third-party signer as denied caller, API levels below 36.**

- [x] Define the supported caller-authentication model and threat model.
- [x] Enforce explicit service targeting and safe exported-service configuration.
- [x] Verify Binder caller identity and package/signature association where applicable.
- [x] Make authorization deny-by-default for protected providers.
- [x] Define provider-level authorization for sensitive tools and user-confirmation requirements.
- [x] Add tests for unauthorized apps, spoofed/mismatched identity, malformed input and callback failure.
- [x] Review logs to ensure sensitive tool arguments and results are not emitted by default.

**Exit criteria:** documented security model and automated negative tests; no reliance on a normal-level permission as the only security boundary.

## M4 — API and developer experience

**Status: done** (docs/INTEGRATION.md, API dumps checked in CI).

- [x] Stabilize the public Kotlin API and error model.
- [x] Provide minimal setup examples for provider and consumer apps.
- [x] Document manifest/service declarations, permissions, API-level restrictions and process lifecycle.
- [x] Add a sample with a side-effect-free tool and explain how providers should protect side-effecting tools.
- [x] Document limitations and compatibility matrix.
- [x] Add API compatibility checks if appropriate.

**Exit criteria:** a developer unfamiliar with the repository can integrate a provider and client from the documentation.

## M5 — Release candidate

**Status: mostly done; the API 28 and 34 CI device jobs have not run yet (they need a push to GitHub).**

- [x] Run library unit tests and Android integration/instrumentation tests in CI.
- [ ] Test on at least the minimum supported API and a current Android API.
- [x] Review dependency licences and notices.
- [x] Audit queue bounds, cleanup, concurrency and security-sensitive code.
- [x] Publish a pre-release artifact or documented local build.
- [ ] Tag a release candidate only after all M1–M4 exit criteria pass.

**Exit criteria:** reproducible build, green CI, documented limitations and a reviewable release candidate.

## M6 — First stable release

- [ ] Resolve release-candidate defects.
- [ ] Confirm supported official Kotlin MCP SDK versions with integration tests.
- [ ] Publish artifacts and release notes.
- [ ] Adopt semantic versioning and a compatibility policy.
- [ ] Decide whether to propose the Android-specific transport upstream to the official Kotlin MCP SDK.

**Exit criteria:** users can consume a versioned library and follow documented compatibility guarantees.

## Later / optional

- [ ] Additional transport conveniences only where real users need them.
- [ ] Better provider discovery, without weakening explicit authorization.
- [ ] Multi-provider routing helpers as a separate layer if they prove useful.
- [ ] Upstream contribution proposal to the official Kotlin MCP SDK.
- [ ] Evaluate community adoption and maintenance burden before adding advanced features.

## Explicit non-goals for the initial release

- Reimplementing MCP client/server/protocol logic.
- Forking the official Kotlin MCP SDK.
- Building an Android-wide agent, tool marketplace or privileged system service.
- Automatically granting permission to execute every tool a provider exposes.
- Guaranteeing that Android keeps a provider process alive indefinitely.
- Automatic retry of potentially side-effecting tool calls after an ambiguous failure.
- Bundling Aidos- or Agentic Messenger-specific business logic into the transport.
