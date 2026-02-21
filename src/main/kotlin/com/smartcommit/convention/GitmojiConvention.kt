package com.smartcommit.convention

import com.smartcommit.diff.model.ChangeCategory
import com.smartcommit.generator.model.GeneratedCommitMessage

/**
 * Gitmoji convention — prefixes the commit title with an emoji that
 * represents the type of change.
 *
 * Standard mapping based on https://gitmoji.dev/:
 * - FEATURE  → ✨ (sparkles)
 * - BUGFIX   → 🐛 (bug)
 * - REFACTOR → ♻️  (recycle)
 * - TEST     → ✅ (check mark)
 * - DOCS     → 📝 (memo)
 * - STYLE    → 🎨 (palette)
 * - BUILD    → 📦 (package)
 * - CI       → 👷 (construction worker)
 * - CHORE    → 🔧 (wrench)
 *
 * This is the plugin's default convention.
 */
class GitmojiConvention : CommitConvention {

    override val displayName: String = "Gitmoji"

    override fun format(
        message: GeneratedCommitMessage,
        category: ChangeCategory,
        scope: String?
    ): GeneratedCommitMessage {
        val emoji = emojiFor(category)
        val title = message.title

        // Don't double-prefix if the AI already included the emoji
        val formattedTitle = if (startsWithEmoji(title)) {
            title
        } else {
            "$emoji ${title.trimStart()}"
        }

        return message.copy(title = formattedTitle)
    }

    override fun promptHint(): String = PROMPT_HINT

    /**
     * Returns the gitmoji emoji for a [ChangeCategory].
     */
    fun emojiFor(category: ChangeCategory): String = EMOJI_MAP.getValue(category)

    // ── Internals ───────────────────────────────────────────

    /**
     * Heuristic check: does the title already start with a known gitmoji?
     * Prevents double-prefixing when AI already formats correctly.
     */
    private fun startsWithEmoji(title: String): Boolean {
        if (title.isEmpty()) return false
        // Check against all known gitmoji in our map
        return EMOJI_MAP.values.any { emoji -> title.startsWith(emoji) }
    }

    companion object {

        /**
         * Category → Gitmoji mapping.
         * Uses the most common/recognizable gitmoji from https://gitmoji.dev/
         */
        val EMOJI_MAP: Map<ChangeCategory, String> = mapOf(
            ChangeCategory.FEATURE to "\u2728",    // ✨
            ChangeCategory.BUGFIX to "\uD83D\uDC1B",     // 🐛
            ChangeCategory.REFACTOR to "\u267B\uFE0F",   // ♻️
            ChangeCategory.TEST to "\u2705",       // ✅
            ChangeCategory.DOCS to "\uD83D\uDCDD",       // 📝
            ChangeCategory.STYLE to "\uD83C\uDFA8",      // 🎨
            ChangeCategory.BUILD to "\uD83D\uDCE6",      // 📦
            ChangeCategory.CI to "\uD83D\uDC77",         // 👷
            ChangeCategory.CHORE to "\uD83D\uDD27"       // 🔧
        )

        internal const val PROMPT_HINT = """Convention: Gitmoji
- Start the title with EXACTLY ONE gitmoji emoji followed by a space.
- Gitmoji mapping:
  ✨ new feature
  🐛 bug fix
  ♻️  refactor
  ✅ tests
  📝 documentation
  🎨 style/formatting
  📦 build/dependencies
  👷 CI/CD
  🔧 chore/config
  🔥 remove code/files
  🚀 deploy/release
  ⬆️  upgrade dependencies
  ⬇️  downgrade dependencies
  🔒 security fix
  💄 UI/cosmetic changes
- Do NOT include a type prefix like "feat:" after the emoji.
- Example: "✨ Add user authentication flow"
- Example: "🐛 Fix null pointer in payment processing"
- The emoji IS the type indicator — no additional prefix needed."""
    }
}
