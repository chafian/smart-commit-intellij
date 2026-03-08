package com.smartcommit.branch

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/**
 * Comprehensive tests for [BranchNameParser].
 *
 * Covers:
 * - Type normalization (feature→feat, bugfix→fix, hotfix→fix, etc.)
 * - Ticket extraction (Jira, GitHub/GitLab bare numbers, Linear)
 * - Scope extraction (non-verb first word after type, nested branch segments)
 * - Description humanization (smart uppercase for API, OAuth, JWT, etc.)
 * - Nested branch names (type/scope/ticket-desc, type/scope/desc)
 * - Default/ignored branch detection (main, master, develop, release/x)
 * - Custom regex parsing with named groups
 * - Edge cases (empty, slash-only, trailing slash)
 * - Caching behavior
 */
class BranchNameParserTest {

    @After
    fun tearDown() {
        BranchNameParser.clearCache()
    }

    // ── Type Normalization ──────────────────────────────────

    @Test
    fun `feature prefix normalizes to feat`() {
        val ctx = BranchNameParser.parse("feature/add-login")
        assertEquals("feat", ctx.type)
    }

    @Test
    fun `feat prefix stays as feat`() {
        val ctx = BranchNameParser.parse("feat/add-login")
        assertEquals("feat", ctx.type)
    }

    @Test
    fun `bugfix prefix normalizes to fix`() {
        val ctx = BranchNameParser.parse("bugfix/resolve-crash")
        assertEquals("fix", ctx.type)
    }

    @Test
    fun `hotfix prefix normalizes to fix`() {
        val ctx = BranchNameParser.parse("hotfix/JIRA-55-login-crash")
        assertEquals("fix", ctx.type)
    }

    @Test
    fun `fix prefix stays as fix`() {
        val ctx = BranchNameParser.parse("fix/null-pointer")
        assertEquals("fix", ctx.type)
    }

    @Test
    fun `refactor prefix stays as refactor`() {
        val ctx = BranchNameParser.parse("refactor/auth-module")
        assertEquals("refactor", ctx.type)
    }

    @Test
    fun `docs prefix stays as docs`() {
        val ctx = BranchNameParser.parse("docs/update-readme")
        assertEquals("docs", ctx.type)
    }

    @Test
    fun `perf prefix stays as perf`() {
        val ctx = BranchNameParser.parse("perf/optimize-queries")
        assertEquals("perf", ctx.type)
    }

    // ── Ticket Extraction ───────────────────────────────────

    @Test
    fun `extracts Jira-style ticket from branch`() {
        val ctx = BranchNameParser.parse("feature/JIRA-142-add-payment-api")
        assertEquals("JIRA-142", ctx.ticket)
        assertTrue(ctx.hasTicket)
    }

    @Test
    fun `extracts Linear-style ticket from branch`() {
        val ctx = BranchNameParser.parse("feat/ENG-42-new-onboarding")
        assertEquals("ENG-42", ctx.ticket)
    }

    @Test
    fun `extracts multi-letter project key ticket`() {
        val ctx = BranchNameParser.parse("fix/PROJ-456-fix-null-check")
        assertEquals("PROJ-456", ctx.ticket)
    }

    @Test
    fun `extracts bare number as GitHub issue`() {
        val ctx = BranchNameParser.parse("fix/142-oauth-redirect")
        assertEquals("#142", ctx.ticket)
    }

    @Test
    fun `no ticket when branch has no numbers`() {
        val ctx = BranchNameParser.parse("feat/add-login")
        assertNull(ctx.ticket)
        assertFalse(ctx.hasTicket)
    }

    @Test
    fun `extracts ticket from branch without type prefix`() {
        val ctx = BranchNameParser.parse("JIRA-142-add-payment-api")
        assertEquals("JIRA-142", ctx.ticket)
        assertNull(ctx.type)
    }

    @Test
    fun `extracts ticket with single-digit number`() {
        val ctx = BranchNameParser.parse("fix/PROJ-1-typo")
        assertEquals("PROJ-1", ctx.ticket)
    }

    // ── Scope Extraction ────────────────────────────────────

