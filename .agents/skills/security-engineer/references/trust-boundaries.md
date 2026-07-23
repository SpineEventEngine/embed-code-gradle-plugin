# Trust boundaries

Use this reference to reason about release installation and executable reuse.
Re-read current implementation and tests before assuming the flow is unchanged.

## Contents

- [Fresh installation or rebuild flow](#fresh-installation-or-rebuild-flow)
- [Trust inputs](#trust-inputs)
- [Release identity](#release-identity)
- [SHA-256 trust](#sha-256-trust)
- [Cache and offline reuse](#cache-and-offline-reuse)
- [Temporary files and promotion](#temporary-files-and-promotion)
- [Archive extraction](#archive-extraction)
- [Path containment](#path-containment)
- [Tokens and redirects](#tokens-and-redirects)
- [Regression probes](#regression-probes)
- [Review standard](#review-standard)

## Fresh installation or rebuild flow

When installing or rebuilding from an asset, preserve this order:

1. Validate the exact release tag and select the platform asset.
2. Normalize the release base URL and derive source identity from its URL, exact tag, and asset.
3. Resolve a trusted SHA-256 from explicit configuration or supported GitHub release metadata.
4. Download into an unpredictable task-temporary file, or stage a retained
   cached asset without following links.
5. Hash staged bytes and compare them with the trusted digest.
6. Extract or copy into a separate staged executable.
7. Mark the staged file executable and calculate its digest.
8. Move it into a checked installation path.
9. Write asset and executable digests plus source identity through checked temporary files.

Do not execute or expose an artifact as valid, and do not persist its trust metadata,
until the corresponding verification succeeds.

For direct reuse, require the stored source identity to match current release inputs and require
a valid stored asset digest. If SHA-256 is configured, it must match that digest. Rehash the
installed executable and compare it with its stored digest. This path requires no network.
It relies on markers from a prior verified installation and detects executable changes only
while those markers remain intact. It does not resist replacement of both executable and markers.

## Trust inputs

Treat these values as untrusted until validated:

- user-configured release tag;
- custom release base URL;
- operating-system and architecture strings when task inputs are overridden;
- remote response status, headers, metadata JSON, and asset bytes;
- archive entry names and content;
- retained cache files and marker files;
- public task output and local-state paths when consumers reconfigure them;
- every existing filesystem component below the installation root.

Treat these values as trust anchors only within their intended scope:

- an explicitly configured normalized SHA-256;
- the digest for the named asset returned by the supported GitHub Releases API.

Stored source-identity and digest markers record prior verification; they are not independent
trust anchors against an actor who can replace both the executable and its markers.

## Release identity

- Keep release tags exact and case-sensitive.
- Reject empty, rolling `latest`, slash-containing, traversal-shaped, and
  unsupported-character tags before URL or path construction.
- Use a filesystem-portable hash of the tag for cache directories; do not use
  the raw tag as a directory name.
- Bind cache state to release base URL, exact tag, and asset name.
- Invalidate reuse when any identity component changes, regardless of an existing executable.
- Preserve explicit checksum configuration as authoritative for custom mirrors.

## SHA-256 trust

- Normalize accepted SHA-256 text to exactly 64 lowercase hexadecimal digits.
- Prefer an explicitly configured digest when present.
- Resolve automatic digests only for the supported HTTPS `github.com` release
  URL shape and the exact named asset from `api.github.com`.
- Require explicit SHA-256 configuration for unsupported custom release sources.
- Hash staged bytes with no-follow file access before extraction.
- Hash the prepared executable separately; asset and executable digests guard different reuse paths.
- Report expected and observed mismatch values only when they contain no secrets.

## Cache and offline reuse

- Treat cached assets and integrity markers as mutable local state.
- Use source-identity and executable-digest markers to detect stale or altered files.
- Do not claim that direct reuse resists an actor who can replace both
  the executable and its markers.
- Treat write access to the installation directory as part of the local trust boundary.
- Reauthenticate a cached asset before rebuilding a missing or modified executable.
- In offline mode, reuse an executable only when current state matches prior local markers.
- Rebuild from cache offline only when a trusted configured digest authenticates the asset.
- Fail closed when neither permitted offline path verifies its required state.
- Never treat a file in the build directory as trusted merely because Gradle is offline.

## Temporary files and promotion

- Create unpredictable download and preparation filenames in the task temporary directory.
- Create metadata replacement files with unpredictable names beside checked destinations.
- Open retained sources with `NOFOLLOW_LINKS`.
- Verify staged data before moving it into the installation tree.
- Prefer atomic replacement and retain a safe fallback when atomic moves are unsupported.
- Recheck destination components before and after promotion.
- Delete temporary files in `finally`, including failure paths.
- Write integrity metadata only after preparing and promoting the executable successfully.

## Archive extraction

- Treat every ZIP entry name as untrusted.
- Stream the expected platform executable's bytes into a caller-selected staging file.
- Never resolve archive paths into the installation directory or extract arbitrary structure.
- Reject archives that lack the expected executable.
- Add explicit handling and probes before accepting archives with ambiguous
  duplicate basenames, unexpected link metadata, or resource-exhaustion risk.
- Do not report zip-slip from a `../` entry alone when the implementation never
  uses the entry path as an output path; prove an out-of-bound write.

## Path containment

- Normalize the installation root and every configured destination.
- Require destinations to be strict descendants of the installation root.
- Do not rely on lexical `startsWith` alone.
- Reject a symbolic-link or redirecting installation root.
- Walk every existing path component without following links.
- Reject symbolic links, Windows junctions, other redirecting filesystem
  entries, and non-directory intermediate components.
- Create missing directories one component at a time and verify their real
  paths remain below the real installation root.
- Check containment for executable, cached asset, digest, source identity, and metadata paths.
- Recheck after moves to reduce time-of-check/time-of-use exposure.
- Test Windows junction semantics on Windows; Unix symlink tests do not replace that probe.

## Tokens and redirects

- Read a GitHub token only from explicit plugin configuration.
- Mark it internal and keep it out of task fingerprints, cache identity,
  persisted metadata, logs, exception messages, and generated configuration.
- Attach authorization only to the exact trusted GitHub API host for checksum metadata.
- Disable redirects for authenticated metadata requests.
- If redirect handling changes, validate every hop and never forward
  authorization across a host boundary.
- Allow unauthenticated asset redirects only while final downloaded bytes remain
  subject to trusted SHA-256 verification before use.
- Keep actionable HTTP status and source context without echoing credentials.

## Regression probes

- **Tag:** Try `../../escaped`, slash, encoded slash, empty, and `latest`. Reject
  them before URL or path use.
- **Digest:** Serve bytes that differ from the trusted SHA-256; install no trusted output.
- **Identity:** Change the base URL, tag case, or platform asset with a cache
  present. Reject stale reuse or reauthenticate it.
- **Executable:** Modify installed bytes after verification. Reject direct reuse.
- **Local markers:** Replace the executable and its markers together. Do not claim
  resistance unless an independent trust anchor authenticates the resulting state.
- **Offline:** Remove metadata or modify the cache without a configured digest; fail closed.
- **Temporary files:** Pre-create likely names and force a mid-install failure.
  Avoid collisions and clean staging files.
- **Archive:** Include traversal entries and the expected executable; write only
  to the controlled staging file.
- **Output path:** Reconfigure executable or metadata outside the root; reject it before writing.
- **Symbolic link:** Link an intermediate directory, asset, marker, or
  destination. Reject it without touching the target.
- **Junction:** Redirect the installation root or a child on Windows; leave the target untouched.
- **Metadata redirect:** Return a `3xx` response from the authenticated API
  endpoint. Do not follow it or forward the token.
- **Asset redirect:** Redirect to bytes with a bad digest. Follow only as
  allowed, then reject the bytes.
- **Token:** Run failures with verbose logging. Never expose the token text.

Assert both the failure message and filesystem aftermath. A failing build alone
does not prove containment or cleanup.

## Review standard

- Reproduce against the current checkout and actual public configuration path.
- Identify the trust boundary, attacker capability, reachable operation, and concrete impact.
- Distinguish a vulnerability from hardening, misleading diagnostics, or an unreachable edge.
- Omit exploitability claims when the required host-specific or network probe is unavailable.
- Prefer a minimal regression fixture that fails before the fix and passes afterward.
- Preserve unrelated cache, release, and compatibility behavior when fixing confirmed issues.
