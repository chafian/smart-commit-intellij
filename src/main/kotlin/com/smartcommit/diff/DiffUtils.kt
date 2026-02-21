package com.smartcommit.diff

import com.smartcommit.diff.model.FileDiff

/**
 * Pure utility functions for diff text manipulation.
 * No IntelliJ API dependencies — fully unit-testable.
 */
object DiffUtils {

    /** Average characters per token for rough estimation (GPT-style tokenizers). */
    private const val CHARS_PER_TOKEN = 4

    /**
     * Compute a unified diff between two text contents.
     * Returns the diff as a string of `+`/`-` prefixed lines.
     * Lines with no change are prefixed with a space.
     *
     * This is a simple line-based diff (not a full Myers algorithm)
     * sufficient for commit message generation. Context lines are included
     * to give AI models surrounding context.
     *
     * @param before  Content before the change (null for new files).
     * @param after   Content after the change (null for deleted files).
     * @param contextLines Number of unchanged context lines around each hunk.
     * @return Unified diff string, or null if both inputs are null.
     */
    fun computeSimpleDiff(before: String?, after: String?, contextLines: Int = 3): String? {
        if (before == null && after == null) return null

        val beforeLines = before?.lines() ?: emptyList()
        val afterLines = after?.lines() ?: emptyList()

        if (before == null) {
            // Entire file is new
            return afterLines.joinToString("\n") { "+$it" }
        }
        if (after == null) {
            // Entire file is deleted
            return beforeLines.joinToString("\n") { "-$it" }
        }

        // Simple LCS-based diff
        val lcs = computeLcs(beforeLines, afterLines)
        return formatDiffFromLcs(beforeLines, afterLines, lcs, contextLines)
    }

    /**
     * Count added and deleted lines from a unified diff string.
     *
     * @return Pair of (linesAdded, linesDeleted).
     */
    fun countChangedLines(diff: String?): Pair<Int, Int> {
        if (diff.isNullOrBlank()) return 0 to 0

        var added = 0
        var deleted = 0
        for (line in diff.lines()) {
            when {
                line.startsWith("+") && !line.startsWith("+++") -> added++
                line.startsWith("-") && !line.startsWith("---") -> deleted++
            }
        }
        return added to deleted
    }

    /**
     * Estimate the number of tokens in a text string.
     * Uses a simple character-count heuristic (1 token ≈ 4 chars).
     */
    fun estimateTokens(text: String): Int {
        return (text.length + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN
    }

    /**
     * Truncate a combined diff text to fit within a token budget.
     * Prioritizes files with the most changes, and truncates individual
     * file diffs by keeping the first N lines.
     *
     * @param fileDiffs   List of file diffs to include.
     * @param maxTokens   Maximum token budget for the combined output.
     * @return Truncated diff text with file headers.
     */
    fun truncateDiffs(fileDiffs: List<FileDiff>, maxTokens: Int): String {
        if (fileDiffs.isEmpty()) return ""

        val sorted = fileDiffs
            .filter { it.hasDiff }
            .sortedByDescending { it.totalChangedLines }

        val builder = StringBuilder()
        var remainingTokens = maxTokens

        for (fileDiff in sorted) {
            val header = formatFileHeader(fileDiff)
            val headerTokens = estimateTokens(header)

            if (remainingTokens <= headerTokens + 10) {
                // Not enough room even for a header + minimal diff
                val skipped = sorted.size - sorted.indexOf(fileDiff)
                builder.append("\n... and $skipped more file(s) truncated\n")
                break
            }

            builder.append(header)
            remainingTokens -= headerTokens

            val diff = fileDiff.diff ?: continue
            val diffTokens = estimateTokens(diff)

            if (diffTokens <= remainingTokens) {
                builder.append(diff).append("\n")
                remainingTokens -= diffTokens
            } else {
                // Truncate this diff to fit remaining budget
                val truncated = truncateText(diff, remainingTokens)
                builder.append(truncated)
                builder.append("\n... (truncated)\n")
                remainingTokens = 0
                break
            }
        }

        return builder.toString().trimEnd()
    }

    /**
     * Format a compact file list with change types for prompt context.
     * Example output:
     * ```
     * A  src/main/kotlin/Foo.kt
     * M  src/main/kotlin/Bar.kt
     * D  src/main/kotlin/Baz.kt
     * R  OldName.kt → NewName.kt
     * ```
     */
    fun formatFileList(fileDiffs: List<FileDiff>): String {
        return fileDiffs.joinToString("\n") { fd ->
            val prefix = when (fd.changeType) {
                com.smartcommit.diff.model.ChangeType.NEW -> "A "
                com.smartcommit.diff.model.ChangeType.MODIFIED -> "M "
                com.smartcommit.diff.model.ChangeType.DELETED -> "D "
                com.smartcommit.diff.model.ChangeType.MOVED -> "R "
                com.smartcommit.diff.model.ChangeType.RENAMED -> "R "
            }
            if (fd.changeType.isRelocation && fd.oldFilePath != null) {
                "$prefix ${fd.oldFilePath} → ${fd.filePath}"
            } else {
                "$prefix ${fd.filePath}"
            }
        }
    }

    /**
     * Detect whether content appears to be binary.
     * Checks for null bytes in the first 8000 characters.
     */
    fun isBinaryContent(content: String?): Boolean {
        if (content == null) return false
        val sample = content.take(8000)
        return sample.contains('\u0000')
    }

    /**
     * Extract the file extension from a path.
     * Returns lowercase extension without dot, or empty string if none.
     */
    fun extractExtension(path: String): String {
        val fileName = path.substringAfterLast('/')
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex >= 0 && dotIndex < fileName.length - 1) {
            fileName.substring(dotIndex + 1).lowercase()
        } else {
            ""
        }
    }

