# Issue #194 — Android backup retention investigation

Updated: 2026-10-14 (original LocalTransport investigation: 2026-10-13). PR #188 stays **draft, unmerged, candidate unregistered**.
Issues #194, #186 and #12 stay open. All six owner policy decisions stay pending.

## Engineering ruling

- **ROOT CAUSE: CONFIRMED for the tested API 34 LocalTransport path.** Cancellation
  removes the previous package archive, even if preflight refused before emission.
  A second, post-emission failure can instead be reported as successful and commit
  an unusable replacement. Neither path preserves the previous usable dataset.
- **APPLICATION DEFECT: No demonstrated production implementation defect causing
  this loss.** The assumption that an application exception guarantees transport
  rollback is disproved. Production code is unchanged by this investigation;
  admission and restore validation must remain strict.
- **LOCAL RETENTION: FAIL.** Original failed evidence is preserved; new controls
  exclude restore consumption, explicit package clearing and token changes as causes.
- **REAL CLOUD RETENTION: BLOCKED — FRESH-TARGET KEY RECOVERY.** Separately
  authorized GMS backup succeeded with real encryption flag 1; independent TARGET
  restore failed in GMS metadata decryption before the application ran. No usable
  baseline exists; pre-/post-emission retention experiments remain NOT_RUN.
- **DORMANT MERGE: HELD under the existing owner policy.** No new merge-blocking
  production defect was demonstrated here. A technical decision to merge dormant
  code can be made independently of transport activation, but requires explicit
  owner authorization to change the current draft/acceptance gate.
- **ACTIVATION: HELD.** Prior usable remote data must survive refused and interrupted
  attempts; actual cloud/D2D, native compatibility and owner policy gates remain.
- **AUTHORIZATION:** Dedicated disposable-account scope was separately authorized
  on 2026-10-14; credentials and PINs were entered directly by the owner.
- **NEXT TASK:** Establish a supported fresh-target GMS decryption-key recovery
  flow and independently recover baseline A before testing either failure path.
  A different device class or broader resets require a separate owner decision.

## Authorized real-GMS execution — 2026-10-14

**BASELINE BLOCKED: fresh-target decryption-key recovery.** Account authorization
was separately received from the owner on 2026-10-14, covering a dedicated
disposable account, connection/backup enablement, synthetic uploads/restores,
intentional refused/partial-emission attempts including possible prior synthetic
backup loss, and scoped cleanup. Existing accounts, physical devices, ordinary
DualDex/production saves, account-wide wipes and broad transport resets remained
excluded. The owner entered account credentials and PINs directly; identifiers and
secrets are not included here. Account authorization is no longer the blocker.

### Exact environment and provenance

- Tested code/head: `dfd912667dbefd28f264c0955acfb5555a275c12`, clean worktree.
  This follow-up changes documentation only; production and harness code are
  unchanged. The earlier main integration/token-probe correction remains intact.
- Official image: `system-images;android-34;google_apis_playstore;x86_64`, revision
  14; API 34 / Android 14. Both fingerprints:
  `google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.036.A4/12096271:user/release-keys`.
- Fresh separate SOURCE `dualdex194-gms34-source`, `emulator-5580`; TARGET
  `dualdex194-gms34-target`, `emulator-5582`. Exact AVD/QEMU checks preceded mutations.
  TARGET had no seeded/cached synthetic data; initial inspection and file checks
  confirmed zero staged entries and both canonical batteries absent.
