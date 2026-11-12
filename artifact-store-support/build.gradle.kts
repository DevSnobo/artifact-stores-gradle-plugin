plugins {
    kotlin("jvm")
    `java-test-fixtures`
    id("org.jlleitschuh.gradle.ktlint")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // Never bundle Gradle: the support classes run inside the Gradle daemon of the consumer.
    compileOnly(gradleApi())
    testImplementation(kotlin("test"))
    testImplementation(gradleApi())
}

tasks.test {
    useJUnitPlatform()
}

// The engine is pinned, so every net.snobo repository formats with the same ktlint version; the rules come from
// the shared .editorconfig at the repository root (ktlint_official, 120 columns, 4 space indent, trailing commas).
ktlint {
    version.set("1.8.0")
}
