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
import io.spine.embedcode.gradle.dependency.LicenseReport
import io.spine.embedcode.gradle.report.DependencyMarkdownReportRenderer
import io.spine.embedcode.gradle.report.DependencyPomReportRenderer
import io.spine.embedcode.gradle.report.UpdateDependencyReports

plugins {
    base
}

apply(from = "version.gradle.kts")
apply(plugin = LicenseReport.id)

val embedCodePluginVersion =
    rootProject.extra.properties["embedCodePluginVersion"] as? String
        ?: error(
            "The `embedCodePluginVersion` property must be defined as a string " +
                "in `version.gradle.kts`.",
        )

allprojects {
    group = "io.spine.tools"
    version = embedCodePluginVersion
}

val dependencyReportDirectory = layout.buildDirectory.dir("reports/dependencies")
extensions.configure<LicenseReportExtension> {
    outputDir = dependencyReportDirectory.get().asFile.absolutePath
    projects = (listOf(project) + subprojects).toTypedArray()
    buildScriptProjects = projects
    configurations = ALL
    excludeOwnGroup = true
    renderers =
        arrayOf<ReportRenderer>(
            DependencyMarkdownReportRenderer(
                "dependencies.md",
                "$group:$name:$version",
            ),
            DependencyPomReportRenderer(),
        )
}
tasks.named("generateLicenseReport") {
    inputs.property("projectVersion", provider { version.toString() })
}

val generatedPomFile =
    layout.buildDirectory.file("reports/dependencies/pom.xml")

tasks.register<UpdateDependencyReports>("generateDependencyReports") {
    description = "Updates the tracked Maven POM and dependency license report."
    group = "documentation"
    dependsOn(
        "generateLicenseReport",
    )
    generatedPom.set(generatedPomFile)
    generatedLicenses.set(dependencyReportDirectory.map { it.file("dependencies.md") })
    trackedPom.set(layout.projectDirectory.file("pom.xml"))
    trackedLicenses.set(layout.projectDirectory.file("dependencies.md"))
}
