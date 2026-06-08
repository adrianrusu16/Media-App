package com.adrianrusu.mediaapp.core.rust.bridge.gateway

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.IEngineListener
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.IMediaEngineService
import com.adrianrusu.mediaapp.core.rust.bridge.engine.MediaEngineServiceContract

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
            val remote = IMediaEngineService.Stub.asInterface(binder)
            remoteService = remote
            service = AidlEngineService(remote)

            try {
                remote.registerListener(remoteListener)
                listener?.onSnapshotChanged(remote.snapshot)
            } catch (_: RemoteException) {
                clearService()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            clearService()
        }

        override fun onBindingDied(name: ComponentName) {
            clearService()
        }

        override fun onNullBinding(name: ComponentName) {
            clearService()
        }
    }

    override fun connect(listener: EngineServiceListener) {
        this.listener = listener
        bound = context.bindService(
            MediaEngineServiceContract.bindIntent(context),
            serviceConnection,
            bindFlags
        )
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

    private fun clearService() {
        remoteService = null
        service = null
    }

    private class AidlEngineService(private val remote: IMediaEngineService) : EngineService {
        override fun snapshot(): EngineSnapshot = remote.snapshot

        override fun dispatch(command: EngineCommand) {
            remote.dispatch(command)
        }
    }
}
