package fi.italeino.mcp.binder.android

import android.os.Binder
import fi.italeino.mcp.binder.CallerIdentity
import fi.italeino.mcp.binder.ProviderHost

/** The AIDL entry point, split from the Service so it can be exercised without one. */
internal class ProviderBinder(
    private val host: () -> ProviderHost,
    private val identityOf: (uid: Int) -> CallerIdentity,
) : IMcpProvider.Stub() {
    override fun connect(client: IMcpEndpoint): IMcpEndpoint {
        // Must run on the transaction thread: getCallingUid() is meaningless anywhere else.
        val caller = identityOf(Binder.getCallingUid())
        val local = LocalEndpoint()
        val link = BinderLink(local, client)
        try {
            host().accept(caller, link)
        } catch (e: Exception) {
            link.close()
            throw e // ConnectionRefusedException is a SecurityException, which Binder carries to the caller
        }
        return local
    }
}
