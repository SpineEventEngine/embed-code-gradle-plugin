---
name: kotlin-engineer
description: >-
  Use for implementing, refactoring, explaining, or reviewing Kotlin source and
  Kotlin DSL files (`.kt` and `.kts`) in this repository, especially public
  plugin APIs, Gradle task types, extensions, and build logic.
---

# Kotlin engineer

## Workflow

1. Read [the project context](../../../PROJECT.md), the complete affected files,
   their nearest tests, and the build configuration that defines their runtime.
2. Identify whether the change affects a public Gradle API, task execution,
   build configuration, serialization, file handling, or network behavior.
3. Read [the Kotlin policy](references/kotlin-policy.md) before editing; use it
   as the source of truth for compatibility, declarations, style, failures, and KDoc.
4. Preserve the existing architecture and behavior outside the requested scope.
   Prefer the smallest idiomatic change that makes the contract explicit.
5. Add or update tests through
   [test-engineer](../test-engineer/SKILL.md) for every behavioral change.
6. Verify with the narrowest relevant Gradle task, then run the broader check
   required by the changed surface.
7. Report the affected contract, files changed, and verification performed.

## Cross-domain work

- Apply [gradle-engineer](../gradle-engineer/SKILL.md) when Kotlin declarations
  participate in Gradle APIs, task modeling, providers, or plugin lifecycle.
- Apply [security-engineer](../security-engineer/SKILL.md) when Kotlin changes affect
  downloads, checksums, archives, paths, caches, processes, or credentials.
