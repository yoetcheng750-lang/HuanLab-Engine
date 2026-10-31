package com.huanlab.engine.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 极客深色配色：深空底 + 紫青霓虹，Material You 的层次感。 */
private val HuanLabDarkScheme = darkColorScheme(
    primary = Color(0xFF8B5CF6),
    onPrimary = Color(0xFF13002B),
    primaryContainer = Color(0xFF3A1F73),
    onPrimaryContainer = Color(0xFFEDE3FF),
    secondary = Color(0xFF22D3EE),
    onSecondary = Color(0xFF00202A),
    secondaryContainer = Color(0xFF0E3B47),
    onSecondaryContainer = Color(0xFFC6F5FF),
    tertiary = Color(0xFFF472B6),
    background = Color(0xFF070A12),
    onBackground = Color(0xFFE7ECF5),
    surface = Color(0xFF0E1421),
    onSurface = Color(0xFFE7ECF5),
    surfaceVariant = Color(0xFF192134),
    onSurfaceVariant = Color(0xFFA6B0C6),
    outline = Color(0xFF2A3352),
    outlineVariant = Color(0xFF1B2236),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF2A0000),
)

private val HuanLabTypography = Typography(
    displaySmall = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

@Composable
fun HuanLabTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HuanLabDarkScheme,
        typography = HuanLabTypography,
        content = content,
    )
}
