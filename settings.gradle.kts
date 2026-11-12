pluginManagement {
    // Bootstrap: the publish plugin lives in the private Reposilite instance, and only this manual entry
    // can add the repository it is resolved from. Both routes are listed, so a release and a snapshot of
    // the plugin resolve alike.
    repositories {
        val base = providers.gradleProperty("reposiliteUrl").getOrElse("http://127.0.0.1:8080")
        val user = providers.gradleProperty("reposilitePublisher").orNull
        val secret = providers.gradleProperty("reposiliteToken").orNull
        listOf("releases" to "Releases", "snapshots" to "Snapshots").forEach { (route, suffix) ->
            maven {
                name = "Reposilite-$suffix"
                url = uri("$base/$route")
                isAllowInsecureProtocol = base.startsWith("http://")
                if (user != null && secret != null) {
                    credentials {
                        username = user
                        password = secret
                    }
                }
            }
        }
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// The manual pluginManagement entries above resolve the net.snobo publishing plugins from Reposilite;
// everything else comes from Maven Central.
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "resolution-gradle-plugin"

include(":artifact-store-support")
