package net.snobo.artifactstores.support

import org.gradle.api.GradleException
import java.net.URI
import java.net.URISyntaxException

fun uriOf(
    value: String,
    source: String,
): URI {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) {
        throw GradleException("$source is empty. Expected an absolute URL.")
    }
    val uri =
        try {
            URI(trimmed)
        } catch (exception: URISyntaxException) {
            throw GradleException("$source is not a valid URL: '$trimmed' (${exception.message}).", exception)
        }
    if (!uri.isAbsolute) {
        throw GradleException("$source is not an absolute URL: '$trimmed'. Expected a URL with a scheme.")
    }
    return uri
}

/** Appends a route segment unless the URL already ends with it. */
fun URI.withRoute(route: String): URI {
    val trimmed = toString().trimEnd('/')
    val lastSegment = path.orEmpty().trimEnd('/').substringAfterLast('/')
    if (lastSegment.equals(route, ignoreCase = true)) {
        return uriOf(trimmed, "Artifact store URL")
    }
    return uriOf("$trimmed/${route.trim('/')}", "Artifact store URL")
}

fun URI.isPlainHttp(): Boolean = scheme.equals("http", ignoreCase = true)

/** Gradle only treats the literal loopback address 127.0.0.1 as secure. */
fun URI.isLoopback(): Boolean = host == "127.0.0.1"
