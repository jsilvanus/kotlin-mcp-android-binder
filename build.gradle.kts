import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

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
    // CI passes -PbinderVersion=0.1.0-build.N+sha on every push to main. Local builds keep SNAPSHOT.
    version = (findProperty("binderVersion") as String?) ?: "0.1.0-SNAPSHOT"
}

subprojects {
    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension> {
            repositories {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/jsilvanus/kotlin-mcp-android-binder")
                    credentials {
                        username = System.getenv("GITHUB_ACTOR") ?: "token"
                        password = System.getenv("GITHUB_TOKEN") ?: ""
                    }
                }
            }
        }
        extensions.configure<PublishingExtension> {
            publications.withType<MavenPublication>().configureEach {
                pom {
                    name.set(artifactId)
                    description.set("Binder transport for standard MCP between Android apps (fi.italeino.mcp).")
                    url.set("https://github.com/jsilvanus/kotlin-mcp-android-binder")
                    licenses {
                        license {
                            name.set("Apache License 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    scm {
                        url.set("https://github.com/jsilvanus/kotlin-mcp-android-binder")
                        connection.set("scm:git:https://github.com/jsilvanus/kotlin-mcp-android-binder.git")
                    }
                }
            }
        }
    }
}
