plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

/**
 * Version of the Kotlin Gradle plugin.
 *
 * Keep in sync with `io.spine.embedcode.gradle.dependency.Kotlin.version`.
 */
val kotlinVersion = "2.4.0"

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
}

kotlin {
    jvmToolchain(17)
}
