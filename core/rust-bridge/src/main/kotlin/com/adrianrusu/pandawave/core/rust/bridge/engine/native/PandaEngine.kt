package com.adrianrusu.pandawave.core.rust.bridge.engine.native

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineBackendDependencyStatus
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineBackendStatus
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAccount
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthSession
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthState
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineControlState
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineLibraryItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistTrackItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistReconciliation
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlayerControls
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineThemePreference
import com.adrianrusu.pandawave.core.rust.bridge.engine.AudioSourceResolver
import com.adrianrusu.pandawave.core.rust.bridge.engine.EngineDispatchResult
import com.adrianrusu.pandawave.core.rust.bridge.engine.RustEngine
import com.adrianrusu.pandawave.core.secure.storage.SecureSecretProtector
import java.io.File

class PandaEngine private constructor(private val nativeHandle: Long, private val clock: () -> Long) :
    RustEngine,
    AutoCloseable {
    private val metadataCache = NativeEngineMetadataCache(::queryNativeMetadata)

    init {
        check(nativeHandle != 0L) { "PandaEngine native handle must not be zero." }
    }

    override fun setAudioSourceResolver(resolver: AudioSourceResolver) {
        check(nativeSetAudioSourceResolver(nativeHandle, resolver)) {
            "PandaEngine failed to install the audio source resolver."
        }
    }

    fun configureBackend(configJson: String) {
        configureBackend(configJson, isDevelopment = false)
    }

    internal fun configureBackend(configJson: String, isDevelopment: Boolean) {
        check(nativeConfigureBackend(nativeHandle, configJson, isDevelopment)) {
            "PandaEngine backend configuration failed"
        }
    }

    override fun snapshot(): EngineSnapshot = nativeSnapshot(nativeHandle).toEngineSnapshot()

    override fun registerPassword(
        email: String,
        password: ByteArray
    ): EngineAuthOperationResult = withSecret(password) {
        nativeRegisterPassword(nativeHandle, email, password).toAuthOperationResult()
    }

    override fun resendVerification(email: String): EngineAuthOperationResult =
        nativeResendVerification(nativeHandle, email).toAuthOperationResult()

    override fun verifyEmail(
        verificationToken: ByteArray,
        deviceLabel: String
    ): EngineAuthOperationResult = withSecret(verificationToken) {
        nativeVerifyEmail(nativeHandle, verificationToken, deviceLabel).toAuthOperationResult()
    }

    override fun loginPassword(
        email: String,
        password: ByteArray,
        deviceLabel: String
    ): EngineAuthOperationResult = withSecret(password) {
        nativeLoginPassword(nativeHandle, email, password, deviceLabel).toAuthOperationResult()
    }

    override fun logout(): EngineAuthOperationResult =
        nativeLogout(nativeHandle).toAuthOperationResult()

    private inline fun withSecret(
        secret: ByteArray,
        operation: () -> EngineAuthOperationResult
    ): EngineAuthOperationResult = try {
        operation()
    } finally {
        secret.fill(0)
    }

    private fun Array<String>?.toAuthOperationResult(): EngineAuthOperationResult =
        PandaEngineNativeAuthOperationMapper.toDomain(this)

    override fun browseResult(index: Int): EngineCatalogItem? = resultItem(
        id = nativeBrowseResultId(nativeHandle, index),
        title = nativeBrowseResultTitle(nativeHandle, index),
        artist = nativeBrowseResultArtist(nativeHandle, index),
        album = nativeBrowseResultAlbum(nativeHandle, index),
        artworkUri = nativeBrowseResultArtworkUri(nativeHandle, index),
        sourceUri = nativeBrowseResultSourceUri(nativeHandle, index),
        mimeType = nativeBrowseResultMimeType(nativeHandle, index),
        itemType = nativeBrowseResultItemType(nativeHandle, index)
    )

    override fun discoveryResult(index: Int): EngineCatalogItem? = nativeDiscoveryResultValues(nativeHandle, index)
        ?.takeIf { it.size == 8 }
        ?.let { values ->
            resultItem(
                values[0], values[1], values[2], values[3].ifEmpty { null },
                values[4].ifEmpty { null }, values[5].ifEmpty { null }, values[6].ifEmpty { null },
                values[7].toIntOrNull() ?: return@let null,
            )
        }

    override fun profilePreferenceValue(key: String): String? =
        nativeProfilePreferenceValue(nativeHandle, key.trim())

    override fun savedTrack(index: Int): EngineLibraryItem? = PandaEngineNativeLibraryItemMapper.toDomain(nativeSavedTrackValues(nativeHandle, index))

    override fun likedTrack(index: Int): EngineLibraryItem? = PandaEngineNativeLibraryItemMapper.toDomain(nativeLikedTrackValues(nativeHandle, index))

    override fun pendingLibraryTrackId(index: Int): String? = nativePendingLibraryTrackId(nativeHandle, index)
    override fun playlist(index: Int): EnginePlaylistItem? = playlistItem(nativePlaylistValues(nativeHandle, index))
    override fun playlistTrack(index: Int): EnginePlaylistTrackItem? = playlistTrackItem(nativePlaylistTrackValues(nativeHandle, index))
    override fun selectedPlaylistId(): String? = nativePlaylistSelectionValues(nativeHandle)?.getOrNull(0)?.ifEmpty { null }
    override fun playlistReconciliation(): EnginePlaylistReconciliation? = playlistReconciliationItem(nativePlaylistSelectionValues(nativeHandle))

    override fun searchResult(index: Int): EngineCatalogItem? = resultItem(
        id = nativeSearchResultId(nativeHandle, index),
        title = nativeSearchResultTitle(nativeHandle, index),
        artist = nativeSearchResultArtist(nativeHandle, index),
        album = nativeSearchResultAlbum(nativeHandle, index),
        artworkUri = nativeSearchResultArtworkUri(nativeHandle, index),
        sourceUri = nativeSearchResultSourceUri(nativeHandle, index),
        mimeType = nativeSearchResultMimeType(nativeHandle, index),
        itemType = nativeSearchResultItemType(nativeHandle, index)
    )

    override fun effectCount(): Int = nativeEffectCount(nativeHandle)

    override fun effect(index: Int): EngineEffect? = effectItem(
        type = nativeEffectType(nativeHandle, index),
        mediaId = nativeEffectMediaId(nativeHandle, index),
        message = nativeEffectNotifyMessage(nativeHandle, index),
        positionMillis = nativeEffectPositionMillis(nativeHandle, index),
        speed = nativeEffectSpeed(nativeHandle, index)
    )

    override fun dispatch(command: EngineCommand): EngineDispatchResult {
        val nativeValues = nativeDispatch(
            handle = nativeHandle,
            commandType = nativeCommandType(command),
            payload = command.payload,
            nowEpochMillis = clock()
        )

        return EngineDispatchResult(
            snapshot = nativeValues.toEngineSnapshot(),
            event = EngineEvent(
                type = EngineEvent.TYPE_COMMAND_APPLIED,
                message = nativeLastEventMessage(nativeHandle) ?: command.type
            ),
            effects = effects()
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
                message = nativeLastEventMessage(nativeHandle) ?: event.type
            ),
            effects = effects()
        )
    }

    override fun close() {
        nativeDestroy(nativeHandle)
    }

    private external fun nativeSnapshot(handle: Long): LongArray

    private external fun nativeRegisterPassword(
        handle: Long,
        email: String,
        password: ByteArray
    ): Array<String>?

    private external fun nativeResendVerification(handle: Long, email: String): Array<String>?

    private external fun nativeVerifyEmail(
        handle: Long,
        verificationToken: ByteArray,
        deviceLabel: String
    ): Array<String>?

    private external fun nativeLoginPassword(
        handle: Long,
        email: String,
        password: ByteArray,
        deviceLabel: String
    ): Array<String>?

    private external fun nativeLogout(handle: Long): Array<String>?

    private external fun nativeConfigureBackend(
        handle: Long,
        configJson: String,
        isDevelopment: Boolean
    ): Boolean

    private external fun nativeInstallSessionStore(
        handle: Long,
        sessionPath: String,
        cryptor: PandaEngineSessionCryptor
    ): Boolean

    private external fun nativeCurrentMediaId(handle: Long): String?

    private external fun nativeCurrentTitle(handle: Long): String?

    private external fun nativeCurrentArtist(handle: Long): String?

    private external fun nativeCurrentAlbum(handle: Long): String?

    private external fun nativeCurrentArtworkUri(handle: Long): String?

    private external fun nativeCurrentSourceUri(handle: Long): String?

    private external fun nativeCurrentMimeType(handle: Long): String?

    private external fun nativeCurrentUserId(handle: Long): String?

    private external fun nativeLastEventMessage(handle: Long): String?

    private external fun nativeBackendStatusValues(handle: Long): Array<String>?

    private external fun nativeAuthStateValues(handle: Long): Array<String>?

    private external fun nativeProfileValues(handle: Long): Array<String>?
    private external fun nativeProtectedAccountValues(handle: Long): Array<String>?
    private external fun nativeDeviceSessionValues(handle: Long, index: Int): Array<String>?
    private external fun nativeSavedTrackValues(handle: Long, index: Int): Array<String>?
    private external fun nativeDiscoveryResultValues(handle: Long, index: Int): Array<String>?
    private external fun nativeProfilePreferenceValue(handle: Long, key: String): String?
    private external fun nativeLikedTrackValues(handle: Long, index: Int): Array<String>?
    private external fun nativePendingLibraryTrackId(handle: Long, index: Int): String?
    private external fun nativePlaylistValues(handle: Long, index: Int): Array<String>?
    private external fun nativePlaylistTrackValues(handle: Long, index: Int): Array<String>?
    private external fun nativePlaylistSelectionValues(handle: Long): Array<String>?

    private external fun nativeEffectCount(handle: Long): Int

    private external fun nativeEffectType(handle: Long, index: Int): Int

    private external fun nativeEffectMediaId(handle: Long, index: Int): String?

    private external fun nativeEffectNotifyMessage(handle: Long, index: Int): String?

    private external fun nativeEffectPositionMillis(handle: Long, index: Int): Long

    private external fun nativeEffectSpeed(handle: Long, index: Int): Float

    private external fun nativeSearchResultId(handle: Long, index: Int): String?

    private external fun nativeSearchResultTitle(handle: Long, index: Int): String?

    private external fun nativeSearchResultArtist(handle: Long, index: Int): String?

    private external fun nativeSearchResultAlbum(handle: Long, index: Int): String?

    private external fun nativeSearchResultArtworkUri(handle: Long, index: Int): String?

    private external fun nativeSearchResultSourceUri(handle: Long, index: Int): String?

    private external fun nativeSearchResultMimeType(handle: Long, index: Int): String?

    private external fun nativeSearchResultItemType(handle: Long, index: Int): Int

    private external fun nativeBrowseResultId(handle: Long, index: Int): String?

    private external fun nativeBrowseResultTitle(handle: Long, index: Int): String?

    private external fun nativeBrowseResultArtist(handle: Long, index: Int): String?

    private external fun nativeBrowseResultAlbum(handle: Long, index: Int): String?

    private external fun nativeBrowseResultArtworkUri(handle: Long, index: Int): String?

    private external fun nativeBrowseResultSourceUri(handle: Long, index: Int): String?

    private external fun nativeBrowseResultMimeType(handle: Long, index: Int): String?

    private external fun nativeBrowseResultItemType(handle: Long, index: Int): Int

    private external fun nativeDispatch(
        handle: Long,
        commandType: Int,
        payload: String?,
        nowEpochMillis: Long
    ): LongArray

    private external fun nativeDispatchPlatformEvent(
        handle: Long,
        eventType: Int,
        payload: String?,
        nowEpochMillis: Long
    ): LongArray

    private external fun nativeDestroy(handle: Long)

    private external fun nativeSetAudioSourceResolver(handle: Long, resolver: AudioSourceResolver): Boolean

    private fun LongArray.toEngineSnapshot(): EngineSnapshot {
        val projection = PandaEngineNativeSnapshotMapper.toProjection(this)
        val snapshot = metadataCache.enrich(projection)
        val backendStatus = projection.backendStatus?.let {
            nativeBackendStatusValues(nativeHandle)?.let(PandaEngineNativeBackendStatusMapper::toDomain)
        }
        val authState = nativeAuthStateValues(nativeHandle)
            ?.let(PandaEngineNativeAuthStateMapper::toDomain)
            ?: EngineAuthState.loginRequired()
        val profile = PandaEngineNativeProfileMapper.toDomain(nativeProfileValues(nativeHandle))
        val protectedAccount = PandaEngineNativeAuthStateMapper.toAccount(nativeProtectedAccountValues(nativeHandle))
        val deviceSessions = List(projection.snapshot.deviceSessionsCount) { index -> PandaEngineNativeAuthStateMapper.toSession(nativeDeviceSessionValues(nativeHandle, index)) }.filterNotNull()
        return snapshot.copy(
            backendStatus = backendStatus,
            authState = authState,
            profile = profile,
            protectedAccount = protectedAccount,
            deviceSessions = deviceSessions
        )
    }

    private fun queryNativeMetadata(): NativeEngineMetadata = NativeEngineMetadata(
        mediaId = nativeCurrentMediaId(nativeHandle),
        title = nativeCurrentTitle(nativeHandle),
        artist = nativeCurrentArtist(nativeHandle),
        album = nativeCurrentAlbum(nativeHandle),
        artworkUri = nativeCurrentArtworkUri(nativeHandle),
        sourceUri = nativeCurrentSourceUri(nativeHandle),
        mimeType = nativeCurrentMimeType(nativeHandle),
        userId = nativeCurrentUserId(nativeHandle)
    )

    private fun resultItem(
        id: String?,
        title: String?,
        artist: String?,
        album: String?,
        artworkUri: String?,
        sourceUri: String?,
        mimeType: String?,
        itemType: Int
    ): EngineCatalogItem? = when {
        id.isNullOrBlank() || title.isNullOrBlank() -> null

        else -> EngineCatalogItem(
            mediaId = id,
            title = title,
            artist = artist.takeUnless { value -> value.isNullOrBlank() },
            album = album.takeUnless { value -> value.isNullOrBlank() },
            artworkUri = artworkUri.takeUnless { value -> value.isNullOrBlank() },
            sourceUri = sourceUri.takeUnless { value -> value.isNullOrBlank() },
            mimeType = mimeType.takeUnless { value -> value.isNullOrBlank() },
            itemType = itemType
        )
    }

    private fun effects(): List<EngineEffect> = List(
        size = effectCount(),
        init = ::effect
    ).filterNotNull()

    private fun effectItem(
        type: Int,
        mediaId: String?,
        message: String?,
        positionMillis: Long,
        speed: Float
    ): EngineEffect? {
        val effectType = type.toEngineEffectType()
        return when (effectType) {
            EngineEffect.TYPE_UNKNOWN -> null

            else -> EngineEffect(
                type = effectType,
                mediaId = mediaId.takeUnless { value -> value.isNullOrBlank() },
                message = message.takeUnless { value -> value.isNullOrBlank() },
                positionMillis = positionMillis.takeUnless { value -> value < 0L },
                speed = speed.takeUnless { value -> value.isNaN() }
            )
        }
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
        private const val COMMAND_START_SESSION = 5
        private const val COMMAND_END_SESSION = 6
        private const val COMMAND_SEARCH = 7
        private const val COMMAND_BROWSE = 8
        private const val COMMAND_SET_SPEED = 9
        private const val COMMAND_SEEK = 10
        private const val COMMAND_PLAY_MEDIA_BY_ID = 14
        private const val COMMAND_HYDRATE_THEME_PREFERENCE = 15
        private const val COMMAND_SET_THEME_PREFERENCE = 16
        private const val COMMAND_APPLY_REMOTE_THEME_PREFERENCE = 17
        private const val COMMAND_REFRESH_BACKEND_STATUS = 18
        private const val COMMAND_LOAD_NEXT_CATALOG_PAGE = 19
        private const val COMMAND_UPSERT_PROFILE = 20
        private const val COMMAND_GET_PROFILE = 21
        private const val COMMAND_UPDATE_PROFILE = 22
        private const val COMMAND_DELETE_PROFILE = 23
        private const val COMMAND_LOAD_PROFILE_PREFERENCES = 24
        private const val COMMAND_UPDATE_PROFILE_PREFERENCES = 25
        private const val COMMAND_LOAD_HISTORY_SETTINGS = 26
        private const val COMMAND_UPDATE_HISTORY_SETTINGS = 27
        private const val COMMAND_LIST_HISTORY = 28
        private const val COMMAND_LOAD_NEXT_HISTORY_PAGE = 29
        private const val COMMAND_DELETE_HISTORY_ENTRY = 30
        private const val COMMAND_CLEAR_HISTORY = 31
        private const val COMMAND_SAVE_TRACK = 32
        private const val COMMAND_REMOVE_SAVED_TRACK = 33
        private const val COMMAND_LIST_SAVED_TRACKS = 34
        private const val COMMAND_LOAD_NEXT_SAVED_TRACKS_PAGE = 35
        private const val COMMAND_LIKE_TRACK = 36
        private const val COMMAND_UNLIKE_TRACK = 37
        private const val COMMAND_LIST_LIKED_TRACKS = 38
        private const val COMMAND_LOAD_NEXT_LIKED_TRACKS_PAGE = 39
        private const val COMMAND_CREATE_PLAYLIST = 40
        private const val COMMAND_UPDATE_PLAYLIST = 41
        private const val COMMAND_DELETE_PLAYLIST = 42
        private const val COMMAND_LIST_PLAYLISTS = 43
        private const val COMMAND_LOAD_NEXT_PLAYLISTS_PAGE = 44
        private const val COMMAND_ADD_PLAYLIST_TRACK = 45
        private const val COMMAND_REMOVE_PLAYLIST_TRACK = 46
        private const val COMMAND_LIST_PLAYLIST_TRACKS = 47
        private const val COMMAND_LOAD_NEXT_PLAYLIST_TRACKS_PAGE = 48
        private const val COMMAND_REORDER_PLAYLIST_TRACKS = 49
        private const val COMMAND_GET_ACCOUNT = 50
        private const val COMMAND_DELETE_ACCOUNT = 51
        private const val COMMAND_LIST_DEVICE_SESSIONS = 52
        private const val COMMAND_LOAD_NEXT_DEVICE_SESSIONS_PAGE = 53
        private const val COMMAND_REVOKE_DEVICE_SESSION = 54
        private const val COMMAND_LOAD_DISCOVERY_FEED = 55
        private const val COMMAND_LOAD_NEXT_DISCOVERY_PAGE = 56
        private const val COMMAND_UNKNOWN = -1

        private const val PLATFORM_EVENT_APP_FOREGROUNDED = 0
        private const val PLATFORM_EVENT_APP_BACKGROUNDED = 1
        private const val PLATFORM_EVENT_SUSPEND_TO_RAM = 2
        private const val PLATFORM_EVENT_RESUME_FROM_RAM = 3
        private const val PLATFORM_EVENT_UX_RESTRICTIONS_CHANGED = 4
        private const val PLATFORM_EVENT_AUDIO_FOCUS_CHANGED = 5
        private const val PLATFORM_EVENT_MEDIA_LOADED = 6
        private const val PLATFORM_EVENT_MEDIA_ERROR = 7
        private const val PLATFORM_EVENT_VEHICLE_DRIVING_STATE_CHANGED = 8
        private const val PLATFORM_EVENT_PLAYBACK_COMPLETED = 9
        private const val PLATFORM_EVENT_UNKNOWN = -1

        private const val EFFECT_PLAY = 0
        private const val EFFECT_PAUSE = 1
        private const val EFFECT_STOP = 2
        private const val EFFECT_SEEK = 3
        private const val EFFECT_REQUEST_AUDIO_FOCUS = 4
        private const val EFFECT_ABANDON_AUDIO_FOCUS = 5
        private const val EFFECT_UPDATE_METADATA = 6
        private const val EFFECT_SESSION_STARTED = 7
        private const val EFFECT_SESSION_ENDED = 8
        private const val EFFECT_SET_SPEED = 9
        private const val EFFECT_NOTIFY_USER = 10
        private const val EFFECT_START_AUDIO_CAPTURE = 11
        private const val EFFECT_STOP_AUDIO_CAPTURE = 12
        private const val EFFECT_DUCK_AUDIO = 13
        private const val EFFECT_UNDUCK_AUDIO = 14
        private const val EFFECT_PREPARE_PLAYBACK_SOURCE = 15

        private fun EngineCommand.toNativeCommandType(): Int = when (type) {
            EngineCommand.TYPE_BOOTSTRAP -> COMMAND_BOOTSTRAP
            EngineCommand.TYPE_PLAY -> COMMAND_PLAY
            EngineCommand.TYPE_PAUSE -> COMMAND_PAUSE
            EngineCommand.TYPE_SKIP_PREVIOUS -> COMMAND_SKIP_PREVIOUS
            EngineCommand.TYPE_SKIP_NEXT -> COMMAND_SKIP_NEXT
            EngineCommand.TYPE_START_SESSION -> COMMAND_START_SESSION
            EngineCommand.TYPE_END_SESSION -> COMMAND_END_SESSION
            EngineCommand.TYPE_SEARCH -> COMMAND_SEARCH
            EngineCommand.TYPE_BROWSE -> COMMAND_BROWSE
            EngineCommand.TYPE_SET_SPEED -> COMMAND_SET_SPEED
            EngineCommand.TYPE_SEEK -> COMMAND_SEEK
            EngineCommand.TYPE_PLAY_MEDIA_BY_ID -> COMMAND_PLAY_MEDIA_BY_ID
            EngineCommand.TYPE_HYDRATE_THEME_PREFERENCE -> COMMAND_HYDRATE_THEME_PREFERENCE
            EngineCommand.TYPE_SET_THEME_PREFERENCE -> COMMAND_SET_THEME_PREFERENCE
            EngineCommand.TYPE_APPLY_REMOTE_THEME_PREFERENCE -> COMMAND_APPLY_REMOTE_THEME_PREFERENCE
            EngineCommand.TYPE_REFRESH_BACKEND_STATUS -> COMMAND_REFRESH_BACKEND_STATUS
            EngineCommand.TYPE_LOAD_NEXT_CATALOG_PAGE -> COMMAND_LOAD_NEXT_CATALOG_PAGE
            EngineCommand.TYPE_UPSERT_PROFILE -> COMMAND_UPSERT_PROFILE
            EngineCommand.TYPE_GET_PROFILE -> COMMAND_GET_PROFILE
            EngineCommand.TYPE_UPDATE_PROFILE -> COMMAND_UPDATE_PROFILE
            EngineCommand.TYPE_DELETE_PROFILE -> COMMAND_DELETE_PROFILE
            EngineCommand.TYPE_LOAD_PROFILE_PREFERENCES -> COMMAND_LOAD_PROFILE_PREFERENCES
            EngineCommand.TYPE_UPDATE_PROFILE_PREFERENCES -> COMMAND_UPDATE_PROFILE_PREFERENCES
            EngineCommand.TYPE_LOAD_HISTORY_SETTINGS -> COMMAND_LOAD_HISTORY_SETTINGS
            EngineCommand.TYPE_UPDATE_HISTORY_SETTINGS -> COMMAND_UPDATE_HISTORY_SETTINGS
            EngineCommand.TYPE_LIST_HISTORY -> COMMAND_LIST_HISTORY
            EngineCommand.TYPE_LOAD_NEXT_HISTORY_PAGE -> COMMAND_LOAD_NEXT_HISTORY_PAGE
            EngineCommand.TYPE_DELETE_HISTORY_ENTRY -> COMMAND_DELETE_HISTORY_ENTRY
            EngineCommand.TYPE_CLEAR_HISTORY -> COMMAND_CLEAR_HISTORY
            EngineCommand.TYPE_SAVE_TRACK -> COMMAND_SAVE_TRACK
            EngineCommand.TYPE_REMOVE_SAVED_TRACK -> COMMAND_REMOVE_SAVED_TRACK
            EngineCommand.TYPE_LIST_SAVED_TRACKS -> COMMAND_LIST_SAVED_TRACKS
            EngineCommand.TYPE_LOAD_NEXT_SAVED_TRACKS_PAGE -> COMMAND_LOAD_NEXT_SAVED_TRACKS_PAGE
            EngineCommand.TYPE_LIKE_TRACK -> COMMAND_LIKE_TRACK
            EngineCommand.TYPE_UNLIKE_TRACK -> COMMAND_UNLIKE_TRACK
            EngineCommand.TYPE_LIST_LIKED_TRACKS -> COMMAND_LIST_LIKED_TRACKS
            EngineCommand.TYPE_LOAD_NEXT_LIKED_TRACKS_PAGE -> COMMAND_LOAD_NEXT_LIKED_TRACKS_PAGE
            EngineCommand.TYPE_CREATE_PLAYLIST -> COMMAND_CREATE_PLAYLIST
            EngineCommand.TYPE_UPDATE_PLAYLIST -> COMMAND_UPDATE_PLAYLIST
            EngineCommand.TYPE_DELETE_PLAYLIST -> COMMAND_DELETE_PLAYLIST
            EngineCommand.TYPE_LIST_PLAYLISTS -> COMMAND_LIST_PLAYLISTS
            EngineCommand.TYPE_LOAD_NEXT_PLAYLISTS_PAGE -> COMMAND_LOAD_NEXT_PLAYLISTS_PAGE
            EngineCommand.TYPE_ADD_PLAYLIST_TRACK -> COMMAND_ADD_PLAYLIST_TRACK
            EngineCommand.TYPE_REMOVE_PLAYLIST_TRACK -> COMMAND_REMOVE_PLAYLIST_TRACK
            EngineCommand.TYPE_LIST_PLAYLIST_TRACKS -> COMMAND_LIST_PLAYLIST_TRACKS
            EngineCommand.TYPE_LOAD_NEXT_PLAYLIST_TRACKS_PAGE -> COMMAND_LOAD_NEXT_PLAYLIST_TRACKS_PAGE
            EngineCommand.TYPE_REORDER_PLAYLIST_TRACKS -> COMMAND_REORDER_PLAYLIST_TRACKS
            EngineCommand.TYPE_GET_ACCOUNT -> COMMAND_GET_ACCOUNT
            EngineCommand.TYPE_DELETE_ACCOUNT -> COMMAND_DELETE_ACCOUNT
            EngineCommand.TYPE_LIST_DEVICE_SESSIONS -> COMMAND_LIST_DEVICE_SESSIONS
            EngineCommand.TYPE_LOAD_NEXT_DEVICE_SESSIONS_PAGE -> COMMAND_LOAD_NEXT_DEVICE_SESSIONS_PAGE
            EngineCommand.TYPE_REVOKE_DEVICE_SESSION -> COMMAND_REVOKE_DEVICE_SESSION
            EngineCommand.TYPE_LOAD_DISCOVERY_FEED -> COMMAND_LOAD_DISCOVERY_FEED
            EngineCommand.TYPE_LOAD_NEXT_DISCOVERY_PAGE -> COMMAND_LOAD_NEXT_DISCOVERY_PAGE
            else -> COMMAND_UNKNOWN
        }

        fun create(
            sessionFile: File,
            sessionProtector: SecureSecretProtector,
            clock: () -> Long = System::currentTimeMillis
        ): PandaEngine {
            require(sessionFile.isAbsolute) { "PandaEngine session file must be absolute." }
            val engine = create(clock)
            return try {
                check(
                    engine.nativeInstallSessionStore(
                        handle = engine.nativeHandle,
                        sessionPath = sessionFile.absolutePath,
                        cryptor = PandaEngineSessionCryptor(sessionProtector)
                    )
                ) { "PandaEngine failed to install secure session storage." }
                engine
            } catch (error: Throwable) {
                engine.close()
                throw error
            }
        }

        internal fun nativeCommandType(command: EngineCommand): Int = command.toNativeCommandType()

        internal fun playlistItem(values: Array<String>?): EnginePlaylistItem? {
            if (values == null || values.size != 7) return null
            val revision = values[3].toNonNegativeLongOrNull() ?: return null
            val createdAt = values[4].toNonNegativeLongOrNull() ?: return null
            val updatedAt = values[5].toNonNegativeLongOrNull() ?: return null
            val description = when (values[6]) {
                "0" -> null
                "1" -> values[2]
                else -> return null
            }
            return EnginePlaylistItem(values[0], values[1], description, revision, createdAt, updatedAt)
        }

        internal fun playlistTrackItem(values: Array<String>?): EnginePlaylistTrackItem? {
            if (values == null || values.size != 12) return null
            val duration = values[7].toNonNegativeLongOrNull() ?: return null
            val explicit = when (values[8]) {
                "0" -> false
                "1" -> true
                else -> return null
            }
            val position = values[10].toUIntOrNull()?.takeIf { it <= Int.MAX_VALUE.toUInt() }?.toInt()
                ?: return null
            val addedAt = values[11].toNonNegativeLongOrNull() ?: return null
            return EnginePlaylistTrackItem(
                values[0], values[1], values[2], values[3], values[4], values[5],
                values[6].ifEmpty { null }, duration, explicit, values[9].ifEmpty { null }, position, addedAt,
            )
        }

        internal fun playlistReconciliationItem(values: Array<String>?): EnginePlaylistReconciliation? {
            if (values == null || values.size != 6 || values[1].isEmpty()) return null
            val expectedRevision = values[2].toNonNegativeLongOrNull() ?: return null
            val serverRevision = values[3].toNonNegativeLongOrNull() ?: return null
            return EnginePlaylistReconciliation(
                values[1], expectedRevision, serverRevision,
                values[4].split('\u001f').filter(String::isNotEmpty),
                values[5].split('\u001f').filter(String::isNotEmpty),
            )
        }

        private fun String.toNonNegativeLongOrNull(): Long? =
            toULongOrNull()?.takeIf { it <= Long.MAX_VALUE.toULong() }?.toLong()

        private fun EnginePlatformEvent.toNativePlatformEventType(): Int = when (type) {
            EnginePlatformEvent.TYPE_APP_FOREGROUNDED -> PLATFORM_EVENT_APP_FOREGROUNDED
            EnginePlatformEvent.TYPE_APP_BACKGROUNDED -> PLATFORM_EVENT_APP_BACKGROUNDED
            EnginePlatformEvent.TYPE_SUSPEND_TO_RAM -> PLATFORM_EVENT_SUSPEND_TO_RAM
            EnginePlatformEvent.TYPE_RESUME_FROM_RAM -> PLATFORM_EVENT_RESUME_FROM_RAM
            EnginePlatformEvent.TYPE_UX_RESTRICTIONS_CHANGED -> PLATFORM_EVENT_UX_RESTRICTIONS_CHANGED
            EnginePlatformEvent.TYPE_AUDIO_FOCUS_CHANGED -> PLATFORM_EVENT_AUDIO_FOCUS_CHANGED
            EnginePlatformEvent.TYPE_MEDIA_LOADED -> PLATFORM_EVENT_MEDIA_LOADED
            EnginePlatformEvent.TYPE_MEDIA_ERROR -> PLATFORM_EVENT_MEDIA_ERROR
            EnginePlatformEvent.TYPE_VEHICLE_DRIVING_STATE_CHANGED -> PLATFORM_EVENT_VEHICLE_DRIVING_STATE_CHANGED
            EnginePlatformEvent.TYPE_PLAYBACK_COMPLETED -> PLATFORM_EVENT_PLAYBACK_COMPLETED
            else -> PLATFORM_EVENT_UNKNOWN
        }

        private fun Int.toEngineEffectType(): String = when (this) {
            EFFECT_PLAY -> EngineEffect.TYPE_PLAY
            EFFECT_PAUSE -> EngineEffect.TYPE_PAUSE
            EFFECT_STOP -> EngineEffect.TYPE_STOP
            EFFECT_SEEK -> EngineEffect.TYPE_SEEK
            EFFECT_REQUEST_AUDIO_FOCUS -> EngineEffect.TYPE_REQUEST_AUDIO_FOCUS
            EFFECT_ABANDON_AUDIO_FOCUS -> EngineEffect.TYPE_ABANDON_AUDIO_FOCUS
            EFFECT_UPDATE_METADATA -> EngineEffect.TYPE_UPDATE_METADATA
            EFFECT_SESSION_STARTED -> EngineEffect.TYPE_SESSION_STARTED
            EFFECT_SESSION_ENDED -> EngineEffect.TYPE_SESSION_ENDED
            EFFECT_SET_SPEED -> EngineEffect.TYPE_SET_SPEED
            EFFECT_NOTIFY_USER -> EngineEffect.TYPE_NOTIFY_USER
            EFFECT_START_AUDIO_CAPTURE -> EngineEffect.TYPE_START_AUDIO_CAPTURE
            EFFECT_STOP_AUDIO_CAPTURE -> EngineEffect.TYPE_STOP_AUDIO_CAPTURE
            EFFECT_DUCK_AUDIO -> EngineEffect.TYPE_DUCK_AUDIO
            EFFECT_UNDUCK_AUDIO -> EngineEffect.TYPE_UNDUCK_AUDIO
            EFFECT_PREPARE_PLAYBACK_SOURCE -> EngineEffect.TYPE_PREPARE_PLAYBACK_SOURCE
            else -> EngineEffect.TYPE_UNKNOWN
        }
    }
}

