# Presentation Dialog Audit (#178)

## Cause and handling

On Android 12+, `CompanionPresentation` supplies a display-bound window context with type `2037`. A plain `android.app.AlertDialog.Builder.show()` creates its window with `TYPE_APPLICATION` (`2`), so Android rejects the mismatch and throws `IllegalArgumentException` from `Dialog.show()`. The exception kills the shared app process, including the emulator.

The window-type helper and existing Cheats/Home/Updater routing were already present in commit `bd23c4a`; this PR reuses them rather than rebuilding that fix. `DualDexComponents.showDialog()` keeps the default window type on the primary display and sets `2037` on non-default displays before showing the dialog. The `Context.display` lookup is guarded for API 30+, where that API was added. The helper can also bind a dialog to its owning view: it dismisses on detach and runs a cancellation callback. `DualDexComponents.confirm()` uses this behavior and a one-shot action guard. Companion UI uses platform `android.app.AlertDialog`; AppCompat builders do not pass through the shared helper.

## Call-site audit

| Call site | Presentation reachable? | Finding | Status |
|---|---:|---|---|
| Cheats: Add, Delete, Load Presets (`CheatsScreenView`) | Yes, More → Cheats | These already used the shared platform helper. Editors and confirmations now close when Cheats detaches; callbacks run once and only while the same exact active ROM and Cheats screen remain current. Cancel, Back, outside cancel, and detach consume the action without mutation. | FIXED |
| Library confirmations and editors (`HomeScreenView`) | Yes, Library | Unsupported-ROM confirmation, rename, game actions, API-key prompt, and ROM info use the shared helper and bind to the Home view. They dismiss on navigation or Presentation dismissal. | SAFE |
| Update prompt (`AppUpdater`) | Yes, from Library | Already used the shared helper. The asynchronous result now checks the owning Home view before prompting, and an open update prompt closes when that view detaches. | SAFE |
| Restart / Close Game (`SettingsScreenView`) | Yes, More → Settings | These use `DualDexComponents.confirm()`. They now close on detach and refuse confirmation after the active ROM identity changes. | SAFE |
| Shared-save and overwrite confirmations (`SaveStateScreenView`) | Yes, More → Saves | These use `DualDexComponents.confirm()`. They now close on detach; shared-save load and overwrite also check that the captured ROM remains active. Stop-sharing is view-bound. | SAFE |
| Clear H&S Coverage Log (`HnsCoverageFactory`, debug builds) | Yes, More → Settings | This was the remaining unsafe path: an AppCompat dialog called `.show()` directly from the Presentation context. It now uses the platform builder, shared helper, owner lifetime, and one-shot confirm/cancel handling. | FIXED |
| Export H&S Coverage Log | Yes, debug builds | This launches Android's external share chooser from a non-Activity Presentation context. `Context.startActivity()` requires `FLAG_ACTIVITY_NEW_TASK`; the prior call omitted it and its failure was reduced to a generic Toast. The outer chooser now carries that flag and logs launch failures. `Intent.createChooser()` propagates the target's URI grant flags and ClipData. | FIXED |
| Save folder / import / export pickers | Yes, More → Saves / Settings | These are Android Activity Result launchers owned by `MainActivity`, not dialogs created from Presentation views. | SAFE |
| Battle move details | Yes, Battle | Details render inline; no dialog is created. | SAFE |
| `MainActivity` and `FrontendLaunchActivity` | No companion-owned dialogs found | Their matches are Toasts and Activity Result flows. | UNREACHABLE |

The source audit found no `DialogFragment`, `PopupWindow`, `WindowManager.addView`, or overlay-window usage in app UI code. Toast `.show()` calls are not dialog windows.

## Regression coverage and limits

`RomBoundActionTest` covers cancel without mutation, one-shot confirmation, a fresh reopened confirmation, active-ROM change, detached-screen invalidation, and primary versus secondary window-type selection. Existing `CheatManagerTest` exact-ROM preset policy remains unchanged; the production built-in catalog remains empty, and no unsupported or unverified ROM gains approvals. The shared helper still leaves primary-display dialogs at the platform default type.

Host tests can exercise the action guard and type-selection policy, but cannot reproduce Android's real WindowManager, Presentation display routing, or on-screen keyboard behavior. The AYN Thor run below is the device gate for those behaviors. Android 12+ uses the explicit Presentation window type; API 28–29 keep their prior behavior because they predate display-scoped window contexts.

## AYN Thor verification

Device: AYN Thor, Android 13 / API 33, external display 4. Installed the debug APK as an in-place update after confirming its signing certificate matched the existing install. App data was preserved. Pokémon Heart and Soul was already running on the upper display; no ROM or save files were added or changed.

| Scenario | Result |
|---|---|
| Open Add Cheat | VERIFIED — editor appeared on display 4. |
| Edit name and code | VERIFIED — Gboard opened on display 4 and accepted input in both fields. |
| Invalid cheat submission | VERIFIED — malformed `x` was saved disabled with the existing “empty, placeholder or malformed code” feedback; it was never enabled or executed. |
| Delete confirmation | VERIFIED — platform confirmation appeared on display 4. Cancel preserved the disposable entry; confirming removed only that entry and restored “No cheats configured.” |
| Load Presets unavailable | VERIFIED — disabled for exact H&S hash `edf76ecf2a1c`; no built-in codes were introduced. |
| Clear H&S Coverage Log | VERIFIED — debug confirmation appeared on display 4; Cancel dismissed it without clearing the log. |
| Add Cancel | VERIFIED — Cancel closed the editor without adding an entry. |
| Repeated Delete confirmation cycles | VERIFIED — two Cancel/reopen cycles left the disabled disposable entry intact; a later explicit Delete removed it once. |
| Tap Library while a confirmation is open | PARTIAL — the touch outside the modal dismissed it and left the entry intact, but the modal consumed the touch instead of navigating. Backgrounding and resuming the host Activity left the dialog visible. A true in-app owner-view detach with a confirmation open remains unverified. |
| Continue gameplay | VERIFIED — the upper-display game remained alive and responsive throughout; app PID remained present after the dialogs. |
| External H&S share chooser | VERIFIED — chooser appeared on the host display with `hns-coverage.json`. Android logged the receiver `ACTION_SEND` with MIME `application/json`, ClipData URI, and chooser task flag. The Bluetooth receiver launched and requested Bluetooth be enabled; canceled before transfer, so end-to-end receipt by a selected destination was not tested. |

After the run, `logcat` contained no app `FATAL EXCEPTION`, `BadTokenException`, `Window type mismatch`, or chooser-launch error; the app process remained alive. The malformed disposable cheat was never enabled and was deleted before leaving the screen. The host Activity's normal pause/autosave ran during the background/resume check; no gameplay input or save import/export was performed. Owner-view detach during in-app navigation and receipt by a selected share destination remain open hardware checks.
