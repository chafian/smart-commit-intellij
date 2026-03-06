package com.smartcommit.branch

import java.util.concurrent.ConcurrentHashMap

/**
 * Parses Git branch names into structured [BranchContext].
 *
 * Supports common branching conventions used with GitHub, Jira, Linear, and GitLab:
 *
 * | Branch                                | Type   | Scope   | Ticket    | Description         |
 * |---------------------------------------|--------|---------|-----------|---------------------|
 * | `feature/JIRA-142-add-payment-api`    | feat   | —       | JIRA-142  | add payment API     |
 * | `feature/payment/JIRA-142-add-api`    | feat   | payment | JIRA-142  | add API             |
 * | `feature/payment-add-api`             | feat   | payment | —         | add API             |
 * | `fix/142-oauth-redirect`              | fix    | —       | #142      | OAuth redirect      |
 * | `bugfix/auth/JIRA-55`                 | fix    | auth    | JIRA-55   | —                   |
 * | `feat/add-login`                      | feat   | —       | —         | add login           |
 * | `JIRA-142-add-payment-api`            | —      | —       | JIRA-142  | add payment API     |
 * | `main`                                | —      | —       | —         | — (EMPTY/ignored)   |
 *
 * **Design principles:**
 * - Pure Kotlin — no IntelliJ APIs, no I/O, fully testable.
 * - Type normalization: `feature`→`feat`, `bugfix`→`fix`, `hotfix`→`fix`.
 * - Smart humanization: `add-payment-api` → `Add payment API` (acronyms preserved).
 * - Scope extraction: first non-verb word after type becomes scope.
 * - Nested branches: supports `type/scope/ticket-desc` and `type/scope/desc`.
 * - Caching: results cached in [ConcurrentHashMap] since branch rarely changes.
 */
object BranchNameParser {

    // ── Cache ───────────────────────────────────────────────

    private val cache = ConcurrentHashMap<String, BranchContext>()

    /**
     * Parse a Git branch name into a [BranchContext].
     *
     * Results are cached by branch name + custom pattern combination.
     *
     * @param branchName    The Git branch name (e.g. "feature/JIRA-142-add-payment-api").
     * @param customPattern Optional regex with named groups: `(?<type>...)`, `(?<ticket>...)`,
     *                      `(?<scope>...)`, `(?<description>...)`. Any group is optional.
     *                      If provided and valid, used instead of auto-detection.
     *                      If invalid, falls back to auto-detection.
     * @return Parsed [BranchContext], or [BranchContext.EMPTY] for default/ignored branches.
     */
    fun parse(branchName: String, customPattern: String? = null): BranchContext {
        if (branchName.isBlank()) return BranchContext.EMPTY

        val cacheKey = "$branchName|${customPattern.orEmpty()}"
        return cache.getOrPut(cacheKey) { doParse(branchName, customPattern) }
    }

    /** Clear the parse cache. Primarily for testing. */
    fun clearCache() {
        cache.clear()
    }

    // ── Core parsing ────────────────────────────────────────

    private fun doParse(branchName: String, customPattern: String?): BranchContext {
        // 1. Try custom regex first (if provided and valid)
        if (!customPattern.isNullOrBlank()) {
            val customResult = parseWithCustomRegex(branchName, customPattern)
            if (customResult != null) return customResult
            // Invalid regex or no match → fall through to auto-detect
        }

        // 2. Check if default/ignored branch
        if (isDefaultBranch(branchName)) {
            return BranchContext.EMPTY
        }

        // 3. Auto-detect
        return parseAutoDetect(branchName)
    }

