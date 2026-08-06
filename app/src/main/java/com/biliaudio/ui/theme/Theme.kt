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

/**
 * 浅色配色方案。
 *
 * 参考 BBPlayer：以中性灰为底、B 站品牌色（粉 #FB7299 + 蓝 #00AEEC）为点缀。
 * 关键优化：
 * - 表面层级（surface → surfaceContainerHighest）按 M3 tonal palette 递进，
 *   保证卡片/MiniPlayer/对话框与背景有清晰区分度，避免「糊成一片」
 * - onSurfaceVariant 提深到 0xFF49454F，副文字在浅灰背景上对比度达 WCAG AA
 * - outline 提深到 0xFF9A9A9A，分割线清晰可辨
 */
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
    // 中性浅灰背景，与品牌粉/蓝形成清爽对比
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFAFAFA),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0E8),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF9A9A9A),
    outlineVariant = Color(0xFFCAC4CF),
    // M3 v1.2 surface tonal 角色：tonal 递进，区分背景/卡片/弹层
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F3F6),
    surfaceContainer = Color(0xFFEFEDF0),
    surfaceContainerHigh = Color(0xFFE9E7EA),
    surfaceContainerHighest = Color(0xFFE3E1E4),
    inverseSurface = Color(0xFF313033),
    inverseOnSurface = Color(0xFFF4EFF4),
)

/**
 * 暗色配色方案。
 *
 * 参考 BBPlayer：中性深色底（不带红/粉色调，避免与品牌粉冲突显得脏），
 * 文字用纯白偏暖灰，品牌色适度提亮保证暗色下可读性。
 * 关键优化：
 * - background 从偏红的 0xFF201A1A 改为中性 0xFF151515，更干净
 * - onBackground 从偏粉的 0xFFECE0E0 改为纯白偏暖 0xFFE6E1E5
 * - surface 层级递进，卡片比背景亮一档，层次清晰
 * - primaryContainer 提亮到 0xFF9A3A5A，与背景对比度更高
 */
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB0C5),
    onPrimary = Color(0xFF5A1130),
    primaryContainer = Color(0xFF9A3A5A),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = Color(0xFF5ED5FF),
    onSecondary = Color(0xFF003547),
    secondaryContainer = Color(0xFF004D66),
    onSecondaryContainer = Color(0xFFB7EAFF),
    tertiary = Color(0xFFCCBFFF),
    onTertiary = Color(0xFF3E1D90),
    tertiaryContainer = Color(0xFF5C35B5),
    onTertiaryContainer = Color(0xFFE7DEFF),
    // 中性深色，不带红/粉色调，与品牌粉点缀色更协调
    background = Color(0xFF151515),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF151515),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4CF),
    outline = Color(0xFF948F99),
    outlineVariant = Color(0xFF49454F),
    // M3 v1.2 surface tonal 角色：暗色下逐级提亮，卡片/弹层层次清晰
    surfaceContainerLowest = Color(0xFF101010),
    surfaceContainerLow = Color(0xFF1D1B1F),
    surfaceContainer = Color(0xFF211F23),
    surfaceContainerHigh = Color(0xFF2B292D),
    surfaceContainerHighest = Color(0xFF363338),
    inverseSurface = Color(0xFFE6E1E5),
    inverseOnSurface = Color(0xFF313033),
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
