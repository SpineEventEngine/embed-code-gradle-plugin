# Agent instructions

These instructions apply to the whole repository. Keep this file focused on
repository-wide operating policy; keep task-specific procedures in the skills under
`.agents/skills/`.

## Orientation

- Read [PROJECT.md](PROJECT.md) before making a non-trivial change.
- Use every discovered skill whose description matches the task. Kotlin code inside Gradle
  plugins normally needs both `kotlin-engineer` and `gradle-engineer`.
- Treat each skill's frontmatter description as the routing source of truth.
- Read the affected implementation, nearby tests, and relevant build configuration before
  editing.

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
- Preserve Gradle's lazy configuration model, configuration-cache compatibility, and the
  Gradle-supplied Kotlin runtime.
- Preserve JUnit Jupiter and Gradle TestKit. Do not introduce Kotest or a mocking framework
  without an explicit dependency and style decision.
- Use local fixtures or local HTTP servers in tests; do not make the test suite depend on live
  external services.
- Apply `.agents/skills/security-engineer/SKILL.md` to download, checksum, cache, archive,
  token, path-containment, symlink, and offline-mode changes.

## Documentation

- Follow `.agents/guidelines/writing-style.md` for Markdown, KDoc, comments, and user-facing
  text.
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
- GitHub Copilot reads
  [`.github/copilot-instructions.md`](.github/copilot-instructions.md), which routes back to
  this file and the matching skills.
