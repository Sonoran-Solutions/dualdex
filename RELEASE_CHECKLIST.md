# DualDex Public Beta Task List

This checklist tracks work recommended before a wider public beta, followed by longer-term work for broader Android support and possible Play Store distribution.

## Release Readiness Principles

Before working down the individual tasks, keep the following release-level rules in place. The goal for `0.9.0-beta.1` is not to make DualDex feature-complete; it is to make a narrow set of core promises trustworthy enough for other people to use with real save data.

### Scope and Product Focus

- [ ] **Feature-freeze major additions until the beta blockers are complete.**
  - Do not add new major companion features, broad single-screen work, ads, cloud saves, or speculative emulator features during hardening.
  - Finish already-active ROM support work only when it does not delay architecture/safety fixes.
  - Put good new ideas into issues/backlog instead of expanding the beta scope.
- [ ] Keep the initial beta promise narrow:
  - Reliable GBA Pokémon emulation on AYN Thor.
  - Protected/recoverable saves.
  - Trustworthy live companion data for a small, explicitly verified set of ROM versions.
- [ ] Treat Assistant and Cheats as optional/experimental if they are not fully verified by release time rather than delaying the core beta.
- [ ] Avoid unnecessary rewrites during beta hardening.
  - No full Compose rewrite.
  - No large dependency modernization project unless required for a blocker.
  - Prefer targeted architecture fixes that preserve already-working behavior.

### Lock Permanent App Identity Before Distribution

- [x] Decide whether the current Android application ID/package (`com.dualdex`) is the permanent identity before distributing public builds. **Decision: `com.dualdex` is the permanent public application ID** (see [RELEASE_ENGINEERING.md](RELEASE_ENGINEERING.md)).
- [ ] Generate and begin testing with the permanent production signing key before public beta.
- [x] Establish release versioning (`0.9.0-beta.1`, `beta.2`, etc.) before the first public APK. **`versionName = 0.9.0-beta.1`, `versionCode = 1`, monotonic +1 policy** (see [RELEASE_ENGINEERING.md](RELEASE_ENGINEERING.md)).
- [ ] Introduce a persistent storage/settings schema version so future releases can run explicit migrations rather than relying on old files continuing to work by accident.
- [ ] Create a known-good pre-hardening tag/branch before the deeper save/core/profile refactors so regressions are easy to bisect or revert.

### Make Save Protection a Product Rule

- [ ] Treat preservation of the user's battery save as higher priority than preserving UI state, save states, or convenience features.
- [ ] Add a simple `Export/Backup All Saves` path before public beta if practical.
- [ ] Never perform a destructive save/storage migration without retaining the previous data until the new format is confirmed valid.
- [ ] Test migrations from existing development storage into the new per-ROM identity scheme.

### Release in Rings

Do not go directly from one developer device to a public beta.

1. [ ] Test a release-signed build on the primary development Thor.
2. [ ] Give the exact build to roughly 3–5 trusted Thor testers.
3. [ ] Expand to roughly 10–20 testers once installation, updates, saves, and device behavior look stable.
4. [ ] Only then publish the wider GitHub beta.

- [ ] Collect device/Android/build diagnostics from each testing ring.
- [ ] Use feedback from these rings to fix release blockers rather than adding features.

### Test Upgrades, Not Only Fresh Installs

For every beta candidate:

- [ ] Test a clean install.
- [ ] Test installing the new signed APK over the previous beta.
- [ ] Verify ROM folder permissions survive the update.
- [ ] Verify Continue/last-ROM behavior survives the update.
- [ ] Verify battery saves survive and load correctly.
- [ ] Verify settings survive or migrate correctly.
- [ ] Verify save states are either compatible or clearly identified as incompatible.
- [ ] Test the transition from current debug/development builds to the permanent release-signed build and document any required save export/import step.

### Hardware Soak Testing

Before promoting a release candidate:

- [ ] Run at least one extended real-play session on AYN Thor rather than only short smoke tests.
- [ ] Exercise normal speed and fast-forward.
- [ ] Exercise save/load state repeatedly.
- [ ] Suspend/resume the app multiple times.
- [ ] Switch ROMs repeatedly.
- [ ] Use the companion tabs and calculator throughout the session.
- [ ] Watch for memory growth, audio drift, thermal issues, battery drain, frame pacing problems, stale companion data, and crashes that only appear after extended play.

