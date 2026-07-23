# Gradle practices

Use these rules when changing build logic or the Gradle-facing implementation.
Verify current versions and paths in the checkout before relying on this map.

## Contents

- [Project ownership](#project-ownership)
- [Lazy configuration](#lazy-configuration)
- [Task state and caching](#task-state-and-caching)
- [Configuration cache](#configuration-cache)
- [Compatibility and Kotlin runtime](#compatibility-and-kotlin-runtime)
- [Kotlin DSL and build logic](#kotlin-dsl-and-build-logic)
- [Cross-platform behavior](#cross-platform-behavior)
- [Publication](#publication)
- [Test strategy](#test-strategy)
- [Repository exclusions](#repository-exclusions)

## Project ownership

- Keep repository composition and dependency repositories in
  `settings.gradle.kts`.
- Keep shared coordinates and version application in the root build and
  `version.gradle.kts`.
- Keep build-only constants and dependency coordinates in `buildSrc/`.
- Keep shared JVM compilation and test setup in the `jvm-module` convention
  plugin.
- Keep plugin declaration, generated sources, functional-test source set, and
  publication metadata in `gradle-plugin/build.gradle.kts`.
- Keep extension defaults and task registration in `EmbedCodePlugin`.
- Keep user-configurable values in `EmbedCodeExtension`.
- Keep execution behavior in typed task classes. Keep pure release, platform,
  JSON, and checksum rules in focused Kotlin files.

## Lazy configuration

- Use `tasks.register`, `tasks.named`, provider mapping, and `flatMap`.
- Pass `TaskProvider` and provider-backed files through the graph instead of
  calling `get()` during configuration.
- Use conventions for defaults and preserve later user configuration.
- Use `disallowChanges()` only for values owned entirely by the plugin after
  wiring.
- Preserve task dependencies carried by `Provider<Directory>` and
  `ConfigurableFileCollection`.
- Avoid configuration-time downloads, process execution, directory creation,
  and file reads whose result belongs to task execution.
- Keep collision handling lazy. Preserve underscore-prefixed fallback task
  names when preferred names are occupied.

## Task state and caching

- Mark scalar and collection configuration with `@Input`.
- Mark source directories with `@InputDirectory` or `@InputFiles`; declare
  `@Optional` only when absence is valid.
- Mark consumed artifacts with `@InputFile`.
- Mark reproducible task products with output annotations.
- Mark retained caches, downloaded assets, and integrity markers that should
  not enter the build cache with `@LocalState`.
- Mark services, roots used only to validate other properties, working
  directories, and secrets with `@Internal`.
- Choose `PathSensitivity.RELATIVE` for project content and
  `PathSensitivity.NONE` when only file bytes matter.
- Disable build caching for in-place document mutation, external mutable
  release state, or tasks whose primary purpose is validating local state.
- Override up-to-date behavior only with an explicit invariant. The install
  task intentionally reruns to authenticate local installation state.

## Configuration cache

- Inject `ExecOperations` and other Gradle services into task types.
- Resolve extension values into task properties during configuration.
- Avoid retaining `Project`, extension objects, closures over project state, or
  arbitrary mutable objects for task execution.
- Keep task actions dependent only on declared task properties, injected
  services, and intentional internal/local state.
- Verify a second invocation reports configuration-cache reuse after changing
  plugin application, registration, task properties, or process execution.

## Compatibility and Kotlin runtime

- Preserve the documented consumer floor of Gradle 8.14.4.
- Treat the wrapper as the development runtime, not as the minimum supported
  consumer version.
- Keep Java bytecode at version 17 while supporting Gradle 8.14.4 and Gradle 9.
- Keep Kotlin language and API usage compatible with Kotlin 2.0.21 supplied by
  Gradle 8.14.4.
- Keep Kotlin standard library dependencies `compileOnly` for plugin
  publication. Supply explicit test runtime dependencies where unit tests need
  Gradle and Kotlin classes.
- Avoid Kotlin or Gradle APIs introduced after the supported consumer floor
  unless guarded by a compatible alternative and covered by versioned TestKit.
- Change the floor only together with README, build settings, compatibility
  tests, and publication claims.

## Kotlin DSL and build logic

- Prefer typed Kotlin DSL accessors and typed task registration.
- Keep reusable module policy in convention plugins rather than copying blocks
  between projects.
- Keep dependency coordinates centralized only when that improves ownership;
  avoid abstraction for a single opaque use.
- Keep public plugin configuration in `build.gradle.kts`. Do not require users
  to maintain an additional YAML file.
- Treat internally generated JSON configuration as task implementation state,
  not as a public configuration surface.

## Cross-platform behavior

- Use Gradle file properties and Java path APIs instead of string concatenation
  for filesystem paths.
- Preserve Windows `.exe` naming and Linux executable permissions.
- Keep path comparisons case and separator aware. Do not infer Windows safety
  from a Linux-only test.
- Avoid shell-specific execution; pass executable and arguments separately.
- Preserve stable argument ordering and messages when tests or users rely on
  them.
- Add platform conditions only for genuinely unsupported host behavior, not to
  hide portable failures.

## Publication

- Keep `io.spine.embed-code` and its implementation class stable unless a
  migration is explicitly requested.
- Keep display name, description, website, VCS URL, and tags consistent with
  README terminology.
- Keep Maven artifact ID, POM name, description, license, developers, and SCM
  coordinates aligned with the plugin declaration.
- Keep `LICENSE` in published JARs and keep sources and Javadoc artifacts.
- Run `validatePlugins` after changing task annotations, plugin declarations,
  extension types, or publication configuration.

## Test strategy

- Use unit tests for pure version, checksum, JSON, and platform rules.
- Use TestKit functional tests for plugin application, DSL wiring, task
  dependencies, task-name collisions, help output, configuration-cache reuse,
  publication-facing behavior, and real Gradle failures.
- Keep a TestKit probe on Gradle 8.14.4 for the minimum consumer contract.
- Assert task outcomes, generated files, process arguments, and actionable
  output rather than internal registration details alone.
- Run focused test classes while iterating, then unit tests, functional tests,
  `validatePlugins`, and `check`.

## Repository exclusions

- Do not add configuration-repository filtering copied from a shared
  organization build unless this repository adopts that policy explicitly.
- Do not require dependency group names to contain `spine`.
- Do not expose `installEmbedCode` in the standard grouped task list merely for
  consistency. Its hidden helper status is intentional; `checkEmbedding` and
  `embedCode` are the user-facing tasks.
