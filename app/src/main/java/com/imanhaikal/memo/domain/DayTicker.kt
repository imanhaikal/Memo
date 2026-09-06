package com.imanhaikal.memo.domain

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import java.time.Clock
import java.time.Duration
import java.time.LocalDate

/**
 * Emits the current local date, and again whenever it changes.
 *
 * The budget's whole premise is "what can I spend *today*", but the UI state was only ever
 * recomputed when a transaction or a setting changed. An app left open past midnight showed
 * yesterday's number. This is the missing input.
 */
interface DayTicker {
    val today: Flow<LocalDate>
}

class SystemDayTicker(
    private val context: Context,
    private val clock: Clock
) : DayTicker {

    override val today: Flow<LocalDate> = merge(
        // Scheduled tick: emit now, then sleep exactly until the next local midnight.
        flow {
            while (true) {
                val now = clock.instant().atZone(clock.zone)
                emit(now.toLocalDate())
                val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(clock.zone)
                val untilMidnight = Duration.between(now, nextMidnight).toMillis()
                delay(untilMidnight.coerceAtLeast(1_000L))
            }
        },
        // The scheduled tick assumes a monotonic clock; these cover the cases where that
        // assumption breaks — the user edits the time, or crosses a timezone.
        callbackFlow {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    trySend(clock.instant().atZone(clock.zone).toLocalDate())
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_DATE_CHANGED)
            }
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            awaitClose { context.unregisterReceiver(receiver) }
        }
    ).distinctUntilChanged()
}
