package com.simplr.mykitta2.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplr.mykitta2.domain.Profile
import com.simplr.mykitta2.ui.common.MyKittaScaffold
import org.koin.compose.viewmodel.koinViewModel

/**
 * Dedicated profile-detail page reached from the My Profile tab's "Profile"
 * menu row. Renders all GetProfile fields with friendly labels: CustName,
 * Phone, Email, ICPartner (as "Registration No"), GSTNo (as "Tax No").
 *
 * Re-uses [ProfileViewModel] (factory-scoped in Koin → a fresh instance per
 * screen), which means the bootstrap fires another `loadProfile()` — but
 * with the 24h cache that's a synchronous cache read, not a network hit.
 */
@Composable
fun ProfileDetailScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    MyKittaScaffold(title = "Profile", onBack = onBack) { padding ->
        when {
            state.profile != null -> ProfileDetailContent(
                profile = state.profile!!,
                padding = padding,
            )
            state.loading -> CenteredSpinner(padding = padding)
            else -> EmptyState(padding = padding, error = state.error)
        }
    }
}

@Composable
private fun ProfileDetailContent(profile: Profile, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item("identity") {
            DetailCard(
                title = "IDENTITY",
                rows = listOf(
                    "Customer" to profile.custName,
                    "Phone" to profile.phone,
                    "Email" to profile.email,
                ),
            )
        }
        item("registration") {
            DetailCard(
                title = "REGISTRATION",
                rows = listOf(
                    "Outlet No" to profile.icPartner,
                    "Tax No" to profile.gstNo,
                ),
            )
        }
    }
}

@Composable
private fun DetailCard(title: String, rows: List<Pair<String, String>>) {
    // Skip the whole section if every row is blank — keeps the screen tidy
    // when a partial response only fills part of the profile.
    val visible = rows.filter { it.second.isNotBlank() }
    if (visible.isEmpty()) return

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                visible.forEachIndexed { index, (label, value) ->
                    DetailRow(label = label, value = value)
                    if (index < visible.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(140.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CenteredSpinner(padding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(strokeWidth = 2.dp)
    }
}

@Composable
private fun EmptyState(padding: PaddingValues, error: String?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "We couldn't load your profile",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!error.isNullOrBlank()) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Spacer-ish — height pad so the column visually centers above the
            // bottom of the box rather than sitting flush at it.
            Box(modifier = Modifier.height(8.dp))
        }
    }
}
