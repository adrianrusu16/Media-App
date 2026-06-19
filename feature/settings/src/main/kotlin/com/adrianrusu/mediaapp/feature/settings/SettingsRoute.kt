package com.adrianrusu.mediaapp.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adrianrusu.mediaapp.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.mediaapp.core.designsystem.tokens.cardResting
import com.adrianrusu.mediaapp.core.designsystem.tokens.lg
import com.adrianrusu.mediaapp.core.designsystem.tokens.md
import com.adrianrusu.mediaapp.core.designsystem.tokens.sm
import com.adrianrusu.mediaapp.core.designsystem.tokens.xs
import com.adrianrusu.mediaapp.core.model.theme.PandaWaveThemePreference
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
        modifier = modifier.fillMaxWidth(),
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
    val tokens = LocalPandaWaveDesignTokens.current

    Surface(
        color = if (state.restriction.isRestricted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(tokens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
        ) {
            Text(
                text = "Settings safety",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = state.restriction.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    body: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: () -> Unit
) {
    val tokens = LocalPandaWaveDesignTokens.current

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = tokens.elevation.cardResting,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(tokens.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = { onCheckedChange() }
            )
        }
    }
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

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = tokens.elevation.cardResting,
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(tokens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
                Text(
                    text = "Theme",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = selectedPreference.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
                options.forEach { option ->
                    ThemePreferenceRow(
                        option = option,
                        selected = option.preference == selectedPreference,
                        enabled = enabled,
                        onPreferenceSelected = onPreferenceSelected
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemePreferenceRow(
    option: ThemePreferenceOption,
    selected: Boolean,
    enabled: Boolean,
    onPreferenceSelected: (PandaWaveThemePreference) -> Unit
) {
    val tokens = LocalPandaWaveDesignTokens.current
    val background = if (selected) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }

    Surface(
        color = background,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) {
                onPreferenceSelected(option.preference)
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(tokens.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                enabled = enabled,
                onClick = { onPreferenceSelected(option.preference) }
            )
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = option.preference.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun PrivacyNoticeCard(acknowledged: Boolean, onAcknowledge: () -> Unit) {
    val tokens = LocalPandaWaveDesignTokens.current

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = tokens.elevation.cardResting,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(tokens.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
            ) {
                Text(
                    text = "Privacy notice",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (acknowledged) {
                        "Acknowledged for this session."
                    } else {
                        "Review data choices before enabling deeper account and provider sync."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Button(
                enabled = !acknowledged,
                onClick = onAcknowledge
            ) {
                Text(text = if (acknowledged) "Done" else "Acknowledge")
            }
        }
    }
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
