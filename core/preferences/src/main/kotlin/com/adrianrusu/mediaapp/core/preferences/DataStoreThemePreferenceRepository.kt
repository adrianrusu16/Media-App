package com.adrianrusu.mediaapp.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.adrianrusu.mediaapp.core.model.theme.PandaWaveThemePreference
import com.adrianrusu.mediaapp.core.model.theme.ThemePreferenceRepository
import com.adrianrusu.mediaapp.core.model.theme.ThemePreferenceState
import com.adrianrusu.mediaapp.core.telemetry.TelemetryLogger
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class DataStoreThemePreferenceRepository(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
    private val telemetryLogger: TelemetryLogger
) : ThemePreferenceRepository {
    override val state = dataStore.data
        .catch { error ->
            if (error !is IOException) throw error

            telemetryLogger.error(
                name = EVENT_THEME_PREFERENCE_READ_FAILED,
                throwable = error
            )
            emit(emptyPreferences())
        }
        .map { preferences ->
            val preference = preferences[ThemePreferenceKey]
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
            preferences[ThemePreferenceKey] = preference.wireValue
        }
    }

    private companion object {
        val ThemePreferenceKey = stringPreferencesKey("theme_preference")
        const val EVENT_THEME_PREFERENCE_READ_FAILED = "theme_preferences.read_failed"
    }
}
