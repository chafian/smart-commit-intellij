package com.smartcommit.generator.model

/**
 * The output of any commit message generator.
 *
 * Follows the widely-adopted Git commit structure:
 * ```
 * <title>          ← subject line, max ~72 chars, mandatory
 *                  ← blank line
 * <body>           ← optional multi-line explanation
 *                  ← blank line
 * <footer>         ← optional metadata (breaking changes, issue refs)
 * ```
 *
 * Pure data class — no IntelliJ or framework dependencies.
 *
 * @property title  The subject line. Always present, never blank.
 * @property body   Optional explanatory body text. Null if not applicable.
 * @property footer Optional footer (issue refs, breaking change notes). Null if not applicable.
 */
data class GeneratedCommitMessage(
    val title: String,
    val body: String? = null,
    val footer: String? = null
) {
    init {
        require(title.isNotBlank()) { "Commit message title must not be blank" }
    }

    /**
     * Format the full commit message as a single string,
     * with blank-line separators between sections.
     */
    fun format(): String = buildString {
        append(title)
        if (!body.isNullOrBlank()) {
            append("\n\n")
            append(body)
        }
        if (!footer.isNullOrBlank()) {
            append("\n\n")
            append(footer)
        }
    }

    /**
     * Returns a copy with the title truncated to [maxLength] **codepoints**.
     *
     * Safety guarantees:
     * - Never splits a Unicode surrogate pair (emoji, CJK supplementary).
     * - Never leaves a trailing partial emoji.
     * - Appends "..." (3 ASCII dots) instead of ellipsis codepoint to avoid
     *   ambiguity with multi-byte ellipsis in byte-counting contexts.
     */
    fun withTruncatedTitle(maxLength: Int = 72): GeneratedCommitMessage {
        val codePointCount = title.codePointCount(0, title.length)
        if (codePointCount <= maxLength) return this

        // offsetByCodePoints is surrogate-pair-safe
        val endIndex = title.offsetByCodePoints(0, maxLength - 3)
        val truncated = title.substring(0, endIndex) + "..."
        return copy(title = truncated)
    }

    /**
     * Returns a copy with the title sanitized for Git commit:
     * - Newlines and carriage returns replaced with spaces.
     * - Leading/trailing whitespace trimmed.
     * - Consecutive spaces collapsed.
     *
     * Commit titles MUST be single-line.
     */
    fun sanitized(): GeneratedCommitMessage {
        val clean = title
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
        return if (clean == title) this else copy(title = clean)
    }

    companion object {
        /** Create a title-only message with no body or footer. */
        fun titleOnly(title: String) = GeneratedCommitMessage(title = title)
    }
}
