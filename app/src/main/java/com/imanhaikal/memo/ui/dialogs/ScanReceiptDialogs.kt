package com.imanhaikal.memo.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.imanhaikal.memo.data.receipt.ScanFailureReason
import com.imanhaikal.memo.ui.theme.AppColors
import com.imanhaikal.memo.utils.rememberStrongHaptics

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

                Button(
                    onClick = {
                        haptic.performClick()
                        onCamera()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Yellow,
                        contentColor = AppColors.TextPrimary
                    ),
                    modifier = Modifier
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

                OutlinedButton(
                    onClick = {
                        haptic.performClick()
                        onGallery()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AppColors.TextPrimary
                    ),
                    modifier = Modifier
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

@Composable
fun ScanningReceiptDialog() {
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            dismissOnBackPress = false,
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
                CircularProgressIndicator(color = AppColors.Yellow)

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Reading receipt…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextPrimary
                )
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

                Button(
                    onClick = {
                        haptic.performClick()
                        onRetry()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Yellow,
                        contentColor = AppColors.TextPrimary
                    ),
                    modifier = Modifier
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

                OutlinedButton(
                    onClick = {
                        haptic.performClick()
                        onManual()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AppColors.TextPrimary
                    ),
                    modifier = Modifier
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
