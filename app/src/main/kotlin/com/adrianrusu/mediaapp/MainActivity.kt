package com.adrianrusu.mediaapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adrianrusu.mediaapp.appshell.presentation.AppShellScreen
import com.adrianrusu.mediaapp.appshell.presentation.AppShellViewModel
import com.adrianrusu.mediaapp.core.designsystem.R as DesignSystemR
import com.adrianrusu.mediaapp.core.designsystem.theme.PandaWaveTheme
import com.adrianrusu.mediaapp.theme.AppThemeViewModel
import com.adrianrusu.mediaapp.theme.ThemeStartupGate
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var themeStartupGate: ThemeStartupGate

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
                        DesignSystemR.integer.mediaapp_splash_exit_animation_duration_millis
                    ).toLong()
                )
                .withEndAction(provider::remove)
                .start()
        }
        enableEdgeToEdge()

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
                    onIntent = viewModel::onIntent
                )
            }
        }
    }
}
