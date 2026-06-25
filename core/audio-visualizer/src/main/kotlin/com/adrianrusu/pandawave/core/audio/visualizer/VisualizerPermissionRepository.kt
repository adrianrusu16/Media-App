package com.adrianrusu.pandawave.core.audio.visualizer

import kotlinx.coroutines.flow.StateFlow

sealed interface VisualizerPermissionState {
    data object Unknown : VisualizerPermissionState

    data object Granted : VisualizerPermissionState

    data class Denied(val canRequest: Boolean) : VisualizerPermissionState
}

enum class VisualizerPermissionAction {
    None,
    Request,
    OpenSettings
}

val VisualizerPermissionState.recommendedAction: VisualizerPermissionAction
    get() = when (this) {
        VisualizerPermissionState.Unknown,
        VisualizerPermissionState.Granted -> VisualizerPermissionAction.None

        is VisualizerPermissionState.Denied -> if (canRequest) {
            VisualizerPermissionAction.Request
        } else {
            VisualizerPermissionAction.OpenSettings
        }
    }

fun resolveVisualizerPermissionState(
    granted: Boolean?,
    hasRequested: Boolean = false,
    shouldShowRationale: Boolean = false
): VisualizerPermissionState = when {
    granted == null -> VisualizerPermissionState.Unknown
    granted -> VisualizerPermissionState.Granted
    !hasRequested -> VisualizerPermissionState.Denied(canRequest = true)
    else -> VisualizerPermissionState.Denied(canRequest = shouldShowRationale)
}

interface VisualizerPermissionRepository {
    val state: StateFlow<VisualizerPermissionState>

    suspend fun markRequestLaunched()

    fun refresh(shouldShowRationale: Boolean)

    fun onRequestResult(granted: Boolean, shouldShowRationale: Boolean)
}