### Battle Tab Readiness

- [ ] **Finish the Battle tab features required for the AYN Thor beta.** Validate the battle, party, field, and profile-aware presentation on-device, and document any remaining limitations before the beta release.

### Make Compatibility Claims Conservative

- [ ] Publish exact verified ROM versions/hashes instead of claiming generic support based only on a hack name.
- [ ] Maintain a compatibility matrix per verified ROM/version, for example:
  - Party reading
  - Battle/enemy tracking
  - Damage calculator
  - Type chart
  - Map/location
  - Battery saves/import/export
  - Cheats, if offered
- [ ] Label incomplete support as experimental/unverified instead of presenting approximate data confidently.
- [ ] Rewrite the release-facing README around what is actually verified at release time: installation, screenshots, exact compatibility, save backup, known limitations, bug reporting, privacy, and non-affiliation.

### Release Candidate Discipline

- [ ] Cut a release candidate such as `0.9.0-beta.1-rc1` once the checklist is substantially complete.
- [ ] Test the **exact release-candidate APK** that will become the beta.
- [ ] Do not add unrelated polish/features to an RC.
- [ ] If a blocker is found, fix only the blocker and cut `rc2`/later rather than silently changing the tested artifact.
- [ ] Promote the same tested commit/artifact lineage to `0.9.0-beta.1` once the RC passes the tester rings and soak tests.

> **Target beta promise:** DualDex runs GBA Pokémon games reliably on the AYN Thor, protects the user's saves, and provides trustworthy live companion information for a small documented list of exact ROM versions.

## Phase 1 — Release Blockers

### Save Safety and ROM Switching

- [ ] Change save identity from detected profile name to a unique ROM identity.
  - Prefer SHA-256 or another stable ROM-specific identifier.
  - Prevent multiple FireRed-based hacks from sharing the same `.sav`, quicksave, or save-state files.
- [ ] Store friendly metadata alongside the ROM-specific save ID.
  - ROM display name
  - Detected hack/profile
  - ROM SHA-256
  - Last played date
- [ ] Create a safe ROM-switching sequence:
  1. Stop/pause emulation.
  2. Flush the currently loaded ROM's battery save.
  3. Create/update backup.
  4. Unload the current ROM from mGBA.
  5. Clear/reset companion state.
  6. Load new ROM.
  7. Load the correct battery save.
  8. Resume emulation.
- [ ] Explicitly call the Libretro unload-game path before loading another ROM.
- [ ] Make battery save writes atomic.
  - Write to a temporary file first.
  - Verify successful write.
  - Replace main `.sav` only afterward.
- [ ] Keep at least one automatic `.sav.bak` backup.
- [ ] Safely handle failed save imports.
  - Do not overwrite the existing save until the imported save has been validated successfully.
- [ ] Test intentionally switching rapidly between several ROMs.
- [ ] Test exiting/killing DualDex immediately after an in-game save.
- [ ] Test app pause/resume while saving.
- [ ] Test battery-save recovery from a backup.

### Save Import / Export Cleanup

- [ ] Remove My Boy!-specific wording from the import UI.
- [ ] Rename the feature to something emulator-neutral such as `Import Battery Save (.sav)` or `Import GBA Save`.
- [ ] Update help text to explain that standard GBA `.sav` files from compatible emulators are supported.
- [ ] Mention tested compatibility rather than implying exclusivity.
  - DualDex
  - RetroArch/mGBA
  - My Boy!
  - Other emulators using standard GBA SRAM/Flash saves
- [ ] Add validation for known common GBA save sizes.
- [ ] Continue stripping/converting known emulator-specific footer data when safe.
- [ ] Test import/export round trips against multiple emulators.
- [ ] Add a warning before replacing an existing DualDex battery save.
- [ ] Automatically back up the existing save before an import.

### ROM Detection and Unsupported Games

- [ ] Replace the current `unknown ROM -> fallback profile` behavior.
- [ ] Introduce explicit ROM compatibility states:
  - **Verified** — exact ROM/version hash supported.
  - **Recognized / Unverified** — likely known hack, but exact version not verified.
  - **Unsupported** — emulator works but companion memory features are unavailable.
