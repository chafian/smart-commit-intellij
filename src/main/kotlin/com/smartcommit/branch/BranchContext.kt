package com.smartcommit.branch

/**
 * Parsed context from a Git branch name.
 *
 * Contains structured information extracted by [BranchNameParser]:
 * type, ticket ID, scope, and humanized description.
 *
 * Used by [PromptBuilder] (AI path) and [TemplateGenerator] (template path)
 * to enrich generated commit messages with branch-derived context.
 *
 * @param rawBranchName The original branch name as-is from Git.
 * @param type          Normalized commit type: "feat", "fix", "refactor", etc.
 *                      Normalized from branch prefix (e.g. "feature" to "feat", "bugfix" to "fix").
 * @param ticket        Issue/ticket reference: "JIRA-142", "#142", "ENG-42", etc.
 * @param scope         Component/module scope extracted from branch segments: "payment", "auth", etc.
 * @param description   Humanized description with smart uppercase: "add payment API", "fix OAuth redirect".
 * @param isDefault     True for default/ignored branches (main, master, develop, release/x).
 *                      When true, no branch enrichment is applied.
 */
data class BranchContext(
    val rawBranchName: String,
    val type: String?,
    val ticket: String?,
    val scope: String?,
    val description: String?,
    val isDefault: Boolean
) {

    companion object {
        /** Empty context for default branches or when Smart Branch is disabled. */
        val EMPTY = BranchContext(
            rawBranchName = "",
            type = null,
            ticket = null,
            scope = null,
            description = null,
            isDefault = true
        )
    }

    /** Whether a ticket/issue ID was found in the branch name. */
    val hasTicket: Boolean get() = !ticket.isNullOrBlank()

    /** Whether a commit type was extracted from the branch prefix. */
    val hasType: Boolean get() = !type.isNullOrBlank()

    /** Whether a scope was extracted from the branch segments. */
    val hasScope: Boolean get() = !scope.isNullOrBlank()

    /** Whether this context has any useful information worth injecting into the commit. */
    val hasUsefulInfo: Boolean get() = !isDefault && (hasTicket || hasType || hasScope || !description.isNullOrBlank())

    /**
     * Footer line for ticket reference.
     * Example: "Refs: JIRA-142"
     * Returns null if no ticket.
     */
    val footerLine: String? get() = if (hasTicket) "Refs: $ticket" else null

    /**
     * Ticket in parentheses for title suffix.
     * Example: "(JIRA-142)"
     * Returns null if no ticket.
     */
    val titleTicketSuffix: String? get() = if (hasTicket) "($ticket)" else null
}
