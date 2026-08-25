# PandaWave Actor Performance Metrics

Date: 2026-08-25

This report captures the available before/after performance evidence for the Rust actor and related performance work. It intentionally separates fully comparable AndroidX Macrobenchmark data from direct Perfetto/ADB fallback measurements.

## Summary

| Area | Before actor | After actor | Comparability |
| --- | ---: | ---: | --- |
| Cold startup, primary emulator | AndroidX Macrobenchmark `timeToInitialDisplayMs` median `5762.0 ms` across 5 runs | Direct `adb am start -W` `TotalTime` median `1322 ms` across 5 runs | Directional only. The after Macrobenchmark runner hit an AndroidX setup failure before app launch. |
| Cold startup traces | 5 AndroidX-generated Perfetto traces | 1 direct whole-app Perfetto startup trace | Both are Perfetto traces, but capture harness differs. |
| Library/Profile journey traces | 5 AndroidX-generated Perfetto traces | 1 direct whole-app Perfetto journey trace | Both trace the app journey, but harness differs. |
| Journey frame timing | AndroidX `FrameTimingMetric`: CPU frame duration P50 `44.6 ms`, P95 `84.4 ms`; overrun P50 `38.0 ms`, P95 `89.5 ms` | `gfxinfo` reset journey: 342 frames, 58 janky frames, p50 `19 ms`, p95 `44 ms`, p99 `101 ms` | Not the same metric. Use the raw Perfetto traces for root-cause comparison. |
| App trace coverage | Whole-app/system Perfetto traces existed; custom `PW.*` markers were mostly native/engine-side | Added app-wide `PW.Startup.*`, `PW.Engine.*`, and `PW.Media3.*` sections before after traces | Trace processor was not available locally to query slices, so marker verification should be done in Perfetto UI or `trace_processor_shell`. |

The rough startup median delta from before Macrobenchmark TTI to after direct `am start -W` TotalTime is `-4440.0 ms` (`-77.1%`). Treat this as a directional signal, not a formal benchmark delta, because the instrumentation path changed.

## Environment

| Field | Value |
| --- | --- |
| Package | `com.adrianrusu.pandawave` |
| Device serial | `emulator-5554` |
| Device | `google emu64xa`, model `sdk_gphone64_x86_64` |
| Android | API `35`, Android 15 build `AE3A.240806.046.B2` |
| Build type | `userdebug` emulator |
| CPU cores | `4` |
| CPU max frequency | `2000 MHz` |
| CPU locked | `false` |
| Memory | about `2.59 GB` |
| Screen size during after direct run | `1408x792` |
| Build variant | benchmark APKs |
| Source checkpoint | `037641230` plus uncommitted app-wide tracing changes |

APK artifacts used for the after run:

| Artifact | Size |
| --- | ---: |
| `app/build/outputs/apk/benchmark/app-benchmark.apk` | `44,598,649 bytes` |
| `benchmark/build/outputs/apk/benchmarkBenchmark/benchmark-benchmarkBenchmark.apk` | `50,795,905 bytes` |

## Before Actor: Primary Macrobenchmark

Artifacts:

- `app/build/perf-results/before/startup/com.adrianrusu.pandawave.benchmark-benchmarkData.json`
- `app/build/perf-results/before/startup/StartupBenchmarks_coldStartupCompilationNone_iter000_2026-08-24-17-22-07.perfetto-trace`
- `app/build/perf-results/before/startup/StartupBenchmarks_coldStartupCompilationNone_iter001_2026-08-24-17-22-23.perfetto-trace`
- `app/build/perf-results/before/startup/StartupBenchmarks_coldStartupCompilationNone_iter002_2026-08-24-17-22-47.perfetto-trace`
- `app/build/perf-results/before/startup/StartupBenchmarks_coldStartupCompilationNone_iter003_2026-08-24-17-23-10.perfetto-trace`
- `app/build/perf-results/before/startup/StartupBenchmarks_coldStartupCompilationNone_iter004_2026-08-24-17-23-31.perfetto-trace`
- `app/build/perf-results/before/journey/com.adrianrusu.pandawave.benchmark-benchmarkData.json`
- `app/build/perf-results/before/journey/JourneyBenchmarks_libraryAndProfileCompilationNone_iter000_2026-08-24-17-20-02.perfetto-trace`
- `app/build/perf-results/before/journey/JourneyBenchmarks_libraryAndProfileCompilationNone_iter001_2026-08-24-17-20-15.perfetto-trace`
- `app/build/perf-results/before/journey/JourneyBenchmarks_libraryAndProfileCompilationNone_iter002_2026-08-24-17-20-26.perfetto-trace`
- `app/build/perf-results/before/journey/JourneyBenchmarks_libraryAndProfileCompilationNone_iter003_2026-08-24-17-20-38.perfetto-trace`
- `app/build/perf-results/before/journey/JourneyBenchmarks_libraryAndProfileCompilationNone_iter004_2026-08-24-17-20-49.perfetto-trace`

