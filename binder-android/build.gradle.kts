plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "fi.italeino.mcp.binder.android"
    compileSdk = 35

    defaultConfig {
        // 28 is where PackageInfo.signingInfo exists, which caller verification needs.
        minSdk = 28
    }
    buildFeatures { aidl = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    api(project(":binder-core"))
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
