package com.simplr.mykitta2.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.simplr.mykitta2.domain.ThemeMode

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

// Dark palette intentionally relies on Material3 defaults seeded with the brand
// primary/secondary — keeps Blue/Red identity while letting Material derive
// surface/background contrast. A hand-tuned dark scheme can replace this later.
private val DarkColors = darkColorScheme(
    primary = MyKittaColors.BlueTertiary,
    secondary = MyKittaColors.RedSecondary,
    tertiary = MyKittaColors.BlueTertiary,
    error = MyKittaColors.Error,
)

@Composable
fun MyKittaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        content = content,
    )
}
