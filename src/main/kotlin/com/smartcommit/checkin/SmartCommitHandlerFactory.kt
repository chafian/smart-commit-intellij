package com.smartcommit.checkin

import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory

/**
 * Factory that creates [SmartCommitHandler] instances for each commit session.
 *
 * Registered in `plugin.xml` via the `com.intellij.checkinHandlerFactory` extension point.
 * IntelliJ calls [createHandler] every time the commit dialog opens.
 */
class SmartCommitHandlerFactory : CheckinHandlerFactory() {

    override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
        return SmartCommitHandler(panel)
    }
}
