package com.adrianrusu.pandawave

import android.content.ComponentName
import android.media.browse.MediaBrowser
import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adrianrusu.pandawave.core.media.adapter.playback.BambooMediaLibraryService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BambooMediaBrowserSmokeTest {
    @Test
    fun `media browser connects and loads the library root`() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val connected = CountDownLatch(1)
        val failure = AtomicReference<String?>(null)
        val rootId = AtomicReference<String?>(null)
        val browserRef = AtomicReference<MediaBrowser>()
        val handler = Handler(Looper.getMainLooper())

        handler.post {
            val browser = MediaBrowser(
                context,
                ComponentName(context, BambooMediaLibraryService::class.java),
                object : MediaBrowser.ConnectionCallback() {
                    override fun onConnected() {
                        rootId.set(browserRef.get()?.root)
                        connected.countDown()
                    }

                    override fun onConnectionFailed() {
                        failure.set("MediaBrowser connection failed")
                        connected.countDown()
                    }

                    override fun onConnectionSuspended() {
                        failure.set("MediaBrowser connection suspended")
                        connected.countDown()
                    }
                },
                null,
            )
            browserRef.set(browser)
            browser.connect()
        }

        assertTrue(
            "Timed out waiting for MediaBrowser connection",
            connected.await(20, TimeUnit.SECONDS),
        )
        assertNull(failure.get())
        assertFalse(rootId.get().isNullOrBlank())

        handler.post { browserRef.get()?.disconnect() }
    }
}
