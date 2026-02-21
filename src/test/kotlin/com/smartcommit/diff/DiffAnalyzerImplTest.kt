package com.smartcommit.diff

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.smartcommit.diff.model.ChangeType
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [DiffAnalyzerImpl].
 * Uses MockK to mock IntelliJ API types ([Change], [ContentRevision], [FilePath]).
 * This verifies the mapping logic without requiring a running IDE.
 */
class DiffAnalyzerImplTest {

    // ── Helpers ─────────────────────────────────────────────

    private fun mockFilePath(path: String): FilePath {
        val fp = mockk<FilePath>()
        every { fp.path } returns path
        every { fp.name } returns path.substringAfterLast('/')
        return fp
    }

    private fun mockRevision(path: String, content: String?): ContentRevision {
        val rev = mockk<ContentRevision>()
        every { rev.file } returns mockFilePath(path)
        every { rev.content } returns content
        every { rev.revisionNumber } returns VcsRevisionNumber.NULL
        return rev
    }

    private fun mockChange(
        beforePath: String?,
        afterPath: String?,
        beforeContent: String? = "old content",
        afterContent: String? = "new content",
        type: Change.Type = Change.Type.MODIFICATION
    ): Change {
        val change = mockk<Change>()
        val before = if (beforePath != null) mockRevision(beforePath, beforeContent) else null
        val after = if (afterPath != null) mockRevision(afterPath, afterContent) else null
        every { change.beforeRevision } returns before
        every { change.afterRevision } returns after
        every { change.type } returns type
        every { change.isRenamed } returns false
        every { change.isMoved } returns false
        return change
    }

    // ── Tests ───────────────────────────────────────────────

    @Test
    fun `extractFileDiffs handles new file`() {
        val change = mockChange(
            beforePath = null,
            afterPath = "src/main/NewFile.kt",
            beforeContent = null,
            afterContent = "class NewFile {}",
            type = Change.Type.NEW
        )

        val analyzer = DiffAnalyzerImpl(listOf(change))
        val diffs = analyzer.extractFileDiffs()

        assertEquals(1, diffs.size)
        val diff = diffs[0]
        assertEquals("src/main/NewFile.kt", diff.filePath)
        assertNull(diff.oldFilePath)
        assertEquals(ChangeType.NEW, diff.changeType)
        assertEquals("kt", diff.fileExtension)
        assertTrue(diff.linesAdded > 0)
        assertEquals(0, diff.linesDeleted)
        assertFalse(diff.isBinary)
    }

    @Test
    fun `extractFileDiffs handles deleted file`() {
        val change = mockChange(
            beforePath = "src/main/OldFile.kt",
            afterPath = null,
            beforeContent = "class OldFile {}",
            afterContent = null,
            type = Change.Type.DELETED
        )

        val analyzer = DiffAnalyzerImpl(listOf(change))
        val diffs = analyzer.extractFileDiffs()

        assertEquals(1, diffs.size)
        val diff = diffs[0]
        assertEquals("src/main/OldFile.kt", diff.filePath)
        assertEquals(ChangeType.DELETED, diff.changeType)
        assertEquals(0, diff.linesAdded)
        assertTrue(diff.linesDeleted > 0)
    }

    @Test
    fun `extractFileDiffs handles modified file`() {
        val change = mockChange(
            beforePath = "src/main/Service.kt",
            afterPath = "src/main/Service.kt",
            beforeContent = "fun old() {}",
            afterContent = "fun new() {}",
            type = Change.Type.MODIFICATION
        )

        val analyzer = DiffAnalyzerImpl(listOf(change))
        val diffs = analyzer.extractFileDiffs()

        assertEquals(1, diffs.size)
        val diff = diffs[0]
        assertEquals("src/main/Service.kt", diff.filePath)
        assertEquals(ChangeType.MODIFIED, diff.changeType)
        assertNotNull(diff.diff)
        assertTrue(diff.hasDiff)
    }

