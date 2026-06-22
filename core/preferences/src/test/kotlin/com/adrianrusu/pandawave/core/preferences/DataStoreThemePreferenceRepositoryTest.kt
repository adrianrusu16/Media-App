package com.adrianrusu.pandawave.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.adrianrusu.pandawave.core.model.theme.PandaWaveThemePreference
import com.adrianrusu.pandawave.core.model.theme.ThemePreferenceState
import com.adrianrusu.pandawave.core.telemetry.TelemetryLogger
import com.adrianrusu.pandawave.core.telemetry.TelemetrySink
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir

class DataStoreThemePreferenceRepositoryTest {
    @TempDir
    lateinit var tempDirectory: Path

    @Test
    fun `theme survives repository recreation`() = runTest {
        val file = tempDirectory.resolve("theme.preferences_pb").toFile()
        val firstScope = repositoryScope(testScheduler)
        val first = createRepository(file, firstScope)

        first.setPreference(PandaWaveThemePreference.ForestTechDark)
        assertEquals(
            PandaWaveThemePreference.ForestTechDark,
            first.state.filterIsInstance<ThemePreferenceState.Ready>().first().preference
        )
        firstScope.cancel()

        val secondScope = repositoryScope(testScheduler)
        val second = createRepository(file, secondScope)
        assertEquals(
            PandaWaveThemePreference.ForestTechDark,
            second.state.filterIsInstance<ThemePreferenceState.Ready>().first().preference
        )
        secondScope.cancel()
    }

    @Test
    fun `unknown stored theme falls back to system default`() = runTest {
        val file = tempDirectory.resolve("unknown.preferences_pb").toFile()
        val scope = repositoryScope(testScheduler)
        val dataStore = createDataStore(file, scope)
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey("theme_preference")] = "unknown_theme"
        }

        val repository = DataStoreThemePreferenceRepository(
            dataStore = dataStore,
            scope = scope,
            telemetryLogger = telemetryLogger()
        )

        assertEquals(
            PandaWaveThemePreference.SystemDefault,
            repository.state.filterIsInstance<ThemePreferenceState.Ready>().first().preference
        )
        scope.cancel()
    }

    private fun repositoryScope(scheduler: TestCoroutineScheduler): CoroutineScope = CoroutineScope(
        SupervisorJob() + StandardTestDispatcher(scheduler)
    )

    private fun createRepository(file: File, scope: CoroutineScope): DataStoreThemePreferenceRepository =
        DataStoreThemePreferenceRepository(
            dataStore = createDataStore(file, scope),
            scope = scope,
            telemetryLogger = telemetryLogger()
        )

    private fun createDataStore(file: File, scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file }
        )

    private fun telemetryLogger(): TelemetryLogger = TelemetryLogger(TelemetrySink { })
}
