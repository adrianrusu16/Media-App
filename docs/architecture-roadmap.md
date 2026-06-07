# Architecture Roadmap

This document captures the intended direction for PandaWave. It should evolve as
the implementation lands, but the core principle is stable: Rust owns the source
of truth, while Android owns the vehicle and platform surfaces.

## Principles

- Rust is the canonical source of truth for app, playback, catalog, user, data,
  and telemetry state.
- AIDL is the primary Android-side boundary to the engine.
- Kotlin owns Android lifecycle, Compose UI, Media3 integration, Hilt wiring,
  Android Keystore access, AAOS UX restrictions, and RRO resource access.
- Media3 and the platform media session are the integration path for AAOS media
  center, system controls, widgets, and the system bar mini-player.
- Automotive safety rules are product requirements, not late validation tasks.
- Security-sensitive logic belongs behind the Rust engine boundary whenever
  possible.

## System Shape

```mermaid
flowchart TD
    System["AAOS media center, system bar, widgets, voice"]
    Compose["PandaWave Compose UI and Bamboo mini-player"]
    Media3["Bamboo MediaLibraryService and MediaSession"]
    Car["AAOS adapters: UX restrictions, RROs, vehicle signals"]
    Aidl["AIDL engine service contract"]
    Rust["PandaEngine source-of-truth runtime"]
    Data["Supabase, Jamendo provider, local encrypted DB"]
    Player["Platform player: ExoPlayer or OEM adapter"]

    System --> Media3
    Compose --> Aidl
    Media3 --> Aidl
    Car --> Aidl
    Aidl --> Rust
    Rust --> Data
    Rust --> Aidl
    Aidl --> Player
    Player --> Media3
```

## Planned Modules

| Area | Modules | Responsibility |
| --- | --- | --- |
| App shell | `:app`, `:feature:appshell` | Startup, navigation host, top-level Android wiring, shell MVI |
| UI | `:core:designsystem`, `:core:ui`, feature modules | Compose screens, reusable mini-player, theme tokens |
| Automotive | `:core:automotive`, `:core:vehicle`, `:core:carui` | UX restrictions, RRO bridge, vehicle signal abstraction, CarUiLib/OEM hooks |
| Media | `:core:media-adapter` | Media3 service/session and platform playback execution |
| Engine boundary | `:core:rust-bridge` | AIDL client, service binding, DTO mapping |
| Security | `:core:secure-storage-adapter` | Android Keystore bridge for Rust-managed encrypted storage |
| Observability | `:core:telemetry-adapter` | Platform sinks for logs, crashes, traces, and redacted telemetry |
| Rust runtime | `:rust:engine` | Auth, API calls, local DB, playback state, catalog, user, sync, telemetry policy |

## Naming

- PandaWave is the product and user-facing brand.
- `RustEngine` remains the Kotlin interface for the Android-to-Rust boundary.
- PandaEngine is the concrete source-of-truth engine implementation, including
  the native binding wrapper and Rust FFI surface.
- Bamboo names Android playback/player-facing surfaces, such as the in-app
  mini-player and Media3 library service.

## Milestones

| Milestone | Commit Theme | Outcome |
| --- | --- | --- |
| 1 | `chore: add initial AAOS car app template` | Untouched Android Studio Car No Activity baseline |
| 2 | `docs: add AAOS media app architecture roadmap` | Project vision, architecture diagram, milestone plan |
| 3 | `chore: add Gradle version catalog and convention plugins` | Centralized dependency/plugin management |
| 4 | `chore: add modular project structure` | Core and feature modules created with empty contracts |
| 5 | `feat: add AIDL engine service boundary` | Bound service, stable command/snapshot DTO shape, fake engine |
| 6 | `feat: add Rust engine skeleton` | Rust workspace, Android build wiring, smoke test through AIDL adapter |
| 7 | `feat: add secure storage bridge` | Android Keystore-backed key access for Rust-managed encrypted data |
| 8 | `feat: add Media3 playback foundation` | MediaLibraryService, MediaSession, player adapter, system controls |
| 9 | `feat: add automotive UX restriction handling` | Restriction monitor, safe navigation rules, simplified restricted mini-player |
| 10 | `feat: add RRO-ready design tokens` | Overlayable resources and Compose theme bridge |
| 11 | `feat: add Rust-owned data layer` | Supabase, local DB, provider abstraction, fake providers for tests |
| 12 | `feat: add settings and profile flows` | User settings, profile state, privacy controls |
| 13 | `chore: add observability pipeline` | Structured logging, crash reporting, traces, telemetry redaction |
| 14 | `test: add unit and instrumentation coverage` | Kotlin, Rust, Media3, Compose, and automotive adapter tests |
| 15 | `ci: add GitHub Actions workflow` | Lint, unit tests, Rust checks, Dokka, build validation |

