package com.adrianrusu.pandawave.appshell.presentation

import androidx.lifecycle.ViewModel
import com.adrianrusu.pandawave.appshell.domain.AppShellIntent
import com.adrianrusu.pandawave.appshell.domain.AppShellRepository
import com.adrianrusu.pandawave.appshell.domain.DispatchAppShellIntentUseCase
import com.adrianrusu.pandawave.appshell.domain.ObserveAppShellStateUseCase
import com.adrianrusu.pandawave.core.common.log.PandaLog
import com.adrianrusu.pandawave.core.telemetry.TelemetryLogger
import com.adrianrusu.pandawave.core.telemetry.TelemetryModule
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AppShellViewModel @Inject constructor(
    private val repository: AppShellRepository,
    observeState: ObserveAppShellStateUseCase,
    private val dispatchIntent: DispatchAppShellIntentUseCase,
    telemetryLogger: TelemetryLogger
) : ViewModel() {
    private val logger = telemetryLogger.forModule(TelemetryModule.AppShell)

    val state = observeState()

    init {
        repository.start()
        logger.info(name = AppShellTelemetryEvents.STARTED)
    }

    fun onIntent(intent: AppShellIntent) {
        val action = when (intent) {
            AppShellIntent.TogglePlayback -> "toggle_playback"
            AppShellIntent.SkipNext -> "skip_next"
            AppShellIntent.SkipPrevious -> "skip_previous"
        }
        PandaLog.v(PandaLog.Tag.APP_SHELL) { "click action=$action section=miniplayer" }
        PandaLog.i(PandaLog.Tag.NPS) { "miniplayer_$action" }
        dispatchIntent(intent)
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }
}

internal object AppShellTelemetryEvents {
    const val STARTED = "app_shell.started"
}