- [ ] Never display memory-derived Pokémon information using an arbitrary fallback profile.
- [ ] Disable unsafe companion features when ROM layout is unknown.
  - Live party reading
  - Opponent reading
  - Location tracking
  - Auto-filled damage calculations
  - Hack-specific mechanics
- [ ] Allow safe generic features for unsupported ROMs.
  - GBA emulation
  - Save states
  - Battery saves
  - Shaders
  - Controls
- [ ] Show the user exactly why a feature is disabled.
- [ ] Display detected ROM SHA/version information in the UI.
- [ ] Add a `Report Unsupported ROM` flow that copies useful diagnostics.

### ROM Profile Architecture

- [ ] Refactor profiles so JSON configuration actually controls the native memory parser.
- [ ] Stop relying on a new hard-coded native `gameId` for every ROM hack.
- [ ] Separate **engine/layout profiles** from **individual ROM profiles**.

#### Engine / Layout Profile

Should define things such as:

- Party memory layout
- Player party offset
- Party count offset
- Enemy party offset
- Battle struct layout
- Location structure
- EV/IV representation
- Save RAM behavior
- Base engine family
- Other low-level memory differences

Potential families include vanilla FireRed, vanilla Emerald, CFRU, pokeemerald-expansion, and other common decomp engines.

#### ROM Profile

Should define:

- Hack name
- Developer
- Base game
- Engine/layout profile
- Supported versions
- SHA-256 hashes
- Custom species
- Custom typings
- Mechanics overrides
- Generation rules
- Documentation URL
- Version-specific memory overrides where necessary

- [ ] Allow multiple supported versions of the same hack without creating an entirely new native enum.
- [ ] Validate profile JSON on startup/build.
- [ ] Fail clearly if a profile contains invalid or missing offsets.
- [ ] Add schema/version information to profile JSON so the format can evolve later.

### Emulator Core Thread Safety

- [ ] Make one thread responsible for operations that mutate mGBA state.
- [ ] Add an emulator command queue or equivalent.
- [ ] Route core operations through it:
  - Load ROM
  - Unload ROM
  - Reset
  - Save state
  - Load state
  - Apply cheats
  - Flush/load SRAM
- [ ] Ensure these operations do not race against `retro_run()`.
- [ ] Keep read-only memory polling clearly separated and verified as safe.
- [ ] Stress-test save states while fast-forwarding.
- [ ] Stress-test ROM switching while companion polling is active.

### Controller Functionality

- [ ] Wire L2 to actual quick-save behavior.
- [ ] Wire R2 to actual quick-load behavior.
- [ ] Wire the advertised X/Y shortcuts to their actual functions.
- [ ] Wire the fast-forward shortcut to the emulator speed toggle.
- [ ] Ensure shortcut buttons do not accidentally get passed to the GBA core when they are meant to trigger DualDex functions.
- [ ] Add optional button remapping later if practical.
- [ ] Verify every controller feature advertised in README and Settings actually works.

## Phase 2 — Public Beta Infrastructure

### Versioning

- [x] Change version from `1.0.0` to a beta version, e.g. `0.9.0-beta.1`. **Done: `0.9.0-beta.1`.**
- [x] Establish versioning rules before release. **Monotonic `versionCode` policy in [RELEASE_ENGINEERING.md](RELEASE_ENGINEERING.md).**
- [x] Include version number in Settings/About. **About & Diagnostics now shows version, versionCode, and build type from `BuildConfig` metadata.**

### Android Signing

- [ ] Generate a permanent production signing key.
- [ ] Back the signing key up securely in more than one safe location.
- [x] Never ship a public release signed using a temporary/debug key. **Enforced fail-closed in `app/build.gradle.kts`: release has no debug-signing fallback and fails without credentials.**
- [ ] Use the same signing identity for all future public builds.
- [x] Document the release-signing process. **Documented in [RELEASE_ENGINEERING.md](RELEASE_ENGINEERING.md) (no secrets committed; key generation/backup remain human steps).**

### GitHub Releases

- [ ] Create an official GitHub Release for every beta build.
- [ ] Attach signed APK.
- [ ] Include version, date, supported ROM/hack versions, known issues, installation instructions, upgrade notes, and SHA-256 checksum.
- [ ] Create a changelog.
- [ ] Tag releases consistently.

