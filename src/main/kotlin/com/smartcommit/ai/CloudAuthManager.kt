package com.smartcommit.ai

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Manages Smart Commit Cloud authentication tokens.
 *
 * Tokens are stored in IntelliJ PasswordSafe (system credential store):
 * - Access token: JWT, 24h lifetime
 * - Refresh token: opaque, 30d, rotating
 *
 * Token refresh strategy:
 * 1. Before API calls, check if access token is expired via [isAccessTokenExpired].
 * 2. If expired, proactively refresh before making the API call.
 * 3. If the API call still returns 401, refresh again and retry once.
 */
object CloudAuthManager {

    private val log = Logger.getInstance(CloudAuthManager::class.java)

    private const val SERVICE_NAME = "SmartCommit"
    private const val KEY_ACCESS_TOKEN = "cloud-access-token"
    private const val KEY_REFRESH_TOKEN = "cloud-refresh-token"
    private const val KEY_USER_EMAIL = "cloud-user-email"

    private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    // ── Token accessors ─────────────────────────────────────

    fun getAccessToken(): String? {
        return PasswordSafe.instance.getPassword(credentialAttributes(KEY_ACCESS_TOKEN))
            ?.ifBlank { null }
    }

    fun getRefreshToken(): String? {
        return PasswordSafe.instance.getPassword(credentialAttributes(KEY_REFRESH_TOKEN))
            ?.ifBlank { null }
    }

    fun getUserEmail(): String? {
        return PasswordSafe.instance.getPassword(credentialAttributes(KEY_USER_EMAIL))
            ?.ifBlank { null }
    }

    fun saveTokens(accessToken: String, refreshToken: String, email: String? = null) {
        PasswordSafe.instance.setPassword(credentialAttributes(KEY_ACCESS_TOKEN), accessToken)
        PasswordSafe.instance.setPassword(credentialAttributes(KEY_REFRESH_TOKEN), refreshToken)
        if (email != null) {
            PasswordSafe.instance.setPassword(credentialAttributes(KEY_USER_EMAIL), email)
        }
    }

    fun clearTokens() {
        PasswordSafe.instance.setPassword(credentialAttributes(KEY_ACCESS_TOKEN), null)
        PasswordSafe.instance.setPassword(credentialAttributes(KEY_REFRESH_TOKEN), null)
        PasswordSafe.instance.setPassword(credentialAttributes(KEY_USER_EMAIL), null)
    }

    /**
     * Whether tokens are stored (access or refresh).
     * Note: This does NOT guarantee the tokens are still valid.
     * Use [getValidAccessToken] for actual API calls.
     */
    fun isConnected(): Boolean = getAccessToken() != null || getRefreshToken() != null

    // ── Token validation ────────────────────────────────────

    /**
     * Check if the stored access token (JWT) is expired or about to expire.
     *
     * Decodes the JWT payload (base64url) and reads the `exp` claim.
     * Returns true if the token expires within the next 60 seconds.
     * Returns true (treat as expired) if the token cannot be decoded.
     */
    fun isAccessTokenExpired(): Boolean {
        val token = getAccessToken() ?: return true
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return true
            // Decode the payload (second part) — base64url encoded JSON
            val payloadJson = String(Base64.getUrlDecoder().decode(parts[1]))
            val payload = json.parseToJsonElement(payloadJson).jsonObject
            val exp = payload["exp"]?.jsonPrimitive?.content?.toLongOrNull() ?: return true
            val nowSecs = System.currentTimeMillis() / 1000
            // Expired if within 60 seconds of expiry
            exp <= nowSecs + 60
        } catch (_: Exception) {
            true // Can't decode → treat as expired
        }
    }

    /**
     * Get a valid access token, refreshing proactively if the current one is expired.
     *
     * @param baseUrl The Cloud backend base URL for refresh calls.
     * @return A valid access token, or null if both stored token and refresh fail.
     */
    fun getValidAccessToken(baseUrl: String): String? {
        val currentToken = getAccessToken() ?: return null

        if (!isAccessTokenExpired()) {
            return currentToken
        }

        // Access token is expired — proactively refresh
        log.info("SmartCommit Cloud: access token expired, proactively refreshing...")
        val newToken = refreshTokens(baseUrl)
        if (newToken != null) {
            log.info("SmartCommit Cloud: proactive refresh succeeded")
        } else {
            log.warn("SmartCommit Cloud: proactive refresh failed")
        }
        return newToken
    }

    // ── Token refresh ───────────────────────────────────────

    /**
     * Attempt to refresh the access token using the stored refresh token.
     *
     * Calls `POST /api/auth/refresh` on the backend.
     * On success, saves the new token pair and returns the new access token.
     * On failure (expired refresh, network error), clears all tokens and returns null.
     *
     * @param baseUrl The Cloud backend base URL (e.g. "https://api.smartcommit.dev")
     */
    fun refreshTokens(baseUrl: String): String? {
        val refreshToken = getRefreshToken()
        if (refreshToken == null) {
            log.warn("SmartCommit Cloud: no refresh token stored — cannot refresh")
            return null
        }

        return try {
            val body = """{"refreshToken":"$refreshToken"}"""
            val request = Request.Builder()
                .url("$baseUrl/api/auth/refresh")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            log.info("SmartCommit Cloud: calling POST $baseUrl/api/auth/refresh")
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    log.warn("SmartCommit Cloud: refresh failed with HTTP ${response.code}")
                    // Refresh token expired or invalid — user must reconnect
                    clearTokens()
                    return null
                }

                val responseBody = response.body?.string() ?: return null
                val root = json.parseToJsonElement(responseBody).jsonObject

                val newAccessToken = root["accessToken"]?.jsonPrimitive?.content ?: return null
                val newRefreshToken = root["refreshToken"]?.jsonPrimitive?.content ?: return null
                val email = root["account"]?.jsonObject?.get("email")?.jsonPrimitive?.content

                saveTokens(newAccessToken, newRefreshToken, email)
                log.info("SmartCommit Cloud: refresh succeeded, new tokens saved")
                newAccessToken
            }
        } catch (e: Exception) {
            log.warn("SmartCommit Cloud: refresh network error — ${e.message}")
            // Network error during refresh — don't clear tokens, let user retry
            null
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    private fun credentialAttributes(key: String): CredentialAttributes {
        return CredentialAttributes(generateServiceName(SERVICE_NAME, key))
    }
}
