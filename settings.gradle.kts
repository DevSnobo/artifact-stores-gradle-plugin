import org.gradle.api.tasks.bundling.AbstractArchiveTask

pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
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

rootProject.name = "artifact-stores-gradle-plugin"

include(":artifact-store-support")
