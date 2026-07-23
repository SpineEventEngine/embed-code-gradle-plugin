---
name: test-engineer
description: >
  Use for adding, changing, diagnosing, or reviewing Kotlin tests in this
  repository, including JUnit Jupiter unit specs, Gradle TestKit functional
  specs, bug reproductions, and security or compatibility regressions.
---

# Test engineer

## Workflow

1. Read [the project context](../../../PROJECT.md), the production code in full,
   and the closest specs in the same source set.
2. Reproduce a reported defect before changing production code whenever a
   deterministic reproduction is possible.
3. Classify the behavior as a focused unit contract or a consuming-build
   contract. Use [the test patterns](references/test-patterns.md) to choose the
   source set and fixture.
4. Add the smallest test that proves the requested behavior and its important
   failure boundary. Keep unrelated coverage work out of scope.
5. Run the focused spec or source-set task first. Run the full repository check
   after the focused test passes.
6. Report the behavior covered, the test location, and every verification
   command with its result.

## Test style

- Use JUnit Jupiter for test structure and assertions.
- Do not add Kotest, a mocking framework, or another assertion dependency.
- Name a suite for subject `X` as `XSpec`; make Kotlin suites `internal`.
- Annotate every suite with `@DisplayName` describing what the subject should
  do.
- Name test functions as backticked behavioral phrases that read as
  specifications.
- Prefer hand-written stubs, fixed inputs, and observable outputs over mocks or
  implementation-detail verification.
- Keep setup close to the behavior unless a helper removes substantial,
  repeated noise.

## Test boundary

- Put pure functions, validation, parsing, value transformations, and isolated
  class behavior under `src/test/kotlin`.
- Put plugin application, task registration and execution, consuming-build
  configuration, configuration-cache behavior, Gradle compatibility, and
  process/file integration under `src/functionalTest/kotlin` with TestKit.
- Assert outcomes visible to a consumer: task outcomes, files, messages,
  generated configuration, requests, and exit behavior.
- Avoid asserting Gradle internals or private call order.

## Determinism and isolation

- Use temporary directories and fixed fixture contents.
- Replace release services with local files or a loopback fake HTTP server.
- Never depend on live GitHub, release assets, DNS, credentials, or public
  network availability.
- Bind fake servers to loopback on an ephemeral port and stop them reliably.
- Use fixed clocks, versions, digests, and environment values where relevant.
- Gate genuinely platform-specific behavior with JUnit conditions; do not hide
  portable failures behind an OS condition.

## Verification

- Run a focused unit spec with `:gradle-plugin:test --tests <class>`.
- Run a focused functional spec with
  `:gradle-plugin:functionalTest --tests <class>`.
- Run `./gradlew check` after focused verification.
- Run the broader build or publication checks required by
  [gradle-engineer](../gradle-engineer/SKILL.md) when build logic, plugin
  metadata, compatibility, or publication behavior changes.

## Companion skills

- Use [kotlin-engineer](../kotlin-engineer/SKILL.md) for Kotlin and KDoc style
  inside tests and production code.
- Use [gradle-engineer](../gradle-engineer/SKILL.md) for Gradle task and plugin
  semantics.
- Use [security-engineer](../security-engineer/SKILL.md) to identify adversarial
  path, archive, download, checksum, cache, process, and credential cases.
- Use [reviewer](../reviewer/SKILL.md) for an independent findings-first review.
