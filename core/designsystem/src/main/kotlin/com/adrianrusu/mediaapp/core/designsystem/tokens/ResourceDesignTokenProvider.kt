package com.adrianrusu.mediaapp.core.designsystem.tokens

import android.content.Context
import android.content.res.Resources
import androidx.annotation.ColorRes
import com.adrianrusu.mediaapp.core.designsystem.R
import com.adrianrusu.mediaapp.core.designsystem.theme.PandaWaveThemeId

class ResourceDesignTokenProvider(context: Context) {
    private val resources: Resources = context.resources
    private val theme = context.theme

    fun load(themeId: PandaWaveThemeId): PandaWaveDesignTokens = PandaWaveDesignTokens(
        colors = PandaWaveColorTokens(
            primary = color(themeId.colorResources.primary),
            onPrimary = color(themeId.colorResources.onPrimary),
            secondary = color(themeId.colorResources.secondary),
            onSecondary = color(themeId.colorResources.onSecondary),
            surface = color(themeId.colorResources.surface),
            onSurface = color(themeId.colorResources.onSurface),
            surfaceVariant = color(themeId.colorResources.surfaceVariant),
            onSurfaceVariant = color(themeId.colorResources.onSurfaceVariant),
            error = color(themeId.colorResources.error),
            onError = color(themeId.colorResources.onError)
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
        sizing = PandaWaveSizingTokens(
            touchTargetMdPx = dimension(R.dimen.mediaapp_touch_target_md),
            touchTargetLgPx = dimension(R.dimen.mediaapp_touch_target_lg)
        ),
        layout = PandaWaveLayoutTokens(
            appContentPaddingPx = dimension(R.dimen.mediaapp_app_content_padding),
            navigationRailWidthPx = dimension(R.dimen.mediaapp_navigation_rail_width),
            navigationSelectedIndicatorWidthPx =
                dimension(R.dimen.mediaapp_navigation_selected_indicator_width),
            navigationSelectedIndicatorHeightPx =
                dimension(R.dimen.mediaapp_navigation_selected_indicator_height),
            nowPlayingArtworkCompactPx = dimension(R.dimen.mediaapp_now_playing_artwork_compact),
            nowPlayingArtworkStandardPx = dimension(R.dimen.mediaapp_now_playing_artwork_standard),
            nowPlayingPrimaryButtonPx = dimension(R.dimen.mediaapp_now_playing_primary_button),
            nowPlayingCompactHeightThresholdPx =
                dimension(R.dimen.mediaapp_now_playing_compact_height_threshold),
            nowPlayingScrollHeightThresholdPx =
                dimension(R.dimen.mediaapp_now_playing_scroll_height_threshold)
        ),
        elevation = PandaWaveElevationTokens(
            cardRestingPx = dimension(R.dimen.mediaapp_elevation_card_resting)
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

    private fun color(@ColorRes id: Int): Int = resources.getColor(id, theme)

    private fun dimension(id: Int): Int = resources.getDimensionPixelSize(id)
}

private data class ColorTokenResourceIds(
    @param:ColorRes val primary: Int,
    @param:ColorRes val onPrimary: Int,
    @param:ColorRes val secondary: Int,
    @param:ColorRes val onSecondary: Int,
    @param:ColorRes val surface: Int,
    @param:ColorRes val onSurface: Int,
    @param:ColorRes val surfaceVariant: Int,
    @param:ColorRes val onSurfaceVariant: Int,
    @param:ColorRes val error: Int,
    @param:ColorRes val onError: Int
)

private val PandaWaveThemeId.colorResources: ColorTokenResourceIds
    get() = when (this) {
        PandaWaveThemeId.BambooGroveLight -> ColorTokenResourceIds(
            primary = R.color.mediaapp_theme_bamboo_grove_light_color_brand_primary,
            onPrimary = R.color.mediaapp_theme_bamboo_grove_light_color_brand_on_primary,
            secondary = R.color.mediaapp_theme_bamboo_grove_light_color_brand_secondary,
            onSecondary = R.color.mediaapp_theme_bamboo_grove_light_color_brand_on_secondary,
            surface = R.color.mediaapp_theme_bamboo_grove_light_color_surface,
            onSurface = R.color.mediaapp_theme_bamboo_grove_light_color_on_surface,
            surfaceVariant = R.color.mediaapp_theme_bamboo_grove_light_color_surface_variant,
            onSurfaceVariant = R.color.mediaapp_theme_bamboo_grove_light_color_on_surface_variant,
            error = R.color.mediaapp_theme_bamboo_grove_light_color_error,
            onError = R.color.mediaapp_theme_bamboo_grove_light_color_on_error
        )

        PandaWaveThemeId.MoonlitBambooDark -> ColorTokenResourceIds(
            primary = R.color.mediaapp_theme_moonlit_bamboo_dark_color_brand_primary,
            onPrimary = R.color.mediaapp_theme_moonlit_bamboo_dark_color_brand_on_primary,
            secondary = R.color.mediaapp_theme_moonlit_bamboo_dark_color_brand_secondary,
            onSecondary = R.color.mediaapp_theme_moonlit_bamboo_dark_color_brand_on_secondary,
            surface = R.color.mediaapp_theme_moonlit_bamboo_dark_color_surface,
            onSurface = R.color.mediaapp_theme_moonlit_bamboo_dark_color_on_surface,
            surfaceVariant = R.color.mediaapp_theme_moonlit_bamboo_dark_color_surface_variant,
            onSurfaceVariant = R.color.mediaapp_theme_moonlit_bamboo_dark_color_on_surface_variant,
            error = R.color.mediaapp_theme_moonlit_bamboo_dark_color_error,
            onError = R.color.mediaapp_theme_moonlit_bamboo_dark_color_on_error
        )

        PandaWaveThemeId.ForestTechLight -> ColorTokenResourceIds(
            primary = R.color.mediaapp_theme_forest_tech_light_color_brand_primary,
            onPrimary = R.color.mediaapp_theme_forest_tech_light_color_brand_on_primary,
            secondary = R.color.mediaapp_theme_forest_tech_light_color_brand_secondary,
            onSecondary = R.color.mediaapp_theme_forest_tech_light_color_brand_on_secondary,
            surface = R.color.mediaapp_theme_forest_tech_light_color_surface,
            onSurface = R.color.mediaapp_theme_forest_tech_light_color_on_surface,
            surfaceVariant = R.color.mediaapp_theme_forest_tech_light_color_surface_variant,
            onSurfaceVariant = R.color.mediaapp_theme_forest_tech_light_color_on_surface_variant,
            error = R.color.mediaapp_theme_forest_tech_light_color_error,
            onError = R.color.mediaapp_theme_forest_tech_light_color_on_error
        )

        PandaWaveThemeId.ForestTechDark -> ColorTokenResourceIds(
            primary = R.color.mediaapp_theme_forest_tech_dark_color_brand_primary,
            onPrimary = R.color.mediaapp_theme_forest_tech_dark_color_brand_on_primary,
            secondary = R.color.mediaapp_theme_forest_tech_dark_color_brand_secondary,
            onSecondary = R.color.mediaapp_theme_forest_tech_dark_color_brand_on_secondary,
            surface = R.color.mediaapp_theme_forest_tech_dark_color_surface,
            onSurface = R.color.mediaapp_theme_forest_tech_dark_color_on_surface,
            surfaceVariant = R.color.mediaapp_theme_forest_tech_dark_color_surface_variant,
            onSurfaceVariant = R.color.mediaapp_theme_forest_tech_dark_color_on_surface_variant,
            error = R.color.mediaapp_theme_forest_tech_dark_color_error,
            onError = R.color.mediaapp_theme_forest_tech_dark_color_on_error
        )
    }
