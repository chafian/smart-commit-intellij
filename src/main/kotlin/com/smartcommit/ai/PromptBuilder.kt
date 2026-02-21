package com.smartcommit.ai

import com.smartcommit.diff.DiffUtils
import com.smartcommit.diff.model.DiffSummary

/**
 * Builds the system and user prompts for LLM-based commit message generation.
 *
 * Pure Kotlin — no IntelliJ APIs, no network, no state.
 * All methods are deterministic functions of their inputs.
 *
 * **Deterministic size guarantees:**
 * - File list: hard-capped at [maxFiles] entries (default 30).
 * - Diff section: hard-capped at [maxDiffTokens] tokens (default 4000).
 * - Total user prompt: hard-capped at [maxTotalChars] characters (default 32000).
 *
 * These limits ensure the prompt never blows up context windows,
 * even for large monorepo commits with hundreds of files.
 *
 * @param maxDiffTokens  Maximum token budget for the diff portion of the user prompt.
 * @param maxFiles       Maximum number of files to list individually in the prompt.
 * @param maxTotalChars  Hard character cap for the entire user prompt.
 * @param conventionHint Optional convention instructions appended to the system prompt.
 */
class PromptBuilder(
    private val maxDiffTokens: Int = 4000,
    private val maxFiles: Int = MAX_FILES_IN_PROMPT,
    private val maxTotalChars: Int = MAX_TOTAL_PROMPT_CHARS,
    private val conventionHint: String = "",
    private val oneLineOnly: Boolean = false,
    private val languageHint: String = "",
    private val customSystemPrompt: String = ""
) {

    /**
     * Build the system prompt — the LLM's "role" and formatting rules.
     */
    fun buildSystemPrompt(): String = buildString {
        append(SYSTEM_ROLE)
        append("\n\n")
        append(OUTPUT_FORMAT)
        append("\n\n")
        append(STYLE_RULES)
        if (oneLineOnly) {
            append("\n\n")
            append(ONE_LINE_HINT)
        }
        if (conventionHint.isNotBlank()) {
            append("\n\n")
            append("Convention-specific rules:\n")
            append(conventionHint)
        }
        if (languageHint.isNotBlank()) {
            append("\n\n")
            append(languageHint)
        }
        if (customSystemPrompt.isNotBlank()) {
            append("\n\n")
            append("Additional user instructions:\n")
            append(customSystemPrompt)
        }
    }

    /**
     * Build the user prompt — the actual changeset data for this commit.
     *
     * **Deterministic size limits enforced:**
     * 1. File list capped at [maxFiles] entries.
     * 2. Diff section capped at [maxDiffTokens] tokens.
     * 3. Total prompt hard-capped at [maxTotalChars] characters.
     */
    fun buildUserPrompt(summary: DiffSummary): String {
        val raw = buildRawUserPrompt(summary)
        // Hard cap: truncate entire prompt if it still exceeds max chars
        return if (raw.length <= maxTotalChars) raw
        else raw.take(maxTotalChars) + "\n\n... (prompt truncated to fit token limit)\n"
    }

    private fun buildRawUserPrompt(summary: DiffSummary): String = buildString {
        append("Generate a commit message for the following changes:\n\n")

        // Section 1: File list (capped at maxFiles)
        val filesToShow = summary.sortedBySignificance.take(maxFiles)
        val omitted = summary.totalFiles - filesToShow.size
        append("## Files Changed (${summary.totalFiles}):\n")
        append(DiffUtils.formatFileList(filesToShow))
        if (omitted > 0) {
            append("\n... and $omitted more file(s) omitted")
        }
        append("\n\n")

        // Section 2: Statistics
        append("## Statistics:\n")
        append("- Files: ${summary.totalFiles}\n")
        append("- Lines added: +${summary.totalLinesAdded}\n")
        append("- Lines deleted: -${summary.totalLinesDeleted}\n")
        append("- New files: ${summary.newFiles.size}\n")
        append("- Modified files: ${summary.modifiedFiles.size}\n")
        append("- Deleted files: ${summary.deletedFiles.size}\n")
        append("- Moved/Renamed files: ${summary.movedFiles.size}\n")
        append("\n")

        // Section 3: Dominant category hint
        append("## Detected change category: ${summary.dominantCategory.label}\n\n")

        // Section 4: Truncated diffs (only for the files we listed)
        val diffText = DiffUtils.truncateDiffs(filesToShow, maxDiffTokens)
        if (diffText.isNotBlank()) {
            append("## Diff:\n")
            append(diffText)
            append("\n")
        }
    }

    companion object {

        // ── Hard limits ─────────────────────────────────────

        /** Maximum number of files listed in the prompt. Beyond this, only stats are shown. */
        const val MAX_FILES_IN_PROMPT = 30

        /** Maximum total characters for the entire user prompt (~8k tokens). */
        const val MAX_TOTAL_PROMPT_CHARS = 32_000

        // ── Prompt fragments ────────────────────────────────

        internal const val SYSTEM_ROLE = """You are a precise Git commit message generator. \
Your sole task is to analyze a code diff and produce a single, high-quality commit message. \
You must respond ONLY with a valid JSON object — no markdown, no explanation, no commentary."""

        internal const val OUTPUT_FORMAT = """Respond with EXACTLY this JSON structure:
{
  "title": "<imperative-mood subject line, max 72 characters>",
  "body": "<optional multi-line explanation of WHY the change was made, wrap at 72 chars>",
  "footer": "<optional: issue references, breaking change notes, or null>"
}

Rules for the JSON:
- "title" is REQUIRED and must not be empty.
- "body" is OPTIONAL. Set to null or omit if the change is self-explanatory.
- "footer" is OPTIONAL. Set to null or omit if not applicable.
- Do NOT wrap the JSON in markdown code fences.
- Do NOT include any text outside the JSON object."""

        internal const val ONE_LINE_HINT = """IMPORTANT: The user wants a ONE-LINE commit message only. \
Set "body" to null and "footer" to null. \
Put all the important information in the "title" field. Keep it concise and under 72 characters."""

        internal const val STYLE_RULES = """Commit message style rules:
- Title: use imperative mood ("Add feature" not "Added feature").
- Title: max 72 characters. Be specific about WHAT changed.
- Title: do NOT include file paths.
- Title: do NOT start with a category prefix unless convention rules say otherwise.
- Body: explain WHY the change was made, not WHAT (the diff shows what).
- Body: wrap lines at 72 characters.
- Body: use bullet points for multiple reasons.
- If the change is trivial (typo fix, formatting), body should be null.
- Analyze the FULL diff to understand the intent, not just file names."""
    }
}
