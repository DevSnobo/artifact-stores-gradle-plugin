package net.snobo.gradle.plugin

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReposiliteSupportTest {

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
        assertEquals("http://127.0.0.1:8080/releases", URI("http://127.0.0.1:8080/releases").withRoute("releases").toString())
    }

    @Test
    fun `keeps a base path when appending the route`() {
        assertEquals("https://repo.example.com/maven/releases", URI("https://repo.example.com/maven").withRoute("releases").toString())
    }

    @Test
    fun `detects plain http`() {
        assertTrue(URI("http://127.0.0.1:8080").isPlainHttp())
        assertFalse(URI("https://repo.example.com").isPlainHttp())
    }

    @Test
    fun `only literal loopback counts as secure for gradle`() {
        assertTrue(URI("http://127.0.0.1:8080").isSecureForGradle())
        assertFalse(URI("http://localhost:8080").isSecureForGradle())
        assertTrue(URI("https://repo.example.com").isSecureForGradle())
    }

    @Test
    fun `reads the last path segment`() {
        assertEquals("releases", URI("http://127.0.0.1:8080/releases").lastPathSegment())
        assertEquals("releases", URI("http://127.0.0.1:8080/releases/").lastPathSegment())
        assertEquals("", URI("http://127.0.0.1:8080").lastPathSegment())
    }

    @Test
    fun `a plain http private route makes the whole access insecure`() {
        val access = ReposiliteAccess(
            baseUrl = URI("https://repo.example.com"),
            releasesUrl = URI("https://repo.example.com/releases"),
            snapshotsUrl = URI("https://repo.example.com/snapshots"),
            privateUrl = URI("http://repo.example.com/private"),
            publisher = "token",
            token = "secret",
        )

        assertTrue(access.isInsecure)
    }

    @Test
    fun `rejects a malformed url with a helpful message`() {
        val failure = kotlin.runCatching { uriOf("not a url", "Reposilite URL") }.exceptionOrNull()
        assertTrue(failure is org.gradle.api.GradleException, "expected a GradleException but got $failure")
        assertTrue(failure.message!!.contains("Reposilite URL"), failure.message)
    }
}
