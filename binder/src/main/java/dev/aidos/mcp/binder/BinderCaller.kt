package dev.aidos.mcp.binder

/**
 * Identity observed at the provider's Binder entry point.
 * UID and package names are derived locally, not accepted as caller-supplied claims.
 */
public data class BinderCaller(
    public val uid: Int,
    public val packageNames: Set<String>,
)
