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

import com.github.jk1.license.LicenseReportExtension
import com.github.jk1.license.LicenseReportExtension.ALL
import com.github.jk1.license.render.ReportRenderer
import com.github.jk1.license.task.ReportTask
import io.spine.embedcode.gradle.BuildSettings
import io.spine.embedcode.gradle.dependency.LicenseReport
import io.spine.embedcode.gradle.report.DependencyMarkdownReportRenderer
import io.spine.embedcode.gradle.report.GenerateDependencyPom
import io.spine.embedcode.gradle.report.UpdateDependencyReports
import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Sync
import org.gradle.plugin.compatibility.compatibility
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

plugins {
    id("jvm-module")
    id("dokka-configuration")
    `java-gradle-plugin`
    jacoco
    `maven-publish`
}

apply(plugin = "com.gradle.plugin-publish")
apply(plugin = LicenseReport.id)

dependencies {
    // Gradle supplies Kotlin at runtime, so the plugin does not publish the standard library.
    compileOnly(kotlin("stdlib"))
    testCompileOnly(kotlin("stdlib"))
    // Unit tests no longer inherit TestKit's Gradle and Kotlin runtime; supply both explicitly.
    testRuntimeOnly(kotlin("stdlib"))
    testRuntimeOnly(gradleApi())
}

val embedCodeAppVersion =
    rootProject.extra.properties["embedCodeAppVersion"] as? String
        ?: error(
            "The `embedCodeAppVersion` property must be defined as a string " +
                "in `version.gradle.kts`.",
        )
val generateEmbedCodeVersion = tasks.register<Sync>("generateEmbedCodeVersion") {
    description = "Generates the default Embed Code application version."
    inputs.property("embedCodeAppVersion", embedCodeAppVersion)
    from(layout.projectDirectory.dir("src/main/templates")) {
        filter<ReplaceTokens>(
            "tokens" to mapOf("embedCodeAppVersion" to embedCodeAppVersion),
        )
    }
    into(layout.buildDirectory.dir("generated/sources/embedCodeVersion/kotlin"))
}

kotlin.sourceSets.named("main") {
    kotlin.srcDir(generateEmbedCodeVersion)
}

val functionalTestSourceSet = sourceSets.create("functionalTest")
functionalTestSourceSet.compileClasspath += sourceSets.main.get().output
functionalTestSourceSet.runtimeClasspath += sourceSets.main.get().output

kotlin {
    target.compilations.getByName("functionalTest") {
        associateWith(target.compilations.getByName("main"))
    }
}

configurations[functionalTestSourceSet.implementationConfigurationName].extendsFrom(
    configurations.testImplementation.get(),
)
configurations[functionalTestSourceSet.compileOnlyConfigurationName].extendsFrom(
    configurations.testCompileOnly.get(),
)
configurations[functionalTestSourceSet.runtimeOnlyConfigurationName].extendsFrom(
    configurations.testRuntimeOnly.get(),
)

val functionalTest = tasks.register<Test>("functionalTest") {
    description = "Runs TestKit functional tests."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = functionalTestSourceSet.output.classesDirs
    classpath = functionalTestSourceSet.runtimeClasspath
    useJUnitPlatform()
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(BuildSettings.bytecodeVersion))
        },
    )
    outputs.doNotCacheIf("TestKit builds depend on the host environment.") { true }
    shouldRunAfter(tasks.test)
}

val testKitCoverageData = layout.buildDirectory.file("jacoco/testKit.exec")
val coverageFunctionalTest = tasks.register<Test>("coverageFunctionalTest") {
    description = "Runs TestKit functional tests and collects plugin coverage."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = functionalTestSourceSet.output.classesDirs
    classpath = functionalTestSourceSet.runtimeClasspath
    useJUnitPlatform()
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(BuildSettings.bytecodeVersion))
        },
    )
    extensions.configure<JacocoTaskExtension> {
        isEnabled = false
        setDestinationFile(testKitCoverageData.map { file -> file.asFile })
    }
    outputs.file(testKitCoverageData)
    outputs.doNotCacheIf("TestKit builds depend on the host environment.") { true }
    notCompatibleWithConfigurationCache(
        "JaCoCo coverage of forked TestKit builds requires execution-time agent paths.",
    )
    doFirst {
        val coverageDataFile = testKitCoverageData.get().asFile
        coverageDataFile.delete()
        val jacocoExtension = extensions.getByType<JacocoTaskExtension>()
        val childJvmArgument =
            Regex("""destfile=[^,]+""").replaceFirst(
                jacocoExtension.asJvmArg,
                Regex.escapeReplacement("destfile=${coverageDataFile.absolutePath}"),
            )
        systemProperty(
            "io.spine.embedcode.gradle.testkit.coverage.jvm-argument",
            childJvmArgument,
        )
    }
    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn(functionalTest)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test, coverageFunctionalTest)
    executionData(
        tasks.test.map { task ->
            task.extensions.getByType<JacocoTaskExtension>().destinationFile
        },
        coverageFunctionalTest.map { task ->
            task.extensions.getByType<JacocoTaskExtension>().destinationFile
        },
    )
    reports {
        xml.required.set(true)
        html.required.set(false)
        csv.required.set(false)
    }
}

base {
    archivesName.set("embed-code-gradle-plugin")
}

// Register the populated task before `withJavadocJar()` requests this conventional task name.
tasks.register<Jar>("javadocJar") {
    description = "Packages Kotlin API documentation generated by Dokka."
    archiveClassifier.set("javadoc")
    from(
        tasks.dokkaGeneratePublicationHtml.flatMap { task ->
            task.outputDirectory
        },
    )
}

java {
    withJavadocJar()
    withSourcesJar()
}

val generateLicenseReport = tasks.named<ReportTask>("generateLicenseReport")
val generateDependencyPom =
    tasks.register<GenerateDependencyPom>("generateDependencyPom") {
        description = "Generates the root POM that documents direct build dependencies."
        group = "documentation"
        groupId.set(provider { project.group.toString() })
        artifactId.set(rootProject.name)
        projectVersion.set(provider { project.version.toString() })
        outputFile.set(layout.buildDirectory.file("reports/dependencies/pom.xml"))
        dependenciesFrom(configurations)
    }
val generateDependencyReports =
    tasks.register<UpdateDependencyReports>("generateDependencyReports") {
        description =
            "Updates pom.xml and dependencies.md. Dependency report generation does not " +
                "support the configuration cache."
        group = "documentation"
        generatedPom.set(generateDependencyPom.flatMap { task -> task.outputFile })
        generatedLicenses.set(
            layout.file(
                generateLicenseReport.map { task ->
                    task.outputFolder.resolve("dependencies.md")
                },
            ),
        )
        trackedPom.set(rootProject.layout.projectDirectory.file("pom.xml"))
        trackedLicenses.set(rootProject.layout.projectDirectory.file("dependencies.md"))
    }

tasks.withType<Jar>().configureEach {
    from(rootProject.layout.projectDirectory.file("LICENSE")) {
        into("META-INF")
    }
}

gradlePlugin {
    testSourceSets(functionalTestSourceSet)
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

extensions.configure<LicenseReportExtension> {
    outputDir = layout.buildDirectory.dir("reports/dependencies").get().asFile.absolutePath
    projects = arrayOf(project)
    configurations = ALL
    excludeOwnGroup = true
    renderers =
        arrayOf<ReportRenderer>(
            DependencyMarkdownReportRenderer(
                "dependencies.md",
                "$group:${rootProject.name} — dependencies from all plugin-module configurations",
            ),
        )
}
