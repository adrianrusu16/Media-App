package com.adrianrusu.mediaapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adrianrusu.mediaapp.appshell.presentation.AppShellScreen
import com.adrianrusu.mediaapp.appshell.presentation.AppShellViewModel
import com.adrianrusu.mediaapp.core.designsystem.theme.MediaAppTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AppShellViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val state = viewModel.state.collectAsStateWithLifecycle()

            MediaAppTheme(darkTheme = isSystemInDarkTheme()) {
                AppShellScreen(
                    state = state.value,
                    onIntent = viewModel::onIntent,
                )
            }
        }
    }
}
