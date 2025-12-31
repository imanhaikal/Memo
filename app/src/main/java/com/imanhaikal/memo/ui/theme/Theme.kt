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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AppColors.Yellow,
    secondary = AppColors.Yellow,
    tertiary = AppColors.Green,
    background = AppColorsDark.Background,
    surface = AppColorsDark.Surface,
    onPrimary = AppColorsDark.TextPrimary,
    onSecondary = AppColorsDark.TextPrimary,
    onTertiary = AppColorsDark.TextPrimary,
    onBackground = AppColorsDark.TextPrimary,
    onSurface = AppColorsDark.TextPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = AppColors.Yellow,
    secondary = AppColors.Yellow,
    tertiary = AppColors.Green,
    background = AppColors.Background,
    surface = AppColors.Surface,
    onPrimary = AppColors.TextPrimary,
    onSecondary = AppColors.TextPrimary,
    onTertiary = AppColors.TextPrimary,
    onBackground = AppColors.TextPrimary,
    onSurface = AppColors.TextPrimary

    /* Other default colors to override
    error = Color(0xFFFFB4AB),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF938F99)
    */
)

@Composable
fun MemoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Disabled by default for brand consistency
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
            // Status bar transparency handled by Edge-to-Edge in MainActivity
            // Here we just ensure the contrast
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}