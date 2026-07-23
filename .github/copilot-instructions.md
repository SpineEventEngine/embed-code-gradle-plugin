# GitHub Copilot instructions

This is the Copilot-specific route for `embed-code-gradle-plugin`. Keep detailed policy in its
owning repository document instead of duplicating it here.

## Read first

- Read [`AGENTS.md`](../AGENTS.md) for repository-wide operating policy.
- Read [`PROJECT.md`](../PROJECT.md) for the project map, runtime flow, compatibility
  constraints, test strategy, and trust boundaries.
- Use matching repository skills under [`.agents/skills/`](../.agents/skills/).

## Skill routing

- Use `kotlin-engineer` for Kotlin language, API, interoperability, and KDoc decisions.
- Use `gradle-engineer` for build scripts, plugin wiring, tasks, lazy configuration,
  configuration cache, compatibility, and publication metadata.
- Use `test-engineer` for JUnit Jupiter unit tests and Gradle TestKit functional tests.
- Use `writer` for Markdown, KDoc, comments, examples, task descriptions, and public errors.
- Use `reviewer` only for non-writing, findings-first review and verification.
- Use `security-engineer` for release assets, checksums, downloads, caches, archives, tokens,
  offline behavior, containment, and symbolic-link safety.

Use multiple skills when a change crosses boundaries. A Kotlin Gradle task generally needs
`kotlin-engineer` and `gradle-engineer`; download or cache logic also needs
`security-engineer`.

## Repository invariants

- Preserve Kotlin DSL, JUnit Jupiter, Gradle TestKit, configuration-cache compatibility, and
  the Gradle-supplied Kotlin runtime.
- Keep tests deterministic and independent of live external services.
- Verify documentation and compatibility claims against current source and build files.
- Do not write, commit, push, publish, reply to review threads, or resolve them while acting as
  `reviewer`.
