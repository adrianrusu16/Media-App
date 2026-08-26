package com.adrianrusu.pandawave.core.rust.bridge.engine.native

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthState
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineBackendAvailability
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineControlState
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlayerControls
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineThemePreference

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
                album = null,
                durationMillis = nativeValues[SNAPSHOT_DURATION_MILLIS_INDEX].takeIf { durationMillis ->
                    durationMillis >= 0L
                },
                playbackExpiresAtEpochMillis = nativeValues[SNAPSHOT_PLAYBACK_EXPIRY_INDEX]
                    .takeIf { expiry -> expiry >= 0L },
                artworkUri = null,
                userId = null,
                restrictionState = restrictionStateFromNative(
                    nativeValues[SNAPSHOT_RESTRICTION_INDEX].toInt()
                ),
                drivingState = drivingStateFromNative(nativeValues[SNAPSHOT_DRIVING_STATE_INDEX].toInt()),
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
                browseResultsCount = nativeValues[SNAPSHOT_BROWSE_RESULTS_COUNT_INDEX].toInt(),
                themePreference = EngineThemePreference(
                    themeId = themePreferenceFromNative(nativeValues[SNAPSHOT_THEME_PREFERENCE_INDEX].toInt()),
                    source = preferenceSourceFromNative(nativeValues[SNAPSHOT_PREFERENCE_SOURCE_INDEX].toInt()),
                    revision = nativeValues[SNAPSHOT_PREFERENCE_REVISION_INDEX],
                    initialized = nativeValues[SNAPSHOT_PREFERENCE_INITIALIZED_INDEX].toBoolean()
                ),
                authState = when (nativeValues[SNAPSHOT_AUTH_STATE_INDEX].toInt()) {
                    AUTH_ANONYMOUS -> EngineAuthState.anonymous()
                    AUTH_AUTHENTICATED -> EngineAuthState(EngineAuthState.AUTHENTICATED)
                    else -> EngineAuthState.loginRequired()
                },
                hasHistorySettings = nativeValues[SNAPSHOT_HAS_HISTORY_SETTINGS_INDEX].toBoolean(),
                historyEnabled = nativeValues[SNAPSHOT_HISTORY_ENABLED_INDEX].toBoolean(),
                historyDeletedCount = nativeValues[SNAPSHOT_HISTORY_DELETED_COUNT_INDEX],
                historyEntriesCount = nativeValues[SNAPSHOT_HISTORY_ENTRIES_COUNT_INDEX].toInt(),
                historyGeneration = nativeValues[SNAPSHOT_HISTORY_GENERATION_INDEX],
                savedTracksCount = nativeValues[SNAPSHOT_SAVED_TRACKS_COUNT_INDEX].toInt(),
                likedTracksCount = nativeValues[SNAPSHOT_LIKED_TRACKS_COUNT_INDEX].toInt(),
                libraryPendingCount = nativeValues[SNAPSHOT_LIBRARY_PENDING_COUNT_INDEX].toInt(),
                hasSavedTracksNextPage = nativeValues[SNAPSHOT_HAS_SAVED_NEXT_PAGE_INDEX].toBoolean(),
                hasLikedTracksNextPage = nativeValues[SNAPSHOT_HAS_LIKED_NEXT_PAGE_INDEX].toBoolean(),
                playlistsCount = nativeValues[SNAPSHOT_PLAYLISTS_COUNT_INDEX].toInt(),
                playlistTracksCount = nativeValues[SNAPSHOT_PLAYLIST_TRACKS_COUNT_INDEX].toInt(),
                hasPlaylistsNextPage = nativeValues[SNAPSHOT_HAS_PLAYLISTS_NEXT_PAGE_INDEX].toBoolean(),
                hasPlaylistTracksNextPage = nativeValues[SNAPSHOT_HAS_PLAYLIST_TRACKS_NEXT_PAGE_INDEX].toBoolean(),
                hasPlaylistReconciliation =
                    nativeValues[SNAPSHOT_HAS_PLAYLIST_RECONCILIATION_INDEX].toBoolean(),
                protectedAccount = null,
                deviceSessions = emptyList(),
                deviceSessionsCount = nativeValues[SNAPSHOT_DEVICE_SESSIONS_COUNT_INDEX].toInt(),
                hasDeviceSessionsNextPage =
                    nativeValues[SNAPSHOT_HAS_DEVICE_SESSIONS_NEXT_PAGE_INDEX].toBoolean(),
                discoveryResultsCount = nativeValues[SNAPSHOT_DISCOVERY_RESULTS_COUNT_INDEX].toInt(),
                hasDiscoveryNextPage = nativeValues[SNAPSHOT_HAS_DISCOVERY_NEXT_PAGE_INDEX].toBoolean(),
                hasHistoryNextPage = nativeValues[SNAPSHOT_HAS_HISTORY_NEXT_PAGE_INDEX].toBoolean(),
                forYouResultsCount = nativeValues[SNAPSHOT_FOR_YOU_RESULTS_COUNT_INDEX].toInt(),
                recommendationsResultsCount =
                    nativeValues[SNAPSHOT_RECOMMENDATIONS_RESULTS_COUNT_INDEX].toInt(),
                backendAvailability = backendAvailabilityFromNative(
                    nativeValues[SNAPSHOT_BACKEND_AVAILABILITY_INDEX].toInt(),
                    nativeValues[SNAPSHOT_BACKEND_UNAVAILABLE_REASON_INDEX].toInt()
                ),
                lastProgressTickEpochMillis = nativeValues[SNAPSHOT_LAST_PROGRESS_TICK_INDEX]
            ),
            metadataRevision = nativeValues[SNAPSHOT_METADATA_REVISION_INDEX],
            backendStatus = nativeValues[SNAPSHOT_HAS_BACKEND_STATUS_INDEX]
                .toBoolean()
                .takeIf { hasStatus -> hasStatus }
                ?.let {
                    NativeBackendStatusProjection(
                        healthy = nativeValues[SNAPSHOT_BACKEND_HEALTHY_INDEX].toBoolean(),
                        checkedAtEpochMillis = nativeValues[SNAPSHOT_BACKEND_CHECKED_AT_INDEX]
                            .takeIf { checkedAt -> checkedAt >= 0L },
                        dependencyCount = nativeValues[SNAPSHOT_BACKEND_DEPENDENCY_COUNT_INDEX].toInt()
                    )
                }
        )
    }

    fun toEngineSnapshot(nativeValues: LongArray): EngineSnapshot = toProjection(nativeValues).snapshot

    private fun playbackStateFromNative(value: Int): String = when (value) {
        PLAYBACK_IDLE -> EngineSnapshot.PLAYBACK_IDLE
        PLAYBACK_PLAYING -> EngineSnapshot.PLAYBACK_PLAYING
        PLAYBACK_PAUSED -> EngineSnapshot.PLAYBACK_PAUSED
        PLAYBACK_BUFFERING -> EngineSnapshot.PLAYBACK_BUFFERING
        PLAYBACK_ERROR -> EngineSnapshot.PLAYBACK_ERROR
        PLAYBACK_ENDED -> EngineSnapshot.PLAYBACK_ENDED
        PLAYBACK_RECOVERING -> EngineSnapshot.PLAYBACK_RECOVERING
        else -> EngineSnapshot.PLAYBACK_IDLE
    }

    private fun restrictionStateFromNative(value: Int): String = when (value) {
        RESTRICTION_UNKNOWN -> EngineSnapshot.RESTRICTION_UNKNOWN
        RESTRICTION_UNRESTRICTED -> EngineSnapshot.RESTRICTION_UNRESTRICTED
        RESTRICTION_RESTRICTED -> EngineSnapshot.RESTRICTION_RESTRICTED
        else -> EngineSnapshot.RESTRICTION_UNKNOWN
    }

    private fun drivingStateFromNative(value: Int): String = when (value) {
        DRIVING_PARKED -> EngineSnapshot.DRIVING_PARKED
        DRIVING_IDLING -> EngineSnapshot.DRIVING_IDLING
        DRIVING_MOVING -> EngineSnapshot.DRIVING_MOVING
        else -> EngineSnapshot.DRIVING_UNKNOWN
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

    private fun backendAvailabilityFromNative(status: Int, reason: Int): EngineBackendAvailability = when (status) {
        BACKEND_AVAILABLE -> EngineBackendAvailability(EngineBackendAvailability.AVAILABLE)

        BACKEND_UNAVAILABLE -> EngineBackendAvailability(
            EngineBackendAvailability.UNAVAILABLE,
            when (reason) {
                BACKEND_REASON_NETWORK_UNAVAILABLE -> EngineBackendAvailability.REASON_NETWORK_UNAVAILABLE
                BACKEND_REASON_TIMEOUT -> EngineBackendAvailability.REASON_TIMEOUT
                BACKEND_REASON_SERVICE_UNAVAILABLE -> EngineBackendAvailability.REASON_SERVICE_UNAVAILABLE
                else -> EngineBackendAvailability.REASON_CONNECTION_FAILED
            }
        )

        else -> EngineBackendAvailability.connecting()
    }

    private fun themePreferenceFromNative(value: Int): String = when (value) {
        THEME_BAMBOO_GROVE_LIGHT -> EngineThemePreference.THEME_BAMBOO_GROVE_LIGHT
        THEME_MOONLIT_BAMBOO_DARK -> EngineThemePreference.THEME_MOONLIT_BAMBOO_DARK
        THEME_FOREST_TECH_LIGHT -> EngineThemePreference.THEME_FOREST_TECH_LIGHT
        THEME_FOREST_TECH_DARK -> EngineThemePreference.THEME_FOREST_TECH_DARK
        else -> EngineThemePreference.THEME_SYSTEM_DEFAULT
    }

    private fun preferenceSourceFromNative(value: Int): String = when (value) {
        PREFERENCE_SOURCE_LOCAL_CACHE -> EngineThemePreference.SOURCE_LOCAL_CACHE
        PREFERENCE_SOURCE_LOCAL_USER -> EngineThemePreference.SOURCE_LOCAL_USER
        PREFERENCE_SOURCE_REMOTE_PROFILE -> EngineThemePreference.SOURCE_REMOTE_PROFILE
        else -> EngineThemePreference.SOURCE_UNINITIALIZED
    }

    private fun Long.toBoolean(): Boolean = this != 0L

    private const val PLAYBACK_IDLE = 0
    private const val PLAYBACK_PLAYING = 1
    private const val PLAYBACK_PAUSED = 2
    private const val PLAYBACK_BUFFERING = 3
    private const val PLAYBACK_ERROR = 4
    private const val PLAYBACK_ENDED = 5
    private const val PLAYBACK_RECOVERING = 6

    private const val RESTRICTION_UNKNOWN = 0
    private const val RESTRICTION_UNRESTRICTED = 1
    private const val RESTRICTION_RESTRICTED = 2

    private const val DRIVING_PARKED = 1
    private const val DRIVING_IDLING = 2
    private const val DRIVING_MOVING = 3

    private const val ERROR_NONE = 0
    private const val ERROR_NOT_FOUND = 1
    private const val ERROR_NETWORK = 2
    private const val ERROR_PLAYER = 3
    private const val ERROR_AUTHENTICATION = 4
    private const val ERROR_MEDIA_SKIPPED = 5

    private const val THEME_BAMBOO_GROVE_LIGHT = 1
    private const val THEME_MOONLIT_BAMBOO_DARK = 2
    private const val THEME_FOREST_TECH_LIGHT = 3
    private const val THEME_FOREST_TECH_DARK = 4

    private const val PREFERENCE_SOURCE_LOCAL_CACHE = 1
    private const val PREFERENCE_SOURCE_LOCAL_USER = 2
    private const val PREFERENCE_SOURCE_REMOTE_PROFILE = 3

    private const val SNAPSHOT_VALUE_COUNT = 62
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
    private const val SNAPSHOT_DURATION_MILLIS_INDEX = 24
    private const val SNAPSHOT_THEME_PREFERENCE_INDEX = 25
    private const val SNAPSHOT_PREFERENCE_SOURCE_INDEX = 26
    private const val SNAPSHOT_PREFERENCE_REVISION_INDEX = 27
    private const val SNAPSHOT_PREFERENCE_INITIALIZED_INDEX = 28
    private const val SNAPSHOT_DRIVING_STATE_INDEX = 29
    private const val SNAPSHOT_HAS_BACKEND_STATUS_INDEX = 30
    private const val SNAPSHOT_BACKEND_HEALTHY_INDEX = 31
    private const val SNAPSHOT_BACKEND_CHECKED_AT_INDEX = 32
    private const val SNAPSHOT_BACKEND_DEPENDENCY_COUNT_INDEX = 33
    private const val SNAPSHOT_PLAYBACK_EXPIRY_INDEX = 34
    private const val SNAPSHOT_AUTH_STATE_INDEX = 35
    private const val SNAPSHOT_HAS_HISTORY_SETTINGS_INDEX = 36
    private const val SNAPSHOT_HISTORY_ENABLED_INDEX = 37
    private const val SNAPSHOT_HISTORY_DELETED_COUNT_INDEX = 38
    private const val SNAPSHOT_HISTORY_ENTRIES_COUNT_INDEX = 39
    private const val SNAPSHOT_SAVED_TRACKS_COUNT_INDEX = 40
    private const val SNAPSHOT_LIKED_TRACKS_COUNT_INDEX = 41
    private const val SNAPSHOT_LIBRARY_PENDING_COUNT_INDEX = 42
    private const val SNAPSHOT_HAS_SAVED_NEXT_PAGE_INDEX = 43
    private const val SNAPSHOT_HAS_LIKED_NEXT_PAGE_INDEX = 44
    private const val SNAPSHOT_PLAYLISTS_COUNT_INDEX = 45
    private const val SNAPSHOT_PLAYLIST_TRACKS_COUNT_INDEX = 46
    private const val SNAPSHOT_HAS_PLAYLISTS_NEXT_PAGE_INDEX = 47
    private const val SNAPSHOT_HAS_PLAYLIST_TRACKS_NEXT_PAGE_INDEX = 48
    private const val SNAPSHOT_HAS_PLAYLIST_RECONCILIATION_INDEX = 49
    private const val SNAPSHOT_DEVICE_SESSIONS_COUNT_INDEX = 51
    private const val SNAPSHOT_HAS_DEVICE_SESSIONS_NEXT_PAGE_INDEX = 52
    private const val SNAPSHOT_DISCOVERY_RESULTS_COUNT_INDEX = 53
    private const val SNAPSHOT_HAS_DISCOVERY_NEXT_PAGE_INDEX = 54
    private const val SNAPSHOT_HAS_HISTORY_NEXT_PAGE_INDEX = 55
    private const val SNAPSHOT_FOR_YOU_RESULTS_COUNT_INDEX = 56
    private const val SNAPSHOT_RECOMMENDATIONS_RESULTS_COUNT_INDEX = 57
    private const val SNAPSHOT_BACKEND_AVAILABILITY_INDEX = 58
    private const val SNAPSHOT_BACKEND_UNAVAILABLE_REASON_INDEX = 59
    private const val SNAPSHOT_HISTORY_GENERATION_INDEX = 60
    private const val SNAPSHOT_LAST_PROGRESS_TICK_INDEX = 61
    private const val AUTH_ANONYMOUS = 0
    private const val AUTH_AUTHENTICATED = 1

    private const val BACKEND_CONNECTING = 0
    private const val BACKEND_AVAILABLE = 1
    private const val BACKEND_UNAVAILABLE = 2
    private const val BACKEND_REASON_NETWORK_UNAVAILABLE = 1
    private const val BACKEND_REASON_TIMEOUT = 3
    private const val BACKEND_REASON_SERVICE_UNAVAILABLE = 4
}
