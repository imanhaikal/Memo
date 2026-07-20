package com.imanhaikal.memo.ui.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.imanhaikal.memo.R
import com.imanhaikal.memo.data.receipt.ScanFailureReason
import com.imanhaikal.memo.ui.components.springPress
import com.imanhaikal.memo.ui.theme.AppColors
import com.imanhaikal.memo.utils.rememberStrongHaptics
import kotlinx.coroutines.delay

@Composable
fun ScanReceiptChooserDialog(
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = rememberStrongHaptics()
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = AppColors.Surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Scan Receipt",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Snap a photo of a receipt and the amount and note will be filled in for you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                val cameraInteraction = remember { MutableInteractionSource() }
                Button(
                    onClick = {
                        haptic.click()
                        onCamera()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Yellow,
                        contentColor = AppColors.OnYellow
                    ),
                    interactionSource = cameraInteraction,
                    modifier = Modifier
                        .springPress(cameraInteraction)
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "Take Photo",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val galleryInteraction = remember { MutableInteractionSource() }
                OutlinedButton(
                    onClick = {
                        haptic.tick()
                        onGallery()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AppColors.TextPrimary
                    ),
                    interactionSource = galleryInteraction,
                    modifier = Modifier
                        .springPress(galleryInteraction)
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "Choose from Gallery",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    )
                }
            }
        }
    }
}

private val ScanningMessages = listOf(
    "Reading receipt…",
    "Finding the total…",
    "Almost there…"
)

@Composable
fun ScanningReceiptDialog(onCancel: () -> Unit) {
    // Back press cancels the scan; a stray tap outside does not
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = AppColors.Surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Pulsing camera glyph in a soft yellow disc instead of a stock spinner
                val pulse = rememberInfiniteTransition(label = "scanPulse")
                val glowAlpha by pulse.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 900, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scanGlowAlpha"
                )

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .graphicsLayer { alpha = glowAlpha }
                        .background(AppColors.Yellow, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_camera),
                        contentDescription = null,
                        tint = AppColors.OnYellow,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Cycle status copy so a multi-second wait still feels alive
                var messageIndex by remember { mutableIntStateOf(0) }
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(1_800)
                        messageIndex = (messageIndex + 1) % ScanningMessages.size
                    }
                }

                AnimatedContent(
                    targetState = messageIndex,
                    transitionSpec = {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                    },
                    label = "scanMessage"
                ) { index ->
                    Text(
                        text = ScanningMessages[index],
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(onClick = onCancel) {
                    Text(
                        text = "Cancel",
                        color = AppColors.TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun ScanErrorDialog(
    reason: ScanFailureReason,
    onRetry: () -> Unit,
    onManual: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = rememberStrongHaptics()
    val message = when (reason) {
        ScanFailureReason.NETWORK -> "Couldn't reach the AI service. Check your connection and try again."
        ScanFailureReason.UNREADABLE -> "Couldn't read a total from that photo. Try a clearer shot of the receipt."
        ScanFailureReason.API_ERROR, ScanFailureReason.PARSE -> "Something went wrong reading the receipt. Please try again."
    }

    // The failure itself gets a distinct physical signature
    LaunchedEffect(Unit) {
        haptic.error()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = AppColors.Surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Scan Failed",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                val retryInteraction = remember { MutableInteractionSource() }
                Button(
                    onClick = {
                        haptic.click()
                        onRetry()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Yellow,
                        contentColor = AppColors.OnYellow
                    ),
                    interactionSource = retryInteraction,
                    modifier = Modifier
                        .springPress(retryInteraction)
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "Try Again",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val manualInteraction = remember { MutableInteractionSource() }
                OutlinedButton(
                    onClick = {
                        haptic.tick()
                        onManual()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AppColors.TextPrimary
                    ),
                    interactionSource = manualInteraction,
                    modifier = Modifier
                        .springPress(manualInteraction)
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "Enter Manually",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Cancel",
                        color = AppColors.TextSecondary
                    )
                }
            }
        }
    }
}
