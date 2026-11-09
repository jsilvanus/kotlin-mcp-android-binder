plugins {
    kotlin("jvm") version "2.4.0" apply false
    kotlin("android") version "2.4.0" apply false
    id("com.android.library") version "8.7.3" apply false
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.17.0"
}

apiValidation {
    // Only the libraries are public API; the samples are not.
    ignoredProjects.addAll(listOf("provider-app", "client-app", "samples"))
    // Generated from IMcpProvider.aidl. The AIDL is the frozen wire contract (see docs/DESIGN.md), not Kotlin API.
    ignoredClasses.addAll(listOf("fi.italeino.mcp.binder.android.IMcpProvider", "fi.italeino.mcp.binder.android.IMcpProvider\$Default", "fi.italeino.mcp.binder.android.IMcpProvider\$Stub", "fi.italeino.mcp.binder.android.IMcpProvider\$_Parcel"))
}

allprojects {
    group = "fi.italeino.mcp"
    version = "0.1.0-SNAPSHOT"
}
