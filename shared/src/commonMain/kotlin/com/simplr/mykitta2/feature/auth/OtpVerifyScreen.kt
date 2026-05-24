package com.simplr.mykitta2.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplr.mykitta2.domain.Country
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun OtpVerifyScreen(
    phoneE164: String,
    userIdDigits: String,
    country: Country,
    onVerified: () -> Unit,
    onBack: () -> Unit,
    viewModel: OtpVerifyViewModel = koinViewModel {
        parametersOf(OtpVerifyArgs(userIdDigits = userIdDigits, country = country))
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.labels.collect { label ->
            when (label) {
                OtpVerifyStore.Label.NavigateToLoggedIn -> onVerified()
                OtpVerifyStore.Label.OtpResent -> snackbarHostState.showSnackbar("OTP resent")
            }
        }
    }

    OtpVerifyContent(
        phoneE164 = phoneE164,
        state = state,
        snackbarHostState = snackbarHostState,
        onOtpChanged = { viewModel.accept(OtpVerifyStore.Intent.OtpChanged(it)) },
        onSubmit = { viewModel.accept(OtpVerifyStore.Intent.Submit) },
        onResend = { viewModel.accept(OtpVerifyStore.Intent.Resend) },
        onBack = onBack,
    )
}

@Composable
private fun OtpVerifyContent(
    phoneE164: String,
    state: OtpVerifyStore.State,
    snackbarHostState: SnackbarHostState,
    onOtpChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Verify your number",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Enter the ${OtpVerifyStore.OTP_LENGTH}-digit code we sent to",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    phoneE164,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Spacer(Modifier.height(28.dp))

                OtpField(otp = state.otp, onOtpChanged = onOtpChanged)

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = onSubmit,
                    enabled = state.isValid && !state.submitting,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                ) {
                    if (state.submitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = LocalContentColor.current,
                            strokeWidth = 2.5.dp,
                        )
                    } else {
                        Text(
                            "Verify",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                AnimatedVisibility(
                    visible = state.error != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column {
                        Spacer(Modifier.height(16.dp))
                        ErrorChip(message = state.error.orEmpty())
                    }
                }

                Spacer(Modifier.height(20.dp))

                ResendRow(
                    canResend = state.canResend,
                    resending = state.resending,
                    cooldownSeconds = state.resendCountdownSeconds,
                    onResend = onResend,
                )

                Spacer(Modifier.height(4.dp))

                TextButton(onClick = onBack) {
                    Text(
                        "Change number",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun OtpField(otp: String, onOtpChanged: (String) -> Unit) {
    val shape = remember { RoundedCornerShape(14.dp) }
    OutlinedTextField(
        value = otp,
        onValueChange = onOtpChanged,
        singleLine = true,
        shape = shape,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        textStyle = TextStyle(
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 18.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
    )
}

@Composable
private fun ResendRow(
    canResend: Boolean,
    resending: Boolean,
    cooldownSeconds: Int,
    onResend: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            "Didn't receive it?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        if (canResend) {
            TextButton(
                onClick = onResend,
                enabled = !resending,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                if (resending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        "Resend OTP",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        } else {
            Text(
                "Resend in ${formatMmSs(cooldownSeconds)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ErrorChip(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .size(22.dp)
                    .background(
                        MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(50),
                    ),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.width(10.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// kotlin-stdlib commonMain has no String.format — hand-format M:SS for portability.
private fun formatMmSs(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val m = safe / 60
    val s = safe % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
