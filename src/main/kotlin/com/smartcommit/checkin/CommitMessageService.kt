package com.smartcommit.checkin

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.smartcommit.ai.AiCommitMessageGenerator
import com.smartcommit.ai.AiProvider
import com.smartcommit.ai.CloudAuthManager
import com.smartcommit.ai.CloudProvider
import com.smartcommit.ai.OllamaProvider
import com.smartcommit.ai.OpenAiProvider
import com.smartcommit.ai.PromptBuilder
import com.smartcommit.branch.BranchContext
import com.smartcommit.branch.BranchNameParser
import com.smartcommit.branch.BranchUtils
import com.smartcommit.convention.CommitConvention
import com.smartcommit.diff.DiffAnalyzerImpl
import com.smartcommit.generator.CommitMessageGenerator
import com.smartcommit.generator.TemplateGenerator
import com.smartcommit.generator.model.GeneratedCommitMessage
import com.smartcommit.history.CommitMessageHistory
import com.smartcommit.settings.AiProviderType
import com.smartcommit.settings.CommitLanguage
import com.smartcommit.settings.CommitStyle
import com.smartcommit.settings.GeneratorMode
import com.smartcommit.settings.SmartCommitSettings

/**
 * Orchestrates commit message generation by wiring together:
 * - Settings (generator mode, provider, convention, etc.)
 * - DiffAnalyzer (extracts diffs from IntelliJ Change objects)
 * - CommitMessageGenerator (template or AI-powered)
 * - CommitConvention (formatting)
 *
 * For Cloud provider, [CloudProvider.complete] calls `POST /api/cloud/generate`
 * which handles authentication, usage consumption, and OpenAI in a single request.
 * On business errors (limit_exhausted, subscription_inactive), [CloudProvider]
 * throws [CloudUsageException] which propagates up to the callers
 * ([SmartCommitHandler], [GenerateCommitMessageAction]).
 *
 * This is the single entry point called by [SmartCommitHandler] and
 * [GenerateCommitMessageAction].
 */
object CommitMessageService {

    /**
     * Result of a Cloud generation that includes usage info for the notification.
     * Stored so callers can read it after generate() returns.
     */
    var lastCloudUsage: CloudUsageInfo? = null
        private set

    /**
     * Generate a commit message from a list of IntelliJ [Change] objects.
     *
     * @param project   The current project.
     * @param changes   The selected changes from the commit panel.
     * @param indicator Optional progress indicator for background task feedback.
     * @return A formatted [GeneratedCommitMessage].
     * @throws CloudUsageException if Cloud provider quota is exhausted or subscription inactive.
     * @throws CloudNotConnectedException if Cloud provider is selected but user is not connected.
     */
    fun generate(
        project: Project,
        changes: List<Change>,
        indicator: ProgressIndicator? = null
    ): GeneratedCommitMessage {
        val settings = SmartCommitSettings.instance()
        lastCloudUsage = null

        // Step 1: Analyze diffs
        indicator?.text = "Analyzing changes..."
        val analyzer = DiffAnalyzerImpl(changes)
        val summary = analyzer.analyze()

        if (summary.isEmpty) {
            return GeneratedCommitMessage.titleOnly("Update code")
        }

        // Step 1.5: Resolve branch context (Smart Branch integration)
        val branchContext = if (settings.smartBranch) {
            val branchName = BranchUtils.getCurrentBranchName(project)
            if (branchName != null) {
                val customPattern = settings.smartBranchPattern.ifBlank { null }
                BranchNameParser.parse(branchName, customPattern)
            } else BranchContext.EMPTY
        } else BranchContext.EMPTY

        // Step 2: Build the generator based on settings
        indicator?.text = "Generating commit message..."
        val convention = settings.convention.createConvention()
        val generator = createGenerator(settings, convention, branchContext)

        // Step 3: Generate
        // For Cloud provider, this call goes through CloudProvider.complete()
        // which calls POST /api/cloud/generate on the server.
        // CloudUsageException / CloudNotConnectedException propagate up from here.
        val message = generator.generate(summary)

        // Step 4: Retrieve Cloud usage info if this was a Cloud generation
        if (settings.generatorMode == GeneratorMode.AI && settings.aiProvider == AiProviderType.CLOUD) {
            lastCloudUsage = lastCloudProvider?.lastUsageInfo
        }

        // Step 5: Apply commit style — strip body/footer for one-line mode
        val styled = when (settings.commitStyle) {
            CommitStyle.ONE_LINE -> message.copy(body = null, footer = null)
            CommitStyle.DETAILED -> message
        }

        // Step 6: Save to history
        try {
            CommitMessageHistory.instance().add(styled.format())
        } catch (_: Exception) {
            // History save should never block generation
        }

        return styled
    }

