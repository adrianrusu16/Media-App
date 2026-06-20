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
        focus = PandaWaveFocusTokens(
            outlineWidthPx = dimension(R.dimen.mediaapp_focus_outline_width),
            outlinePaddingPx = dimension(R.dimen.mediaapp_focus_outline_padding)
        ),
        components = PandaWaveComponentTokens(
            iconSmallPx = dimension(R.dimen.mediaapp_icon_size_sm),
            iconMediumPx = dimension(R.dimen.mediaapp_icon_size_md),
            iconLargePx = dimension(R.dimen.mediaapp_icon_size_lg),
            rotaryStepThresholdPx = dimension(R.dimen.mediaapp_rotary_step_threshold),
            navigationLogoSizePx = dimension(R.dimen.mediaapp_navigation_logo_size),
            navigationItemHeightPx = dimension(R.dimen.mediaapp_navigation_item_height),
            navigationItemSpacingPx = dimension(R.dimen.mediaapp_navigation_item_spacing),
            navigationSelectedIndicatorInsetPx =
                dimension(R.dimen.mediaapp_navigation_selected_indicator_inset),
            mediaTileCompactMinWidthPx = dimension(R.dimen.mediaapp_media_tile_compact_min_width),
            mediaTileCompactMaxWidthPx = dimension(R.dimen.mediaapp_media_tile_compact_max_width),
            mediaTileCompactMinHeightPx = dimension(R.dimen.mediaapp_media_tile_compact_min_height),
            mediaTileStandardMinWidthPx = dimension(R.dimen.mediaapp_media_tile_standard_min_width),
            mediaTileStandardMaxWidthPx = dimension(R.dimen.mediaapp_media_tile_standard_max_width),
            mediaTileStandardMinHeightPx = dimension(R.dimen.mediaapp_media_tile_standard_min_height),
            mediaTileCompactArtworkHeightPx =
                dimension(R.dimen.mediaapp_media_tile_compact_artwork_height),
            mediaTileStandardArtworkHeightPx =
                dimension(R.dimen.mediaapp_media_tile_standard_artwork_height),
            mediaRowArtworkSizePx = dimension(R.dimen.mediaapp_media_row_artwork_size),
            mediaRowMinHeightPx = dimension(R.dimen.mediaapp_media_row_min_height),
            categoryCardMinWidthPx = dimension(R.dimen.mediaapp_category_card_min_width),
            categoryCardMaxWidthPx = dimension(R.dimen.mediaapp_category_card_max_width),
            categoryCardMinHeightPx = dimension(R.dimen.mediaapp_category_card_min_height),
            cardPaddingPx = dimension(R.dimen.mediaapp_card_padding),
            actionableCardMinHeightPx = dimension(R.dimen.mediaapp_actionable_card_min_height),
            miniPlayerArtworkSizePx = dimension(R.dimen.mediaapp_miniplayer_artwork_size),
            miniPlayerTransportButtonSizePx =
                dimension(R.dimen.mediaapp_miniplayer_transport_button_size),
            miniPlayerInternalSpacingPx = dimension(R.dimen.mediaapp_miniplayer_internal_spacing),
            progressTrackHeightPx = dimension(R.dimen.mediaapp_progress_track_height),
            progressThumbSizePx = dimension(R.dimen.mediaapp_progress_thumb_size),
            volumeControlHeightPx = dimension(R.dimen.mediaapp_volume_control_height),
            waveformHeightPx = dimension(R.dimen.mediaapp_waveform_height),
            waveformBarWidthPx = dimension(R.dimen.mediaapp_waveform_bar_width),
            voiceIndicatorBorderWidthPx = dimension(R.dimen.mediaapp_voice_indicator_border_width),
            voiceIndicatorBarsWidthPx = dimension(R.dimen.mediaapp_voice_indicator_bars_width),
            voiceIndicatorBarsHeightPx = dimension(R.dimen.mediaapp_voice_indicator_bars_height),
            voiceBarWidthPx = dimension(R.dimen.mediaapp_voice_bar_width),
            voiceBarGapPx = dimension(R.dimen.mediaapp_voice_bar_gap),
            voiceBarIdleHeightPx = dimension(R.dimen.mediaapp_voice_bar_idle_height),
            nowPlayingSecondaryTransportSizePx =
                dimension(R.dimen.mediaapp_now_playing_secondary_transport_size),
            nowPlayingTransportSpacingPx =
                dimension(R.dimen.mediaapp_now_playing_transport_spacing),
            nowPlayingFooterHeightPx = dimension(R.dimen.mediaapp_now_playing_footer_height),
            nowPlayingQuickActionWidthPx = dimension(R.dimen.mediaapp_now_playing_quick_action_width),
            nowPlayingQuickActionHeightPx = dimension(R.dimen.mediaapp_now_playing_quick_action_height)
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
