# Project

This document gives agents and contributors the project map, runtime flow, compatibility,
test strategy, and trust boundaries. Read [AGENTS.md](AGENTS.md) for operating policy.

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
- `gradle.properties`: Gradle runtime, parallelism, and configuration-cache settings,
  plus Kotlin style and dependency defaults.
- `buildSrc/`: build settings, dependency coordinates, and the shared `jvm-module`
  convention, including Detekt.
- `gradle-plugin/build.gradle.kts`: plugin declaration, generated version source, functional
  test source set, publication metadata, and Plugin Portal configuration.
- `gradle-plugin/src/main/kotlin/`: extension, plugin, task, platform, version, JSON, checksum,
  download, installation, and execution logic.
- `gradle-plugin/src/main/templates/`: generated default-version source template.
- `gradle-plugin/src/test/kotlin/`: focused unit specifications.
- `gradle-plugin/src/functionalTest/kotlin/`: TestKit consumer-build specifications.
- `config/detekt/`: project-specific Detekt rules and the baseline for existing findings.
- `scripts/check_agent_config.py` and `scripts/tests/`: deterministic validation and tests.
- `.github/workflows/check.yml`: agent configuration and Ubuntu and Windows build verification.
- `.agents/guidelines/`: shared writing, English-language, and project-ownership rules.
- `.agents/skills/`: repository engineering, test, writing, review, and security workflows.

## Runtime flow

1. `EmbedCodePlugin` creates the `embedCode` extension and lazily registers
   installation, check, and update tasks.
2. `InstallEmbedCodeTask` selects the platform asset, establishes its expected SHA-256 digest,
   and installs or restores a verified executable under `build/embed-code/`.
3. `EmbedCodeTask` validates the configured source and documentation roots. It passes direct
   source settings as command-line arguments or writes temporary JSON for named sources,
   then launches the executable in `check` or `embed` mode.
4. `checkEmbedding` reports stale documentation, while `embedCode` updates selected files.

When behavior changes, trace the complete extension → plugin → task → arguments or generated
configuration → executable flow instead of patching only the first visible symptom.

## Compatibility and dependency policy

- The Gradle wrapper version and checksum are defined in `gradle/wrapper/gradle-wrapper.properties`.
- Document the minimum Gradle version in `README.md` and cover it with compatibility tests.
- `BuildSettings` independently defines the build JDK and emitted bytecode versions.
- `jvm-module.gradle.kts` owns Kotlin language/API compatibility, explicit API mode, Java
  release settings, and the test launcher.
- `gradle-plugin/build.gradle.kts` uses the Kotlin runtime supplied by Gradle;
  it does not publish `kotlin-stdlib`.
- Dependency versions live in Kotlin objects under `buildSrc`; do not introduce a version
  catalog as an unrelated refactor.

Read these source files before changing a version or compatibility claim. Avoid copying
version numbers into agent guidance where a durable source path is sufficient.

## Test strategy

- Use unit specifications for pure parsing, validation, mapping, checksums, platforms, and versions.
- Use TestKit functional specifications for plugin application, extension and task wiring,
  generated configuration, logging, configuration-cache reuse, process execution,
  compatibility, filesystem and cache behavior, and consumer-visible failures.
- Keep tests offline with deterministic fixtures, temporary directories, and loopback HTTP servers.
- Use `./gradlew test` for unit tests, `./gradlew functionalTest` for TestKit tests, and
  `./gradlew check` for both plus plugin validation.
- CI validates agent configuration on Ubuntu and independently runs the build and publishes
  the plugin to Maven Local on Ubuntu and Windows. Preserve cross-platform paths, permissions,
  line endings, and process behavior.

## Trust boundaries

Treat executable acquisition and reuse as security-sensitive:

- Authenticate release assets before extraction or execution.
- Bind cached metadata to the release source, exact tag, platform asset, and digest.
- Rehash installed executables before reuse. Local markers detect changes only while intact.
- Fail closed offline unless one path succeeds: reuse state that matches prior markers,
  or rebuild from a cached asset verified by a configured digest.
- Keep all derived installation paths inside the fixed installation root.
- Reject symbolic-link or junction paths that can redirect writes outside that root.
- Keep tokens explicit and secret; exclude them from logs, task inputs, cache keys, and metadata.
- Use unpredictable temporary files and safe replacement when installing assets.

Apply `gradle-engineer` and `security-engineer` to work spanning Gradle and security boundaries.

## Documentation ownership

- `README.md`: user-facing purpose, requirements, Kotlin DSL, execution, and development commands.
- `PROJECT.md`: project map, runtime flow, compatibility, test strategy, and trust boundaries.
- `AGENTS.md`: repository-wide agent operating policy and routing.
- `.agents/guidelines/`: shared writing, English-language, and project-ownership rules.
- `.agents/skills/*/SKILL.md`: task-specific workflows and project constraints.

Keep user instructions in `README.md`, contributor and architecture context here, and agent
procedures in the matching skill.

## Agent routes

- Codex: `AGENTS.md` → `PROJECT.md` → matching `.agents/skills/` entries.
- Claude: `CLAUDE.md` → `AGENTS.md` → `PROJECT.md` → matching `.agents/skills/` entries.
- GitHub Copilot: `.github/copilot-instructions.md` → `AGENTS.md` → `PROJECT.md`
  → matching repository skills under `.agents/skills/`.

Keep routes thin. Update the owning document instead of duplicating policy across clients.
