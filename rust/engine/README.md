# Rust Engine

This workspace contains **PandaEngine**, the Rust source-of-truth engine for PandaWave.

Android modules communicate with the engine through the AIDL service boundary in
`:core:rust-bridge`, while Rust owns deterministic domain behavior (commands,
events, snapshots, middleware, recovery, and effect emission).

## Current State (Middleware / Engine)

- Async-first engine dispatch with explicit `is_busy` lifecycle for long-running work.
- Middleware chain with validation, recovery, and side-effect orchestration.
- Recoverable-path behavior surfaces explicit recovery events (not silent empty outcomes).
- Voice workflow coverage includes negative/error paths and cleanup guarantees.
- FFI bridge has async slow-dispatch coverage to protect against deadlock regressions.
- Networking is transport-agnostic at core boundaries, with gRPC details isolated in networking adapters.

## Architecture At a Glance

```mermaid
flowchart LR
    subgraph Android[Android / AAOS]
        UI[UI + Service]
    end

    subgraph FFI[crates/ffi]
        API[api::* extern C fns]
        TYPES[types + mappings + constants]
        HANDLE[engine_handle]
    end

    subgraph Core[crates/app_core]
        ENGINE[engine::core + state_machine]
        MW[middleware pipeline]
        DATA[data::repository + queue + persistence]
        NET[networking traits + adapters]
        SERVICES[services::*]
    end

    subgraph Remote[Remote Services]
        CANOPY[Canopy gRPC]
    end

    UI --> API
    API --> HANDLE
    HANDLE --> ENGINE
    ENGINE --> MW
    ENGINE --> DATA
    DATA --> NET
    NET --> CANOPY
    ENGINE --> SERVICES
    TYPES -. ABI/domain translation .- API
```

## Crates

```text
crates/app_core
  Core domain + engine logic (models, reducer/state machine, middleware, networking boundaries).

crates/ffi
  C ABI/FFI adapter used by Android bridge layers.
```

### Module Ownership (current)

- `app_core::engine::core` keeps production engine logic; test coverage is split under `app_core::engine::core::core_tests::*` by domain theme.
- `ffi::lib` is a crate root only; FFI internals are split into `api`, `types`, `engine_handle`, `constants`, and `mappings`.

### Key Paths (quick navigation)

- `crates/app_core/src/engine/core.rs` — orchestration root + public `Engine` API.
- `crates/app_core/src/engine/core/` — focused command/platform/effects/persistence/control handlers.
- `crates/app_core/src/middleware/` — trait + standard/analytics/pipeline modules.
- `crates/app_core/src/data/` — queue, repository abstractions, persistence contracts.
- `crates/app_core/src/networking/` — transport traits, canopy adapters, retry wrappers.
- `crates/ffi/src/lib.rs` — stable C ABI exports and curated re-exports.

## Local Verification

Run from `engine/`:

```powershell
cargo test --workspace
cargo clippy --workspace --tests
cargo fmt --check
cargo llvm-cov
```

### Targeted verification during refactors

```powershell
# Core only
cargo test -p media_app_core core::core_tests -- --nocapture

# Networking retry wrappers
cargo test -p media_app_core retrying_backend_client -- --nocapture
cargo test -p media_app_core retrying_audio_source_client -- --nocapture

# FFI boundary
cargo test -p panda_engine_ffi -- --nocapture
cargo check -p media_app_core -p panda_engine_ffi
```

## Architecture Highlights

- **Deterministic Core**: Reducer/state machine drives predictable command->event->snapshot transitions.
- **Middleware Pipeline**: Validation + recovery + telemetry-friendly extension points.
- **Async Repository Contracts**: Async repository operations and busy-state transitions are explicitly tested.
- **Transport Isolation**: Core depends on traits (`BackendClient`, `AudioSourceClient`) rather than transport SDKs.
- **Canopy gRPC Path**: `canopy.proto` + `tonic-build` generated types, channel reuse, interceptor metadata, health mapping.
- **Progressive Search**: Additive streaming search path for progressive UI consumption.
- **Retry Hardening**: Backend/audio-source retry wrappers support policy gating, exponential backoff, jitter, and retry time budgets.
- **Cancellation Safety**: Explicit cancellation coverage for long-running async networking paths.

