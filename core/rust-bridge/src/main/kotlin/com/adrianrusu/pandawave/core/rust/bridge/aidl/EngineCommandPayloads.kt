package com.adrianrusu.pandawave.core.rust.bridge.aidl

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

object EngineCommandPayloads {
    const val DEFAULT_BROWSE_PARENT_ID = "root"
    const val DEFAULT_SESSION_USER_ID = "guest"

    private const val DEFAULT_PLAYBACK_SPEED = 1F
    private const val MIN_PLAYBACK_SPEED = 0F
    private const val MIN_POSITION_MILLIS = 0L

    fun seekPositionMillis(positionMillis: Long): String = positionMillis
        .coerceAtLeast(MIN_POSITION_MILLIS)
        .toString()

    fun playbackSpeed(speed: Float): String = speed
        .coerceAtLeast(MIN_PLAYBACK_SPEED)
        .toString()

    fun searchCatalog(query: String, pageSize: Int = DEFAULT_CATALOG_PAGE_SIZE): String = buildJsonObject {
        put(KEY_VERSION, PAYLOAD_VERSION)
        put(KEY_QUERY, query)
        putJsonObject(KEY_PAGE) { put(KEY_PAGE_SIZE, pageSize.coerceAtLeast(0)) }
    }.toString()

    fun browseCatalog(
        parentId: String?,
        genres: List<String> = emptyList(),
        pageSize: Int = DEFAULT_CATALOG_PAGE_SIZE
    ): String = buildJsonObject {
        put(KEY_VERSION, PAYLOAD_VERSION)
        put(KEY_PARENT_ID, parentId?.takeIf(String::isNotBlank) ?: DEFAULT_BROWSE_PARENT_ID)
        put(KEY_GENRES, buildJsonArray { genres.forEach { genre -> add(genre) } })
        putJsonObject(KEY_PAGE) { put(KEY_PAGE_SIZE, pageSize.coerceAtLeast(0)) }
    }.toString()

    fun loadNextCatalogPage(operationId: String): String = buildJsonObject {
        put(KEY_VERSION, PAYLOAD_VERSION)
        put(KEY_OPERATION_ID, operationId)
    }.toString()

    fun searchQuery(query: String): String = searchCatalog(query)

    fun browseParentId(parentId: String): String = browseCatalog(parentId)

    fun mediaId(mediaId: String): String = mediaId.trim()

    fun themePreference(themeId: String): String = buildThemePreferencePayload(themeId).toString()

    fun upsertProfile(displayName: String?): String = buildJsonObject {
        put(KEY_VERSION, PAYLOAD_VERSION)
        put(KEY_DISPLAY_NAME, displayName)
    }.toString()

    fun updateProfileDisplayName(displayName: String?): String = buildJsonObject {
        put(KEY_VERSION, PAYLOAD_VERSION)
        put(KEY_UPDATE_DISPLAY_NAME, true)
        put(KEY_DISPLAY_NAME, displayName)
    }.toString()

    fun historyEnabled(enabled: Boolean): String = buildJsonObject {
        put(KEY_VERSION, PAYLOAD_VERSION)
        put(KEY_ENABLED, enabled)
    }.toString()

    fun historyEntry(historyId: String): String = buildJsonObject {
        put(KEY_VERSION, PAYLOAD_VERSION)
        put(KEY_HISTORY_ID, historyId.trim())
    }.toString()

    fun historyPage(pageSize: Int): String = buildJsonObject {
        put(KEY_VERSION, PAYLOAD_VERSION)
        putJsonObject(KEY_PAGE) { put(KEY_PAGE_SIZE, pageSize.coerceAtLeast(0)) }
    }.toString()

    fun playbackCompleted(trackId: String, durationMillis: Long, completionRatio: Double): String =
        buildJsonObject {
            put(KEY_VERSION, PAYLOAD_VERSION)
            put(KEY_TRACK_ID, trackId.trim())
            put(KEY_DURATION_MILLIS, durationMillis.coerceAtLeast(0L))
            put(KEY_COMPLETION_RATIO, completionRatio.takeIf(Double::isFinite)?.coerceIn(0.0, 1.0) ?: 0.0)
        }.toString()

    fun updateProfileTheme(themeId: String): String = buildJsonObject {
        put(KEY_VERSION, PAYLOAD_VERSION)
        putJsonObject(KEY_VALUES) {
            put(KEY_THEME, themeId)
        }
    }.toString()

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

    fun parseSearchQuery(payload: String?): String = parseCatalogObject(payload)
        ?.get(KEY_QUERY)
        ?.jsonPrimitive
        ?.content
        .orEmpty()

    fun parseBrowseParentId(payload: String?): String = parseCatalogObject(payload)
        ?.get(KEY_PARENT_ID)
        ?.jsonPrimitive
        ?.content
        ?.takeIf(String::isNotBlank)
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

    private fun parseCatalogObject(payload: String?) = runCatching {
        Json.parseToJsonElement(payload.orEmpty()).jsonObject
    }.getOrNull()?.takeIf { values ->
        values[KEY_VERSION]?.jsonPrimitive?.longOrNull == PAYLOAD_VERSION.toLong()
    }

    private const val PAYLOAD_VERSION = 1
    private const val DEFAULT_CATALOG_PAGE_SIZE = 0
    private const val KEY_VERSION = "version"
    private const val KEY_THEME_ID = "theme_id"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_BASELINE_REVISION = "baseline_revision"
    private const val KEY_DISPLAY_NAME = "display_name"
    private const val KEY_UPDATE_DISPLAY_NAME = "update_display_name"
    private const val KEY_VALUES = "values"
    private const val KEY_THEME = "theme"
    private const val KEY_QUERY = "query"
    private const val KEY_PARENT_ID = "parent_id"
    private const val KEY_GENRES = "genres"
    private const val KEY_PAGE = "page"
    private const val KEY_PAGE_SIZE = "page_size"
    private const val KEY_OPERATION_ID = "operation_id"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_HISTORY_ID = "history_id"
    private const val KEY_TRACK_ID = "track_id"
    private const val KEY_DURATION_MILLIS = "duration_ms"
    private const val KEY_COMPLETION_RATIO = "completion_ratio"
}
