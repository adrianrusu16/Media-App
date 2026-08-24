# Rust Actor Performance And Benchmarking Design

## Problem

PandaWave currently exposes a synchronous Kotlin-to-AIDL-to-JNI command path. The native FFI blocks a single-thread Tokio runtime while `ConcurrentEngine` holds its only `tokio::sync::Mutex<Engine>` across asynchronous dispatch. Slow remote work therefore blocks command submission, snapshot reads, Binder callers, and sometimes the Android main thread. Snapshot and effect projection adds repeated JNI and Binder calls, while cold startup, eager Compose lists, unbounded paging caches, and high-frequency UI updates add independent latency and memory costs.

Nothing is in production, so compatibility with the synchronous engine protocol is not a release constraint. The project can adopt a clearer actor protocol now and validate it before that protocol becomes public behavior.

## Approved Direction

Replace `ConcurrentEngine` with a Rust actor that exclusively owns mutable engine state. Kotlin and Binder submit commands asynchronously to a bounded mailbox. The actor performs deterministic state transitions, publishes immutable revisioned snapshots, launches remote or blocking effects outside the state owner, and applies effect completions only when their operation generation is still current.

The actor migration is one part of a complete performance batch. PandaWave will also receive release-like Macrobenchmark infrastructure, generated Baseline Profiles, asynchronous and batched Media3 catalog IPC, lazy rotary lists, bounded caches, deferred media startup, throttled volume updates, inactive-animation suppression, and custom Perfetto trace points.

## Actor Model

The engine runtime has four responsibilities:

1. A bounded multi-producer/single-consumer mailbox accepts commands, platform events, ticks, effect completions, and shutdown.
2. One actor task owns `Engine` and is the only code allowed to mutate it.
3. Effect workers perform asynchronous network work or explicitly isolated blocking/CPU work without retaining mutable engine state.
4. Revisioned publication channels expose the latest immutable snapshot and ordered outcomes/effects without reading actor-owned state through a lock.

The actor uses request IDs for caller correlation and operation generations for stale-result rejection. Queue capacity is finite so overload is visible and backpressure is defined. High-frequency replaceable commands, such as intermediate volume changes, may be conflated before entering the mailbox; state-changing commands such as play, pause, authentication, and playlist mutation preserve FIFO ordering.

## Threading

The FFI runtime moves from Tokio's current-thread runtime to a multi-thread runtime. The first benchmark comparison will test two and four Tokio worker threads. The selected default must be based on trace evidence and remain configurable for benchmark experiments.

The actor itself remains logically single-threaded. Network futures run concurrently on Tokio workers. Filesystem, crypto, or CPU work that actually blocks is routed through `spawn_blocking` or a bounded dedicated worker. Android main remains responsible only for UI state application. Binder threads enqueue work and return; they do not wait for network completion.

Adding multiple mailbox consumers is explicitly out of scope because it would sacrifice deterministic state ordering. Audio real-time work also remains outside the general Tokio pool.

## Asynchronous Bridge

The AIDL service accepts a request and returns an immediate acceptance result containing a request ID, or uses a one-way submission where no immediate validation is required. Existing one-way engine listeners carry revisioned snapshots and outcomes back to the app process. Kotlin maintains request-ID-to-completion mappings only for operations whose callers need a terminal result; ordinary playback and navigation commands consume the published `StateFlow` instead.

Queued commands across service connection preserve order. Disconnect, shutdown, mailbox-full, timeout, and cancellation outcomes are explicit. Credentials and verification tokens continue to be zeroed or released according to the existing security contracts.

## Snapshot And Effect Projection

The actor publishes an immutable `Arc` projection whenever the state revision changes. Synchronous FFI snapshot access reads that published projection rather than locking the engine. JNI retrieves one batched detail projection per revision and one batched outcome/effect projection per completed request. Kotlin caches detail projections by revision and does not repeat native calls for unchanged data.

