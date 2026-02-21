package com.smartcommit.ai

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [OllamaProvider].
 * Uses OkHttp MockWebServer — no real network calls.
 */
class OllamaProviderTest {

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

    private fun provider(model: String = "llama3"): OllamaProvider {
        return OllamaProvider(
            model = model,
            baseUrl = server.url("").toString().trimEnd('/'),
            timeoutSecs = 5
        )
    }

    // ── Successful completion ────────────────────────────────

    @Test
    fun `complete returns response content on success`() {
        val responseJson = """
            {
              "model": "llama3",
              "response": "{\"title\": \"Fix bug\"}",
              "done": true
            }
        """.trimIndent()

        server.enqueue(MockResponse().setBody(responseJson).setResponseCode(200))

        val result = provider().complete("system", "user")
        assertTrue(result.isSuccess)
        assertEquals("{\"title\": \"Fix bug\"}", result.getOrThrow())
    }

    @Test
    fun `complete sends correct request structure`() {
        server.enqueue(MockResponse().setBody("""
            {"response": "ok", "done": true}
        """).setResponseCode(200))

        provider(model = "codellama").complete("sys prompt", "usr prompt")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.path!!.endsWith("/api/generate"))
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))

        val body = request.body.readUtf8()
        assertTrue(body.contains("codellama"))
        assertTrue(body.contains("sys prompt"))
        assertTrue(body.contains("usr prompt"))
        assertTrue(body.contains("\"stream\":false"))
    }

    @Test
    fun `complete does not send authorization header`() {
        server.enqueue(MockResponse().setBody("""
            {"response": "ok", "done": true}
        """).setResponseCode(200))

        provider().complete("sys", "usr")

        val request = server.takeRequest()
        assertNull(request.getHeader("Authorization"))
    }

    // ── Error responses ─────────────────────────────────────

    @Test
    fun `complete returns failure on 404 model not found`() {
        server.enqueue(MockResponse().setBody("""{"error": "model not found"}""").setResponseCode(404))

        val result = provider().complete("sys", "usr")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is OllamaProvider.OllamaException)
        assertTrue(ex!!.message!!.contains("404"))
    }

    @Test
    fun `complete returns failure on 500 server error`() {
        server.enqueue(MockResponse().setBody("Internal Error").setResponseCode(500))

        val result = provider().complete("sys", "usr")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("500"))
    }

    // ── Malformed responses ─────────────────────────────────

    @Test
    fun `complete returns failure when response field is missing`() {
        server.enqueue(MockResponse().setBody("""{"model": "llama3", "done": true}""").setResponseCode(200))

        val result = provider().complete("sys", "usr")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("No content"))
    }

    @Test
    fun `complete returns failure for non-JSON response`() {
        server.enqueue(MockResponse().setBody("not json").setResponseCode(200))

        val result = provider().complete("sys", "usr")
        assertTrue(result.isFailure)
    }

    // ── Network errors ──────────────────────────────────────

    @Test
    fun `complete returns failure on connection refused`() {
        server.shutdown()
        val result = provider().complete("sys", "usr")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is OllamaProvider.OllamaException)
        assertTrue(ex!!.message!!.contains("Cannot reach Ollama"))
    }

    // ── Request body structure ───────────────────────────────

    @Test
    fun `buildRequestBody includes model and stream false`() {
        val body = provider(model = "mistral").buildRequestBody("sys", "usr")
        assertTrue(body.contains("\"model\":\"mistral\""))
        assertTrue(body.contains("\"stream\":false"))
        assertTrue(body.contains("\"system\":\"sys\""))
        assertTrue(body.contains("\"prompt\":\"usr\""))
    }

    @Test
    fun `buildRequestBody includes temperature and num_predict`() {
        val body = provider().buildRequestBody("sys", "usr")
        assertTrue(body.contains("\"temperature\""))
        assertTrue(body.contains("\"num_predict\""))
    }

    // ── Response extraction ─────────────────────────────────

    @Test
    fun `extractContent parses valid Ollama response`() {
        val json = """{"model": "llama3", "response": "hello", "done": true}"""
        assertEquals("hello", provider().extractContent(json))
    }

    @Test
    fun `extractContent returns null for garbage`() {
        assertNull(provider().extractContent("garbage"))
    }

    @Test
    fun `extractContent returns null for missing response field`() {
        assertNull(provider().extractContent("""{"model": "llama3"}"""))
    }

    // ── Provider metadata ───────────────────────────────────

    @Test
    fun `name includes model`() {
        val p = provider(model = "codellama")
        assertEquals("Ollama (codellama)", p.name)
    }
}
