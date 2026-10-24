# Roadmap

This roadmap is milestone-based. A milestone is complete only when its acceptance criteria are demonstrated in code and tests; dates are not commitments.

## M0 — Repository and design baseline

**Status: implemented; awaiting CI validation.**

- [x] Create standalone repository.
- [x] Select Apache-2.0.
- [x] Document scope, architecture, plan and roadmap.
- [x] Inspect the official Kotlin MCP SDK public Transport API.
- [x] Record compatibility decision: SDK 0.14.0, Kotlin 2.3.10, AGP 8.13.2, Gradle 8.13, minSdk 26.
- [x] Decide package name, module layout and AIDL contract placement in the library.
- [x] Document transport, identity, permission and lifecycle decisions.

**Exit criteria:** design note resolves API compatibility and module structure before implementation begins.

## M1 — Minimal cross-app proof of concept

**Status: implemented; awaiting CI validation.**

- [x] Create Android library module and AIDL service contract.
- [x] Implement client-side and provider-side Binder adapters for MCP JSON-RPC messages.
- [x] Wire adapters to the official SDK Transport interface without copying or forking SDK code.
- [x] Add sample provider exposing a harmless get_status tool.
- [x] Add separate client app and cross-app instrumentation test.
- [x] Test path initializes MCP, lists the tool, invokes it and checks the response.
- [x] Add CI build and emulator integration test workflow.

**Exit criteria:** MCP initialization and one tool call succeed across two apps using Binder, confirmed by green CI.

## M2 — Protocol correctness and lifecycle

- [ ] Verify request/response correlation, notifications and concurrent requests.
- [ ] Implement and test explicit timeout and cancellation semantics.
- [ ] Handle bind failure, disconnect, Binder death and provider/client process death comprehensively.
- [ ] Make close/reconnect paths idempotent and prevent resource leaks.
- [ ] Bound in-flight requests and add concurrency/back-pressure tests.
- [ ] Add malformed-message, timeout, cancellation, reconnect and repeated-connect tests.

**Exit criteria:** lifecycle failures produce bounded, documented errors and do not leak jobs, bindings or pending callbacks.

## M3 — Security hardening

- [ ] Define and review the full caller-authentication threat model.
- [x] Implement explicit service targeting in the client API.
- [x] Capture Binder caller identity synchronously and validate UID/package association.
- [x] Make provider base authorization deny-by-default.
- [x] Verify session methods are called by the opening UID.
- [ ] Add automated unauthorized-app, spoofed identity, malformed-input and callback-failure tests.
- [ ] Review logging and confirm sensitive arguments/results are never emitted by default.

**Exit criteria:** documented security model and automated negative tests; no reliance on a normal-level permission as the only security boundary.

## M4 — API and developer experience

- [ ] Stabilize public Kotlin API and error model.
- [ ] Provide production-oriented provider and consumer integration examples.
- [x] Document manifest/service declarations, permissions, API level and lifecycle limitations.
- [ ] Document full compatibility matrix and API compatibility policy.
- [ ] Add API compatibility checks if appropriate.

**Exit criteria:** a developer unfamiliar with the repository can integrate a provider and client from the documentation.

## M5 — Release candidate

- [ ] Run library unit tests and Android integration/instrumentation tests in CI.
- [ ] Test minimum supported API and a current Android API.
- [ ] Review dependency licences and notices.
- [ ] Audit queue bounds, cleanup, concurrency and security-sensitive code.
- [ ] Publish a pre-release artifact or documented local build.
- [ ] Tag a release candidate only after M1–M4 exit criteria pass.

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
