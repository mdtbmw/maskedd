package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ApplePlayfulLightColorScheme = lightColorScheme(
    primary = PlayfulYellow,
    onPrimary = CharcoalBlack,
    primaryContainer = PlayfulYellowLight,
    onPrimaryContainer = CharcoalBlack,
    secondary = CharcoalBlack,
    onSecondary = PureWhite,
    secondaryContainer = OffWhiteSurface,
    onSecondaryContainer = TextMain,
    tertiary = PlayfulYellowDark,
    onTertiary = PureWhite,
    background = PureWhite,
    onBackground = TextMain,
    surface = PureWhite,
    onSurface = TextMain,
    surfaceVariant = OffWhiteSurface,
    onSurfaceVariant = TextMuted,
    outline = CardBorderSoft
)

@Composable
fun LyricReadTheme(
    darkTheme: Boolean = false, // Enforce clean, white-dominated Apple aesthetic
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ApplePlayfulLightColorScheme,
        typography = Typography,
        content = content
    )
}
