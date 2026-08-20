# Rust Engine Integration

Rust is the source of truth for PandaWave domain state. Android owns platform
integration, process lifecycle, AIDL, Media3, AAOS UX restrictions, RRO access,
and secure platform services.

## Current Integration

The repository now contains a Rust workspace at `rust/engine` with
`panda_engine_core` and `panda_engine_ffi`. PandaEngine owns the source-of-truth
runtime model:

- `EngineCommand`
- `EngineEvent`
- `EngineSnapshot`
- `EnginePlatformEvent`
- `PlaybackState`
- `RestrictionState`
- `Engine` state machine
- middleware and effects
- repository, queue, session, persistence, Canopy adapters, and networking boundaries

The Kotlin AIDL service in `:core:rust-bridge` selects the native PandaEngine
host through `PandaEngineFactory`. Native-load failures are hard integration
errors, not production fallback paths. `:core:rust-bridge` also declares the
generated `jniLibs` lane used to package Rust Android builds.

The native binding and production host are implemented:

- `panda_engine_ffi` exposes the C ABI over `panda_engine_core` and the
  Android-native JNI entrypoints that translate JVM values into that ABI.
- The Kotlin `PandaEngine` loads `panda_engine_ffi`, owns the native handle,
  installs the encrypted session store, dispatches commands and platform
  events, and maps native snapshots, results, and effects.
- `MediaEngineService` depends on the `RustEngine` interface and uses the
  native-only factory.
- The Gradle native lane builds and packages the Rust library for the supported
  Android ABIs as part of normal app builds.

The fake engine remains an explicit test/local fixture only.

See [native-engine-host.md](native-engine-host.md) for the by-the-books AIDL,
JNI, Rust FFI, and PandaEngine hosting boundary. See
[android-platform-integration.md](android-platform-integration.md) for Android
surface projection, AAOS integration, content providers, and native packaging.


## Canopy Runtime Boundary

PandaEngine talks to Canopy through the pinned BSR Prost/Tonic SDKs and a
secret-free `client-connection.json` deployment handoff. Kotlin loads that asset
and passes it to the native host, but Rust validates the schema, immutable SDK
contract, endpoint rules, authentication metadata shape, and TLS policy before
installing one shared channel for catalog, playback, system, profile, history,
library, playlist, discovery, and account/auth clients.

The Canopy adapter owns session coordination and retry policy. A complete
`SessionEnvelope` is persisted through Rust-owned storage using the Android
Keystore bridge only for cryptographic operations. `CanopyOperation` is the
central table for every RPC's authentication requirement and replay class;
protected calls fail before dispatch when no access session is available, and
refresh uses a single-use refresh credential path.

Playback uses direct opaque capabilities from `ResolvePlayback`. Android and
Media3 receive the resolved URL, MIME type, and expiry as data-plane inputs, and
must preserve the URL byte-for-byte instead of rebuilding or decoding it.

Canopy owns the persistent application data plane: backend-managed local media,
PostgreSQL metadata and authorization policy, and Nginx streaming. Supabase is
not part of the current architecture, and PandaEngine does not embed a
provider-specific catalog or client media database. Its durable client
persistence is limited to scoped state such as the encrypted session envelope.

## Runtime Flow

```text
Android gateway caller
        |
AIDL engine service boundary
        |
Kotlin PandaEngine native binding adapter
        |
JNI shim
        |
Rust FFI facade
        |
Rust app_core / engine crates
        |
EngineSnapshot and platform commands
```

## State Ownership

PandaEngine owns playback, session readiness, queue, catalog, and platform-aware
media behavior. Android callers project those snapshots into their own surface
state, then send user input back as engine commands.

Platform lifecycle and playback changes enter the same boundary as commands
through `EnginePlatformEvent`. The current reducers handle media load/error,
audio focus, suspend-to-RAM, playback completion, vehicle driving state, and UX
restriction changes while returning the resulting canonical snapshot and
effects through the host boundary.

Theme selection remains profile/preference state instead of playback engine
state. Android may project a server-backed profile preference into UI state and
RRO resources, but PandaEngine should not decide visual theme unless that theme
becomes part of a broader profile contract shared with backend/user state.

## Boundary Rule

Kotlin can own platform shapes, but Rust owns client-side domain decisions.
Kotlin must not grow parallel business logic for playback, auth, user state,
catalog normalization, or Canopy operation policy. Fake engines remain explicit
test fixtures and are never production fallback paths.

## Wire Values

AIDL uses simple string values because it is the IPC boundary. The native Rust
FFI uses compact integer discriminants. Kotlin maps between those wire values at
the boundary, while Rust keeps enum-based domain models internally.

## Verification

Run the Android app verification from the project root:

```powershell
.\gradlew.bat --no-configuration-cache :app:assembleDebug
```

The app build compiles and packages PandaEngine for every supported Android ABI
by default. Android native packaging and smoke-test commands live in
[android-platform-integration.md](android-platform-integration.md).


The immutable Canopy SDK and shipped connection assets are checked from the
repository root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-canopy-sdk.ps1
```

Run Rust verification from `rust/engine` after Rust is installed:

```powershell
cargo test --workspace
```
