# Screenshot and overlay evidence

Captured on 7 September 2026. The [guide](RRO_GUIDE.md) contains 25 resource sections: 21 use new emulator captures, two retain supplied references, and two have no image because the required state was unavailable. Six additional PNGs provide separate theme and overlay comparison panels. Some sections intentionally reuse the same full-screen capture to explain different components.

## Device and source context

| Field | Recorded value |
| --- | --- |
| Target package | `com.adrianrusu.pandawave` |
| Installed version | `1.0`, version code `1` |
| Installed build relationship | Captured the already installed app; its exact source commit was not independently established |
| Resource-table source snapshot | `ac64500c3c3f77831078bdc626cefc2dd87fb957` |
| Device | Connected AAOS x86_64 emulator, `emulator-5554` |
| Android / app user | Android 15 / API 35, user `10` |
| Display | `1408 × 792`, physical density `160`, font scale `1.0` |
| Build fingerprint | `google/sdk_gcar_x86_64/emulator_car64_x86_64:15/AAI5.260516.001.A1/15446886:userdebug/test-keys` |
| App theme | Forest Tech Dark, except the three explicitly labeled alternative-theme captures |
| Vehicle state | Baseline/parked except the explicitly labeled restricted Home capture |
| Ambient preference | Enabled, 15-second inactivity delay; temporarily disabled for navigation, then restored and checked |
| Capture format | Full-screen PNG, without cropping, painting, annotations or synthetic content |
| Provenance | Per-file source label, dimensions and SHA-256 in [image-manifest.json](image-manifest.json) |

The AAOS clock/status/dock sometimes disappear during captures; those system surfaces are outside the target app. Full-screen images retain what the emulator rendered. Captions describe the pictured component rather than treating every nearby resource as a proven control over it.

## Personal overlay test

The complete [personal example](examples/personal-overlay/AndroidManifest.xml) was compiled with Android SDK Build Tools 36.0.0 and the installed Android 36.1 platform jar, aligned and signed using a disposable local test key. APK signature verification succeeded using v3. The signing key and generated APK are not part of the guide archive.

| Check | Result |
| --- | --- |
| Package installed for user 10 | `com.example.pandawave.overlay.personal` |
| Overlayable target | `PandaWaveDesignTokens` in `com.adrianrusu.pandawave` |
| Enabled overlay list | `[x] com.example.pandawave.overlay.personal` |
| Enabled primary lookup | `#ff00d7ff` |
| Enabled rail lookup | `108.0dip` |
| Visual Home result | Cyan selected rail marker and play controls; content begins after the wider rail |
| Visual Now Playing result | Cyan artist text, progress, Play control and volume slider |
| Rollback primary lookup | `#ff93d87e` |
| Rollback rail lookup | `100.0dip` |
| Cleanup | Overlay disabled, app relaunched, package uninstalled for user 10 with `Success`; final target overlay list empty |

The comparison uses the same device, theme, density, font scale, track and Home scroll position. Baseline playback is paused at 0:00 and overlay playback at 0:23. Differences in the progress position and animated bars are not overlay effects. The example proves these two resources on this installed debug environment; it does not certify OEM integration, release shrinking, all 216 resources, or animation timings.

On this Windows host, signing tools encountered a Unicode-path issue under the accented profile directory. Using the existing ASCII SDK path alias and invoking `java -jar <build-tools>/lib/apksigner.jar` allowed signing to complete. Keep SDK/JDK paths accessible to their native tools when reproducing the build.

## Capture limits and corrections

| Section/state | Evidence and remaining limit |
| --- | --- |
| Standard media tiles | The provider loads these published values, but no production renderer reading the standard sizing fields was found. Home uses compact tiles. No screenshot is invented for the unused variant. |
| Category cards | `BambooCategoryCard` reads the category sizes, but no feature route invokes it. The actual empty Playlists form is shown and is not labeled a category-card example. |
| Login and registration | The emulator was authenticated. The session was preserved; a parked signed-out test session is still needed. The documented filename remains reserved without a broken image embed. |
| Populated Library History | Current account history was empty. The populated screenshot from the original ZIP is retained with unknown capture/build metadata. |
| Launcher media card | Current launcher showed Radio. The original PandaWave launcher card is retained with unknown capture/build metadata. A fresh PandaWave splash was captured. |
| Ambient | The verified idle APT. frame is included. Attempts to obtain a stable active amplitude-source comparison did not produce a sufficiently verified pair. |
| Voice | Now Playing calls `BambooVoiceIndicator()` with its default `isActive=true`; Search supplies an empty `onVoiceClick`. The screenshot is decorative bars, not microphone/listening evidence. |
| Feedback | Empty Saved Library shows a plain text message. It does not exercise the shared feedback icon and layout geometry. Loading and recoverable-error variants remain uncaptured. |
| Focus | Keyboard Tab focus visibly outlines Library while Next and other playback controls are disabled. Hardware rotary behavior remains untested. |
| Restrictions | AAOS reported `no_dialpad`, `no_filtering`, `no_keyboard`, `no_setup`, `no_text_message`, `no_video` for the restricted Home capture. The image alone does not prove app enforcement. The three published restriction integers have no production field readers in the reviewed source. |
| Other acceptance work | Monochrome launcher, restricted authentication, larger font scales, other HU sizes and video-based motion tests remain outside this capture set. |

The original annotated and unannotated ZIP images remain unchanged in their reference folders. Use the [image index](../images/README.md) to replace an example later, and the [capture template](CAPTURE_CHECKLIST.md) to record its conditions.
