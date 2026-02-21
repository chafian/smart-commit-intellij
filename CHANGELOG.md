# Changelog

All notable changes to the **Smart Commit** plugin will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

[1.0.0]: https://github.com/smart-commit/smart-commit-plugin/releases/tag/v1.0.0
