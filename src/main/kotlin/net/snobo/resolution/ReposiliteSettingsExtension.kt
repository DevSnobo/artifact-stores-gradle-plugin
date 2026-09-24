package net.snobo.resolution

import org.gradle.api.initialization.resolve.RepositoriesMode
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import java.net.URI

/**
 * Configuration of the Reposilite resolution plugin.
 *
 * Apply `net.snobo.resolution` in `settings.gradle.kts` and configure this extension there.
 */
abstract class ReposiliteSettingsExtension {
    /** Base URL of the Reposilite instance, without a repository route. */
    abstract val url: Property<URI>

    /** Full URL of the releases route. Overrides [url] + [releasesRepository]. */
    abstract val releasesUrl: Property<URI>

    /** Full URL of the snapshots route. Overrides [url] + [snapshotsRepository]. */
    abstract val snapshotsUrl: Property<URI>

    /** Name of the releases route on the server. */
    abstract val releasesRepository: Property<String>

    /** Name of the snapshots route on the server. */
    abstract val snapshotsRepository: Property<String>

    /** Full URL of the private route. Overrides [url] + [privateRepository]. */
    abstract val privateUrl: Property<URI>

    /** Name of the private route on the server. Reads from it need a token with route access. */
    abstract val privateRepository: Property<String>

    /** Prefix used to name the created Gradle repositories. */
    abstract val repositoryName: Property<String>

    /** Reposilite access token name. Defaults to the `reposilitePublisher` Gradle property. */
    abstract val publisher: Property<String>

    /** Reposilite access token secret. Defaults to the `reposiliteToken` Gradle property. */
    abstract val token: Property<String>

    /** Groups resolved from Reposilite. Other groups never contact the server. */
    abstract val includeGroups: ListProperty<String>

    /** Register Reposilite as a dependency repository. */
    abstract val useForDependencies: Property<Boolean>

    /** Register Reposilite as a plugin repository (also resolves this plugin itself). */
    abstract val useForPlugins: Property<Boolean>

    /**
     * Also use the private route, next to [useForDependencies] and [useForPlugins]. Only the configured
     * [includeGroups] are requested from it, and a token is required because Reposilite protects the private
     * route by default.
     */
    abstract val usePrivate: Property<Boolean>

    /** Allow plain HTTP. When false, a plain HTTP URL fails the build. */
    abstract val allowInsecure: Property<Boolean>

    /** Optional override of `dependencyResolutionManagement.repositoriesMode`. */
    abstract val repositoriesMode: Property<RepositoriesMode>
}
