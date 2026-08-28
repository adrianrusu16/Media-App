package com.adrianrusu.pandawave.core.media.adapter.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.adrianrusu.pandawave.core.common.log.PandaLog
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BambooMediaSessionWarmup internal constructor(private val connector: MediaSessionControllerConnector) :
    AutoCloseable {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        AndroidMediaSessionControllerConnector(context.applicationContext)
    )

    private var connection: AutoCloseable? = null

    fun start() {
        if (connection != null) return
        connection = connector.connect()
    }

    fun ensureRunning() {
        if (connection != null) return
        start()
    }

    fun reconnect() {
        close()
        start()
    }

    override fun close() {
        connection?.close()
        connection = null
    }
}

internal fun interface MediaSessionControllerConnector {
    fun connect(): AutoCloseable
}

private class AndroidMediaSessionControllerConnector(private val context: Context) : MediaSessionControllerConnector {
    override fun connect(): AutoCloseable = MediaSessionControllerConnection(context)
}

private class MediaSessionControllerConnection(context: Context) : AutoCloseable {
    private val sessionToken = SessionToken(
        context,
        ComponentName(context, BambooMediaLibraryService::class.java)
    )
    private val controllerFuture: ListenableFuture<MediaController> =
        MediaController.Builder(context, sessionToken).buildAsync()
    private var mediaController: MediaController? = null
    private var released = false

    init {
        Futures.addCallback(
            controllerFuture,
            object : FutureCallback<MediaController> {
                override fun onSuccess(result: MediaController) {
                    if (released) {
                        result.release()
                        return
                    }
                    mediaController = result
                    PandaLog.d(PandaLog.Tag.MEDIA) { "media_session_warmup connected" }
                }

                override fun onFailure(t: Throwable) {
                    PandaLog.w(PandaLog.Tag.MEDIA) {
                        "media_session_warmup connect_failed reason=${t.javaClass.simpleName}"
                    }
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    override fun close() {
        if (released) return
        released = true
        mediaController?.release()
        mediaController = null
        MediaController.releaseFuture(controllerFuture)
    }
}
