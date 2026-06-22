package com.adrianrusu.pandawave.core.rust.bridge.aidl

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

object EngineCommandPayloads {
    const val DEFAULT_BROWSE_PARENT_ID = "root"

    private const val DEFAULT_PLAYBACK_SPEED = 1F
    private const val MIN_PLAYBACK_SPEED = 0F
    private const val MIN_POSITION_MILLIS = 0L

    fun seekPositionMillis(positionMillis: Long): String = positionMillis
        .coerceAtLeast(MIN_POSITION_MILLIS)
        .toString()

    fun playbackSpeed(speed: Float): String = speed
        .coerceAtLeast(MIN_PLAYBACK_SPEED)
        .toString()

    fun searchQuery(query: String): String = query

    fun browseParentId(parentId: String): String = parentId.ifBlank { DEFAULT_BROWSE_PARENT_ID }

    fun mediaId(mediaId: String): String = mediaId.trim()

    fun themePreference(themeId: String): String = buildThemePreferencePayload(themeId).toString()

    fun remoteThemePreference(themeId: String, userId: String, baselineRevision: Long): String =
        buildThemePreferencePayload(themeId) {
            put(KEY_USER_ID, userId)
            put(KEY_BASELINE_REVISION, baselineRevision.coerceAtLeast(0L))
        }.toString()

    fun parseSeekPositionMillis(payload: String?): Long = payload
        ?.toLongOrNull()
        ?.coerceAtLeast(MIN_POSITION_MILLIS)
        ?: MIN_POSITION_MILLIS

    fun parsePlaybackSpeed(payload: String?): Float = payload
        ?.toFloatOrNull()
        ?.coerceAtLeast(MIN_PLAYBACK_SPEED)
        ?: DEFAULT_PLAYBACK_SPEED

    fun parseSearchQuery(payload: String?): String = payload.orEmpty()

    fun parseBrowseParentId(payload: String?): String = payload
        ?.takeIf { parentId -> parentId.isNotBlank() }
        ?: DEFAULT_BROWSE_PARENT_ID

    fun parseMediaId(payload: String?): String = payload.orEmpty().trim()

    fun parseThemePreference(payload: String?): ParsedThemePreference? = runCatching {
        val values = Json.parseToJsonElement(payload.orEmpty()).jsonObject
        if (values[KEY_VERSION]?.jsonPrimitive?.longOrNull != PAYLOAD_VERSION.toLong()) return null

        ParsedThemePreference(
            themeId = values[KEY_THEME_ID]?.jsonPrimitive?.content.orEmpty(),
            userId = values[KEY_USER_ID]?.jsonPrimitive?.content,
            baselineRevision = values[KEY_BASELINE_REVISION]?.jsonPrimitive?.longOrNull
        ).takeIf { parsed -> parsed.themeId.isNotBlank() }
    }.getOrNull()

    private fun buildThemePreferencePayload(
        themeId: String,
        additionalValues: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {}
    ) = buildJsonObject {
        put(KEY_VERSION, PAYLOAD_VERSION)
        put(KEY_THEME_ID, themeId)
        additionalValues()
    }

    data class ParsedThemePreference(val themeId: String, val userId: String?, val baselineRevision: Long?)

    private const val PAYLOAD_VERSION = 1
    private const val KEY_VERSION = "version"
    private const val KEY_THEME_ID = "theme_id"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_BASELINE_REVISION = "baseline_revision"
}
