package io.spine.embedcode.gradle.dependency

// https://github.com/gradle/plugin-publish-plugin
object PluginPublish {

    const val version = "2.1.1"
    const val id = "com.gradle.plugin-publish"

    const val lib = "$id:$id.gradle.plugin:$version"
}
