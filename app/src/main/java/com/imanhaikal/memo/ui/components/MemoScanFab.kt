package com.imanhaikal.memo.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.imanhaikal.memo.R
import com.imanhaikal.memo.ui.theme.AppColors
import com.imanhaikal.memo.utils.rememberStrongHaptics

@Composable
fun MemoScanFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberStrongHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scanFabScale"
    )

    SmallFloatingActionButton(
        onClick = {
            haptic.performClick()
            onClick()
        },
        modifier = modifier.scale(scale),
        shape = RoundedCornerShape(50),
        containerColor = AppColors.Yellow,
        contentColor = AppColors.TextPrimary,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 6.dp,
            pressedElevation = 10.dp
        ),
        interactionSource = interactionSource
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_camera),
            contentDescription = "Scan receipt"
        )
    }
}
