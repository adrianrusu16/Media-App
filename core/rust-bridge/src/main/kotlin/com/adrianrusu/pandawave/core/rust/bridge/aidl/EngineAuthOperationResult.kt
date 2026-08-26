package com.adrianrusu.pandawave.core.rust.bridge.aidl

import android.os.Parcel
import android.os.Parcelable

/** Credential-free result for dedicated authentication operations. */
data class EngineAuthOperationResult(
    val status: String,
    val errorType: String? = null,
    val retryAfterMillis: Long? = null
) : Parcelable {
    init {
        require(status in VALID_STATUSES) { "Unknown engine auth operation status." }
        require(retryAfterMillis == null || retryAfterMillis >= 0) {
            "Retry hint must not be negative."
        }
        require((status == STATUS_ERROR) == (errorType != null)) {
            "Only failed auth operations carry an error type."
        }
        require(errorType == null || errorType in VALID_ERROR_TYPES) {
            "Unknown engine auth error type."
        }
    }

    val isSuccessful: Boolean
        get() = status == STATUS_ACCEPTED ||
            status == STATUS_AUTHENTICATED ||
            status == STATUS_ANONYMOUS

    private constructor(parcel: Parcel) : this(
        status = parcel.readString().orEmpty(),
        errorType = parcel.readString(),
        retryAfterMillis = parcel.readNullableLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(status)
        parcel.writeString(errorType)
        parcel.writeNullableLong(retryAfterMillis)
    }

    override fun describeContents(): Int = 0

    companion object {
        const val STATUS_ACCEPTED = "accepted"
        const val STATUS_REJECTED = "rejected"
        const val STATUS_AUTHENTICATED = "authenticated"
        const val STATUS_ANONYMOUS = "anonymous"
        const val STATUS_ERROR = "error"

        const val ERROR_INVALID_INPUT = "invalid_input"
        const val ERROR_NOT_FOUND = "not_found"
        const val ERROR_LOGIN_REQUIRED = "login_required"
        const val ERROR_AUTH_EXPIRED = "auth_expired"
        const val ERROR_FORBIDDEN = "forbidden"
        const val ERROR_ALREADY_EXISTS = "already_exists"
        const val ERROR_FAILED_PRECONDITION = "failed_precondition"
        const val ERROR_CONFLICT = "conflict"
        const val ERROR_RATE_LIMITED = "rate_limited"
        const val ERROR_SERVICE_UNAVAILABLE = "service_unavailable"
        const val ERROR_BACKEND_FAULT = "backend_fault"
        const val ERROR_TRANSPORT = "transport"
        const val ERROR_UNSAFE_TRANSPORT = "unsafe_transport"
        const val ERROR_MAPPING_DEFECT = "mapping_defect"
        const val ERROR_NETWORK = "network_error"
        const val ERROR_PLAYER = "player_error"
        const val ERROR_AUTHENTICATION = "authentication_error"
        const val ERROR_SESSION_STORAGE = "session_storage"
        const val ERROR_MEDIA_SKIPPED = "media_skipped"
        const val ERROR_COMMAND_REJECTED = "command_rejected"
        const val ERROR_UNKNOWN = "unknown"

        private val VALID_STATUSES = setOf(
            STATUS_ACCEPTED,
            STATUS_REJECTED,
            STATUS_AUTHENTICATED,
            STATUS_ANONYMOUS,
            STATUS_ERROR
        )

        private val VALID_ERROR_TYPES = setOf(
            ERROR_INVALID_INPUT,
            ERROR_NOT_FOUND,
            ERROR_LOGIN_REQUIRED,
            ERROR_AUTH_EXPIRED,
            ERROR_FORBIDDEN,
            ERROR_ALREADY_EXISTS,
            ERROR_FAILED_PRECONDITION,
            ERROR_CONFLICT,
            ERROR_RATE_LIMITED,
            ERROR_SERVICE_UNAVAILABLE,
            ERROR_BACKEND_FAULT,
            ERROR_TRANSPORT,
            ERROR_UNSAFE_TRANSPORT,
            ERROR_MAPPING_DEFECT,
            ERROR_NETWORK,
            ERROR_PLAYER,
            ERROR_AUTHENTICATION,
            ERROR_SESSION_STORAGE,
            ERROR_MEDIA_SKIPPED,
            ERROR_COMMAND_REJECTED,
            ERROR_UNKNOWN
        )

        fun accepted(): EngineAuthOperationResult = EngineAuthOperationResult(STATUS_ACCEPTED)

        fun rejected(): EngineAuthOperationResult = EngineAuthOperationResult(STATUS_REJECTED)

        fun authenticated(): EngineAuthOperationResult = EngineAuthOperationResult(STATUS_AUTHENTICATED)

        fun anonymous(): EngineAuthOperationResult = EngineAuthOperationResult(STATUS_ANONYMOUS)

        fun error(errorType: String, retryAfterMillis: Long? = null): EngineAuthOperationResult =
            EngineAuthOperationResult(
                status = STATUS_ERROR,
                errorType = errorType,
                retryAfterMillis = retryAfterMillis
            )

        fun unavailable(): EngineAuthOperationResult = error(ERROR_SERVICE_UNAVAILABLE)

        @JvmField
        val CREATOR: Parcelable.Creator<EngineAuthOperationResult> =
            object : Parcelable.Creator<EngineAuthOperationResult> {
                override fun createFromParcel(parcel: Parcel): EngineAuthOperationResult =
                    EngineAuthOperationResult(parcel)

                override fun newArray(size: Int): Array<EngineAuthOperationResult?> = arrayOfNulls(size)
            }
    }
}

private fun Parcel.readNullableLong(): Long? = when (readByte()) {
    VALUE_PRESENT -> readLong()
    else -> null
}

private fun Parcel.writeNullableLong(value: Long?) {
    if (value == null) {
        writeByte(VALUE_ABSENT)
    } else {
        writeByte(VALUE_PRESENT)
        writeLong(value)
    }
}

private const val VALUE_ABSENT: Byte = 0
private const val VALUE_PRESENT: Byte = 1
