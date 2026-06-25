package com.adrianrusu.pandawave.permission

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.adrianrusu.pandawave.core.audio.visualizer.VisualizerPermissionState
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir

class DataStoreVisualizerPermissionRepositoryTest {
    @TempDir
    lateinit var tempDirectory: Path

    @Test
    fun `request launch is persisted before returning and survives recreation`() = runTest {
        val file = tempDirectory.resolve("permission.preferences_pb").toFile()
        val firstScope = repositoryScope(testScheduler)
        val first = createRepository(file = file, scope = firstScope)

        assertEquals(
            VisualizerPermissionState.Denied(canRequest = true),
            first.settledState()
        )
        first.markRequestLaunched()
        assertEquals(
            VisualizerPermissionState.Denied(canRequest = false),
            first.settledState()
        )
        firstScope.cancel()

        val secondScope = repositoryScope(testScheduler)
        val second = createRepository(file = file, scope = secondScope)
        assertEquals(
            VisualizerPermissionState.Denied(canRequest = false),
            second.settledState()
        )
        secondScope.cancel()
    }

    private suspend fun DataStoreVisualizerPermissionRepository.settledState(): VisualizerPermissionState =
        state.filter { it != VisualizerPermissionState.Unknown }.first()

    private fun repositoryScope(scheduler: TestCoroutineScheduler): CoroutineScope = CoroutineScope(
        SupervisorJob() + StandardTestDispatcher(scheduler)
    )

    private fun createRepository(file: File, scope: CoroutineScope): DataStoreVisualizerPermissionRepository =
        DataStoreVisualizerPermissionRepository(
            isPermissionGranted = { false },
            dataStore = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { file }
            ),
            scope = scope
        )
}
