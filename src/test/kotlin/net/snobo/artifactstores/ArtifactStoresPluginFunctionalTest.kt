package net.snobo.artifactstores

import net.snobo.artifactstores.support.ReposiliteStub
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Set ARTIFACT_STORE_TEST_GRADLE_VERSION to run the whole suite against another Gradle release. */
private val testGradleVersion: String? = System.getenv("ARTIFACT_STORE_TEST_GRADLE_VERSION")

class ArtifactStoresPluginFunctionalTest {
    private lateinit var projectDir: File
    private lateinit var testKitDir: File
    private lateinit var stub: ReposiliteStub

    @BeforeTest
    fun setUp() {
        projectDir = Files.createTempDirectory("artifact-stores-test").toFile()
        testKitDir = Files.createTempDirectory("artifact-stores-testkit").toFile()
        stub = ReposiliteStub().start()
    }

    @AfterTest
    fun tearDown() {
        stub.stop()
        projectDir.deleteRecursively()
        testKitDir.deleteRecursively()
    }

    private fun write(
        path: String,
        content: String,
    ) {
        val file = File(projectDir, path)
        file.parentFile?.mkdirs()
        file.writeText(content.trimIndent())
    }

    private fun credentials(
        name: String = "net-snobo-publisher",
        secret: String = "stub-secret",
    ) {
        write("gradle.properties", "artifactStore.oss.username=$name\nartifactStore.oss.password=$secret\n")
    }

    private fun runner(
        vararg args: String,
        extraEnvironment: Map<String, String> = emptyMap(),
    ): GradleRunner {
        // CI exports the store credentials for this build. TestKit builds inherit the environment, so a
        // fixture that expects no credentials would silently see CI's. Keep those variables out of scratch builds.
        val environment =
            System.getenv().filterKeys {
                !it.startsWith("ARTIFACT_STORE_") &&
                    !it.startsWith("REPOSILITE_") &&
                    !it.startsWith("ORG_GRADLE_PROJECT_artifactStore") &&
                    !it.startsWith("ORG_GRADLE_PROJECT_reposilite")
            } + extraEnvironment
        val runner =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withTestKitDir(testKitDir)
                .withEnvironment(environment)
        testGradleVersion?.let { runner.withGradleVersion(it) }
        return runner.withArguments(*args, "--stacktrace")
    }

    /**
     * Extra arguments that enable Isolated Projects on the Gradle version under test. Gradle 9.7 made
     * `--isolated-projects` a command-line option; earlier releases only know the system property. Either form
     * also enables the configuration cache.
     */
    private fun isolatedProjects(): List<String> =
        if (gradleVersionUnderTest() >= GradleVersion.version("9.7")) {
            listOf("--isolated-projects")
        } else {
            listOf("-Dorg.gradle.unsafe.isolated-projects=true")
        }

    private fun gradleVersionUnderTest(): GradleVersion =
        testGradleVersion?.let { GradleVersion.version(it) } ?: GradleVersion.current()

    private fun assertIsolatedProjectsEnabled(output: String) {
        assertTrue(
            output.contains("isolated projects is an incubating feature", ignoreCase = true),
            "expected Gradle to enable Isolated Projects:\n$output",
        )
    }

    /** Writes a settings script with the given body of the `artifactStores` block. */
    private fun settings(
        artifactStoresBlock: String,
        extra: String = "",
    ) {
        val text =
            buildString {
                appendLine("plugins { id(\"net.snobo.artifact-stores\") }")
                appendLine()
                appendLine("artifactStores {")
                appendLine(artifactStoresBlock.trimIndent().prependIndent("    "))
                appendLine("}")
                appendLine()
                if (extra.isNotBlank()) {
                    appendLine(extra.trimIndent())
                    appendLine()
                }
                appendLine("rootProject.name = \"artifact-store-probe\"")
            }
        File(projectDir, "settings.gradle.kts").writeText(text)
    }

    private fun resolveProbeBuild(artifact: String = "net.snobo.probe:probe:1.0") {
        write(
            "build.gradle.kts",
            """
            plugins { `java-library` }

            dependencies { implementation("$artifact") }

            tasks.register("resolveProbe") {
                val classpath = configurations.getByName("compileClasspath")
                doLast {
                    println("GRADLE_VERSION=" + gradle.gradleVersion)
                    println("RESOLVED=" + classpath.files.map { it.name }.sorted())
                }
            }
            """,
        )
    }

    private fun resolutionProject(
        artifactStoresBlock: String,
        artifact: String = "net.snobo.probe:probe:1.0",
    ) {
        settings(artifactStoresBlock)
        resolveProbeBuild(artifact)
    }

