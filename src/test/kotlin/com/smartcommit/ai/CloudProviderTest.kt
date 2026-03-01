package com.smartcommit.ai

import com.smartcommit.checkin.CloudUsageException
import com.smartcommit.checkin.CloudUsageInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Unit tests for the Cloud generation flow (POST /api/cloud/generate).
 *
 * Uses OkHttp MockWebServer. Since [CloudProvider.complete] requires
 * IntelliJ PasswordSafe (via [CloudAuthManager]), we test the HTTP call
 * and response parsing logic directly using a test helper that mirrors
 * CloudProvider's behavior without IntelliJ dependencies.
 *
 * The full integration (auth + generate) is tested manually in the IDE.
 */
class CloudProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var client: TestCloudClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = TestCloudClient(server.url("").toString().trimEnd('/'))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ── Successful generation ───────────────────────────────

    @Test
    fun `successful generation returns message and usage info`() {
        server.enqueue(MockResponse().setBody("""
            {
              "ok": true,
              "message": "feat(auth): add email verification flow",
              "usage": { "used": 12, "limit": 30, "remaining": 18 },
              "resetAt": "2026-03-01T00:00:00Z"
            }
        """).setResponseCode(200))

        val result = client.callGenerate("system prompt", "user prompt")

        assertTrue(result.isSuccess)
        assertEquals("feat(auth): add email verification flow", result.message)
        assertNotNull(result.usageInfo)
        assertEquals(12, result.usageInfo!!.used)
        assertEquals(30, result.usageInfo!!.limit)
        assertEquals(18, result.usageInfo!!.remaining)
        assertEquals("FREE", result.usageInfo!!.plan)
    }

    @Test
    fun `successful generation with Starter plan limit`() {
        server.enqueue(MockResponse().setBody("""
            {
              "ok": true,
              "message": "fix: resolve null pointer in login",
              "usage": { "used": 150, "limit": 300, "remaining": 150 },
              "resetAt": "2026-03-01T00:00:00Z"
            }
        """).setResponseCode(200))

        val result = client.callGenerate("system", "user")
        assertTrue(result.isSuccess)
        assertEquals("STARTER", result.usageInfo!!.plan)
    }

    @Test
    fun `successful generation with Pro plan limit`() {
        server.enqueue(MockResponse().setBody("""
            {
              "ok": true,
              "message": "refactor: extract validation logic",
              "usage": { "used": 500, "limit": 3000, "remaining": 2500 },
              "resetAt": "2026-03-01T00:00:00Z"
            }
        """).setResponseCode(200))

        val result = client.callGenerate("system", "user")
        assertTrue(result.isSuccess)
        assertEquals("PRO", result.usageInfo!!.plan)
    }

    // ── Limit exhausted ─────────────────────────────────────

    @Test
    fun `limit exhausted throws CloudUsageException`() {
        server.enqueue(MockResponse().setBody("""
            {
              "ok": false,
              "reason": "limit_exhausted",
              "usage": { "used": 30, "limit": 30, "remaining": 0 },
              "resetAt": "2026-03-01T00:00:00Z"
            }
        """).setResponseCode(200))

        try {
            client.callGenerate("system", "user")
            fail("Expected CloudUsageException")
        } catch (e: CloudUsageException) {
            assertEquals(CloudUsageException.Reason.LIMIT_EXHAUSTED, e.reason)
            assertEquals(30, e.used)
            assertEquals(30, e.limit)
            assertEquals("2026-03-01T00:00:00Z", e.resetAt)
        }
    }

    // ── Subscription inactive ───────────────────────────────

    @Test
    fun `subscription inactive throws CloudUsageException`() {
        server.enqueue(MockResponse().setBody("""
            {
              "ok": false,
              "reason": "subscription_inactive"
            }
        """).setResponseCode(200))

        try {
            client.callGenerate("system", "user")
            fail("Expected CloudUsageException")
        } catch (e: CloudUsageException) {
            assertEquals(CloudUsageException.Reason.SUBSCRIPTION_INACTIVE, e.reason)
        }
    }

    // ── Generation failed (OpenAI error on server) ──────────

    @Test
    fun `generation_failed returns failure for template fallback`() {
        server.enqueue(MockResponse().setBody("""
            {
              "ok": false,
              "reason": "generation_failed",
              "usage": { "used": 13, "limit": 30, "remaining": 17 },
              "resetAt": "2026-03-01T00:00:00Z"
            }
        """).setResponseCode(200))

        val result = client.callGenerate("system", "user")
        assertTrue(result.isFailure)
        assertTrue(result.error!!.contains("generation failed"))
    }

    // ── Rate limited (429) ──────────────────────────────────

    @Test
    fun `429 response throws CloudUsageException with RATE_LIMITED`() {
        server.enqueue(MockResponse().setResponseCode(429))

        try {
            client.callGenerate("system", "user")
            fail("Expected CloudUsageException")
        } catch (e: CloudUsageException) {
            assertEquals(CloudUsageException.Reason.RATE_LIMITED, e.reason)
        }
    }

    // ── Server error ────────────────────────────────────────

    @Test
    fun `500 response returns failure`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val result = client.callGenerate("system", "user")
        assertTrue(result.isFailure)
        assertTrue(result.error!!.contains("HTTP 500"))
    }

    // ── Network error ───────────────────────────────────────

    @Test
    fun `network error returns failure`() {
        server.shutdown() // force connection failure

        val result = client.callGenerate("system", "user")
        assertTrue(result.isFailure)
        assertTrue(result.error!!.contains("Network error"))
    }

    // ── Request format ──────────────────────────────────────

    @Test
    fun `sends correct request format`() {
        server.enqueue(MockResponse().setBody("""
            {
              "ok": true,
              "message": "test message",
              "usage": { "used": 1, "limit": 30, "remaining": 29 },
              "resetAt": ""
            }
        """).setResponseCode(200))

        client.callGenerate("my system prompt", "my user prompt")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.path!!.contains("/api/cloud/generate"))
        assertEquals("Bearer test-access-token", request.getHeader("Authorization"))

        val body = request.body.readUtf8()
        assertTrue(body.contains("\"systemPrompt\""))
        assertTrue(body.contains("\"userPrompt\""))
        assertTrue(body.contains("my system prompt"))
        assertTrue(body.contains("my user prompt"))
    }

    // ── Malformed response ──────────────────────────────────

    @Test
    fun `malformed JSON returns failure`() {
        server.enqueue(MockResponse().setBody("not json at all").setResponseCode(200))

        val result = client.callGenerate("system", "user")
        assertTrue(result.isFailure)
    }

    // ── 401 unauthorized ────────────────────────────────────

    @Test
    fun `401 response returns unauthorized failure`() {
        server.enqueue(MockResponse().setResponseCode(401))

        val result = client.callGenerate("system", "user")
        assertTrue(result.isFailure)
        assertTrue(result.error!!.contains("unauthorized"))
    }
}

