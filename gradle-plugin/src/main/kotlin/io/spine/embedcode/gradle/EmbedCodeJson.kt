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

import java.util.Locale

/**
 * Creates a JSON document accepted by Embed Code's YAML configuration parser.
 */
internal fun createConfigurationJson(
    namedSources: Map<String, String>,
    docsPath: String,
    docIncludes: List<String>,
    docExcludes: List<String>,
    separator: String,
    info: Boolean,
    stacktrace: Boolean,
): String {
    val json = StringBuilder()
    json.append("{\n  \"code-path\": [\n")
    var index = 0
    for (source in namedSources.entries) {
        if (index > 0) {
            json.append(",\n")
        }
        json.append("    {\"name\": ")
        appendJsonString(json, source.key)
        json.append(", \"path\": ")
        appendJsonString(json, source.value)
        json.append('}')
        index++
    }
    json.append("\n  ],\n  \"docs-path\": ")
    appendJsonString(json, docsPath)
    json.append(",\n  \"doc-includes\": ")
    appendJsonArray(json, docIncludes)
    json.append(",\n  \"doc-excludes\": ")
    appendJsonArray(json, docExcludes)
    json.append(",\n  \"separator\": ")
    appendJsonString(json, separator)
    json.append(",\n  \"info\": ").append(info)
    json.append(",\n  \"stacktrace\": ").append(stacktrace)
    json.append("\n}\n")
    return json.toString()
}

private fun appendJsonArray(json: StringBuilder, values: List<String>) {
    json.append('[')
    for (index in values.indices) {
        if (index > 0) {
            json.append(", ")
        }
        appendJsonString(json, values[index])
    }
    json.append(']')
}

private fun appendJsonString(json: StringBuilder, value: String) {
    json.append('"')
    for (character in value) {
        when (character) {
            '"' -> json.append("\\\"")
            '\\' -> json.append("\\\\")
            '\b' -> json.append("\\b")
            '\u000C' -> json.append("\\f")
            '\n' -> json.append("\\n")
            '\r' -> json.append("\\r")
            '\t' -> json.append("\\t")
            else -> {
                if (character < '\u0020') {
                    json.append(String.format(Locale.ROOT, "\\u%04x", character.code))
                } else {
                    json.append(character)
                }
            }
        }
    }
    json.append('"')
}
