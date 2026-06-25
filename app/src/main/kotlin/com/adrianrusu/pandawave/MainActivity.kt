package com.adrianrusu.pandawave

import android.Manifest
import android.os.Bundle
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.adrianrusu.pandawave.appshell.presentation.AppShellScreen
import com.adrianrusu.pandawave.appshell.presentation.AppShellViewModel
import com.adrianrusu.pandawave.core.audio.visualizer.VisualizerPermissionRepository
import com.adrianrusu.pandawave.core.designsystem.R as DesignSystemR
import com.adrianrusu.pandawave.core.designsystem.theme.PandaWaveTheme
import com.adrianrusu.pandawave.core.playback.BambooPlaybackRepository
import com.adrianrusu.pandawave.core.telemetry.TelemetryLogger
import com.adrianrusu.pandawave.core.telemetry.TelemetryModule
import com.adrianrusu.pandawave.core.ui.interaction.UserInteractionTracker
import com.adrianrusu.pandawave.permission.shouldRequestVisualizerPermission
import com.adrianrusu.pandawave.theme.AppThemeViewModel
import com.adrianrusu.pandawave.theme.ThemeStartupGate
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var themeStartupGate: ThemeStartupGate

    @Inject
    lateinit var userInteractionTracker: UserInteractionTracker

    @Inject
    lateinit var visualizerPermissionRepository: VisualizerPermissionRepository

    @Inject
    lateinit var playbackRepository: BambooPlaybackRepository

    @Inject
    lateinit var telemetryLogger: TelemetryLogger

    private var visualizerPermissionRequestInFlight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition(themeStartupGate::shouldKeepSplashVisible)
        splashScreen.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0F)
                .scaleX(1.04F)
                .scaleY(1.04F)
                .setDuration(
                    resources.getInteger(
                        DesignSystemR.integer.pandawave_splash_exit_animation_duration_millis
                    ).toLong()
                )
                .withEndAction(provider::remove)
                .start()
        }
        enableEdgeToEdge()

        val permissionLogger = telemetryLogger.forModule(TelemetryModule.App)
        val visualizerPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            visualizerPermissionRequestInFlight = false
            visualizerPermissionRepository.onRequestResult(
                granted = granted,
                shouldShowRationale = shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
            )
        }
        playbackRepository.start()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                visualizerPermissionRepository.refresh(
                    shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
                )
                combine(
                    visualizerPermissionRepository.state,
                    playbackRepository.state
                ) { permissionState, playbackState ->
                    shouldRequestVisualizerPermission(
                        permissionState = permissionState,
                        vehicleSafety = playbackState.vehicleSafety
                    )
                }.collect { shouldRequest ->
                    if (!shouldRequest || visualizerPermissionRequestInFlight) return@collect

                    visualizerPermissionRequestInFlight = true
                    runCatching {
                        visualizerPermissionRepository.markRequestLaunched()
                    }.onSuccess {
                        visualizerPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }.onFailure { error ->
                        visualizerPermissionRequestInFlight = false
                        permissionLogger.warning(
                            name = VISUALIZER_PERMISSION_PERSIST_FAILED,
                            throwable = error
                        )
                    }
                }
            }
        }

        setContent {
            val viewModel: AppShellViewModel = hiltViewModel()
            val themeViewModel: AppThemeViewModel = hiltViewModel()
            val state = viewModel.state.collectAsStateWithLifecycle()
            val themePreference = themeViewModel.preference.collectAsStateWithLifecycle()

            PandaWaveTheme(
                darkTheme = isSystemInDarkTheme(),
                themePreference = themePreference.value
            ) {
                AppShellScreen(
                    state = state.value,
                    onIntent = viewModel::onIntent,
                    onMoveTaskToBack = { moveTaskToBack(true) }
                )
            }
        }
    }

    override fun onUserInteraction() {
        userInteractionTracker.recordInteraction()
        super.onUserInteraction()
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            userInteractionTracker.recordInteraction()
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onDestroy() {
        playbackRepository.close()
        super.onDestroy()
    }

    private companion object {
        const val VISUALIZER_PERMISSION_PERSIST_FAILED = "app.visualizer_permission.persist_failed"
    }
}
