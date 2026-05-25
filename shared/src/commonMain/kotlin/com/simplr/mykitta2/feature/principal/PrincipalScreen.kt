package com.simplr.mykitta2.feature.principal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.simplr.mykitta2.domain.Principal
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrincipalScreen(
    onOpenCatalog: (Principal) -> Unit = {},
) {
    val viewModel: PrincipalViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.labels.collect { label ->
            when (label) {
                is PrincipalStore.Label.OpenCatalog -> onOpenCatalog(label.principal)
            }
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Principal") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        PrincipalContent(
            modifier = Modifier.fillMaxSize().padding(padding),
            state = state,
            onPrincipalClick = { viewModel.accept(PrincipalStore.Intent.PrincipalClicked(it)) },
        )
    }
}

@Composable
private fun PrincipalContent(
    modifier: Modifier,
    state: PrincipalStore.State,
    onPrincipalClick: (Principal) -> Unit,
) {
    when {
        // First paint with no cache yet — show a spinner instead of an empty
        // grid. After the first emission (even an empty list), `loading` flips
        // to false and we fall through to the grid / empty branches.
        state.loading && state.principals.isEmpty() -> Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        state.principals.isEmpty() -> EmptyState(modifier = modifier)

        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = modifier,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            items(state.principals, key = { it.principalId }) { principal ->
                PrincipalCard(
                    principal = principal,
                    onClick = { onPrincipalClick(principal) },
                )
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🏷️", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "No principals yet",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "You'll see your brand suppliers here once they're assigned.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
    }
}

@Composable
private fun PrincipalCard(
    principal: Principal,
    onClick: () -> Unit,
) {
    // Inactive principals stay tappable (legacy parity — the user can still
    // browse the catalog) but render at reduced opacity so the difference is
    // visible at a glance.
    val cardAlpha = if (principal.isActive) 1f else 0.55f
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().alpha(cardAlpha),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val swatch = ColorPainter(principalSwatch(principal.principalId))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(principalSwatch(principal.principalId)),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = principal.principalImg,
                    contentDescription = principal.principalName,
                    contentScale = ContentScale.Crop,
                    placeholder = swatch,
                    error = swatch,
                    fallback = swatch,
                    modifier = Modifier.fillMaxSize(),
                )
                if (!principal.isActive) {
                    Text(
                        text = "INACTIVE",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = principal.principalName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// Deterministic placeholder colour per principal — same hashing approach as
// HomeScreen.itemSwatch so the grid stays stable across recompositions and
// matches the visual language of the item cards.
private fun principalSwatch(key: String): Color {
    val palette = listOf(
        Color(0xFF1565C0), Color(0xFF42A5F5), Color(0xFF7C3A8A),
        Color(0xFFDE5C2C), Color(0xFF2E8B57), Color(0xFFB23A48),
    )
    var h = 0
    for (c in key) h = (h * 31 + c.code) and 0x7fffffff
    return palette[h % palette.size]
}
