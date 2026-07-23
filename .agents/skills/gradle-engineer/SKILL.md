---
name: gradle-engineer
description: >-
  Apply repository-specific Gradle engineering policy when changing or reviewing
  build scripts, buildSrc, Kotlin DSL, plugin, extension, or task APIs, lazy
  configuration, task state, configuration-cache behavior, TestKit coverage,
  consumer compatibility, or publication metadata.
---

# Gradle engineering

Preserve Gradle semantics across the plugin implementation and its build. Treat
configuration avoidance, task state, consumer compatibility, and cross-platform
behavior as public contracts.

## Start

1. Read [`PROJECT.md`](../../../PROJECT.md) for the project map and supported behavior.
2. Read the affected build scripts, plugin API, and task types, along with
   nearby unit and functional tests in full.
3. Trace values from the extension through `Provider` wiring into task inputs,
   execution behavior, outputs, and local state.
4. Read [Gradle practices](references/gradle-practices.md) when changing task
   modeling, compatibility, build logic, publication, or platform behavior.
5. Clarify only material ambiguity in the public DSL, compatibility floor, or
   verification target before editing.

## Cross-domain work

- Apply [`kotlin-engineer`](../kotlin-engineer/SKILL.md) to Kotlin source and
  build-logic implementation quality.
- Apply [`security-engineer`](../security-engineer/SKILL.md) to release
  downloads, integrity metadata, offline reuse, tokens, archives, and paths.

## Apply the Gradle model

- Register tasks lazily and retain `TaskProvider` or `Provider` values. Avoid
  eager realization and configuration-time filesystem or network work.
- Model configurable values with `Property`, `ListProperty`, `MapProperty`,
  `DirectoryProperty`, `RegularFileProperty`, and provider transformations.
- Wire producer-backed files through providers or file collections to preserve task dependencies.
- Declare every task input, output, local-state file, and internal value
  according to its actual semantics. Choose path sensitivity deliberately.
- Keep tokens internal and out of task inputs, logs, cache identities, and publication values.
- Keep task actions configuration-cache safe. Inject execution services and
  avoid accessing `Project` or mutable configuration state at execution time.
- Disable caching or up-to-date reuse when in-place writes, mutable external
  sources, or validation of local state make reuse unsound.
- Prefer Kotlin DSL and existing convention plugins; do not duplicate module
  configuration or add ad hoc Groovy.

## Preserve repository contracts

- Keep consumer builds compatible with Gradle 8.14.4 and newer. Do not infer
  the compatibility floor from the current wrapper version.
- Compile against the Kotlin runtime supplied by supported Gradle versions.
  Do not publish a conflicting Kotlin standard library with the plugin.
- Preserve Java 17 bytecode compatibility unless the supported Gradle floor is
  intentionally changed with tests and documentation.
- Keep the public configuration Gradle-native. Treat generated Embed Code
  configuration as an internal implementation detail.
- Preserve Linux and Windows behavior for paths, executable names, permissions,
  process arguments, and task outputs.
- Keep plugin ID, implementation class, website, VCS URL, tags, artifact
  coordinates, POM metadata, and license packaging aligned.
- Allow the install task to remain ungrouped and hidden from the standard task
  listing. Do not flag or change that behavior without a user-facing reason.
- Do not copy organization-wide repository filtering or dependency-group naming rules
  unless this repository adopts them explicitly.

## Verify

Run the narrowest relevant command first, then broaden:

1. `./gradlew :gradle-plugin:test --tests <test-class>`
2. `./gradlew :gradle-plugin:functionalTest --tests <test-class>`
3. `./gradlew :gradle-plugin:test :gradle-plugin:functionalTest`
4. `./gradlew :gradle-plugin:validatePlugins`
5. `./gradlew check`

Add or run a Gradle 8.14.4 TestKit scenario when compatibility-sensitive code
changes. Exercise configuration-cache reuse when plugin or task wiring changes.
Exercise platform-specific behavior on the relevant operating system; report an
unavailable Windows or Linux probe instead of treating another host as proof.

## Report

Summarize the Gradle contract changed, focused and full checks run, supported
consumer/platform coverage, and any remaining unverified behavior.