### Cold Startup

Benchmark:

- Class: `com.adrianrusu.pandawave.benchmark.StartupBenchmarks`
- Test: `coldStartupCompilationNone`
- Compilation mode: `CompilationMode.None`, represented by the JSON context as `run-from-apk`
- Startup mode: cold
- Iterations: 5
- Total benchmark runtime: `99.3035788 s`

Metrics:

| Metric | Min | Median | Max | Mean | CoV | Runs |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| `timeToInitialDisplayMs` | `1838.6698` | `5762.0211` | `7036.3755` | `5391.1439` | `0.3907` from AndroidX, `0.3495` population from runs | `1838.6698`, `5762.0211`, `7036.3755`, `6902.6553`, `5415.9978` |
| `frameCount` | `5` | `5` | `7` | n/a | `0.1889` | `5`, `7`, `5`, `7`, `5` |

Sampled frame metrics:

| Metric | P50 | P90 | P95 | P99 |
| --- | ---: | ---: | ---: | ---: |
| `frameDurationCpuMs` | `244.2124` | `2400.2472` | `2888.2105` | `3033.3709` |
| `frameOverrunMs` | `329.7350` | `2721.5109` | `3268.8845` | `3504.5959` |

Notes:

- The startup distribution was very wide. One run was much faster than the others (`1838.6698 ms`), while the median landed at `5762.0211 ms`.
- Startup sampled frame metrics are dominated by very large early-frame values, which is common when cold startup and first render are bundled into the same frame-timing window.

### Library/Profile Journey

Benchmark:

- Class: `com.adrianrusu.pandawave.benchmark.JourneyBenchmarks`
- Test: `libraryAndProfileCompilationNone`
- Compilation mode: `CompilationMode.None`, represented by the JSON context as `run-from-apk`
- Startup mode: warm
- Iterations: 5
- Flow: open Library, scroll content 3 times, open Profile, scroll content 3 times
- Total benchmark runtime: `71.1704254 s`

Metrics:

| Metric | Min | Median | Max | CoV | Runs |
| --- | ---: | ---: | ---: | ---: | --- |
| `frameCount` | `7` | `7` | `7` | `0.0` | `7`, `7`, `7`, `7`, `7` |

Sampled frame metrics:

| Metric | P50 | P90 | P95 | P99 |
| --- | ---: | ---: | ---: | ---: |
| `frameDurationCpuMs` | `44.5623` | `77.1695` | `84.4229` | `101.7980` |
| `frameOverrunMs` | `38.0071` | `79.6287` | `89.4742` | `103.0970` |

## Before Actor: Host-GPU Checkpoint

These are separate from the primary before checkpoint and should not be merged into the same median. They are useful as context for renderer/device sensitivity.

Artifacts:

