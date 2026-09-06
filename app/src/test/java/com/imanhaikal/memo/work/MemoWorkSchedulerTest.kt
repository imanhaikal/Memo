package com.imanhaikal.memo.work

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * The scheduling arithmetic. Worth testing on its own because "next 9am" is exactly the
 * kind of thing that silently becomes "in 24 hours" or "immediately" at the boundaries.
 */
class MemoWorkSchedulerTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    private fun clockAt(time: String): Clock =
        Clock.fixed(Instant.parse(time), zone)

    @Test
    fun `a target later today is the same day`() {
        val delay = MemoWorkScheduler.delayUntilNextLocalTime(
            clockAt("2024-04-10T07:00:00Z"),
            LocalTime.of(9, 0)
        )

        assertEquals(2 * 60, delay.toMinutes())
    }

    @Test
    fun `a target already past today rolls to tomorrow`() {
        val delay = MemoWorkScheduler.delayUntilNextLocalTime(
            clockAt("2024-04-10T10:00:00Z"),
            LocalTime.of(9, 0)
        )

        assertEquals(23 * 60, delay.toMinutes())
    }

    @Test
    fun `exactly on the target waits a full day rather than firing twice`() {
        val delay = MemoWorkScheduler.delayUntilNextLocalTime(
            clockAt("2024-04-10T09:00:00Z"),
            LocalTime.of(9, 0)
        )

        assertEquals(24 * 60, delay.toMinutes())
    }

    @Test
    fun `just before midnight the next early target is minutes away`() {
        val delay = MemoWorkScheduler.delayUntilNextLocalTime(
            clockAt("2024-04-10T23:50:00Z"),
            LocalTime.of(0, 30)
        )

        assertEquals(40, delay.toMinutes())
    }
}