### CI

- [ ] Add GitHub Actions or equivalent CI.
- [ ] Run Kotlin unit tests on every PR/push.
- [ ] Run native C parser tests.
- [ ] Run native calculator tests.
- [ ] Build a debug APK to catch integration failures.
- [ ] Eventually automate signed release builds after the signing process is mature.

### Documentation Cleanup

- [ ] Fix incorrect Git clone URL.
- [ ] Fix `gameId` documentation inconsistency.
- [ ] Audit tests for stale hard-coded `gameId` assumptions.
- [ ] Update supported-ROM list to match reality.
- [ ] Document specific supported hack versions rather than only hack names.
- [ ] Mark experimental/unverified support clearly.
- [ ] Remove outdated feature claims.
- [ ] Verify README controller mappings.
- [ ] Add installation instructions for sideloading.
- [ ] Add an FAQ.
- [ ] Add troubleshooting instructions.
- [ ] Audit Gemini model/version wording in README and Settings so code and documentation agree.

## Phase 3 — Diagnostics and Supportability

### In-App Diagnostics

- [ ] Add `Copy Diagnostics` button.
- [ ] Include:
  - DualDex version
  - Git commit/build ID
  - Android version
  - Device model
  - Display information
  - CPU ABI
  - mGBA/core version
  - ROM title
  - ROM SHA-256 or shortened hash
  - Detected profile
  - Compatibility state
  - Engine/layout profile
  - Current companion features enabled/disabled
- [ ] Never include ROM contents, save contents, Gemini API key, or user questions/chat history.
- [ ] Add optional debug-log export later.

### GitHub Issue Templates

- [ ] Create `Bug Report` template.
- [ ] Create `ROM Compatibility Request` template.
- [ ] Create `Feature Request` template.
- [ ] Ask users to paste DualDex diagnostics.
- [ ] Require hack version/patch version for compatibility reports.
- [ ] Make clear that ROM files should never be uploaded to GitHub issues.

### Crash Handling

- [ ] Improve user-facing errors instead of showing raw exception messages where possible.
- [ ] Handle missing mGBA core, invalid ROM, invalid save, corrupt save state, unsupported ROM, missing storage permission, and invalid persisted ROM URI.
- [ ] Preserve saves when something crashes.
- [ ] Consider opt-in crash reporting later.

## Phase 4 — Privacy and Security

### Gemini API Key

- [ ] Stop displaying saved API keys in plain text.
- [ ] Use a password/masked input field.
- [ ] Move API key storage away from ordinary plaintext SharedPreferences.
- [ ] Prefer Android Keystore-backed encryption.
- [ ] Add `Clear API Key`.
- [ ] Clearly explain that the user's Gemini key is used directly by DualDex.

### AI Assistant Disclosure

- [ ] Explain what information is sent when Assistant is used:
  - User's question
  - Active ROM/profile
  - Relevant party information
- [ ] Explain that normal emulation and local companion features do not require Gemini.
- [ ] Keep Assistant optional.

### Android Backups

- [ ] Review `android:allowBackup`.
- [ ] Add explicit backup rules.
- [ ] Exclude cached/copied ROM files, API keys/secrets, and temporary files.
- [ ] Decide whether battery saves should be included in Android backup.
- [ ] Document where saves live.

### Privacy Policy

- [ ] Write a basic privacy policy before adding advertising or Play Store distribution.
- [ ] Explain networking behavior.
- [ ] Explain Gemini usage.
- [ ] Explain any future crash analytics.
- [ ] Explain advertising SDK behavior if ads are eventually introduced.

## Phase 5 — Usability / Beta Polish

### First-Run Experience

- [ ] Add lightweight onboarding.
- [ ] Explain that DualDex does not include ROMs.
- [ ] Let user choose ROM folder.
- [ ] Explain dual-screen behavior on AYN Thor.
- [ ] Explain unsupported-ROM behavior.
- [ ] Point users to Settings and save import.

### Game Library

- [ ] Persist URI permissions for individually selected ROMs.
- [ ] Gracefully handle a ROM that was deleted or moved.
- [ ] Detect duplicate ROMs.
- [ ] Show detected profile/compatibility state beside each ROM.
- [ ] Show hack/version when known.
- [ ] Optionally remember last-played date.
- [ ] Consider optional recursive scanning for nested ROM folders.
- [ ] Consider cover art much later; not needed for beta.

