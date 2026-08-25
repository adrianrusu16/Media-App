package com.adrianrusu.pandawave.core.rust.bridge.engine

import android.app.Service
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.content.pm.ApplicationInfo
import android.os.IBinder
import android.os.RemoteCallbackList
import com.adrianrusu.pandawave.core.common.trace.PandaTrace
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineHistoryItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineHistoryPage
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
    @Volatile
    private var engine: RustEngine? = null
    private var unavailableSnapshot: EngineSnapshot = unavailableSnapshot()
    private var connectivityManager: ConnectivityManager? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            engine?.hintNetworkAvailability(isAvailable = true)
        }

        override fun onLost(network: Network) {
            engine?.hintNetworkAvailability(isAvailable = false)
        }
    }

    override fun onCreate() {
        super.onCreate()
        PandaTrace.section("PW.Engine.Service.onCreate") {
            engine = runCatching {
                val configJson = PandaTrace.section("PW.Engine.Service.loadConfig") {
                    EngineConnectionConfigLoader.load(this)
                }
                val isDevelopment = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
                PandaTrace.section("PW.Engine.Service.createNative") {
                    PandaEngineFactory.create(
                        configJson = configJson,
                        isDevelopment = isDevelopment,
                        sessionFile = File(noBackupFilesDir, SESSION_FILE_RELATIVE_PATH),
                        sessionProtector = AndroidKeystoreSecureSecretProtector()
                    )
                }
            }.getOrNull()
            unavailableSnapshot = unavailableSnapshot()
            if (engine != null) startNetworkHints()
        }
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

        override fun getSnapshot(): EngineSnapshot = PandaTrace.section("PW.Engine.Service.snapshot") {
            engine?.snapshot() ?: unavailableSnapshot
        }

        override fun getBrowseResult(index: Int): EngineCatalogItem? = engine?.browseResult(index)
        override fun getDiscoveryResult(index: Int): EngineCatalogItem? = engine?.discoveryResult(index)
        override fun getForYouResult(index: Int): EngineCatalogItem? = engine?.forYouResult(index)
        override fun getRecommendationResult(index: Int): EngineCatalogItem? = engine?.recommendationResult(index)
        override fun getDiscoveryResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> =
            engine?.discoveryResultsPage(offset, limit).orEmpty()
        override fun getForYouResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> =
            engine?.forYouResultsPage(offset, limit).orEmpty()
        override fun getRecommendationResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> =
            engine?.recommendationResultsPage(offset, limit).orEmpty()
        override fun getProfilePreferenceValue(key: String): String? = engine?.profilePreferenceValue(key)

        override fun getSearchResult(index: Int): EngineCatalogItem? = engine?.searchResult(index)
        override fun getHistoryEntry(index: Int): EngineHistoryItem? = engine?.historyEntry(index)
        override fun getHistoryPage(offset: Int, limit: Int, generation: Long): EngineHistoryPage {
            val current = engine ?: return EngineHistoryPage(generation, emptyList())
            val currentGeneration = current.snapshot().historyGeneration
            return EngineHistoryPage(
                generation = currentGeneration,
                items = if (currentGeneration == generation) {
                    current.historyPage(offset, limit, generation).items
                } else {
                    emptyList()
                },
            )
        }
        override fun getSavedTrack(index: Int) = engine?.savedTrack(index)
        override fun getSavedTracksPage(offset: Int, limit: Int) =
            engine?.savedTracksPage(offset, limit).orEmpty()
        override fun getLikedTrack(index: Int) = engine?.likedTrack(index)
        override fun getLikedTracksPage(offset: Int, limit: Int) =
            engine?.likedTracksPage(offset, limit).orEmpty()
        override fun getPendingLibraryTrackId(index: Int) = engine?.pendingLibraryTrackId(index)
        override fun getPlaylist(index: Int): EnginePlaylistItem? = engine?.playlist(index)
        override fun getPlaylistsPage(offset: Int, limit: Int) =
            engine?.playlistsPage(offset, limit).orEmpty()
        override fun getPlaylistTrack(index: Int): EnginePlaylistTrackItem? = engine?.playlistTrack(index)
        override fun getPlaylistTracksPage(offset: Int, limit: Int) =
            engine?.playlistTracksPage(offset, limit).orEmpty()
        override fun getSelectedPlaylistId(): String? = engine?.selectedPlaylistId()
        override fun getPlaylistReconciliation(): EnginePlaylistReconciliation? = engine?.playlistReconciliation()

        override fun getEffectCount(): Int = engine?.effectCount() ?: 0

        override fun getEffect(index: Int): EngineEffect? = engine?.effect(index)

        override fun dispatch(command: EngineCommand): EngineDispatchResult =
            PandaTrace.section("PW.Engine.Service.dispatch") {
            val result = engine?.dispatch(command)
                ?: backendUnavailableResult(unavailableSnapshot)

            notifySnapshotChanged(result.snapshot)
            notifyEngineEvent(result.event)
            result
        }

        override fun dispatchPlatformEvent(event: EnginePlatformEvent): EngineDispatchResult =
            PandaTrace.section("PW.Engine.Service.platformEvent") {
            val result = engine?.dispatchPlatformEvent(event)
                ?: backendUnavailableResult(unavailableSnapshot)

            notifySnapshotChanged(result.snapshot)
            notifyEngineEvent(result.event)
            result
        }

        override fun registerListener(listener: IEngineListener) {
            PandaTrace.section("PW.Engine.Service.registerListener") {
                listeners.register(listener)
                listener.onSnapshotChanged(engine?.snapshot() ?: unavailableSnapshot)
                listener.onEngineEvent(
                    EngineEvent(
                        type = EngineEvent.TYPE_LISTENER_REGISTERED,
                        message = null
                    )
                )
            }
        }

        override fun unregisterListener(listener: IEngineListener) {
            listeners.unregister(listener)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        stopNetworkHints()
        listeners.kill()
        (engine as? AutoCloseable)?.close()
        engine = null
        super.onDestroy()
    }

    private fun notifySnapshotChanged(snapshot: EngineSnapshot) {
        PandaTrace.section("PW.Engine.Service.notifySnapshot") {
            val count = listeners.beginBroadcast()
            try {
                for (index in 0 until count) {
                    listeners.getBroadcastItem(index).onSnapshotChanged(snapshot)
                }
            } finally {
                listeners.finishBroadcast()
            }
        }
    }

    private fun notifyEngineEvent(event: EngineEvent) {
        PandaTrace.section("PW.Engine.Service.notifyEvent") {
            val count = listeners.beginBroadcast()
            try {
                for (index in 0 until count) {
                    listeners.getBroadcastItem(index).onEngineEvent(event)
                }
            } finally {
                listeners.finishBroadcast()
            }
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

    private fun startNetworkHints() {
        engine?.startBackendHealthMonitoring(
            onDispatchResult = { result ->
                notifySnapshotChanged(result.snapshot)
                notifyEngineEvent(result.event)
            },
            onSnapshotChanged = ::notifySnapshotChanged
        )
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        try {
            connectivityManager?.registerDefaultNetworkCallback(networkCallback)
        } catch (_: SecurityException) {
            // Monitoring continues when platform connectivity hints are unavailable.
        }
    }

    private fun stopNetworkHints() {
        connectivityManager?.let { manager ->
            try {
                manager.unregisterNetworkCallback(networkCallback)
            } catch (_: IllegalArgumentException) {
                // It was never registered or was already unregistered.
            }
        }
        connectivityManager = null
        engine?.stopBackendHealthMonitoring()
    }

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
