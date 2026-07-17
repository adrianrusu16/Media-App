package com.adrianrusu.pandawave.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adrianrusu.pandawave.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.pandawave.core.designsystem.tokens.lg
import com.adrianrusu.pandawave.core.designsystem.tokens.md
import com.adrianrusu.pandawave.feature.auth.domain.AuthFormMode
import com.adrianrusu.pandawave.feature.auth.domain.AuthFormPhase
import com.adrianrusu.pandawave.feature.auth.domain.AuthNotice
import com.adrianrusu.pandawave.feature.auth.domain.AuthUiEffect
import com.adrianrusu.pandawave.feature.auth.presentation.AuthFormViewModel
import com.adrianrusu.pandawave.feature.auth.presentation.LoginViewModel
import com.adrianrusu.pandawave.feature.auth.presentation.RegisterViewModel

@Composable
fun LoginRoute(
    interactiveAllowed: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel()
) {
    AuthRoute(viewModel, interactiveAllowed, onClose, modifier)
}

@Composable
fun RegisterRoute(
    interactiveAllowed: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegisterViewModel = hiltViewModel()
) {
    AuthRoute(viewModel, interactiveAllowed, onClose, modifier)
}

@Composable
private fun AuthRoute(
    viewModel: AuthFormViewModel,
    interactiveAllowed: Boolean,
    onClose: () -> Unit,
    modifier: Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val backendAvailable by viewModel.isAvailable.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect == AuthUiEffect.Close) onClose()
        }
    }
    LaunchedEffect(interactiveAllowed) {
        if (!interactiveAllowed) viewModel.cancelOrRestrict()
    }

    AuthFormScreen(
        state = state,
        enabled = interactiveAllowed && backendAvailable,
        onSubmit = viewModel::submit,
        onResend = viewModel::resend,
        onCancel = viewModel::cancelOrRestrict,
        modifier = modifier
    )
}

@Composable
private fun AuthFormScreen(
    state: com.adrianrusu.pandawave.feature.auth.domain.AuthFormState,
    enabled: Boolean,
    onSubmit: (String, ByteArray) -> Unit,
    onResend: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalPandaWaveDesignTokens.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val isWorking = state.phase == AuthFormPhase.SUBMITTING ||
        state.phase == AuthFormPhase.FINISHING_SIGN_IN

    Column(
        modifier = modifier.fillMaxSize().testTag("auth-form"),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)
    ) {
        Text(
            text = stringResource(
                if (state.mode == AuthFormMode.LOGIN) {
                    R.string.pandawave_auth_login_title
                } else {
                    R.string.pandawave_auth_register_title
                }
            ),
            style = MaterialTheme.typography.headlineMedium
        )

        if (state.phase == AuthFormPhase.VERIFICATION_PENDING) {
            Text(stringResource(R.string.pandawave_auth_verification_title))
            Text(stringResource(R.string.pandawave_auth_verification_body))
            NoticeText(state.notice)
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md)) {
                Button(
                    enabled = enabled && !state.resendInFlight,
                    onClick = { onResend(email.trim()) }
                ) {
                    Text(stringResource(R.string.pandawave_auth_resend))
                }
                OutlinedButton(onClick = onCancel) {
                    Text(stringResource(R.string.pandawave_auth_cancel))
                }
            }
            return@Column
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth().testTag("auth-email"),
            enabled = enabled && !isWorking,
            singleLine = true,
            label = { Text(stringResource(R.string.pandawave_auth_email)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            )
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth().testTag("auth-password"),
            enabled = enabled && !isWorking,
            singleLine = true,
            label = { Text(stringResource(R.string.pandawave_auth_password)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            )
        )
        NoticeText(state.notice)
        if (!enabled) Text(stringResource(R.string.pandawave_auth_unavailable))
        if (state.phase == AuthFormPhase.FINISHING_SIGN_IN) {
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.lg)) {
                CircularProgressIndicator()
                Text(stringResource(R.string.pandawave_auth_finishing))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md)) {
            Button(
                modifier = Modifier.testTag("auth-submit"),
                enabled = enabled && !isWorking && email.isNotBlank() && password.isNotBlank(),
                onClick = {
                    val commandPassword = password.encodeToByteArray()
                    password = ""
                    onSubmit(email.trim(), commandPassword)
                }
            ) {
                Text(
                    stringResource(
                        if (state.mode == AuthFormMode.LOGIN) {
                            R.string.pandawave_auth_submit_login
                        } else {
                            R.string.pandawave_auth_submit_register
                        }
                    )
                )
            }
            OutlinedButton(onClick = {
                password = ""
                onCancel()
            }) {
                Text(stringResource(R.string.pandawave_auth_cancel))
            }
        }
    }
}

@Composable
private fun NoticeText(notice: AuthNotice?) {
    if (notice == null) return
    val message = when (notice) {
        AuthNotice.LOGIN_REJECTED -> R.string.pandawave_auth_login_rejected
        AuthNotice.REVIEW_INPUT -> R.string.pandawave_auth_review_input
        AuthNotice.RATE_LIMITED -> R.string.pandawave_auth_rate_limited
        AuthNotice.TRY_AGAIN_LATER -> R.string.pandawave_auth_try_later
        AuthNotice.SESSION_STORAGE_FAILED -> R.string.pandawave_auth_storage_failed
        AuthNotice.REQUEST_UNCONFIRMED -> R.string.pandawave_auth_unconfirmed
        AuthNotice.EMAIL_SENT -> R.string.pandawave_auth_email_sent
    }
    Text(stringResource(message), color = MaterialTheme.colorScheme.error)
}