### Save UI

- [ ] Clearly distinguish in-game/battery saves, save states, and quicksaves.
- [ ] Explain that save states may not remain compatible between emulator/core versions.
- [ ] Add confirmation before overwriting save-state slots.
- [ ] Separate automatic resume state from the user's manual quicksave so `onPause()` cannot overwrite an intentional quicksave.
- [ ] Consider save-state screenshots later.

### About Page

- [ ] Add DualDex version, open-source link, license information, mGBA attribution/license, support/Patreon link, bug-report link, compatibility documentation, and privacy policy.
- [ ] Add `Copy Diagnostics`.

### APK Cleanup

- [ ] Remove duplicated mGBA shared library.
- [ ] Check other ABIs for duplicate native binaries.
- [ ] Audit packaged assets for other duplicates.
- [ ] Check final APK size.
- [ ] Enable release optimization/minification after confirming it does not break JNI.
- [ ] Test actual release builds, not only debug builds.

### Documentation / WebView Polish

- [ ] Fix the offline guide so hack-specific Ghost Grey information is not shown for unrelated profiles.
- [ ] Review embedded docs WebView security and navigation behavior.
- [ ] Prefer HTTPS documentation sources.
- [ ] Consider restricting external navigation or opening unrelated links in the system browser.

## Phase 6 — ROM Hack Compatibility

Do not block beta indefinitely for this. Aim for roughly **5–8 highly tested games/hacks** rather than dozens of partially working ones.

### Initial Beta Target

- [ ] Vanilla FireRed
- [ ] Vanilla Emerald
- [ ] Radical Red
- [ ] Ghost Grey
- [ ] Heart & Soul
- [ ] Pokémon Unbound
- [ ] Select another high-demand FireRed/CFRU hack
- [ ] Select another popular Emerald/pokeemerald-expansion hack

### For Every Supported Hack

- [ ] Verify exact version.
- [ ] Record SHA-256.
- [ ] Verify party reading.
- [ ] Verify enemy reading.
- [ ] Verify active battlers.
- [ ] Verify IVs/EVs.
- [ ] Verify moves.
- [ ] Verify typings.
- [ ] Verify custom species.
- [ ] Verify damage calculator assumptions.
- [ ] Verify type chart generation.
- [ ] Verify map/location functionality where supported.
- [ ] Verify battery saving.
- [ ] Verify save import/export.
- [ ] Verify cheats if advertised.
- [ ] Document anything intentionally unsupported.

## Phase 7 — AYN Thor Beta Release

### Recommended Beta Model

- [ ] Release the AYN Thor beta for free.
- [ ] Do **not** add advertising to the Thor beta.
- [ ] Include an optional Patreon/support link.
- [ ] Keep core features available to everyone.
- [ ] Use Patreon for supporting development, early preview builds, development updates, ROM-hack priority voting, and community/support perks.
- [ ] Treat Patreon as support/convenience rather than DRM around an MIT project.

### Beta Messaging

- [ ] Clearly label the software as beta.
- [ ] Warn users to keep external backups of important saves.
- [ ] Publish known issues.
- [ ] Give users an easy compatibility-request process.

## Phase 8 — Single-Screen Android Support

Treat this as a separate product milestone after the Thor beta is stable.

### Goal

Make DualDex useful on standard Android phones, Android gaming handhelds, tablets, and other devices with only one display.

### Layout Modes

- [ ] Create a proper single-screen layout system instead of treating it only as a fallback.
- [ ] Add configurable layouts such as:
  - **Gameplay Mode** — emulator nearly full-screen; companion opens on demand.
  - **Split Mode** — emulator on top, companion below.
  - **Side-by-Side Mode** — useful for tablets and wide handhelds.
  - **Drawer / Slide-Up Companion** — gameplay full-screen with temporary companion panel.
  - **Quick Overlay** — small battle/type information overlay without opening the full companion.
- [ ] Remember preferred layout per device.
- [ ] Allow companion panel size adjustment.
- [ ] Automatically choose reasonable defaults based on screen size/aspect ratio.
- [ ] Ensure controller navigation works without relying on touchscreen interaction.

