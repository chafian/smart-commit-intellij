package com.smartcommit.ai

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [OpenAiProvider].
 * Uses OkHttp MockWebServer — no real network calls.
 */
class OpenAiProviderTest {

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

    private fun provider(apiKey: String = "test-key", model: String = "gpt-4o-mini"): OpenAiProvider {
        return OpenAiProvider(
            apiKey = apiKey,
            model = model,
            baseUrl = server.url("/v1").toString().trimEnd('/'),
            timeoutSecs = 5
        )
    }

    // ── Successful completion ────────────────────────────────

    @Test
    fun `complete returns content on successful response`() {
        val responseJson = """
            {
              "id": "chatcmpl-123",
              "choices": [{
                "index": 0,
                "message": {
                  "role": "assistant",
                  "content": "{\"title\": \"Add feature\"}"
                }
              }]
            }
        """.trimIndent()

        server.enqueue(MockResponse().setBody(responseJson).setResponseCode(200))

        val result = provider().complete("system", "user")
        assertTrue(result.isSuccess)
        assertEquals("{\"title\": \"Add feature\"}", result.getOrThrow())
    }

    @Test
    fun `complete sends correct request structure`() {
        server.enqueue(MockResponse().setBody("""
            {"choices": [{"message": {"content": "ok"}}]}
        """).setResponseCode(200))

        provider(apiKey = "sk-test123", model = "gpt-4o").complete("sys prompt", "usr prompt")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.path!!.endsWith("/chat/completions"))
        assertEquals("Bearer sk-test123", request.getHeader("Authorization"))
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))

        val body = request.body.readUtf8()
        assertTrue(body.contains("gpt-4o"))
        assertTrue(body.contains("sys prompt"))
        assertTrue(body.contains("usr prompt"))
        assertTrue(body.contains("\"role\":\"system\""))
        assertTrue(body.contains("\"role\":\"user\""))
    }

    // ── Error responses ─────────────────────────────────────

    @Test
    fun `complete returns failure on 401 unauthorized`() {
        server.enqueue(MockResponse().setBody("""{"error": "invalid_api_key"}""").setResponseCode(401))

        val result = provider().complete("sys", "usr")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is OpenAiProvider.OpenAiException)
        assertTrue(ex!!.message!!.contains("401"))
    }

    @Test
    fun `complete returns failure on 429 rate limit`() {
        server.enqueue(MockResponse().setBody("""{"error": "rate_limited"}""").setResponseCode(429))

        val result = provider().complete("sys", "usr")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("429"))
    }

    @Test
    fun `complete returns failure on 500 server error`() {
        server.enqueue(MockResponse().setBody("Internal Server Error").setResponseCode(500))

        val result = provider().complete("sys", "usr")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("500"))
    }

    // ── Malformed responses ─────────────────────────────────

    @Test
    fun `complete returns failure when choices is empty`() {
        server.enqueue(MockResponse().setBody("""{"choices": []}""").setResponseCode(200))

        val result = provider().complete("sys", "usr")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("No content"))
    }

    @Test
    fun `complete returns failure when response is not JSON`() {
        server.enqueue(MockResponse().setBody("not json at all").setResponseCode(200))

        val result = provider().complete("sys", "usr")
        assertTrue(result.isFailure)
    }

    @Test
    fun `complete returns failure when message content is missing`() {
        server.enqueue(MockResponse().setBody("""
            {"choices": [{"message": {}}]}
        """).setResponseCode(200))

        val result = provider().complete("sys", "usr")
        assertTrue(result.isFailure)
    }

    // ── Network errors ──────────────────────────────────────

    @Test
    fun `complete returns failure on connection refused`() {
        server.shutdown() // force connection failure
        val result = provider().complete("sys", "usr")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is OpenAiProvider.OpenAiException)
    }

    // ── Request body structure ───────────────────────────────

    @Test
    fun `buildRequestBody includes temperature and max_tokens`() {
        val body = provider().buildRequestBody("sys", "usr")
        assertTrue(body.contains("\"temperature\""))
        assertTrue(body.contains("\"max_tokens\""))
    }

    // ── Response extraction ─────────────────────────────────

    @Test
    fun `extractContent parses valid OpenAI response`() {
        val json = """
            {"choices": [{"message": {"role": "assistant", "content": "hello world"}}]}
        """.trimIndent()
        assertEquals("hello world", provider().extractContent(json))
    }

    @Test
    fun `extractContent returns null for garbage`() {
        assertNull(provider().extractContent("garbage"))
    }

    @Test
    fun `extractContent returns null for missing choices`() {
        assertNull(provider().extractContent("""{"id": "123"}"""))
    }

    // ── Provider metadata ───────────────────────────────────

    @Test
    fun `name includes model`() {
        val p = provider(model = "gpt-4o")
        assertEquals("OpenAI (gpt-4o)", p.name)
    }
}
