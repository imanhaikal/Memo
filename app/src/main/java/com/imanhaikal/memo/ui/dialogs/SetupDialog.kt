package com.imanhaikal.memo.ui.dialogs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.imanhaikal.memo.ui.theme.AppColors
import com.imanhaikal.memo.ui.theme.MemoTheme
import kotlinx.coroutines.launch

@Composable
fun SetupDialog(
    onConfirm: (amount: Double, days: Int) -> Unit,
    onDismiss: () -> Unit // Although usually setup isn't dismissible without action, we'll include it for standard dialog API
) {
    var amountText by remember { mutableStateOf("") }
    var daysText by remember { mutableStateOf("30") }

    val scale = remember { Animatable(0.9f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 300)
            )
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = AppColors.Surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .scale(scale.value)
                .alpha(alpha.value)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Welcome",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Let's set up your budget.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Amount Input
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Total Budget") },
                    placeholder = { Text("e.g. 1000") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.TextPrimary,
                        unfocusedBorderColor = AppColors.Border,
                        focusedLabelColor = AppColors.TextPrimary,
                        cursorColor = AppColors.TextPrimary,
                        focusedTextColor = AppColors.TextPrimary,
                        unfocusedTextColor = AppColors.TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Days Input
                OutlinedTextField(
                    value = daysText,
                    onValueChange = { daysText = it },
                    label = { Text("Number of Days") },
                    placeholder = { Text("e.g. 30") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.TextPrimary,
                        unfocusedBorderColor = AppColors.Border,
                        focusedLabelColor = AppColors.TextPrimary,
                        cursorColor = AppColors.TextPrimary,
                        focusedTextColor = AppColors.TextPrimary,
                        unfocusedTextColor = AppColors.TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        val amount = amountText.toDoubleOrNull()
                        val days = daysText.toIntOrNull()
                        if (amount != null && amount > 0 && days != null && days > 0) {
                            onConfirm(amount, days)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.TextPrimary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Start Budget",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun SetupDialogPreview() {
    MemoTheme {
        SetupDialog(onConfirm = { _, _ -> }, onDismiss = {})
    }
}