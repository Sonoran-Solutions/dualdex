# AYN Thor hardware acceptance: initial gate

**Tracking issue:** [#168](https://github.com/Sonoran-Solutions/dualdex/issues/168) (links #40, #14, #75, #38)
**Date:** 2026-10-09
**Disposition: BLOCKED**

No AYN Thor was available, so no physical scenario was run and **no hardware acceptance is claimed**.
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
