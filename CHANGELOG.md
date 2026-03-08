# Changelog

All notable changes to the **Smart Commit** plugin will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.3.1] - 2026-03-08

### Fixed
- **Prompt quality overhaul** — Commit messages now follow professional conventions with proper `type(scope): description` format when branch context is available.
- **AI scope inference** — When parser can't extract scope (verb-first branches like `add-payment-api`), the AI now infers scope from branch description (e.g. produces `feat(payment):`).
- **No more filler sentences** — AI no longer starts body with "This change introduces..." or similar padding. Body starts directly with the explanation.
- **Single-topic titles** — Title describes one primary change only. Secondary changes move to body.
- **Structured body format** — Bullet lists prefixed with "Changes:" heading, short and direct items.

### Changed
- Gitmoji convention now produces `emoji type(scope): description` format when branch context provides type and scope (e.g. `✨ feat(payment): add payment API integration`).
- Conventional Commits convention updated to always use branch-provided type and scope when available.
- Scope extraction excludes known acronyms (API, OAuth, JWT, etc.) — these are topic words, not component scopes.
- Title max length guidance tightened for more concise subjects.

## [1.3.0] - 2026-03-06

### Added
- **Smart Branch Integration** — Automatically extracts context from Git branch names to enrich generated commit messages. Connects Branch → Commit → Issue tracker seamlessly.
- **Branch name parser** — Auto-detects common branching conventions: `feature/JIRA-142-add-payment-api` → type=`feat`, ticket=`JIRA-142`, description=`add payment API`. Supports Jira, GitHub/GitLab bare numbers, Linear, and nested branch structures.
- **Type normalization** — Branch prefixes normalized to commit types: `feature`→`feat`, `bugfix`→`fix`, `hotfix`→`fix`, and 10 other standard types.
- **Smart humanization** — Branch slugs converted to readable descriptions with 40+ known acronyms preserved: `add-payment-api` → `add payment API`, `fix-oauth-redirect` → `fix OAuth redirect`.
- **Scope extraction** — First non-verb word after type becomes the commit scope: `feature/payment-add-api` → scope=`payment`. Nested branches also supported: `feature/payment/JIRA-142-add-api`.
- **Ticket placement options** — Ticket references appear in footer by default (`Refs: JIRA-142`) with a setting to place them in the title instead (`(JIRA-142)`).
- **Custom regex support** — Optional custom regex with named groups (`(?<type>...)`, `(?<ticket>...)`, `(?<scope>...)`, `(?<description>...)`) for non-standard branch naming.
- **Smart Branch settings** — Three new settings: enable/disable toggle (default: on), custom regex pattern, and ticket placement (footer vs title).
- **Branch context caching** — Parsed results cached in `ConcurrentHashMap` since branch names rarely change mid-session.

### Changed
- `PromptBuilder` now includes branch context (type, scope, ticket, description) in both system and user prompts for AI-powered generation.
- `TemplateGenerator` now uses branch-derived scope (overriding file-path detection) and handles ticket injection in footer or title.
- `CommitMessageService` resolves branch context via Git4Idea API and threads it through the entire generation pipeline.
- Settings UI gains a "Smart Branch" section between General and Cloud sections.

### Technical Details
- 73 new tests across `BranchNameParserTest` (60), `PromptBuilderTest` (5), and `TemplateGeneratorTest` (8).
- Total test count: 368 (up from 295).
- New package: `com.smartcommit.branch` with `BranchContext`, `BranchNameParser`, and `BranchUtils`.
- Depends on `Git4Idea` (already declared) for `GitRepositoryManager` branch name retrieval.

## [1.2.2] - 2026-03-05

### Added
- **Actionable "Not Connected" dialog** — When generating with Cloud but not connected, shows clear dialog with three options: "Connect IDE" (starts device-code flow directly), "Switch to OpenAI", or "Cancel". Replaces the old "Open Settings" dialog.
- **Cloud hint notification** — After generating with OpenAI or Ollama, shows a subtle nudge: "Tip: Smart Commit Cloud works without API keys. Free plan includes 30 generations/month." with a clickable "Connect IDE" action. Shown up to 3 times, never if already connected.
- **Shared DeviceCodeFlowHelper** — Extracted device-code flow into a reusable utility, eliminating code duplication across welcome dialog, settings, and error dialogs.

### Changed
- `NotificationUtils` now supports notifications with action links via `infoWithAction()`.
- `FirstRunStartupActivity` simplified from 205 to 71 lines by using shared `DeviceCodeFlowHelper`.

## [1.2.1] - 2026-03-05

### Fixed
- **Cloud fallback bug** — After IDE restart or token expiration, the plugin silently fell back to template-generated messages instead of notifying the user. Now properly shows "Not Connected" dialog with option to open Settings and reconnect.
- **Not Connected dialog** — Upgraded from a dismissible notification to an actionable dialog with "Open Settings" button for quick reconnection.

### Changed
- Added diagnostic logging in CloudProvider for easier debugging of auth/refresh issues.

## [1.2.0] - 2026-03-05

### Added
- **First-run welcome dialog** — Shown once after installation to explain Smart Commit Cloud: free tier, no API keys, secure. Offers "Connect IDE", "Use OpenAI instead", or "Close" as clear next steps.

