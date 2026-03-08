package com.smartcommit.ai

import com.smartcommit.branch.BranchContext
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
 * @param branchContext  Parsed branch context for Smart Branch integration.
 * @param ticketInFooter Whether ticket goes in footer (true) or title suffix (false).
 */
class PromptBuilder(
    private val maxDiffTokens: Int = 4000,
    private val maxFiles: Int = MAX_FILES_IN_PROMPT,
    private val maxTotalChars: Int = MAX_TOTAL_PROMPT_CHARS,
    private val conventionHint: String = "",
    private val oneLineOnly: Boolean = false,
    private val languageHint: String = "",
    private val customSystemPrompt: String = "",
    private val maxSubjectLength: Int = 72,
    private val branchContext: BranchContext = BranchContext.EMPTY,
    private val ticketInFooter: Boolean = true
) {

    /** Format the output format template with the user's max subject length. */
    private fun outputFormat(): String = OUTPUT_FORMAT_TEMPLATE.format(maxSubjectLength)

    /** Format the style rules template with the user's max subject length. */
    private fun styleRules(): String = STYLE_RULES_TEMPLATE.format(maxSubjectLength)

    /** Format the one-line hint with the user's max subject length. */
    private fun oneLineHint(): String = ONE_LINE_HINT_TEMPLATE.format(maxSubjectLength)

    /**
     * Build the system prompt — the LLM's "role" and formatting rules.
     */
    fun buildSystemPrompt(): String = buildString {
        append(SYSTEM_ROLE)
        append("\n\n")
        append(outputFormat())
        append("\n\n")
        append(styleRules())
        if (oneLineOnly) {
            append("\n\n")
            append(oneLineHint())
        }
        // Branch context — scope, type, and ticket instructions
        if (branchContext.hasUsefulInfo) {
            append("\n\n")
            append("BRANCH CONTEXT RULES (IMPORTANT — follow these precisely):\n")
            if (branchContext.hasType) {
                append("- The branch type is \"${branchContext.type}\". Use this as the commit type.\n")
            }
            if (branchContext.hasScope) {
                append("- The branch scope is \"${branchContext.scope}\". Include it in the title as the scope component, e.g. ${branchContext.type ?: "feat"}(${branchContext.scope}): ...\n")
            } else if (branchContext.description != null) {
                append("- No explicit scope was detected. Infer the most likely component/module scope from the branch description and include it in the title, e.g. feat(inferred-scope): ...\n")
            }
            if (branchContext.hasTicket) {
                if (ticketInFooter) {
                    append("- The branch references ticket ${branchContext.ticket}. Include it in the footer as \"Refs: ${branchContext.ticket}\". Do NOT include the ticket ID in the title.\n")
                } else {
                    append("- The branch references ticket ${branchContext.ticket}. Append it to the end of the title in parentheses: \"... (${branchContext.ticket})\". Do NOT include it in the footer.\n")
                }
            }
            if (branchContext.description != null) {
                append("- The branch description is \"${branchContext.description}\". Use it to guide the title wording.\n")
            }
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

        // Section 3.5: Branch context (if available)
        if (branchContext.hasUsefulInfo) {
            append("## Branch Context:\n")
            append("Branch: ${branchContext.rawBranchName}\n")
            if (branchContext.hasType) append("Type: ${branchContext.type}\n")
            if (branchContext.hasScope) append("Scope: ${branchContext.scope}\n")
            if (branchContext.hasTicket) append("Ticket: ${branchContext.ticket}\n")
            if (branchContext.description != null) append("Description: ${branchContext.description}\n")
            append("\n")
        }

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

        internal const val OUTPUT_FORMAT_TEMPLATE = """Respond with EXACTLY this JSON structure:
{
  "title": "<imperative-mood subject line, max %d characters>",
  "body": "<optional multi-line explanation of WHY the change was made, wrap at 72 chars>",
  "footer": "<optional: issue references, breaking change notes, or null>"
}

Rules for the JSON:
- "title" is REQUIRED and must not be empty.
- "body" is OPTIONAL. Set to null or omit if the change is self-explanatory.
- "footer" is OPTIONAL. Set to null or omit if not applicable.
- Do NOT wrap the JSON in markdown code fences.
- Do NOT include any text outside the JSON object."""

        internal const val ONE_LINE_HINT_TEMPLATE = """IMPORTANT: The user wants a ONE-LINE commit message only. \
Set "body" to null and "footer" to null. \
Put all the important information in the "title" field. Keep it concise and under %d characters."""

        internal const val STYLE_RULES_TEMPLATE = """Commit message style rules:
- Title: use imperative mood ("Add feature" not "Added feature").
- Title: max %d characters. Be concise and specific about the PRIMARY change.
- Title: describe ONE primary change only. Move secondary changes to the body.
- Title: do NOT include file paths.
- Title: do NOT combine multiple topics with "and" (e.g. "Add X and update Y" is bad).
- Title: do NOT start with a category prefix unless convention rules say otherwise.
- Body: NEVER start with filler phrases like "This change introduces", "This update adds", "This commit", "This PR". Start directly with the explanation.
- Body: explain WHY the change was made, not WHAT (the diff shows what).
- Body: wrap lines at 72 characters.
- Body: use bullet points prefixed with "- " for listing changes (lowercase start).
- Body: prefix bullet lists with "Changes:" on its own line when listing modifications.
- Body: keep bullet points short and direct (max 8-10 words each).
- If the change is trivial (typo fix, formatting), body should be null.
- Analyze the FULL diff to understand the intent, not just file names."""
    }
}
