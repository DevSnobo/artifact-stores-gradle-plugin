package net.snobo.artifactstores

import net.snobo.artifactstores.support.id
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.resolve.RepositoriesMode
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.authentication.http.BasicAuthentication
import org.gradle.authentication.http.HttpHeaderAuthentication

/**
 * Registers the configured artifact stores as dependency and plugin repositories.
 *
 * Usage in `settings.gradle.kts`:
 * ```
 * plugins { id("net.snobo.artifact-stores") }
 * artifactStores { reposilite("oss") { includeGroups.add("net.snobo") } }
 * ```
 */
private val LOGGER: Logger = Logging.getLogger(ArtifactStoresPlugin::class.java)

class ArtifactStoresPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        val extension = settings.extensions.create("artifactStores", ArtifactStoresExtension::class.java)
        settings.gradle.settingsEvaluated { configure(settings, extension) }
    }

    private fun configure(
        settings: Settings,
        extension: ArtifactStoresExtension,
    ) {
        val stores = extension.configuredStores.mapNotNull { it.resolve(LOGGER) }
        requireUniqueRepositoryNames(stores)
        extension.repositoriesMode.orNull?.let { mode ->
            settings.dependencyResolutionManagement.repositoriesMode.set(mode)
        }
        if (extension.refreshChangingModules.get()) {
            // Gradle has no per-repository cache policy. The extension switch is build-wide: every configuration
            // of every project stops honoring the snapshot cache for changing modules. The lifecycle action runs
            // in each project's own context, so Isolated Projects can evaluate projects independently.
            settings.gradle.lifecycle.beforeProject { project ->
                project.configurations.all { configuration ->
                    configuration.resolutionStrategy.cacheChangingModulesFor(0, "seconds")
                }
            }
        }
        val warnAboutProjectRepositories =
            stores.any { it.forDependencies } &&
                settings.dependencyResolutionManagement.repositoriesMode.get() == RepositoriesMode.PREFER_PROJECT
        if (warnAboutProjectRepositories) {
            // One warning per offending project. The action sees only the project it belongs to, which keeps it
            // compatible with Isolated Projects.
            settings.gradle.lifecycle.afterProject { project ->
                if (project.repositories.isNotEmpty()) {
                    LOGGER.warn(
                        "Project '${project.path}' declares its own repositories, so Gradle ignores the artifact " +
                            "store repositories from settings (repositoriesMode = PREFER_PROJECT). Move those " +
                            "repositories to settings, or set artifactStores.repositoriesMode = " +
                            "RepositoriesMode.FAIL_ON_PROJECT_REPOS.",
                    )
                }
            }
        }
        stores.forEach { store ->
            if (store.forDependencies) {
                settings.dependencyResolutionManagement.repositories.registerArtifactStore(store)
            }
            if (store.forPlugins) {
                settings.pluginManagement.repositories.registerArtifactStore(store)
            }
        }
    }

    private fun requireUniqueRepositoryNames(stores: List<ResolvedArtifactStore>) {
        requireUniqueRepositoryNamesIn(
            stores.filter { it.forDependencies },
            "dependencyResolutionManagement.repositories",
        )
        requireUniqueRepositoryNamesIn(
            stores.filter { it.forPlugins },
            "pluginManagement.repositories",
        )
    }

    private fun requireUniqueRepositoryNamesIn(
        stores: List<ResolvedArtifactStore>,
        handler: String,
    ) {
        val owners = mutableMapOf<String, String>()
        stores.forEach { store ->
            store.repositoryDefinitions().forEach { definition ->
                val previous = owners.put(definition.name, store.name)
                if (previous != null) {
                    throw GradleException(
                        "Duplicate Gradle repository name '${definition.name}' in $handler from artifact " +
                            "stores '$previous' and '${store.name}'. Set a different 'repositoryName' on one of them.",
                    )
                }
            }
        }
    }
}

internal fun RepositoryHandler.registerArtifactStore(store: ResolvedArtifactStore) {
    if (store.exclusive) {
        exclusiveContent(
            Action { exclusive ->
                store.repositoryDefinitions().forEach { definition ->
                    exclusive.forRepository {
                        maven(
                            Action { repository ->
                                configureRepository(store, definition, repository, applyGroupFilter = false)
                            },
                        )
                    }
                }
                exclusive.filter(
                    Action { filter ->
                        store.includeGroups.forEach(filter::includeGroupAndSubgroups)
                    },
                )
            },
        )
        return
    }
    store.repositoryDefinitions().forEach { definition ->
        maven(
            Action { repository ->
                configureRepository(store, definition, repository)
            },
        )
    }
}

private fun configureRepository(
    store: ResolvedArtifactStore,
    definition: RepositoryDefinition,
    repository: MavenArtifactRepository,
    applyGroupFilter: Boolean = true,
) {
    repository.name = definition.name
    repository.setUrl(definition.url)
    repository.isAllowInsecureProtocol = store.allowInsecure
    when (definition.content) {
        RepositoryContent.RELEASES -> repository.mavenContent(Action { content -> content.releasesOnly() })
        RepositoryContent.SNAPSHOTS -> repository.mavenContent(Action { content -> content.snapshotsOnly() })
        RepositoryContent.BOTH -> Unit
    }
    if (applyGroupFilter && store.includeGroups.isNotEmpty()) {
        repository.content(
            Action { content ->
                store.includeGroups.forEach(content::includeGroupAndSubgroups)
            },
        )
    }
    store.credentials.applyTo(repository)
}

private fun StoreCredentials.applyTo(repository: MavenArtifactRepository) {
    when (this) {
        StoreCredentials.None -> {
            return
        }

        is StoreCredentials.Basic -> {
            repository.credentials(
                PasswordCredentials::class.java,
                Action { credentials ->
                    credentials.username = username
                    credentials.password = password
                },
            )
            repository.authentication(
                Action { authentication ->
                    authentication.create("basic", BasicAuthentication::class.java)
                },
            )
        }

        is StoreCredentials.Header -> {
            repository.credentials(
                HttpHeaderCredentials::class.java,
                Action { credentials ->
                    credentials.name = name
                    credentials.value = value
                },
            )
            repository.authentication(
                Action { authentication ->
                    authentication.create("header", HttpHeaderAuthentication::class.java)
                },
            )
        }
    }
}
