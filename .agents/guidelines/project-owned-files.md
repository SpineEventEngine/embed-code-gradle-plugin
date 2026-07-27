# Project-owned files

Some skills edit or stamp files across a whole repository. For example,
`proofread` rewrites prose. These skills must touch only the files the
repository **owns**. Editing an upstream file is worse than a no-op: the
next submodule update overwrites the change or turns it into a merge
conflict, and the same edit is needed again.

This page defines the shared scoping rule; the file-type filter stays in the
consuming skill.

## What to skip

### Submodule contents

Skip every path declared as a submodule in `.gitmodules`, and everything
beneath it.

When a skill enumerates files with `git ls-files`, submodules are already
excluded — a submodule appears to the parent repo as a single gitlink, not
as its files. Apply the skip explicitly anyway, so a diff-based run (which
lists changed *paths*) drops a submodule entry the same way.

This repository currently declares no submodules, so the rule is inert here.
It stays documented because a skill must not assume that: `.gitmodules` is
the source of truth, and the rule applies as soon as one is added.

## Everything else is project-owned

This repository writes and owns its own build logic, agent configuration,
and documentation — `buildSrc/`, `AGENTS.md`, `CLAUDE.md`, `gradle.properties`,
`.github/`, and `.agents/` included. Do not skip a file on the assumption
that it is distributed from somewhere else; confirm against `.gitmodules`.
