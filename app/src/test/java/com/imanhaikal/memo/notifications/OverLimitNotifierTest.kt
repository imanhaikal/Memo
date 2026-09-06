package com.imanhaikal.memo.notifications

import com.imanhaikal.memo.data.NotificationSettings
import com.imanhaikal.memo.data.Transaction
import com.imanhaikal.memo.domain.BudgetCalculatorUseCase
import com.imanhaikal.memo.domain.BudgetSummaryProvider
import com.imanhaikal.memo.testing.MemoTestHarness
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * The trigger for the "Over limit" toggle. The toggle shipped with no wiring at all, so
 * these cover the decision logic rather than the posting, which is injected.
 */
class OverLimitNotifierTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2024, 4, 10)
    private val clock: Clock = Clock.fixed(today.atStartOfDay(zone).toInstant(), zone)

    /** Only what the OS accepted, so the guard assertions read as "was it shown". */
    private val sent = mutableListOf<NotificationText>()

    /** False stands in for a post the OS dropped, e.g. the runtime permission is denied. */
    private var postSucceeds = true

    private fun notifier(harness: MemoTestHarness) = OverLimitNotifier(
        summaryProvider = BudgetSummaryProvider(
            budgetRepository = harness.repository,
            transactionDao = harness.transactionDao,
            calculator = BudgetCalculatorUseCase(zone)
        ),
        preferences = harness.notificationPreferences,
        clock = clock,
        notify = { text -> postSucceeds.also { accepted -> if (accepted) sent += text } }
    )

    /** 300_000 over 30 days is 10_000 a day; this spends 15_000 of it. */
    private suspend fun MemoTestHarness.seedOverspend() {
        seedBudget(amountCents = 300_000L, totalDays = 30, startDate = today)
        transactionDao.insertTransaction(
            Transaction(
                amount = 15_000L,
                note = "Splurge",
                date = millisAtNoon(today),
                budgetId = 1L
            )
        )
    }

    private suspend fun MemoTestHarness.enableOverLimit() {
        notificationPreferences.update(NotificationSettings(overLimit = true))
    }

    @Test
    fun `notifies when the day's allowance is blown`() = runTest {
        val harness = MemoTestHarness(clock, today)
        harness.seedOverspend()
        harness.enableOverLimit()

        notifier(harness).checkAndNotify(today)

        assertEquals(1, sent.size)
        assertTrue(sent.single().title.contains("Over", ignoreCase = true))
    }

    @Test
    fun `stays silent while the toggle is off`() = runTest {
        val harness = MemoTestHarness(clock, today)
        harness.seedOverspend()

        notifier(harness).checkAndNotify(today)

        assertTrue(sent.isEmpty())
    }

    @Test
    fun `stays silent while still within the allowance`() = runTest {
        val harness = MemoTestHarness(clock, today)
        harness.seedBudget(amountCents = 300_000L, totalDays = 30, startDate = today)
        harness.transactionDao.insertTransaction(
            Transaction(
                amount = 2_500L,
                note = "Lunch",
                date = harness.millisAtNoon(today),
                budgetId = 1L
            )
        )
        harness.enableOverLimit()

        notifier(harness).checkAndNotify(today)

        assertTrue(sent.isEmpty())
    }

    @Test
    fun `notifies once a day however often it is checked`() = runTest {
        val harness = MemoTestHarness(clock, today)
        harness.seedOverspend()
        harness.enableOverLimit()
        val notifier = notifier(harness)

        repeat(4) { notifier.checkAndNotify(today) }

        assertEquals(1, sent.size)
    }

    @Test
    fun `the guard resets with the new day`() = runTest {
        val harness = MemoTestHarness(clock, today)
        harness.seedOverspend()
        harness.enableOverLimit()
        val notifier = notifier(harness)

        notifier.checkAndNotify(today)

        // Yesterday's overspend re-amortizes over the remaining days, so tomorrow starts
        // positive again — it takes a fresh overspend to go over a second time, which is
        // what the guard must not suppress.
        val tomorrow = today.plusDays(1)
        harness.transactionDao.insertTransaction(
            Transaction(
                amount = 15_000L,
                note = "Again",
                date = harness.millisAtNoon(tomorrow),
                budgetId = 1L
            )
        )
        notifier.checkAndNotify(tomorrow)

        assertEquals(2, sent.size)
    }

    @Test
    fun `a day spent under the limit does not burn the guard`() = runTest {
        val harness = MemoTestHarness(clock, today)
        harness.seedBudget(amountCents = 300_000L, totalDays = 30, startDate = today)
        harness.enableOverLimit()
        val notifier = notifier(harness)

        notifier.checkAndNotify(today)
        assertTrue(sent.isEmpty())

        harness.transactionDao.insertTransaction(
            Transaction(
                amount = 15_000L,
                note = "Splurge",
                date = harness.millisAtNoon(today),
                budgetId = 1L
            )
        )
        notifier.checkAndNotify(today)

        assertEquals(1, sent.size)
    }

    @Test
    fun `a dropped notification does not burn the guard`() = runTest {
        val harness = MemoTestHarness(clock, today)
        harness.seedOverspend()
        harness.enableOverLimit()
        val notifier = notifier(harness)

        // The toggle is on but the OS refuses the post — nothing is shown.
        postSucceeds = false
        notifier.checkAndNotify(today)
        assertTrue(sent.isEmpty())

        // The permission arrives later the same day. The alert must still be able to fire;
        // before this it stayed silent until midnight.
        postSucceeds = true
        notifier.checkAndNotify(today)

        assertEquals(1, sent.size)
    }

    @Test
    fun `no budget means nothing to be over`() = runTest {
        val harness = MemoTestHarness(clock, today)
        harness.enableOverLimit()

        notifier(harness).checkAndNotify(today)

        assertTrue(sent.isEmpty())
    }
}
