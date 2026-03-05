package com.smartcommit.util

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

/**
 * Utility for displaying notifications to the user.
 *
 * Uses the "Smart Commit" notification group defined in `plugin.xml`.
 */
object NotificationUtils {

    private const val GROUP_ID = "Smart Commit"

    fun info(project: Project?, title: String, content: String = "") {
        notify(project, title, content, NotificationType.INFORMATION)
    }

    fun warning(project: Project?, title: String, content: String = "") {
        notify(project, title, content, NotificationType.WARNING)
    }

    fun error(project: Project?, title: String, content: String = "") {
        notify(project, title, content, NotificationType.ERROR)
    }

    /**
     * Show an info notification with an action link.
     */
    fun infoWithAction(
        project: Project?,
        title: String,
        content: String,
        actionText: String,
        action: () -> Unit
    ) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(title, content, NotificationType.INFORMATION)
            .addAction(NotificationAction.createSimpleExpiring(actionText) { action() })
            .notify(project)
    }

    private fun notify(
        project: Project?,
        title: String,
        content: String,
        type: NotificationType
    ) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(title, content, type)
            .notify(project)
    }
}