The AIDL service gains ranged/batched catalog and library result methods. Media3 catalog callbacks run on a dedicated executor and return genuinely asynchronous futures. Search notification counts come from snapshot metadata rather than loading every result solely to calculate a count.

## Startup

The app process renders before starting optional playback infrastructure. Theme preference coordination runs only in the main app process and is injected lazily where possible. `BambooMediaLibraryService` defers ExoPlayer and `MediaLibrarySession` creation until the first controller, browse, or playback demand while preserving Android Automotive OS service behavior.

## UI And Cache Work

Library and Profile use rotary-aware `LazyColumn` implementations with stable keys and content types. History and remote-result paging use overflow-safe page arithmetic and bounded windows/LRU storage. Volume dragging keeps local visual state, sends conflated updates at a controlled rate, and commits the final value. Audio indicators create no infinite transition while inactive.

## Benchmarking

Add a `:benchmark` `com.android.test` module targeting a non-debuggable, minified, release-like `benchmark` app variant signed with the debug key. Use stable AndroidX Benchmark 1.4.1 and ProfileInstaller 1.4.1. The module owns Macrobenchmark journeys and Baseline Profile generation for the multi-process app.

Three comparable checkpoints are required:

1. Current source before runtime behavior changes.
2. Rust actor and asynchronous bridge complete.
3. Actor plus all remaining optimizations and generated Baseline Profile.

Each checkpoint runs cold startup, Home-to-Library/Profile scrolling, and Now Playing interaction. Measurements include startup timing, frame timing, ART behavior, memory snapshots, `gfxinfo`, Binder latency, actor queue latency, JNI projection calls, and Perfetto traces. Baseline Profile enabled and disabled compilation modes are compared separately.

Emulator results are useful for regression comparison but do not determine final thread counts for production hardware. The report records device model, API level, ABI, build variant, worker count, compilation mode, iterations, and variance.

## Tracing

Existing Rust `tracing` output to Android logcat is preserved. Performance tracing adds Android system-trace events visible in Perfetto. Kotlin uses platform/Jetpack trace sections at command submission, Binder callbacks, catalog work, and startup boundaries. Native tracing records actor enqueue, reduce, effect launch, effect completion, snapshot publication, and JNI batching.

Stable trace names use the `PW.` prefix. Required counters include actor queue depth, in-flight effects, and snapshot revision. Asynchronous work uses request IDs or trace cookies so a slice can begin and end on different runtime workers safely. Trace formatting is guarded so disabled tracing does not allocate high-cardinality labels.

## Correctness And Failure Handling

- Commands accepted before shutdown either complete or receive an explicit cancellation outcome.
- Actor panics or channel closure fail pending requests and leave a diagnostic trace/log event.
- Stale effect completions cannot overwrite newer identity, session, search, playlist, or playback state.
- Snapshot revisions are monotonic.
- Effects retain order per accepted command.
- Mailbox overload never blocks Android main; replaceable updates may conflate and nonreplaceable commands fail fast with telemetry.
- Benchmark and tracing code must not expose credentials, tokens, source capabilities, or private media metadata.

## Non-Goals

- Running multiple mutable engine actors against the same state.
- Parallel mutation of `Engine`.
- Replacing Media3 or Compose.
- Treating emulator thread-count results as representative hardware certification.
- Preserving the old synchronous bridge as a permanent public API.

## Success Criteria

- No Android main-thread path performs synchronous Binder or JNI engine dispatch.
- No engine mutex is held across an `.await`; the actor is the sole mutable-state owner.
- Snapshot reads remain available while remote effects are in flight.
- Per-item JNI/AIDL projection loops are replaced by bounded batches.
- Cold startup and journey frame metrics improve without behavioral regressions.
- Actor queue, effects, Binder, JNI, and startup phases are identifiable in Perfetto.
- All focused Rust/Kotlin tests, release-like builds, benchmark journeys, and emulator QA pass.

