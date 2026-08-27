package com.adrianrusu.pandawave.core.model.catalog

data class BambooCatalogNode(
    val mediaId: String,
    val title: String,
    val subtitle: String? = null,
    val artworkUri: String? = null,
    val isBrowsable: Boolean,
    val isPlayable: Boolean,
    val artist: String? = null,
    val album: String? = null,
    val catalogItemType: Int? = null,
    val artworkId: String? = null,
    val artworkVersion: String? = null,
    val durationMillis: Long? = null,
    val engineMediaId: String? = null
) {
    init {
        require(mediaId.isNotBlank()) {
            "Catalog node mediaId must not be blank."
        }
        require(title.isNotBlank()) {
            "Catalog node title must not be blank."
        }
        require(isBrowsable || isPlayable) {
            "Catalog node must be browsable, playable, or both."
        }
    }
}
