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

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.Collections.synchronizedList
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Plugin version increment check should")
internal class CheckVersionIncrementSpec {

    @Test
    fun `allow a version whose marker artifact is missing`() {
        val requests = synchronizedList(mutableListOf<String>())
        withServer { server ->
            server.createContext("/") { exchange ->
                requests.add(exchange.requestURI.path)
                exchange.respond(404)
            }

            createTask(server).verifyVersion()
        }

        assertEquals(listOf(EXPECTED_MARKER_PATH), requests)
    }

    @Test
    fun `follow Portal redirects before accepting a missing version`() {
        val requests = synchronizedList(mutableListOf<String>())
        withServer { server ->
            server.createContext("/") { exchange ->
                val path = exchange.requestURI.path
                requests.add(path)
                if (path == EXPECTED_MARKER_PATH) {
                    exchange.responseHeaders.add("Location", "/missing")
                    exchange.respond(303)
                } else {
                    exchange.respond(404)
                }
            }

            createTask(server).verifyVersion()
        }

        assertEquals(listOf(EXPECTED_MARKER_PATH, "/missing"), requests)
    }

    @Test
    fun `reject a version whose marker artifact exists`() {
        val error =
            withServer { server ->
                server.createContext("/") { exchange -> exchange.respond(200) }

                assertThrows(GradleException::class.java) {
                    createTask(server).verifyVersion()
                }
            }

        assertEquals(
            "Plugin `io.spine.embed-code` version `0.1.1` is already published to the Gradle " +
                "Plugin Portal. Increment `embedCodePluginVersion` in `version.gradle.kts`.",
            error.message,
        )
    }

    @Test
    fun `fail closed when the Portal cannot verify the version`() {
        withServer { server ->
            server.createContext("/") { exchange -> exchange.respond(503) }

            val error = assertThrows(GradleException::class.java) {
                createTask(server).verifyVersion()
            }

            assertEquals(
                "Could not verify whether plugin `io.spine.embed-code` version `0.1.1` is " +
                    "already published. The Gradle Plugin Portal returned HTTP 503 for " +
                    "`${server.baseUrl}$EXPECTED_MARKER_PATH`. Retry before publishing.",
                error.message,
            )
        }
    }

    private fun createTask(server: HttpServer): CheckVersionIncrement {
        val project = ProjectBuilder.builder().build()
        return project.tasks
            .register("checkVersionIncrement", CheckVersionIncrement::class.java)
            .get()
            .apply {
                pluginId.set("io.spine.embed-code")
                pluginVersion.set("0.1.1")
                portalBaseUrl.set(server.baseUrl)
            }
    }

    private fun <T> withServer(action: (HttpServer) -> T): T {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        return try {
            action(server)
        } finally {
            server.stop(0)
        }
    }

    private val HttpServer.baseUrl: String
        get() = "http://127.0.0.1:${address.port}"

    private fun HttpExchange.respond(status: Int) {
        sendResponseHeaders(status, -1)
        close()
    }

    private companion object {
        const val EXPECTED_MARKER_PATH =
            "/m2/io/spine/embed-code/io.spine.embed-code.gradle.plugin/0.1.1/" +
                "io.spine.embed-code.gradle.plugin-0.1.1.pom"
    }
}