    // ── Private helpers ─────────────────────────────────────

    private fun formatFileHeader(fileDiff: FileDiff): String {
        val typeLabel = when (fileDiff.changeType) {
            com.smartcommit.diff.model.ChangeType.NEW -> "new file"
            com.smartcommit.diff.model.ChangeType.MODIFIED -> "modified"
            com.smartcommit.diff.model.ChangeType.DELETED -> "deleted"
            com.smartcommit.diff.model.ChangeType.MOVED -> "moved"
            com.smartcommit.diff.model.ChangeType.RENAMED -> "renamed"
        }
        return buildString {
            append("--- ${fileDiff.filePath} ($typeLabel")
            append(", +${fileDiff.linesAdded}/-${fileDiff.linesDeleted}")
            append(") ---\n")
        }
    }

    private fun truncateText(text: String, maxTokens: Int): String {
        val maxChars = maxTokens * CHARS_PER_TOKEN
        return if (text.length <= maxChars) {
            text
        } else {
            text.take(maxChars)
        }
    }

    /**
     * Compute the Longest Common Subsequence (LCS) table for two lists of lines.
     * Returns a 2D array where lcs[i][j] is the LCS length of
     * beforeLines[0..i-1] and afterLines[0..j-1].
     */
    private fun computeLcs(beforeLines: List<String>, afterLines: List<String>): Array<IntArray> {
        val m = beforeLines.size
        val n = afterLines.size
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (beforeLines[i - 1] == afterLines[j - 1]) {
                    dp[i - 1][j - 1] + 1
                } else {
                    maxOf(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }
        return dp
    }

    /**
     * Format a unified diff from LCS table, including context lines around each hunk.
     */
    private fun formatDiffFromLcs(
        beforeLines: List<String>,
        afterLines: List<String>,
        lcs: Array<IntArray>,
        contextLines: Int
    ): String {
        // Backtrack through LCS to produce diff operations
        val ops = mutableListOf<DiffOp>()
        var i = beforeLines.size
        var j = afterLines.size

        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && beforeLines[i - 1] == afterLines[j - 1] -> {
                    ops.add(DiffOp.EQUAL)
                    i--; j--
                }
                j > 0 && (i == 0 || lcs[i][j - 1] >= lcs[i - 1][j]) -> {
                    ops.add(DiffOp.ADD)
                    j--
                }
                else -> {
                    ops.add(DiffOp.REMOVE)
                    i--
                }
            }
        }
        ops.reverse()

        // Determine which lines are within context range of a change
        val isChange = BooleanArray(ops.size) { ops[it] != DiffOp.EQUAL }
        val include = BooleanArray(ops.size)

        for (idx in ops.indices) {
            if (isChange[idx]) {
                for (ctx in maxOf(0, idx - contextLines)..minOf(ops.size - 1, idx + contextLines)) {
                    include[ctx] = true
                }
            }
        }

        // Build output
        val result = StringBuilder()
        var bi = 0 // before-line index
        var ai = 0 // after-line index
        var inHunk = false

        for (idx in ops.indices) {
            if (!include[idx]) {
                if (inHunk) {
                    inHunk = false
                }
                when (ops[idx]) {
                    DiffOp.EQUAL -> { bi++; ai++ }
                    DiffOp.ADD -> ai++
                    DiffOp.REMOVE -> bi++
                }
                continue
            }

            if (!inHunk) {
                if (result.isNotEmpty()) result.append("\n")
                inHunk = true
            }

            when (ops[idx]) {
                DiffOp.EQUAL -> {
                    result.append(" ").append(beforeLines[bi]).append("\n")
                    bi++; ai++
                }
                DiffOp.ADD -> {
                    result.append("+").append(afterLines[ai]).append("\n")
                    ai++
                }
                DiffOp.REMOVE -> {
                    result.append("-").append(beforeLines[bi]).append("\n")
                    bi++
                }
            }
        }

        return result.toString().trimEnd()
    }

    private enum class DiffOp { EQUAL, ADD, REMOVE }
}
