package com.adrianrusu.pandawave.core.playback

sealed interface BambooPlaybackIntent {
    data object Refresh : BambooPlaybackIntent
    data object Play : BambooPlaybackIntent
    data object Pause : BambooPlaybackIntent
    data object TogglePlayback : BambooPlaybackIntent
    data object SkipPrevious : BambooPlaybackIntent
    data object SkipNext : BambooPlaybackIntent
    data class SeekTo(val positionMillis: Long) : BambooPlaybackIntent
    data class SetSpeed(val speed: Float) : BambooPlaybackIntent
    data class PlayMedia(val mediaId: String) : BambooPlaybackIntent
    data class PlayQueue(val mediaIds: List<String>, val startIndex: Int) : BambooPlaybackIntent
    data class SearchCatalog(val query: String) : BambooPlaybackIntent
    data class BrowseCatalog(val parentId: String) : BambooPlaybackIntent
    data class LoadNextCatalogPage(val operationId: String) : BambooPlaybackIntent

    /**
     * Platform-level event that should be processed by the engine.
     */
    data class PlatformEvent(val type: String, val payload: String? = null) : BambooPlaybackIntent
}
