package com.adrianrusu.mediaapp.core.rust.bridge.engine.native

import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineControlState
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EnginePlayerControls
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.mediaapp.core.rust.bridge.engine.EngineDispatchResult
import com.adrianrusu.mediaapp.core.rust.bridge.engine.RustEngine

class PandaEngine private constructor(private val nativeHandle: Long, private val clock: () -> Long) :
    RustEngine,
    AutoCloseable {
    private val metadataCache = NativeEngineMetadataCache(::queryNativeMetadata)

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

    private external fun nativeCurrentMediaId(handle: Long): String?

    private external fun nativeCurrentTitle(handle: Long): String?

    private external fun nativeCurrentArtist(handle: Long): String?

    private external fun nativeCurrentUserId(handle: Long): String?

    private external fun nativeDispatch(handle: Long, commandType: Int, nowEpochMillis: Long): LongArray

    private external fun nativeDispatchPlatformEvent(
        handle: Long,
        eventType: Int,
        payload: String?,
        nowEpochMillis: Long
    ): LongArray

    private external fun nativeDestroy(handle: Long)

    private fun LongArray.toEngineSnapshot(): EngineSnapshot =
        metadataCache.enrich(PandaEngineNativeSnapshotMapper.toProjection(this))

    private fun queryNativeMetadata(): NativeEngineMetadata = NativeEngineMetadata(
        mediaId = nativeCurrentMediaId(nativeHandle),
        title = nativeCurrentTitle(nativeHandle),
        artist = nativeCurrentArtist(nativeHandle),
        userId = nativeCurrentUserId(nativeHandle)
    )

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
    }
}

internal object PandaEngineNativeSnapshotMapper {
    fun toProjection(nativeValues: LongArray): NativeEngineSnapshotProjection {
        require(nativeValues.size >= SNAPSHOT_VALUE_COUNT) {
            "Native snapshot must contain at least $SNAPSHOT_VALUE_COUNT values."
        }

        return NativeEngineSnapshotProjection(
            snapshot = EngineSnapshot(
                playbackState = playbackStateFromNative(nativeValues[SNAPSHOT_PLAYBACK_INDEX].toInt()),
                mediaId = null,
                title = null,
                artist = null,
                userId = null,
                restrictionState = restrictionStateFromNative(
                    nativeValues[SNAPSHOT_RESTRICTION_INDEX].toInt()
                ),
                updatedAtEpochMillis = nativeValues[SNAPSHOT_UPDATED_AT_INDEX],
                hasActiveSession = nativeValues[SNAPSHOT_HAS_ACTIVE_SESSION_INDEX].toBoolean(),
                hasError = nativeValues[SNAPSHOT_HAS_ERROR_INDEX].toBoolean(),
                errorType = errorTypeFromNative(nativeValues[SNAPSHOT_ERROR_TYPE_INDEX].toInt()),
                searchResultsCount = nativeValues[SNAPSHOT_SEARCH_RESULTS_COUNT_INDEX].toInt(),
                playbackSpeed = Float.fromBits(nativeValues[SNAPSHOT_PLAYBACK_SPEED_BITS_INDEX].toInt()),
                positionMillis = nativeValues[SNAPSHOT_POSITION_MILLIS_INDEX],
                isBusy = nativeValues[SNAPSHOT_IS_BUSY_INDEX].toBoolean(),
                canDispatch = nativeValues[SNAPSHOT_CAN_DISPATCH_INDEX].toBoolean(),
                controls = EnginePlayerControls(
                    playPause = EngineControlState(
                        isVisible = nativeValues[SNAPSHOT_PLAY_PAUSE_VISIBLE_INDEX].toBoolean(),
                        isEnabled = nativeValues[SNAPSHOT_PLAY_PAUSE_ENABLED_INDEX].toBoolean(),
                        isActive = nativeValues[SNAPSHOT_PLAY_PAUSE_ACTIVE_INDEX].toBoolean()
                    ),
                    skipNext = EngineControlState(
                        isVisible = nativeValues[SNAPSHOT_SKIP_NEXT_VISIBLE_INDEX].toBoolean(),
                        isEnabled = nativeValues[SNAPSHOT_SKIP_NEXT_ENABLED_INDEX].toBoolean(),
                        isActive = nativeValues[SNAPSHOT_SKIP_NEXT_ACTIVE_INDEX].toBoolean()
                    ),
                    skipPrevious = EngineControlState(
                        isVisible = nativeValues[SNAPSHOT_SKIP_PREVIOUS_VISIBLE_INDEX].toBoolean(),
                        isEnabled = nativeValues[SNAPSHOT_SKIP_PREVIOUS_ENABLED_INDEX].toBoolean(),
                        isActive = nativeValues[SNAPSHOT_SKIP_PREVIOUS_ACTIVE_INDEX].toBoolean()
                    ),
                    showPlayIcon = nativeValues[SNAPSHOT_SHOW_PLAY_ICON_INDEX].toBoolean()
                ),
                hasVoiceHypothesis = nativeValues[SNAPSHOT_HAS_VOICE_HYPOTHESIS_INDEX].toBoolean(),
                browseResultsCount = nativeValues[SNAPSHOT_BROWSE_RESULTS_COUNT_INDEX].toInt()
            ),
            metadataRevision = nativeValues[SNAPSHOT_METADATA_REVISION_INDEX]
        )
    }

