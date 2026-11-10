# Dependencies and licences

Reviewed for the release candidate. The library itself is Apache-2.0.

| Dependency | Used by | Licence |
|---|---|---|
| io.modelcontextprotocol:kotlin-sdk-client / -server 0.15.0 | binder-core (api) | Apache-2.0 |
| org.jetbrains.kotlinx:kotlinx-coroutines-core 1.10.2 | binder-core (api) | Apache-2.0 |
| kotlinx-serialization, ktor (transitive via the SDK) | binder-core | Apache-2.0 |
| Kotlin stdlib | all | Apache-2.0 |
| junit, robolectric, androidx.test (test only) | tests | EPL-1.0 / MIT / Apache-2.0 |

No copyleft dependency is shipped. The transitive tree of the SDK should be re-checked on every SDK bump (`./gradlew :binder-core:dependencies --configuration runtimeClasspath`).

## Audit notes (queues, cleanup, concurrency)

- Inbound: bounded queue (64) with reader-thread backpressure; frame length checked before allocation.
- Writes are serialised by one lock; a stalled peer is cut by the send timeout.
- Every terminal path closes the link, unbinds, unlinks the death recipient, and fires `onClose` once.
- Reader threads are daemon threads, one per session, ended by `shutdown` on close.
