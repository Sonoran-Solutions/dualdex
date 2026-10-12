# Quota-Aware Android Cloud Backup Design

Status: design spike only. This document and its host-only selector do not add a production `BackupAgent`, change shipping backup rules, or claim that cloud backup works end to end.

## Problem and current contract

PR #185 (`13d215271df4b58138c708c972a74409289d6bb0`) is merged. The allowlist in [ANDROID_BACKUP_POLICY.md](ANDROID_BACKUP_POLICY.md), `backup_rules.xml`, and `data_extraction_rules.xml` deliberately includes canonical saves, legacy/fallback recovery data, and the exact legacy-assignment preference. It excludes ROM caches, ordinary preferences and secrets, debug output, staging, and unrelated files. Android 12+ already has separate cloud and device-transfer sections; Android 9–11 expresses the split with `clientSideEncryption` and `deviceToDeviceTransfer` require-flags. Preserve the complete current device-transfer set.

The Thor aggregate measured 118,773,282 bytes: essential battery saves and metadata 919,695; states/resume markers 9,512,064; thumbnails 17,153; recovery copies 108,324,370. The measured set is 4.53 times a 25 MiB quota. The source manifest is private and must remain outside the repository. While 919,695 bytes is comfortably below the proposed cloud payload limit, its aggregate alone does not prove that every ROM identity, valid canonical save, and required metadata record was enumerated. Thor selector coverage is therefore **NOT_RUN**; no individual-file simulation is claimed.

