package com.adrianrusu.pandawave.core.media.adapter.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
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

    override fun close() {
        connection?.close()
        connection = null
    }
}

internal fun interface MediaSessionControllerConnector {
    fun connect(): AutoCloseable
}

private class AndroidMediaSessionControllerConnector(private val context: Context) : MediaSessionControllerConnector {
    override fun connect(): AutoCloseable {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, BambooMediaLibraryService::class.java)
        )
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        return AutoCloseable {
            MediaController.releaseFuture(controllerFuture)
        }
    }
}
