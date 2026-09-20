package com.imanhaikal.memo.ui.dialogs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.imanhaikal.memo.R
import com.imanhaikal.memo.ui.components.ReceiptImageState
import com.imanhaikal.memo.ui.components.rememberReceiptBitmap
import com.imanhaikal.memo.utils.rememberStrongHaptics
import java.io.File
import kotlin.math.abs

/**
 * Full-screen lightbox for one saved receipt.
 *
 * A [Dialog] rather than a nav destination: the viewer is opened from inside the add/edit
 * dialog, and pushing a screen underneath an open dialog means either the dialog floats
 * over the new screen or its half-typed state has to be torn down and rebuilt.
 */
@Composable
fun ReceiptViewerDialog(
    file: File?,
    onShare: () -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val haptic = rememberStrongHaptics()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val state by rememberReceiptBitmap(file = file, maxDimension = VIEWER_PX)

            when (val current = state) {
                ReceiptImageState.Loading -> CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )

                // Routine, not exceptional: backups carry rows but no image files, so every
                // restored install lands here. Say why rather than showing a blank screen.
                ReceiptImageState.Missing -> MissingReceipt(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 40.dp)
                )

                is ReceiptImageState.Loaded -> ZoomableReceipt(bitmap = current)
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScrimIconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }

                Row {
                    // Sharing needs the file on this device. Remove stays available either
                    // way, so a reference whose image is missing can still be detached.
                    if (state is ReceiptImageState.Loaded) {
                        ScrimIconButton(onClick = {
                            haptic.tick()
                            onShare()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share receipt",
                                tint = Color.White
                            )
                        }
                    }
                    if (onDelete != null) {
                        Spacer(modifier = Modifier.size(4.dp))
                        ScrimIconButton(onClick = {
                            haptic.thud()
                            onDelete()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remove receipt",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun ZoomableReceipt(bitmap: ReceiptImageState.Loaded) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    /** Keeps the image from being panned off screen once it is larger than the viewport. */
    fun clampOffsets() {
        val maxX = (viewport.width * (scale - 1f) / 2f).coerceAtLeast(0f)
        val maxY = (viewport.height * (scale - 1f) / 2f).coerceAtLeast(0f)
        offsetX = offsetX.coerceIn(-maxX, maxX)
        offsetY = offsetY.coerceIn(-maxY, maxY)
    }

    Image(
        bitmap = bitmap.bitmap,
        contentDescription = "Receipt",
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewport = it }
            // Two detectors, two modifiers: a single pointerInput can only run one
            // suspending gesture loop at a time.
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                    clampOffsets()
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        if (abs(scale - 1f) < 0.01f) {
                            scale = DOUBLE_TAP_SCALE
                            // The layer scales about its centre, so shift by the tap's
                            // distance from it to keep the tapped point under the finger.
                            offsetX = (viewport.width / 2f - tap.x) * (DOUBLE_TAP_SCALE - 1f)
                            offsetY = (viewport.height / 2f - tap.y) * (DOUBLE_TAP_SCALE - 1f)
                        } else {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        }
                        clampOffsets()
                    }
                )
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offsetX
                translationY = offsetY
            }
    )
}

@Composable
private fun MissingReceipt(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_receipt),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "This receipt image isn't on this device",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Backups don't include receipt images.",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

/** A dark disc behind each control, so white glyphs stay legible over a pale receipt. */
@Composable
private fun ScrimIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(Color.Black.copy(alpha = 0.45f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick, content = { content() })
    }
}

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f

/** Receipts are text, so decode generously — this is the surface people pinch into. */
private const val VIEWER_PX = 2048
