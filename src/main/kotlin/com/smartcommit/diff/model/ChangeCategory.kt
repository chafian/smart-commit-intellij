package com.smartcommit.diff.model

/**
 * Semantic category of a change, inferred from file paths and diff patterns.
 * Used to select the appropriate commit message prefix/emoji and to prioritize
 * which changes are most significant in the summary.
 */
enum class ChangeCategory(val label: String, val priority: Int) {
    /** New user-facing functionality. */
    FEATURE("feature", 1),

    /** A fix for a defect or incorrect behavior. */
    BUGFIX("fix", 2),

    /** Code restructuring without behavior change. */
    REFACTOR("refactor", 3),

    /** Test additions or modifications. */
    TEST("test", 4),

    /** Documentation changes (markdown, text, comments). */
    DOCS("docs", 5),

    /** Code style / formatting only. */
    STYLE("style", 6),

    /** Build system or dependency changes. */
    BUILD("build", 7),

    /** CI/CD pipeline configuration changes. */
    CI("ci", 8),

    /** Maintenance tasks, config, tooling, misc. */
    CHORE("chore", 9);

    companion object {
        /**
         * Returns the dominant (highest priority) category from a collection.
         * Falls back to [CHORE] for empty input.
         */
        fun dominant(categories: Collection<ChangeCategory>): ChangeCategory {
            return categories.minByOrNull { it.priority } ?: CHORE
        }
    }
}
