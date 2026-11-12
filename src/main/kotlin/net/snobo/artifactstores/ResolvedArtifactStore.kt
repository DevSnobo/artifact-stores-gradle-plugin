package net.snobo.artifactstores

import net.snobo.artifactstores.support.ArtifactStoreProfile
import net.snobo.artifactstores.support.ArtifactStoreProfiles
import net.snobo.artifactstores.support.StoreField
import net.snobo.artifactstores.support.StoreFieldNames
import net.snobo.artifactstores.support.id
import net.snobo.artifactstores.support.isLoopback
import net.snobo.artifactstores.support.isPlainHttp
import net.snobo.artifactstores.support.withRoute
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import java.net.URI

/** Plain, final values of one store. The settings are read exactly once, at registration time. */
internal data class ResolvedArtifactStore(
    val name: String,
    val type: ArtifactStoreType,
    val repositoryName: String,
    val releasesUrl: URI,
    val snapshotsUrl: URI,
    val credentials: StoreCredentials,
    val includeGroups: List<String>,
    val allowInsecure: Boolean,
    val forDependencies: Boolean,
    val forPlugins: Boolean,
)

/** One Gradle repository to create for a store. */
internal data class RepositoryDefinition(
    val name: String,
    val url: URI,
)

internal sealed interface StoreCredentials {
    data object None : StoreCredentials

    data class Basic(
        val username: String,
        val password: String,
    ) : StoreCredentials

    data class Header(
        val name: String,
        val value: String,
    ) : StoreCredentials
}

internal fun ResolvedArtifactStore.repositoryDefinitions(): List<RepositoryDefinition> =
    if (snapshotsUrl == releasesUrl) {
        listOf(RepositoryDefinition(repositoryName, releasesUrl))
    } else {
        listOf(
            RepositoryDefinition("$repositoryName-Releases", releasesUrl),
            RepositoryDefinition("$repositoryName-Snapshots", snapshotsUrl),
        )
    }

/**
 * Resolves the store once, at registration time. Returns null for a store that registers nowhere, so a fully
 * disabled store is not validated at all.
 */
internal fun ArtifactStore.resolve(logger: Logger): ResolvedArtifactStore? {
    val profile = ArtifactStoreProfiles.of(type.get())
    val dependencies = forDependencies.get()
    val plugins = forPlugins.get()
    if (!dependencies && !plugins) {
        logger.warn(
            "Artifact store '$name' (${profile.type.id}) has both 'forDependencies' " +
                "and 'forPlugins' set to false; no repository was added for it.",
        )
        return null
    }
    val names = StoreFieldNames(name)
    val baseUrl = url.orNull
    val releases = releasesUrl.orNull ?: releaseFromBaseUrl(baseUrl, names, profile)
    val snapshots = snapshotsUrl.orNull ?: snapshotFromBaseUrl(baseUrl, releases)
    val allowInsecure = allowInsecure.get()
    requireSecureUrls(allowInsecure, releases, snapshots)
    warnWhenInsecure(logger, allowInsecure, releases, snapshots)
    return ResolvedArtifactStore(
        name = name,
        type = profile.type,
        repositoryName = repositoryName.orNull?.takeIf { it.isNotBlank() } ?: name,
        releasesUrl = releases,
        snapshotsUrl = snapshots,
        credentials = resolveCredentials(profile, names, logger),
        includeGroups = includeGroups.get().filter { it.isNotBlank() },
        allowInsecure = allowInsecure,
        forDependencies = dependencies,
        forPlugins = plugins,
    )
}

private fun ArtifactStore.releaseFromBaseUrl(
    baseUrl: URI?,
    names: StoreFieldNames,
    profile: ArtifactStoreProfile,
): URI {
    if (baseUrl == null) {
        throw GradleException(
            "Artifact store '$name' (${profile.type.id}) has no releases repository. Set 'url' " +
                "(${names.gradleProperty(StoreField.URL)} or ${names.environmentVariable(StoreField.URL)}) to use " +
                "the store's releases route, set 'releasesUrl' (${names.gradleProperty(StoreField.RELEASES_URL)} " +
                "or ${names.environmentVariable(StoreField.RELEASES_URL)}), or set 'releasesRepository' " +
                "(${names.gradleProperty(StoreField.RELEASES_REPOSITORY)} or " +
                "${names.environmentVariable(StoreField.RELEASES_REPOSITORY)}) together with 'url'.",
        )
    }
    val route = releasesRepository.orNull?.takeIf { it.isNotBlank() }
    return route?.let(baseUrl::withRoute) ?: baseUrl
}

