package com.smartcommit.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * [AiProvider] implementation for the Ollama local LLM API.
 *
 * Calls `POST /api/generate` with a combined prompt (Ollama's generate
 * endpoint uses a single `prompt` field rather than message roles).
 *
 * Ollama runs locally — no API key needed, fully private.
 *
 * No IntelliJ APIs. No hardcoded config. All params via constructor.
 *
 * @param model       Ollama model name (default: `llama3`).
 * @param baseUrl     Ollama server URL (default: `http://localhost:11434`).
 * @param timeoutSecs Request timeout — longer default (60s) for cold starts.
 * @param httpClient  Injectable OkHttp client (for testing).
 */
class OllamaProvider(
    private val model: String = "llama3",
    private val baseUrl: String = "http://localhost:11434",
    private val timeoutSecs: Long = 60,
    private val httpClient: OkHttpClient = defaultClient(timeoutSecs)
) : AiProvider {

    override val name: String = "Ollama ($model)"

    override fun complete(systemPrompt: String, userPrompt: String): Result<String> {
        return try {
            val body = buildRequestBody(systemPrompt, userPrompt)
            val request = Request.Builder()
                .url("$baseUrl/api/generate")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "No response body"
                    return Result.failure(
                        OllamaException("Ollama API error ${response.code}: $errorBody")
                    )
                }

                val responseBody = response.body?.string()
                    ?: return Result.failure(OllamaException("Empty response body from Ollama"))

                val content = extractContent(responseBody)
                    ?: return Result.failure(OllamaException("No content in Ollama response"))

                Result.success(content)
            }
        } catch (e: IOException) {
            Result.failure(OllamaException("Cannot reach Ollama at $baseUrl: ${e.message}", e))
        } catch (e: Exception) {
            Result.failure(OllamaException("Unexpected error calling Ollama: ${e.message}", e))
        }
    }

    // ── Request construction ────────────────────────────────

    @Serializable
    internal data class GenerateRequest(
        val model: String,
        val prompt: String,
        val system: String,
        val stream: Boolean = false,
        val options: GenerateOptions = GenerateOptions()
    )

    @Serializable
    internal data class GenerateOptions(
        val temperature: Double = 0.3,
        val num_predict: Int = 512
    )

    private val json = Json { encodeDefaults = true }

    internal fun buildRequestBody(systemPrompt: String, userPrompt: String): String {
        val request = GenerateRequest(
            model = model,
            prompt = userPrompt,
            system = systemPrompt
        )
        return json.encodeToString(request)
    }

    // ── Response extraction ─────────────────────────────────

    private val responseJson = Json { ignoreUnknownKeys = true }

    /**
     * Extract `response` field from the Ollama generate response JSON.
     *
     * Ollama's non-streaming response format:
     * ```json
     * {
     *   "model": "llama3",
     *   "response": "...",
     *   "done": true,
     *   ...
     * }
     * ```
     */
    internal fun extractContent(responseBody: String): String? {
        return try {
            val root = responseJson.parseToJsonElement(responseBody).jsonObject
            root["response"]?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
    }

    class OllamaException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        fun defaultClient(timeoutSecs: Long): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(timeoutSecs, TimeUnit.SECONDS)
            .readTimeout(timeoutSecs, TimeUnit.SECONDS)
            .writeTimeout(timeoutSecs, TimeUnit.SECONDS)
            .build()
    }
}
