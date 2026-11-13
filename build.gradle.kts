import groovy.util.Node
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.plugin.compatibility.compatibility
import java.util.Properties

plugins {
    kotlin("jvm") version "2.4.21"
    `java-gradle-plugin`
    `maven-publish`
    // The shared net.snobo style: `ktlintCheck` runs inside `check` and `ktlintFormat` rewrites the sources.
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    // Shades the support subproject into the plugin jar, so the published plugin stays self-contained.
    id("com.gradleup.shadow") version "9.6.1"
    // Publishes the plugin to the Gradle Plugin Portal. Only the release workflow runs the upload.
    id("com.gradle.plugin-publish") version "2.2.1"
}

group = "net.snobo"

// The release workflow passes the version from the release tag. Every other build is a local snapshot.
version = providers.gradleProperty("version").orElse("local-SNAPSHOT").get()

allprojects {
    // Lock every resolvable configuration, including the TestKit fixtures. Plugin and buildscript
    // classpaths are not covered by locking; dependency verification covers those artifacts.
    dependencyLocking {
        lockAllConfigurations()
    }
    // Gradle 9 made these the defaults; Gradle 8.14 still needs the explicit block, and the
    // release and verification jobs must produce byte-identical archives.
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}

tasks.register("resolveAndLockAll") {
    group = "verification"
    description = "Resolves every resolvable configuration and writes dependency lock state."
    notCompatibleWithConfigurationCache("Resolves configurations at execution time.")
    doFirst {
        require(gradle.startParameter.isWriteDependencyLocks) {
            "Run this task with --write-locks."
        }
    }
    doLast {
        allprojects.forEach { target ->
            target.configurations.filter { it.isCanBeResolved }.forEach { it.resolve() }
        }
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // Keep the plugin loadable by Gradle 8.14 and newer, which still run on Java 17.
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

/**
 * Only the support module goes into the shaded jar. Gradle and the Kotlin standard library are provided by
 * the Gradle runtime of the consumer and must not be bundled.
 */
val shaded =
    configurations.create("shaded") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }

dependencies {
    implementation(project(":artifact-store-support"))
    // The shade takes the support classes only, not its transitive Kotlin stdlib or Gradle API.
    shaded(project(":artifact-store-support")) {
        isTransitive = false
    }
    testImplementation(kotlin("test"))
    // The DSL tests use the support module's test fixtures. Depending on the fixtures configuration directly
    // keeps the fixtures out of this project's publications.
    testImplementation(project(":artifact-store-support", "testFixturesRuntimeElements"))
}

val projectUrl = "https://github.com/DevSnobo/resolution-gradle-plugin"
val projectDescription =
    "Registers Reposilite, Nexus, Artifactory or generic Maven stores as dependency and plugin repositories " +
        "in settings.gradle.kts."

gradlePlugin {
    website = "https://github.com/DevSnobo/resolution-gradle-plugin"
    vcsUrl = "https://github.com/DevSnobo/resolution-gradle-plugin.git"
    plugins {
        create("artifactStores") {
            id = "net.snobo.artifact-stores"
            implementationClass = "net.snobo.artifactstores.ArtifactStoresPlugin"
            displayName = "Artifact stores"
            description = projectDescription
            tags.addAll("maven", "repository", "reposilite", "nexus", "artifactory")
            compatibility {
                features {
                    configurationCache = true
                    isolatedProjects = false
                }
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
    // A different Gradle version for the TestKit builds changes the outcome, so it must invalidate the task.
    inputs.property(
        "artifactStoreTestGradleVersion",
        providers.environmentVariable("ARTIFACT_STORE_TEST_GRADLE_VERSION").orElse("wrapper"),
    )
}

/**
 * Fails when the wrapper stops pinning its distribution checksum, or when the properties file and the
 * supported Gradle version disagree. setup-gradle validates the wrapper JAR itself.
 */
val verifyWrapper =
    tasks.register("verifyWrapper") {
        group = "verification"
        description = "Checks that the Gradle wrapper is pinned to the expected version and checksum."
        val propertiesFile = layout.projectDirectory.file("gradle/wrapper/gradle-wrapper.properties")
        inputs.file(propertiesFile)
        doLast {
            val properties =
                Properties().apply {
                    propertiesFile.asFile.inputStream().use { load(it) }
                }
            val expectedVersion = "9.9.1"
            val distributionUrl = properties.getProperty("distributionUrl").orEmpty()
            check(distributionUrl.endsWith("gradle-$expectedVersion-bin.zip")) {
                "The wrapper distribution must be Gradle $expectedVersion (bin), but distributionUrl is: $distributionUrl"
            }
            val checksum = properties.getProperty("distributionSha256Sum").orEmpty()
            check(checksum.matches(Regex("[0-9a-f]{64}"))) {
                "gradle-wrapper.properties must pin distributionSha256Sum to the SHA-256 of the distribution ZIP."
            }
        }
    }

tasks.check {
    dependsOn(verifyWrapper)
}

tasks.jar {
    // shadowJar owns the empty classifier; the plain jar is published under an explicit classifier.
    archiveClassifier.set("main")
}

tasks.shadowJar {
    // The plugin-publish plugin publishes the shadow jar as the main plugin artifact, which requires that
    // shadowJar carries no classifier.
    archiveClassifier.set("")
    configurations = listOf(shaded)
    // The support module and this project both contribute META-INF/*.kotlin_module; keep the first copy.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.processResources {
    from(rootProject.file("LICENSE")) { into("META-INF") }
    from(rootProject.file("NOTICE")) { into("META-INF") }
}

tasks.withType<Jar>().matching { it.name == "sourcesJar" }.configureEach {
    // The support classes are shaded into the plugin jar, so their sources belong in the published sources jar.
    from(project(":artifact-store-support").file("src/main/kotlin"))
    from(rootProject.file("LICENSE")) { into("META-INF") }
    from(rootProject.file("NOTICE")) { into("META-INF") }
}

// The Gradle metadata of pluginMaven would still name the support project as a dependency, but its classes are
// inside the shaded jar. The plugin artifact is published with a POM only.
tasks.withType<GenerateModuleMetadata>().configureEach {
    if (name == "generateMetadataFileForPluginMavenPublication") {
        enabled = false
    }
}

publishing {
    // A clean clone has no reposiliteUrl, so it configures no remote repository and `check` needs
    // no private infrastructure. The release workflow supplies the URL and the write token.
    val baseUrl = providers.gradleProperty("reposiliteUrl").orNull
    if (baseUrl != null) {
        repositories {
            maven {
                name = "Reposilite"
                val route = if (version.toString().endsWith("-SNAPSHOT")) "snapshots" else "releases"
                url = uri("${baseUrl.trimEnd('/')}/$route")
                isAllowInsecureProtocol = baseUrl.startsWith("http://")
                val publisher = providers.gradleProperty("reposilitePublisher").orNull
                val token = providers.gradleProperty("reposiliteToken").orNull
                if (!publisher.isNullOrBlank() && !token.isNullOrBlank()) {
                    credentials {
                        username = publisher
                        password = token
                    }
                }
            }
        }
    }
}

afterEvaluate {
    extensions.configure<PublishingExtension> {
        publications.named<MavenPublication>("pluginMaven") {
            // The support classes are bundled, so net.snobo:artifact-store-support must not appear in the POM.
            // The logic is inline: a build-script function reference is not serializable by the configuration cache.
            pom.withXml {
                val root = asNode()
                root.children().filterIsInstance<Node>().forEach { container ->
                    if (container.name().toString().substringAfterLast('}') != "dependencies") return@forEach
                    container.children().filterIsInstance<Node>().forEach { dependency ->
                        if (dependency.name().toString().substringAfterLast('}') != "dependency") return@forEach
                        val bundled =
                            dependency.children().filterIsInstance<Node>().any {
                                it.name().toString().substringAfterLast('}') == "artifactId" &&
                                    it.text() == "artifact-store-support"
                            }
                        if (bundled) {
                            container.remove(dependency)
                        }
                    }
                }
            }
            pom {
                name.set("Artifact Stores Gradle Plugin")
                description.set(projectDescription)
                url.set(projectUrl)
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }
                scm {
                    url.set(projectUrl)
                    connection.set("scm:git:$projectUrl.git")
                    developerConnection.set("scm:git:ssh://git@github.com/DevSnobo/resolution-gradle-plugin.git")
                }
            }
        }
    }
}

// The engine is pinned, so every net.snobo repository formats with the same ktlint version; the rules come from
// the shared .editorconfig at the repository root (ktlint_official, 120 columns, 4 space indent, trailing commas).
ktlint {
    version.set("1.8.0")
}
