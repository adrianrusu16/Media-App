package com.adrianrusu.mediaapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adrianrusu.mediaapp.appshell.presentation.AppShellScreen
import com.adrianrusu.mediaapp.appshell.presentation.AppShellViewModel
import com.adrianrusu.mediaapp.core.designsystem.theme.PandaWaveTheme
import com.adrianrusu.mediaapp.theme.AppThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
