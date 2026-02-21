# Smart Commit

[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/30298-smart-commit?label=JetBrains%20Marketplace&logo=jetbrains&color=blue)](https://plugins.jetbrains.com/plugin/30298-smart-commit)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/30298-smart-commit?logo=jetbrains)](https://plugins.jetbrains.com/plugin/30298-smart-commit)
[![Rating](https://img.shields.io/jetbrains/plugin/r/rating/30298-smart-commit?logo=jetbrains)](https://plugins.jetbrains.com/plugin/30298-smart-commit/reviews)
[![License](https://img.shields.io/github/license/chafian/smart-commit-intellij)](LICENSE)
[![Build](https://github.com/chafian/smart-commit-intellij/actions/workflows/build.yml/badge.svg)](https://github.com/chafian/smart-commit-intellij/actions/workflows/build.yml)

**AI-powered Git commit message generator for IntelliJ-based IDEs.**

Smart Commit analyzes your staged changes and generates professional, meaningful commit messages using either AI (OpenAI / Ollama) or deterministic templates. Supports Gitmoji, Conventional Commits, and free-form conventions.

<p align="center">
  <a href="https://plugins.jetbrains.com/plugin/30298-smart-commit">
    <img src="https://img.shields.io/badge/Install-Smart%20Commit-blue?style=for-the-badge&logo=jetbrains" alt="Install Smart Commit" />
  </a>
</p>

## Features

- **AI-Powered Generation** — Uses OpenAI (GPT-4o-mini, GPT-4o) or Ollama (fully local, private) to generate context-aware commit messages from your diffs.
- **Template-Based Fallback** — Deterministic, offline generation when AI is unavailable or for predictable results.
- **Commit Conventions** — Gitmoji (default), Conventional Commits (`feat(scope): ...`), or free-form.
- **Multi-Language** — Generate commit messages in 12 languages: English, Chinese, Japanese, Korean, Spanish, French, German, Portuguese, Russian, Arabic, Hindi, and Turkish.
- **Commit Style** — Choose between one-line (title only) or detailed (title + body) messages.
- **Message History** — Browse and reuse previously generated commit messages (`Alt+H`).
- **Custom Prompts & Templates** — Define your own AI system prompt or override templates for team-specific commit styles.
- **Smart Scope Detection** — Automatically detects module names from Gradle, Maven, npm, Cargo, and other build systems.
- **One-Click or Auto** — Generate via toolbar button, `Alt+G` shortcut, or automatically when the commit dialog opens.
- **Privacy-Friendly** — Use Ollama for fully local generation. No data leaves your machine.
- **Secure** — API keys stored in IntelliJ PasswordSafe (system credential store), never in plain text.

## Installation

### From JetBrains Marketplace (Recommended)

1. Open your JetBrains IDE (IntelliJ IDEA, WebStorm, PyCharm, CLion, GoLand, PhpStorm, etc.)
2. Go to **Settings → Plugins → Marketplace**
3. Search for **"Smart Commit"**
4. Click **Install** and restart the IDE

Or install directly from the [JetBrains Marketplace page](https://plugins.jetbrains.com/plugin/30298-smart-commit).

### From Disk

1. Download the latest `.zip` from [Releases](https://github.com/chafian/smart-commit-intellij/releases)
2. Go to **Settings → Plugins → Gear icon → Install Plugin from Disk...**
3. Select the downloaded `.zip` file
4. Restart the IDE

## Quick Start

1. Open a Git project and make some changes
2. Open the **Commit** tool window (`Alt+0`)
3. Stage your files
4. Press `Alt+G` or click the **lightning bolt** button in the commit message toolbar
5. Configure your AI provider in **Tools → Smart Commit Settings...**

## Configuration

Open settings via **Tools → Smart Commit Settings...** or **Settings → Tools → Smart Commit**.

### General

| Setting | Default | Description |
|---------|---------|-------------|
| Generator Mode | AI-Powered | Choose between AI or Template-based generation |
| AI Provider | OpenAI | OpenAI (cloud) or Ollama (local) |
| Convention | Gitmoji | Commit format: Gitmoji, Conventional Commits, or Free-form |
| Commit Style | Detailed | One-line (title only) or Detailed (title + body) |
| Language | English | Commit message language (12 languages available) |
| Auto-generate | Off | Automatically generate when commit dialog opens |

### OpenAI

| Setting | Default | Description |
|---------|---------|-------------|
| Model | gpt-4o-mini | OpenAI model to use (gpt-4o-mini is cost-effective) |
| API Key | — | Your OpenAI API key (stored securely in PasswordSafe) |

### Ollama (Local)

| Setting | Default | Description |
|---------|---------|-------------|
| URL | http://localhost:11434 | Ollama server URL |
| Model | llama3 | Local model name (llama3, codellama, mistral, etc.) |

### Custom Prompt / Template

| Setting | Description |
|---------|-------------|
| Custom System Prompt | Extra instructions appended to the AI prompt (e.g. "Always mention the ticket number") |
| Custom Title Template | Override the title template for Template mode. Variables: `{{type}}`, `{{scope}}`, `{{summary}}` |
| Custom Body Template | Override the body template. Variables: `{{files_changed}}`, `{{body_lines}}`, etc. |

### Advanced

| Setting | Default | Description |
|---------|---------|-------------|
| Max Subject Length | 72 | Maximum characters for the commit title line |
| Max Diff Tokens | 4000 | Token budget for diff content sent to AI |

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `Alt+G` | Generate commit message |
| `Alt+H` | Browse Smart Commit history |

## How It Works

```
Staged Changes
     │
     ▼
DiffAnalyzerImpl          ── Extracts diffs from IntelliJ Change objects
     │
     ▼
ChangeClassifier          ── Classifies each file into a category
     │                       (feature, fix, refactor, test, docs, etc.)
     ▼
DiffSummary               ── Aggregated statistics and classifications
     │
     ├──[AI Mode]─────────▶ PromptBuilder ──▶ AiProvider ──▶ AiResponse parser
     │                                                             │
     ├──[Template Mode]───▶ TemplateEngine ──▶ Variable map        │
     │                                                             │
     ▼                                                             ▼
CommitConvention.format()  ── Applies Gitmoji / Conventional / Free-form
     │
     ▼
GeneratedCommitMessage     ── title + body + footer
     │
     ▼
Commit Dialog              ── Message inserted via VCS API
```

### Safety Guarantees

- **AI never blocks the commit workflow.** If the AI provider fails (network error, timeout, invalid response), the plugin falls back to template-based generation. If that also fails, it returns a safe hardcoded message.
- **Title is always single-line.** Newlines are stripped, whitespace is collapsed.
- **Title truncation is codepoint-safe.** Never splits Unicode surrogate pairs (emoji, CJK characters).
- **Prompt size is deterministic.** Hard caps on file count (30), diff tokens (4000), and total prompt characters (32K) prevent context window blowups.

## Compatibility

- **IDEs:** IntelliJ IDEA, WebStorm, PyCharm, CLion, GoLand, PhpStorm, Rider, and all IntelliJ-based IDEs
- **Version:** 2024.1 or later
- **Requires:** Git plugin enabled (bundled with all JetBrains IDEs)
- **For AI mode:** OpenAI API key or running Ollama instance

## Building from Source

```bash
git clone https://github.com/chafian/smart-commit-intellij.git
cd smart-commit-intellij
./gradlew buildPlugin
```

The built plugin will be at `build/distributions/smart-commit-*.zip`.

Run tests:

```bash
./gradlew test
```

## Contributing

Contributions are welcome! Please open an issue or submit a pull request on [GitHub](https://github.com/chafian/smart-commit-intellij).

## License

Apache License 2.0. See [LICENSE](LICENSE) for details.
