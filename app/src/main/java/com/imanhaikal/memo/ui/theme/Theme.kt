package com.imanhaikal.memo.ui.theme

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = DarkPalette.yellow,
    onPrimary = DarkPalette.onYellow,
    secondary = DarkPalette.yellow,
    onSecondary = DarkPalette.onYellow,
    tertiary = DarkPalette.green,
    background = DarkPalette.background,
    surface = DarkPalette.surface,
    onBackground = DarkPalette.textPrimary,
    onSurface = DarkPalette.textPrimary,
    onSurfaceVariant = DarkPalette.textSecondary,
    outline = DarkPalette.border,
    error = DarkPalette.red
)

private val LightColorScheme = lightColorScheme(
    primary = LightPalette.yellow,
    onPrimary = LightPalette.onYellow,
    secondary = LightPalette.yellow,
    onSecondary = LightPalette.onYellow,
    tertiary = LightPalette.green,
    background = LightPalette.background,
    surface = LightPalette.surface,
    onBackground = LightPalette.textPrimary,
    onSurface = LightPalette.textPrimary,
    onSurfaceVariant = LightPalette.textSecondary,
    outline = LightPalette.border,
    error = LightPalette.red
)

@Composable
fun MemoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Disabled by default for brand consistency
    content: @Composable () -> Unit
) {
    // Swap the brand palette before content composes so every AppColors read
    // in this frame already resolves against the active theme.
    val palette = if (darkTheme) DarkPalette else LightPalette
    if (AppColors.palette != palette) {
        AppColors.palette = palette
    }

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
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
