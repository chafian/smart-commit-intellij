package com.smartcommit.diff.model

/**
 * Structured representation of a single file's change.
 * This is a pure data class with no IntelliJ API dependencies,
 * making it fully testable in isolation.
 *
 * @property filePath       Relative path of the file after the change (or before, if deleted).
 * @property oldFilePath    Relative path before the change; differs from [filePath] for moves/renames.
 *                          Null for newly added files.
 * @property changeType     The type of VCS operation (new, modified, deleted, moved, renamed).
 * @property fileExtension  Lowercase file extension without dot, e.g. "kt", "xml". Empty if none.
 * @property diff           Unified diff text between before and after revisions.
 *                          Null for binary files or when content is unavailable.
 * @property linesAdded     Number of lines added (computed from diff).
 * @property linesDeleted   Number of lines deleted (computed from diff).
 * @property isBinary       True if the file is detected as binary.
 */
data class FileDiff(
    val filePath: String,
    val oldFilePath: String?,
    val changeType: ChangeType,
    val fileExtension: String,
    val diff: String?,
    val linesAdded: Int,
    val linesDeleted: Int,
    val isBinary: Boolean
) {
    /** Total number of changed lines (added + deleted). */
    val totalChangedLines: Int get() = linesAdded + linesDeleted

    /** True if this diff has actual textual content available. */
    val hasDiff: Boolean get() = !diff.isNullOrBlank()

    /** The file name without directory path. */
    val fileName: String get() = filePath.substringAfterLast('/')

    /** The parent directory path, or empty string for root-level files. */
    val directory: String
        get() {
            val idx = filePath.lastIndexOf('/')
            return if (idx >= 0) filePath.substring(0, idx) else ""
        }
}