    /**
     * Writes a two-project build. The root project has no repositories and no dependencies; `:sub` holds the
     * probe dependency and a configuration-cache compatible resolve task, so Isolated Projects must configure
     * both projects independently. The task copies the resolved classpath into `sub/build/resolved`.
     */
    private fun multiProjectResolutionProject(
        artifactStoresBlock: String,
        projectRepositories: String = "",
    ) {
        settings(artifactStoresBlock, extra = "include(\":sub\")")
        write("build.gradle.kts", "plugins { `java-library` }")
        write(
            "sub/build.gradle.kts",
            """
            plugins { `java-library` }

            ${projectRepositories.trimIndent()}

            dependencies { implementation("net.snobo.probe:probe:1.0") }

            tasks.register<Copy>("resolveProbe") {
                from(configurations["compileClasspath"])
                into(layout.buildDirectory.dir("resolved"))
            }
            """,
        )
    }

    private fun assertSubProjectResolved(output: String) {
        assertTrue(
            File(projectDir, "sub/build/resolved/probe-1.0.jar").isFile,
            "expected the probe jar in sub/build/resolved:\n$output",
        )
    }

    private fun restartStub(artifactRoutes: Set<String>) {
        stub.stop()
        stub = ReposiliteStub(artifactRoutes = artifactRoutes).start()
    }

    /** Settings script fragment that prints the names of the settings dependency repositories. */
    private fun printRepositoryNames(): String =
        """
        val settingsRepositories = dependencyResolutionManagement.repositories
        gradle.settingsEvaluated {
            println("DEPENDENCY_REPOS=" + settingsRepositories.map { it.name }.sorted())
        }
        """.trimIndent()

    /** Settings script fragment that prints the names and URLs of the settings dependency repositories. */
    private fun printRepositories(): String =
        """
        val settingsRepositories = dependencyResolutionManagement.repositories
        gradle.settingsEvaluated {
            settingsRepositories.forEach { repository ->
                val maven = repository as org.gradle.api.artifacts.repositories.MavenArtifactRepository
                println("REPO=" + maven.name + " " + maven.url)
            }
        }
        """.trimIndent()

    @Test
    fun `resolves an artifact from a reposilite store using the configured credentials`() {
        credentials()
        resolutionProject(
            """
            reposilite("oss") {
                url = uri("${stub.baseUrl}")
                includeGroups.add("net.snobo.probe")
            }
            """,
        )

        val result = runner("resolveProbe").build()

        assertTrue(result.output.contains("probe-1.0.jar"), result.output)
        testGradleVersion?.let { requested ->
            assertTrue(
                result.output.contains("GRADLE_VERSION=$requested"),
                "expected Gradle $requested in:\n${result.output}",
            )
        }
        val requests = stub.requests.filter { it.path.startsWith("/releases/net/snobo/probe/") }
        assertTrue(requests.isNotEmpty(), "expected artifact requests, got ${stub.requests}")
        assertTrue(requests.all { it.authorization != null }, "expected credentials on every request")
    }

    @Test
    fun `groups outside the filter never contact the store`() {
        credentials()
        resolutionProject(
            artifactStoresBlock =
                """
                reposilite("oss") {
                    url = uri("${stub.baseUrl}")
                    includeGroups.add("net.snobo.probe")
                }
                """,
            artifact = "org.apache.commons:commons-lang3:3.14.0",
        )

        val result = runner("resolveProbe").buildAndFail()

        assertTrue(result.output.contains("Could not find org.apache.commons:commons-lang3"), result.output)
        assertTrue(stub.requests.isEmpty(), "the store must not be contacted: ${stub.requests}")
    }

    @Test
    fun `a store without credentials still registers and sends no authorization header`() {
        resolutionProject(
            """
            reposilite("oss") {
                url = uri("${stub.baseUrl}")
                includeGroups.add("net.snobo.probe")
            }
            """,
        )

        val result = runner("resolveProbe").build()

        assertTrue(result.output.contains("probe-1.0.jar"), result.output)
        assertTrue(stub.requests.isNotEmpty(), "expected artifact requests, got ${stub.requests}")
        assertTrue(stub.requests.all { it.authorization == null }, "expected no authentication: ${stub.requests}")
    }

    @Test
    fun `missing credentials produce a warning that names both sources`() {
        settings(
            """
            reposilite("oss") {
                url = uri("${stub.baseUrl}")
            }
            """,
        )
        write("build.gradle.kts", "plugins { `java-library` }")

        val result = runner("help").build()

        assertTrue(result.output.contains("no credentials"), result.output)
        assertTrue(result.output.contains("artifactStore.oss.username"), result.output)
        assertTrue(result.output.contains("artifactStore.oss.password"), result.output)
        assertTrue(result.output.contains("ARTIFACT_STORE_OSS_USERNAME"), result.output)
        assertTrue(result.output.contains("ARTIFACT_STORE_OSS_PASSWORD"), result.output)
    }

