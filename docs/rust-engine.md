# Rust Engine Integration

Rust is the source of truth for PandaWave domain state. Android owns platform
integration, process lifecycle, AIDL, Media3, AAOS UX restrictions, RRO access,
and secure platform services.

## Current Milestone

The repository now contains a Rust workspace at `rust/engine` with
`media_app_core` and `panda_engine_ffi`. PandaEngine owns the source-of-truth
runtime model:

- `EngineCommand`
- `EngineEvent`
- `EngineSnapshot`
- `EnginePlatformEvent`
- `PlaybackState`
- `RestrictionState`
- `Engine` state machine
- middleware and effects
- repository, queue, session, persistence, and networking boundaries

The Kotlin AIDL service in `:core:rust-bridge` selects the native PandaEngine
host through `PandaEngineFactory`. Native-load failures are hard integration
errors, not production fallback paths. The next milestone is to package the
Rust native library for Android ABIs so the native path is available on device.

The next binding layer is also scaffolded:

- `panda_engine_ffi` exposes a small C ABI over `media_app_core`.
- `PandaEngine` defines the Kotlin wrapper shape for the future JNI/native
  library.
- `MediaEngineService` depends on the `RustEngine` interface and uses the
  native-only factory.

The fake engine remains an explicit test/local fixture only.

See [native-engine-host.md](native-engine-host.md) for the by-the-books Android
service, AIDL, JNI, Rust FFI, and PandaEngine hosting model.

## Intended Flow

```text
Compose, Media3, AAOS system command
        |
Kotlin platform adapter
        |
AIDL service boundary
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
media behavior. Android projects those snapshots into Compose and Media3 state,
then sends user input back as engine commands.

Platform lifecycle changes enter the same boundary as commands through
`EnginePlatformEvent`. The first events are intentionally no-op state-machine
inputs that update engine time and emit `platform_event_applied`; later reducers
can use the same path for suspend-to-RAM, resume, UX restriction, and service
recovery behavior.

Theme selection remains profile/preference state instead of playback engine
state. Android may project a server-backed profile preference into Compose and
RRO resources, but PandaEngine should not decide visual theme unless that theme
becomes part of a broader profile contract shared with backend/user state.

## Boundary Rule

Kotlin can own platform shapes, but Rust owns decisions. Kotlin should not grow
parallel business logic for playback, auth, user state, catalog normalization, or
provider policy. Temporary fake implementations should stay small and be replaced
by Rust calls as soon as the native binding exists.

## Wire Values

AIDL uses simple string values because it is the IPC boundary. The native Rust
FFI uses compact integer discriminants. Kotlin maps between those wire values at
the boundary, while Rust keeps enum-based domain models internally.

## Verification

Run Android verification from the project root:

```powershell
.\gradlew.bat --no-configuration-cache :app:assembleDebug
```

Run Rust verification from `rust/engine` after Rust is installed:

```powershell
cargo test
```