    @Test
    fun `extractFileDiffs handles moved file`() {
        val change = mockChange(
            beforePath = "src/old/File.kt",
            afterPath = "src/new/File.kt",
            type = Change.Type.MOVED
        )

        val analyzer = DiffAnalyzerImpl(listOf(change))
        val diffs = analyzer.extractFileDiffs()

        assertEquals(1, diffs.size)
        val diff = diffs[0]
        assertEquals("src/new/File.kt", diff.filePath)
        assertEquals("src/old/File.kt", diff.oldFilePath)
        assertEquals(ChangeType.MOVED, diff.changeType)
    }

    @Test
    fun `extractFileDiffs handles renamed file`() {
        val change = mockChange(
            beforePath = "src/OldName.kt",
            afterPath = "src/NewName.kt",
            type = Change.Type.MOVED
        )
        every { change.isRenamed } returns true

        val analyzer = DiffAnalyzerImpl(listOf(change))
        val diffs = analyzer.extractFileDiffs()

        assertEquals(1, diffs.size)
        assertEquals(ChangeType.RENAMED, diffs[0].changeType)
        assertEquals("src/OldName.kt", diffs[0].oldFilePath)
        assertEquals("src/NewName.kt", diffs[0].filePath)
    }

    @Test
    fun `extractFileDiffs detects binary content`() {
        val change = mockChange(
            beforePath = "assets/image.png",
            afterPath = "assets/image.png",
            beforeContent = "PNG\u0000\u0000binary",
            afterContent = "PNG\u0000\u0000modified binary"
        )

        val analyzer = DiffAnalyzerImpl(listOf(change))
        val diffs = analyzer.extractFileDiffs()

        assertEquals(1, diffs.size)
        assertTrue(diffs[0].isBinary)
        assertNull(diffs[0].diff)
    }

    @Test
    fun `extractFileDiffs handles multiple changes`() {
        val changes = listOf(
            mockChange("src/A.kt", "src/A.kt", "a", "a modified", Change.Type.MODIFICATION),
            mockChange(null, "src/B.kt", null, "new B", Change.Type.NEW),
            mockChange("src/C.kt", null, "old C", null, Change.Type.DELETED),
        )

        val analyzer = DiffAnalyzerImpl(changes)
        val diffs = analyzer.extractFileDiffs()

        assertEquals(3, diffs.size)
        assertEquals(ChangeType.MODIFIED, diffs[0].changeType)
        assertEquals(ChangeType.NEW, diffs[1].changeType)
        assertEquals(ChangeType.DELETED, diffs[2].changeType)
    }

    @Test
    fun `extractFileDiffs survives content retrieval failure`() {
        val revision = mockk<ContentRevision>()
        every { revision.file } returns mockFilePath("src/Error.kt")
        every { revision.content } throws RuntimeException("Simulated VCS error")
        every { revision.revisionNumber } returns VcsRevisionNumber.NULL

        val change = mockk<Change>()
        every { change.beforeRevision } returns revision
        every { change.afterRevision } returns revision
        every { change.type } returns Change.Type.MODIFICATION
        every { change.isRenamed } returns false
        every { change.isMoved } returns false

        val analyzer = DiffAnalyzerImpl(listOf(change))
        val diffs = analyzer.extractFileDiffs()

        // Should still produce a diff entry (with null diff content)
        assertEquals(1, diffs.size)
        assertEquals("src/Error.kt", diffs[0].filePath)
    }

    @Test
    fun `analyze produces DiffSummary with classifications`() {
        val changes = listOf(
            mockChange(null, "src/test/FooTest.kt", null, "test content", Change.Type.NEW),
            mockChange("README.md", "README.md", "old", "new", Change.Type.MODIFICATION),
        )

        val analyzer = DiffAnalyzerImpl(changes)
        val summary = analyzer.analyze()

        assertEquals(2, summary.totalFiles)
        assertFalse(summary.isEmpty)
        assertTrue(summary.classifications.isNotEmpty())
    }

    @Test
    fun `extractFileDiffs returns empty list for empty changes`() {
        val analyzer = DiffAnalyzerImpl(emptyList())
        val diffs = analyzer.extractFileDiffs()
        assertTrue(diffs.isEmpty())
    }
}
