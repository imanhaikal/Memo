package com.imanhaikal.memo.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.imanhaikal.memo.ui.theme.AppColors
import com.imanhaikal.memo.utils.rememberStrongHaptics

/**
 * An [IconButton] that behaves like the rest of the app: springs under the finger and
 * ticks on tap. The bare Material default is fine in isolation, but next to a screen
 * of scaling, ticking controls a flat icon reads as an unfinished one — and these are
 * the app's two most-tapped controls (Settings, Back).
 */
@Composable
fun MemoIconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = AppColors.TextPrimary
) {
    val haptic = rememberStrongHaptics()
    val interaction = remember { MutableInteractionSource() }

    IconButton(
        onClick = {
            haptic.tick()
            onClick()
        },
        interactionSource = interaction,
        modifier = modifier.springPress(interaction)
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint
        )
    }
}
