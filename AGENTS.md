# Agent instructions

These instructions apply to the whole repository. Keep this file focused on repository-wide
operating policy; keep task-specific procedures in the skills under `.agents/skills/`.

## Orientation

- Read [PROJECT.md](PROJECT.md) before making a non-trivial change.
- Use every discovered skill whose description matches the task.
- Treat each skill's frontmatter description as the routing source of truth.
- Read the affected implementation, nearby tests, and relevant build configuration before editing.

## Skill routing

Use all matching skills. The links are an index; each frontmatter description defines its scope:

- [`kotlin-engineer`](.agents/skills/kotlin-engineer/SKILL.md)
- [`gradle-engineer`](.agents/skills/gradle-engineer/SKILL.md)
- [`test-engineer`](.agents/skills/test-engineer/SKILL.md)
- [`writer`](.agents/skills/writer/SKILL.md)
- [`security-engineer`](.agents/skills/security-engineer/SKILL.md)
- [`reviewer`](.agents/skills/reviewer/SKILL.md)

Combine skills only when their scopes overlap:

- Kotlin in plugin or build logic normally requires both `kotlin-engineer` and `gradle-engineer`.
- Any behavioral change requires `test-engineer` with the owning implementation skill.
- Security work requires `security-engineer` and `test-engineer`, plus the skill
  responsible for the affected implementation.
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
- Preserve lazy Gradle configuration, its supplied Kotlin runtime, and configuration-cache support.
- Preserve JUnit Jupiter and Gradle TestKit. Do not introduce Kotest or a mocking framework
  without an explicit dependency and style decision.
- Use local fixtures or loopback HTTP servers so tests remain independent of live services.

## Documentation

- Apply the [writer skill](.agents/skills/writer/SKILL.md) and
  [writing style](.agents/guidelines/writing-style.md) to documentation and user-facing text.
- Follow the
  [Kotlin KDoc policy](.agents/skills/kotlin-engineer/references/kotlin-policy.md#kdoc)
  for declarations and public APIs.
- Verify documentation claims against current code, tests, build files, or command output.

## Verification

- Agent configuration validation requires Python 3.10 or newer.
- After changing agent documentation, routes, or skills, run
  `python3 scripts/check_agent_config.py`.
- After changing the validator, run
  `python3 -m unittest discover -s scripts/tests -t . -p 'test_*.py'`.
- Start with the narrowest Gradle task or test that proves the change.
- Run `./gradlew check` before handing off a completed code or test change unless the task is
  documentation-only or a narrower check is explicitly sufficient.
- Run `git diff --check` after editing.
- Report every command actually run. Separate failures caused by the change from failures
  caused by the environment or unrelated work.

## Reporting

- Include a `## Used skills` section in every final response.
- List all materially applied skills, including repository and platform-provided skills.
- Do not list skills that were merely discovered, inspected, or considered.
- Write `- None.` when no skill was used.
- Place the section last unless a required machine-readable trailer must remain last.

## Agent entry points

- Codex reads this `AGENTS.md` and discovers repository skills under `.agents/skills/`.
- Claude reads [CLAUDE.md](CLAUDE.md), which routes back to this file.
- [GitHub Copilot instructions](.github/copilot-instructions.md) route Copilot back
  to this file and the matching skills.
