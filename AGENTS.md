# Agent instructions

These instructions apply to the whole repository. Keep this file focused on repository-wide
operating policy; keep task-specific procedures in the skills under `.agents/skills/`.

## Orientation

- Read [PROJECT.md](PROJECT.md) before making a non-trivial change.
- Use every discovered skill whose description matches the task.
- Treat each skill's frontmatter description as the routing source of truth.
- Read the affected implementation, nearby tests, and relevant build configuration before editing.

## Skill routing

Use all skills whose scope matches the requested work:

- [`kotlin-engineer`](.agents/skills/kotlin-engineer/SKILL.md): Kotlin source,
  Kotlin DSL, public API design, interoperability, and implementation quality.
- [`gradle-engineer`](.agents/skills/gradle-engineer/SKILL.md): build scripts,
  plugin and task APIs, lazy configuration, compatibility, and publication metadata.
- [`test-engineer`](.agents/skills/test-engineer/SKILL.md): JUnit Jupiter unit tests,
  Gradle TestKit functional tests, reproductions, fixtures, and regression coverage.
- [`writer`](.agents/skills/writer/SKILL.md): Markdown, KDoc, comments, examples,
  task descriptions, public errors, and agent instructions.
- [`security-engineer`](.agents/skills/security-engineer/SKILL.md): release downloads,
  integrity, caches, offline reuse, archives, tokens, redirects, and path containment.
- [`reviewer`](.agents/skills/reviewer/SKILL.md): read-only review of diffs, commits,
  and pull requests, including relevant test execution and specialist review lenses.

Combine skills only when their scopes overlap:

- Kotlin in plugin or build logic normally requires both `kotlin-engineer` and `gradle-engineer`.
- Any behavioral change requires `test-engineer` with the owning implementation skill.
- Security-sensitive behavior requires the affected implementation skill,
  `security-engineer`, and `test-engineer`.
- Documentation and public API text require `writer` plus the skill owning the contract.
- Review uses `reviewer` plus matching specialist skills; the reviewer remains read-only.

## Operating policy

- Preserve unrelated local changes and untracked files.
- Keep changes narrowly scoped. Do not combine opportunistic cleanup with the requested work.
- Ask a question only when an unresolved decision would materially change the result.
- Do not commit, push, tag, merge, rebase, cherry-pick, reply to review threads, or resolve
  threads unless the user's current request explicitly asks for that action.
- Do not publish plugins, change release versions, or use credentials unless explicitly asked.
- Do not add telemetry, analytics, hidden network access, or automatic dependency updates.

## Project defaults

- Use Kotlin and Gradle Kotlin DSL. Do not add Groovy build scripts.
- Use the Gradle wrapper and preserve the compatibility targets declared by the build.
- Preserve Gradle's lazy configuration and supplied Kotlin runtime, along with
  configuration-cache compatibility.
- Preserve JUnit Jupiter and Gradle TestKit. Do not introduce Kotest or a mocking framework
  without an explicit dependency and style decision.
- Use local fixtures or loopback HTTP servers so tests remain independent of live services.

## Documentation

- Follow `.agents/guidelines/writing-style.md` for Markdown, KDoc, comments, and user-facing text.
- Give every named Kotlin type useful KDoc, regardless of visibility.
- Give every public API useful KDoc. Document private members only when they carry a
  non-obvious contract, invariant, side effect, or failure mode.
- Verify documentation claims against current code, tests, build files, or command output.

## Verification

- Start with the narrowest Gradle task or test that proves the change.
- Run `./gradlew check` before handing off a completed code or test change unless the task is
  documentation-only or a narrower check is explicitly sufficient.
- Run `git diff --check` after editing.
- Report every command actually run and distinguish failures caused by the change from
  environment or unrelated failures.

## Agent entry points

- Codex reads this `AGENTS.md` and discovers repository skills under `.agents/skills/`.
- Claude reads [CLAUDE.md](CLAUDE.md), which routes back to this file.
- [GitHub Copilot instructions](.github/copilot-instructions.md) route Copilot back
  to this file and the matching skills.
