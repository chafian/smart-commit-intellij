package com.smartcommit.diff

import com.smartcommit.diff.model.ChangeCategory
import com.smartcommit.diff.model.ChangeType
import com.smartcommit.diff.model.FileDiff
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [ChangeClassifier].
 * These are pure JUnit tests — no IntelliJ test framework needed.
 */
class ChangeClassifierTest {

    // ── Helper ──────────────────────────────────────────────

    private fun fileDiff(
        filePath: String,
        changeType: ChangeType = ChangeType.MODIFIED,
        diff: String? = null
    ): FileDiff {
        val ext = DiffUtils.extractExtension(filePath)
        val (added, deleted) = DiffUtils.countChangedLines(diff)
        return FileDiff(
            filePath = filePath,
            oldFilePath = null,
            changeType = changeType,
            fileExtension = ext,
            diff = diff,
            linesAdded = added,
            linesDeleted = deleted,
            isBinary = false
        )
    }

    // ── Test files ──────────────────────────────────────────

    @Test
    fun `test files in test directory are classified as TEST`() {
        assertEquals(ChangeCategory.TEST, ChangeClassifier.classify(fileDiff("src/test/kotlin/FooTest.kt")))
        assertEquals(ChangeCategory.TEST, ChangeClassifier.classify(fileDiff("src/tests/java/BarTest.java")))
        assertEquals(ChangeCategory.TEST, ChangeClassifier.classify(fileDiff("__tests__/component.test.tsx")))
    }

    @Test
    fun `test files with test extension are classified as TEST`() {
        assertEquals(ChangeCategory.TEST, ChangeClassifier.classify(fileDiff("src/utils.test.ts")))
        assertEquals(ChangeCategory.TEST, ChangeClassifier.classify(fileDiff("src/utils.spec.js")))
    }

    // ── Documentation ───────────────────────────────────────

    @Test
    fun `markdown files are classified as DOCS`() {
        assertEquals(ChangeCategory.DOCS, ChangeClassifier.classify(fileDiff("README.md")))
        assertEquals(ChangeCategory.DOCS, ChangeClassifier.classify(fileDiff("docs/guide.md")))
    }

    @Test
    fun `known doc file names are classified as DOCS`() {
        assertEquals(ChangeCategory.DOCS, ChangeClassifier.classify(fileDiff("CHANGELOG")))
        assertEquals(ChangeCategory.DOCS, ChangeClassifier.classify(fileDiff("LICENSE")))
        assertEquals(ChangeCategory.DOCS, ChangeClassifier.classify(fileDiff("CONTRIBUTING.md")))
    }

    @Test
    fun `text and rst files are classified as DOCS`() {
        assertEquals(ChangeCategory.DOCS, ChangeClassifier.classify(fileDiff("notes.txt")))
        assertEquals(ChangeCategory.DOCS, ChangeClassifier.classify(fileDiff("api.rst")))
    }

    // ── Build ───────────────────────────────────────────────

    @Test
    fun `build files are classified as BUILD`() {
        assertEquals(ChangeCategory.BUILD, ChangeClassifier.classify(fileDiff("build.gradle.kts")))
        assertEquals(ChangeCategory.BUILD, ChangeClassifier.classify(fileDiff("pom.xml")))
        assertEquals(ChangeCategory.BUILD, ChangeClassifier.classify(fileDiff("package.json")))
        assertEquals(ChangeCategory.BUILD, ChangeClassifier.classify(fileDiff("requirements.txt")))
    }

    @Test
    fun `files in gradle directory are classified as BUILD`() {
        assertEquals(ChangeCategory.BUILD, ChangeClassifier.classify(fileDiff("gradle/libs.versions.toml")))
        assertEquals(ChangeCategory.BUILD, ChangeClassifier.classify(fileDiff("buildSrc/build.gradle.kts")))
    }

    // ── CI ───────────────────────────────────────────────────

    @Test
    fun `CI workflow files are classified as CI`() {
        assertEquals(ChangeCategory.CI, ChangeClassifier.classify(fileDiff(".github/workflows/build.yml")))
        assertEquals(ChangeCategory.CI, ChangeClassifier.classify(fileDiff(".circleci/config.yml")))
    }

    // ── Style ───────────────────────────────────────────────

    @Test
    fun `CSS files are classified as STYLE`() {
        assertEquals(ChangeCategory.STYLE, ChangeClassifier.classify(fileDiff("src/app.css")))
        assertEquals(ChangeCategory.STYLE, ChangeClassifier.classify(fileDiff("src/theme.scss")))
    }

    // ── Config / Chore ──────────────────────────────────────

    @Test
    fun `config files are classified as CHORE`() {
        assertEquals(ChangeCategory.CHORE, ChangeClassifier.classify(fileDiff(".gitignore")))
        assertEquals(ChangeCategory.CHORE, ChangeClassifier.classify(fileDiff(".editorconfig")))
    }

    @Test
    fun `deleted source files are classified as CHORE`() {
        assertEquals(
            ChangeCategory.CHORE,
            ChangeClassifier.classify(fileDiff("src/main/OldClass.kt", ChangeType.DELETED))
        )
    }

    // ── Diff content heuristics ─────────────────────────────

    @Test
    fun `diff containing fix keywords is classified as BUGFIX`() {
        val diff = """
            -    val result = items.get(index)
            +    val result = items.getOrNull(index) // fix null pointer
        """.trimIndent()
        assertEquals(
            ChangeCategory.BUGFIX,
            ChangeClassifier.classify(fileDiff("src/main/Service.kt", diff = diff))
        )
    }

    @Test
    fun `diff containing refactor keywords is classified as REFACTOR`() {
        val diff = """
            +    // Refactored to extract common logic
            +    private fun commonSetup() {
        """.trimIndent()
        assertEquals(
            ChangeCategory.REFACTOR,
            ChangeClassifier.classify(fileDiff("src/main/Service.kt", diff = diff))
        )
    }

    // ── Fallback ────────────────────────────────────────────

    @Test
    fun `new source file with no keywords defaults to FEATURE`() {
        assertEquals(
            ChangeCategory.FEATURE,
            ChangeClassifier.classify(fileDiff("src/main/NewFeature.kt", ChangeType.NEW))
        )
    }

    @Test
    fun `modified source file with no keywords defaults to FEATURE`() {
        val diff = "+    val name: String = \"hello\""
        assertEquals(
            ChangeCategory.FEATURE,
            ChangeClassifier.classify(fileDiff("src/main/Model.kt", diff = diff))
        )
    }

    // ── classifyAll ─────────────────────────────────────────

    @Test
    fun `classifyAll returns correct map for mixed changes`() {
        val diffs = listOf(
            fileDiff("src/test/FooTest.kt"),
            fileDiff("README.md"),
            fileDiff("src/main/Feature.kt", ChangeType.NEW),
        )
        val result = ChangeClassifier.classifyAll(diffs)

        assertEquals(3, result.size)
        assertEquals(ChangeCategory.TEST, result[diffs[0]])
        assertEquals(ChangeCategory.DOCS, result[diffs[1]])
        assertEquals(ChangeCategory.FEATURE, result[diffs[2]])
    }
}
