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

import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

/**
 * Configures Embed Code for a Gradle project.
 *
 * The extension maps directly to Embed Code command-line options and does not
 * create or require a YAML configuration file.
 */
public abstract class EmbedCodeExtension {

    private val configuredSourceNames = mutableSetOf<String>()

    /** An optional release version, with the latest release used when absent. */
    public abstract val version: Property<String>

    /**
     * An optional SHA-256 digest of the configured release asset.
     *
     * The plugin resolves this digest automatically for releases hosted on
     * `github.com`. Configure this property together with [version] to pin a
     * release asset explicitly.
     */
    public abstract val sha256: Property<String>

    /**
     * An optional GitHub token used to read release checksum metadata.
     *
     * Configure this property explicitly when authenticated GitHub API access
     * is intended. The plugin does not read a token from the environment by
     * default.
     */
    public abstract val githubToken: Property<String>

    /** The root directory containing source files used by embedding instructions. */
    public abstract val codePath: DirectoryProperty

    /** Named source roots keyed by the name used in embedding instructions. */
    internal abstract val namedSources: MapProperty<String, String>

    /** Named source directories with their task dependencies. */
    internal abstract val namedSourceDirectories: ConfigurableFileCollection

    /**
     * Adds a named source root.
     *
     * @param name the name referenced as `$name` in an embedding instruction
     * @param directory the source root directory
     */
    public fun namedSource(name: String, directory: Directory) {
        val normalizedName = registerSourceName(name)
        namedSources.put(normalizedName, directory.asFile.absolutePath)
        namedSourceDirectories.from(directory)
    }

    /**
     * Adds a named source root supplied by another Gradle provider.
     *
     * @param name the name referenced as `$name` in an embedding instruction
     * @param directory the source root provider, including its task dependency
     */
    public fun namedSource(name: String, directory: Provider<Directory>) {
        val normalizedName = registerSourceName(name)
        namedSources.put(
            normalizedName,
            directory.map { value -> value.asFile.absolutePath },
        )
        namedSourceDirectories.from(directory)
    }

    /** The root directory containing Markdown or HTML documentation. */
    public abstract val docsPath: DirectoryProperty

    /** Glob patterns selecting documentation files to process. */
    public abstract val docIncludes: ListProperty<String>

    /** Glob patterns selecting documentation files to skip. */
    public abstract val docExcludes: ListProperty<String>

    /** Text inserted between joined fragment parts. */
    public abstract val separator: Property<String>

    /** Whether Embed Code should print informational log messages. */
    public abstract val info: Property<Boolean>

    /** Whether Embed Code should print stack traces after panics. */
    public abstract val stacktrace: Property<Boolean>

    /**
     * The base URL of the Embed Code releases.
     *
     * The plugin appends `/latest/download/<platform-asset>` when no version is
     * configured, or `/download/v<version>/<platform-asset>` for an explicit
     * version. This property primarily supports functional testing.
     */
    public abstract val downloadBaseUrl: Property<String>

    private fun registerSourceName(name: String): String {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) {
            throw InvalidUserDataException("An Embed Code source name must not be empty.")
        }
        if (!configuredSourceNames.add(normalizedName)) {
            throw InvalidUserDataException(
                "Embed Code source `$normalizedName` is already configured.",
            )
        }
        return normalizedName
    }
}
