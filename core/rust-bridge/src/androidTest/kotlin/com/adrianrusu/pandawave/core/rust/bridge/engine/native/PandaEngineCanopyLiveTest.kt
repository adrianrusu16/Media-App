package com.adrianrusu.pandawave.core.rust.bridge.engine.native

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommandPayloads
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PandaEngineCanopyLiveTest {
    @Test(timeout = 30_000L)
    fun `pandaengine reaches canopy and streams resolved playback`() {
        assumeTrue(
            "Pass canopyLive=true and configure adb reverse tcp:8080 tcp:8080",
            InstrumentationRegistry.getArguments().getString("canopyLive").toBoolean()
        )

        PandaEngine.create().use { engine ->
            engine.configureBackend(connectionConfig(), isDevelopment = true)
            engine.dispatch(EngineCommand(EngineCommand.TYPE_START_SESSION, "canopy-live-test"))

            val statusResult = engine.dispatch(EngineCommand(EngineCommand.TYPE_REFRESH_BACKEND_STATUS, null))
            assertFalse(statusResult.snapshot.hasError)
            assertTrue(statusResult.snapshot.backendStatus?.healthy == true)

            val browseResult = engine.dispatch(
                EngineCommand(
                    EngineCommand.TYPE_BROWSE,
                    EngineCommandPayloads.browseCatalog(parentId = "root")
                )
            )
            assertFalse(browseResult.snapshot.hasError)
            assertTrue(browseResult.snapshot.browseResultsCount > 0)

            val track = (0 until browseResult.snapshot.browseResultsCount)
                .mapNotNull(engine::browseResult)
                .firstOrNull { item -> item.itemType == EngineCatalogItem.TYPE_TRACK }
            assertNotNull("Canopy local fixtures must expose a playable track", track)

            val playbackResult = engine.dispatch(
                EngineCommand(
                    EngineCommand.TYPE_PLAY_MEDIA_BY_ID,
                    EngineCommandPayloads.mediaId(requireNotNull(track).mediaId)
                )
            )
            assertFalse(playbackResult.snapshot.hasError)
            val sourceUri = requireNotNull(playbackResult.snapshot.sourceUri)
            assertTrue(sourceUri.startsWith("http://127.0.0.1:8080/stream/"))
            assertEquals(sourceUri, Uri.parse(sourceUri).toString())
            assertTrue(requireNotNull(playbackResult.snapshot.playbackExpiresAtEpochMillis) > 0L)
            assertTrue(playbackResult.snapshot.mimeType?.startsWith("audio/") == true)

            val connection = URL(sourceUri).openConnection() as HttpURLConnection
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.setRequestProperty("Range", "bytes=0-255")
            try {
                assertEquals(HttpURLConnection.HTTP_PARTIAL, connection.responseCode)
                assertTrue(connection.contentType.startsWith("audio/"))
                assertTrue(connection.inputStream.use { stream -> stream.read() } >= 0)
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun connectionConfig(): String = InstrumentationRegistry.getInstrumentation()
        .context.assets.open("client-connection.json")
        .bufferedReader().use { reader -> reader.readText() }
}
