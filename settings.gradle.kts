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

rootProject.name = "artifact-stores-gradle-plugin"

include(":artifact-store-support")
