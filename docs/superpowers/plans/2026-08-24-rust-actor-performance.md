# Rust Actor Performance And Benchmarking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the lock-based Rust engine with an observable actor, prepare release-like benchmarking, and implement the complete seven-item performance batch with reproducible before/actor/final evidence.

**Architecture:** One Rust actor owns mutable engine state and publishes immutable revisioned projections. A bounded asynchronous AIDL/JNI bridge submits commands without blocking Android main, effect workers perform remote/blocking work outside the actor, and Macrobenchmark plus Perfetto measure fixed user journeys.

**Tech Stack:** Rust, Tokio, JNI, AIDL, Kotlin coroutines, AndroidX Media3, Jetpack Compose, Macrobenchmark, Baseline Profiles, Perfetto, JUnit.

**Spec:** `docs/superpowers/specs/2026-08-24-rust-actor-performance-design.md`

## Global Constraints

- Work directly on `master` as requested.
- Preserve unrelated working-tree changes, including current Rust Android log tracing work.
- Capture the current-source benchmark checkpoint before changing runtime behavior.
- Use test-first changes for behavior, ordering, cache bounds, and concurrency contracts.
- Keep benchmark traces and generated reports under ignored build output directories.
- Never include credentials, tokens, capabilities, or private media metadata in trace labels.

---

### Task 1: Add release-like benchmark and Baseline Profile infrastructure

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `benchmark/build.gradle.kts`
- Create: `benchmark/src/main/AndroidManifest.xml`
- Create: `benchmark/src/main/kotlin/com/adrianrusu/pandawave/benchmark/StartupBenchmarks.kt`
- Create: `benchmark/src/main/kotlin/com/adrianrusu/pandawave/benchmark/JourneyBenchmarks.kt`
- Create: `benchmark/src/main/kotlin/com/adrianrusu/pandawave/benchmark/BaselineProfileGenerator.kt`

- [ ] Add stable Benchmark/Baseline Profile 1.4.1 and ProfileInstaller 1.4.1 catalog entries and plugins.
- [ ] Create a non-debuggable, minified `benchmark` app build type initialized from `release`, signed with the debug key, profileable, and using release fallbacks.
- [ ] Add a `com.android.test` benchmark module targeting `:app` with `AndroidBenchmarkRunner` and self-instrumenting enabled.
- [ ] Implement cold startup, Library/Profile scroll, and Now Playing interaction journeys with deterministic UI Automator selectors.
- [ ] Add Baseline Profile generation covering startup and the same critical journeys, including the `:engine` process.
- [ ] Build the benchmark APK and execute a dry benchmark run before any behavior change.

### Task 2: Capture the current-source checkpoint

**Files:**
- Create under ignored output: `app/build/perf-results/before/`

- [ ] Record device model, API, ABI, CPU count, emulator/hardware status, app version, variant, and compilation mode.
- [ ] Run repeated cold startup Macrobenchmarks with compilation disabled and with any existing profile.
- [ ] Run journey frame-timing benchmarks and save generated JSON and Perfetto traces.
- [ ] Capture matching `gfxinfo`, `meminfo`, process/thread lists, and cold-start `am start -W` samples.
- [ ] Validate that custom Android trace sections and both app processes are visible.

### Task 3: Specify actor contracts with failing Rust tests

**Files:**
- Create: `rust/engine/crates/app_core/src/engine/actor.rs`
- Modify: `rust/engine/crates/app_core/src/engine/mod.rs`
- Test: `rust/engine/crates/app_core/src/engine/actor.rs`
- Test: `rust/engine/crates/app_core/tests/`

- [ ] Add failing tests for FIFO command handling, bounded-mailbox overload, monotonic snapshot revisions, and explicit shutdown cancellation.
- [ ] Add a slow-effect test proving snapshots remain readable and unrelated commands progress while remote work is pending.
- [ ] Add stale-completion tests for identity/session replacement, search pagination, playlists, and playback resolution.
- [ ] Add outcome-order and actor-panic/channel-closure tests.

### Task 4: Implement the Rust actor and effect supervisor

**Files:**
- Create: `rust/engine/crates/app_core/src/engine/actor.rs`
- Create: `rust/engine/crates/app_core/src/engine/effect_supervisor.rs`
- Modify: `rust/engine/crates/app_core/src/engine/core.rs`
- Modify: `rust/engine/crates/app_core/src/engine/effects.rs`
- Remove after migration: `rust/engine/crates/app_core/src/engine/concurrent.rs`
- Modify: relevant networking and command modules under `rust/engine/crates/app_core/src/`

