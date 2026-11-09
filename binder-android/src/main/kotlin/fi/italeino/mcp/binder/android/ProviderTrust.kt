package fi.italeino.mcp.binder.android

import android.content.Context
import android.content.pm.PackageManager

/**
 * Which provider a client is willing to talk to. A package name alone proves nothing: a malicious
 * app installed first can claim any name that is not yet installed. The signing certificate is what
 * binds a package to its publisher, so every real policy needs one.
 *
 * Checked before binding and again once connected, so a provider replaced in between fails.
 */
public class ProviderTrust private constructor(
    private val expectedPackage: String?,
    private val certs: Set<String>?,
) {
    internal fun verify(pm: PackageManager, packageName: String): Boolean {
        if (certs == null) return true // AnyProvider: explicit opt-out
        if (packageName != expectedPackage) return false
        val actual = SigningCerts.of(pm, packageName)
        return actual.isNotEmpty() && actual.any { it in certs }
    }

    public companion object {
        /** Trust [packageName] only when signed with one of [signingCertSha256] (hex SHA-256 digests). */
        public fun pinned(packageName: String, signingCertSha256: Set<String>): ProviderTrust {
            require(signingCertSha256.isNotEmpty()) { "a pin needs at least one certificate digest" }
            return ProviderTrust(packageName, signingCertSha256.map { it.lowercase() }.toSet())
        }

        /** Trust [packageName] only if it is signed with the same key as the calling app, e.g. your own suite of apps. */
        public fun sameSignatureAs(context: Context, packageName: String): ProviderTrust =
            pinned(packageName, SigningCerts.of(context.packageManager, context.packageName))

        /** Skips provider verification. Development only: any app can claim a provider's component name. */
        public val AnyProvider: ProviderTrust = ProviderTrust(null, null)
    }
}
