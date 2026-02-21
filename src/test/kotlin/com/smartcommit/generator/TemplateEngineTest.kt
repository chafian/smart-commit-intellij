package com.smartcommit.generator

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [TemplateEngine].
 * Pure JUnit — no IntelliJ dependencies.
 */
class TemplateEngineTest {

    // ── Simple placeholder interpolation ────────────────────

    @Test
    fun `render replaces single placeholder`() {
        val result = TemplateEngine.render("Hello {{name}}", mapOf("name" to "World"))
        assertEquals("Hello World", result)
    }

    @Test
    fun `render replaces multiple placeholders`() {
        val result = TemplateEngine.render(
            "{{type}}({{scope}}): {{summary}}",
            mapOf("type" to "feat", "scope" to "auth", "summary" to "add login")
        )
        assertEquals("feat(auth): add login", result)
    }

    @Test
    fun `render replaces duplicate placeholders`() {
        val result = TemplateEngine.render(
            "{{x}} and {{x}}",
            mapOf("x" to "same")
        )
        assertEquals("same and same", result)
    }

    @Test
    fun `render removes unresolved placeholders`() {
        val result = TemplateEngine.render("Hello {{unknown}}", emptyMap())
        assertEquals("Hello", result.trim())
    }

    @Test
    fun `render handles empty variables map`() {
        val result = TemplateEngine.render("no vars here", emptyMap())
        assertEquals("no vars here", result)
    }

    @Test
    fun `render handles template with no placeholders`() {
        val result = TemplateEngine.render("plain text", mapOf("unused" to "value"))
        assertEquals("plain text", result)
    }

    @Test
    fun `render handles empty template`() {
        val result = TemplateEngine.render("", mapOf("key" to "val"))
        assertEquals("", result)
    }

    // ── Placeholder name validation ─────────────────────────

    @Test
    fun `render supports underscores in variable names`() {
        val result = TemplateEngine.render("{{my_var}}", mapOf("my_var" to "ok"))
        assertEquals("ok", result)
    }

    @Test
    fun `render supports dots in variable names`() {
        val result = TemplateEngine.render("{{a.b}}", mapOf("a.b" to "dotted"))
        assertEquals("dotted", result)
    }

    @Test
    fun `render ignores malformed placeholders`() {
        // Missing closing braces, extra braces, etc. should be left as-is
        val result = TemplateEngine.render("{{valid}} {invalid} {{a}", mapOf("valid" to "ok"))
        assertEquals("ok {invalid} {{a}", result)
    }

    // ── Conditional blocks ──────────────────────────────────

    @Test
    fun `render includes conditional block when variable is present`() {
        val result = TemplateEngine.render(
            "prefix{{#scope}}({{scope}}){{/scope}}: text",
            mapOf("scope" to "auth")
        )
        assertEquals("prefix(auth): text", result)
    }

    @Test
    fun `render removes conditional block when variable is absent`() {
        val result = TemplateEngine.render(
            "prefix{{#scope}}({{scope}}){{/scope}}: text",
            emptyMap()
        )
        assertEquals("prefix: text", result)
    }

    @Test
    fun `render removes conditional block when variable is blank`() {
        val result = TemplateEngine.render(
            "prefix{{#scope}}({{scope}}){{/scope}}: text",
            mapOf("scope" to "  ")
        )
        assertEquals("prefix: text", result)
    }

    @Test
    fun `render handles nested conditionals with different keys`() {
        val template = "{{#a}}A{{#b}}B{{/b}}{{/a}}"
        assertEquals("AB", TemplateEngine.render(template, mapOf("a" to "1", "b" to "1")))
        assertEquals("A", TemplateEngine.render(template, mapOf("a" to "1")))
        assertEquals("", TemplateEngine.render(template, emptyMap()))
    }

    @Test
    fun `render handles multiline conditional blocks`() {
        val template = """
            Title
            {{#body}}
            Body: {{body}}
            {{/body}}
            End
        """.trimIndent()

        val withBody = TemplateEngine.render(template, mapOf("body" to "details"))
        assertTrue(withBody.contains("Body: details"))
        assertTrue(withBody.contains("Title"))

        val withoutBody = TemplateEngine.render(template, emptyMap())
        assertFalse(withoutBody.contains("Body"))
        assertTrue(withoutBody.contains("Title"))
    }

    // ── Blank line collapsing ───────────────────────────────

    @Test
    fun `render collapses excessive blank lines from removed blocks`() {
        val template = "Line1\n\n\n{{#missing}}stuff{{/missing}}\n\n\nLine2"
        val result = TemplateEngine.render(template, emptyMap())
        // Should not have more than one blank line between Line1 and Line2
        assertFalse(result.contains("\n\n\n"))
    }

    // ── extractVariableNames ────────────────────────────────

    @Test
    fun `extractVariableNames finds all variable names`() {
        val template = "{{type}}{{#scope}}({{scope}}){{/scope}}: {{summary}}"
        val names = TemplateEngine.extractVariableNames(template)
        assertEquals(setOf("type", "scope", "summary"), names)
    }

    @Test
    fun `extractVariableNames returns empty set for no placeholders`() {
        val names = TemplateEngine.extractVariableNames("plain text")
        assertTrue(names.isEmpty())
    }

    // ── Security: no eval ───────────────────────────────────

    @Test
    fun `render does not execute code-like content in placeholders`() {
        val malicious = mapOf("key" to "\${Runtime.getRuntime().exec(\"rm -rf /\")}")
        val result = TemplateEngine.render("{{key}}", malicious)
        // Should be treated as literal string
        assertTrue(result.contains("Runtime"))
    }

    @Test
    fun `render handles special regex characters in values safely`() {
        val result = TemplateEngine.render("{{val}}", mapOf("val" to "price is \$10.00 (USD)"))
        assertEquals("price is \$10.00 (USD)", result)
    }
}