- [ ] Implement bounded actor messages for commands, platform events, ticks, completions, and shutdown.
- [ ] Give the actor exclusive `Engine` ownership and publish immutable `Arc<EngineSnapshot>` projections.
- [ ] Split remote commands into prepare/effect/commit phases with request IDs and operation generations.
- [ ] Route blocking work through bounded blocking execution and keep async networking on Tokio.
- [ ] Remove lock-across-`.await` engine access and pass all actor contract tests.

### Task 5: Move FFI to the actor runtime and add native Perfetto tracing

**Files:**
- Modify: `rust/engine/crates/ffi/Cargo.toml`
- Modify: `rust/engine/crates/ffi/src/engine_handle.rs`
- Modify: `rust/engine/crates/ffi/src/api/dispatch.rs`
- Modify: `rust/engine/crates/ffi/src/api/lifecycle.rs`
- Modify: `rust/engine/crates/ffi/src/api/query.rs`
- Modify: `rust/engine/crates/ffi/src/jni_bridge.rs`
- Create: `rust/engine/crates/ffi/src/perfetto_trace.rs`
- Test: `rust/engine/crates/ffi/src/tests/`

- [ ] Add failing FFI tests for immediate submission, request completion, snapshot availability during slow effects, and shutdown safety.
- [ ] Replace `new_current_thread` and per-call `runtime.block_on` with one multi-thread runtime owned by the handle.
- [ ] Make two and four worker counts selectable for benchmark runs, with a conservative measured default.
- [ ] Preserve the current Android log tracing changes and add `PW.` Android system-trace slices/counters for actor queue, effects, snapshot publication, and JNI work.
- [ ] Ensure async trace slices use stable request cookies and no secret/high-cardinality labels.

### Task 6: Batch revisioned JNI projections

**Files:**
- Modify: `rust/engine/crates/ffi/src/api/query.rs`
- Modify: `rust/engine/crates/ffi/src/jni_bridge.rs`
- Modify: `core/rust-bridge/src/main/kotlin/com/adrianrusu/pandawave/core/rust/bridge/engine/native/PandaEngine.kt`
- Test: `core/rust-bridge/src/test/kotlin/com/adrianrusu/pandawave/core/rust/bridge/engine/native/`

- [ ] Add tests counting native calls for a full snapshot and multi-effect outcome.
- [ ] Expose one revisioned detail batch and one ordered effect/outcome batch per request.
- [ ] Cache Kotlin detail projection by revision and remove per-field/per-effect native getter loops.
- [ ] Trace projection size and duration without logging media content.

### Task 7: Make AIDL submission and publication asynchronous

**Files:**
- Modify: `core/rust-bridge/src/main/aidl/com/adrianrusu/pandawave/core/rust/bridge/aidl/IMediaEngineService.aidl`
- Modify: `core/rust-bridge/src/main/aidl/com/adrianrusu/pandawave/core/rust/bridge/aidl/IEngineListener.aidl`
- Create/modify request outcome parcelables under `core/rust-bridge/src/main/`
- Modify: `core/rust-bridge/src/main/kotlin/com/adrianrusu/pandawave/core/rust/bridge/engine/MediaEngineService.kt`
- Modify: `core/rust-bridge/src/main/kotlin/com/adrianrusu/pandawave/core/rust/bridge/gateway/AidlEngineGateway.kt`
- Modify: `core/rust-bridge/src/main/kotlin/com/adrianrusu/pandawave/core/rust/bridge/gateway/AndroidEngineServiceConnection.kt`
- Modify: `core/playback/src/main/kotlin/com/adrianrusu/pandawave/core/playback/DefaultBambooPlaybackRepository.kt`
- Test: corresponding gateway, service, and playback tests

- [ ] Write failing tests proving caller threads return after enqueue, connection replay remains FIFO, and request completion/cancellation is correlated.
- [ ] Convert engine command/platform-event/auth submission to asynchronous request IDs and listener outcomes.
- [ ] Publish gateway state through flows and keep Android main limited to state application.
- [ ] Add mailbox-full, disconnected, timeout, and shutdown behavior with trace points.

### Task 8: Measure and tune the actor checkpoint

**Files:**
- Create under ignored output: `app/build/perf-results/actor-2-workers/`
- Create under ignored output: `app/build/perf-results/actor-4-workers/`

- [ ] Run identical Macrobenchmark and manual Perfetto journeys with two workers.
- [ ] Repeat with four workers under the same compilation/device conditions.
- [ ] Compare startup, frames, queue latency, in-flight effects, CPU scheduling, Binder latency, JNI calls, memory, and variance.
- [ ] Select and document the worker default from evidence.

### Task 9: Defer cold-start media and process work

