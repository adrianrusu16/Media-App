package com.adrianrusu.pandawave.core.rust.bridge.aidl

import android.os.Parcel
import android.os.Parcelable

data class EngineSnapshot(
    val playbackState: String,
    val mediaId: String?,
    val title: String?,
    val artist: String?,
    val album: String? = null,
    val durationMillis: Long? = null,
    val artworkUri: String? = null,
    val sourceUri: String? = null,
    val mimeType: String? = null,
    val playbackExpiresAtEpochMillis: Long? = null,
    val userId: String?,
    val restrictionState: String,
    val updatedAtEpochMillis: Long,
    val hasActiveSession: Boolean = false,
    val hasError: Boolean = false,
    val errorType: String = ERROR_NONE,
    val searchResultsCount: Int = 0,
    val playbackSpeed: Float = 1F,
    val positionMillis: Long = 0L,
    val isBusy: Boolean = false,
    val canDispatch: Boolean = true,
    val controls: EnginePlayerControls = EnginePlayerControls.default(),
    val hasVoiceHypothesis: Boolean = false,
    val browseResultsCount: Int = 0,
    val themePreference: EngineThemePreference = EngineThemePreference.uninitialized(),
    val drivingState: String = DRIVING_UNKNOWN,
    val backendStatus: EngineBackendStatus? = null,
    val authState: EngineAuthState = EngineAuthState.anonymous(),
    val profile: EngineProfile? = null,
    val hasHistorySettings: Boolean = false,
    val historyEnabled: Boolean = false,
    val historyDeletedCount: Long = 0L,
    val historyEntriesCount: Int = 0
) : Parcelable {
    constructor(parcel: Parcel) : this(
        playbackState = parcel.readString().orEmpty(),
        mediaId = parcel.readString(),
        title = parcel.readString(),
        artist = parcel.readString(),
        album = parcel.readString(),
        durationMillis = parcel.readNullableLong(),
        artworkUri = parcel.readString(),
        sourceUri = parcel.readString(),
        mimeType = parcel.readString(),
        playbackExpiresAtEpochMillis = parcel.readNullableLong(),
        userId = parcel.readString(),
        restrictionState = parcel.readString().orEmpty(),
        updatedAtEpochMillis = parcel.readLong(),
        hasActiveSession = parcel.readBooleanValue(),
        hasError = parcel.readBooleanValue(),
        errorType = parcel.readString() ?: ERROR_NONE,
        searchResultsCount = parcel.readInt(),
        playbackSpeed = parcel.readFloat(),
        positionMillis = parcel.readLong(),
        isBusy = parcel.readBooleanValue(),
        canDispatch = parcel.readBooleanValue(),
        controls = EnginePlayerControls(
            playPause = parcel.readControlState(),
            skipNext = parcel.readControlState(),
            skipPrevious = parcel.readControlState(),
            showPlayIcon = parcel.readBooleanValue()
        ),
        hasVoiceHypothesis = parcel.readBooleanValue(),
        browseResultsCount = parcel.readInt(),
        themePreference = EngineThemePreference(
            themeId = parcel.readString() ?: EngineThemePreference.THEME_SYSTEM_DEFAULT,
            source = parcel.readString() ?: EngineThemePreference.SOURCE_UNINITIALIZED,
            revision = parcel.readLong(),
            initialized = parcel.readBooleanValue()
        ),
        drivingState = parcel.readString() ?: DRIVING_UNKNOWN,
        backendStatus = parcel.readBackendStatus(),
        authState = parcel.readEngineAuthState(),
        profile = parcel.readEngineProfile(),
        hasHistorySettings = parcel.readBooleanValue(),
        historyEnabled = parcel.readBooleanValue(),
        historyDeletedCount = parcel.readLong(),
        historyEntriesCount = parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(playbackState)
        parcel.writeString(mediaId)
        parcel.writeString(title)
        parcel.writeString(artist)
        parcel.writeString(album)
        parcel.writeNullableLong(durationMillis)
        parcel.writeString(artworkUri)
        parcel.writeString(sourceUri)
        parcel.writeString(mimeType)
        parcel.writeNullableLong(playbackExpiresAtEpochMillis)
        parcel.writeString(userId)
        parcel.writeString(restrictionState)
        parcel.writeLong(updatedAtEpochMillis)
        parcel.writeBooleanValue(hasActiveSession)
        parcel.writeBooleanValue(hasError)
        parcel.writeString(errorType)
        parcel.writeInt(searchResultsCount)
        parcel.writeFloat(playbackSpeed)
        parcel.writeLong(positionMillis)
        parcel.writeBooleanValue(isBusy)
        parcel.writeBooleanValue(canDispatch)
        parcel.writeControlState(controls.playPause)
        parcel.writeControlState(controls.skipNext)
        parcel.writeControlState(controls.skipPrevious)
        parcel.writeBooleanValue(controls.showPlayIcon)
        parcel.writeBooleanValue(hasVoiceHypothesis)
        parcel.writeInt(browseResultsCount)
        parcel.writeString(themePreference.themeId)
        parcel.writeString(themePreference.source)
        parcel.writeLong(themePreference.revision)
        parcel.writeBooleanValue(themePreference.initialized)
        parcel.writeString(drivingState)
        parcel.writeBackendStatus(backendStatus)
        parcel.writeEngineAuthState(authState)
        parcel.writeEngineProfile(profile)
        parcel.writeBooleanValue(hasHistorySettings)
        parcel.writeBooleanValue(historyEnabled)
        parcel.writeLong(historyDeletedCount)
        parcel.writeInt(historyEntriesCount)
    }

    override fun describeContents(): Int = 0

    companion object {
        const val PLAYBACK_IDLE = "idle"
        const val PLAYBACK_PLAYING = "playing"
        const val PLAYBACK_PAUSED = "paused"
        const val PLAYBACK_BUFFERING = "buffering"
        const val PLAYBACK_ERROR = "error"
        const val RESTRICTION_UNKNOWN = "unknown"
        const val RESTRICTION_UNRESTRICTED = "unrestricted"
        const val RESTRICTION_RESTRICTED = "restricted"
        const val DRIVING_UNKNOWN = "unknown"
        const val DRIVING_PARKED = "parked"
        const val DRIVING_IDLING = "idling"
        const val DRIVING_MOVING = "moving"
        const val ERROR_NONE = "none"
        const val ERROR_NOT_FOUND = "not_found"
        const val ERROR_NETWORK = "network"
        const val ERROR_PLAYER = "player"
        const val ERROR_AUTHENTICATION = "authentication"
        const val ERROR_MEDIA_SKIPPED = "media_skipped"
        const val ERROR_UNKNOWN = "unknown"

        fun idle(nowMillis: Long): EngineSnapshot = EngineSnapshot(
            playbackState = PLAYBACK_IDLE,
            mediaId = null,
            title = null,
            artist = null,
            album = null,
            durationMillis = null,
            artworkUri = null,
            sourceUri = null,
            mimeType = null,
            playbackExpiresAtEpochMillis = null,
            userId = null,
            restrictionState = RESTRICTION_UNKNOWN,
            updatedAtEpochMillis = nowMillis,
            controls = EnginePlayerControls.defaultIdle()
        )

        @JvmField
        val CREATOR: Parcelable.Creator<EngineSnapshot> =
            object : Parcelable.Creator<EngineSnapshot> {
                override fun createFromParcel(parcel: Parcel): EngineSnapshot = EngineSnapshot(parcel)

                override fun newArray(size: Int): Array<EngineSnapshot?> = arrayOfNulls(size)
            }
    }
}