internal object PandaEngineNativeAuthOperationMapper {
    fun toDomain(values: Array<String>?): EngineAuthOperationResult {
        if (values == null || values.size != VALUE_COUNT) {
            return EngineAuthOperationResult.error(EngineAuthOperationResult.ERROR_MAPPING_DEFECT)
        }
        return runCatching {
            when (values[STATUS_INDEX]) {
                EngineAuthOperationResult.STATUS_ACCEPTED -> EngineAuthOperationResult.accepted()
                EngineAuthOperationResult.STATUS_REJECTED -> EngineAuthOperationResult.rejected()
                EngineAuthOperationResult.STATUS_AUTHENTICATED ->
                    EngineAuthOperationResult.authenticated()
                EngineAuthOperationResult.STATUS_ANONYMOUS -> EngineAuthOperationResult.anonymous()
                EngineAuthOperationResult.STATUS_ERROR -> EngineAuthOperationResult.error(
                    errorType = values[ERROR_INDEX].ifBlank {
                        EngineAuthOperationResult.ERROR_UNKNOWN
                    },
                    retryAfterMillis = values[RETRY_INDEX].takeIf(String::isNotBlank)?.toLong()
                )
                else -> EngineAuthOperationResult.error(
                    EngineAuthOperationResult.ERROR_MAPPING_DEFECT
                )
            }
        }.getOrElse {
            EngineAuthOperationResult.error(EngineAuthOperationResult.ERROR_MAPPING_DEFECT)
        }
    }