    @Test
    fun `the nexus preset requests the maven-releases route`() {
        restartStub(setOf("repository"))
        resolutionProject(
            """
            nexus("corp") {
                url = uri("${stub.baseUrl}")
                includeGroups.add("net.snobo.probe")
            }
            """,
        )

        val result = runner("resolveProbe").build()

        assertTrue(result.output.contains("probe-1.0.jar"), result.output)
        val requests = stub.requests.filter { it.path.startsWith("/repository/maven-releases/net/snobo/probe/") }
        assertTrue(requests.isNotEmpty(), "expected requests to the maven-releases route, got ${stub.requests}")
    }

    @Test
    fun `the artifactory preset requests the libs-release route`() {
        restartStub(setOf("artifactory"))
        resolutionProject(
            """
            artifactory("mirror") {
                url = uri("${stub.baseUrl}")
                includeGroups.add("net.snobo.probe")
            }
            """,
        )

        val result = runner("resolveProbe").build()

        assertTrue(result.output.contains("probe-1.0.jar"), result.output)
        val requests = stub.requests.filter { it.path.startsWith("/artifactory/libs-release/net/snobo/probe/") }
        assertTrue(requests.isNotEmpty(), "expected requests to the libs-release route, got ${stub.requests}")
    }

    @Test
    fun `the generic preset with one URL registers a single repository and resolves from it`() {
        restartStub(setOf("releases"))
        settings(
            artifactStoresBlock =
                """
                generic("central") {
                    releasesUrl = uri("${stub.baseUrl}/releases")
                    includeGroups.add("net.snobo.probe")
                }
                """,
            extra = printRepositoryNames(),
        )
        resolveProbeBuild()

        val result = runner("resolveProbe").build()

        assertTrue(result.output.contains("probe-1.0.jar"), result.output)
        assertTrue(result.output.contains("DEPENDENCY_REPOS=[central]"), result.output)
        val requests = stub.requests.filter { it.path.startsWith("/releases/net/snobo/probe/") }
        assertTrue(requests.isNotEmpty(), "expected requests to the releases URL, got ${stub.requests}")
    }

    @Test
    fun `credentials are read from the store environment variables`() {
        resolutionProject(
            """
            reposilite("oss") {
                url = uri("${stub.baseUrl}")
                includeGroups.add("net.snobo.probe")
            }
            """,
        )

        val result =
            runner(
                "resolveProbe",
                extraEnvironment =
                    mapOf(
                        "ARTIFACT_STORE_OSS_USERNAME" to "net-snobo-publisher",
                        "ARTIFACT_STORE_OSS_PASSWORD" to "stub-secret",
                    ),
            ).build()

        assertTrue(result.output.contains("probe-1.0.jar"), result.output)
        val requests = stub.requests.filter { it.path.startsWith("/releases/net/snobo/probe/") }
        assertTrue(requests.isNotEmpty(), "expected artifact requests, got ${stub.requests}")
        assertTrue(requests.all { it.authorization != null }, "expected credentials on every request")
    }

    @Test
    fun `a duplicate instance name fails`() {
        settings(
            """
            reposilite("dup") {
                url = uri("${stub.baseUrl}")
            }
            generic("dup") {
                releasesUrl = uri("${stub.baseUrl}/releases")
            }
            """,
        )
        write("build.gradle.kts", "plugins { `java-library` }")

        val failure = runner("help").buildAndFail()

        assertTrue(failure.output.contains("Artifact store 'dup' is already configured"), failure.output)
    }

    @Test
    fun `two stores with the same repository name fail`() {
        settings(
            """
            reposilite("oss") {
                url = uri("${stub.baseUrl}")
                repositoryName = "shared"
            }
            nexus("corp") {
                url = uri("${stub.baseUrl}")
                repositoryName = "shared"
            }
            """,
        )
        write("build.gradle.kts", "plugins { `java-library` }")

        val failure = runner("help").buildAndFail()

        assertTrue(failure.output.contains("Duplicate Gradle repository name 'shared-Releases'"), failure.output)
        assertTrue(failure.output.contains("'oss'"), failure.output)
        assertTrue(failure.output.contains("'corp'"), failure.output)
    }

    @Test
    fun `a preset without a configuration block uses the store defaults`() {
        settings("reposilite(\"oss\")", extra = printRepositories())
        write("build.gradle.kts", "plugins { `java-library` }")

        val result = runner("help").build()

        assertTrue(result.output.contains("REPO=oss-Releases http://127.0.0.1:8080/releases"), result.output)
        assertTrue(result.output.contains("REPO=oss-Snapshots http://127.0.0.1:8080/snapshots"), result.output)
    }

