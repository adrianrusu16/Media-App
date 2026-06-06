# Rust Engine Integration

Rust is the source of truth for Media App domain state. Android owns platform
integration, process lifecycle, AIDL, Media3, AAOS UX restrictions, RRO access,
and secure platform services.

## Current Milestone

The repository now contains a Rust workspace at `rust/engine` with a
dependency-free `media_app_core` crate. It models the first engine primitives:

- `EngineCommand`
- `EngineEvent`
- `EngineSnapshot`
- `PlaybackState`
- `RestrictionState`
- `Engine` reducer

The Kotlin AIDL service in `:core:rust-bridge` still uses a fake reducer. It
shares the same wire values as the Rust reducer so the later binding swap is
mechanical rather than architectural.

The next binding layer is also scaffolded:

- `media_app_ffi` exposes a small C ABI over `media_app_core`.
- `NativeRustEngine` defines the Kotlin wrapper shape for the future JNI/native
  library.
- `MediaEngineService` depends on the `RustEngine` interface and currently uses
  `FakeRustEngine`.

`NativeRustEngine` should not be selected until the native library is packaged
into the Android app.

## Intended Flow

```text
Compose, Media3, AAOS system command
        |
Kotlin platform adapter
        |
AIDL service boundary
        |
Kotlin native binding adapter
        |
Rust app_core / engine crates
        |
EngineSnapshot and platform commands
```

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
