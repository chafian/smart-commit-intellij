package com.smartcommit.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vcs.VcsDataKeys
import com.smartcommit.history.CommitMessageHistory
import com.smartcommit.util.NotificationUtils

/**
 * Action that shows a popup list of previously generated commit messages.
 * The user can select one to insert it into the commit message field.
 *
 * Registered in plugin.xml under Vcs.MessageActionGroup.
 * Shortcut: Alt+H
 *
 * Uses [AllIcons.Actions.SearchWithHistory] — a magnifier with history indicator,
 * visually distinct from IntelliJ's built-in "Commit Message History" (plain clock).
 */
class ShowCommitHistoryAction : AnAction(
    "Smart Commit History",
    "Show previously generated commit messages",
    AllIcons.Actions.SearchWithHistory
) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val commitMessageControl = e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL)
        val commitMessageDocument = e.getData(VcsDataKeys.COMMIT_MESSAGE_DOCUMENT)

        if (commitMessageControl == null && commitMessageDocument == null) {
            NotificationUtils.warning(project, "Smart Commit", "Cannot access commit message field.")
            return
        }

        val history = CommitMessageHistory.instance()
        val messages = history.getAll()

        if (messages.isEmpty()) {
            NotificationUtils.info(project, "Smart Commit", "No commit message history yet.")
            return
        }

        // Build display list: show first line of each message, truncated
        val displayItems = messages.map { msg ->
            val firstLine = msg.lines().first().take(80)
            if (msg.lines().size > 1) "$firstLine ..." else firstLine
        }

        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(displayItems)
            .setTitle("Smart Commit History")
            .setItemChosenCallback { selected ->
                val index = displayItems.indexOf(selected)
                if (index >= 0) {
                    val fullMessage = messages[index]
                    if (commitMessageControl != null) {
                        commitMessageControl.setCommitMessage(fullMessage)
                    } else if (commitMessageDocument != null) {
                        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
                            commitMessageDocument.setText(fullMessage)
                        }
                    }
                }
            }
            .createPopup()
            .showInBestPositionFor(e.dataContext)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isVisible = true
        val history = CommitMessageHistory.instance()
        e.presentation.isEnabled = history.size() > 0
    }
}
