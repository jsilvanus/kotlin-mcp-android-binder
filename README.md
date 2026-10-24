# Kotlin MCP Android Binder

An Android-specific Binder IPC transport for the official Kotlin Model Context Protocol (MCP) SDK. It lets separate Android applications exchange standard MCP JSON-RPC messages without running a local HTTP server.

**Status:** M1 prototype. Not production-ready; lifecycle race coverage and security review remain.

## Documentation

- [Project plan](docs/PLAN.md)
- [Roadmap](docs/ROADMAP.md)
- [M0 design and compatibility decisions](docs/DESIGN.md)

## Modules

- binder: reusable Android library implementing the official SDK Transport interface and Binder session contracts.
- sample/provider: separate provider app exposing a harmless get_status tool.
- sample/client: consumer app with an instrumentation test that connects to the provider over Binder, initializes MCP, lists tools, and invokes the tool.

## Compatibility

The current design baseline pins the official Kotlin MCP SDK to 0.14.0, Kotlin 2.3.10, AGP 8.13.2, Gradle 8.13, Android API 26+ and JVM target 17. See DESIGN.md.

## Build

Requires JDK 17, Android SDK platform 36, and Gradle 8.13.

    gradle :binder:assembleDebug :binder:testDebugUnitTest
    gradle :sample:provider:assembleDebug :sample:client:assembleDebug

Run the cross-app test on an emulator with API 35. Install the provider APK first, then run:

    gradle :sample:client:connectedDebugAndroidTest

CI builds and installs the provider APK before running the consumer instrumentation test.

## Security notes

- Always bind to an explicit provider component.
- Exported providers must require an appropriate Android permission and implement provider-side authorization.
- The provider base class denies callers by default.
- Tool providers must authorize each sensitive capability independently. Listing a tool does not grant permission to call it.
- Do not expose sensitive tools through a broadly accessible exported service.

## Direction

This project is a transport adapter, not a competing MCP implementation. It reuses the official SDK protocol types and client/server behaviour. The first version uses bounded JSON strings over AIDL; large binary payloads, transport-specific routing and app-specific agent policy are out of scope.

## License

Apache-2.0. See LICENSE.