    private const val VALUE_COUNT = 3
    private const val STATUS_INDEX = 0
    private const val ERROR_INDEX = 1
    private const val RETRY_INDEX = 2
}

internal object PandaEngineNativeBackendStatusMapper {
    fun toDomain(values: Array<String>): EngineBackendStatus {
        require(values.size >= HEADER_VALUE_COUNT) { "Native backend status header is incomplete." }
        val dependencyCount = values[DEPENDENCY_COUNT_INDEX].toInt()
        require(dependencyCount >= 0 && values.size == HEADER_VALUE_COUNT + dependencyCount * 3) {
            "Native backend dependency values are incomplete."
        }

        return EngineBackendStatus(
            healthy = when (values[HEALTHY_INDEX]) {
                "1" -> true
                "0" -> false
                else -> error("Native backend health value is invalid.")
            },
            version = values[VERSION_INDEX],
            status = values[STATUS_INDEX],
            checkedAtEpochMillis = values[CHECKED_AT_INDEX].takeIf(String::isNotEmpty)?.toLong(),
            dependencies = List(dependencyCount) { index ->
                val offset = HEADER_VALUE_COUNT + index * 3
                EngineBackendDependencyStatus(
                    name = values[offset],
                    status = values[offset + 1],
                    message = values[offset + 2]
                )
            }
        )
    }