Android Auto Backup is best-effort and its cloud quota can vary by transport. XML paths have no wildcard support, so XML cannot pick `battery.sav` from arbitrary SHA-256 directory names. Android 12+ explicitly separates `<cloud-backup>` from `<device-transfer>`, allowing cloud exclusions while retaining local transfer. See the [Auto Backup guide](https://developer.android.com/identity/data/autobackup), [BackupAgent API](https://developer.android.com/reference/android/app/backup/BackupAgent), and [FullBackupDataOutput API](https://developer.android.com/reference/android/app/backup/FullBackupDataOutput).

## Architecture comparison

| Approach | Correctness and security | Complexity / migration | Device transfer and testability |
| --- | --- | --- | --- |
| A. Custom `BackupAgent` selects files directly | Can inspect transport flags and reported quota, enumerate dynamic ROM directories, and stream an allowlisted manifest. Must implement stable reads, restore staging, and all fail-closed checks. | Medium-high. Changes manifest wiring and replaces default selection behavior. Callback lifecycle is restricted; API 28–34 supports full backup and transport flags. Set `android:fullBackupOnly="true"` to keep file-based Auto Backup semantics. XML must not be treated as a second selector once the custom agent owns `onFullBackup()`. | Best separation: cloud path selects compact payload; D2D path submits the existing complete roots. Pure selector and archive codec can be host-tested; actual Android transport still needs disposable-device validation. `onQuotaExceeded()` reports an aborted operation and quota, it is not a retry hook. |
| B. Dedicated compact cloud snapshot | A versioned archive with an explicit manifest, hashes, and exact ROM SHA-256 destinations gives a bounded, inspectable unit. Atomic temp-build then rename means a partial archive is never eligible. A custom agent is still needed to choose this snapshot for cloud but bypass it for D2D. | Medium-high. Adds archive, integrity, stale-source, staging, and cleanup rules. Building on every save write adds disk churn; building only in the backup callback avoids that, but requires agent-controlled selection. | Preserves D2D when agent sends the existing full roots for that transport. Snapshot format and selector are straightforward to test without Android. Restore can stage the entire archive before offering import. |
| C. Separate XML policy only | Android 12+ can exclude state/recovery roots from cloud while retaining them for D2D, but paths cannot select dynamic `battery.sav` files inside `saves_v2/<sha256>/`. Including all of `saves_v2/` remains potentially over quota. Android 9–11 rules lack the same named cloud/D2D sections and conditional include flags cannot express arbitrary per-file selection. | Low initial code, high correctness ceiling: cannot guarantee bounded essential coverage without rearranging canonical data or adding staging/restore behavior. Changing save layout raises migration risk. | D2D remains simple and broad. Cloud is unbounded or omits canonical saves. XML-only behavior is easy to inspect but cannot implement the requested priority policy. |

### Recommendation

Use a **custom file-based `BackupAgent` that constructs one temporary compact snapshot for cloud, and streams the unchanged PR #185 allowlist for device-to-device transfer**. This is A's transport dispatch with B's atomic snapshot as its cloud payload. Keep the selector/codec as pure Kotlin and separate from production save execution. Do not persist a continuously refreshed snapshot; build it during the backup callback from a stable captured file set, verify source stamps again before finalizing, write a completion marker only after hashes and manifest validate, then atomically rename the temporary archive. Delete stale temporary work on the next run. Never modify canonical or historical files.

The manifest should be a bounded versioned JSON or binary header followed by ZIP entries stored without compression (save data is already compact/unpredictable). Each entry records exact lowercase 64-hex ROM SHA-256, category, normalized archive-relative destination, byte size, and SHA-256 content digest. Bound entry count, manifest bytes, path length, total uncompressed bytes, and archive bytes. Reject unknown versions, duplicate destinations, duplicate metadata, unsafe paths, mismatched identity directories, bad hashes, and truncated archives. Never include ROM payloads, ordinary preferences, API keys, logs, or arbitrary files.

For cloud, use `FullBackupDataOutput.getQuota()` when available (API 26+) and a conservative maximum of 20 MiB or 80% of the reported quota, whichever is smaller, less a fixed 256 KiB container/manifest reserve. This leaves room below the documented nominal 25 MB quota and for transport accounting. If no trustworthy positive quota is available, use the 20 MiB cap. If the net payload allowance is non-positive, refuse. `onQuotaExceeded()` only reports that the operation was halted/rolled back; a later attempt can adapt to its reported quota. It must not mark protection successful.

## Deterministic backup priority and outcome

Discover expected identities from valid canonical directory metadata and verified legacy-assignment records. Validate exact ROM SHA-256; titles are display-only. For each expected identity choose exactly one valid canonical `battery.sav`; only if it is absent/invalid may a recovery battery save with an explicit verified identity assignment substitute. Require the matching identity metadata in the same essential bundle. Unknown or ambiguous legacy saves remain excluded and reported as unprotected, never guessed by filename/title.

1. **Tier 1, essential:** one current canonical battery save per known valid ROM identity plus identity metadata. A verified recovery save can cover an identity with no valid canonical file; preserve the source classification and hash.
2. **Tier 2, recovery:** verified legacy battery saves or additional recovery material not needed for Tier 1. Include only after all Tier 1 identities fit.
3. **Tier 3, convenience:** resume/quick/manual states, thumbnails, and further generations, after Tiers 1 and 2.

Selection is deterministic: normalize and validate records, reject duplicate source/destination records, group essential files by full SHA-256, sort identities lexicographically, and test the complete essential set as one unit. If any identity lacks an unambiguous essential save/metadata pair, or the full essential set exceeds the budget, return **REFUSAL**, select no payload, and report every uncovered identity. Do not choose a newest global subset that silently leaves one ROM without its only save. If all essentials fit, include optional files in tier then identity then normalized-path order while each complete file fits; skip oversized optional records and continue to later candidates. Return SUCCESS only if all valid eligible records fit, PARTIAL if all essential saves are covered but optional data was skipped, and REFUSAL if complete essential coverage cannot be achieved. Report exact selected bytes, budget, covered/unprotected identities, and exclusion reasons. Never delete local history to fit.

The prototype in [QuotaBackupSelection.kt](../app/src/test/java/com/dualdex/emulator/QuotaBackupSelection.kt) implements this all-essential-or-refuse rule over synthetic metadata only. Production archive construction must also compare size and modification stamps (and preferably hashes) between selection and copy; any changed source aborts the whole snapshot and requires a later retry. An absent completion marker, malformed manifest, or failed archive hash makes a snapshot unusable.

## Restore contract

The cloud archive is restored to a private pending-import/staging location, never directly into `saves_v2`. Validate archive bounds, format version, complete marker, every entry path, expected directory/hash relationship, file length and SHA-256, metadata identity, and the full set before exposing any import. Path traversal, links, duplicate destinations, malformed metadata, mismatched hashes, and truncation reject the entire archive. Do not overwrite or delete a valid canonical save during backup preparation or restore validation.

After validation, show a user-reviewed import list keyed by full ROM SHA-256 and title hint. A fresh install can retain staged saves before a ROM is configured; selecting the exact same ROM bytes resolves to that identity. Same title with different bytes stays separate; different titles with identical bytes use the same identity. Before import, compare the staged record with the current canonical save. If a newer valid local save exists, require explicit user choice and retain both until the choice is committed. Never silently replace newer valid data. Write accepted content through the existing save integrity/atomic transaction utilities into a new staging target, recheck digest, then commit atomically. Interrupted import leaves canonical files intact and pending content retryable.

Corrupt or unsupported archives are rejected without changing existing saves. Missing optional states are normal. Legacy content is imported only when its exact verified assignment metadata is present; otherwise present it as unassigned and do not associate it. Save states remain version-sensitive: verify compatibility with the current emulator core before offering load, and treat state rejection as non-destructive. Do not automatically restore or load an incompatible state.

## Android-version and transfer contract

| Runtime | Cloud | Device-to-device |
| --- | --- | --- |
| Android 9–11 (API 28–30) | Custom agent checks `FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED`; only then emit the bounded cloud archive. If the flag/quota is unavailable or ambiguous, refuse cloud content. Legacy XML should retain only conservative gating/fallback behavior after agent interaction is verified. | On `FLAG_DEVICE_TO_DEVICE_TRANSFER`, emit the unchanged PR #185 roots: `saves_v2/`, `saves/`, `saves_fallback/`, and `dualdex_legacy_catalog.xml`. Do not apply the cloud cap. |
| Android 12+ (API 31–34) | Same cloud archive and budget. Keep `disableIfNoEncryptionCapabilities="true"`. Do not rely on the default XML path selection after agent ownership is configured. | Same complete roots and catalog. Retain the explicit `<device-transfer>` include list. |

If flags are absent, conflicting, or identify neither cloud nor D2D, fail closed and emit no save payload. No cross-platform/iOS transfer is in scope. Before rollout, verify custom-agent/XML behavior and both transports on API 28–30 and 31–34 disposable profiles. Existing XML and manifest are not changed in this spike.

## Failure handling and security assumptions

- Source files are private app data but can change while a game is running. Capture file identity/length/mtime, open with no-follow semantics where available, copy to private temp files, hash copied bytes, compare final source stamps, and abort on any mutation. A future implementation should coordinate only with a read-safe save snapshot boundary; do not hold the emulator save lock across slow transport I/O.
- The backup callback can run when the app UI and content providers are unavailable. The agent must use only direct file APIs and small pure Kotlin code; no activity, database/provider initialization, network call, user prompt, or app-level singleton.
- The archive is untrusted on restore. Enforce size/count limits before extraction, use allowlisted destinations, reject symlinks and traversal, and stage on the same filesystem for atomic commit.
- ROM identity is exact SHA-256; display names never establish ownership. No ROM bytes are enumerated or serialized.
- Do not claim protection until a successful backup is observed by the platform. Refusal/partial outcomes should be surfaced through safe diagnostic state without logging paths, hashes, save bytes, credentials, or game content.
- Cloud restore requires explicit import confirmation when it can conflict with local data. Device-transfer restore remains platform-driven and must be tested separately; this design does not claim to solve Android's restore ordering or merge semantics for D2D.

## Validation plan and current results

Synthetic selector tests cover one/multiple ROMs; same-title/different-hash identity; large optional state and >100 MB recovery; missing canonical with verified recovery; unassigned legacy; duplicate/bad metadata; traversal and ROM-payload rejection; essential over-quota; exact-budget and one-byte-over; stable input-order results; essential coverage despite optional pressure; interrupted snapshot marker; source mutation; and refusal when full coverage is impossible. The fixtures contain metadata only, not ROM or save contents.

The Thor aggregate implies the essential category total (919,695 bytes) is below 20 MiB, but the protected per-file manifest is not present in the repository and no individual-file coverage test was run. **Thor selector simulation: NOT_RUN.** Issue #12's latest comment records that safe same-signer update checks passed and cloud restore/D2D restore remain NOT_RUN on disposable profiles. This task does not repeat device operations.

Before implementation, validate archive construction/restore using synthetic files and injected interruption/mutation; run the selector tests; inspect APK manifest and rules; then run exact-head CI. Before enabling the feature, test actual cloud quota callbacks and cloud/D2D selection/restoration on disposable Android 9–11 and 12+ devices/profiles. Never test destructive restore using the owner's only save data.

## Owner decisions before production

1. Confirm that a cloud restore must always stage and request confirmation, including a fresh install, or define a less interactive first-install policy.
2. Choose the user-visible location for incomplete/refused backup status; background callbacks cannot prompt directly.
3. Confirm whether a verified legacy save with no canonical file counts as essential coverage (recommended: yes, but label it recovery-derived).
4. Confirm whether a cloud backup refusal should retain the previous successful cloud version (platform rollback is documented on quota excess, but deliberate agent refusal needs transport validation).
5. Approve the 20 MiB/80%-of-reported-quota budget and archive reserve after measurements on supported transports.

## Follow-up implementation issue

Implementation follow-up: [issue #186](https://github.com/Sonoran-Solutions/dualdex/issues/186), linked to #12. It covers the production agent, bounded archive, staged import, and disposable transport validation. Keep #12 open for the remaining device-to-device/cloud restore checks. Do not treat this design spike as a shipped backup feature.
