package com.adrianrusu.pandawave.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.adrianrusu.pandawave.core.telemetry.TelemetryLogger
import com.adrianrusu.pandawave.core.telemetry.TelemetryModule
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class DataStoreAmbientModePreferenceRepository(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
    telemetryLogger: TelemetryLogger
) : AmbientModePreferenceRepository {
    private val logger = telemetryLogger.forModule(TelemetryModule.Preferences)

    override val state = dataStore.data
        .catch { error ->
            if (error !is IOException) throw error

            logger.error(
                name = EVENT_AMBIENT_PREFERENCE_READ_FAILED,
                throwable = error
            )
            emit(emptyPreferences())
        }
        .map { stored ->
            AmbientModePreferenceState.Ready(
                AmbientModePreferences(
                    enabled = stored[AmbientModeEnabledKey] ?: true,
                    timeoutSeconds = AmbientModePreferences.normalizeTimeoutSeconds(
                        stored[AmbientModeTimeoutSecondsKey] ?: AmbientModePreferences.DEFAULT_TIMEOUT_SECONDS
                    )
                )
            )
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = AmbientModePreferenceState.Loading
        )

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { stored ->
            stored[AmbientModeEnabledKey] = enabled
        }
    }

    override suspend fun setTimeoutSeconds(timeoutSeconds: Int) {
        dataStore.edit { stored ->
            stored[AmbientModeTimeoutSecondsKey] = AmbientModePreferences.normalizeTimeoutSeconds(timeoutSeconds)
        }
    }

    private companion object {
        val AmbientModeEnabledKey = booleanPreferencesKey("ambient_mode_enabled")
        val AmbientModeTimeoutSecondsKey = intPreferencesKey("ambient_mode_timeout_seconds")
        const val EVENT_AMBIENT_PREFERENCE_READ_FAILED = "ambient_preferences.read_failed"
    }
}
