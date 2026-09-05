package com.imanhaikal.memo.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/**
 * Requests focus once the requester's node is attached and globally positioned.
 * Inside a Dialog the window's nodes can attach several frames after composition,
 * where an immediate [FocusRequester.requestFocus] throws "FocusRequester is not initialized".
 */
fun Modifier.autoFocusOnceAttached(focusRequester: FocusRequester): Modifier = composed {
    var hasRequestedFocus by remember { mutableStateOf(false) }

    this
        .focusRequester(focusRequester)
        .onGloballyPositioned {
            if (!hasRequestedFocus) {
                focusRequester.requestFocus()
                hasRequestedFocus = true
            }
        }
}

/** How far the user must drag downward before the keyboard is taken as unwanted. */
private val DismissDragDistance = 56.dp

/**
 * Dismisses the keyboard when the user deliberately drags the content downward.
 *
 * Deliberately *not* [androidx.compose.foundation.layout.imeNestedScroll]: that drags the
 * IME in both directions, so scrolling past the bottom edge pulls the keyboard back up —
 * in a form, reaching the submit button summons the very thing you were scrolling out
 * from under. This is the dismiss half only.
 */
fun Modifier.dismissKeyboardOnDragDown(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    val thresholdPx = with(LocalDensity.current) { DismissDragDistance.toPx() }

    val connection = remember(focusManager, thresholdPx) {
        object : NestedScrollConnection {
            // Accumulate across events so a flick and a slow drag behave the same, and
            // so incidental jitter during typing doesn't close the keyboard
            private var draggedDown = 0f

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero

                // Any upward movement means they changed their mind — start over
                draggedDown = if (available.y > 0f) draggedDown + available.y else 0f

                if (draggedDown >= thresholdPx) {
                    draggedDown = 0f
                    focusManager.clearFocus()
                }
                // Never consume: the scroll itself must still happen
                return Offset.Zero
            }

            // Called at the end of every drag, fling or not. Without this the total
            // survives the gesture, and several unrelated nudges eventually add up to
            // a dismissal the user never asked for.
            override suspend fun onPreFling(available: Velocity): Velocity {
                draggedDown = 0f
                return Velocity.Zero
            }
        }
    }

    nestedScroll(connection)
}
