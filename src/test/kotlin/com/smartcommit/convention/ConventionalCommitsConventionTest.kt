package com.smartcommit.convention

import com.smartcommit.diff.model.ChangeCategory
import com.smartcommit.generator.model.GeneratedCommitMessage
import org.junit.Assert.*
import org.junit.Test

class ConventionalCommitsConventionTest {

    private val convention = ConventionalCommitsConvention()

    // ── Basic formatting ────────────────────────────────────

    @Test
    fun `format adds feat prefix for feature`() {
        val msg = GeneratedCommitMessage("Add user authentication")
        val result = convention.format(msg, ChangeCategory.FEATURE)
        assertEquals("feat: add user authentication", result.title)
    }

    @Test
    fun `format adds fix prefix for bugfix`() {
        val msg = GeneratedCommitMessage("Resolve null pointer in payment")
        val result = convention.format(msg, ChangeCategory.BUGFIX)
        assertEquals("fix: resolve null pointer in payment", result.title)
    }

    @Test
    fun `format adds refactor prefix for refactor`() {
        val msg = GeneratedCommitMessage("Extract validation logic")
        val result = convention.format(msg, ChangeCategory.REFACTOR)
        assertEquals("refactor: extract validation logic", result.title)
    }

    @Test
    fun `format adds test prefix for test`() {
        val msg = GeneratedCommitMessage("Add unit tests for UserService")
        val result = convention.format(msg, ChangeCategory.TEST)
        assertEquals("test: add unit tests for UserService", result.title)
    }

    @Test
    fun `format adds docs prefix for docs`() {
        val msg = GeneratedCommitMessage("Update README installation section")
        val result = convention.format(msg, ChangeCategory.DOCS)
        assertEquals("docs: update README installation section", result.title)
    }

    @Test
    fun `format adds style prefix for style`() {
        val msg = GeneratedCommitMessage("Fix indentation in main module")
        val result = convention.format(msg, ChangeCategory.STYLE)
        assertEquals("style: fix indentation in main module", result.title)
    }

    @Test
    fun `format adds build prefix for build`() {
        val msg = GeneratedCommitMessage("Update Gradle dependencies")
        val result = convention.format(msg, ChangeCategory.BUILD)
        assertEquals("build: update Gradle dependencies", result.title)
    }

    @Test
    fun `format adds ci prefix for ci`() {
        val msg = GeneratedCommitMessage("Add GitHub Actions workflow")
        val result = convention.format(msg, ChangeCategory.CI)
        assertEquals("ci: add GitHub Actions workflow", result.title)
    }

    @Test
    fun `format adds chore prefix for chore`() {
        val msg = GeneratedCommitMessage("Update gitignore patterns")
        val result = convention.format(msg, ChangeCategory.CHORE)
        assertEquals("chore: update gitignore patterns", result.title)
    }

    // ── All categories have type mapping ────────────────────

    @Test
    fun `every ChangeCategory has a mapped type`() {
        for (category in ChangeCategory.entries) {
            assertNotNull(
                "Missing type for $category",
                ConventionalCommitsConvention.TYPE_MAP[category]
            )
        }
    }

    // ── Scope handling ──────────────────────────────────────

    @Test
    fun `format includes scope when provided`() {
        val msg = GeneratedCommitMessage("Add login validation")
        val result = convention.format(msg, ChangeCategory.FEATURE, scope = "auth")
        assertEquals("feat(auth): add login validation", result.title)
    }

    @Test
    fun `format excludes scope when null`() {
        val msg = GeneratedCommitMessage("Add login validation")
        val result = convention.format(msg, ChangeCategory.FEATURE, scope = null)
        assertEquals("feat: add login validation", result.title)
    }

    @Test
    fun `format excludes scope when blank`() {
        val msg = GeneratedCommitMessage("Add login validation")
        val result = convention.format(msg, ChangeCategory.FEATURE, scope = "  ")
        assertEquals("feat: add login validation", result.title)
    }

