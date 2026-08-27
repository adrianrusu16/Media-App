package com.adrianrusu.pandawave.core.ui.discovery

import com.adrianrusu.pandawave.core.ui.artwork.BambooArtworkFallback
import com.adrianrusu.pandawave.core.ui.artwork.BambooArtworkModel

data class BambooMediaItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val action: BambooMediaAction,
    val artwork: BambooArtworkModel? = null,
    val artworkFallback: BambooArtworkFallback = BambooArtworkFallback.Track
)

enum class BambooMediaAction {
    Play,
    Navigate,
    Unavailable
}

data class BambooCategoryItem(val id: String, val title: String, val description: String, val enabled: Boolean = true)

data class BambooFilterOption(val id: String, val label: String, val selected: Boolean) {
    companion object {
        fun items(selectedId: String, labels: List<Pair<String, String>>): List<BambooFilterOption> =
            labels.map { (id, label) ->
                BambooFilterOption(
                    id = id,
                    label = label,
                    selected = id == selectedId
                )
            }
    }
}
