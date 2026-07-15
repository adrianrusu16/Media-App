package com.adrianrusu.pandawave.core.rust.bridge.engine.native

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommandPayloads
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PandaEngineNativeSmokeTest {
    @Test
    fun `production backend configuration rejects cleartext endpoints`() {
        PandaEngine.create().use { engine ->
            assertThrows(IllegalStateException::class.java) {
                engine.configureBackend(cleartextConfig(), isDevelopment = false)
            }
        }
    }

    @Test
    fun `native engine follows session and playback lifecycle`() {
        var nowEpochMillis = 1_000L
        PandaEngine.create(clock = { nowEpochMillis }).use { engine ->
            val initialSnapshot = engine.snapshot()
            assertEquals(EngineSnapshot.PLAYBACK_IDLE, initialSnapshot.playbackState)
            assertEquals(EngineSnapshot.RESTRICTION_UNKNOWN, initialSnapshot.restrictionState)
            assertEquals(1_000L, initialSnapshot.updatedAtEpochMillis)

            nowEpochMillis = 1_500L
            val sessionResult = engine.dispatch(
                EngineCommand(
                    type = EngineCommand.TYPE_START_SESSION,
                    payload = "android-smoke-user"
                )
            )
            assertTrue(sessionResult.snapshot.hasActiveSession)
            assertEquals("android-smoke-user", sessionResult.snapshot.userId)

            nowEpochMillis = 2_000L
            val playResult = engine.dispatch(
                EngineCommand(
                    type = EngineCommand.TYPE_PLAY,
                    payload = null
                )
            )
            assertEquals(EngineSnapshot.PLAYBACK_BUFFERING, playResult.snapshot.playbackState)
            assertEquals(EngineEvent.TYPE_COMMAND_APPLIED, playResult.event.type)
            assertEquals(EngineCommand.TYPE_PLAY, playResult.event.message)
            assertEquals(2_000L, playResult.snapshot.updatedAtEpochMillis)

            nowEpochMillis = 2_250L
            val loadedResult = engine.dispatchPlatformEvent(
                EnginePlatformEvent(
                    type = EnginePlatformEvent.TYPE_MEDIA_LOADED,
                    payload = null
                )
            )
            assertEquals(EngineSnapshot.PLAYBACK_PLAYING, loadedResult.snapshot.playbackState)

            nowEpochMillis = 2_500L
            val seekResult = engine.dispatch(
                EngineCommand(
                    type = EngineCommand.TYPE_SEEK,
                    payload = EngineCommandPayloads.seekPositionMillis(12_345L)
                )
            )
            assertEquals(12_345L, seekResult.snapshot.positionMillis)

            val speedResult = engine.dispatch(
                EngineCommand(
                    type = EngineCommand.TYPE_SET_SPEED,
                    payload = EngineCommandPayloads.playbackSpeed(1.25F)
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

            nowEpochMillis = 3_500L
            val endSessionResult = engine.dispatch(
                EngineCommand(
                    type = EngineCommand.TYPE_END_SESSION,
                    payload = null
                )
            )
            assertFalse(endSessionResult.snapshot.hasActiveSession)
        }
    }

    private fun cleartextConfig(): String =
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .targetContext.assets.open("client-connection.json")
            .bufferedReader().use { it.readText() }
}
