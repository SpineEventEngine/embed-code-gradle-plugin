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

import org.gradle.api.GradleException;

import java.util.Locale;
import java.util.Objects;

/** A released executable selected for an operating system and architecture. */
final class EmbedCodePlatform {

    private final String assetName;
    private final String executableName;

    /**
     * Creates a platform description.
     *
     * @param assetName the release asset to download
     * @param executableName the installed executable name
     */
    EmbedCodePlatform(String assetName, String executableName) {
        this.assetName = assetName;
        this.executableName = executableName;
    }

    /** Returns the platform-specific release asset name. */
    String getAssetName() {
        return assetName;
    }

    /** Returns the executable name after extraction. */
    String getExecutableName() {
        return executableName;
    }

    /** Selects the release asset for {@code osName} and {@code architecture}. */
    static EmbedCodePlatform detect(String osName, String architecture) {
        String os = osName.toLowerCase(Locale.ROOT);
        String arch = architecture.toLowerCase(Locale.ROOT);
        boolean isAmd64 = arch.equals("amd64") || arch.equals("x86_64");
        boolean isArm64 = arch.equals("aarch64") || arch.equals("arm64");

        if (os.contains("mac") && isArm64) {
            return new EmbedCodePlatform(
                    "embed-code-macos-arm64.zip",
                    "embed-code-macos-arm64"
            );
        }
        if (os.contains("mac") && isAmd64) {
            return new EmbedCodePlatform(
                    "embed-code-macos-x64.zip",
                    "embed-code-macos-x64"
            );
        }
        if (os.contains("linux") && isAmd64) {
            return new EmbedCodePlatform("embed-code-linux", "embed-code-linux");
        }
        if (os.contains("windows") && isAmd64) {
            return new EmbedCodePlatform(
                    "embed-code-windows.exe",
                    "embed-code-windows.exe"
            );
        }
        throw new GradleException(
                "Embed Code does not publish a binary for operating system `" + osName
                        + "` and architecture `" + architecture + "`."
        );
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EmbedCodePlatform)) {
            return false;
        }
        EmbedCodePlatform that = (EmbedCodePlatform) other;
        return assetName.equals(that.assetName)
                && executableName.equals(that.executableName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(assetName, executableName);
    }

    @Override
    public String toString() {
        return "EmbedCodePlatform{" +
                "assetName='" + assetName + '\'' +
                ", executableName='" + executableName + '\'' +
                '}';
    }
}
