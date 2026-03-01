package com.smartcommit.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * HTTP client for Cloud AI usage enforcement.
 *
 * @deprecated Use [CloudProvider] which calls `POST /api/cloud/generate` instead.
 *             That endpoint handles usage consumption + OpenAI call in a single request.
 *             This client is kept temporarily for backward compatibility during transition.
 *
 * Calls `POST /api/cloud/usage/consume` to atomically decrement the user's
 * monthly quota before each generation.
 *
 * Handles 401 responses by signaling the caller to refresh tokens and retry.
 */
@Deprecated("Use CloudProvider which calls /api/cloud/generate instead")
object CloudUsageClient {

    private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Attempt to consume one Cloud AI generation.
     *
     * @param baseUrl     The Cloud backend base URL (e.g. "https://api.smartcommit.dev")
     * @param accessToken The user's JWT access token
     * @return [UsageResult] indicating success or the specific failure reason
     */
    fun consume(baseUrl: String, accessToken: String): UsageResult {
        return try {
            val body = """{"amount":1}"""
            val request = Request.Builder()
                .url("$baseUrl/api/cloud/usage/consume")
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.code == 401) {
                    return UsageResult.Unauthorized
                }

                if (response.code == 429) {
                    return UsageResult.RateLimited
                }

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "No response body"
                    return UsageResult.Error("Server error ${response.code}: $errorBody")
                }

                val responseBody = response.body?.string()
                    ?: return UsageResult.Error("Empty response body")

                parseResponse(responseBody)
            }
        } catch (e: Exception) {
            UsageResult.Error("Network error: ${e.message}")
        }
    }

    private fun parseResponse(responseBody: String): UsageResult {
        return try {
            val root = json.parseToJsonElement(responseBody).jsonObject
            val ok = root["ok"]?.jsonPrimitive?.boolean ?: false

            if (ok) {
                val plan = root["plan"]?.jsonPrimitive?.content ?: "FREE"
                val usage = root["usage"]?.jsonObject
                val used = usage?.get("used")?.jsonPrimitive?.int ?: 0
                val limit = usage?.get("limit")?.jsonPrimitive?.int ?: 0
                val remaining = usage?.get("remaining")?.jsonPrimitive?.int ?: 0
                val resetAt = usage?.get("resetAt")?.jsonPrimitive?.content ?: ""

                UsageResult.Ok(
                    plan = plan,
                    used = used,
                    limit = limit,
                    remaining = remaining,
                    resetAt = resetAt
                )
            } else {
                val reason = root["reason"]?.jsonPrimitive?.content ?: "unknown"
                val resetAt = root["resetAt"]?.jsonPrimitive?.content

                when (reason) {
                    "limit_exhausted" -> UsageResult.LimitExhausted(
                        resetAt = resetAt ?: ""
                    )
                    "subscription_inactive" -> UsageResult.SubscriptionInactive
                    else -> UsageResult.Error("Rejected: $reason")
                }
            }
        } catch (e: Exception) {
            UsageResult.Error("Failed to parse usage response: ${e.message}")
        }
    }
}

/**
 * Result of a Cloud usage consumption attempt.
 */
sealed class UsageResult {
    /** Usage consumed successfully. */
    data class Ok(
        val plan: String,
        val used: Int,
        val limit: Int,
        val remaining: Int,
        val resetAt: String
    ) : UsageResult()

    /** Monthly limit reached — user needs to upgrade or wait. */
    data class LimitExhausted(val resetAt: String) : UsageResult()

    /** Subscription is no longer active. */
    data object SubscriptionInactive : UsageResult()

    /** Access token expired — caller should refresh and retry. */
    data object Unauthorized : UsageResult()

    /** Rate limited — too many requests. */
    data object RateLimited : UsageResult()

    /** Network or parsing error. */
    data class Error(val message: String) : UsageResult()
}
