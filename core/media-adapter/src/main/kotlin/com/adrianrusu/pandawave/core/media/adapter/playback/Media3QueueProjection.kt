package com.adrianrusu.pandawave.core.media.adapter.playback

internal data class Media3QueueItem(
    val queueItemId: String,
    val mediaId: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val artworkUri: String? = null,
    val durationMs: Long? = null
)

/**
 * Metadata-only projection of PandaEngine's queue into Media3.
 *
 * Source URLs never live here. The custom Player exposes this as its timeline;
 * ExoPlayer remains a one-item playback sink.
 */
internal class Media3QueueProjection {
    @Volatile
    var generation: Long = 0L
        private set

    @Volatile
    var currentIndex: Int = 0
        private set

    private val lock = Any()
    private var items: List<Media3QueueItem> = emptyList()

    val size: Int
        get() = synchronized(lock) { items.size }

    fun snapshot(): List<Media3QueueItem> = synchronized(lock) { items }

    fun current(): Media3QueueItem? = synchronized(lock) { items.getOrNull(currentIndex) }

    fun replace(nextItems: List<Media3QueueItem>, currentIndex: Int, generation: Long = this.generation + 1L) {
        synchronized(lock) {
            items = nextItems
            this.currentIndex = currentIndex.coerceIn(0, (nextItems.size - 1).coerceAtLeast(0))
            this.generation = generation
        }
    }

    fun moveCursor(currentIndex: Int) {
        synchronized(lock) {
            if (items.isEmpty()) return
            this.currentIndex = currentIndex.coerceIn(0, items.lastIndex)
        }
    }

    fun alignToMediaId(mediaId: String?) {
        val id = mediaId?.trim()?.takeIf(String::isNotBlank) ?: return
        synchronized(lock) {
            val match = items.indexOfFirst { item -> item.mediaId == id || item.queueItemId == id }
            if (match >= 0) {
                currentIndex = match
            }
        }
    }

    fun hasSeekableTimeline(): Boolean = size > 1
}
