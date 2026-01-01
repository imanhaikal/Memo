package com.imanhaikal.memo.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

class StrongHaptics(private val context: Context) {

    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(android.os.VibratorManager::class.java)
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(android.os.Vibrator::class.java)
    }

    @RequiresPermission(android.Manifest.permission.VIBRATE)
    fun performClick() {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Predefined strong click effect for Q+
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Custom vibration for Oreo+ (100ms at high amplitude)
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                // Fallback for older devices
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        }
    }
}

@Composable
fun rememberStrongHaptics(): StrongHaptics {
    val context = LocalContext.current
    return remember(context) { StrongHaptics(context) }
}