- `app/build/perf-results/before/host-gpu/startup/com.adrianrusu.pandawave.benchmark-benchmarkData.json`
- `app/build/perf-results/before/host-gpu/startup/StartupBenchmarks_coldStartupCompilationNone_iter000_2026-08-24-17-30-35.perfetto-trace`
- `app/build/perf-results/before/host-gpu/startup/StartupBenchmarks_coldStartupCompilationNone_iter001_2026-08-24-17-31-05.perfetto-trace`
- `app/build/perf-results/before/host-gpu/startup/StartupBenchmarks_coldStartupCompilationNone_iter002_2026-08-24-17-31-29.perfetto-trace`
- `app/build/perf-results/before/host-gpu/startup/StartupBenchmarks_coldStartupCompilationNone_iter003_2026-08-24-17-31-52.perfetto-trace`
- `app/build/perf-results/before/host-gpu/startup/StartupBenchmarks_coldStartupCompilationNone_iter004_2026-08-24-17-32-14.perfetto-trace`
- `app/build/perf-results/before/host-gpu/journey/com.adrianrusu.pandawave.benchmark-benchmarkData.json`
- `app/build/perf-results/before/host-gpu/journey/JourneyBenchmarks_libraryAndProfileCompilationNone_iter000_2026-08-24-17-34-53.perfetto-trace`
- `app/build/perf-results/before/host-gpu/journey/JourneyBenchmarks_libraryAndProfileCompilationNone_iter001_2026-08-24-17-35-12.perfetto-trace`
- `app/build/perf-results/before/host-gpu/journey/JourneyBenchmarks_libraryAndProfileCompilationNone_iter002_2026-08-24-17-35-31.perfetto-trace`
- `app/build/perf-results/before/host-gpu/journey/JourneyBenchmarks_libraryAndProfileCompilationNone_iter003_2026-08-24-17-35-51.perfetto-trace`
- `app/build/perf-results/before/host-gpu/journey/JourneyBenchmarks_libraryAndProfileCompilationNone_iter004_2026-08-24-17-36-09.perfetto-trace`

Host-GPU cold startup:

| Metric | Min | Median | Max | CoV | Runs |
| --- | ---: | ---: | ---: | ---: | --- |
| `timeToInitialDisplayMs` | `5330.1048` | `6067.1258` | `9183.9847` | `0.2397` | `9183.9847`, `7029.3697`, `6067.1258`, `5460.0856`, `5330.1048` |
| `frameCount` | `4` | `6` | `7` | `0.2041` | `7`, `6`, `7`, `6`, `4` |

Host-GPU cold startup sampled frame metrics:

| Metric | P50 | P90 | P95 | P99 |
| --- | ---: | ---: | ---: | ---: |
| `frameDurationCpuMs` | `176.4484` | `1974.8637` | `2336.9426` | `2844.9648` |
| `frameOverrunMs` | `255.5291` | `2392.7723` | `2806.6778` | `3478.7609` |

Host-GPU Library/Profile journey:

| Metric | Min | Median | Max | CoV | Runs |
| --- | ---: | ---: | ---: | ---: | --- |
| `frameCount` | `4` | `6` | `7` | `0.1889` | `6`, `6`, `4`, `7`, `6` |

Host-GPU journey sampled frame metrics:

| Metric | P50 | P90 | P95 | P99 |
| --- | ---: | ---: | ---: | ---: |
| `frameDurationCpuMs` | `126.1895` | `200.1127` | `216.6408` | `333.9416` |
| `frameOverrunMs` | `137.6214` | `268.8553` | `293.0359` | `399.4868` |

## Before Actor: AAOS Trace

An AAOS startup trace exists here:

- `app/build/perf-results/before/aaos/pandawave-before-aaos-startup.perfetto-trace`

No AndroidX JSON metrics were found next to this AAOS trace. Treat it as a raw Perfetto artifact only.

## After Actor: Macrobenchmark Status

The benchmark APKs built and installed, but the AndroidX Macrobenchmark runner failed before app launch:

```text
java.lang.IllegalStateException: Failed to cancel background dexopt job, result: ''
    at androidx.benchmark.macro.MacrobenchmarkScope.cancelBackgroundDexopt$benchmark_macro(MacrobenchmarkScope.kt:573)
```

The same emulator accepted the underlying package-manager command manually:

```text
adb shell pm bg-dexopt-job --cancel
Background dexopt job cancelled
```

