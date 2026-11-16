package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LifePilotDarkColorScheme = darkColorScheme(
    primary = ElectricBlueBright,
    onPrimary = NavyMidnight,
    primaryContainer = ElectricBlueContainer,
    onPrimaryContainer = TextWhite,
    secondary = CyanAccent,
    onSecondary = NavyMidnight,
    secondaryContainer = CyanContainer,
    onSecondaryContainer = TextWhite,
    tertiary = EmeraldSuccess,
    onTertiary = NavyMidnight,
    tertiaryContainer = EmeraldContainerDark,
    onTertiaryContainer = TextWhite,
    background = NavyMidnight,
    onBackground = TextWhite,
    surface = NavySurface,
    onSurface = TextWhite,
    surfaceVariant = NavySurfaceElevated,
    onSurfaceVariant = TextSlateLight,
    surfaceContainerLowest = NavyMidnight,
    surfaceContainerLow = NavySurface,
    surfaceContainer = NavySurfaceElevated,
    surfaceContainerHigh = NavySurfaceBright,
    outline = NavyOutline,
    outlineVariant = NavyOutlineSubtle,
    error = CoralError,
    onError = Color.White,
    errorContainer = CoralContainerDark,
    onErrorContainer = TextWhite
)

private val LifePilotLightColorScheme = lightColorScheme(
    primary = CobaltPrimary,
    onPrimary = Color.White,
    primaryContainer = CobaltContainer,
    onPrimaryContainer = InkDark,
    secondary = TealAccentDark,
    onSecondary = Color.White,
    secondaryContainer = TealContainerLight,
    onSecondaryContainer = InkDark,
    tertiary = EmeraldSuccess,
    onTertiary = Color.White,
    tertiaryContainer = EmeraldContainerLight,
    onTertiaryContainer = InkDark,
    background = LightCanvas,
    onBackground = InkDark,
    surface = LightSurface,
    onSurface = InkDark,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurfaceVariant,
    surfaceContainerHigh = CobaltContainer,
    outline = LightOutline,
    outlineVariant = LightSurfaceVariant,
    error = CoralError,
    onError = Color.White,
    errorContainer = CoralContainerLight,
    onErrorContainer = InkDark
)

val LifePilotShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun LifePilotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) LifePilotDarkColorScheme else LifePilotLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LifePilotTypography,
        shapes = LifePilotShapes,
        content = content
    )
}
