package com.smartcommit.ai

import com.smartcommit.convention.CommitConvention
import com.smartcommit.diff.model.DiffSummary
import com.smartcommit.generator.CommitMessageGenerator
import com.smartcommit.generator.TemplateGenerator
import com.smartcommit.generator.model.GeneratedCommitMessage

/**
 * AI-powered [CommitMessageGenerator] implementation.
 *
 * Orchestration flow:
 * 1. [PromptBuilder] converts [DiffSummary] → system prompt + user prompt.
 * 2. [AiProvider] sends the prompt to an LLM and returns raw text.
 * 3. [AiResponse] parses the raw text into a [GeneratedCommitMessage].
 * 4. [CommitConvention.format] applies convention formatting (if convention is set).
 * 5. On any failure (network, parse, empty), falls back to [TemplateGenerator].
 *
 * The convention hint is baked into the prompt so the LLM generates
 * convention-conforming output. The [convention] `format()` is also applied
 * as post-processing to guarantee correctness even if the LLM ignores the hint.
 *
 * All dependencies are injected via constructor — no hardcoded config,
 * no IntelliJ APIs, no UI coupling.
 *
 * @param provider       The LLM provider to use (OpenAI, Ollama, etc.).
 * @param promptBuilder  Builds the prompt pair from a diff summary.
 * @param fallback       Generator to use when AI fails. Defaults to [TemplateGenerator].
 * @param maxTitleLength Maximum subject line length. Titles are truncated after generation.
 * @param convention     Optional commit convention for post-processing the AI output.
 */
class AiCommitMessageGenerator(
    private val provider: AiProvider,
    private val promptBuilder: PromptBuilder = PromptBuilder(),
    private val fallback: CommitMessageGenerator = TemplateGenerator(),
    private val maxTitleLength: Int = 72,
    private val convention: CommitConvention? = null
) : CommitMessageGenerator {

    override val displayName: String = "AI (${provider.name})"

    /**
     * Generate a commit message using the AI provider.
     *
     * **Contract: this method NEVER throws.** Every code path returns a
     * valid [GeneratedCommitMessage]. If the AI provider fails for any
     * reason (missing key, timeout, 429, invalid JSON, empty response,
     * or any unexpected exception), it falls back to [TemplateGenerator].
     *
     * This guarantee ensures the AI layer can never block the commit workflow.
     */
    override fun generate(summary: DiffSummary): GeneratedCommitMessage {
        // Guard: empty changeset → fallback immediately (no throw)
        if (summary.isEmpty) {
            return safeLastResort("Empty changeset")
        }

        return try {
            generateFromAi(summary)
        } catch (e: Exception) {
            // Absolute safety net — nothing escapes
            safeFallback(summary, reason = "Unexpected error: ${e.message}")
        }
    }

    // ── Core AI flow (may throw — caller catches) ───────────

    private fun generateFromAi(summary: DiffSummary): GeneratedCommitMessage {
        // Step 1: Build prompts
        val systemPrompt = promptBuilder.buildSystemPrompt()
        val userPrompt = promptBuilder.buildUserPrompt(summary)

        // Step 2: Call AI provider
        val completionResult = provider.complete(systemPrompt, userPrompt)

        // Step 3: Handle provider failure → fallback
        val rawResponse = completionResult.getOrElse {
            return safeFallback(summary, reason = "Provider error: ${it.message}")
        }

        if (rawResponse.isBlank()) {
            return safeFallback(summary, reason = "Empty AI response")
        }

        // Step 4: Parse the response
        val parseResult = AiResponse.parse(rawResponse)

        var message = parseResult.getOrElse {
            return safeFallback(summary, reason = "Parse error: ${it.message}")
        }

        // Step 5: Apply convention formatting (safety net even if LLM formatted correctly)
        if (convention != null) {
            message = convention.format(message, summary.dominantCategory)
        }

        // Step 6: Enforce title length and sanitize
        return message.sanitized().withTruncatedTitle(maxTitleLength)
    }

    // ── Fallback (also guarded) ─────────────────────────────

    private fun safeFallback(summary: DiffSummary, reason: String): GeneratedCommitMessage {
        return try {
            fallback.generate(summary)
        } catch (e: Exception) {
            // Even the fallback failed — produce an absolute last-resort message
            safeLastResort(reason)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun safeLastResort(reason: String): GeneratedCommitMessage {
        return GeneratedCommitMessage(title = "Update code")
    }
}
