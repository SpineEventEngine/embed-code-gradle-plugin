package io.spine.embedcode.gradle.dependency

/** JUnit dependencies used by tests. */
object JUnit {

    const val version = "6.1.1"
    private const val group = "org.junit.jupiter"

    // https://github.com/junit-team/junit5
    object Jupiter {
        const val lib = "$group:junit-jupiter:$version"
    }

    // https://github.com/junit-team/junit5/tree/main/junit-platform-launcher
    object PlatformLauncher {
        const val version = "6.1.1"
        const val lib = "org.junit.platform:junit-platform-launcher:$version"
    }
}
