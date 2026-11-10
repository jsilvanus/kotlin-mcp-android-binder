package fi.italeino.mcp.binder.android

import android.os.Binder
import android.os.ParcelFileDescriptor
import fi.italeino.mcp.binder.CallerIdentity
import fi.italeino.mcp.binder.ProviderHost

/** The AIDL entry point, split from the Service so it can be exercised without one. */
internal class ProviderBinder(
    private val host: () -> ProviderHost,
    private val identityOf: (uid: Int) -> CallerIdentity,
    private val maxFrameBytes: Int,
) : IMcpProvider.Stub() {
    override fun connect(): ParcelFileDescriptor {
        // Must run on the transaction thread: getCallingUid() is meaningless anywhere else.
        val caller = identityOf(Binder.getCallingUid())
        val (providerEnd, clientEnd) = ParcelFileDescriptor.createSocketPair().let { it[0] to it[1] }
        try {
            host().accept(caller, socketLink(providerEnd, maxFrameBytes, "mcp-provider-${caller.uid}"))
        } catch (e: Exception) {
            runCatching { providerEnd.close() }
            runCatching { clientEnd.close() }
            throw e // ConnectionRefusedException is a SecurityException, which Binder carries to the caller
        }
        return clientEnd // closed by Binder once written to the reply
    }
}
