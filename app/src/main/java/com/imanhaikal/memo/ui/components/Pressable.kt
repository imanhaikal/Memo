package com.imanhaikal.memo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

object PressScale {
    /** For buttons, FABs and other small controls. */
    const val Button = 0.94f

    /** For large surfaces like list rows and cards, where a deep squash reads as cartoonish. */
    const val Surface = 0.97f
}

// Press-in and release are different physical events and want different springs.
// Under the finger the element must simply *be* compressed — an underdamped spring
// wobbles while the user is still touching it, which reads as loose rather than
// springy — and it has to get there fast enough to feel like acknowledgement
// (StiffnessHigh settles in ~50ms). The bounce belongs on release, once the finger
// is gone and the element is springing back on its own.
private val PressInSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessHigh
)

private val ReleaseSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMedium
)

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
    val scale = remember { Animatable(1f) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            scale.animateTo(pressedScale, PressInSpec)
        } else {
            scale.animateTo(1f, ReleaseSpec)
        }
    }

    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}
