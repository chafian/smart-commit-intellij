package com.smartcommit.ai

/**
 * Abstraction over an LLM inference endpoint.
 *
 * Implementations wrap a specific API (OpenAI, Ollama, etc.) and handle
 * HTTP transport, authentication, and response extraction. The caller
 * receives a raw string — parsing into structured output is handled
 * separately by [AiResponse].
 *
 * Implementations must:
 * - Accept configuration (API key, URL, model) via constructor — never hardcode.
 * - Be safe to call from any thread (OkHttp is thread-safe).
 * - Return [Result.failure] for network errors, auth failures, timeouts.
 *
 * No IntelliJ APIs. No UI. No settings dependency.
 */
interface AiProvider {

    /** Human-readable provider name for logging and display. */
    val name: String

    /**
     * Send a prompt to the LLM and return the raw completion text.
     *
     * @param systemPrompt  The system-level instruction (role, format rules).
     * @param userPrompt    The user-level prompt (the actual diff data).
     * @return [Result.success] with the raw completion string,
     *         or [Result.failure] with the underlying exception.
     */
    fun complete(systemPrompt: String, userPrompt: String): Result<String>
}
