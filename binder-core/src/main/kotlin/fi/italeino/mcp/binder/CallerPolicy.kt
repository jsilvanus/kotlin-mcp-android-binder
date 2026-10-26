package fi.italeino.mcp.binder

/**
 * Who is on the other end of a Binder call, as Android reports it.
 *
 * Captured synchronously inside the Binder entry point (`Binder.getCallingUid()` is only valid on
 * the transaction thread) and then passed around as plain data.
 */
public data class CallerIdentity(
    val uid: Int,
    /** Packages sharing [uid]. Empty means the lookup failed, which policies must treat as unverified. */
    val packages: Set<String>,
    /** Lower-case hex SHA-256 digests of the signing certificates of [uid]'s package. Empty means unverified. */
    val signingCertSha256: Set<String>,
)

/** Decides whether a caller may open an MCP session. Never rely on a `normal`-level permission alone. */
public fun interface CallerPolicy {
    public fun isAllowed(caller: CallerIdentity): Boolean

    public companion object {
        /** The default: nobody gets in until the provider says so. */
        public val DenyAll: CallerPolicy = CallerPolicy { false }

        /**
         * Allows a caller only if it presents one of [allowed]'s signing certificates for one of its
         * packages. A package name alone is spoofable by anything installed first, so it never suffices.
         * Fails closed when identity is incomplete.
         */
        public fun allowlist(allowed: Map<String, Set<String>>): CallerPolicy {
            val normalized = allowed.mapValues { (_, certs) -> certs.map { it.lowercase() }.toSet() }
            return CallerPolicy { caller ->
                caller.packages.any { pkg ->
                    val certs = normalized[pkg] ?: return@any false
                    certs.isNotEmpty() && caller.signingCertSha256.any { it in certs }
                }
            }
        }
    }
}
