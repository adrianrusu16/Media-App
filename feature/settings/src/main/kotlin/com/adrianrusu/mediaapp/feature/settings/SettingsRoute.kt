package com.adrianrusu.mediaapp.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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

@OptIn(ExperimentalMaterial3Api::class)
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
            label = "Light"
        ),
        ThemePreferenceOption(
            preference = PandaWaveThemePreference.MoonlitBambooDark,
            label = "Dark"
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
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = option.preference == selectedPreference,
                        enabled = enabled,
                        onClick = { onPreferenceSelected(option.preference) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = options.size
                        )
                    ) {
                        Text(text = option.label)
                    }
                }
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
    }
