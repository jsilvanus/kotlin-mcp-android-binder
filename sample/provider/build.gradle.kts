plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "dev.aidos.mcp.sample.provider"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.aidos.mcp.sample.provider"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":binder"))
    implementation("io.modelcontextprotocol:kotlin-sdk-server:0.14.0")
}