    // ── Factory methods ─────────────────────────────────────

    /**
     * Reference to the last created CloudProvider, so we can read its lastUsageInfo.
     */
    private var lastCloudProvider: CloudProvider? = null

    private fun createGenerator(
        settings: SmartCommitSettings,
        convention: CommitConvention,
        branchContext: BranchContext = BranchContext.EMPTY
    ): CommitMessageGenerator {
        val ticketInFooter = settings.smartBranchTicketInFooter
        return when (settings.generatorMode) {
            GeneratorMode.TEMPLATE -> {
                val titleTpl = settings.customTitleTemplate.ifBlank { null }
                val bodyTpl = settings.customBodyTemplate.ifBlank { null }
                TemplateGenerator(
                    titleTemplate = titleTpl ?: TemplateGenerator.DEFAULT_TITLE_TEMPLATE,
                    bodyTemplate = bodyTpl ?: TemplateGenerator.DEFAULT_BODY_TEMPLATE,
                    maxTitleLength = settings.maxSubjectLength,
                    convention = convention,
                    branchContext = branchContext,
                    ticketInFooter = ticketInFooter
                )
            }
            GeneratorMode.AI -> {
                val provider = createAiProvider(settings)
                val languageHint = if (settings.commitLanguage != CommitLanguage.ENGLISH) {
                    settings.commitLanguage.promptHint
                } else ""
                val promptBuilder = PromptBuilder(
                    maxDiffTokens = settings.maxDiffTokens,
                    conventionHint = convention.promptHint(),
                    oneLineOnly = settings.commitStyle == CommitStyle.ONE_LINE,
                    languageHint = languageHint,
                    customSystemPrompt = settings.customSystemPrompt,
                    maxSubjectLength = settings.maxSubjectLength,
                    branchContext = branchContext,
                    ticketInFooter = ticketInFooter
                )
                val titleTpl = settings.customTitleTemplate.ifBlank { null }
                val bodyTpl = settings.customBodyTemplate.ifBlank { null }
                val fallback = TemplateGenerator(
                    titleTemplate = titleTpl ?: TemplateGenerator.DEFAULT_TITLE_TEMPLATE,
                    bodyTemplate = bodyTpl ?: TemplateGenerator.DEFAULT_BODY_TEMPLATE,
                    maxTitleLength = settings.maxSubjectLength,
                    convention = convention,
                    branchContext = branchContext,
                    ticketInFooter = ticketInFooter
                )
                AiCommitMessageGenerator(
                    provider = provider,
                    promptBuilder = promptBuilder,
                    fallback = fallback,
                    maxTitleLength = settings.maxSubjectLength,
                    convention = convention
                )
            }
        }
    }

    private fun createAiProvider(settings: SmartCommitSettings): AiProvider {
        return when (settings.aiProvider) {
            AiProviderType.OPENAI -> {
                val apiKey = getOpenAiApiKey()
                OpenAiProvider(
                    apiKey = apiKey,
                    model = settings.openAiModel
                )
            }
            AiProviderType.OLLAMA -> OllamaProvider(
                baseUrl = settings.ollamaUrl,
                model = settings.ollamaModel
            )
            AiProviderType.CLOUD -> {
                val provider = CloudProvider(
                    baseUrl = settings.cloudBaseUrl.trimEnd('/')
                )
                lastCloudProvider = provider
                provider
            }
        }
    }

    /**
     * Retrieves the OpenAI API key from IntelliJ PasswordSafe.
     * Falls back to the OPENAI_API_KEY environment variable if not stored.
     */
    private fun getOpenAiApiKey(): String {
        val stored = com.smartcommit.settings.SmartCommitConfigurable.loadApiKey()
        return stored.ifBlank { System.getenv("OPENAI_API_KEY") ?: "" }
    }
}

// ── Cloud-specific exceptions and data ──────────────────

/**
 * Thrown when Cloud usage cannot proceed. Callers should catch this
 * and show the appropriate dialog (upgrade modal, subscription inactive, etc.).
 */
class CloudUsageException(
    val reason: Reason,
    val used: Int = 0,
    val limit: Int = 0,
    val resetAt: String = ""
) : RuntimeException("Cloud usage blocked: $reason") {

    enum class Reason {
        LIMIT_EXHAUSTED,
        SUBSCRIPTION_INACTIVE,
        RATE_LIMITED
    }
}

/**
 * Thrown when Cloud provider is selected but user is not connected.
 */
class CloudNotConnectedException(
    message: String = "Not connected to Smart Commit Cloud."
) : RuntimeException(message)

/**
 * Usage info from a successful Cloud consumption, used for the notification.
 */
data class CloudUsageInfo(
    val plan: String,
    val used: Int,
    val limit: Int,
    val remaining: Int,
    val resetAt: String
)
