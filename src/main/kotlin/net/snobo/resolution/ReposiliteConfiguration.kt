package net.snobo.resolution

import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.api.provider.ProviderFactory
import java.net.URI

internal fun ReposiliteSettingsExtension.applyConventions(providers: ProviderFactory) {
    url.convention(baseUrlProvider(providers))
    releasesRepository.convention(ReposiliteDefaults.RELEASES_ROUTE)
    snapshotsRepository.convention(ReposiliteDefaults.SNAPSHOTS_ROUTE)
    privateRepository.convention(ReposiliteDefaults.PRIVATE_ROUTE)
    repositoryName.convention(ReposiliteDefaults.REPOSITORY_NAME)
    publisher.convention(providers.reposilitePublisher())
    token.convention(providers.reposiliteValue(ReposiliteDefaults.TOKEN_PROPERTY, ReposiliteDefaults.TOKEN_ENVIRONMENT))
    includeGroups.convention(listOf(ReposiliteDefaults.DEFAULT_GROUP))
    useForDependencies.convention(true)
    useForPlugins.convention(true)
    usePrivate.convention(false)
    allowInsecure.convention(true)
}

private fun baseUrlProvider(providers: ProviderFactory) =
    providers
        .reposiliteValue(ReposiliteDefaults.URL_PROPERTY, ReposiliteDefaults.URL_ENVIRONMENT)
        .asUri(
            "Reposilite URL (Gradle property '${ReposiliteDefaults.URL_PROPERTY}' or '${ReposiliteDefaults.URL_ENVIRONMENT}')",
        ).orElse(uriOf(ReposiliteDefaults.DEFAULT_URL, "default Reposilite URL"))

/** Resolves the configured Reposilite coordinates into plain values. */
@Suppress("LongParameterList")
internal fun resolveAccess(
    baseUrl: URI,
    releasesUrl: URI?,
    snapshotsUrl: URI?,
    privateUrl: URI?,
    releasesRoute: String,
    snapshotsRoute: String,
    privateRoute: String,
    publisher: String?,
    token: String?,
): ReposiliteAccess =
    ReposiliteAccess(
        baseUrl = baseUrl,
        releasesUrl = releasesUrl ?: baseUrl.withRoute(releasesRoute),
        snapshotsUrl = snapshotsUrl ?: baseUrl.withRoute(snapshotsRoute),
        privateUrl = privateUrl ?: baseUrl.withRoute(privateRoute),
        publisher = publisher?.takeIf { it.isNotBlank() },
        token = token?.takeIf { it.isNotBlank() },
    )

internal fun ReposiliteSettingsExtension.resolve(): ReposiliteAccess =
    resolveAccess(
        baseUrl = url.get(),
        releasesUrl = releasesUrl.orNull,
        snapshotsUrl = snapshotsUrl.orNull,
        privateUrl = privateUrl.orNull,
        releasesRoute = releasesRepository.get(),
        snapshotsRoute = snapshotsRepository.get(),
        privateRoute = privateRepository.get(),
        publisher = publisher.orNull,
        token = token.orNull,
    )

/** Fails when the user disallowed plain HTTP but a configured URL is plain HTTP. */
internal fun requireSecureIfRequested(
    access: ReposiliteAccess,
    allowInsecure: Boolean,
) {
    if (access.isInsecure && !allowInsecure) {
        throw GradleException(
            "Reposilite is configured with plain HTTP (${access.baseUrl}) while 'reposilite.allowInsecure' is false. " +
                "Use an https URL (see the Reposilite ssl guide) or set reposilite.allowInsecure = true.",
        )
    }
}

internal fun Logger.warnInsecureIfNeeded(access: ReposiliteAccess) {
    if (access.isInsecure && !access.baseUrl.isSecureForGradle()) {
        warn(
            "Reposilite at ${access.baseUrl} is reached over plain HTTP. Credentials are sent unencrypted and Gradle " +
                "requires the insecure protocol opt-in, which this plugin enables. Prefer https for anything but localhost.",
        )
    }
}
