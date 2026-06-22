package com.adrianrusu.pandawave.theme

import android.os.SystemClock
import com.adrianrusu.pandawave.core.model.theme.ThemePreferenceRepository
import com.adrianrusu.pandawave.core.model.theme.ThemePreferenceState
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

class ThemeStartupGate internal constructor(
    private val state: StateFlow<ThemePreferenceState>,
    private val nowMillis: () -> Long,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS
) {
    @Inject
    constructor(repository: ThemePreferenceRepository) : this(
        state = repository.state,
        nowMillis = SystemClock::elapsedRealtime,
        timeoutMillis = DEFAULT_TIMEOUT_MILLIS
    )

    private val startedAtMillis = nowMillis()

    fun shouldKeepSplashVisible(): Boolean = state.value is ThemePreferenceState.Loading &&
        nowMillis() - startedAtMillis < timeoutMillis

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 1_500L
    }
}
