package com.smartcommit.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.BrowserUtil
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.ui.dsl.builder.*
import com.smartcommit.ai.CloudAuthManager
import com.smartcommit.checkin.CommitMessageService
import com.smartcommit.convention.ConventionType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.awt.Font
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Settings page for Smart Commit, registered under `Settings > Tools > Smart Commit`.
 *
 * Cloud is the hero: it's shown first, is the default, and visually dominates.
 * OpenAI / Ollama sections are shown only when the respective provider is selected.
 *
 * When connected, fetches live usage from `GET /api/account/me` to display
 * plan, usage count, and reset info inside the settings panel.
 */
class SmartCommitConfigurable : BoundConfigurable("Smart Commit") {

    private val settings = SmartCommitSettings.instance()
    private val log = Logger.getInstance(SmartCommitConfigurable::class.java)

    // Local copy of the API key for the form (loaded from PasswordSafe)
    private var openAiApiKey: String = loadApiKeySafe()

    // ── Mutable UI references ──────────────────────────────
    private var statusLabel: JLabel? = null
    private var planLabel: JLabel? = null
    private var usageLabel: JLabel? = null
    private var connectButton: JButton? = null
    private var manageAccountButton: JButton? = null
    private var signOutButton: JButton? = null
    private var notConnectedHintLabel: JLabel? = null
    private var pricingLinkLabel: JLabel? = null

    // OpenAI / Ollama panels for dynamic show/hide
    private var openAiWrapperPanel: JPanel? = null
    private var ollamaWrapperPanel: JPanel? = null

