# ROM session safety and Thor acceptance

## Scope and revisions

Acceptance date: 2026-10-15 (America/Phoenix). This is general pre-beta hardening, not R.O.W.E. integration or completion of the release soak.

- Starting main: `9b3a2f85b7a9228004872da3aa6871c68c4b3469`.
- Regression/code revision: `8677fb6ba86487a481258b81b6dcc4ea2aac6ed9`.
- PR #204 integration: **VERIFIED**. GitHub reports merge at the starting SHA. The merged tree is byte-identical to reviewed head `2d4e00d5bc14c5265d629016f16aaa934c9e6e35` (`git diff` is empty). Both the senior review and final technical-pass re-review were read.
- The final documentation/PR head and exact-head hosted CI run are recorded in the draft PR handoff. This document identifies the tested code revision explicitly without requiring a self-referential commit hash.

See [session contract](ROM_SESSION_OBSERVATION_SAFETY.md) and the [general pre-beta checklist](../RELEASE_CHECKLIST.md). Production R.O.W.E. compatibility remains **HOLD**; [issue #59](https://github.com/Sonoran-Solutions/dualdex/issues/59) remains **OPEN**.

## Deterministic regressions

The fixtures use invented hashes, fake core bridges, counters and latches. They neither load game bytes nor depend on sleeps. The main fixture asserts that every reader executes with the coordinator lock held. Its successful reads populate party, enemy, both role battlers, indexed battlers, battle UI/presence, active slots, stat stages, location and challenge settings before invalidation is tested.

| Case | Evidence | Result |
| --- | --- | --- |
| UNBOUND, watcher stopped | Direct load after populated A; two polls clear every observation and invoke no further reader | PASS |
| UNLOADED, watcher stopped | Unload after populated A; observations stay cleared across subsequent ticks | PASS |
| Foreign bound B with retained A session | Every reader counter stays unchanged; A clears; legitimate B session then publishes using B's game ID | PASS |
| Same-hash reload | Generation advances, old party/location/UI cannot survive through invalid-read retention, fresh reads recover | PASS |
| State load | Separately tests generation invalidation and recovery | PASS |
| SRAM load | Separately tests generation invalidation and recovery | PASS |
| Reset | Separately tests generation invalidation and recovery | PASS |
| Revocation during an active read | Setter and publication serialize; after setter returns all fields are clear and later denied polls cannot repopulate | PASS |
| Revocation while a reader awaits admission | Latches allow the setter to finish before releasing either a queued poll or direct battle read; no reader runs | PASS |
| Legacy stabilization | Same trusted generation retains the first four invalid party/enemy/location reads, clears at the existing fifth-read threshold, and recovers; mismatch clears immediately | PASS |
| Direct battle helper | Exact H&S-style permission reads once; UNBOUND, UNLOADED, foreign hash, revoked trust and unsupported profile read zero times; each denial recovers | PASS |
| Direct helper after same-hash reload | Every old field is already clear inside the next native callback; returned menu cannot accept input until fresh battle-presence samples arrive | PASS |
| Lock timeout | Real poll contention and injected direct-helper timeout invoke no reader or publication; valid direct reading recovers | PASS |
| Core mutations during active poll | Latches cover reload/state/SRAM/reset contention; old generation stays fixed inside the reader and both threads finish; next tick cannot retain old party/location | PASS |
| Reentrancy and macro cancellation | Existing coordinator reentrancy, frame isolation and quick-load-during-directional-settle tests remain passing | PASS |
| Diagnostic nesting | Coordinator test expects one hold for one transaction; old code would count three overlapping holds | PASS |

The session suite has 16 tests (previously 6); coordinator suite has 17 (previously 16). Existing battle-input tests cover 27 cases; compatibility tests cover 23; profile tests cover 9. Matrix rows with several transitions execute each transition, rather than counting a single happy path as the matrix.

The lock audit follows `pollTick → executeExclusive → observationSessionLock → protected readers`, including the reentrant direct battle helper. The initial poll snapshot releases the monitor before requesting the core lock. Session setters and generation-watcher callbacks acquire only the monitor. ROM switching publishes the companion session after its core transaction. No reverse monitor-to-core edge was found. No new disk/network work or UI callback was placed in the polling transaction. Existing save-manager disk I/O in ROM-switch transactions was not redesigned. Video/audio snapshot paths remain outside the coordinator lock.

## Bounded instrumentation correction

No session-safety defect or attributable performance defect was reproduced. The only runtime change filters diagnostics to **outermost** `executeExclusive` calls. Previously each protected reader counted its nested hold as well as the complete surrounding poll, producing overlapping samples. The new regression requires one outer hold; the old implementation would count three. This changes diagnostics only, not lock acquisition, lock scope, reader admission or publication.

The log's `companion` label still includes all outer coordinator transactions, including smart-fast-forward location reads and core mutations. Its count and average are **not a poll-only duration distribution**. A maximum is a maximum observed transaction, not a guarantee about an unseen reader. The 50 ms timeout bounds acquisition only, never hold time. No per-frame log or profiling inside `retro_run` was added.

The existing controls are `debug.dualdex.ffdiag=1/0` and log tag `DualDexFFDiag`. Source inspection found that the property is sampled once per process startup; its old live-toggle comment was corrected. Set the property before starting a fresh process and restart to disable it.

## Thor preflight and save protection

- Authorized wireless ADB identified manufacturer **AYN**, model **AYN Thor**, device **kalama**, Android **13**, ABI **arm64-v8a**. No tablet or emulator was substituted.
- Build fingerprint: `qti/kalama/kalama:13/TKQ1.231222.001/eng.Thor.20260206.163241:user/release-keys`.
- Installed and candidate versions: `com.dualdex`, `0.9.0-dev`, versionCode **1**, debuggable. The installed build predates this task; its exact source revision is unknown.
- Both APK certificates match SHA-256 `1503d5f647425370597bb78194528f49b3a34c0cbc421b0e9617071cce2587a9`.
- Before any upgrade: archived 83 private save/state/recovery/staging/catalog files, verified every archive member against device SHA-256 digests, preserved the old APK, and copied the selected external save mirror (13 files). A second 83-file archive after normal lifecycle pause was also verified. Before state testing, the owner's newer active save set (17 files) was separately archived and verified so the newer quicksave was preserved too. Backups are in a private persistent host directory, outside Git.
- Safe-install decision: **ALLOW in-place update** after those checks. `adb install -r` succeeded. All 83 private file digests matched immediately after installation and before launch. No uninstall, clear-data, downgrade or destructive restore was used.
- Tested APK SHA-256: `9a0d01268cf2ad862566403711626da298e74a39ece348160f3a53d75bc117a3` (the runtime source at the regression/code revision above).
- Game selected through the existing Library Resume action. Filename says v2.0.0; the existing exact H&S 2.0.5 identity is SHA-256 `edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b`. Filenames are not verification evidence. No ROM bytes were copied into this repository or shared.
- Both displays were inspected; the companion showed live party data. Main display used 1920×1080 and secondary 1240×1080, both configured at 120 Hz. The device was charging. Thermal service reported status 0 but HAL unavailable/no temperature samples, so no measured temperature or throttling claim is made.

## Device measurements and limits

The existing 100 ms companion polling configuration was used. The initial scene was a stationary overworld **with the in-game menu open**, Party companion tab active, normal speed. A separate overworld run followed menu closure and owner gameplay. The owner then entered a wild battle normally; both guest command menu and matching companion player/enemy presentation were verified on the two displays. The fast-forward run used the existing 4× control with Smart Fast-Forward left ON; `speed=4x` was verified in the logs.

For the later 65-window captures, the first five reporting windows are excluded as tab/speed warm-up, leaving 60 complete windows per steady comparison. Uncut logs are retained. The 4× transition window reached 18.44 ms; it is not part of the steady distribution below. No guest memory was edited.

| Steady metric | Overworld 1× | Battle 4× |
| --- | ---: | ---: |
| Reporting windows / elapsed | 60 / 60.292 s | 60 / 60.006 s |
| Effective rate | 59.71 frames/s | 238.99 frames/s |
| Frame interval average / maximum | 16.740 / 23.82 ms | 4.190 / 7.25 ms |
| Frame interval histogram | 1 at 8–12; 62 at 12–16; 3,537 at 16–24 ms | 1 at 1–2; 31 at 2–3; 697 at 3–4; 13,607 at 4–6; 5 at 6–8 ms |
| Emulator step average / maximum | 2.635 / 6.35 ms | 2.238 / 4.98 ms |
| Frame coordinator wait average / maximum | rounded 0.00 / 0.66 ms | rounded 0.00 / 0.88 ms |
| Outer transaction count | 7,927 | 29,318 |
| Transaction wait average / maximum | 0.010 / 4.84 ms | 0.019 / 3.86 ms |
| Transaction hold average / maximum | 0.054 / 2.09 ms | 0.027 / 2.22 ms |
| Acquisition timeouts | 0 | 0 |
| Deadline misses / resets / lost frames | 0 / 0 / 0 | 148 / 0 / 0 |
| Video reads / unavailable / refused | 7,160 / 0 / 0 | 7,129 / 0 / 0 |
| Audio reads / empty-ring reads | 59,839 / 52,626 | 5,625 / 0 |
| Audio samples drained | 5,786,278 | 5,760,000 |

The 4× deadline misses are recorded rather than turned into an invented failure threshold. The sample sustains the expected approximately 239 frames/s with no reset; without a comparable earlier build, no improvement or regression percentage is claimed. An earlier normal-battle capture was cut short when its host ADB server exited: 11 windows / 11.053 s, 59.71 frames/s, 17.92 ms maximum interval, 2.66 ms maximum outer hold, no timeout or unavailable video. This short capture does not substitute for a complete battle control window.

The complete normal-battle control contains 60 steady windows / 60.291 s: 59.71 frames/s, 16.741 / 19.52 ms mean/max interval, 0.082 / 2.15 ms mean/max outer hold, 0.015 / 3.18 ms mean/max outer wait, no timeout, deadline miss/reset, or unavailable video (7,159 reads). Histogram: 83 at 12–16 ms and 3,517 at 16–24 ms. The final four windows also exercise Party navigation while the same battle remains open; this UI transition is disclosed rather than treating the lower UI as identical throughout. The owner separately reported no noticeable issues during the 4× run. Party and Map navigation subsequently displayed the current party and resolved Route 8 correctly.

The companion was then dismissed using its normal Back behavior, leaving the game running with the Restore Companion button visible. The 60 steady normal-battle windows while hidden report 59.71 frames/s, 16.741 / 19.73 ms mean/max interval, 0.080 / 3.06 ms mean/max outer hold, 0.013 / 3.37 ms mean/max outer wait, 7,805 outer transactions, no timeout/deadline reset or unavailable video. Polling remains active when the Presentation is dismissed (also confirmed in source), so this is a visibility control, not a polling-OFF control; no percentage improvement is inferred. The companion restored normally.

After preserving the owner's newer active save set, Quick Save wrote a fresh 528,448-byte current checkpoint and Quick Load returned to the same wild-battle command menu. Both displays then showed the expected player/enemy pair and current party/location. This is a device restore/recovery smoke; the deterministic suite supplies the generation/publication race proof.

Same-ROM Library reload completed at 17:59:23 local time. Session-manager logs explicitly confirm battery restore, newest auto-resume restore and a successful switch to the same H&S storage key. Observations recovered after the reload; no same-hash generation exception or crash was observed.

The cross-ROM control uses the owner's existing Odyssey v4.1.1 image, SHA-256 `44c7e3eafab19c39df7c39d54bafb78a1d9caf7c371244b6f5efb12cfd98d0d0`, as an **unverified** foreign B (not a supported baseline). The session-manager log confirms the A → B switch. Party shows six empty slots and the explicit live-data-disabled warning rather than retaining H&S members. Battle also shows the disabled warning; Map shows unavailable/unknown location, no live marker and no retained Route 8 selection. Returning B → A completed at 18:03:34 with explicit H&S battery/auto-resume restore logs, normal overworld rendering and the correct six-member party. No profile/hash permission was changed to enable this case.

The 4× overworld run was recovered from the device's retained `DualDexFFDiag` buffer after the host transport exited. Deduplication by complete log line and timestamp ordering recovered the original continuous first 65 windows; the largest adjacent timestamp gap was 1.001 s. After excluding five warm-up windows, the 60 steady windows span 60.000 s: 239.00 frames/s, 4.190 / 5.18 ms mean/max interval, histogram 76 at 3–4 ms and 14,264 at 4–6 ms; emulator step 0.912 / 1.34 ms mean/max; outer hold 0.007 / 1.21 ms, outer wait rounded 0.00 / 1.07 ms, frame-lock wait maximum 0.04 ms. There were 29,802 outer transactions, no acquisition timeout or deadline miss/reset, 7,133 video reads with zero unavailable/refused, and 5,627 audio reads / zero empty reads / 5,762,048 samples. A prior attempted speed selection still logged 1× and is retained as a 1× recovery run, not mislabelled as fast-forward evidence.

Normal background/resume and device sleep/wake were exercised after restoring 1×. Android reported settled `Asleep` then `Awake`; the same process (PID 13441) and both displays recovered the correct H&S overworld/party. Additional short background/resume cycles used `am start -W` to wait for completion. The app-scoped crash buffer was empty. The process ran from the 17:33 upgrade launch through the 18:10 observations, including mixed owner play, stationary timed controls, reloads and ROM transitions. This is not an extended continuous real-play soak and does not establish memory growth or long-term audio drift.

End-point `dumpsys meminfo` reported PSS 267,477 KiB (261.2 MiB), RSS 376,644 KiB (367.8 MiB), swap PSS 22,296 KiB. Both PSS and RSS exceed the <150 MB footprint target printed in `ARCHITECTURE.md`; that target's metric/provenance and current budget need reconciliation. There is no initial or controlled older-build memory sample, so this does not establish growth or attribute the footprint to PR #204. Memory-budget acceptance remains outstanding; no speculative memory refactor was made. Thermal status remained 0 with HAL unavailable, so no temperature/throttling conclusion is possible.

The following detailed table preserves the initial menu-only sample, rather than relabelling it as movement, battle, fast-forward or inactive-polling evidence.
Capture: 60 complete diagnostic windows, 60.295 s, 3,600 emulation steps. Rates use total steps / total reported window seconds. Averages are weighted by each metric's sample count; values inherit log rounding. The frame interval histogram provides the distribution; an isolated maximum is not used as a regression percentage.

| Metric | Absolute measurement |
| --- | ---: |
| Effective emulation rate | 59.71 frames/s |
| Frame interval average / maximum | 16.740 / 19.79 ms |
| Interval histogram | 75 at 12–16 ms; 3,525 at 16–24 ms |
| Emulator step average / maximum | 3.117 / 6.47 ms |
| Frame coordinator wait average / maximum | 0.003 / 0.40 ms |
| Outer coordinator transactions | 7,925 |
| Transaction wait average / maximum | 0.022 / 4.48 ms |
| Transaction hold average / maximum | 0.073 / 2.94 ms |
| Coordinator acquisition timeouts | 0 |
| Deadline misses / resets / lost frames | 0 / 0 / 0 |
| Video reads / unavailable / refused | 7,157 / 0 / 0 |
| Audio reads / empty-ring reads | 59,782 / 52,576 |
| Audio samples drained | 5,786,280 |

An empty audio-ring read is not an audible dropout: the drain loop polls more frequently than samples arrive. These counters do not establish speaker quality or input latency. The owner separately reported no noticeable audio, stutter or control issues during the initial normal-speed run. No crash/ANR appeared in the bounded filtered capture; that is not an extended crash/ANR soak. Still images prove display availability, not continuous subjective smoothness.

A controlled pre-#204 comparison is **NOT_AVAILABLE**. The prior installed APK was backed up and sampled, but has no verified source/build lineage. No downgrade was attempted and its values are not used to calculate an improvement. Future comparison must use the same device, exact ROM, scene, speed, polling cadence, display/thermal settings and observation interval. No numerical pass threshold was invented after the run.

Wireless debugging initially connected at the supplied endpoint, dropped, reconnected on the owner's corrected port, and required another transport reconnect after this capture. Incomplete scenarios remain **NOT_RUN**, not PASS. Connection loss is not evidence of an emulator crash or a lock regression.

The final stopped-app save archive verified 89 files against device digests: all original 83 paths remain, 66 original files are unchanged, 17 were updated by normal play/state/session/lifecycle operations, and six paths are new. Pre-test and post-test copies remain in protected persistent host storage. Normal speed was restored. Diagnostics was set to `0`, followed by a normal save-flushing pause and process restart to apply the startup-only setting; the preserved H&S session was resumed for handback.

## Owner RC repetition and remaining soak procedure

1. Enable the already-configured wireless debugging session and record `adb devices -l`, actual manufacturer/model, Android fingerprint, package version, APK digest and certificate. Keep the device awake during capture. Do not change debugging settings/drivers as part of this procedure.
2. Confirm the protected backups remain readable. Before another install, pause normal emulation, back up all private save directories and any SAF mirror, verify member digests, and compare package ID/version/signer. Install with `-r` only after preservation succeeds. Do not uninstall, clear data or downgrade over the owner's saves.
3. Use the locally owned exact H&S 2.0.5 bytes. Confirm the SHA-256 against the shipped profile, irrespective of the filename. Set normal speed through existing app controls. Keep the same 100 ms polling and screen/charging configuration for comparisons.
4. Start diagnostics before a fresh app process. After warm-up and confirming a real ROM is running, capture at least 60 complete one-second windows per stable scene. Record scene boundaries separately and exclude transition/warm-up windows from stationary distributions. Preserve aggregate logs privately; do not publish game/save bytes or personal paths.

```bash
# Replace SERIAL with the currently authorized Thor endpoint; verify identity first.
adb -s SERIAL shell setprop debug.dualdex.ffdiag 1
# Pause normally, then restart the app process if it was already running.
adb -s SERIAL shell am force-stop com.dualdex
adb -s SERIAL shell am start -n com.dualdex/.MainActivity
# Resume the owned game through Library. Warm up; verify the displayed scene/speed.
# Stop this capture after >=60 complete reporting windows; retain unfiltered crash logs separately.
adb -s SERIAL logcat -T 1 -v threadtime DualDexFFDiag:I AndroidRuntime:E '*:S' > thor-scenario.log
```

| Required scenario | Action and evidence to record | Current result |
| --- | --- | --- |
| Normal-speed overworld | Close menu; move normally; check party/location and frame distribution | Owner gameplay followed by stationary town capture measured; extended movement distribution NOT_RUN |
| Fast-forward overworld | Use existing 2×/4× speed controls; repeat comparable scene and interval | Complete continuous 60-window 4× overworld control measured |
| Normal-speed battle | Enter a battle normally; inspect player/enemy, UI, location and responsiveness | Complete 1× battle-menu control measured; extended active play remains outside this smoke |
| Fast-forward battle | Repeat comparable battle phase at selected FF speed; record actual `speed=` | 60 steady windows at verified 4× measured; complete battle play-through NOT_RUN |
| Party/menu navigation | Navigate guest menus and companion members; correlate stalls with time windows | Guest command-menu/Run and companion Party/Battle/Map exercised; no observed stall, no measured input-latency distribution |
| Active companion tabs | Switch Party/Battle/Map; verify fresh, consistent presentation | Party/Battle/Map observed; Route 8 resolved, current participants/party retained correctly |
| Minimized/inactive companion | Record whether polling actually stops or merely the UI hides; compare like for like | Complete 1× hidden-UI control measured; polling stays ON, companion restores |
| Save-state load | Load an existing safe development state; verify immediate old-generation clearing and fresh recovery | Fresh safe checkpoint saved/loaded, observations recovered; race timing proved synthetically |
| Same ROM reload | Reload via Library; inspect clear/recovery without cross-generation data | Library reload succeeded; logs confirm matching identity, battery restore and newest auto-resume |
| A → B and B → A | Use separately backed-up locally owned ROMs; verify no old party/enemy/location/UI survives | H&S → denied Odyssey clears Party/Battle/Map; returning H&S restores its party and normal overworld |
| Suspend/resume | Pause normally, suspend/resume several times; verify saved identity and fresh observations | Background/resume cycles and actual Asleep → Awake passed targeted recovery |
| Continuous play | Agree soak duration before starting; monitor memory, pacing tails, thermal/battery, crashes/ANRs | Mixed ~37-minute process session observed; extended real-play/memory-trend soak NOT_RUN |

For every run record timestamps, build/APK SHA, exact ROM hash/version, scene, speed, actual polling state, frame histogram, mean/max waits and outer holds, timeouts, deadline resets, audio/video counters and human observations of audio, stutters and controls. If a repeatable problem occurs, reproduce it and a control, separate step cost from acquisition wait and hold cost, and identify the dominant reader before changing code. Keep live memory inside the coordinator transaction; do not change video/audio locking or stale-session guarantees.

After capture, pause normally, set `debug.dualdex.ffdiag` to `0` and restart the process to disable diagnostics. Preserve post-run save digests separately: normal gameplay/lifecycle writes may legitimately update battery/resume data; compare untouched saves individually rather than assuming every save must remain byte-identical after play.

## Validation and disposition

Local checks passed: `./ci.sh all` (final committed code, 1,415 JVM tests), `./ci.sh source-check`, `./ci.sh fixture-test`, `./ci.sh rowe-test`, focused-class results within the canonical JVM runs, and `git diff --check`. No custom Gradle/CMake build commands replace the canonical gates. Source-check passed 1,415 local JVM cases, no failures/skips; one pre-existing untracked calculator test is included locally but deliberately excluded from this PR. The final clean hosted count and exact revision are recorded on the PR.

The native reader suite passed 100 cases, tracker self-tests 93, calculator 7,423, memory fixtures 73, ROWE raw decoder 422,771 assertions, and host identity adapter 686 scenarios / 11,156 assertions. No native sources changed, so sanitizer/ABI reruns were not added solely for Kotlin tests. Shipping CMake excludes both host-only ROWE sources; both APK ABIs were inspected for absence of their dynamic symbols. No new production ROWE profile, hash, JNI/Kotlin integration or calculator capability was added.

**Code verdict:** local canonical regression gate PASS; exact-head hosted gate is recorded separately in the draft PR checks. **Device verdict:** targeted normal/4× overworld and battle pacing PASS, with no reproducible lock-induced symptom and no owner-reported audio/stutter/control issue; extended Thor real-play/RC soak **NOT_RUN**. The available sample does not reproduce a lock-induced pacing defect, but cannot establish extended active-play, memory-budget, thermal or long-session release acceptance. No broad release-checklist boxes were completed.

**Recommended next task:** run the extended Thor real-play RC soak with a controlled memory baseline and reconcile the documented footprint budget, retaining normal/4× pacing and audio/input observations. R.O.W.E. production work remains on hold.