## [1.1.1] - 2026-03-02

### Changed
- Toolbar icons reverted to standard IntelliJ icons (Lightning for Generate, PpLib for History) for better visual consistency with the IDE.
- Branded gradient icon kept for JetBrains Marketplace listing only.

## [1.1.0] - 2026-03-02

### Added
- **Smart Commit Cloud** — New default AI provider. Zero configuration, no API keys needed. Free tier with 30 generations/month, paid plans for Starter ($1/mo, 300/month) and Pro ($5/mo, 3,000/month).
- **Cloud-first settings UI** — Cloud provider shown first with "(Recommended)" label. Rich status panel with colored connection indicator, plan display, usage counter with reset countdown, and pricing link.
- **IDE Connect flow** — Device-code authentication to link your IDE to your Smart Commit Cloud account. Connect via browser approval.
- **Upgrade modal** — In-app upgrade prompt when free tier is exhausted, with plan comparison and direct links to pricing.
- **Usage notifications** — Post-generation notification showing current usage with upgrade nudge for free users.
- **Message history popup** — Browse previously generated commit messages (`Alt+H`) with live preview, cancel restore, and positioning that matches IntelliJ's built-in history popup.
- **Dynamic max subject length** — The AI prompt now respects the user's configured max subject length setting instead of always using 72 characters.
- **Settings loading state** — Cloud status shows "Loading..." while fetching account info, with "Could not load" fallback on failure.

### Changed
- Default AI provider changed from OpenAI to Smart Commit Cloud.
- OpenAI and Ollama settings sections are now hidden when not selected, reducing UI clutter.
- Sections renamed to "Advanced Customization" and "Generation Limits" for clarity.
- History action icon changed from clock (Vcs.History) to stacked layers (Nodes.PpLib) to distinguish from IntelliJ's built-in Commit Message History.

### Fixed
- **Enum serialization** — Removed `toString()` overrides on enums stored in `@State` to prevent settings corruption when display names change. Combo boxes now use `SimpleListCellRenderer` with `displayName` property.
- **Cloud server URL** — Hidden from settings UI (internal implementation detail).

### Removed
- Deprecated `CloudUsageClient` class and its tests (replaced by integrated `CloudProvider` endpoint).

### Technical Details
- Cloud provider uses single `POST /api/cloud/generate` endpoint (auth + usage + generation in one call).
- Token management via IntelliJ PasswordSafe with automatic refresh on 401.
- Server-side usage enforcement via atomic MongoDB operations.

## [1.0.0] - 2026-02-20

### Added
- **AI-Powered generation** using OpenAI (GPT-4o-mini, GPT-4o) or Ollama (local LLM).
- **Template-Based generation** for deterministic, offline commit messages.
- **Commit conventions**: Gitmoji (default), Conventional Commits, and Free-form.
- **Diff analysis engine** with 4-tier classification (path, change type, diff content, fallback).
- **Smart change classification** into 9 categories: feature, fix, refactor, test, docs, style, build, CI, chore.
- **IntelliJ commit dialog integration** via `CheckinHandlerFactory` extension point.
- **Generate Commit Message action** with `Alt+G` keyboard shortcut and toolbar button.
- **Auto-generate mode** that triggers on commit dialog open (configurable).
- **Overwrite confirmation** dialog when replacing existing commit messages.
- **Settings page** under `Settings > Tools > Smart Commit` with Kotlin UI DSL 2.
- **PasswordSafe integration** for secure OpenAI API key storage.
- **Triple-layer safety** in AI generator: try AI, fallback to template, last-resort hardcoded message.
- **Codepoint-safe title truncation** that never splits surrogate pairs (emoji, CJK).
- **Deterministic prompt size control** with hard caps on file count (30), diff tokens (4000), and total prompt characters (32K).
- **i18n support** via `SmartCommitBundle.properties` message bundle.
- **CI/CD pipeline** with GitHub Actions for build, test, verify, sign, and publish.

### Technical Details
- Target platform: IntelliJ IDEA 2024.1+ (build 241+)
- Language: Kotlin 1.9.25, JVM 17
- Build: Gradle 8.13 with IntelliJ Platform Gradle Plugin 2.11.0
- HTTP client: OkHttp 4.12.0
- JSON: kotlinx-serialization-json 1.6.3
- Testing: JUnit 4, MockK 1.13.9, OkHttp MockWebServer

[1.3.1]: https://github.com/chafian/smart-commit-intellij/releases/tag/v1.3.1
[1.3.0]: https://github.com/chafian/smart-commit-intellij/releases/tag/v1.3.0
[1.2.2]: https://github.com/chafian/smart-commit-intellij/releases/tag/v1.2.2
[1.2.1]: https://github.com/chafian/smart-commit-intellij/releases/tag/v1.2.1
[1.2.0]: https://github.com/chafian/smart-commit-intellij/releases/tag/v1.2.0
[1.1.1]: https://github.com/chafian/smart-commit-intellij/releases/tag/v1.1.1
[1.1.0]: https://github.com/chafian/smart-commit-intellij/releases/tag/v1.1.0
[1.0.0]: https://github.com/chafian/smart-commit-intellij/releases/tag/v1.0.0
