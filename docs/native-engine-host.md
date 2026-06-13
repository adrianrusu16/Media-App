# Native Engine Host

PandaWave treats PandaEngine as a platform-grade runtime, not a helper library.
The Android app owns Android lifecycle and surfaces; Rust owns deterministic
media state and domain decisions.

## Ecosystem Map

```mermaid
flowchart LR
    PandaWave["PandaWave\nAAOS app"]
    BambooUI["BambooUI\nUI and design system"]
    PandaEngine["PandaEngine\nRust engine and middleware"]
    Canopy["Canopy\ngRPC backend"]
    JadeStore["JadeStore\nCanopy persistence"]
    JadeCache["JadeCache\nCanopy cache"]
    JadeSync["JadeSync\nfuture sync"]
    PandaOS["PandaOS\nfuture AAOS image"]

    PandaOS -->|Media APIs, widgets, system UI| PandaWave
    PandaWave --> BambooUI
    PandaWave -->|AIDL + JNI host boundary| PandaEngine
    PandaEngine -->|gRPC| Canopy
    Canopy --> JadeStore
    Canopy --> JadeCache
    Canopy --> JadeSync
```

## Host Stack

```mermaid
flowchart TD
    Surfaces["Compose, Media3, notifications, widgets, AAOS media center"]
    KotlinAdapters["Kotlin repositories and platform adapters"]
    Aidl["AIDL engine service\nprocess and client boundary"]
    Host["Kotlin PandaEngineHost\nRustEngine implementation"]
    Jni["Handwritten JNI shim\nAndroid-native binding"]
    Ffi["Rust FFI facade\nC ABI, structs, constants, panic containment"]
    Core["PandaEngine core\nstate machine, middleware, data, effects"]
    AndroidEffects["Android effect executors\nExoPlayer, audio focus, notifications"]

    Surfaces --> KotlinAdapters
    KotlinAdapters --> Aidl
    Aidl --> Host
    Host --> Jni
    Jni --> Ffi
    Ffi --> Core
    Core --> Ffi
    Ffi --> Jni
    Jni --> Host
    Host --> Aidl
    Aidl --> KotlinAdapters
    KotlinAdapters --> AndroidEffects
    AndroidEffects -->|platform events| KotlinAdapters
```

## Boundary Responsibilities

| Boundary | Owns | Must Not Own |
| --- | --- | --- |
| PandaEngine core | Domain state, state machine, middleware, queue, catalog, session, effects | Android lifecycle, JNI, AIDL, UI naming |
| Rust FFI facade | ABI-safe handles, constants, structs, memory rules, panic containment | Domain decisions |
| JNI shim | JVM/native conversion and Android-specific native entrypoints | Business logic |
| Kotlin engine host | Native handle lifecycle, fallback policy, thread dispatch, DTO mapping | Rust state transitions |
| AIDL service | Process boundary, listener registration, snapshots, command/event delivery | UI policy |
| Android adapters | Media3, widgets, notifications, audio focus, AAOS restrictions, RROs | Canonical playback/catalog state |

## Service Model

The engine should be hosted by a non-exported Android service boundary and
observed by Media3, Compose, widgets, and future PandaOS surfaces through the
same gateway contract. Android can keep media affordances alive through
`MediaLibraryService`, while the engine service owns one Rust engine instance.

```mermaid
sequenceDiagram
    participant Widget as Home widget / AAOS control
    participant Media3 as MediaLibraryService
    participant Gateway as EngineGateway
    participant Service as MediaEngineService
    participant Host as PandaEngineHost
    participant Rust as PandaEngine

    Widget->>Media3: media command
    Media3->>Gateway: dispatch command
    Gateway->>Service: AIDL command
    Service->>Host: RustEngine.dispatch
    Host->>Rust: JNI -> FFI
    Rust-->>Host: outcome snapshot + event + effects
    Host-->>Service: mapped result
    Service-->>Gateway: listener snapshot/event
    Gateway-->>Media3: projected state
    Media3-->>Widget: platform media update
```

## Integration Milestones

1. **Native Library Packaging**
   Build `panda_engine_ffi` for Android ABIs and package
   `libpanda_engine_ffi.so` into the app or `:core:rust-bridge`.

2. **JNI Shim**
   Add Android-native JNI entrypoints that match `PandaEngine.kt`. The shim
   should call the Rust FFI facade and convert FFI structs into Kotlin-friendly
   primitives or DTOs.

3. **Native Engine Selection**
   Replace fake-only construction with a native-first factory and explicit fake
   fallback for tests/local failure modes.

4. **Contract Expansion**
   Expand AIDL/Kotlin commands, snapshots, events, and effects to match the
   Rust engine contract intentionally. Do not expose every Rust field by habit;
   expose fields that Android surfaces need.

5. **Effect Execution**
   Route engine effects to Android executors, then report platform events back
   into PandaEngine.

6. **Engine-Backed Catalog**
   Replace placeholder Media3 browsing/search with engine browse/search
   commands and snapshot/result projection.

## Naming Rule

Use **Bamboo** for user-facing UI and design-system components, such as
`BambooMiniPlayer`, theme tokens, controls, and visible UI surfaces.

Use **Panda** or neutral media/domain names for implementation and source of
truth types, such as engine hosts, media catalog nodes, snapshots, repositories,
and adapters that are not visible to the user.