## Networking Composition (Current)

```text
CanopyTonicTransport
  -> RetryingBackendClient<C>
  -> RemoteRepository<C>
  -> Engine middleware/dispatch

CanopyAudioSourceClient<C>
  -> RetryingAudioSourceClient<C>
  -> Engine playback/source flow
```

## Sequence Diagram: Engine Command Dispatch

```mermaid
sequenceDiagram
    participant AAOS as PandaWave (AAOS App)
    participant FFI as panda_engine_dispatch (FFI)
    participant Core as PandaEngine Core
    participant MW as Middleware Chain
    participant Repo as RemoteRepository
    participant Net as RetryingBackendClient + CanopyTonicTransport

    AAOS->>FFI: dispatch(command, payload, now)
    FFI->>Core: EngineCommand
    Core->>MW: apply(command)
    MW->>Core: set is_busy=true (if async op)
    Core->>Repo: search/browse
    Repo->>Net: backend call (gRPC)
    Net-->>Repo: result or mapped error
    Repo-->>Core: domain items / failure
    Core->>MW: recovery/validation hooks
    MW->>Core: set is_busy=false
    Core-->>FFI: events + snapshot + effects
    FFI-->>AAOS: observer callbacks / effects to execute
```

## Sequence Diagram: Progressive Search Stream

```mermaid
sequenceDiagram
    participant UI as PandaWave UI
    participant Repo as RemoteRepository
    participant Retry as RetryingBackendClient
    participant Transport as CanopyTonicTransport
    participant Canopy as Canopy gRPC Search Stream

    UI->>Repo: search_stream(query)
    Repo->>Retry: search_stream(query)
    Retry->>Transport: open stream attempt #1
    Transport->>Canopy: Search(query)

    alt retryable startup failure
        Canopy-->>Transport: UNAVAILABLE / DEADLINE_EXCEEDED
        Transport-->>Retry: mapped error
        Retry->>Retry: policy + backoff/jitter + budget check
        Retry->>Transport: open stream attempt #N
    end

    Transport-->>Retry: stream opened
    Retry-->>Repo: MediaItemStream

    loop for each streamed chunk
        Canopy-->>Transport: SearchResult item
        Transport-->>Repo: mapped MediaItem
        Repo-->>UI: progressive item
    end

    opt cancellation
        UI--xRepo: drop stream / cancel task
        Repo--xRetry: stream dropped
        Retry--xTransport: cancel in-flight stream
    end
```

## FFI Integration Guide (Android)

1. **Initialize Logging**: `panda_engine_init_logging(level)`
2. **Create Engine**: `panda_engine_create(now_millis)`
3. **Enable Vosk (Optional)**: `panda_engine_enable_vosk(engine, model_path)`.
4. **Set Observer**: `panda_engine_set_observer(...)` for state/event callbacks.
5. **Dispatch Commands**: `panda_engine_dispatch(engine, type, payload, now_millis)`.
6. **Process Audio**: Stream PCM 16-bit 16kHz mono via `EngineCommandType::ProcessVoiceAudio`.
7. **Handle Effects**: Query `panda_engine_get_effects_count/types` and execute platform effects.
8. **Persist/Restore**: Use `panda_engine_save` and `panda_engine_restore` on lifecycle transitions.

## Contribution Notes (modularization guardrails)

- Prefer thin module roots (`mod.rs` or top-level file) plus focused submodules by responsibility.
- Keep crate boundaries clean: `app_core` should not depend on Android/platform details; `ffi` should not own domain rules.
- Keep public surface intentional: default to `pub(crate)`/`pub(super)` unless a symbol is required cross-module/crate.
- For large files, split by behavior domain first (dispatch/effects/persistence/tests), not by arbitrary line count.
- After each structural change, run at least targeted tests for touched areas plus `cargo check` for both core and FFI crates.
