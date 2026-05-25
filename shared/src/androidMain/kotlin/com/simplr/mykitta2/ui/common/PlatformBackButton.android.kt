package com.simplr.mykitta2.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Material `arrow_back`: a thick horizontal shaft terminating in a chevron
 *  on the leading side. Stroke ~14% of the glyph size with round caps. */
@Composable
internal actual fun PlatformBackChevron(contentDescription: String) {
    val color = LocalContentColor.current
    Canvas(
        modifier = Modifier
            .size(20.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        val w = size.width
        val h = size.height
        val strokeWidth = w * 0.14f
        val midY = h / 2f
        val tipX = w * 0.22f
        val tailX = w * 0.82f
        val armDy = h * 0.26f
        drawLine(color, Offset(tipX, midY), Offset(tailX, midY), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(tipX, midY), Offset(tipX + armDy, midY - armDy), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(tipX, midY), Offset(tipX + armDy, midY + armDy), strokeWidth, StrokeCap.Round)
    }
}
