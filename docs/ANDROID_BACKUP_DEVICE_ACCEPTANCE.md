# Android backup device acceptance — PR #188

Date: 2026-10-13. **HOLD: draft, unmerged, candidate inactive.**

Reviewed starting head: `b8a6ca9c73ed6b8e36918b4d8407f0dd5008e7ee`.
PR base at task start: `5148bf28dc08cdd0f093a9741f420f6f24ca313a`.
The final PR head and exact-head CI are recorded in the PR handoff. Device APK
provenance is recorded separately below; documentation-only commits do not change
an already tested APK. Issues #186 and #12 remain open.

## Real-GMS checkpoint — issue #194, 2026-10-13

**BLOCKED: ACCOUNT AUTHORIZATION.** Pre-emission and post-emission real-GMS
retention are both BLOCKED; no baseline, Android version, selected cloud transport,
cloud callback, dataset token or recovered cloud path/hash has been verified in
this task. No Google account or backup setting was touched. The existing local
retention FAIL and scoped API 28/33/34 evidence are unchanged.

See the [preparation, integration and authorized execution handoff](ANDROID_BACKUP_RETENTION_INVESTIGATION.md#real-gms-preparation-checkpoint--2026-10-13-issue-194)
for the complete separate-account authorization scope, installed SDK/environment
limitations, per-experiment identity/status/hash requirements and stop conditions.
Fresh supported Google Play source/targets and a dedicated authorized account are
still needed. Test-only package rejection must be recorded as BLOCKED; it cannot
be bypassed. The existing API-34 call-2 emission seam requires observation on GMS,
with absent injection classified INVALID_TEST.

Main `7b9fd6f` is integrated via merge `ff902bd`; the sole conflict was the `ci.sh`
usage line. R.O.W.E. fixture/test/sanitizer gates and `backup-acceptance` are
preserved. The local archive probe now refuses an observed token other than `1`;
the original token-1 evidence remains valid. This focused harness correction and
mechanical integration do not change production behavior or grant merge/activation.
Exact-head local/hosted validation and APK/certificate identities are recorded in
the new PR #188 / issue #194 handoff, separately from earlier device runs below.

Dormant merge **Held** (owner policy; ordinary save ordering/fsync/UI changes
remain active and have scoped native/lifecycle/durability limits). BackupAgent
activation **Held** (retention and other acceptance gates unresolved). All six
owner decisions remain pending. Keep PR draft/unmerged, auto-merge disabled,
ordinary agent unregistered and #194/#186/#12 open. Next: authorize/provision
one disposable GMS source/target pair and independently recover baseline A.

## Status meanings and safety

- **PASS**: executed on the identified Android environment, with the stated scope.
- **FAIL**: executed and contradicted the expected result.
- **NOT_RUN**: not executed; no inferred pass.
- **BLOCKED**: a named prerequisite prevented execution.
- **SIMULATED**: injected fault, fake core, local emulation of transport flags, or
  a single-device transfer test; it does not prove remote cloud or real D2D behavior.

Only `com.dualdex.backupacceptance` is installed, cleared, restored or uninstalled
by the runner. It has `android:testOnly=true`, a distinct package/provider namespace,
`fullBackupOnly=true`, and a test-only observing subclass of the candidate agent.
The ordinary main/debug/release manifests are unchanged. The flavor exists only
when explicitly requested; it has no release variant. The existing release
source-isolation check remains intact. No ROM, real save, signing secret, owner
backup account or owner `com.dualdex` storage is used.

On a personal physical device, the runner additionally requires a test APK built
with `DUALDEX_ACCEPTANCE_BACKUP=false`. Transport experiments are refused unless
an emulator serial, `ro.kernel.qemu=1` and explicit `--disposable-avd` match the
actual AVD name. D2D initialization also requires `--allow-transport-reset`.
No screenshots, APKs, game payloads or private device dumps are committed.

## Environments actually established

| Environment | Identity | Scope |
| --- | --- | --- |
| API 28 emulator | `system-images;android-28;default;x86_64`; Android 9 `PSR1.180720.012/4923214`; AVD `dualdex188-api28`, port 5580 | Fresh `/tmp` userdata; real Android storage/UI; legacy backup rules; built-in local transport |
| API 34 emulator | `system-images;android-34;google_apis;x86_64`; Android 14 `UE1A.230829.050/12077443`; AVD `dualdex188-api34`, port 5582 | Fresh `/tmp` userdata; real Android storage/UI; modern extraction rules; AOSP local and GMS D2D test transports |
| AYN Thor, API 33 | Android 13 `TKQ1.231222.001`; build `qti/kalama/kalama:13/TKQ1.231222.001/eng.Thor.20260206.163241:user/release-keys`; USB | Physical app-private storage and production Saves component; isolated no-backup package; no transport/account changes |
| Connected Samsung devices | API 36 and 37 | Outside requested API range; inventory only; no installation or backup changes |

The SDK initially had platforms/build tools 34, 35 and 36, NDK 27.2 and CMake 3.22.1,
but no emulator/system images. KVM and sufficient host storage were available.
The two official images and emulator were installed for this task. No Google
account was added to either emulator.

## Results matrix

| Scenario | API 28 | API 34 | Evidence / limit |
| --- | --- | --- | --- |
| Open directory using `FileChannel.open(READ)` and `force(true)` | PASS | PASS | `storage.json`, `directory-channel-sync`; returned normally on emulator app-private storage |
| Copy staging, atomic replacement, preserve previous-good `.bak` | PASS | PASS | `copy-atomic-replace-and-backup`; exact target/backup digests below |
| Missing/empty recovery rejected; valid recovery usable | PASS | PASS | `missing-invalid-recovery`; synthetic bytes |
| File fsync rejection; failure after rename before directory-sync confirmation | SIMULATED | SIMULATED | Injected on Android: pre-rename failure preserved target; post-rename failure returned false, new target and old `.bak` present |
| Strict restore atomic-move rejection, with no fallback | SIMULATED | SIMULATED | Injected Android move refusal retained original target and local copy |
| Battery flush/import, slots, quick save/load, auto-resume after future barrier | SIMULATED | SIMULATED | Actual Android filesystem and `SaveStateManager`; synthetic core bridge, no native emulator compatibility claim |
| Import with absent/existing battery, no prior barrier, failed marker file/directory sync | SIMULATED | SIMULATED | All four combinations: no candidate SRAM load, battery unchanged/absent, resume bytes unchanged, false result |
| Recreated manager and fresh process retain reviewable sessions | PASS | PASS | `restart.json` after `am force-stop`; file/session persistence, not power-loss proof |
| Missing/late-created/empty/newer destination, same-size/same-mtime mutation | PASS | PASS | Real device `StagedRestoreStore` and actual dialogs; empty-file conflict, late/mutated destination rejected |
| Displaced local retained, ordinary `.bak` unchanged, original staged copy retained | PASS | PASS | `staged.json`; hashes verified |
| Invalid metadata/incomplete session isolated | PASS | PASS | `invalid-and-incomplete-isolation`; no canonical publication |
| Production Saves review without ROM; cancel/confirm replacement and missing import | PASS | PASS | `ui.json`; eight checks through actual `SaveStateScreenView` and production dialogs in isolated activity |
| Activity recreation/reopen dismisses old dialog and keeps restored/retained sessions | PASS | PASS | `ui-recreate-and-reopen-preserves-sessions`; component lifecycle coverage |
| Real loaded-game refusal and incompatible native state | NOT_RUN | NOT_RUN | Fake-core refusal boundaries exercised; no authorized compatible ROM/core fixture |
| Quota-prioritized multi-ROM archive; essentials too large; malformed metadata | SIMULATED | SIMULATED | Direct production helper on Android, bounded archive and negative assertions; not transport proof |
| Same-size/time source mutation and incomplete inventory refusal | SIMULATED | SIMULATED | Test seam/symlink on Android; previous local snapshot preserved before publication |
| Framework callback with absent encryption/D2D flags refuses | PASS | — | API 28 local transport reported flags 0, quota 26,214,400; package failed `-1002` |
| Successful cloud-route callback/emission and staged restore | BLOCKED | SIMULATED | API 28 local transport cannot supply required flag; API 34 local transport used fake encryption flag 1, quota 26,214,400; valid archive restored to staging |
| Preserve previous successful local-transport dataset after refusal | BLOCKED | **FAIL** | API 34: first restore had 8 entries; after agent refusal, same-token restore had 0; [issue #194](https://github.com/Sonoran-Solutions/dualdex/issues/194) |
| Preserve previous successful **remote cloud** dataset after refusal | NOT_RUN | NOT_RUN | No separately authorized disposable cloud account/dataset; local behavior cannot establish GMS remote behavior |
| GMS D2D single-device test-mode backup/restore | NOT_RUN | SIMULATED | Actual GMS transport callbacks, flags 2, quota `Long.MAX_VALUE`; documented reinstall restored all 14 admitted files by hash, canonical battery absent |
| Real source-to-target D2D migration | NOT_RUN | NOT_RUN | No real two-device setup-wizard transfer executed |
| Directory headers through real transport | NOT_RUN | NOT_RUN | Observed archive and 14-file restores contained file callbacks only; independent directory counters remain host-tested |
| Actual transport at 16,384 files / 4 GiB file / 16 GiB set boundaries | NOT_RUN | NOT_RUN | Logic remains host-verified; no enormous payload or storage exhaustion attempted |
| Actual `onQuotaExceeded`, hard low-storage and abrupt power loss | NOT_RUN | NOT_RUN | No controlled transport quota/storage/power-loss fixture; exceptions are not physical durability evidence |
| Shipping activity, second display, real native state compatibility | NOT_RUN | NOT_RUN | Production Saves component tested in harness; full handheld/session behavior still needs Thor/device QA |

## Physical Thor follow-up — API 33

Wireless connection timed out; the owner then connected the Thor by USB. On clean
head `bbbd207f55a15b08d6e72c35ec11d74378abb3ee`, the same committed harness ran with
`--transport none --cleanup` and `sourceDirty=false`. The final APK manifest was
verified before installation: `com.dualdex.backupacceptance`, test-only,
`allowBackup=false`. All 29 scoped outcomes passed (12 storage, 4 staged, 3 cloud
helper, 8 UI, 2 restart). No physical backup callback or transport was invoked.

| Thor scenario | Status | Observed result / limit |
| --- | --- | --- |
| Directory `FileChannel` sync; atomic replacement and recovery | PASS | Sync returned normally; target became byte-9 digest, previous-good copy stayed byte-3; invalid recovery refused |
| Existing/missing/empty/newer/mutated/late-created destinations | PASS | Actual staged store and eight production dialog checks; canceled/conflicting imports preserved local data; explicit replacement retained local copy |
| Activity recreation/reopen and fresh-process review | PASS | Dialog lifecycle checks passed; three reviewable sessions and their exact identities/digests survived `am force-stop` |
| Timestamp precision | PASS | Requested `1700000000123` read back as `1700000000000`; whole-second ordering correction also applies on this physical filesystem |
| Battery/core and four barrier-publication failures | SIMULATED | Real Android storage, fake core and injected sync failures; candidate SRAM loads stayed zero on failed protection, old resume bytes and canonical batteries stayed intact |
| Atomic rename/fsync failure, cloud selection/mutation/inventory refusal | SIMULATED | Injected failures and direct helpers on device; no native compatibility, actual transport or power-loss claim |
| Physical cloud/D2D, full shipping dual-display lifecycle, real core compatibility, power loss/low storage | NOT_RUN | Isolated component QA only; automatic backup disabled; no owner save or account accessed |

Private reports: `/tmp/dualdex-188-thor-bbbd207/{environment.json,storage.json,staged.json,cloud.json,ui.json,restart.json,commands.json}`.
The runner uninstalled only the test package after success; a subsequent package
query confirmed it absent. No device-wide backup settings were changed.

## Issue #194 controlled follow-up

The [retention investigation](ANDROID_BACKUP_RETENTION_INVESTIGATION.md) preserves
the original FAIL and adds bounded controls on a new, explicitly disposable API 34
AVD. Repeated restore/process/clear controls passed; successful B replaced A under
the same token. Pre-emission refusal deleted A before recovery. A test-only
post-emission IOException was instead reported as package Success and committed a
truncated archive that restore validation rejected. Both retention outcomes are
**FAIL**; real GMS cloud retention remains **NOT_RUN**. No production code changed.
The linked analysis separates dormant-merge policy from activation readiness and
provides the separately authorized real-cloud protocol. #194 remains open.

## Two reproducible source corrections

1. **API 28 timestamp precision.** A requested file time `1700000000123` read
   back as `1700000000000` through both File and NIO. The former `latest + 1 ms`
   ordering could not survive publication, so barrier/import/live-state writes
   failed. `SaveStateFiles.nextStateTimestamp` now rounds upward to a whole second,
   still strictly above all old states/barriers, and fails closed on overflow.
   Ordering times may advance ahead of wall time by a second per rapid write;
   they are safety ordering values, not a claim about actual play time.
2. **API 34 inventory/archive timestamp mismatch.** On one unchanged source,
   File reported `1791929095063`, NIO attributes `1791929095000`. Inventory stamps
   used NIO, while archive preflight/rechecks used File, refusing a valid archive.
   Inventory capture and recheck now use the same File timestamp view as the
   archive. Source content hashes, size checks and final rechecks remain required.

The isolated Android harness reproduces both boundaries. Host regressions live
in `SaveStates153Test` and `CloudBackupFailureTest`. Directory sync itself succeeded
on both emulator filesystems; no change to that primitive was needed here.

## Critical failed local-transport retention experiment

Transport: `com.android.localtransport/.LocalTransport`, API 34, no cloud account.
`backup_local_transport_parameters=is_encrypted=true` supplies a **fake encryption
flag**. This is Android framework/local-storage evidence, not a remote cloud test.

1. Seed two synthetic identities and valid metadata, states and thumbnails.
2. `bmgr backupnow com.dualdex.backupacceptance` returned package Success;
   agent `onFullBackup(flags=1, quota=26214400)` returned normally.
3. Clear only the test package and restore the listed token. One archive file
   callback (791,490 bytes), then `onRestoreFinished`; review exposed 8 hash-valid
   entries, with canonical battery still missing.
4. Reseed and make one metadata JSON malformed. The agent threw `IOException`
   (`Essential save coverage unavailable`); package status was `-1002`, reported
   as "Transport rejected package because it wasn't able to process it at the time".
5. Clear only the test package and restore the **same** token. Framework restore
   returned 0 but invoked no file callback and exposed **zero** staged entries.

`bmgr` printed aggregate "Backup finished with result: Success" despite the
package error. The runner therefore checks the **package result and recovered
hashes**, never the aggregate line. Throwing did abort the app operation, but
it did not preserve this transport's older dataset. Stop this retention path;
resolve [issue #194](https://github.com/Sonoran-Solutions/dualdex/issues/194) and verify an authorized real cloud transport before
making any previous-version-preservation or activation claim.

Diagnostic references (private, outside repo):
`/tmp/dualdex-188-api34-local-a181239/{commands.json,callbacks.log,backup-valid.txt,backup-refused.txt,baseline.json,after-refusal.json,transport-result.json}`.
The failed assertion is intentional evidence, not a test to weaken.

## Synthetic hashes and observed callback scope

| Bytes / role | SHA-256 |
| --- | --- |
| 131,072 copies of byte 3: original battery, prior-good copy | `aaf6a8ab7f17b35353821ff0e7bf75abc68f963b2db36c3a1ddb27e382037c89` |
| 131,072 copies of byte 9: replacement battery | `d693d44d8478475adfae30336c111ec5bc69f61ea934b46d66857c1b3ea81607` |
| 262,144 copies of byte 5: opaque synthetic state | `89cc4aac7a40201f83493c2e208705c1c049f9041c5815a8c2a270b0b7d642e2` |
| Three-byte thumbnail fixture | `039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81` |

Original target → successful copy: byte-3 digest → byte-9 digest; `.bak` stayed
byte-3. Injected file-fsync failure: target stayed byte-3. Injected final directory
sync failure: API returned false, target was byte-9, `.bak` byte-3. Protective marker
failures never loaded candidate SRAM or changed a battery.

The two ROM identities are 64 repeated `1` and `2` characters, never real ROM hashes.
Metadata bytes have generated dates; their exact per-run hashes and APK hash are
in the private reports. D2D's 14-file set includes both batteries, metadata,
states, thumbnails, `.tmp`, `.bak`, both legacy roots and the exact catalog.
ROM-cache/log/preference sentinels were absent from the restored set; catalog
remained reference-only, legacy files unassigned, and canonical saves absent.
The GMS test-mode stream generated two full-backup callbacks,
14 `TYPE_FILE` callbacks and one finish callback, with no `TYPE_DIRECTORY` callback.
The callback count alone does not assert what any other transport emits.

## Reproduce safely

Use the canonical build target, not an ad hoc Gradle command:

```bash
./ci.sh backup-acceptance
python3 tools/android-backup/acceptance.py --serial emulator-5580 \
  --output /tmp/dualdex-188-api28 --transport none
python3 tools/android-backup/acceptance.py --serial emulator-5582 \
  --output /tmp/dualdex-188-api34 --transport local --disposable-avd dualdex188-api34
python3 tools/android-backup/acceptance.py --serial emulator-5582 \
  --output /tmp/dualdex-188-d2d --transport d2d-test --baseline-only \
  --disposable-avd dualdex188-api34 --allow-transport-reset
```

The API 34 `--transport local` retention run is expected to exit nonzero on the
observed image. `--baseline-only` tests only a successful round trip. The GMS D2D
mode follows the documented test-mode uninstall/select-GMS/reinstall flow;
direct `bmgr restore` against D2dTransport returned `-1000` before app callbacks
and is not the working procedure. No local copying or direct restore-callback
invocation is substituted for these observations.

To repeat Thor storage/UI testing with an explicitly selected USB serial:

```bash
DUALDEX_ACCEPTANCE_BACKUP=false ./ci.sh backup-acceptance
python3 tools/android-backup/acceptance.py --serial THOR_SERIAL \
  --output /tmp/dualdex-188-thor --transport none --cleanup
```

The runner checks the final APK manifest before installation: exact package,
test-only marker, observing agent, full-backup-only mode and instrumentation target.
A physical device's manifest must also have `allowBackup=false`. It records
commands, API/fingerprint, source revision/dirty state, APK digest and per-scenario
reports. Raw diagnostics/screenshots remain private. Review `environment.json`;
a dirty-tree run is exploratory and must not be described as an exact committed-head run.

### Create the same disposable emulators

```bash
sdkmanager --install emulator 'system-images;android-28;default;x86_64' \
  'system-images;android-34;google_apis;x86_64'
mkdir -p /tmp/dualdex-188-avd
printf 'no\n' | ANDROID_AVD_HOME=/tmp/dualdex-188-avd avdmanager create avd \
  --name dualdex188-api28 --package 'system-images;android-28;default;x86_64' \
  --path /tmp/dualdex-188-avd/api28
printf 'no\n' | ANDROID_AVD_HOME=/tmp/dualdex-188-avd avdmanager create avd \
  --name dualdex188-api34 --package 'system-images;android-34;google_apis;x86_64' \
  --path /tmp/dualdex-188-avd/api34
ANDROID_AVD_HOME=/tmp/dualdex-188-avd emulator -avd dualdex188-api28 \
  -port 5580 -no-window -no-audio -gpu swiftshader_indirect -no-snapshot -memory 2048
# In another terminal, same options with dualdex188-api34 and port 5582.
```

Wait for `adb -s emulator-5580 shell getprop sys.boot_completed` (and 5582) to
return 1. Use explicit serials for every command. Restore transport settings in
`finally`; the runner does this even on failure. Add `--cleanup` to uninstall only
the synthetic package. Stop emulators with `adb -s emulator-5580 emu kill` and
5582, then delete only `/tmp/dualdex-188-avd`. SDK images can remain installed.
Do not run transport initialization/wipe commands on an owner profile.

### Outstanding real-cloud and two-device protocol

Use a separately authorized disposable account/profile and the verified test APK.
Do not transplant these commands onto a personal backup dataset. Follow the
[Android backup testing guide](https://developer.android.com/identity/data/testingbackup)
for the selected transport; capture its exact package result, flags, token and
callback log. Seed and record all admitted file digests, create a valid backup,
restore and verify staging, then change synthetic data and inject malformed
metadata, missing essentials or a controlled source failure. Restore the prior
token and compare **all** prior digests. A normal return, exception, local archive,
aggregate `bmgr` success or mocked flag is insufficient. On refusal, record the
package failure independently from dataset recoverability.

For real D2D, use two fresh devices/profiles and the actual setup-wizard transfer.
Compare every admitted path/digest and the excluded sentinels after target restore;
record directory callbacks and repeat review/conflict/lifecycle checks. Exercise
supported quota, max-count and storage-failure boundaries without owner data.
Save-state/native compatibility needs a separately authorized legal fixture;
byte length, fake-core acceptance and storage hashes do not prove compatibility.
Power-loss/storage-controller durability requires a controlled physical-device
fixture. Neither exception injection nor emulator restart establishes it.

## Owner decision record — all six are PENDING

| Decision | Implemented default | Evidence and tradeoff | Recommended owner ruling |
| --- | --- | --- | --- |
| Confirmation on fresh/missing-destination import | Always staged; explicit Import file | API 28/34 UI passed; extra recovery step, prevents unattended replacement/assignment | Accept explicit confirmation, including fresh install |
| Location for refused/incomplete status | Saves screen status sourced from `BackupStatusStore`; background callback cannot prompt | Device callbacks refuse; status describes selection/refusal, not verified upload protection | Accept Saves as initial location; review visibility on Thor before activation |
| Verified recovery as essential coverage | Only exact, hash-verified portable legacy/recovery bindings can satisfy coverage; unassigned legacy makes coverage incomplete | Host policy tests; full-tree device recovery preservation passed; avoids title-based guesses, may refuse more backups | Accept verified recovery as essential, visibly recovery-derived; keep unassigned refusal |
| Previous successful cloud backup preservation | Throw on refusal/incomplete operation; no app-controlled remote version guarantee | Local-transport retention **failed**; actual remote transport NOT_RUN | Require prior cloud version recoverability; do not accept or activate until proved or policy/design revised explicitly |
| Cloud budget/reserve | min(20 MiB, 80% reported positive quota), minus 256 KiB reserve; invalid/nonpositive budget refuses | Android helper bounded selection passed; actual tested local quota 25 MiB, no real cloud measurement | Retain provisional budget; require supported real transport measurement before final acceptance |
| Ordinary cloud/D2D full-tree restore review | Validate/stage all; confirm missing destinations; explicit replacement and retained local for every existing file | API 28/34 conflicts/UI and GMS single-device full-tree staging passed; extra review/storage cost | Accept strict staging and per-file decisions, no automatic date winner or title assignment |

No ruling above has been approved on the owner's behalf. Device test passes do
not imply policy acceptance. The failed retention gate needs resolution first.

## Validation and activation recommendation

Canonical host, focused regression, test APK, ordinary-manifest and exact-head
hosted results are recorded in the final PR handoff. The clean committed-harness rerun used `a181239aa2a13bc7196b68eef075481a28856a7d`
with `sourceDirty=false` on API 28 and both API 34 transport runs. Each API passed
29 storage/core-boundary/conflict/UI/restart scenario outcomes (classifications
above still apply). API 34 GMS D2D test mode restored all 14 admitted paths with
exact source digests. The local-transport retention command correctly exited
nonzero with `previousDatasetRetention=FAIL`, 8 baseline entries and 0 after refusal.

Transport APK SHA-256:
`4c61f02fff4738146f5d05bc88307cbcbf2af582585a7cc637fab9d14a7f53a3`.
The no-backup physical APK was built and its merged manifest verified
(`allowBackup=false`, test-only, distinct package), SHA-256
`45011f3c9332e62cd4cc5e73c76a3a62a03ba19fd3b581ceba5b226b370460dd`;
it was installed for the successful USB Thor run above, then removed by cleanup.

`./ci.sh all` passed, including 1,451 JVM tests and the debug APK. All 58 focused
regressions passed: cloud failures 7, file-tree policy 14, save-state safety 16,
staged restore 21. The actual ordinary debug APK manifest was inspected:
`package=com.dualdex`, backup agent absent. The runner's negative controls rejected
the ordinary APK before any ADB command and rejected Python optimization that
could disable its assertions. The final source-check and exact final-head hosted
status are in the PR handoff; they do not change failed/device/owner gates.

Keep PR #188 draft, unmerged and without auto-merge; keep the shipping agent
unregistered. Next: review the two measured timestamp corrections and scoped
retention failure, verify an authorized real cloud transport, complete shipping
handheld lifecycle and real two-device/native compatibility QA, then request
explicit owner rulings.


### Private evidence integrity references

These are synthetic reports and filtered logs, not game data or full device dumps.
Their containing directories are permission-restricted. Retain them privately or
rerun the committed procedure; no APK/raw storage/screenshot is committed.

| Private evidence file | SHA-256 |
| --- | --- |
| `/tmp/dualdex-188-api28-a181239/environment.json` | `0d9e58fb2108fed2e24d3aa1a5dcccf736951191e2b390a428a905d42b6a24f4` |
| `/tmp/dualdex-188-api28-a181239/storage.json` | `3a4f745545525a3420eae28364fca7d5403200d1ad10114e9b17b5e9356b242c` |
| `/tmp/dualdex-188-api28-a181239/ui.json` | `91464ac7bcf66fa40358cbb432b31dc6378c8554c96baa59d5b79939dab82201` |
| `/tmp/dualdex-188-api34-local-a181239/environment.json` | `806388c4270d045358d37572a03c0ac41ad939f00b78dc04cdea3d7c43016af6` |
| `/tmp/dualdex-188-api34-local-a181239/callbacks.log` | `6e3a8d0d9476908511b01c99fb321ac567add04f7800a8f75dddf141b30ca96b` |
| `/tmp/dualdex-188-api34-local-a181239/baseline.json` | `2b8aaf5b7b3973161315caa3b1d0e5707cdccb171a42d3c4bdc91b1f17efaf25` |
| `/tmp/dualdex-188-api34-local-a181239/after-refusal.json` | `ad18a0cd3e3a857e7a298890450494e0c7525e284a1f4b92c1dcb006b6fd3d67` |
| `/tmp/dualdex-188-api34-local-a181239/transport-result.json` | `da3feee0a6c16bd6ca943391ef7e84d3c4328fe9767a238fa008460450af2220` |
| `/tmp/dualdex-188-api34-d2d-a181239/callbacks.log` | `7e23c6a3c8a53d8e8aa1fb7b551129d6cfcf0f6471808ead0159b44b88957ef6` |
| `/tmp/dualdex-188-api34-d2d-a181239/transport-result.json` | `685172add5910bb51e50ec8cbef7e531be8a1d22bab7b07218e1c48eba9076a4` |
| `/tmp/dualdex-188-thor-bbbd207/environment.json` | `46b78c19a1e5d9cd55a3a803ff4d5a2b66d0cb118c33711ceb6c98f757985a34` |
| `/tmp/dualdex-188-thor-bbbd207/storage.json` | `2cc9ccdc0090e73ced158f96152d8ca5767974ac52aac1b9736a8cf023430a0d` |
| `/tmp/dualdex-188-thor-bbbd207/ui.json` | `0e4903478dfba0f18b2444ade09cb86be626219c1da5c0a78ffd2e91ef9e3416` |
| `/tmp/dualdex-188-thor-bbbd207/restart.json` | `7674700ff00ea846e32e6fae693ab1874a91c943b2d7823773ed60810cf9593d` |
