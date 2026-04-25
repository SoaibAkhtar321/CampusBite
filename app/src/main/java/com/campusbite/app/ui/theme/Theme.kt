package com.campusbite.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.campusbite.app.ui.theme.Orange
import com.campusbite.app.ui.theme.TextPrimary
import com.campusbite.app.ui.theme.TextSecondary

private val LightColorScheme = lightColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    primaryContainer = OrangeLight,
    onPrimaryContainer = OrangeDark,
    background = Background,
    onBackground = TextPrimary,
    surface = SurfaceGray,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    error = Error
)

private val DarkColorScheme = darkColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    primaryContainer = OrangeDark,
    onPrimaryContainer = OrangeLight,
    background = Color(0xFF121212),
    onBackground = Color.White,
    surface = Color(0xFF1E1E1E),
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFAAAAAA),
    error = Error
)
@Composable
fun CampusBiteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}