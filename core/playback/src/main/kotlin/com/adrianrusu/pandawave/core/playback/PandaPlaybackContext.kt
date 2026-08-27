package com.adrianrusu.pandawave.core.playback

/**
 * Engine-owned collection a platform selection was made from.
 *
 * Media3 may invent opaque item IDs, but playback always names one of these
 * contexts so PandaEngine can build the queue from the selected occurrence.
 */
sealed interface PandaPlaybackContext {
    data object ForYou : PandaPlaybackContext
    data object History : PandaPlaybackContext
    data object Saved : PandaPlaybackContext
    data object Downloads : PandaPlaybackContext
    data object VoiceDefault : PandaPlaybackContext
    data class Search(val query: String) : PandaPlaybackContext
    data class Album(val albumId: String) : PandaPlaybackContext
    data class Playlist(val playlistId: String) : PandaPlaybackContext
    data class Artist(val artistId: String) : PandaPlaybackContext
    data class Browse(val parentId: String) : PandaPlaybackContext
}
