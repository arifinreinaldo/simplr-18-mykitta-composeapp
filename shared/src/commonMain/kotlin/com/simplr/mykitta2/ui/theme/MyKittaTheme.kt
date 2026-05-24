package com.simplr.mykitta2.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = MyKittaColors.BluePrimary,
    onPrimary = MyKittaColors.BlueOnPrimary,
    primaryContainer = MyKittaColors.BluePrimaryContainer,
    onPrimaryContainer = MyKittaColors.BlueOnPrimaryContainer,
    secondary = MyKittaColors.RedSecondary,
    onSecondary = MyKittaColors.RedOnSecondary,
    secondaryContainer = MyKittaColors.RedSecondaryContainer,
    onSecondaryContainer = MyKittaColors.RedOnSecondaryContainer,
    tertiary = MyKittaColors.BlueTertiary,
    onTertiary = MyKittaColors.BlueOnTertiary,
    background = MyKittaColors.Background,
    onBackground = MyKittaColors.OnBackground,
    surface = MyKittaColors.Surface,
    onSurface = MyKittaColors.OnSurface,
    surfaceVariant = MyKittaColors.SurfaceVariant,
    onSurfaceVariant = MyKittaColors.OnSurfaceVariant,
    outline = MyKittaColors.Outline,
    error = MyKittaColors.Error,
    onError = MyKittaColors.OnError,
)

@Composable
fun MyKittaTheme(content: @Composable () -> Unit) {
    // Dark mode is deferred — the launcher icon assumes a light backdrop. When dark
    // mode lands, derive a darkColorScheme that keeps Blue/Red identity but lifts
    // tonal contrast for surfaces.
    MaterialTheme(colorScheme = LightColors, content = content)
}