Interpretation:

- The after Macrobenchmark run did not produce a valid AndroidX benchmark JSON.
- This is a harness/setup issue, not evidence of app startup failure.
- The fallback after measurements below use direct Perfetto, direct `am start -W`, and `gfxinfo`.

## After Actor: Direct Startup Metrics

Artifacts:

- `app/build/perf-results/actor-after-tracing/startup/pandawave-after-startup.perfetto-trace`
- `app/build/perf-results/actor-after-tracing/startup/am-start-W-cold-starts.txt`

Perfetto capture:

| Field | Value |
| --- | --- |
| Duration | `20 s` |
| Device path | `/data/misc/perfetto-traces/pandawave-after-startup.perfetto-trace` |
| Local size | `4,325,893 bytes` |
| App filter | `--app com.adrianrusu.pandawave` |
| Categories | `sched freq idle am wm gfx view binder_driver hal dalvik` |

Direct cold startup timing:

| Metric | Min | Median | Max | Mean | CoV | Runs |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| `am start -W TotalTimeMs` | `1250` | `1322` | `1656` | `1369.6` | `0.1078` | `1656`, `1322`, `1250`, `1269`, `1351` |
| `am start -W WaitTimeMs` | `1253` | `1326` | `1659` | `1373.8` | `0.1074` | `1659`, `1326`, `1253`, `1272`, `1359` |

All 5 runs reported:

```text
LaunchState: COLD
Status: ok
Activity: com.adrianrusu.pandawave/.MainActivity
```

An earlier after trace launch reported:

```text
TotalTime: 2096
WaitTime: 2102
LaunchState: COLD
```

That earlier launch is the one that overlapped the saved startup Perfetto trace. The 5-run `am start -W` artifact was captured afterward to get a small distribution.

## After Actor: Direct Library/Profile Journey Metrics

Artifacts:

- `app/build/perf-results/actor-after-tracing/journey/pandawave-after-journey.perfetto-trace`
- `app/build/perf-results/actor-after-tracing/journey/gfxinfo-framestats.txt`
- `app/build/perf-results/actor-after-tracing/journey/gfxinfo-framestats-reset-journey.txt`

Perfetto capture:

| Field | Value |
| --- | --- |
| Duration | `35 s` |
| Device path | `/data/misc/perfetto-traces/pandawave-after-journey.perfetto-trace` |
| Local size | `19,798,519 bytes` |
| App filter | `--app com.adrianrusu.pandawave` |
| Categories | `sched freq idle am wm gfx view binder_driver hal dalvik` |
| Flow | tap Library, scroll 3 times, tap Profile, scroll 3 times |

Clean journey-only `gfxinfo` pass:

| Metric | Value |
| --- | ---: |
| Total frames rendered | `342` |
| Janky frames | `58` |
| Janky frames % | `16.96%` |
| Legacy janky frames | `213` |
| Legacy janky frames % | `62.28%` |
| Frame p50 | `19 ms` |
| Frame p90 | `36 ms` |
| Frame p95 | `44 ms` |
| Frame p99 | `101 ms` |
| Missed Vsync | `7` |
| High input latency | `519` |
| Slow UI thread | `15` |
| Slow bitmap uploads | `0` |
| Slow issue draw commands | `54` |
| Frame deadline missed | `58` |
| Frame deadline missed, legacy | `49` |
| GPU p50 | `13 ms` |
| GPU p90 | `22 ms` |
| GPU p95 | `4950 ms` |
| GPU p99 | `4950 ms` |

Process-wide journey snapshot captured immediately after the first direct Perfetto journey:

| Metric | Value |
| --- | ---: |
| Total frames rendered | `393` |
| Janky frames | `28` |
| Janky frames % | `7.12%` |
| Legacy janky frames | `222` |
| Legacy janky frames % | `56.49%` |
| Frame p50 | `18 ms` |
| Frame p90 | `32 ms` |
| Frame p95 | `40 ms` |
| Frame p99 | `150 ms` |
| Missed Vsync | `8` |
| High input latency | `595` |
| Slow UI thread | `14` |
| Slow bitmap uploads | `0` |
| Slow issue draw commands | `23` |
| Frame deadline missed | `28` |
| GPU p50 | `6 ms` |
| GPU p90 | `11 ms` |
| GPU p95 | `13 ms` |
| GPU p99 | `4950 ms` |

