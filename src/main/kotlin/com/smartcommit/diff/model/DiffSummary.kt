package com.smartcommit.diff.model

/**
 * Aggregated summary of all changes selected for commit.
 * Provides high-level statistics and grouped views that generators
 * use to produce commit messages.
 *
 * This is a pure data class — no IntelliJ API dependencies.
 */
data class DiffSummary(
    /** All individual file diffs in this commit. */
    val fileDiffs: List<FileDiff>,

    /** Per-file classification results. */
    val classifications: Map<FileDiff, ChangeCategory>
) {
    // ── Counts ──────────────────────────────────────────────

    val totalFiles: Int get() = fileDiffs.size
    val totalLinesAdded: Int get() = fileDiffs.sumOf { it.linesAdded }
    val totalLinesDeleted: Int get() = fileDiffs.sumOf { it.linesDeleted }

    val newFiles: List<FileDiff> get() = fileDiffs.filter { it.changeType == ChangeType.NEW }
    val modifiedFiles: List<FileDiff> get() = fileDiffs.filter { it.changeType == ChangeType.MODIFIED }
    val deletedFiles: List<FileDiff> get() = fileDiffs.filter { it.changeType == ChangeType.DELETED }
    val movedFiles: List<FileDiff> get() = fileDiffs.filter { it.changeType.isRelocation }

    // ── Groupings ───────────────────────────────────────────

    /** Files grouped by their classified category. */
    val byCategory: Map<ChangeCategory, List<FileDiff>>
        get() = fileDiffs.groupBy { classifications[it] ?: ChangeCategory.CHORE }

    /** Files grouped by parent directory. */
    val byDirectory: Map<String, List<FileDiff>>
        get() = fileDiffs.groupBy { it.directory }

    /** Files grouped by file extension. */
    val byExtension: Map<String, List<FileDiff>>
        get() = fileDiffs.groupBy { it.fileExtension }

    // ── Derived properties ──────────────────────────────────

    /** The dominant (highest-priority) category across all changes. */
    val dominantCategory: ChangeCategory
        get() = ChangeCategory.dominant(classifications.values)

    /** True if every file in the changeset is a new addition. */
    val isAllNew: Boolean get() = fileDiffs.isNotEmpty() && fileDiffs.all { it.changeType == ChangeType.NEW }

    /** True if every file in the changeset is a deletion. */
    val isAllDeleted: Boolean get() = fileDiffs.isNotEmpty() && fileDiffs.all { it.changeType == ChangeType.DELETED }

    /** True if the changeset has no files. */
    val isEmpty: Boolean get() = fileDiffs.isEmpty()

    /** The combined diff text of all files, concatenated. Null entries are skipped. */
    val combinedDiff: String
        get() = fileDiffs.mapNotNull { it.diff }.joinToString("\n")

    /**
     * Returns the file diffs sorted by significance:
     * highest total changed lines first, then alphabetically by path.
     */
    val sortedBySignificance: List<FileDiff>
        get() = fileDiffs.sortedWith(
            compareByDescending<FileDiff> { it.totalChangedLines }
                .thenBy { it.filePath }
        )
}
