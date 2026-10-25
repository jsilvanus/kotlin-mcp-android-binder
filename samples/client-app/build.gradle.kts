plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "fi.italeino.mcp.sample.client"
    compileSdk = 35
    defaultConfig {
        applicationId = "fi.italeino.mcp.sample.client"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.0.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":binder-android"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
