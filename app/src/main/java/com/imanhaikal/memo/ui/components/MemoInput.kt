package com.imanhaikal.memo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.imanhaikal.memo.ui.theme.AppColors
import com.imanhaikal.memo.ui.theme.MemoTheme

@Composable
fun MemoInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    /**
     * Lines a multi-line field grows to before it starts scrolling internally.
     * Ignored when [singleLine] is true.
     */
    maxLines: Int = Int.MAX_VALUE
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val backgroundColor = AppColors.Field
    val shape = RoundedCornerShape(12.dp)

    // Constant stroke width, animated colour: the ring fades in on focus instead of
    // snapping, and the geometry never changes between states
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) AppColors.Yellow else Color.Transparent,
        animationSpec = tween(durationMillis = 160),
        label = "inputBorderColor"
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor, shape)
            .border(2.dp, borderColor, shape)
            .heightIn(min = 56.dp) // Standard touch target height
            .padding(horizontal = 16.dp)
            .semantics {
                label?.let { contentDescription = it }
            },
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = AppColors.TextPrimary,
            textAlign = TextAlign.Start
        ),
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(AppColors.TextPrimary),
        decorationBox = { innerTextField ->
            // A single line centres in the 56dp touch target; a field that grows has to
            // start at the top instead, or its first line floats in the middle of the box.
            Box(
                contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (singleLine) Modifier else Modifier.padding(vertical = 16.dp))
            ) {
                // The label doubles as the resting placeholder; on focus it gives way
                // to the more specific hint.
                if (value.isEmpty()) {
                    if (label != null && !isFocused) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppColors.TextSecondary,
                            textAlign = TextAlign.Start
                        )
                    } else if (placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppColors.TextTertiary,
                            textAlign = TextAlign.Start
                        )
                    }
                }

                innerTextField()
            }
        }
    )
}

@Preview
@Composable
fun MemoInputPreview() {
    MemoTheme {
        MemoInput(
            value = "",
            onValueChange = {},
            label = "Enter Amount"
        )
    }
}