    @Test
    fun `header authentication sends the configured header`() {
        write(
            "gradle.properties",
            "artifactStore.central.headerName=X-Auth\nartifactStore.central.headerValue=stub-secret\n",
        )
        resolutionProject(
            """
            generic("central") {
                releasesUrl = uri("${stub.baseUrl}/releases")
                auth.set(net.snobo.artifactstores.AuthType.HEADER)
                includeGroups.add("net.snobo.probe")
            }
            """,
        )

        val result = runner("resolveProbe").build()

        assertTrue(result.output.contains("probe-1.0.jar"), result.output)
        val requests = stub.requests.filter { it.path.startsWith("/releases/net/snobo/probe/") }
        assertTrue(requests.isNotEmpty(), "expected artifact requests, got ${stub.requests}")
        assertTrue(requests.all { it.headers["x-auth"] == "stub-secret" }, "expected the header: ${stub.requests}")
    }

    @Test
    fun `warns about plain HTTP outside loopback and about stores that register nowhere`() {
        settings(
            """
            reposilite("plain") {
                url = uri("http://localhost:8080")
            }
            reposilite("unused") {
                forDependencies = false
                forPlugins = false
            }
            """,
        )
        write("build.gradle.kts", "plugins { `java-library` }")

        val result = runner("help").build()

        assertTrue(result.output.contains("plain HTTP (http://localhost:8080/releases)"), result.output)
        assertTrue(result.output.contains("has both 'forDependencies' and 'forPlugins' set to false"), result.output)
        assertTrue(result.output.contains("no credentials"), result.output)
    }

    @Test
    fun `plain HTTP with allowInsecure false fails with the store name and the URL`() {
        settings(
            """
            reposilite("plain") {
                url = uri("http://localhost:8080")
                allowInsecure = false
            }
            """,
        )
        write("build.gradle.kts", "plugins { `java-library` }")

        val failure = runner("help").buildAndFail()

        assertTrue(failure.output.contains("Artifact store 'plain' resolves to plain HTTP"), failure.output)
        assertTrue(failure.output.contains("http://localhost:8080/releases"), failure.output)
        assertTrue(failure.output.contains("'allowInsecure' is false"), failure.output)
    }

    @Test
    fun `a store resolves with the configuration cache enabled and reused`() {
        credentials()
        settings(
            """
            refreshChangingModules = true
            reposilite("oss") {
                url = uri("${stub.baseUrl}")
                includeGroups.add("net.snobo.probe")
            }
            """,
        )
        // Resolve through a file collection input, so the task itself is configuration-cache compatible.
        write(
            "build.gradle.kts",
            """
            plugins { `java-library` }

            dependencies { implementation("net.snobo.probe:probe:1.0") }

            tasks.register<Copy>("resolveProbe") {
                from(configurations["compileClasspath"])
                into(layout.buildDirectory.dir("resolved"))
            }
            """,
        )

        val first = runner("resolveProbe", "--configuration-cache").build()
        val resolved = File(projectDir, "build/resolved").listFiles().orEmpty().map { it.name }
        assertTrue(resolved.contains("probe-1.0.jar"), "expected the probe jar in $resolved:\n${first.output}")

        val second = runner("resolveProbe", "--configuration-cache").build()
        val reused =
            second.output.contains("Configuration cache entry reused") ||
                second.output.contains("Reusing configuration cache")
        assertTrue(reused, "expected the second build to reuse the configuration cache:\n${second.output}")
        assertTrue(File(projectDir, "build/resolved/probe-1.0.jar").isFile, second.output)
    }

    // Isolated Projects: every project is configured on its own, so the plugin must never read another project.
    // These fixtures include a `:sub` project to make a cross-project access a build failure instead of a no-op.

    @Test
    fun `settings repositories resolve a dependency from a subproject under isolated projects`() {
        credentials()
        multiProjectResolutionProject(
            """
            reposilite("oss") {
                url = uri("${stub.baseUrl}")
                includeGroups.add("net.snobo.probe")
            }
            """,
        )

        val result = runner(":sub:resolveProbe", *isolatedProjects().toTypedArray()).build()

        assertIsolatedProjectsEnabled(result.output)
        assertSubProjectResolved(result.output)
        val requests = stub.requests.filter { it.path.startsWith("/releases/net/snobo/probe/") }
        assertTrue(requests.isNotEmpty(), "expected artifact requests, got ${stub.requests}")
    }

    @Test
    fun `refreshChangingModules applies to every project under isolated projects`() {
        credentials()
        multiProjectResolutionProject(
            """
            refreshChangingModules = true
            reposilite("oss") {
                url = uri("${stub.baseUrl}")
                includeGroups.add("net.snobo.probe")
            }
            """,
        )

        val result = runner(":sub:resolveProbe", *isolatedProjects().toTypedArray()).build()

        assertIsolatedProjectsEnabled(result.output)
        assertSubProjectResolved(result.output)
    }

