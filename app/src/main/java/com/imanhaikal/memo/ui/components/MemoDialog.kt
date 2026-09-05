package com.imanhaikal.memo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.imanhaikal.memo.ui.theme.AppColors
import com.imanhaikal.memo.utils.dismissKeyboardOnDragDown
import kotlinx.coroutines.launch

/**
 * Plays the dialog's exit animation, then runs [after].
 *
 * Content must route every action that closes the dialog through this — calling
 * the caller's state flag directly yanks the dialog out of the tree mid-frame and
 * skips the exit entirely.
 */
typealias DialogCloser = (after: () -> Unit) -> Unit

/**
 * The app's one dialog surface. Owns the things every Memo dialog needs to get right
 * and previously re-implemented (or forgot) individually:
 *
 * - a symmetric spring entrance **and** exit, via the [DialogCloser] handed to content
 * - a full-size window with `decorFitsSystemWindows = false`, so the IME doesn't pan
 *   and clip the window (the wrap-content default corrupts the inner scroll viewport:
 *   content scrolls down but can never scroll back up)
 * - `imePadding` bounding the card above the keyboard, keeping the inner scroll range
 *   correct and fully reachable
 * - a downward drag that dismisses the keyboard, without the reverse (see
 *   [dismissKeyboardOnDragDown])
 * - outside-tap dismissal (the window covers the screen, so it's ours to handle) and
 *   dead-space taps that clear focus without also dismissing
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissOnClickOutside: Boolean = true,
    dismissOnBackPress: Boolean = true,
    maxWidth: Dp = 320.dp,
    contentPadding: Dp = 24.dp,
    content: @Composable ColumnScope.(close: DialogCloser) -> Unit
) {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(ENTER_SCALE) }
    val alpha = remember { Animatable(0f) }
    // Guards against a second close being started while the exit is already playing
    // (e.g. back press landing on top of an outside tap)
    val closing = remember { mutableStateOf(false) }

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
        launch { alpha.animateTo(1f, tween(durationMillis = ENTER_FADE_MS)) }
    }

    val close: DialogCloser = { after ->
        if (!closing.value) {
            closing.value = true
            scope.launch {
                launch { scale.animateTo(EXIT_SCALE, tween(durationMillis = EXIT_MS)) }
                alpha.animateTo(0f, tween(durationMillis = EXIT_MS))
                after()
            }
        }
    }

    Dialog(
        onDismissRequest = { close(onDismissRequest) },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = dismissOnBackPress,
            // The window is full-size, so Compose's own outside-tap detection would
            // fire anywhere on screen. We detect it ourselves on the scrim below.
            dismissOnClickOutside = false
        )
    ) {
        // Must be read inside the Dialog: its window hosts a separate focus owner,
        // and the activity's FocusManager cannot clear focus in here.
        val focusManager = LocalFocusManager.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .pointerInput(dismissOnClickOutside) {
                    detectTapGestures(
                        onTap = { if (dismissOnClickOutside) close(onDismissRequest) }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = AppColors.Surface,
                modifier = modifier
                    .padding(16.dp)
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        this.alpha = alpha.value
                    }
            ) {
                Column(
                    modifier = Modifier
                        // Tapping non-interactive card area clears focus, hiding the keyboard
                        // (and swallows the tap so it doesn't dismiss the dialog); children
                        // (fields, chips, buttons) consume their own taps first.
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { focusManager.clearFocus() })
                        }
                        .dismissKeyboardOnDragDown()
                        .verticalScroll(rememberScrollState())
                        .padding(contentPadding),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    content(close)
                }
            }
        }
    }
}

private const val ENTER_SCALE = 0.9f
private const val ENTER_FADE_MS = 220
private const val EXIT_SCALE = 0.92f
private const val EXIT_MS = 140
