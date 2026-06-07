package com.adrianrusu.mediaapp.appshell.data

import com.adrianrusu.mediaapp.appshell.domain.AppShellIntent
import com.adrianrusu.mediaapp.appshell.domain.AppShellReducer
import com.adrianrusu.mediaapp.appshell.domain.AppShellRepository
import com.adrianrusu.mediaapp.appshell.domain.AppShellState
import com.adrianrusu.mediaapp.appshell.domain.RestrictionUiState
import com.adrianrusu.mediaapp.core.automotive.ux.AutomotiveUxRestrictionObserver
import com.adrianrusu.mediaapp.core.automotive.ux.AutomotiveUxRestrictions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class InMemoryAppShellRepository(
    private val uxRestrictionObserver: AutomotiveUxRestrictionObserver,
) : AppShellRepository {
    private val mutableState = MutableStateFlow(AppShellState())

    override val state: StateFlow<AppShellState> = mutableState.asStateFlow()

    override fun start() {
        uxRestrictionObserver.start { restrictions ->
            mutableState.update { current ->
                current.copy(
                    restriction = restrictions.toUiState(),
                    miniPlayer = current.miniPlayer.copy(
                        isRestricted = restrictions.isRestricted,
                    ),
                )
            }
        }
    }

    override fun dispatch(intent: AppShellIntent) {
        mutableState.update { current ->
            AppShellReducer.reduce(current, intent)
        }
    }

    override fun close() {
        uxRestrictionObserver.close()
    }
}

private fun AutomotiveUxRestrictions.toUiState(): RestrictionUiState {
    val label = when (source) {
        AutomotiveUxRestrictions.Source.AutomotivePlatform ->
            if (isRestricted) "Driver-safe mode" else "Unrestricted"

        AutomotiveUxRestrictions.Source.NotAutomotive ->
            "Standard device"

        AutomotiveUxRestrictions.Source.Unavailable ->
            "Safety status unavailable"
    }

    return RestrictionUiState(
        label = label,
        isRestricted = isRestricted,
    )
}
