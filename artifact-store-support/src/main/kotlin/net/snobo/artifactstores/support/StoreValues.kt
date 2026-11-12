package net.snobo.artifactstores.support

import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import java.net.URI
import java.util.Locale

/** Fields that can be supplied by the DSL, a Gradle property or an environment variable. */
enum class StoreField(
    val property: String,
    val environment: String,
) {
    URL("url", "URL"),
    RELEASES_URL("releasesUrl", "RELEASES_URL"),
    SNAPSHOTS_URL("snapshotsUrl", "SNAPSHOTS_URL"),
    RELEASES_REPOSITORY("releasesRepository", "RELEASES_REPOSITORY"),
    SNAPSHOTS_REPOSITORY("snapshotsRepository", "SNAPSHOTS_REPOSITORY"),
    USERNAME("username", "USERNAME"),
    PASSWORD("password", "PASSWORD"),
    HEADER_NAME("headerName", "HEADER_NAME"),
    HEADER_VALUE("headerValue", "HEADER_VALUE"),
}

/**
 * Names of the Gradle property and environment variable of one store field: `artifactStore.<name>.<field>`
 * and `ARTIFACT_STORE_<NAME>_<FIELD>`, with every character outside A-Z0-9 replaced by an underscore in
 * the environment name.
 */
class StoreFieldNames(
    private val storeName: String,
) {
    /** Prefix shared by every environment variable of the store, including its trailing underscore. */
    val environmentPrefix: String = "ARTIFACT_STORE_" + normalize(storeName) + "_"

    fun property(field: StoreField): String = "artifactStore.$storeName.${field.property}"

    fun environment(field: StoreField): String = environmentPrefix + field.environment

    fun gradleProperty(field: StoreField): String = "Gradle property '${property(field)}'"

    fun environmentVariable(field: StoreField): String = "environment variable '${environment(field)}'"

    private fun normalize(name: String): String =
        name
            .uppercase(Locale.ROOT)
            .map { character -> if (character in 'A'..'Z' || character in '0'..'9') character else '_' }
            .joinToString("")
}

/** Property, then environment variable, then whatever the caller uses as the preset default. */
class StoreValues(
    private val providers: ProviderFactory,
    private val storeName: String,
) {
    private val names = StoreFieldNames(storeName)

    fun string(field: StoreField): Provider<String> =
        providers.gradleProperty(names.property(field)).orElse(providers.environmentVariable(names.environment(field)))

    fun uri(field: StoreField): Provider<URI> =
        string(field).map { value ->
            val source =
                "Artifact store '$storeName' ${field.property} " +
                    "(${names.gradleProperty(field)} or ${names.environmentVariable(field)})"
            uriOf(value, source)
        }
}
