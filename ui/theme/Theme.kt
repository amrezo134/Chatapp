package com.creatix.chatapp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = PurplePrimary,
    onPrimary = Color.White,
    secondary = MintAccent,
    background = BackgroundLight,
    surface = SurfaceLight,
    surfaceVariant = BubbleOtherLight,
    onBackground = Color(0xFF1C1C1E),
    onSurface = Color(0xFF1C1C1E)
)

private val DarkColors = darkColorScheme(
    primary = PurpleLightTint,
    onPrimary = Color(0xFF1C1C1E),
    secondary = MintAccent,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = BubbleOtherDark,
    onBackground = Color(0xFFECECEC),
    onSurface = Color(0xFFECECEC)
)

@Composable
fun ChatAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
