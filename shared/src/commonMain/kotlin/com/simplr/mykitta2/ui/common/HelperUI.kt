package com.simplr.mykitta2.ui.common

import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Top-bar action button: an emoji glyph with an optional numeric badge.
 *
 * `BadgedBox` wraps the glyph (not the surrounding `IconButton`) so the badge
 * anchors to the icon's visual edge instead of the 48.dp touch-target corner —
 * which is what makes the badge sit visibly far from the icon when wrapped the
 * other way around.
 *
 * @param badgeCount when > 0 renders the badge; 0 hides it entirely.
 */
@Composable
fun BadgedIconButton(
    glyph: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
    fontSize: TextUnit = 20.sp,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        if (badgeCount > 0) {
            BadgedBox(badge = { Badge { Text(badgeCount.toString()) } }) {
                Text(glyph, fontSize = fontSize)
            }
        } else {
            Text(glyph, fontSize = fontSize)
        }
    }
}
