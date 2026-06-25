package com.adrianrusu.pandawave.core.designsystem.tokens

import android.content.Context
import android.content.res.Resources
import androidx.annotation.ColorRes
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.adrianrusu.pandawave.core.designsystem.R
import com.adrianrusu.pandawave.core.designsystem.theme.PandaWaveThemeId

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
            ambientVisualizerActive = color(R.color.pandawave_ambient_visualizer_active),
            ambientVisualizerIdle = color(R.color.pandawave_ambient_visualizer_idle),
            ambientVisualizerIdleAlpha =
                fraction(R.fraction.pandawave_ambient_visualizer_idle_alpha),
            ambientVisualizerActiveMinAlpha =
                fraction(R.fraction.pandawave_ambient_visualizer_active_min_alpha),
            ambientVisualizerActiveMaxAlpha =
                fraction(R.fraction.pandawave_ambient_visualizer_active_max_alpha),
            error = color(themeId.colorResources.error),
            onError = color(themeId.colorResources.onError)
        ),
        typography = PandaWaveTypographyTokens(
            display = textStyle(
                size = R.dimen.pandawave_type_display_size,
                lineHeight = R.dimen.pandawave_type_display_line_height,
                weight = R.integer.pandawave_type_display_weight
            ),
            sectionTitle = textStyle(
                size = R.dimen.pandawave_type_section_title_size,
                lineHeight = R.dimen.pandawave_type_section_title_line_height,
                weight = R.integer.pandawave_type_section_title_weight
            ),
            body = textStyle(
                size = R.dimen.pandawave_type_body_size,
                lineHeight = R.dimen.pandawave_type_body_line_height,
                weight = R.integer.pandawave_type_body_weight
            ),
            metadata = textStyle(
                size = R.dimen.pandawave_type_metadata_size,
                lineHeight = R.dimen.pandawave_type_metadata_line_height,
                weight = R.integer.pandawave_type_metadata_weight
            ),
            controlLabel = textStyle(
                size = R.dimen.pandawave_type_control_label_size,
                lineHeight = R.dimen.pandawave_type_control_label_line_height,
                weight = R.integer.pandawave_type_control_label_weight
            )
        ),
        spacing = PandaWaveSpacingTokens(
            xsPx = dimension(R.dimen.pandawave_spacing_xs),
            smPx = dimension(R.dimen.pandawave_spacing_sm),
            mdPx = dimension(R.dimen.pandawave_spacing_md),
            lgPx = dimension(R.dimen.pandawave_spacing_lg),
            xlPx = dimension(R.dimen.pandawave_spacing_xl)
        ),
        shape = PandaWaveShapeTokens(
            smallCornerPx = dimension(R.dimen.pandawave_shape_corner_sm),
            mediumCornerPx = dimension(R.dimen.pandawave_shape_corner_md),
            miniPlayerHeightPx = dimension(R.dimen.pandawave_miniplayer_height)
        ),
        sizing = PandaWaveSizingTokens(
            touchTargetMdPx = dimension(R.dimen.pandawave_touch_target_md),
            touchTargetLgPx = dimension(R.dimen.pandawave_touch_target_lg)
        ),
        focus = PandaWaveFocusTokens(
            outlineWidthPx = dimension(R.dimen.pandawave_focus_outline_width),
            outlinePaddingPx = dimension(R.dimen.pandawave_focus_outline_padding)
        ),
        components = PandaWaveComponentTokens(
            iconSmallPx = dimension(R.dimen.pandawave_icon_size_sm),
            iconMediumPx = dimension(R.dimen.pandawave_icon_size_md),
            iconLargePx = dimension(R.dimen.pandawave_icon_size_lg),
            rotaryStepThresholdPx = dimension(R.dimen.pandawave_rotary_step_threshold),
            navigationLogoSizePx = dimension(R.dimen.pandawave_navigation_logo_size),
            navigationItemHeightPx = dimension(R.dimen.pandawave_navigation_item_height),
            navigationItemSpacingPx = dimension(R.dimen.pandawave_navigation_item_spacing),
            navigationSelectedIndicatorInsetPx =
                dimension(R.dimen.pandawave_navigation_selected_indicator_inset),
            navigationSelectedIndicatorCornerPx =
                dimension(R.dimen.pandawave_navigation_selected_indicator_corner),
            mediaSectionSpacingPx = dimension(R.dimen.pandawave_media_section_spacing),
            mediaCarouselSpacingPx = dimension(R.dimen.pandawave_media_carousel_spacing),
            mediaTileCompactMinWidthPx = dimension(R.dimen.pandawave_media_tile_compact_min_width),
            mediaTileCompactMaxWidthPx = dimension(R.dimen.pandawave_media_tile_compact_max_width),
            mediaTileCompactMinHeightPx = dimension(R.dimen.pandawave_media_tile_compact_min_height),
            mediaTileStandardMinWidthPx = dimension(R.dimen.pandawave_media_tile_standard_min_width),
            mediaTileStandardMaxWidthPx = dimension(R.dimen.pandawave_media_tile_standard_max_width),
            mediaTileStandardMinHeightPx = dimension(R.dimen.pandawave_media_tile_standard_min_height),
            mediaTileHeroMinWidthPx = dimension(R.dimen.pandawave_media_tile_hero_min_width),
            mediaTileHeroMaxWidthPx = dimension(R.dimen.pandawave_media_tile_hero_max_width),
            mediaTileHeroMinHeightPx = dimension(R.dimen.pandawave_media_tile_hero_min_height),
            mediaTileCompactArtworkHeightPx =
                dimension(R.dimen.pandawave_media_tile_compact_artwork_height),
            mediaTileStandardArtworkHeightPx =
                dimension(R.dimen.pandawave_media_tile_standard_artwork_height),
            mediaTileHeroArtworkHeightPx =
                dimension(R.dimen.pandawave_media_tile_hero_artwork_height),
            mediaRowArtworkSizePx = dimension(R.dimen.pandawave_media_row_artwork_size),
            mediaRowMinHeightPx = dimension(R.dimen.pandawave_media_row_min_height),
            categoryCardMinWidthPx = dimension(R.dimen.pandawave_category_card_min_width),
            categoryCardMaxWidthPx = dimension(R.dimen.pandawave_category_card_max_width),
            categoryCardMinHeightPx = dimension(R.dimen.pandawave_category_card_min_height),
            cardPaddingPx = dimension(R.dimen.pandawave_card_padding),
            actionableCardMinHeightPx = dimension(R.dimen.pandawave_actionable_card_min_height),
            preferenceRowMinHeightPx = dimension(R.dimen.pandawave_preference_row_min_height),
            preferenceContentPaddingPx = dimension(R.dimen.pandawave_preference_content_padding),
            preferenceIconSizePx = dimension(R.dimen.pandawave_preference_icon_size),
            preferenceControlWidthPx = dimension(R.dimen.pandawave_preference_control_width),
            miniPlayerArtworkSizePx = dimension(R.dimen.pandawave_miniplayer_artwork_size),
            miniPlayerTransportButtonSizePx =
                dimension(R.dimen.pandawave_miniplayer_transport_button_size),
            miniPlayerInternalSpacingPx = dimension(R.dimen.pandawave_miniplayer_internal_spacing),
            progressTrackHeightPx = dimension(R.dimen.pandawave_progress_track_height),
            progressThumbSizePx = dimension(R.dimen.pandawave_progress_thumb_size),
            volumeControlHeightPx = dimension(R.dimen.pandawave_volume_control_height),
            volumeControlMaxWidthPx = dimension(R.dimen.pandawave_volume_control_max_width),
            waveformHeightPx = dimension(R.dimen.pandawave_waveform_height),
            waveformBarWidthPx = dimension(R.dimen.pandawave_waveform_bar_width),
            voiceIndicatorBorderWidthPx = dimension(R.dimen.pandawave_voice_indicator_border_width),
            voiceIndicatorBarsWidthPx = dimension(R.dimen.pandawave_voice_indicator_bars_width),
            voiceIndicatorBarsHeightPx = dimension(R.dimen.pandawave_voice_indicator_bars_height),
            voiceBarWidthPx = dimension(R.dimen.pandawave_voice_bar_width),
            voiceBarGapPx = dimension(R.dimen.pandawave_voice_bar_gap),
            voiceBarIdleHeightPx = dimension(R.dimen.pandawave_voice_bar_idle_height),
            ambientArtworkMinSizePx = dimension(R.dimen.pandawave_ambient_artwork_min_size),
            ambientArtworkMaxSizePx = dimension(R.dimen.pandawave_ambient_artwork_max_size),
            ambientVisualizerHeightPx = dimension(R.dimen.pandawave_ambient_visualizer_height),
            ambientVisualizerBarWidthPx = dimension(R.dimen.pandawave_ambient_visualizer_bar_width),
            ambientVisualizerBarGapPx = dimension(R.dimen.pandawave_ambient_visualizer_bar_gap),
            ambientVisualizerBarRadiusPx = dimension(R.dimen.pandawave_ambient_visualizer_bar_radius),
            ambientVisualizerMinBarHeightPx =
                dimension(R.dimen.pandawave_ambient_visualizer_min_bar_height),
            ambientVisualizerMaxBarHeightPx =
                dimension(R.dimen.pandawave_ambient_visualizer_max_bar_height),
            nowPlayingSecondaryTransportSizePx =
                dimension(R.dimen.pandawave_now_playing_secondary_transport_size),
            nowPlayingTransportSpacingPx =
                dimension(R.dimen.pandawave_now_playing_transport_spacing),
            nowPlayingFooterHeightPx = dimension(R.dimen.pandawave_now_playing_footer_height),
            nowPlayingQuickActionWidthPx = dimension(R.dimen.pandawave_now_playing_quick_action_width),
            nowPlayingQuickActionHeightPx = dimension(R.dimen.pandawave_now_playing_quick_action_height),
            feedbackIconSizePx = dimension(R.dimen.pandawave_feedback_icon_size),
            feedbackMaxWidthPx = dimension(R.dimen.pandawave_feedback_max_width),
            feedbackSpacingPx = dimension(R.dimen.pandawave_feedback_spacing)
        ),
        layout = PandaWaveLayoutTokens(
            appContentPaddingPx = dimension(R.dimen.pandawave_app_content_padding),
            navigationRailWidthPx = dimension(R.dimen.pandawave_navigation_rail_width),
            navigationSelectedIndicatorWidthPx =
                dimension(R.dimen.pandawave_navigation_selected_indicator_width),
            navigationSelectedIndicatorHeightPx =
                dimension(R.dimen.pandawave_navigation_selected_indicator_height),
            nowPlayingArtworkCompactPx = dimension(R.dimen.pandawave_now_playing_artwork_compact),
            nowPlayingArtworkStandardPx = dimension(R.dimen.pandawave_now_playing_artwork_standard),
            nowPlayingPrimaryButtonPx = dimension(R.dimen.pandawave_now_playing_primary_button),
            nowPlayingCompactHeightThresholdPx =
                dimension(R.dimen.pandawave_now_playing_compact_height_threshold),
            nowPlayingScrollHeightThresholdPx =
                dimension(R.dimen.pandawave_now_playing_scroll_height_threshold),
            compactWidthThresholdPx = dimension(R.dimen.pandawave_layout_compact_width_threshold),
            textMaxWidthPx = dimension(R.dimen.pandawave_layout_text_max_width)
        ),
        elevation = PandaWaveElevationTokens(
            cardRestingPx = dimension(R.dimen.pandawave_elevation_card_resting)
        ),
        motion = PandaWaveMotionTokens(
            voiceCycleMillis = resources.getInteger(R.integer.pandawave_motion_voice_cycle_millis),
            voiceActivationMillis =
                resources.getInteger(R.integer.pandawave_motion_voice_activation_millis),
            ambientEntryMillis = resources.getInteger(R.integer.pandawave_ambient_entry_duration_millis),
            ambientExitMillis = resources.getInteger(R.integer.pandawave_ambient_exit_duration_millis)
        ),
        restrictions = PandaWaveRestrictionTokens(
            maxBrowseColumnsUnrestricted =
                resources.getInteger(R.integer.pandawave_max_browse_columns_unrestricted),
            maxBrowseColumnsRestricted =
                resources.getInteger(R.integer.pandawave_max_browse_columns_restricted),
            maxVisibleActionsRestricted =
                resources.getInteger(R.integer.pandawave_max_visible_actions_restricted)
        )
    )

    private fun color(@ColorRes id: Int): Int = resources.getColor(id, theme)

    private fun dimension(id: Int): Int = resources.getDimensionPixelSize(id)

    private fun fraction(id: Int): Float = resources.getFraction(id, FRACTION_BASE, FRACTION_BASE)

    private fun textStyle(size: Int, lineHeight: Int, weight: Int): TextStyle {
        val scaledDensity = resources.displayMetrics.density * resources.configuration.fontScale
        return TextStyle(
            fontSize = (resources.getDimension(size) / scaledDensity).sp,
            lineHeight = (resources.getDimension(lineHeight) / scaledDensity).sp,
            fontWeight = FontWeight(resources.getInteger(weight))
        )
    }

    private companion object {
        const val FRACTION_BASE = 1
    }
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
            primary = R.color.pandawave_theme_bamboo_grove_light_primary,
            onPrimary = R.color.pandawave_theme_bamboo_grove_light_on_primary,
            secondary = R.color.pandawave_theme_bamboo_grove_light_secondary,
            onSecondary = R.color.pandawave_theme_bamboo_grove_light_on_secondary,
            surface = R.color.pandawave_theme_bamboo_grove_light_surface,
            onSurface = R.color.pandawave_theme_bamboo_grove_light_on_surface,
            surfaceVariant = R.color.pandawave_theme_bamboo_grove_light_surface_container_high,
            onSurfaceVariant = R.color.pandawave_theme_bamboo_grove_light_on_surface_variant,
            error = R.color.pandawave_theme_bamboo_grove_light_error,
            onError = R.color.pandawave_theme_bamboo_grove_light_on_error
        )

        PandaWaveThemeId.MoonlitBambooDark -> ColorTokenResourceIds(
            primary = R.color.pandawave_theme_moonlit_bamboo_dark_primary,
            onPrimary = R.color.pandawave_theme_moonlit_bamboo_dark_on_primary,
            secondary = R.color.pandawave_theme_moonlit_bamboo_dark_secondary,
            onSecondary = R.color.pandawave_theme_moonlit_bamboo_dark_on_secondary,
            surface = R.color.pandawave_theme_moonlit_bamboo_dark_surface,
            onSurface = R.color.pandawave_theme_moonlit_bamboo_dark_on_surface,
            surfaceVariant = R.color.pandawave_theme_moonlit_bamboo_dark_surface_container_high,
            onSurfaceVariant = R.color.pandawave_theme_moonlit_bamboo_dark_on_surface_variant,
            error = R.color.pandawave_theme_moonlit_bamboo_dark_error,
            onError = R.color.pandawave_theme_moonlit_bamboo_dark_on_error
        )

        PandaWaveThemeId.ForestTechLight -> ColorTokenResourceIds(
            primary = R.color.pandawave_theme_forest_tech_light_primary,
            onPrimary = R.color.pandawave_theme_forest_tech_light_on_primary,
            secondary = R.color.pandawave_theme_forest_tech_light_secondary,
            onSecondary = R.color.pandawave_theme_forest_tech_light_on_secondary,
            surface = R.color.pandawave_theme_forest_tech_light_surface,
            onSurface = R.color.pandawave_theme_forest_tech_light_on_surface,
            surfaceVariant = R.color.pandawave_theme_forest_tech_light_surface_container_high,
            onSurfaceVariant = R.color.pandawave_theme_forest_tech_light_on_surface_variant,
            error = R.color.pandawave_theme_forest_tech_light_error,
            onError = R.color.pandawave_theme_forest_tech_light_on_error
        )

        PandaWaveThemeId.ForestTechDark -> ColorTokenResourceIds(
            primary = R.color.pandawave_theme_forest_tech_dark_primary,
            onPrimary = R.color.pandawave_theme_forest_tech_dark_on_primary,
            secondary = R.color.pandawave_theme_forest_tech_dark_secondary,
            onSecondary = R.color.pandawave_theme_forest_tech_dark_on_secondary,
            surface = R.color.pandawave_theme_forest_tech_dark_surface,
            onSurface = R.color.pandawave_theme_forest_tech_dark_on_surface,
            surfaceVariant = R.color.pandawave_theme_forest_tech_dark_surface_container_high,
            onSurfaceVariant = R.color.pandawave_theme_forest_tech_dark_on_surface_variant,
            error = R.color.pandawave_theme_forest_tech_dark_error,
            onError = R.color.pandawave_theme_forest_tech_dark_on_error
        )
    }
