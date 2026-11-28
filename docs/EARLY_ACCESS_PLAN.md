# DualDex Early Access Plan (`0.9.0-beta.1`)

**Status: planning document, reconciled against `main` @ `3d0a0e4` (2026-11-27).**

`0.9.0-beta.1` has **not** been cut or shipped. DualDex is still pre-beta
(`versionName = 0.9.0-dev`, `versionCode = 1`). This document is the single
source of truth for what the first Early Access build promises, what it
deliberately defers, and how the parallel workstreams fit together.

- Release gates and per-item checkboxes: [RELEASE_CHECKLIST.md](../RELEASE_CHECKLIST.md)
- User-facing description: [README.md](../README.md)
- H&S capability boundary: [HNS_ISSUE_40_CLOSURE_AUDIT.md](HNS_ISSUE_40_CLOSURE_AUDIT.md)
- Unbound calculator posture: [UNBOUND_CALCULATOR_PLAN.md](UNBOUND_CALCULATOR_PLAN.md), [UNBOUND_DAMAGE_ORACLE.md](UNBOUND_DAMAGE_ORACLE.md)

---

## 1. What is already done on `main`

Verified by code, tests, committed evidence, or merged PRs on `main`.

| Area | State | Key references |
|---|---|---|
| Exact Heart & Soul 2.0.5 core integration | **Done.** Exact `Release-v2.0.5` / `1f42b74`, SHA-256 `edf76ecf…7679b`; party, data, battle lifecycle (`battleStateReadVerified = true`), location/map, bounded calculator; unsupported surfaces fail closed. | PR #167 (calculator freeze), [HNS_ISSUE_40_CLOSURE_AUDIT.md](HNS_ISSUE_40_CLOSURE_AUDIT.md) |
| H&S calculator coverage | **Done (frozen, `ESTIMATED` ceiling).** Verified arithmetic/oracle evidence across 17 slices (422 admitted move IDs, 2,523-scenario oracle corpus); production live-session ceiling remains `CalcSupport.ESTIMATED`; only correctness/beta-blocking fixes until after `0.9.0-beta.1`. | [CALCULATOR_BETA_FREEZE.md](CALCULATOR_BETA_FREEZE.md), [HNS_CALCULATOR_FINAL_PREBETA_ACCEPTANCE.md](HNS_CALCULATOR_FINAL_PREBETA_ACCEPTANCE.md) |
| H&S battle UI / move selection | **Done.** DS-style 2×2 move grid; tap-to-select moves with macro evidence; MoveRefused reporting. | PRs #176, #179, #180, [HNS_BATTLE_MOVE_SELECT_EVIDENCE.md](HNS_BATTLE_MOVE_SELECT_EVIDENCE.md) |
| Navigator shell + in-game-style Party slots | **Done.** | PR #171, [NAVIGATOR_DESIGN.md](NAVIGATOR_DESIGN.md), [navigator/](navigator/) |
| `#153` quick wins | **Done.** Smart fast-forward, hotkey chords, frontend launch (`FrontendLaunchActivity`), save sharing (`SaveShareStore`), GitHub Releases updater, 10 save-state slots with thumbnails, status bar, touch overlay, foe-team panel, L2/R2 quick save/load. | PR #172 (`feat/issue-153-quick-wins`), PR #170 (L2/R2) |
| Save safety foundations | **Done.** SHA-256 ROM identity, atomic battery saves, SAF mirror, session identity binding + stale-observation rejection, save-only backup/D2D allowlists. | PRs #204–#205, #185, [ROM_SESSION_OBSERVATION_SAFETY.md](ROM_SESSION_OBSERVATION_SAFETY.md), [ANDROID_BACKUP_POLICY.md](ANDROID_BACKUP_POLICY.md) |
| Assistant removal | **Done.** Removed from build/settings; source retained under `disabled/assistant/`. | PR #182 |
| Built-in cheats fail-closed | **Done.** Empty production catalog; custom cheats user-supplied/unverified. | PR #121, [CHEAT_COMPATIBILITY.md](CHEAT_COMPATIBILITY.md) |
| ROM compatibility states | **Done.** Verified / Recognized-Unverified / Unsupported; no arbitrary FireRed fallback for memory reads. | `app/src/main/java/com/dualdex/romhack/RomCompatibility.kt` |
| Pokémon Unbound 2.1.1.1 native reads & calc | **Partial (`ESTIMATED` calc enabled; battle reads disabled).** Party/location/map-name reading, `UnboundDataPack`, exact candidate SHA-256 bound, and `CalcRuleset.UNBOUND_CFRU` Singles ordinary-hit `ESTIMATED` mode (`UNBOUND_CFRU_SOURCE_APPROXIMATE`) enabled. Shipped `unbound.json` keeps `battleStateReadVerified = false` (`mayReadBattleState = false`), so active-battle live-state requests fail closed. | PRs #207–#213, [UNBOUND_CALCULATOR_PLAN.md](UNBOUND_CALCULATOR_PLAN.md) |
| Unbound damage oracle | **Host-source corpus merged.** Host CFRU damage oracle, 579-case × 16-roll golden corpus, and JS CFRU core (`tools/calc-bundler/unbound-cfru.js`) at host parity; production `ESTIMATED` Singles mode uses `UNBOUND_CFRU_SOURCE_APPROXIMATE`, while exact-ROM table/Thumb oracle parity remains unverified. | [UNBOUND_DAMAGE_ORACLE.md](UNBOUND_DAMAGE_ORACLE.md) |
| Targeted Thor validation | **HOLD (not an RC gate sign-off).** Initial Thor gate ([THOR_HARDWARE_ACCEPTANCE.md](THOR_HARDWARE_ACCEPTANCE.md)) remains **HOLD** with outstanding §5 matrix rows/subchecks and the continuous `>= 60`-minute soak (`F1`) open. Subsequent Oct 15 targeted ~37-minute mixed session passed selected normal/4× pacing and save/ROM-switch checks ([ROM_SESSION_HARDWARE_ACCEPTANCE.md](ROM_SESSION_HARDWARE_ACCEPTANCE.md)); `F1` soak and cloud/D2D restore remain `NOT_RUN`. | [THOR_HARDWARE_ACCEPTANCE.md](THOR_HARDWARE_ACCEPTANCE.md), [ROM_SESSION_HARDWARE_ACCEPTANCE.md](ROM_SESSION_HARDWARE_ACCEPTANCE.md) |