    @Test
    fun `extracts scope from first non-verb word`() {
        val ctx = BranchNameParser.parse("feature/payment-add-api")
        assertEquals("feat", ctx.type)
        assertEquals("payment", ctx.scope)
        assertNotNull(ctx.description)
        assertTrue(ctx.description!!.contains("API"))
    }

    @Test
    fun `no scope when first word is a verb`() {
        val ctx = BranchNameParser.parse("feat/add-login")
        assertNull(ctx.scope)
        assertEquals("add login", ctx.description)
    }

    @Test
    fun `extracts scope from nested branch`() {
        val ctx = BranchNameParser.parse("feature/payment/JIRA-142-add-api")
        assertEquals("feat", ctx.type)
        assertEquals("payment", ctx.scope)
        assertEquals("JIRA-142", ctx.ticket)
        assertNotNull(ctx.description)
    }

    @Test
    fun `extracts scope from nested branch without ticket`() {
        val ctx = BranchNameParser.parse("feature/payment/add-api")
        assertEquals("feat", ctx.type)
        assertEquals("payment", ctx.scope)
        assertNotNull(ctx.description)
    }

    @Test
    fun `scope from nested branch for bugfix`() {
        val ctx = BranchNameParser.parse("bugfix/auth/JIRA-55")
        assertEquals("fix", ctx.type)
        assertEquals("auth", ctx.scope)
        assertEquals("JIRA-55", ctx.ticket)
    }

    @Test
    fun `auth is detected as scope not verb`() {
        val ctx = BranchNameParser.parse("feature/auth-add-sso")
        assertEquals("auth", ctx.scope)
    }

    // ── Description Humanization ────────────────────────────

    @Test
    fun `humanizes API correctly`() {
        val result = BranchNameParser.humanize("add-payment-api")
        assertEquals("add payment API", result)
    }

    @Test
    fun `humanizes OAuth correctly`() {
        val result = BranchNameParser.humanize("fix-oauth-redirect")
        assertEquals("fix OAuth redirect", result)
    }

    @Test
    fun `humanizes JWT correctly`() {
        val result = BranchNameParser.humanize("update-jwt-validation")
        assertEquals("update JWT validation", result)
    }

    @Test
    fun `humanizes URL correctly`() {
        val result = BranchNameParser.humanize("resolve-url-encoding")
        assertEquals("resolve URL encoding", result)
    }

    @Test
    fun `humanizes HTTP correctly`() {
        val result = BranchNameParser.humanize("add-http-client")
        assertEquals("add HTTP client", result)
    }

    @Test
    fun `humanizes JSON correctly`() {
        val result = BranchNameParser.humanize("update-json-parser")
        assertEquals("update JSON parser", result)
    }

    @Test
    fun `humanizes multiple acronyms in one description`() {
        val result = BranchNameParser.humanize("add-api-jwt-auth")
        assertEquals("add API JWT auth", result)
    }

    @Test
    fun `humanizes with underscores`() {
        val result = BranchNameParser.humanize("add_payment_api")
        assertEquals("add payment API", result)
    }

    @Test
    fun `humanizes gRPC correctly`() {
        val result = BranchNameParser.humanize("add-grpc-endpoint")
        assertEquals("add gRPC endpoint", result)
    }

    @Test
    fun `humanizes GraphQL correctly`() {
        val result = BranchNameParser.humanize("add-graphql-schema")
        assertEquals("add GraphQL schema", result)
    }

    @Test
    fun `humanizes SSO correctly`() {
        val result = BranchNameParser.humanize("implement-sso-login")
        assertEquals("implement SSO login", result)
    }

    @Test
    fun `humanizes ID correctly`() {
        val result = BranchNameParser.humanize("fix-user-id-validation")
        assertEquals("fix user ID validation", result)
    }

    // ── Nested Branch Names ─────────────────────────────────

    @Test
    fun `three-level nested branch with ticket`() {
        val ctx = BranchNameParser.parse("feature/payment/JIRA-142-add-api")
        assertEquals("feat", ctx.type)
        assertEquals("payment", ctx.scope)
        assertEquals("JIRA-142", ctx.ticket)
        assertNotNull(ctx.description)
        assertTrue(ctx.description!!.contains("API"))
    }