data class EngineAuthState(
    val state: String,
    val account: EngineAccount? = null,
    val session: EngineAuthSession? = null
) {
    internal fun normalized(): EngineAuthState = when (state) {
        ANONYMOUS -> anonymous()
        LOGIN_REQUIRED -> loginRequired()
        AUTHENTICATED -> if (account.isValid() && session.isValid()) this else loginRequired()
        else -> loginRequired()
    }

    companion object {
        const val ANONYMOUS = "anonymous"
        const val AUTHENTICATED = "authenticated"
        const val LOGIN_REQUIRED = "login_required"

        fun anonymous(): EngineAuthState = EngineAuthState(ANONYMOUS)

        fun loginRequired(): EngineAuthState = EngineAuthState(LOGIN_REQUIRED)
    }
}

private fun EngineAccount?.isValid(): Boolean = this != null &&
    id.isNotBlank() && primaryEmail.isNotBlank() && status.isNotBlank() &&
    createdAtEpochMillis >= 0

private fun EngineAuthSession?.isValid(): Boolean = this != null &&
    id.isNotBlank() && createdAtEpochMillis >= 0 && lastUsedAtEpochMillis >= 0 &&
    expiresAtEpochMillis >= 0

data class EngineAccount(
    val id: String,
    val primaryEmail: String,
    val status: String,
    val createdAtEpochMillis: Long
)

data class EngineAuthSession(
    val id: String,
    val deviceLabel: String,
    val createdAtEpochMillis: Long,
    val lastUsedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val current: Boolean
)

data class EngineBackendStatus(
    val healthy: Boolean,
    val version: String,
    val status: String,
    val checkedAtEpochMillis: Long?,
    val dependencies: List<EngineBackendDependencyStatus>
)

data class EngineBackendDependencyStatus(
    val name: String,
    val status: String,
    val message: String
)

data class EngineControlState(val isVisible: Boolean, val isEnabled: Boolean, val isActive: Boolean) {
    companion object {
        fun hidden(): EngineControlState = EngineControlState(
            isVisible = false,
            isEnabled = false,
            isActive = false
        )

        fun enabled(): EngineControlState = EngineControlState(
            isVisible = true,
            isEnabled = true,
            isActive = false
        )
    }
}

