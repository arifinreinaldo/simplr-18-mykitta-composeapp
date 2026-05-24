package com.simplr.mykitta2.feature.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simplr.mykitta2.domain.Country

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountrySelectorSheet(
    selected: Country,
    onSelect: (Country) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = "Select country",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Country.entries.forEach { country ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(country) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${country.flagEmoji}  ${displayName(country)}  ·  ${country.dialCode}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (country == selected) {
                        Text("✓", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

private fun displayName(country: Country): String = when (country) {
    Country.PH -> "Philippines"
    Country.SG -> "Singapore"
}
