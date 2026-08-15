# Module Structure

This is the intended modular layout for PandaWave. The modules will be
created incrementally after the Gradle convention layer is in place.

## Target Layout

```text
media_app/
  app/
    Android app shell, startup, manifest, navigation host

  core/
    common/
      Shared Kotlin primitives, result types, dispatchers, coroutine helpers
    model/
      Kotlin DTOs and UI-facing models mapped from AIDL snapshots
    designsystem/
      Compose theme, typed RRO-backed colors, typography, motion, geometry,
      state selectors, and stable branded drawables
    ui/
      Reusable BambooUI navigation, discovery, preference-card, playback,
      focus, feedback, and mini-player components
    playback/
      Shared Bamboo playback state, command gating, and engine/UX observation
    automotive/
      AAOS feature detection, UX restrictions, car-safe state
    vehicle/
      Vehicle signal abstraction over public car APIs and OEM-only adapters
    carui/
      CarUiLib hooks, OEM/system-image UI adapters, Compose fallback bridge
    media-adapter/
      Bamboo Media3 MediaLibraryService, MediaSession, player adapter
    rust-bridge/
      AIDL client, service binding, parcelable mapping, PandaEngine adapter
    secure-storage-adapter/
      Android Keystore bridge for Rust-managed encrypted storage
    telemetry-adapter/
      Android logging, crash, trace, and telemetry sinks
    testing/
      Test fakes, fixtures, Compose test helpers

  feature/
    appshell/
      App-wide Compose shell, MVI state, root screen chrome, destination routing,
      and shell DI
    home/
      Landing media surface and resume/recent content
    library/
      User library browsing
    search/
      Safe search and provider-backed discovery
    nowplaying/
      Full now-playing experience
    settings/
      Parked-only settings and privacy controls
    profile/
      User profile and account state
    auth/
      Sign-in, session recovery, account bootstrap

  provider/
    jamendo/
      Optional Jamendo provider adapter behind Rust-owned provider policy

  rust/
    engine/
      Rust workspace for Canopy auth/API adapters, local encrypted state,
      playback, user state, catalog, sync, telemetry policy, and FFI/AIDL
      integration support

  build-logic/
    Gradle convention plugins for Android app, Android library, Compose, Hilt,
    testing, Dokka, Rust integration, and CI tasks
```

## Dependency Direction

```text
feature:* -> core:ui -> core:model
feature:appshell, feature:nowplaying, core:media-adapter -> core:playback -> core:rust-bridge
app -> feature:appshell
feature:appshell -> feature:* (navigation destinations, as they become concrete)
core:playback -> core:automotive
core:rust-bridge -> AIDL service boundary
Rust engine -> Canopy gRPC, optional provider adapters, local encrypted state
```

Feature modules should not call Canopy, provider adapters, SQLite, native code,
or platform cryptographic APIs directly. They dispatch user/system events to
the engine boundary and render snapshots returned by Rust.

## Product Flavors

```text
play
  Public AAOS distribution, Compose fallback UI, public android.car APIs only

oemSystem
  OEM/system-image integration, CarUiLib hooks, RRO/plugin customization,
  signature permissions, and deeper vehicle adapters where available
```

The flavor boundary keeps privileged automotive integration available without
making it a requirement for the regular app build.

