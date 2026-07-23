---
name: kotlin-engineer
description: >
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
3. Load [the Kotlin policy](references/kotlin-policy.md) for declaration,
   compatibility, style, and KDoc patterns.
4. Preserve the existing architecture and behavior outside the requested scope.
   Prefer the smallest idiomatic change that makes the contract explicit.
5. Add or update tests through
   [test-engineer](../test-engineer/SKILL.md) for every behavioral change.
6. Verify with the narrowest relevant Gradle task, then run the broader check
   required by the changed surface.
7. Report the affected contract, files changed, and verification performed.

## Required policy

- Follow the official Kotlin coding conventions and the established local
  formatting, naming, and file organization.
- Preserve explicit API mode. Declare visibility and API types deliberately;
  avoid accidental public surface expansion.
- Keep published plugin bytecode compatible with Java 17 even when the build
  uses a newer JDK. Do not call runtime APIs introduced after Java 17.
- Preserve the Gradle-supplied Kotlin runtime model. Do not package a separate
  Kotlin runtime or raise the configured language/API compatibility casually.
- Prefer immutable values and read-only interfaces. Limit mutation to the
  narrowest implementation scope.
- Express invalid user configuration with clear validation at the boundary.
  Keep internal failures distinct from actionable Gradle user errors.
- Avoid `!!`. Use it only for a proven invariant that cannot be represented
  more safely, and explain that invariant next to the use.
- Keep comments useful: explain constraints, invariants, or surprising choices
  rather than restating code.

## Documentation contract

- Write useful KDoc for every named type, regardless of visibility: classes,
  interfaces, objects, companion objects, enum classes, annotation classes,
  and type aliases.
- Write useful KDoc for every public constructor, property, and function.
- Document every public enum entry.
- Document private and internal non-type declarations only when their contract
  or implementation is not obvious.
- Describe behavior, defaults, side effects, failure conditions, Gradle
  lifecycle semantics, and compatibility constraints where applicable.
- Update KDoc when code changes invalidate an existing statement.

## Companion skills

- Use [gradle-engineer](../gradle-engineer/SKILL.md) for Provider API, task
  modeling, configuration-cache, and plugin wiring decisions.
- Use [test-engineer](../test-engineer/SKILL.md) for unit and TestKit tests.
- Use [security-engineer](../security-engineer/SKILL.md) when changing downloads,
  checksums, archives, paths, caches, processes, or credentials.
- Use [writer](../writer/SKILL.md) for README and other user-facing prose.
- Use [reviewer](../reviewer/SKILL.md) for an independent findings-first review.
