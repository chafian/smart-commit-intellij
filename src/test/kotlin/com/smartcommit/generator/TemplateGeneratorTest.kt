package com.smartcommit.generator

import com.smartcommit.diff.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [TemplateGenerator].
 * Pure JUnit — no IntelliJ dependencies.
 */
class TemplateGeneratorTest {

    // ── Helpers ──────────────────────────────────────────────

    private fun fileDiff(
        path: String,
        type: ChangeType = ChangeType.MODIFIED,
        oldPath: String? = null,
        diff: String? = "+changed",
        added: Int = 1,
        deleted: Int = 0
    ) = FileDiff(
        filePath = path,
        oldFilePath = oldPath,
        changeType = type,
        fileExtension = path.substringAfterLast('.', ""),
        diff = diff,
        linesAdded = added,
        linesDeleted = deleted,
        isBinary = false
    )

    private fun summary(
        vararg diffs: FileDiff,
        classifications: Map<FileDiff, ChangeCategory>? = null
    ): DiffSummary {
        val diffList = diffs.toList()
        val classMap = classifications ?: diffList.associateWith { ChangeCategory.FEATURE }
        return DiffSummary(fileDiffs = diffList, classifications = classMap)
    }

    private val generator = TemplateGenerator()

    // ── Basic generation ────────────────────────────────────

    @Test
    fun `generate produces non-blank title`() {
        val fd = fileDiff("src/main/Foo.kt", ChangeType.NEW)
        val result = generator.generate(summary(fd))
        assertTrue(result.title.isNotBlank())
    }

    @Test
    fun `generate produces formatted message with format()`() {
        val fd = fileDiff("src/main/Foo.kt", ChangeType.NEW)
        val result = generator.generate(summary(fd))
        val formatted = result.format()
        assertTrue(formatted.isNotBlank())
        assertTrue(formatted.contains(result.title))
    }

    @Test(expected = IllegalStateException::class)
    fun `generate throws on empty summary`() {
        generator.generate(DiffSummary(emptyList(), emptyMap()))
    }

    // ── Title content ───────────────────────────────────────

    @Test
    fun `title contains the type label`() {
        val fd = fileDiff("src/main/Foo.kt")
        val classMap = mapOf(fd to ChangeCategory.BUGFIX)
        val result = generator.generate(summary(fd, classifications = classMap))
        assertTrue("Title should contain 'fix': ${result.title}", result.title.contains("fix"))
    }

    @Test
    fun `title includes scope when files share common directory`() {
        val fd1 = fileDiff("src/auth/Login.kt")
        val fd2 = fileDiff("src/auth/Session.kt")
        val result = generator.generate(summary(fd1, fd2))
        assertTrue("Title should contain scope 'auth': ${result.title}", result.title.contains("auth"))
    }

    @Test
    fun `title omits scope when files have no common directory`() {
        val fd1 = fileDiff("src/auth/Login.kt")
        val fd2 = fileDiff("lib/utils/Helper.kt")
        val result = generator.generate(summary(fd1, fd2))
        // Should not have "()" empty scope parens
        assertFalse("Title should not have empty parens: ${result.title}", result.title.contains("()"))
    }

    @Test
    fun `title for single new file includes filename`() {
        val fd = fileDiff("src/main/NewFeature.kt", ChangeType.NEW)
        val result = generator.generate(summary(fd))
        assertTrue("Title should include filename: ${result.title}", result.title.contains("NewFeature.kt"))
    }

    @Test
    fun `title for single deleted file includes filename`() {
        val fd = fileDiff("src/main/OldFile.kt", ChangeType.DELETED, diff = "-old", added = 0, deleted = 1)
        val classMap = mapOf(fd to ChangeCategory.CHORE)
        val result = generator.generate(summary(fd, classifications = classMap))
        assertTrue("Title should include filename: ${result.title}", result.title.contains("OldFile.kt"))
    }

    // ── Title truncation ────────────────────────────────────

    @Test
    fun `title is truncated to maxTitleLength`() {
        val longPathGen = TemplateGenerator(maxTitleLength = 30)
        val fd = fileDiff("src/very/deeply/nested/directory/structure/FileName.kt", ChangeType.NEW)
        val result = longPathGen.generate(summary(fd))
        assertTrue("Title should be <= 30 chars: '${result.title}' (${result.title.length})",
            result.title.length <= 30)
    }

    // ── Body content ────────────────────────────────────────

