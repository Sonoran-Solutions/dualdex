# DualDex — Agent Collaboration Guidelines

Welcome to DualDex. This repository is integrated with the Sonoran Solutions multi-agent development workflow.

---

## 1. Agent Roles & Authority

Any agent working in this repository is expected to:

- **Stay in scope**: work only on the task you were given. Don't make unrelated refactors, drive-by changes, or modifications outside this project.
- **Work on a branch**: never push to `main`. All changes go through a pull request.
- **Get human approval to merge**: a human reviewer has final review and merge authority. Don't merge without approval
- **Verify before handing off**: run the focused `ci.sh` targets your change can affect during development, and make sure the candidate has one valid `./ci.sh all` result before review (see section 2a for who runs it and when evidence can be reused).

---

## 2. Canonical Build & Test Contract

All agents **MUST** use the canonical CI script at the repository root. Do not invent custom Gradle or CMake commands.

```bash
# Focused targets (prefer these while iterating; pick by the behavior you changed)
./ci.sh fixture-test    # ROM-free production replay, ROWE raw decoder, identity adapter
./ci.sh rowe-test       # host-only raw decoder + identity adapter synthetic tests
./ci.sh coverage-test   # H&S debug coverage tests + structural release proof

# Run the full test suite (native, calculator, H&S tooling, and Kotlin unit tests)
./ci.sh test

# Build debug APK (app-debug.apk)
./ci.sh build

# Complete test + build cycle
./ci.sh all
```

See the header comment in `ci.sh` for exactly which suites each target runs. `./ci.sh release` also exists but is reserved for humans (see Repository Safety Rules).

- **Prerequisites**: JDK 17, Android SDK Platform 34, Android NDK 27.2.12479018, CMake 3.22.1, and a host C compiler (GCC or Clang) for the native host suites.
- **Git Submodules**: `native/quickjs` is a submodule required to build **and** to run the calculator suite. `./ci.sh test`, `build`, and `all` check it out at the exact commit recorded by the superproject and fail (rather than degrade) if it is missing, stale, or wrong.
- **Fail-closed**: every command is deterministic and non-interactive. A missing prerequisite, a failing native, calculator, or Kotlin test, or a failing build all return a non-zero exit code — nothing is silently skipped.
- **Calculator goldens**: `native/tests/test_js_calc.c` asserts exact Gen III damage values with documented provenance; see [docs/QUICKJS_CALCULATOR_TESTS.md](docs/QUICKJS_CALCULATOR_TESTS.md) before changing fixtures or `tools/calc-bundler/entry.js`.

---

## 2a. Validation policy

Follow the global validation and job-supervision rules (and the `validation-supervision` skill) for risk-based check selection, evidence reuse, host-slot limits, and bounded supervision.
Repo specifics: `ci.sh` is the only command vocabulary; the agent opening or updating the PR owns the candidate's `./ci.sh all` (hosted CI on the same SHA counts), and supervised jobs must clean up any Gradle daemons or emulator/core processes they start.

Gates that remain explicit: human merge approval (section 1), release signing (section 4), the byte-for-byte generated-artifact checks in `./ci.sh test`, and calculator golden provenance (`docs/QUICKJS_CALCULATOR_TESTS.md`).

| Behavior area | Verified command | Expected result | Normal duration | Broader-validation trigger |
|---|---|---|---|---|
| Docs, audits, evidence | diff review only | n/a | n/a | never for inert files |
| Memory fixtures, ROWE decoder, identity adapter | `./ci.sh fixture-test` / `./ci.sh rowe-test` (`rowe-sanitize` for memory-safety concerns) | exit 0 | TBD (not yet baselined) | profile JSON or `pokemon_reader.*` offsets changed → `./ci.sh test` |
| H&S coverage and release proof | `./ci.sh coverage-test` | exit 0 | TBD (not yet baselined) | coverage-affecting UI or battle console changes |
| Everything (native, calculator, oracle, generators, Kotlin) | `./ci.sh test` | exit 0 | TBD (not yet baselined) | any cross-boundary change; required once per candidate via `all` |
| Candidate APK | `./ci.sh build` / `./ci.sh all` | `app-debug.apk` produced, exit 0 | TBD (not yet baselined) | once per review candidate, by the integration owner |

