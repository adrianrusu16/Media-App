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
