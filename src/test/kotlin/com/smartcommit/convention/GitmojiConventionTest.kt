package com.smartcommit.convention

import com.smartcommit.diff.model.ChangeCategory
import com.smartcommit.generator.model.GeneratedCommitMessage
import org.junit.Assert.*
import org.junit.Test

class GitmojiConventionTest {

    private val convention = GitmojiConvention()

    // ── Basic formatting ────────────────────────────────────

    @Test
    fun `format prepends sparkles emoji for feature`() {
        val msg = GeneratedCommitMessage("Add user authentication")
        val result = convention.format(msg, ChangeCategory.FEATURE)
        assertEquals("\u2728 Add user authentication", result.title)
    }

    @Test
    fun `format prepends bug emoji for bugfix`() {
        val msg = GeneratedCommitMessage("Fix null pointer in payment")
        val result = convention.format(msg, ChangeCategory.BUGFIX)
        assertEquals("\uD83D\uDC1B Fix null pointer in payment", result.title)
    }

    @Test
    fun `format prepends recycle emoji for refactor`() {
        val msg = GeneratedCommitMessage("Extract validation logic")
        val result = convention.format(msg, ChangeCategory.REFACTOR)
        assertEquals("\u267B\uFE0F Extract validation logic", result.title)
    }

    @Test
    fun `format prepends check emoji for test`() {
        val msg = GeneratedCommitMessage("Add unit tests for UserService")
        val result = convention.format(msg, ChangeCategory.TEST)
        assertEquals("\u2705 Add unit tests for UserService", result.title)
    }

    @Test
    fun `format prepends memo emoji for docs`() {
        val msg = GeneratedCommitMessage("Update README")
        val result = convention.format(msg, ChangeCategory.DOCS)
        assertEquals("\uD83D\uDCDD Update README", result.title)
    }

    @Test
    fun `format prepends palette emoji for style`() {
        val msg = GeneratedCommitMessage("Fix indentation")
        val result = convention.format(msg, ChangeCategory.STYLE)
        assertEquals("\uD83C\uDFA8 Fix indentation", result.title)
    }

    @Test
    fun `format prepends package emoji for build`() {
        val msg = GeneratedCommitMessage("Update dependencies")
        val result = convention.format(msg, ChangeCategory.BUILD)
        assertEquals("\uD83D\uDCE6 Update dependencies", result.title)
    }

    @Test
    fun `format prepends worker emoji for ci`() {
        val msg = GeneratedCommitMessage("Add GitHub Actions workflow")
        val result = convention.format(msg, ChangeCategory.CI)
        assertEquals("\uD83D\uDC77 Add GitHub Actions workflow", result.title)
    }

    @Test
    fun `format prepends wrench emoji for chore`() {
        val msg = GeneratedCommitMessage("Update gitignore")
        val result = convention.format(msg, ChangeCategory.CHORE)
        assertEquals("\uD83D\uDD27 Update gitignore", result.title)
    }

    // ── All categories have emoji ───────────────────────────

    @Test
    fun `every ChangeCategory has a mapped emoji`() {
        for (category in ChangeCategory.entries) {
            assertNotNull(
                "Missing emoji for $category",
                GitmojiConvention.EMOJI_MAP[category]
            )
        }
    }

    // ── No double-prefix ────────────────────────────────────

    @Test
    fun `format does not double-prefix if emoji already present`() {
        val msg = GeneratedCommitMessage("\u2728 Add user authentication")
        val result = convention.format(msg, ChangeCategory.FEATURE)
        assertEquals("\u2728 Add user authentication", result.title)
    }

    @Test
    fun `format does not double-prefix with different category emoji`() {
        // AI returned bug emoji but we classify as feature — keep AI's emoji
        val msg = GeneratedCommitMessage("\uD83D\uDC1B Fix the crash")
        val result = convention.format(msg, ChangeCategory.FEATURE)
        // Title already starts with a known emoji, don't double-prefix
        assertEquals("\uD83D\uDC1B Fix the crash", result.title)
    }

    // ── Body and footer preserved ───────────────────────────

    @Test
    fun `format preserves body and footer`() {
        val msg = GeneratedCommitMessage(
            title = "Add login",
            body = "Implemented OAuth2 flow",
            footer = "Closes #42"
        )
        val result = convention.format(msg, ChangeCategory.FEATURE)
        assertEquals("\u2728 Add login", result.title)
        assertEquals("Implemented OAuth2 flow", result.body)
        assertEquals("Closes #42", result.footer)
    }

    // ── Scope is ignored (gitmoji doesn't use scope) ────────

    @Test
    fun `format ignores scope parameter`() {
        val msg = GeneratedCommitMessage("Add auth endpoint")
        val result = convention.format(msg, ChangeCategory.FEATURE, scope = "auth")
        // Gitmoji doesn't include scope in the title
        assertEquals("\u2728 Add auth endpoint", result.title)
    }

    // ── Prompt hint ─────────────────────────────────────────

    @Test
    fun `promptHint is not empty and mentions gitmoji`() {
        val hint = convention.promptHint()
        assertTrue(hint.isNotBlank())
        assertTrue(hint.contains("Gitmoji", ignoreCase = true))
        assertTrue(hint.contains("\u2728")) // sparkles emoji
        assertTrue(hint.contains("\uD83D\uDC1B")) // bug emoji
    }

    // ── Display name ────────────────────────────────────────

    @Test
    fun `displayName is Gitmoji`() {
        assertEquals("Gitmoji", convention.displayName)
    }

    // ── emojiFor helper ─────────────────────────────────────

    @Test
    fun `emojiFor returns correct emoji for each category`() {
        assertEquals("\u2728", convention.emojiFor(ChangeCategory.FEATURE))
        assertEquals("\uD83D\uDC1B", convention.emojiFor(ChangeCategory.BUGFIX))
        assertEquals("\u267B\uFE0F", convention.emojiFor(ChangeCategory.REFACTOR))
    }
}
