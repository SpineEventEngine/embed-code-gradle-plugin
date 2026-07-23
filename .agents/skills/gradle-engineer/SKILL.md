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
2. Read the affected build scripts, plugin API, task types, and nearby unit or
   functional tests in full.
3. Trace values from the extension through `Provider` wiring into task inputs,
   execution behavior, outputs, and local state.
4. Read [Gradle practices](references/gradle-practices.md) when changing task
   modeling, compatibility, build logic, publication, or platform behavior.
5. Clarify only material ambiguity in the public DSL, compatibility floor, or
   verification target before editing.

## Coordinate responsibilities

- Apply [`kotlin-engineer`](../kotlin-engineer/SKILL.md) to Kotlin source and
  build-logic implementation quality.
- Apply [`test-engineer`](../test-engineer/SKILL.md) to test structure,
  assertions, fixtures, and regression design.
- Apply [`security-engineer`](../security-engineer/SKILL.md) to release
  downloads, integrity metadata, offline reuse, tokens, archives, and paths.
- Apply [`writer`](../writer/SKILL.md) to public DSL documentation and examples.
- Apply [`reviewer`](../reviewer/SKILL.md) for findings-first review output.

## Apply the Gradle model

- Register tasks lazily and retain `TaskProvider` or `Provider` values. Avoid
  eager realization and configuration-time filesystem or network work.
- Model configurable values with `Property`, `ListProperty`, `MapProperty`,
  `DirectoryProperty`, `RegularFileProperty`, and provider transformations.
- Wire producer-backed files through providers or file collections so Gradle
  retains task dependencies.
- Declare every task input, output, local-state file, and internal value
  according to its actual semantics. Choose path sensitivity deliberately.
- Keep secrets internal. Never expose a token as an input, log value, cache
  identity, or publication value.
- Keep task actions configuration-cache safe. Inject execution services and
  avoid accessing `Project` or mutable configuration state at execution time.
- Disable caching or up-to-date reuse when in-place writes, mutable external
  sources, or validation of local state make reuse unsound.
- Prefer Kotlin DSL and existing convention plugins over ad hoc Groovy or
  duplicated module configuration.

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
- Do not import organization-wide configuration-repository filtering or add a
  rule requiring dependency groups to contain `spine`; neither is a repository
  policy.

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
