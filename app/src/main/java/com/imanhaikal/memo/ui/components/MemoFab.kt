package com.imanhaikal.memo.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.imanhaikal.memo.ui.theme.AppColors
import com.imanhaikal.memo.utils.rememberStrongHaptics

@Composable
fun MemoFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberStrongHaptics()
    val interactionSource = remember { MutableInteractionSource() }

    ExtendedFloatingActionButton(
        onClick = {
            haptic.click()
            onClick()
        },
        modifier = modifier.springPress(interactionSource, pressedScale = 0.92f),
        shape = RoundedCornerShape(50),
        containerColor = AppColors.InverseSurface,
        contentColor = AppColors.OnInverse,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 8.dp,
            pressedElevation = 12.dp
        ),
        interactionSource = interactionSource,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null
            )
        },
        text = {
            Text(text = "Add Expense")
        }
    )
}
