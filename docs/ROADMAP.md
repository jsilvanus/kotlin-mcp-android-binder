# Roadmap

This roadmap is intentionally milestone-based. A milestone is complete only when its acceptance criteria are demonstrated in code and tests; dates are not commitments.

## M0 — Repository and design baseline

**Status: in progress**

- [x] Create standalone repository.
- [x] Select Apache-2.0.
- [ ] Document scope, architecture, plan and roadmap.
- [ ] Inspect the official Kotlin MCP SDK's current public Transport API and supported Android/JVM targets.
- [ ] Record a compatibility decision: SDK version, minimum Android API, Kotlin/AGP versions.
- [ ] Decide the public package name, module layout and whether the initial AIDL contract belongs in the library or in a host app.

**Exit criteria:** a short design note resolves API compatibility and module structure before implementation begins.

## M1 — Minimal cross-app proof of concept

- [ ] Create the Android library module and a minimal AIDL service contract.
- [ ] Implement client-side and provider-side Binder adapters for MCP JSON-RPC messages.
- [ ] Wire the adapters into the official SDK without copying or forking SDK code.
- [ ] Add a sample provider exposing a harmless get_status tool.
- [ ] Add a separate client app that discovers/connects to the provider and invokes the tool.
- [ ] Demonstrate operation across separate application processes.
- [ ] Add a basic happy-path integration test and build CI.

**Exit criteria:** MCP initialisation and one tool call succeed across two apps using Binder.

## M2 — Protocol correctness and lifecycle

- [ ] Verify request/response correlation, notifications and concurrent requests.
- [ ] Implement explicit timeouts and cancellation semantics.
- [ ] Handle bind failure, disconnect, Binder death and provider/client process death.
- [ ] Make close/reconnect paths idempotent and prevent resource leaks.
- [ ] Bound in-flight requests and message sizes; clean up pending state on every exit path.
- [ ] Add malformed-message, timeout, cancellation, reconnect and repeated-connect tests.

**Exit criteria:** lifecycle failures produce bounded, documented errors and do not leak jobs, bindings or pending callbacks.

## M3 — Security hardening

- [ ] Define the supported caller-authentication model and threat model.
- [ ] Enforce explicit service targeting and safe exported-service configuration.
- [ ] Verify Binder caller identity and package/signature association where applicable.
- [ ] Make authorization deny-by-default for protected providers.
- [ ] Define provider-level authorization for sensitive tools and user-confirmation requirements.
- [ ] Add tests for unauthorized apps, spoofed/mismatched identity, malformed input and callback failure.
- [ ] Review logs to ensure sensitive tool arguments and results are not emitted by default.

**Exit criteria:** documented security model and automated negative tests; no reliance on a normal-level permission as the only security boundary.

## M4 — API and developer experience

- [ ] Stabilize the public Kotlin API and error model.
- [ ] Provide minimal setup examples for provider and consumer apps.
- [ ] Document manifest/service declarations, permissions, API-level restrictions and process lifecycle.
- [ ] Add a sample with a side-effect-free tool and explain how providers should protect side-effecting tools.
- [ ] Document limitations and compatibility matrix.
- [ ] Add API compatibility checks if appropriate.

**Exit criteria:** a developer unfamiliar with the repository can integrate a provider and client from the documentation.

## M5 — Release candidate

- [ ] Run library unit tests and Android integration/instrumentation tests in CI.
- [ ] Test on at least the minimum supported API and a current Android API.
- [ ] Review dependency licences and notices.
- [ ] Audit queue bounds, cleanup, concurrency and security-sensitive code.
- [ ] Publish a pre-release artifact or documented local build.
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
