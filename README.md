# Kotlin MCP Android Binder

An Android-specific Binder IPC transport for the official Kotlin Model Context Protocol (MCP) SDK. The goal is to let separate Android apps exchange standard MCP messages without running a local HTTP server.

**Status:** M1 proof of concept in code, **experimental and not production-ready**. The JVM core and an in-process Android wiring test pass; no cross-process run on a device has happened yet.

## Documentation

- [Project plan](docs/PLAN.md)
- [Roadmap](docs/ROADMAP.md)
- [Design note: compatibility, modules, wire contract](docs/DESIGN.md)

## Direction

This project will implement a transport adapter around the official Kotlin MCP SDK. It will not fork or reimplement the SDK. Security, cancellation, lifecycle handling and cross-app integration tests are release requirements, not optional polish.

## Layout

- `binder-core`: Android-free transport, limits, caller policy, provider host. `./gradlew :binder-core:test`
- `binder-android`: AIDL, `McpProviderService`, `McpBinderClient`. Needs an Android SDK (`ANDROID_HOME`).
- `samples/provider-app`, `samples/client-app`: the cross-app demo. Install both (same signing key) and press the button in the client.

## Compatibility

| Component | Version |
|---|---|
| Official Kotlin MCP SDK | 0.15.0 (pinned) |
| Android | minSdk 28, compileSdk 35 |
| Kotlin / AGP / Gradle | 2.4.0 / 8.7.3 / 8.14 |

## License

Apache-2.0. See [LICENSE](LICENSE).
