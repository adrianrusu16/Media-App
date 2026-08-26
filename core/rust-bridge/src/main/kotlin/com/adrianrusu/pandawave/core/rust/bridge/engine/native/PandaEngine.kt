package com.adrianrusu.pandawave.core.rust.bridge.engine.native

import com.adrianrusu.pandawave.core.common.log.PandaLog
import com.adrianrusu.pandawave.core.common.trace.PandaTrace
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthState
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineBackendAvailability
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineHistoryItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineHistoryPage
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineLibraryItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistReconciliation
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistTrackItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.pandawave.core.rust.bridge.engine.AudioSourceResolver
import com.adrianrusu.pandawave.core.rust.bridge.engine.EngineDispatchResult
import com.adrianrusu.pandawave.core.rust.bridge.engine.RustEngine
import com.adrianrusu.pandawave.core.secure.storage.SecureSecretProtector
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

class PandaEngine private constructor(private val nativeHandle: Long, private val clock: () -> Long) :
    RustEngine,
    AutoCloseable {
    private val metadataCache = NativeEngineMetadataCache(::queryNativeMetadata)
    private val probeExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val probeLock = Any()
    private var scheduledProbe: ScheduledFuture<*>? = null
    private var probeInFlight = false
    private var consecutiveProbeFailures = 0
    private var onHealthDispatchResult: ((EngineDispatchResult) -> Unit)? = null
    private var onHealthSnapshotChanged: ((EngineSnapshot) -> Unit)? = null

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

    private fun setBackendAvailability(availability: EngineBackendAvailability) {
        val nativeAvailability = when (availability.status) {
            EngineBackendAvailability.CONNECTING -> NATIVE_BACKEND_CONNECTING
            EngineBackendAvailability.AVAILABLE -> NATIVE_BACKEND_AVAILABLE
            EngineBackendAvailability.UNAVAILABLE -> NATIVE_BACKEND_UNAVAILABLE
            else -> return
        }
        val nativeReason = when (availability.reason) {
            EngineBackendAvailability.REASON_NETWORK_UNAVAILABLE -> NATIVE_BACKEND_REASON_NETWORK_UNAVAILABLE
            EngineBackendAvailability.REASON_TIMEOUT -> NATIVE_BACKEND_REASON_TIMEOUT
            EngineBackendAvailability.REASON_SERVICE_UNAVAILABLE -> NATIVE_BACKEND_REASON_SERVICE_UNAVAILABLE
            else -> NATIVE_BACKEND_REASON_CONNECTION_FAILED
        }
        check(nativeSetBackendAvailability(nativeHandle, nativeAvailability, nativeReason)) {
            "PandaEngine failed to update backend availability."
        }
    }

    override fun startBackendHealthMonitoring(
        onDispatchResult: (EngineDispatchResult) -> Unit,
        onSnapshotChanged: (EngineSnapshot) -> Unit
    ) {
        synchronized(probeLock) {
            onHealthDispatchResult = onDispatchResult
            onHealthSnapshotChanged = onSnapshotChanged
        }
        requestHealthProbe(delayMillis = 0)
    }

    override fun stopBackendHealthMonitoring() {
        synchronized(probeLock) {
            scheduledProbe?.cancel(false)
            scheduledProbe = null
            onHealthDispatchResult = null
            onHealthSnapshotChanged = null
        }
    }

    override fun hintNetworkAvailability(isAvailable: Boolean) {
        if (healthDispatchResult() == null) return

        if (isAvailable) {
            synchronized(probeLock) { consecutiveProbeFailures = 0 }
            requestHealthProbe(delayMillis = 0, replaceScheduled = true)
            return
        }

        setBackendAvailability(
            EngineBackendAvailability(
                EngineBackendAvailability.UNAVAILABLE,
                EngineBackendAvailability.REASON_NETWORK_UNAVAILABLE
            )
        )
        healthSnapshotChanged()?.invoke(snapshot())
        requestHealthProbe(delayMillis = nextProbeDelayMillis())
    }

    override fun snapshot(): EngineSnapshot = PandaTrace.section("PW.Engine.Native.snapshot") {
        nativeSnapshot(nativeHandle).toEngineSnapshot()
    }

    override fun registerPassword(email: String, password: ByteArray): EngineAuthOperationResult =
        withSecret(password) {
            nativeRegisterPassword(nativeHandle, email, password).toAuthOperationResult()
        }

    override fun resendVerification(email: String): EngineAuthOperationResult =
        nativeResendVerification(nativeHandle, email).toAuthOperationResult()

    override fun verifyEmail(verificationToken: ByteArray, deviceLabel: String): EngineAuthOperationResult =
        withSecret(verificationToken) {
            nativeVerifyEmail(nativeHandle, verificationToken, deviceLabel).toAuthOperationResult()
        }

    override fun loginPassword(email: String, password: ByteArray, deviceLabel: String): EngineAuthOperationResult =
        withSecret(password) {
            nativeLoginPassword(nativeHandle, email, password, deviceLabel).toAuthOperationResult()
        }

    override fun logout(): EngineAuthOperationResult = nativeLogout(nativeHandle).toAuthOperationResult()

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

    override fun browseResult(index: Int): EngineCatalogItem? =
        PandaEngineNativeCatalogItemMapper.toDomain(nativeBrowseResultValues(nativeHandle, index))

    override fun browseResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> =
        PandaEngineNativeCatalogItemMapper.toPage(
            nativeSnapshotPageValues(nativeHandle, PAGE_BROWSE, offset, limit)
        )

    override fun discoveryResult(index: Int): EngineCatalogItem? = nativeDiscoveryResultValues(nativeHandle, index)
        .let(PandaEngineNativeCatalogItemMapper::toDomain)

    override fun forYouResult(index: Int): EngineCatalogItem? = nativeForYouResultValues(nativeHandle, index)
        .let(PandaEngineNativeCatalogItemMapper::toDomain)

    override fun recommendationResult(index: Int): EngineCatalogItem? =
        nativeRecommendationResultValues(nativeHandle, index)
            .let(PandaEngineNativeCatalogItemMapper::toDomain)

    override fun discoveryResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> =
        PandaEngineNativeCatalogItemMapper.toPage(
            nativeSnapshotPageValues(nativeHandle, PAGE_DISCOVERY, offset, limit)
        )

    override fun forYouResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> =
        PandaEngineNativeCatalogItemMapper.toPage(
            nativeSnapshotPageValues(nativeHandle, PAGE_FOR_YOU, offset, limit)
        )

    override fun recommendationResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> =
        PandaEngineNativeCatalogItemMapper.toPage(
            nativeSnapshotPageValues(nativeHandle, PAGE_RECOMMENDATIONS, offset, limit)
        )

    override fun profilePreferenceValue(key: String): String? = nativeProfilePreferenceValue(nativeHandle, key.trim())

    override fun savedTrack(index: Int): EngineLibraryItem? =
        PandaEngineNativeLibraryItemMapper.toDomain(nativeSavedTrackValues(nativeHandle, index))

    override fun likedTrack(index: Int): EngineLibraryItem? =
        PandaEngineNativeLibraryItemMapper.toDomain(nativeLikedTrackValues(nativeHandle, index))

    override fun savedTracksPage(offset: Int, limit: Int): List<EngineLibraryItem> =
        PandaEngineNativeLibraryItemMapper.toPage(
            nativeSnapshotPageValues(nativeHandle, PAGE_SAVED, offset, limit)
        )

    override fun likedTracksPage(offset: Int, limit: Int): List<EngineLibraryItem> =
        PandaEngineNativeLibraryItemMapper.toPage(
            nativeSnapshotPageValues(nativeHandle, PAGE_LIKED, offset, limit)
        )

    override fun pendingLibraryTrackId(index: Int): String? = nativePendingLibraryTrackId(nativeHandle, index)

    override fun pendingLibraryTrackIdsPage(offset: Int, limit: Int): List<String> =
        nativeSnapshotPageValues(nativeHandle, PAGE_PENDING_IDS, offset, limit).orEmpty().toList()

    override fun playlist(index: Int): EnginePlaylistItem? = playlistItem(nativePlaylistValues(nativeHandle, index))
    override fun playlistTrack(index: Int): EnginePlaylistTrackItem? =
        playlistTrackItem(nativePlaylistTrackValues(nativeHandle, index))
    override fun playlistsPage(offset: Int, limit: Int): List<EnginePlaylistItem> =
        playlistItems(nativeSnapshotPageValues(nativeHandle, PAGE_PLAYLISTS, offset, limit))
    override fun playlistTracksPage(offset: Int, limit: Int): List<EnginePlaylistTrackItem> =
        playlistTrackItems(nativeSnapshotPageValues(nativeHandle, PAGE_PLAYLIST_TRACKS, offset, limit))
    override fun selectedPlaylistId(): String? = nativePlaylistSelectionValues(nativeHandle)?.getOrNull(0)?.ifEmpty {
        null
    }
    override fun playlistReconciliation(): EnginePlaylistReconciliation? =
        playlistReconciliationItem(nativePlaylistSelectionValues(nativeHandle))

    override fun searchResult(index: Int): EngineCatalogItem? =
        PandaEngineNativeCatalogItemMapper.toDomain(nativeSearchResultValues(nativeHandle, index))

    override fun searchResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> =
        PandaEngineNativeCatalogItemMapper.toPage(
            nativeSnapshotPageValues(nativeHandle, PAGE_SEARCH, offset, limit)
        )

    override fun historyEntry(index: Int): EngineHistoryItem? =
        PandaEngineNativeHistoryItemMapper.toDomain(nativeHistoryEntryValues(nativeHandle, index))

    override fun historyPage(offset: Int, limit: Int, generation: Long): EngineHistoryPage =
        PandaTrace.section("PW.Engine.Native.historyPage") {
            val page = PandaEngineNativeHistoryItemMapper.toPage(
                nativeHistoryPageValues(nativeHandle, offset, limit, generation),
                generation
            )
            PandaLog.i(PandaLog.Tag.HISTORY) {
                "page_read offset=$offset limit=$limit requestedGeneration=$generation " +
                    "generation=${page.generation} count=${page.items.size} " +
                    "titles=${PandaLog.titles(page.items.map { it.title })}"
            }
            page
        }

    override fun effectCount(): Int = nativeEffectCount(nativeHandle)

    override fun effect(index: Int): EngineEffect? = PandaTrace.section("PW.Engine.Native.effectBatch") {
        effectItem(nativeEffectValues(nativeHandle, index))
    }

    override fun dispatch(command: EngineCommand): EngineDispatchResult =
        PandaTrace.section("PW.Engine.Native.dispatch") {
            val nativeValues = nativeDispatch(
                handle = nativeHandle,
                commandType = nativeCommandType(command),
                payload = command.payload,
                nowEpochMillis = clock()
            )

            EngineDispatchResult(
                snapshot = nativeValues.toEngineSnapshot(),
                event = EngineEvent(
                    type = EngineEvent.TYPE_COMMAND_APPLIED,
                    message = nativeLastEventMessage(nativeHandle) ?: command.type
                ),
                effects = effects()
            )
        }

    override fun dispatchPlatformEvent(event: EnginePlatformEvent): EngineDispatchResult =
        PandaTrace.section("PW.Engine.Native.platformEvent") {
            val nativeValues = nativeDispatchPlatformEvent(
                handle = nativeHandle,
                eventType = event.toNativePlatformEventType(),
                payload = event.payload,
                nowEpochMillis = clock()
            )

            EngineDispatchResult(
                snapshot = nativeValues.toEngineSnapshot(),
                event = EngineEvent(
                    type = EngineEvent.TYPE_PLATFORM_EVENT_APPLIED,
                    message = nativeLastEventMessage(nativeHandle) ?: event.type
                ),
                effects = effects()
            )
        }

    override fun close() {
        stopBackendHealthMonitoring()
        probeExecutor.shutdownNow()
        nativeDestroy(nativeHandle)
    }

    /** Runs only the idempotent status RPC. User operations are never replayed. */
    private fun requestHealthProbe(delayMillis: Long, replaceScheduled: Boolean = false) {
        synchronized(probeLock) {
            if (probeInFlight) return
            if (scheduledProbe != null) {
                if (!replaceScheduled) return
                scheduledProbe?.cancel(false)
            }
            scheduledProbe = try {
                probeExecutor.schedule(::runHealthProbe, delayMillis, TimeUnit.MILLISECONDS)
            } catch (_: RejectedExecutionException) {
                null
            }
        }
    }

    private fun runHealthProbe() {
        synchronized(probeLock) {
            scheduledProbe = null
            if (probeInFlight || onHealthDispatchResult == null) return
            probeInFlight = true
        }

        var nextDelayMillis: Long? = null
        try {
            val result = dispatch(
                EngineCommand(EngineCommand.TYPE_REFRESH_BACKEND_STATUS, null)
            )
            healthDispatchResult()?.invoke(result)
            nextDelayMillis = if (
                result.snapshot.backendAvailability.status == EngineBackendAvailability.AVAILABLE
            ) {
                synchronized(probeLock) { consecutiveProbeFailures = 0 }
                HEALTHY_PROBE_INTERVAL_MILLIS
            } else {
                synchronized(probeLock) {
                    consecutiveProbeFailures = (consecutiveProbeFailures + 1)
                        .coerceAtMost(BACKOFF_DELAYS_MILLIS.lastIndex)
                }
                nextProbeDelayMillis()
            }
        } finally {
            synchronized(probeLock) { probeInFlight = false }
        }
        nextDelayMillis
            ?.takeIf { healthDispatchResult() != null }
            ?.let { delayMillis -> requestHealthProbe(delayMillis) }
    }

    private fun nextProbeDelayMillis(): Long {
        val base = synchronized(probeLock) {
            BACKOFF_DELAYS_MILLIS[
                consecutiveProbeFailures.coerceIn(0, BACKOFF_DELAYS_MILLIS.lastIndex)
            ]
        }
        return (base * ThreadLocalRandom.current().nextDouble(0.8, 1.2)).toLong()
    }

    private fun healthDispatchResult(): ((EngineDispatchResult) -> Unit)? = synchronized(probeLock) {
        onHealthDispatchResult
    }

    private fun healthSnapshotChanged(): ((EngineSnapshot) -> Unit)? = synchronized(probeLock) {
        onHealthSnapshotChanged
    }

    private external fun nativeSnapshot(handle: Long): LongArray

    private external fun nativeRegisterPassword(handle: Long, email: String, password: ByteArray): Array<String>?

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

    private external fun nativeConfigureBackend(handle: Long, configJson: String, isDevelopment: Boolean): Boolean

    private external fun nativeSetBackendAvailability(handle: Long, availability: Int, reason: Int): Boolean

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

    private external fun nativeMetadataValues(handle: Long): Array<String>?

    private external fun nativeLastEventMessage(handle: Long): String?

    private external fun nativeBackendStatusValues(handle: Long): Array<String>?

    private external fun nativeAuthStateValues(handle: Long): Array<String>?

    private external fun nativeProfileValues(handle: Long): Array<String>?
    private external fun nativeProtectedAccountValues(handle: Long): Array<String>?
    private external fun nativeDeviceSessionValues(handle: Long, index: Int): Array<String>?
    private external fun nativeSavedTrackValues(handle: Long, index: Int): Array<String>?
    private external fun nativeBrowseResultValues(handle: Long, index: Int): Array<String>?
    private external fun nativeSearchResultValues(handle: Long, index: Int): Array<String>?
    private external fun nativeDiscoveryResultValues(handle: Long, index: Int): Array<String>?
    private external fun nativeForYouResultValues(handle: Long, index: Int): Array<String>?
    private external fun nativeRecommendationResultValues(handle: Long, index: Int): Array<String>?
    private external fun nativeProfilePreferenceValue(handle: Long, key: String): String?
    private external fun nativeLikedTrackValues(handle: Long, index: Int): Array<String>?
    private external fun nativeHistoryEntryValues(handle: Long, index: Int): Array<String>?
    private external fun nativeHistoryPageValues(
        handle: Long,
        offset: Int,
        limit: Int,
        generation: Long
    ): Array<String>?
    private external fun nativeSnapshotPageValues(handle: Long, kind: Int, offset: Int, limit: Int): Array<String>?
    private external fun nativePendingLibraryTrackId(handle: Long, index: Int): String?
    private external fun nativePlaylistValues(handle: Long, index: Int): Array<String>?
    private external fun nativePlaylistTrackValues(handle: Long, index: Int): Array<String>?
    private external fun nativePlaylistSelectionValues(handle: Long): Array<String>?

    private external fun nativeEffectCount(handle: Long): Int

    private external fun nativeEffectValues(handle: Long, index: Int): Array<String>?

    private external fun nativeEffectPageValues(handle: Long, offset: Int, limit: Int): Array<String>?

    private external fun nativeEffectType(handle: Long, index: Int): Int

    private external fun nativeEffectMediaId(handle: Long, index: Int): String?

    private external fun nativeEffectNotifyMessage(handle: Long, index: Int): String?

    private external fun nativeEffectPositionMillis(handle: Long, index: Int): Long

    private external fun nativeEffectPlaybackInstanceId(handle: Long, index: Int): Long

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

    private fun LongArray.toEngineSnapshot(): EngineSnapshot = PandaTrace.section("PW.Engine.Native.projectSnapshot") {
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
        val deviceSessions = PandaEngineNativeAuthStateMapper.toSessions(
            nativeSnapshotPageValues(
                nativeHandle,
                PAGE_DEVICE_SESSIONS,
                0,
                projection.snapshot.deviceSessionsCount.coerceIn(0, MAX_ENGINE_PAGE_QUERY_SIZE)
            )
        )
        snapshot.copy(
            backendStatus = backendStatus,
            authState = authState,
            profile = profile,
            protectedAccount = protectedAccount,
            deviceSessions = deviceSessions
        )
    }

    private fun queryNativeMetadata(): NativeEngineMetadata = PandaTrace.section("PW.Engine.Native.metadataBatch") {
        metadataItem(nativeMetadataValues(nativeHandle))
    }

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

    private fun effects(): List<EngineEffect> = PandaTrace.section("PW.Engine.Native.effects") {
        PandaEngineNativePackedPage.toItems(
            nativeEffectPageValues(nativeHandle, 0, MAX_ENGINE_PAGE_QUERY_SIZE),
            EFFECT_VALUE_COUNT
        ) { values, offset ->
            if (values.size < offset + EFFECT_VALUE_COUNT) {
                null
            } else {
                effectItem(values.copyOfRange(offset, offset + EFFECT_VALUE_COUNT))
            }
        }
    }

    private fun metadataItem(values: Array<String>?): NativeEngineMetadata {
        if (values == null || values.size != METADATA_VALUE_COUNT) return NativeEngineMetadata.empty()
        return NativeEngineMetadata(
            mediaId = values[0].ifBlank { null },
            title = values[1].ifBlank { null },
            artist = values[2].ifBlank { null },
            album = values[3].ifBlank { null },
            artworkUri = values[4].ifBlank { null },
            sourceUri = values[5].ifBlank { null },
            mimeType = values[6].ifBlank { null },
            userId = values[7].ifBlank { null }
        )
    }

    private fun effectItem(values: Array<String>?): EngineEffect? {
        if (values == null || values.size != EFFECT_VALUE_COUNT) return null
        val type = values[0].toIntOrNull() ?: return null
        val positionMillis = values[3].toLongOrNull() ?: -1L
        val speed = values[4].toFloatOrNull() ?: Float.NaN
        val playbackInstanceId = values[5].toLongOrNull() ?: -1L
        return effectItem(
            type = type,
            mediaId = values[1],
            message = values[2],
            positionMillis = positionMillis,
            speed = speed,
            playbackInstanceId = playbackInstanceId
        )
    }

    private fun effectItem(
        type: Int,
        mediaId: String?,
        message: String?,
        positionMillis: Long,
        speed: Float,
        playbackInstanceId: Long
    ): EngineEffect? = when (val effectType = type.toEngineEffectType()) {
        EngineEffect.TYPE_UNKNOWN -> null

        else -> EngineEffect(
            type = effectType,
            mediaId = mediaId.takeUnless { value -> value.isNullOrBlank() },
            message = message.takeUnless { value -> value.isNullOrBlank() },
            positionMillis = positionMillis.takeUnless { value -> value < 0L },
            speed = speed.takeUnless { value -> value.isNaN() },
            playbackInstanceId = playbackInstanceId.takeUnless { value -> value < 0L }
        )
    }

    companion object {
        private const val METADATA_VALUE_COUNT = 8
        private const val EFFECT_VALUE_COUNT = 6
        private const val MAX_ENGINE_PAGE_QUERY_SIZE = 50
        private const val PAGE_BROWSE = 0
        private const val PAGE_SEARCH = 1
        private const val PAGE_DISCOVERY = 2
        private const val PAGE_FOR_YOU = 3
        private const val PAGE_RECOMMENDATIONS = 4
        private const val PAGE_SAVED = 5
        private const val PAGE_LIKED = 6
        private const val PAGE_PLAYLISTS = 7
        private const val PAGE_PLAYLIST_TRACKS = 8
        private const val PAGE_PENDING_IDS = 9
        private const val PAGE_DEVICE_SESSIONS = 10

        private const val HEALTHY_PROBE_INTERVAL_MILLIS = 60_000L
        private val BACKOFF_DELAYS_MILLIS =
            longArrayOf(1_000L, 2_000L, 5_000L, 10_000L, 30_000L, 60_000L)

        fun create(clock: () -> Long = System::currentTimeMillis): PandaEngine {
            PandaEngineLibrary.load()
            return PandaEngine(
                nativeHandle = nativeCreate(clock()),
                clock = clock
            )
        }

        @JvmStatic
        private external fun nativeCreate(nowEpochMillis: Long): Long

        private const val NATIVE_BACKEND_CONNECTING = 0
        private const val NATIVE_BACKEND_AVAILABLE = 1
        private const val NATIVE_BACKEND_UNAVAILABLE = 2
        private const val NATIVE_BACKEND_REASON_NETWORK_UNAVAILABLE = 1
        private const val NATIVE_BACKEND_REASON_CONNECTION_FAILED = 2
        private const val NATIVE_BACKEND_REASON_TIMEOUT = 3
        private const val NATIVE_BACKEND_REASON_SERVICE_UNAVAILABLE = 4

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
        private const val COMMAND_LOAD_FOR_YOU_FEED = 57
        private const val COMMAND_LOAD_RECOMMENDATIONS = 58
        private const val COMMAND_PLAY_QUEUE = 59
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
        private const val PLATFORM_EVENT_PLAYBACK_POSITION_CHECKPOINT = 10
        private const val PLATFORM_EVENT_AUDIO_FOCUS_REQUEST_RESULT = 11
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
        private const val EFFECT_RECREATE_PLAYER_AND_LOAD = 16

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
            EngineCommand.TYPE_PLAY_QUEUE -> COMMAND_PLAY_QUEUE
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
            EngineCommand.TYPE_LOAD_FOR_YOU_FEED -> COMMAND_LOAD_FOR_YOU_FEED
            EngineCommand.TYPE_LOAD_RECOMMENDATIONS -> COMMAND_LOAD_RECOMMENDATIONS
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

        internal fun playlistItems(values: Array<String>?): List<EnginePlaylistItem> =
            PandaEngineNativePackedPage.toItems(values, 7) { packed, offset ->
                playlistItem(packed.copyOfRange(offset, offset + 7))
            }

        internal fun playlistTrackItem(values: Array<String>?): EnginePlaylistTrackItem? {
            if (values == null || values.size != 12) return null
            val duration = values[7].toNonNegativeLongOrNull() ?: return null
            val explicit = when (values[8]) {
                "0" -> false
                "1" -> true
                else -> return null
            }
            val position = values[10].toLongOrNull()?.takeIf { it in 0L..Int.MAX_VALUE.toLong() }?.toInt()
                ?: return null
            val addedAt = values[11].toNonNegativeLongOrNull() ?: return null
            return EnginePlaylistTrackItem(
                values[0], values[1], values[2], values[3], values[4], values[5],
                values[6].ifEmpty { null }, duration, explicit, values[9].ifEmpty { null }, position, addedAt
            )
        }

        internal fun playlistTrackItems(values: Array<String>?): List<EnginePlaylistTrackItem> =
            PandaEngineNativePackedPage.toItems(values, 12) { packed, offset ->
                playlistTrackItem(packed.copyOfRange(offset, offset + 12))
            }

        internal fun playlistReconciliationItem(values: Array<String>?): EnginePlaylistReconciliation? {
            if (values == null || values.size != 6 || values[1].isEmpty()) return null
            val expectedRevision = values[2].toNonNegativeLongOrNull() ?: return null
            val serverRevision = values[3].toNonNegativeLongOrNull() ?: return null
            return EnginePlaylistReconciliation(
                values[1],
                expectedRevision,
                serverRevision,
                values[4].split('\u001f').filter(String::isNotEmpty),
                values[5].split('\u001f').filter(String::isNotEmpty)
            )
        }

        private fun String.toNonNegativeLongOrNull(): Long? = toLongOrNull()?.takeIf { it >= 0L }

        private fun EnginePlatformEvent.toNativePlatformEventType(): Int = when (type) {
            EnginePlatformEvent.TYPE_APP_FOREGROUNDED -> PLATFORM_EVENT_APP_FOREGROUNDED
            EnginePlatformEvent.TYPE_APP_BACKGROUNDED -> PLATFORM_EVENT_APP_BACKGROUNDED
            EnginePlatformEvent.TYPE_SUSPEND_TO_RAM -> PLATFORM_EVENT_SUSPEND_TO_RAM
            EnginePlatformEvent.TYPE_RESUME_FROM_RAM -> PLATFORM_EVENT_RESUME_FROM_RAM
            EnginePlatformEvent.TYPE_UX_RESTRICTIONS_CHANGED -> PLATFORM_EVENT_UX_RESTRICTIONS_CHANGED
            EnginePlatformEvent.TYPE_AUDIO_FOCUS_CHANGED -> PLATFORM_EVENT_AUDIO_FOCUS_CHANGED
            EnginePlatformEvent.TYPE_AUDIO_FOCUS_REQUEST_RESULT -> PLATFORM_EVENT_AUDIO_FOCUS_REQUEST_RESULT
            EnginePlatformEvent.TYPE_MEDIA_LOADED -> PLATFORM_EVENT_MEDIA_LOADED
            EnginePlatformEvent.TYPE_MEDIA_ERROR -> PLATFORM_EVENT_MEDIA_ERROR
            EnginePlatformEvent.TYPE_VEHICLE_DRIVING_STATE_CHANGED -> PLATFORM_EVENT_VEHICLE_DRIVING_STATE_CHANGED
            EnginePlatformEvent.TYPE_PLAYBACK_COMPLETED -> PLATFORM_EVENT_PLAYBACK_COMPLETED
            EnginePlatformEvent.TYPE_PLAYBACK_POSITION_CHECKPOINT -> PLATFORM_EVENT_PLAYBACK_POSITION_CHECKPOINT
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
            EFFECT_RECREATE_PLAYER_AND_LOAD -> EngineEffect.TYPE_RECREATE_PLAYER_AND_LOAD
            else -> EngineEffect.TYPE_UNKNOWN
        }
    }
}
