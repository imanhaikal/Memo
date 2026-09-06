package com.imanhaikal.memo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Complete brand palette for one theme. */
@Immutable
data class MemoPalette(
    val background: Color,
    val surface: Color,
    /** Fill for text fields and unselected chips. */
    val field: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val yellow: Color,
    /** Content on top of a yellow container — dark in both themes. */
    val onYellow: Color,
    val green: Color,
    val greenSubtle: Color,
    val red: Color,
    val redSubtle: Color,
    val border: Color,
    /** High-contrast pill (snackbar, primary FAB). */
    val inverseSurface: Color,
    val onInverse: Color,
    val disabled: Color,
    val onDisabled: Color
)

val LightPalette = MemoPalette(
    background = Color(0xFFF9F9F9),
    surface = Color(0xFFFFFFFF),
    field = Color(0xFFF5F5F5),
    textPrimary = Color(0xFF111111),
    textSecondary = Color(0xFF666666),
    textTertiary = Color(0xFF707070),
    yellow = Color(0xFFF2E057),
    onYellow = Color(0xFF111111),
    green = Color(0xFF007A38),
    greenSubtle = Color(0xFFE3FCEF),
    red = Color(0xFFC91F1F),
    redSubtle = Color(0xFFFFEBEB),
    border = Color(0xFFF0F0F0),
    inverseSurface = Color(0xFF111111),
    onInverse = Color(0xFFFFFFFF),
    disabled = Color(0xFFDDDDDD),
    onDisabled = Color(0xFF999999)
)

val DarkPalette = MemoPalette(
    background = Color(0xFF111111),
    surface = Color(0xFF1A1A1A),
    field = Color(0xFF262626),
    textPrimary = Color(0xFFEEEEEE),
    textSecondary = Color(0xFFAAAAAA),
    textTertiary = Color(0xFF8F8F8F),
    yellow = Color(0xFFF2E057),
    onYellow = Color(0xFF111111),
    green = Color(0xFF53D28A),
    greenSubtle = Color(0xFF12301F),
    red = Color(0xFFE86A6A),
    redSubtle = Color(0xFF381A1A),
    border = Color(0xFF2A2A2A),
    inverseSurface = Color(0xFF2E2E2E),
    onInverse = Color(0xFFFFFFFF),
    disabled = Color(0xFF333333),
    onDisabled = Color(0xFF777777)
)

/**
 * Theme-aware access to the brand palette. [MemoTheme] swaps [palette] to match
 * the system theme; the backing snapshot state makes every composable that reads
 * a color recompose when the theme changes.
 */
object AppColors {
    var palette: MemoPalette by mutableStateOf(LightPalette)
        internal set

    val Background: Color get() = palette.background
    val Surface: Color get() = palette.surface
    val Field: Color get() = palette.field
    val TextPrimary: Color get() = palette.textPrimary
    val TextSecondary: Color get() = palette.textSecondary
    val TextTertiary: Color get() = palette.textTertiary
    val Yellow: Color get() = palette.yellow
    val OnYellow: Color get() = palette.onYellow
    val Green: Color get() = palette.green
    val GreenSubtle: Color get() = palette.greenSubtle
    val Red: Color get() = palette.red
    val RedSubtle: Color get() = palette.redSubtle
    val Border: Color get() = palette.border
    val InverseSurface: Color get() = palette.inverseSurface
    val OnInverse: Color get() = palette.onInverse
    val Disabled: Color get() = palette.disabled
    val OnDisabled: Color get() = palette.onDisabled
}
