# Module Structure

This is the intended modular layout for the AAOS media app. The modules will be
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
      Compose theme, RRO-backed tokens, icons, typography, spacing
    ui/
      Reusable Compose components, including the in-app mini-player
    automotive/
      AAOS feature detection, UX restrictions, car-safe state
    vehicle/
      Vehicle signal abstraction over public car APIs and OEM-only adapters
    carui/
      CarUiLib hooks, OEM/system-image UI adapters, Compose fallback bridge
    media-adapter/
      Media3 MediaLibraryService, MediaSession, player adapter
    rust-bridge/
      AIDL client, service binding, parcelable mapping, fake engine
    secure-storage-adapter/
      Android Keystore bridge for Rust-managed encrypted storage
    telemetry-adapter/
      Android logging, crash, trace, and telemetry sinks
    testing/
      Test fakes, fixtures, Compose test helpers

  feature/
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
      Rust workspace for auth, API calls, local DB, playback, user state,
      catalog, sync, telemetry policy, and FFI/AIDL integration support

  build-logic/
    Gradle convention plugins for Android app, Android library, Compose, Hilt,
    testing, Dokka, Rust integration, and CI tasks
```

## Dependency Direction

```text
feature:* -> core:ui -> core:model
feature:* -> core:rust-bridge
app -> feature:*
core:media-adapter -> core:rust-bridge
core:automotive -> core:rust-bridge
core:rust-bridge -> AIDL service boundary
Rust engine -> Supabase, Jamendo, local DB
```

Feature modules should not call Supabase, Jamendo, SQLite, or native code
directly. They dispatch user/system events to the engine boundary and render
snapshots returned by Rust.

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
