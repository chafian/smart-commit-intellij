package com.smartcommit.generator

import com.smartcommit.diff.model.DiffSummary
import com.smartcommit.generator.model.GeneratedCommitMessage

/**
 * Contract for generating a commit message from analyzed changes.
 *
 * Implementations:
 * - [TemplateGenerator] — deterministic, template-based (Phase 3)
 * - `AiGenerator` — LLM-powered, async (Phase 4)
 *
 * This interface is intentionally minimal. It takes a [DiffSummary]
 * (the full picture of what changed) and returns a structured
 * [GeneratedCommitMessage].
 *
 * Implementations must be safe to call from any thread.
 * If an implementation needs I/O (e.g. network for AI), it should
 * handle that internally and may throw on failure.
 */
interface CommitMessageGenerator {

    /** Human-readable name of this generator, for UI display. */
    val displayName: String

    /**
     * Generate a commit message from the given diff summary.
     *
     * @param summary Aggregated and classified change data.
     * @return A structured commit message with at least a title.
     * @throws IllegalStateException if the summary is empty.
     */
    fun generate(summary: DiffSummary): GeneratedCommitMessage
}
