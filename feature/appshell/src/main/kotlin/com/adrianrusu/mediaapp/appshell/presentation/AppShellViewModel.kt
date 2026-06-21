package com.adrianrusu.mediaapp.appshell.presentation

import androidx.lifecycle.ViewModel
import com.adrianrusu.mediaapp.appshell.domain.AppShellIntent
import com.adrianrusu.mediaapp.appshell.domain.AppShellRepository
import com.adrianrusu.mediaapp.appshell.domain.DispatchAppShellIntentUseCase
import com.adrianrusu.mediaapp.appshell.domain.ObserveAppShellStateUseCase
import com.adrianrusu.mediaapp.core.telemetry.TelemetryLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AppShellViewModel @Inject constructor(
    private val repository: AppShellRepository,
    observeState: ObserveAppShellStateUseCase,
    private val dispatchIntent: DispatchAppShellIntentUseCase,
    private val telemetryLogger: TelemetryLogger
) : ViewModel() {

    val state = observeState()

    init {
        repository.start()
        telemetryLogger.info(
            name = "app_shell.started",
            attributes = mapOf("screen" to state.value.selectedDestination.name)
        )
    }

    fun onIntent(intent: AppShellIntent) {
        dispatchIntent(intent)
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }
}
