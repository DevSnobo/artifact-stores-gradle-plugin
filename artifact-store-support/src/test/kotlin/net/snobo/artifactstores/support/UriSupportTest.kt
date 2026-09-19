package net.snobo.artifactstores.support

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UriSupportTest {
    private val base = URI("http://127.0.0.1:8080")

    @Test
    fun `appends the route to a base url`() {
        assertEquals("http://127.0.0.1:8080/releases", base.withRoute("releases").toString())
    }

    @Test
    fun `ignores a trailing slash on the base url`() {
        assertEquals("http://127.0.0.1:8080/snapshots", URI("http://127.0.0.1:8080/").withRoute("snapshots").toString())
    }

    @Test
    fun `keeps a url that already ends with the route`() {
        assertEquals(
            "http://127.0.0.1:8080/releases",
            URI("http://127.0.0.1:8080/releases").withRoute("releases").toString(),
        )
    }

    @Test
    fun `keeps a base path when appending the route`() {
        assertEquals(
            "https://repo.example.com/maven/releases",
            URI("https://repo.example.com/maven").withRoute("releases").toString(),
        )
    }

    @Test
    fun `appends a multi segment route`() {
        assertEquals(
            "https://nexus.example.com/repository/maven-releases",
            URI("https://nexus.example.com").withRoute("repository/maven-releases").toString(),
        )
    }

    @Test
    fun `detects plain http`() {
        assertTrue(URI("http://127.0.0.1:8080").isPlainHttp())
        assertFalse(URI("https://repo.example.com").isPlainHttp())
    }

    @Test
    fun `only literal loopback counts as secure for gradle`() {
        assertTrue(URI("http://127.0.0.1:8080").isLoopback())
        assertFalse(URI("http://localhost:8080").isLoopback())
        assertFalse(URI("https://repo.example.com").isLoopback())
    }

    @Test
    fun `rejects a relative url`() {
        val failure = runCatching { uriOf("maven2/releases", "Artifact store 'oss' url") }.exceptionOrNull()

        assertTrue(failure is org.gradle.api.GradleException, "expected a GradleException but got $failure")
        assertTrue(failure.message!!.contains("not an absolute URL"), failure.message)
        assertTrue(failure.message!!.contains("Artifact store 'oss' url"), failure.message)
    }

    @Test
    fun `rejects a malformed url with a helpful message`() {
        val failure = runCatching { uriOf("not a url", "Artifact store 'oss' url") }.exceptionOrNull()

        assertTrue(failure is org.gradle.api.GradleException, "expected a GradleException but got $failure")
        assertTrue(failure.message!!.contains("Artifact store 'oss' url"), failure.message)
    }
}
