package com.smartcommit.diff

import com.smartcommit.diff.model.ChangeCategory
import com.smartcommit.diff.model.ChangeType
import com.smartcommit.diff.model.FileDiff

/**
 * Classifies a [FileDiff] into a semantic [ChangeCategory] based on
 * file path patterns and diff content heuristics.
 *
 * This is pure logic with no IntelliJ dependencies — fully unit-testable.
 */
object ChangeClassifier {

    // ── Path-based rules (checked first, most reliable) ─────

    private val TEST_PATH_PATTERNS = listOf(
        Regex("""(?i)[/\\]tests?[/\\]"""),
        Regex("""(?i)[/\\]__tests__[/\\]"""),
        Regex("""(?i)[/\\]spec[/\\]"""),
        Regex("""(?i)\.(?:test|spec|tests)\.\w+$"""),
        Regex("""(?i)Test\.\w+$"""),
    )

    private val DOC_EXTENSIONS = setOf(
        "md", "txt", "rst", "adoc", "asciidoc", "rdoc", "textile", "wiki", "org"
    )

    private val DOC_FILE_NAMES = setOf(
        "readme", "changelog", "license", "licence", "contributing",
        "authors", "code_of_conduct", "security", "history"
    )

    private val BUILD_FILE_NAMES = setOf(
        "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts",
        "pom.xml", "package.json", "package-lock.json", "yarn.lock",
        "gradle.properties", "gradle-wrapper.properties",
        "makefile", "cmake", "cmakelists.txt",
        "cargo.toml", "cargo.lock", "go.mod", "go.sum",
        "gemfile", "gemfile.lock", "requirements.txt", "setup.py", "pyproject.toml",
        "composer.json", "composer.lock",
    )

    private val BUILD_PATH_PATTERNS = listOf(
        Regex("""(?i)(?:^|[/\\])gradle[/\\]"""),
        Regex("""(?i)(?:^|[/\\])buildSrc[/\\]"""),
        Regex("""(?i)(?:^|[/\\])build-logic[/\\]"""),
    )

    private val CI_PATH_PATTERNS = listOf(
        Regex("""(?i)(?:^|[/\\])\.github[/\\]workflows[/\\]"""),
        Regex("""(?i)(?:^|[/\\])\.github[/\\]actions[/\\]"""),
        Regex("""(?i)(?:^|[/\\])\.circleci[/\\]"""),
        Regex("""(?i)(?:^|[/\\])\.gitlab-ci"""),
        Regex("""(?i)(?:^|[/\\])jenkinsfile"""),
        Regex("""(?i)(?:^|[/\\])\.travis\.yml$"""),
        Regex("""(?i)(?:^|[/\\])azure-pipelines"""),
    )

    private val STYLE_EXTENSIONS = setOf("css", "scss", "sass", "less", "styl")

    private val CONFIG_FILE_NAMES = setOf(
        ".gitignore", ".gitattributes", ".editorconfig",
        ".eslintrc", ".eslintrc.json", ".eslintrc.js", ".eslintrc.yml",
        ".prettierrc", ".prettierrc.json",
        "tsconfig.json", "jsconfig.json",
        ".dockerignore", "dockerfile",
        "docker-compose.yml", "docker-compose.yaml",
    )

    // ── Diff content heuristics ─────────────────────────────

    private val BUG_FIX_INDICATORS = listOf(
        Regex("""(?i)\bfix(?:ed|es|ing)?\b"""),
        Regex("""(?i)\bbug\b"""),
        Regex("""(?i)\bnull\s*(?:pointer|check|safe)\b"""),
        Regex("""(?i)\bNPE\b"""),
        Regex("""(?i)\bcatch\b.*\bexception\b"""),
        Regex("""(?i)\berror\s*handling\b"""),
        Regex("""(?i)\boff[- ]by[- ]one\b"""),
        Regex("""(?i)\brace\s*condition\b"""),
    )