private fun ArtifactStore.snapshotFromBaseUrl(
    baseUrl: URI?,
    releases: URI,
): URI {
    if (baseUrl == null) return releases
    val route = snapshotsRepository.orNull?.takeIf { it.isNotBlank() }
    return route?.let(baseUrl::withRoute) ?: baseUrl
}

/** Fails before any repository is created, so the message names the store instead of Gradle's repository. */
private fun ArtifactStore.requireSecureUrls(
    allowInsecure: Boolean,
    releasesUrl: URI,
    snapshotsUrl: URI,
) {
    if (allowInsecure) return
    listOf(releasesUrl, snapshotsUrl)
        .distinct()
        .filter { it.isPlainHttp() }
        .forEach { url ->
            throw GradleException(
                "Artifact store '$name' resolves to plain HTTP ($url) but 'allowInsecure' is false. Use an https " +
                    "URL, or set 'allowInsecure = true' to register the store anyway.",
            )
        }
}

private fun ArtifactStore.warnWhenInsecure(
    logger: Logger,
    allowInsecure: Boolean,
    releasesUrl: URI,
    snapshotsUrl: URI,
) {
    if (!allowInsecure) return
    val insecureUrls = listOf(releasesUrl, snapshotsUrl).filter { it.isPlainHttp() && !it.isLoopback() }.distinct()
    insecureUrls.forEach { url ->
        logger.warn(
            "Artifact store '$name' is reached over plain HTTP ($url). Credentials travel unencrypted and the " +
                "repository is registered with the insecure protocol opt-in. Prefer https for anything but a " +
                "local store.",
        )
    }
}

private fun ArtifactStore.resolveCredentials(
    profile: ArtifactStoreProfile,
    names: StoreFieldNames,
    logger: Logger,
): StoreCredentials =
    when (auth.get()) {
        AuthType.NONE -> {
            StoreCredentials.None
        }

        AuthType.BASIC -> {
            credentialsPair(
                profile = profile,
                names = names,
                firstField = StoreField.USERNAME,
                secondField = StoreField.PASSWORD,
                first = username.orNull,
                second = password.orNull,
                logger = logger,
            )?.let { (user, secret) -> StoreCredentials.Basic(user, secret) } ?: StoreCredentials.None
        }

        AuthType.HEADER -> {
            credentialsPair(
                profile = profile,
                names = names,
                firstField = StoreField.HEADER_NAME,
                secondField = StoreField.HEADER_VALUE,
                first = headerName.orNull,
                second = headerValue.orNull,
                logger = logger,
            )?.let { (header, value) -> StoreCredentials.Header(header, value) } ?: StoreCredentials.None
        }
    }

@Suppress("LongParameterList")
private fun ArtifactStore.credentialsPair(
    profile: ArtifactStoreProfile,
    names: StoreFieldNames,
    firstField: StoreField,
    secondField: StoreField,
    first: String?,
    second: String?,
    logger: Logger,
): Pair<String, String>? {
    val firstValue = first?.takeIf { it.isNotBlank() }
    val secondValue = second?.takeIf { it.isNotBlank() }
    if (firstValue != null && secondValue != null) {
        return firstValue to secondValue
    }
    if (firstValue == null && secondValue == null) {
        logger.warn(
            "Artifact store '$name' (${profile.type.id}) has no credentials. Set " +
                "${names.gradleProperty(firstField)} and ${names.gradleProperty(secondField)}, or " +
                "${names.environmentVariable(firstField)} and ${names.environmentVariable(secondField)}. The " +
                "repository is registered without credentials, so public reads may work and protected reads fail.",
        )
        return null
    }
    val present = if (firstValue != null) firstField else secondField
    val missing = if (firstValue != null) secondField else firstField
    throw GradleException(
        "Artifact store '$name' (${profile.type.id}) has '${present.property}' but no '${missing.property}'. Set " +
            "${names.gradleProperty(firstField)} and ${names.gradleProperty(secondField)}, or " +
            "${names.environmentVariable(firstField)} and ${names.environmentVariable(secondField)}, or neither.",
    )
}