The clean reset pass is better for the after journey frame snapshot. The process-wide snapshot is still preserved because it is the artifact captured next to the original journey Perfetto trace.

## Trace Instrumentation Added For After Run

The after build includes broader app trace sections through a shared `PandaTrace` helper:

- `PW.Startup.Application.onCreate`
- `PW.Startup.ThemeCoordinator.start`
- `PW.Startup.AudioCache.install`
- `PW.Startup.MainActivity.onCreate`
- `PW.Startup.Splash.install`
- `PW.Startup.Splash.exitAnimation`
- `PW.Startup.PlaybackRepository.start`
- `PW.Startup.Compose.setContent`
- `PW.Startup.Compose.firstEffect`
- `PW.Startup.PlaybackRepository.close`
- `PW.Engine.Gateway.*`
- `PW.Engine.Connection.*`
- `PW.Engine.Binder.*`
- `PW.Engine.Service.*`
- `PW.Engine.Native.*`
- `PW.Media3.Service.*`
- `PW.Media3.Player.create`
- `PW.Media3.Catalog.*`
- `PW.Media3.Callback.*`

Local marker validation caveat:

- A binary `rg -a "PW."` pass can find earlier short `PW.` strings in some before traces.
- It did not reliably find the new full marker names in the after traces.
- No host or device `trace_processor_shell` was available in the current environment.
- The right validation path is to open the raw traces in Perfetto UI or query them with `trace_processor_shell` once available.

## What This Says So Far

1. There is usable before data from AndroidX Macrobenchmark, including raw Perfetto traces and 5-run JSON metrics.
2. There is usable after data from direct Perfetto and direct ADB/gfxinfo, including whole-app startup and Library/Profile journey traces.
3. The after app launches cold successfully and repeatedly on the API 35 emulator.
4. The after direct cold-start timings are much lower than the before Macrobenchmark TTI median, but the formal before/after benchmark delta is blocked until AndroidX Macrobenchmark can run after again.
5. The journey still shows jank in the direct `gfxinfo` pass: `58 / 342` janky frames in the clean reset journey, with p95 frame time `44 ms`.
6. The after journey trace should be inspected around missed frame/deadline clusters, slow draw commands, and binder/native sections to decide the next code optimization.

## Recommended Next Measurement Step

Fix or work around the AndroidX Macrobenchmark dexopt setup issue, then rerun:

```powershell
adb shell am instrument -w `
    -e class 'com.adrianrusu.pandawave.benchmark.StartupBenchmarks#coldStartupCompilationNone' `
    -e androidx.benchmark.suppressErrors EMULATOR `
    com.adrianrusu.pandawave.benchmark/androidx.test.runner.AndroidJUnitRunner

adb shell am instrument -w `
    -e class 'com.adrianrusu.pandawave.benchmark.JourneyBenchmarks#libraryAndProfileCompilationNone' `
    -e androidx.benchmark.suppressErrors EMULATOR `
    com.adrianrusu.pandawave.benchmark/androidx.test.runner.AndroidJUnitRunner
```

Then store the new AndroidX outputs under:

- `app/build/perf-results/actor-after-tracing/startup-macrobenchmark/`
- `app/build/perf-results/actor-after-tracing/journey-macrobenchmark/`

That will give a true apples-to-apples comparison against the primary before checkpoint.

## Verification

Focused unit verification after tracing changes:

```powershell
./gradlew.bat :core:common:testDebugUnitTest :app:testDebugUnitTest `
    :core:media-adapter:testDebugUnitTest :core:rust-bridge:testDebugUnitTest `
    "-PpandaEngine.buildNative=false" --console=plain
```

Result:

```text
BUILD SUCCESSFUL in 37s
381 actionable tasks: 12 executed, 369 up-to-date
```
