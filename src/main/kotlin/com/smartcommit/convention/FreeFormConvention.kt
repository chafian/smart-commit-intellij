package com.smartcommit.convention

import com.smartcommit.diff.model.ChangeCategory
import com.smartcommit.generator.model.GeneratedCommitMessage

/**
 * Free-form convention — no special formatting rules.
 *
 * The commit message is passed through as-is. The only rule enforced
 * is that the title starts with an uppercase letter and uses imperative mood.
 *
 * This is the simplest convention, suitable for projects without a
 * strict commit message policy.
 */
class FreeFormConvention : CommitConvention {

    override val displayName: String = "Free-form"

    override fun format(
        message: GeneratedCommitMessage,
        category: ChangeCategory,
        scope: String?
    ): GeneratedCommitMessage {
        val title = message.title.trim()
        if (title.isEmpty()) return message

        // Only transformation: capitalize first letter for consistency
        val formattedTitle = title[0].uppercaseChar() + title.substring(1)

        return if (formattedTitle == message.title) message
        else message.copy(title = formattedTitle)
    }

    override fun promptHint(): String = PROMPT_HINT

    companion object {
        internal const val PROMPT_HINT = """Convention: Free-form
- Write a clear, descriptive commit message in plain English.
- Start with an uppercase letter, imperative mood ("Add" not "Added").
- No required prefix, emoji, or structured format.
- Keep the title concise and specific (max 72 characters).
- Example: "Add user authentication with OAuth2 support"
- Example: "Fix crash when loading empty profile"
- Example: "Remove deprecated payment gateway integration"
- The body should explain WHY the change was made if not obvious."""
    }
}
