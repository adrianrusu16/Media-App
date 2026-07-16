package com.adrianrusu.pandawave.core.rust.bridge.gateway

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.pandawave.core.rust.bridge.aidl.IEngineListener
import com.adrianrusu.pandawave.core.rust.bridge.aidl.IMediaEngineService
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
                notifyEngineEvent(EngineEvent.TYPE_SERVICE_CONNECTED)
            } catch (_: RemoteException) {
                clearService()
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
        bound = context.bindService(
            MediaEngineServiceContract.bindIntent(context),
            serviceConnection,
            bindFlags
        )
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

    private fun clearService() {
        remoteService = null
        service = null
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
        override fun registerPassword(
            email: String,
            password: ByteArray
        ): EngineAuthOperationResult = remote.registerPassword(email, password)

        override fun resendVerification(email: String): EngineAuthOperationResult =
            remote.resendVerification(email)

        override fun verifyEmail(
            verificationToken: ByteArray,
            deviceLabel: String
        ): EngineAuthOperationResult = remote.verifyEmail(verificationToken, deviceLabel)

        override fun loginPassword(
            email: String,
            password: ByteArray,
            deviceLabel: String
        ): EngineAuthOperationResult = remote.loginPassword(email, password, deviceLabel)

        override fun logout(): EngineAuthOperationResult = remote.logout()

        override fun snapshot(): EngineSnapshot = remote.snapshot

        override fun browseResult(index: Int): EngineCatalogItem? = remote.getBrowseResult(index)

        override fun searchResult(index: Int): EngineCatalogItem? = remote.getSearchResult(index)

        override fun effectCount(): Int = remote.effectCount

        override fun effect(index: Int): EngineEffect? = remote.getEffect(index)

        override fun dispatch(command: EngineCommand) {
            remote.dispatch(command)
        }

        override fun dispatchPlatformEvent(event: EnginePlatformEvent) {
            remote.dispatchPlatformEvent(event)
        }
    }
}