### Single-Screen UX

- [ ] Add controller shortcut to open/close companion.
- [ ] Add shortcut to switch companion tabs.
- [ ] Avoid covering critical gameplay UI.
- [ ] Test portrait phones where applicable.
- [ ] Test landscape phones.
- [ ] Test 16:9 handhelds.
- [ ] Test wider 20:9/21:9 displays.
- [ ] Test tablets.

### Device Abstraction

- [ ] Stop assuming AYN Thor dimensions in presentation/layout code.
- [ ] Create device/display capability detection.
- [ ] Treat dual-screen mode as one presentation strategy rather than the architecture itself.
- [ ] Keep emulator and companion UI logically independent of screen arrangement.

## Phase 9 — Possible Play Store Edition

Only tackle this after the core beta is stable.

### Play Store Version

- [ ] Research current Google Play emulator and ROM-related policy before submission.
- [ ] Confirm bundled mGBA/core licensing and distribution requirements.
- [ ] Ensure no copyrighted ROM data is distributed.
- [ ] Add proper Play Store privacy/data-safety declarations.
- [ ] Build a Play Store-compatible Android App Bundle.
- [ ] Meet current target SDK requirements.
- [ ] Add store listing screenshots for standard phones/handhelds.
- [ ] Clearly explain that users supply their own legally obtained ROMs.

### Possible Monetization

Potential split:

**AYN Thor / GitHub Edition**

- Free
- No ads
- Full dual-screen functionality
- Patreon/support link

**Google Play / General Android Edition**

- Free
- Potentially light advertising
- Designed for standard single-screen devices
- Optional one-time ad removal or supporter unlock, depending on Play policy/business model

- [ ] If ads are used, keep them away from gameplay.
- [ ] No interstitials triggered during gameplay.
- [ ] No ads on save/load actions.
- [ ] Prefer Home/Library/About placements.
- [ ] Measure whether advertising revenue is actually worth the added complexity.
- [ ] Consider a cheap one-time `Remove Ads` purchase instead of requiring a recurring Patreon subscription for ordinary Play Store users.

## Phase 10 — Licensing / Distribution Cleanup

- [ ] Add a third-party notices file for bundled libraries and cores.
- [ ] Document mGBA version/build provenance and MPL-2.0 obligations.
- [ ] Review QuickJS-NG, damage calculator, and other bundled dependency licenses.
- [ ] Ensure ROMs, BIOS files, and copyrighted game assets are never distributed with releases.
- [ ] Add a clear non-affiliation disclaimer covering Nintendo, The Pokémon Company, Game Freak, AYN, and ROM-hack authors.

## Phase 11 — Later Improvements

These should **not delay the initial beta**.

- [ ] User-configurable controller bindings.
- [ ] Automatic update checker for GitHub builds.
- [ ] In-app compatibility database updates.
- [ ] Downloadable ROM profiles without releasing a new APK.
- [ ] Community-maintained profile repository.
- [ ] Save-state screenshots.
- [ ] Multiple save backup generations.
- [ ] Cloud save support.
- [ ] Better offline hack documentation.
- [ ] Expanded map support.
- [ ] Per-game companion customization.
- [ ] More calculator mechanics for modern ROM hacks.
- [ ] More emulator cores/systems only if the Pokémon companion concept eventually justifies it.

## Recommended Bug-Fix Order

The static audit produced GitHub issues **#1–#19**. Work through the following order before adding substantial new ROM-hack support. The ordering intentionally handles user-data safety and core ownership first, because several later fixes become safer and cleaner once those foundations exist.

### Tier 1 — Protect Saves and Stabilize the Emulator Core

1. [ ] **#2 — Give every ROM a unique save/state identity.**
   - This prevents different ROMs or hack versions from sharing SRAM, save states, or cheats.
2. [ ] **#3 — Make battery-save writes/imports atomic and recoverable.**
   - Add backups before relying on public testers' real saves.
3. [ ] **#5 — Serialize libretro mutations on the emulator thread.**
   - Establish the command queue/core-owner architecture before routing more operations through it.
4. [ ] **#4 — Make ROM switching a safe transaction.**
   - Build the switch process on top of #2, #3, and #5 so it can flush, unload, reset, load, and restore safely.

