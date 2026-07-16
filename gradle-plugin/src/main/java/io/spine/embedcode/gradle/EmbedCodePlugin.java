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

package io.spine.embedcode.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

import java.util.Arrays;
import java.util.Collections;

/** Registers automatic installation and execution tasks for Embed Code. */
public final class EmbedCodePlugin implements Plugin<Project> {

    private static final String DEFAULT_DOWNLOAD_BASE_URL =
            "https://github.com/SpineEventEngine/embed-code-go/releases";
    private static final String TASK_GROUP = "embed code";

    /** Applies the plugin to {@code project}. */
    @Override
    public void apply(Project project) {
        String checkTaskName = availableTaskName(project, "checkEmbedding");
        String embedTaskName = availableTaskName(project, "embedCode");
        EmbedCodeExtension extension = project.getExtensions().create(
                "embedCode",
                EmbedCodeExtension.class
        );
        extension.getDocIncludes().convention(Arrays.asList("**/*.md", "**/*.html"));
        extension.getDocExcludes().convention(Collections.emptyList());
        extension.getNamedSources().convention(Collections.emptyMap());
        extension.getSeparator().convention("...");
        extension.getInfo().convention(false);
        extension.getStacktrace().convention(false);
        extension.getDownloadBaseUrl().convention(DEFAULT_DOWNLOAD_BASE_URL);

        EmbedCodePlatform platform = EmbedCodePlatform.detect(
                System.getProperty("os.name"),
                System.getProperty("os.arch")
        );
        TaskProvider<InstallEmbedCodeTask> installTask = project.getTasks().register(
                "installEmbedCode",
                InstallEmbedCodeTask.class,
                task -> {
                    task.setDescription("Installs the requested Embed Code executable");
                    task.getVersion().set(extension.getVersion());
                    task.getDownloadBaseUrl().set(extension.getDownloadBaseUrl());
                    task.getAssetName().set(platform.getAssetName());
                    task.getExecutableName().set(platform.getExecutableName());
                    task.getExecutableFile().set(
                            project.getLayout().getBuildDirectory().file(
                                    extension.getVersion().map(
                                            version -> "embed-code/" + version
                                                    + '/' + platform.getExecutableName()
                                    ).orElse(
                                            "embed-code/latest/" + platform.getExecutableName()
                                    )
                            )
                    );
                    task.getOutputs().upToDateWhen(ignored -> extension.getVersion().isPresent());
                }
        );

        registerExecutionTask(
                project,
                extension,
                installTask,
                checkTaskName,
                "Checks embedded code snippets are up to date",
                "check"
        );
        registerExecutionTask(
                project,
                extension,
                installTask,
                embedTaskName,
                "Updates embedded code snippets from source files",
                "embed"
        );
    }

    /** Registers one mode-specific execution task backed by {@code installTask}. */
    private static void registerExecutionTask(
            Project project,
            EmbedCodeExtension extension,
            TaskProvider<InstallEmbedCodeTask> installTask,
            String name,
            String description,
            String mode
    ) {
        project.getTasks().register(name, EmbedCodeTask.class, task -> {
            task.setGroup(TASK_GROUP);
            task.setDescription(description);
            task.getMode().set(mode);
            task.getCodePath().set(extension.getCodePath());
            task.getNamedSources().set(extension.getNamedSources());
            task.getNamedSourceDirectories().from(extension.getNamedSourceDirectories());
            task.getDocsPath().set(extension.getDocsPath());
            task.getDocIncludes().set(extension.getDocIncludes());
            task.getDocExcludes().set(extension.getDocExcludes());
            task.getSeparator().set(extension.getSeparator());
            task.getInfo().set(extension.getInfo());
            task.getStacktrace().set(extension.getStacktrace());
            task.getExecutableFile().set(
                    installTask.flatMap(InstallEmbedCodeTask::getExecutableFile)
            );
            task.getWorkingDirectory().set(project.getLayout().getProjectDirectory());
        });
    }

    /** Returns {@code preferredName}, prepending underscores until the task name is unused. */
    private static String availableTaskName(Project project, String preferredName) {
        String candidate = preferredName;
        while (project.getTasks().getNames().contains(candidate)) {
            candidate = '_' + candidate;
        }
        return candidate;
    }

}
