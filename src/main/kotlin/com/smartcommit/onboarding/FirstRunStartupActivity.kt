package com.smartcommit.onboarding

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.ui.DialogWrapper
import com.smartcommit.ai.CloudAuthManager
import com.smartcommit.settings.AiProviderType
import com.smartcommit.settings.SmartCommitConfigurable
import com.smartcommit.settings.SmartCommitSettings
import com.smartcommit.util.DeviceCodeFlowHelper
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
                // "Connect IDE" — start device code flow via shared helper
                DeviceCodeFlowHelper.start(project)
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
}
