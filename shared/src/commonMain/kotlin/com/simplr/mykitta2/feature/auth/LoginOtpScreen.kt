package com.simplr.mykitta2.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplr.mykitta2.domain.Country
import mykitta.shared.generated.resources.Res
import mykitta.shared.generated.resources.app_logo
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LoginOtpScreen(
    onOtpSent: (phoneE164: String, userIdDigits: String, country: Country) -> Unit,
    viewModel: LoginOtpViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.labels.collect { label ->
            when (label) {
                is LoginOtpStore.Label.NavigateToOtpVerify ->
                    onOtpSent(label.phoneE164, label.userIdDigits, label.country)
            }
        }
    }

    LoginOtpContent(
        state = state,
        onPhoneChanged = { viewModel.accept(LoginOtpStore.Intent.PhoneChanged(it)) },
        onOpenCountrySelector = { viewModel.accept(LoginOtpStore.Intent.OpenCountrySelector) },
        onCloseCountrySelector = { viewModel.accept(LoginOtpStore.Intent.CloseCountrySelector) },
        onSelectCountry = { viewModel.accept(LoginOtpStore.Intent.SelectCountry(it)) },
        onSubmit = { viewModel.accept(LoginOtpStore.Intent.Submit) },
    )
}

@Composable
private fun LoginOtpContent(
    state: LoginOtpStore.State,
    onPhoneChanged: (String) -> Unit,
    onOpenCountrySelector: () -> Unit,
    onCloseCountrySelector: () -> Unit,
    onSelectCountry: (Country) -> Unit,
    onSubmit: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Scaffold(
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
                BrandHeader()

                Spacer(Modifier.height(28.dp))

                Text(
                    "Welcome",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Enter your phone number to receive a one-time password.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(28.dp))

                PhoneField(
                    state = state,
                    onPhoneChanged = onPhoneChanged,
                    onOpenCountrySelector = onOpenCountrySelector,
                )

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
                            "Send OTP",
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
                        ErrorBanner(message = state.error.orEmpty())
                    }
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    "By continuing, you agree to MyKitta's Terms and Privacy Policy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(0.75f),
                )
            }
        }
    }

    if (state.countrySelectorOpen) {
        CountrySelectorSheet(
            selected = state.country,
            onSelect = onSelectCountry,
            onDismiss = onCloseCountrySelector,
        )
    }
}

@Composable
private fun BrandHeader() {
    // Soft brand-blue tinted halo behind the logo for visual weight.
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(108.dp),
    ) {
        Box(
            modifier = Modifier
                .size(108.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(28.dp),
                ),
        )
        Image(
            painter = painterResource(Res.drawable.app_logo),
            contentDescription = null,
            modifier = Modifier.size(88.dp),
        )
    }
}

@Composable
private fun PhoneField(
    state: LoginOtpStore.State,
    onPhoneChanged: (String) -> Unit,
    onOpenCountrySelector: () -> Unit,
) {
    val shape = remember { RoundedCornerShape(14.dp) }
    OutlinedTextField(
        value = state.phoneFormatted,
        onValueChange = onPhoneChanged,
        placeholder = {
            Text(
                placeholderFor(state.country),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        },
        leadingIcon = {
            CountryChipInline(country = state.country, onClick = onOpenCountrySelector)
        },
        singleLine = true,
        shape = shape,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
    )
}

@Composable
private fun CountryChipInline(country: Country, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(start = 4.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(country.flagEmoji, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(6.dp))
        Text(
            country.dialCode,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(2.dp))
        Text(
            "▾",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        // Subtle separator between chip and input
        Box(
            modifier = Modifier
                .height(24.dp)
                .width(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        )
    }
}

@Composable
private fun ErrorBanner(message: String) {
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
                    )
                    .padding(horizontal = 8.dp, vertical = 0.dp),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun placeholderFor(country: Country): String = when (country) {
    Country.PH -> "9XX XXX XXXX"
    Country.SG -> "XXXX XXXX"
}
