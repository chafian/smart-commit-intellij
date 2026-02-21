package com.smartcommit.ai

import com.smartcommit.diff.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [PromptBuilder].
 * Pure JUnit — no IntelliJ dependencies, no network.
 */
class PromptBuilderTest {

    // ── Helpers ──────────────────────────────────────────────

    private fun fileDiff(
        path: String,
        type: ChangeType = ChangeType.MODIFIED,
        diff: String? = "+changed line",
        added: Int = 1,
        deleted: Int = 0
    ) = FileDiff(
        filePath = path,
        oldFilePath = null,
        changeType = type,
        fileExtension = path.substringAfterLast('.', ""),
        diff = diff,
        linesAdded = added,
        linesDeleted = deleted,
        isBinary = false
    )

    private fun summary(vararg diffs: FileDiff): DiffSummary {
        val diffList = diffs.toList()
        return DiffSummary(
            fileDiffs = diffList,
            classifications = diffList.associateWith { ChangeCategory.FEATURE }
        )
    }

    private val builder = PromptBuilder()

    // ── System prompt ───────────────────────────────────────

    @Test
    fun `system prompt contains JSON format instructions`() {
        val prompt = builder.buildSystemPrompt()
        assertTrue(prompt.contains("\"title\""))
        assertTrue(prompt.contains("\"body\""))
        assertTrue(prompt.contains("\"footer\""))
        assertTrue(prompt.contains("JSON"))
    }

    @Test
    fun `system prompt contains style rules`() {
        val prompt = builder.buildSystemPrompt()
        assertTrue(prompt.contains("imperative mood"))
        assertTrue(prompt.contains("72 characters"))
    }

    @Test
    fun `system prompt contains role description`() {
        val prompt = builder.buildSystemPrompt()
        assertTrue(prompt.contains("commit message generator"))
    }

    @Test
    fun `system prompt omits convention hint when empty`() {
        val prompt = PromptBuilder(conventionHint = "").buildSystemPrompt()
        assertFalse(prompt.contains("Convention-specific rules"))
    }

    @Test
    fun `system prompt includes convention hint when provided`() {
        val prompt = PromptBuilder(conventionHint = "Use gitmoji prefix").buildSystemPrompt()
        assertTrue(prompt.contains("Convention-specific rules"))
        assertTrue(prompt.contains("Use gitmoji prefix"))
    }

    // ── User prompt ─────────────────────────────────────────

    @Test
    fun `user prompt contains file list`() {
        val s = summary(fileDiff("src/main/Foo.kt"))
        val prompt = builder.buildUserPrompt(s)
        assertTrue(prompt.contains("src/main/Foo.kt"))
    }

    @Test
    fun `user prompt contains file count`() {
        val s = summary(fileDiff("a.kt"), fileDiff("b.kt"), fileDiff("c.kt"))
        val prompt = builder.buildUserPrompt(s)
        assertTrue(prompt.contains("Files Changed (3)"))
        assertTrue(prompt.contains("Files: 3"))
    }

    @Test
    fun `user prompt contains line statistics`() {
        val s = summary(fileDiff("a.kt", added = 10, deleted = 3))
        val prompt = builder.buildUserPrompt(s)
        assertTrue(prompt.contains("+10"))
        assertTrue(prompt.contains("-3"))
    }

    @Test
    fun `user prompt contains dominant category`() {
        val s = summary(fileDiff("a.kt"))
        val prompt = builder.buildUserPrompt(s)
        assertTrue(prompt.contains("feature"))
    }

    @Test
    fun `user prompt contains change type prefixes`() {
        val s = summary(
            fileDiff("new.kt", ChangeType.NEW),
            fileDiff("mod.kt", ChangeType.MODIFIED),
            fileDiff("del.kt", ChangeType.DELETED)
        )
        val prompt = builder.buildUserPrompt(s)
        assertTrue(prompt.contains("A "))
        assertTrue(prompt.contains("M "))
        assertTrue(prompt.contains("D "))
    }

    @Test
    fun `user prompt contains diff section`() {
        val s = summary(fileDiff("a.kt", diff = "+val x = 1"))
        val prompt = builder.buildUserPrompt(s)
        assertTrue(prompt.contains("## Diff:"))
        assertTrue(prompt.contains("+val x = 1"))
    }

    @Test
    fun `user prompt omits diff section when no diffs available`() {
        val fd = FileDiff(
            filePath = "image.png", oldFilePath = null,
            changeType = ChangeType.MODIFIED, fileExtension = "png",
            diff = null, linesAdded = 0, linesDeleted = 0, isBinary = true
        )
        val s = DiffSummary(listOf(fd), mapOf(fd to ChangeCategory.CHORE))
        val prompt = builder.buildUserPrompt(s)
        assertFalse(prompt.contains("## Diff:"))
    }

    @Test
    fun `user prompt respects maxDiffTokens`() {
        val longDiff = (1..1000).joinToString("\n") { "+line $it with padding content" }
        val s = summary(fileDiff("big.kt", diff = longDiff, added = 1000))
        val smallBuilder = PromptBuilder(maxDiffTokens = 50)
        val prompt = smallBuilder.buildUserPrompt(s)
        // Should be truncated — full diff at ~40k chars wouldn't fit in 50 tokens (~200 chars)
        assertTrue(prompt.contains("truncated"))
    }

    @Test
    fun `user prompt includes statistics for new and deleted file counts`() {
        val s = summary(
            fileDiff("new.kt", ChangeType.NEW),
            fileDiff("del.kt", ChangeType.DELETED, diff = "-old", added = 0, deleted = 1)
        )
        val prompt = builder.buildUserPrompt(s)
        assertTrue(prompt.contains("New files: 1"))
        assertTrue(prompt.contains("Deleted files: 1"))
    }
}
