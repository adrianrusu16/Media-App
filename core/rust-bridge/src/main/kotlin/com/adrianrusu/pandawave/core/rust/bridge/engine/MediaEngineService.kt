package com.adrianrusu.pandawave.core.rust.bridge.engine

import android.app.Service
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.IBinder
import android.os.RemoteCallbackList
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistReconciliation
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistTrackItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.pandawave.core.rust.bridge.aidl.IEngineListener
import com.adrianrusu.pandawave.core.rust.bridge.aidl.IMediaEngineService
import com.adrianrusu.pandawave.core.rust.bridge.config.EngineConnectionConfigLoader
import com.adrianrusu.pandawave.core.secure.storage.keystore.AndroidKeystoreSecureSecretProtector
import java.io.File

class MediaEngineService : Service() {
    private val listeners = RemoteCallbackList<IEngineListener>()
    private var engine: RustEngine? = null
    private var unavailableSnapshot: EngineSnapshot = unavailableSnapshot()

    override fun onCreate() {
        super.onCreate()
        engine = runCatching {
            val configJson = EngineConnectionConfigLoader.load(this)
            val isDevelopment = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            PandaEngineFactory.create(
                configJson = configJson,
                isDevelopment = isDevelopment,
                sessionFile = File(noBackupFilesDir, SESSION_FILE_RELATIVE_PATH),
                sessionProtector = AndroidKeystoreSecureSecretProtector()
            )
        }.getOrNull()
        unavailableSnapshot = unavailableSnapshot()
    }

    private val binder = object : IMediaEngineService.Stub() {
        override fun registerPassword(
            email: String,
            password: ByteArray
        ): EngineAuthOperationResult = withSecret(password) {
            engine?.registerPassword(email, password) ?: EngineAuthOperationResult.unavailable()
        }

        override fun resendVerification(email: String): EngineAuthOperationResult =
            engine?.resendVerification(email) ?: EngineAuthOperationResult.unavailable()

        override fun verifyEmail(
            verificationToken: ByteArray,
            deviceLabel: String
        ): EngineAuthOperationResult = withSecret(verificationToken) {
            engine?.verifyEmail(verificationToken, deviceLabel)
                ?: EngineAuthOperationResult.unavailable()
        }.also { notifySnapshotChanged(engine?.snapshot() ?: unavailableSnapshot) }

        override fun loginPassword(
            email: String,
            password: ByteArray,
            deviceLabel: String
        ): EngineAuthOperationResult = withSecret(password) {
            engine?.loginPassword(email, password, deviceLabel)
                ?: EngineAuthOperationResult.unavailable()
        }.also { notifySnapshotChanged(engine?.snapshot() ?: unavailableSnapshot) }

        override fun logout(): EngineAuthOperationResult =
            (engine?.logout() ?: EngineAuthOperationResult.unavailable())
                .also { notifySnapshotChanged(engine?.snapshot() ?: unavailableSnapshot) }

        override fun getSnapshot(): EngineSnapshot = engine?.snapshot() ?: unavailableSnapshot

        override fun getBrowseResult(index: Int): EngineCatalogItem? = engine?.browseResult(index)

        override fun getSearchResult(index: Int): EngineCatalogItem? = engine?.searchResult(index)
        override fun getSavedTrack(index: Int) = engine?.savedTrack(index)
        override fun getLikedTrack(index: Int) = engine?.likedTrack(index)
        override fun getPendingLibraryTrackId(index: Int) = engine?.pendingLibraryTrackId(index)
        override fun getPlaylist(index: Int): EnginePlaylistItem? = engine?.playlist(index)
        override fun getPlaylistTrack(index: Int): EnginePlaylistTrackItem? = engine?.playlistTrack(index)
        override fun getSelectedPlaylistId(): String? = engine?.selectedPlaylistId()
        override fun getPlaylistReconciliation(): EnginePlaylistReconciliation? = engine?.playlistReconciliation()

        override fun getEffectCount(): Int = engine?.effectCount() ?: 0

        override fun getEffect(index: Int): EngineEffect? = engine?.effect(index)

        override fun dispatch(command: EngineCommand) {
            val result = engine?.dispatch(command)
                ?: backendUnavailableResult(unavailableSnapshot)

            notifySnapshotChanged(result.snapshot)
            notifyEngineEvent(result.event)
        }

        override fun dispatchPlatformEvent(event: EnginePlatformEvent) {
            val result = engine?.dispatchPlatformEvent(event)
                ?: backendUnavailableResult(unavailableSnapshot)

            notifySnapshotChanged(result.snapshot)
            notifyEngineEvent(result.event)
        }

        override fun registerListener(listener: IEngineListener) {
            listeners.register(listener)
            listener.onSnapshotChanged(engine?.snapshot() ?: unavailableSnapshot)
            listener.onEngineEvent(
                EngineEvent(
                    type = EngineEvent.TYPE_LISTENER_REGISTERED,
                    message = null
                )
            )
        }

        override fun unregisterListener(listener: IEngineListener) {
            listeners.unregister(listener)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        listeners.kill()
        (engine as? AutoCloseable)?.close()
        engine = null
        super.onDestroy()
    }

    private fun notifySnapshotChanged(snapshot: EngineSnapshot) {
        val count = listeners.beginBroadcast()
        try {
            for (index in 0 until count) {
                listeners.getBroadcastItem(index).onSnapshotChanged(snapshot)
            }
        } finally {
            listeners.finishBroadcast()
        }
    }

    private fun notifyEngineEvent(event: EngineEvent) {
        val count = listeners.beginBroadcast()
        try {
            for (index in 0 until count) {
                listeners.getBroadcastItem(index).onEngineEvent(event)
            }
        } finally {
            listeners.finishBroadcast()
        }
    }

    private inline fun withSecret(
        secret: ByteArray,
        operation: () -> EngineAuthOperationResult
    ): EngineAuthOperationResult = try {
        operation()
    } finally {
        secret.fill(0)
    }

    private fun unavailableSnapshot(): EngineSnapshot = EngineSnapshot.idle(System.currentTimeMillis()).copy(
        playbackState = EngineSnapshot.PLAYBACK_ERROR,
        hasError = true,
        errorType = EngineSnapshot.ERROR_NETWORK,
        canDispatch = false
    )

    private companion object {
        const val SESSION_FILE_RELATIVE_PATH = "panda-engine/session.bin"
    }

}

internal fun backendUnavailableResult(snapshot: EngineSnapshot): EngineDispatchResult = EngineDispatchResult(
    snapshot = snapshot,
    event = EngineEvent(
        type = EngineEvent.TYPE_GATEWAY_UNAVAILABLE,
        message = "backend_unavailable"
    ),
    effects = emptyList()
)
