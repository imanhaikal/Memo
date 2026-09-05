package com.imanhaikal.memo.utils

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the user has haptics switched on in Memo's own settings. Independent of the
 * device-wide setting, which [StrongHaptics] checks separately — either one being off
 * silences the app.
 */
val LocalHapticsEnabled = compositionLocalOf { true }

/**
 * Tiered haptic vocabulary. Weight scales with the significance of the action:
 * [roll] for repeated micro-feedback, [tick] for minor interactions, [click]
 * for primary actions, [thud] for a completed destructive one, and distinct
 * [success]/[error] signatures for outcomes.
 */
class StrongHaptics(
    private val context: Context,
    private val userEnabled: () -> Boolean = { true }
) {

    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(android.os.VibratorManager::class.java)
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    // Capability queries go through binder IPC; cache them so [roll], which fires
    // every ~60ms during counter animations, doesn't repeat them per tick.
    private val hasVibrator: Boolean by lazy { vibrator?.hasVibrator() == true }

    // LOW_TICK and THUD landed in API 31, a release after the composition API itself —
    // probing for them below that asks about a primitive the platform does not know.
    private val lowTickSupported: Boolean by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            vibrator?.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK) == true
    }

    private val successPrimitivesSupported: Boolean by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            vibrator?.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                VibrationEffect.Composition.PRIMITIVE_TICK
            ) == true
    }

    private val thudSupported: Boolean by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            vibrator?.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD) == true
    }

    // Tagging effects as touch feedback lets the OS route them correctly and honour
    // the user's touch-intensity slider, rather than treating them as generic buzzes.
    private val touchAttributes: VibrationAttributes? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
        } else {
            null
        }

    // Settings.System is a ContentResolver query — also IPC, and on the same hot path
    // as [roll]. Cache it briefly rather than per-call: the user toggling device
    // haptics mid-gesture is not worth an IPC every 60ms.
    private var systemEnabledCache = true
    private var systemEnabledCheckedAt = 0L

    private val systemHapticsEnabled: Boolean
        get() {
            val now = SystemClock.uptimeMillis()
            if (now - systemEnabledCheckedAt > SYSTEM_SETTING_TTL_MS) {
                systemEnabledCache = runCatching {
                    // Deprecated for *writing* — apps must not change it — but still the
                    // only public way to read it, and still live. USAGE_TOUCH covers this
                    // on API 33+; minSdk here is 26, so the explicit check stays for all
                    // versions rather than trusting the split to be exact.
                    @Suppress("DEPRECATION")
                    Settings.System.getInt(
                        context.contentResolver,
                        Settings.System.HAPTIC_FEEDBACK_ENABLED,
                        1
                    ) == 1
                }.getOrDefault(true)
                systemEnabledCheckedAt = now
            }
            return systemEnabledCache
        }

    private fun enabled(): Boolean = hasVibrator && userEnabled() && systemHapticsEnabled

    @RequiresPermission(android.Manifest.permission.VIBRATE)
    private fun emit(effect: VibrationEffect) {
        if (!enabled()) return
        val v = vibrator ?: return
        // The attributes overload is API 33. The version check has to be explicit rather
        // than implied by touchAttributes being non-null, or the call is unguarded.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && touchAttributes != null) {
            v.vibrate(effect, touchAttributes)
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(effect)
        }
    }

    /** Light tick: list-row taps, secondary buttons, selections, swipe thresholds. */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    fun tick() = predefined(VibrationEffect.EFFECT_TICK, fallbackMs = 15)

    /** Faint tick for rolling number counters; soft enough to repeat every few frames. */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    fun roll() {
        if (!enabled()) return
        // The version check sits at the call site, not only inside the capability flag:
        // that flag is a lazy property, so the test within it is invisible from here.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && lowTickSupported) {
            emitLowTick()
        } else {
            predefined(VibrationEffect.EFFECT_TICK, fallbackMs = 10)
        }
    }

    /** Standard click: FABs and primary/confirm buttons. */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    fun click() = predefined(VibrationEffect.EFFECT_CLICK, fallbackMs = 30)

    /**
     * A single weighted drop: a destructive action the user asked for and got
     * (swipe-to-delete). Deliberately not [error] — nothing went wrong, and reusing
     * the failure signature for a successful action drains it of meaning.
     */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    fun thud() {
        if (!enabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && thudSupported) {
            emitThud()
        } else {
            predefined(VibrationEffect.EFFECT_HEAVY_CLICK, fallbackMs = 35)
        }
    }

    /** Crisp rise-then-tick: an action completed (expense added, receipt parsed). */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    fun success() {
        if (!enabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && successPrimitivesSupported) {
            emitSuccessRise()
        } else {
            emit(VibrationEffect.createWaveform(longArrayOf(0, 25, 80, 15), -1))
        }
    }

    /** Double buzz: something went wrong (scan failed) or is destructive (reset all). */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    fun error() {
        if (!enabled()) return
        emit(VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 40), -1))
    }

    // Primitive compositions live in their own annotated helpers so the SDK level they
    // need is declared once, at the only place that builds them.
    @RequiresApi(Build.VERSION_CODES.S)
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    private fun emitLowTick() {
        emit(
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.4f)
                .compose()
        )
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    private fun emitThud() {
        emit(
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.7f)
                .compose()
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    private fun emitSuccessRise() {
        emit(
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.6f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 1f, 80)
                .compose()
        )
    }

    @RequiresPermission(android.Manifest.permission.VIBRATE)
    private fun predefined(effect: Int, fallbackMs: Long) {
        if (!enabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            emit(VibrationEffect.createPredefined(effect))
        } else {
            emit(VibrationEffect.createOneShot(fallbackMs, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private companion object {
        const val SYSTEM_SETTING_TTL_MS = 500L
    }
}

@Composable
fun rememberStrongHaptics(): StrongHaptics {
    val context = LocalContext.current
    val enabled = LocalHapticsEnabled.current
    // Read the preference through a lambda so a toggle doesn't rebuild the instance
    // (and re-run its capability probes) — the existing instance just starts returning
    // early on its next call.
    val enabledRef = rememberUpdatedStateOf(enabled)
    return remember(context) { StrongHaptics(context) { enabledRef() } }
}

@Composable
private fun rememberUpdatedStateOf(value: Boolean): () -> Boolean {
    val state = androidx.compose.runtime.rememberUpdatedState(value)
    return remember { { state.value } }
}
