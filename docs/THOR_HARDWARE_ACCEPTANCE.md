# AYN Thor hardware acceptance: initial gate

**Tracking issue:** [#168](https://github.com/Sonoran-Solutions/dualdex/issues/168) (links #40, #14, #75, #38)
**Date:** 2026-10-09
**Disposition: HOLD (interim; supersedes the earlier BLOCKED)**

A real AYN Thor was reached over wireless ADB on 2026-10-09 after the first revision of this report. Only the ADB-observable subset (section 9) has run; mandatory coverage (controller, dual-screen lifecycle, save/lifecycle, 60-minute soak) is still outstanding, so **no hardware acceptance (GO) is claimed**. Sections 1-8 below were written while the device was unavailable; section 9 is authoritative where they differ.
This document records the discovery evidence, the prerequisite state, the static findings that are
already known without hardware, and the owner-assisted procedure for the real pass. It is not the
`0.9.0-beta.1` release-candidate certification and does not claim the public beta is ready.

---

## 1. Prerequisites

| Item | State |
|---|---|
| `origin/main` | `225dfcdce0d98651e1a9c85bb3c311cf71e1c01b` |
| PR #167 (Slice 17 Rapid Spin + calculator freeze) | **OPEN, not merged** (head `feat/hns-final-calculator-slice-and-beta-freeze`, `9eb79b4`). The freeze is therefore not on `main`; a post-freeze acceptance cannot be claimed against `main` |
| App version | `versionName 0.9.0-dev`, `versionCode 1` (`app/build.gradle*`), undistributed dev identity per #38 |
| Previous hardware evidence | None. `HNS_ISSUE_40_CLOSURE_AUDIT.md` and `RELEASE_CHECKLIST.md` state that no Thor run was performed |
| Tested APK SHA-256 | **None.** No APK was built or installed |
| Dedicated Thor issue | None existed; [#168](https://github.com/Sonoran-Solutions/dualdex/issues/168) created |

The test APK must be built from **post-#167 `main`** (or the exact #167 head, labelled as pre-merge).

## 2. Device discovery

`adb devices -l` listed one wireless device:

| Field | Value |
|---|---|
| Manufacturer / model | samsung / `SM-F971U1` (device `h8q`) |
| Android | 17, fingerprint `samsung/h8quew/h8q:17/CP2A.260605.016/F971U1UES3AZIL_OYM3AZIL:user/release-keys` |
| ABI | arm64-v8a |
| Displays | built-in 2448x1848 and built-in 1248x1972 |
| DualDex installed | No |

This is **not an AYN Thor** and is not equivalent to one (different vendor firmware, display topology
and controls, no gamepad). Nothing was installed on it and no data on it was touched. Only read-only
`getprop`/`dumpsys display`/`pm list packages` were run. Results from it would not count toward this gate.

## 3. Static findings (source only, not hardware evidence)

| Finding | Evidence | Relates to |
|---|---|---|
| Settings advertises "L2 / R2 -> Quick Save (L2) / Quick Load (R2)" | `SettingsScreenView.kt:377` | #14 |
| `KEYCODE_BUTTON_L2/R2` are mapped to joypad bits `BTN_L2`/`BTN_R2` | `InputManager.kt:86-87` | #14 |
| `SaveStateManager.quickSave/quickLoad` exist but have no caller in the emulator input path (only `SaveStateManager` itself references them) | `grep quickSave\|quickLoad app/src/main` | #14 |

Provisional classification: **P1 #14** (advertised shortcut apparently unwired). Scenario C-L2/C-R2
below must stay FAIL-until-proven on the Thor; do not mark PASS on this reading alone. "Button X / Y:
Turbo / Menu Shortcut" and the fast-forward shortcut are likewise unverified.

## 4. Protecting existing data (applies before any install on the Thor)

1. `adb shell pm list packages | grep dualdex`; if installed, record `versionName`/`versionCode`
   and signer (`adb shell dumpsys package <pkg> | grep -E "versionName|signatures|Signing"`).
2. Install only with `adb install -r` of an APK **signed by the same key**. If the signer differs,
   **stop**: do not uninstall; owner decides.
3. Back up app saves/states and ROM-adjacent `.sav` files off-device first
   (`adb pull`; shared storage paths, then `adb exec-out run-as <pkg>` only for debuggable builds).
   Record `sha256sum` of every pulled file and compare sizes to `adb shell stat`.
4. If Android scoped storage prevents a complete backup, stop and ask the owner.
5. Run destructive or failure-injection tests only on copies. Deeper work stays in #75.

## 5. Test matrix (all `NOT_RUN`)

Result vocabulary: PASS / FAIL / BLOCKED / NOT_RUN. Primary ROM:
`edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b` (H&S 2.0.5); also verified
vanilla FireRed/Emerald. Trust is by SHA-256, never filename.

| ID | Scenario | Result |
|---|---|---|
| A1 | Install exact APK without data loss; record SHA-256 + source commit | NOT_RUN |
| A2 | Cold launch, load verified ROM: video, input latency, audio | NOT_RUN |
| A3 | Open/close companion screens during play | NOT_RUN |
| A4 | Close/relaunch; Continue after process death (`am kill`) and reboot | NOT_RUN |
| B1 | Top display renders game; bottom shows companion; scaling/aspect/sharpness | NOT_RUN |
| B2 | Bottom-screen touch never triggers game input | NOT_RUN |
| B3 | Lid/fold, sleep/wake, rotation: no stale/duplicate presentation windows | NOT_RUN |
| B4 | Background/resume both screens; no black/frozen/misfocused UI | NOT_RUN |
| C1 | D-pad, analog, A/B, L/R, Start/Select | NOT_RUN |
| C2 | L2 quicksave (#14) | NOT_RUN (expected FAIL, see section 3) |
| C3 | R2 quickload (#14) | NOT_RUN (expected FAIL) |
| C4 | Fast-forward toggle changes real speed, with state feedback | NOT_RUN |
| C5 | Consumed shortcuts not forwarded to core; no repeat on hold | NOT_RUN |
| D1 | Party: count, identity, HP/level/types/items/status, faint, no stale after ROM switch | NOT_RUN |
| D2 | Battle: wild/trainer, switch, HP/stat stages, clears after battle, no false Magic Room on fresh battle, Doubles | NOT_RUN |
| D3 | Calculator: supported calcs use live participants/field; unsupported/ambiguous refuse | NOT_RUN |
| D4 | Map: labels/region update, no stale after ROM switch; mark observed vs source-backed | NOT_RUN |
| D5 | UI across Library/Party/Battle/Map/More: readability, clipping, touch targets | NOT_RUN |
| E1 | Battery save create/reload | NOT_RUN |
| E2 | Save states repeated create/load; auto-resume | NOT_RUN |
| E3 | Background/resume x N, suspend/wake, force-stop + Continue, reboot | NOT_RUN |
| E4 | ROM A -> B -> A keeps own save identity and companion state | NOT_RUN |
| E5 | Fast-forward during save/load; failed ROM access recoverable | NOT_RUN |
| F1 | >= 60 min real-play soak (see 6) | NOT_RUN |
| G1 | No built-in cheat offered as trusted for H&S (#17); no unsafe cheats on real saves | NOT_RUN |
| G2 | Offline Assistant does not invent H&S facts (#13); note privacy issues for #12 | NOT_RUN |

`battleUiVerified` and `interactiveControlsVerified` remain `false`; nothing here satisfies their contracts.

## 6. Soak measurement recipe (direct measurements only)

Sample every 5 minutes for >= 60 min, logging the wall-clock duration actually run:

```bash
adb shell dumpsys meminfo com.dualdex            # PSS growth
adb shell dumpsys batterystats --reset            # before; dumpsys battery for level/temp after
adb shell dumpsys thermalservice                  # throttling status
adb shell dumpsys gfxinfo com.dualdex framestats  # frame pacing
adb logcat -b crash,main -v time > soak.log       # crashes, ANRs, native failures
```

Report audio glitches, slowdown and companion sync accuracy as **visual observations**, separately
from the numbers. Do not state thresholds that are not documented.

## 7. Open defects

| Severity | Item | Issue |
|---|---|---|
| P1 (provisional, source-only) | Advertised L2/R2 quicksave/quickload not wired in input path | #14 |
| (none) | No hardware defects: no hardware run | |

## 8. Owner actions required

1. Merge or decide on PR #167 so the freeze is on `main`; build the APK from that commit and record its SHA-256.
2. Provide the AYN Thor over ADB and confirm the existing install's signer matches the test APK.
3. Supply the exact H&S 2.0.5 ROM (hash above) and vanilla baselines; approve the backup location.
4. Run section 4, then section 5, then section 6 (or have the agent run them with the Thor attached).
5. Decide #14: implement shortcuts, or remove the Settings/README claims.

Per the disposition rules: **BLOCKED** until a real Thor is available; GO/HOLD can only come from that run.

---

## 9. Interim hardware results (real AYN Thor, 2026-10-09)

**Device:** AYN Thor, `qti/kalama/kalama:13/TKQ1.231222.001/eng.Thor.20260206.163241:user/release-keys`,
Android 13 (SDK 33), arm64-v8a. Displays: id 0 "Built-in Screen" 1080x1920 (120 Hz default),
id 4 "Screen-2" 1080x1240 (`FLAG_PRESENTATION`). Input: "Odin Controller", `fts_ts`/`fts_ts_3`
touch, `hall_switch`, `gpio-keys`. Storage: 219G free of 934G.

**Tested build:** source commit `9eb79b4209ba66cd61e38a0902e3692581fab9ed` (#167 head, pre-merge,
`./ci.sh build` succeeded), APK SHA-256
`26bb4d246878a7d3ca65afbd6438ad021b3ec101e04b80711c98bf7a7d0382c9`, `0.9.0-dev` / versionCode 1.
Not post-merge `main`; must be retested after #167 merges.

**Backup (before install):** all 82 files of `/data/data/com.dualdex/files` (incl. saves_v2, save_staging,
saves_fallback, rom_cache) archived off-device; per-file SHA-256 on device and host identical;
the previously installed APK (SHA-256 `788c4adb...1d11`, built 2026-09-24, debug signer
`1503d5f6...87a9`) and the H&S ROM were also saved. After `adb install -r` (same signer, same
versionCode) all 82 file hashes were unchanged and `firstInstallTime` was preserved.

| ID | Result | Evidence |
|---|---|---|
| A1 install, data preserved | PASS | hashes above; signer match |
| A2 cold launch / video / companion render | PASS (partial) | `com.dualdex` focused on both displays; top shows game video (starfield intro), bottom shows Library then Party; no FATAL/ANR in logcat. Audio output and input latency **NOT_RUN** (need a human) |
| A4 process death / reboot / Continue | NOT_RUN | |
| B1 dual-screen layout | PARTIAL, see F1 | companion on display 4, game on display 0 |
| D1 party | PASS (single observation) | 6 slots, Lv/HP/nature/item shown for LEET Porygon-Z 237/237; not cross-checked against the in-game party screen |
| all others in section 5 | NOT_RUN | |

**Observations needing a human, not yet defects:**

- F1: game viewport on the top display measured 1620x648 px inside 1920x1080 (2.5:1) where GBA is 3:2
  (1620x1080 or 1440x960 would be uniform). That is a non-uniform scale unless a stretch/fill
  setting is selected. Confirm the scaling mode in Settings and visually on the device; classify
  P2 if it is unintended.
- F2: ROM file `Pokémon Heart and Soul (v2.0.0).gba` is byte-identical to the verified 2.0.5 hash
  `edf76ecf...679b`; the library title is filename-derived and misleading. The profile name shown
  is "Pokemon Heart & Soul". P3 labeling issue; trust is correctly hash-based.
- F3: `screencap -d 0/-d 4` returned empty images; physical display IDs
  (`4630946441858561667`, `4630946482288158084`) work. Evidence images are in
  `~/thor-backups/shots/` (local, not committed).
- Memory snapshot right after launch: TOTAL PSS 151,430 KB (single sample, not a soak result).

#14 L2/R2 remain unverified: nothing in the input path calls quick save/load (section 3).

**Remaining human checks:** audio, all physical controller scenarios (C), fold/sleep/wake display
lifecycle (B3/B4), battle/map/calculator exercises (D2-D5), save/lifecycle matrix (E), and the
>= 60 min soak (F). The Thor is currently running H&S via auto-resume on the tested build.
