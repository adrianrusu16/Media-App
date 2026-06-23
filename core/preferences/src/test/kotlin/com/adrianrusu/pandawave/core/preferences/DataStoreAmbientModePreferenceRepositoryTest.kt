package com.adrianrusu.pandawave.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
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

class DataStoreAmbientModePreferenceRepositoryTest {
    @TempDir
    lateinit var tempDirectory: Path

    @Test
    fun `defaults to enabled with fifteen second timeout`() = runTest {
        val scope = repositoryScope(testScheduler)
        val repository = createRepository(
            file = tempDirectory.resolve("defaults.preferences_pb").toFile(),
            scope = scope
        )

        assertEquals(
            AmbientModePreferences(enabled = true, timeoutSeconds = 15),
            repository.readyPreferences()
        )
        scope.cancel()
    }

    @Test
    fun `timeout is normalized to five second steps within range`() = runTest {
        val scope = repositoryScope(testScheduler)
        val repository = createRepository(
            file = tempDirectory.resolve("normalized.preferences_pb").toFile(),
            scope = scope
        )

        repository.setTimeoutSeconds(58)
        assertEquals(60, repository.readyPreferences().timeoutSeconds)

        repository.setTimeoutSeconds(2)
        assertEquals(5, repository.readyPreferences().timeoutSeconds)

        repository.setTimeoutSeconds(62)
        assertEquals(60, repository.readyPreferences().timeoutSeconds)
        scope.cancel()
    }

    @Test
    fun `enabled preference survives repository recreation`() = runTest {
        val file = tempDirectory.resolve("enabled.preferences_pb").toFile()
        val firstScope = repositoryScope(testScheduler)
        val first = createRepository(file = file, scope = firstScope)

        first.setEnabled(false)
        assertEquals(false, first.readyPreferences().enabled)
        firstScope.cancel()

        val secondScope = repositoryScope(testScheduler)
        val second = createRepository(file = file, scope = secondScope)
        assertEquals(false, second.readyPreferences().enabled)
        secondScope.cancel()
    }

    @Test
    fun `timeout survives repository recreation`() = runTest {
        val file = tempDirectory.resolve("timeout.preferences_pb").toFile()
        val firstScope = repositoryScope(testScheduler)
        val first = createRepository(file = file, scope = firstScope)

        first.setTimeoutSeconds(35)
        assertEquals(35, first.readyPreferences().timeoutSeconds)
        firstScope.cancel()

        val secondScope = repositoryScope(testScheduler)
        val second = createRepository(file = file, scope = secondScope)
        assertEquals(35, second.readyPreferences().timeoutSeconds)
        secondScope.cancel()
    }

    @Test
    fun `read failure records a preferences breadcrumb and emits defaults`() = runTest {
        val telemetrySink = RecordingAmbientTelemetrySink()
        val scope = repositoryScope(testScheduler)
        val repository = DataStoreAmbientModePreferenceRepository(
            dataStore = FailingAmbientPreferencesDataStore(),
            scope = scope,
            telemetryLogger = TelemetryLogger(telemetrySink)
        )

        assertEquals(
            AmbientModePreferences(enabled = true, timeoutSeconds = 15),
            repository.readyPreferences()
        )
        assertEquals(TelemetryModule.Preferences, telemetrySink.events.single().module)
        scope.cancel()
    }

    private suspend fun DataStoreAmbientModePreferenceRepository.readyPreferences(): AmbientModePreferences =
        state.filterIsInstance<AmbientModePreferenceState.Ready>().first().preferences

    private fun repositoryScope(scheduler: TestCoroutineScheduler): CoroutineScope = CoroutineScope(
        SupervisorJob() + StandardTestDispatcher(scheduler)
    )

    private fun createRepository(file: File, scope: CoroutineScope): DataStoreAmbientModePreferenceRepository =
        DataStoreAmbientModePreferenceRepository(
            dataStore = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { file }
            ),
            scope = scope,
            telemetryLogger = TelemetryLogger(TelemetrySink { })
        )
}

private class FailingAmbientPreferencesDataStore : DataStore<Preferences> {
    override val data = flow<Preferences> {
        throw IOException("read failed")
    }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(emptyPreferences())
}

private class RecordingAmbientTelemetrySink : TelemetrySink {
    val events = mutableListOf<TelemetryEvent>()

    override fun record(event: TelemetryEvent) {
        events += event
    }
}
