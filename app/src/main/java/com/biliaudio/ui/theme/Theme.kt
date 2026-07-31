package com.biliaudio.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFFB7299),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF3B001A),
    secondary = Color(0xFF00AEEC),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB7EAFF),
    onSecondaryContainer = Color(0xFF001F2A),
    tertiary = Color(0xFF7C4DFF),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE7DEFF),
    onTertiaryContainer = Color(0xFF25005D),
    // 统一页面背景为浅蓝灰，与 AppColors.ScreenBg 一致，避免页面间色差跳变
    background = Color(0xFFF1F3F8),
    onBackground = Color(0xFF1F1F1F),
    surface = Color(0xFFF1F3F8),
    onSurface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFFE6E8EE),
    onSurfaceVariant = Color(0xFF8A8F99),
    outline = Color(0xFFBFBFBF),
    // M3 v1.2 surface tonal 角色，供 MiniPlayer 等组件使用
    surfaceContainerHighest = Color(0xFFE6E8EE),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB0C5),
    onPrimary = Color(0xFF5A1130),
    primaryContainer = Color(0xFF7C2A45),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = Color(0xFF5ED5FF),
    onSecondary = Color(0xFF003547),
    secondaryContainer = Color(0xFF004D66),
    onSecondaryContainer = Color(0xFFB7EAFF),
    tertiary = Color(0xFFCCBFFF),
    onTertiary = Color(0xFF3E1D90),
    tertiaryContainer = Color(0xFF5C35B5),
    onTertiaryContainer = Color(0xFFE7DEFF),
    background = Color(0xFF201A1A),
    onBackground = Color(0xFFECE0E0),
    surface = Color(0xFF201A1A),
    onSurface = Color(0xFFECE0E0),
    surfaceVariant = Color(0xFF514347),
    onSurfaceVariant = Color(0xFFD5C2C6),
    outline = Color(0xFF9E8C90),
    surfaceContainerHighest = Color(0xFF3D3D3D),
)

@Composable
fun BiliAudioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // 状态栏透明：内容延伸到状态栏下方，由各页面自行处理状态栏内边距，
            // 实现「任何时候状态栏透明」的全屏沉浸效果。
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}

/**
 * 应用语义化颜色的 Composable 访问器。
 *
 * 统一各页面共用的文字、分割线、背景等颜色，避免硬编码散落各处。
 * 所有取值映射到 [MaterialTheme.colorScheme]，因此**自动适配暗色模式**——
 * 之前用固定 Color 常量导致 ProfileScreen 在暗色模式下仍是浅底深字。
 *
 * 主题点缀色取自 B 站品牌色：粉（primary）+ 蓝（secondary）。
 */
object AppColors {
    val ScreenBg: Color @Composable get() = MaterialTheme.colorScheme.background
    val TextPrimary: Color @Composable get() = MaterialTheme.colorScheme.onBackground
    val TextSecondary: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val TextMuted: Color @Composable get() = MaterialTheme.colorScheme.outline
    val IconMuted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val Divider: Color @Composable get() = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val CardBg: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHighest
    /** B 站粉（主题点缀色，设置项图标） */
    val AccentPink: Color @Composable get() = MaterialTheme.colorScheme.primary
    /** B 站蓝（主题点缀色） */
    val AccentBlue: Color @Composable get() = MaterialTheme.colorScheme.secondary
    val LogText: Color @Composable get() = MaterialTheme.colorScheme.onSurface
}