    private const val HEALTHY_INDEX = 0
    private const val VERSION_INDEX = 1
    private const val STATUS_INDEX = 2
    private const val CHECKED_AT_INDEX = 3
    private const val DEPENDENCY_COUNT_INDEX = 4
    private const val HEADER_VALUE_COUNT = 5
}

internal object PandaEngineNativeAuthStateMapper {
    fun toAccount(values: Array<String>?): EngineAccount? {
        if (values == null || values.size != 4) return null
        return runCatching { EngineAccount(values[0], values[1], values[2], values[3].toLong()) }.getOrNull()
    }

    fun toSession(values: Array<String>?): EngineAuthSession? {
        if (values == null || values.size != 6) return null
        return runCatching {
            EngineAuthSession(values[0], values[1], values[2].toLong(), values[3].toLong(), values[4].toLong(), when(values[5]) { "1" -> true; "0" -> false; else -> error("invalid current flag") })
        }.getOrNull()
    }

    fun toDomain(values: Array<String>): EngineAuthState = when {
        values.contentEquals(arrayOf(EngineAuthState.ANONYMOUS)) -> EngineAuthState.anonymous()
        values.contentEquals(arrayOf(EngineAuthState.LOGIN_REQUIRED)) -> EngineAuthState.loginRequired()
        values.size == AUTHENTICATED_VALUE_COUNT && values[STATE_INDEX] == EngineAuthState.AUTHENTICATED ->
            runCatching {
                EngineAuthState(
                    state = EngineAuthState.AUTHENTICATED,
                    account = EngineAccount(
                        id = values[ACCOUNT_ID_INDEX],
                        primaryEmail = values[ACCOUNT_EMAIL_INDEX],
                        status = values[ACCOUNT_STATUS_INDEX],
                        createdAtEpochMillis = values[ACCOUNT_CREATED_INDEX].toLong()
                    ),
                    session = EngineAuthSession(
                        id = values[SESSION_ID_INDEX],
                        deviceLabel = values[SESSION_DEVICE_INDEX],
                        createdAtEpochMillis = values[SESSION_CREATED_INDEX].toLong(),
                        lastUsedAtEpochMillis = values[SESSION_LAST_USED_INDEX].toLong(),
                        expiresAtEpochMillis = values[SESSION_EXPIRES_INDEX].toLong(),
                        current = when (values[SESSION_CURRENT_INDEX]) {
                            "1" -> true
                            "0" -> false
                            else -> error("invalid current-session flag")
                        }
                    )
                ).normalized()
            }.getOrElse { EngineAuthState.loginRequired() }
        else -> EngineAuthState.loginRequired()
    }