    override fun createPanel(): DialogPanel = panel {
        // ── General ─────────────────────────────────────────
        group("General") {
            row("Generator Mode:") {
                comboBox(GeneratorMode.entries.toList())
                    .bindItem(settings::generatorMode.toNullableProperty())
                    .comment("AI-Powered uses an LLM; Template-Based is deterministic and offline")
            }
            row("AI Provider:") {
                comboBox(AiProviderType.entries.toList())
                    .bindItem(settings::aiProvider.toNullableProperty())
                    .comment("Cloud is zero-config. OpenAI and Ollama require manual setup.")
                    .applyToComponent {
                        addActionListener { updateProviderSections() }
                    }
            }
            row("Convention:") {
                comboBox(ConventionType.entries.toList())
                    .bindItem(settings::convention.toNullableProperty())
                    .comment("Gitmoji adds emoji prefixes; Conventional Commits uses type(scope): format")
            }
            row("Commit Style:") {
                comboBox(CommitStyle.entries.toList())
                    .bindItem(settings::commitStyle.toNullableProperty())
                    .comment("One-Line produces only a title; Detailed includes title + body explanation")
            }
            row("Language:") {
                comboBox(CommitLanguage.entries.toList())
                    .bindItem(settings::commitLanguage.toNullableProperty())
                    .comment("Language for the generated commit message (AI mode only)")
            }
            row {
                checkBox("Auto-generate on commit dialog open")
                    .bindSelected(settings::autoGenerate)
            }
            row {
                checkBox("Confirm before overwriting existing message")
                    .bindSelected(settings::confirmOverwrite)
            }
        }

        // ── Smart Commit Cloud (always visible, hero section) ──
        group("Smart Commit Cloud") {
            // Tagline
            row {
                val tagline = JLabel("Secure. No API keys required. Team-ready.")
                tagline.font = tagline.font.deriveFont(Font.ITALIC)
                cell(tagline)
            }
            // Trust text
            row {
                cell(JLabel("\u2022 Diffs are processed securely. No code is stored."))
            }
            // Status — colored HTML dot + text
            row {
                val lbl = JLabel(buildStatusHtml())
                statusLabel = lbl
                cell(lbl)
            }
            // Plan (hidden until data loads)
            row {
                val pLbl = JLabel("")
                planLabel = pLbl
                pLbl.isVisible = false
                cell(pLbl)
            }
            // Usage (hidden until data loads)
            row {
                val uLbl = JLabel("")
                usageLabel = uLbl
                uLbl.isVisible = false
                cell(uLbl)
            }
            // Not-connected hint
            row {
                val hint = JLabel("Connect your IDE to enable Cloud AI. Free plan includes 30 generations/month.")
                notConnectedHintLabel = hint
                cell(hint)
            }
            // Pricing link
            row {
                val pricing = JLabel("<html><a href=''>View Pricing</a></html>")
                pricing.cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
                pricing.addMouseListener(object : java.awt.event.MouseAdapter() {
                    override fun mouseClicked(e: java.awt.event.MouseEvent?) {
                        BrowserUtil.browse("https://smartcommit.dev/pricing")
                    }
                })
                pricingLinkLabel = pricing
                cell(pricing)
            }
            // Buttons
            row {
                val connBtn = JButton("Connect IDE").apply {
                    addActionListener { startDeviceCodeFlow() }
                }
                connectButton = connBtn
                cell(connBtn)

                val manageBtn = JButton("Manage Account").apply {
                    addActionListener { BrowserUtil.browse("https://smartcommit.dev/account") }
                }
                manageAccountButton = manageBtn
                cell(manageBtn)

                val outBtn = JButton("Sign Out").apply {
                    addActionListener { handleSignOut() }
                }
                signOutButton = outBtn
                cell(outBtn)
            }
        }

        // ── OpenAI ──────────────────────────────────────────
        val openAiPanel = com.intellij.ui.dsl.builder.panel {
            group("OpenAI (Advanced \u2013 Bring your own API key)") {
                row("OpenAI Model (Advanced):") {
                    textField()
                        .bindText(settings::openAiModel)
                        .comment("e.g. gpt-4o-mini, gpt-4o, gpt-3.5-turbo")
                }
                row("API Key:") {
                    passwordField()
                        .bindText(::openAiApiKey)
                        .comment("Stored securely in system credential store (PasswordSafe)")
                        .resizableColumn()
                        .align(Align.FILL)
                }
            }
        }
        openAiWrapperPanel = openAiPanel
        row { cell(openAiPanel).align(Align.FILL) }

        // ── Ollama ──────────────────────────────────────────
        val ollamaPanel = com.intellij.ui.dsl.builder.panel {
            group("Ollama (Local \u2013 Offline)") {
                row("URL:") {
                    textField()
                        .bindText(settings::ollamaUrl)
                        .comment("e.g. http://localhost:11434")
                        .resizableColumn()
                        .align(Align.FILL)
                }
                row("Model:") {
                    textField()
                        .bindText(settings::ollamaModel)
                        .comment("e.g. llama3, codellama, mistral")
                }
            }
        }
        ollamaWrapperPanel = ollamaPanel
        row { cell(ollamaPanel).align(Align.FILL) }

        // ── Advanced Customization ──────────────────────────
        group("Advanced Customization") {
            row {
                comment("Leave blank to use defaults. Custom system prompt is appended to the built-in prompt (AI mode).")
            }
            row("Custom System Prompt:") {
                textArea()
                    .bindText(settings::customSystemPrompt)
                    .rows(4)
                    .comment("Extra instructions appended to the AI system prompt. E.g. 'Always mention the ticket number.'")
                    .resizableColumn()
                    .align(Align.FILL)
            }
            row("Custom Title Template:") {
                textField()
                    .bindText(settings::customTitleTemplate)
                    .comment("Template mode: override title template. Variables: {{type}}, {{scope}}, {{summary}}, etc.")
                    .resizableColumn()
                    .align(Align.FILL)
            }
            row("Custom Body Template:") {
                textArea()
                    .bindText(settings::customBodyTemplate)
                    .rows(3)
                    .comment("Template mode: override body template. Variables: {{files_changed}}, {{body_lines}}, etc.")
                    .resizableColumn()
                    .align(Align.FILL)
            }
        }

        // ── Generation Limits ───────────────────────────────
        group("Generation Limits") {
            row("Max Subject Length:") {
                spinner(30..120, 1)
                    .bindIntValue(settings::maxSubjectLength)
                    .comment("Maximum characters for the commit title line")
            }
            row("Max Diff Tokens:") {
                spinner(500..16000, 500)
                    .bindIntValue(settings::maxDiffTokens)
                    .comment("Maximum tokens of diff content sent to AI provider")
            }
        }

        // Initial UI state
        refreshCloudSection()
        updateProviderSections()
    }

    override fun apply() {
        super.apply()
        saveApiKey(openAiApiKey)
    }

    override fun reset() {
        super.reset()
        openAiApiKey = loadApiKeySafe()
        refreshCloudSection()
        updateProviderSections()
    }

    // ── Provider section visibility ─────────────────────────

    private fun updateProviderSections() {
        val provider = settings.aiProvider
        openAiWrapperPanel?.isVisible = (provider == AiProviderType.OPENAI)
        ollamaWrapperPanel?.isVisible = (provider == AiProviderType.OLLAMA)
    }

    // ── Cloud section logic ─────────────────────────────────

