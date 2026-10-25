plugins {
    kotlin("jvm")
}

val mcpSdkVersion = "0.15.0"

kotlin {
    // Binder transactions are hostile territory: keep the public surface deliberate.
    explicitApi()
    compilerOptions {
        // Android consumes this as Java 17 bytecode.
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    api("io.modelcontextprotocol:kotlin-sdk-client:$mcpSdkVersion")
    api("io.modelcontextprotocol:kotlin-sdk-server:$mcpSdkVersion")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.release.set(17)
}
