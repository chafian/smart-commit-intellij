package com.smartcommit.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil

/**
 * Action that opens the Smart Commit settings page.
 *
 * Registered in `plugin.xml` under the Tools menu, giving users
 * a convenient way to access plugin settings from the main menu.
 */
class OpenSmartCommitSettingsAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        ShowSettingsUtil.getInstance().showSettingsDialog(
            e.project,
            "com.smartcommit"
        )
    }
}
