# AYN Thor hardware acceptance: initial gate

**Tracking issue:** [#168](https://github.com/Sonoran-Solutions/dualdex/issues/168) (links #40, #14, #75, #38)
**Initial Thor run date:** 2026-10-09 (post-Oct-9 `main` updates noted separately below)
**Disposition: HOLD (interim; supersedes the earlier BLOCKED)**

A real AYN Thor was reached over wireless ADB on 2026-10-09 after the first revision of this report, yielding the initial device checks in section 9 and the owner-reported L2/R2 retest in section 9.1. Mandatory release coverage (audio output, remaining physical controller inputs, X/Y turbo shortcut, dual-screen lifecycle, full battle/map/calculator exercises, save/lifecycle matrix, and the >= 60-minute continuous soak) remained outstanding on that date, so **no hardware acceptance (GO) is claimed**. Sections 1–3 record the initial discovery snapshot (`2026-10-09`); section 5 reflects the combined `2026-10-09` evidence (`26bb4d24...` and `f41f5b5c...`) plus clearly labeled post-Oct-9 `main` status notes.
This document records the discovery evidence, the prerequisite state, the static findings that are
already known without hardware, and the owner-assisted procedure for the real pass. It is not the
`0.9.0-beta.1` release-candidate certification and does not claim the public beta is ready.

---

## 1. Prerequisites at initial discovery (2026-10-09)

Historical snapshot recorded before the first AYN Thor ADB session on 2026-10-09:

| Item | State at 2026-10-09 discovery |
|---|---|
| `origin/main` | `225dfcdce0d98651e1a9c85bb3c311cf71e1c01b` |
| PR #167 (Slice 17 Rapid Spin + calculator freeze) | **OPEN, not merged** at discovery (head `feat/hns-final-calculator-slice-and-beta-freeze`, `9eb79b4`). Freeze was not yet on `main` at that moment |
| App version | `versionName 0.9.0-dev`, `versionCode 1` (`app/build.gradle*`), undistributed dev identity per #38 |
| Previous hardware evidence | None. `HNS_ISSUE_40_CLOSURE_AUDIT.md` and `RELEASE_CHECKLIST.md` stated that no Thor run had been performed |
| Tested APK SHA-256 | **None** at initial discovery (subsequently built and tested in section 9) |
| Dedicated Thor issue | None existed; [#168](https://github.com/Sonoran-Solutions/dualdex/issues/168) created |

> **Post-2026-10-09 `main` status:** PR #167 subsequently merged to `main` at `167b15e` (`167b15ec5a11d5114910c198c9f6757517036e3b`), and PR #170 (L2/R2 shortcuts and review fixes) subsequently merged to `main` at `ccd5e1a` (`ccd5e1a27ef01ff3760c95a29186a0a02c15fed6`). The exact APK source commits tested on the Thor in section 9 (`9eb79b4` and `df7fdc3`) are preserved verbatim below for traceability.

## 2. Initial device discovery (2026-10-09, pre-Thor)

Before the AYN Thor was brought onto wireless ADB later on 2026-10-09, `adb devices -l` initially listed one wireless device:

| Field | Value |
|---|---|
| Manufacturer / model | samsung / `SM-F971U1` (device `h8q`) |
| Android | 17, fingerprint `samsung/h8quew/h8q:17/CP2A.260605.016/F971U1UES3AZIL_OYM3AZIL:user/release-keys` |
| ABI | arm64-v8a |
| Displays | built-in 2448x1848 and built-in 1248x1972 |
| DualDex installed | No |

This is **not an AYN Thor** and is not equivalent to one (different vendor firmware, display topology
and controls, no gamepad). Nothing was installed on it and no data on it was touched. Only read-only
`getprop`/`dumpsys display`/`pm list packages` were run. Results from it do not count toward this gate; actual AYN Thor device details are recorded in section 9.

## 3. Static findings (source only, not hardware evidence)

| Finding | Evidence | Relates to |
|---|---|---|
| Settings advertises "L2 / R2 -> Quick Save (L2) / Quick Load (R2)" | `SettingsScreenView.kt:377` | #14 |
| `KEYCODE_BUTTON_L2/R2` are mapped to joypad bits `BTN_L2`/`BTN_R2` | `InputManager.kt:86-87` | #14 |
| `SaveStateManager.quickSave/quickLoad` existed with UI button calls in `SaveStateScreenView.kt`, but had no caller in the emulator controller/input path | `grep quickSave\|quickLoad app/src/main` | #14 |

Provisional classification at discovery: **P1 #14** (advertised shortcut unwired from controller input; later confirmed on `26bb4d24...` and fixed for L2/R2 in PR #170 / `ccd5e1a`). "Button X / Y: Turbo / Menu Shortcut" and the fast-forward shortcut remain unverified on hardware.

## 4. Protecting existing data (applies before any install on the Thor)

Follow these steps strictly in order before installing or updating any APK on the Thor (see also the verified Oct 15 preflight in [`docs/ROM_SESSION_HARDWARE_ACCEPTANCE.md`](ROM_SESSION_HARDWARE_ACCEPTANCE.md#thor-preflight-and-save-protection)):

1. **Select the exact AYN Thor ADB target explicitly:** inspect `adb devices -l`, confirm the device is the AYN Thor, and pass `adb -s <thor-serial>` on every subsequent command so no other attached device can be touched.
2. **Inspect installed package version and signing certificate:** run `adb -s <thor-serial> shell pm list packages | grep dualdex`; if installed, record `versionName`/`versionCode` and signer (`adb -s <thor-serial> shell dumpsys package com.dualdex | grep -E "versionName|versionCode|signatures|Signing"`). Compare the installed certificate SHA-256 against the candidate APK signer (`apksigner verify --print-certs <apk>`). If the signer differs, **stop**: do not uninstall and do not install; owner decides.
3. **Pause/flush live emulation before pulling saves:** normally pause emulation (or stop the app cleanly after a normal pause) so live battery saves and auto-resume files are flushed before backup.
4. **Back up private saves/states and external mirrors off-device, and verify hashes:** pull all private app save/state directories (`saves_v2`, `save_staging`, `saves_fallback`, `rom_cache`, etc. via `adb -s <thor-serial> exec-out run-as com.dualdex` for debuggable builds) plus any external/shared save mirrors and ROM-adjacent `.sav` files (`adb -s <thor-serial> pull`). Record `sha256sum` of every pulled file and verify every file against device SHA-256 hashes and `stat` sizes. If Android scoped storage or permissions prevent a complete backup, **stop** and ask the owner.
5. **Install only after all checks pass, then re-verify hashes:** run `adb -s <thor-serial> install -r <apk>` (same-signer in-place update; never uninstall or clear data), and re-verify private save hashes against the pre-install manifest before launching. Run destructive or failure-injection tests only on copies. Deeper work stays in #75.

## 5. Test matrix

Result vocabulary: `PASS` / `PARTIAL` / `FAIL` / `BLOCKED` / `NOT_RUN` / `N/A`.
Primary ROM: `edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b` (H&S 2.0.5); vanilla FireRed and vanilla Emerald are **planned baselines** (not executed in the 2026-10-09 run). Trust is by SHA-256, never filename.

| ID | Scenario | Result (2026-10-09 run + `main` follow-up) |
|---|---|---|
| A1 | Install exact APK without data loss; record SHA-256 + source commit | `PASS` (APK `26bb4d24...` + subsequent `adb install -r` updates in §9.1; 82 private files unchanged, signer match) |
| A2 | Cold launch, load verified ROM: video, input latency, audio | `PARTIAL` (video/companion render + logcat clean on `26bb4d24...`; audio output and input latency `NOT_RUN`) |
| A3 | Open/close companion screens during play | `NOT_RUN` (on 2026-10-09; see post-Oct-9 note below) |
| A4 | Close/relaunch; Continue after process death (`am kill`) and reboot | `NOT_RUN` (on 2026-10-09; see post-Oct-9 note below) |
| B1 | Top display renders game; bottom shows companion; scaling/aspect/sharpness | `PARTIAL` (companion on display 4, game on display 0 on `26bb4d24...`; non-uniform 2.5:1 viewport observation — see `O1` in §9) |
| B2 | Bottom-screen touch never triggers game input | `NOT_RUN` |
| B3 | Lid/fold, sleep/wake, rotation: no stale/duplicate presentation windows | `NOT_RUN` (on 2026-10-09; see post-Oct-9 note below) |
| B4 | Background/resume both screens; no black/frozen/misfocused UI | `NOT_RUN` (on 2026-10-09; see post-Oct-9 note below) |
| C1 | D-pad, analog, A/B, L/R, Start/Select | `NOT_RUN` |
| C2 | L2 quicksave (#14) | `FAIL` on `26bb4d24...` (`9eb79b4`); `PASS` (owner-reported) on `f41f5b5c...` (`df7fdc3`, §9.1) |
| C3 | R2 quickload (#14) | `FAIL` on `26bb4d24...` (`9eb79b4`); `PASS` (owner-reported) on `f41f5b5c...` (`df7fdc3`, §9.1) |
| C4 | Fast-forward toggle changes real speed, with state feedback | `NOT_RUN` |
| C5 | Consumed shortcuts not forwarded to core; no repeat on hold | `PASS` for L2/R2 (owner-reported on `f41f5b5c...`, §9.1); X/Y turbo shortcut `NOT_RUN` |
| C6 | L2/R2 "Speed Down / Up" mode steps 1x-4x, Settings speed selector follows | `PASS` (owner-reported on `f41f5b5c...` after debounce, §9.1) |
| C7 | L2/R2 Behavior setting: Quick Save/Load default, Speed Down/Up, Disabled | `PASS` (owner-reported on `f41f5b5c...`, §9.1) |
| D1 | Party: count, identity, HP/level/types/items/status, faint, no stale after ROM switch | `PARTIAL` (single observed 6-slot party display on `26bb4d24...`; in-game cross-check accuracy and ROM-switch clearing unverified in Oct 9 run) |
| D2 | Battle: wild/trainer, switch, HP/stat stages, clears after battle, no false Magic Room on fresh battle, Doubles | `NOT_RUN` (on 2026-10-09; see post-Oct-9 note below) |
| D3 | Calculator: supported calcs use live participants/field; unsupported/ambiguous refuse | `NOT_RUN` |
| D4 | Map: labels/region update, no stale after ROM switch; mark observed vs source-backed | `NOT_RUN` (on 2026-10-09; see post-Oct-9 note below) |
| D5 | UI across Library/Party/Battle/Map/More: readability, clipping, touch targets | `NOT_RUN` |
| E1 | Battery save create/reload | `NOT_RUN` (on 2026-10-09; see post-Oct-9 note below) |
| E2 | Save states repeated create/load; auto-resume | `NOT_RUN` (on 2026-10-09; see post-Oct-9 note below) |
| E3 | Background/resume x N, suspend/wake, force-stop + Continue, reboot | `NOT_RUN` (on 2026-10-09; see post-Oct-9 note below) |
| E4 | ROM A -> B -> A keeps own save identity and companion state | `NOT_RUN` (on 2026-10-09; see post-Oct-9 note below) |
| E5 | Fast-forward during save/load; failed ROM access recoverable | `NOT_RUN` |
| F1 | >= 60 min continuous real-play soak (see §6) | `NOT_RUN` (still open on `main`; see post-Oct-9 note below) |
| G1 | No built-in cheat offered as trusted for H&S (#17); no unsafe cheats on real saves | `NOT_RUN` |
| G2 | Offline Assistant does not invent H&S facts (#13); note privacy issues for #12 | `N/A` (retired on `main`: Offline Assistant removed in PR #182, `4af42ba`; `NOT_RUN` on 2026-10-09) |

**Post-2026-10-09 follow-up status on `main`:**
- **Profile capability flags:** On `2026-10-09` (`9eb79b4` / `df7fdc3`), `battleUiVerified` and `interactiveControlsVerified` were both `false` in `heart_and_soul.json` (nothing in this report satisfies them). After PR #180 (`382b1b0`) merged on `main`, exact H&S sets `battleUiVerified = true` (and `commandCursorVerified`, `moveCursorVerified`, `moveSelectionVerified = true`) while `interactiveControlsVerified` remains `false`.
- **Retired item (`G2`):** Offline Assistant was removed from the app build in PR #182 (`4af42ba`), closing #13 (`RELEASE_CHECKLIST.md`) and making `G2` `N/A (retired)`.
- **Subsequent Oct 15 Thor run:** See [`docs/ROM_SESSION_HARDWARE_ACCEPTANCE.md`](ROM_SESSION_HARDWARE_ACCEPTANCE.md) (PR #205, `110b42f`) for subsequent Thor evidence covering wild battle/companion presentation and Battle/Party/Map navigation, quick-save/quick-load checkpoint recovery, same-ROM reload, H&S -> Odyssey -> H&S ROM switching, 1x/4x pacing, and background/resume + sleep/wake recovery. That run recorded a mixed ~37-minute process session (not a continuous real-play soak), so the >= 60-minute continuous soak (`F1`) remains open (`HOLD`).

## 6. Soak measurement recipe (direct measurements only)

Run a continuous >= 60-minute real-play soak (distinct from the ~37-minute mixed session in [`docs/ROM_SESSION_HARDWARE_ACCEPTANCE.md`](ROM_SESSION_HARDWARE_ACCEPTANCE.md)), logging the exact wall-clock start/end times and total duration actually run.

**Required real-play actions during the >= 60-minute soak:**
- Active overworld movement and normal-speed / fast-forward gameplay.
- Repeated battle entries/exits and companion tab transitions (`Party` / `Battle` / `Map`).
- Periodic screen sleep/wake or lid/fold checks (confirming clean presentation recovery with no black/frozen/duplicate display).
- Periodic battery save and save-state create/reload checks on test copies/safe checkpoints.

**Periodic 5-minute read-only sampler (run every 5 minutes; do NOT wipe device-wide battery stats with `batterystats --reset`):**

```bash
# Replace <thor-serial> with the verified AYN Thor ADB serial.
adb -s <thor-serial> shell dumpsys meminfo com.dualdex            # PSS / RSS growth
adb -s <thor-serial> shell dumpsys battery                        # read-only level, voltage, temp, charging state
adb -s <thor-serial> shell dumpsys thermalservice                 # read-only thermal status / throttling
adb -s <thor-serial> shell dumpsys gfxinfo com.dualdex framestats # frame pacing
```

**Separate duration- and size-bounded log capture (run outside the 5-minute sampler loop, bounded by wall-clock duration and rotated file size):**

```bash
# Bounded crash/ANR/native capture (e.g. 65-minute timeout cap, 4 x 2 MiB rotating logs)
timeout 3900 adb -s <thor-serial> logcat -b crash,main,system -v threadtime -f /sdcard/Download/dualdex-soak.log -r 2048 -n 4 AndroidRuntime:E ActivityManager:W DualDex*:I '*:S'
```

*(Alternatively, stream to host with `timeout 3900 adb -s <thor-serial> logcat ... | head -c 16777216 > soak.log` so host capture is strictly size- and duration-bounded.)*

Report slowdown and companion sync accuracy as **visual observations**, and audible glitches/dropouts as **listener observations**, separately from the numeric telemetry. Do not state thresholds that are not documented.

## 7. Defect and resolution status (2026-10-09 findings vs `main`)

| Status | Severity | Item | Issue / Resolution |
|---|---|---|---|
| **Fixed & merged** | P1 (hardware-confirmed 2026-10-09 on `26bb4d24...`) | Advertised L2/R2 quicksave/quickload did nothing | #14; fixed in PR #170, owner retest passed on `f41f5b5c...` (`df7fdc3`), merged on `main` at `ccd5e1a`. Scoped strictly to L2/R2; X/Y turbo/menu and fast-forward shortcuts remain open |
| **Fixed & merged** | P1 (review finding, code-supported, not a hardware repro) | Deferred quick/slot save-load could act on the wrong ROM after a ROM switch | Fixed in PR #170 (`df7fdc3`, merged at `ccd5e1a`), regression tests `test28b`, `test28c` (`RomSaveIntegrityTest`) |
| **Fixed & merged** | P2 | Persisted fast-forward speed not applied at startup; fresh default showed 2x while emulator ran 1x | Fixed in PR #170 (`6d24f4d` / `df7fdc3`, merged at `ccd5e1a`) |
| **Open (unconfirmed)** | P2 (unconfirmed) | Viewport aspect observation `O1`: top display measured 1620x648 px (2.5:1 vs GBA 3:2); a stretch-to-fill setting exists | Needs on-device settings/visual confirmation |
| **Open** | P1 (#14 remaining scope) | Advertised "Button X / Y: Turbo / Menu Shortcut" and fast-forward shortcut unverified on hardware | Decide/verify on Thor or update Settings/README claims |
| **Open** | P3 | Library ROM title derived from filename (`O2`: `v2.0.0` filename vs verified `v2.0.5` hash) | Labeling only; trust is correctly hash-based |

## 8. Remaining owner / hardware actions under `HOLD`

PR #167 (`167b15e`) and PR #170 (`ccd5e1a`) have merged onto `main`, and the Oct 15 session-safety run ([`docs/ROM_SESSION_HARDWARE_ACCEPTANCE.md`](ROM_SESSION_HARDWARE_ACCEPTANCE.md)) subsequently exercised targeted battle/map/reload/ROM-switch/sleep-wake flows. The remaining actions keeping this gate at **HOLD** are:

1. **Confirm `O1` viewport scaling on Thor:** inspect Settings scaling mode and visually verify whether the 1620x648 (2.5:1) top-display viewport is user configuration or a P2 defect.
2. **Close remaining #14 controller/shortcut coverage:** test D-pad, analog, A/B, L/R, Start/Select (`C1`), fast-forward toggle (`C4`), and X/Y turbo/menu shortcuts (`C5`), or reconcile remaining Settings/README shortcut claims.
3. **Complete remaining matrix rows & baseline runs:** run audio/input-latency (`A2`), bottom-touch isolation (`B2`), calculator (`D3`), UI ergonomics (`D5`), fast-forward save/load & failed-ROM recovery (`E5`), cheat gating (`G1`), and planned vanilla FireRed/Emerald baseline comparisons.
4. **Run the >= 60-minute continuous real-play soak (`F1`):** execute section 6 on a post-merge build following section 4 preflight, with read-only battery/thermal/memory sampling, bounded logcat, visual observations, and listener audio observations.

Disposition stays **HOLD** until the remaining mandatory hardware coverage and >= 60-minute continuous soak pass on a verified build.

---

## 9. Interim hardware results (real AYN Thor, 2026-10-09)

**Device:** AYN Thor, `qti/kalama/kalama:13/TKQ1.231222.001/eng.Thor.20260206.163241:user/release-keys`,
Android 13 (SDK 33), arm64-v8a. Displays: id 0 "Built-in Screen" 1080x1920 (120 Hz default),
id 4 "Screen-2" 1080x1240 (`FLAG_PRESENTATION`). Input: "Odin Controller", `fts_ts`/`fts_ts_3`
touch, `hall_switch`, `gpio-keys`. Storage: 219G free of 934G.

**First tested build (2026-10-09):** source commit `9eb79b4209ba66cd61e38a0902e3692581fab9ed` (PR #167 head at the time of the test; `./ci.sh build` succeeded), APK SHA-256
`26bb4d246878a7d3ca65afbd6438ad021b3ec101e04b80711c98bf7a7d0382c9`, `0.9.0-dev` / versionCode 1.
*(Note: PR #167 subsequently merged to `main` at `167b15e`, and later Thor session-safety checks ran in PR #205.)*

**Backup (before install):** all 82 files of `/data/data/com.dualdex/files` (incl. `saves_v2`, `save_staging`,
`saves_fallback`, `rom_cache`) archived off-device; per-file SHA-256 on device and host identical;
the previously installed APK (SHA-256 `788c4adb...1d11`, built 2026-09-24, debug signer
`1503d5f6...87a9`) and the H&S ROM were also saved. After `adb install -r` (same signer, same
versionCode) all 82 file hashes were unchanged and `firstInstallTime` was preserved.

| ID | Result (on `26bb4d24...` unless noted) | Evidence |
|---|---|---|
| A1 install, data preserved | `PASS` | hashes above; signer match (and preserved across subsequent `adb install -r` updates in §9.1) |
| A2 cold launch / video / companion render | `PARTIAL` | `com.dualdex` focused on both displays; top shows game video (starfield intro), bottom shows Library then Party; no FATAL/ANR in logcat. Audio output and input latency `NOT_RUN` (need a human) |
| A4 process death / reboot / Continue | `NOT_RUN` | |
| B1 dual-screen layout | `PARTIAL` (see `O1`) | companion on display 4, game on display 0 |
| D1 party | `PARTIAL` | single observation: 6 slots, Lv/HP/nature/item shown for LEET Porygon-Z 237/237; not cross-checked against the in-game party screen and ROM-switch clearing unverified |
| All other section 5 scenarios on first APK (`26bb4d24...`) | `NOT_RUN` (except `C2`/`C3` `FAIL`) | On `26bb4d24...`, `C2`/`C3` failed (see §9.1); `C2`/`C3`/`C5` (L2/R2 only)/`C6`/`C7` were subsequently retested on `f41f5b5c...` in §9.1 |

**Observations needing a human, not yet defects:**

- `O1`: game viewport on the top display measured 1620x648 px inside 1920x1080 (2.5:1) where GBA is 3:2
  (1620x1080 or 1440x960 would be uniform). That is a non-uniform scale unless a stretch/fill
  setting is selected. Confirm the scaling mode in Settings and visually on the device; classify
  P2 if it is unintended.
- `O2`: ROM file `Pokémon Heart and Soul (v2.0.0).gba` is byte-identical to the verified 2.0.5 hash
  `edf76ecf...679b`; the library title is filename-derived and misleading. The profile name shown
  is "Pokemon Heart & Soul". P3 labeling issue; trust is correctly hash-based.
- `O3`: `screencap -d 0/-d 4` returned empty images; physical display IDs
  (`4630946441858561667`, `4630946482288158084`) work. Evidence images are in
  `~/thor-backups/shots/` (local, not committed).
- Memory snapshot right after launch: TOTAL PSS 151,430 KB (single sample, not a soak result).

**Remaining human checks (as of 2026-10-09):** audio, remaining physical controller inputs (`C1`, `C4`, X/Y turbo `C5`), fold/sleep/wake display lifecycle (`B3`/`B4`), battle/map/calculator exercises (`D2`–`D5`), save/lifecycle matrix (`E`), and the >= 60 min continuous soak (`F1`). *(Subsequent 2026-10-15 Thor session-safety observations for battle/map, quick-save/load, ROM switch, and sleep/wake are recorded in [`docs/ROM_SESSION_HARDWARE_ACCEPTANCE.md`](ROM_SESSION_HARDWARE_ACCEPTANCE.md).)*

### 9.1 L2/R2 controller shortcuts (#14): finding, fix and retest

| Step | Result |
|---|---|
| Owner pressed L2/R2 on the AYN Thor with APK `26bb4d24...` (source `9eb79b4`) | **FAIL**: quick save and quick load did nothing (P1, #14) |
| Cause (source) | `InputManager` mapped `KEYCODE_BUTTON_L2/R2` to joypad bits GBA lacks; nothing called `SaveStateManager.quickSave/quickLoad` from the input path. The Odin Controller reports both `BTN_TL2/TR2` keys and analog `ABS_BRAKE/GAS` |
| Fix | PR #170: L2/R2 consumed (key and analog, edge-triggered, no repeat), routed to the same `SaveStateManager` quick-state path as the Save screen; new Settings > Controls "L2 / R2 Behavior": Quick Save/Load (default), Speed Down/Up (1x-4x, applied live, persisted, Emulation speed selector updates live), Disabled |
| First fix retest | Owner: L2/R2 work; speed stepping too sensitive (a light press jumped 1x to 3x) |
| Debounce (`db146af`) | 300 ms per-trigger cooldown across key and axis sources, analog press threshold 0.6 to 0.75 |
| Senior review of `db146af` | P1: a deferred shortcut could save/load against the wrong ROM after a switch; P2: persisted speed not restored at startup; P2: stray test file in the PR |
| Review fixes (`6d24f4d`, `df7fdc3`) | Loaded-ROM identity checked inside `globalSaveLock` for quick save/load and for save/load slot; persisted speed applied at startup (default 1x); stray test untracked |
| Final retest | Owner reports testing complete and working on APK SHA-256 `f41f5b5c56bc8ff53fab456f8d61c46f45ddccc40817ae85c90c572128b5e3ac` (source `df7fdc3`, later merged to `main` at `ccd5e1a`) |

Evidence quality: the final retest is an **owner report from physical use**, not an instrumented
measurement; no logcat or recording was captured for it. The cross-ROM race was **not** reproduced
on hardware; it is covered by deterministic unit tests (`RomSaveIntegrityTest` 60/60).

**Build and install record (all `adb install -r`, same debug signer `1503d5f6...87a9`, data hashes
verified unchanged by each install, original backup untouched):**

| APK SHA-256 | Source | Note |
|---|---|---|
| `26bb4d24...82c9` | `9eb79b4` | first acceptance run (L2/R2 FAIL) |
| `9b66d3f5...64db` | `8e3d869` | L2/R2 wired |
| `c9edb60a...445c` | `db146af` | debounce |
| `1db1695c...b1c1` | `6d24f4d` | review fixes: quick-state race, startup speed |
| `f41f5b5c...5e3ac` | `df7fdc3` | slot save/load race (**tested build for 2026-10-09 L2/R2 retest; merged on `main` at `ccd5e1a`**) |

**Gates on `df7fdc3`:** `./ci.sh all` green (native 99/0; `RomSaveIntegrityTest` 60/60;
`InputManagerShortcutTest` 4/4; `SettingsManagerTest` 4/4); `./ci.sh source-check` green (exit 0,
generated output matches disk, no drift); `git diff --check` clean.

**Scope and disposition:** this closes scenarios `C2`, `C3`, `C5` (L2/R2 only), `C6`, and `C7` and the
hardware-confirmed L2/R2 #14 defect (merged to `main` in PR #170 at `ccd5e1a`). It does **not** change the overall disposition:
mandatory coverage (audio, remaining controller inputs, fast-forward X/Y toggle, dual-screen
lifecycle, battle/map/calculator exercises, save/lifecycle matrix, >= 60-minute continuous soak) remained outside
the 2026-10-09 report. **Disposition stays HOLD.** On the tested `df7fdc3` build, auto-resume and battery-save import/export paths did not yet carry the loaded-ROM guard (tracked for #75, not claimed as tested there; commit `80d8963` on `main` subsequently added those loaded-ROM guards).
