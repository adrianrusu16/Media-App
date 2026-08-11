# Task 5 report — PandaWave playlists

## RED / GREEN evidence

- RED: `cargo test -p panda_engine_core playlist` failed with unresolved `PlaylistReconciler` and missing playlist test port symbols, as expected before the feature existed.
- GREEN: `cargo test -p panda_engine_core playlist` passed the ABORTED reorder reconciliation and retry-classification tests (2 matching tests passed).
- `cargo test -p panda_engine_ffi --lib` initially had 43 passing tests and one expected backend-composition reference-count failure (7 expected versus 8 after the playlist client retained the shared coordinator); the assertion was updated to match the new composition.
- GREEN: `cargo test -p panda_engine_ffi --lib` then passed all 44 tests.
- Library gate: `./gradlew.bat --no-configuration-cache :feature:library:testDebugUnitTest --console=plain` initially failed in the sandbox due to an inaccessible Gradle distribution lock. The approved rerun reached Android/Rust bridge tasks, then waited on an existing build-directory file lock; this is an external concurrent-build constraint, not a source failure.

## Files changed

- Added playlist domain, protected port, Canopy gRPC adapter, and ABORTED reconciler.
- Added strict playlist command wire parsing, engine dispatch/projection state, and production backend composition.
- Added Kotlin command constants/payload builders and Library repository/ViewModel playlist command flows.

## Design decisions

- Every protected playlist call receives the current account/session identity and is revalidated after it completes.
- Reorder is non-replayable. On `ABORTED`, it refetches once, records the server order plus local proposal, returns `Conflict`, and never sends a second reorder; a later explicit command is required.
- Pagination tokens remain in the engine and are never projected to Kotlin.
- Canopy's generated `ReorderPlaylistTracksRequest` names its ordered field `track_ids`; the domain calls these `membership_id` values and maps them without transformation because this SDK currently exposes no distinct membership identifier.

## Concerns

- The current JNI compact snapshot does not yet project playlist rows/conflict details, so the Kotlin flow dispatches playlist commands but cannot render the server-authoritative playlist projection until that bridge extension is completed.
- The requested Library test gate is currently contended by an existing Gradle build-directory lock.

## Bridge continuation

- Added append-only compact snapshot fields 45–49 for playlist/track counts, pagination flags, and reconciliation presence; pre-existing 0–44 values remain unchanged.
- Added credential-free JNI indexed playlist/track rows plus selection/reconciliation retrieval and native Kotlin mapping. No page token, auth credential, or generated backend type is projected.
- RED/GREEN: `cargo test -p panda_engine_ffi --lib jni_bridge::tests::snapshot_values_match_kotlin_compact_layout` initially failed because the append-only expected values were inserted at the wrong indices; after correcting the test to assert the preserved 0–44 prefix and the 45–49 tail, it passed.
- Remaining: Compose playlist CRUD/membership/reconciliation screens are intentionally out of this continuation's scope.

## Bridge completion continuation

- RED: `:core:rust-bridge:testDebugUnitTest --tests com.adrianrusu.pandawave.core.rust.bridge.gateway.AidlEngineGatewayTest` failed with the expected `EnginePlaylistItem(... ) but was: <null>` assertion after the AIDL gateway playlist forwarding seam was temporarily removed.
- GREEN: after restoring forwarding, `:core:rust-bridge:testDebugUnitTest :feature:library:testDebugUnitTest :core:rust-bridge:compileDebugAndroidTestKotlin` exited `0` using `E:\Android\gradle-home`; it covered all 67 rust-bridge unit tests, all library unit tests, and Android-test compilation.
- Added append-only `IMediaEngineService` indexed playlist/track getters plus selected-playlist and reconciliation getters. `MediaEngineService`, `AndroidEngineServiceConnection`, `EngineService`, `AidlEngineGateway`, and `InProcessEngineGateway` now forward the same credential-free values as native `PandaEngine`.
- Added Parcelable AIDL declarations for playlist rows, membership tracks, and reconciliation state. Reconciliation transports expected/server revisions and indexed server/proposed membership IDs only; no token, credential, generated backend type, or page token crosses the boundary.
- `PandaEngineLibraryRepository` now hydrates playlists once per authenticated identity alongside the existing saved/liked guard, projects playlist rows/tracks, selected ID, both next-page flags, and complete reconciliation revisions. The focused repository test asserts all of those values.
- The full gate initially exposed a stale 45-value Kotlin native-snapshot fixture; its preserved prefix now appends and asserts fields 45-49, matching the existing Rust compact snapshot ABI without altering earlier indices.

## Compose completion continuation

