package com.smartcommit.checkin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.smartcommit.settings.AiProviderType
import com.smartcommit.settings.GeneratorMode
import com.smartcommit.settings.SmartCommitSettings
import com.smartcommit.util.CloudDialogs
import com.smartcommit.util.DeviceCodeFlowHelper
import com.smartcommit.util.NotificationUtils

/**
 * Hooks into the commit workflow to optionally auto-generate commit messages.
 *
 * Created by [SmartCommitHandlerFactory] for each commit dialog session.
 * If "auto-generate" is enabled in settings, it triggers message generation
 * when the included changes list changes.
 *
 * @param panel The commit panel, providing access to selected changes and the message field.
 */
class SmartCommitHandler(
    private val panel: CheckinProjectPanel
) : CheckinHandler() {

    /**
     * Called when the set of included changes is updated.
     * If auto-generate is enabled, triggers commit message generation.
     */
    override fun includedChangesChanged() {
        val settings = SmartCommitSettings.instance()
        if (!settings.autoGenerate) return

        val changes = panel.selectedChanges
        if (changes.isEmpty()) return

        // Don't overwrite if user already typed something
        val existingMessage = panel.commitMessage.orEmpty().trim()
        if (existingMessage.isNotEmpty() && settings.confirmOverwrite) return

        generateInBackground()
    }

    /**
     * Generate a commit message in the background and set it on the panel.
     * This is also called by [GenerateCommitMessageAction] for manual trigger.
     */
    fun generateInBackground() {
        val project = panel.project
        val changes = panel.selectedChanges

        if (changes.isEmpty()) {
            NotificationUtils.warning(project, "Smart Commit", "No changes selected for commit.")
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Smart Commit: Generating message...",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Analyzing changes..."

                try {
                    val message = CommitMessageService.generate(project, changes.toList(), indicator)
                    val cloudUsage = CommitMessageService.lastCloudUsage

                    // Apply message on EDT
                    ApplicationManager.getApplication().invokeLater {
                        val existingMessage = panel.commitMessage.orEmpty().trim()
                        val settings = SmartCommitSettings.instance()

                        if (existingMessage.isNotEmpty() && settings.confirmOverwrite) {
                            val result = Messages.showYesNoDialog(
                                project,
                                "A commit message already exists. Do you want to replace it?",
                                "Smart Commit",
                                Messages.getQuestionIcon()
                            )
                            if (result != Messages.YES) return@invokeLater
                        }

                        panel.setCommitMessage(message.format())

                        // Show Cloud usage notification after successful generation
                        if (cloudUsage != null) {
                            CloudDialogs.showUsageNotification(
                                project,
                                cloudUsage.plan,
                                cloudUsage.used,
                                cloudUsage.limit
                            )
                        } else {
                            // Nudge non-Cloud users toward Cloud
                            CloudDialogs.showCloudHintIfNeeded(project)
                        }
                    }
                } catch (e: CloudUsageException) {
                    // Show appropriate Cloud dialog on EDT
                    ApplicationManager.getApplication().invokeLater {
                        when (e.reason) {
                            CloudUsageException.Reason.LIMIT_EXHAUSTED ->
                                CloudDialogs.showLimitExhaustedDialog(project, e.used, e.limit, e.resetAt)
                            CloudUsageException.Reason.SUBSCRIPTION_INACTIVE ->
                                CloudDialogs.showSubscriptionInactiveDialog(project)
                            CloudUsageException.Reason.RATE_LIMITED ->
                                CloudDialogs.showRateLimitError(project)
                        }
                    }
                } catch (e: CloudNotConnectedException) {
                    ApplicationManager.getApplication().invokeLater {
                        when (CloudDialogs.showNotConnectedDialog(project)) {
                            CloudDialogs.NotConnectedAction.CONNECT_IDE -> {
                                DeviceCodeFlowHelper.start(project)
                            }
                            CloudDialogs.NotConnectedAction.SWITCH_OPENAI -> {
                                SmartCommitSettings.instance().aiProvider = AiProviderType.OPENAI
                                NotificationUtils.info(project, "Smart Commit", "Switched to OpenAI. Set your API key in Settings > Tools > Smart Commit.")
                            }
                            CloudDialogs.NotConnectedAction.CANCEL -> { /* do nothing */ }
                        }
                    }
                } catch (e: Exception) {
                    NotificationUtils.error(
                        project,
                        "Smart Commit Error",
                        e.message ?: "Unknown error during message generation"
                    )
                }
            }
        })
    }
}
