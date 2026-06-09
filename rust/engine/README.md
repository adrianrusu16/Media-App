# Rust Engine

This workspace contains PandaEngine, the Rust source-of-truth engine for PandaWave.

Rust owns auth/session state, Supabase and provider API calls, local database
management, playback decisions, user/profile state, catalog normalization, sync
policy, telemetry shaping, and security-sensitive domain logic.

Android modules communicate with the engine through the AIDL service boundary in
`:core:rust-bridge`. The Kotlin service currently uses a fake reducer with the
same wire values; future milestones will move that reducer call behind a native
Rust binding.

## Crates

```text
crates/app_core
  Dependency-free domain state, command/event types, snapshots, and reducer.
```

## Local Verification

After installing Rust, run:

```powershell
cargo test
```

from this directory.

## Architecture Highlights

- **Modular Reducer**: Decoupled state management with deterministic transitions.
- **Middleware Pipeline**: Extensible chain for logging, telemetry, and validation.
- **Voice Interaction**: Pluggable ASR/NLU architecture with contextual metadata support.
- **Thread Safety**: Concurrent access model optimized for Android's multi-threaded environment.
- **Effect System**: Pure logic emits side-effects (Effects) for the platform to execute.
- **Persistence**: Cross-session state recovery with AAOS-compliant resume logic.

## FFI Integration Guide (Android)

1. **Initialize Logging**: `panda_engine_init_logging(level)`
2. **Create Engine**: `panda_engine_create(now_millis)`
3. **Set Observer**: `panda_engine_set_observer(...)` for real-time state/event callbacks.
4. **Dispatch Commands**: `panda_engine_dispatch(engine, type, payload, now_millis)`
5. **Process Audio**: Stream PCM data via `EngineCommandType::ProcessVoiceAudio` during voice interactions.
6. **Handle Effects**: After each tick or dispatch, query effects using `panda_engine_get_effects_count/types` and execute them (e.g., call ExoPlayer).
7. **Manage Persistence**: Use `panda_engine_save` and `panda_engine_restore` during app lifecycle events.
