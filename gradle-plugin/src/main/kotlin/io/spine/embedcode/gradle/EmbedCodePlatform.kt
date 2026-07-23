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

import org.gradle.api.GradleException
import java.util.Locale

/**
 * A released executable selected for an operating system and architecture.
 */
internal data class EmbedCodePlatform(
    val assetName: String,
    val executableName: String,
) {

    companion object {

        /**
         * Returns the stable installed executable name for [osName].
         */
        fun installedExecutableName(osName: String): String =
            if (osName.lowercase(Locale.ROOT).contains("windows")) {
                "embed-code.exe"
            } else {
                "embed-code"
            }

        /**
         * Selects the release asset for [osName], [architecture], and [releaseTag].
         */
        fun detect(
            osName: String,
            architecture: String,
            releaseTag: String,
        ): EmbedCodePlatform {
            val os = osName.lowercase(Locale.ROOT)
            val arch = architecture.lowercase(Locale.ROOT)
            val isAmd64 = arch == "amd64" || arch == "x86_64"
            val isArm64 = arch == "aarch64" || arch == "arm64"

            return when {
                os.contains("mac") && isArm64 -> EmbedCodePlatform(
                    "embed-code-macos-arm64.zip",
                    "embed-code-macos-arm64",
                )

                os.contains("mac") && isAmd64 -> EmbedCodePlatform(
                    "embed-code-macos-x64.zip",
                    "embed-code-macos-x64",
                )

                os.contains("linux") && isAmd64 -> EmbedCodePlatform(
                    if (releaseTag in bareLinuxAssetReleases) {
                        "embed-code-linux"
                    } else {
                        "embed-code-linux.zip"
                    },
                    "embed-code-linux",
                )

                os.contains("windows") && isAmd64 -> EmbedCodePlatform(
                    "embed-code-windows.exe",
                    "embed-code-windows.exe",
                )

                else -> throw GradleException(
                    "Embed Code does not publish a binary for operating system `$osName` " +
                        "and architecture `$architecture`.",
                )
            }
        }

        /**
         * All releases published before Linux ZIP packaging was introduced.
         */
        private val bareLinuxAssetReleases = setOf("v1.2.3", "v1.2.4")
    }
}