    @Test
    fun `body contains file count`() {
        val fd1 = fileDiff("src/A.kt")
        val fd2 = fileDiff("src/B.kt")
        val result = generator.generate(summary(fd1, fd2))
        assertNotNull(result.body)
        assertTrue("Body should contain file count: ${result.body}", result.body!!.contains("2 file(s) changed"))
    }

    @Test
    fun `body contains line stats`() {
        val fd = fileDiff("src/A.kt", added = 10, deleted = 3)
        val result = generator.generate(summary(fd))
        assertNotNull(result.body)
        assertTrue("Body should contain added lines", result.body!!.contains("+10"))
        assertTrue("Body should contain deleted lines", result.body!!.contains("-3"))
    }

    @Test
    fun `body contains file paths in breakdown`() {
        val fd = fileDiff("src/main/Service.kt", added = 5, deleted = 2)
        val result = generator.generate(summary(fd))
        assertNotNull(result.body)
        assertTrue("Body should contain file path", result.body!!.contains("src/main/Service.kt"))
    }

    @Test
    fun `body groups files by category`() {
        val fdFeat = fileDiff("src/Feature.kt")
        val fdTest = fileDiff("src/test/FeatureTest.kt")
        val classMap = mapOf(
            fdFeat to ChangeCategory.FEATURE,
            fdTest to ChangeCategory.TEST
        )
        val result = generator.generate(summary(fdFeat, fdTest, classifications = classMap))
        assertNotNull(result.body)
        assertTrue("Body should contain Features heading", result.body!!.contains("Features"))
        assertTrue("Body should contain Tests heading", result.body!!.contains("Tests"))
    }

    // ── Moved/Renamed files ─────────────────────────────────

    @Test
    fun `body shows old path for renamed files`() {
        val fd = fileDiff("src/NewName.kt", ChangeType.RENAMED, oldPath = "src/OldName.kt")
        val result = generator.generate(summary(fd))
        assertNotNull(result.body)
        assertTrue("Body should mention old path", result.body!!.contains("OldName.kt"))
    }

    // ── Custom templates ────────────────────────────────────

    @Test
    fun `custom title template is used`() {
        val gen = TemplateGenerator(titleTemplate = "[{{type}}] {{summary}}")
        val fd = fileDiff("src/Foo.kt")
        val classMap = mapOf(fd to ChangeCategory.BUGFIX)
        val result = gen.generate(summary(fd, classifications = classMap))
        assertTrue("Title should use custom format: ${result.title}", result.title.startsWith("[fix]"))
    }

    @Test
    fun `custom body template is used`() {
        val gen = TemplateGenerator(bodyTemplate = "Changed {{files_changed}} files")
        val fd1 = fileDiff("src/A.kt")
        val fd2 = fileDiff("src/B.kt")
        val result = gen.generate(summary(fd1, fd2))
        assertEquals("Changed 2 files", result.body)
    }

    // ── Variable map ────────────────────────────────────────

    @Test
    fun `buildVariableMap contains all expected keys`() {
        val fd = fileDiff("src/main/Service.kt", added = 5, deleted = 2)
        val sum = summary(fd)
        val vars = generator.buildVariableMap(sum)

        assertTrue(vars.containsKey("type"))
        assertTrue(vars.containsKey("scope"))
        assertTrue(vars.containsKey("summary"))
        assertTrue(vars.containsKey("files"))
        assertTrue(vars.containsKey("files_changed"))
        assertTrue(vars.containsKey("lines_added"))
        assertTrue(vars.containsKey("lines_deleted"))
        assertTrue(vars.containsKey("new_files"))
        assertTrue(vars.containsKey("modified_files"))
        assertTrue(vars.containsKey("deleted_files"))
        assertTrue(vars.containsKey("moved_files"))
        assertTrue(vars.containsKey("body_lines"))
    }

    @Test
    fun `buildVariableMap type matches dominant category`() {
        val fd = fileDiff("src/Test.kt")
        val classMap = mapOf(fd to ChangeCategory.TEST)
        val vars = generator.buildVariableMap(summary(fd, classifications = classMap))
        assertEquals("test", vars["type"])
    }

    @Test
    fun `buildVariableMap files_changed is correct count`() {
        val fd1 = fileDiff("a.kt")
        val fd2 = fileDiff("b.kt")
        val fd3 = fileDiff("c.kt")
        val vars = generator.buildVariableMap(summary(fd1, fd2, fd3))
        assertEquals("3", vars["files_changed"])
    }

    // ── displayName ─────────────────────────────────────────

    @Test
    fun `displayName returns Template`() {
        assertEquals("Template", generator.displayName)
    }
}
