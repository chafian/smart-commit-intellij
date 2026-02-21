package com.smartcommit.convention

import com.smartcommit.diff.model.ChangeCategory
import com.smartcommit.generator.model.GeneratedCommitMessage

/**
 * Formats a [GeneratedCommitMessage] according to a specific commit message convention.
 *
 * Conventions transform the raw generated message (title/body/footer) into
 * the convention's required format. For example, Gitmoji prepends an emoji,
 * Conventional Commits wraps the type in `type(scope): ...` format, etc.
 *
 * Conventions also provide hints for AI prompt engineering so that the LLM
 * generates messages already conforming to the convention's style.
 *
 * Implementations must be stateless, pure functions — no IntelliJ API dependencies.
 */
interface CommitConvention {

    /** Human-readable display name for settings UI. */
    val displayName: String

    /**
     * Format a commit message according to this convention.
     *
     * @param message  The raw generated message (title + optional body/footer).
     * @param category The dominant change category, used for prefix/emoji selection.
     * @param scope    Optional scope (module/component name). Null if not detected.
     * @return A new [GeneratedCommitMessage] formatted per this convention.
     */
    fun format(
        message: GeneratedCommitMessage,
        category: ChangeCategory,
        scope: String? = null
    ): GeneratedCommitMessage

    /**
     * Returns convention-specific rules to include in the AI system prompt.
     * This guides the LLM to generate messages already in the correct format.
     *
     * @return Multi-line string of convention rules, or empty if no special rules.
     */
    fun promptHint(): String
}

/**
 * Enum of available convention types, for use in settings/serialization.
 */
enum class ConventionType(val displayName: String) {
    GITMOJI("Gitmoji"),
    CONVENTIONAL("Conventional Commits"),
    FREEFORM("Free-form");

    override fun toString(): String = displayName

    /**
     * Create the corresponding [CommitConvention] instance.
     */
    fun createConvention(): CommitConvention = when (this) {
        GITMOJI -> GitmojiConvention()
        CONVENTIONAL -> ConventionalCommitsConvention()
        FREEFORM -> FreeFormConvention()
    }
}
