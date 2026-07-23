# Project

This document gives agents and contributors the project map, runtime flow, compatibility
constraints, test strategy, and trust boundaries. For repository-wide operating policy, read
[AGENTS.md](AGENTS.md).

## Overview

`embed-code-gradle-plugin` publishes the `io.spine.embed-code` Gradle plugin. The plugin
downloads an authenticated Embed Code release for the current platform and exposes Gradle
tasks that either check documentation snippets or update them.

Consumers configure the plugin through Gradle Kotlin DSL. They do not maintain a separate
Embed Code configuration file and do not need to install the executable or Kotlin.

## Project map

- `build.gradle.kts`: root group and version wiring.
- `settings.gradle.kts`: plugin and dependency repositories and the `gradle-plugin` module.
- `version.gradle.kts`: the plugin version and default Embed Code application version.
- `gradle.properties`: configuration-cache, parallel-build, Kotlin-style, and standard-library
  settings.
- `buildSrc/`: build settings, dependency coordinates, and the shared `jvm-module` convention.
- `gradle-plugin/build.gradle.kts`: plugin declaration, generated version source, functional
  test source set, publication metadata, and Plugin Portal configuration.
- `gradle-plugin/src/main/kotlin/`: extension, plugin, task, platform, version, JSON, checksum,
  download, installation, and execution logic.
- `gradle-plugin/src/main/templates/`: generated default-version source template.
- `gradle-plugin/src/test/kotlin/`: focused unit specifications.
- `gradle-plugin/src/functionalTest/kotlin/`: Gradle TestKit specifications against temporary
  consumer builds.
- `.github/workflows/check.yml`: Ubuntu and Windows build verification.
- `.agents/skills/`: repository-specific implementation, test, writing, review, and security
  workflows for agents.

## Runtime flow

1. `EmbedCodePlugin` creates the `embedCode` extension and registers installation, check, and
   update tasks lazily.
2. `InstallEmbedCodeTask` selects the platform asset, establishes its expected SHA-256 digest,
   and installs or restores a verified executable under `build/embed-code/`.
3. `EmbedCodeTask` validates the configured source roots and documentation root, generates the
   temporary JSON configuration, and launches the executable in `check` or `embed` mode.
4. `checkEmbedding` reports stale documentation without rewriting it. `embedCode` updates
   selected documentation files.

When behavior changes, trace the complete extension → plugin → task → generated configuration
→ executable flow instead of patching only the first visible symptom.

## Compatibility and dependency policy

- The Gradle wrapper version and checksum are defined in
  `gradle/wrapper/gradle-wrapper.properties`.
- The public minimum Gradle version is documented in `README.md` and must remain covered by
  compatibility tests.
- `BuildSettings` owns the build JDK and emitted bytecode versions. Do not infer one from the
  other.
- `jvm-module.gradle.kts` owns Kotlin language/API compatibility, explicit API mode, Java
  release settings, and the test launcher.
- `gradle-plugin/build.gradle.kts` deliberately uses Gradle's Kotlin runtime instead of
  publishing `kotlin-stdlib`.
- Dependency versions live in Kotlin objects under `buildSrc`; do not introduce a version
  catalog as an unrelated refactor.

Read these source files before changing a version or compatibility claim. Avoid copying
version numbers into agent guidance where a durable source path is sufficient.

## Test strategy

- Use unit specifications for pure parsing, validation, mapping, checksum, platform, and
  version behavior.
- Use TestKit functional specifications for plugin application, extension wiring, task
  registration, generated configuration, logging, configuration-cache reuse, process
  execution, compatibility, filesystem effects, download/cache behavior, and failures visible
  to a consumer build.
- Keep tests deterministic and offline-capable with temporary directories, fake release
  assets, and local HTTP servers.
- Use `./gradlew test` for unit tests, `./gradlew functionalTest` for TestKit tests, and
  `./gradlew check` for both plus plugin validation.
- CI runs the build and publishes the plugin to Maven Local on Ubuntu and Windows. Preserve
  cross-platform paths, permissions, line endings, and process behavior.

## Trust boundaries

Treat executable acquisition and reuse as security-sensitive:

- Authenticate release assets before extraction or execution.
- Bind cached metadata to the release source, exact tag, platform asset, and digest.
- Re-hash installed executables before reuse and fail closed when offline trust cannot be
  established.
- Keep all derived installation paths inside the locked installation root.
- Reject symbolic-link or junction paths that can redirect writes outside that root.
- Keep tokens explicit, secret, and absent from logs, task inputs, cache keys, and persisted
  metadata.
- Use unpredictable temporary files and safe replacement when installing assets.

Apply both `gradle-engineer` and `security-engineer` to changes that cross Gradle lifecycle and
trust boundaries.

## Documentation ownership

- `README.md`: user-facing purpose, requirements, Kotlin DSL configuration, execution, and
  development commands.
- `PROJECT.md`: project map, runtime flow, compatibility, test strategy, and trust boundaries.
- `AGENTS.md`: repository-wide agent operating policy and routing.
- `.agents/guidelines/writing-style.md`: shared Spine writing and typography rules.
- `.agents/skills/*/SKILL.md`: task-specific workflows and project constraints.

Keep user instructions in `README.md`, contributor and architecture context here, and agent
procedures in the matching skill.

## Agent routes

- Codex: `AGENTS.md` → `PROJECT.md` → matching `.agents/skills/` entries.
- Claude: `CLAUDE.md` → `AGENTS.md` → matching `.agents/skills/` entries.
- GitHub Copilot: `.github/copilot-instructions.md` → `AGENTS.md` → matching skills.

Keep routes thin. Update the owning document instead of duplicating policy across clients.
