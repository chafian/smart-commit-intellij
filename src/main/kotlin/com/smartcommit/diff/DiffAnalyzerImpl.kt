package com.smartcommit.diff

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.smartcommit.diff.model.ChangeType
import com.smartcommit.diff.model.FileDiff

/**
 * IntelliJ API adapter that reads [Change] objects from the VCS subsystem
 * and produces pure [FileDiff] data models.
 *
 * This is the **only** class in the diff package that imports IntelliJ APIs.
 * All downstream logic operates on the pure model types.
 *
 * @param changes The list of [Change] objects selected for commit,
 *                typically obtained from [com.intellij.openapi.vcs.CheckinProjectPanel.getSelectedChanges].
 */
class DiffAnalyzerImpl(private val changes: Collection<Change>) : DiffAnalyzer {

    private val log = Logger.getInstance(DiffAnalyzerImpl::class.java)

    override fun extractFileDiffs(): List<FileDiff> {
        return changes.mapNotNull { change ->
            try {
                extractSingleFileDiff(change)
            } catch (e: Exception) {
                log.warn("Failed to extract diff for change: $change", e)
                null
            }
        }
    }

    // ── Private helpers ─────────────────────────────────────

    private fun extractSingleFileDiff(change: Change): FileDiff {
        val beforeRevision: ContentRevision? = change.beforeRevision
        val afterRevision: ContentRevision? = change.afterRevision

        val changeType = mapChangeType(change)
        val filePath = resolveFilePath(afterRevision, beforeRevision)
        val oldFilePath = resolveOldFilePath(changeType, beforeRevision, filePath)
        val extension = DiffUtils.extractExtension(filePath)

        // Read content from revisions
        val beforeContent = safeGetContent(beforeRevision)
        val afterContent = safeGetContent(afterRevision)

        // Detect binary
        val isBinary = DiffUtils.isBinaryContent(beforeContent) || DiffUtils.isBinaryContent(afterContent)

        // Compute diff (skip for binary files)
        val diff = if (isBinary) {
            null
        } else {
            DiffUtils.computeSimpleDiff(beforeContent, afterContent)
        }

        val (linesAdded, linesDeleted) = DiffUtils.countChangedLines(diff)

        return FileDiff(
            filePath = filePath,
            oldFilePath = oldFilePath,
            changeType = changeType,
            fileExtension = extension,
            diff = diff,
            linesAdded = linesAdded,
            linesDeleted = linesDeleted,
            isBinary = isBinary
        )
    }

    /**
     * Map IntelliJ's [Change.Type] to our pure [ChangeType] enum.
     */
    private fun mapChangeType(change: Change): ChangeType {
        return when (change.type) {
            Change.Type.NEW -> ChangeType.NEW
            Change.Type.DELETED -> ChangeType.DELETED
            Change.Type.MOVED -> {
                if (change.isRenamed) ChangeType.RENAMED else ChangeType.MOVED
            }
            Change.Type.MODIFICATION -> ChangeType.MODIFIED
        }
    }

    /**
     * Resolve the primary file path — prefer after-revision (the "current" state),
     * fall back to before-revision for deletions.
     */
    private fun resolveFilePath(
        afterRevision: ContentRevision?,
        beforeRevision: ContentRevision?
    ): String {
        val revision = afterRevision ?: beforeRevision
            ?: error("Change has neither before nor after revision")
        return revision.file.path
    }

    /**
     * Resolve the old file path for relocations.
     * Returns null unless the change is a move or rename with a different path.
     */
    private fun resolveOldFilePath(
        changeType: ChangeType,
        beforeRevision: ContentRevision?,
        currentPath: String
    ): String? {
        if (!changeType.isRelocation) return null
        val oldPath = beforeRevision?.file?.path ?: return null
        return if (oldPath != currentPath) oldPath else null
    }

    /**
     * Safely read content from a revision.
     * Returns null if the revision is null or content retrieval fails.
     */
    private fun safeGetContent(revision: ContentRevision?): String? {
        if (revision == null) return null
        return try {
            revision.content
        } catch (e: Exception) {
            log.warn("Failed to read content from revision: ${revision.file.path}", e)
            null
        }
    }
}
