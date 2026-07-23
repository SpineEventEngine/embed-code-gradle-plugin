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
2. Reproduce a reported defect deterministically before changing production code when possible.
3. Classify the behavior as a focused unit contract or a consuming-build contract. Use
   [the test patterns](references/test-patterns.md) for source-set selection,
   deterministic fixtures, TestKit setup, and verification commands.
4. Add the smallest test that proves the requested behavior and its important
   failure boundary. Keep unrelated coverage work out of scope.
5. After the focused spec or source-set task passes, run the full repository check.
6. Report the covered behavior, test location, and result of every verification command.

## Test style

- Use JUnit Jupiter for test structure and assertions.
- Do not add Kotest, a mocking framework, or another assertion dependency.
- Name a suite for subject `X` as `XSpec`; make Kotlin suites `internal`.
- Annotate every suite with `@DisplayName` describing the subject's expected behavior.
- Name test functions as backticked behavioral specifications.
- Prefer hand-written stubs, fixed inputs, and observable outputs to mocks and call-order checks.
- Keep setup close to the behavior; extract a helper only when it removes substantial noise.

## Cross-domain work

- Apply [gradle-engineer](../gradle-engineer/SKILL.md) when expected behavior depends
  on Gradle lifecycle, task annotations, configuration cache, or compatibility.
- Apply [security-engineer](../security-engineer/SKILL.md) to identify adversarial
  path, archive, download, checksum, cache, process, and credential cases.
