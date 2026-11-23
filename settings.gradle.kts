import org.gradle.api.tasks.bundling.AbstractArchiveTask

pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

// Dogfooding: apply the last published plugin from the Gradle Plugin Portal and configure the
// repository it registers. Bump the version with each release; CI checks that it is the latest.
plugins {
    id("net.snobo.artifact-stores") version "0.6.0"
}

artifactStores {
    generic("central") {
        releasesUrl = uri("https://repo1.maven.org/maven2")
    }
}

buildCache {
    local {
        // CI points this outside the Gradle User Home, so the setup-gradle cache does not keep
        // a second copy. The workflow stores and restores this directory with actions/cache.
        val ciCacheDir = providers.environmentVariable("GRADLE_BUILD_CACHE_DIR").orNull
        if (ciCacheDir != null) {
            directory = java.io.File(ciCacheDir)
        }
    }
}

// Isolated Projects: configure each project from its own isolated lifecycle action instead of
// the root build script's allprojects block.
gradle.lifecycle.beforeProject {
    dependencyLocking {
        lockAllConfigurations()
    }
    // Gradle 9 made these the defaults; Gradle 8.14 still needs the explicit block, and the
    // release and verification jobs must produce byte-identical archives.
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}

// Register the resolution task after the build script has created and finalized its
// configurations. Each project owns its task and resolves only its own configurations. Running
// `./gradlew resolveAndLockAll` executes the task in every project, so no task action touches
// another project and the work is compatible with Isolated Projects and the configuration cache.
gradle.lifecycle.afterProject {
    val resolvableConfigurations = objects.fileCollection()
    configurations.filter { it.isCanBeResolved }.forEach { resolvableConfigurations.from(it) }
    tasks.register("resolveAndLockAll") {
        group = "verification"
        description =
            "Resolves this project's resolvable configurations for dependency locking " +
            "(run with --write-locks)."
        outputs.upToDateWhen { false }
        inputs.files(resolvableConfigurations)
        doLast {
            // Force resolution. Gradle also resolves the declared inputs for fingerprinting.
            inputs.files.files
        }
    }
}

rootProject.name = "artifact-stores-gradle-plugin"

include(":artifact-store-support")
