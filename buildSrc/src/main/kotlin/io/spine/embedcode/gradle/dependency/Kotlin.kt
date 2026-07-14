package io.spine.embedcode.gradle.dependency

/** Kotlin dependencies used by the project. */
object Kotlin {

    const val version = "2.4.0"
    private const val group = "org.jetbrains.kotlin"

    // https://github.com/JetBrains/kotlin/tree/master/libraries/tools/kotlin-gradle-plugin
    object GradlePlugin {
        const val lib = "$group:kotlin-gradle-plugin:$version"
    }
}
