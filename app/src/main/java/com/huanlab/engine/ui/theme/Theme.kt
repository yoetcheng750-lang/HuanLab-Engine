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
    surface = Color(0xFF0A0F1A),
    onSurface = Color(0xFFE7ECF5),
    surfaceVariant = Color(0xFF192134),
    onSurfaceVariant = Color(0xFFA6B0C6),
    surfaceDim = Color(0xFF04060B),
    surfaceBright = Color(0xFF2A3352),
    surfaceContainerLowest = Color(0xFF030509),
    surfaceContainerLow = Color(0xFF070B14),
    surfaceContainer = Color(0xFF0C121E),
    surfaceContainerHigh = Color(0xFF111828),
    surfaceContainerHighest = Color(0xFF182034),
    outline = Color(0xFF2A3352),
    outlineVariant = Color(0xFF1B2236),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF2A0000),
    errorContainer = Color(0xFF5C1010),
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
