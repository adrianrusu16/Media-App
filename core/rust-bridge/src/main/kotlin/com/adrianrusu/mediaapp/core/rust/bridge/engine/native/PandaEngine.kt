package com.adrianrusu.mediaapp.core.rust.bridge.engine.native

import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.mediaapp.core.rust.bridge.engine.EngineDispatchResult
import com.adrianrusu.mediaapp.core.rust.bridge.engine.RustEngine

class PandaEngine private constructor(private val nativeHandle: Long, private val clock: () -> Long) :
    RustEngine,
    AutoCloseable {
    init {
        check(nativeHandle != 0L) { "PandaEngine native handle must not be zero." }
    }

    override fun snapshot(): EngineSnapshot = nativeSnapshot(nativeHandle).toEngineSnapshot()

    override fun dispatch(command: EngineCommand): EngineDispatchResult {
        val nativeValues = nativeDispatch(
            handle = nativeHandle,
            commandType = command.toNativeCommandType(),
            nowEpochMillis = clock()
        )

        return EngineDispatchResult(
            snapshot = nativeValues.toEngineSnapshot(),
            event = EngineEvent(
                type = EngineEvent.TYPE_COMMAND_APPLIED,
                message = command.type
            )
        )
    }

    override fun dispatchPlatformEvent(event: EnginePlatformEvent): EngineDispatchResult {
        val nativeValues = nativeDispatchPlatformEvent(
            handle = nativeHandle,
            eventType = event.toNativePlatformEventType(),
            payload = event.payload,
            nowEpochMillis = clock()
        )

        return EngineDispatchResult(
            snapshot = nativeValues.toEngineSnapshot(),
            event = EngineEvent(
                type = EngineEvent.TYPE_PLATFORM_EVENT_APPLIED,
                message = event.type
            )
        )
    }

    override fun close() {
        nativeDestroy(nativeHandle)
    }

    private external fun nativeSnapshot(handle: Long): LongArray

    private external fun nativeDispatch(handle: Long, commandType: Int, nowEpochMillis: Long): LongArray
    
    private external fun nativeDispatchPlatformEvent(
        handle: Long,
        eventType: Int,
        payload: String?,
        nowEpochMillis: Long
    ): LongArray

    private external fun nativeDestroy(handle: Long)

    private fun LongArray.toEngineSnapshot(): EngineSnapshot {
        require(size >= SNAPSHOT_VALUE_COUNT) {
            "Native snapshot must contain at least $SNAPSHOT_VALUE_COUNT values."
        }

        return EngineSnapshot(
            playbackState = playbackStateFromNative(this[SNAPSHOT_PLAYBACK_INDEX].toInt()),
            mediaId = null,
            title = null,
            artist = null,
            userId = null,
            restrictionState = restrictionStateFromNative(
                this[SNAPSHOT_RESTRICTION_INDEX].toInt()
            ),
            updatedAtEpochMillis = this[SNAPSHOT_UPDATED_AT_INDEX]
        )
    }

    companion object {
        fun create(clock: () -> Long = System::currentTimeMillis): PandaEngine {
            PandaEngineLibrary.load()
            return PandaEngine(
                nativeHandle = nativeCreate(clock()),
                clock = clock
            )
        }

        @JvmStatic
        private external fun nativeCreate(nowEpochMillis: Long): Long

        private const val COMMAND_BOOTSTRAP = 0
        private const val COMMAND_PLAY = 1
        private const val COMMAND_PAUSE = 2
        private const val COMMAND_SKIP_PREVIOUS = 3
        private const val COMMAND_SKIP_NEXT = 4
        private const val COMMAND_UNKNOWN = -1

        private const val PLATFORM_EVENT_APP_FOREGROUNDED = 0
        private const val PLATFORM_EVENT_APP_BACKGROUNDED = 1
        private const val PLATFORM_EVENT_SUSPEND_TO_RAM = 2
        private const val PLATFORM_EVENT_RESUME_FROM_RAM = 3
        private const val PLATFORM_EVENT_UX_RESTRICTIONS_CHANGED = 4
        private const val PLATFORM_EVENT_AUDIO_FOCUS_CHANGED = 5
        private const val PLATFORM_EVENT_MEDIA_LOADED = 6
        private const val PLATFORM_EVENT_MEDIA_ERROR = 7
        private const val PLATFORM_EVENT_UNKNOWN = -1

        private const val PLAYBACK_IDLE = 0
        private const val PLAYBACK_PLAYING = 1
        private const val PLAYBACK_PAUSED = 2
        private const val PLAYBACK_BUFFERING = 3
        private const val PLAYBACK_ERROR = 4

        private const val RESTRICTION_UNKNOWN = 0

        private const val SNAPSHOT_VALUE_COUNT = 3
        private const val SNAPSHOT_PLAYBACK_INDEX = 0
        private const val SNAPSHOT_RESTRICTION_INDEX = 1
        private const val SNAPSHOT_UPDATED_AT_INDEX = 2

        private fun EngineCommand.toNativeCommandType(): Int = when (type) {
            EngineCommand.TYPE_BOOTSTRAP -> COMMAND_BOOTSTRAP
            EngineCommand.TYPE_PLAY -> COMMAND_PLAY
            EngineCommand.TYPE_PAUSE -> COMMAND_PAUSE
            EngineCommand.TYPE_SKIP_PREVIOUS -> COMMAND_SKIP_PREVIOUS
            EngineCommand.TYPE_SKIP_NEXT -> COMMAND_SKIP_NEXT
            else -> COMMAND_UNKNOWN
        }

        private fun EnginePlatformEvent.toNativePlatformEventType(): Int = when (type) {
            EnginePlatformEvent.TYPE_APP_FOREGROUNDED -> PLATFORM_EVENT_APP_FOREGROUNDED
            EnginePlatformEvent.TYPE_APP_BACKGROUNDED -> PLATFORM_EVENT_APP_BACKGROUNDED
            EnginePlatformEvent.TYPE_SUSPEND_TO_RAM -> PLATFORM_EVENT_SUSPEND_TO_RAM
            EnginePlatformEvent.TYPE_RESUME_FROM_RAM -> PLATFORM_EVENT_RESUME_FROM_RAM
            EnginePlatformEvent.TYPE_UX_RESTRICTIONS_CHANGED -> PLATFORM_EVENT_UX_RESTRICTIONS_CHANGED
            EnginePlatformEvent.TYPE_AUDIO_FOCUS_CHANGED -> PLATFORM_EVENT_AUDIO_FOCUS_CHANGED
            EnginePlatformEvent.TYPE_MEDIA_LOADED -> PLATFORM_EVENT_MEDIA_LOADED
            EnginePlatformEvent.TYPE_MEDIA_ERROR -> PLATFORM_EVENT_MEDIA_ERROR
            else -> PLATFORM_EVENT_UNKNOWN
        }

        private fun playbackStateFromNative(value: Int): String = when (value) {
            PLAYBACK_IDLE -> EngineSnapshot.PLAYBACK_IDLE
            PLAYBACK_PLAYING -> EngineSnapshot.PLAYBACK_PLAYING
            PLAYBACK_PAUSED -> EngineSnapshot.PLAYBACK_PAUSED
            PLAYBACK_BUFFERING -> EngineSnapshot.PLAYBACK_BUFFERING
            PLAYBACK_ERROR -> EngineSnapshot.PLAYBACK_ERROR
            else -> EngineSnapshot.PLAYBACK_IDLE
        }

        private fun restrictionStateFromNative(value: Int): String = when (value) {
            RESTRICTION_UNKNOWN -> EngineSnapshot.RESTRICTION_UNKNOWN
            else -> EngineSnapshot.RESTRICTION_UNKNOWN
        }
    }
}
