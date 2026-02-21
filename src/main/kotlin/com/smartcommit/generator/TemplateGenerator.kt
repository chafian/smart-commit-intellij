package com.smartcommit.generator

import com.smartcommit.convention.CommitConvention
import com.smartcommit.diff.ScopeDetector
import com.smartcommit.diff.model.ChangeCategory
import com.smartcommit.diff.model.ChangeType
import com.smartcommit.diff.model.DiffSummary
import com.smartcommit.diff.model.FileDiff
import com.smartcommit.generator.model.GeneratedCommitMessage

/**
 * Deterministic, template-based commit message generator.
 *
 * Builds a variable map from a [DiffSummary], then feeds it through
 * [TemplateEngine] to produce the final message. No AI, no network,
 * no IntelliJ APIs — pure Kotlin logic.
 *
 * When a [convention] is provided, the generated message is post-processed
 * through [CommitConvention.format] to apply convention-specific formatting
 * (e.g. Gitmoji emoji prefix, Conventional Commits type prefix).
 *
 * @param titleTemplate  Template for the subject line.
 * @param bodyTemplate   Template for the message body (optional section).
 * @param maxTitleLength Maximum characters for the title (default 72).
 * @param convention     Optional commit convention to apply to the output.
 */
class TemplateGenerator(
    private val titleTemplate: String = DEFAULT_TITLE_TEMPLATE,
    private val bodyTemplate: String = DEFAULT_BODY_TEMPLATE,
    private val maxTitleLength: Int = 72,
    private val convention: CommitConvention? = null
) : CommitMessageGenerator {

    override val displayName: String = "Template"

    override fun generate(summary: DiffSummary): GeneratedCommitMessage {
        check(!summary.isEmpty) { "Cannot generate commit message from empty changeset" }

        val variables = buildVariableMap(summary)

        val title = TemplateEngine.render(titleTemplate, variables)
        val body = TemplateEngine.render(bodyTemplate, variables)

        var message = GeneratedCommitMessage(
            title = title,
            body = body.ifBlank { null }
        )

        // Apply convention formatting if configured
        if (convention != null) {
            val scope = variables["scope"]?.ifEmpty { null }
            message = convention.format(message, summary.dominantCategory, scope)
        }

        return message.withTruncatedTitle(maxTitleLength)
    }

    // ── Variable map construction ───────────────────────────

    /**
     * Build the full map of template variables from a [DiffSummary].
     *
     * Available variables:
     * | Key              | Example value                        |
     * |------------------|--------------------------------------|
     * | `type`           | `feature`                            |
     * | `scope`          | `auth`                               |
     * | `summary`        | `Add login validation`               |
     * | `files`          | `A  src/Login.kt\nM  src/Auth.kt`    |
     * | `files_changed`  | `3`                                  |
     * | `lines_added`    | `42`                                 |
     * | `lines_deleted`  | `7`                                  |
     * | `new_files`      | `src/Login.kt, src/Auth.kt`          |
     * | `modified_files` | `src/Service.kt`                     |
     * | `deleted_files`  | `src/Legacy.kt`                      |
     * | `moved_files`    | `OldName.kt → NewName.kt`            |
     * | `body_lines`     | Multi-line breakdown by category      |
     */
    internal fun buildVariableMap(summary: DiffSummary): Map<String, String> {
        val vars = mutableMapOf<String, String>()

        // Core fields
        val category = summary.dominantCategory
        vars["type"] = category.label
        vars["scope"] = ScopeDetector.detect(summary)
        vars["summary"] = inferSummary(summary)

        // File stats
        vars["files_changed"] = summary.totalFiles.toString()
        vars["lines_added"] = summary.totalLinesAdded.toString()
        vars["lines_deleted"] = summary.totalLinesDeleted.toString()

        // File lists
        vars["files"] = formatFileList(summary.fileDiffs)
        vars["new_files"] = formatPathList(summary.newFiles)
        vars["modified_files"] = formatPathList(summary.modifiedFiles)
        vars["deleted_files"] = formatPathList(summary.deletedFiles)
        vars["moved_files"] = formatMovedList(summary.movedFiles)

        // Body breakdown
        vars["body_lines"] = buildBodyBreakdown(summary)

        return vars
    }

    // ── Summary inference ───────────────────────────────────

    /**
     * Infer a human-readable summary sentence from the changes.
     * Uses the dominant category and the most significant files.
     */
    private fun inferSummary(summary: DiffSummary): String {
        val dominant = summary.dominantCategory
        val topFiles = summary.sortedBySignificance.take(3)

        return when {
            summary.isAllNew && summary.totalFiles == 1 ->
                "Add ${topFiles[0].fileName}"

            summary.isAllNew ->
                "Add ${describeFileGroup(topFiles)}"

            summary.isAllDeleted && summary.totalFiles == 1 ->
                "Remove ${topFiles[0].fileName}"

            summary.isAllDeleted ->
                "Remove ${describeFileGroup(topFiles)}"

            summary.totalFiles == 1 -> {
                val file = topFiles[0]
                "${verbForCategory(dominant)} ${file.fileName}"
            }

            else -> {
                val scope = ScopeDetector.detect(summary)
                if (scope.isNotEmpty()) {
                    "${verbForCategory(dominant)} $scope"
                } else {
                    "${verbForCategory(dominant)} ${describeFileGroup(topFiles)}"
                }
            }
        }
    }

    // ── Formatting helpers ──────────────────────────────────

    private fun formatFileList(diffs: List<FileDiff>): String {
        return diffs.joinToString("\n") { fd ->
            val prefix = when (fd.changeType) {
                ChangeType.NEW -> "A"
                ChangeType.MODIFIED -> "M"
                ChangeType.DELETED -> "D"
                ChangeType.MOVED, ChangeType.RENAMED -> "R"
            }
            if (fd.changeType.isRelocation && fd.oldFilePath != null) {
                "$prefix  ${fd.oldFilePath} -> ${fd.filePath}"
            } else {
                "$prefix  ${fd.filePath}"
            }
        }
    }

    private fun formatPathList(diffs: List<FileDiff>): String {
        return diffs.joinToString(", ") { it.fileName }
    }

    private fun formatMovedList(diffs: List<FileDiff>): String {
        return diffs.joinToString(", ") { fd ->
            if (fd.oldFilePath != null) {
                "${fd.oldFilePath.substringAfterLast('/')} -> ${fd.fileName}"
            } else {
                fd.fileName
            }
        }
    }

    private fun describeFileGroup(files: List<FileDiff>): String {
        return when (files.size) {
            0 -> "changes"
            1 -> files[0].fileName
            2 -> "${files[0].fileName} and ${files[1].fileName}"
            else -> "${files[0].fileName} and ${files.size - 1} other files"
        }
    }

    private fun verbForCategory(category: ChangeCategory): String {
        return when (category) {
            ChangeCategory.FEATURE -> "Update"
            ChangeCategory.BUGFIX -> "Fix"
            ChangeCategory.REFACTOR -> "Refactor"
            ChangeCategory.TEST -> "Update tests for"
            ChangeCategory.DOCS -> "Update docs for"
            ChangeCategory.STYLE -> "Restyle"
            ChangeCategory.BUILD -> "Update build config for"
            ChangeCategory.CI -> "Update CI for"
            ChangeCategory.CHORE -> "Update"
        }
    }

    /**
     * Build the multi-line body breakdown, grouped by category.
     * Example:
     * ```
     * Features:
     * - src/main/Login.kt (+20/-3)
     *
     * Tests:
     * - src/test/LoginTest.kt (+45/-0)
     * ```
     */
    private fun buildBodyBreakdown(summary: DiffSummary): String {
        val groups = summary.byCategory
            .entries
            .sortedBy { it.key.priority }

        return buildString {
            for ((category, files) in groups) {
                append(categoryHeading(category))
                append(":\n")
                for (file in files) {
                    append("- ${file.filePath}")
                    if (file.linesAdded > 0 || file.linesDeleted > 0) {
                        append(" (+${file.linesAdded}/-${file.linesDeleted})")
                    }
                    if (file.changeType.isRelocation && file.oldFilePath != null) {
                        append(" (from ${file.oldFilePath})")
                    }
                    append("\n")
                }
                append("\n")
            }
        }.trimEnd()
    }

    private fun categoryHeading(category: ChangeCategory): String {
        return when (category) {
            ChangeCategory.FEATURE -> "Features"
            ChangeCategory.BUGFIX -> "Bug Fixes"
            ChangeCategory.REFACTOR -> "Refactoring"
            ChangeCategory.TEST -> "Tests"
            ChangeCategory.DOCS -> "Documentation"
            ChangeCategory.STYLE -> "Style"
            ChangeCategory.BUILD -> "Build"
            ChangeCategory.CI -> "CI"
            ChangeCategory.CHORE -> "Chores"
        }
    }

    companion object {
        /**
         * Default title template.
         * Produces: `<type>(<scope>): <summary>` or `<type>: <summary>` when no scope.
         */
        const val DEFAULT_TITLE_TEMPLATE = "{{type}}{{#scope}}({{scope}}){{/scope}}: {{summary}}"

        /**
         * Default body template.
         * Includes a file count stat line and a categorized breakdown.
         */
        const val DEFAULT_BODY_TEMPLATE =
            "{{files_changed}} file(s) changed, +{{lines_added}}/-{{lines_deleted}} lines\n\n{{body_lines}}"
    }
}
