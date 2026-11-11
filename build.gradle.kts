plugins {
    kotlin("jvm") version "2.4.20"
    `java-gradle-plugin`
    // The shared net.snobo style: `ktlintCheck` runs inside `check` and `ktlintFormat` rewrites the sources.
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    // From 0.1.1 this build publishes itself with the plugin that its own first release published.
    // `maven-publish` is applied by that plugin.
    id("net.snobo.publish") version "0.1.3"
    // The version comes from the release tag at HEAD; every other commit is local-SNAPSHOT.
    id("net.snobo.version") version "1.0.0"
}

group = "net.snobo"

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
            implementationClass = "net.snobo.resolution.ReposiliteSettingsPlugin"
            displayName = "Reposilite resolution"
            description = "Registers a self hosted Reposilite instance as a dependency and plugin repository."
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

// The engine is pinned, so every net.snobo repository formats with the same ktlint version; the rules come from
// the shared .editorconfig at the repository root (ktlint_official, 120 columns, 4 space indent, trailing commas).
ktlint {
    version.set("1.8.0")
}
