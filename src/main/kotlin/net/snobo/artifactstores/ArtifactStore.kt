package net.snobo.artifactstores

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import java.net.URI
import javax.inject.Inject

/**
 * One artifact store instance. The preset functions of [ArtifactStoresExtension] create the instance, apply
 * the type defaults and then run the configuration block.
 */
abstract class ArtifactStore
    @Inject
    constructor(
        val name: String,
    ) {
        /** Store type, set by the preset function. */
        abstract val type: Property<ArtifactStoreType>

        /** Base URL of the store, without a route. */
        abstract val url: Property<URI>

        /** Full URL of the releases repository. Overrides [url] + [releasesRepository]. */
        abstract val releasesUrl: Property<URI>

        /** Full URL of the snapshots repository. Overrides [url] + [snapshotsRepository]. */
        abstract val snapshotsUrl: Property<URI>

        /** Route segment appended to [url] for the releases repository. */
        abstract val releasesRepository: Property<String>

        /** Route segment appended to [url] for the snapshots repository. */
        abstract val snapshotsRepository: Property<String>

        /** Name of the created Gradle repositories. Defaults to the instance [name]. */
        abstract val repositoryName: Property<String>

        /** User name for [AuthType.BASIC]. */
        abstract val username: Property<String>

        /** Password for [AuthType.BASIC]. Never logged. */
        abstract val password: Property<String>

        /** Authentication mode. Defaults to the mode of the store type. */
        abstract val auth: Property<AuthType>

        /** Header name for [AuthType.HEADER]. */
        abstract val headerName: Property<String>

        /** Header value for [AuthType.HEADER]. Never logged. */
        abstract val headerValue: Property<String>

        /** Groups served by the store. Empty means no content filter. */
        abstract val includeGroups: ListProperty<String>

        /**
         * Register the store through Gradle `exclusiveContent`. Its groups are then served only by this
         * store and never fall through to another repository. Requires at least one [includeGroups]
         * entry. The default is false.
         */
        abstract val exclusive: Property<Boolean>

        /**
         * Opt in to plain HTTP for the created repositories. The default is true. With false, registration
         * fails when a resolved releases or snapshots URL is plain HTTP.
         */
        abstract val allowInsecure: Property<Boolean>

        /** Register the store for dependency resolution. */
        abstract val forDependencies: Property<Boolean>

        /** Register the store for plugin resolution. */
        abstract val forPlugins: Property<Boolean>
    }
