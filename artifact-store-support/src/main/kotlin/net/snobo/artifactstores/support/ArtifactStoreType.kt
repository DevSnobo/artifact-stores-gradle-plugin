package net.snobo.artifactstores.support

/** The kind of artifact store an instance is backed by. */
enum class ArtifactStoreType {
    REPOSILITE,
    NEXUS,
    ARTIFACTORY,
    GENERIC,
}

/** Lower case name used in messages. */
val ArtifactStoreType.id: String get() = name.lowercase()