- Active Google Play services: versionCode `263933032`, versionName
  `26.39.33 (190800-995470952)` (updated from the image's 23.18.18).
- Selected transport on both, verified before backup/restore:
  `com.google.android.gms/.backup.BackupTransportService`. Backup Manager enabled;
  `backup_enable_d2d_test_mode` and `backup_local_transport_parameters` unset.
  No fake flags, D2D mode, LocalTransport selection, root/private GMS storage reads,
  local-copy recovery substitute, `bmgr init`, or requested broad transport reset.
- Identical installed test-only package `com.dualdex.backupacceptance`, version 1,
  observer/instrumentation, `fullBackupOnly=true`, `allowBackup=true`. Both installed
  APK hashes verified: `1e5cebbf46664a71141cfae93c5b70bad8d44aa78e9448231736287aa457a843`.
  Public signing-certificate SHA-256:
  `1503d5f647425370597bb78194528f49b3a34c0cbc421b0e9617071cce2587a9`.
- A Google Play API 29 image (revision 9) and separate source/target AVDs were also
  prepared. No account or cloud experiment ran there. Android 9–11 comparison is
  **NOT_RUN**; do not extrapolate API 34 upload or restore results to it.

### Bounded baseline preparation and attempt A

| Step / expected result | Actual callback / framework / transport evidence | Package / aggregate result | Classification |
| --- | --- | --- | --- |
| Valid synthetic seed before secure-lock setup; expect encrypted baseline | `onCreate` → `onFullBackup flags=0 quota=26214400 call=1` → IOException `Transport not verified`; no emitted app files, no restore sets | Package rejected / aggregate Success | BLOCKED: client-side encryption prerequisite; not a retention test |
| After owner set PINs; expect encrypted baseline | Both `secure=true`; GMS reports secondary key not synced; framework stops during `@pm@` metadata backup; no app callbacks | Acceptance package NOT_RUN; `@pm@` Transport error / aggregate Transport error | BLOCKED: key synchronization; missing package result correctly rejected by parser |
| After verified owner unlock; expect key sync / usable backup | GMS moves past missing-key error, then logs `Crypto conflict, requesting re-init`, `Found non-Folsom (unwrapped) keys in DB`, `Removing all backup records`, `Starting new backup session` | Acceptance package NOT_RUN; `@pm@` Transport error / aggregate Transport error | BLOCKED: GMS metadata transition; its internal behavior observed, no initialization/reset command issued |
| One valid retry after GMS's new-session event; expect cloud acceptance | SOURCE `onCreate` → measurement `onFullBackup flags=1 quota=26214400 call=1` returned → streaming `call=2`, same flags/quota, returned; 794,112 bytes reported transferred | Package Success / aggregate Success; command exit 0 | PASS for scoped GMS backup acceptance only; usable remote baseline still requires TARGET recovery |
| Separate untouched TARGET restores observed SOURCE token; expect all eight staged path/hashes and no canonical writes | `restoreStarting: 1 packages`; monitor `PM_AGENT_HAS_NO_METADATA` for `@pm@`; `restoreFinished: -1000`; zero app restore callbacks | No application package result; aggregate restore -1000; shell exit 0 | BLOCKED baseline: no application data recovered; **not a retention FAIL** |

The actual SOURCE dataset token `386db8add52d6f95` was the sole listed token on
both environments after accepted backup. It was selected explicitly on TARGET:

```bash
adb -s emulator-5582 shell bmgr restore 386db8add52d6f95   com.dualdex.backupacceptance --monitor
```

TARGET GMS attempted encrypted `@pm@` recovery but logged `Could not retrieve key`
and `java.security.UnrecoverableKeyException: Key not found` for the source's
Folsom recovery key. Framework then refused restore without package metadata.
No `onRestoreFile`/`onRestoreFinished` from the application occurred. Both API 34
installations lack `com.google.android.setupwizard` (including uninstalled
package listing), and GMS additionally logged unknown authority
`com.google.android.setupwizard.partner`. These observations establish the
missing-key restore blocker; they do **not** prove that the absent setup wizard is
its sole cause or that every GMS emulator fails. No supported source-key recovery
flow was established on this image. No private recovery API, key copying,
setup-wizard sideload, provisioning-flag change or integrity bypass was attempted.

### Complete baseline path/hash comparison

These are selected SOURCE hashes for the accepted attempt. TARGET inspection after
the failed restore returned **zero** staged entries; both synthetic identities'
canonical batteries remained absent. Every expected entry is missing:

| Exact selected relative path | Expected SHA-256 | TARGET recovered |
| --- | --- | --- |
| `saves_v2/1111111111111111111111111111111111111111111111111111111111111111/battery.sav` | `aaf6a8ab7f17b35353821ff0e7bf75abc68f963b2db36c3a1ddb27e382037c89` | missing |
| `saves_v2/1111111111111111111111111111111111111111111111111111111111111111/metadata.json` | `efe24b762dfe647c93d50150ac5a1535aa454ee01f5350ada1cd39d9a924b5b7` | missing |
| `saves_v2/1111111111111111111111111111111111111111111111111111111111111111/slot_1.png` | `039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81` | missing |
| `saves_v2/1111111111111111111111111111111111111111111111111111111111111111/slot_1.state` | `89cc4aac7a40201f83493c2e208705c1c049f9041c5815a8c2a270b0b7d642e2` | missing |
| `saves_v2/2222222222222222222222222222222222222222222222222222222222222222/battery.sav` | `d693d44d8478475adfae30336c111ec5bc69f61ea934b46d66857c1b3ea81607` | missing |
| `saves_v2/2222222222222222222222222222222222222222222222222222222222222222/metadata.json` | `317bf9b0aec6e5b7a4edbc4b9efb229a27283c4f66055513fd689d0b55e7f859` | missing |
| `saves_v2/2222222222222222222222222222222222222222222222222222222222222222/slot_1.png` | `039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81` | missing |
| `saves_v2/2222222222222222222222222222222222222222222222222222222222222222/slot_1.state` | `89cc4aac7a40201f83493c2e208705c1c049f9041c5815a8c2a270b0b7d642e2` | missing |

This empty recovery is baseline failure/blockage, not evidence about preservation
after a newer failed backup. It occurred **before** either failure experiment.
**Experiment B (pre-emission retention): NOT_RUN, baseline prerequisite BLOCKED.**
**Experiment C (partial-emission retention): NOT_RUN, baseline prerequisite BLOCKED.**
The two-call sequence is now observed on GMS, but the existing test-only emission
injection did not run and has no GMS retention result. No malformed-metadata or
partial-emission fixture was seeded in this real-cloud phase. No retries after a
conclusive retention failure occurred; no such retention experiment was admissible.

### Cleanup, decisions and evidence

After preserving reports, authorized cleanup issued **only**
`bmgr wipe com.google.android.gms/.backup.BackupTransportService com.dualdex.backupacceptance`.
Framework recorded `clearBackupData()` for that exact package/transport. Server-side
physical erasure was not independently verified. No account-wide wipe or transport
initialization/reset was requested. Only the test package was uninstalled from
both AVDs, absence verified, both test emulators stopped, and the four task-owned
`/tmp` AVD userdata directories removed. SDK images and private evidence remain;
no account identifier, credential, PIN, raw backup or binary fixture is committed.

**Application defect:** None demonstrated; encrypted target restore stopped before
DualDex ran. **LocalTransport findings:** unchanged, both original retention FAIL.
**Dormant merge:** Held under owner policy, independently of this blocker; ordinary
save ordering, parent-directory fsync and Saves review UI retain their earlier
scoped evidence and native/lifecycle/abrupt-power-loss limits below.
**Activation:** Held; successful upload is not recoverable-baseline or retention
proof. Issues #194/#186/#12 remain open; #188 draft/unmerged, auto-merge disabled,
ordinary candidate unregistered. Existing owner policy choices remain pending.

**Next bounded task:** Establish a supported fresh-target Google cloud key-recovery
flow that can recover this package's complete baseline before any failure test.
Use environments inside the authorized disposable-emulator scope; another device
class, account-wide reset or broad transport reinitialization requires a separate
owner decision. If supported GMS cannot meet prior-usable-backup preservation,
return for a separately approved versioned-recovery design; do not weaken it.

Private evidence: `/tmp/dualdex-gms-194-authorized/api34-A-new-session` (accepted
backup / failed recovery), with separately preserved no-lock, key-sync and
metadata-transition attempts in `api34-A`, `api34-A-encrypted`, `api34-A-post-unlock`.
These device results belong to tested code `dfd9126`; subsequent documentation-head
local/hosted validation is recorded separately in the final issue/PR handoff.

| Private evidence file | SHA-256 |
| --- | --- |
| `baseline-recovery-comparison.json` | `682829df16354e67aac3b8f5ed1f7ee71ae861ededbc89151d9737cd163323ac` |
| `baseline-backup.txt` | `dd448f6dadd4b3da5caa5c1418765383495e155de5eb005d6d28490c26dfc247` |
| `baseline-restore.txt` | `ddfe7b55ee3816ffd77983cfdcee79b63d251916b46ceda22c0dea9be501ac26` |
| `source-callbacks.log` | `49dc9da222ff6977b6777919bae636ad91104800cec46f7aa22eb07a1ce1a990` |
| `target-callbacks.log` | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |
| `target-restore-diagnostics.log` | `f4cb10a6c7bc10ec50471f0a45e72cd42bc2584a3221a4c39d2f327ef9bd8f82` |
| `expected-path-hashes.json` | `2a89f24a5acc1e35108ade0375881ad9da84da13751d98786cb2c22c9b070283` |

## Real-GMS preparation checkpoint — 2026-10-13 (issue #194)

Historical checkpoint; superseded by the authorized execution above.

**BLOCKED: ACCOUNT AUTHORIZATION.** No separate owner authorization or dedicated
Google account was supplied. No account was connected, backup setting changed,
synthetic cloud upload attempted, or cloud dataset cleaned up in this task.
The attached task explicitly excludes interpreting its own text as authorization.

| Scenario | Environment / active transport / callbacks | Package / aggregate / recovered hashes | Status |
| --- | --- | --- | --- |
| A: independently recoverable GMS baseline | No authorized source/target; transport, flags, GMS version and token unobserved | NOT_RUN / NOT_RUN / no recovered files | BLOCKED |
| B: GMS pre-emission retention | A not established; no cloud callbacks | NOT_RUN / NOT_RUN / no comparison possible | BLOCKED |
| C: GMS post-emission retention | New A not established; injection not executed on GMS | NOT_RUN / NOT_RUN / no comparison possible | BLOCKED |
| Android 9–11 comparison | Installed API 28 image is `default;x86_64`, not a verified GMS environment | No cloud evidence | BLOCKED |
| Android 12–14 comparison | Installed API 34 image is `google_apis;x86_64`; no authorized account or verified remote baseline | No cloud evidence | BLOCKED |

Read-only preparation found JDK 17, Platform 34, NDK 27.2.12479018,
CMake 3.22.1, host compiler and Android command-line tools available. No saved
AVD was listed, no emulator was connected, and no Google Play Store system image
was installed. The attached physical device was not selected or inspected.
Installed `google_apis` alone does not establish supported cloud recovery.
Provision fresh Google Play source and target AVDs using Device Manager (prefer
API 29/30 and API 34 where available); give each an explicit disposable name,
separate new userdata, and disable snapshot reuse. Record unsupported generations
as BLOCKED. Do not create accounts or enter credentials through automation.

The current official [AVD guide](https://developer.android.com/studio/run/managing-avds)
and [backup testing guide](https://developer.android.com/identity/data/testingbackup)
were consulted. The latter's sample selects LocalTransport and fake encryption;
it must not be run as evidence of actual GMS retention. The separate-source/target
requirement here is stronger than a same-device reinstall check.

### Integration and narrow harness correction

Starting PR head: `88d6f34597727b67e3f511c38679c90e78cf7cfe`.
Fetched main: `7b9fd6f` (merged R.O.W.E. research stack). Merge commit `ff902bd`
preserves both histories without squash/rebase. **Only conflict:** `ci.sh` usage
text. Resolution lists `rowe-test`, `rowe-sanitize` and `backup-acceptance` together;
all existing dispatch gates remain. `fixture-test`, hence `test`/`all`, invokes
the new R.O.W.E. raw suite; acceptance still runs the three host boundary tests
before its opt-in APK build. No additional conflict or unrelated refactor occurred.

Latest senior review: [5511874704](https://github.com/Sonoran-Solutions/dualdex/pull/188#pullrequestreview-5511874704).
Its P3 archive concern is addressed by checking the **observed** restore token
before every local archive probe and refusing anything other than the measured
`1` layout. The existing token boundary test includes unsupported/layout-injection
cases. This is a focused tooling change for review, not a new retention experiment.
No LocalTransport simulation was rerun in place of the blocked cloud phase.

Production application source/manifests are unchanged from the reviewed PR head;
the merged R.O.W.E. changes remain exactly as main supplied them. The observer
remains opt-in, `testOnly`, package `com.dualdex.backupacceptance`, debug-only,
with no release variant. Ordinary `com.dualdex` does not register the candidate.
Previous API 28/33/34 and LocalTransport evidence below retains its original scope
and hashes. New exact-head validation and build identities are reported separately
in the issue/PR handoff; old green runs are not validation of this combined head.

### Authorized execution handoff

Before experimental execution, obtain an explicit owner statement covering **all**
of: dedicated disposable account use; connecting it to each fresh source/target;
enabling account backup; uploading synthetic data; restoring that data; deliberately
failed attempts (including risk of destroying the last synthetic backup); and
cleanup of those synthetic datasets. Account identifiers, credentials and private
logs stay outside Git. No existing owner account, normal DualDex installation or
production saves may be used. This gate does not authorize `bmgr init`, account-wide
wipe or unrelated dataset removal; any such operation needs its own explicit scope.

Use the manual protocol below, never the runner's `local` or `d2d-test` mode.
No new cloud mutation automation is needed before a supported environment exists.
For **each** source/target and each experiment:

- Verify exact serial, QEMU property and disposable AVD name before install or
  mutation. Verify final APK package, `testOnly`, observer/instrumentation and
  `allowBackup`; capture SHA-256 and public signing certificate digest with
  `apksigner verify --print-certs`. Use the identical APK on source and targets.
- Privately record API/fingerprint, GMS version (`dumpsys package
  com.google.android.gms`), selected transport (`bmgr list transports`), backup
  configuration and callback flags/quota. Expected selected transport is
  `com.google.android.gms/.backup.BackupTransportService`; a listed class is not
  remote proof. Read `backup_local_transport_parameters` and
  `backup_enable_d2d_test_mode`; refuse fake encryption or enabled D2D mode.
  Check selection again immediately before every backup and restore. Do not
  enable other transport test flags or inspect GMS private storage with root.
- Capture timestamped callbacks and framework output for each isolated attempt,
  package and aggregate status separately, and source/target `bmgr list sets`.
  Preserve the original baseline token. Multiple tokens require explicit mapping
  to the source dataset, never guessing or substituting a current target token.
- Establish A on a separate fresh target with **all eight selected path/SHA-256
  pairs**, validated sessions, excluded sentinels absent, and no automatic
  canonical batteries for either synthetic ROM identity. Do not seed a target or
  copy application data to it. Installation-time automatic restore must also be
  captured; it cannot be mistaken for a later failure recovery on reused userdata.
  Package rejection for test-only/sideloaded apps is an environment BLOCKED result;
  do not remove safeguards to obtain eligibility.
- For B, require malformed-metadata refusal and callback evidence of no file
  emission. No framework attempt or no intended refusal means NOT_RUN or
  INVALID_TEST. Restore baseline A on another fresh target, compare the complete
  map, and stop after a conclusive FAIL. Missing, changed or invalid entries are
  FAIL even when framework text reports Success.
- For C, establish a new independently recovered A before changed valid data and
  `emission-failure`. The existing observer assumes measurement call 1 and streaming
  call 2 **on the same instance**. This is not yet proven for GMS. Require both
  actual partial emission and `injected failure after archive prefix emission`
  diagnostics. An absent injection or a different callback sequence is
  INVALID_TEST, never PASS. Adapt only the test observer if observed behavior
  requires it, review/rebuild that delta and re-establish A before retrying.
- Restore C's original baseline on a new cache-free target. Save a complete
  per-path expected/recovered digest comparison, including missing/unexpected
  paths; distinguish retention outcome from the simulated fault trigger. Keep
  raw reports in a private mode-0700 directory. A hash mismatch exits nonzero.
  Stop after a conclusive failure; do not repeatedly overwrite a dataset.
- Remove only the test package and agreed disposable environments/datasets after
  preserving private evidence. Do not use a broad transport reset for cleanup.
  Repeat on another supported Android generation only after establishing its
  independent A; report differences in flags, quota, measurement/callback order,
  eligibility, tokens and retention without extrapolation.

**Independent rulings:** Application defect: None demonstrated. LocalTransport
findings: unchanged (both retention FAIL). Dormant PR merge: Held under owner
policy; active save ordering, directory fsync and Saves review UI retain their
prior scoped evidence and unverified native/lifecycle/power-loss limits described
below. Agent activation: Held; real GMS retention is unknown. If GMS cannot meet
last-usable-backup preservation, obtain a separate versioned-recovery design
decision without weakening the requirement. Keep #188 draft/unmerged, auto-merge
disabled and #194/#186/#12 open. Outstanding decisions: disposable-account scope,
supported environments, dormant-merge policy and the six existing activation
choices. **Next bounded step:** after separate account authorization/provisioning,
prove A's complete remote recovery on one fresh GMS target before B or C.

## Audit of the original result

The original clean `a181239` run recorded one valid cloud archive restore (8 entries),
a malformed-metadata backup refusal, then an empty restore from token `1`.
`commands.json`, `callbacks.log`, both restore reports and `transport-result.json`
agree. The package rejection and aggregate task success are different observations.
There was no intervening transport initialization, wipe, token switch or package
uninstall in that sequence. The original runner did explicitly clear **only** the
acceptance package before each restore. It selected the single listed local token.

This audit alone did not prove causality: a restore or clear might have affected the
transport. Controls A/E below establish that they did not in this environment;
control C eliminates a baseline restore before the refused attempt entirely.
The [original acceptance FAIL](ANDROID_BACKUP_DEVICE_ACCEPTANCE.md#critical-failed-local-transport-retention-experiment)
is retained, with its original private hashes and command records.

## Android implementation evidence

Reference sources are pinned to official AOSP `android-14.0.0_r75`; this is an
Android 14 reference, not a claim that the Google emulator image is built from
that exact public tag. The deployed image's LocalTransport cancellation and
framework binder completion bytecode were also inspected using its `oatdump`.
They corroborate the relevant paths below. No Android framework/transport was patched.

1. **Transaction preparation precedes application preflight.**
   [`PerformFullTransportBackupTask.run`, lines 419–525](https://android.googlesource.com/platform/frameworks/base/+/android-14.0.0_r75/services/backup/java/com/android/server/backup/fullbackup/PerformFullTransportBackupTask.java#419)
   calls transport `performFullBackup`, obtains quota/flags, then starts the
   runner's measurement. A failed runner leads to `cancelFullBackup`; successful
   completion leads to `finishBackup`. The transport transaction therefore exists
   before DualDex can refuse essential coverage inside `onFullBackup`.
2. **Measurement exceptions do not themselves communicate a negative result.**
   [`BackupAgent.BackupServiceBinder.doMeasureFullBackup`, lines 1244–1270](https://android.googlesource.com/platform/frameworks/base/+/android-14.0.0_r75/core/java/android/app/backup/BackupAgent.java#1244)
   invokes `onFullBackup`, wraps/rethrows IOException, but its `finally` reports
   the measured byte count. With no emitted files this is zero.
   [Preflight, lines 758–790](https://android.googlesource.com/platform/frameworks/base/+/android-14.0.0_r75/services/backup/java/com/android/server/backup/fullbackup/PerformFullTransportBackupTask.java#758)
   passes a nonnegative size to the transport's size check. LocalTransport rejects
   size <= 0. The recorded package error **-1002** is
   [`TRANSPORT_PACKAGE_REJECTED`](https://android.googlesource.com/platform/frameworks/base/+/android-14.0.0_r75/core/java/android/app/backup/BackupTransport.java#55),
   rather than proof that a remote server rejected or retained anything.
3. **LocalTransport has one mutable package archive.**
   [`performFullBackup`, `sendBackupData`, `finishBackup`, `cancelFullBackup`](https://android.googlesource.com/platform/frameworks/base/+/android-14.0.0_r75/packages/LocalTransport/src/com/android/localtransport/LocalTransport.java#455)
   retain the target name/socket at transaction start. The first data send opens
   the existing package tar file for replacement. Finish closes/flushes it.
   Cancellation obtains that same filename, tears down the transaction and deletes
   the file if present, even when this attempt never opened a replacement. There is
   no previous-version file or rollback transaction. **Start alone does not delete
   A; cancellation can delete A before any new app file is emitted.**
4. **Streaming exceptions can still complete successfully.**
   [`BackupAgent.BackupServiceBinder.doFullBackup`, lines 1198–1240](https://android.googlesource.com/platform/frameworks/base/+/android-14.0.0_r75/core/java/android/app/backup/BackupAgent.java#1198)
   wraps/rethrows IOException but writes end-of-data and reports operation result
   zero in `finally`. [`FullBackupEngine.backupOnePackage`](https://android.googlesource.com/platform/frameworks/base/+/android-14.0.0_r75/services/backup/java/com/android/server/backup/fullbackup/FullBackupEngine.java#240)
   can consequently complete the runner successfully. Control D records exactly
   this outcome: package Success, a truncated snapshot committed, zero usable
   staged entries. This is an injected app failure through the real framework,
   not proof that a production filesystem failure was reproduced.
5. **Token `1` is a dataset identifier, not a version.**
   [`CURRENT_SET_TOKEN`, `getAvailableRestoreSets`, `getCurrentRestoreSet`, `startRestore`](https://android.googlesource.com/platform/frameworks/base/+/android-14.0.0_r75/packages/LocalTransport/src/com/android/localtransport/LocalTransport.java#605)
   keep the current local dataset token constant and read its package archive.
   Successful B overwrites A under the same token. Restore reads do not consume it.
   The source can list separately populated directories 2–9, but our fresh AVD had
   none; the framework did not create historical versions for these backups.

The public transport contract asks cancellation to discard partial data and roll
back **if possible**; it does not promise universal previous-version preservation:
[`BackupTransport.cancelFullBackup`](https://android.googlesource.com/platform/frameworks/base/+/android-14.0.0_r75/core/java/android/app/backup/BackupTransport.java#550).
The [public agent callback](https://developer.android.com/reference/android/app/backup/BackupAgent#onFullBackup(android.app.backup.FullBackupDataOutput))
provides file emission and IOException, not an application-controlled transport
transaction API. The separate documented quota-exceeded rollback behavior must not
be generalized to arbitrary callback exceptions. No quota-overflow experiment ran.
[Auto Backup documentation](https://developer.android.com/identity/data/autobackup#BackupLocation)
also describes latest-backup storage, not user-accessible version history.

Cancellation is established here by the framework control flow, deployed method
implementation and immediate archive deletion after the rejected preflight. The
transport's debug logging is compiled off; no direct runtime method trace is claimed.
GMS is an independent transport: its server-side transaction policy is **unknown**.
The API 34 framework observations justify testing it, not predicting its results.

## New controlled experiment — bounded, complete, stopped

Fresh AVD `dualdex194-retention`, `/tmp/dualdex-194-avd`, serial `emulator-5584`.
API 34, `google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.050/12077443:userdebug/dev-keys`.
No Google account. Only AOSP LocalTransport; fake encryption flag 1, quota 26,214,400.
`adb root` enabled only on this explicitly disposable AVD for archive **hash/stat**
probes. No raw transport payload is copied into Git.

Clean harness/runner revision: `04ad1c7cd15e5c86fe5e154bdcab5dc9edff0fd3`;
`sourceDirty=false`. APK SHA-256:
`0be66151a99abe188eb4808ec761049b7c29a4bc7238348543f50bd26a48c6ac`.
Package `com.dualdex.backupacceptance`; merged manifest verified before install.
Production source is byte-identical to reviewed `fef66e1`.

| Control | Exact operation / expectation | Observed result | Classification |
| --- | --- | --- | --- |
| A | Valid A → clear test package → restore token 1 twice, no later backup; expect same 8 source hashes | Both restores match all 8; transport archive SHA unchanged | PASS: real framework/LocalTransport control |
| E | Force-stop and fresh inspection; restore same token without explicit `pm clear` | Same 8 hashes; transport archive unchanged | PASS: real process/restore control; Android may perform its own restore-time clearing |
| B | Seed changed valid B → backup → restore token 1 | Package Success; same token; all 8 B hashes; changed battery is NEW, A is not returned | PASS: replacement control, **not** retention/version-history proof |
| C | Valid A backup, no intervening baseline restore → malformed metadata → refusal before file emission | Package -1002; aggregate Success; tar file immediately absent **before clear/restore**; 0 staged entries | **FAIL: previous usable local dataset lost** |
| D | Valid A + verified 8-file restore → valid measurement of B → test-only truncate/emit archive → IOException | Package/aggregate Success; replacement tar 398,336 bytes; restored ZIP 395,745 bytes; validation isolates it, 0 reviewable entries, canonical battery missing | **FAIL: previous usable local dataset replaced by invalid data**; injected fault through real framework |

D's fault is confined to the observing subclass in the opt-in test flavor. On
this measured API 34 path, callback 1 performs production measurement, callback 2
emits the first half of that prepared archive and throws. Both calls and the
emitted byte count are logged. The runner requires the injection log to exist;
a skipped injection cannot satisfy the matrix. No production error seam was added.

Archive path measured on this AVD only:
`/data/user/0/com.android.localtransport/files/1/_full/com.dualdex.backupacceptance`.

| Checkpoint | Bytes | Transport tar SHA-256 |
| --- | --- | --- |
| A after backup, first restore, second restore and E restore | 794,112 | `7a4c77ca0c103d98fca0b78b7f4c47c6fa691061136803d99fab26ead9dc92db` |
| B after successful replacement and restore | 794,112 | `868ddad97f963d49c2d4a63da35708b36b7f1b0fceadb3ebd755ec6fa06572ec` |
| C valid baseline | 794,112 | `f820b426f25c560397e7d8da045177cd0cecd931b2f4adecc4a95858f89521a7` |
| C immediately after refusal | absent | absent |
| D valid baseline | 794,112 | `da02e2894da931f8609fff03ddb67b3e9aa7eced84cf0c7eab84dbeb91b7b01d` |
| D after injected emission failure | 398,336 | `4857c49139ec8be055b4d5d059dc091aed4ede52148660479dbbe7889a231d3f` |

A's first battery SHA is
`aaf6a8ab7f17b35353821ff0e7bf75abc68f963b2db36c3a1ddb27e382037c89`;
B's is `d693d44d8478475adfae30336c111ec5bc69f61ea934b46d66857c1b3ea81607`.
Every baseline compared all 8 selected path/digests against the seed inventory,
not only the battery. Generated metadata dates explain different tar hashes for
independently seeded A baselines. The complete maps and results remain private.

The runner exited **1** with `LocalTransport retention FAIL`; this is the correct
failed acceptance outcome, not a harness crash. It restored the previous selected
transport, encryption/D2D parameters and disabled-backup state, then uninstalled
only the synthetic package. No further destructive retention retry was performed.

### Reproduction and safety guards

Create a **new** AVD using the previously documented official API 34 image:

```bash
mkdir -p /tmp/dualdex-194-avd
printf 'no\n' | ANDROID_AVD_HOME=/tmp/dualdex-194-avd avdmanager create avd \
  --name dualdex194-retention --package 'system-images;android-34;google_apis;x86_64' \
  --path /tmp/dualdex-194-avd/api34
ANDROID_AVD_HOME=/tmp/dualdex-194-avd emulator -avd dualdex194-retention \
  -port 5584 -no-window -no-audio -gpu swiftshader_indirect -no-snapshot -memory 2048
# In another terminal, after sys.boot_completed is 1:
adb -s emulator-5584 emu avd name  # must be dualdex194-retention
adb -s emulator-5584 root
adb -s emulator-5584 wait-for-device
./ci.sh backup-acceptance
python3 tools/android-backup/acceptance.py --serial emulator-5584 \
  --output /tmp/dualdex-194-controls --transport local \
  --disposable-avd dualdex194-retention --retention-matrix --cleanup
```

Expected exit is nonzero on the observed image. Inspect each package result,
archive checkpoint and recovered hash map. The mode omits unrelated storage/UI
scenarios already verified on API 28/33/34. Neither `bmgr init` nor cloud reset is
needed here. Afterward stop only this emulator and remove only its `/tmp` AVD
userdata; preserve the private reports. Do not substitute another existing AVD.

All transport modes now require serial/property checks **and** an explicit exact
AVD-name match. D2D initialization additionally requires `--allow-transport-reset`.
These guards execute before installation or settings changes. Physical mode still
requires `allowBackup=false` and cannot run transport tests. Ordinary-app APKs are
rejected before ADB calls. Python optimization is refused. Package and aggregate
status are stored separately; missing/ambiguous results or tokens fail closed.
Three small host tests cover these guards/parsers via `./ci.sh backup-acceptance`.

## Smallest safe correction / design choices

**No production fix is justified by these controls.** LocalTransport owns the
archive before the agent callback; an agent cannot undo its deletion. Prebuilding
more data, changing exception types, swallowing errors or returning an empty
successful stream cannot establish rollback. Re-emitting a stale cached snapshot
would hide incomplete essential coverage and does not address streaming failure.
Admission, ownership, ROM/secrets exclusions and staging validation stay intact.

| Choice | Protection and cost | Recommendation |
| --- | --- | --- |
| Leave candidate inactive; use existing explicit battery/state export/share flows | Owner can retain independently named copies outside app storage; requires action, per-game verification and external retention | Available interim route; no claim that opening a share sheet alone creates a durable backup |
| Independently versioned, verified export artifacts | Can retain older complete artifacts without transport rollback; needs a destination, storage/retention UI and explicit policy | Preferred design to evaluate **if real-cloud retention cannot meet the requirement**; separate owner design approval before implementation |
| Register the current agent after only LocalTransport tests | Depends on transport behavior already disproved locally and unknown remotely | Reject activation |
| Test authorized real GMS while preserving current code/policy | Answers the actual supported-transport question with the smallest next experiment | Recommended next task; success is scoped to tested versions, not universal rollback |

No alternate architecture, dependency, automatic upload or cleanup is implemented.

## Separately authorized real-GMS protocol — NOT_RUN

**Do not execute this section until the owner separately authorizes account setup,
connection, backup configuration and cloud execution.** Provision a disposable
Google account with no existing personal datasets; a fresh source and fresh target
Android environment. For supported-generation coverage use API 28–30 and API 31–34
where GMS actually supports the test package and required encryption conditions.
Record inability to establish them as BLOCKED; do not fake flags or swap in D2D.

1. Record source/target fingerprints, API and GMS version privately. Owner signs
   only the disposable account in and selects it for backup. Follow the
   [official testing guide](https://developer.android.com/identity/data/testingbackup)
   for prerequisites, but its LocalTransport sample is **not remote proof**.
   Verify the selected transport is `com.google.android.gms/.backup.BackupTransportService`,
   D2D test mode is off, and no `backup_local_transport_parameters` fake flag is set.
   Never initialize/wipe the GMS transport without separate reset authorization.
2. Build `./ci.sh backup-acceptance` with automatic backup allowed **only for these
   disposable environments**. Verify APK SHA and final manifest/package/observer
   exactly as the committed runner does before installing `com.dualdex.backupacceptance`.
   The existing runner deliberately cannot automate a real GMS account/transport.
3. With explicit source serial `SOURCE`, run and capture:

   ```bash
   adb -s SOURCE shell am instrument -w -e mode seed \
     com.dualdex.backupacceptance/com.dualdex.acceptance.AcceptanceInstrumentation
   adb -s SOURCE shell run-as com.dualdex.backupacceptance cat files/acceptance/report-seed.json
   adb -s SOURCE shell bmgr list transports
   adb -s SOURCE shell bmgr backupnow com.dualdex.backupacceptance
   adb -s SOURCE shell bmgr list sets
   ```

   Require package Success, observed cloud-route flags, real GMS identity and a
   recorded source dataset token. Copy the 8 cloud-selected source path/digests
   from the seed inventory, excluding `.bak`, `.tmp` and the legacy catalog.
   Record aggregate status separately. Do not assume token `1`, choose an arbitrary
   listed dataset or treat `bmgr` success as proof of remote persistence.
4. On a separately fresh target using the same disposable account and test APK,
   select the recorded source dataset and restore **only** the acceptance package:
   `adb -s TARGET shell bmgr restore RECORDED_SOURCE_TOKEN com.dualdex.backupacceptance`.
   Run `-e mode inspect` with the same instrumentation command and read
   `files/acceptance/report-inspect.json`. Require all 8 path/digests, valid staged
   sessions and missing canonical batteries. Save callback/restore outcomes.
   No LocalTransport files or other host/device copies may supply the target.
5. On SOURCE seed `-e scenario malformed`, then issue the same package backup.
   Require the intended refusal callback and record package/aggregate outcomes,
   transport/token and time. Do not substitute a local quota/mock error.
6. Create a fresh target again (or separately provisioned fresh test profile) with
   the disposable account and **no restored cache/data**, then recover the recorded
   source token. Compare every prior path/digest. This avoids mistaking a target's
   cached A for remotely retained A. Retention PASS requires byte-identical usable
   A. Missing/different/invalid data is FAIL regardless of status text.
7. Also exercise the scoped post-emission `-e scenario emission-failure` case,
   with a newly verified A baseline first. On versions whose callback sequence
   differs, adapt only the isolated observer and prove injection execution;
   never infer it from a result label. Require usable A after interruption.
   Stop after a conclusive failure; no repeated destructive trial loop.
8. Uninstall only the isolated package, restore authorized test settings, and
   remove only disposable test environments/account datasets under the agreed
   cleanup policy. Keep account identifiers, logs, raw data and credentials private.

Package `-1002`, normal callbacks, encrypted transport flags and a locally retained
ZIP cannot by themselves satisfy the remote recoverability requirement. Obtain
owner acceptance of the documented preservation policy after evidence, not before.

## Dormant-code merge assessment, independently of activation

| Component after merging | Reachability / existing evidence | Remaining ordinary-user consequence |
| --- | --- | --- |
| Candidate framework backup/restore callbacks | No `backupAgent` in ordinary manifest; opt-in flavor only registers observer | #194 candidate callback failures are not newly reachable in ordinary builds; existing XML Auto Backup remains enabled and is not a versioned backup guarantee |
| Selection/inventory/archive construction | Called by unregistered candidate (or isolated test harness) | No automatic candidate cloud snapshot production in ordinary builds |
| `SaveStateManager` ordering and battery import guard | Active immediately for ordinary manual/quick/auto states and battery imports; host tests plus Android storage/fake-core evidence on API 28/33/34 | New timestamps may lead wall time; failed protection conservatively blocks old resumes; native game/SRAM compatibility still unverified |
| Shared `AtomicSaveFile.copyFromStaging` directory fsync | Active immediately for states, battery flush/export, migration/recovery callers; physical Thor directory-sync/atomic-copy success plus injected failures | Unsupported/failing directory sync can report failure after rename although target changed; power-loss/low-storage and remaining device/filesystem coverage are unproven |
| Staged store / review UI | Saves review entry exists immediately without ROM; actual production component/conflicts/recreation/restart tested on API 28/33/34; normal app currently has no candidate restore producer | Full shipping dual-screen/native-game lifecycle still NOT_RUN; no automated import or timestamp winner |
| Android test observer/instrumentation | Only explicit debug acceptance flavor, distinct package/test-only | Absent from ordinary builds; failure fixture cannot affect normal app |

**A — Dormant merge: HELD by the current owner instruction.** The retention failure
alone does not prove these active save changes unsafe, nor does agent inactivity
make them dormant. No new source-level P1/P2 application defect was found by this
investigation. Prior focused review accepted them; scoped physical evidence exists.
An owner could authorize merging dormant candidate code as a separate decision,
keeping registration absent and guarded in CI and accepting the documented native,
lifecycle and durability limits. This investigation does not grant that waiver or
claim all production save paths were exercised. The current policy still holds PR
#188 draft/unmerged until explicitly changed.

**B — Activation: HELD for substantive safety reasons.** Remote prior-data recovery,
actual two-device transfer, transport boundaries and native compatibility remain
unproven. Local retention failed twice by distinct mechanisms. A shipping-manifest
registration change requires its own evidence and explicit owner policy acceptance;
a dormant merge approval would not authorize it.

## Validation and private provenance

No production source changed relative to `fef66e1`. New code is limited to the
acceptance observer/fixtures, runner safeguards/controls, three host boundary tests
and their canonical acceptance-target invocation. Previous API 28/33/34 acceptance
results remain valid within their original scope; this task did not rerun that
entire matrix. Final canonical/focused/build/manifest and exact-head hosted results
are recorded in the PR/issue handoff.

Private directory `/tmp/dualdex-194-controls-04ad1c7` contains exact `commands.json`,
per-operation backup text/status/transport/token/restore reports, archive probes,
all seed and recovered maps, merged manifest and filtered callback log. The final
command records include restored settings and test-package uninstall. Source and
deployed-method diagnostics are in `/tmp/dualdex-194-aosp`.

| Evidence file | SHA-256 |
| --- | --- |
| `environment.json` | `3367fa8a4ad5e9d2877b0a1d48f4a370269cb1b7248c43dd1a82692295f3bb39` |
| `retention-matrix.json` | `ef2b2e302d1d4c7936349c44c252d17ed35b711d125fa8e0dfc25da5679dde7d` |
| `callbacks.log` | `fb70ae6ddc749eef84839bdb6a41d0e9fa33e8684483f2e7a5806ad3adeb035b` |
| `/tmp/dualdex-194-aosp/deployed-cancelFullBackup-oat.txt` | `7143535e0b12ecc2b880d2807abd891064cdbc5db8a01a4dc6290da2a93a04db` |
