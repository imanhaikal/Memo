package com.imanhaikal.memo.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.imanhaikal.memo.utils.CurrencyUtils
import com.imanhaikal.memo.utils.rememberStrongHaptics

// Spacing between roll ticks: dense enough to feel continuous, sparse enough
// that LOW_TICK effects don't blur into a single buzz
private const val ROLL_TICK_SPACING_MS = 60L

@Composable
fun RollingCurrency(
    value: Long,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    currencyCode: String = "MYR",
    // Disable when several counters animate together — the single vibrator
    // can only render one stream of ticks
    hapticsEnabled: Boolean = true
) {
    val haptics = rememberStrongHaptics()
    // We use Float for animation performance, assuming budget values fit within Float precision for display
    val animatedValue = remember { Animatable(value.toFloat()) }

    LaunchedEffect(value) {
        var lastShown = animatedValue.value.toLong()
        var lastTickAt = 0L
        animatedValue.animateTo(
            targetValue = value.toFloat(),
            // No bounce: an overshooting spring would transiently display a wrong amount
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) {
            if (!hapticsEnabled) return@animateTo
            val shown = this.value.toLong()
            if (shown != lastShown) {
                lastShown = shown
                val now = SystemClock.uptimeMillis()
                if (now - lastTickAt >= ROLL_TICK_SPACING_MS) {
                    lastTickAt = now
                    haptics.roll()
                }
            }
        }
    }

    Text(
        text = CurrencyUtils.formatCurrency(animatedValue.value.toLong(), currencyCode),
        style = style,
        color = color,
        modifier = modifier
    )
}
