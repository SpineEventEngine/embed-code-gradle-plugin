# Test patterns

Match the nearest existing spec before introducing a new helper or layout.

## Unit spec

Use JUnit Jupiter structure and assertions:

```kotlin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`ReleaseTag` should")
internal class ReleaseTagSpec {

    @Test
    fun `reject a blank value`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            validateReleaseTag(" ")
        }

        assertEquals("Release tag must not be blank.", error.message)
    }
}
```

Use several assertions when they describe one behavior. Split a test when a
failure would leave the broken contract unclear.

## Unit or functional

| Behavior | Source set |
|---|---|
| Parse, normalize, hash, validate, or map a value | `src/test/kotlin` |
| Select a platform name without executing Gradle | `src/test/kotlin` |
| Apply the plugin or configure its extension | `src/functionalTest/kotlin` |
| Register, wire, execute, or inspect a task | `src/functionalTest/kotlin` |
| Verify configuration-cache reuse | `src/functionalTest/kotlin` |
| Verify behavior across Gradle versions | `src/functionalTest/kotlin` |
| Launch the fake Embed Code executable | `src/functionalTest/kotlin` |

Do not use TestKit when a direct unit call proves the contract. Do not replace
a consuming-build test with a unit test when Gradle wiring is the behavior.

## TestKit project

Create the consuming project under a JUnit `@TempDir`. Write only the settings,
build script, source, documentation, and fake assets required by the case.

Create runners with the plugin-under-test classpath:

```kotlin
private fun runner(vararg arguments: String): GradleRunner =
    GradleRunner.create()
        .withProjectDir(projectDirectory.toFile())
        .withArguments(
            *arguments,
            "--configuration-cache",
            "--stacktrace",
        )
        .withPluginClasspath()
```

Match the repository's established runner helper when it differs. Keep
configuration cache enabled by default so new behavior does not silently break
the plugin's compatibility claim. Disable it only in a test that explicitly
proves a non-configuration-cache path, and state why.

Assert task outcomes and consumer-visible files or messages:

```kotlin
val result = runner(":checkEmbedding").build()

assertEquals(TaskOutcome.SUCCESS, result.task(":checkEmbedding")?.outcome)
assertEquals("check", Files.readString(projectDirectory.resolve("mode.txt")).trim())
```

Use `buildAndFail()` only when failure is the expected contract. Assert the
actionable message rather than a complete stack trace.

## Fake releases and HTTP

- Build fake release assets inside the temporary project.
- Use fixed payloads and calculate expected SHA-256 values locally.
- Use `HttpServer` on `127.0.0.1` with port `0` when request behavior matters.
- Record request paths, headers, or counts in thread-safe test state.
- Return only the status and body needed by the test.
- Stop the server in `finally`, `@AfterEach`, or another guaranteed cleanup
  path.
- Use a local file URI instead of HTTP when transport behavior is irrelevant.

Never call GitHub or another public endpoint from a test. A live service makes
rate limits, credentials, network failures, mutable releases, and remote state
part of the result.

## Filesystem and process fixtures

- Resolve every fixture below the temporary directory.
- Use fixed file contents and explicit UTF-8 where an API requires a charset.
- Create a minimal fake executable that records arguments and produces a fixed
  exit result.
- Test both the expected path and important containment or replacement
  failures.
- Avoid sleeps. Wait on an observable process, file, or request condition with
  a bounded timeout when synchronization is necessary.
- Apply JUnit OS conditions only to behavior that truly depends on executable
  permissions or platform process semantics.

## Compatibility cases

Use `withGradleVersion(...)` only for versions supported by the plugin's stated
compatibility range. Keep the ordinary runner on the wrapper version. Cover a
second Gradle version when the changed API or behavior varies across Gradle
releases.

Keep Java 17 consumer compatibility distinct from the JDK used to build the
project. A test passing on the build JDK alone does not prove that the published
plugin runs on Java 17.

## Verification commands

Prefer this order:

```bash
./gradlew :gradle-plugin:test --tests io.spine.embedcode.gradle.SubjectSpec
./gradlew :gradle-plugin:functionalTest --tests io.spine.embedcode.gradle.PluginSpec
./gradlew check
```

Run only the applicable focused command first. Run the full check before
claiming completion.
