package com.imanhaikal.memo.ui.components

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

@Composable
fun RollingCurrency(
    value: Long,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    currencyCode: String = "MYR"
) {
    // We use Float for animation performance, assuming budget values fit within Float precision for display
    val animatedValue = remember { Animatable(value.toFloat()) }

    LaunchedEffect(value) {
        animatedValue.animateTo(
            targetValue = value.toFloat(),
            // No bounce: an overshooting spring would transiently display a wrong amount
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    Text(
        text = CurrencyUtils.formatCurrency(animatedValue.value.toLong(), currencyCode),
        style = style,
        color = color,
        modifier = modifier
    )
}
