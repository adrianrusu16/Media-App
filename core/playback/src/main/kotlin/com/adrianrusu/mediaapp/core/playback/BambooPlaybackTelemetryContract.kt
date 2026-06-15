package com.adrianrusu.mediaapp.core.playback

object BambooPlaybackIntentNames {
    const val REFRESH = "refresh"
    const val PLAY = "play"
    const val PAUSE = "pause"
    const val TOGGLE_PLAYBACK = "toggle_playback"
    const val SKIP_PREVIOUS = "skip_previous"
    const val SKIP_NEXT = "skip_next"
    const val SEEK_TO = "seek_to"
    const val SET_SPEED = "set_speed"
    const val SEARCH_CATALOG = "search_catalog"
    const val BROWSE_CATALOG = "browse_catalog"
    const val PLATFORM_EVENT = "platform_event"

    fun from(intent: BambooPlaybackIntent): String = when (intent) {
        BambooPlaybackIntent.Refresh -> REFRESH
        BambooPlaybackIntent.Play -> PLAY
        BambooPlaybackIntent.Pause -> PAUSE
        BambooPlaybackIntent.TogglePlayback -> TOGGLE_PLAYBACK
        BambooPlaybackIntent.SkipPrevious -> SKIP_PREVIOUS
        BambooPlaybackIntent.SkipNext -> SKIP_NEXT
        is BambooPlaybackIntent.SeekTo -> SEEK_TO
        is BambooPlaybackIntent.SetSpeed -> SET_SPEED
        is BambooPlaybackIntent.SearchCatalog -> SEARCH_CATALOG
        is BambooPlaybackIntent.BrowseCatalog -> BROWSE_CATALOG
        is BambooPlaybackIntent.PlatformEvent -> PLATFORM_EVENT
    }
}

object BambooPlaybackTelemetryAttributes {
    const val COMMAND_TYPE = "command_type"
    const val ENGINE_STATUS = "engine_status"
    const val EVENT_TYPE = "event_type"
    const val INTENT = "intent"
}

val BambooPlaybackIntent.telemetryName: String
    get() = BambooPlaybackIntentNames.from(this)
