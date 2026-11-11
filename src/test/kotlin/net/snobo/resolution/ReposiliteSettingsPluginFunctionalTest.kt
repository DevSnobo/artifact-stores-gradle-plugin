package net.snobo.resolution

import org.gradle.testkit.runner.GradleRunner
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/** Set REPOSILITE_TEST_GRADLE_VERSION to run the whole suite against another Gradle release. */
private val testGradleVersion: String? = System.getenv("REPOSILITE_TEST_GRADLE_VERSION")

class ReposiliteSettingsPluginFunctionalTest {
    private lateinit var projectDir: File
    private lateinit var testKitDir: File
    private lateinit var stub: ReposiliteStub

    @BeforeTest
    fun setUp() {
        projectDir = Files.createTempDirectory("reposilite-resolution-test").toFile()
        testKitDir = Files.createTempDirectory("reposilite-testkit").toFile()
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
        write("gradle.properties", "reposilitePublisher=$name\nreposiliteToken=$secret\n")
    }

    private fun runner(vararg args: String): GradleRunner {
        // CI exports the Reposilite credentials for this build. TestKit builds inherit the environment, so a
        // fixture that expects no credentials would silently see CI's. Keep those variables out of scratch builds.
        val environment =
            System.getenv().filterKeys {
                !it.startsWith("ORG_GRADLE_PROJECT_reposilite") && !it.startsWith("REPOSILITE_")
            }
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

    /** Settings fixture that resolves `net.snobo.probe:probe:1.0` from the stub. */
    private fun resolutionProject(extraReposiliteConfig: String = "") {
        write(
            "settings.gradle.kts",
            """
            plugins { id("net.snobo.resolution") }

            reposilite {
                url = uri("${stub.baseUrl}")
                includeGroups.add("net.snobo.probe")
                $extraReposiliteConfig
            }

            rootProject.name = "resolution-probe"
            """,
        )
        write(
            "build.gradle.kts",
            """
            plugins { `java-library` }

            dependencies { implementation("net.snobo.probe:probe:1.0") }

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

    /**
     * Settings plugin fixture where only the private route hands out artifacts, so resolving `probe` proves
     * that the route was requested.
     */
    private fun privateResolutionProject(usePrivate: Boolean) {
        credentials()
        stub.stop()
        stub = ReposiliteStub(artifactRoutes = setOf("private")).start()
        write(
            "settings.gradle.kts",
            """
            plugins { id("net.snobo.resolution") }

            reposilite {
                url = uri("${stub.baseUrl}")
                includeGroups.add("net.snobo.probe")
                ${if (usePrivate) "usePrivate = true" else ""}
            }

            rootProject.name = "private-probe"
            """,
        )
        write(
            "build.gradle.kts",
            """
            plugins { `java-library` }

            dependencies { implementation("net.snobo.probe:probe:1.0") }

            tasks.register("resolveProbe") {
                val classpath = configurations.getByName("compileClasspath")
                doLast { println("RESOLVED=" + classpath.files.map { it.name }.sorted()) }
            }
            """,
        )
    }

    @Test
    fun `settings plugin resolves artifacts from reposilite using the token`() {
        credentials()
        resolutionProject()

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
    fun `groups outside the filter never contact reposilite`() {
        credentials()
        write(
            "settings.gradle.kts",
            """
            plugins { id("net.snobo.resolution") }

            reposilite {
                url = uri("${stub.baseUrl}")
                includeGroups.add("net.snobo.probe")
            }

            rootProject.name = "filter-probe"
            """,
        )
        write(
            "build.gradle.kts",
            """
            plugins { `java-library` }

            dependencies { implementation("org.apache.commons:commons-lang3:3.14.0") }

            tasks.register("resolveForeign") {
                val classpath = configurations.getByName("compileClasspath")
                doLast { classpath.files.forEach { println("RESOLVED=" + it.name) } }
            }
            """,
        )

        val result = runner("resolveForeign").buildAndFail()

        assertTrue(result.output.contains("Could not find org.apache.commons:commons-lang3"), result.output)
        assertTrue(stub.requests.isEmpty(), "reposilite must not be contacted: ${stub.requests}")
    }

    @Test
    fun `credentials are also read from the reposiliteUsername alias`() {
        write("gradle.properties", "reposiliteUsername=net-snobo-publisher\nreposiliteToken=stub-secret\n")
        resolutionProject()

        val result = runner("resolveProbe").build()

        assertTrue(result.output.contains("probe-1.0.jar"), result.output)
        assertTrue(
            stub.requests.filter { it.path.startsWith("/releases/net/snobo/probe/") }.all { it.authorization != null },
            "expected credentials on every request: ${stub.requests}",
        )
    }

    @Test
    fun `settings plugin reads from the private route when usePrivate is enabled`() {
        privateResolutionProject(usePrivate = true)

        val result = runner("resolveProbe").build()

        assertTrue(result.output.contains("probe-1.0.jar"), result.output)
        assertTrue(
            stub.requests.any { it.path.startsWith("/private/net/snobo/probe/") },
            "expected requests to the private route, got ${stub.requests}",
        )
    }

    @Test
    fun `settings plugin never contacts the private route by default`() {
        privateResolutionProject(usePrivate = false)

        val failure = runner("resolveProbe").buildAndFail()

        assertTrue(failure.output.contains("Could not find net.snobo.probe:probe:1.0"), failure.output)
        assertTrue(
            stub.requests.none { it.path.startsWith("/private/") },
            "the private route must not be contacted: ${stub.requests}",
        )
    }

    @Test
    fun `usePrivate without credentials warns about the protected route`() {
        write(
            "settings.gradle.kts",
            """
            plugins { id("net.snobo.resolution") }

            reposilite {
                url = uri("${stub.baseUrl}")
                includeGroups.add("net.snobo.probe")
                usePrivate = true
            }

            rootProject.name = "private-warning-probe"
            """,
        )
        write("build.gradle.kts", "plugins { `java-library` }")

        val result = runner("help").build()

        assertTrue(result.output.contains("registered without credentials"), result.output)
        assertTrue(result.output.contains("reads fail with 401"), result.output)
    }
}
