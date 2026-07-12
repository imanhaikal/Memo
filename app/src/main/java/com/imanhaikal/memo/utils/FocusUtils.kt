package com.imanhaikal.memo.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned

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
