package net.snobo.resolution

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.resolve.RepositoriesMode
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

/**
 * Registers the Reposilite instance as a dependency and plugin repository.
 *
 * Usage in `settings.gradle.kts`:
 * ```
 * plugins { id("net.snobo.resolution") }
 * reposilite { includeGroups.add("net.snobo.foundation") }
 * ```
 */
private val LOGGER: Logger = Logging.getLogger(ReposiliteSettingsPlugin::class.java)

class ReposiliteSettingsPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        val extension = settings.extensions.create("reposilite", ReposiliteSettingsExtension::class.java)
        extension.applyConventions(settings.providers)
        settings.gradle.settingsEvaluated { configure(settings, extension) }
        settings.gradle.projectsEvaluated { warnWhenSettingsRepositoriesAreIgnored(settings, extension) }
    }

    private fun configure(
        settings: Settings,
        extension: ReposiliteSettingsExtension,
    ) {
        val access = extension.resolve()
        val allowInsecure = extension.allowInsecure.get()
        requireSecureIfRequested(access, allowInsecure)
        LOGGER.warnInsecureIfNeeded(access)

        if (!access.hasCredentials) {
            LOGGER.info(
                "Reposilite: no '${ReposiliteDefaults.PUBLISHER_PROPERTY}'/" +
                    "'${ReposiliteDefaults.TOKEN_PROPERTY}' found. Public routes stay readable, " +
                    "authorized routes will fail with 401.",
            )
        }

        extension.repositoriesMode.orNull?.let { mode ->
            settings.dependencyResolutionManagement.repositoriesMode.set(mode)
        }

        val groups = extension.includeGroups.get().filter { it.isNotBlank() }
        val name = extension.repositoryName.get()
        val useForDependencies = extension.useForDependencies.get()
        val useForPlugins = extension.useForPlugins.get()
        val usePrivate = extension.usePrivate.get()

        val routes =
            buildList {
                add("Releases" to access.releasesUrl)
                add("Snapshots" to access.snapshotsUrl)
                if (usePrivate) add("Private" to access.privateUrl)
            }

        if (useForDependencies) {
            val repositories = settings.dependencyResolutionManagement.repositories
            routes.forEach { (suffix, url) ->
                repositories.reposiliteMavenRepository("$name-$suffix", url, access, allowInsecure, groups)
            }
        }

        if (useForPlugins) {
            val repositories = settings.pluginManagement.repositories
            routes.forEach { (suffix, url) ->
                repositories.reposiliteMavenRepository("$name-$suffix", url, access, allowInsecure, groups)
            }
        }

        if (usePrivate && !access.hasCredentials) {
            LOGGER.warn(
                "Reposilite: the private route (${access.privateUrl}) is registered without credentials. Reposilite " +
                    "protects that route, so reads fail with 401 until " +
                    "'${ReposiliteDefaults.PUBLISHER_PROPERTY}' and '${ReposiliteDefaults.TOKEN_PROPERTY}' " +
                    "are configured.",
            )
        }

        if (!useForDependencies && !useForPlugins) {
            LOGGER.warn("Reposilite: 'useForDependencies' and 'useForPlugins' are both false; no repository was added.")
        }
    }

    private fun warnWhenSettingsRepositoriesAreIgnored(
        settings: Settings,
        extension: ReposiliteSettingsExtension,
    ) {
        if (!extension.useForDependencies.get()) return
        if (settings.dependencyResolutionManagement.repositoriesMode.get() != RepositoriesMode.PREFER_PROJECT) return

        val offenders =
            settings.gradle.rootProject.allprojects
                .filter { it.repositories.isNotEmpty() }
                .map { it.path }

        if (offenders.isEmpty()) return

        LOGGER.warn(
            "Reposilite repositories declared in settings are ignored by ${offenders.joinToString(
                ", ",
            )} because those " +
                "projects declare their own repositories (repositoriesMode = PREFER_PROJECT). Move those " +
                "repositories to settings, or set reposilite.repositoriesMode = " +
                "RepositoriesMode.FAIL_ON_PROJECT_REPOS.",
        )
    }
}