Nothing above implies that unrun AYN Thor hardware checks, signing-key
generation, tester rings, or a published APK have happened. Those remain open
gates in the release checklist.

---

## 2. What `0.9.0-beta.1` must contain

The beta promise stays narrow: reliable GBA emulation on the AYN Thor, protected
saves, and trustworthy live companion data for an explicitly verified ROM list.

### Included ROMs (owner-approved)

| ROM | Requirement for `0.9.0-beta.1` | Current state |
|---|---|---|
| Exact Vanilla FireRed (rev 0/1) | Regression baseline; party/location + Gen 3 calculator (battle-state reads disabled on shipped profile) | Verified profile, merged, tested; native layout evidence exists (`CONFIG_FIRERED`), while shipped `vanilla_firered.json` sets `battleStateReadVerified = false` (`mayReadBattleState = false`) |
| Exact Vanilla Emerald | Regression baseline; party/location + Gen 3 calculator (battle-state reads disabled on shipped profile) | Verified profile, merged, tested; native layout evidence exists (`CONFIG_EMERALD`), while shipped `vanilla_emerald.json` sets `battleStateReadVerified = false` (`mayReadBattleState = false`) |
| Exact Heart & Soul 2.0.5 | Full supported companion contract (live + `ESTIMATED` bounded calculator) | Complete; frozen (`ESTIMATED` calc ceiling with verified arithmetic/oracle evidence); Thor hardware gate remains **HOLD** per [THOR_HARDWARE_ACCEPTANCE.md](THOR_HARDWARE_ACCEPTANCE.md) |
| Exact Pokémon Unbound 2.1.1.1 | Live party/location/map-name reads + `ESTIMATED` Singles ordinary-hit calculator (or explicit exact-ROM deferral) | Party/location/map-name reader merged; `ESTIMATED` Singles calculator enabled behind exact-hash gate (`UNBOUND_CFRU_SOURCE_APPROXIMATE`); `battleStateReadVerified = false` disables live battle-state reads on shipped `unbound.json` |

Every supported ROM must be documented with its exact SHA-256, and unsupported
or unverified builds must degrade to emulation-only rather than confident
guesses.

### Required before the RC cut

1. Confirm Unbound's `0.9.0-beta.1` scope (`ESTIMATED` Singles ordinary-hit
   mode with `battleStateReadVerified = false` / live battle-state reads
   disabled and documented as `UNBOUND_CFRU_SOURCE_APPROXIMATE`), or complete
   exact-ROM verification before enabling battle-state reads.
2. Complete the general pre-beta blockers: save recovery paths, ROM switching
   edge cases, cheat gating, controller shortcuts, diagnostics, signing, and
   ring testing (see the release checklist).
3. Close the [THOR_HARDWARE_ACCEPTANCE.md](THOR_HARDWARE_ACCEPTANCE.md) **HOLD**
   disposition — complete all outstanding §5 matrix rows/subchecks and the
   continuous `>= 60`-minute real-play soak (`F1`) — plus the disposable-device
   cloud/D2D restore checks that are currently **NOT_RUN**.
4. Generate the permanent production signing key and cut
   `0.9.0-beta.1-rc1` → `0.9.0-beta.1`.

### Deferred out of `0.9.0-beta.1`

| Deferred item | Reason | Tracked |
|---|---|---|
| R.O.W.E. (`#59`) | Bespoke unencrypted 76-byte party layout; host decoder and extensive runtime/identity research exist, but no production profile/calculator. Moving it out keeps the beta date honest. | Post-beta, issue #59 |
| Quota-Aware Cloud Backup (`#186`, `#194`) | PR #185 D2D/cloud allowlists, local atomic saves, and save sharing already cover sideloaded Early Access needs; the custom `BackupAgent` design is a design spike only. | Post-beta, [ANDROID_CLOUD_BACKUP_DESIGN.md](ANDROID_CLOUD_BACKUP_DESIGN.md) |

