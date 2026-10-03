# Built-in cheat compatibility — issue #17

## Exact-ROM policy

`CheatPresetPolicy.production` is the single built-in approval catalog. It is
**empty**: the repository has no adequate independent exact-target verification
for the previously shipped codes. No production preset identity, payload, format,
or supported hash/version is approved. Do not repopulate it with plausible codes.

| ROM | Built-in result |
| --- | --- |
| H&S 2.0.5 (`edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b`) | None |
| Other H&S images, even with identical titles | None |
| FireRed / LeafGreen / Emerald, including exact recognized releases | None |
| Recognized/unverified, unsupported, missing identity | None |
| Future R.O.W.E., Unbound, or other verified profiles | None without separate exact-target code evidence |

Names, profile IDs, native game IDs, base-game families, valid SHA syntax and
VERIFIED live-memory trust never authorize built-ins. A future catalog entry must
have a stable preset ID, exact supported hash/version, literal code payload and
format, and documented independent verification of that payload on that target.
Authorization compares the saved ID **and full original payload** against the
exact hash's catalog; a saved flag or arbitrary ID alone grants nothing.
Synthetic approvals in `CheatManagerTest` are test fixtures only, not shipped
approvals or real-ROM evidence.

## Entry points and session binding

Default listing, explicit loading/reset, save/add/update/toggle and final
application use the same policy. All name-only legacy APIs return no presets or
perform no mutation. Empty preset loading is rejected without replacing custom
entries. Updates and saves retain existing built-in provenance, preventing a flag
edit from turning a quarantined built-in into a custom code.

`LibretroCoreCoordinator` holds the loaded cheat ROM hash under its existing fair
reentrant lock. Load/unload/core replacement/cleanup invalidate it.
`RomSessionManager` binds the successfully loaded, SHA-checked new image within
the existing switch transaction, then automatically reapplies its SHA-scoped
cheats **before** publishing the new ViewModel session. Detection occurs against
those same new bytes; cheat authorization has no dependency on the profile's live
trust or the old ViewModel. The injected cheat manager uses that same coordinator.

`applyCheats` checks the binding, reads/transitions entries, resets, rechecks each
built-in approval, validates syntax and sets every enabled payload within one
exclusive coordinator transaction. A stale ROM A action after switching to B
cannot even reset B's cheats. Stale add/edit actions may retain changes in A's own
SHA store but report application rejection. Reset/load also checks binding before
changing storage. No new JNI path or emulation-thread architecture is used.

All storage access goes through one process-wide transaction monitor shared by
all `CheatManager` instances, including managers with separately injected
coordinators. The lock order is **core coordinator → shared storage monitor**.
A transaction captures its coordinator once; locked helpers never acquire a
different coordinator while holding storage. Listing/quarantine, explicit save,
add/update/toggle/delete, Disable All, preset loading/reset and application keep
the complete read-modify-persist operation inside that boundary. UI Disable All
calls `disableAllCheats` instead of composing a read and later whole-list save.
`saveCheats` remains an explicit whole-list replacement API; callers needing a
read-modify operation use the atomic manager methods. No storage-only lock path
waits for the core. ROM loading already holds the same core lock before entering
this transaction, so its ordering remains consistent.

## Persisted transition and custom entries

Legacy entries with explicit `isPreset: true` remain built-ins; unapproved ones
are disabled with an explanation. Entries with missing/non-boolean provenance
are quarantined as built-ins with an ambiguity explanation. An explicit legacy
`isPreset: false` remains user-entered custom provenance. Missing provenance
never receives approval, even if an ID and payload happen to match a catalog.

The transition on reads and writes is idempotent and persisted before automatic
application. IDs, names and code text are retained. No blanket reset or changes to
ROM identities, SRAM, save states or save migration are made. Malformed JSON fails
closed without deleting the original stored text. There is no blanket migration
from name-keyed storage; existing SHA isolation remains intact.