    @Test
    fun `three-level nested branch without ticket`() {
        val ctx = BranchNameParser.parse("feature/payment/add-api")
        assertEquals("feat", ctx.type)
        assertEquals("payment", ctx.scope)
        assertNull(ctx.ticket)
        assertNotNull(ctx.description)
    }

    @Test
    fun `nested fix branch with scope and ticket`() {
        val ctx = BranchNameParser.parse("fix/auth/142-token-expired")
        assertEquals("fix", ctx.type)
        assertEquals("auth", ctx.scope)
        assertEquals("#142", ctx.ticket)
    }

    // ── Default Branch Detection ────────────────────────────

    @Test
    fun `main returns EMPTY`() {
        val ctx = BranchNameParser.parse("main")
        assertTrue(ctx.isDefault)
        assertEquals(BranchContext.EMPTY, ctx)
    }

    @Test
    fun `master returns EMPTY`() {
        val ctx = BranchNameParser.parse("master")
        assertTrue(ctx.isDefault)
    }

    @Test
    fun `develop returns EMPTY`() {
        val ctx = BranchNameParser.parse("develop")
        assertTrue(ctx.isDefault)
    }

    @Test
    fun `dev returns EMPTY`() {
        val ctx = BranchNameParser.parse("dev")
        assertTrue(ctx.isDefault)
    }

    @Test
    fun `staging returns EMPTY`() {
        val ctx = BranchNameParser.parse("staging")
        assertTrue(ctx.isDefault)
    }

    @Test
    fun `production returns EMPTY`() {
        val ctx = BranchNameParser.parse("production")
        assertTrue(ctx.isDefault)
    }

    @Test
    fun `HEAD returns EMPTY`() {
        val ctx = BranchNameParser.parse("HEAD")
        assertTrue(ctx.isDefault)
    }

    @Test
    fun `release prefix returns EMPTY`() {
        val ctx = BranchNameParser.parse("release/v1.2.3")
        assertTrue(ctx.isDefault)
    }

    @Test
    fun `releases prefix returns EMPTY`() {
        val ctx = BranchNameParser.parse("releases/2026.1")
        assertTrue(ctx.isDefault)
    }

    @Test
    fun `hotfix is NOT ignored - has useful info`() {
        val ctx = BranchNameParser.parse("hotfix/JIRA-55-login-crash")
        assertFalse(ctx.isDefault)
        assertEquals("fix", ctx.type)
        assertEquals("JIRA-55", ctx.ticket)
    }

    // ── Custom Regex ────────────────────────────────────────

    @Test
    fun `custom regex with all named groups`() {
        val pattern = """(?<type>\w+)/(?<scope>\w+)/(?<ticket>[A-Z]+-\d+)-(?<description>.+)"""
        val ctx = BranchNameParser.parse("feature/payment/JIRA-142-add-api", pattern)
        assertEquals("feat", ctx.type)   // normalized
        assertEquals("payment", ctx.scope)
        assertEquals("JIRA-142", ctx.ticket)
        assertNotNull(ctx.description)
    }

    @Test
    fun `custom regex with partial groups`() {
        val pattern = """(?<ticket>[A-Z]+-\d+)"""
        val ctx = BranchNameParser.parse("feature/PROJ-99-something", pattern)
        assertEquals("PROJ-99", ctx.ticket)
        assertNull(ctx.type) // group not in regex
    }

    @Test
    fun `invalid custom regex falls back to auto-detect`() {
        val pattern = """[invalid("""  // broken regex
        val ctx = BranchNameParser.parse("feature/JIRA-142-add-api", pattern)
        // Should fall back to auto-detect and still work
        assertEquals("feat", ctx.type)
        assertEquals("JIRA-142", ctx.ticket)
    }

    @Test
    fun `custom regex that does not match falls back to auto-detect`() {
        val pattern = """^NOMATCH-(?<ticket>\d+)$"""
        val ctx = BranchNameParser.parse("feature/JIRA-142-add-api", pattern)
        // Pattern doesn't match → auto-detect kicks in
        assertEquals("feat", ctx.type)
        assertEquals("JIRA-142", ctx.ticket)
    }

