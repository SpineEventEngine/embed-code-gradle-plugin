---
name: security-engineer
description: >-
  Apply repository-specific security engineering policy when changing or
  reviewing release URLs or tags, SHA-256 trust, executable caches, offline
  reuse, temporary files, archive extraction, redirects, GitHub tokens, path
  containment, or symbolic-link and Windows-junction handling.
---

# Security engineering

Protect the boundary between remote release data and an executable run by a
consumer build. Require evidence for security claims and preserve fail-closed
behavior when authenticity or filesystem containment cannot be established.

## Start

1. Read [`PROJECT.md`](../../../PROJECT.md) and the affected release, checksum,
   installation, platform, and functional-test code.
2. Identify the attacker-controlled value, trusted value, filesystem boundary,
   online/offline state, and final executable before proposing a change.
3. Reproduce a reported weakness with a concrete regression probe. Do not claim
   exploitability when the probe is unavailable or an existing guard blocks it.
4. Verify the current flow against source and tests, then apply
   [Trust boundaries](references/trust-boundaries.md) to download, cache, redirect,
   archive, token, or path work.

## Cross-domain work

- Apply [`gradle-engineer`](../gradle-engineer/SKILL.md) to task properties,
  local state, configuration cache, and TestKit wiring.
- Use [`test-engineer`](../test-engineer/SKILL.md) for
  deterministic regression fixtures and assertions.
- Apply [`writer`](../writer/SKILL.md) when security behavior changes public
  configuration, diagnostics, or release documentation.

## Verify

Run the smallest relevant unit or TestKit test first. Then run:

1. `./gradlew :gradle-plugin:test`
2. `./gradlew :gradle-plugin:functionalTest`
3. `./gradlew check`

Run filesystem probes on the operating system whose semantics they claim to
cover. Report an unrun Windows junction or Linux permission probe explicitly.

## Report

For implementation, state the trust invariant, local-write assumptions, regression probe,
and verification result. For review, report only reproducible vulnerabilities or concrete
defense-in-depth gaps; omit speculative exploitability and identify unrun probes.
