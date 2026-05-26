package com.simplr.mykitta2.feature.history

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.domain.Order
import com.simplr.mykitta2.domain.OrderItemPreview
import com.simplr.mykitta2.domain.OrderStatus
import com.simplr.mykitta2.ui.common.PlatformBackButton
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit = {},
    viewModel: HistoryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.labels.collect { label ->
            when (label) {
                is HistoryStore.Label.Error -> snackbarHostState.showSnackbar(label.message)
            }
        }
    }

    val statuses = remember { OrderStatus.entries.toList() }
    val pagerState = rememberPagerState(
        initialPage = state.currentTab.ordinal,
        pageCount = { statuses.size },
    )

    // Pager → store: swiping switches the active tab.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                val status = statuses[page]
                if (status != state.currentTab) {
                    viewModel.accept(HistoryStore.Intent.SelectTab(status))
                }
            }
    }

    // Store → pager: tapping a Tab updates currentTab; without this sync, the
    // pager would stay on the previously-visible page (showing stale rows from
    // the prior tab while the tab indicator highlights the new one).
    LaunchedEffect(state.currentTab) {
        val targetPage = state.currentTab.ordinal
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = { PlatformBackButton(onClick = onBack) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = state.currentTab.ordinal) {
                statuses.forEachIndexed { index, status ->
                    Tab(
                        selected = state.currentTab == status,
                        onClick = { viewModel.accept(HistoryStore.Intent.SelectTab(status)) },
                        text = { Text(status.label) },
                    )
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val status = statuses[page]
                val tab = state.tabs.getValue(status)
                HistoryTabContent(
                    status = status,
                    tab = tab,
                    onRefresh = { viewModel.accept(HistoryStore.Intent.Refresh) },
                    onLoadMore = { viewModel.accept(HistoryStore.Intent.LoadMore) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryTabContent(
    status: OrderStatus,
    tab: HistoryStore.TabState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                ?: return@derivedStateOf false
            last >= tab.orders.size - 3 && tab.orders.isNotEmpty()
        }
    }
    LaunchedEffect(shouldLoadMore, tab.orders.size) {
        snapshotFlow { shouldLoadMore }
            .filter { it }
            .collect { onLoadMore() }
    }

    PullToRefreshBox(
        isRefreshing = tab.initialLoad is Outcome.Loading,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            // Empty-state branches come first — they apply for Idle / Loading /
            // Success / Failure as long as the per-tab cache is empty. The
            // PullToRefreshBox spinner is the only "loading" affordance we
            // need; without these branches, the pager would silently render a
            // blank LazyColumn for never-fetched tabs (looks like stale data
            // from the adjacent tab to the user).
            tab.orders.isEmpty() && tab.initialLoad is Outcome.Failure ->
                CenteredMessage("Couldn't load history. Pull to retry.")
            tab.orders.isEmpty() ->
                CenteredMessage("No ${status.label} Order at the Moment")
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = tab.orders, key = { it.invNo }) { order ->
                    OrderCard(order)
                }
                if (tab.pagination is Outcome.Loading) {
                    item("loading") {
                        Box(
                            Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                        }
                    }
                }
                if (!tab.hasMore && tab.orders.isNotEmpty()) {
                    item("end") {
                        Box(
                            Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "No more results",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OrderCard(order: Order) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = order.invNo,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(order.status)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = order.principalName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            order.firstProduct?.let { preview ->
                FirstItemRow(
                    preview = preview,
                    extraItems = (order.itemCount - 1).coerceAtLeast(0),
                )
                Spacer(Modifier.height(12.dp))
            }
            Row {
                Text(
                    text = order.invDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${currencySymbol(order.currency)}${formatAmount(order.total)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun FirstItemRow(preview: OrderItemPreview, extraItems: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = preview.imageUrl,
            contentDescription = preview.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = preview.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${preview.qty} item${if (preview.qty == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (extraItems > 0) {
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = "+$extraItems more",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusPill(status: OrderStatus) {
    val bg = when (status) {
        OrderStatus.WAITING -> MaterialTheme.colorScheme.surfaceVariant
        OrderStatus.PROCESSED -> MaterialTheme.colorScheme.tertiaryContainer
        OrderStatus.ON_DELIVERY -> MaterialTheme.colorScheme.primaryContainer
        OrderStatus.FINISHED -> MaterialTheme.colorScheme.secondaryContainer
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bg,
    ) {
        Text(
            text = status.label,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private fun formatAmount(value: Double): String {
    val cents = (value * 100).toLong()
    val whole = cents / 100
    val frac = (cents % 100).toString().padStart(2, '0')
    return "$whole.$frac"
}

/** Display symbol for an ISO currency code. Falls back to the code itself
 *  (with a trailing space) for unmapped currencies so the amount stays
 *  unambiguous. */
private fun currencySymbol(code: String): String = when (code) {
    "PHP" -> "₱"
    "SGD" -> "S$"
    else -> "$code "
}
