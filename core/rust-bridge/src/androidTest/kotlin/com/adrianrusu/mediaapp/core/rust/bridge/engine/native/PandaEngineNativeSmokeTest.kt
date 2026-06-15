package com.adrianrusu.mediaapp.core.rust.bridge.engine.native

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PandaEngineNativeSmokeTest {
    @Test
    fun nativeEngineLoadsSnapshotsDispatchesAndDestroys() {
        var nowEpochMillis = 1_000L
        PandaEngine.create(clock = { nowEpochMillis }).use { engine ->
            val initialSnapshot = engine.snapshot()
            assertEquals(EngineSnapshot.PLAYBACK_IDLE, initialSnapshot.playbackState)
            assertEquals(EngineSnapshot.RESTRICTION_UNKNOWN, initialSnapshot.restrictionState)
            assertEquals(1_000L, initialSnapshot.updatedAtEpochMillis)

            nowEpochMillis = 2_000L
            val playResult = engine.dispatch(
                EngineCommand(
                    type = EngineCommand.TYPE_PLAY,
                    payload = null
                )
            )
            assertEquals(EngineSnapshot.PLAYBACK_PLAYING, playResult.snapshot.playbackState)
            assertEquals(EngineEvent.TYPE_COMMAND_APPLIED, playResult.event.type)
            assertEquals(EngineCommand.TYPE_PLAY, playResult.event.message)
            assertEquals(2_000L, playResult.snapshot.updatedAtEpochMillis)

            nowEpochMillis = 2_500L
            val seekResult = engine.dispatch(
                EngineCommand(
                    type = EngineCommand.TYPE_SEEK,
                    payload = "12345"
                )
            )
            assertEquals(12_345L, seekResult.snapshot.positionMillis)

            val speedResult = engine.dispatch(
                EngineCommand(
                    type = EngineCommand.TYPE_SET_SPEED,
                    payload = "1.25"
                )
            )
            assertEquals(1.25F, speedResult.snapshot.playbackSpeed)

            nowEpochMillis = 3_000L
            val platformResult = engine.dispatchPlatformEvent(
                EnginePlatformEvent(
                    type = EnginePlatformEvent.TYPE_APP_FOREGROUNDED,
                    payload = null
                )
            )
            assertEquals(EngineSnapshot.PLAYBACK_PLAYING, platformResult.snapshot.playbackState)
            assertEquals(EngineEvent.TYPE_PLATFORM_EVENT_APPLIED, platformResult.event.type)
            assertEquals(EnginePlatformEvent.TYPE_APP_FOREGROUNDED, platformResult.event.message)
            assertEquals(3_000L, platformResult.snapshot.updatedAtEpochMillis)
        }
    }
}
