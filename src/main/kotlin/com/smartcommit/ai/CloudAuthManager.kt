package com.smartcommit.ai

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Manages Smart Commit Cloud authentication tokens.
 *
 * Tokens are stored in IntelliJ PasswordSafe (system credential store):
 * - Access token: JWT, 24h lifetime
 * - Refresh token: opaque, 30d, rotating
 *
 * Token refresh strategy: try the request normally, if 401 call [refreshTokens]
 * and retry once. No JWT expiry pre-checking.
 */
object CloudAuthManager {

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

    fun isConnected(): Boolean = getAccessToken() != null

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
        val refreshToken = getRefreshToken() ?: return null

        return try {
            val body = """{"refreshToken":"$refreshToken"}"""
            val request = Request.Builder()
                .url("$baseUrl/api/auth/refresh")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
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
                newAccessToken
            }
        } catch (_: Exception) {
            // Network error during refresh — don't clear tokens, let user retry
            null
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    private fun credentialAttributes(key: String): CredentialAttributes {
        return CredentialAttributes(generateServiceName(SERVICE_NAME, key))
    }
}
