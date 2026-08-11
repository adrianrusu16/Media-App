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