    /**
     * Auto-detect parsing strategy.
     *
     * Split by `/` to handle nested branches:
     * - First segment: check if it's a known type prefix
     * - Middle segments: potential scope
     * - Last segment (or remainder): scan for ticket, then description
     */
    private fun parseAutoDetect(branchName: String): BranchContext {
        val segments = branchName.split("/").filter { it.isNotBlank() }
        if (segments.isEmpty()) return BranchContext.EMPTY

        var type: String? = null
        var scope: String? = null
        var ticket: String? = null
        var descriptionParts: String? = null

        // Determine if first segment is a type prefix
        val firstNormalized = normalizeType(segments[0])
        val hasTypePrefix = firstNormalized != null

        if (hasTypePrefix) {
            type = firstNormalized

            when {
                segments.size == 1 -> {
                    // Just "feature" or "feat" — type only, no description
                }
                segments.size == 2 -> {
                    // "feature/JIRA-142-add-payment-api" or "feature/payment-add-api"
                    val remainder = segments[1]
                    val parsed = parseRemainderSegment(remainder)
                    ticket = parsed.ticket
                    scope = parsed.scope
                    descriptionParts = parsed.description
                }
                else -> {
                    // Nested: "feature/payment/JIRA-142-add-api" or "feature/payment/add-api"
                    // segments[1] = scope (middle segment)
                    // segments[2..] = joined as the remainder
                    scope = segments[1]
                    val remainder = segments.drop(2).joinToString("/")
                    val parsed = parseRemainderSegment(remainder)
                    ticket = parsed.ticket
                    // Don't extract scope again from nested remainder
                    descriptionParts = parsed.description
                }
            }
        } else {
            // No type prefix: "JIRA-142-add-payment-api" or "add-dark-mode"
            val fullText = segments.joinToString("/")
            val parsed = parseRemainderSegment(fullText)
            ticket = parsed.ticket
            scope = parsed.scope
            descriptionParts = parsed.description
        }

        val humanizedDesc = descriptionParts?.let { humanize(it) }

        return BranchContext(
            rawBranchName = branchName,
            type = type,
            ticket = ticket,
            scope = scope,
            description = humanizedDesc,
            isDefault = false
        )
    }

    // ── Remainder segment parsing ───────────────────────────

    /**
     * Result of parsing a segment after the type prefix (and optionally scope).
     * E.g. for "JIRA-142-add-payment-api" → ticket=JIRA-142, description="add-payment-api", scope=null
     * E.g. for "payment-add-api" → ticket=null, description="add-api", scope="payment"
     */
    private data class SegmentParseResult(
        val ticket: String?,
        val scope: String?,
        val description: String?
    )

    /**
     * Parse a remainder segment for ticket, scope, and description.
     *
     * Strategy:
     * 1. Try to extract a Jira-style ticket at the start: `PROJ-123-...`
     * 2. Try to extract a bare number at the start: `142-...`
     * 3. Whatever remains after ticket extraction is the description slug
     * 4. If no ticket, try to extract scope from the first word (if it's not a verb)
     */
    private fun parseRemainderSegment(segment: String): SegmentParseResult {
        if (segment.isBlank()) return SegmentParseResult(null, null, null)

        var ticket: String? = null
        var descSlug: String? = null
        var scope: String? = null

        // Try Jira-style ticket at start: "JIRA-142-add-payment-api"
        val jiraMatch = JIRA_TICKET_AT_START.find(segment)
        if (jiraMatch != null) {
            ticket = jiraMatch.groupValues[1]
            val afterTicket = segment.substring(jiraMatch.range.last + 1).trimStart('-', '_')
            descSlug = afterTicket.ifBlank { null }
        }

        // Try bare number at start: "142-oauth-redirect"
        if (ticket == null) {
            val bareMatch = BARE_NUMBER_AT_START.find(segment)
            if (bareMatch != null) {
                ticket = "#${bareMatch.groupValues[1]}"
                val afterNumber = segment.substring(bareMatch.range.last + 1).trimStart('-', '_')
                descSlug = afterNumber.ifBlank { null }
            }
        }

        // No ticket found — the entire segment is the description slug
        if (ticket == null) {
            descSlug = segment
        }

        // Try to extract scope from description if no ticket was found
        // and no scope was already set by the caller (nested branch)
        if (descSlug != null && ticket == null) {
            val words = splitSlug(descSlug)
            if (words.size >= 2) {
                val firstWord = words[0].lowercase()
                if (firstWord !in VERB_WORDS && firstWord.length > 1) {
                    scope = firstWord
                    descSlug = words.drop(1).joinToString("-")
                }
            }
        }

        return SegmentParseResult(
            ticket = ticket,
            scope = scope,
            description = descSlug?.ifBlank { null }
        )
    }

