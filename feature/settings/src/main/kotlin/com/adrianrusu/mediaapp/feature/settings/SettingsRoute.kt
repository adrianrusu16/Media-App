package com.adrianrusu.mediaapp.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adrianrusu.mediaapp.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.mediaapp.core.designsystem.tokens.md
import com.adrianrusu.mediaapp.core.designsystem.tokens.sm
import com.adrianrusu.mediaapp.core.model.theme.PandaWaveThemePreference
import com.adrianrusu.mediaapp.core.ui.components.BambooActionCard
import com.adrianrusu.mediaapp.core.ui.components.BambooCard
import com.adrianrusu.mediaapp.core.ui.components.BambooSelectableRow
import com.adrianrusu.mediaapp.core.ui.components.BambooSwitchRow
import com.adrianrusu.mediaapp.core.ui.components.BambooTitleBody
import com.adrianrusu.mediaapp.core.ui.focus.BambooRotaryColumn
import com.adrianrusu.mediaapp.feature.settings.domain.SettingsIntent
import com.adrianrusu.mediaapp.feature.settings.domain.SettingsState
import com.adrianrusu.mediaapp.feature.settings.presentation.SettingsViewModel

@Composable
fun SettingsRoute(modifier: Modifier = Modifier) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    SettingsScreen(
        state = state,
        onIntent = viewModel::onIntent,
        modifier = modifier
    )
}

@Composable
private fun SettingsScreen(state: SettingsState, onIntent: (SettingsIntent) -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalPandaWaveDesignTokens.current

    BambooRotaryColumn(
        modifier = modifier
            .fillMaxWidth()
            .testTag("settings-route"),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)
    ) {
        SettingsSwitchRow(
            title = stringResource(R.string.pandawave_settings_diagnostics_title),
            body = stringResource(R.string.pandawave_settings_diagnostics_body),
            checked = state.diagnosticsEnabled,
            onCheckedChange = { onIntent(SettingsIntent.ToggleDiagnostics) }
        )
        SettingsSwitchRow(
            title = stringResource(R.string.pandawave_settings_personalization_title),
            body = stringResource(R.string.pandawave_settings_personalization_body),
            checked = state.personalizationEnabled,
            onCheckedChange = { onIntent(SettingsIntent.TogglePersonalization) }
        )
        SettingsSwitchRow(
            title = stringResource(R.string.pandawave_settings_explicit_title),
            body = stringResource(R.string.pandawave_settings_explicit_body),
            checked = state.explicitContentAllowed,
            onCheckedChange = { onIntent(SettingsIntent.ToggleExplicitContent) }
        )
        ThemePreferenceCard(
            selectedPreference = state.themePreference,
            onPreferenceSelected = { preference ->
                onIntent(SettingsIntent.SelectThemePreference(preference))
            }
        )
        PrivacyNoticeCard(
            acknowledged = state.privacyNoticeAcknowledged,
            onAcknowledge = { onIntent(SettingsIntent.AcknowledgePrivacyNotice) }
        )
    }
}

@Composable
private fun SettingsSwitchRow(title: String, body: String, checked: Boolean, onCheckedChange: () -> Unit) {
    BambooSwitchRow(
        title = title,
        body = body,
        checked = checked,
        enabled = true,
        onCheckedChange = { onCheckedChange() }
    )
}

@Composable
private fun ThemePreferenceCard(
    selectedPreference: PandaWaveThemePreference,
    onPreferenceSelected: (PandaWaveThemePreference) -> Unit
) {
    val tokens = LocalPandaWaveDesignTokens.current
    val options = listOf(
        ThemePreferenceOption(
            preference = PandaWaveThemePreference.SystemDefault,
            label = stringResource(R.string.pandawave_settings_theme_system)
        ),
        ThemePreferenceOption(
            preference = PandaWaveThemePreference.BambooGroveLight,
            label = stringResource(R.string.pandawave_settings_theme_bamboo_grove_light)
        ),
        ThemePreferenceOption(
            preference = PandaWaveThemePreference.MoonlitBambooDark,
            label = stringResource(R.string.pandawave_settings_theme_moonlit_bamboo_dark)
        ),
        ThemePreferenceOption(
            preference = PandaWaveThemePreference.ForestTechLight,
            label = stringResource(R.string.pandawave_settings_theme_forest_tech_light)
        ),
        ThemePreferenceOption(
            preference = PandaWaveThemePreference.ForestTechDark,
            label = stringResource(R.string.pandawave_settings_theme_forest_tech_dark)
        )
    )

    BambooCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)
        ) {
            BambooTitleBody(
                title = stringResource(R.string.pandawave_settings_theme_title),
                body = selectedPreference.localizedLabel()
            )
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
                options.forEach { option ->
                    BambooSelectableRow(
                        title = option.label,
                        body = option.preference.localizedDescription(),
                        selected = option.preference == selectedPreference,
                        enabled = true,
                        onClick = {
                            onPreferenceSelected(option.preference)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyNoticeCard(acknowledged: Boolean, onAcknowledge: () -> Unit) {
    BambooActionCard(
        title = stringResource(R.string.pandawave_settings_privacy_title),
        body = if (acknowledged) {
            stringResource(R.string.pandawave_settings_privacy_acknowledged)
        } else {
            stringResource(R.string.pandawave_settings_privacy_review)
        },
        actionLabel = if (acknowledged) {
            stringResource(R.string.pandawave_settings_done)
        } else {
            stringResource(R.string.pandawave_settings_acknowledge)
        },
        actionEnabled = !acknowledged,
        onActionClick = onAcknowledge
    )
}

private data class ThemePreferenceOption(val preference: PandaWaveThemePreference, val label: String)

@Composable
private fun PandaWaveThemePreference.localizedLabel(): String = when (this) {
    PandaWaveThemePreference.SystemDefault -> stringResource(R.string.pandawave_settings_theme_system_label)

    PandaWaveThemePreference.BambooGroveLight ->
        stringResource(R.string.pandawave_settings_theme_bamboo_grove_light)

    PandaWaveThemePreference.MoonlitBambooDark ->
        stringResource(R.string.pandawave_settings_theme_moonlit_bamboo_dark)

    PandaWaveThemePreference.ForestTechLight ->
        stringResource(R.string.pandawave_settings_theme_forest_tech_light)

    PandaWaveThemePreference.ForestTechDark ->
        stringResource(R.string.pandawave_settings_theme_forest_tech_dark)
}

@Composable
private fun PandaWaveThemePreference.localizedDescription(): String = when (this) {
    PandaWaveThemePreference.SystemDefault ->
        stringResource(R.string.pandawave_settings_theme_system_description)

    PandaWaveThemePreference.BambooGroveLight ->
        stringResource(R.string.pandawave_settings_theme_bamboo_grove_light_description)

    PandaWaveThemePreference.MoonlitBambooDark ->
        stringResource(R.string.pandawave_settings_theme_moonlit_bamboo_dark_description)

    PandaWaveThemePreference.ForestTechLight ->
        stringResource(R.string.pandawave_settings_theme_forest_tech_light_description)

    PandaWaveThemePreference.ForestTechDark ->
        stringResource(R.string.pandawave_settings_theme_forest_tech_dark_description)
}
