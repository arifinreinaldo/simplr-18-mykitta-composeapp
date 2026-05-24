package com.simplr.mykitta2.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.simplr.mykitta2.ui.common.MyKittaScaffold

/**
 * Terminal placeholder shown after a successful OTP verification. Real "logged-in"
 * surfaces (home, catalog, etc.) ship in later sub-projects; for now this confirms
 * the auth flow worked end-to-end.
 */
@Composable
fun SignedInPlaceholderScreen(onBackToLogin: () -> Unit) {
    MyKittaScaffold(title = "Signed in") { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(
                "You're signed in.",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                "The rest of MyKitta (home, catalog, orders) will land in the next sub-projects.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            OutlinedButton(onClick = onBackToLogin) { Text("Sign in again") }
        }
    }
}