**Files:**
- Modify: `app/src/main/kotlin/com/adrianrusu/pandawave/MainActivity.kt`
- Modify: `app/src/main/kotlin/com/adrianrusu/pandawave/PandaWaveApplication.kt`
- Modify: `core/media-adapter/src/main/kotlin/com/adrianrusu/pandawave/core/media/adapter/playback/BambooMediaLibraryService.kt`
- Modify: dependency injection modules and tests as required

- [ ] Add startup tests for main-process-only coordinators and lazy media initialization.
- [ ] Stop eagerly starting the media service before first composition.
- [ ] Initialize theme/app-only dependencies only in the main process and lazily where possible.
- [ ] Create ExoPlayer and MediaLibrarySession on first controller, browse, or playback demand while preserving AAOS behavior.
- [ ] Add `PW.Startup.*` trace slices around application, first composition, engine connection, and media initialization.

### Task 10: Batch and async Media3 catalog IPC

**Files:**
- Modify: `core/rust-bridge/src/main/aidl/com/adrianrusu/pandawave/core/rust/bridge/aidl/IMediaEngineService.aidl`
- Modify: `core/media-adapter/src/main/kotlin/com/adrianrusu/pandawave/core/media/adapter/playback/BambooMediaLibraryCatalog.kt`
- Modify: `core/media-adapter/src/main/kotlin/com/adrianrusu/pandawave/core/media/adapter/playback/BambooMediaLibrarySessionCallback.kt`
- Test: `core/media-adapter/src/test/kotlin/`

- [ ] Add tests proving catalog callbacks do no synchronous engine IPC and one page uses bounded batch calls.
- [ ] Add ranged/batched AIDL list methods and asynchronous Media3 futures on a dedicated executor.
- [ ] Derive search result counts from snapshot metadata instead of loading `Int.MAX_VALUE` results.
- [ ] Batch identity hydration commands through the actor where ordering permits.

### Task 11: Add rotary lazy lists and UI update controls

**Files:**
- Create: `core/ui/src/main/kotlin/com/adrianrusu/pandawave/core/ui/focus/BambooRotaryLazyColumn.kt`
- Modify: `feature/library/src/main/kotlin/com/adrianrusu/pandawave/feature/library/LibraryRoute.kt`
- Modify: `feature/profile/src/main/kotlin/com/adrianrusu/pandawave/feature/profile/ProfileRoute.kt`
- Modify: `feature/nowplaying/src/main/kotlin/com/adrianrusu/pandawave/feature/nowplaying/NowPlayingRoute.kt`
- Modify: audio indicator implementation under `core/` or `feature/nowplaying/`
- Test: relevant Compose/UI-model tests

- [ ] Add tests for stable lazy-list semantics, inactive indicators, and conflated/final volume updates.
- [ ] Implement rotary-aware `LazyColumn` with stable keys/content types and migrate Library/Profile.
- [ ] Keep slider drag state local, conflate intermediate engine updates, and always commit the final value.
- [ ] Avoid creating an infinite transition while voice activity is inactive.

### Task 12: Bound paging and result caches

**Files:**
- Modify: `core/media-adapter/src/main/kotlin/com/adrianrusu/pandawave/core/media/adapter/playback/BambooMediaLibraryCatalog.kt`
- Modify: `rust/engine/crates/app_core/src/networking/canopy/remote_repository.rs`
- Test: corresponding Kotlin and Rust tests

- [ ] Add overflow, hostile-page, eviction, playback-resolution, and identity-change tests.
- [ ] Clamp page indexes and required counts with overflow-safe arithmetic.
- [ ] Retain a bounded page window in the media service.
- [ ] Replace the unbounded Rust result map with an LRU/window retaining only recent and playback-required items.

### Task 13: Generate the Baseline Profile and capture final evidence

**Files:**
- Generate: `app/src/benchmark/generated/baselineProfiles/`
- Create under ignored output: `app/build/perf-results/final/`

- [ ] Generate and package the multi-process Baseline Profile.
- [ ] Verify the APK contains the compiled profile and ProfileInstaller support.
- [ ] Run final benchmarks with compilation none and baseline-profile-required modes.
- [ ] Capture matching Perfetto, `gfxinfo`, startup, memory, and thread evidence.
- [ ] Produce a before/actor/final comparison including median, p90/p95 where available, coefficient of variation, caveats, and selected worker count.

### Task 14: Integrated verification and graph refresh

**Files:**
- Update: `graphify-out/`

- [ ] Run focused and full Rust tests plus formatting/lints.
- [ ] Run affected Android JVM/instrumented tests and assemble the benchmark/release-like APK.
- [ ] Run emulator QA for startup, Home, Library, Profile, Now Playing, playback controls, reconnect, and shutdown.
- [ ] Run `graphify update .` and verify refreshed graph output.
- [ ] Review the final diff without staging or reverting unrelated user changes.

