package com.smartcommit.ai

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CloudUsageClient].
 * Uses OkHttp MockWebServer — no real network calls.
 */
class CloudUsageClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun baseUrl(): String = server.url("").toString().trimEnd('/')

    // ── Successful consumption ───────────────────────────────

    @Test
    fun `consume returns Ok on successful response`() {
        val responseJson = """
            {
              "ok": true,
              "plan": "FREE",
              "subscriptionStatus": "NONE",
              "usage": {
                "used": 12,
                "limit": 30,
                "remaining": 18,
                "resetAt": "2026-03-01T00:00:00Z"
              }
            }
        """.trimIndent()

        server.enqueue(MockResponse().setBody(responseJson).setResponseCode(200))

        val result = CloudUsageClient.consume(baseUrl(), "test-token")
        assertTrue(result is UsageResult.Ok)

        val ok = result as UsageResult.Ok
        assertEquals("FREE", ok.plan)
        assertEquals(12, ok.used)
        assertEquals(30, ok.limit)
        assertEquals(18, ok.remaining)
        assertEquals("2026-03-01T00:00:00Z", ok.resetAt)
    }

    @Test
    fun `consume sends correct request`() {
        server.enqueue(MockResponse().setBody("""
            {"ok": true, "plan": "FREE", "usage": {"used": 1, "limit": 30, "remaining": 29, "resetAt": ""}}
        """).setResponseCode(200))

        CloudUsageClient.consume(baseUrl(), "my-jwt-token")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.path!!.contains("/api/cloud/usage/consume"))
        assertEquals("Bearer my-jwt-token", request.getHeader("Authorization"))
        assertTrue(request.body.readUtf8().contains("\"amount\":1"))
    }

    // ── Limit exhausted ─────────────────────────────────────

    @Test
    fun `consume returns LimitExhausted when quota is used up`() {
        val responseJson = """
            {
              "ok": false,
              "reason": "limit_exhausted",
              "resetAt": "2026-03-01T00:00:00Z"
            }
        """.trimIndent()

        server.enqueue(MockResponse().setBody(responseJson).setResponseCode(200))

        val result = CloudUsageClient.consume(baseUrl(), "test-token")
        assertTrue(result is UsageResult.LimitExhausted)

        val exhausted = result as UsageResult.LimitExhausted
        assertEquals("2026-03-01T00:00:00Z", exhausted.resetAt)
    }

    // ── Subscription inactive ───────────────────────────────

    @Test
    fun `consume returns SubscriptionInactive when subscription is inactive`() {
        val responseJson = """
            {
              "ok": false,
              "reason": "subscription_inactive"
            }
        """.trimIndent()

        server.enqueue(MockResponse().setBody(responseJson).setResponseCode(200))

        val result = CloudUsageClient.consume(baseUrl(), "test-token")
        assertTrue(result is UsageResult.SubscriptionInactive)
    }

    // ── Unauthorized (401) ──────────────────────────────────

    @Test
    fun `consume returns Unauthorized on 401`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error": "unauthorized"}"""))

        val result = CloudUsageClient.consume(baseUrl(), "expired-token")
        assertTrue(result is UsageResult.Unauthorized)
    }

    // ── Rate limited (429) ──────────────────────────────────

    @Test
    fun `consume returns RateLimited on 429`() {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error": "rate_limited"}"""))

        val result = CloudUsageClient.consume(baseUrl(), "test-token")
        assertTrue(result is UsageResult.RateLimited)
    }

    // ── Server error ────────────────────────────────────────

    @Test
    fun `consume returns Error on 500`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val result = CloudUsageClient.consume(baseUrl(), "test-token")
        assertTrue(result is UsageResult.Error)
        assertTrue((result as UsageResult.Error).message.contains("500"))
    }

    // ── Network error ───────────────────────────────────────

    @Test
    fun `consume returns Error on connection failure`() {
        server.shutdown() // force connection failure

        val result = CloudUsageClient.consume(baseUrl(), "test-token")
        assertTrue(result is UsageResult.Error)
        assertTrue((result as UsageResult.Error).message.contains("Network error"))
    }

    // ── Malformed response ──────────────────────────────────

    @Test
    fun `consume returns Error on garbage response`() {
        server.enqueue(MockResponse().setBody("not json").setResponseCode(200))

        val result = CloudUsageClient.consume(baseUrl(), "test-token")
        assertTrue(result is UsageResult.Error)
    }
}
