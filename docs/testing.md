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


## Canopy static and compatibility gates

Run the immutable SDK compatibility verifier from the repository root whenever
Canopy SDK pins, shipped connection assets, CI, or local integration docs move:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-canopy-sdk.ps1
```

The verifier checks the pinned BSR release/commit/package versions, every
shipped `client-connection.json`, and the absence of local protobuf/OpenAPI
client generation in production paths.

Stage 4 also keeps reusable redaction scans in `config/canopy-secret-patterns.txt`.
Treat a no-match `rg` exit code as clean and an exit code greater than 1 as a
scan failure.

## Live Canopy instrumentation

The live Canopy tests are opt-in because they require the documented WSL backend,
a booted Android emulator, and emulator routing for the backend-issued opaque
playback capability. See [canopy-backend-integration.md](canopy-backend-integration.md)
and [canopy-local-integration.md](canopy-local-integration.md) for the exact commands.

`PandaEngineCanopyLiveTest` proves status, catalog browse, playback resolution,
opaque URL projection, and a ranged audio response. `CanopyProtectedServicesIntegrationTest`
proves authenticated profile, history, library, playlist, discovery, account,
and device-session flows when `canopyProtected=true` and runtime credentials are
supplied. The Media3 Android test separately proves that Android `Uri` preserves
an escaped `%2F` capability without decoding or rewriting it. Normal connected-test
runs skip live backend methods unless their explicit opt-in arguments are supplied.
