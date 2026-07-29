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

package io.spine.embedcode.gradle.publish

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpClient.Redirect.NORMAL
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers.discarding
import java.time.Duration
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask

/**
 * Checks that the current plugin version is not published to the Gradle Plugin Portal.
 *
 * The task checks the exact plugin-marker POM. It succeeds when the Portal returns HTTP 404 and
 * fails closed for an existing artifact, an unexpected response, or a network failure.
 */
@UntrackedTask(because = "The result depends on live Gradle Plugin Portal state.")
public abstract class CheckVersionIncrement : DefaultTask() {

    /** Plugin ID whose marker artifact is checked. */
    @get:Input
    public abstract val pluginId: Property<String>

    /** Plugin version whose marker artifact is checked. */
    @get:Input
    public abstract val pluginVersion: Property<String>

    /** Base URL of the Gradle Plugin Portal. */
    @get:Input
    public abstract val portalBaseUrl: Property<String>

    /** Verifies that the current version can be published. */
    @TaskAction
    public fun verifyVersion() {
        val markerUri = markerUri()
        when (val status = request(markerUri)) {
            HTTP_OK ->
                throw GradleException(
                    "Plugin `${pluginId.get()}` version `${pluginVersion.get()}` is already " +
                        "published to the Gradle Plugin Portal. Increment " +
                        "`embedCodePluginVersion` in `version.gradle.kts`.",
                )

            HTTP_NOT_FOUND -> Unit
            else ->
                throw GradleException(
                    "Could not verify whether plugin `${pluginId.get()}` version " +
                        "`${pluginVersion.get()}` is already published. The Gradle Plugin Portal " +
                        "returned HTTP $status for `$markerUri`. Retry before publishing.",
                )
        }
    }

    private fun markerUri(): URI {
        val id = pluginId.get()
        val version = pluginVersion.get()
        val markerArtifact = "$id.gradle.plugin"
        val groupPath = id.replace('.', '/')
        return URI.create(
            "${portalBaseUrl.get().trimEnd('/')}/m2/$groupPath/$markerArtifact/" +
                "$version/$markerArtifact-$version.pom",
        )
    }

    private fun request(markerUri: URI): Int {
        val request =
            HttpRequest
                .newBuilder(markerUri)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build()
        val client =
            HttpClient
                .newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .followRedirects(NORMAL)
                .build()
        try {
            return client.send(request, discarding()).statusCode()
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw requestFailure(markerUri, error)
        } catch (error: IOException) {
            throw requestFailure(markerUri, error)
        }
    }

    private fun requestFailure(markerUri: URI, cause: Exception): GradleException =
        GradleException(
            "Could not verify whether plugin `${pluginId.get()}` version " +
                "`${pluginVersion.get()}` is already published. The Gradle Plugin Portal " +
                "request to `$markerUri` failed. Retry before publishing.",
            cause,
        )

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_NOT_FOUND = 404
        val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(30)
    }
}
