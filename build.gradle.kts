plugins {
    kotlin("jvm") version "2.4.20"
    `java-gradle-plugin`
    `maven-publish`
}

group = "net.snobo"
// The version is never hard coded: pass it per release, for example -Pproject.version=0.1.0.
version = providers.gradleProperty("project.version")
    .orElse(providers.gradleProperty("version"))
    .getOrElse("0.0.0-DEV")

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // Keep the plugin loadable by Gradle 8.14 and newer, which still run on Java 17.
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(kotlin("test"))
}

gradlePlugin {
    plugins {
        create("resolution") {
            id = "net.snobo.resolution"
            implementationClass = "net.snobo.gradle.plugin.ReposiliteSettingsPlugin"
            displayName = "Reposilite resolution"
            description = "Registers a self hosted Reposilite instance as a dependency and plugin repository."
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

// Bootstrap publishing: this repository cannot apply the plugin it produces, because the classes of the
// plugin do not exist while this build is being configured, and no version of it is published yet. The
// hand written block below is therefore the bootstrap for the first release only: version 0.1.1 replaces
// it with the `net.snobo.publish` plugin once that plugin has its first release (see TODO.md).
val reposiliteBaseUrl = providers.gradleProperty("reposiliteUrl").getOrElse("http://127.0.0.1:8080")
val reposiliteRoute = if (version.toString().endsWith("-SNAPSHOT")) "snapshots" else "releases"

publishing {
    repositories {
        maven {
            name = "Reposilite"
            url = uri("$reposiliteBaseUrl/$reposiliteRoute")
            isAllowInsecureProtocol = reposiliteBaseUrl.startsWith("http://")
            val publisher = providers.gradleProperty("reposilitePublisher").orNull
            val token = providers.gradleProperty("reposiliteToken").orNull
            if (publisher != null && token != null) {
                credentials {
                    username = publisher
                    password = token
                }
            }
        }
    }
}
