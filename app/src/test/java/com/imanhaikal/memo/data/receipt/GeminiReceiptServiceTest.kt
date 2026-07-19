package com.imanhaikal.memo.data.receipt

import com.imanhaikal.memo.data.Category
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class GeminiReceiptServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var service: GeminiReceiptService

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        service = GeminiReceiptService(
            apiKey = "test-key",
            baseUrl = server.url("/v1beta/models/gemini-3.1-flash-lite:generateContent").toString()
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
    fun `valid response returns success and sends expected request`() {
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
    }

    @Test
    fun `response with details carries category, description and datetime`() {
        val zone = ZoneId.systemDefault()
        val receiptTime = LocalDateTime.now(zone).minusDays(1).withSecond(0).withNano(0)
        val datetime = receiptTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        server.enqueue(
            MockResponse().setBody(
                successBody(
                    """{"total":"12.50","note":"Tesco","confidence":0.9,""" +
                        """"category":"shopping","description":"Groceries and snacks","datetime":"$datetime"}"""
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
                dateMillis = receiptTime.atZone(zone).toInstant().toEpochMilli()
            ),
            outcome
        )

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"category\""))
        assertTrue(body.contains("\"datetime\""))
    }

    @Test
    fun `response without details falls back to empty defaults`() {
        server.enqueue(
            MockResponse().setBody(successBody("""{"total":"12.50","note":"Tesco","confidence":0.9}"""))
        )

        val outcome = service.extractReceipt("aW1hZ2U=") as ScanOutcome.Success

        assertNull(outcome.category)
        assertEquals("", outcome.description)
        assertNull(outcome.dateMillis)
    }

    @Test
    fun `unknown category id maps to other`() {
        server.enqueue(
            MockResponse().setBody(
                successBody("""{"total":"5.00","note":"Kiosk","category":"gadgets"}""")
            )
        )

        val outcome = service.extractReceipt("aW1hZ2U=") as ScanOutcome.Success

        assertEquals(Category.OTHER, outcome.category)
    }

    @Test
    fun `fenced json payload still succeeds`() {
        server.enqueue(
            MockResponse().setBody(successBody("```json\n{\"total\":\"7.00\",\"note\":\"KFC\"}\n```"))
        )

        assertEquals(ScanOutcome.Success(700L, "KFC"), service.extractReceipt("aW1hZ2U="))
    }

    @Test
    fun `zero total maps to unreadable`() {
        server.enqueue(
            MockResponse().setBody(successBody("""{"total":"0","note":"","confidence":0}"""))
        )

        assertEquals(
            ScanOutcome.Failure(ScanFailureReason.UNREADABLE),
            service.extractReceipt("aW1hZ2U=")
        )
    }

    @Test
    fun `server error maps to api error`() {
        server.enqueue(MockResponse().setResponseCode(500))

        assertEquals(
            ScanOutcome.Failure(ScanFailureReason.API_ERROR),
            service.extractReceipt("aW1hZ2U=")
        )
    }

    @Test
    fun `malformed body maps to parse error`() {
        server.enqueue(MockResponse().setBody("<html>gateway</html>"))

        assertEquals(
            ScanOutcome.Failure(ScanFailureReason.PARSE),
            service.extractReceipt("aW1hZ2U=")
        )
    }

    @Test
    fun `connection failure maps to network error`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        assertEquals(
            ScanOutcome.Failure(ScanFailureReason.NETWORK),
            service.extractReceipt("aW1hZ2U=")
        )
    }
}
