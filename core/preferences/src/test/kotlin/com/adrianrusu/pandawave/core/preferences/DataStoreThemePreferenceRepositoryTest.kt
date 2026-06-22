package com.adrianrusu.pandawave.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.adrianrusu.pandawave.core.model.theme.PandaWaveThemePreference
import com.adrianrusu.pandawave.core.model.theme.ThemePreferenceState
import com.adrianrusu.pandawave.core.telemetry.TelemetryEvent
import com.adrianrusu.pandawave.core.telemetry.TelemetryLogger
import com.adrianrusu.pandawave.core.telemetry.TelemetryModule
import com.adrianrusu.pandawave.core.telemetry.TelemetrySink
import java.io.File
import java.io.IOException
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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

    @Test
    fun `read failure records a preferences breadcrumb without blocking fallback`() = runTest {
        val telemetrySink = RecordingTelemetrySink()
        val scope = repositoryScope(testScheduler)
        val repository = DataStoreThemePreferenceRepository(
            dataStore = FailingPreferencesDataStore(),
            scope = scope,
            telemetryLogger = TelemetryLogger(telemetrySink)
        )

        assertEquals(
            PandaWaveThemePreference.SystemDefault,
            repository.state.filterIsInstance<ThemePreferenceState.Ready>().first().preference
        )
        assertEquals(TelemetryModule.Preferences, telemetrySink.events.single().module)
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

private class FailingPreferencesDataStore : DataStore<Preferences> {
    override val data = flow<Preferences> {
        throw IOException("read failed")
    }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(emptyPreferences())
}

private class RecordingTelemetrySink : TelemetrySink {
    val events = mutableListOf<TelemetryEvent>()

    override fun record(event: TelemetryEvent) {
        events += event
    }
}