    /**
     * Full refresh: set button/label visibility from PasswordSafe state,
     * then kick off a background fetch for live usage data.
     */
    private fun refreshCloudSection() {
        val connected = isConnectedSafe()
        val email = getEmailSafe()

        log.info("SmartCommit: refreshCloudSection connected=$connected email=$email")

        // Status label
        statusLabel?.text = buildStatusHtml(connected, email)

        // Buttons
        connectButton?.isVisible = !connected
        manageAccountButton?.isVisible = connected
        signOutButton?.isVisible = connected

        // Hint
        notConnectedHintLabel?.isVisible = !connected

        // Pricing always visible
        pricingLinkLabel?.isVisible = true

        if (connected) {
            // Try cached usage first
            val cached = CommitMessageService.lastCloudUsage
            if (cached != null) {
                showUsageData(cached.plan, cached.used, cached.limit, cached.resetAt)
            } else {
                planLabel?.isVisible = false
                usageLabel?.isVisible = false
            }
            // Fetch live data
            fetchAccountInfoInBackground()
        } else {
            planLabel?.isVisible = false
            usageLabel?.isVisible = false
        }
    }

    /**
     * Build the status line HTML.
     * Connected:     🟢 Connected as: user@example.com
     * Not connected: 🔴 Not Connected
     */
    private fun buildStatusHtml(
        connected: Boolean = isConnectedSafe(),
        email: String? = getEmailSafe()
    ): String {
        return if (connected) {
            val emailText = if (!email.isNullOrBlank()) " as: $email" else ""
            "<html><font color='#22c55e'>\u25CF</font> <b>Connected${emailText}</b></html>"
        } else {
            "<html><font color='#ef4444'>\u25CF</font> <b>Not Connected</b></html>"
        }
    }

    /**
     * Show plan + usage labels.
     */
    private fun showUsageData(plan: String, used: Int, limit: Int, resetAt: String) {
        val planDisplay = when (plan.uppercase()) {
            "STARTER" -> "Starter"
            "PRO" -> "Pro"
            else -> "Free"
        }
        planLabel?.text = "Plan: $planDisplay"
        planLabel?.isVisible = true

        val resetText = formatResetDays(resetAt)
        usageLabel?.text = "Usage: $used / $limit $resetText"
        usageLabel?.isVisible = true
    }

    private fun formatResetDays(resetAt: String): String {
        if (resetAt.isBlank()) return ""
        return try {
            val resetInstant = Instant.parse(resetAt)
            val days = ChronoUnit.DAYS.between(Instant.now(), resetInstant)
            when {
                days <= 0 -> "(Resets today)"
                days == 1L -> "(Resets in 1 day)"
                else -> "(Resets in $days days)"
            }
        } catch (_: Exception) {
            ""
        }
    }

    // ── Background fetch ────────────────────────────────────

