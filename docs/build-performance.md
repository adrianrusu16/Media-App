# PandaWave build performance

Development builds should compile only the native work they need. Release
builds keep full production ABI coverage and the Cargo `release` profile.

## Debug (local development)

`assembleDebug` builds one ABI with the Cargo `android-dev` profile.

Emulator (default):

```powershell
.\gradlew.bat :app:assembleDebug -PpandaEngine.debugAbis=x86_64
```

`x86_64` is the default Debug ABI, so the property can be omitted for the
usual AAOS emulator workflow.

ARM64 device:

```powershell
.\gradlew.bat :app:assembleDebug -PpandaEngine.debugAbis=arm64-v8a
```

No native build:

```powershell
.\gradlew.bat :app:assembleDebug -PpandaEngine.buildNative=false
```

Artifacts produced with `buildNative=false` are not runtime-validation
artifacts and must not be shipped.

Debug JNI libraries are generated under:

`core/rust-bridge/build/generated/panda-engine/debug/jniLibs/`

## Release

`assembleRelease` builds every configured production ABI with the Cargo
`release` profile. The default remains:

`arm64-v8a,armeabi-v7a,x86,x86_64`

```powershell
.\gradlew.bat :app:assembleRelease `
    -Ppandawave.verificationAppLinkHost=<public-host>
```

Override only when you intend to change the release ABI set:

```powershell
.\gradlew.bat :app:assembleRelease `
    -PpandaEngine.releaseAbis=arm64-v8a,armeabi-v7a,x86,x86_64 `
    -Ppandawave.verificationAppLinkHost=<public-host>
```

Release JNI libraries are generated under:

`core/rust-bridge/build/generated/panda-engine/release/jniLibs/`

## Native task graph

Debug and release native work is separate. Changing `pandaEngine.debugAbis`
or `pandaEngine.releaseAbis` changes which Cargo tasks run; `ndk.abiFilters`
alone is not enough.

- Debug: `preDebugBuild` → `syncPandaEngineDebugJniLibs` → selected
  `buildPandaEngineAndroidDebug*` tasks (`android-dev`)
- Release: `preReleaseBuild` → `syncPandaEngineReleaseJniLibs` → selected
  `buildPandaEngineAndroidRelease*` tasks (`release`)

`buildPandaEngineAndroid` still builds every configured release ABI.

Gradle pins `CARGO_TARGET_DIR` to `rust/engine/target` so a shell or
sandbox `CARGO_TARGET_DIR` cannot send the `.so` somewhere the JNI sync
task does not look.

## Gradle

The repository enables:

- `org.gradle.parallel=true`
- `org.gradle.caching=true`
- `org.gradle.configuration-cache=true`

Cargo ABI tasks still run one at a time (`PandaEngineCargoMutex`).

On this Windows machine, keep Gradle away from Android Studio's daemon
registry when measuring builds:

```powershell
$env:GRADLE_USER_HOME = 'C:\GradleHome'
```

`kotlin.compiler.execution.strategy=in-process` remains until an ASCII
Gradle home is confirmed working with the Kotlin daemon.

## CI

- `android-fast`: quality, lint, and unit tests with
  `-PpandaEngine.buildNative=false` (no NDK, no Android Rust targets)
- `android-debug`: one `x86_64` `android-dev` native assemble after fast
  validation
- `android-release`: production ABIs + `assembleRelease` /
  `assembleBenchmark` on `master`, `release*` branches, and
  `workflow_dispatch`
- Connected MediaBrowser smoke stays `buildNative=false`

## AIDL

AIDL processing is enabled only on `:core:rust-bridge`.
