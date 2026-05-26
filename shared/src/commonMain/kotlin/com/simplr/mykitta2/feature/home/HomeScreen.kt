package com.simplr.mykitta2.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.simplr.mykitta2.domain.Banner
import com.simplr.mykitta2.domain.CategoryRail
import com.simplr.mykitta2.domain.Item
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration.Companion.seconds

private const val BANNER_AUTO_ADVANCE_SECONDS = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = koinViewModel(),
    onOpenCart: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenRewards: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.labels.collect { label ->
            when (label) {
                is HomeStore.Label.ShowSnackbar ->
                    scope.launch { snackbarHostState.showSnackbar(label.text) }
            }
        }
    }

    // Surface repository failures the store funnels into state.error. Keying on
    // the error string means re-showing if a retry surfaces the same message
    // (store nulls error on RefreshStarted so the key transitions X → null → X).
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { HomeSearchBar(onClick = onOpenSearch) },
                actions = {
                    // material-icons-extended isn't in the deps yet — use plain text
                    // glyphs so this slice stays in the existing dep graph.
                    BadgedBox(
                        badge = {
                            if (state.notifCount > 0) Badge { Text(state.notifCount.toString()) }
                        }
                    ) {
                        IconButton(onClick = onOpenNotifications) { Text("🔔", fontSize = 20.sp) }
                    }
                    IconButton(onClick = onOpenCart) { Text("🛒", fontSize = 20.sp) }
                    IconButton(onClick = onOpenChat) { Text("💬", fontSize = 20.sp) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        HomeContent(
            modifier = Modifier.fillMaxSize().padding(padding),
            state = state,
            onItemClick = { viewModel.accept(HomeStore.Intent.ItemClicked(it)) },
            onBannerClick = { viewModel.accept(HomeStore.Intent.BannerClicked(it)) },
            onOpenRewards = onOpenRewards,
            onRefreshPoints = { viewModel.accept(HomeStore.Intent.RefreshPoints) },
        )
    }
}

