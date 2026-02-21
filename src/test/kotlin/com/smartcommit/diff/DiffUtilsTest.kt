package com.smartcommit.diff

import com.smartcommit.diff.model.ChangeType
import com.smartcommit.diff.model.FileDiff
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [DiffUtils].
 * Pure JUnit tests — no IntelliJ test framework needed.
 */
class DiffUtilsTest {

    // ── extractExtension ────────────────────────────────────

    @Test
    fun `extractExtension returns lowercase extension`() {
        assertEquals("kt", DiffUtils.extractExtension("src/main/Foo.kt"))
        assertEquals("java", DiffUtils.extractExtension("src/main/Bar.JAVA"))
        // extractExtension only returns the last extension segment
        assertEquals("kts", DiffUtils.extractExtension("build.gradle.kts"))
    }

    @Test
    fun `extractExtension returns empty for no extension`() {
        assertEquals("", DiffUtils.extractExtension("Makefile"))
        assertEquals("", DiffUtils.extractExtension("Dockerfile"))
    }

    @Test
    fun `extractExtension handles nested paths`() {
        assertEquals("yml", DiffUtils.extractExtension(".github/workflows/build.yml"))
    }

    // ── isBinaryContent ─────────────────────────────────────

    @Test
    fun `isBinaryContent returns false for null`() {
        assertFalse(DiffUtils.isBinaryContent(null))
    }

    @Test
    fun `isBinaryContent returns false for normal text`() {
        assertFalse(DiffUtils.isBinaryContent("fun main() { println(\"hello\") }"))
    }

    @Test
    fun `isBinaryContent returns true for content with null bytes`() {
        assertTrue(DiffUtils.isBinaryContent("PK\u0000\u0000binary content"))
    }

    // ── countChangedLines ───────────────────────────────────

    @Test
    fun `countChangedLines returns zero for null`() {
        assertEquals(0 to 0, DiffUtils.countChangedLines(null))
    }

    @Test
    fun `countChangedLines returns zero for blank`() {
        assertEquals(0 to 0, DiffUtils.countChangedLines(""))
    }

    @Test
    fun `countChangedLines counts added and deleted lines`() {
        val diff = """
            +added line 1
            +added line 2
            -deleted line 1
             context line
            +added line 3
        """.trimIndent()
        assertEquals(3 to 1, DiffUtils.countChangedLines(diff))
    }

    @Test
    fun `countChangedLines ignores diff header markers`() {
        val diff = """
            --- a/file.kt
            +++ b/file.kt
            +real added line
            -real deleted line
        """.trimIndent()
        assertEquals(1 to 1, DiffUtils.countChangedLines(diff))
    }

    // ── estimateTokens ──────────────────────────────────────

    @Test
    fun `estimateTokens returns reasonable estimate`() {
        // 4 chars per token
        assertEquals(1, DiffUtils.estimateTokens("abc"))    // 3 chars → ceil(3/4) = 1
        assertEquals(1, DiffUtils.estimateTokens("abcd"))   // 4 chars → 1
        assertEquals(3, DiffUtils.estimateTokens("hello world")) // 11 chars → ceil(11/4) = 3
    }

    @Test
    fun `estimateTokens returns 0 for empty string`() {
        assertEquals(0, DiffUtils.estimateTokens(""))
    }

    // ── computeSimpleDiff ───────────────────────────────────

    @Test
    fun `computeSimpleDiff returns null for both null`() {
        assertNull(DiffUtils.computeSimpleDiff(null, null))
    }

    @Test
    fun `computeSimpleDiff for new file shows all lines as added`() {
        val diff = DiffUtils.computeSimpleDiff(null, "line1\nline2\nline3")
        assertNotNull(diff)
        assertEquals("+line1\n+line2\n+line3", diff)
    }

    @Test
    fun `computeSimpleDiff for deleted file shows all lines as removed`() {
        val diff = DiffUtils.computeSimpleDiff("line1\nline2", null)
        assertNotNull(diff)
        assertEquals("-line1\n-line2", diff)
    }

    @Test
    fun `computeSimpleDiff shows modifications`() {
        val before = "line1\nline2\nline3"
        val after = "line1\nmodified\nline3"
        val diff = DiffUtils.computeSimpleDiff(before, after)
        assertNotNull(diff)
        assertTrue(diff!!.contains("-line2"))
        assertTrue(diff.contains("+modified"))
        assertTrue(diff.contains(" line1"))
        assertTrue(diff.contains(" line3"))
    }

    // ── formatFileList ──────────────────────────────────────

    @Test
    fun `formatFileList produces correct prefixes`() {
        val diffs = listOf(
            makeFileDiff("src/New.kt", ChangeType.NEW),
            makeFileDiff("src/Mod.kt", ChangeType.MODIFIED),
            makeFileDiff("src/Del.kt", ChangeType.DELETED),
        )
        val result = DiffUtils.formatFileList(diffs)
        assertTrue(result.contains("A  src/New.kt"))
        assertTrue(result.contains("M  src/Mod.kt"))
        assertTrue(result.contains("D  src/Del.kt"))
    }

    @Test
    fun `formatFileList shows old path for renames`() {
        val diff = FileDiff(
            filePath = "src/NewName.kt",
            oldFilePath = "src/OldName.kt",
            changeType = ChangeType.RENAMED,
            fileExtension = "kt",
            diff = null,
            linesAdded = 0,
            linesDeleted = 0,
            isBinary = false
        )
        val result = DiffUtils.formatFileList(listOf(diff))
        assertTrue(result.contains("src/OldName.kt"))
        assertTrue(result.contains("src/NewName.kt"))
    }

    // ── truncateDiffs ───────────────────────────────────────

    @Test
    fun `truncateDiffs returns empty for empty list`() {
        assertEquals("", DiffUtils.truncateDiffs(emptyList(), 1000))
    }

    @Test
    fun `truncateDiffs includes file header and diff content`() {
        val diffs = listOf(
            makeFileDiff("src/Foo.kt", ChangeType.MODIFIED, "+added line")
        )
        val result = DiffUtils.truncateDiffs(diffs, 1000)
        assertTrue(result.contains("src/Foo.kt"))
        assertTrue(result.contains("+added line"))
    }

    @Test
    fun `truncateDiffs truncates when exceeding token budget`() {
        val longDiff = (1..500).joinToString("\n") { "+line number $it with some content padding" }
        val diffs = listOf(
            makeFileDiff("src/Big.kt", ChangeType.MODIFIED, longDiff)
        )
        val result = DiffUtils.truncateDiffs(diffs, 50)
        assertTrue(result.contains("truncated"))
    }

    // ── Helper ──────────────────────────────────────────────

    private fun makeFileDiff(
        path: String,
        type: ChangeType,
        diff: String? = null
    ): FileDiff {
        val (added, deleted) = DiffUtils.countChangedLines(diff)
        return FileDiff(
            filePath = path,
            oldFilePath = null,
            changeType = type,
            fileExtension = DiffUtils.extractExtension(path),
            diff = diff,
            linesAdded = added,
            linesDeleted = deleted,
            isBinary = false
        )
    }
}
