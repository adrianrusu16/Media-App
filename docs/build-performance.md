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
