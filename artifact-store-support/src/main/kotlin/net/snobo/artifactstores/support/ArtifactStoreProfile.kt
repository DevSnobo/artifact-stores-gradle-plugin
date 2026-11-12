package net.snobo.artifactstores.support

/**
 * Type specific defaults. The preset functions of the plugin extension read this record once, and the
 * registration reads only the resolved snapshot, so no other code branches on [ArtifactStoreType].
 */
data class ArtifactStoreProfile(
    val type: ArtifactStoreType,
    /** Route segment appended to the base URL for releases; null when the type has no fixed routes. */
    val releasesRoute: String?,
    /** Route segment appended to the base URL for snapshots; null when the type has no fixed routes. */
    val snapshotsRoute: String?,
    val auth: AuthType,
    /** Base URL used when neither the DSL, a Gradle property nor the environment provides one. */
    val defaultUrl: String?,
    /** Whether snapshots are served from the releases URL unless a snapshots URL is set. */
    val snapshotsShareReleases: Boolean,
)

object ArtifactStoreProfiles {
    private val profiles: Map<ArtifactStoreType, ArtifactStoreProfile> =
        listOf(
            ArtifactStoreProfile(
                type = ArtifactStoreType.REPOSILITE,
                releasesRoute = "releases",
                snapshotsRoute = "snapshots",
                auth = AuthType.BASIC,
                defaultUrl = "http://127.0.0.1:8080",
                snapshotsShareReleases = false,
            ),
            ArtifactStoreProfile(
                type = ArtifactStoreType.NEXUS,
                releasesRoute = "repository/maven-releases",
                snapshotsRoute = "repository/maven-snapshots",
                auth = AuthType.BASIC,
                defaultUrl = null,
                snapshotsShareReleases = false,
            ),
            ArtifactStoreProfile(
                type = ArtifactStoreType.ARTIFACTORY,
                releasesRoute = "artifactory/libs-release",
                snapshotsRoute = "artifactory/libs-snapshot",
                auth = AuthType.BASIC,
                defaultUrl = null,
                snapshotsShareReleases = false,
            ),
            ArtifactStoreProfile(
                type = ArtifactStoreType.GENERIC,
                releasesRoute = null,
                snapshotsRoute = null,
                auth = AuthType.NONE,
                defaultUrl = null,
                snapshotsShareReleases = true,
            ),
        ).associateBy { it.type }

    fun of(type: ArtifactStoreType): ArtifactStoreProfile = profiles.getValue(type)
}
