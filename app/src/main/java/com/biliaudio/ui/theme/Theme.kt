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
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF201A1A),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF201A1A),
    surfaceVariant = Color(0xFFF2DDE2),
    onSurfaceVariant = Color(0xFF514347),
    outline = Color(0xFF837377),
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
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
