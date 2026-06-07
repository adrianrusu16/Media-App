package com.adrianrusu.mediaapp.core.rust.bridge.engine

import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
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

        else -> current.copy(updatedAtEpochMillis = nowMillis)
    }
}
