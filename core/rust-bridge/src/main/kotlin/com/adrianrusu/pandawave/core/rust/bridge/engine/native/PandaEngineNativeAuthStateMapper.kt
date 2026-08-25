package com.adrianrusu.pandawave.core.rust.bridge.engine.native

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAccount
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthSession
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthState

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
