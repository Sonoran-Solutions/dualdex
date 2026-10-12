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
- Android 11 and earlier use `fullBackupContent`. That legacy format applies the same allowlist to the available backup/transfer operation; it cannot express the Android 12+ cloud-versus-device-transfer distinction.
- `android:allowBackup` remains `true`; the XML allowlists define the data boundary. Android documents that `allowBackup="false"` does not disable device-to-device transfer consistently on every manufacturer.
- DualDex does not configure cross-platform transfer. It has no iOS companion target or platform-specific transfer identity; adding one would require its own explicit rules and review.

Android Auto Backup is best-effort. The platform documents a 25 MB per-app cloud quota, stores only the latest cloud backup, and runs backups only when device/account settings and runtime conditions permit. Large save states or legacy recovery copies can exceed that quota, and a device may never complete a backup. Keep independent save exports; automatic backup is not a replacement for an explicit export. See the [Android Auto Backup guide](https://developer.android.com/identity/data/autobackup) and [backup best practices](https://developer.android.com/privacy-and-security/risks/backup-best-practices).

## Restore behavior and limits

Restore does not require the original ROM to be present: save files and metadata remain in the restored hash-keyed directory. DualDex loads a battery save only after a ROM is selected and computes that ROM's SHA-256. Selecting the same bytes resolves to the same identity; ROMs with similar names but different bytes have separate directories. If the ROM is absent, the save remains stored but cannot be loaded yet. Legacy candidates remain available for explicit review/migration; the included catalog preserves assignments when present.

On load, the existing save code recovers interrupted atomic writes, rejects missing/empty or wrong-size states, and honors the resume barrier and battery-save timestamp so an older resume state cannot roll back a newer battery save. The native core can still reject an otherwise size-valid state; no restore engine or cross-version state compatibility is promised. Existing fail-closed save switching, save identity, import/export, and migration behavior are unchanged by this policy.

No connected AYN Thor was available during this change, so installation compatibility, byte-for-byte device save preservation, and real device-transfer restore are **NOT_RUN**. XML parsing, host tests, and a merged-manifest build do not verify Android transport behavior. Do not use an owner's only save data for a destructive restore experiment; use a disposable device/profile for cloud-restore and transfer validation.

## Assistant retirement

Issue #12 originally also requested Gemini credential storage, model selection, and Assistant network disclosure. The Assistant was removed from the app in PR #182; those requirements are retired and are not rebuilt here. The only applicable issue #12 scope is Android backup and device-transfer policy.
