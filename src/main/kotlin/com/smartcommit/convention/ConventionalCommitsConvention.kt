package com.smartcommit.convention

import com.smartcommit.diff.model.ChangeCategory
import com.smartcommit.generator.model.GeneratedCommitMessage

/**
 * Conventional Commits convention — formats titles as `type(scope): description`.
 *
 * Based on https://www.conventionalcommits.org/en/v1.0.0/
 *
 * Format: `<type>[optional scope]: <description>`
 * - type: feat, fix, refactor, test, docs, style, build, ci, chore
 * - scope: optional, in parentheses
 * - description: imperative mood, lowercase first letter
 *
 * Examples:
 * - `feat(auth): add login validation`
 * - `fix: resolve null pointer in payment service`
 * - `docs(readme): update installation instructions`
 */
class ConventionalCommitsConvention : CommitConvention {

    override val displayName: String = "Conventional Commits"

    override fun format(
        message: GeneratedCommitMessage,
        category: ChangeCategory,
        scope: String?
    ): GeneratedCommitMessage {
        val title = message.title
        val type = typeFor(category)

        // Don't double-prefix if the AI already formatted conventionally
        if (CONVENTIONAL_PATTERN.matches(title)) {
            return message
        }

        val formattedTitle = if (!scope.isNullOrBlank()) {
            "$type($scope): ${lowercaseFirst(title)}"
        } else {
            "$type: ${lowercaseFirst(title)}"
        }

        return message.copy(title = formattedTitle)
    }

    override fun promptHint(): String = PROMPT_HINT

    /**
     * Returns the Conventional Commits type string for a [ChangeCategory].
     */
    fun typeFor(category: ChangeCategory): String = TYPE_MAP.getValue(category)

    // ── Internals ───────────────────────────────────────────

    /**
     * Lowercase the first character of the description.
     * Conventional Commits spec recommends lowercase after the colon.
     * Preserves the rest of the string as-is.
     */
    private fun lowercaseFirst(text: String): String {
        if (text.isEmpty()) return text
        // Strip any existing conventional prefix the AI may have partially added
        val stripped = stripExistingPrefix(text)
        return if (stripped.isEmpty()) text
        else stripped[0].lowercaseChar() + stripped.substring(1)
    }

    /**
     * If the text already starts with a known type prefix (e.g. "feat: ..."),
     * strip it so we don't double-prefix.
     */
    private fun stripExistingPrefix(text: String): String {
        val match = LOOSE_PREFIX_PATTERN.find(text)
        return if (match != null) text.substring(match.range.last + 1).trimStart()
        else text
    }

    companion object {

        /**
         * Category → Conventional Commits type mapping.
         */
        val TYPE_MAP: Map<ChangeCategory, String> = mapOf(
            ChangeCategory.FEATURE to "feat",
            ChangeCategory.BUGFIX to "fix",
            ChangeCategory.REFACTOR to "refactor",
            ChangeCategory.TEST to "test",
            ChangeCategory.DOCS to "docs",
            ChangeCategory.STYLE to "style",
            ChangeCategory.BUILD to "build",
            ChangeCategory.CI to "ci",
            ChangeCategory.CHORE to "chore"
        )

        /**
         * Regex matching a fully-formed conventional commit title:
         * `type(scope): description` or `type: description`
         */
        private val CONVENTIONAL_PATTERN = Regex(
            """^(feat|fix|refactor|test|docs|style|build|ci|chore|perf|revert)(\([^)]+\))?!?:\s.+"""
        )

        /**
         * Regex matching a loose type prefix at the start of a string.
         * Used to strip accidental prefixes before reformatting.
         */
        private val LOOSE_PREFIX_PATTERN = Regex(
            """^(feat|fix|refactor|test|docs|style|build|ci|chore|perf|revert)(\([^)]+\))?!?:\s*"""
        )

        internal const val PROMPT_HINT = """Convention: Conventional Commits (https://www.conventionalcommits.org/)
- Title MUST follow the format: <type>[optional scope]: <description>
- Types: feat, fix, refactor, test, docs, style, build, ci, chore
- Scope is optional, in parentheses: feat(auth): add login
- Description starts with lowercase letter, imperative mood.
- Do NOT include emoji.
- Examples:
  feat: add user authentication flow
  fix(payment): resolve null pointer in checkout
  refactor(auth): extract validation logic to separate class
  docs: update API documentation
  chore: update dependencies
- For BREAKING CHANGES, add "!" before the colon: feat!: remove deprecated API
- Footer may include "BREAKING CHANGE: <description>" if applicable."""
    }
}
