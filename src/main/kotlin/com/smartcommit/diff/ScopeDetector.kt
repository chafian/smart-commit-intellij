package com.smartcommit.diff

import com.smartcommit.diff.model.DiffSummary
import com.smartcommit.diff.model.FileDiff

/**
 * Detects a meaningful scope (module/component name) from changed files.
 *
 * Uses a multi-strategy approach:
 * 1. Detect known build system module markers (Gradle, Maven, npm, etc.)
 * 2. Detect well-known project structure patterns (e.g. `src/main/java/com/foo/bar`)
 * 3. Fall back to the deepest common directory segment
 *
 * Pure Kotlin — no IntelliJ APIs.
 */
object ScopeDetector {

    // Build files that mark module roots
    private val MODULE_MARKERS = setOf(
        "build.gradle",
        "build.gradle.kts",
        "pom.xml",
        "package.json",
        "Cargo.toml",
        "go.mod",
        "CMakeLists.txt",
        "setup.py",
        "pyproject.toml",
        "build.sbt",
        "Gemfile"
    )

    // Well-known top-level directories that are NOT meaningful scopes
    private val GENERIC_DIRS = setOf(
        "src", "main", "java", "kotlin", "scala", "resources",
        "test", "tests", "spec", "lib", "app", "com", "org", "net", "io"
    )

    /**
     * Detect the best scope string for a [DiffSummary].
     *
     * @return A short, meaningful scope name (e.g. "auth", "api", "user-service"),
     *         or empty string if no scope can be determined.
     */
    fun detect(summary: DiffSummary): String {
        if (summary.isEmpty) return ""

        // Strategy 1: If files touch a single module (identified by build file proximity)
        val moduleScope = detectModuleFromPaths(summary.fileDiffs)
        if (moduleScope.isNotEmpty()) return moduleScope

        // Strategy 2: Detect meaningful package/directory scope
        val packageScope = detectPackageScope(summary.fileDiffs)
        if (packageScope.isNotEmpty()) return packageScope

        // Strategy 3: Common directory fallback
        return detectCommonDirectory(summary.fileDiffs)
    }

    /**
     * Strategy 1: Detect module name from build system structure.
     *
     * In a multi-module project like:
     * ```
     * modules/auth/src/main/...
     * modules/api/src/main/...
     * ```
     * If all changed files are under `modules/auth/`, the scope is "auth".
     */
    private fun detectModuleFromPaths(diffs: List<FileDiff>): String {
        val paths = diffs.map { it.filePath }

        // Check if any changed file IS a build file — the parent dir is the module
        for (path in paths) {
            val fileName = path.substringAfterLast('/')
            if (fileName in MODULE_MARKERS) {
                val dir = path.substringBeforeLast('/', "")
                val moduleName = dir.substringAfterLast('/')
                if (moduleName.isNotEmpty() && moduleName !in GENERIC_DIRS) {
                    return moduleName
                }
            }
        }

        // Check for common multi-module patterns:
        // All files share a prefix like "modules/<name>/" or "<name>/src/"
        // Use directory parts only (exclude file names)
        val dirSegments = paths.map { path ->
            val dir = path.substringBeforeLast('/', "")
            dir.split('/').filter { it.isNotEmpty() }
        }.filter { it.isNotEmpty() }
        if (dirSegments.isEmpty()) return ""

        // Find the deepest common prefix that contains a non-generic meaningful name
        val commonLen = commonPrefixLength(dirSegments)
        if (commonLen == 0) return ""

        // Walk through common prefix segments and pick the last non-generic one
        val commonSegments = dirSegments[0].take(commonLen)
        val meaningful = commonSegments.lastOrNull { it !in GENERIC_DIRS && it.isNotBlank() }
        return meaningful ?: ""
    }

    /**
     * Strategy 2: Detect a meaningful package or component name.
     *
     * For Java/Kotlin files under `src/main/java/com/example/auth/...`,
     * extracts "auth" as the scope.
     *
     * For files under `components/UserProfile/...`, extracts "UserProfile".
     */
    private fun detectPackageScope(diffs: List<FileDiff>): String {
        val dirs = diffs.map { it.directory }.filter { it.isNotEmpty() }
        if (dirs.isEmpty()) return ""

        val commonPrefix = findCommonPathPrefix(dirs)
        if (commonPrefix.isEmpty()) return ""

        // Split into segments and find the last non-generic one
        val segments = commonPrefix.split('/')
        val meaningful = segments.lastOrNull { it !in GENERIC_DIRS && it.isNotBlank() }

        return meaningful ?: ""
    }

    /**
     * Strategy 3: Simple common directory fallback.
     * Returns the deepest shared directory segment.
     */
    private fun detectCommonDirectory(diffs: List<FileDiff>): String {
        val dirs = diffs.map { it.directory }.filter { it.isNotEmpty() }
        if (dirs.isEmpty()) return ""

        val common = findCommonPathPrefix(dirs)
        if (common.isEmpty()) return ""

        return common.trimEnd('/').substringAfterLast('/').ifEmpty { "" }
    }

    // ── Utilities ───────────────────────────────────────────

    private fun commonPrefixLength(segments: List<List<String>>): Int {
        if (segments.isEmpty()) return 0
        val minLen = segments.minOf { it.size }
        for (i in 0 until minLen) {
            val seg = segments[0][i]
            if (!segments.all { it[i] == seg }) return i
        }
        return minLen
    }

    private fun findCommonPathPrefix(paths: List<String>): String {
        if (paths.isEmpty()) return ""
        if (paths.size == 1) return paths[0]

        val segments = paths.map { it.split('/') }
        val minLen = segments.minOf { it.size }
        val common = mutableListOf<String>()

        for (i in 0 until minLen) {
            val segment = segments[0][i]
            if (segments.all { it[i] == segment }) {
                common.add(segment)
            } else {
                break
            }
        }

        return common.joinToString("/")
    }
}
