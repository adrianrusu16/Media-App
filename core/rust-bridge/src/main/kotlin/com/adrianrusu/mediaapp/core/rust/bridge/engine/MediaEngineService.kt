package com.adrianrusu.mediaapp.core.rust.bridge.engine

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteCallbackList
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.IEngineListener
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.IMediaEngineService

class MediaEngineService : Service() {
    private val listeners = RemoteCallbackList<IEngineListener>()

    @Volatile
    private var snapshot: EngineSnapshot =
        EngineSnapshot.idle(System.currentTimeMillis())

    private val binder = object : IMediaEngineService.Stub() {
        override fun getSnapshot(): EngineSnapshot =
            this@MediaEngineService.snapshot

        override fun dispatch(command: EngineCommand) {
            val nextSnapshot = FakeEngineReducer.reduce(
                current = this@MediaEngineService.snapshot,
                command = command,
                nowMillis = System.currentTimeMillis(),
            )
            this@MediaEngineService.snapshot = nextSnapshot

            notifySnapshotChanged(nextSnapshot)
            notifyEngineEvent(
                EngineEvent(
                    type = EngineEvent.TYPE_COMMAND_APPLIED,
                    message = command.type,
                ),
            )
        }

        override fun registerListener(listener: IEngineListener) {
            listeners.register(listener)
            listener.onSnapshotChanged(snapshot)
            listener.onEngineEvent(
                EngineEvent(
                    type = EngineEvent.TYPE_LISTENER_REGISTERED,
                    message = null,
                ),
            )
        }

        override fun unregisterListener(listener: IEngineListener) {
            listeners.unregister(listener)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        listeners.kill()
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
