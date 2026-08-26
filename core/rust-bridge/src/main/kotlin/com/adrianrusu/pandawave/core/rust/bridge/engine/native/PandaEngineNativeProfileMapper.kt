package com.adrianrusu.pandawave.core.rust.bridge.engine.native

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineProfile

internal object PandaEngineNativeProfileMapper {
    private const val VALUE_COUNT = 6

    fun toDomain(values: Array<String>?): EngineProfile? {
        if (values == null || values.size != VALUE_COUNT) return null
        val id = values[0]
        val externalUserId = values[1]
        val displayNamePresent = when (values[2]) {
            "0" -> false
            "1" -> true
            else -> return null
        }
        if (id.isBlank() || externalUserId.isBlank()) return null
        val createdAt = values[4].toOptionalEpochMillis() ?: values[4].takeIf(String::isNotEmpty)?.let {
            return null
        }
        val updatedAt = values[5].toOptionalEpochMillis() ?: values[5].takeIf(String::isNotEmpty)?.let {
            return null
        }
        return EngineProfile(
            id = id,
            externalUserId = externalUserId,
            displayName = values[3].takeIf { displayNamePresent },
            createdAtEpochMillis = createdAt,
            updatedAtEpochMillis = updatedAt
        )
    }

    private fun String.toOptionalEpochMillis(): Long? = takeIf(String::isNotEmpty)?.toLongOrNull()?.takeIf { it >= 0L }
}
