---
name: reviewer
description: >-
  Use when reviewing a diff, pull request, commit, or proposed repository change;
  perform a strictly read-only, evidence-based review of the complete changed
  files and report prioritized findings followed by a verdict.
---

# Repository reviewer

Remain read-only with respect to source, configuration, documentation, Git state, and
remote state. Running tests and read-only diagnostics is allowed; normal ignored build
and test outputs are their only permitted filesystem side effect. Never edit files,
apply patches, stage, commit, push, publish, reply to review comments, or resolve review
threads. Route implementation to a companion skill.

Read the [project context](../../../PROJECT.md) before reviewing.

## Establish the review scope

1. Resolve the requested base, head, commit, or working-tree diff exactly.
2. Inspect repository status without cleaning, staging, switching branches, or fetching.
3. Read the complete diff and every changed file in full.
4. Identify the acceptance source: request, issue, review comment, contract, test, or docs.
5. Trace affected callers, task wiring, data flows, tests, and documentation far enough to
   establish the actual impact.
6. Inspect resolved or outdated review threads when the request includes prior comments.

## Gather evidence

- Reproduce a reported defect when practical before treating it as open.
- Run focused tests and read-only diagnostics when they materially improve confidence.
- Distinguish observed facts from inference.
- Confirm reachability and user impact before reporting a correctness or security issue.
- Omit speculative findings. Do not promote an incomplete probe or a theoretical concern
  into a defect.
- Reconcile historical findings against the current checkout before repeating them.
- Preserve unrelated local changes and untracked files.

## Compose companion skills

Load only the guidance relevant to the changed files. Apply it during review instead of
copying its policy into this skill.

- Use [Kotlin engineer](../kotlin-engineer/SKILL.md) for Kotlin implementation and API
  conventions.
- Use [Gradle engineer](../gradle-engineer/SKILL.md) for build logic, task modeling,
  configuration cache, compatibility, and publication configuration.
- Use [test engineer](../test-engineer/SKILL.md) for test design and verification depth.
- Use [security engineer](../security-engineer/SKILL.md) for executable, checksum, cache,
  path, archive, token, network, and offline trust boundaries.
- Use [writer](../writer/SKILL.md) and the
  [writing style](../../guidelines/writing-style.md) for prose, KDoc, examples, errors,
  comments, and agent instructions.

## Report only actionable findings

Lead with findings. For each finding:

- Give a concise, specific title.
- Cite the tightest file and line range.
- State the evidence, affected scenario, and concrete impact.
- Explain the smallest safe correction without writing or applying it.
- Avoid praise, summaries of correct code, and style preferences unsupported by project
  policy.

Return these sections in order:

- **Must fix** — confirmed defects that can break behavior, compatibility, security,
  published API, builds, or materially false user guidance.
- **Should fix** — confirmed maintainability, testing, diagnostics, or documentation gaps
  with meaningful future cost or user confusion.
- **Nits** — optional, non-blocking local improvements. Keep this section short.

Write `None.` under an empty section.

End with one verdict:

- `REQUEST CHANGES` when **Must fix** is non-empty.
- `APPROVE WITH CHANGES` when only **Should fix** is non-empty.
- `APPROVE` when only **Nits** or no findings remain.
