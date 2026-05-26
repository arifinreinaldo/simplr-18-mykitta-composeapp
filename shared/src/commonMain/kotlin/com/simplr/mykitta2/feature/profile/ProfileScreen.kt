package com.simplr.mykitta2.feature.profile

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplr.mykitta2.core.env.BuildEnv
import com.simplr.mykitta2.data.prefs.ThemeStore
import com.simplr.mykitta2.domain.ThemeMode
import com.simplr.mykitta2.ui.common.MyKittaScaffold
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private data class MenuItem(
    val id: String,
    val icon: String,
    val title: String,
)

private data class MenuSection(
    val title: String,
    val items: List<MenuItem>,
)

private val SECTIONS = listOf(
    MenuSection(
        title = "Account",
        items = listOf(
            MenuItem("profile", "👤", "Profile"),
            MenuItem("stores", "👥", "Store Partner"),
            MenuItem("shipment", "🚚", "Shipment Address"),
            MenuItem("history", "🧾", "History"),
        ),
    ),
    MenuSection(
        title = "Business",
        items = listOf(
            MenuItem("principal", "🏪", "Request Principal"),
        ),
    ),
    MenuSection(
        title = "Help & info",
        items = listOf(
            MenuItem("faq", "❓", "FAQ"),
            MenuItem("tutorial", "▶️", "Play Tutorial"),
            MenuItem("about", "ℹ️", "About"),
        ),
    ),
    MenuSection(
        title = "Settings",
        items = listOf(
            MenuItem("theme", "☀️", "Theme"),
        ),
    ),
)

@Composable
fun ProfileScreen(
    onMenuClick: (String) -> Unit = {},
    onLogout: () -> Unit = {},
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Dialog survives recomposition + config change; the logout action is
    // destructive enough that an accidental re-show on rotation is preferable
    // to silently swallowing the user's intent.
    var showLogoutConfirm by rememberSaveable { mutableStateOf(false) }
    var showThemePicker by rememberSaveable { mutableStateOf(false) }

    val themeStore: ThemeStore = koinInject()
    val themeMode by themeStore.mode.collectAsStateWithLifecycle()

    MyKittaScaffold(title = "Profile") { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item("header") {
                    // Header tracks `state.profile.custName` once GetProfile lands.
                    // While we wait for the first cache hit / network response on a
                    // cold first open, fall back to a generic greeting — surfacing
                    // the legacy `Session.userName` (a phone number) here would look
                    // weird, and we don't want to flash it for half a second.
                    ProfileHeader(
                        name = state.headerName.ifBlank { "there" },
                        subtitle = "Welcome back",
                    )
                }
                items(items = SECTIONS, key = { it.title }) { section ->
                    MenuSectionCard(
                        section = section,
                        onClick = { id ->
                            // Theme is handled in-screen via dialog; everything else
                            // bubbles up so MainShell can route to a dedicated screen.
                            if (id == "theme") showThemePicker = true
                            else onMenuClick(id)
                        },
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                VersionLabel()
                LogoutButton(onClick = { showLogoutConfirm = true })
            }
        }
    }

    if (showLogoutConfirm) {
        LogoutConfirmDialog(
            onConfirm = {
                showLogoutConfirm = false
                onLogout()
            },
            onDismiss = { showLogoutConfirm = false },
        )
    }

    if (showThemePicker) {
        ThemePickerDialog(
            current = themeMode,
            onSelect = { selected ->
                themeStore.set(selected)
                showThemePicker = false
            },
            onDismiss = { showThemePicker = false },
        )
    }
}

@Composable
private fun ThemePickerDialog(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Theme") },
        text = {
            Column {
                ThemeOptionRow(
                    label = "System default",
                    selected = current == ThemeMode.SYSTEM,
                    onClick = { onSelect(ThemeMode.SYSTEM) },
                )
                ThemeOptionRow(
                    label = "Light",
                    selected = current == ThemeMode.LIGHT,
                    onClick = { onSelect(ThemeMode.LIGHT) },
                )
                ThemeOptionRow(
                    label = "Dark",
                    selected = current == ThemeMode.DARK,
                    onClick = { onSelect(ThemeMode.DARK) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun ThemeOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun LogoutConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log out?") },
        text = {
            Text("You'll be signed out and your locally stored data will be cleared. You can sign back in any time.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "Log out",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ProfileHeader(name: String, subtitle: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(name = name)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "$subtitle, $name",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun Avatar(name: String) {
    val initials = remember(name) {
        name.trim()
            .split(' ', limit = 2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .take(2)
            .ifEmpty { "?" }
    }
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun MenuSectionCard(
    section: MenuSection,
    onClick: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = section.title.uppercase(),
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
                section.items.forEachIndexed { index, item ->
                    MenuRow(item = item, onClick = { onClick(item.id) })
                    if (index < section.items.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 64.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(item: MenuItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = item.icon, fontSize = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "›",
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VersionLabel() {
    Text(
        text = "Simplr Ver.${BuildEnv.versionName}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    )
}

@Composable
private fun LogoutButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Box(
            modifier = Modifier.padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Log out",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
