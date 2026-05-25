package com.simplr.mykitta2.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A back button that wears the host platform's chevron shape:
 *  Android renders the chunky Material `arrow_back`, iOS renders the thin
 *  SF-Symbol `chevron.backward`. The container is a Material3
 *  FilledTonalIconButton so it reads as a proper tap target on either OS. */
@Composable
fun PlatformBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Back",
) {
    FilledTonalIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier.padding(start = 4.dp),
    ) {
        PlatformBackChevron(contentDescription)
    }
}

/** Draws the back glyph in the host platform's style. Tint is inherited from
 *  the enclosing IconButton's `contentColor` via `LocalContentColor`. */
@Composable
internal expect fun PlatformBackChevron(contentDescription: String)
