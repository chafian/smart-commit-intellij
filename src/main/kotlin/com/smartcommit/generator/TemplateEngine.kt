package com.smartcommit.generator

/**
 * Safe, eval-free template interpolation engine.
 *
 * Resolves `{{key}}` placeholders against a `Map<String, String>`.
 * Supports optional conditional blocks: `{{#key}}...{{/key}}` renders
 * the inner content only if the variable is present and non-blank.
 *
 * **Security:** No reflection, no code execution, no eval. Only simple
 * string replacement from an explicit variable map.
 *
 * Pure Kotlin — no framework dependencies.
 */
object TemplateEngine {

    /**
     * Regex matching `{{variableName}}` placeholders.
     * Variable names: alphanumeric + underscore + dot, 1-64 chars.
     */
    private val PLACEHOLDER_REGEX = Regex("""\{\{([a-zA-Z_][a-zA-Z0-9_.]{0,63})\}\}""")

    /**
     * Regex matching conditional blocks `{{#key}}...content...{{/key}}`.
     * Uses non-greedy match for the inner content.
     * Supports nesting of different keys but not same-key nesting.
     */
    private val CONDITIONAL_REGEX = Regex("""\{\{#([a-zA-Z_][a-zA-Z0-9_.]{0,63})\}\}(.*?)\{\{/\1\}\}""", RegexOption.DOT_MATCHES_ALL)

    /**
     * Interpolate a template string with the given variables.
     *
     * 1. Resolves conditional blocks first (can be nested with different keys).
     * 2. Then resolves simple `{{key}}` placeholders.
     * 3. Unresolved placeholders are replaced with an empty string.
     *
     * @param template  The template string containing `{{key}}` placeholders.
     * @param variables Map of variable name → value.
     * @return The interpolated string.
     */
    fun render(template: String, variables: Map<String, String>): String {
        // Step 1: Resolve conditional blocks (iterate until stable — handles nesting)
        var result = template
        var previousResult: String
        var iterations = 0
        val maxIterations = 10 // safety limit against pathological templates

        do {
            previousResult = result
            result = resolveConditionals(result, variables)
            iterations++
        } while (result != previousResult && iterations < maxIterations)

        // Step 2: Resolve simple placeholders
        result = resolvePlaceholders(result, variables)

        // Step 3: Clean up residual blank lines from removed conditionals
        result = collapseBlankLines(result)

        return result
    }

    /**
     * List all placeholder variable names found in a template.
     * Useful for documentation or validation.
     */
    fun extractVariableNames(template: String): Set<String> {
        val names = mutableSetOf<String>()
        PLACEHOLDER_REGEX.findAll(template).forEach { names.add(it.groupValues[1]) }
        CONDITIONAL_REGEX.findAll(template).forEach { names.add(it.groupValues[1]) }
        return names
    }

    // ── Private helpers ─────────────────────────────────────

    private fun resolveConditionals(template: String, variables: Map<String, String>): String {
        return CONDITIONAL_REGEX.replace(template) { match ->
            val key = match.groupValues[1]
            val innerContent = match.groupValues[2]
            val value = variables[key]

            if (!value.isNullOrBlank()) {
                // Condition met: keep inner content (will be further processed)
                innerContent
            } else {
                // Condition not met: remove entire block
                ""
            }
        }
    }

    private fun resolvePlaceholders(template: String, variables: Map<String, String>): String {
        return PLACEHOLDER_REGEX.replace(template) { match ->
            val key = match.groupValues[1]
            variables[key] ?: ""
        }
    }

    private fun collapseBlankLines(text: String): String {
        // Replace 3+ consecutive newlines with exactly 2 (one blank line)
        return text.replace(Regex("""\n{3,}"""), "\n\n").trim()
    }
}