    fun toEngineSnapshot(nativeValues: LongArray): EngineSnapshot = toProjection(nativeValues).snapshot

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

    private fun errorTypeFromNative(value: Int): String = when (value) {
        ERROR_NONE -> EngineSnapshot.ERROR_NONE
        ERROR_NOT_FOUND -> EngineSnapshot.ERROR_NOT_FOUND
        ERROR_NETWORK -> EngineSnapshot.ERROR_NETWORK
        ERROR_PLAYER -> EngineSnapshot.ERROR_PLAYER
        ERROR_AUTHENTICATION -> EngineSnapshot.ERROR_AUTHENTICATION
        ERROR_MEDIA_SKIPPED -> EngineSnapshot.ERROR_MEDIA_SKIPPED
        else -> EngineSnapshot.ERROR_UNKNOWN
    }

    private fun Long.toBoolean(): Boolean = this != 0L

    private const val PLAYBACK_IDLE = 0
    private const val PLAYBACK_PLAYING = 1
    private const val PLAYBACK_PAUSED = 2
    private const val PLAYBACK_BUFFERING = 3
    private const val PLAYBACK_ERROR = 4

    private const val RESTRICTION_UNKNOWN = 0

    private const val ERROR_NONE = 0
    private const val ERROR_NOT_FOUND = 1
    private const val ERROR_NETWORK = 2
    private const val ERROR_PLAYER = 3
    private const val ERROR_AUTHENTICATION = 4
    private const val ERROR_MEDIA_SKIPPED = 5

    private const val SNAPSHOT_VALUE_COUNT = 24
    private const val SNAPSHOT_PLAYBACK_INDEX = 0
    private const val SNAPSHOT_RESTRICTION_INDEX = 1
    private const val SNAPSHOT_UPDATED_AT_INDEX = 2
    private const val SNAPSHOT_HAS_ACTIVE_SESSION_INDEX = 3
    private const val SNAPSHOT_HAS_ERROR_INDEX = 4
    private const val SNAPSHOT_ERROR_TYPE_INDEX = 5
    private const val SNAPSHOT_SEARCH_RESULTS_COUNT_INDEX = 6
    private const val SNAPSHOT_PLAYBACK_SPEED_BITS_INDEX = 7
    private const val SNAPSHOT_POSITION_MILLIS_INDEX = 8
    private const val SNAPSHOT_IS_BUSY_INDEX = 9
    private const val SNAPSHOT_CAN_DISPATCH_INDEX = 10
    private const val SNAPSHOT_PLAY_PAUSE_VISIBLE_INDEX = 11
    private const val SNAPSHOT_PLAY_PAUSE_ENABLED_INDEX = 12
    private const val SNAPSHOT_PLAY_PAUSE_ACTIVE_INDEX = 13
    private const val SNAPSHOT_SKIP_NEXT_VISIBLE_INDEX = 14
    private const val SNAPSHOT_SKIP_NEXT_ENABLED_INDEX = 15
    private const val SNAPSHOT_SKIP_NEXT_ACTIVE_INDEX = 16
    private const val SNAPSHOT_SKIP_PREVIOUS_VISIBLE_INDEX = 17
    private const val SNAPSHOT_SKIP_PREVIOUS_ENABLED_INDEX = 18
    private const val SNAPSHOT_SKIP_PREVIOUS_ACTIVE_INDEX = 19
    private const val SNAPSHOT_SHOW_PLAY_ICON_INDEX = 20
    private const val SNAPSHOT_HAS_VOICE_HYPOTHESIS_INDEX = 21
    private const val SNAPSHOT_BROWSE_RESULTS_COUNT_INDEX = 22
    private const val SNAPSHOT_METADATA_REVISION_INDEX = 23
}
