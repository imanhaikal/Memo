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
import java.text.NumberFormat
import java.util.Locale

@Composable
fun RollingCurrency(
    value: Double,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    // We use Float for animation performance, assuming budget values fit within Float precision for display
    val animatedValue = remember { Animatable(value.toFloat()) }

    LaunchedEffect(value) {
        animatedValue.animateTo(
            targetValue = value.toFloat(),
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    Text(
        text = NumberFormat.getCurrencyInstance(Locale.US).format(animatedValue.value),
        style = style,
        color = color,
        modifier = modifier
    )
}