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
behavior when authenticity or filesystem ownership cannot be established.

## Start

1. Read [`PROJECT.md`](../../../PROJECT.md) and the affected release, checksum,
   installation, platform, and functional-test code.
2. Identify the attacker-controlled value, trusted value, filesystem boundary,
   online/offline state, and final executable before proposing a change.
3. Reproduce a reported weakness with a concrete regression probe. Do not
   present a hypothetical path as exploitable when the probe is unavailable or
   blocked by an existing guard.
4. Read [Trust boundaries](references/trust-boundaries.md) for download, cache,
   redirect, archive, token, or path work.

## Coordinate responsibilities

- Apply [`gradle-engineer`](../gradle-engineer/SKILL.md) to task properties,
  local state, configuration cache, and TestKit wiring.
- Apply [`kotlin-engineer`](../kotlin-engineer/SKILL.md) to Kotlin and Java
  filesystem or network API usage.
- Apply [`test-engineer`](../test-engineer/SKILL.md) to regression structure and
  deterministic fixtures.
- Apply [`reviewer`](../reviewer/SKILL.md) to severity, evidence, and review
  scope.
- Apply [`writer`](../writer/SKILL.md) when security behavior changes public
  configuration, diagnostics, or release documentation.

## Preserve trust invariants

- Accept exact validated release tags. Keep rolling or path-shaped identifiers
  out of release URLs and cache paths.
- Authenticate every downloaded or retained asset with a trusted SHA-256 before
  extraction or execution.
- Bind reusable state to release base URL, exact tag, and platform asset. Treat
  a source-identity mismatch as unverified state.
- Rehash the installed executable before reuse. Never treat file existence,
  executable permission, or an old marker alone as authenticity.
- Fail closed offline unless a locally verified executable is intact or a
  cached asset can be authenticated with an already trusted digest.
- Keep temporary files unpredictable, contained, cleaned in `finally`, and
  promoted only after verification.
- Extract only the expected executable into a controlled staging file. Never
  resolve arbitrary archive entry paths into the installation tree.
- Validate normalized path containment and reject symbolic links, junctions,
  redirecting entries, and non-directory components from the installation root
  to every destination.
- Keep tokens opt-in, internal, and absent from logs, errors, cache metadata,
  task inputs, and requests to untrusted hosts.
- Keep authenticated metadata requests from following redirects. Verify asset
  bytes after any allowed unauthenticated download redirect.

## Build regression evidence

- Prove digest mismatch, stale source identity, modified executable, and
  unauthenticated offline cache behavior.
- Probe `..` and encoded path input, absolute destination overrides, symlinked
  files or directories, and Windows junctions where the host supports them.
- Probe metadata redirects separately from release-asset redirects, including
  whether authorization could cross a host boundary.
- Probe crafted archives against the actual extraction strategy and assert no
  write outside controlled staging and installation paths.
- Assert failure leaves no trusted executable or valid-looking integrity
  metadata behind.

## Verify

Run the smallest relevant unit or TestKit test first. Then run:

1. `./gradlew :gradle-plugin:test`
2. `./gradlew :gradle-plugin:functionalTest`
3. `./gradlew check`

Run filesystem probes on the operating system whose semantics they claim to
cover. Report an unrun Windows junction or Linux permission probe explicitly.

## Report

For implementation, state the trust invariant, regression probe, and verification
result. For review, report only reproducible vulnerabilities or concrete
defense-in-depth gaps; omit speculative exploitability and identify unrun probes.
