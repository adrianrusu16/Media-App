package com.adrianrusu.mediaapp.core.ui.discovery

data class BambooMediaItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val action: BambooMediaAction
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
