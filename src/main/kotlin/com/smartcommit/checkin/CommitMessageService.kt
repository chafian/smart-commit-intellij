package com.smartcommit.checkin

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.smartcommit.ai.AiCommitMessageGenerator
import com.smartcommit.ai.AiProvider
import com.smartcommit.ai.OllamaProvider
import com.smartcommit.ai.OpenAiProvider
import com.smartcommit.ai.PromptBuilder
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
 * This is the single entry point called by [SmartCommitHandler] and
 * [GenerateCommitMessageAction].
 */
object CommitMessageService {

    /**
     * Generate a commit message from a list of IntelliJ [Change] objects.
     *
     * @param project   The current project.
     * @param changes   The selected changes from the commit panel.
     * @param indicator Optional progress indicator for background task feedback.
     * @return A formatted [GeneratedCommitMessage].
     */
    @Suppress("UNUSED_PARAMETER") // project kept for future PasswordSafe per-project scope
    fun generate(
        project: Project,
        changes: List<Change>,
        indicator: ProgressIndicator? = null
    ): GeneratedCommitMessage {
        val settings = SmartCommitSettings.instance()

        // Step 1: Analyze diffs
        indicator?.text = "Analyzing changes..."
        val analyzer = DiffAnalyzerImpl(changes)
        val summary = analyzer.analyze()

        if (summary.isEmpty) {
            return GeneratedCommitMessage.titleOnly("Update code")
        }

        // Step 2: Build the generator based on settings
        indicator?.text = "Generating commit message..."
        val convention = settings.convention.createConvention()
        val generator = createGenerator(settings, convention)

        // Step 3: Generate
        val message = generator.generate(summary)

        // Step 4: Apply commit style — strip body/footer for one-line mode
        val styled = when (settings.commitStyle) {
            CommitStyle.ONE_LINE -> message.copy(body = null, footer = null)
            CommitStyle.DETAILED -> message
        }

        // Step 5: Save to history
        try {
            CommitMessageHistory.instance().add(styled.format())
        } catch (_: Exception) {
            // History save should never block generation
        }

        return styled
    }

    // ── Factory methods ─────────────────────────────────────

    private fun createGenerator(
        settings: SmartCommitSettings,
        convention: CommitConvention
    ): CommitMessageGenerator {
        return when (settings.generatorMode) {
            GeneratorMode.TEMPLATE -> {
                val titleTpl = settings.customTitleTemplate.ifBlank { null }
                val bodyTpl = settings.customBodyTemplate.ifBlank { null }
                TemplateGenerator(
                    titleTemplate = titleTpl ?: TemplateGenerator.DEFAULT_TITLE_TEMPLATE,
                    bodyTemplate = bodyTpl ?: TemplateGenerator.DEFAULT_BODY_TEMPLATE,
                    maxTitleLength = settings.maxSubjectLength,
                    convention = convention
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
                    customSystemPrompt = settings.customSystemPrompt
                )
                val titleTpl = settings.customTitleTemplate.ifBlank { null }
                val bodyTpl = settings.customBodyTemplate.ifBlank { null }
                val fallback = TemplateGenerator(
                    titleTemplate = titleTpl ?: TemplateGenerator.DEFAULT_TITLE_TEMPLATE,
                    bodyTemplate = bodyTpl ?: TemplateGenerator.DEFAULT_BODY_TEMPLATE,
                    maxTitleLength = settings.maxSubjectLength,
                    convention = convention
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
