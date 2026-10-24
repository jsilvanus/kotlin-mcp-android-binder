# Kotlin MCP Android Binder

An Android-specific Binder IPC transport for the official Kotlin Model Context Protocol (MCP) SDK. The goal is to let separate Android apps exchange standard MCP messages without running a local HTTP server.

**Status:** planning / design phase. Not production-ready.

## Documentation

- [Project plan](docs/PLAN.md)
- [Roadmap](docs/ROADMAP.md)

## Direction

This project will implement a transport adapter around the official Kotlin MCP SDK. It will not fork or reimplement the SDK. Security, cancellation, lifecycle handling and cross-app integration tests are release requirements, not optional polish.

## License

Apache-2.0. See [LICENSE](LICENSE).