## AIDL Boundary

The AIDL service should expose coarse commands and snapshots instead of a chatty
getter API. Kotlin components dispatch events and observe snapshots. Rust
validates commands, updates canonical state, and returns platform work as typed
commands.

Android features depend on `EngineGateway`, the app-facing port for engine
commands and snapshots. The current app graph uses `InProcessEngineGateway`
over the fake `RustEngine`, while `AidlEngineGateway` and
`AndroidEngineServiceConnection` prepare the real bound-service path. Flipping
from in-process to AIDL should replace only the gateway binding, leaving
repositories, use cases, UI, and Bamboo Media3 surfaces on the same boundary.
Repositories subscribe to gateway snapshots so engine changes from system media
controls, Media3, or future service-side work can update UI state without
waiting for a local screen intent.

Example service shape:

```aidl
interface IMediaEngineService {
    EngineSnapshot getSnapshot();
    void dispatch(in EngineCommand command);
    void registerListener(IEngineListener listener);
    void unregisterListener(IEngineListener listener);
}
```

## App Presentation Pattern

Android UI should follow an MVI shape: Compose renders immutable state, sends
typed intents, and does not call platform, network, database, or Rust APIs
directly. Repositories own state sources, while use cases define the app-facing
operations that ViewModels call.
Hilt owns Android-side dependency graphs, with app-wide engine and telemetry
objects scoped separately from view-model-owned UI state repositories.

The app shell now lives in `:feature:appshell`, with `:app` kept as the Android
composition root for startup, manifest, and app-wide singleton bindings. As the
implementation grows, destination content can move behind feature/domain module
boundaries without changing the screen model.
Home, Library, Search, Settings, and Profile now expose route Composables from
their own modules, while the shell keeps shared chrome, navigation state, and
the in-app mini-player.
Settings is the first destination with its own feature MVI stack, use cases,
ViewModel, repository, and Hilt bindings. Its privacy and personalization
controls observe AAOS UX restrictions and become parked-only when required.
Playback-facing UI state is mapped from engine snapshots so PandaEngine can
replace the fake implementation without changing Compose screens.
Now Playing is the first playback-owned feature MVI surface. It observes the
PandaEngine snapshot through the `EngineGateway` interface, dispatches playback
intents through the engine boundary, and mirrors AAOS UX restrictions without
taking ownership of playback state.

## Playback Ownership

PandaEngine drives playback decisions and state. Android executes platform playback and
media-session work because AAOS owns those surfaces.

```text
Media command -> Bamboo Media3 adapter -> AIDL -> PandaEngine reducer
PandaEngine playback command -> AIDL -> PlatformPlayer -> ExoPlayer/OEM adapter
Player event -> AIDL -> PandaEngine -> canonical playback snapshot
```

The first Android playback foundation is intentionally platform-only: a Media3
`MediaLibraryService` exposes an `ExoPlayer`-backed session to AAOS and media
controllers, while library contents and command policy stay reserved for the
Rust engine wiring milestone.
Media3 play-state changes are projected into engine commands, so system and
AAOS media controls follow the same Rust-owned state path as in-app controls.

## Security Posture

- Never ship Supabase service-role keys or backend-only secrets in the app.
- Treat the Supabase anon/publishable key as public and rely on Row Level
  Security, scoped JWTs, and backend-only privileged operations.
- Keep auth/session, database, and provider logic behind the Rust boundary.
- Store encryption material through Android Keystore and expose only a narrow
  platform key provider to Rust.
- Redact tokens, user identifiers, request bodies, and native errors from logs.
- Route Android logs and diagnostics through the telemetry adapter so redaction
  is applied before events reach sinks.
- Use typed errors across AIDL and never expose raw Rust panics to callers.
- Keep the AIDL service non-exported unless an OEM/system integration requires a
  signature-protected exported service.
- Run dependency audit, static analysis, unit tests, and instrumentation tests in
  CI.

## Automotive Integration

- Use Media3 and `MediaSession` as the platform media path.
- Observe AAOS UX restrictions with a platform adapter and project them into
  app and Rust state.
- Use RRO-ready design tokens for OEM customization.
- Prefer public `android.car` APIs for vehicle state.
- Keep HAL/VHAL integrations behind an OEM-only adapter boundary.
- Use CarUiLib and Car UI plugins where available in OEM/system-image builds,
  while preserving a Compose Material fallback for regular distribution.