- RED: `:feature:library:testDebugUnitTest --tests com.adrianrusu.pandawave.feature.library.presentation.LibraryViewModelTest` failed with unresolved `updatePlaylist` in `LibraryViewModel` and its repository contract. `:feature:library:compileDebugAndroidTestKotlin` then failed because `LibraryRoute` did not expose playlist callbacks.
- GREEN: the focused ViewModel test passed after adding update wiring and verifies create/update/delete/select, membership add/remove, and the complete ordered membership-id list plus expected revision. `:feature:library:testDebugUnitTest :feature:library:compileDebugAndroidTestKotlin` passed after adding the PLAYLISTS Compose controls and conflict confirmation test.
- The PLAYLISTS tab exposes create/update/delete/select, membership add/remove, and a vertical drag interaction. Drag completion dispatches the complete current membership-id order with the selected playlist revision. Conflict UI renders the refreshed server order and proposed local order; only the explicit confirmation sends a new reorder command using the server revision.
- Final gates: `cargo test -p panda_engine_core playlist` passed (2 playlist tests); `cargo test -p panda_engine_ffi --lib` passed (44 tests); `:core:rust-bridge:testDebugUnitTest`, `:feature:appshell:testDebugUnitTest`, `:feature:appshell:compileDebugKotlin`, `:feature:library:lintDebug`, and `:app:assembleDebug` completed without reported failures; `cargo fmt --all -- --check` and `git diff --check e23ccea` passed; `graphify update .` completed.

## Pre-review verification fix

- Replaced the two redundant `map` closures in `engine/core/playlist.rs` with the `PlaylistMutation::Playlist` function item required by Clippy.
- `cargo fmt --all -- --check` output: *(no output; exit code 0)*.
- `cargo clippy --workspace --all-targets --all-features -j 1 -- -D warnings` output:

```text
    Checking panda_engine_core v0.1.0 (E:\AndroidStudioProjects\media_app\rust\engine\crates\app_core)
    Checking panda_engine_ffi v0.1.0 (E:\AndroidStudioProjects\media_app\rust\engine\crates\ffi)
    Finished `dev` profile [unoptimized + debuginfo] target(s) in 19.71s
```

## Fix round 1

- RED: `cargo test -p panda_engine_core create_playlist_rejects_an_expected_revision -j 1` failed because create accepted `expected_revision: 7`; create now requires the field to be null/absent.
- RED: `cargo test -p panda_engine_ffi playlist_ffi_discriminants_parse_their_wire_payloads -j 1` failed to compile because playlist constants and the production parser path were absent. Rust and Kotlin now append all ten playlist command discriminants at 40-49, preserve 0-39, and parse through `EngineCommand::from_wire`.
- GREEN: `cargo test -p panda_engine_ffi production_dispatch_recognizes_playlist_command_discriminants -j 1` passed a real `panda_engine_dispatch` regression for all ten playlist commands; the Kotlin bridge mapping test covers the same 40-49 sequence.
- RED: `bound_request_revalidates_identity_after_successful_rpc` returned success after account/session replacement, current=false, and logout. `bound_request_revalidates_identity_after_failed_rpc` preserved a transport error after logout. The shared bound-auth request helper now re-reads and checks the exact account/session after every awaited RPC, including failed responses and safe retries, while `bound_request_preserves_typed_failure_for_same_identity` keeps ordinary typed failures unchanged.
- RED: focused engine tests showed partial paginated reorder reached the port, successful reorder left the projected membership order stale, and a first-operation conflict projection survived an identity switch. The engine now rejects reorder while a selected membership page is incomplete, applies the acknowledged complete order and positions, and binds both success and conflict projections to the revalidated identity. Compose omits the drag handler while `hasPlaylistTracksNextPage` is true.
- Native playlist rows now use guarded unsigned parsing and reject u64/u32 values outside Kotlin `Long`/`Int` ranges without throwing. The append-only seventh playlist-row value explicitly records description presence, preserving present-empty distinct from absent; focused mapper tests cover both boundaries.
- GREEN: `cargo fmt --all -- --check`, `cargo test --workspace -j 1` (205 core unit tests plus integrations; 46 FFI tests), and `cargo clippy --workspace --all-targets --all-features -j 1 -- -D warnings` passed.
- GREEN: `:core:rust-bridge:testDebugUnitTest :feature:library:testDebugUnitTest :feature:library:compileDebugAndroidTestKotlin :feature:library:lintDebug :feature:appshell:testDebugUnitTest :feature:appshell:compileDebugKotlin :app:assembleDebug` completed `BUILD SUCCESSFUL in 4m 7s` (644 tasks). The only diagnostic was the pre-existing Compose test-rule deprecation warning.