    @Test
    fun `PREFER_PROJECT warns for the project that declares its own repositories under isolated projects`() {
        restartStub(setOf("releases", "snapshots", "project"))
        multiProjectResolutionProject(
            """
            repositoriesMode.set(org.gradle.api.initialization.resolve.RepositoriesMode.PREFER_PROJECT)
            reposilite("oss") {
                url = uri("${stub.baseUrl}")
                includeGroups.add("net.snobo.probe")
            }
            """,
            projectRepositories =
                """
                repositories {
                    maven {
                        name = "project"
                        url = uri("${stub.baseUrl}/project")
                    }
                }
                """,
        )

        val result = runner(":sub:resolveProbe", *isolatedProjects().toTypedArray()).build()

        assertIsolatedProjectsEnabled(result.output)
        assertSubProjectResolved(result.output)
        assertEquals(
            1,
            Regex("Project ':[^']*' declares its own repositories").findAll(result.output).count(),
            "expected exactly one warning:\n${result.output}",
        )
        assertTrue(result.output.contains("Project ':sub' declares its own repositories"), result.output)
        val projectRequests = stub.requests.filter { it.path.startsWith("/project/net/snobo/probe/") }
        assertTrue(projectRequests.isNotEmpty(), "expected the project repository to resolve: ${stub.requests}")
        val settingsRequests = stub.requests.filter { it.path.startsWith("/releases/net/snobo/probe/") }
        assertTrue(settingsRequests.isEmpty(), "expected the settings repositories to be ignored: ${stub.requests}")
    }

    @Test
    fun `PREFER_PROJECT without project repositories does not warn under isolated projects`() {
        credentials()
        multiProjectResolutionProject(
            """
            repositoriesMode.set(org.gradle.api.initialization.resolve.RepositoriesMode.PREFER_PROJECT)
            reposilite("oss") {
                url = uri("${stub.baseUrl}")
                includeGroups.add("net.snobo.probe")
            }
            """,
        )

        val result = runner(":sub:resolveProbe", *isolatedProjects().toTypedArray()).build()

        assertIsolatedProjectsEnabled(result.output)
        assertSubProjectResolved(result.output)
        assertTrue(!result.output.contains("declares its own repositories"), result.output)
    }

    @Test
    fun `FAIL_ON_PROJECT_REPOS resolves under isolated projects without a warning`() {
        credentials()
        multiProjectResolutionProject(
            """
            repositoriesMode.set(org.gradle.api.initialization.resolve.RepositoriesMode.FAIL_ON_PROJECT_REPOS)
            reposilite("oss") {
                url = uri("${stub.baseUrl}")
                includeGroups.add("net.snobo.probe")
            }
            """,
        )

        val result = runner(":sub:resolveProbe", *isolatedProjects().toTypedArray()).build()

        assertIsolatedProjectsEnabled(result.output)
        assertSubProjectResolved(result.output)
        assertTrue(!result.output.contains("declares its own repositories"), result.output)
    }

    @Test
    fun `an isolated projects build reuses the configuration cache`() {
        credentials()
        multiProjectResolutionProject(
            """
            reposilite("oss") {
                url = uri("${stub.baseUrl}")
                includeGroups.add("net.snobo.probe")
            }
            """,
        )

        val arguments = arrayOf(":sub:resolveProbe", *isolatedProjects().toTypedArray())
        val first = runner(*arguments).build()
        assertIsolatedProjectsEnabled(first.output)
        assertSubProjectResolved(first.output)

        val second = runner(*arguments).build()
        val reused =
            second.output.contains("Configuration cache entry reused") ||
                second.output.contains("Reusing configuration cache")
        assertTrue(reused, "expected the second build to reuse the configuration cache:\n${second.output}")
        assertSubProjectResolved(second.output)
    }

    @Test
    fun `the reposilite route overrides are used for the repository URLs`() {
        restartStub(setOf("custom-releases", "custom-snapshots"))
        settings(
            """
            reposilite("oss") {
                url = uri("${stub.baseUrl}")
                releasesRepository = "custom-releases"
                snapshotsRepository = "custom-snapshots"
                includeGroups.add("net.snobo.probe")
            }
            """,
            extra = printRepositories(),
        )
        resolveProbeBuild()

        val result = runner("resolveProbe").build()

        assertTrue(result.output.contains("probe-1.0.jar"), result.output)
        assertTrue(
            result.output.contains("REPO=oss-Releases ${stub.baseUrl}/custom-releases"),
            result.output,
        )
        assertTrue(
            result.output.contains("REPO=oss-Snapshots ${stub.baseUrl}/custom-snapshots"),
            result.output,
        )
        val requests = stub.requests.filter { it.path.startsWith("/custom-releases/net/snobo/probe/") }
        assertTrue(requests.isNotEmpty(), "expected requests to the overridden route, got ${stub.requests}")
    }