User-supplied custom codes remain controllable per ROM SHA and are labelled
**unverified**. Empty/placeholder/malformed entries are disabled without deleting
text. Syntax validation checks 8+8 or 8+4 hex words, preserving multiline order,
whitespace and `+` separators and existing whole-line `#`/`//` comments, then sends
canonical lines. This follows the GBA splitter in
[mGBA libretro at e31759b](https://github.com/mgba-emu/mgba/blob/e31759b/src/platform/libretro/libretro.c)
and its [GBA parser](https://github.com/mgba-emu/mgba/blob/e31759b/src/gba/cheats.c)
(the packaged arm64 core reports `0.11-1-e31759b`). It does not decode effects or
claim code compatibility. The core API provides no per-code success receipt;
“settings applied” records dispatch, not semantic correctness in the game.

The UI disables Load Presets, explains “No verified built-in cheats for this
exact ROM,” shows quarantine reasons and custom provenance, and displays manager
result messages instead of optimistic activation/preset-loading success. Enabled
means saved user intent; it is not a claim of verified game effects.

## Validation and remaining acceptance

Starting SHA: `dd64abadc36724c714535dce4e132d3c3837677e` (PR #120 merge).
Main had not advanced from the supplied baseline. Its Native & Unit Tests, Build
Debug APK and H&S Source Validation GitHub checks all passed. Those checks are
the exact-SHA baseline evidence; no baseline CI failures were observed. An
initial local `all` run also passed, but overlapped editing; the final commands
below ran after implementation stopped changing.

Focused regression coverage is in `CheatManagerTest`: production defaults/name
APIs, exact H&S and live trust independence, synthetic exact ID/payload/hash
approval and forgery rejection, legacy quarantine/idempotence/text preservation,
SHA isolation, empty-catalog non-destruction, stale actions, multiline syntax and
result messages, automatic load/reload/switch and reset/set serialization.
Existing `RomSaveIntegrityTest` and `LibretroCoreCoordinatorLockTest` remain in
the canonical suite. Validation commands: `./ci.sh all`, `./ci.sh source-check`,
`git diff --check`. The original implementation `all` run passed (1,098 Kotlin tests, including eight
cheat, 58 save-integrity and four coordinator-lock tests, plus native/calculator/
tooling suites and debug APK). Final `./ci.sh source-check` and
`git diff --check` also passed. Pushed-head GitHub results are recorded in the PR
and issue handoff.
During implementation an outdated preset-default assertion and an incomplete
fake-core SRAM writer failed; neither was a baseline failure. Fixtures were
corrected without weakening production save checks or H&S artifacts.

### Senior-review P1 follow-up

Review of `3f5ca9ca219d45764ede77f55453d7409e8cce4d` reproduced a stale
quarantine write that could restore an enabled custom entry after a user disable.
The shared transaction above addresses that finding without changing approvals,
syntax, ROM binding, custom provenance or save-storage behavior.

Deterministic tests pause the actual migration write in a thread-safe store and
run a second manager's disable, delete, add or update. They exercise both shared
and distinct core coordinators, propagate worker failures, bound waits and joins,
and verify storage plus subsequent dispatch through both managers. Five tests
failed against the unchanged reviewed manager (blob
`c779649e5da90af348bbc77f8b640055784be638`), proving the negative control.
The fix also adds a Disable All regression: six new tests in total, alongside
the original eight. Follow-up `./ci.sh all` passed (1,104 Kotlin tests, all 14
cheat regressions, native/calculator/tooling suites and debug APK);
`./ci.sh source-check` and `git diff --check` also passed. Pushed-head results
are recorded in the PR/issue re-review handoff. Hardware remains NOT_RUN.

Hardware: **NOT_RUN**. No authorized Thor setup was used and no experimental
codes were executed against real saves. Human smoke checklist:

1. Back up **all** saves before any device-changing test. Keep app data installed.
   Use a test setup with custom entries disabled **before loading a ROM**; do not
   load real saves with enabled unverified codes for this smoke test.
2. Open exact H&S 2.0.5 → Cheats. Confirm no verified built-ins and disabled Load
   Presets, with a visible explanation.
3. Confirm existing custom names/code text remain and are labelled unverified;
   do not enable unverified codes. Inspect any legacy built-in's retained text and
   disabled explanation.
4. Reload/restart H&S; confirm quarantine persists and custom entries remain.
5. Open an Add Cheat dialog on A, switch to B, then submit an **empty/placeholder**
   code; confirm no activation success and no B entry. Return to A to inspect any
   retained disabled entry. Do not submit executable guessed codes.
6. Repeat A/B switching and confirm lists follow SHA identity and unavailable
   presets never erase custom entries.

UI layout/accessibility and device reload/switch smoke checks remain hardware
acceptance work. No real-ROM built-in execution is certified. This resolves the
implementation gap delegated from the historical #40 audit; it does not complete
#75, the Thor matrix, Assistant #13, or all pre-beta hardening.