    /**
     * Fetch account info from `GET /api/account/me` on a pooled thread.
     * Updates status, plan, and usage labels on the EDT when done.
     */
    private fun fetchAccountInfoInBackground() {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val baseUrl = settings.cloudBaseUrl.trimEnd('/')
                var accessToken = CloudAuthManager.getAccessToken()
                if (accessToken == null) {
                    log.warn("SmartCommit: fetchAccountInfo — no access token")
                    return@executeOnPooledThread
                }

                var response = callAccountMe(baseUrl, accessToken)

                // Handle 401 — refresh and retry
                if (response.first == 401) {
                    log.info("SmartCommit: fetchAccountInfo — 401, refreshing token")
                    val newToken = CloudAuthManager.refreshTokens(baseUrl)
                    if (newToken == null) {
                        log.warn("SmartCommit: fetchAccountInfo — refresh failed")
                        return@executeOnPooledThread
                    }
                    accessToken = newToken
                    response = callAccountMe(baseUrl, accessToken)
                }

                val (code, body) = response
                if (code != 200 || body == null) {
                    log.warn("SmartCommit: fetchAccountInfo — HTTP $code")
                    return@executeOnPooledThread
                }

                log.info("SmartCommit: fetchAccountInfo — success, parsing response")
                val root = cloudJson.parseToJsonElement(body).jsonObject
                val plan = root["plan"]?.jsonPrimitive?.content ?: "FREE"
                val email = root["email"]?.jsonPrimitive?.content
                val usage = root["usage"]?.jsonObject
                val used = usage?.get("used")?.jsonPrimitive?.int ?: 0
                val limit = usage?.get("limit")?.jsonPrimitive?.int ?: 0
                val resetAt = usage?.get("resetAt")?.jsonPrimitive?.content ?: ""

                log.info("SmartCommit: fetchAccountInfo — plan=$plan email=$email used=$used limit=$limit")

                ApplicationManager.getApplication().invokeLater {
                    // Update status with server email
                    statusLabel?.text = buildStatusHtml(true, email ?: getEmailSafe())
                    showUsageData(plan, used, limit, resetAt)
                }

            } catch (e: Exception) {
                log.warn("SmartCommit: fetchAccountInfo failed", e)
            }
        }
    }

    /**
     * HTTP call to GET /api/account/me.
     * @return Pair(httpCode, responseBody)
     */
    private fun callAccountMe(baseUrl: String, accessToken: String): Pair<Int, String?> {
        val request = Request.Builder()
            .url("$baseUrl/api/account/me")
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        return cloudHttpClient.newCall(request).execute().use { resp ->
            Pair(resp.code, resp.body?.string())
        }
    }

    // ── Actions ─────────────────────────────────────────────

    private fun handleSignOut() {
        CloudAuthManager.clearTokens()
        refreshCloudSection()
        Messages.showInfoMessage(
            "Signed out from Smart Commit Cloud.",
            "Smart Commit"
        )
    }

    // ── PasswordSafe thread-safe wrappers ───────────────────

    private fun getEmailSafe(): String? {
        return try {
            com.intellij.openapi.application.ReadAction.compute<String?, Throwable> {
                CloudAuthManager.getUserEmail()
            }
        } catch (_: Exception) {
            CloudAuthManager.getUserEmail()
        }
    }

    private fun isConnectedSafe(): Boolean {
        return try {
            com.intellij.openapi.application.ReadAction.compute<Boolean, Throwable> {
                CloudAuthManager.isConnected()
            }
        } catch (_: Exception) {
            CloudAuthManager.isConnected()
        }
    }

    private fun loadApiKeySafe(): String {
        return try {
            com.intellij.openapi.application.ReadAction.compute<String, Throwable> {
                loadApiKey()
            }
        } catch (_: Exception) {
            loadApiKey()
        }
    }

    // ── Cloud device-code flow ─────────────────────────────

    private val cloudJson = Json { ignoreUnknownKeys = true }
    private val cloudHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Start the device-code "Connect IDE" flow:
     * 1. POST /api/ide/auth/start -> deviceCode + userCode + verificationUrl
     * 2. Open browser
     * 3. Poll until approved/expired
     * 4. Save tokens -> refresh UI + fetch usage
     */
    private fun startDeviceCodeFlow() {
        val baseUrl = settings.cloudBaseUrl.trimEnd('/')

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            null, "Smart Commit: Connecting to Cloud...", true
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

                    val startResponse = cloudHttpClient.newCall(startRequest).execute()
                    if (!startResponse.isSuccessful) {
                        showError("Failed to start connection: ${startResponse.code}")
                        return
                    }

                    val startJson = cloudJson.parseToJsonElement(
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

                        val pollResponse = cloudHttpClient.newCall(pollRequest).execute()
                        if (!pollResponse.isSuccessful) continue

                        val pollJson = cloudJson.parseToJsonElement(
                            pollResponse.body?.string() ?: continue
                        ).jsonObject

                        val status = pollJson["status"]?.jsonPrimitive?.content ?: continue

                        when (status) {
                            "approved" -> {
                                val tokens = pollJson["tokens"]?.jsonObject ?: return
                                val accessToken = tokens["accessToken"]?.jsonPrimitive?.content ?: return
                                val refreshToken = tokens["refreshToken"]?.jsonPrimitive?.content ?: return
                                val email = pollJson["account"]?.jsonObject
                                    ?.get("email")?.jsonPrimitive?.content

                                CloudAuthManager.saveTokens(accessToken, refreshToken, email)

                                ApplicationManager.getApplication().invokeLater {
                                    refreshCloudSection()
                                    Messages.showInfoMessage(
                                        "Successfully connected to Smart Commit Cloud" +
                                            (if (email != null) " as $email" else "") + ".",
                                        "Smart Commit"
                                    )
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

    // ── PasswordSafe helpers ────────────────────────────────

    companion object {
        private const val SERVICE_NAME = "SmartCommit"
        private const val KEY_OPENAI_API_KEY = "openai-api-key"

        private fun credentialAttributes(): CredentialAttributes {
            return CredentialAttributes(
                generateServiceName(SERVICE_NAME, KEY_OPENAI_API_KEY)
            )
        }

        fun loadApiKey(): String {
            return PasswordSafe.instance.getPassword(credentialAttributes()).orEmpty()
        }

        fun saveApiKey(apiKey: String) {
            PasswordSafe.instance.setPassword(credentialAttributes(), apiKey.ifBlank { null })
        }
    }
}
