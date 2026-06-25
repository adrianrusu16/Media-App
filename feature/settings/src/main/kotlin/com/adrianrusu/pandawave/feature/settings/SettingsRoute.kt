package com.adrianrusu.pandawave.feature.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adrianrusu.pandawave.core.audio.visualizer.VisualizerPermissionAction
import com.adrianrusu.pandawave.core.audio.visualizer.VisualizerPermissionState
import com.adrianrusu.pandawave.core.audio.visualizer.recommendedAction
import com.adrianrusu.pandawave.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.pandawave.core.designsystem.tokens.md
import com.adrianrusu.pandawave.core.designsystem.tokens.sm
import com.adrianrusu.pandawave.core.model.theme.PandaWaveThemePreference
import com.adrianrusu.pandawave.core.ui.components.BambooActionCard
import com.adrianrusu.pandawave.core.ui.components.BambooCard
import com.adrianrusu.pandawave.core.ui.components.BambooSelectableRow
import com.adrianrusu.pandawave.core.ui.components.BambooSwitchRow
import com.adrianrusu.pandawave.core.ui.components.BambooTitleBody
import com.adrianrusu.pandawave.core.ui.focus.BambooRotaryColumn
import com.adrianrusu.pandawave.core.ui.focus.bambooBringIntoViewOnFocus
import com.adrianrusu.pandawave.feature.settings.domain.SettingsIntent
import com.adrianrusu.pandawave.feature.settings.domain.SettingsState
import com.adrianrusu.pandawave.feature.settings.presentation.SettingsViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsRoute(modifier: Modifier = Modifier) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(viewModel, lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onVisualizerPermissionSnapshot(activity.shouldShowVisualizerPermissionRationale())
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.onVisualizerPermissionSnapshot(activity.shouldShowVisualizerPermissionRationale())
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onOpenVisualizerPermissionSettings = {
            context.openApplicationPermissionSettings()
        },
        modifier = modifier
    )
}

@Composable
private fun SettingsScreen(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    onOpenVisualizerPermissionSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
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
        AmbientModePreferences(
            enabled = state.ambientModeEnabled,
            timeoutSeconds = state.ambientTimeoutSeconds,
            onEnabledChange = { enabled ->
                onIntent(SettingsIntent.SetAmbientModeEnabled(enabled))
            },
            onTimeoutChange = { timeoutSeconds ->
                onIntent(SettingsIntent.SetAmbientTimeoutSeconds(timeoutSeconds))
            }
        )
        VisualizerPermissionPreference(
            state = state.visualizerPermissionState,
            actionEnabled = !state.restriction.isRestricted,
            onOpenSettings = onOpenVisualizerPermissionSettings
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
private fun VisualizerPermissionPreference(
    state: VisualizerPermissionState,
    actionEnabled: Boolean,
    onOpenSettings: () -> Unit
) {
    when (state.recommendedAction) {
        VisualizerPermissionAction.Request -> BambooCard {
            BambooTitleBody(
                title = stringResource(R.string.pandawave_settings_visualizer_permission_title),
                body = stringResource(R.string.pandawave_settings_visualizer_permission_denied)
            )
        }

        VisualizerPermissionAction.OpenSettings -> BambooActionCard(
            title = stringResource(R.string.pandawave_settings_visualizer_permission_title),
            body = stringResource(R.string.pandawave_settings_visualizer_permission_blocked),
            actionLabel = stringResource(R.string.pandawave_settings_visualizer_permission_open_settings),
            actionEnabled = actionEnabled,
            onActionClick = onOpenSettings
        )

        VisualizerPermissionAction.None -> BambooCard {
            BambooTitleBody(
                title = stringResource(R.string.pandawave_settings_visualizer_permission_title),
                body = stringResource(
                    if (state == VisualizerPermissionState.Granted) {
                        R.string.pandawave_settings_visualizer_permission_granted
                    } else {
                        R.string.pandawave_settings_visualizer_permission_checking
                    }
                )
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Activity?.shouldShowVisualizerPermissionRationale(): Boolean =
    this?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) == true

private fun Context.openApplicationPermissionSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
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
private fun AmbientModePreferences(
    enabled: Boolean,
    timeoutSeconds: Int,
    onEnabledChange: (Boolean) -> Unit,
    onTimeoutChange: (Int) -> Unit
) {
    var pendingTimeout by remember(timeoutSeconds) {
        mutableFloatStateOf(timeoutSeconds.toFloat())
    }

    BambooSwitchRow(
        title = stringResource(R.string.pandawave_settings_ambient_title),
        body = stringResource(R.string.pandawave_settings_ambient_body),
        checked = enabled,
        enabled = true,
        onCheckedChange = onEnabledChange
    )
    BambooCard {
        BambooTitleBody(
            title = stringResource(R.string.pandawave_settings_ambient_timeout_title),
            body = stringResource(
                R.string.pandawave_settings_ambient_timeout_body,
                pendingTimeout.roundToInt()
            )
        )
        Slider(
            value = pendingTimeout,
            onValueChange = { pendingTimeout = it },
            modifier = Modifier
                .fillMaxWidth()
                .bambooBringIntoViewOnFocus()
                .testTag("ambient-timeout-slider"),
            enabled = enabled,
            valueRange = 5f..60f,
            steps = 10,
            onValueChangeFinished = {
                onTimeoutChange(pendingTimeout.roundToInt())
            }
        )
    }
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
