package com.adrianrusu.pandawave.core.rust.bridge.gateway

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import com.adrianrusu.pandawave.core.common.trace.PandaTrace
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
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
import com.adrianrusu.pandawave.core.rust.bridge.engine.EngineDispatchResult
import com.adrianrusu.pandawave.core.rust.bridge.engine.MediaEngineServiceContract

/**
 * Android service connection that adapts the media engine AIDL binder.
 */
class AndroidEngineServiceConnection(
    private val context: Context,
    private val bindFlags: Int = Context.BIND_AUTO_CREATE
) : EngineServiceConnection {
    @Volatile
    override var service: EngineService? = null
        private set

    private var bound = false
    private var remoteService: IMediaEngineService? = null
    private var remoteBinder: IBinder? = null
    private var deathRecipient: IBinder.DeathRecipient? = null
    private var listener: EngineServiceListener? = null

    private val remoteListener = object : IEngineListener.Stub() {
        override fun onSnapshotChanged(snapshot: EngineSnapshot) {
            listener?.onSnapshotChanged(snapshot)
        }

        override fun onEngineEvent(event: EngineEvent) {
            listener?.onEngineEvent(event)
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            PandaTrace.section("PW.Engine.Connection.onServiceConnected") {
                clearService()
                val remote = IMediaEngineService.Stub.asInterface(binder)
                val connectedService = AidlEngineService(remote)
                remoteService = remote
                remoteBinder = binder
                service = connectedService
                val recipient = IBinder.DeathRecipient { invalidate(connectedService) }
                deathRecipient = recipient

                try {
                    binder.linkToDeath(recipient, 0)
                    remote.registerListener(remoteListener)
                    listener?.onSnapshotChanged(remote.snapshot)
                    notifyEngineEvent(EngineEvent.TYPE_SERVICE_CONNECTED)
                } catch (_: RemoteException) {
                    invalidate(connectedService)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            clearService()
            notifyEngineEvent(EngineEvent.TYPE_SERVICE_DISCONNECTED)
        }

        override fun onBindingDied(name: ComponentName) {
            clearService()
            notifyEngineEvent(EngineEvent.TYPE_SERVICE_BINDING_DIED)
            rebind()
        }

        override fun onNullBinding(name: ComponentName) {
            clearService()
            notifyEngineEvent(EngineEvent.TYPE_SERVICE_NULL_BINDING)
        }
    }

    override fun connect(listener: EngineServiceListener) {
        this.listener = listener
        bind()
    }

    override fun invalidate(service: EngineService) {
        if (clearService(expectedService = service)) {
            notifyEngineEvent(EngineEvent.TYPE_SERVICE_DISCONNECTED)
            rebind()
        }
    }

    override fun close() {
        remoteService?.let { remote ->
            try {
                remote.unregisterListener(remoteListener)
            } catch (_: RemoteException) {
                // The remote process is already gone; cleanup continues locally.
            }
        }

        if (bound) {
            context.unbindService(serviceConnection)
        }

        bound = false
        listener = null
        clearService()
    }

    private fun bind() {
        bound = PandaTrace.section("PW.Engine.Connection.bind") {
            context.bindService(
                MediaEngineServiceContract.bindIntent(context),
                serviceConnection,
                bindFlags
            )
        }
    }

    private fun rebind() {
        if (bound) {
            context.unbindService(serviceConnection)
            bound = false
        }

        if (listener != null) {
            bind()
        }
    }

    private fun clearService(expectedService: EngineService? = null): Boolean {
        if (expectedService != null && service !== expectedService) {
            return false
        }

        val binder = remoteBinder
        val recipient = deathRecipient
        remoteService = null
        remoteBinder = null
        deathRecipient = null
        service = null
        if (binder != null && recipient != null) {
            try {
                binder.unlinkToDeath(recipient, 0)
            } catch (_: NoSuchElementException) {
                // The Binder has already removed the recipient after process death.
            }
        }
        return true
    }

    private fun notifyEngineEvent(type: String) {
        listener?.onEngineEvent(
            EngineEvent(
                type = type,
                message = null
            )
        )
    }

    private class AidlEngineService(private val remote: IMediaEngineService) : EngineService {
        override fun registerPassword(email: String, password: ByteArray): EngineAuthOperationResult =
            remote.registerPassword(email, password)

        override fun resendVerification(email: String): EngineAuthOperationResult = remote.resendVerification(email)

        override fun verifyEmail(verificationToken: ByteArray, deviceLabel: String): EngineAuthOperationResult =
            remote.verifyEmail(verificationToken, deviceLabel)

        override fun loginPassword(email: String, password: ByteArray, deviceLabel: String): EngineAuthOperationResult =
            remote.loginPassword(email, password, deviceLabel)

        override fun logout(): EngineAuthOperationResult = remote.logout()

        override fun snapshot(): EngineSnapshot = PandaTrace.section("PW.Engine.Binder.snapshot") {
            remote.snapshot
        }

        override fun browseResult(index: Int): EngineCatalogItem? = remote.getBrowseResult(index)
        override fun browseResultsPage(offset: Int, limit: Int) = remote.getBrowseResultsPage(offset, limit).orEmpty()
        override fun discoveryResult(index: Int): EngineCatalogItem? = remote.getDiscoveryResult(index)
        override fun forYouResult(index: Int): EngineCatalogItem? = remote.getForYouResult(index)
        override fun recommendationResult(index: Int): EngineCatalogItem? = remote.getRecommendationResult(index)
        override fun discoveryResultsPage(offset: Int, limit: Int) =
            remote.getDiscoveryResultsPage(offset, limit).orEmpty()
        override fun forYouResultsPage(offset: Int, limit: Int) = remote.getForYouResultsPage(offset, limit).orEmpty()
        override fun recommendationResultsPage(offset: Int, limit: Int) =
            remote.getRecommendationResultsPage(offset, limit).orEmpty()
        override fun profilePreferenceValue(key: String): String? = remote.getProfilePreferenceValue(key)

        override fun searchResult(index: Int): EngineCatalogItem? = remote.getSearchResult(index)
        override fun searchResultsPage(offset: Int, limit: Int) = remote.getSearchResultsPage(offset, limit).orEmpty()
        override fun historyEntry(index: Int): EngineHistoryItem? = remote.getHistoryEntry(index)
        override fun historyPage(offset: Int, limit: Int, generation: Long): EngineHistoryPage =
            remote.getHistoryPage(offset, limit, generation)
        override fun savedTrack(index: Int) = remote.getSavedTrack(index)
        override fun savedTracksPage(offset: Int, limit: Int) = remote.getSavedTracksPage(offset, limit).orEmpty()
        override fun likedTrack(index: Int) = remote.getLikedTrack(index)
        override fun likedTracksPage(offset: Int, limit: Int) = remote.getLikedTracksPage(offset, limit).orEmpty()
        override fun pendingLibraryTrackId(index: Int) = remote.getPendingLibraryTrackId(index)
        override fun pendingLibraryTrackIdsPage(offset: Int, limit: Int) =
            remote.getPendingLibraryTrackIdsPage(offset, limit).orEmpty()
        override fun playlist(index: Int): EnginePlaylistItem? = remote.getPlaylist(index)
        override fun playlistsPage(offset: Int, limit: Int) = remote.getPlaylistsPage(offset, limit).orEmpty()
        override fun playlistTrack(index: Int): EnginePlaylistTrackItem? = remote.getPlaylistTrack(index)
        override fun playlistTracksPage(offset: Int, limit: Int) = remote.getPlaylistTracksPage(offset, limit).orEmpty()
        override fun selectedPlaylistId(): String? = remote.selectedPlaylistId
        override fun playlistReconciliation(): EnginePlaylistReconciliation? = remote.playlistReconciliation

        override fun effectCount(): Int = remote.effectCount

        override fun effect(index: Int): EngineEffect? = remote.getEffect(index)

        override fun dispatch(command: EngineCommand): EngineDispatchResult =
            PandaTrace.section("PW.Engine.Binder.dispatch") {
                remote.dispatch(command)
            }

        override fun dispatchPlatformEvent(event: EnginePlatformEvent): EngineDispatchResult =
            PandaTrace.section("PW.Engine.Binder.platformEvent") {
                remote.dispatchPlatformEvent(event)
            }
    }
}
