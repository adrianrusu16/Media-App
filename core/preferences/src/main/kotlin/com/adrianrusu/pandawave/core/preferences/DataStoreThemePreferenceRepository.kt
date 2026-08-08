package com.adrianrusu.pandawave.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.adrianrusu.pandawave.core.model.theme.PandaWaveThemePreference
import com.adrianrusu.pandawave.core.model.theme.ThemePreferenceRepository
import com.adrianrusu.pandawave.core.model.theme.ThemePreferenceState
import com.adrianrusu.pandawave.core.telemetry.TelemetryLogger
import com.adrianrusu.pandawave.core.telemetry.TelemetryModule
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class DataStoreThemePreferenceRepository(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
    telemetryLogger: TelemetryLogger
) : ThemePreferenceRepository {
    private val logger = telemetryLogger.forModule(TelemetryModule.Preferences)

    override val state = dataStore.data
        .catch { error ->
            if (error !is IOException) throw error

            logger.error(
                name = EVENT_THEME_PREFERENCE_READ_FAILED,
                throwable = error
            )
            emit(emptyPreferences())
        }
        .map { preferences ->
            val preference = (preferences[ThemePreferenceProjectionKey]
                ?: preferences[LegacyThemePreferenceKey])
                ?.let(PandaWaveThemePreference::fromWireOrNull)
                ?: PandaWaveThemePreference.SystemDefault
            ThemePreferenceState.Ready(preference)
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = ThemePreferenceState.Loading
        )

    override suspend fun setPreference(preference: PandaWaveThemePreference) {
        dataStore.edit { preferences ->
            preferences[ThemePreferenceProjectionKey] = preference.wireValue
            preferences.remove(LegacyThemePreferenceKey)
        }
    }

    private companion object {
        val ThemePreferenceProjectionKey = stringPreferencesKey("theme_preference_projection")
        val LegacyThemePreferenceKey = stringPreferencesKey("theme_preference")
        const val EVENT_THEME_PREFERENCE_READ_FAILED = "theme_preferences.read_failed"
    }
}