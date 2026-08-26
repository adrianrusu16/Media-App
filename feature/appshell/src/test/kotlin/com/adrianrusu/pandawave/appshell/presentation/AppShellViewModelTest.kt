package com.adrianrusu.pandawave.appshell.presentation

import com.adrianrusu.pandawave.appshell.domain.AppShellIntent
import com.adrianrusu.pandawave.appshell.domain.AppShellRepository
import com.adrianrusu.pandawave.appshell.domain.AppShellState
import com.adrianrusu.pandawave.appshell.domain.DispatchAppShellIntentUseCase
import com.adrianrusu.pandawave.appshell.domain.ObserveAppShellStateUseCase
import com.adrianrusu.pandawave.core.telemetry.TelemetryLogger
import com.adrianrusu.pandawave.core.telemetry.TelemetryModule
import com.adrianrusu.pandawave.core.testing.RecordingTelemetrySink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AppShellViewModelTest {
    @Test
    fun `startup records an app shell breadcrumb`() {
        val repository = RecordingAppShellRepository()
        val telemetrySink = RecordingTelemetrySink()

        AppShellViewModel(
            repository = repository,
            observeState = ObserveAppShellStateUseCase(repository),
            dispatchIntent = DispatchAppShellIntentUseCase(repository),
            telemetryLogger = TelemetryLogger(telemetrySink)
        )

        assertEquals(1, repository.startCount)
        assertEquals(AppShellTelemetryEvents.STARTED, telemetrySink.events.single().name)
        assertEquals(TelemetryModule.AppShell, telemetrySink.events.single().module)
    }
}

private class RecordingAppShellRepository : AppShellRepository {
    private val mutableState = MutableStateFlow(AppShellState())
    var startCount = 0

    override val state: StateFlow<AppShellState> = mutableState

    override fun start() {
        startCount += 1
    }

    override fun dispatch(intent: AppShellIntent) = Unit

    override fun close() = Unit
}