    private val REFACTOR_INDICATORS = listOf(
        Regex("""(?i)\brefactor\b"""),
        Regex("""(?i)\brename[ds]?\b"""),
        Regex("""(?i)\bextract(?:ed|s|ing)?\b"""),
        Regex("""(?i)\bmov(?:ed?|ing)\b.*\b(?:method|function|class)\b"""),
        Regex("""(?i)\bcleanup\b"""),
        Regex("""(?i)\bsimplif(?:y|ied|ies)\b"""),
    )

    // ── Public API ──────────────────────────────────────────

    /**
     * Classify a single file diff into a [ChangeCategory].
     *
     * Classification priority:
     * 1. Path-based rules (test, docs, build, CI, style, config)
     * 2. Change-type heuristics (all-new → FEATURE, all-deleted → CHORE)
     * 3. Diff content heuristics (bug-fix patterns, refactor patterns)
     * 4. Default fallback → FEATURE for additions/modifications, CHORE for deletions
     */
    fun classify(fileDiff: FileDiff): ChangeCategory {
        // 1. Path-based classification (highest confidence)
        classifyByPath(fileDiff)?.let { return it }

        // 2. Change-type shortcut
        classifyByChangeType(fileDiff)?.let { return it }

        // 3. Diff content heuristics
        classifyByDiffContent(fileDiff)?.let { return it }

        // 4. Fallback
        return when (fileDiff.changeType) {
            ChangeType.NEW -> ChangeCategory.FEATURE
            ChangeType.DELETED -> ChangeCategory.CHORE
            else -> ChangeCategory.FEATURE
        }
    }

    /**
     * Classify all file diffs and return a map of each diff to its category.
     */
    fun classifyAll(fileDiffs: List<FileDiff>): Map<FileDiff, ChangeCategory> {
        return fileDiffs.associateWith { classify(it) }
    }

    // ── Internal classification steps ───────────────────────

    private fun classifyByPath(fileDiff: FileDiff): ChangeCategory? {
        val path = fileDiff.filePath
        val lowerPath = path.lowercase()
        val lowerFileName = fileDiff.fileName.lowercase()
        val ext = fileDiff.fileExtension

        // Test files
        if (TEST_PATH_PATTERNS.any { it.containsMatchIn(path) }) {
            return ChangeCategory.TEST
        }

        // CI/CD configs
        if (CI_PATH_PATTERNS.any { it.containsMatchIn(path) }) {
            return ChangeCategory.CI
        }

        // Build files
        if (BUILD_FILE_NAMES.any { lowerFileName == it || lowerPath.endsWith(it) }) {
            return ChangeCategory.BUILD
        }
        if (BUILD_PATH_PATTERNS.any { it.containsMatchIn(path) }) {
            return ChangeCategory.BUILD
        }

        // Documentation
        if (ext in DOC_EXTENSIONS) {
            return ChangeCategory.DOCS
        }
        if (DOC_FILE_NAMES.any { lowerFileName.startsWith(it) }) {
            return ChangeCategory.DOCS
        }

        // Style-only files
        if (ext in STYLE_EXTENSIONS) {
            return ChangeCategory.STYLE
        }

        // Config / chore files
        if (CONFIG_FILE_NAMES.any { lowerFileName == it }) {
            return ChangeCategory.CHORE
        }

        return null
    }

    private fun classifyByChangeType(fileDiff: FileDiff): ChangeCategory? {
        // Pure deletions of source files are typically chore/cleanup
        if (fileDiff.changeType == ChangeType.DELETED) {
            return ChangeCategory.CHORE
        }
        return null
    }

    private fun classifyByDiffContent(fileDiff: FileDiff): ChangeCategory? {
        val diff = fileDiff.diff ?: return null

        // Check for bug-fix indicators in added lines only
        val addedLines = diff.lines().filter { it.startsWith("+") && !it.startsWith("+++") }
        val addedText = addedLines.joinToString("\n")

        if (BUG_FIX_INDICATORS.any { it.containsMatchIn(addedText) }) {
            return ChangeCategory.BUGFIX
        }

        if (REFACTOR_INDICATORS.any { it.containsMatchIn(addedText) }) {
            return ChangeCategory.REFACTOR
        }

        return null
    }
}