    @Test
    fun `a nexus store with full URLs and no base URL resolves`() {
        restartStub(setOf("repository"))
        settings(
            """
            nexus("corp") {
                releasesUrl = uri("${stub.baseUrl}/repository/maven-releases")
                snapshotsUrl = uri("${stub.baseUrl}/repository/maven-snapshots")
                includeGroups.add("net.snobo.probe")
            }
            """,
            extra = printRepositories(),
        )
        resolveProbeBuild()

        val result = runner("resolveProbe").build()

        assertTrue(result.output.contains("probe-1.0.jar"), result.output)
        assertTrue(
            result.output.contains("REPO=corp-Releases ${stub.baseUrl}/repository/maven-releases"),
            result.output,
        )
        val requests = stub.requests.filter { it.path.startsWith("/repository/maven-releases/net/snobo/probe/") }
        assertTrue(requests.isNotEmpty(), "expected requests to the full releases URL, got ${stub.requests}")
    }

    @Test
    fun `a generic store with only a url registers one repository and resolves from it`() {
        restartStub(setOf("maven2"))
        settings(
            """
            generic("central") {
                url = uri("${stub.baseUrl}/maven2")
                includeGroups.add("net.snobo.probe")
            }
            """,
            extra = printRepositories(),
        )
        resolveProbeBuild()

        val result = runner("resolveProbe").build()

        assertTrue(result.output.contains("probe-1.0.jar"), result.output)
        assertTrue(result.output.contains("REPO=central ${stub.baseUrl}/maven2"), result.output)
        assertTrue(!result.output.contains("REPO=central-"), "expected a single repository: ${result.output}")
        val requests = stub.requests.filter { it.path.startsWith("/maven2/net/snobo/probe/") }
        assertTrue(requests.isNotEmpty(), "expected requests to the url, got ${stub.requests}")
    }

    @Test
    fun `an exclusive store hides its groups from other repositories`() {
        restartStub(setOf("exclusive", "fallback"))
        resolutionProject(
            """
            generic("exclusive") {
                releasesUrl = uri("${stub.baseUrl}/exclusive")
                includeGroups.add("net.snobo.probe")
                exclusive = true
            }
            generic("fallback") {
                releasesUrl = uri("${stub.baseUrl}/fallback")
                includeGroups.add("net.snobo.probe")
            }
            """,
        )

        val result = runner("resolveProbe").build()

        assertTrue(result.output.contains("probe-1.0.jar"), result.output)
        val exclusiveRequests = stub.requests.filter { it.path.startsWith("/exclusive/net/snobo/probe/") }
        assertTrue(exclusiveRequests.isNotEmpty(), "expected the exclusive store to resolve: ${stub.requests}")
        val fallbackRequests = stub.requests.filter { it.path.startsWith("/fallback/") }
        assertTrue(fallbackRequests.isEmpty(), "the exclusive store must hide the group: ${stub.requests}")
    }

    @Test
    fun `an exclusive store does not fall through for a missing artifact`() {
        restartStub(setOf("fallback"))
        resolutionProject(
            """
            generic("exclusive") {
                releasesUrl = uri("${stub.baseUrl}/exclusive")
                includeGroups.add("net.snobo.probe")
                exclusive = true
            }
            generic("fallback") {
                releasesUrl = uri("${stub.baseUrl}/fallback")
                includeGroups.add("net.snobo.probe")
            }
            """,
        )

        val result = runner("resolveProbe").buildAndFail()

        assertTrue(result.output.contains("Could not find net.snobo.probe:probe:1.0"), result.output)
        val fallbackRequests = stub.requests.filter { it.path.startsWith("/fallback/") }
        assertTrue(fallbackRequests.isEmpty(), "the missing artifact must not fall through: ${stub.requests}")
    }

    @Test
    fun `exclusive without includeGroups fails`() {
        settings(
            """
            generic("exclusive") {
                releasesUrl = uri("${stub.baseUrl}/exclusive")
                exclusive = true
            }
            """,
        )
        write("build.gradle.kts", "plugins { `java-library` }")

        val failure = runner("help").buildAndFail()

        assertTrue(failure.output.contains("sets 'exclusive = true' but has no 'includeGroups'"), failure.output)
    }

    @Test
    fun `a disabled store is not validated or registered`() {
        settings(
            """
            nexus("off") {
                enabled = false
            }
            generic("oss") {
                releasesUrl = uri("${stub.baseUrl}/releases")
                includeGroups.add("net.snobo.probe")
            }
            """,
            extra = printRepositoryNames(),
        )
        write("build.gradle.kts", "plugins { `java-library` }")

        val result = runner("help").build()

        assertTrue(result.output.contains("DEPENDENCY_REPOS=[oss]"), result.output)
        assertTrue(!result.output.contains("has no releases repository"), result.output)
        assertTrue(!result.output.contains("no credentials"), result.output)
    }

