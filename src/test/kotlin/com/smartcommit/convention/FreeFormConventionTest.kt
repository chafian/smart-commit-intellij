package com.smartcommit.convention

import com.smartcommit.diff.model.ChangeCategory
import com.smartcommit.generator.model.GeneratedCommitMessage
import org.junit.Assert.*
import org.junit.Test

class FreeFormConventionTest {

    private val convention = FreeFormConvention()

    // ── Basic formatting ────────────────────────────────────

    @Test
    fun `format capitalizes first letter`() {
        val msg = GeneratedCommitMessage("add user authentication")
        val result = convention.format(msg, ChangeCategory.FEATURE)
        assertEquals("Add user authentication", result.title)
    }

    @Test
    fun `format preserves already capitalized title`() {
        val msg = GeneratedCommitMessage("Add user authentication")
        val result = convention.format(msg, ChangeCategory.FEATURE)
        assertEquals("Add user authentication", result.title)
    }

    @Test
    fun `format does not add any prefix or emoji`() {
        val msg = GeneratedCommitMessage("Fix null pointer in payment")
        val result = convention.format(msg, ChangeCategory.BUGFIX)
        assertEquals("Fix null pointer in payment", result.title)
        // No "fix:" prefix, no emoji
        assertFalse(result.title.contains(":"))
    }

    // ── Category is irrelevant ──────────────────────────────

    @Test
    fun `format produces same output regardless of category`() {
        val msg = GeneratedCommitMessage("Update user service")
        val results = ChangeCategory.entries.map { category ->
            convention.format(msg, category).title
        }.toSet()
        // All results should be identical
        assertEquals(1, results.size)
        assertEquals("Update user service", results.first())
    }

    // ── Scope is ignored ────────────────────────────────────

    @Test
    fun `format ignores scope parameter`() {
        val msg = GeneratedCommitMessage("Add auth endpoint")
        val result = convention.format(msg, ChangeCategory.FEATURE, scope = "auth")
        assertEquals("Add auth endpoint", result.title)
    }

    // ── Body and footer preserved ───────────────────────────

    @Test
    fun `format preserves body and footer`() {
        val msg = GeneratedCommitMessage(
            title = "add login",
            body = "Implemented OAuth2 flow",
            footer = "Closes #42"
        )
        val result = convention.format(msg, ChangeCategory.FEATURE)
        assertEquals("Add login", result.title)
        assertEquals("Implemented OAuth2 flow", result.body)
        assertEquals("Closes #42", result.footer)
    }

    // ── Edge cases ──────────────────────────────────────────

    @Test
    fun `format trims leading whitespace from title`() {
        val msg = GeneratedCommitMessage("  add user auth  ")
        val result = convention.format(msg, ChangeCategory.FEATURE)
        assertEquals("Add user auth", result.title)
    }

    @Test
    fun `format handles single character title`() {
        val msg = GeneratedCommitMessage("x")
        val result = convention.format(msg, ChangeCategory.CHORE)
        assertEquals("X", result.title)
    }

    // ── Prompt hint ─────────────────────────────────────────

    @Test
    fun `promptHint is not empty and mentions free-form`() {
        val hint = convention.promptHint()
        assertTrue(hint.isNotBlank())
        assertTrue(hint.contains("Free-form", ignoreCase = true))
    }

    // ── Display name ────────────────────────────────────────

    @Test
    fun `displayName is Free-form`() {
        assertEquals("Free-form", convention.displayName)
    }
}
