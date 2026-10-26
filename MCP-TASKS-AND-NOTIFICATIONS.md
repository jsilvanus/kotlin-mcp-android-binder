# MCP Tasks and Notifications: Client-Side Design Notes

**Status:** Design guidance for the Kotlin MCP Android Binder transport  
**Last reviewed:** 2026-10-26  
**Scope:** Aidos/Kotlin MCP client behaviour across Binder and other MCP transports

## Executive summary

Aidos should treat asynchronous task handling as a first-class client capability, while remaining interoperable with MCP servers that only support ordinary synchronous tool calls.

Keep three concepts separate:

1. **Tool-call result:** a tool call may finish immediately, or use the MCP Tasks extension to return a task handle.
2. **Progress:** progress notifications describe work during an operation; they are not the same as a durable task's lifecycle state.
3. **Task lifecycle:** task operations let a client inspect a task, obtain its result, request cancellation, and respond to a task's input requirement where the extension and implementation support it.

Do not require every tool to accept a `taskId` argument. Task handling is protocol-level behaviour, not a universal parameter added to every tool schema. A normal tool call need not create a task.

## Client behaviour

The client abstraction should cover the following operations, mapping them to the official protocol/SDK APIs where supported:

- **Call tool:** invoke a tool and handle either an immediate tool result or a task handle.
- **Get task:** retrieve the status and result for a known task ID.
- **Wait/observe:** receive task updates when the server and SDK support the applicable notification/subscription mechanism; otherwise poll with a bounded interval and timeout policy.
- **Supply input:** handle an `input_required` state through the extension-defined update/input mechanism, subject to authorization and user approval policy.
- **Cancel:** request cancellation and report the observed outcome accurately. Cancellation is cooperative; a request does not prove that execution stopped.
- **Reconnect/recover:** retain the remote task reference when appropriate and resume observation after reconnecting. Do not promise durable recovery unless the server persists task state and results.
- **Capability handling:** detect which protocol capabilities/extensions are available. Never assume every MCP server or SDK implements Tasks or every notification mechanism.

A useful internal API shape (illustrative, not an MCP wire format) is:

```kotlin
sealed interface ToolExecution {
    data class ImmediateResult(val result: CallToolResult) : ToolExecution
    data class StartedTask(val taskId: String) : ToolExecution
}

interface TaskClient {
    suspend fun getTask(taskId: String): TaskSnapshot
    suspend fun waitForChange(taskId: String, timeoutMillis: Long): TaskSnapshot
    suspend fun provideInput(taskId: String, input: TaskInput)
    suspend fun cancelTask(taskId: String): CancelRequestOutcome
}
```

Adapt the types to the SDK and project conventions; do not invent new wire methods where standard MCP methods exist. Keep local Aidos tasks distinct from remote MCP task handles because ownership, persistence, authorization, and cancellation semantics differ.

## Notifications: distinguish the channels

| Kind | Purpose | Client behaviour |
|---|---|---|
| Progress notification | Reports progress associated with an operation, often correlated by a progress token | Update progress UI; do not treat it as proof of task completion |
| Task lifecycle/status update | Reports a task state change when the Tasks extension and implementation support notifications/subscriptions | Update cached state; still support polling/reconciliation |
| Tool/resource/list-change notification | Signals that available tools or resources have changed | Refresh the relevant discovery/cache state |
| Logging notification | Delivers server log messages at the negotiated level | Route to diagnostics; avoid exposing sensitive data |

Notifications are not interchangeable. In particular, ordinary progress reporting does not imply that a server implements the Tasks extension.

## Polling and push strategy

Use notifications when the server/SDK supports them, but do not make correctness depend on receiving them. Notifications can be unavailable, lost across disconnections, or unsupported by a particular implementation.

Recommended observer strategy:

1. Start the tool call and inspect whether the response is an immediate result or task handle.
2. If a task handle is returned, register/subscribe for updates if supported.
3. Reconcile with the authoritative task state using the standard task-get operation.
4. If push updates are unsupported or the connection is interrupted, poll with backoff and a deadline.
5. On a notification, update the local view and reconcile as needed; treat duplicate or out-of-order updates defensively.
6. Stop observation when a terminal state is confirmed, or surface timeout/unknown state explicitly.

Do not poll aggressively. Use bounded backoff, cancellation of the observer when no longer needed, and clear behaviour for transient transport failures.

## SDK and protocol support caveat

The official Kotlin SDK is the preferred baseline; this project should wrap it rather than fork it. However, support for general MCP notifications, task-related protocol types, and the full Tasks extension are separate claims. The presence of task data types or wire-level metadata does not by itself prove that the complete client workflow (task start/get/input/cancel plus subscriptions/notifications) is implemented.

Before relying on an SDK version, verify the actual API and tests for each operation required by this project. Where an operation is missing, isolate any compatibility implementation behind the adapter and document its tested protocol revision. Avoid assuming support solely from the SDK's version number.

Primary references to verify during implementation:

- Official Kotlin SDK: https://github.com/modelcontextprotocol/kotlin-sdk
- MCP specification repository: https://github.com/modelcontextprotocol/modelcontextprotocol
- Tasks extension specification: https://github.com/modelcontextprotocol/ext-tasks
- MCP enhancement proposal tracker: https://github.com/modelcontextprotocol/modelcontextprotocol/tree/main/seps

Protocol revisions, proposals, and SDK implementations change independently. Re-check the current specification and SDK tests before claiming conformance; proposals are not stable protocol guarantees.

## Security and lifecycle requirements

- A task ID is a reference, not authorization. Authorize every get, input/update, and cancel operation against the caller and task owner.
- Do not let an Android Binder client access another app's task merely by guessing or receiving its ID.
- Bind task handles to the authenticated/authorized calling identity and enforce the project's two-sided app/tool authorization policy.
- Do not expose secrets or sensitive tool output in progress notifications or logs.
- Handle Binder client death and service process restart explicitly. A Binder connection lifecycle must not accidentally define the lifetime of remote work.
- Distinguish “cancel requested” from “cancelled” in APIs and UI.
- Define cleanup/retention for cached task snapshots and results.

## Implementation checklist

- [ ] Inspect the pinned official Kotlin SDK version and identify the exact APIs for tool-call task handles.
- [ ] Verify task get, result retrieval, cancellation, and input-required/update handling.
- [ ] Verify task notification/subscription support separately from generic progress notifications.
- [ ] Add protocol capability detection and a synchronous-tool fallback.
- [ ] Add polling fallback with backoff and terminal-state handling.
- [ ] Test disconnection/reconnection, duplicate notifications, server restart, and cancellation races.
- [ ] Add authorization tests for cross-app Binder callers and task IDs.
- [ ] Document the tested protocol revision and SDK version in release notes.

## Decision

Implement a stable internal task-observation abstraction in the Kotlin Binder transport, but keep MCP wire behaviour aligned with the official SDK/specification. Support both push updates and polling, and preserve correct behaviour when a remote server supports only synchronous tool calls.
