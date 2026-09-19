package net.snobo.artifactstores.support

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArtifactStoreProfilesTest {
    @Test
    fun `reposilite preset defines routes, auth, default url and separate snapshots`() {
        val profile = ArtifactStoreProfiles.of(ArtifactStoreType.REPOSILITE)

        assertEquals("releases", profile.releasesRoute)
        assertEquals("snapshots", profile.snapshotsRoute)
        assertEquals(AuthType.BASIC, profile.auth)
        assertEquals("http://127.0.0.1:8080", profile.defaultUrl)
        assertFalse(profile.snapshotsShareReleases)
    }

    @Test
    fun `nexus preset defines routes, auth and no default url`() {
        val profile = ArtifactStoreProfiles.of(ArtifactStoreType.NEXUS)

        assertEquals("repository/maven-releases", profile.releasesRoute)
        assertEquals("repository/maven-snapshots", profile.snapshotsRoute)
        assertEquals(AuthType.BASIC, profile.auth)
        assertNull(profile.defaultUrl)
        assertFalse(profile.snapshotsShareReleases)
    }

    @Test
    fun `artifactory preset defines routes, auth and no default url`() {
        val profile = ArtifactStoreProfiles.of(ArtifactStoreType.ARTIFACTORY)

        assertEquals("artifactory/libs-release", profile.releasesRoute)
        assertEquals("artifactory/libs-snapshot", profile.snapshotsRoute)
        assertEquals(AuthType.BASIC, profile.auth)
        assertNull(profile.defaultUrl)
        assertFalse(profile.snapshotsShareReleases)
    }

    @Test
    fun `generic preset has no routes, no auth and shares snapshots with releases`() {
        val profile = ArtifactStoreProfiles.of(ArtifactStoreType.GENERIC)

        assertNull(profile.releasesRoute)
        assertNull(profile.snapshotsRoute)
        assertEquals(AuthType.NONE, profile.auth)
        assertNull(profile.defaultUrl)
        assertTrue(profile.snapshotsShareReleases)
    }
}
