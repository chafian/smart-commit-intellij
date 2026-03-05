package com.smartcommit.onboarding

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.smartcommit.ai.CloudAuthManager
import com.smartcommit.settings.AiProviderType
import com.smartcommit.settings.SmartCommitConfigurable
import com.smartcommit.settings.SmartCommitSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.prefs.Preferences

/**
 * Shows the welcome dialog once when the plugin is first installed.
 *
 * Uses Java Preferences API (not IntelliJ @State) to persist the flag,
 * since @State requires the settings panel to have been loaded at least once.
 *
 * The dialog is shown on the EDT after project opens, with a short delay
 * so the IDE has time to finish rendering.
 */
class FirstRunStartupActivity : ProjectActivity {

    private val log = Logger.getInstance(FirstRunStartupActivity::class.java)

    companion object {
        private const val PREF_KEY = "smartcommit.firstrun.shown"
        private val prefs = Preferences.userNodeForPackage(FirstRunStartupActivity::class.java)
    }

    override suspend fun execute(project: Project) {
        // Skip if already shown
        if (prefs.getBoolean(PREF_KEY, false)) return

        // Skip if user is already connected to Cloud
        if (CloudAuthManager.isConnected()) {
            prefs.putBoolean(PREF_KEY, true)
            return
        }

        // Show dialog on EDT after a short delay
        ApplicationManager.getApplication().invokeLater {
            showWelcomeDialog(project)
        }
    }

    private fun showWelcomeDialog(project: Project) {
        val dialog = WelcomeDialog()
        dialog.show()

        // Mark as shown regardless of choice
        prefs.putBoolean(PREF_KEY, true)

        when (dialog.exitCode) {
            DialogWrapper.OK_EXIT_CODE -> {
                // "Connect IDE" — start device code flow
                startDeviceCodeFlow(project)
            }
            WelcomeDialog.USE_OPENAI_EXIT_CODE -> {
                // "Use OpenAI instead" — switch provider and open settings
                val settings = SmartCommitSettings.instance()
                settings.aiProvider = AiProviderType.OPENAI
                ShowSettingsUtil.getInstance().showSettingsDialog(project, SmartCommitConfigurable::class.java)
            }
            // CANCEL / ESC — do nothing, Cloud is already the default
        }
    }

    // ── Device-code flow (same logic as SmartCommitConfigurable) ──

    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun startDeviceCodeFlow(project: Project) {
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
                                }
                                return
                            }
                            "expired" -> {
                                showError("Connection code expired. You can try again from Settings > Tools > Smart Commit.")
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

                    showError("Connection timed out. You can try again from Settings > Tools > Smart Commit.")

                } catch (e: Exception) {
                    log.warn("Welcome dialog device-code flow failed", e)
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
