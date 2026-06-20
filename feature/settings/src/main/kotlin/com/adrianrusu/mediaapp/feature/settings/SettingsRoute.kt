package com.adrianrusu.mediaapp.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adrianrusu.mediaapp.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.mediaapp.core.designsystem.tokens.md
import com.adrianrusu.mediaapp.core.designsystem.tokens.sm
import com.adrianrusu.mediaapp.core.model.theme.PandaWaveThemePreference
import com.adrianrusu.mediaapp.core.ui.components.BambooActionCard
import com.adrianrusu.mediaapp.core.ui.components.BambooCard
import com.adrianrusu.mediaapp.core.ui.components.BambooSelectableRow
import com.adrianrusu.mediaapp.core.ui.components.BambooStatusCard
import com.adrianrusu.mediaapp.core.ui.components.BambooSwitchRow
import com.adrianrusu.mediaapp.core.ui.components.BambooTitleBody
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(state = rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)
    ) {
        SettingsStatusCard(state = state)
        SettingsSwitchRow(
            title = "Diagnostics",
            body = "Share redacted reliability events and playback health signals.",
            checked = state.diagnosticsEnabled,
            enabled = state.controlsEnabled,
            onCheckedChange = { onIntent(SettingsIntent.ToggleDiagnostics) }
        )
        SettingsSwitchRow(
            title = "Personalization",
            body = "Let the Rust engine use recent listening state to shape recommendations.",
            checked = state.personalizationEnabled,
            enabled = state.controlsEnabled,
            onCheckedChange = { onIntent(SettingsIntent.TogglePersonalization) }
        )
        SettingsSwitchRow(
            title = "Explicit content",
            body = "Allow content marked explicit when provider policy supports it.",
            checked = state.explicitContentAllowed,
            enabled = state.controlsEnabled,
            onCheckedChange = { onIntent(SettingsIntent.ToggleExplicitContent) }
        )
        ThemePreferenceCard(
            selectedPreference = state.themePreference,
            enabled = state.controlsEnabled,
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
private fun SettingsStatusCard(state: SettingsState) {
    BambooStatusCard(
        title = "Settings safety",
        body = state.restriction.label,
        highlighted = state.restriction.isRestricted
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    body: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: () -> Unit
) {
    BambooSwitchRow(
        title = title,
        body = body,
        checked = checked,
        enabled = enabled,
        onCheckedChange = { onCheckedChange() }
    )
}

@Composable
private fun ThemePreferenceCard(
    selectedPreference: PandaWaveThemePreference,
    enabled: Boolean,
    onPreferenceSelected: (PandaWaveThemePreference) -> Unit
) {
    val tokens = LocalPandaWaveDesignTokens.current
    val options = listOf(
        ThemePreferenceOption(
            preference = PandaWaveThemePreference.SystemDefault,
            label = "System"
        ),
        ThemePreferenceOption(
            preference = PandaWaveThemePreference.BambooGroveLight,
            label = "Bamboo Grove Light"
        ),
        ThemePreferenceOption(
            preference = PandaWaveThemePreference.MoonlitBambooDark,
            label = "Moonlit Bamboo Dark"
        ),
        ThemePreferenceOption(
            preference = PandaWaveThemePreference.ForestTechLight,
            label = "Forest Tech Light"
        ),
        ThemePreferenceOption(
            preference = PandaWaveThemePreference.ForestTechDark,
            label = "Forest Tech Dark"
        )
    )

    BambooCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)
        ) {
            BambooTitleBody(
                title = "Theme",
                body = selectedPreference.label
            )
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
                options.forEach { option ->
                    BambooSelectableRow(
                        title = option.label,
                        body = option.preference.description,
                        selected = option.preference == selectedPreference,
                        enabled = enabled,
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
        title = "Privacy notice",
        body = if (acknowledged) {
            "Acknowledged for this session."
        } else {
            "Review data choices before enabling deeper account and provider sync."
        },
        actionLabel = if (acknowledged) "Done" else "Acknowledge",
        actionEnabled = !acknowledged,
        onActionClick = onAcknowledge
    )
}

private data class ThemePreferenceOption(val preference: PandaWaveThemePreference, val label: String)

private val PandaWaveThemePreference.label: String
    get() = when (this) {
        PandaWaveThemePreference.SystemDefault -> "Follow system appearance."
        PandaWaveThemePreference.BambooGroveLight -> "Bamboo Grove Light"
        PandaWaveThemePreference.MoonlitBambooDark -> "Moonlit Bamboo Dark"
        PandaWaveThemePreference.ForestTechLight -> "Forest Tech Light"
        PandaWaveThemePreference.ForestTechDark -> "Forest Tech Dark"
    }

private val PandaWaveThemePreference.description: String
    get() = when (this) {
        PandaWaveThemePreference.SystemDefault -> "Use the vehicle or device appearance setting."
        PandaWaveThemePreference.BambooGroveLight -> "Panda ivory surfaces with bamboo and bark accents."
        PandaWaveThemePreference.MoonlitBambooDark -> "Charcoal surfaces with moonlit bamboo greens."
        PandaWaveThemePreference.ForestTechLight -> "A brighter Forest Tech palette for daytime cabins."
        PandaWaveThemePreference.ForestTechDark -> "The Stitch Forest Tech palette for low-glare driving."
    }
