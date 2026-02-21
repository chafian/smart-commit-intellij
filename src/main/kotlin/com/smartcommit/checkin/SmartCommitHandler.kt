package com.smartcommit.checkin

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.smartcommit.settings.SmartCommitSettings
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

                    // Apply message on EDT
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
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