### Tier 2 — Make Live Companion Data Trustworthy

5. [ ] **#1 — Fix battle lifecycle and active-enemy tracking.**
   - Prefer one coherent battle snapshot and explicit battle state rather than enemy-party heuristics.
6. [ ] **#7 — Add Verified / Recognized-Unverified / Unsupported ROM states.**
   - Never parse an unknown ROM using arbitrary FireRed/default offsets.
7. [ ] **#8 — Make engine/layout profiles authoritative for native memory parsing.**
   - Do this before adding many more ROM hacks.
8. [ ] **#9 — Make the damage calculator profile/version aware.**
   - Stop hard-coding Gen 3 rules for hacks that use modern mechanics.
9. [ ] **#16 — Clear stale party/location/battle state when the active game becomes invalid or changes.**
10. [ ] **#11 — Move map/location routing and SaveBlock behavior into engine/layout profiles.**

### Tier 3 — Fix Public-Beta UX and Feature Reliability

11. [ ] **#10 — Fix cached-tab lifecycle bugs in Assistant and Docs.**
12. [ ] **#14 — Implement the controller shortcuts currently advertised by DualDex.**
13. [ ] **#6 — Persist direct-ROM URI permissions and recover cleanly from stale URIs.**
14. [ ] **#12 — Secure Gemini credentials and define explicit Android backup exclusions.**
15. [ ] **#13 — Remove cross-ROM incorrect fallback guidance from Assistant/Docs.**

### Tier 4 — Cleanup and Regression Prevention

16. [ ] **#15 — Eliminate stale/duplicated `gameId` assumptions across native, Kotlin, tests, and docs.**
17. [ ] **#17 — Key cheat presets to verified ROM versions instead of display-name heuristics.**
18. [ ] **#18 — Fix the HomeScreenView coroutine/lifecycle leak.**
19. [ ] **#19 — Remove duplicate packaged mGBA core binaries.**

### Audit Exit Gate

Before moving from bug fixing back to ROM-hack expansion:

- [ ] Issues #2, #3, #4, #5, #7, and #8 are complete.
- [ ] Issue #1 has regression coverage for switches, faints, and battle end.
- [ ] Supported ROMs cannot display live data under an unverified memory layout.
- [ ] Save corruption/cross-ROM contamination tests pass.
- [ ] Repeated save/load/switch stress testing produces no core race failures.
- [ ] At least FireRed, Emerald, and one modern-mechanics hack have calculator golden tests.

## Suggested Development Order

1. **Complete audit Tier 1: save safety + emulator core ownership (#2, #3, #5, #4).**
2. **Complete audit Tier 2: trustworthy battle/ROM/profile/calculator state (#1, #7, #8, #9, #16, #11).**
3. **Complete audit Tier 3 public-beta reliability fixes (#10, #14, #6, #12, #13).**
4. **Complete audit Tier 4 cleanup/regression work (#15, #17, #18, #19).**
5. **Finish save-import wording + cross-emulator validation.**
6. **Add diagnostics and GitHub issue templates.**
7. **Clean up documentation and supported-version tables.**
8. **Set up permanent signing, CI, and GitHub release process.**
9. **Finish privacy policy / Assistant disclosure work.**
10. **Finish Unbound + a few other high-value hacks only after profile architecture is stable.**
11. **Run the complete per-ROM beta compatibility matrix.**
12. **Release free AYN Thor beta.**
13. **Use actual beta feedback to determine priorities.**
14. **Build first-class single-screen layouts.**
15. **Evaluate a Google Play/general-Android release.**
16. **Only then decide whether ads are actually worth adding.**

## Beta Definition of Done

DualDex is ready for `0.9.0-beta.1` when:

- A bad or unsupported ROM cannot produce confidently incorrect companion information.
- Switching games cannot reasonably destroy or cross-contaminate saves.
- Battery saves are backed up and recoverable.
- Every advertised controller shortcut actually works.
- Supported ROM versions are documented and tested.
- Public builds are permanently signed and reproducible.
- A tester can send useful diagnostics without sending their ROM.
- Gemini/API-key handling is appropriate for someone else's device.
- Installation and update instructions are clear.
- The AYN Thor experience is polished enough that the remaining issues are legitimately beta bugs rather than missing release infrastructure.
