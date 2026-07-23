---
name: gradle-engineer
description: >-
  Apply repository-specific Gradle engineering policy when changing or reviewing
  build scripts, buildSrc, Kotlin DSL, plugin, extension, or task APIs, lazy
  configuration, task state, configuration-cache behavior, TestKit coverage,
  consumer compatibility, or publication metadata.
---

# Gradle engineering

Preserve Gradle semantics across the plugin and its build. Treat configuration avoidance,
task state, consumer compatibility, and cross-platform behavior as public contracts.

## Start

1. Read [`PROJECT.md`](../../../PROJECT.md) for the project map and supported behavior.
2. Read the affected build scripts, plugin API, and task types, along with
   nearby unit and functional tests in full.
3. Trace values from the extension through `Provider` wiring into task inputs,
   execution behavior, outputs, and local state.
4. Read [Gradle practices](references/gradle-practices.md) before editing; use it
   as the source of truth for Gradle implementation and verification policy.
5. Clarify only material ambiguity in the public DSL, compatibility floor, or test target.

## Cross-domain work

- Apply [`kotlin-engineer`](../kotlin-engineer/SKILL.md) to Kotlin source and build logic.
- Apply [`security-engineer`](../security-engineer/SKILL.md) to release
  downloads, integrity metadata, offline reuse, tokens, archives, and paths.

## Verify

Run the narrowest relevant command first, then broaden:

1. `./gradlew :gradle-plugin:test --tests <test-class>`
2. `./gradlew :gradle-plugin:functionalTest --tests <test-class>`
3. `./gradlew :gradle-plugin:test :gradle-plugin:functionalTest`
4. `./gradlew :gradle-plugin:validatePlugins`
5. `./gradlew check`

Add or run the minimum-version TestKit scenario when compatibility-sensitive
code changes. Exercise configuration-cache reuse when plugin or task wiring changes.
Exercise platform-specific behavior on the relevant operating system; report any
unavailable supported-platform probe instead of treating another host as proof.

## Report

Summarize the Gradle contract changed, focused and full checks run, supported
consumer/platform coverage, and any remaining unverified behavior.
