# CI cost controls and manual checks

`DualDex CI` still reports all three existing job names on every PR to `main`:
`Native & Unit Tests`, `Build Debug APK`, and `H&S Source Validation`.
There are no workflow path filters or skipped jobs that leave required checks pending.
Each job checks out the repository, classifies the change and tests the classifier.
Only expensive steps are conditional; classification/test failures fail the job.
Missing or unrecognized classifier output does not authorize skipping work.

The lightweight PR path accepts **only ordinary edits to existing regular files**
in this explicit list:

- `FUTURE_PLATFORM_ROADMAP.md`
- `POST_BETA_ENHANCED_BATTLE_CONSOLE.md`
- `UI_DESIGN_AUDIT.md`
- `docs/CI.md`

These planning/CI documents are not inputs to the regression suites. The jobs run
classifier regression tests and `git diff --check` for these edits. Reviewers still
check prose accuracy and links; this does not certify gameplay or release claims.
Do not move authority or executable inputs into this allowlist.

All other paths require the complete existing gates, including README (which
contains exact-version trust claims), contribution/build/release contracts,
H&S and native authority/provenance/evidence documents, scripts, source, tests,
content/schema, dependencies and workflows. Mixed changes, unknown paths,
additions, deletions, renames, file-mode changes and empty diffs also require full
CI. There is deliberately no `docs/**` or `*.md` exemption.

Classification compares immutable PR base/head event SHAs with NUL-delimited,
rename-disabled Git output. Full history makes both endpoints available, including
fork PRs. Base changes since divergence can cause extra full validation. Git errors
fail the job; missing event metadata defaults to full validation. Every push to
`main` runs the complete gates regardless of paths.

New runs supersede older runs **only for the same PR**. Main pushes have unique
concurrency groups. The workflow uses setup-java's Gradle dependency cache keyed
by Gradle build files, wrapper properties and project properties; cache hits never
skip tests or builds. Cold caches and simultaneous first writers remain possible.

## Local validation

From the repository root:

```bash
python3 -m unittest discover -s tools/ci -p 'test_classify_changes.py' -v
# Install actionlint separately if needed; it validates YAML and Actions expressions.
actionlint .github/workflows/ci.yml
git diff --check
./ci.sh test
./ci.sh build
# Equivalent complete test/build contract:
./ci.sh all
# Independent pinned-source gate (needs the upstream checkout and ARM/newlib tools):
HNS_UPSTREAM_DIR=/path/to/pokehns-expansion ./ci.sh source-check
```

Use the JDK 17 / Android SDK, NDK and CMake prerequisites in `AGENTS.md`.
The source checkout must be the workflow's pinned revision; the canonical script
verifies it fail-closed. Production signing/release remains human-only.

The active `protect-main` ruleset at implementation time requires `Native & Unit
Tests` and `Build Debug APK`. Their names and reporting behavior are preserved,
as is `H&S Source Validation`. No repository setting is changed. The legacy branch
protection API was inaccessible (403); any additional administrative requirements
must be verified by a maintainer. This workflow does not add merge-queue support
or alter existing trigger coverage.
