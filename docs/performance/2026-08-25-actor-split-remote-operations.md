# PandaWave Actor: Splitting Remote Operations Off The Actor

Date: 2026-08-25

This report completes the measurement step left open by `2026-08-25-actor-perf-metrics.md`. That report could not produce an after-actor AndroidX Macrobenchmark result because the runner failed during setup, so it fell back to direct `am start -W` and `gfxinfo`. AndroidX Macrobenchmark now runs successfully, so every number below comes from the same harness as the before checkpoint and is directly comparable.

It also records a second, sharper comparison. The actor was measured twice on the same commit, differing only in whether remote I/O runs on the actor or on workers, which isolates the effect of this change from everything else in the actor work.

## Checkpoints

| Checkpoint | Meaning | `split_remote_operations` | Operations with workers |
| --- | --- | --- | ---: |
| `before` | Pre-actor engine | n/a | n/a |
| `actor` | Actor owns state; remote I/O still runs on the actor | `false` | `1` of `5` |
| `actor-split` | Actor owns state; remote I/O runs on workers | `true` | `5` of `5` |

Both actor checkpoints are the same `feature: add media warmup` commit on top of `ac81bf46b`. The `actor-split` checkpoint adds the engine change described under [Engine Change](#engine-change) and nothing else. That change has since been folded into the same commit, which is now `ea64618a0`; the `actor` checkpoint was measured before it was folded in. The `before` checkpoint is the primary (non host-GPU) before checkpoint from the earlier report.

## Summary

| Metric | `before` | `actor` | `actor-split` | Split vs actor |
| --- | ---: | ---: | ---: | ---: |
| Cold startup `timeToInitialDisplayMs` median | `5762.0 ms` | `1133.9 ms` | `596.0 ms` | `-47.4%` |
| Journey `frameDurationCpuMs` P50 | `44.6 ms` | `23.5 ms` | `11.0 ms` | `-53.2%` |
| Journey `frameOverrunMs` P50 | `38.0 ms` | `9.3 ms` | `-2.4 ms` | `-11.7 ms` |
| Journey `frameOverrunMs` P99 | `103.1 ms` | `30.8 ms` | `11.3 ms` | `-63.3%` |

The journey overrun result is the most direct evidence that the split did what it was intended to do. `frameOverrunMs` measures how far past its vsync deadline each frame finished, so a negative P50 means the median frame now completes *before* its deadline rather than after it. That is the expected signature of the UI thread no longer waiting behind engine work.

Two startup tail metrics did not improve. See [Caveats](#caveats).

## Environment

Identical across all three checkpoints, which is what makes the comparison valid.

| Field | Value |
| --- | --- |
| Package | `com.adrianrusu.pandawave` |
| Device serial | `emulator-5554` |
| AVD | `PandaBenchmarkApi35` |
| Device | `google emu64xa`, model `sdk_gphone64_x86_64` |
| Android | API `35` |
| CPU cores | `4` |
| CPU locked | `false` |
| Memory | `2,592,718,848 bytes` |
| Compilation mode | `CompilationMode.None`, reported as `run-from-apk` |
| Warmup iterations | `0` |
| Measured iterations | `5` |

## Cold Startup

Benchmark:

- Class: `com.adrianrusu.pandawave.benchmark.StartupBenchmarks`
- Test: `coldStartupCompilationNone`
- Startup mode: cold
- Total benchmark runtime, `actor-split`: `28.9543 s`

`timeToInitialDisplayMs`:

| Checkpoint | Min | Median | Max | CoV |
| --- | ---: | ---: | ---: | ---: |
| `before` | `1838.6698` | `5762.0211` | `7036.3755` | `0.3907` |
| `actor` | `1050.4976` | `1133.8815` | `1471.7804` | `0.1400` |
| `actor-split` | `556.2656` | `595.9902` | `1685.3565` | `0.5835` |

`actor-split` runs, in iteration order: `1685.3565`, `734.8605`, `556.2656`, `573.3210`, `595.9902`.

Sampled frame metrics:

| Checkpoint | Metric | P50 | P90 | P95 | P99 |
| --- | --- | ---: | ---: | ---: | ---: |
| `before` | `frameDurationCpuMs` | `244.2124` | `2400.2472` | `2888.2105` | `3033.3709` |
| `actor` | `frameDurationCpuMs` | `34.4604` | `508.9809` | `584.7431` | `618.6468` |
| `actor-split` | `frameDurationCpuMs` | `18.0068` | `335.0627` | `345.8852` | `640.4683` |
| `before` | `frameOverrunMs` | `329.7350` | `2721.5109` | `3268.8845` | `3504.5959` |
| `actor` | `frameOverrunMs` | `43.6213` | `501.5941` | `583.9280` | `621.9580` |
| `actor-split` | `frameOverrunMs` | `22.4404` | `321.2498` | `333.2839` | `625.5737` |

`frameCount`:

| Checkpoint | Min | Median | Max |
| --- | ---: | ---: | ---: |
| `before` | `5` | `5` | `7` |
| `actor` | `3` | `5` | `6` |
| `actor-split` | `4` | `5` | `6` |

## Library/Profile Journey

Benchmark:

- Class: `com.adrianrusu.pandawave.benchmark.JourneyBenchmarks`
- Test: `libraryAndProfileCompilationNone`
- Startup mode: warm
- Flow: open Library, scroll content 3 times, open Profile, scroll content 3 times
- Total benchmark runtime, `actor-split`: `50.3386 s`

Sampled frame metrics:

| Checkpoint | Metric | P50 | P90 | P95 | P99 |
| --- | --- | ---: | ---: | ---: | ---: |
| `before` | `frameDurationCpuMs` | `44.5623` | `77.1695` | `84.4229` | `101.7980` |
| `actor` | `frameDurationCpuMs` | `23.4778` | `36.2187` | `39.2496` | `43.9796` |
| `actor-split` | `frameDurationCpuMs` | `11.0358` | `18.8343` | `21.2588` | `23.8145` |
| `before` | `frameOverrunMs` | `38.0071` | `79.6287` | `89.4742` | `103.0970` |
| `actor` | `frameOverrunMs` | `9.3123` | `22.2276` | `25.6770` | `30.7668` |
| `actor-split` | `frameOverrunMs` | `-2.4020` | `6.3330` | `9.2110` | `11.3061` |

`frameCount`:

| Checkpoint | Min | Median | Max | CoV |
| --- | ---: | ---: | ---: | ---: |
| `before` | `7` | `7` | `7` | `0.0` |
| `actor` | `6` | `6` | `7` | `0.0745` |
| `actor-split` | `7` | `7` | `7` | `0.0` |

`frameCount` counts frames produced for the same scripted interaction, so it is not a quality metric here. The return to `7` alongside a halving of per-frame cost is not a regression.

## Engine Change

Before this change, `spawn_operation_worker` only handled `HistorySettings`. The other four `EngineOperationRequest` variants returned without launching anything, which is why production kept `split_remote_operations` disabled.

Filling in the missing variants was not sufficient on its own. Two defects had to be fixed first.

**A launched operation could strand its command.** The caller inserted the operation into `pending_operations` *before* calling `spawn_operation_worker`. When the worker returned silently, no completion was ever sent, so the command's oneshot never resolved. `spawn_operation_worker` now returns `bool`, and the caller registers the operation only when a worker actually started; otherwise the command falls through to inline dispatch.

**The split path lost effects and events.** It applied results straight onto the snapshot and emitted no `EngineEvent` and no `EngineEffect`, so a resolved track would never have produced `PreparePlaybackSource` or reached `PlaybackState::Buffering`. Workers now perform only the remote call. The actor stores the result as a prefetch and re-dispatches the original command, so the normal inline path produces the snapshot mutation, event, effects, queue changes and identity-staleness checks exactly as it always did. The split and inline paths therefore share one implementation rather than two that can drift.

Operations and their guards:

| Operation | Remote call performed by the worker | Guard before splitting |
| --- | --- | --- |
| `AccountProjection` | `AccountPort::get_account` | Authenticated identity and a configured account port |
| `SearchPage` | `MediaRepository::search_catalog` | Always splittable; a repository is always present |
| `PlaylistPage` | `PlaylistPort::list` | Authenticated identity and a configured playlist port |
| `HistorySettings` | `HistoryPort::get_settings` | Authenticated identity and a configured history port |
| `PlaybackResolution` | `PlaybackPort::resolve_playback` | A configured playback port, so inline fallbacks stay reachable |

Supporting changes:

- `Engine.repository` became `Arc<dyn MediaRepository>` so a worker can hold it. `set_repository` still accepts a `Box` and converts, so no call site changed.
- Catalog continuations read the query and page size from the engine's own `catalog_operations` map. The previous split path used an empty query and a hardcoded page size of `20`.
- Worker errors propagate into `last_error` instead of being dropped.

## Verification

Rust workspace:

```powershell
cargo test --offline --workspace
```

Result: `242` engine unit tests pass and all `22` actor contract tests pass.

Six actor contract tests needed fixture changes rather than assertion changes. They previously submitted splittable commands against an engine with no ports configured, which the new guards correctly refuse to split, and they injected completions that would now race a real worker. They now use ports whose futures never resolve, so the injected completion remains the only one that lands.

One assertion changed meaning. `search_pagination_applies_current_pages_and_rejects_a_stale_continuation` hardcoded the catalog operation id it had injected through the completion payload. The engine now allocates that id itself on both the inline and split paths, so the test reads it from the outcome event instead.

One unrelated test fails on this tree: `canopy_sdk_compatibility::verifier_defaults_to_the_repository_containing_the_script`. It is caused by a pre-existing uncommitted Canopy SDK version bump in `scripts/verify-canopy-sdk.ps1` and is unrelated to the engine.

### Build verification

The Rust library must be confirmed rebuilt before trusting any benchmark result. A `CARGO_TARGET_DIR` set in the shell is inherited by the Gradle daemon, which sends cargo output to a different directory while `syncPandaEngineAndroidJniLibs` still reads `rust/engine/target/<triple>/release/`. This produces `BUILD SUCCESSFUL` with either a stale engine library or, once the sync task finds no source, no engine library at all.

Check the timestamps and sizes before benchmarking:

```powershell
Get-ChildItem 'rust\engine\target\*\release\libpanda_engine_ffi.so' |
    ForEach-Object { "{0}  {1,9}  {2}" -f $_.LastWriteTime, $_.Length, $_.Directory.Parent.Name }
```

If they are stale, stop the daemon so it restarts with a clean environment, then force the tasks:

```powershell
./gradlew.bat --stop
./gradlew.bat ':core:rust-bridge:buildPandaEngineAndroidArm64V8a' `
    ':core:rust-bridge:buildPandaEngineAndroidArmeabiV7a' `
    ':core:rust-bridge:buildPandaEngineAndroidX86' `
    ':core:rust-bridge:buildPandaEngineAndroidX86_64' --rerun-tasks
```

The `actor-split` numbers in this report come from a build where all four libraries were confirmed regenerated and changed size.

## Caveats

1. **Two startup tail metrics did not improve.** `frameDurationCpuMs` P99 rose from `618.6468` to `640.4683` and `frameOverrunMs` P99 from `621.9580` to `625.5737`. `timeToInitialDisplayMs` max also rose, `1471.7804` to `1685.3565`. All three are driven by iteration `0`, the usual cold-cache outlier: the remaining four runs span `556.2656` to `734.8605`. The accurate statement is that the tail is unchanged, not that it improved.
2. **Five iterations is a small sample.** It is enough to establish a 2x median shift and not enough to characterise a P99. Re-run with more iterations before making a release decision that depends on the tail.
3. **Emulator results are directional only.** Macrobenchmark printed its emulator warning on every run and CPU clocks were not pinned (`cpuLocked = false`). The device contract in `benchmark/README.md` already requires confirming release decisions on representative hardware.
4. **The before/actor delta is not attributable to the actor alone.** The `before` to `actor` step spans several changes, including the MediaSession warmup. Only the `actor` to `actor-split` step is a controlled comparison.

## Artifacts

| Checkpoint | Path |
| --- | --- |
| `before` | `app/build/perf-results/before/{startup,journey}/` |
| `actor` | `app/build/perf-results/actor/{startup,journey}/` |
| `actor-split` | `app/build/perf-results/actor-split/{startup,journey}/` |

Each directory holds the AndroidX `benchmarkData.json` and five `.perfetto-trace` files. Use `perfetto/startup-hotspots.sql` and `perfetto/startup-cpu.sql` against the startup traces for root-cause work; the medians in this report are not enough to explain the remaining tail.

## Next Measurement Step

1. Re-run both benchmarks with more iterations to characterise the startup tail, which is the only area that did not improve.
2. Capture the `final-baseline-profile` checkpoint described in `benchmark/README.md`, since every checkpoint so far uses `CompilationMode.None`.
3. Capture an AAOS checkpoint through manual Perfetto, as AndroidX Macrobenchmark does not reliably support headless-system-user images.
