plugins {
    kotlin("jvm") version "2.4.20"
    `java-gradle-plugin`
    // From 0.1.1 this build publishes itself with the plugin that its own first release published.
    // `maven-publish` is applied by that plugin.
    id("net.snobo.publish") version "0.1.0"
}

group = "net.snobo"
// The version is never hard coded: pass it per release, for example -Pversion=0.1.0. The
// `project.version` spelling is accepted as well, because -Pproject.version does not map onto Project.version.
version = providers.gradleProperty("version")
    .orElse(providers.gradleProperty("project.version"))
    .getOrElse("local-SNAPSHOT")

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
