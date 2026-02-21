package com.smartcommit.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.smartcommit.convention.ConventionType

/**
 * Persistent application-level settings for the Smart Commit plugin.
 *
 * Registered as an `applicationService` in `plugin.xml`.
 * Settings are stored in `smartCommit.xml` under the IDE config directory.
 *
 * Access via [instance].
 */
@State(
    name = "SmartCommitSettings",
    storages = [Storage("smartCommit.xml")]
)
class SmartCommitSettings : PersistentStateComponent<SmartCommitSettings.SettingsState> {

    private var state = SettingsState()

    override fun getState(): SettingsState = state

    override fun loadState(state: SettingsState) {
        this.state = state
    }

    // ── Convenience accessors ───────────────────────────────

    var generatorMode: GeneratorMode
        get() = state.generatorMode
        set(value) { state.generatorMode = value }

    var aiProvider: AiProviderType
        get() = state.aiProvider
        set(value) { state.aiProvider = value }

    var openAiModel: String
        get() = state.openAiModel
        set(value) { state.openAiModel = value }

    var ollamaModel: String
        get() = state.ollamaModel
        set(value) { state.ollamaModel = value }

    var ollamaUrl: String
        get() = state.ollamaUrl
        set(value) { state.ollamaUrl = value }

    var convention: ConventionType
        get() = state.convention
        set(value) { state.convention = value }

    var maxDiffTokens: Int
        get() = state.maxDiffTokens
        set(value) { state.maxDiffTokens = value }

    var autoGenerate: Boolean
        get() = state.autoGenerate
        set(value) { state.autoGenerate = value }

    var confirmOverwrite: Boolean
        get() = state.confirmOverwrite
        set(value) { state.confirmOverwrite = value }

    var includeBody: Boolean
        get() = state.includeBody
        set(value) { state.includeBody = value }

    var commitStyle: CommitStyle
        get() = state.commitStyle
        set(value) { state.commitStyle = value }

    var maxSubjectLength: Int
        get() = state.maxSubjectLength
        set(value) { state.maxSubjectLength = value }

    var customSystemPrompt: String
        get() = state.customSystemPrompt
        set(value) { state.customSystemPrompt = value }

    var customTitleTemplate: String
        get() = state.customTitleTemplate
        set(value) { state.customTitleTemplate = value }

    var customBodyTemplate: String
        get() = state.customBodyTemplate
        set(value) { state.customBodyTemplate = value }

    var commitLanguage: CommitLanguage
        get() = state.commitLanguage
        set(value) { state.commitLanguage = value }

    // ── State data class ────────────────────────────────────

    /**
     * Serializable state. All fields must have defaults for XML deserialization.
     * Uses simple types that IntelliJ's XML serializer can handle.
     */
    data class SettingsState(
        var generatorMode: GeneratorMode = GeneratorMode.AI,
        var aiProvider: AiProviderType = AiProviderType.OPENAI,
        var openAiModel: String = "gpt-4o-mini",
        var ollamaModel: String = "llama3",
        var ollamaUrl: String = "http://localhost:11434",
        var convention: ConventionType = ConventionType.GITMOJI,
        var commitStyle: CommitStyle = CommitStyle.DETAILED,
        var commitLanguage: CommitLanguage = CommitLanguage.ENGLISH,
        var maxDiffTokens: Int = 4000,
        var autoGenerate: Boolean = false,
        var confirmOverwrite: Boolean = true,
        var includeBody: Boolean = true,
        var maxSubjectLength: Int = 72,
        var customSystemPrompt: String = "",
        var customTitleTemplate: String = "",
        var customBodyTemplate: String = ""
    )

    companion object {
        fun instance(): SmartCommitSettings {
            return ApplicationManager.getApplication().getService(SmartCommitSettings::class.java)
        }
    }
}

/**
 * Whether to use AI-powered or template-based commit message generation.
 */
enum class GeneratorMode(val displayName: String) {
    AI("AI-Powered"),
    TEMPLATE("Template-Based");

    override fun toString(): String = displayName
}

/**
 * Which AI provider to use.
 */
enum class AiProviderType(val displayName: String) {
    OPENAI("OpenAI"),
    OLLAMA("Ollama (Local)");

    override fun toString(): String = displayName
}

/**
 * How detailed the generated commit message should be.
 */
enum class CommitStyle(val displayName: String) {
    ONE_LINE("One-Line (title only)"),
    DETAILED("Detailed (title + body)");

    override fun toString(): String = displayName
}

/**
 * Language for the generated commit message.
 */
enum class CommitLanguage(val displayName: String, val promptHint: String) {
    ENGLISH("English", "Write the commit message in English."),
    CHINESE("Chinese", "Write the commit message in Chinese (Simplified, \u7B80\u4F53\u4E2D\u6587)."),
    JAPANESE("Japanese", "Write the commit message in Japanese (\u65E5\u672C\u8A9E)."),
    KOREAN("Korean", "Write the commit message in Korean (\uD55C\uAD6D\uC5B4)."),
    SPANISH("Spanish", "Write the commit message in Spanish (Espa\u00F1ol)."),
    FRENCH("French", "Write the commit message in French (Fran\u00E7ais)."),
    GERMAN("German", "Write the commit message in German (Deutsch)."),
    PORTUGUESE("Portuguese", "Write the commit message in Portuguese (Portugu\u00EAs)."),
    RUSSIAN("Russian", "Write the commit message in Russian (\u0420\u0443\u0441\u0441\u043A\u0438\u0439)."),
    ARABIC("Arabic", "Write the commit message in Arabic (\u0627\u0644\u0639\u0631\u0628\u064A\u0629)."),
    HINDI("Hindi", "Write the commit message in Hindi (\u0939\u093F\u0928\u094D\u0926\u0940)."),
    TURKISH("Turkish", "Write the commit message in Turkish (T\u00FCrk\u00E7e).");

    override fun toString(): String = displayName
}
