package com.smartcommit.ai

import com.intellij.openapi.diagnostic.Logger
import com.smartcommit.checkin.CloudNotConnectedException
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
import java.util.concurrent.TimeUnit

/**
 * [AiProvider] implementation for Smart Commit Cloud.
 *
 * Calls `POST /api/cloud/generate` — the single endpoint that:
 * 1. Authenticates via JWT
 * 2. Consumes usage atomically (server-side)
 * 3. Calls OpenAI on the backend
 * 4. Returns the generated commit message
 *
 * One request. One transaction boundary. No separate /usage/consume call.
 *
 * On success, stores usage info in [lastUsageInfo] so callers can show
 * the usage notification after generation.
 *
 * On business errors (limit_exhausted, subscription_inactive, rate_limited),
 * throws [CloudUsageException] for callers to handle (show upgrade modal, etc.).
 *
 * On 401, attempts token refresh via [CloudAuthManager.refreshTokens] and retries once.
 *
 * @param baseUrl The Cloud backend base URL (e.g. "https://api.smartcommit.dev")
 */
open class CloudProvider(
    private val baseUrl: String = "https://api.smartcommit.dev"
) : AiProvider {

    private val log = Logger.getInstance(CloudProvider::class.java)

    override val name: String = "Smart Commit Cloud"

    /** Usage info from the last successful generation. Read by callers for notification. */
    open var lastUsageInfo: CloudUsageInfo? = null
        internal set

    private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun complete(systemPrompt: String, userPrompt: String): Result<String> {
        lastUsageInfo = null

        val accessToken = CloudAuthManager.getAccessToken()
        if (accessToken == null) {
            log.warn("SmartCommit Cloud: no access token found — not connected")
            throw CloudNotConnectedException()
        }

        // First attempt
        log.info("SmartCommit Cloud: calling /api/cloud/generate...")
        var result = callGenerate(accessToken, systemPrompt, userPrompt)

        // Handle 401: refresh token and retry once
        if (result.isUnauthorized) {
            log.info("SmartCommit Cloud: got 401, attempting token refresh...")
            val newToken = CloudAuthManager.refreshTokens(baseUrl)
            if (newToken == null) {
                log.warn("SmartCommit Cloud: token refresh failed — session expired")
                throw CloudNotConnectedException("Session expired. Please reconnect your IDE in Settings > Tools > Smart Commit.")
            }
            log.info("SmartCommit Cloud: token refreshed, retrying generate...")
            result = callGenerate(newToken, systemPrompt, userPrompt)

            if (result.isUnauthorized) {
                log.warn("SmartCommit Cloud: still 401 after refresh — clearing tokens")
                CloudAuthManager.clearTokens()
                throw CloudNotConnectedException("Session expired. Please reconnect your IDE in Settings > Tools > Smart Commit.")
            }
        }

        // Handle rate limiting (429 from RateLimitFilter)
        if (result.isRateLimited) {
            throw CloudUsageException(reason = CloudUsageException.Reason.RATE_LIMITED)
        }

        // Handle non-200 HTTP errors
        if (result.httpError != null) {
            return Result.failure(RuntimeException("Server error: ${result.httpError}"))
        }

        // Handle network errors
        if (result.networkError != null) {
            return Result.failure(RuntimeException("Network error: ${result.networkError}"))
        }

        // Parse the structured response
        val body = result.responseBody ?: return Result.failure(RuntimeException("Empty response"))
        return parseGenerateResponse(body)
    }

    /**
     * Make the HTTP call to POST /api/cloud/generate.
     */
    private fun callGenerate(
        accessToken: String,
        systemPrompt: String,
        userPrompt: String
    ): CallResult {
        return try {
            // Build JSON body manually (no kotlinx-serialization encoding issues)
            val escapedSystem = escapeJsonString(systemPrompt)
            val escapedUser = escapeJsonString(userPrompt)
            val requestBody = """{"systemPrompt":"$escapedSystem","userPrompt":"$escapedUser"}"""

            val request = Request.Builder()
                .url("$baseUrl/api/cloud/generate")
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(request).execute().use { response ->
                when {
                    response.code == 401 -> CallResult(isUnauthorized = true)
                    response.code == 429 -> CallResult(isRateLimited = true)
                    !response.isSuccessful -> CallResult(httpError = "HTTP ${response.code}")
                    else -> CallResult(responseBody = response.body?.string())
                }
            }
        } catch (e: Exception) {
            CallResult(networkError = e.message ?: "Unknown network error")
        }
    }

    /**
     * Parse the JSON response from /api/cloud/generate.
     * Throws [CloudUsageException] for business errors. Returns Result for everything else.
     */
    private fun parseGenerateResponse(responseBody: String): Result<String> {
        return try {
            val root = json.parseToJsonElement(responseBody).jsonObject
            val ok = root["ok"]?.jsonPrimitive?.boolean ?: false

            if (ok) {
                val message = root["message"]?.jsonPrimitive?.content
                    ?: return Result.failure(RuntimeException("No message in response"))

                // Extract usage info for notification
                val usage = root["usage"]?.jsonObject
                if (usage != null) {
                    val used = usage["used"]?.jsonPrimitive?.int ?: 0
                    val limit = usage["limit"]?.jsonPrimitive?.int ?: 0
                    val remaining = usage["remaining"]?.jsonPrimitive?.int ?: 0
                    val resetAt = root["resetAt"]?.jsonPrimitive?.content ?: ""

                    // Infer plan from limit
                    val plan = when (limit) {
                        300 -> "STARTER"
                        3000 -> "PRO"
                        else -> "FREE"
                    }

                    lastUsageInfo = CloudUsageInfo(
                        plan = plan,
                        used = used,
                        limit = limit,
                        remaining = remaining,
                        resetAt = resetAt
                    )
                }

                Result.success(message)
            } else {
                val reason = root["reason"]?.jsonPrimitive?.content ?: "unknown"
                val resetAt = root["resetAt"]?.jsonPrimitive?.content ?: ""

                // Extract usage info if available (for limit_exhausted)
                val usage = root["usage"]?.jsonObject
                val used = usage?.get("used")?.jsonPrimitive?.int ?: 0
                val limit = usage?.get("limit")?.jsonPrimitive?.int ?: 0

                when (reason) {
                    "limit_exhausted" -> throw CloudUsageException(
                        reason = CloudUsageException.Reason.LIMIT_EXHAUSTED,
                        used = used,
                        limit = limit,
                        resetAt = resetAt
                    )
                    "subscription_inactive" -> throw CloudUsageException(
                        reason = CloudUsageException.Reason.SUBSCRIPTION_INACTIVE
                    )
                    "rate_limited" -> throw CloudUsageException(
                        reason = CloudUsageException.Reason.RATE_LIMITED
                    )
                    "generation_failed" -> {
                        // OpenAI failed on the server — usage was consumed but no message
                        // Return failure so the fallback template generator kicks in
                        Result.failure(RuntimeException("Cloud AI generation failed. Using fallback."))
                    }
                    "not_configured" -> {
                        Result.failure(RuntimeException("Cloud AI is not configured on the server."))
                    }
                    else -> Result.failure(RuntimeException("Cloud error: $reason"))
                }
            }
        } catch (e: CloudUsageException) {
            throw e // re-throw — these must propagate to callers
        } catch (e: Exception) {
            Result.failure(RuntimeException("Failed to parse response: ${e.message}"))
        }
    }

    /**
     * Escape a string for JSON embedding.
     */
    private fun escapeJsonString(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    /**
     * Internal result of an HTTP call — before JSON parsing.
     */
    private data class CallResult(
        val responseBody: String? = null,
        val isUnauthorized: Boolean = false,
        val isRateLimited: Boolean = false,
        val httpError: String? = null,
        val networkError: String? = null
    )
}
