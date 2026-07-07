package com.imanhaikal.memo.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

object PressScale {
    /** For buttons, FABs and other small controls. */
    const val Button = 0.94f

    /** For large surfaces like list rows and cards, where a deep squash reads as cartoonish. */
    const val Surface = 0.97f
}

/**
 * Scales the element down while pressed and springs it back on release, driven by the
 * same [InteractionSource] the click handler uses — so drag-off and scroll cancellation
 * relax the scale without firing the action.
 */
fun Modifier.springPress(
    interactionSource: InteractionSource,
    pressedScale: Float = PressScale.Button
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "springPressScale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
