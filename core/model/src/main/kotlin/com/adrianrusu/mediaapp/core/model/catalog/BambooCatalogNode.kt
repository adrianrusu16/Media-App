package com.adrianrusu.mediaapp.core.model.catalog

data class BambooCatalogNode(
    val mediaId: String,
    val title: String,
    val subtitle: String? = null,
    val isBrowsable: Boolean,
    val isPlayable: Boolean
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
