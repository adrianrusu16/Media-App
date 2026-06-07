package com.adrianrusu.mediaapp.core.designsystem.tokens

import android.content.Context
import android.content.res.Resources
import com.adrianrusu.mediaapp.core.designsystem.R

class ResourceDesignTokenProvider(context: Context) {
    private val resources: Resources = context.resources
    private val theme = context.theme

    fun load(): PandaWaveDesignTokens = PandaWaveDesignTokens(
        colors = PandaWaveColorTokens(
            primary = color(R.color.mediaapp_color_brand_primary),
            onPrimary = color(R.color.mediaapp_color_brand_on_primary),
            secondary = color(R.color.mediaapp_color_brand_secondary),
            onSecondary = color(R.color.mediaapp_color_brand_on_secondary),
            surface = color(R.color.mediaapp_color_surface),
            onSurface = color(R.color.mediaapp_color_on_surface),
            surfaceVariant = color(R.color.mediaapp_color_surface_variant),
            onSurfaceVariant = color(R.color.mediaapp_color_on_surface_variant),
            error = color(R.color.mediaapp_color_error),
            onError = color(R.color.mediaapp_color_on_error)
        ),
        spacing = PandaWaveSpacingTokens(
            xsPx = dimension(R.dimen.mediaapp_spacing_xs),
            smPx = dimension(R.dimen.mediaapp_spacing_sm),
            mdPx = dimension(R.dimen.mediaapp_spacing_md),
            lgPx = dimension(R.dimen.mediaapp_spacing_lg),
            xlPx = dimension(R.dimen.mediaapp_spacing_xl)
        ),
        shape = PandaWaveShapeTokens(
            smallCornerPx = dimension(R.dimen.mediaapp_shape_corner_sm),
            mediumCornerPx = dimension(R.dimen.mediaapp_shape_corner_md),
            miniPlayerHeightPx = dimension(R.dimen.mediaapp_miniplayer_height)
        ),
        restrictions = PandaWaveRestrictionTokens(
            maxBrowseColumnsUnrestricted =
                resources.getInteger(R.integer.mediaapp_max_browse_columns_unrestricted),
            maxBrowseColumnsRestricted =
                resources.getInteger(R.integer.mediaapp_max_browse_columns_restricted),
            maxVisibleActionsRestricted =
                resources.getInteger(R.integer.mediaapp_max_visible_actions_restricted)
        )
    )

    private fun color(id: Int): Int = resources.getColor(id, theme)

    private fun dimension(id: Int): Int = resources.getDimensionPixelSize(id)
}
