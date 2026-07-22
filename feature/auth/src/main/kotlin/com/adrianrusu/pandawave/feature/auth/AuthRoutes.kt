package com.adrianrusu.pandawave.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import com.adrianrusu.pandawave.feature.auth.domain.AuthField
import com.adrianrusu.pandawave.feature.auth.domain.AuthFieldError
import com.adrianrusu.pandawave.feature.auth.domain.AuthFieldFeedback
import com.adrianrusu.pandawave.feature.auth.domain.AuthFieldFeedbackPolicy
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
internal fun AuthFormScreen(
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
    var feedback by remember { mutableStateOf(AuthFieldFeedback()) }
    var emailWasFocused by remember { mutableStateOf(false) }
    var passwordWasFocused by remember { mutableStateOf(false) }
    val passwordFocusRequester = remember { FocusRequester() }
    val isWorking = state.phase == AuthFormPhase.SUBMITTING ||
        state.phase == AuthFormPhase.FINISHING_SIGN_IN
    val submit = {
        val submission = AuthFieldFeedbackPolicy.onSubmit(state.mode, email, password)
        feedback = submission.feedback
        if (submission.canSubmit && enabled && !isWorking) {
            val commandPassword = password.encodeToByteArray()
            password = ""
            emailWasFocused = false
            passwordWasFocused = false
            onSubmit(email.trim(), commandPassword)
        }
    }

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
            onValueChange = {
                email = it
                feedback = AuthFieldFeedbackPolicy.onEdit(feedback, AuthField.EMAIL)
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        emailWasFocused = true
                        feedback = AuthFieldFeedbackPolicy.onFocus(feedback, AuthField.EMAIL)
                    } else if (emailWasFocused && state.phase == AuthFormPhase.IDLE) {
                        feedback = AuthFieldFeedbackPolicy.onBlur(
                            feedback,
                            AuthField.EMAIL,
                            state.mode,
                            email,
                            password
                        )
                    }
                }
                .testTag("auth-email"),
            enabled = enabled && !isWorking,
            singleLine = true,
            isError = feedback.emailError != null,
            label = { Text(stringResource(R.string.pandawave_auth_email)) },
            supportingText = feedback.emailError?.let { error ->
                { Text(stringResource(error.messageResource())) }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { passwordFocusRequester.requestFocus() }
            )
        )
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                feedback = AuthFieldFeedbackPolicy.onEdit(feedback, AuthField.PASSWORD)
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(passwordFocusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        passwordWasFocused = true
                        feedback = AuthFieldFeedbackPolicy.onFocus(feedback, AuthField.PASSWORD)
                    } else if (passwordWasFocused && state.phase == AuthFormPhase.IDLE) {
                        feedback = AuthFieldFeedbackPolicy.onBlur(
                            feedback,
                            AuthField.PASSWORD,
                            state.mode,
                            email,
                            password
                        )
                    }
                }
                .testTag("auth-password"),
            enabled = enabled && !isWorking,
            singleLine = true,
            isError = feedback.passwordError != null,
            label = { Text(stringResource(R.string.pandawave_auth_password)) },
            supportingText = feedback.passwordError?.let { error ->
                { Text(stringResource(error.messageResource())) }
            },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { submit() })
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
                enabled = enabled && !isWorking,
                onClick = submit
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
        AuthNotice.POLICY_MISMATCH -> R.string.pandawave_auth_policy_mismatch
        AuthNotice.RATE_LIMITED -> R.string.pandawave_auth_rate_limited
        AuthNotice.TRY_AGAIN_LATER -> R.string.pandawave_auth_try_later
        AuthNotice.SESSION_STORAGE_FAILED -> R.string.pandawave_auth_storage_failed
        AuthNotice.REQUEST_UNCONFIRMED -> R.string.pandawave_auth_unconfirmed
        AuthNotice.EMAIL_SENT -> R.string.pandawave_auth_email_sent
    }
    Text(stringResource(message), color = MaterialTheme.colorScheme.error)
}

private fun AuthFieldError.messageResource(): Int = when (this) {
    AuthFieldError.EMAIL_REQUIRED -> R.string.pandawave_auth_email_required
    AuthFieldError.EMAIL_INVALID -> R.string.pandawave_auth_email_invalid
    AuthFieldError.PASSWORD_REQUIRED -> R.string.pandawave_auth_password_required
    AuthFieldError.PASSWORD_TOO_SHORT -> R.string.pandawave_auth_password_too_short
    AuthFieldError.PASSWORD_TOO_LONG -> R.string.pandawave_auth_password_too_long
}
