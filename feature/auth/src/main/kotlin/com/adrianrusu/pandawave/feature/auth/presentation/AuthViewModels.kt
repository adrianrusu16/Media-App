package com.adrianrusu.pandawave.feature.auth.presentation

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineAuthGateway
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineGateway
import com.adrianrusu.pandawave.feature.auth.domain.AuthFormMode
import com.adrianrusu.pandawave.feature.auth.domain.AuthUiEffect
import com.adrianrusu.pandawave.feature.auth.domain.ProfileAuthEffect
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

abstract class AuthFormViewModel(
    mode: AuthFormMode,
    authGateway: EngineAuthGateway,
    engineGateway: EngineGateway
) : ViewModel() {
    private val effectChannel = Channel<AuthUiEffect>(Channel.BUFFERED)
    private val controller = AuthFlowController(
        mode = mode,
        authGateway = authGateway,
        engineGateway = engineGateway,
        scope = viewModelScope,
        deviceLabel = Build.MODEL.ifBlank { "Android device" },
        onEffect = { effectChannel.trySend(it) }
    )

    val state = controller.state
    val isAvailable = controller.isAvailable
    val effects = effectChannel.receiveAsFlow()

    fun submit(email: String, password: ByteArray) = controller.submit(email, password)

    fun resend(email: String) = controller.resend(email)

    fun cancelOrRestrict() = controller.cancelOrRestrict()

    override fun onCleared() {
        controller.close()
        effectChannel.close()
        super.onCleared()
    }
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    authGateway: EngineAuthGateway,
    engineGateway: EngineGateway
) : AuthFormViewModel(AuthFormMode.LOGIN, authGateway, engineGateway)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    authGateway: EngineAuthGateway,
    engineGateway: EngineGateway
) : AuthFormViewModel(AuthFormMode.REGISTER, authGateway, engineGateway)

@HiltViewModel
class ProfileAuthViewModel @Inject constructor(
    authGateway: EngineAuthGateway,
    engineGateway: EngineGateway
) : ViewModel() {
    private val effectChannel = Channel<ProfileAuthEffect>(Channel.BUFFERED)
    private val controller = ProfileAuthController(
        authGateway = authGateway,
        engineGateway = engineGateway,
        scope = viewModelScope,
        snapshotTimeoutMillis = LOGOUT_SNAPSHOT_TIMEOUT_MILLIS,
        onEffect = { effectChannel.trySend(it) }
    )

    val state = controller.state
    val isAvailable = controller.isAvailable
    val effects = effectChannel.receiveAsFlow()

    fun logout() = controller.logout()

    override fun onCleared() {
        controller.close()
        effectChannel.close()
        super.onCleared()
    }

    private companion object {
        const val LOGOUT_SNAPSHOT_TIMEOUT_MILLIS = 3_000L
    }
}
