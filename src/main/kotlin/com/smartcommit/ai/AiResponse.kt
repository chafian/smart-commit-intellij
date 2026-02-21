package com.smartcommit.ai

import com.smartcommit.generator.model.GeneratedCommitMessage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Safe parser for AI-generated JSON responses.
 *
 * Expected JSON format from the LLM:
 * ```json
 * {
 *   "title": "feat(auth): add login validation",
 *   "body": "Add email format check and password strength validation...",
 *   "footer": "Closes #42"
 * }
 * ```
 *
 * If JSON parsing fails (malformed, missing fields, or the LLM returned
 * free-text instead of JSON), the [parseFallback] method extracts a
 * best-effort message from the raw text.
 *
 * Pure Kotlin + kotlinx.serialization. No IntelliJ APIs.
 */
object AiResponse {

    /**
     * Kotlinx serialization DTO matching the expected LLM JSON output.
     */
    @Serializable
    internal data class CommitMessageDto(
        val title: String,
        val body: String? = null,
        val footer: String? = null
    )

    /** Lenient JSON parser — ignores unknown keys, allows trailing commas. */
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Parse the raw AI response string into a [GeneratedCommitMessage].
     *
     * Strategy:
     * 1. Try to parse as JSON → [CommitMessageDto].
     * 2. If the response contains a JSON object embedded in markdown fences,
     *    extract it and retry.
     * 3. If all JSON parsing fails, fall back to [parseFallback].
     *
     * @param raw The raw string returned by [AiProvider.complete].
     * @return A valid [GeneratedCommitMessage], never null.
     *         Returns a best-effort result even for garbage input.
     */
    fun parse(raw: String): Result<GeneratedCommitMessage> {
        val trimmed = raw.trim()

        // Strategy 1: Direct JSON parse
        tryParseJson(trimmed)?.let { return Result.success(it) }

        // Strategy 2: Extract JSON from markdown code fences
        val extracted = extractJsonBlock(trimmed)
        if (extracted != null) {
            tryParseJson(extracted)?.let { return Result.success(it) }
        }

        // Strategy 3: Best-effort fallback from raw text
        val fallback = parseFallback(trimmed)
        return if (fallback != null) {
            Result.success(fallback)
        } else {
            Result.failure(AiParseException("Unable to parse AI response into a commit message"))
        }
    }

    // ── Internal parsing ────────────────────────────────────

    /**
     * Attempt to deserialize JSON into a [GeneratedCommitMessage].
     * Returns null on any failure.
     */
    internal fun tryParseJson(text: String): GeneratedCommitMessage? {
        return try {
            val dto = json.decodeFromString<CommitMessageDto>(text)
            if (dto.title.isBlank()) return null
            GeneratedCommitMessage(
                title = sanitizeTitle(dto.title),
                body = dto.body?.trim()?.ifBlank { null },
                footer = dto.footer?.trim()?.ifBlank { null }
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extract a JSON object from markdown code fences.
     * Handles:
     * - ```json\n{...}\n```
     * - ```\n{...}\n```
     * - Bare {..} if surrounded by other text
     */
    internal fun extractJsonBlock(text: String): String? {
        // Match ```json ... ``` or ``` ... ```
        val fenceRegex = Regex("""```(?:json)?\s*\n?\s*(\{[\s\S]*?\})\s*\n?\s*```""")
        fenceRegex.find(text)?.let { return it.groupValues[1].trim() }

        // Match first bare JSON object {...}
        val braceStart = text.indexOf('{')
        val braceEnd = text.lastIndexOf('}')
        if (braceStart >= 0 && braceEnd > braceStart) {
            return text.substring(braceStart, braceEnd + 1)
        }

        return null
    }

    /**
     * Best-effort extraction of a commit message from free-text AI output.
     *
     * Treats the first non-blank line as the title, and the rest as the body.
     * Returns null only if the input is entirely blank.
     */
    internal fun parseFallback(text: String): GeneratedCommitMessage? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null

        val title = lines.first()
            .removePrefix("Title:")
            .removePrefix("title:")
            .removePrefix("Subject:")
            .removePrefix("subject:")
            .trim()

        if (title.isBlank()) return null

        val bodyLines = lines.drop(1)
        val body = if (bodyLines.isNotEmpty()) bodyLines.joinToString("\n") else null

        return GeneratedCommitMessage(
            title = sanitizeTitle(title),
            body = body?.ifBlank { null }
        )
    }

    /**
     * Sanitize a raw title string from AI output:
     * - Replace newlines/carriage returns with spaces (single-line guarantee).
     * - Collapse consecutive whitespace.
     * - Trim leading/trailing whitespace.
     * - Cap at 200 codepoints (safety limit before final truncation).
     */
    internal fun sanitizeTitle(raw: String): String {
        val clean = raw
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
        val cpCount = clean.codePointCount(0, clean.length)
        return if (cpCount <= 200) clean
        else clean.substring(0, clean.offsetByCodePoints(0, 200))
    }

    /**
     * Exception indicating the AI response could not be parsed at all.
     */
    class AiParseException(message: String) : RuntimeException(message)
}
