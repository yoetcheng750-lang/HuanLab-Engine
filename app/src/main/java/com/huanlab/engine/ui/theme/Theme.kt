package com.huanlab.engine.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 品牌深空配色：Android 12 以下或关闭动态取色时使用。 */
private val BrandDarkScheme = darkColorScheme(
    primary = Color(0xFF7C8CFF),
    onPrimary = Color(0xFF0B1030),
    primaryContainer = Color(0xFF2A3560),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFF4FD1A5),
    onSecondary = Color(0xFF00281C),
    secondaryContainer = Color(0xFF16362E),
    onSecondaryContainer = Color(0xFFC9F5E6),
    tertiary = Color(0xFFA78BFA),
    onTertiary = Color(0xFF1B0F3A),
    background = Color(0xFF080B12),
    onBackground = Color(0xFFE6E9F2),
    surface = Color(0xFF0B0F18),
    onSurface = Color(0xFFE6E9F2),
    surfaceVariant = Color(0xFF1B2232),
    onSurfaceVariant = Color(0xFF8E97AD),
    surfaceDim = Color(0xFF080B12),
    surfaceBright = Color(0xFF2A3348),
    surfaceContainerLowest = Color(0xFF05070C),
    surfaceContainerLow = Color(0xFF0D111B),
    surfaceContainer = Color(0xFF141926),
    surfaceContainerHigh = Color(0xFF1A2030),
    surfaceContainerHighest = Color(0xFF232A3D),
    outline = Color(0xFF3A4360),
    outlineVariant = Color(0xFF232A3D),
    error = Color(0xFFFF8A8A),
    onError = Color(0xFF2A0000),
    errorContainer = Color(0xFF4A1414),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** 品牌排版统一在此配置，Composable 内只允许引用样式名。 */
private val YoeTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

fun supportsDynamicColor(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun HuanLabTheme(
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val useDynamic = dynamicColor && supportsDynamicColor()
    val colorScheme = when {
        useDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicDarkColorScheme(context)
        else -> BrandDarkScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = YoeTypography,
        content = content,
    )
}
