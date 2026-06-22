package com.adrianrusu.pandawave.core.rust.bridge.engine

/**
 * Android-side provider for playable source descriptors requested by PandaEngine.
 */
fun interface AudioSourceResolver {
    fun resolve(trackId: String): EnginePlaybackSource
}

data class EnginePlaybackSource(
    val sourceId: String,
    val uri: String,
    val mimeType: String? = null,
    val expectedDurationMillis: Long? = null
) {
    init {
        require(sourceId.isNotBlank()) { "Engine playback source id must not be blank." }
        require(uri.isNotBlank()) { "Engine playback source uri must not be blank." }
        require(expectedDurationMillis == null || expectedDurationMillis >= 0L) {
            "Engine playback source duration must not be negative."
        }
    }
}

object AudioSourceResolvers {
    fun pandaWaveContent(): AudioSourceResolver = PandaWaveContentAudioSourceResolver()

    fun unavailable(): AudioSourceResolver = UnavailableAudioSourceResolver
}

private object UnavailableAudioSourceResolver : AudioSourceResolver {
    override fun resolve(trackId: String): EnginePlaybackSource =
        error("No AudioSourceResolver configured for PandaEngine trackId=$trackId.")
}
