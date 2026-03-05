package com.smartcommit.util

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

/**
 * Dialog and notification helpers for Smart Commit Cloud.
 *
 * These are the monetization-critical UI elements:
 * - Upgrade modal when quota is exhausted (premium look with clear pricing)
 * - Usage notification after each generation (drives awareness of scarcity)
 * - Subscription inactive modal
 * - Not connected error
 */
object CloudDialogs {

    private const val PRICING_URL = "https://smartcommit.dev/pricing"
    private const val ACCOUNT_URL = "https://smartcommit.dev/account"

    /**
     * Show the upgrade modal when monthly limit is reached.
     *
     * This is THE critical monetization moment. The user has hit their limit
     * and is actively trying to generate — maximum intent to convert.
     *
     * Premium look with clear pricing inside the dialog to maximize conversion.
     *
     * @param used  Number of generations used this month
     * @param limit Monthly generation limit for the user's plan
     * @param resetAt ISO date string of when the quota resets
     */
    fun showLimitExhaustedDialog(project: Project?, used: Int, limit: Int, resetAt: String) {
        val resetDate = formatResetDate(resetAt)

        // Determine current plan name for context
        val currentPlan = when (limit) {
            300 -> "Starter"
            3000 -> "Pro"
            else -> "Free"
        }

        val message = buildString {
            appendLine("You've used $used/$limit $currentPlan plan generations this month.")
            appendLine()
            if (currentPlan == "Free") {
                appendLine("Upgrade to keep generating instantly:")
                appendLine()
                appendLine("  Starter \u2014 \$1/month \u2192 300 generations")
                appendLine("  Pro     \u2014 \$5/month \u2192 3,000 generations")
            } else if (currentPlan == "Starter") {
                appendLine("Upgrade to Pro for more generations:")
                appendLine()
                appendLine("  Pro \u2014 \$5/month \u2192 3,000 generations")
            } else {
                appendLine("You've reached the maximum plan limit.")
            }
            appendLine()
            append("Your quota resets on $resetDate.")
        }

        val buttons = when (currentPlan) {
            "Free" -> arrayOf("Upgrade to Starter \u2013 \$1/month", "Upgrade to Pro \u2013 \$5/month", "Cancel")
            "Starter" -> arrayOf("Upgrade to Pro \u2013 \$5/month", "Cancel")
            else -> arrayOf("Manage Account", "Cancel")
        }

        val result = Messages.showDialog(
            project,
            message,
            "Monthly Limit Reached",
            buttons,
            0, // default button: first upgrade option
            Messages.getWarningIcon()
        )

        // Any non-Cancel button opens the pricing/account page
        val cancelIndex = buttons.size - 1
        if (result in 0 until cancelIndex) {
            BrowserUtil.browse(PRICING_URL)
        }
    }

    /**
     * Show modal when subscription is inactive (canceled/expired, not past-due grace).
     */
    fun showSubscriptionInactiveDialog(project: Project?) {
        val result = Messages.showDialog(
            project,
            "Your Smart Commit Cloud subscription is no longer active.\n\n" +
                "Reactivate your subscription to continue using Cloud AI,\n" +
                "or switch to OpenAI / Ollama in Settings > Tools > Smart Commit.",
            "Subscription Inactive",
            arrayOf("Manage Account", "Cancel"),
            0,
            Messages.getWarningIcon()
        )

        if (result == 0) {
            BrowserUtil.browse(ACCOUNT_URL)
        }
    }

    /**
     * Show error dialog when user tries Cloud generation without connecting.
     * Offers to open settings for reconnection.
     */
    fun showNotConnectedError(project: Project?) {
        val result = Messages.showDialog(
            project,
            "Your Smart Commit Cloud session has expired or is not connected.\n\n" +
                "Open Settings to reconnect your IDE, or switch to a different AI provider.",
            "Smart Commit Cloud — Not Connected",
            arrayOf("Open Settings", "Cancel"),
            0,
            Messages.getWarningIcon()
        )

        if (result == 0) {
            com.intellij.openapi.options.ShowSettingsUtil.getInstance()
                .showSettingsDialog(project, "Smart Commit")
        }
    }

    /**
     * Show rate limit error notification.
     */
    fun showRateLimitError(project: Project?) {
        NotificationUtils.warning(
            project,
            "Smart Commit Cloud",
            "Too many requests. Please wait a moment before trying again."
        )
    }

    /**
     * Show usage notification after a successful Cloud generation.
     *
     * For free users: includes a subtle upgrade nudge.
     *   "Free plan — 12/30 used this month. Upgrade for 300/month →"
     *
     * For paid users: clean usage indicator.
     *   "Starter plan — 42/300 this month"
     *
     * This is the subtle-but-powerful awareness driver.
     * Users who see their quota ticking down are more likely to upgrade.
     */
    fun showUsageNotification(project: Project?, plan: String, used: Int, limit: Int) {
        val planDisplay = when (plan) {
            "STARTER" -> "Starter"
            "PRO" -> "Pro"
            else -> "Free"
        }

        val content = if (plan == "FREE" || plan.isEmpty()) {
            "$planDisplay plan \u2014 $used/$limit used this month. Upgrade for 300/month \u2192"
        } else {
            "$planDisplay plan \u2014 $used/$limit this month"
        }

        NotificationUtils.info(
            project,
            "Smart Commit Cloud",
            content
        )
    }

    /**
     * Format an ISO date string into a user-friendly date.
     * Falls back to the raw string if parsing fails.
     */
    private fun formatResetDate(resetAt: String): String {
        return try {
            val instant = java.time.Instant.parse(resetAt)
            val date = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            date.format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy"))
        } catch (_: Exception) {
            resetAt.substringBefore("T") // fallback: "2026-03-01"
        }
    }
}