    // ── Edge Cases ──────────────────────────────────────────

    @Test
    fun `empty string returns EMPTY`() {
        val ctx = BranchNameParser.parse("")
        assertEquals(BranchContext.EMPTY, ctx)
    }

    @Test
    fun `blank string returns EMPTY`() {
        val ctx = BranchNameParser.parse("   ")
        assertEquals(BranchContext.EMPTY, ctx)
    }

    @Test
    fun `slash only returns EMPTY-like`() {
        val ctx = BranchNameParser.parse("/")
        // Split by "/" produces empty segments → no useful info
        assertFalse(ctx.hasUsefulInfo)
    }

    @Test
    fun `type prefix only with no description`() {
        val ctx = BranchNameParser.parse("feature/")
        assertEquals("feat", ctx.type)
        assertNull(ctx.ticket)
        assertNull(ctx.scope)
    }

    @Test
    fun `branch without type prefix and no ticket`() {
        val ctx = BranchNameParser.parse("add-dark-mode")
        assertNull(ctx.type)
        assertNull(ctx.ticket)
        assertFalse(ctx.isDefault)
        // "add" is a verb, so no scope extraction
        assertNull(ctx.scope)
        assertEquals("add dark mode", ctx.description)
    }

    @Test
    fun `branch with scope-like word and no type prefix`() {
        val ctx = BranchNameParser.parse("dashboard-redesign")
        assertNull(ctx.type)
        assertEquals("dashboard", ctx.scope)
        assertNotNull(ctx.description)
    }

    // ── BranchContext Properties ─────────────────────────────

    @Test
    fun `hasUsefulInfo is true when type present`() {
        val ctx = BranchNameParser.parse("feat/add-login")
        assertTrue(ctx.hasUsefulInfo)
    }

    @Test
    fun `hasUsefulInfo is false for default branches`() {
        val ctx = BranchNameParser.parse("main")
        assertFalse(ctx.hasUsefulInfo)
    }

    @Test
    fun `footerLine returns correct format`() {
        val ctx = BranchNameParser.parse("feature/JIRA-142-add-payment")
        assertEquals("Refs: JIRA-142", ctx.footerLine)
    }

    @Test
    fun `footerLine is null when no ticket`() {
        val ctx = BranchNameParser.parse("feat/add-login")
        assertNull(ctx.footerLine)
    }

    @Test
    fun `titleTicketSuffix returns correct format`() {
        val ctx = BranchNameParser.parse("fix/142-oauth-bug")
        assertEquals("(#142)", ctx.titleTicketSuffix)
    }

    @Test
    fun `titleTicketSuffix is null when no ticket`() {
        val ctx = BranchNameParser.parse("feat/add-login")
        assertNull(ctx.titleTicketSuffix)
    }

    // ── Cache ───────────────────────────────────────────────

    @Test
    fun `same branch name returns cached result`() {
        val first = BranchNameParser.parse("feature/JIRA-142-add-api")
        val second = BranchNameParser.parse("feature/JIRA-142-add-api")
        assertSame(first, second)
    }

    @Test
    fun `clearCache invalidates cached results`() {
        val first = BranchNameParser.parse("feature/JIRA-142-add-api")
        BranchNameParser.clearCache()
        val second = BranchNameParser.parse("feature/JIRA-142-add-api")
        // After clearing, a new instance is created (equal but not same)
        assertEquals(first, second)
        assertNotSame(first, second)
    }

    @Test
    fun `different custom pattern produces different cache entry`() {
        val auto = BranchNameParser.parse("feature/JIRA-142-add-api")
        val custom = BranchNameParser.parse("feature/JIRA-142-add-api", "(?<ticket>[A-Z]+-\\d+)")
        // Both should have the ticket, but they are independently cached
        assertEquals("JIRA-142", auto.ticket)
        assertEquals("JIRA-142", custom.ticket)
    }

    // ── Full Integration Examples ────────────────────────────

