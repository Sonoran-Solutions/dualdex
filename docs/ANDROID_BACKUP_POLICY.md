# Android Backup and Device-Transfer Policy

DualDex uses an explicit Android backup allowlist. The allowlist preserves app-private saves and migration recovery data while leaving ROM bytes, credentials, debug data, caches, and ordinary preferences out of automatic transfer. It is implemented by `app/src/main/res/xml/data_extraction_rules.xml` for Android 12+ and `app/src/main/res/xml/backup_rules.xml` for Android 11 and earlier.

## Storage inventory

| App-owned storage | Purpose and classification | Automatic policy |
| --- | --- | --- |
| `filesDir/saves_v2/<sha256>/` | Essential canonical battery saves, states, thumbnails, identity/profile metadata, and `.bak` recovery copies. User data; identity is portable because the directory key is the ROM SHA-256. Not a ROM payload. | Included in cloud backup when encryption is supported and in device transfer. |
| `filesDir/saves/` | Recoverable legacy `.sav` and `.state` files, including original and migrated copies. | Included in both policies. |
| `filesDir/saves_fallback/` | Recoverable legacy and old-identity migration backups. | Included in both policies. |
| `sharedpref/dualdex_legacy_catalog.xml` | Source-path-to-ROM-hash assignments used to prevent a legacy save from being silently assigned to another ROM. This is migration integrity metadata, not a convenience setting or credential. | Included as one exact SharedPreferences file. |
| `filesDir/save_staging/` | Temporary save transactions, import candidates, and rollback staging. | Excluded because it is outside the allowlist. |
| `filesDir/rom_cache/` | Cached ROMs named by hash (`.gba`) and incoming ROM copies (`incoming_*.tmp`). Copyrighted ROM payloads and temporary copies. | Excluded because it is outside the allowlist. |
| `filesDir/frontend_roms/` | ROM copies received from front-end launchers when the source cannot be read directly. Copyrighted ROM payloads. | Excluded because it is outside the allowlist. |
| `filesDir/library_scan_cache.json` | Regenerable ROM scan metadata. | Excluded. |
| `filesDir/covers/` | Regenerable cover-art cache. | Excluded. |
| `filesDir/hns-coverage/log.json` (debug build) | Debug calculator observations. | Excluded. |
| `sharedpref/dualdex_settings.xml` | Controller, display, resume, folder URI, last-played URI, and other local settings. Retired Gemini key/model names are removed when settings initialize. | Excluded, including any legacy Gemini values still present before initialization. |
| `sharedpref/dualdex_library.xml` | Library preferences and the SteamGridDB API key; also contains folder URIs, custom names, and per-ROM flags. The key is sensitive; the rest is convenience/device-specific state. | Excluded in full. The key is never transferred by Android backup. |
| `sharedpref/dualdex_cheats.xml` | Local cheat preferences. | Excluded. |
| `cacheDir/` | Temporary updater APKs, debug exports, and other downloads/exports. | Android excludes cache directories from Auto Backup; no cache path is allowlisted. |
| User-selected SAF folders | Optional save mirror/export and ROM locations outside app-private storage. Their contents are not app-private backup inputs; the selected URIs and grants are in excluded preferences. | Existing external files are not changed. Users may need to reselect folders and regrant access on a new device. |

The three save directories contain the existing save architecture's data: `battery.sav`, save states and resume markers, per-ROM `metadata.json`, thumbnails, and prior-good `.bak` files. `SaveStateManager` writes transaction staging under the separate `save_staging` directory. `AtomicSaveFile` may also leave a same-directory `<target>.tmp` after an abrupt interruption; because the allowlisted save roots are directory-level rules and ROM identities are dynamic, Android's XML cannot exclude those individual names without unsupported wildcards. Normal successful writes remove the temporary file, and interrupted-write recovery handles it on later access. Changes that put non-save or ROM content inside an allowlisted save root require a policy review.

All other app files, databases, ordinary preferences, device-protected storage, and app external-files paths are not implicitly included. An include rule changes Android's behavior to include only the named paths; these rules do not depend on a denylist staying complete.

## Android version behavior

