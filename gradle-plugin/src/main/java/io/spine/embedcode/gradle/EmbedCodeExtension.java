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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

/**
 * Configures Embed Code for a Gradle project.
 *
 * <p>The extension maps directly to Embed Code command-line options and does
 * not create or require a YAML configuration file.</p>
 */
public abstract class EmbedCodeExtension {

    /** Returns the release version to download and run, defaulting to the plugin version. */
    public abstract Property<String> getVersion();

    /** Returns the root directory containing source files used by embedding instructions. */
    public abstract DirectoryProperty getCodePath();

    /** Returns named source roots keyed by the name used in embedding instructions. */
    public abstract MapProperty<String, String> getNamedSources();

    /** Returns named source directories with their task dependencies. */
    public abstract ConfigurableFileCollection getNamedSourceDirectories();

    /**
     * Adds a named source root.
     *
     * @param name the name referenced as {@code $name} in an embedding instruction
     * @param directory the source root directory
     */
    public void namedSource(String name, Directory directory) {
        String normalizedName = name.trim();
        if (normalizedName.isEmpty()) {
            throw new InvalidUserDataException("An Embed Code source name must not be empty.");
        }
        getNamedSources().put(normalizedName, directory.getAsFile().getAbsolutePath());
        getNamedSourceDirectories().from(directory);
    }

    /**
     * Adds a named source root supplied by another Gradle provider.
     *
     * @param name the name referenced as {@code $name} in an embedding instruction
     * @param directory the source root provider, including its task dependency
     */
    public void namedSource(String name, Provider<Directory> directory) {
        String normalizedName = name.trim();
        if (normalizedName.isEmpty()) {
            throw new InvalidUserDataException("An Embed Code source name must not be empty.");
        }
        getNamedSources().put(
                normalizedName,
                directory.map(value -> value.getAsFile().getAbsolutePath())
        );
        getNamedSourceDirectories().from(directory);
    }

    /** Returns the root directory containing Markdown or HTML documentation. */
    public abstract DirectoryProperty getDocsPath();

    /** Returns glob patterns selecting documentation files to process. */
    public abstract ListProperty<String> getDocIncludes();

    /** Returns glob patterns selecting documentation files to skip. */
    public abstract ListProperty<String> getDocExcludes();

    /** Returns text inserted between joined fragment parts. */
    public abstract Property<String> getSeparator();

    /** Returns whether Embed Code should print informational log messages. */
    public abstract Property<Boolean> getInfo();

    /** Returns whether Embed Code should print stack traces after panics. */
    public abstract Property<Boolean> getStacktrace();

    /**
     * <p>The plugin appends {@code /v<version>/<platform-asset>} to this URL.
     * This property primarily supports release mirrors and functional testing.</p>
     *
     * @return the base URL containing versioned Embed Code release directories
     */
    public abstract Property<String> getDownloadBaseUrl();
}