---

## 3. Handoff Protocol & Envelopes

Task transitions are driven by structured YAML envelopes in GitHub issues and PRs (Schema Version 1):

### Receiving Work
When a task is assigned to you, the envelope specifies:
```yaml
---
schema_version: 1
task_id: "ORCH-xxx"
agent: <planner>
to: <implementer>
repo: Sonoran-Solutions/dualdex
branch: feat/<feature-name>
action: implement
acceptance: |
  - Criteria 1...
  - Criteria 2...
---
```

### Handing Off for Review
After implementing the changes and confirming the candidate has a valid `./ci.sh all` result (yours, or reused per section 2a):
1. Push branch to `origin feat/<feature-name>`.
2. Open or update PR against `main`.
3. Post the review handoff block in the PR description:
```yaml
---
schema_version: 1
task_id: "ORCH-xxx"
agent: <implementer>
to: <reviewer>
repo: Sonoran-Solutions/dualdex
branch: feat/<feature-name>
action: review
summary: |
  Implementation complete. ./ci.sh all passed on <tested sha>; later revisions
  (if any) and why that evidence still applies.
pr_url: https://github.com/Sonoran-Solutions/dualdex/pull/<number>
---
```

---

## 4. Repository Safety Rules

1. **Branch naming**: Use `feat/...`, `fix/...`, or `chore/...` branches (see section 1 for the no-push-to-`main` rule).
2. **Never commit secrets**: Do not commit tokens, credentials, or `.env` files.
3. **Never commit game files**: Do not commit ROMs, save files, save states, or other copyrighted game data (`*.gba`, `*.gb`, `*.gbc`, `*.nds`, `*.sav`, `*.srm`, `*.ss0`–`*.ss9`, etc.; these are also in `.gitignore`). Tests and tooling must stay ROM-free.
4. **Release signing is human-only**: Do not run `./ci.sh release` or create, edit, or read `signing.properties` or keystores. `signing.properties.example` is the only signing file that belongs in the repo.
5. **Don't hand-edit generated files**: Many data tables, layouts, corpora, and census artifacts are produced by generators under `tools/` (`generate_*.py`), and `./ci.sh test` checks several of them byte-for-byte against their sources. Change the generator or its inputs and regenerate; never patch the output directly. See the relevant tool's README (e.g. `tools/hns-damage-oracle/README.md`) for regeneration steps.
6. **ROM Hack Profiles**: New profiles belong in `app/src/main/assets/profiles/<id>.json` and must have corresponding unit tests in `RomHackProfileTest.kt`.
7. **Native C Code**: Follow C11 standard; keep memory offsets synchronized between `pokemon_reader.h`, `pokemon_reader.c`, and profile JSON files.

---

## 5. Where to Look

Read the relevant docs before changing behaviour:

- [README.md](README.md): project overview.
- [ARCHITECTURE.md](ARCHITECTURE.md): how the mGBA emulator core, native memory reader, calculator, and dual-screen UI fit together.
- [CONTRIBUTING.md](CONTRIBUTING.md): development setup and how to add a new ROM hack profile.
- [docs/QUICKJS_CALCULATOR_TESTS.md](docs/QUICKJS_CALCULATOR_TESTS.md): calculator golden fixtures and their provenance.
- [docs/](docs/) `HNS_*.md`: H&S compatibility evidence, calculator capability, and closure audits. Check these before changing H&S calculator or live-state behaviour.
- [RELEASE_ENGINEERING.md](RELEASE_ENGINEERING.md) and [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md): release process (human-driven).
