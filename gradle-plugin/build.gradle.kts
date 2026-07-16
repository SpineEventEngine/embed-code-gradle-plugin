/*
 * Copyright 2026, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import io.spine.embedcode.gradle.dependency.PluginPublish
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.plugin.compatibility.compatibility

plugins {
    id("jvm-module")
    `java-gradle-plugin`
    `maven-publish`
}

apply(plugin = PluginPublish.id)

base {
    archivesName.set("embed-code-gradle-plugin")
}

java {
    withJavadocJar()
    withSourcesJar()
}

tasks.withType<Jar>().configureEach {
    from(rootProject.layout.projectDirectory.file("LICENSE")) {
        into("META-INF")
    }
}

// Getter docs use concise "Returns..." prose instead of duplicate `@return` tags.
tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:-missing", true)
}

tasks.test {
    inputs.property(
        "embedCodeGradle7JavaHome",
        providers.environmentVariable("EMBED_CODE_GRADLE_7_JAVA_HOME")
            .orElse(providers.environmentVariable("JAVA_HOME_17_X64"))
            .orElse(""),
    )
}

gradlePlugin {
    website.set("https://github.com/SpineEventEngine/embed-code-gradle-plugin")
    vcsUrl.set("https://github.com/SpineEventEngine/embed-code-gradle-plugin")
    plugins {
        create("embedCode") {
            id = "io.spine.embed-code"
            implementationClass = "io.spine.embedcode.gradle.EmbedCodePlugin"
            displayName = "Embed Code Gradle Plugin"
            description =
                "Runs Embed Code from Gradle without a separately installed executable."
            tags.set(listOf("documentation", "code-samples"))
            compatibility {
                features {
                    configurationCache = true
                }
            }
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        if (name == "pluginMaven") {
            artifactId = "embed-code-gradle-plugin"
        }
        pom {
            name.set("Embed Code Gradle Plugin")
            description.set(
                "Runs Embed Code from Gradle without a separately installed executable.",
            )
            url.set("https://github.com/SpineEventEngine/embed-code-gradle-plugin")
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    id.set("SpineEventEngine")
                    name.set("Spine Event Engine")
                    url.set("https://github.com/SpineEventEngine")
                }
            }
            scm {
                url.set("https://github.com/SpineEventEngine/embed-code-gradle-plugin")
                connection.set(
                    "scm:git:https://github.com/SpineEventEngine/" +
                        "embed-code-gradle-plugin.git",
                )
                developerConnection.set(
                    "scm:git:ssh://git@github.com/SpineEventEngine/" +
                        "embed-code-gradle-plugin.git",
                )
            }
        }
    }
}