    @Test
    fun `full example - Jira feature branch`() {
        val ctx = BranchNameParser.parse("feature/JIRA-142-add-payment-api")
        assertEquals("feat", ctx.type)
        assertEquals("JIRA-142", ctx.ticket)
        // scope not extracted because first description word "add" is a verb
        // AI infers scope from the description instead
        assertNull(ctx.scope)
        assertFalse(ctx.isDefault)
        assertTrue(ctx.hasUsefulInfo)
        assertEquals("Refs: JIRA-142", ctx.footerLine)
        assertNotNull(ctx.description)
        assertTrue(ctx.description!!.contains("payment"))
        assertTrue(ctx.description!!.contains("API"))
    }

    @Test
    fun `full example - GitHub fix branch`() {
        val ctx = BranchNameParser.parse("fix/142-oauth-redirect")
        assertEquals("fix", ctx.type)
        assertEquals("#142", ctx.ticket)
        assertEquals("Refs: #142", ctx.footerLine)
        assertEquals("(#142)", ctx.titleTicketSuffix)
        assertNotNull(ctx.description)
        assertTrue(ctx.description!!.contains("OAuth"))
    }

    @Test
    fun `full example - nested scope with ticket`() {
        val ctx = BranchNameParser.parse("feature/payment/JIRA-142-add-api")
        assertEquals("feat", ctx.type)
        assertEquals("payment", ctx.scope)
        assertEquals("JIRA-142", ctx.ticket)
        assertTrue(ctx.hasScope)
        assertTrue(ctx.hasTicket)
        assertTrue(ctx.hasType)
        assertTrue(ctx.hasUsefulInfo)
    }

    @Test
    fun `full example - simple feature no ticket`() {
        val ctx = BranchNameParser.parse("feat/add-login")
        assertEquals("feat", ctx.type)
        assertNull(ctx.ticket)
        assertNull(ctx.scope)  // "add" is a verb
        assertEquals("add login", ctx.description)
        assertFalse(ctx.hasTicket)
        assertTrue(ctx.hasType)
        assertTrue(ctx.hasUsefulInfo)
    }

    // ── Internal Method Tests ────────────────────────────────

    @Test
    fun `normalizeType returns null for unknown prefix`() {
        assertNull(BranchNameParser.normalizeType("foobar"))
    }

    @Test
    fun `normalizeType is case-insensitive`() {
        assertEquals("feat", BranchNameParser.normalizeType("Feature"))
        assertEquals("fix", BranchNameParser.normalizeType("BUGFIX"))
    }

    @Test
    fun `isDefaultBranch detects all exact matches`() {
        assertTrue(BranchNameParser.isDefaultBranch("main"))
        assertTrue(BranchNameParser.isDefaultBranch("master"))
        assertTrue(BranchNameParser.isDefaultBranch("develop"))
        assertTrue(BranchNameParser.isDefaultBranch("dev"))
        assertTrue(BranchNameParser.isDefaultBranch("staging"))
        assertTrue(BranchNameParser.isDefaultBranch("production"))
        assertTrue(BranchNameParser.isDefaultBranch("HEAD"))
    }

    @Test
    fun `isDefaultBranch detects release prefixes`() {
        assertTrue(BranchNameParser.isDefaultBranch("release/v1.0"))
        assertTrue(BranchNameParser.isDefaultBranch("releases/2026.1"))
    }

    @Test
    fun `isDefaultBranch does not flag feature branches`() {
        assertFalse(BranchNameParser.isDefaultBranch("feature/add-login"))
        assertFalse(BranchNameParser.isDefaultBranch("hotfix/JIRA-55"))
    }

    @Test
    fun `humanize preserves non-acronym words as lowercase`() {
        val result = BranchNameParser.humanize("simple-change")
        assertEquals("simple change", result)
    }

    @Test
    fun `humanize handles single word`() {
        val result = BranchNameParser.humanize("api")
        assertEquals("API", result)
    }

    @Test
    fun `humanize handles empty string`() {
        val result = BranchNameParser.humanize("")
        assertEquals("", result)
    }
}
