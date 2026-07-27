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

package io.spine.embedcode.gradle.report

import com.github.jk1.license.LicenseReportExtension
import com.github.jk1.license.ProjectData
import com.github.jk1.license.render.InventoryMarkdownReportRenderer
import com.github.jk1.license.render.ReportRenderer
import java.io.File
import org.gradle.api.tasks.Input

/**
 * Renders a deterministic Markdown dependency license report.
 *
 * This renderer adapts the upstream output to a report tracked in the repository:
 *
 * - The upstream renderer adds trailing spaces to dependency lines. They are removed so that
 *   the generated documentation passes the repository's whitespace checks.
 * - The upstream renderer links embedded license files relatively, expecting them to sit next
 *   to the report. Only the report itself is tracked, so those links are turned into plain
 *   code spans that name the file without pointing at a missing path.
 *
 * @property filename name of the generated report file.
 * @property title report title.
 */
public class DependencyMarkdownReportRenderer(
    @get:Input
    public val filename: String,
    @get:Input
    public val title: String,
) : ReportRenderer {

    private val delegate =
        // Parameters: overrides file, timestamp flag, and dependency-counter flag.
        InventoryMarkdownReportRenderer(
            filename,
            title,
            null,
            false,
            true,
        )

    /** Writes the report and normalizes it for tracking in the repository. */
    override fun render(data: ProjectData) {
        delegate.render(data)
        val extension = data.extension as LicenseReportExtension
        val outputFile = File(extension.absoluteOutputDir, filename)
        outputFile.writeText(normalizeMarkdownReport(outputFile.readText()))
    }
}

/**
 * A Markdown link whose target is not an absolute URL.
 *
 * The lookahead keeps `http:`, `https:`, and any other scheme-qualified target intact.
 */
private val relativeLink = Regex("""\[([^]]+)]\((?!\w+:)[^)]*\)""")

internal fun normalizeMarkdownReport(report: String): String {
    val lines =
        report
            .lineSequence()
            .map { neutralizeRelativeLinks(it.trimEnd()) }
            .toList()
            .dropWhile { it.isEmpty() }
            .dropLastWhile { it.isEmpty() }
    return lines.joinToString(separator = "\n", postfix = "\n")
}

internal fun neutralizeRelativeLinks(line: String): String =
    relativeLink.replace(line) { "`${it.groupValues[1]}`" }