    // ── No double-prefix ────────────────────────────────────

    @Test
    fun `format does not double-prefix if already conventional`() {
        val msg = GeneratedCommitMessage("feat(auth): add login validation")
        val result = convention.format(msg, ChangeCategory.FEATURE)
        assertEquals("feat(auth): add login validation", result.title)
    }

    @Test
    fun `format does not double-prefix for fix with scope`() {
        val msg = GeneratedCommitMessage("fix(payment): resolve null pointer")
        val result = convention.format(msg, ChangeCategory.BUGFIX)
        assertEquals("fix(payment): resolve null pointer", result.title)
    }

    @Test
    fun `format does not double-prefix for plain type colon`() {
        val msg = GeneratedCommitMessage("chore: update dependencies")
        val result = convention.format(msg, ChangeCategory.CHORE)
        assertEquals("chore: update dependencies", result.title)
    }

    // ── Preserves already-conventional titles ─────────────────

    @Test
    fun `format preserves already-conventional title even with different category`() {
        // AI returned "feat: ..." but we classify as fix — trust the AI's formatting
        // since the title already matches conventional format
        val msg = GeneratedCommitMessage("feat: add something")
        val result = convention.format(msg, ChangeCategory.BUGFIX)
        assertEquals("feat: add something", result.title)
    }

    @Test
    fun `format adds prefix to title that has loose prefix-like text but is not conventional`() {
        // "Feature add something" is not conventional format, so we should prefix it
        val msg = GeneratedCommitMessage("Feature add something")
        val result = convention.format(msg, ChangeCategory.BUGFIX)
        assertEquals("fix: feature add something", result.title)
    }

    // ── Body and footer preserved ───────────────────────────

    @Test
    fun `format preserves body and footer`() {
        val msg = GeneratedCommitMessage(
            title = "Add login",
            body = "Implemented OAuth2 flow",
            footer = "BREAKING CHANGE: new auth API"
        )
        val result = convention.format(msg, ChangeCategory.FEATURE)
        assertEquals("feat: add login", result.title)
        assertEquals("Implemented OAuth2 flow", result.body)
        assertEquals("BREAKING CHANGE: new auth API", result.footer)
    }

    // ── Lowercase first character ───────────────────────────

    @Test
    fun `format lowercases first character of description`() {
        val msg = GeneratedCommitMessage("Update README")
        val result = convention.format(msg, ChangeCategory.DOCS)
        assertEquals("docs: update README", result.title)
    }

    @Test
    fun `format preserves already lowercase description`() {
        val msg = GeneratedCommitMessage("update README")
        val result = convention.format(msg, ChangeCategory.DOCS)
        assertEquals("docs: update README", result.title)
    }

    // ── Prompt hint ─────────────────────────────────────────

    @Test
    fun `promptHint is not empty and mentions conventional commits`() {
        val hint = convention.promptHint()
        assertTrue(hint.isNotBlank())
        assertTrue(hint.contains("Conventional Commits", ignoreCase = true))
        assertTrue(hint.contains("feat"))
        assertTrue(hint.contains("fix"))
    }

    // ── Display name ────────────────────────────────────────

    @Test
    fun `displayName is Conventional Commits`() {
        assertEquals("Conventional Commits", convention.displayName)
    }

    // ── typeFor helper ──────────────────────────────────────

    @Test
    fun `typeFor returns correct type for each category`() {
        assertEquals("feat", convention.typeFor(ChangeCategory.FEATURE))
        assertEquals("fix", convention.typeFor(ChangeCategory.BUGFIX))
        assertEquals("refactor", convention.typeFor(ChangeCategory.REFACTOR))
        assertEquals("test", convention.typeFor(ChangeCategory.TEST))
        assertEquals("docs", convention.typeFor(ChangeCategory.DOCS))
        assertEquals("chore", convention.typeFor(ChangeCategory.CHORE))
    }
}