    @Test
    fun `info diagnostics show effective values without secrets`() {
        credentials()
        settings(
            """
            reposilite("oss") {
                url = uri("${stub.baseUrl}")
                includeGroups.add("net.snobo.probe")
            }
            """,
        )
        write("build.gradle.kts", "plugins { `java-library` }")

        val result = runner("help", "--info").build()

        assertTrue(result.output.contains("Artifact store 'oss' (reposilite)"), result.output)
        assertTrue(result.output.contains("releases=${stub.baseUrl}/releases"), result.output)
        assertTrue(result.output.contains("snapshots=${stub.baseUrl}/snapshots"), result.output)
        assertTrue(result.output.contains("auth=basic"), result.output)
        assertTrue(result.output.contains("groups=[net.snobo.probe]"), result.output)
        assertTrue(result.output.contains("repository='oss'"), result.output)
        assertTrue(!result.output.contains("stub-secret"), result.output)
        assertTrue(!result.output.contains("net-snobo-publisher"), result.output)
    }

    @Test
    fun `a fully disabled store is not resolved or validated`() {
        settings(
            """
            nexus("off") {
                forDependencies = false
                forPlugins = false
            }
            """,
            extra = printRepositoryNames(),
        )
        write("build.gradle.kts", "plugins { `java-library` }")

        val result = runner("help").build()

        assertTrue(
            result.output.contains("has both 'forDependencies' and 'forPlugins' set to false"),
            result.output,
        )
        assertTrue(result.output.contains("DEPENDENCY_REPOS=[]"), result.output)
        assertTrue(!result.output.contains("no credentials"), result.output)
        assertTrue(!result.output.contains("has no releases repository"), result.output)
    }

    @Test
    fun `a snapshotsUrl registers a second repository`() {
        restartStub(setOf("releases", "snapshots"))
        settings(
            """
            generic("central") {
                releasesUrl = uri("${stub.baseUrl}/releases")
                snapshotsUrl = uri("${stub.baseUrl}/snapshots")
            }
            """,
            extra = printRepositories(),
        )
        write("build.gradle.kts", "plugins { `java-library` }")

        val result = runner("help").build()

        assertTrue(result.output.contains("REPO=central-Releases ${stub.baseUrl}/releases"), result.output)
        assertTrue(result.output.contains("REPO=central-Snapshots ${stub.baseUrl}/snapshots"), result.output)
    }

    @Test
    fun `release and snapshot repositories only serve their own version kind`() {
        restartStub(setOf("releases", "snapshots"))
        settings(
            """
            generic("store") {
                releasesUrl = uri("${stub.baseUrl}/releases")
                snapshotsUrl = uri("${stub.baseUrl}/snapshots")
                includeGroups.add("net.snobo.probe")
            }
            """,
        )
        write(
            "build.gradle.kts",
            """
            plugins { `java-library` }

            val releaseProbe by configurations.creating
            val snapshotProbe by configurations.creating

            dependencies {
                releaseProbe("net.snobo.probe:probe:1.0")
                snapshotProbe("net.snobo.probe:probe:1.0-SNAPSHOT")
            }

            tasks.register("resolveProbe") {
                inputs.files(releaseProbe, snapshotProbe)
                doLast {
                    println("RELEASE=" + releaseProbe.files.map { it.name }.sorted())
                    println("SNAPSHOT=" + snapshotProbe.files.map { it.name }.sorted())
                }
            }
            """,
        )

        val result = runner("resolveProbe", "--refresh-dependencies").build()

        assertTrue(result.output.contains("probe-1.0.jar"), result.output)
        assertTrue(result.output.contains("probe-1.0-SNAPSHOT.jar"), result.output)
        assertTrue(
            stub.requests.none { it.path.contains("/releases/") && it.path.contains("1.0-SNAPSHOT") },
            "the releases repository received a snapshot request: ${stub.requests}",
        )
        assertTrue(
            stub.requests.none { it.path.contains("/snapshots/") && it.path.contains("/1.0/") },
            "the snapshots repository received a release request: ${stub.requests}",
        )
    }

