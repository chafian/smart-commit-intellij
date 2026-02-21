# Smart Commit

**AI-powered Git commit message generator for IntelliJ IDEA.**

Smart Commit analyzes your staged changes and generates professional, meaningful commit messages using either AI (OpenAI / Ollama) or deterministic templates. Supports Gitmoji, Conventional Commits, and free-form conventions.

## Features

- **AI-Powered Generation** -- Uses OpenAI (GPT-4o-mini, GPT-4o) or Ollama (fully local, private) to generate context-aware commit messages from your diffs.
- **Template-Based Fallback** -- Deterministic, offline generation when AI is unavailable or for predictable results.
- **Commit Conventions** -- Gitmoji (default), Conventional Commits (`feat(scope): ...`), or free-form.
- **One-Click or Auto** -- Generate via toolbar button, `Alt+G` shortcut, or automatically when the commit dialog opens.
- **Smart Classification** -- Automatically detects change categories (feature, fix, refactor, test, docs, etc.) from file paths and diff content.
- **Privacy-Friendly** -- Use Ollama for fully local generation. No data leaves your machine.
- **Secure** -- API keys stored in IntelliJ PasswordSafe (system credential store), never in plain text.

## Installation

### From JetBrains Marketplace

1. Open IntelliJ IDEA
2. Go to **Settings > Plugins > Marketplace**
3. Search for **"Smart Commit"**
4. Click **Install** and restart the IDE

### From Disk

1. Download the latest `.zip` from [Releases](https://github.com/smart-commit/smart-commit-plugin/releases)
2. Go to **Settings > Plugins > Gear icon > Install Plugin from Disk...**
3. Select the downloaded `.zip` file
4. Restart the IDE

## Configuration

Go to **Settings > Tools > Smart Commit** to configure:

### General

| Setting | Default | Description |
|---------|---------|-------------|
| Generator Mode | AI-Powered | Choose between AI or Template-based generation |
| Convention | Gitmoji | Commit message format: Gitmoji, Conventional Commits, or Free-form |
| Auto-generate | Off | Automatically generate when commit dialog opens |
| Confirm overwrite | On | Ask before replacing an existing commit message |
| Include body | On | Generate a message body in addition to the title |

### OpenAI

| Setting | Default | Description |
|---------|---------|-------------|
| Model | gpt-4o-mini | OpenAI model to use (gpt-4o-mini is cost-effective) |
| API Key | -- | Your OpenAI API key (stored in PasswordSafe) |

### Ollama (Local)

| Setting | Default | Description |
|---------|---------|-------------|
| URL | http://localhost:11434 | Ollama server URL |
| Model | llama3 | Local model name (llama3, codellama, mistral, etc.) |

### Advanced

| Setting | Default | Description |
|---------|---------|-------------|
| Max Subject Length | 72 | Maximum characters for the commit title line |
| Max Diff Tokens | 4000 | Token budget for diff content sent to AI |

## Usage

### Manual Generation

1. Stage your changes and open the **Commit** tool window
2. Click the **lightning bolt** button in the commit message toolbar, or press **Alt+G**
3. The plugin analyzes your changes and generates a commit message

### Auto Generation

1. Enable **Auto-generate on commit dialog open** in settings
2. Every time you open the commit dialog, a message is generated automatically
3. If a message already exists, you'll be asked before it's overwritten

## How It Works

```
Staged Changes
     |
     v
DiffAnalyzerImpl          -- Extracts diffs from IntelliJ Change objects
     |
     v
ChangeClassifier          -- Classifies each file into a category
     |                       (feature, fix, refactor, test, docs, etc.)
     v
DiffSummary               -- Aggregated statistics and classifications
     |
     +---[AI Mode]---------> PromptBuilder --> AiProvider --> AiResponse parser
     |                                                             |
     +---[Template Mode]---> TemplateEngine --> Variable map       |
     |                                                             |
     v                                                             v
CommitConvention.format()  -- Applies Gitmoji / Conventional / Free-form
     |
     v
GeneratedCommitMessage     -- title + body + footer
     |
     v
Commit Dialog              -- Message set via CheckinProjectPanel
```

### Safety Guarantees

- **AI never blocks the commit workflow.** If the AI provider fails (network error, timeout, invalid response), the plugin falls back to template-based generation. If that also fails, it returns a safe hardcoded message ("Update code").
- **Title is always single-line.** Newlines are stripped, whitespace is collapsed.
- **Title truncation is codepoint-safe.** Never splits Unicode surrogate pairs (emoji, CJK supplementary characters).
- **Prompt size is deterministic.** Hard caps on file count (30), diff tokens (4000), and total prompt characters (32K) prevent context window blowups.

## Requirements

- IntelliJ IDEA 2024.1 or later (Community or Ultimate)
- Git plugin enabled (bundled with IDEA)
- For AI mode: OpenAI API key or running Ollama instance

## Building from Source

```bash
git clone https://github.com/smart-commit/smart-commit-plugin.git
cd smart-commit-plugin
./gradlew buildPlugin
```

The built plugin will be at `build/distributions/Smart Commit-*.zip`.

To run tests:

```bash
./gradlew test
```

To verify plugin structure:

```bash
./gradlew verifyPluginProjectConfiguration
```

## License

Apache License 2.0. See [LICENSE](LICENSE) for details.
