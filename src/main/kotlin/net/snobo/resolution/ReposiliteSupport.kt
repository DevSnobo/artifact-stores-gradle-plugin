package net.snobo.resolution

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.authentication.http.BasicAuthentication
import java.net.URI
import java.net.URISyntaxException

/** Gradle property names, environment variables and defaults understood by the plugin. */
internal object ReposiliteDefaults {
    const val URL_PROPERTY = "reposiliteUrl"
    const val PUBLISHER_PROPERTY = "reposilitePublisher"
    const val TOKEN_PROPERTY = "reposiliteToken"

    /** Alias accepted for backward compatibility with hand written Reposilite configurations. */
    const val USERNAME_PROPERTY = "reposiliteUsername"

    const val URL_ENVIRONMENT = "REPOSILITE_URL"
    const val PUBLISHER_ENVIRONMENT = "REPOSILITE_PUBLISHER"
    const val TOKEN_ENVIRONMENT = "REPOSILITE_TOKEN"
    const val USERNAME_ENVIRONMENT = "REPOSILITE_USERNAME"

    /** Only the literal 127.0.0.1 is treated as secure by Gradle (GUtil.isSecureUrl). */
    const val DEFAULT_URL = "http://127.0.0.1:8080"
    const val RELEASES_ROUTE = "releases"
    const val SNAPSHOTS_ROUTE = "snapshots"
    const val PRIVATE_ROUTE = "private"
    const val DEFAULT_GROUP = "net.snobo"
    const val REPOSITORY_NAME = "Reposilite"
}

/** Reads a value from a Gradle property, then from an environment variable. */
internal fun ProviderFactory.reposiliteValue(property: String, environment: String): Provider<String> =
    gradleProperty(property).orElse(environmentVariable(environment))

/**
 * Token name: `reposilitePublisher`, with `reposiliteUsername` accepted as an alias because older hand
 * written Reposilite snippets used that name.
 */
internal fun ProviderFactory.reposilitePublisher(): Provider<String> =
    gradleProperty(ReposiliteDefaults.PUBLISHER_PROPERTY)
        .orElse(gradleProperty(ReposiliteDefaults.USERNAME_PROPERTY))
        .orElse(environmentVariable(ReposiliteDefaults.PUBLISHER_ENVIRONMENT))
        .orElse(environmentVariable(ReposiliteDefaults.USERNAME_ENVIRONMENT))

internal fun uriOf(value: String, source: String): URI {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) {
        throw GradleException("$source is empty. Expected a URL such as ${ReposiliteDefaults.DEFAULT_URL}.")
    }
    return try {
        URI(trimmed)
    } catch (exception: URISyntaxException) {
        throw GradleException("$source is not a valid URL: '$trimmed' (${exception.message}).", exception)
    }
}

internal fun Provider<String>.asUri(source: String): Provider<URI> = map { uriOf(it, source) }

/** Appends a route segment unless the URL already ends with it. */
internal fun URI.withRoute(route: String): URI {
    val trimmed = toString().trimEnd('/')
    val lastSegment = path.orEmpty().trimEnd('/').substringAfterLast('/')
    if (lastSegment.equals(route, ignoreCase = true)) {
        return uriOf(trimmed, "reposilite URL")
    }
    return uriOf("$trimmed/${route.trim('/')}", "reposilite URL")
}

internal fun URI.lastPathSegment(): String = path.orEmpty().trimEnd('/').substringAfterLast('/')

internal fun URI.isPlainHttp(): Boolean = scheme.equals("http", ignoreCase = true)

/** Gradle only treats the literal loopback address 127.0.0.1 as a secure URL. */
internal fun URI.isSecureForGradle(): Boolean = !isPlainHttp() || host == "127.0.0.1"

internal fun String?.isPresent(): Boolean = !isNullOrBlank()

/** Resolved Reposilite coordinates. */
internal data class ReposiliteAccess(
    val baseUrl: URI,
    val releasesUrl: URI,
    val snapshotsUrl: URI,
    val privateUrl: URI,
    val publisher: String?,
    val token: String?,
) {
    val hasCredentials: Boolean = publisher.isPresent() && token.isPresent()
    val isInsecure: Boolean =
        baseUrl.isPlainHttp() || releasesUrl.isPlainHttp() || snapshotsUrl.isPlainHttp() || privateUrl.isPlainHttp()
}

@Suppress("LongParameterList")
internal fun RepositoryHandler.reposiliteMavenRepository(
    name: String,
    url: URI,
    access: ReposiliteAccess,
    allowInsecureProtocol: Boolean,
    includeGroups: List<String>,
): MavenArtifactRepository = maven(Action { repository ->
    repository.name = name
    repository.setUrl(url)
    repository.isAllowInsecureProtocol = allowInsecureProtocol
    if (includeGroups.isNotEmpty()) {
        repository.content(Action { content -> includeGroups.forEach(content::includeGroupAndSubgroups) })
    }
    if (access.hasCredentials) {
        repository.credentials(PasswordCredentials::class.java, Action { credentials ->
            credentials.username = access.publisher
            credentials.password = access.token
        })
        repository.authentication(Action { authentication ->
            authentication.create("basic", BasicAuthentication::class.java)
        })
    }
})
