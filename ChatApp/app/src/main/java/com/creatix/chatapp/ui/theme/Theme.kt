package com.creatix.chatapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E1FF),
    onPrimaryContainer = BrandPrimaryDark,
    secondary = BrandSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFF4EE),
    onSecondaryContainer = Color(0xFF0B4F49),
    tertiary = BrandTertiary,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = ErrorRed,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = BrandPrimaryLight,
    onPrimary = Color(0xFF1B1440),
    primaryContainer = Color(0xFF3A339C),
    onPrimaryContainer = Color(0xFFE3E1FF),
    secondary = BrandSecondary,
    onSecondary = Color(0xFF04302B),
    secondaryContainer = Color(0xFF0F4A43),
    onSecondaryContainer = Color(0xFFCFF4EE),
    tertiary = BrandTertiary,
    onTertiary = Color(0xFF3D2100),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = ErrorRed,
    onError = Color.White,
)

/** تدرج لوني موحّد يُستخدم في الـ AppBar الرئيسي وشاشات الدخول والزرار الرئيسي */
val ChatAppBrandGradient
    @Composable get() = Brush.linearGradient(listOf(GradientStart, GradientMid, GradientEnd))

/** تدرج فقاعة الرسالة المرسلة (بتاعتي) */
val ChatAppSentBubbleGradient
    @Composable get() = Brush.linearGradient(listOf(BubbleSentGradientStart, BubbleSentGradientEnd))

/** لون فقاعة الرسالة المستقبَلة حسب الوضع (فاتح/داكن) */
val ColorScheme.bubbleReceived: Color
    @Composable get() = if (isSystemInDarkTheme()) BubbleReceivedDark else BubbleReceivedLight

@Composable
fun ChatAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