data class EnginePlayerControls(
    val playPause: EngineControlState,
    val skipNext: EngineControlState,
    val skipPrevious: EngineControlState,
    val showPlayIcon: Boolean
) {
    companion object {
        fun default(): EnginePlayerControls = EnginePlayerControls(
            playPause = EngineControlState.hidden(),
            skipNext = EngineControlState.hidden(),
            skipPrevious = EngineControlState.hidden(),
            showPlayIcon = true
        )

        fun defaultIdle(): EnginePlayerControls = EnginePlayerControls(
            playPause = EngineControlState.enabled(),
            skipNext = EngineControlState.hidden(),
            skipPrevious = EngineControlState.hidden(),
            showPlayIcon = true
        )
    }
}

private fun Parcel.readBooleanValue(): Boolean = readInt() != 0

private fun Parcel.writeBooleanValue(value: Boolean) {
    writeInt(if (value) 1 else 0)
}

private fun Parcel.readNullableLong(): Long? = if (readBooleanValue()) {
    readLong()
} else {
    null
}

private fun Parcel.writeNullableLong(value: Long?) {
    writeBooleanValue(value != null)
    if (value != null) {
        writeLong(value)
    }
}

private fun Parcel.readControlState(): EngineControlState = EngineControlState(
    isVisible = readBooleanValue(),
    isEnabled = readBooleanValue(),
    isActive = readBooleanValue()
)

private fun Parcel.writeControlState(controlState: EngineControlState) {
    writeBooleanValue(controlState.isVisible)
    writeBooleanValue(controlState.isEnabled)
    writeBooleanValue(controlState.isActive)
}

private fun Parcel.readBackendStatus(): EngineBackendStatus? {
    if (!readBooleanValue()) return null

    return EngineBackendStatus(
        healthy = readBooleanValue(),
        version = readString().orEmpty(),
        status = readString().orEmpty(),
        checkedAtEpochMillis = readNullableLong(),
        dependencies = List(readInt()) {
            EngineBackendDependencyStatus(
                name = readString().orEmpty(),
                status = readString().orEmpty(),
                message = readString().orEmpty()
            )
        }
    )
}

private fun Parcel.writeBackendStatus(status: EngineBackendStatus?) {
    writeBooleanValue(status != null)
    if (status == null) return

    writeBooleanValue(status.healthy)
    writeString(status.version)
    writeString(status.status)
    writeNullableLong(status.checkedAtEpochMillis)
    writeInt(status.dependencies.size)
    status.dependencies.forEach { dependency ->
        writeString(dependency.name)
        writeString(dependency.status)
        writeString(dependency.message)
    }
}

private fun Parcel.readEngineProfile(): EngineProfile? {
    if (!readBooleanValue()) return null
    val id = readString().orEmpty()
    val externalUserId = readString().orEmpty()
    val displayName = if (readBooleanValue()) readString().orEmpty() else null
    val createdAtEpochMillis = readNullableLong()
    val updatedAtEpochMillis = readNullableLong()
    return EngineProfile(
        id = id,
        externalUserId = externalUserId,
        displayName = displayName,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis
    ).takeIf { it.id.isNotBlank() && it.externalUserId.isNotBlank() }
}

private fun Parcel.writeEngineProfile(profile: EngineProfile?) {
    writeBooleanValue(profile != null)
    if (profile == null) return
    writeString(profile.id)
    writeString(profile.externalUserId)
    writeBooleanValue(profile.displayName != null)
    if (profile.displayName != null) writeString(profile.displayName)
    writeNullableLong(profile.createdAtEpochMillis)
    writeNullableLong(profile.updatedAtEpochMillis)
}

internal fun Parcel.readEngineAuthState(): EngineAuthState {
    val state = readString() ?: return EngineAuthState.loginRequired()
    if (state != EngineAuthState.AUTHENTICATED) {
        return when (state) {
            EngineAuthState.ANONYMOUS -> EngineAuthState.anonymous()
            else -> EngineAuthState.loginRequired()
        }
    }
    return EngineAuthState(
        state = state,
        account = EngineAccount(
            id = readString().orEmpty(),
            primaryEmail = readString().orEmpty(),
            status = readString().orEmpty(),
            createdAtEpochMillis = readLong()
        ),
        session = EngineAuthSession(
            id = readString().orEmpty(),
            deviceLabel = readString().orEmpty(),
            createdAtEpochMillis = readLong(),
            lastUsedAtEpochMillis = readLong(),
            expiresAtEpochMillis = readLong(),
            current = readBooleanValue()
        )
    ).normalized()
}

internal fun Parcel.writeEngineAuthState(authState: EngineAuthState) {
    val normalized = authState.normalized()
    val account = normalized.account
    val session = normalized.session
    writeString(normalized.state)
    if (normalized.state != EngineAuthState.AUTHENTICATED || account == null || session == null) return
    writeString(account.id)
    writeString(account.primaryEmail)
    writeString(account.status)
    writeLong(account.createdAtEpochMillis)
    writeString(session.id)
    writeString(session.deviceLabel)
    writeLong(session.createdAtEpochMillis)
    writeLong(session.lastUsedAtEpochMillis)
    writeLong(session.expiresAtEpochMillis)
    writeBooleanValue(session.current)
}
