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

package io.spine.embedcode.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

/**
 * Registers automatic installation and execution tasks for Embed Code.
 */
public class EmbedCodePlugin : Plugin<Project> {

    /**
     * Applies the plugin to [project].
     */
    override fun apply(project: Project) {
        project.logger.info("Applying the Embed Code plugin to project `{}`.", project.path)
        val checkTaskName = availableTaskName(project, "checkEmbedding")
        val embedTaskName = availableTaskName(project, "embedCode")
        val installTaskName = availableTaskName(project, "installEmbedCode")
        val extension = project.extensions.create(
            "embedCode",
            EmbedCodeExtension::class.java,
        )
        extension.docIncludes.convention(listOf("**/*.md", "**/*.html"))
        extension.docExcludes.convention(emptyList())
        extension.namedSources.convention(emptyMap())
        extension.separator.convention("...")
        extension.info.convention(false)
        extension.stacktrace.convention(false)
        extension.downloadBaseUrl.convention(DEFAULT_DOWNLOAD_BASE_URL)

        val operatingSystem = System.getProperty("os.name").orEmpty()
        val architecture = System.getProperty("os.arch").orEmpty()
        // The installed file name always tracks the host operating system.
        // Overriding the task's `operatingSystem` input changes asset selection only.
        val installedExecutableName = EmbedCodePlatform.installedExecutableName(operatingSystem)
        val installationDirectory = project.layout.buildDirectory.dir("embed-code")
        val installTask = project.tasks.register(
            installTaskName,
            InstallEmbedCodeTask::class.java,
        ) { task ->
            task.description = "Installs the requested Embed Code executable"
            task.version.set(extension.version)
            task.sha256.set(extension.sha256)
            task.githubToken.set(extension.githubToken)
            task.downloadBaseUrl.set(extension.downloadBaseUrl)
            task.operatingSystem.set(operatingSystem)
            task.architecture.set(architecture)
            task.offline.set(project.gradle.startParameter.isOffline)
            task.installationDirectory.set(installationDirectory)
            task.installationDirectory.disallowChanges()
            val requestedTag = task.version.map(::validateVersion)
            val installationSubdirectory = requestedTag.map { tag ->
                if (tag.isEmpty()) "embed-code/latest" else "embed-code/versions/$tag"
            }.orElse("embed-code/latest")
            // Keep explicit tags separate so one named `latest` cannot alias the rolling cache.
            task.executableFile.set(
                project.layout.buildDirectory.file(
                    installationSubdirectory.map { directory ->
                        "$directory/$installedExecutableName"
                    },
                ),
            )
            task.resolvedVersionFile.set(
                project.layout.buildDirectory.file("embed-code/latest/version.txt"),
            )
            task.assetChecksumFile.set(
                project.layout.buildDirectory.file(
                    installationSubdirectory.map { directory -> "$directory/asset.sha256" },
                ),
            )
            task.executableChecksumFile.set(
                project.layout.buildDirectory.file(
                    installationSubdirectory.map { directory ->
                        "$directory/executable.sha256"
                    },
                ),
            )
            task.sourceIdentityFile.set(
                project.layout.buildDirectory.file(
                    installationSubdirectory.map { directory -> "$directory/source.sha256" },
                ),
            )
            // Intentionally rerun and re-hash the cached executable before every execution.
            task.outputs.upToDateWhen { false }
        }

        registerExecutionTask(
            project,
            extension,
            installTask,
            checkTaskName,
            "Checks embedded code snippets are up to date",
            "check",
        )
        registerExecutionTask(
            project,
            extension,
            installTask,
            embedTaskName,
            "Updates embedded code snippets from source files",
            "embed",
        )
        project.logger.info(
            "Registered Embed Code tasks `{}` and `{}` in project `{}`.",
            checkTaskName,
            embedTaskName,
            project.path,
        )
    }

    private companion object {

        const val DEFAULT_DOWNLOAD_BASE_URL =
            "https://github.com/SpineEventEngine/embed-code-go/releases"
        const val TASK_GROUP = "embed code"

        /**
         * Registers one mode-specific execution task backed by [installTask].
         */
        fun registerExecutionTask(
            project: Project,
            extension: EmbedCodeExtension,
            installTask: TaskProvider<InstallEmbedCodeTask>,
            name: String,
            description: String,
            mode: String,
        ) {
            project.tasks.register(name, EmbedCodeTask::class.java) { task ->
                task.group = TASK_GROUP
                task.description = description
                task.mode.set(mode)
                task.codePath.set(extension.codePath)
                task.namedSources.set(extension.namedSources)
                task.namedSourceDirectories.from(extension.namedSourceDirectories)
                task.docsPath.set(extension.docsPath)
                task.docIncludes.set(extension.docIncludes)
                task.docExcludes.set(extension.docExcludes)
                task.separator.set(extension.separator)
                task.info.set(extension.info)
                task.stacktrace.set(extension.stacktrace)
                task.executableFile.set(installTask.flatMap { it.executableFile })
                task.workingDirectory.set(project.layout.projectDirectory)
            }
        }

        /**
         * Returns [preferredName], prepending underscores until it is unused.
         */
        fun availableTaskName(project: Project, preferredName: String): String {
            var candidate = preferredName
            while (project.tasks.names.contains(candidate)) {
                candidate = "_$candidate"
            }
            return candidate
        }
    }
}
