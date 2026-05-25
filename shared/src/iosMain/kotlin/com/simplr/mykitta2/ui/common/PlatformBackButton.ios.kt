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

/** SF Symbol `chevron.backward`: just the chevron, no shaft. Thinner stroke
 *  (~10% of glyph) and steeper arms than the Material arrow — matches the
 *  hairline look the system back chevron has across iOS apps. */
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
        val strokeWidth = w * 0.10f
        val midY = h / 2f
        // Apex sits left-of-center, arms sweep up/down to roughly 0.7 width.
        val apexX = w * 0.34f
        val armX = w * 0.66f
        val armDy = h * 0.30f
        drawLine(color, Offset(apexX, midY), Offset(armX, midY - armDy), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(apexX, midY), Offset(armX, midY + armDy), strokeWidth, StrokeCap.Round)
    }
}
