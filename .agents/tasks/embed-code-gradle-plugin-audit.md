# Embed Code Gradle Plugin — Full Audit

**Scope:** entire repository at `master` commit `a2b0478` (merge of
[PR #9](https://github.com/SpineEventEngine/embed-code-gradle-plugin/pull/9), plugin version `0.1.1`).
Covers all production sources, build logic (`buildSrc`, build scripts), CI workflow, tests, and README.
Static analysis and a live check of the upstream release layout; the build and test suite were **not** executed.

---

## Summary

The plugin is in strong shape. The install pipeline is fail-closed end to end: release assets are
authenticated by SHA-256 (explicit `embedCode.sha256` or the GitHub Releases API `digest` field) before
first installation, the installed executable is re-hashed against its stored digest before every reuse,
and a modified installation is rebuilt from the retained, re-authenticated asset. Cache paths are
containment-checked and symlink/junction-hardened with `NOFOLLOW_LINKS` on every read, write, and move.
Release tags are charset-validated at both configuration surfaces and mapped to hashed, filesystem-portable
cache keys. The GitHub token is explicit opt-in, attached only to `api.github.com` metadata requests, with
redirects disabled on those requests. Configuration-cache compatibility is declared and functionally tested.
Test coverage is extensive: 58 functional (TestKit) tests across Gradle 8.14.4/9.0.0 and 23 unit tests.

One **high** forward-compatibility issue is outstanding (Linux asset naming), plus a handful of medium
process/coverage gaps and low-priority cleanups.

---

## High

### H1. Linux asset mapping will break on the next upstream release

`EmbedCodePlatform.kt:73` requests the Linux asset as `embed-code-linux` (bare binary).
Verified live during this audit: the current upstream latest (`v1.2.4`) still publishes
`embed-code-linux` unzipped, so everything works **today**. However, the upstream release workflow
on `embed-code-go`'s default branch now zips the Linux binary ("Publish Linux as a ZIP so Unix
executable permissions survive extraction"). The **next** release will publish `embed-code-linux.zip`,
and latest-mode installs on Linux will fail with HTTP 404 — and the `addReleaseTagMigrationHint` text
will not explain why.

**Recommendation:** try `embed-code-linux.zip` first and fall back to the bare asset (or select by
release metadata), so both existing and future releases install. Update the functional-test fixture
(`createFakeRelease`) in the same change — it currently mirrors the bare-binary layout and masks this.

---

## Medium

### M1. CI never builds `master`

`.github/workflows/check.yml` still triggers only on `pull_request`. The post-merge state of `master`
is never verified, and the README badge reflects the latest PR run, not `master`. Flagged in every
review since PR #1. Two lines: add `push: branches: [master]`.

### M2. `checkEmbedding` is not wired into the `check` lifecycle, and the choice is undocumented

Consumers running `./gradlew check` will not get embedding verification; the README shows manual
invocation without explaining that this is deliberate (network dependency). Either wire it (possibly
opt-in) or add one sentence of rationale to the README's *Execution* section.

### M3. The zip-extraction path is never exercised in CI

The CI matrix is `ubuntu-latest` + `windows-latest`, but only the macOS assets are ZIPs — so
`extractExecutable` and the extract-from-cached-asset restore path run only on macOS, which has no
runner. Either add `macos-latest` to the matrix, or add a functional test that overrides the install
task's `operatingSystem`/`architecture` to a macOS platform on any host (the plugin already documents
that overriding these inputs changes asset selection only), with a zipped fake release.

### M4. No toolchain auto-provisioning for contributors

The build requires **two** JDKs — 25 (toolchain) and 17 (test launchers) — but `settings.gradle.kts`
configures no toolchain resolver. On a machine without both preinstalled, the build fails instead of
downloading them. Add the `org.gradle.toolchains.foojay-resolver-convention` settings plugin.

### M5. No release/publish automation

Publishing metadata is complete and Portal-ready (plugin ID `io.spine.embed-code`, POM, sources/javadoc
jars, configuration-cache compatibility declared), but the only workflow is `check.yml`. There is no
`publishPlugins` workflow, no tag-triggered release, and no documented manual release procedure or
CHANGELOG. Before Portal submission, add at least a documented release process; a tag-triggered
workflow with Portal keys in repository secrets is the standard shape.

---

## Low

### L1. `installFromVerifiedAsset` swallows `IOException`

`InstallEmbedCodeTask.kt:576` — the catch returns `false`, and the online fresh-download path then
throws a generic "could not be restored" error with the underlying cause lost. Log the exception (or
attach it to the thrown `GradleException`) to keep restore failures diagnosable.

### L2. `sha256` with rolling latest is advised against but not enforced

The KDoc now says to configure `sha256` "together with `version`", but the combination
(`sha256` set, `version` absent) is still accepted and hard-fails on the next upstream release when
the downloaded asset stops matching. Consider failing fast at execution with a message that says to
pin `version`, rather than surfacing as a digest mismatch.

### L3. Redundant path-validation work

`requireNoSymbolicLinks` re-runs `requireInsideInstallationDirectory` + `prepareInstallationDirectory`
and walks every component with `toRealPath()` on each call; it is invoked for every path, again inside
`readStoredValue`/`writeStoredValue`/`deleteSafely`, and `moveSafely` validates before *and* after the
move. Reading metadata also has the side effect of creating the installation directory tree.
Correctness is fine; consolidating (validate root once per task action, then leaf-only checks) would
cut filesystem stats and simplify the flow.

### L4. Duplicated versions in `buildSrc`

`kotlinVersion = "2.4.10"` and `pluginPublishVersion = "2.1.1"` in `buildSrc/build.gradle.kts` each
carry a "keep in sync" comment pointing at the dependency objects. A `buildSrc/gradle.properties` or a
version catalog would remove both traps.

### L5. `rootProject.extra["embedCodePluginVersion"]!!`

A typo in the key fails with a bare NPE at configuration time (`build.gradle.kts`). A lookup with a
descriptive error, or moving the version to `gradle.properties`, fails more legibly.

### L6. Local build cache not enabled

`gradle.properties` enables configuration cache and parallelism but not `org.gradle.caching=true` —
a free speedup for `buildSrc` and compilation tasks.

### L7. README no longer explains `$name/` references for named sources

The named-source example is followed only by the mutual-exclusivity note; the sentence explaining that
embedding instructions reference the roots as `$model/` / `$database/` was dropped during the PR #9
README restructure and not restored.

### L8. Reuse log messages have four variants

"Reusing previously verified…", "Reusing verified…", "Reusing locally verified… in offline mode",
"Reusing the cached executable…" — all describe closely related outcomes and are individually pinned
by tests. Unifying the phrasing would simplify both the UX and the assertions.

### L9. `JUnit.kt` style inconsistencies

`Jupiter` uses `$version` while `PlatformLauncher` uses `${JUnit.version}` and inlines the
`org.junit.platform` group as a raw string; no comment notes that JUnit 6 unified platform/jupiter
versioning (a `1.x` reflex from JUnit 5 will read it as a bug).

### L10. Supply-chain hardening for CI itself

Actions are tag-pinned (`checkout@v7`, `setup-java@v5`, `setup-gradle@v6`) rather than SHA-pinned, and
there is no Dependabot/Renovate configuration for actions, the wrapper, or `buildSrc` dependencies.
The wrapper itself is well protected (`distributionSha256Sum` verified against the official Gradle
9.6.1 checksum, URL validation on).

---

## Informational — accepted limitations worth keeping on record

- **TOCTOU residue:** symlink checks are check-then-open; the final path component is protected by
  `NOFOLLOW_LINKS` at open time, but an intermediate directory could in principle be swapped between
  check and use. Exploitation requires concurrent local write access to `build/`, which already implies
  arbitrary build code execution — reasonable to accept, and the KDoc acknowledges the lexical nature
  of the checks.
- **Sticky latest:** "latest" is resolved once and pinned until `clean`. This is now clearly documented
  in the README (*Version* section) with the local re-verification behavior. Users wanting automatic
  updates must clean; that tradeoff is deliberate.
- **Concurrent daemons:** two simultaneous installs can interleave metadata writes; individual writes
  are atomic and any mixed state fails verification on the next run and self-heals via re-download.
- **Zip decompression limits:** `extractExecutable` has no size cap, but extraction only ever runs on
  digest-verified assets, so a zip bomb requires compromising the digest source first.
- **Groovy `JsonSlurper`:** rides on the Gradle-embedded Groovy runtime — appropriate for a Gradle
  plugin; would need revisiting only if the code were reused outside Gradle.
- **Unauthenticated API rate limits:** the first online install may hit GitHub's 60/hour
  unauthenticated `api.github.com` limit on shared CI IPs; error messages now point to
  `embedCode.sha256`/`embedCode.githubToken`, and the README's CI guidance covers it. Warm caches make
  this a first-run-only concern.

---

## Verdict

No correctness or security defects found in the current code. **H1 is the one item that will cause a
real-world outage** (Linux latest-mode installs, on the next upstream release) and should be fixed
proactively rather than reactively. M1–M5 are process and coverage gaps that matter increasingly as the
plugin approaches Gradle Plugin Portal submission. The lows are cleanups that can ride along with other
work.
