package com.smartcommit.diff

import com.smartcommit.diff.model.*
import org.junit.Assert.assertEquals
import org.junit.Test

class ScopeDetectorTest {

    private fun fileDiff(
        path: String,
        changeType: ChangeType = ChangeType.MODIFIED
    ) = FileDiff(
        filePath = path,
        oldFilePath = null,
        changeType = changeType,
        fileExtension = path.substringAfterLast('.', ""),
        diff = null,
        linesAdded = 10,
        linesDeleted = 5,
        isBinary = false
    )

    private fun summary(vararg paths: String): DiffSummary {
        val diffs = paths.map { fileDiff(it) }
        return DiffSummary(
            fileDiffs = diffs,
            classifications = diffs.associateWith { ChangeCategory.FEATURE }
        )
    }

    @Test
    fun `detects module from multi-module Gradle project`() {
        val scope = ScopeDetector.detect(summary(
            "modules/auth/src/main/kotlin/Login.kt",
            "modules/auth/src/main/kotlin/Auth.kt"
        ))
        assertEquals("auth", scope)
    }

    @Test
    fun `detects module when build file is changed`() {
        val diffs = listOf(
            fileDiff("api/build.gradle.kts"),
            fileDiff("api/src/main/kotlin/Controller.kt")
        )
        val s = DiffSummary(
            fileDiffs = diffs,
            classifications = diffs.associateWith { ChangeCategory.BUILD }
        )
        assertEquals("api", ScopeDetector.detect(s))
    }

    @Test
    fun `detects meaningful scope skipping generic dirs`() {
        val scope = ScopeDetector.detect(summary(
            "src/main/java/com/example/auth/LoginService.java",
            "src/main/java/com/example/auth/AuthController.java"
        ))
        assertEquals("auth", scope)
    }

    @Test
    fun `returns empty for root-level files with no common scope`() {
        val scope = ScopeDetector.detect(summary(
            "README.md",
            "LICENSE"
        ))
        assertEquals("", scope)
    }

    @Test
    fun `detects scope from npm package structure`() {
        val diffs = listOf(
            fileDiff("packages/ui-kit/package.json"),
            fileDiff("packages/ui-kit/src/Button.tsx")
        )
        val s = DiffSummary(
            fileDiffs = diffs,
            classifications = diffs.associateWith { ChangeCategory.FEATURE }
        )
        assertEquals("ui-kit", ScopeDetector.detect(s))
    }

    @Test
    fun `detects scope from components directory`() {
        val scope = ScopeDetector.detect(summary(
            "components/UserProfile/index.tsx",
            "components/UserProfile/styles.css"
        ))
        assertEquals("UserProfile", scope)
    }

    @Test
    fun `returns empty for empty summary`() {
        val s = DiffSummary(fileDiffs = emptyList(), classifications = emptyMap())
        assertEquals("", ScopeDetector.detect(s))
    }

    @Test
    fun `single file returns its meaningful directory`() {
        val scope = ScopeDetector.detect(summary(
            "services/payment/PaymentProcessor.kt"
        ))
        assertEquals("payment", scope)
    }
}
