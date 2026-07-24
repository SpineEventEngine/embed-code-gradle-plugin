# Kotlin policy

Use this reference while changing Kotlin source or Kotlin DSL in this
repository. Prefer nearby established code when it is more specific.

## Compatibility boundary

- Build with the configured toolchain while emitting Java 17-compatible plugin bytecode.
- Treat Java 17 as the maximum runtime API surface. A successful build on a
  newer JDK does not prove consumer compatibility.
- Preserve the configured Kotlin language and API versions and Gradle-supplied runtime.
  Consumers must not need to install Kotlin or apply its plugin.
- Keep the Kotlin standard library non-published as configured. Do not replace
  `compileOnly` with `implementation` without an explicit compatibility reason.
- Avoid hard-coded environment-specific absolute paths. Use Gradle file
  properties and resolve absolute paths only at the task or generated-configuration boundary.

## Declarations and APIs

- Add explicit visibility to public declarations and any declaration whose
  intended boundary could be ambiguous.
- Add explicit return and property types to public APIs. Prefer an explicit
  type internally when it communicates a Gradle or domain contract.
- Avoid exposing mutable collections or implementation-specific mutable state.
- Prefer these Gradle managed properties for configuration:
  `Property<T>`, `ListProperty<T>`, `MapProperty<K, V>`, `DirectoryProperty`,
  and `RegularFileProperty` instead of mutable or eagerly resolved values.
- Accept `Provider<T>` when callers should retain lazy evaluation. Do not
  realize a provider merely to convert it into an eager value.
- Keep task and extension APIs declarative. Put execution work in task actions,
  not property accessors or plugin application.
- Keep user-facing validation close to the configuration or execution boundary.
  Use Gradle exception types when the failure is actionable to a build user.

## Kotlin style

- Follow the official Kotlin conventions and match the closest file.
- Prefer `val`, expression bodies when they remain readable, exhaustive `when`,
  safe calls, Elvis expressions, `require`, `check`, and `requireNotNull`.
- Use a data class only for a value with value-based equality. Do not convert a
  service, task, plugin, or stateful object into a data class.
- Keep functions focused. Extract a helper when it gives a rule or invariant a
  name; avoid helpers that only obscure one straightforward expression.
- Prefer named arguments when adjacent arguments have the same type or the
  call's meaning is otherwise unclear.
- Keep Java interop intentional. Do not add `@JvmStatic`, `@JvmOverloads`, or
  other bridge annotations unless a verified consumer needs them.
- Preserve local constant naming instead of normalizing unrelated declarations.
- Do not introduce unrelated coroutines, Flow, multiplatform, Android, Protobuf, or frameworks.

## Nullability and failures

- Model optional data as nullable only when absence has one clear meaning.
- Avoid `!!`; prefer validation or a non-null type. If an external API makes an
  invariant unrepresentable, document the invariant next to the assertion.
- Catch the narrowest useful exception. Preserve the original cause when
  wrapping an internal failure in an actionable Gradle error.
- Keep credentials and tokens out of user-facing messages. Include a filesystem
  path only when it identifies an actionable value; do not echo low-level exception text.

## KDoc

Document every named type and every public API. Named types include regular,
data, value, sealed, enum, and annotation classes; interfaces; objects;
companion objects; and type aliases. Document every public enum entry. Make the
documentation useful to a plugin consumer or future maintainer.

Include the applicable parts of this contract:

- what the declaration represents or performs;
- defaults and whether configuration is required;
- input, output, caching, offline, or network behavior;
- accepted formats and validation rules;
- observable side effects and failures;
- Java 17, Gradle, or Kotlin-runtime compatibility constraints.

Avoid repeating the declaration in prose:

```kotlin
/**
 * Selects the exact Embed Code release tag to install.
 *
 * Rejects blank or unsafe tags before resolving an installation path.
 */
public abstract val version: Property<String>
```

Use KDoc links only for symbols visible to the documented source set. Do not link published
API documentation to `.agents/`, `buildSrc`, issues, branches, or transient implementation notes.

For private and internal non-type declarations, add KDoc only when it explains
a non-obvious contract. Prefer a short inline comment for a local invariant.
