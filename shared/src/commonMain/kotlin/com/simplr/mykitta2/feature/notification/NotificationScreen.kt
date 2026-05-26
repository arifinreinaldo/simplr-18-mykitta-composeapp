package com.simplr.mykitta2.feature.notification

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplr.mykitta2.domain.Notification
import com.simplr.mykitta2.ui.common.MyKittaScaffold
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun NotificationScreen(
    onBack: () -> Unit,
    onOpenPrincipal: (principalId: String, principalName: String) -> Unit,
    viewModel: NotificationViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Label observer — type-routed nav + transient snackbars.
    LaunchedEffect(viewModel) {
        viewModel.labels.collect { label ->
            when (label) {
                is NotificationStore.Label.NavigateToPrincipal ->
                    onOpenPrincipal(label.principalId, label.principalName)
                NotificationStore.Label.NavigateUnsupportedType ->
                    scope.launch { snackbarHostState.showSnackbar("Coming soon") }
                is NotificationStore.Label.ShowSnackbar ->
                    scope.launch { snackbarHostState.showSnackbar(label.text) }
            }
        }
    }

    // State.error → snackbar (mirrors HomeScreen).
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
    }

    // Infinite-scroll trigger: fire LoadNextPage when the user reaches the
    // last few rows. distinctUntilChanged keeps a single fire per row crossing.
    LaunchedEffect(listState, state.endReached, state.items.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                val items = state.items
                if (!state.endReached && !state.loadingMore && items.isNotEmpty()
                    && lastVisible >= items.lastIndex - 3) {
                    viewModel.accept(NotificationStore.Intent.LoadNextPage)
                }
            }
    }

    MyKittaScaffold(
        title = "Notifications",
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.firstLoadInFlight -> FullScreenLoader(padding)
            state.items.isEmpty() -> EmptyState(padding)
            else -> NotificationList(
                state = state,
                listState = listState,
                padding = padding,
                onTap = { viewModel.accept(NotificationStore.Intent.TapItem(it)) },
            )
        }
    }
}

@Composable
private fun NotificationList(
    state: NotificationStore.State,
    listState: LazyListState,
    padding: PaddingValues,
    onTap: (Notification) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(padding),
    ) {
        if (state.showingCache) {
            item("cache-banner") { OfflineCacheBanner() }
        }
        items(state.items, key = { it.id }) { notification ->
            NotificationRow(notification = notification, onClick = { onTap(notification) })
        }
        if (state.loadingMore) {
            item("loading-more") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
        if (state.endReached && state.items.isNotEmpty()) {
            item("end") {
                Text(
                    text = "You're all caught up",
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NotificationRow(
    notification: Notification,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = if (notification.isRead) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (notification.isRead) Color.Transparent
                                else MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                    ),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = notification.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = notification.createdAt,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun OfflineCacheBanner() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("📡", fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Showing cached notifications",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun EmptyState(padding: PaddingValues) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🔔", fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "No notifications yet",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "When you have notifications, they'll show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FullScreenLoader(padding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
    ) { CircularProgressIndicator() }
}
