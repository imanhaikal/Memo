package com.imanhaikal.memo.ui

import com.imanhaikal.memo.data.Category
import com.imanhaikal.memo.data.Transaction
import com.imanhaikal.memo.ui.components.groupTransactionsByDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class TransactionGroupingTest {

    private val zone = ZoneId.of("Asia/Kuala_Lumpur")
    private val today = LocalDate.of(2026, 7, 19)

    private fun transaction(id: Int, amount: Long, month: Int, day: Int, hour: Int) = Transaction(
        id = id,
        amount = amount,
        note = "t$id",
        date = ZonedDateTime.of(2026, month, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()
    )

    @Test
    fun `empty list yields no groups`() {
        assertTrue(groupTransactionsByDay(emptyList(), zone, today).isEmpty())
    }

    @Test
    fun `groups by local day preserving date-desc order`() {
        val transactions = listOf(
            transaction(1, 500, 7, 19, 15),
            transaction(2, 300, 7, 19, 9),
            transaction(3, 700, 7, 18, 20),
            transaction(4, 100, 7, 15, 12)
        )

        val groups = groupTransactionsByDay(transactions, zone, today)

        assertEquals(3, groups.size)
        assertEquals(listOf("Today", "Yesterday", "Jul 15"), groups.map { it.label })
        assertEquals(listOf(1, 2), groups[0].transactions.map { it.id })
        assertEquals(listOf(3), groups[1].transactions.map { it.id })
        assertEquals(listOf(4), groups[2].transactions.map { it.id })
    }

    @Test
    fun `per-day totals sum amounts`() {
        val transactions = listOf(
            transaction(1, 500, 7, 19, 15),
            transaction(2, 300, 7, 19, 9),
            transaction(3, 700, 7, 18, 20)
        )

        val groups = groupTransactionsByDay(transactions, zone, today)

        assertEquals(800L, groups[0].totalCents)
        assertEquals(700L, groups[1].totalCents)
    }

    @Test
    fun `category fromId resolves ids, blanks and unknowns`() {
        assertEquals(Category.FOOD, Category.fromId("food"))
        assertEquals(Category.OTHER, Category.fromId("gadgets"))
        assertNull(Category.fromId(""))
        assertNull(Category.fromId(null))
    }
}
