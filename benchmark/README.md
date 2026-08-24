# PandaWave Performance Benchmarks

This module owns the repeatable startup, frame-timing, Perfetto, and Baseline Profile journeys.

## Device Contract

- Use one fixed API 35+ single-user device for AndroidX Macrobenchmark comparisons.
- Keep CPU count, renderer, resolution, animations, and power settings unchanged across checkpoints.
- Emulator results are comparison data only. Confirm release decisions on representative hardware.
- AAOS headless-system-user images are not supported reliably by AndroidX Macrobenchmark. Use the
  same app journey with a manual Perfetto capture on AAOS as a separate automotive checkpoint.

## Build

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:GRADLE_USER_HOME = 'E:\Android\gradle-home'
./gradlew.bat ':app:assembleBenchmark' ':benchmark:assembleBenchmarkBenchmark' `
    '-Ppandawave.verificationAppLinkHost=benchmark.pandawave.dev'
```

Install `app/build/outputs/apk/benchmark/app-benchmark.apk` and
`benchmark/build/outputs/apk/benchmarkBenchmark/benchmark-benchmarkBenchmark.apk`.

## Run

Pass `androidx.benchmark.suppressErrors=EMULATOR` explicitly when invoking instrumentation through
ADB. Gradle supplies the argument only for Gradle-managed connected tests.

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

Pull `/sdcard/Android/media/com.adrianrusu.pandawave.benchmark` immediately after each benchmark
class. AndroidX replaces that output directory when the next class runs. Store results under
`app/build/perf-results/<checkpoint>/<journey>`.

## Checkpoints

1. `before`: current engine implementation, before actor ownership changes.
2. `actor-2-workers` and `actor-4-workers`: actor implementation before other optimizations.
3. `final-none`: all improvements with `CompilationMode.None`.
4. `final-baseline-profile`: all improvements with the generated Baseline Profile.

Use `perfetto/startup-hotspots.sql` and `perfetto/startup-cpu.sql` against representative startup
traces. Keep the raw JSON and every `.perfetto-trace`; top-line medians are not enough to explain
regressions.
