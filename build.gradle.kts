plugins {
    base
}

apply(from = "version.gradle.kts")

allprojects {
    group = "io.spine.tools"
    version = rootProject.extra["embedCodePluginVersion"]!!
}
