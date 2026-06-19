# Now Playing Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Stitch-inspired PandaWave Now Playing screen in Compose without changing PandaEngine contracts.

**Architecture:** Keep `NowPlayingRoute` backed by the existing `NowPlayingState` and `NowPlayingIntent`. Add pure UI projection helpers for testable labels/control availability, then compose the full-screen cockpit from focused private composables in `feature/nowplaying`. Remove app-shell engine internals from user-facing header chrome and rely on the existing sidebar for Settings/Profile.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Android vector drawables, JUnit 5/kotlin-test, existing BambooUI/design-system tokens.

---

### Task 1: Spec And UI Projection

**Files:**
- Modify: `docs/superpowers/specs/2026-06-19-now-playing-screen-design.md`
- Create: `feature/nowplaying/src/main/kotlin/com/adrianrusu/mediaapp/feature/nowplaying/NowPlayingUiModel.kt`
- Create: `feature/nowplaying/src/test/kotlin/com/adrianrusu/mediaapp/feature/nowplaying/NowPlayingUiModelTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.adrianrusu.mediaapp.feature.nowplaying

import com.adrianrusu.mediaapp.core.playback.BambooEngineConnectionUiState
import com.adrianrusu.mediaapp.feature.nowplaying.domain.NowPlayingPlaybackState
import com.adrianrusu.mediaapp.feature.nowplaying.domain.NowPlayingRestrictionState
import com.adrianrusu.mediaapp.feature.nowplaying.domain.NowPlayingState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NowPlayingUiModelTest {
    @Test
    fun `drive restriction does not disable media controls`() {
        val model = NowPlayingState(
            playbackState = NowPlayingPlaybackState.Paused,
            engineConnection = BambooEngineConnectionUiState.Ready,
            restriction = NowPlayingRestrictionState(
                label = "Driver-safe mode",
                isRestricted = true
            )
        ).toNowPlayingUiModel(volume = 45F)

        assertTrue(model.controlsEnabled)
        assertTrue(model.isDriveRestricted)
    }

    @Test
    fun `play button uses panda paw icon when paused`() {
        val model = NowPlayingState(
            playbackState = NowPlayingPlaybackState.Paused,
            engineConnection = BambooEngineConnectionUiState.Ready
        ).toNowPlayingUiModel(volume = 45F)

        assertEquals(NowPlayingPrimaryControlIcon.PandaPaw, model.primaryControlIcon)
    }

    @Test
    fun `play button uses pause icon when playing`() {
        val model = NowPlayingState(
            playbackState = NowPlayingPlaybackState.Playing,
            engineConnection = BambooEngineConnectionUiState.Ready
        ).toNowPlayingUiModel(volume = 45F)

        assertEquals(NowPlayingPrimaryControlIcon.Pause, model.primaryControlIcon)
    }

    @Test
    fun `engine unavailable disables media controls without exposing engine copy`() {
        val model = NowPlayingState(
            playbackState = NowPlayingPlaybackState.Paused,
            engineConnection = BambooEngineConnectionUiState.Connecting
        ).toNowPlayingUiModel(volume = 45F)

        assertFalse(model.controlsEnabled)
        assertEquals("Controls unavailable", model.availabilityLabel)
    }

    @Test
    fun `volume is clamped to zero to one hundred`() {
        assertEquals(0F, NowPlayingVolumeUiModel.from(-20F).value)
        assertEquals(100F, NowPlayingVolumeUiModel.from(120F).value)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat --no-configuration-cache :feature:nowplaying:testDebugUnitTest --console=plain`

Expected: FAIL because `NowPlayingUiModel`, `NowPlayingPrimaryControlIcon`, and `toNowPlayingUiModel` do not exist.

- [ ] **Step 3: Implement the projection**

Create `NowPlayingUiModel.kt` with a pure projection from `NowPlayingState` to UI-ready state. `controlsEnabled` must only use engine readiness and must not check `restriction.isRestricted`.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat --no-configuration-cache :feature:nowplaying:testDebugUnitTest --console=plain`

Expected: PASS.

### Task 2: Stitch Now Playing Compose Screen

**Files:**
- Modify: `feature/nowplaying/src/main/kotlin/com/adrianrusu/mediaapp/feature/nowplaying/NowPlayingRoute.kt`
- Add: `core/designsystem/src/main/res/drawable/ic_panda_paw.xml`
- Modify: `core/designsystem/src/main/res/values/public.xml`

- [ ] **Step 1: Add the panda paw vector**

Create a simple Android vector drawable named `ic_panda_paw` in the design-system module and expose it from `public.xml`.

- [ ] **Step 2: Replace the old status-card screen**

Rewrite the private screen composition in `NowPlayingRoute.kt` around:

- central artwork placeholder with PandaWave logo
- title/detail overlay
- thick progress row with leaf marker
- large green primary pebble button
- previous/next pebble buttons
- quick-action footer buttons
- local-state volume control

- [ ] **Step 3: Keep mini-player behavior unchanged**

Do not edit `core/ui/src/main/kotlin/com/adrianrusu/mediaapp/core/ui/miniplayer/BambooMiniPlayer.kt` except for compile-required imports if shared helpers are introduced.

- [ ] **Step 4: Compile Now Playing**

Run: `.\gradlew.bat --no-configuration-cache :feature:nowplaying:compileDebugKotlin --console=plain`

Expected: PASS.

### Task 3: App Shell Chrome Cleanup

**Files:**
- Modify: `feature/appshell/src/main/kotlin/com/adrianrusu/mediaapp/appshell/presentation/AppShellScreen.kt`

- [ ] **Step 1: Remove user-facing engine chip**

Update `Header` so it shows the app name and current destination only. It must not render `engineConnection.label`.

- [ ] **Step 2: Remove old global quick-action buttons**

Delete the bottom `QuickActions` item from `AppShellContent`; Now Playing owns its Stitch quick actions and the sidebar owns persistent navigation.

- [ ] **Step 3: Verify app shell compile**

Run: `.\gradlew.bat --no-configuration-cache :feature:appshell:compileDebugKotlin --console=plain`

Expected: PASS.

### Task 4: Verification And Milestone Commit

**Files:**
- All files changed in Tasks 1-3.

- [ ] **Step 1: Run focused tests**

Run: `.\gradlew.bat --no-configuration-cache :feature:nowplaying:testDebugUnitTest :feature:appshell:testDebugUnitTest --console=plain`

Expected: PASS.

- [ ] **Step 2: Run standard verification**

Run: `.\gradlew.bat --no-configuration-cache spotlessApply :core:playback:testDebugUnitTest :core:media-adapter:testDebugUnitTest qualityCheck :app:assembleDebug --console=plain`

Expected: PASS.

- [ ] **Step 3: Update graph**

Run: `graphify update .`

Expected: PASS. If it fails with the known `uv trampoline failed to canonicalize script path`, record the failure and continue.

- [ ] **Step 4: Commit and push**

Commit message: `feat: build stitch now playing screen`

Push: `git push origin master`