/**
 * Test helper that mirrors CloudProvider's HTTP call and response parsing
 * without requiring IntelliJ PasswordSafe.
 */
class TestCloudClient(private val baseUrl: String) {

    private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    data class GenerateResult(
        val isSuccess: Boolean,
        val isFailure: Boolean = !isSuccess,
        val message: String? = null,
        val usageInfo: CloudUsageInfo? = null,
        val error: String? = null
    )

    fun callGenerate(systemPrompt: String, userPrompt: String): GenerateResult {
        val escapedSystem = escapeJson(systemPrompt)
        val escapedUser = escapeJson(userPrompt)
        val body = """{"systemPrompt":"$escapedSystem","userPrompt":"$escapedUser"}"""

        return try {
            val request = Request.Builder()
                .url("$baseUrl/api/cloud/generate")
                .addHeader("Authorization", "Bearer test-access-token")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(request).execute().use { response ->
                when {
                    response.code == 401 -> GenerateResult(
                        isSuccess = false,
                        error = "unauthorized"
                    )
                    response.code == 429 -> throw CloudUsageException(
                        reason = CloudUsageException.Reason.RATE_LIMITED
                    )
                    !response.isSuccessful -> GenerateResult(
                        isSuccess = false,
                        error = "HTTP ${response.code}"
                    )
                    else -> parseResponse(response.body?.string() ?: "")
                }
            }
        } catch (e: CloudUsageException) {
            throw e
        } catch (e: Exception) {
            GenerateResult(isSuccess = false, error = "Network error: ${e.message}")
        }
    }

    private fun parseResponse(responseBody: String): GenerateResult {
        return try {
            val root = json.parseToJsonElement(responseBody).jsonObject
            val ok = root["ok"]?.jsonPrimitive?.boolean ?: false

            if (ok) {
                val message = root["message"]?.jsonPrimitive?.content

                var usageInfo: CloudUsageInfo? = null
                val usage = root["usage"]?.jsonObject
                if (usage != null) {
                    val used = usage["used"]?.jsonPrimitive?.int ?: 0
                    val limit = usage["limit"]?.jsonPrimitive?.int ?: 0
                    val remaining = usage["remaining"]?.jsonPrimitive?.int ?: 0
                    val resetAt = root["resetAt"]?.jsonPrimitive?.content ?: ""
                    val plan = when (limit) {
                        300 -> "STARTER"
                        3000 -> "PRO"
                        else -> "FREE"
                    }
                    usageInfo = CloudUsageInfo(plan, used, limit, remaining, resetAt)
                }

                GenerateResult(isSuccess = true, message = message, usageInfo = usageInfo)
            } else {
                val reason = root["reason"]?.jsonPrimitive?.content ?: "unknown"
                val resetAt = root["resetAt"]?.jsonPrimitive?.content ?: ""
                val usage = root["usage"]?.jsonObject
                val used = usage?.get("used")?.jsonPrimitive?.int ?: 0
                val limit = usage?.get("limit")?.jsonPrimitive?.int ?: 0

                when (reason) {
                    "limit_exhausted" -> throw CloudUsageException(
                        reason = CloudUsageException.Reason.LIMIT_EXHAUSTED,
                        used = used, limit = limit, resetAt = resetAt
                    )
                    "subscription_inactive" -> throw CloudUsageException(
                        reason = CloudUsageException.Reason.SUBSCRIPTION_INACTIVE
                    )
                    "generation_failed" -> GenerateResult(
                        isSuccess = false,
                        error = "Cloud AI generation failed. Using fallback."
                    )
                    else -> GenerateResult(isSuccess = false, error = "Cloud error: $reason")
                }
            }
        } catch (e: CloudUsageException) {
            throw e
        } catch (e: Exception) {
            GenerateResult(isSuccess = false, error = "Parse error: ${e.message}")
        }
    }

    private fun escapeJson(s: String): String = s
        .replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
}