    // ── Custom regex parsing ────────────────────────────────

    private fun parseWithCustomRegex(branchName: String, pattern: String): BranchContext? {
        return try {
            val regex = Regex(pattern)
            val match = regex.find(branchName) ?: return null

            val type = safeGroup(match, "type")?.let { normalizeType(it) ?: it }
            val ticket = safeGroup(match, "ticket")
            val scope = safeGroup(match, "scope")
            val description = safeGroup(match, "description")?.let { humanize(it) }

            BranchContext(
                rawBranchName = branchName,
                type = type,
                ticket = ticket,
                scope = scope,
                description = description,
                isDefault = false
            )
        } catch (_: IllegalArgumentException) {
            // Should not happen with safeGroup, but just in case
            null
        } catch (_: java.util.regex.PatternSyntaxException) {
            // Invalid regex — return null to fall back to auto-detect
            null
        }
    }

    /**
     * Safely extract a named group from a match result.
     * Returns null if the group doesn't exist in the pattern (instead of throwing).
     */
    private fun safeGroup(match: MatchResult, groupName: String): String? {
        return try {
            match.groups[groupName]?.value
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    // ── Type normalization ──────────────────────────────────

    /**
     * Normalize a branch type prefix to its canonical commit type.
     *
     * Returns null if the input is not a recognized type prefix.
     */
    internal fun normalizeType(raw: String): String? {
        return TYPE_NORMALIZATION[raw.lowercase()]
    }

    private val TYPE_NORMALIZATION = mapOf(
        "feature" to "feat",
        "feat" to "feat",
        "fix" to "fix",
        "bugfix" to "fix",
        "hotfix" to "fix",
        "chore" to "chore",
        "refactor" to "refactor",
        "docs" to "docs",
        "test" to "test",
        "ci" to "ci",
        "build" to "build",
        "style" to "style",
        "perf" to "perf"
    )

    // ── Default branch detection ────────────────────────────

    /**
     * Check if a branch name is a default/ignored branch.
     *
     * Exact matches: main, master, develop, dev, staging, production, HEAD
     * Prefix matches: release/, releases/
     */
    internal fun isDefaultBranch(name: String): Boolean {
        if (name in IGNORED_EXACT) return true
        return IGNORED_PREFIXES.any { name.startsWith(it) }
    }

    private val IGNORED_EXACT = setOf(
        "main", "master", "develop", "dev", "staging", "production", "HEAD"
    )

    private val IGNORED_PREFIXES = setOf(
        "release/", "releases/"
    )

    // ── Description humanization ────────────────────────────

    /**
     * Convert a slug (hyphen/underscore-separated) into a human-readable description.
     *
     * - Splits by `-` and `_`
     * - Preserves known uppercase acronyms (API, OAuth, JWT, etc.)
     * - Capitalizes the first word (unless it's an acronym)
     *
     * Examples:
     * - `"add-payment-api"` → `"add payment API"`
     * - `"fix-oauth-redirect"` → `"fix OAuth redirect"`
     * - `"update-jwt-validation"` → `"update JWT validation"`
     */
    internal fun humanize(slug: String): String {
        val words = splitSlug(slug)
        if (words.isEmpty()) return slug

        val humanized = words.mapIndexed { index, word ->
            val lower = word.lowercase()
            when {
                lower in UPPERCASE_WORDS -> UPPERCASE_WORDS_MAP[lower] ?: word.uppercase()
                index == 0 -> word.lowercase()
                else -> word.lowercase()
            }
        }

        return humanized.joinToString(" ")
    }

    /**
     * Split a slug by hyphens and underscores, filtering empty parts.
     */
    private fun splitSlug(slug: String): List<String> {
        return slug.split('-', '_').filter { it.isNotBlank() }
    }

    /**
     * Words that should always be fully uppercase in commit messages.
     *
     * Maps lowercase → correct casing. Most are simple uppercase,
     * but some have mixed casing (e.g. "oauth" → "OAuth").
     */
    private val UPPERCASE_WORDS_MAP = mapOf(
        "api" to "API",
        "oauth" to "OAuth",
        "jwt" to "JWT",
        "url" to "URL",
        "http" to "HTTP",
        "https" to "HTTPS",
        "json" to "JSON",
        "xml" to "XML",
        "html" to "HTML",
        "css" to "CSS",
        "sql" to "SQL",
        "id" to "ID",
        "ui" to "UI",
        "ux" to "UX",
        "cli" to "CLI",
        "sdk" to "SDK",
        "ssr" to "SSR",
        "sso" to "SSO",
        "cors" to "CORS",
        "crud" to "CRUD",
        "dto" to "DTO",
        "orm" to "ORM",
        "grpc" to "gRPC",
        "smtp" to "SMTP",
        "ssl" to "SSL",
        "tls" to "TLS",
        "tcp" to "TCP",
        "udp" to "UDP",
        "dns" to "DNS",
        "cdn" to "CDN",
        "ci" to "CI",
        "cd" to "CD",
        "io" to "IO",
        "ai" to "AI",
        "db" to "DB",
        "ws" to "WS",
        "wss" to "WSS",
        "graphql" to "GraphQL",
        "aws" to "AWS",
        "gcp" to "GCP",
        "s3" to "S3",
        "sqs" to "SQS",
        "sns" to "SNS"
    )

    /** Set of lowercase words for fast lookup. */
    private val UPPERCASE_WORDS = UPPERCASE_WORDS_MAP.keys

    /**
     * Common verb words that appear at the start of branch descriptions.
     * When the first word is a verb, it's NOT treated as a scope — it's part of the description.
     * When the first word is NOT a verb, it's likely a scope (module/component name).
     */
    private val VERB_WORDS = setOf(
        "add", "fix", "update", "remove", "delete", "implement", "create",
        "refactor", "resolve", "handle", "improve", "change", "set", "get",
        "move", "rename", "migrate", "integrate", "configure", "enable",
        "disable", "support", "replace", "merge", "split", "extract",
        "introduce", "deprecate", "revert", "bump", "upgrade", "downgrade",
        "setup", "init", "initialize", "cleanup", "clean", "optimize",
        "simplify", "rework", "redesign", "rebuild", "restore", "reset",
        "allow", "prevent", "enforce", "validate", "verify", "check",
        "ensure", "convert", "transform", "normalize", "format", "parse",
        "wrap", "unwrap", "register", "unregister", "connect", "disconnect",
        "show", "hide", "display", "render", "apply", "use"
    )

    // ── Ticket patterns ─────────────────────────────────────

    /**
     * Jira / Linear / Shortcut style ticket at the start of a segment.
     * Matches: `JIRA-142`, `PROJ-42`, `ENG-1`, followed by end-of-string or `-`/`_`.
     */
    private val JIRA_TICKET_AT_START = Regex("""^([A-Z][A-Z0-9]+-\d+)(?:[-_]|$)""")

    /**
     * Bare numeric ID at the start of a segment (GitHub/GitLab issue number).
     * Matches: `142` followed by `-` or `_` or end-of-string.
     */
    private val BARE_NUMBER_AT_START = Regex("""^(\d+)(?:[-_]|$)""")
}