    private const val STATE_INDEX = 0
    private const val ACCOUNT_ID_INDEX = 1
    private const val ACCOUNT_EMAIL_INDEX = 2
    private const val ACCOUNT_STATUS_INDEX = 3
    private const val ACCOUNT_CREATED_INDEX = 4
    private const val SESSION_ID_INDEX = 5
    private const val SESSION_DEVICE_INDEX = 6
    private const val SESSION_CREATED_INDEX = 7
    private const val SESSION_LAST_USED_INDEX = 8
    private const val SESSION_EXPIRES_INDEX = 9
    private const val SESSION_CURRENT_INDEX = 10
    private const val AUTHENTICATED_VALUE_COUNT = 11
}

internal object PandaEngineNativeLibraryItemMapper {
    fun toDomain(values: Array<String>?): EngineLibraryItem? {
        if (values == null || values.size != 10) return null
        return runCatching {
            EngineLibraryItem(
                relationshipId = values[0], mediaId = values[1], title = values[2],
                artistId = values[3], artist = values[4], album = values[5].ifEmpty { null },
                durationMillis = values[6].toLong(), explicit = values[7] == "1",
                artworkId = values[8].ifEmpty { null }, relationshipAtEpochMillis = values[9].toLong()
            )
        }.getOrNull()
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
                savedTracksCount = nativeValues[SNAPSHOT_SAVED_TRACKS_COUNT_INDEX].toInt(),
                likedTracksCount = nativeValues[SNAPSHOT_LIKED_TRACKS_COUNT_INDEX].toInt(),
                libraryPendingCount = nativeValues[SNAPSHOT_LIBRARY_PENDING_COUNT_INDEX].toInt(),
                hasSavedTracksNextPage = nativeValues[SNAPSHOT_HAS_SAVED_NEXT_PAGE_INDEX].toBoolean(),
                hasLikedTracksNextPage = nativeValues[SNAPSHOT_HAS_LIKED_NEXT_PAGE_INDEX].toBoolean()
                ,playlistsCount = nativeValues[45].toInt(), playlistTracksCount = nativeValues[46].toInt(), hasPlaylistsNextPage = nativeValues[47].toBoolean(), hasPlaylistTracksNextPage = nativeValues[48].toBoolean(), hasPlaylistReconciliation = nativeValues[49].toBoolean()
                ,protectedAccount = null, deviceSessions = emptyList(), deviceSessionsCount = nativeValues[51].toInt(), hasDeviceSessionsNextPage = nativeValues[52].toBoolean(), discoveryResultsCount = nativeValues[53].toInt(), hasDiscoveryNextPage = nativeValues[54].toBoolean(), hasHistoryNextPage = nativeValues[55].toBoolean()
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

    private const val SNAPSHOT_VALUE_COUNT = 56
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
    private const val AUTH_ANONYMOUS = 0
    private const val AUTH_AUTHENTICATED = 1
}
