[![Build on Ubuntu and Windows][build-badge]][gh-actions]
[![license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](http://www.apache.org/licenses/LICENSE-2.0)

# Embed Code Gradle plugin

Gradle plugin for [Embed Code][embed-code], an application that keeps code
examples in Markdown and HTML synchronized with their source files.

The plugin downloads the released Embed Code executable for the current
platform, so developers and CI jobs do not have to install it manually.
It adds two tasks:

- `checkEmbedding` checks that embedded code is up to date.
- `embedCode` updates embedded code in place.

## Requirements

- Java 17 or a newer version supported by the selected Gradle version.
- Gradle 8.14.4 or newer.
- Linux AMD64, Windows AMD64, or macOS AMD64/ARM64.

Consumers do not need to install Kotlin or apply a Kotlin plugin.
The plugin is written in Kotlin, but uses the Kotlin runtime supplied by Gradle.

## How to use

This section describes how to use the plugin. For information about the Embed
Code application itself, see its [documentation][embed-code].

Add the following configuration to the project's `build.gradle.kts`:

```kotlin
plugins {
    id("io.spine.embed-code") version "0.1.0" // Specify the actual version here.
}

embedCode {

    // Specify the directory containing source files referenced by embedding instructions.
    //
    // This property is required unless `namedSource(...)` is used.
    //
    codePath.set(layout.projectDirectory.dir("src/main/java"))

    // Specify the directory containing Markdown or HTML documentation.
    //
    // This property is required.
    //
    docsPath.set(layout.projectDirectory.dir("docs"))

    // Configure documentation files to include and exclude.
    //
    // This section is optional. The default includes are `**/*.md` and
    // `**/*.html`; the default excludes list is empty.
    //
    docIncludes.set(listOf("**/*.md", "**/*.html"))
    docExcludes.set(listOf("drafts/**", "generated/**"))

    // Configure other Embed Code command-line options.
    //
    // This section is optional. The values below are the defaults.
    //
    separator.set("...")
    info.set(false)
    stacktrace.set(false)
}
```

Use named source roots when documentation embeds code from multiple modules:

```kotlin
embedCode {
    namedSource(
        "model",
        layout.projectDirectory.dir("model"),
    )
    namedSource(
        "database",
        layout.projectDirectory.dir("database"),
    )
    docsPath.set(layout.projectDirectory)
}
```

Embedding instructions refer to these roots with `$model/` and
`$database/`. `codePath` and `namedSource(...)` are mutually exclusive.

By default, the plugin resolves and verifies the latest Embed Code release on
the first installation. It then reuses the executable in
`build/embed-code/latest` without another release or checksum-metadata request.
Run `clean` or remove that directory to check for a newer release. Changing the
configured version, release source, platform, or `sha256` to a different digest
also invalidates the matching cached installation. If a latest-release check
fails after invalidation, the plugin reuses an existing installed executable. If
that executable is missing, the plugin can restore the retained release asset
only when `sha256` supplies a trust anchor.

To use a specific Embed Code application release, add its exact release tag to
the extension:

```kotlin
embedCode {
    version.set("v1.2.4")
}
```

API requests are unauthenticated unless a token provider is configured explicitly:

```kotlin
embedCode {
    githubToken.set(providers.environmentVariable("EMBED_CODE_GITHUB_TOKEN"))
}
```

For CI, configure `githubToken` to avoid GitHub's unauthenticated API rate limit
during the initial resolution. Without `sha256`, the first online installation
resolves the asset digest from GitHub release metadata. Pin both `version` and
`sha256` to keep that initial installation tied to an immutable release.

The digest applies to the downloaded release asset. For macOS, this means the
ZIP archive rather than the extracted executable. The verified release asset is
retained under `build/embed-code`. Local metadata records that the installed
executable came from a verified asset. Later builds trust this local state and
do not rehash the executable or retained asset. If local cache contents may have
been changed, remove the installation directory to force verification again.

Offline mode reuses an existing regular executable from the selected cache
directory without remote verification and without requiring `sha256`. If the
executable is missing, `sha256` is required to authenticate and restore the
retained asset. Explicit release tags use SHA-256-derived cache directory names,
which preserve case-sensitive tag identity on Windows and case-insensitive file
systems.

Check that documentation is up to date:

```bash
./gradlew :checkEmbedding
```

Update documentation:

```bash
./gradlew :embedCode
```

The plugin prefers the `checkEmbedding` and `embedCode` task names. If a name
is already occupied when the plugin is applied, underscores are prepended until
an available name is found, for example `_embedCode` or `__embedCode`.
The fallback cannot account for a conflicting task registered later.

## Development

Run compilation, plugin validation, and the complete test suite:

```bash
./gradlew check
```

Fast unit tests run under `test`. TestKit coverage runs separately under
`functionalTest`; the `check` task includes both.

To test the plugin in another project, publish it to the local Maven repository:

```bash
./gradlew :gradle-plugin:publishToMavenLocal
```

In this case, add `mavenLocal()` to `pluginManagement.repositories` in the
consuming project's `settings.gradle.kts`. Adding it only to the regular
`repositories` block does not make Gradle plugin markers available to the
`plugins` block.

## License

The plugin is available under the [Apache License 2.0](LICENSE).

[build-badge]: https://github.com/SpineEventEngine/embed-code-gradle-plugin/actions/workflows/check.yml/badge.svg
[embed-code]: https://github.com/SpineEventEngine/embed-code-go
[gh-actions]: https://github.com/SpineEventEngine/embed-code-gradle-plugin/actions
