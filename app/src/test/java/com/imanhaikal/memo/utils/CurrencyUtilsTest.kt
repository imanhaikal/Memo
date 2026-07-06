package com.imanhaikal.memo.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrencyUtilsTest {

    @Test
    fun `parseAmountToCents handles valid decimal amounts`() {
        assertEquals(123_46L, CurrencyUtils.parseAmountToCents("123.456"))
        assertEquals(100L, CurrencyUtils.parseAmountToCents("1"))
        assertEquals(1L, CurrencyUtils.parseAmountToCents("0.01"))
    }

    @Test
    fun `parseAmountToCents rejects invalid non-positive and oversized input`() {
        assertNull(CurrencyUtils.parseAmountToCents(""))
        assertNull(CurrencyUtils.parseAmountToCents("0"))
        assertNull(CurrencyUtils.parseAmountToCents("-1"))
        assertNull(CurrencyUtils.parseAmountToCents("999999999999999999999999999999999999"))
    }
}
