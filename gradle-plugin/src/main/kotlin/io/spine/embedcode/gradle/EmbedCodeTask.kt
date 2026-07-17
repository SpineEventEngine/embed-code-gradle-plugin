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

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.TreeMap
import javax.inject.Inject

/**
 * Runs Embed Code in either check or embed mode.
 */
@DisableCachingByDefault(because = "Embed Code checks or updates documentation files in place")
public abstract class EmbedCodeTask : DefaultTask() {

    /** Process execution without project access at execution time. */
    @get:Inject
    protected abstract val execOperations: ExecOperations

    /** The execution mode assigned by the plugin. */
    @get:Input
    public abstract val mode: Property<String>

    /** The source root passed to `-code-path`. */
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val codePath: DirectoryProperty

    /**
     * Named source roots included in an internally generated configuration.
     *
     * The absolute path values make this input machine-specific. The task intentionally
     * declares no outputs and disables caching because it checks or modifies documentation.
     */
    @get:Input
    public abstract val namedSources: MapProperty<String, String>

    /** Named source directories with their producing task dependencies. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val namedSourceDirectories: ConfigurableFileCollection

    /** The documentation root passed to `-docs-path`. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val docsPath: DirectoryProperty

    /** Documentation include patterns passed to `-doc-includes`. */
    @get:Input
    public abstract val docIncludes: ListProperty<String>

    /** Documentation exclude patterns passed to `-doc-excludes`. */
    @get:Input
    public abstract val docExcludes: ListProperty<String>

    /** The fragment separator passed to `-separator`. */
    @get:Input
    public abstract val separator: Property<String>

    /** Whether informational logging is enabled. */
    @get:Input
    public abstract val info: Property<Boolean>

    /** Whether panic stack traces are enabled. */
    @get:Input
    public abstract val stacktrace: Property<Boolean>

    /** The installed platform executable. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val executableFile: RegularFileProperty

    /** The process working directory. */
    @get:Internal
    public abstract val workingDirectory: DirectoryProperty

    /**
     * Executes Embed Code with arguments derived from the Gradle extension.
     */
    @TaskAction
    public fun runEmbedCode() {
        val executionMode = mode.get()
        val processDirectory = workingDirectory.get().asFile
        logger.info(
            "Preparing Embed Code `{}` mode in `{}`.",
            executionMode,
            processDirectory,
        )
        val configuredSources = TreeMap(namedSources.get())
        val hasDirectSource = codePath.isPresent
        val hasNamedSources = configuredSources.isNotEmpty()
        if (hasDirectSource == hasNamedSources) {
            throw GradleException(
                "Configure exactly one of `codePath` or `namedSource(...)` for Embed Code.",
            )
        }

        val arguments = mutableListOf<String>()
        arguments.add("-mode=$executionMode")
        if (hasNamedSources) {
            arguments.add("-config-path=${writeNamedSourceConfiguration(configuredSources)}")
        } else {
            val sourceDirectory = codePath.get().asFile
            val documentationDirectory = docsPath.get().asFile
            logger.info(
                "Using source root `{}` and documentation root `{}`.",
                sourceDirectory,
                documentationDirectory,
            )
            arguments.add("-code-path=${sourceDirectory.absolutePath}")
            arguments.add("-docs-path=${documentationDirectory.absolutePath}")
            if (docIncludes.get().isNotEmpty()) {
                arguments.add("-doc-includes=${docIncludes.get().joinToString(",")}")
            }
            if (docExcludes.get().isNotEmpty()) {
                arguments.add("-doc-excludes=${docExcludes.get().joinToString(",")}")
            }
            arguments.add("-separator=${separator.get()}")
            arguments.add("-info=${info.get()}")
            arguments.add("-stacktrace=${stacktrace.get()}")
        }

        val executable = executableFile.get().asFile
        logger.info(
            "Starting Embed Code `{}` mode with executable `{}`.",
            executionMode,
            executable,
        )
        execOperations.exec { spec ->
            spec.executable(executable)
            spec.args(arguments)
            spec.setWorkingDir(processDirectory)
        }
        logger.info("Embed Code `{}` mode completed successfully.", executionMode)
    }

    /**
     * Writes the generated configuration used when named source roots are configured.
     */
    private fun writeNamedSourceConfiguration(configuredSources: Map<String, String>): Path {
        val normalizedSources = TreeMap<String, String>()
        for (source in configuredSources.entries) {
            var path = Paths.get(source.value)
            if (!path.isAbsolute) {
                path = workingDirectory.get().asFile.toPath().resolve(path)
            }
            path = path.normalize().toAbsolutePath()
            if (!Files.isDirectory(path)) {
                throw GradleException(
                    "Embed Code source `${source.key}` is not a directory: $path",
                )
            }
            normalizedSources[source.key] = path.toString()
        }

        val json = createConfigurationJson(
            normalizedSources,
            docsPath.get().asFile.absolutePath,
            docIncludes.get(),
            docExcludes.get(),
            separator.get(),
            info.get(),
            stacktrace.get(),
        )
        val configuration = temporaryDir.toPath().resolve("embed-code.json")
        try {
            Files.write(configuration, json.toByteArray(StandardCharsets.UTF_8))
        } catch (exception: IOException) {
            throw GradleException(
                "Could not write the generated Embed Code configuration to $configuration.",
                exception,
            )
        }
        logger.info(
            "Generated Embed Code configuration at `{}` for {} named source roots.",
            configuration,
            configuredSources.size,
        )
        return configuration
    }

}
