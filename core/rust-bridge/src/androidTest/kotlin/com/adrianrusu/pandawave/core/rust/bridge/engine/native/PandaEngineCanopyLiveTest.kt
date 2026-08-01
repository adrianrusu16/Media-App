package com.adrianrusu.pandawave.core.rust.bridge.engine.native

import android.net.Uri
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthState
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommandPayloads
import com.adrianrusu.pandawave.core.secure.storage.keystore.AndroidKeystoreSecureSecretProtector
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PandaEngineCanopyLiveTest {
    @Test(timeout = 30_000L)
    fun `pandaengine reaches canopy and streams resolved playback`() {
        assumeCanopyLive()

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

    @Test(timeout = 60_000L)
    fun `pandaengine persists restores and clears a verified canopy session`() {
        assumeCanopyLive()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testDirectory = File(context.noBackupFilesDir, "canopy-live-auth")
        check(testDirectory.parentFile?.canonicalFile == context.noBackupFilesDir.canonicalFile)
        testDirectory.deleteRecursively()
        val sessionFile = File(testDirectory, "session.bin")
        val protector = AndroidKeystoreSecureSecretProtector()
        val email = "pandawave-${UUID.randomUUID()}@example.test"

        PandaEngine.create(sessionFile, protector).use { engine ->
            engine.configureBackend(connectionConfig(), isDevelopment = true)
            assertEquals(EngineAuthState.ANONYMOUS, engine.snapshot().authState.state)
            assertFalse(sessionFile.exists())

            val registration = engine.registerPassword(email, LIVE_AUTH_PASSWORD.encodeToByteArray())
            assertEquals(EngineAuthOperationResult.STATUS_ACCEPTED, registration.status)
            assertEquals(EngineAuthState.ANONYMOUS, engine.snapshot().authState.state)
            assertFalse(sessionFile.exists())

            val verificationToken = awaitVerificationToken(email)
            val verification = engine.verifyEmail(verificationToken, DEVICE_LABEL)
            assertEquals(
                "verification errorType=${verification.errorType}",
                EngineAuthOperationResult.STATUS_AUTHENTICATED,
                verification.status
            )
            assertTrue(sessionFile.isFile)
            assertTrue(sessionFile.length() > 0L)
            assertEquals(EngineAuthState.AUTHENTICATED, engine.snapshot().authState.state)
        }

        PandaEngine.create(sessionFile, protector).use { restoredEngine ->
            restoredEngine.configureBackend(connectionConfig(), isDevelopment = true)
            assertEquals(EngineAuthState.AUTHENTICATED, restoredEngine.snapshot().authState.state)

            val logout = restoredEngine.logout()
            assertEquals(EngineAuthOperationResult.STATUS_ANONYMOUS, logout.status)
            assertEquals(EngineAuthState.ANONYMOUS, restoredEngine.snapshot().authState.state)
            assertFalse(sessionFile.exists())

            restoredEngine.dispatch(EngineCommand(EngineCommand.TYPE_START_SESSION, "canopy-live-auth"))
            val browseResult = restoredEngine.dispatch(
                EngineCommand(
                    EngineCommand.TYPE_BROWSE,
                    EngineCommandPayloads.browseCatalog(parentId = "root")
                )
            )
            assertFalse(browseResult.snapshot.hasError)
            assertTrue(browseResult.snapshot.browseResultsCount > 0)
        }
    }

    private fun assumeCanopyLive() {
        assumeTrue(
            "Pass canopyLive=true and configure adb reverse tcp:8080 tcp:8080",
            InstrumentationRegistry.getArguments().getString("canopyLive").toBoolean()
        )
    }

    private fun awaitVerificationToken(email: String): ByteArray {
        val encodedQuery = URLEncoder.encode("to:$email", Charsets.UTF_8.name())
        val endpoint = "http://10.0.2.2:8025/view/latest.txt?query=$encodedQuery"
        val deadline = SystemClock.elapsedRealtime() + MAILPIT_TIMEOUT_MILLIS
        var lastStatus: Int? = null

        while (SystemClock.elapsedRealtime() < deadline) {
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            connection.connectTimeout = MAILPIT_REQUEST_TIMEOUT_MILLIS
            connection.readTimeout = MAILPIT_REQUEST_TIMEOUT_MILLIS
            try {
                lastStatus = connection.responseCode
                if (lastStatus == HttpURLConnection.HTTP_OK) {
                    val message = connection.inputStream.bufferedReader().use { it.readText() }
                    extractVerificationToken(message)?.let { return it.encodeToByteArray() }
                }
            } finally {
                connection.disconnect()
            }
            SystemClock.sleep(MAILPIT_POLL_INTERVAL_MILLIS)
        }

        fail("Verification message did not arrive before timeout; last HTTP status=$lastStatus")
        error("unreachable")
    }

    private fun extractVerificationToken(message: String): String? = message
        .lineSequence()
        .map(String::trim)
        .mapNotNull { line -> runCatching { Uri.parse(line) }.getOrNull() }
        .firstOrNull { uri -> uri.path?.endsWith("/verify-email") == true }
        ?.getQueryParameter("token")
        ?.takeIf(String::isNotEmpty)

    private fun connectionConfig(): String = InstrumentationRegistry.getInstrumentation()
        .context.assets.open("client-connection.json")
        .bufferedReader().use { reader -> reader.readText() }

    private companion object {
        const val DEVICE_LABEL = "PandaEmulatorNoStore"
        const val LIVE_AUTH_PASSWORD = "Canopy-Local-Test-Password-42!"
        const val MAILPIT_TIMEOUT_MILLIS = 30_000L
        const val MAILPIT_REQUEST_TIMEOUT_MILLIS = 3_000
        const val MAILPIT_POLL_INTERVAL_MILLIS = 250L
    }
}
