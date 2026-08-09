# Task 4 Report: Saved Tracks and Likes

## Outcome

Implemented the authenticated Library vertical slice from the Canopy v1 API through the Rust engine, C/JNI/AIDL bridge, Kotlin repository/ViewModel, and Compose UI. Saved and liked tracks are now user-reachable, paginated, and projected from authoritative engine snapshots. Mutations publish pending state while awaiting acknowledgement, roll back on failure, and fail closed across a local service disconnect.

## Canonical schema and relationship key

The pinned `pandawave_canopy-api_community_neoeinstein-prost` v1 schema exposes `SavedTrack` and `LikedTrack` as a `TrackSummary` plus relationship timestamp; it does not expose a separate relationship identifier. The evidence is in the generated dependency source at:

`C:\Users\Cătălina\.cargo\registry\src\buf.build-fba48764fc35cfd8\pandawave_canopy-api_community_neoeinstein-prost-0.5.0-00000000000000-145678c1d73e.2\src\canopy.v1.rs` (`SavedTrack` near line 244 and `LikedTrack` near line 273).

The canonical track ID is therefore the stable relationship key. The implementation does not invent an unavailable wire ID. Contract tests document this choice and assert deduplication by that canonical ID rather than using the plan's illustrative `saved-1` value.

## Implementation notes

- Added exact `account_id` plus current `session_id` identity binding to every protected Library request.
- Reused the shared authenticated Canopy executor and session coordinator. The coordinator rejects a non-current identity before an initial RPC or retry, and the engine revalidates the identity after every await before publishing results.
- Added save, remove-saved, like, unlike, list-saved, and list-liked ports and Canopy adapters. Mutations use the established idempotent server-operation classification; reads use the read classification.
- Added engine-owned opaque pagination and list deduplication by canonical relationship key. Callers cannot supply continuation tokens.
- Added pending saved/liked track IDs to intermediate snapshots. Acknowledged mutations reconcile with authoritative projections; errors restore the prior projections and publish a sanitized typed error.
- Clear or mask Library projections when account/session ownership changes or authentication is removed.
- Appended command ABI values 32 through 39 and snapshot compact-array fields 40 through 44. Existing ABI indices remain unchanged.
- Added atomic credential-free `EngineLibraryItem` transport and appended AIDL indexed accessors for saved, liked, and pending items.
- Kept protected mutations non-replayable across a local binder disconnect. Library reads retain the existing safe reconnect/replay behavior.
- Added a Hilt-backed Library repository and ViewModel plus Saved/Liked tabs, loading, signed-out, empty, retry, pending, pagination, remove/save, and like/unlike UI behavior.

## TDD evidence

The work followed explicit RED to GREEN boundaries:

- Model RED: unresolved Library model exports. GREEN: exact identity/model contract passed.
- Adapter RED: missing mapping and authenticated retry behavior. GREEN: three adapter contracts passed.
- Engine RED: missing Library commands, setter, and snapshot projections. GREEN: four engine contracts passed, covering pagination/deduplication, acknowledgement/rollback, in-flight identity replacement rejection, and logout masking.
- Strict-wire RED: an empty object was accepted for a no-payload next-page command. GREEN: all no-payload commands reject supplied JSON.
- FFI RED: missing appended constants and fields. GREEN: three Library boundary tests passed.
- Kotlin bridge RED: appended snapshot fields were not mapped. GREEN: rust-bridge unit tests passed.
- Feature RED: Library state/repository/ViewModel APIs were absent. GREEN: repository and ViewModel unit tests passed.
- Compose RED: route behavior and actions were absent. GREEN: Android-test compile and the connected device test passed.

The first connected UI run also exposed a test-fixture theme problem and then off-screen action targeting. The fixture was switched to `PandaWaveTheme`, and scrolling was made explicit. No product defect was found in either case.

## Verification

All completion gates passed on the final implementation:

- `cargo fmt --all -- --check`
- `cargo test --workspace -j 1` (including 197 core unit tests, all integration suites, and 44 FFI tests)
- `cargo clippy --workspace --all-targets --all-features -j 1 -- -D warnings`
- `gradlew.bat --no-configuration-cache :core:rust-bridge:testDebugUnitTest :feature:library:testDebugUnitTest :feature:library:compileDebugAndroidTestKotlin :feature:library:lintDebug :feature:appshell:testDebugUnitTest :feature:appshell:compileDebugKotlin :app:assembleDebug --no-daemon --console=plain` (644 tasks, successful)
- `gradlew.bat --no-configuration-cache :feature:library:connectedDebugAndroidTest --no-daemon --console=plain` on PandaEmulatorNoStore API 35 (1/1 passed)
- `git diff --check`
- `graphify update .` (5,380 nodes, 11,140 edges, 330 communities)

The repository-wide Spotless check has a known baseline of 73 unrelated pre-existing CRLF violations. No broad formatting apply was run. Files touched by this task were verified by the focused compile, test, lint, Rust formatting, and Git whitespace gates above.

## Tooling, cleanup, and scope preservation

The required `apply_patch` operation was attempted before edits. On existing files the Windows helper repeatedly failed with `helper_unknown_error`, so edits used narrow, exact-path, occurrence-counted PowerShell fallbacks. One Gradle fallback stopped safely because its CRLF anchor count was zero; a normalized exact-anchor retry succeeded. One pending-bridge fallback applied the C query then stopped at a zero-count JNI anchor; a normalized exact-anchor retry completed it. The first encoding helper failed to parse its mojibake literal; an ASCII resource-name match corrected the string to `Updating...`. All temporary Task 4 helper scripts were removed after use.

The pre-existing untracked `.codex`, `.serena`, `AGENTS.md`, graphify outputs, IDE/target directories, crash log, and archive remain untouched and unstaged. Graphify outputs were updated as required but remain unstaged because they were untracked before this task.
