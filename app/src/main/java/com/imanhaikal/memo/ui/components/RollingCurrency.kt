package com.imanhaikal.memo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.imanhaikal.memo.utils.rememberStrongHaptics
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import com.imanhaikal.memo.utils.CurrencyUtils

@Composable
fun RollingCurrency(
    value: Double,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    currencyCode: String = "USD"
) {
    // We use Float for animation performance, assuming budget values fit within Float precision for display
    val animatedValue = remember { Animatable(value.toFloat()) }
    val strongHaptics = rememberStrongHaptics()

    LaunchedEffect(value) {
        animatedValue.animateTo(
            targetValue = value.toFloat(),
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    LaunchedEffect(animatedValue) {
        snapshotFlow { animatedValue.value.toInt() }
            .drop(1)
            .collect {
                strongHaptics.performClick()
            }
    }

    Text(
        text = CurrencyUtils.formatCurrency(animatedValue.value.toDouble(), currencyCode),
        style = style,
        color = color,
        modifier = modifier
    )
}