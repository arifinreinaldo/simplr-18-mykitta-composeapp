package com.simplr.mykitta2.feature.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplr.mykitta2.ui.common.ListViewSeparated
import com.simplr.mykitta2.ui.common.MyKittaScaffold

data class Menu(
    val icon: String,
    val title: String
)

@Composable
fun ProfileScreen() {
    MyKittaScaffold(title = "Profile") { padding ->
        Box(
            modifier = Modifier.fillMaxWidth().padding(padding).padding(horizontal = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            ListViewSeparated(
                items = listOf(
                    Menu("\uD83D\uDC64", "Profile"),
                    Menu("\uD83D\uDC65", "List Store"),
                    Menu("\uD83D\uDE9A", "Shipment Address"),
                    Menu("\uD83E\uDDFE", "History"),
                    Menu("\uD83C\uDFEA", "Request Principal"),
                    Menu("❓", "FAQ"),
                    Menu("☀\uFE0F", "Theme"),
                    Menu("▶\uFE0F", "Play Tutorial"),
                    Menu("ℹ\uFE0F", "About"),

                    ),
                separator = {
                    HorizontalDivider()
                },
                itemContent = { menu ->
                    Row(modifier = Modifier.padding(vertical = 5.dp)) {
                        Text(text = menu.icon, fontSize = 20.sp)
                        Text(menu.title)
                    }

                }
            )
        }
    }
}