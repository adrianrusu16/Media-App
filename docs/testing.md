# Testing

PandaWave uses separate test lanes for host-side Kotlin behavior and Android
platform integration.

## Local JVM tests

Local tests under `src/test` run on the JUnit Platform with JUnit Jupiter and
Kotlin's `kotlin.test` API.

Preferred defaults:

- Write assertions with `kotlin.test`.
- Use backtick test names that read like short sentences.
- Keep test names behavior-focused instead of implementation-focused.
- Prefer simple fakes over mocks when the collaborator is small and local.
- Use Kotlin `use` for `AutoCloseable` or `Closeable` resources.

Example:

```kotlin
@Test
fun `play when ready maps to play intent`() {
    assertEquals(BambooPlaybackIntent.Play, mapper.fromPlayWhenReady(true))
}
```

JUnit Jupiter test instances default to `per_class` through the shared Android
Gradle convention so Kotlin tests can use instance setup without extra
annotation ceremony.

## Android instrumentation tests

Android instrumentation tests under `src/androidTest` stay on
`AndroidJUnitRunner` and AndroidX test APIs. They exercise platform integration,
device/runtime behavior, and native packaging smoke paths.

Instrumentation tests may still use Kotlin language features, including `use`,
but they should not depend on the host-side JUnit Platform runner.
