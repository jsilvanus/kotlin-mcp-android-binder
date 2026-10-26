rootProject.name = "kotlin-mcp-android-binder"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

include(":binder-core")

// The Android modules need an Android SDK, which plain JVM environments do not have.
// binder-core carries all protocol, limit and authorisation logic and builds without it.
val androidSdk = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
if (androidSdk != null || file("local.properties").exists()) {
    include(":binder-android", ":samples:provider-app", ":samples:client-app")
}
