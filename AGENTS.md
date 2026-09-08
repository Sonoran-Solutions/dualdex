# DualDex — Agent Collaboration Guidelines

Welcome to DualDex. This repository is integrated with the Sonoran Solutions multi-agent development workflow.

---

## 1. Agent Roles & Authority

| Role | Agent / Harness | Responsibilities |
|---|---|---|
| **Primary Implementer** | **Google Antigravity** | Feature development, bulk codegen, scaffolding, UI components, ROM hack profiles, native C updates, and local builds. |
| **Planner & Reviewer** | **Codex** | Task breakdown, acceptance criteria, architectural design, and pull request review against specifications. |
| **Build Herald / Technician** | **Hermes Agent** | CI failure triage, automated bounded build repairs on test branches. |
| **Specialist / Hub** | **DeepSeek Harness** | Multi-agent coordination, webhook routing, specialized algorithmic debugging. |
| **Product Gate** | **Human** | Final review and merge authority. |

---

## 2. Canonical Build & Test Contract

All agents **MUST** use the canonical CI script at the repository root. Do not invent custom Gradle or CMake commands.

```bash
# Run native C parser tests + Kotlin unit tests
./ci.sh test

# Build debug APK (app-debug.apk)
./ci.sh build

# Complete test + build cycle
./ci.sh all
```

- **Prerequisites**: JDK 17, Android SDK Platform 34, Android NDK 27.2.12479018, CMake 3.22.1, and a host C compiler (GCC or Clang) for the native test runner.
- **Git Submodules**: `native/quickjs` is a submodule required to build. `./ci.sh build` and `./ci.sh all` initialize it and fail (rather than degrade) if it cannot be initialized; `./ci.sh test` does not need it.
- **Fail-closed**: every command is deterministic and non-interactive. A missing prerequisite, a failing native or Kotlin test, or a failing build all return a non-zero exit code — nothing is silently skipped.

---

## 3. Handoff Protocol & Envelopes

Task transitions are driven by structured YAML envelopes in GitHub issues and PRs (Schema Version 1):

### Receiving Work from Codex
When Codex plans an issue for Antigravity, the envelope specifies:
```yaml
---
schema_version: 1
task_id: "ORCH-xxx"
agent: codex
to: antigravity
repo: Sonoran-Solutions/dualdex
branch: feat/<feature-name>
action: implement
acceptance: |
  - Criteria 1...
  - Criteria 2...
---
```

### Handing Off to Codex for Review
After implementing the changes and confirming `./ci.sh all` succeeds:
1. Push branch to `origin feat/<feature-name>`.
2. Open or update PR against `main`.
3. Post the review handoff block in the PR description:
```yaml
---
schema_version: 1
task_id: "ORCH-xxx"
agent: antigravity
to: codex
repo: Sonoran-Solutions/dualdex
branch: feat/<feature-name>
action: review
summary: |
  Implementation complete. Tested with ./ci.sh all.
pr_url: https://github.com/Sonoran-Solutions/dualdex/pull/<number>
---
```

---

## 4. Slack Visibility & Notifications

Per Sonoran policy (ORCH-034/035), do **not** post per-tool or per-file notifications. Post only top-level **state transitions** using the shared script:

```bash
# Available transitions:
/home/dq/sso-orchestrator/slack-notify/slack-notify.sh task-started "<task-id>" --branch "feat/..."
/home/dq/sso-orchestrator/slack-notify/slack-notify.sh pr-ready "<task-id>" --branch "feat/..." --link "<pr-url>"
/home/dq/sso-orchestrator/slack-notify/slack-notify.sh blocked "<task-id>" --reason "<why>"
/home/dq/sso-orchestrator/slack-notify/slack-notify.sh done "<task-id>" --link "<pr-url>"
```

Configuration is loaded from `~/.config/sonoran/orchestrator.env`.

---

## 5. Repository Safety Rules

1. **Never commit directly to `main`**: All changes must be developed on a branch (`feat/...`, `fix/...`, `chore/...`) and merged via pull request.
2. **Never commit secrets**: Do not commit tokens, credentials, or `.env` files.
3. **ROM Hack Profiles**: New profiles belong in `app/src/main/assets/profiles/<id>.json` and must have corresponding unit tests in `RomHackProfileTest.kt`.
4. **Native C Code**: Follow C11 standard; keep memory offsets synchronized between `pokemon_reader.h`, `pokemon_reader.c`, and profile JSON files.
