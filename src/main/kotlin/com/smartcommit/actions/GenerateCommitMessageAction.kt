package com.smartcommit.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.smartcommit.checkin.CommitMessageService
import com.smartcommit.settings.SmartCommitSettings
import com.smartcommit.util.NotificationUtils

/**
 * Action that generates a commit message from selected changes.
 *
 * Registered in `plugin.xml` with:
 * - Group: `Vcs.MessageActionGroup` (commit message toolbar)
 * - Shortcut: `Alt+G`
 * - Icon: Lightning bolt
 *
 * This action is available in the commit dialog and can be triggered
 * manually via the toolbar button or keyboard shortcut.
 */
class GenerateCommitMessageAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Try COMMIT_MESSAGE_CONTROL first; fall back to setting via COMMIT_MESSAGE_DOCUMENT
        val commitMessageControl = e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL)
        val commitMessageDocument = e.getData(VcsDataKeys.COMMIT_MESSAGE_DOCUMENT)

        if (commitMessageControl == null && commitMessageDocument == null) {
            NotificationUtils.warning(project, "Smart Commit", "Cannot access commit message field.")
            return
        }

        // Get changes: try data context first, fall back to default changelist
        val changes: List<Change> = getChanges(e, project)

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

                try {
                    val message = CommitMessageService.generate(project, changes, indicator)

                    // Apply message on EDT
                    ApplicationManager.getApplication().invokeLater {
                        val settings = SmartCommitSettings.instance()

                        // Check existing message via Document if available
                        val currentText = commitMessageDocument?.text?.trim().orEmpty()
                        if (currentText.isNotEmpty() && settings.confirmOverwrite) {
                            val result = Messages.showYesNoDialog(
                                project,
                                "A commit message already exists. Do you want to replace it?",
                                "Smart Commit",
                                Messages.getQuestionIcon()
                            )
                            if (result != Messages.YES) return@invokeLater
                        }

                        if (commitMessageControl != null) {
                            commitMessageControl.setCommitMessage(message.format())
                        } else if (commitMessageDocument != null) {
                            // Fallback: write directly into the Document
                            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
                                commitMessageDocument.setText(message.format())
                            }
                        }
                        NotificationUtils.info(project, "Smart Commit", "Commit message generated successfully.")
                    }
                } catch (ex: Exception) {
                    NotificationUtils.error(
                        project,
                        "Smart Commit Error",
                        ex.message ?: "Unknown error during message generation"
                    )
                }
            }
        })
    }

    override fun update(e: AnActionEvent) {
        // Always visible when placed in the commit message toolbar group.
        // Enable whenever we can find changes (data context or default changelist).
        e.presentation.isVisible = true
        val project = e.project
        if (project == null) {
            e.presentation.isEnabled = false
            return
        }
        val changes = getChanges(e, project)
        e.presentation.isEnabled = changes.isNotEmpty()
    }

    /**
     * Resolves the list of changes to generate a message for.
     *
     * Strategy:
     * 1. Try [VcsDataKeys.CHANGES] from the action data context (works in modal commit dialog).
     * 2. Fall back to the default changelist from [ChangeListManager] (works in non-modal commit tool window).
     */
    private fun getChanges(e: AnActionEvent, project: Project): List<Change> {
        // 1. Data context — available in modal commit dialog
        val contextChanges = e.getData(VcsDataKeys.CHANGES)
        if (contextChanges != null && contextChanges.isNotEmpty()) {
            return contextChanges.toList()
        }

        // 2. Fallback — default changelist from ChangeListManager
        val clm = ChangeListManager.getInstance(project)
        val defaultList = clm.defaultChangeList
        return defaultList.changes.toList()
    }
}
