package com.smartcommit.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * [AiProvider] implementation for the OpenAI Chat Completions API.
 *
 * Calls `POST /v1/chat/completions` with system + user messages.
 * Extracts the assistant's reply from `choices[0].message.content`.
 *
 * No IntelliJ APIs. No hardcoded keys. All config via constructor.
 *
 * @param apiKey      OpenAI API key. Passed at construction time, never stored in files.
 * @param model       Model ID (default: `gpt-4o-mini` — cost-effective for commit messages).
 * @param baseUrl     API base URL (default: OpenAI production). Override for proxies or Azure.
 * @param timeoutSecs Request timeout in seconds.
 * @param httpClient  Injectable OkHttp client (for testing with MockWebServer).
 */
class OpenAiProvider(
    private val apiKey: String,
    private val model: String = "gpt-4o-mini",
    private val baseUrl: String = "https://api.openai.com/v1",
    private val timeoutSecs: Long = 30,
    private val httpClient: OkHttpClient = defaultClient(timeoutSecs)
) : AiProvider {

    override val name: String = "OpenAI ($model)"

    override fun complete(systemPrompt: String, userPrompt: String): Result<String> {
        return try {
            val body = buildRequestBody(systemPrompt, userPrompt)
            val request = Request.Builder()
                .url("$baseUrl/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "No response body"
                    return Result.failure(
                        OpenAiException("OpenAI API error ${response.code}: $errorBody")
                    )
                }

                val responseBody = response.body?.string()
                    ?: return Result.failure(OpenAiException("Empty response body from OpenAI"))

                val content = extractContent(responseBody)
                    ?: return Result.failure(OpenAiException("No content in OpenAI response"))

                Result.success(content)
            }
        } catch (e: IOException) {
            Result.failure(OpenAiException("Network error calling OpenAI: ${e.message}", e))
        } catch (e: Exception) {
            Result.failure(OpenAiException("Unexpected error calling OpenAI: ${e.message}", e))
        }
    }

    // ── Request construction ────────────────────────────────

    @Serializable
    internal data class ChatMessage(val role: String, val content: String)

    @Serializable
    internal data class ChatRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val temperature: Double = 0.3,
        val max_tokens: Int = 512
    )

    private val json = Json { encodeDefaults = true }

    internal fun buildRequestBody(systemPrompt: String, userPrompt: String): String {
        val request = ChatRequest(
            model = model,
            messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = userPrompt)
            )
        )
        return json.encodeToString(request)
    }

    // ── Response extraction ─────────────────────────────────

    private val responseJson = Json { ignoreUnknownKeys = true }

    /**
     * Extract `choices[0].message.content` from the OpenAI response JSON.
     */
    internal fun extractContent(responseBody: String): String? {
        return try {
            val root = responseJson.parseToJsonElement(responseBody).jsonObject
            val choices = root["choices"]?.jsonArray ?: return null
            if (choices.isEmpty()) return null
            val message = choices[0].jsonObject["message"]?.jsonObject ?: return null
            message["content"]?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
    }

    class OpenAiException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        fun defaultClient(timeoutSecs: Long): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(timeoutSecs, TimeUnit.SECONDS)
            .readTimeout(timeoutSecs, TimeUnit.SECONDS)
            .writeTimeout(timeoutSecs, TimeUnit.SECONDS)
            .build()
    }
}
