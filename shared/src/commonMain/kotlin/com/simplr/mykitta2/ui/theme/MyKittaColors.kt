package com.simplr.mykitta2.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Brand palette sampled from `ic_b2b_launcher-playstore.png`:
 *  - Primary blue: the "M" device. Material-tonal pick approximating the
 *    mid-gradient stop of the launcher icon.
 *  - Secondary red: the shopping-bag accent in the icon.
 *
 * Kept in sync with `androidApp/.../res/values/colors.xml` so XML system
 * surfaces (launcher background, status bar) and Compose surfaces match.
 */
internal object MyKittaColors {
    val BluePrimary = Color(0xFF1565C0)
    val BlueOnPrimary = Color(0xFFFFFFFF)
    val BluePrimaryContainer = Color(0xFFD3E3FD)
    val BlueOnPrimaryContainer = Color(0xFF001A41)

    val RedSecondary = Color(0xFFE53935)
    val RedOnSecondary = Color(0xFFFFFFFF)
    val RedSecondaryContainer = Color(0xFFFFDAD6)
    val RedOnSecondaryContainer = Color(0xFF410002)

    val BlueTertiary = Color(0xFF42A5F5)
    val BlueOnTertiary = Color(0xFFFFFFFF)

    val Background = Color(0xFFFFFFFF)
    val OnBackground = Color(0xFF1A1C1E)
    val Surface = Color(0xFFFFFFFF)
    val OnSurface = Color(0xFF1A1C1E)
    val SurfaceVariant = Color(0xFFE1E2EC)
    val OnSurfaceVariant = Color(0xFF44474F)
    val Outline = Color(0xFF74777F)

    val Error = Color(0xFFB3261E)
    val OnError = Color(0xFFFFFFFF)
}
