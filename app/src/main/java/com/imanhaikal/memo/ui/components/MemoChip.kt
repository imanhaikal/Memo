package com.imanhaikal.memo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.imanhaikal.memo.ui.theme.AppColors

/** How long the selected/unselected colours take to cross-fade. */
private const val CHIP_FADE_MS = 180

/**
 * A selectable pill. Category, theme and search-filter chips were three separate copies of
 * this; the paddings stayed different between them, so they are parameters rather than
 * fixed values.
 *
 * Selection cross-fades rather than hard-swapping the fill, and the chip springs under the
 * finger — the treatment the rest of the app's controls already use.
 */
@Composable
fun MemoChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 12.dp,
    verticalPadding: Dp = 8.dp,
    /** Null keeps `labelSmall`'s own weight; callers that vary it by selection compute it. */
    fontWeight: FontWeight? = null,
    leadingIcon: (@Composable (tint: Color) -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val background by animateColorAsState(
        targetValue = if (selected) AppColors.Yellow else AppColors.Field,
        animationSpec = tween(durationMillis = CHIP_FADE_MS),
        label = "memoChipBackground"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) AppColors.OnYellow else AppColors.TextSecondary,
        animationSpec = tween(durationMillis = CHIP_FADE_MS),
        label = "memoChipContent"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .springPress(interaction, pressedScale = PressScale.Surface)
            // Clip before the click modifier so the touch target matches the pill, not its bounds
            .clip(RoundedCornerShape(50))
            .background(background)
            // selectable, not clickable: every chip group here is mutually exclusive, and
            // this is what tells TalkBack the pill is selected rather than just tappable.
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
    ) {
        if (leadingIcon != null) {
            leadingIcon(contentColor)
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.let {
                if (fontWeight != null) it.copy(fontWeight = fontWeight) else it
            },
            color = contentColor
        )
    }
}
