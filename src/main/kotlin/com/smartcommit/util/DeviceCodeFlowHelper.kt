package com.smartcommit.util

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.smartcommit.ai.CloudAuthManager
import com.smartcommit.settings.SmartCommitSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Shared device-code "Connect IDE" flow.
 *
 * Extracted from [SmartCommitConfigurable] and [FirstRunStartupActivity]
 * so it can be started from:
 * - Welcome dialog (first run)
 * - Settings panel (Connect IDE button)
 * - "Not connected" dialog (when trying to generate without connection)
 * - Cloud hint notification action
 *
 * Flow:
 * 1. POST /api/ide/auth/start → deviceCode + userCode + verificationUrl
 * 2. Open browser at verificationUrl
 * 3. Poll POST /api/ide/auth/poll until approved/expired/rejected
 * 4. Save tokens → show success message
 */
object DeviceCodeFlowHelper {

    private val log = Logger.getInstance(DeviceCodeFlowHelper::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Start the device-code flow as a background task with progress indicator.
     *
     * @param project The current project (can be null)
     * @param onSuccess Optional callback invoked on EDT after successful connection.
     *                  Receives the connected email (or null if not available).
     */
    fun start(project: Project?, onSuccess: ((email: String?) -> Unit)? = null) {
        val settings = SmartCommitSettings.instance()
        val baseUrl = settings.cloudBaseUrl.trimEnd('/')

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, "Smart Commit: Connecting to Cloud...", true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                indicator.text = "Starting device code flow..."

                try {
                    val startBody = """{"clientInfo":{"ideName":"IntelliJ IDEA","ideVersion":"2024.1","os":"${System.getProperty("os.name")}"}}"""
                    val startRequest = Request.Builder()
                        .url("$baseUrl/api/ide/auth/start")
                        .addHeader("Content-Type", "application/json")
                        .post(startBody.toRequestBody("application/json".toMediaType()))
                        .build()

                    val startResponse = httpClient.newCall(startRequest).execute()
                    if (!startResponse.isSuccessful) {
                        showError("Failed to start connection: ${startResponse.code}")
                        return
                    }

                    val startJson = json.parseToJsonElement(
                        startResponse.body?.string() ?: return
                    ).jsonObject

                    val deviceCode = startJson["deviceCode"]?.jsonPrimitive?.content ?: return
                    val userCode = startJson["userCode"]?.jsonPrimitive?.content ?: return
                    val verificationUrl = startJson["verificationUrl"]?.jsonPrimitive?.content
                        ?: "https://smartcommit.dev/connect?code=$userCode"
                    val intervalSeconds = startJson["intervalSeconds"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3

                    BrowserUtil.browse(verificationUrl)
                    indicator.text = "Waiting for approval... Code: $userCode"

                    val maxAttempts = (15 * 60) / intervalSeconds
                    for (attempt in 1..maxAttempts) {
                        if (indicator.isCanceled) return

                        Thread.sleep(intervalSeconds * 1000)
                        indicator.fraction = attempt.toDouble() / maxAttempts

                        val pollBody = """{"deviceCode":"$deviceCode"}"""
                        val pollRequest = Request.Builder()
                            .url("$baseUrl/api/ide/auth/poll")
                            .addHeader("Content-Type", "application/json")
                            .post(pollBody.toRequestBody("application/json".toMediaType()))
                            .build()

                        val pollResponse = httpClient.newCall(pollRequest).execute()
                        if (!pollResponse.isSuccessful) continue

                        val pollJson = json.parseToJsonElement(
                            pollResponse.body?.string() ?: continue
                        ).jsonObject

                        val status = pollJson["status"]?.jsonPrimitive?.content ?: continue

                        when (status) {
                            "approved" -> {
                                val tokens = pollJson["tokens"]?.jsonObject ?: return
                                val accessToken = tokens["accessToken"]?.jsonPrimitive?.content ?: return
                                val refreshToken = tokens["refreshToken"]?.jsonPrimitive?.content ?: return
                                val accountObj = pollJson["account"]?.jsonObject
                                val email = accountObj?.get("email")?.jsonPrimitive?.content

                                CloudAuthManager.saveTokens(accessToken, refreshToken, email)

                                ApplicationManager.getApplication().invokeLater {
                                    Messages.showInfoMessage(
                                        "Successfully connected to Smart Commit Cloud" +
                                            (if (email != null) " as $email" else "") + ".\n\n" +
                                            "You're all set! Press Alt+G in any commit dialog to generate messages.",
                                        "Smart Commit"
                                    )
                                    onSuccess?.invoke(email)
                                }
                                return
                            }
                            "expired" -> {
                                showError("Connection code expired. Please try again.")
                                return
                            }
                            "rejected" -> {
                                val reason = pollJson["reason"]?.jsonPrimitive?.content
                                if (reason == "device_limit") {
                                    showError("Device limit reached. Go to smartcommit.dev/account to manage your connected devices.")
                                } else {
                                    showError("Connection rejected: ${reason ?: "unknown reason"}")
                                }
                                return
                            }
                        }
                    }

                    showError("Connection timed out. Please try again.")

                } catch (e: Exception) {
                    log.warn("Device-code flow failed", e)
                    showError("Connection failed: ${e.message}")
                }
            }

            private fun showError(message: String) {
                ApplicationManager.getApplication().invokeLater {
                    Messages.showErrorDialog(message, "Smart Commit Cloud")
                }
            }
        })
    }
}