**Ghost Grey note:** `ghost_grey.json` stays present but unverified
(`isVerified: false`, no hashes). Do **not** claim Ghost Grey support until the
author's opt-out position is confirmed and exact evidence exists.

---

## 3. Tier A / Tier B compatibility model

The tiered model is what lets the beta stay small while the compatibility
architecture scales to many hacks.

| Tier | Definition | Beta treatment | Current members |
|---|---|---|---|
| **Tier A — Verified Live + Exact Calc** | Exact hash verified, memory layout verified, and a source/oracle-verified `VERIFIED` damage calculator (or a documented, fail-closed calculator refusal) | Advertised as supported | FireRed and Emerald for manual/party Gen 3 calc (noting shipped profiles set `battleStateReadVerified = false` / `mayReadBattleState = false`); H&S/Unbound only if promoted |
| **Tier B — Verified Live + Estimated Calc** | Exact hash verified + verified memory layout, with `CalcSupport.ESTIMATED` (or a documented refusal) for damage numbers | Can be advertised after privacy/hardening gates; results labelled approximate | H&S 2.0.5 (`ESTIMATED` live-session ceiling per [HNS_CALCULATOR_FINAL_PREBETA_ACCEPTANCE.md](HNS_CALCULATOR_FINAL_PREBETA_ACCEPTANCE.md)); Unbound 2.1.1.1 (`ESTIMATED` Singles ordinary-hit mode enabled, `battleStateReadVerified = false`); future CFRU/expansion hacks |

Rules that keep the tiers honest:

- Tier B never publishes a number as `VERIFIED`; the ceiling caps at
  `CalcSupport.ESTIMATED` and the UI labels it approximate.
- A tier assignment requires an exact SHA-256 match; name/header recognition
  alone stays `RECOGNIZED_UNVERIFIED` (emulation only).
- Any hack promoted to Tier A must ship its oracle corpus and golden tests.

Reference implementation: `app/src/main/java/com/dualdex/calculator/CalcCapabilityPolicy.kt`.

---

## 4. Parallel differentiator tracks

These run concurrently and are **not** artificial blockers for
`0.9.0-beta.1`. Each lands in the beta only if it is green and Thor-verified
before the RC cut; otherwise it rolls into `0.9.0-beta.2` with no delay to the
beta.

| Track | Scope | Landing rule |
|---|---|---|
| Declarative `gamedata/` + `#8` | Profile-driven native reader, family extractors, `rom_identify.py` | Land green; no speculative generic-hack rewrite |
| Nuzlocke Tracker | Companion tracker for encounters, catches, deaths | Land green + Thor-verified |
| Calc-Driven Best Switch Panel | Uses the verified calculator to suggest switches | Land green + Thor-verified |
| In-App BPS/UPS Auto-Patcher | Patch a base ROM in-app to a supported hack build | Land green + Thor-verified |
| Diagnostics + Multi-Handheld Display Fix | `Copy Diagnostics`, non-Thor layouts | Land green + Thor-verified |

Anything that fails these conditions simply misses the `0.9.0-beta.1` cut. It
does not become a reason to postpone the release.

---

## 5. Post-Beta Wave 2

After `0.9.0-beta.1` ships:

1. **R.O.W.E.** as the first new post-beta integration, using the host decoder
   work under issue #59.
2. **Quota-Aware Cloud Backup** (`#186`/`#194`) built on the custom
   file-based `BackupAgent` design.
3. **Tier B onboarding** for additional CFRU / pokeemerald-expansion hacks.
4. **Calculator freeze lifted** by the owner; new mechanic coverage resumes per
   the exception rules in [CALCULATOR_BETA_FREEZE.md](CALCULATOR_BETA_FREEZE.md).
5. **Single-screen Android layouts** and the remaining `#154`/`#155` backlog.

---

## 6. Execution matrix

| Workstream | `0.9.0-beta.1` | Parallel tracks | Post-beta |
|---|---|---|---|
| Heart & Soul 2.0.5 | Complete, frozen (`ESTIMATED` calc ceiling); Thor gate **HOLD** | — | Freeze lifted after release |
| Unbound 2.1.1.1 | `ESTIMATED` Singles calc enabled; `battleStateReadVerified = false` | — | Exact-ROM verification / Tier A promotion |
| FireRed / Emerald | Verify at RC time (`battleStateReadVerified = false` on shipped profiles) | — | Keep as regression baselines |
| R.O.W.E. | **Deferred** | — | First Wave 2 integration |
| Cloud Backup | **Deferred** | — | Wave 2 |
| `gamedata/` + `#8` | Optional | Active | Continues |
| Nuzlocke / Best Switch / Patcher / Diagnostics | Optional | Active | Continues |
| Tier B hacks | Not required | — | Wave 2 |
| Thor soak (`F1` / §5 **HOLD**), signing, rings | **Required** | — | — |
