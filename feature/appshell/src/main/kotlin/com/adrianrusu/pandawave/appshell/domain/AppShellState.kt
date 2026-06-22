package com.adrianrusu.pandawave.appshell.domain

import com.adrianrusu.pandawave.core.playback.BambooEngineConnectionStatus
import com.adrianrusu.pandawave.core.playback.BambooEngineConnectionUiState
import com.adrianrusu.pandawave.core.ui.miniplayer.MiniPlayerState

data class AppShellState(
    val engineConnection: BambooEngineConnectionUiState = BambooEngineConnectionUiState.Connecting,
    val miniPlayer: MiniPlayerState = MiniPlayerState.Empty
) {
    val canDispatchEngineCommands: Boolean
        get() = engineConnection.status == BambooEngineConnectionStatus.Ready
}
