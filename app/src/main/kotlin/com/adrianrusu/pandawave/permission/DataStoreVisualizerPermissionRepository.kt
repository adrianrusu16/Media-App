package com.adrianrusu.pandawave.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.adrianrusu.pandawave.core.audio.visualizer.VisualizerPermissionRepository
import com.adrianrusu.pandawave.core.audio.visualizer.VisualizerPermissionState
import com.adrianrusu.pandawave.core.audio.visualizer.resolveVisualizerPermissionState
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class DataStoreVisualizerPermissionRepository internal constructor(
    private val isPermissionGranted: () -> Boolean,
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope
) : VisualizerPermissionRepository {
    constructor(
        context: Context,
        dataStore: DataStore<Preferences>,
        scope: CoroutineScope
    ) : this(
        isPermissionGranted = {
            context.applicationContext.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED
        },
        dataStore = dataStore,
        scope = scope
    )

    private val mutableState = MutableStateFlow<VisualizerPermissionState>(VisualizerPermissionState.Unknown)
    override val state: StateFlow<VisualizerPermissionState> = mutableState.asStateFlow()

    private var hasRequested: Boolean? = null
    private var shouldShowRationale = false

    init {
        scope.launch {
            dataStore.data
                .catch { error ->
                    if (error is IOException) {
                        emit(
                            androidx.datastore.preferences.core.emptyPreferences()
                        )
                    } else {
                        throw error
                    }
                }
                .map { preferences -> preferences[HAS_REQUESTED_KEY] ?: false }
                .collect { requested ->
                    hasRequested = requested
                    publishState()
                }
        }
    }

    override fun refresh(shouldShowRationale: Boolean) {
        this.shouldShowRationale = shouldShowRationale
        publishState()
    }

    override suspend fun markRequestLaunched() {
        dataStore.edit { preferences -> preferences[HAS_REQUESTED_KEY] = true }
        hasRequested = true
        publishState()
    }

    override fun onRequestResult(granted: Boolean, shouldShowRationale: Boolean) {
        hasRequested = true
        this.shouldShowRationale = shouldShowRationale
        publishState(grantedOverride = granted)
        scope.launch {
            dataStore.edit { preferences -> preferences[HAS_REQUESTED_KEY] = true }
        }
    }

    private fun publishState(grantedOverride: Boolean? = null) {
        val requested = hasRequested ?: return
        val granted = grantedOverride ?: isPermissionGranted()
        mutableState.value = resolveVisualizerPermissionState(
            granted = granted,
            hasRequested = requested,
            shouldShowRationale = shouldShowRationale
        )
    }

    private companion object {
        const val PERMISSION = Manifest.permission.RECORD_AUDIO
        val HAS_REQUESTED_KEY = booleanPreferencesKey("visualizer_permission_requested")
    }
}
