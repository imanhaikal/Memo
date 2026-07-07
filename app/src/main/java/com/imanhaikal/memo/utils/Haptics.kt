package com.imanhaikal.memo.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Tiered haptic vocabulary. Weight scales with the significance of the action:
 * [tick] for minor interactions, [click] for primary actions, and distinct
 * [success]/[error] signatures for outcomes.
 */
class StrongHaptics(context: Context) {

    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(android.os.VibratorManager::class.java)
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    /** Light tick: list-row taps, secondary buttons, selections, swipe thresholds. */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    fun tick() = predefined(VibrationEffect.EFFECT_TICK, fallbackMs = 15)

    /** Standard click: FABs and primary/confirm buttons. */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    fun click() = predefined(VibrationEffect.EFFECT_CLICK, fallbackMs = 30)

    /** Crisp rise-then-tick: an action completed (expense added, receipt parsed). */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    fun success() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            v.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                VibrationEffect.Composition.PRIMITIVE_TICK
            )
        ) {
            v.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.6f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 1f, 80)
                    .compose()
            )
        } else {
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 25, 80, 15), -1))
        }
    }

    /** Double buzz: something went wrong (scan failed) or is destructive (delete confirmed). */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    fun error() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 40), -1))
    }

    @RequiresPermission(android.Manifest.permission.VIBRATE)
    private fun predefined(effect: Int, fallbackMs: Long) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            v.vibrate(VibrationEffect.createPredefined(effect))
        } else {
            v.vibrate(VibrationEffect.createOneShot(fallbackMs, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}

@Composable
fun rememberStrongHaptics(): StrongHaptics {
    val context = LocalContext.current
    return remember(context) { StrongHaptics(context) }
}
