import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.tasks.GenerateModuleMetadata

plugins {
    kotlin("jvm")
    `java-test-fixtures`
    `maven-publish`
    id("org.jlleitschuh.gradle.ktlint")
}

group = "net.snobo"

// The release workflow passes the version from the release tag. Every other build is a local snapshot.
version = providers.gradleProperty("version").orElse("local-SNAPSHOT").get()

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly(gradleApi())
    testImplementation(kotlin("test"))
    testImplementation(gradleApi())
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            // The test fixtures are for this repository's own tests. Publishing the java component
            // would otherwise advertise a test-fixtures variant whose jar is never uploaded.
            suppressPomMetadataWarningsFor("testFixturesApiElements")
            suppressPomMetadataWarningsFor("testFixturesRuntimeElements")
            pom {
                name.set("Artifact stores support")
                description.set(
                    "Shared artifact store model and Maven repository support for the artifact-stores Gradle plugins.",
                )
                url.set("https://github.com/DevSnobo/artifact-stores-gradle-plugin")
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }
                scm {
                    url.set("https://github.com/DevSnobo/artifact-stores-gradle-plugin")
                    connection.set("scm:git:https://github.com/DevSnobo/artifact-stores-gradle-plugin.git")
                    developerConnection.set(
                        "scm:git:ssh://git@github.com/DevSnobo/artifact-stores-gradle-plugin.git",
                    )
                }
            }
        }
    }

    // A clean clone has no reposiliteUrl, so it configures no remote repository and `check` needs
    // no private infrastructure. The release workflow supplies the URL and the write token.
    val baseUrl = providers.gradleProperty("reposiliteUrl").orNull
    if (baseUrl != null) {
        repositories {
            maven {
                name = "Reposilite"
                val route = if (version.toString().endsWith("-SNAPSHOT")) "snapshots" else "releases"
                url = uri("${baseUrl.trimEnd('/')}/$route")
                isAllowInsecureProtocol = baseUrl.startsWith("http://")
                val publisher = providers.gradleProperty("reposilitePublisher").orNull
                val token = providers.gradleProperty("reposiliteToken").orNull
                if (!publisher.isNullOrBlank() && !token.isNullOrBlank()) {
                    credentials {
                        username = publisher
                        password = token
                    }
                }
            }
        }
    }
}

// Publish a POM only. The java component also carries the local test-fixtures variants, and
// Gradle module metadata would advertise their jar even though only the main jar is published.
tasks.withType<GenerateModuleMetadata>().configureEach {
    enabled = false
}

ktlint {
    version.set("1.8.0")
}
