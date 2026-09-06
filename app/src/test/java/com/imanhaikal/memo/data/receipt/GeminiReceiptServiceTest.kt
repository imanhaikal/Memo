package com.imanhaikal.memo.data.receipt

import com.imanhaikal.memo.data.Category
import com.imanhaikal.memo.data.Transaction
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime

class GeminiReceiptServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var service: GeminiReceiptService

    private val zone = ZoneId.of("Asia/Kuala_Lumpur")

    // 2026-07-19 12:00 in Asia/Kuala_Lumpur. Fixed so prompt and datetime assertions
    // can't drift across a midnight boundary mid-run.
    private val now = ZonedDateTime.of(2026, 7, 19, 12, 0, 0, 0, zone)

    private fun millisAt(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        service = GeminiReceiptService(
            apiKey = "test-key",
            clock = Clock.fixed(now.toInstant(), zone),
            baseUrl = server.url("/v1beta/models/gemini-flash-lite-latest:generateContent").toString()
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun successBody(payload: String): String {
        val escaped = payload
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
        return """
            {
              "candidates": [
                {
                  "content": {"parts": [{"text": "$escaped"}], "role": "model"},
                  "finishReason": "STOP"
                }
              ]
            }
        """.trimIndent()
    }

    @Test
    fun `valid response returns success and sends expected request`() = runTest {
        server.enqueue(
            MockResponse().setBody(successBody("""{"total":"12.50","note":"Tesco","confidence":0.9}"""))
        )

        val outcome = service.extractReceipt("aW1hZ2U=")

        assertEquals(ScanOutcome.Success(1250L, "Tesco"), outcome)

        val recorded = server.takeRequest()
        assertEquals("test-key", recorded.getHeader("x-goog-api-key"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"responseMimeType\":\"application/json\""))
        assertTrue(body.contains("\"thinkingLevel\":\"MINIMAL\""))
        assertTrue(body.contains("\"data\":\"aW1hZ2U=\""))
        // The prompt must anchor the model to the current date so it doesn't
        // misresolve ambiguous or missing receipt years
        assertTrue(body.contains("Today's date is 2026-07-19"))
    }

    @Test
    fun `response with details carries category, description and datetime`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                successBody(
                    """{"total":"12.50","note":"Tesco","confidence":0.9,""" +
                        """"category":"shopping","description":"Groceries and snacks","datetime":"2026-07-18 14:35"}"""
                )
            )
        )

        val outcome = service.extractReceipt("aW1hZ2U=")

        assertEquals(
            ScanOutcome.Success(
                amountCents = 1250L,
                note = "Tesco",
                category = Category.SHOPPING,
                description = "Groceries and snacks",
                dateMillis = millisAt(2026, 7, 18, 14, 35)
            ),
            outcome
        )

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"category\""))
        assertTrue(body.contains("\"datetime\""))
    }

    @Test
    fun `date-only receipt datetime carries hasTime false`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                successBody("""{"total":"5.00","note":"Kiosk","datetime":"2026-07-18"}""")
            )
        )

        val outcome = service.extractReceipt("aW1hZ2U=") as ScanOutcome.Success

        assertEquals(millisAt(2026, 7, 18, 12, 0), outcome.dateMillis)
        assertEquals(false, outcome.dateHasTime)
    }

    @Test
    fun `response without details falls back to empty defaults`() = runTest {
        server.enqueue(
            MockResponse().setBody(successBody("""{"total":"12.50","note":"Tesco","confidence":0.9}"""))
        )

        val outcome = service.extractReceipt("aW1hZ2U=") as ScanOutcome.Success

        assertNull(outcome.category)
        assertEquals("", outcome.description)
        assertNull(outcome.dateMillis)
    }

    @Test
    fun `unknown category id maps to other`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                successBody("""{"total":"5.00","note":"Kiosk","category":"gadgets"}""")
            )
        )

        val outcome = service.extractReceipt("aW1hZ2U=") as ScanOutcome.Success

        assertEquals(Category.OTHER, outcome.category)
    }

    @Test
    fun `over-long note and description are truncated to the shared limits`() = runTest {
        // A prefill longer than the dialog's own cap used to lock its edit field.
        server.enqueue(
            MockResponse().setBody(
                successBody(
                    """{"total":"5.00","note":"${"n".repeat(120)}",""" +
                        """"description":"${"d".repeat(600)}"}"""
                )
            )
        )

        val outcome = service.extractReceipt("aW1hZ2U=") as ScanOutcome.Success

        assertEquals(Transaction.NOTE_MAX_CHARS, outcome.note.length)
        assertEquals(Transaction.DESCRIPTION_MAX_CHARS, outcome.description.length)
    }

    @Test
    fun `fenced json payload still succeeds`() = runTest {
        server.enqueue(
            MockResponse().setBody(successBody("```json\n{\"total\":\"7.00\",\"note\":\"KFC\"}\n```"))
        )

        assertEquals(ScanOutcome.Success(700L, "KFC"), service.extractReceipt("aW1hZ2U="))
    }

    @Test
    fun `zero total maps to unreadable`() = runTest {
        server.enqueue(
            MockResponse().setBody(successBody("""{"total":"0","note":"","confidence":0}"""))
        )

        assertEquals(
            ScanOutcome.Failure(ScanFailureReason.UNREADABLE),
            service.extractReceipt("aW1hZ2U=")
        )
    }

    @Test
    fun `server error maps to api error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        assertEquals(
            ScanOutcome.Failure(ScanFailureReason.API_ERROR),
            service.extractReceipt("aW1hZ2U=")
        )
    }

    @Test
    fun `rate limit maps to api error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))

        assertEquals(
            ScanOutcome.Failure(ScanFailureReason.API_ERROR),
            service.extractReceipt("aW1hZ2U=")
        )
    }

    @Test
    fun `safety-blocked candidate maps to parse error`() = runTest {
        // A blocked response is a 200 with a candidate that carries no content.
        server.enqueue(
            MockResponse().setBody("""{"candidates": [{"finishReason": "SAFETY"}]}""")
        )

        assertEquals(
            ScanOutcome.Failure(ScanFailureReason.PARSE),
            service.extractReceipt("aW1hZ2U=")
        )
    }

    @Test
    fun `empty candidates maps to parse error`() = runTest {
        server.enqueue(MockResponse().setBody("""{"candidates": []}"""))

        assertEquals(
            ScanOutcome.Failure(ScanFailureReason.PARSE),
            service.extractReceipt("aW1hZ2U=")
        )
    }

    @Test
    fun `malformed body maps to parse error`() = runTest {
        server.enqueue(MockResponse().setBody("<html>gateway</html>"))

        assertEquals(
            ScanOutcome.Failure(ScanFailureReason.PARSE),
            service.extractReceipt("aW1hZ2U=")
        )
    }

    @Test
    fun `connection failure maps to network error`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        assertEquals(
            ScanOutcome.Failure(ScanFailureReason.NETWORK),
            service.extractReceipt("aW1hZ2U=")
        )
    }
}
