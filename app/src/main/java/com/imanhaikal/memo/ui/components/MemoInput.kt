package com.imanhaikal.memo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
    singleLine: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val backgroundColor = Color(0xFFF5F5F5)
    val shape = RoundedCornerShape(12.dp)
    
    // Determine border style based on focus
    val borderModifier = if (isFocused) {
        Modifier.border(2.dp, AppColors.Yellow, shape)
    } else {
        Modifier.border(1.dp, Color.Transparent, shape) // Transparent border to prevent layout shift
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .then(borderModifier)
            .background(backgroundColor, shape)
            .heightIn(min = 56.dp) // Standard touch target height
            .padding(horizontal = 16.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = AppColors.TextPrimary,
            textAlign = TextAlign.Center
        ),
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(AppColors.TextPrimary),
        decorationBox = { innerTextField ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (value.isEmpty()) {
                    if (label != null && !isFocused) {
                        // Show label centered when empty and not focused
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppColors.TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    } else if (placeholder.isNotEmpty()) {
                        // Show placeholder if label is hidden (focused) or null
                         Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppColors.TextTertiary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                
                // If focused and we have a label, we might want to move it or just hide it.
                // For this "centered" design, hiding the label on focus/typing and showing placeholder 
                // or just relying on context is common. 
                // However, the original code had labels. 
                // Let's adopt a simple strategy: Label acts as placeholder.
                
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