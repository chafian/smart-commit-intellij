package com.smartcommit.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import com.smartcommit.history.CommitMessageHistory
import com.smartcommit.util.NotificationUtils
import java.awt.Point
import javax.swing.ListSelectionModel

/**
 * Action that shows a popup list of previously generated commit messages,
 * matching IntelliJ's built-in "Commit Message History" positioning and behavior:
 *
 * - Same clock icon (Vcs.History)
 * - Popup positioned directly above the commit message editor field
 * - Hovering/navigating items previews the full message in the commit text area
 * - Clicking or pressing Enter confirms the selection
 * - Cancelling (Esc) restores the original message
 *
 * Registered in plugin.xml under Vcs.MessageActionGroup.
 * Shortcut: Alt+H
 */
class ShowCommitHistoryAction : AnAction(
    "Smart Commit History",
    "Show previously generated commit messages",
    AllIcons.Nodes.PpLib
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

        // Cast to CommitMessage (JPanel) to access editorField for positioning — same as IntelliJ's built-in action
        val commitMessage = commitMessageControl as? CommitMessage

        val history = CommitMessageHistory.instance()
        val messages = history.getAll()

        if (messages.isEmpty()) {
            NotificationUtils.info(project, "Smart Commit", "No commit message history yet.")
            return
        }

        // Save the original message so we can restore on cancel
        val originalMessage: String = when {
            commitMessageDocument != null -> commitMessageDocument.text
            else -> ""
        }

        // Build display items: first line, truncated to 80 chars
        val displayItems = messages.map { msg ->
            val firstLine = msg.lines().first().take(80)
            if (msg.lines().size > 1) "$firstLine ..." else firstLine
        }

        // Track whether user confirmed a selection
        var confirmed = false

        // Build the popup — matches IntelliJ's built-in ShowMessageHistoryAction pattern
        val popupBuilder = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(displayItems)
            .setTitle("Smart Commit History")
            .setVisibleRowCount(7)
            .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            .setRenderer(SimpleListCellRenderer.create("") { it ?: "" })
            .setItemSelectedCallback { selected ->
                // Preview on keyboard navigation / hover
                if (selected != null) {
                    val idx = displayItems.indexOf(selected)
                    if (idx >= 0 && idx < messages.size) {
                        setMessage(commitMessageControl, commitMessageDocument, project, messages[idx])
                    }
                }
            }
            .setItemChosenCallback { selected ->
                // Confirm selection on click / Enter
                confirmed = true
                val idx = displayItems.indexOf(selected)
                if (idx >= 0 && idx < messages.size) {
                    setMessage(commitMessageControl, commitMessageDocument, project, messages[idx])
                }
            }

        // Add listeners: reposition above editor (beforeShown) + restore on cancel (onClosed)
        popupBuilder.addListener(object : JBPopupListener {
            override fun beforeShown(event: LightweightWindowEvent) {
                // Reposition popup directly above the editor field — same as IntelliJ's built-in history
                if (commitMessage != null) {
                    val popup = event.asPopup()
                    val relativePoint = RelativePoint(commitMessage.editorField, Point(0, -JBUI.scale(3)))
                    val screenPoint = Point(relativePoint.screenPoint).apply {
                        translate(0, -popup.size.height)
                    }
                    popup.setLocation(screenPoint)
                }
            }

            override fun onClosed(event: LightweightWindowEvent) {
                if (!confirmed) {
                    setMessage(commitMessageControl, commitMessageDocument, project, originalMessage)
                }
                // Return focus to editor
                commitMessage?.editorField?.requestFocusInWindow()
            }
        })

        val popup = popupBuilder.createPopup()

        // Show with platform positioning first — beforeShown will reposition above editor
        popup.showInBestPositionFor(e.dataContext)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isVisible = true
        val history = CommitMessageHistory.instance()
        e.presentation.isEnabled = history.size() > 0
    }

    /**
     * Set the commit message in the text area using whichever mechanism is available.
     */
    private fun setMessage(
        commitMessageControl: com.intellij.openapi.vcs.CommitMessageI?,
        commitMessageDocument: com.intellij.openapi.editor.Document?,
        project: com.intellij.openapi.project.Project,
        message: String
    ) {
        if (commitMessageControl != null) {
            commitMessageControl.setCommitMessage(message)
        } else if (commitMessageDocument != null) {
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
                commitMessageDocument.setText(message)
            }
        }
    }
}
