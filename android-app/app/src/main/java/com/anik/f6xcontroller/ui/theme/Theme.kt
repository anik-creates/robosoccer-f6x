package com.anik.f6xcontroller.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MonochromeDarkColorScheme = darkColorScheme(
    primary = PureWhite,
    secondary = TextSecondary,
    tertiary = PureWhite,
    background = PureBlack,
    surface = PureBlack,
    onPrimary = PureBlack,
    onSecondary = PureBlack,
    onTertiary = PureBlack,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = PureWhite,
    onError = PureBlack
)

@Composable
fun RoboSoccerF6XTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MonochromeDarkColorScheme,
        typography = Typography,
        content = content
    )
}
