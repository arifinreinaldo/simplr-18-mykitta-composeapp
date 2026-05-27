package com.simplr.mykitta2.feature.address

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import com.simplr.mykitta2.domain.Address
import com.simplr.mykitta2.ui.common.PlatformBackButton
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * Shipment-address list. Reachable from the Profile menu via the top-level
 * [com.simplr.mykitta2.ui.nav.Destination.AddressList] destination — hosted
 * outside MainShell so the bottom navigation bar is hidden.
 *
 * Pull-to-refresh forces a network re-list (TTL bypass); the cache is the
 * source of truth otherwise. The "Address saved" snackbar is signalled from
 * the form screen via the parent's saved-state handle ([navEntry]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressListScreen(
    onBack: () -> Unit,
    onOpenForm: (customerAddressId: String?) -> Unit,
    navEntry: NavBackStackEntry,
    viewModel: AddressListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pullState = rememberPullToRefreshState()

    // Label observer — open form for create or edit.
    LaunchedEffect(viewModel) {
        viewModel.labels.collect { label ->
            when (label) {
                is AddressListStore.Label.OpenForm -> onOpenForm(label.customerAddressId)
            }
        }
    }

    // Error → snackbar.
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.accept(AddressListStore.Intent.DismissError)
        }
    }

    // Form → list signal: when the form pops back after saving, it sets
    // `address_saved = true` on this entry's saved-state handle. We observe
    // the flag, show a snackbar, and clear it.
    LaunchedEffect(navEntry) {
        val handle = navEntry.savedStateHandle
        handle.getStateFlow(KEY_ADDRESS_SAVED, false)
            .filter { it }
            .collect {
                handle[KEY_ADDRESS_SAVED] = false
                scope.launch { snackbarHostState.showSnackbar("Address saved") }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shipment Addresses") },
                navigationIcon = { PlatformBackButton(onClick = onBack) },
                actions = {
                    IconButton(onClick = { viewModel.accept(AddressListStore.Intent.AddTapped) }) {
                        Text("+", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { viewModel.accept(AddressListStore.Intent.Refresh) },
            state = pullState,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.initialLoading && state.addresses.isEmpty() ->
                    FullScreenLoader()
                state.addresses.isEmpty() ->
                    EmptyState(onAddTap = { viewModel.accept(AddressListStore.Intent.AddTapped) })
                else ->
                    AddressList(
                        addresses = state.addresses,
                        onTap = { viewModel.accept(AddressListStore.Intent.RowTapped(it.customerAddressId)) },
                        onSetDefault = { viewModel.accept(AddressListStore.Intent.SetDefault(it.customerAddressId)) },
                    )
            }
        }
    }
}

internal const val KEY_ADDRESS_SAVED = "address_saved"

@Composable
private fun AddressList(
    addresses: List<Address>,
    onTap: (Address) -> Unit,
    onSetDefault: (Address) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(addresses, key = { it.customerAddressId }) { address ->
            AddressCard(
                address = address,
                onClick = { onTap(address) },
                onSetDefault = { onSetDefault(address) },
            )
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun AddressCard(
    address: Address,
    onClick: () -> Unit,
    onSetDefault: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text("📍", fontSize = 22.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = address.name.ifEmpty { "Unnamed address" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    DefaultToggle(
                        isDefault = address.isSelected,
                        onSet = onSetDefault,
                    )
                }
                Spacer(Modifier.height(6.dp))
                val composed = composedAddressLines(address)
                Text(
                    text = composed,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (address.contact.isNotBlank() || address.phone.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = listOf(address.contact, address.phone)
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The "is this my default?" affordance. On the default row, shows a filled
 * "★ Default" badge (the tap is a no-op — already default). On other rows,
 * shows an outlined ☆ that fires [onSet]. Local-only state; no network call.
 */
@Composable
private fun DefaultToggle(
    isDefault: Boolean,
    onSet: () -> Unit,
) {
    if (isDefault) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                text = "★ Default",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    } else {
        Surface(
            onClick = onSet,
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                text = "☆ Set default",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

private fun composedAddressLines(a: Address): String {
    val line1 = a.address1
    val line2 = a.address2
    val locality = listOf(a.subdivision, a.barangay, a.city, a.province, a.zipcode)
        .filter { it.isNotBlank() }
        .joinToString(", ")
    return listOf(line1, line2, locality)
        .filter { it.isNotBlank() }
        .joinToString("\n")
}

@Composable
private fun EmptyState(onAddTap: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("📍", fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "No saved addresses",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Add an address to speed up future deliveries.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Surface(
            onClick = onAddTap,
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary,
        ) {
            Text(
                text = "+ Add address",
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun FullScreenLoader() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
