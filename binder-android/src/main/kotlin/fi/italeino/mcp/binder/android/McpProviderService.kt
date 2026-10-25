package fi.italeino.mcp.binder.android

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import fi.italeino.mcp.binder.CallerIdentity
import fi.italeino.mcp.binder.ProviderHost
import java.security.MessageDigest

/**
 * Base class for an app that serves MCP over Binder. Subclass, supply [host], declare the service
 * with an explicit `android:exported` and, for anything sensitive, a signature-level permission.
 *
 * Authorisation is [ProviderHost]'s `CallerPolicy`, which denies everyone unless the provider
 * configured it. The identity is captured here, on the Binder thread, before anything is dispatched.
 */
public abstract class McpProviderService : Service() {
    protected abstract val host: ProviderHost

    private val provider = ProviderBinder(this::host, ::callerIdentity)

    override fun onBind(intent: Intent?): IBinder = provider

    private fun callerIdentity(uid: Int): CallerIdentity {
        val packages = packageManager.getPackagesForUid(uid)?.toSet().orEmpty()
        return CallerIdentity(uid, packages, packages.flatMapTo(mutableSetOf()) { SigningCerts.of(packageManager, it) })
    }
}

/** Signing-certificate lookup shared by the service and by providers building their own policy. */
public object SigningCerts {
    /** Lower-case hex SHA-256 digests of [packageName]'s signers; empty if unknown or unreadable. */
    public fun of(pm: PackageManager, packageName: String): Set<String> = runCatching {
        val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        info.signingInfo?.apkContentsSigners.orEmpty().map { signer ->
            MessageDigest.getInstance("SHA-256").digest(signer.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }.getOrDefault(emptySet())
}
