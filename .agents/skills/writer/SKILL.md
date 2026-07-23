---
name: writer
description: >-
  Use when creating or revising README.md, PROJECT.md, AGENTS.md, skill routes,
  Markdown, Kotlin KDoc, Gradle task descriptions, public errors, or explanatory
  comments; write verified project documentation in the Spine style.
---

# Documentation writer

Read the [project context](../../../PROJECT.md) and the
[writing style](../../guidelines/writing-style.md) before editing.

## Establish the audience and owner

- Identify the reader, the problem to solve, and the outcome that makes the text useful.
- Prefer updating the document that already owns the topic.
- Keep `README.md` focused on the user problem, setup, Kotlin DSL configuration,
  tasks, and short contributor commands.
- Keep `PROJECT.md` focused on stable project context and architecture,
  compatibility, and source-of-truth pointers.
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

## Write Kotlin documentation

- Give every named type useful KDoc, regardless of visibility. Cover classes, objects,
  companion objects, interfaces, enums, annotation classes, type aliases, and sealed types.
- Give every public API useful KDoc, including public constructors, functions,
  properties, constants, and nested declarations.
- Document private members only when their intent, invariant, lifecycle, side effect,
  failure mode, or trust boundary is not obvious from the code.
- Start with a short behavioral summary. Explain contracts and reasons, not syntax.
- Use KDoc links such as `[EmbedCodeTask]` for code symbols.
- Use `@param`, `@return`, and `@throws` only when they add information
  that the signature and summary do not provide.
- Preserve useful existing constraints and explanatory comments when restructuring prose.
- Reject boilerplate that restates a declaration, assignment, or obvious return value.

## Write operational text

- Make task descriptions concise and action-oriented.
- Make public errors state what failed, identify the relevant user-facing value or
  property, and provide a recovery path when one exists.
- Keep credentials and other sensitive values out of errors, examples, and logs.
- Use inline comments to explain why a constraint exists. Do not narrate the next statement.
- Preserve exact machine text, command output, code, generated content, and license text.

## Validate the edit

- Check heading hierarchy, links, referenced paths, terminology, code fences, and wrapping.
- Check that changed Markdown and KDoc follow the shared writing style.
- Leave no orphans: reflow or rewrite a paragraph, list item, table cell, or KDoc block
  whose final source line contains one word or an unusually short fragment.
- Run `git diff --check`.
- Run focused Gradle verification when a changed example or comment depends on behavior.
- Report the evidence used, commands run, and any claim that remains unverified.
