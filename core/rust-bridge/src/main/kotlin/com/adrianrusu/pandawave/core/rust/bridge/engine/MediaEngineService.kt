package com.adrianrusu.pandawave.core.rust.bridge.engine

import android.app.Service
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.IBinder
import android.os.RemoteCallbackList
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.pandawave.core.rust.bridge.aidl.IEngineListener
import com.adrianrusu.pandawave.core.rust.bridge.aidl.IMediaEngineService
import com.adrianrusu.pandawave.core.rust.bridge.config.EngineConnectionConfigLoader

class MediaEngineService : Service() {
    private val listeners = RemoteCallbackList<IEngineListener>()
    private var engine: RustEngine? = null
    private var unavailableSnapshot: EngineSnapshot = unavailableSnapshot()

    override fun onCreate() {
        super.onCreate()
        engine = runCatching {
            val configJson = EngineConnectionConfigLoader.load(this)
            val isDevelopment = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            PandaEngineFactory.create(configJson, isDevelopment)
        }.getOrNull()
        unavailableSnapshot = unavailableSnapshot()
    }

    private val binder = object : IMediaEngineService.Stub() {
        override fun getSnapshot(): EngineSnapshot = engine?.snapshot() ?: unavailableSnapshot

        override fun getBrowseResult(index: Int): EngineCatalogItem? = engine?.browseResult(index)

        override fun getSearchResult(index: Int): EngineCatalogItem? = engine?.searchResult(index)

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

    private fun unavailableSnapshot(): EngineSnapshot = EngineSnapshot.idle(System.currentTimeMillis()).copy(
        playbackState = EngineSnapshot.PLAYBACK_ERROR,
        hasError = true,
        errorType = EngineSnapshot.ERROR_NETWORK,
        canDispatch = false
    )

}

internal fun backendUnavailableResult(snapshot: EngineSnapshot): EngineDispatchResult = EngineDispatchResult(
    snapshot = snapshot,
    event = EngineEvent(
        type = EngineEvent.TYPE_GATEWAY_UNAVAILABLE,
        message = "backend_unavailable"
    ),
    effects = emptyList()
)
