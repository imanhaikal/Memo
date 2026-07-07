package com.imanhaikal.memo.data.receipt

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptResponseParserTest {

    private val json = Json { ignoreUnknownKeys = true }

    // --- extractPayloadText ---

    @Test
    fun `extracts text from first candidate`() {
        val body = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [{"text": "{\"total\":\"12.50\",\"note\":\"Tesco\"}"}],
                    "role": "model"
                  },
                  "finishReason": "STOP"
                }
              ],
              "usageMetadata": {"promptTokenCount": 100}
            }
        """.trimIndent()
        assertEquals(
            "{\"total\":\"12.50\",\"note\":\"Tesco\"}",
            ReceiptResponseParser.extractPayloadText(body, json)
        )
    }

    @Test
    fun `returns null for empty candidates`() {
        assertNull(ReceiptResponseParser.extractPayloadText("""{"candidates": []}""", json))
    }

    @Test
    fun `returns null for candidate without parts`() {
        val body = """{"candidates": [{"finishReason": "SAFETY"}]}"""
        assertNull(ReceiptResponseParser.extractPayloadText(body, json))
    }

    @Test
    fun `returns null for malformed body`() {
        assertNull(ReceiptResponseParser.extractPayloadText("not json at all", json))
    }

    // --- stripMarkdownFences ---

    @Test
    fun `strips json fences`() {
        val fenced = "```json\n{\"total\":\"9.00\"}\n```"
        assertEquals("{\"total\":\"9.00\"}", ReceiptResponseParser.stripMarkdownFences(fenced))
    }

    @Test
    fun `strips bare fences`() {
        val fenced = "```\n{\"total\":\"9.00\"}\n```"
        assertEquals("{\"total\":\"9.00\"}", ReceiptResponseParser.stripMarkdownFences(fenced))
    }

    @Test
    fun `leaves plain json untouched`() {
        assertEquals("{\"a\":1}", ReceiptResponseParser.stripMarkdownFences("{\"a\":1}"))
    }

    // --- parseExtraction ---

    @Test
    fun `parses extraction payload`() {
        val extraction = ReceiptResponseParser.parseExtraction(
            """{"total":"12.50","note":"Tesco","confidence":0.95}""", json
        )
        assertEquals("12.50", extraction?.total)
        assertEquals("Tesco", extraction?.note)
    }

    @Test
    fun `parse returns null for garbage`() {
        assertNull(ReceiptResponseParser.parseExtraction("oops", json))
    }

    // --- totalToCents ---

    @Test
    fun `parses plain decimal`() {
        assertEquals(1250L, ReceiptResponseParser.totalToCents("12.50"))
    }

    @Test
    fun `parses single decimal digit`() {
        assertEquals(1250L, ReceiptResponseParser.totalToCents("12.5"))
    }

    @Test
    fun `parses thousands separators`() {
        assertEquals(123456L, ReceiptResponseParser.totalToCents("1,234.56"))
    }

    @Test
    fun `parses currency prefix`() {
        assertEquals(1250L, ReceiptResponseParser.totalToCents("RM 12.50"))
    }

    @Test
    fun `parses comma decimal separator`() {
        assertEquals(1250L, ReceiptResponseParser.totalToCents("12,50"))
    }

    @Test
    fun `parses comma thousands without decimals`() {
        assertEquals(123400L, ReceiptResponseParser.totalToCents("1,234"))
    }

    @Test
    fun `zero total means unreadable`() {
        assertNull(ReceiptResponseParser.totalToCents("0"))
    }

    @Test
    fun `empty total is null`() {
        assertNull(ReceiptResponseParser.totalToCents(""))
    }

    @Test
    fun `garbage total is null`() {
        assertNull(ReceiptResponseParser.totalToCents("abc"))
    }

    @Test
    fun `negative total is null`() {
        assertNull(ReceiptResponseParser.totalToCents("-5.00"))
    }
}
