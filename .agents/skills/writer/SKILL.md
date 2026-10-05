---
name: writer
description: >-
  Use when creating, reviewing, or revising README.md, PROJECT.md, AGENTS.md,
  skill routes, Markdown, Kotlin KDoc, Gradle task descriptions, public errors,
  explanatory comments, or pull-request titles and descriptions; write verified
  project documentation in the Spine style.
---

# Documentation writer

Read the [project context](../../../PROJECT.md), the
[writing style](../../guidelines/writing-style.md), and the
[English style](../../guidelines/english-style.md) before reviewing or editing.

## Establish the audience and owner

- Identify the reader, the problem to solve, and the outcome that makes the text useful.
- Prefer updating the document that already owns the topic.
- Keep `README.md` focused on the user problem, setup, Kotlin DSL configuration,
  tasks, and short contributor commands.
- Keep `PROJECT.md` focused on stable architecture, compatibility, context, and source pointers.
- Keep `AGENTS.md`, skill descriptions, and other agent routes focused on dispatch and
  repository-wide policy. Link to the owning skill instead of duplicating its guidance.
- Keep KDoc with its declaration, task descriptions with their task registrations, and
  public error messages at the boundary that reports the failure.

## Verify before writing

1. Read the complete target file and the related source, tests, build logic, and routes.
2. Treat current repository behavior as authoritative.
3. Verify every path, command, version, default, and platform, along with every task
   name, property, and failure claim, against current evidence.
4. Apply the specialist skill that owns each technical claim being changed.
5. Run the narrowest practical command when prose claims executable behavior or output.
6. Mark any remaining unverified claim explicitly; do not turn an assumption into fact.

## Write user-facing documentation

- Explain the user's problem before introducing implementation details.
- Use Kotlin DSL in Gradle examples. Do not add Groovy DSL examples unless requested.
- Keep examples small, copyable, and consistent with the supported Gradle and Java ranges.
- Describe exact release-tag, checksum, cache, token, and offline behavior without
  weakening or overstating the trust guarantees.
- Prefer direct instructions and observable outcomes over promotional language.
- Keep one source of truth for detailed guidance and link to it from shorter entry points.

## Write pull requests

Describe one concrete improvement and why it matters. Keep the title and body understandable
without opening the code. Preserve essential behavior, constraints, risks, and reviewer actions.

- Omit a trailing period from the title.
- Always include `## Summary` followed by `## Changes`, even for a small change.
- In `Summary`, use one short paragraph stating what the work achieves and how it benefits
  the project.
- In `Changes`, use short outcome bullets without implementation details or repetition
  of the summary.
- Add `## Additional changes` after `Changes` only for incidental work unrelated to the main
  goal. Use short outcome bullets.
- Add other sections only for a distinct constraint or reviewer action. Omit
  verification, testing, and build information, as well as agent attribution.
- For stacked work, add `## Reviewer notes` naming the source branch and exact boundary commit.
  State that earlier commits are outside this task and direct review after that boundary.
  Verify the parent PR's status before claiming it is open or unmerged.
- Add a closing keyword such as `Fixes #123` for every resolved issue.
- Do not hard-wrap pull-request prose, including local drafts. Break lines only for
  intentional Markdown structure.
- Omit implementation inventories, conversation history, and exhaustive examples.

## Write Kotlin documentation

Use the [KDoc policy](../kotlin-engineer/references/kotlin-policy.md#kdoc) as the source of
truth for Kotlin documentation, and apply
[kotlin-engineer](../kotlin-engineer/SKILL.md) to affected Kotlin source.

## Write operational text

- Make task descriptions concise and action-oriented.
- Make public errors state what failed, identify the relevant user-facing value or
  property, and provide a recovery path when one exists.
- Keep credentials and other sensitive values out of errors, examples, and logs.
- Use inline comments to explain why a constraint exists. Do not narrate the next statement.
- Preserve exact machine text, command output, code, generated content, and license text.

## Validate the edit

- Check heading hierarchy, links, paths, terminology, code fences, table alignment, and wrapping.
- Check that changed Markdown and KDoc follow the shared writing and English styles.
- Leave no orphans: reflow or rewrite a paragraph, list item, table cell, or KDoc block
  whose final source line contains one word or an unusually short fragment.
- Run `git diff --check`.
- Run focused Gradle verification when a changed example or comment depends on behavior.
- Report the evidence used, commands run, and any claim that remains unverified.
