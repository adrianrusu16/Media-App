package com.adrianrusu.mediaapp.core.rust.bridge.engine

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object PandaWaveAudioSourceContract {
    const val SCHEME = "content"
    const val AUTHORITY = "com.adrianrusu.mediaapp.audio"
    const val AUDIO_PATH_SEGMENT = "audio"
    const val DEFAULT_MIME_TYPE = "audio/mpeg"

    fun sourceIdForTrack(trackId: String): String = "pandawave:${normalizedTrackId(trackId)}"

    fun sourceUriForTrack(trackId: String): String =
        "$SCHEME://$AUTHORITY/$AUDIO_PATH_SEGMENT/${normalizedTrackId(trackId).urlEncodePathSegment()}"

    fun trackIdFromSourceUri(sourceUri: String): String? {
        val uri = runCatching { URI(sourceUri) }.getOrNull() ?: return null
        if (uri.scheme != SCHEME || uri.authority != AUTHORITY) return null

        val expectedPrefix = "/$AUDIO_PATH_SEGMENT/"
        val rawPath = uri.rawPath ?: return null
        if (!rawPath.startsWith(expectedPrefix)) return null

        val rawTrackId = rawPath.removePrefix(expectedPrefix)
        if (rawTrackId.isBlank()) return null

        return URLDecoder.decode(rawTrackId, StandardCharsets.UTF_8.name()).takeUnless { trackId ->
            trackId.isBlank()
        }
    }

    private fun normalizedTrackId(trackId: String): String {
        val normalized = trackId.trim()
        require(normalized.isNotBlank()) { "PandaWave audio source track id must not be blank." }
        return normalized
    }

    private fun String.urlEncodePathSegment(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8.name()).replace("+", "%20")
}

class PandaWaveContentAudioSourceResolver(
    private val mimeType: String = PandaWaveAudioSourceContract.DEFAULT_MIME_TYPE
) : AudioSourceResolver {
    override fun resolve(trackId: String): EnginePlaybackSource = EnginePlaybackSource(
        sourceId = PandaWaveAudioSourceContract.sourceIdForTrack(trackId),
        uri = PandaWaveAudioSourceContract.sourceUriForTrack(trackId),
        mimeType = mimeType
    )
}
