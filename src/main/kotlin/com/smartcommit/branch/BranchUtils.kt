package com.smartcommit.branch

import com.intellij.openapi.project.Project
import git4idea.repo.GitRepositoryManager

/**
 * Thin bridge to Git4Idea for retrieving the current Git branch name.
 *
 * This is the ONLY file in the `branch` package that imports IntelliJ or Git4Idea APIs.
 * All other branch-related logic ([BranchNameParser], [BranchContext]) is pure Kotlin.
 */
object BranchUtils {

    /**
     * Get the current Git branch name for the given project.
     *
     * Uses the first Git repository found in the project. For multi-root projects,
     * this returns the branch of the first repository (typically the main one).
     *
     * @param project The IntelliJ project.
     * @return The branch name (e.g. "feature/JIRA-142-add-payment-api"), or null
     *         if no Git repository is found or the repo is in a detached HEAD state.
     */
    fun getCurrentBranchName(project: Project): String? {
        return try {
            val repos = GitRepositoryManager.getInstance(project).repositories
            repos.firstOrNull()?.currentBranch?.name
        } catch (_: Exception) {
            // Git4Idea not available or repository error — fail gracefully
            null
        }
    }
}