@Composable
private fun HomeSearchBar(onClick: () -> Unit) {
    // Looks like a text field, behaves like a button. Tapping it routes to the
    // dedicated SearchScreen where a real TextField takes focus — keeps the
    // home top bar inert so it doesn't steal IME focus on entry.
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().height(40.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🔍", fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Search Item Here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HomeContent(
    modifier: Modifier,
    state: HomeStore.State,
    onItemClick: (Item) -> Unit,
    onBannerClick: (Banner) -> Unit,
    onOpenRewards: () -> Unit,
    onRefreshPoints: () -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("banner") {
            BannerCarousel(
                banners = state.banners,
                loading = state.bannersLoading,
                onClick = onBannerClick,
            )
        }
        item("points") {
            PointsCard(
                points = state.points,
                loading = state.pointsLoading,
                onClick = onOpenRewards,
                onRefresh = onRefreshPoints,
            )
        }
        if (state.railsLoading) {
            item("rails-loading") {
                Box(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
        } else {
            items(state.rails, key = { it.functionName }) { rail ->
                Rail(rail = rail, onItemClick = onItemClick)
            }
        }
    }
}

@Composable
private fun PointsCard(
    points: Int,
    loading: Boolean,
    onClick: () -> Unit,
    onRefresh: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🎁", fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Points",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${points.formatThousands()} pts",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    if (loading) {
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    } else {
                        IconButton(onClick = onRefresh) {
                            Text("⟳", fontSize = 20.sp)
                        }
                    }
                }
            }
            Text(
                text = "Rewards",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

private fun Int.formatThousands(): String {
    val s = this.toString()
    if (s.length <= 3) return s
    val sb = StringBuilder()
    val rem = s.length % 3
    for (i in s.indices) {
        if (i != 0 && (i - rem) % 3 == 0) sb.append(',')
        sb.append(s[i])
    }
    return sb.toString()
}

@Composable
private fun BannerCarousel(
    banners: List<Banner>,
    loading: Boolean,
    onClick: (Banner) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading -> CircularProgressIndicator()
            banners.isEmpty() -> Text(
                "No banners",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            else -> {
                // Infinite-scroll trick: pageCount is Int.MAX_VALUE so the user can
                // swipe (and auto-advance can step) in either direction forever, with
                // `page % realCount` mapping back into the real banner list. Initial
                // page sits at a multiple of realCount near the middle of the range so
                // there's effectively unbounded headroom both ways before overflowing.
                // Falls back to a plain finite pager when there's only one banner.
                val realCount = banners.size
                val pagerState = rememberPagerState(
                    initialPage = if (realCount > 1) {
                        (Int.MAX_VALUE / 2) - (Int.MAX_VALUE / 2) % realCount
                    } else 0,
                    pageCount = { if (realCount > 1) Int.MAX_VALUE else realCount },
                )
                LaunchedEffect(realCount) {
                    if (realCount <= 1) return@LaunchedEffect
                    while (true) {
                        delay(BANNER_AUTO_ADVANCE_SECONDS.seconds)
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                }
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    val index = page % realCount
                    val banner = banners[index]
                    val fallback = ColorPainter(bannerSwatch(index))
                    // Caption is a fallback affordance — only visible while the image is
                    // loading, failed, or absent; once Coil reports Success the artwork
                    // speaks for itself. Key on bannerImg so paging resets the state.
                    var imageLoaded by remember(banner.bannerImg) { mutableStateOf(false) }
                    Surface(
                        onClick = { onClick(banner) },
                        color = bannerSwatch(index),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            AsyncImage(
                                model = banner.bannerImg,
                                contentDescription = banner.bannerName,
                                contentScale = ContentScale.FillBounds,
                                placeholder = fallback,
                                error = fallback,
                                fallback = fallback,
                                onSuccess = { imageLoaded = true },
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (!imageLoaded) {
                                Text(
                                    text = banner.bannerName,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 20.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(24.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Rail(
    rail: CategoryRail,
    onItemClick: (Item) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        when {
            rail.loading -> Box(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            rail.items.isEmpty() -> Text(
                text = "No items",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            else -> LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(rail.items, key = { it.productId }) { item ->
                    ItemCard(item = item, onClick = { onItemClick(item) })
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ItemCard(item: Item, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.width(140.dp),
    ) {
        Column(Modifier.padding(8.dp)) {
            val swatch = ColorPainter(itemSwatch(item.productId))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(itemSwatch(item.productId)),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = item.productUrl,
                    contentDescription = item.productDesc,
                    contentScale = ContentScale.Crop,
                    placeholder = swatch,
                    error = swatch,
                    fallback = swatch,
                    modifier = Modifier.fillMaxSize(),
                )
                if (item.isSoldOut) {
                    // Re-overlay on top of the loaded image so the badge stays
                    // visible regardless of which state Coil settles on.
                    Text(
                        "SOLD OUT",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = item.productDesc,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${item.unitPrice} / ${item.displayUom}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// Stable colour per banner / item from its key — keeps placeholders consistent
// across recompositions so the screen doesn't shimmer to a new colour on scroll.
private fun bannerSwatch(index: Int): Color = listOf(
    Color(0xFF7C3A8A), Color(0xFF1F6FD6), Color(0xFFDE5C2C),
    Color(0xFF2E8B57), Color(0xFFB23A48),
)[index % 5]

private fun itemSwatch(key: String): Color {
    val palette = listOf(
        Color(0xFF8E9AAF), Color(0xFFCBC0D3), Color(0xFFEFD3D7),
        Color(0xFFFEEAFA), Color(0xFFDEE2FF), Color(0xFFB8C0FF),
    )
    var h = 0
    for (c in key) h = (h * 31 + c.code) and 0x7fffffff
    return palette[h % palette.size]
}