    @Test
    fun `the DSL beats a Gradle property and a Gradle property beats the environment`() {
        write(
            "gradle.properties",
            """
            artifactStore.dsl.url=${stub.baseUrl}/dsl-property
            artifactStore.dsl.releasesRepository=dsl-property-route
            artifactStore.props.url=${stub.baseUrl}/props-property
            artifactStore.props.releasesRepository=props-property-route
            """.trimIndent(),
        )
        settings(
            """
            reposilite("dsl") {
                url = uri("${stub.baseUrl}/dsl-dsl")
                releasesRepository = "dsl-dsl-route"
            }
            reposilite("props")
            reposilite("env")
            """,
            extra = printRepositories(),
        )
        write("build.gradle.kts", "plugins { `java-library` }")

        val result =
            runner(
                "help",
                extraEnvironment =
                    mapOf(
                        "ARTIFACT_STORE_DSL_URL" to "${stub.baseUrl}/dsl-env",
                        "ARTIFACT_STORE_DSL_RELEASES_REPOSITORY" to "dsl-env-route",
                        "ARTIFACT_STORE_PROPS_URL" to "${stub.baseUrl}/props-env",
                        "ARTIFACT_STORE_PROPS_RELEASES_REPOSITORY" to "props-env-route",
                        "ARTIFACT_STORE_ENV_URL" to "${stub.baseUrl}/env-env",
                        "ARTIFACT_STORE_ENV_RELEASES_REPOSITORY" to "env-route",
                    ),
            ).build()

        assertTrue(
            result.output.contains("REPO=dsl-Releases ${stub.baseUrl}/dsl-dsl/dsl-dsl-route"),
            result.output,
        )
        assertTrue(
            result.output.contains("REPO=props-Releases ${stub.baseUrl}/props-property/props-property-route"),
            result.output,
        )
        assertTrue(
            result.output.contains("REPO=env-Releases ${stub.baseUrl}/env-env/env-route"),
            result.output,
        )
    }

    @Test
    fun `store names that normalize to the same environment prefix fail`() {
        settings(
            """
            reposilite("a-b")
            generic("a_b")
            """,
        )
        write("build.gradle.kts", "plugins { `java-library` }")

        val failure = runner("help").buildAndFail()

        assertTrue(failure.output.contains("share the environment variable prefix"), failure.output)
        assertTrue(failure.output.contains("ARTIFACT_STORE_A_B_"), failure.output)
        assertTrue(failure.output.contains("'a-b'") && failure.output.contains("'a_b'"), failure.output)
    }

    @Test
    fun `a dependency-only and a plugin-only store may share a repository name`() {
        settings(
            """
            reposilite("dep") {
                url = uri("${stub.baseUrl}")
                repositoryName = "shared"
                forPlugins = false
            }
            nexus("plug") {
                url = uri("${stub.baseUrl}")
                repositoryName = "shared"
                forDependencies = false
            }
            """,
            extra = printRepositoryNames(),
        )
        write("build.gradle.kts", "plugins { `java-library` }")

        val result = runner("help").build()

        assertTrue(
            result.output.contains("DEPENDENCY_REPOS=[shared-Releases, shared-Snapshots]"),
            result.output,
        )
    }

    @Test
    fun `a store without any url names every url source in the failure`() {
        settings("nexus(\"corp\")")
        write("build.gradle.kts", "plugins { `java-library` }")

        val failure = runner("help").buildAndFail()

        assertTrue(failure.output.contains("has no releases repository"), failure.output)
        assertTrue(failure.output.contains("'url'"), failure.output)
        assertTrue(failure.output.contains("artifactStore.corp.url"), failure.output)
        assertTrue(failure.output.contains("ARTIFACT_STORE_CORP_URL"), failure.output)
        assertTrue(failure.output.contains("'releasesUrl'"), failure.output)
        assertTrue(failure.output.contains("artifactStore.corp.releasesUrl"), failure.output)
        assertTrue(failure.output.contains("ARTIFACT_STORE_CORP_RELEASES_URL"), failure.output)
        assertTrue(failure.output.contains("'releasesRepository'"), failure.output)
        assertTrue(failure.output.contains("artifactStore.corp.releasesRepository"), failure.output)
        assertTrue(failure.output.contains("ARTIFACT_STORE_CORP_RELEASES_REPOSITORY"), failure.output)
    }

    @Test
    fun `a relative url from a Gradle property is rejected`() {
        write("gradle.properties", "artifactStore.central.url=relative/maven2")
        settings("generic(\"central\")")
        write("build.gradle.kts", "plugins { `java-library` }")

        val failure = runner("help").buildAndFail()

        assertTrue(failure.output.contains("not an absolute URL"), failure.output)
        assertTrue(failure.output.contains("Artifact store 'central' url"), failure.output)
    }

    @Test
    fun `refreshChangingModules configures the build and keeps the store resolvable`() {
        credentials()
        resolutionProject(
            """
            refreshChangingModules = true
            reposilite("oss") {
                url = uri("${stub.baseUrl}")
                includeGroups.add("net.snobo.probe")
            }
            """,
        )

        val result = runner("resolveProbe").build()

        assertTrue(result.output.contains("probe-1.0.jar"), result.output)
    }
}