- Android 12+ uses `dataExtractionRules`. Cloud backup and device-to-device transfer have separate, matching allowlists; omitting one mode's section would leave that mode at Android's default behavior. Cloud backup is disabled when the transport cannot provide encryption. Device transfer remains eligible because it is a separate local transfer mode.
- Android 11 and earlier use `fullBackupContent`. Since the app's minimum Android version is API 28, the legacy rules use `requireFlags` to gate cloud includes on `clientSideEncryption` and provide separate `deviceToDeviceTransfer` includes. These are alternative entries for each allowlisted path, so a local transfer remains eligible without the cloud-encryption flag.
- `android:allowBackup` remains `true`; the XML allowlists define the data boundary. Android documents that `allowBackup="false"` does not disable device-to-device transfer consistently on every manufacturer.
- DualDex does not configure cross-platform transfer. It has no iOS companion target or platform-specific transfer identity; adding one would require its own explicit rules and review.

Android Auto Backup is best-effort. The platform documents a 25 MB per-app cloud quota, stores only the latest cloud backup, and runs backups only when device/account settings and runtime conditions permit. Large save states or legacy recovery copies can exceed that quota, and a device may never complete a backup. Keep independent save exports; automatic backup is not a replacement for an explicit export. See the [Android Auto Backup guide](https://developer.android.com/identity/data/autobackup) and [backup best practices](https://developer.android.com/privacy-and-security/risks/backup-best-practices).

### Thor cloud-quota measurement (2026-10-12)

The preserved pre-update Thor manifest contains 65 files matched by the current allowlist, totaling **118,773,282 bytes (113.271 MiB)**. That is **4.53× a 25 MiB quota**, so this dataset is over quota and Android Auto Backup will not write it to cloud storage. This is an observed size for the Thor dataset, not a guarantee about other installations.

| Eligible category | Files | Bytes | MiB |
| --- | ---: | ---: | ---: |
| Primary battery saves and essential metadata | 13 | 919,695 | 0.877 |
| Save states and resume markers | 18 | 9,512,064 | 9.071 |
| Thumbnails | 3 | 17,153 | 0.016 |
| Recovery copies, including fallback and prior-generation saves | 31 | 108,324,370 | 103.306 |
| **Total** | **65** | **118,773,282** | **113.271** |

The primary battery/metadata set fits under the quota on this device, but the existing XML cannot select those filenames across arbitrary ROM-hash directories. A separate quota-aware design should define a bounded cloud snapshot or custom backup agent and retain the broader device-transfer set; it must account for installations whose primary saves alone exceed the quota. The local-transfer policy remains useful for this dataset. Measurement used the protected pre-update per-file manifest (`ed63e0aed3502afd6b5f39da84a671a4bb7f7338e7cd368f8e1a7691f3c9a929`); that manifest stays outside the repository with the device backup because it contains user-data paths and hashes.

The follow-up design spike and host-only policy prototype are documented in [ANDROID_CLOUD_BACKUP_DESIGN.md](ANDROID_CLOUD_BACKUP_DESIGN.md). They do not change the shipping rules above.

## Restore behavior and limits

Restore does not require the original ROM to be present: save files and metadata remain in the restored hash-keyed directory. DualDex loads a battery save only after a ROM is selected and computes that ROM's SHA-256. Selecting the same bytes resolves to the same identity; ROMs with similar names but different bytes have separate directories. If the ROM is absent, the save remains stored but cannot be loaded yet. Legacy candidates remain available for explicit review/migration; the included catalog preserves assignments when present.

On load, the existing save code recovers interrupted atomic writes, rejects missing/empty or wrong-size states, and honors the resume barrier and battery-save timestamp so an older resume state cannot roll back a newer battery save. The native core can still reject an otherwise size-valid state; no restore engine or cross-version state compatibility is promised. Existing fail-closed save switching, save identity, import/export, and migration behavior are unchanged by this policy.

### AYN Thor validation (2026-10-12)

Safe update checks were run on an AYN Thor running Android 13. The installed `com.dualdex` app was `0.9.0-dev` (versionCode 1); its signer matched the candidate debug APK. An in-place `adb install -r` succeeded without clearing app data. Before installing, the app-private save, state, recovery, staging, and legacy-catalog data (83 files) and the installed APK were copied to permission-restricted local storage, with per-file SHA-256 digests recorded. All 83 private-data digests matched immediately after the update.

After loading the existing ROM, all 17 battery/legacy `.sav` files and all 30 `.state` files remained byte-identical. The app refreshed two active-ROM metadata files during normal startup (`mirrorStatus`, generation, and `updatedAt`); both remained present and valid. Logs confirmed the existing battery save and `auto_resume.state` loaded under the same per-ROM identity. The game rendered, the companion presentation appeared on the second display, and its Team tab showed live party data. The installed APK's manifest and both extracted rule resources contained the expected allowlists.

The app-selected SAF mirror was also found (5 `.sav`, 7 `.state`, and 1 metadata file) and copied to protected local storage. Its device-side modification times predate the app update, but a separate pre-install digest baseline was not recorded for this external folder. Cloud backup restore and device-to-device restore remain **NOT_RUN**; XML, host tests, and a same-device update do not verify Android transport behavior. Keep issue #12 open until those restores are exercised in a disposable device/profile. Do not use an owner's only save data for a destructive restore experiment.

## Assistant retirement

Issue #12 originally also requested Gemini credential storage, model selection, and Assistant network disclosure. The Assistant was removed from the app in PR #182; those requirements are retired and are not rebuilt here. The only applicable issue #12 scope is Android backup and device-transfer policy.

## Quota-aware backup implementation candidate (2026-10-12)

A production selector, filesystem inventory, and bounded cloud snapshot codec are under review in issue #186. The selector enforces the proposed 20 MiB cap, a 256 KiB reserve, explicit zero-quota refusal, bounded entries/paths, complete essential coverage, and checked aggregate accounting. The inventory walks only `saves_v2/`, `saves/`, and `saves_fallback/`; legacy battery data requires a portable source path, exact ROM identity, and matching content digest. The archive is deterministic, versioned, validates category/path/identity and metadata/battery relationships, checks the selected source set again after copying all entries, and atomically publishes only after validation.

**The candidate `QuotaAwareBackupAgent` is not registered in the normal manifest.** Existing Android XML allowlists remain active, so system backup eligibility is best-effort and does not mean that an upload completed. D2D candidate emission walks and emits each regular file under the same three approved save roots used by restore validation, plus the exact legacy catalog file. It refuses incomplete walks, symlinks, and unexpected filesystem entries. The D2D sender preflights the complete inventory with the same admission check used by restore staging: nonempty files, at most 4 GiB per file, 16 GiB in total (including the catalog), 16,384 files, and a 1 MiB legacy catalog. Canonical `saves_v2/<hash>/metadata.json` (including nested metadata under that identity) is also capped at 1 MiB and validated by the same bounded JSON parser and case-insensitive ROM-hash comparison during sender preflight and staged restore validation. Malformed JSON, missing hashes, and mismatched identities refuse the transfer without modifying local saves. A rejected entry refuses the transfer before emission; sizes and metadata contents are rechecked immediately before each file is emitted. Nonempty `.tmp`, `.bak`, `.orig.bak`, and `.migrated.bak` files are retained for staged review under these bounds; empty copies refuse the transfer rather than being silently dropped. ROM payload extensions (`.gba`, `.agb`, `.gb`, `.gbc`, `.nds`, case insensitive), including temporary and recovery suffixes, are rejected in every save root by both sender and receiver. This extension check is defense in depth, not ROM content detection. Cloud archives remain capped at 20 MiB. Restore callbacks stage files without writing canonical saves. Saves → Review restored saves now supports explicit per-file import after closing the game. Every existing destination requires explicit replacement with a retained local copy; review-time hashes and dates are checked again at commit. Original backups stay staged, legacy catalogs remain reference-only, and imported battery/state files suppress automatic state resume. See the staged restore contract in [ANDROID_CLOUD_BACKUP_DESIGN.md](ANDROID_CLOUD_BACKUP_DESIGN.md). Host tests cover actual enumerated emission paths, recovery files, oversized D2D entries, archive ownership claims, and source mutation during snapshot construction. No transport selection, cloud quota, or device-transfer behavior is claimed as verified by this candidate. It must not be enabled until device validation of review/import, atomic storage behavior, disposable API 28–34 transports, and owner acceptance are complete. Keep manual save exports.
