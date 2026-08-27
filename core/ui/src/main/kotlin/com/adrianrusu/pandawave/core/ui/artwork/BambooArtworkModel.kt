package com.adrianrusu.pandawave.core.ui.artwork

import androidx.compose.runtime.Immutable
import com.adrianrusu.pandawave.core.common.log.PandaLog

@Immutable
data class BambooArtworkModel(
    val id: String,
    val version: String,
    val uri: String
) {
    fun cacheKey(): String = "$id:$version"
}

sealed interface BambooArtworkFallback {
    data object Track : BambooArtworkFallback
    data object Album : BambooArtworkFallback
    data object Artist : BambooArtworkFallback
    data object Playlist : BambooArtworkFallback
    data object Radio : BambooArtworkFallback
}

fun Triple<String?, String?, String?>.toBambooArtworkModel(): BambooArtworkModel? {
    val (id, version, uri) = this
    val hasId = !id.isNullOrBlank()
    val hasVersion = !version.isNullOrBlank()
    val hasUri = !uri.isNullOrBlank()
    if (!hasId && !hasVersion && !hasUri) {
        return null
    }
    if (!hasId || !hasVersion || !hasUri) {
        PandaLog.w(PandaLog.Tag.ARTWORK) {
            "artwork.model_incomplete hasId=$hasId hasVersion=$hasVersion hasUri=$hasUri " +
                "id=${PandaLog.field(id)} version=${PandaLog.field(version)} " +
                "uri=${uri?.let(::artworkUriForLog).orEmpty()}"
        }
        return null
    }
    return BambooArtworkModel(id = id.trim(), version = version.trim(), uri = uri.trim())
}

fun toBambooArtworkModel(id: String?, version: String?, uri: String?): BambooArtworkModel? =
    Triple(id, version, uri).toBambooArtworkModel()
