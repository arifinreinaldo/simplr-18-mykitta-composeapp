package com.simplr.mykitta2.feature.address

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplr.mykitta2.domain.Country
import com.simplr.mykitta2.ui.common.PlatformBackButton
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Create / edit a shipment address. Reachable from
 * [AddressListScreen] via callback; lives at the top of the nav stack so the
 * bottom bar stays hidden during entry.
 *
 * One screen covers both modes — `customerAddressId == null` is create,
 * otherwise it's edit-prefill (read from cache via [AddressFormStore]
 * bootstrap). PH-specific fields are hidden on SG users. On a successful
 * save, the screen signals the list via `navEntry.previousBackStackEntry`'s
 * saved-state handle and pops.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressFormScreen(
    customerAddressId: String?,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: AddressFormViewModel = koinViewModel(
        parameters = { parametersOf(AddressFormArgs(customerAddressId)) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.labels.collect { label ->
            when (label) {
                AddressFormStore.Label.Saved -> onSaved()
            }
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.accept(AddressFormStore.Intent.DismissError)
        }
    }

    val title = when (state.mode) {
        AddressFormStore.Mode.Create -> "Add address"
        is AddressFormStore.Mode.Edit -> "Edit address"
    }
    val saveEnabled = state.isValid && !state.submitting && !state.initialLoading

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { PlatformBackButton(onClick = onBack) },
                actions = {
                    TextButton(
                        enabled = saveEnabled,
                        onClick = { viewModel.accept(AddressFormStore.Intent.Submit) },
                    ) {
                        if (state.submitting) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Text("Save", fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.initialLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }
        FormBody(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            onFieldChanged = { field, value ->
                viewModel.accept(AddressFormStore.Intent.FieldChanged(field, value))
            },
        )
    }
}

@Composable
private fun FormBody(
    state: AddressFormStore.State,
    modifier: Modifier = Modifier,
    onFieldChanged: (AddressFormStore.Field, String) -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader("CONTACT")
        FormTextField(
            label = "Address label",
            value = state.fields.name,
            error = state.errors[AddressFormStore.Field.NAME],
            onValueChange = { onFieldChanged(AddressFormStore.Field.NAME, it) },
        )
        FormTextField(
            label = "Recipient name",
            value = state.fields.contact,
            error = state.errors[AddressFormStore.Field.CONTACT],
            onValueChange = { onFieldChanged(AddressFormStore.Field.CONTACT, it) },
        )
        FormTextField(
            label = "Phone",
            value = state.fields.phone,
            error = state.errors[AddressFormStore.Field.PHONE],
            keyboardType = KeyboardType.Phone,
            onValueChange = { onFieldChanged(AddressFormStore.Field.PHONE, it) },
        )

        Spacer(Modifier.height(4.dp))
        SectionHeader("ADDRESS")
        FormTextField(
            label = "Address line 1",
            value = state.fields.address1,
            error = state.errors[AddressFormStore.Field.ADDRESS1],
            onValueChange = { onFieldChanged(AddressFormStore.Field.ADDRESS1, it) },
        )
        FormTextField(
            label = "Address line 2 (optional)",
            value = state.fields.address2,
            error = state.errors[AddressFormStore.Field.ADDRESS2],
            onValueChange = { onFieldChanged(AddressFormStore.Field.ADDRESS2, it) },
        )
        FormTextField(
            label = "City",
            value = state.fields.city,
            error = state.errors[AddressFormStore.Field.CITY],
            onValueChange = { onFieldChanged(AddressFormStore.Field.CITY, it) },
        )
        FormTextField(
            label = "Postal code",
            value = state.fields.zipcode,
            error = state.errors[AddressFormStore.Field.ZIPCODE],
            keyboardType = KeyboardType.Number,
            onValueChange = { onFieldChanged(AddressFormStore.Field.ZIPCODE, it) },
        )

        // PH-specific block — mirrors the legacy split (PH users had a
        // dedicated form with these fields; SG users never saw them).
        AnimatedVisibility(visible = state.country == Country.PH) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Spacer(Modifier.height(4.dp))
                SectionHeader("PHILIPPINES DETAILS")
                FormTextField(
                    label = "Province",
                    value = state.fields.province,
                    error = state.errors[AddressFormStore.Field.PROVINCE],
                    onValueChange = { onFieldChanged(AddressFormStore.Field.PROVINCE, it) },
                )
                FormTextField(
                    label = "Barangay",
                    value = state.fields.barangay,
                    error = state.errors[AddressFormStore.Field.BARANGAY],
                    onValueChange = { onFieldChanged(AddressFormStore.Field.BARANGAY, it) },
                )
                FormTextField(
                    label = "Subdivision (optional)",
                    value = state.fields.subdivision,
                    error = state.errors[AddressFormStore.Field.SUBDIVISION],
                    onValueChange = { onFieldChanged(AddressFormStore.Field.SUBDIVISION, it) },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun FormTextField(
    label: String,
    value: String,
    error: String?,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = if (error != null) {
            { Text(error) }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}
