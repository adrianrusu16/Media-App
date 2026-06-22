package com.adrianrusu.pandawave.core.rust.bridge.engine

import android.app.Service
import android.content.Intent
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

class MediaEngineService : Service() {
    private val listeners = RemoteCallbackList<IEngineListener>()
    private val engine: RustEngine = PandaEngineFactory.create(
        audioSourceResolver = AudioSourceResolvers.pandaWaveContent()
    )

    private val binder = object : IMediaEngineService.Stub() {
        override fun getSnapshot(): EngineSnapshot = engine.snapshot()

        override fun getBrowseResult(index: Int): EngineCatalogItem? = engine.browseResult(index)

        override fun getSearchResult(index: Int): EngineCatalogItem? = engine.searchResult(index)

        override fun getEffectCount(): Int = engine.effectCount()

        override fun getEffect(index: Int): EngineEffect? = engine.effect(index)

        override fun dispatch(command: EngineCommand) {
            val result = engine.dispatch(command)

            notifySnapshotChanged(result.snapshot)
            notifyEngineEvent(result.event)
        }

        override fun dispatchPlatformEvent(event: EnginePlatformEvent) {
            val result = engine.dispatchPlatformEvent(event)

            notifySnapshotChanged(result.snapshot)
            notifyEngineEvent(result.event)
        }

        override fun registerListener(listener: IEngineListener) {
            listeners.register(listener)
            listener.onSnapshotChanged(engine.snapshot())
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
}
