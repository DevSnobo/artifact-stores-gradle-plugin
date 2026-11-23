package net.snobo.artifactstores

import net.snobo.artifactstores.support.ArtifactStoreProfile
import net.snobo.artifactstores.support.ArtifactStoreProfiles
import net.snobo.artifactstores.support.StoreField
import net.snobo.artifactstores.support.StoreFieldNames
import net.snobo.artifactstores.support.StoreValues
import net.snobo.artifactstores.support.id
import net.snobo.artifactstores.support.uriOf
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.initialization.resolve.RepositoriesMode
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import javax.inject.Inject

/**
 * Configuration of the artifact stores.
 *
 * ```
 * artifactStores {
 *     reposilite("oss") { includeGroups.add("net.snobo") }
 *     nexus("corp") { url = uri("https://nexus.example.com") }
 *     artifactory("mirror") { url = uri("https://artifactory.example.com") }
 *     generic("central") { releasesUrl = uri("https://repo1.maven.org/maven2") }
 * }
 * ```
 */
abstract class ArtifactStoresExtension
    @Inject
    constructor(
        private val objects: ObjectFactory,
        private val providers: ProviderFactory,
    ) {
        /** Optional override of `dependencyResolutionManagement.repositoriesMode`. */
        abstract val repositoriesMode: Property<RepositoriesMode>

        /**
         * Refresh changing modules on every build. The default is false.
         *
         * Gradle has no per-repository cache policy, so this build-wide switch sets
         * `cacheChangingModulesFor(0, "seconds")` on every configuration of every project instead of honoring
         * the snapshot cache. It applies to all stores and to dependencies that do not come from an artifact
         * store as well.
         */
        abstract val refreshChangingModules: Property<Boolean>

        private val stores = mutableListOf<ArtifactStore>()

        init {
            refreshChangingModules.convention(false)
        }

        internal val configuredStores: List<ArtifactStore>
            get() = stores

        fun reposilite(name: String): ArtifactStore = create(ArtifactStoreType.REPOSILITE, name, null)

        fun reposilite(
            name: String,
            action: Action<in ArtifactStore>,
        ): ArtifactStore = create(ArtifactStoreType.REPOSILITE, name, action)

        fun nexus(name: String): ArtifactStore = create(ArtifactStoreType.NEXUS, name, null)

        fun nexus(
            name: String,
            action: Action<in ArtifactStore>,
        ): ArtifactStore = create(ArtifactStoreType.NEXUS, name, action)

        fun artifactory(name: String): ArtifactStore = create(ArtifactStoreType.ARTIFACTORY, name, null)

        fun artifactory(
            name: String,
            action: Action<in ArtifactStore>,
        ): ArtifactStore = create(ArtifactStoreType.ARTIFACTORY, name, action)

        fun generic(name: String): ArtifactStore = create(ArtifactStoreType.GENERIC, name, null)

        fun generic(
            name: String,
            action: Action<in ArtifactStore>,
        ): ArtifactStore = create(ArtifactStoreType.GENERIC, name, action)

        private fun create(
            type: ArtifactStoreType,
            name: String,
            action: Action<in ArtifactStore>?,
        ): ArtifactStore {
            if (name.isBlank()) {
                throw GradleException("Artifact store names must not be blank.")
            }
            if (stores.any { it.name == name }) {
                throw GradleException("Artifact store '$name' is already configured. Instance names must be unique.")
            }
            val environmentPrefix = StoreFieldNames(name).environmentPrefix
            val conflicting = stores.firstOrNull { StoreFieldNames(it.name).environmentPrefix == environmentPrefix }
            if (conflicting != null) {
                throw GradleException(
                    "Artifact stores '${conflicting.name}' and '$name' share the environment variable prefix " +
                        "'$environmentPrefix'. Rename one of them so their ARTIFACT_STORE_<NAME>_* variables differ.",
                )
            }
            val store = objects.newInstance(ArtifactStore::class.java, name)
            applyConventions(store, ArtifactStoreProfiles.of(type))
            stores.add(store)
            action?.execute(store)
            return store
        }

        private fun applyConventions(
            store: ArtifactStore,
            profile: ArtifactStoreProfile,
        ) {
            val values = StoreValues(providers, store.name)
            store.type.set(profile.type)
            val url = values.uri(StoreField.URL)
            store.url.convention(
                profile.defaultUrl?.let {
                    url.orElse(uriOf(it, "default ${profile.type.id} URL"))
                } ?: url,
            )
            store.releasesUrl.convention(values.uri(StoreField.RELEASES_URL))
            store.snapshotsUrl.convention(
                if (profile.snapshotsShareReleases) {
                    values.uri(StoreField.SNAPSHOTS_URL).orElse(store.releasesUrl)
                } else {
                    values.uri(StoreField.SNAPSHOTS_URL)
                },
            )
            val releasesRepository = values.string(StoreField.RELEASES_REPOSITORY)
            store.releasesRepository.convention(
                profile.releasesRoute?.let { route -> releasesRepository.orElse(route) } ?: releasesRepository,
            )
            val snapshotsRepository = values.string(StoreField.SNAPSHOTS_REPOSITORY)
            store.snapshotsRepository.convention(
                profile.snapshotsRoute?.let { route -> snapshotsRepository.orElse(route) } ?: snapshotsRepository,
            )
            store.repositoryName.convention(store.name)
            store.username.convention(values.string(StoreField.USERNAME))
            store.password.convention(values.string(StoreField.PASSWORD))
            store.auth.convention(profile.auth)
            store.headerName.convention(values.string(StoreField.HEADER_NAME))
            store.headerValue.convention(values.string(StoreField.HEADER_VALUE))
            store.includeGroups.convention(emptyList())
            store.exclusive.convention(false)
            store.allowInsecure.convention(true)
            store.forDependencies.convention(true)
            store.forPlugins.convention(true)
        }
    }
