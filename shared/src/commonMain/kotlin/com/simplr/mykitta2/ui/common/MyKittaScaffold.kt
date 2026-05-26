package com.simplr.mykitta2.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyKittaScaffold(
    title: String? = null,
    onBack: (() -> Unit)? = null,
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            if (title != null || onBack != null) {
                TopAppBar(
                    title = { if (title != null) Text(title) },
                    navigationIcon = {
                        if (onBack != null) {
                            PlatformBackButton(onClick = onBack)
                        }
                    },
                )
            }
        },
        snackbarHost = snackbarHost,
        content = content,
    )
}

@Composable
fun <T> ListViewSeparated(
    items: List<T>,
    separator: @Composable () -> Unit,
    itemContent: @Composable (T) -> Unit
) {
    LazyColumn {
        itemsIndexed(items) { index, item ->

            itemContent(item)

            if (index < items.lastIndex) {
                separator()
            }
        }
    }
}