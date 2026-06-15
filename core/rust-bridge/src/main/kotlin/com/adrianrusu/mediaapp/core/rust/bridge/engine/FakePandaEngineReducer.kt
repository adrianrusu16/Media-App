package com.adrianrusu.mediaapp.core.rust.bridge.engine

import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommandPayloads
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot

internal object FakePandaEngineReducer {
    fun reduce(current: EngineSnapshot, command: EngineCommand, nowMillis: Long): EngineSnapshot = when (command.type) {
        EngineCommand.TYPE_PLAY -> current.copy(
            playbackState = EngineSnapshot.PLAYBACK_PLAYING,
            updatedAtEpochMillis = nowMillis
        )

        EngineCommand.TYPE_PAUSE -> current.copy(
            playbackState = EngineSnapshot.PLAYBACK_PAUSED,
            updatedAtEpochMillis = nowMillis
        )

        EngineCommand.TYPE_BOOTSTRAP -> current.copy(
            updatedAtEpochMillis = nowMillis
        )

        EngineCommand.TYPE_SKIP_PREVIOUS,
        EngineCommand.TYPE_SKIP_NEXT -> current.copy(
            updatedAtEpochMillis = nowMillis
        )

        EngineCommand.TYPE_SEEK -> current.copy(
            positionMillis = EngineCommandPayloads.parseSeekPositionMillis(command.payload),
            updatedAtEpochMillis = nowMillis
        )

        EngineCommand.TYPE_SET_SPEED -> current.copy(
            playbackSpeed = EngineCommandPayloads.parsePlaybackSpeed(command.payload),
            updatedAtEpochMillis = nowMillis
        )

        EngineCommand.TYPE_SEARCH -> current.copy(
            searchResultsCount = searchResultsCountFor(EngineCommandPayloads.parseSearchQuery(command.payload)),
            updatedAtEpochMillis = nowMillis
        )

        EngineCommand.TYPE_BROWSE -> current.copy(
            browseResultsCount = browseResultsCountFor(EngineCommandPayloads.parseBrowseParentId(command.payload)),
            updatedAtEpochMillis = nowMillis
        )

        else -> current.copy(updatedAtEpochMillis = nowMillis)
    }

    fun reducePlatformEvent(current: EngineSnapshot, event: EnginePlatformEvent, nowMillis: Long): EngineSnapshot =
        when (event.type) {
            EnginePlatformEvent.TYPE_APP_FOREGROUNDED,
            EnginePlatformEvent.TYPE_APP_BACKGROUNDED,
            EnginePlatformEvent.TYPE_SUSPEND_TO_RAM,
            EnginePlatformEvent.TYPE_RESUME_FROM_RAM,
            EnginePlatformEvent.TYPE_UX_RESTRICTIONS_CHANGED -> current.copy(updatedAtEpochMillis = nowMillis)

            else -> current.copy(updatedAtEpochMillis = nowMillis)
        }

    private fun searchResultsCountFor(query: String): Int = if (query.isBlank()) {
        0
    } else {
        1
    }

    private fun browseResultsCountFor(parentId: String): Int = if (parentId ==
        EngineCommandPayloads.DEFAULT_BROWSE_PARENT_ID
    ) {
        1
    } else {
        0
    }
}
