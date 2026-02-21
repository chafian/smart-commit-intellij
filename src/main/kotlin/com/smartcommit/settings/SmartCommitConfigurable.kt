package com.smartcommit.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import com.smartcommit.convention.ConventionType

/**
 * Settings page for Smart Commit, registered under `Settings > Tools > Smart Commit`.
 *
 * Uses Kotlin UI DSL 2 (`panel { }`) to build the settings form.
 * Settings are bound to [SmartCommitSettings] via property references.
 *
 * API keys are stored separately in [PasswordSafe] (never in XML).
 */
class SmartCommitConfigurable : BoundConfigurable("Smart Commit") {

    private val settings = SmartCommitSettings.instance()

    // Local copy of the API key for the form (loaded from PasswordSafe)
    private var openAiApiKey: String = loadApiKey()

    override fun createPanel(): DialogPanel = panel {
        group("General") {
            row("Generator Mode:") {
                comboBox(GeneratorMode.entries.toList())
                    .bindItem(settings::generatorMode.toNullableProperty())
                    .comment("AI-Powered uses an LLM; Template-Based is deterministic and offline")
            }
            row("AI Provider:") {
                comboBox(AiProviderType.entries.toList())
                    .bindItem(settings::aiProvider.toNullableProperty())
                    .comment("Choose OpenAI (cloud) or Ollama (local). Only used in AI mode.")
            }
            row("Convention:") {
                comboBox(ConventionType.entries.toList())
                    .bindItem(settings::convention.toNullableProperty())
                    .comment("Gitmoji adds emoji prefixes; Conventional Commits uses type(scope): format")
            }
            row("Commit Style:") {
                comboBox(CommitStyle.entries.toList())
                    .bindItem(settings::commitStyle.toNullableProperty())
                    .comment("One-Line produces only a title; Detailed includes title + body explanation")
            }
            row("Language:") {
                comboBox(CommitLanguage.entries.toList())
                    .bindItem(settings::commitLanguage.toNullableProperty())
                    .comment("Language for the generated commit message (AI mode only)")
            }
            row {
                checkBox("Auto-generate on commit dialog open")
                    .bindSelected(settings::autoGenerate)
            }
            row {
                checkBox("Confirm before overwriting existing message")
                    .bindSelected(settings::confirmOverwrite)
            }
        }

        group("OpenAI") {
            row("Model:") {
                textField()
                    .bindText(settings::openAiModel)
                    .comment("e.g. gpt-4o-mini, gpt-4o, gpt-3.5-turbo")
            }
            row("API Key:") {
                passwordField()
                    .bindText(::openAiApiKey)
                    .comment("Stored securely in system credential store (PasswordSafe)")
                    .resizableColumn()
                    .align(Align.FILL)
            }
        }

        group("Ollama (Local)") {
            row("URL:") {
                textField()
                    .bindText(settings::ollamaUrl)
                    .comment("e.g. http://localhost:11434")
                    .resizableColumn()
                    .align(Align.FILL)
            }
            row("Model:") {
                textField()
                    .bindText(settings::ollamaModel)
                    .comment("e.g. llama3, codellama, mistral")
            }
        }

        group("Custom Prompt / Template") {
            row {
                comment("Leave blank to use defaults. Custom system prompt is appended to the built-in prompt (AI mode).")
            }
            row("Custom System Prompt:") {
                textArea()
                    .bindText(settings::customSystemPrompt)
                    .rows(4)
                    .comment("Extra instructions appended to the AI system prompt. E.g. 'Always mention the ticket number.'")
                    .resizableColumn()
                    .align(Align.FILL)
            }
            row("Custom Title Template:") {
                textField()
                    .bindText(settings::customTitleTemplate)
                    .comment("Template mode: override title template. Variables: {{type}}, {{scope}}, {{summary}}, etc.")
                    .resizableColumn()
                    .align(Align.FILL)
            }
            row("Custom Body Template:") {
                textArea()
                    .bindText(settings::customBodyTemplate)
                    .rows(3)
                    .comment("Template mode: override body template. Variables: {{files_changed}}, {{body_lines}}, etc.")
                    .resizableColumn()
                    .align(Align.FILL)
            }
        }

        group("Advanced") {
            row("Max Subject Length:") {
                spinner(30..120, 1)
                    .bindIntValue(settings::maxSubjectLength)
                    .comment("Maximum characters for the commit title line")
            }
            row("Max Diff Tokens:") {
                spinner(500..16000, 500)
                    .bindIntValue(settings::maxDiffTokens)
                    .comment("Maximum tokens of diff content sent to AI provider")
            }
        }
    }

    override fun apply() {
        super.apply()
        saveApiKey(openAiApiKey)
    }

    override fun reset() {
        super.reset()
        openAiApiKey = loadApiKey()
    }

    // ── PasswordSafe helpers ────────────────────────────────

    companion object {
        private const val SERVICE_NAME = "SmartCommit"
        private const val KEY_OPENAI_API_KEY = "openai-api-key"

        private fun credentialAttributes(): CredentialAttributes {
            return CredentialAttributes(
                generateServiceName(SERVICE_NAME, KEY_OPENAI_API_KEY)
            )
        }

        fun loadApiKey(): String {
            return PasswordSafe.instance.getPassword(credentialAttributes()).orEmpty()
        }

        fun saveApiKey(apiKey: String) {
            PasswordSafe.instance.setPassword(credentialAttributes(), apiKey.ifBlank { null })
        }
    }
}
