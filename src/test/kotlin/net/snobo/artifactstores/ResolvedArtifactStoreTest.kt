package net.snobo.artifactstores

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals

class ResolvedArtifactStoreTest {
    @Test
    fun `one repository for a shared layout`() {
        val store = store(releases = "https://repo.example.com/maven2", snapshots = "https://repo.example.com/maven2")

        assertEquals(
            listOf(RepositoryDefinition("oss", URI("https://repo.example.com/maven2"), RepositoryContent.BOTH)),
            store.repositoryDefinitions(),
        )
    }

    @Test
    fun `releases and snapshots repositories for a separate layout`() {
        val store =
            store(releases = "https://repo.example.com/releases", snapshots = "https://repo.example.com/snapshots")

        assertEquals(
            listOf(
                RepositoryDefinition(
                    "oss-Releases",
                    URI("https://repo.example.com/releases"),
                    RepositoryContent.RELEASES,
                ),
                RepositoryDefinition(
                    "oss-Snapshots",
                    URI("https://repo.example.com/snapshots"),
                    RepositoryContent.SNAPSHOTS,
                ),
            ),
            store.repositoryDefinitions(),
        )
    }

    private fun store(
        releases: String,
        snapshots: String,
    ) = ResolvedArtifactStore(
        name = "oss",
        type = ArtifactStoreType.REPOSILITE,
        repositoryName = "oss",
        releasesUrl = URI(releases),
        snapshotsUrl = URI(snapshots),
        credentials = StoreCredentials.None,
        includeGroups = emptyList(),
        exclusive = false,
        allowInsecure = true,
        forDependencies = true,
        forPlugins = true,
    )
}
