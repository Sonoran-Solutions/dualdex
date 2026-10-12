# H&S 2.0.5 tap-to-select moves: device acceptance (#175)

Acceptance evidence for turning on tap-to-select moves for **Heart & Soul 2.0.5**. It builds on
the reader and closed-loop adapter from #174 / PR #179 ([HNS_BATTLE_MENU_READER.md](HNS_BATTLE_MENU_READER.md)).
A human reviewer signs off on this evidence before merge.

| | |
| --- | --- |
| Device | AYN Thor, Android 13 (top display `4630946441858561667`, companion display `4630946482288158084`) |
| ROM | Pokémon Heart & Soul 2.0.5, SHA-256 `edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b` (runtime exact-verified, "LIVE") |
| Builds | `31dff34` (sessions 1–2), `313091a` (refusal fix, session 3) — debug APKs from `./ci.sh all` |
| Date | 2026-10-11 |
| Settings | Interactive Touch Controls = Allowed (except the Settings-off row) |

## Method

Each tap leaves one `DualDex_MoveSelect` logcat line (`BattleInputAdapter.executeSelectMove`):
slot, start state and cursors, battler and player count, result, frames elapsed, a frame
timeline, and lock-timeout write retries. In the timeline, presses read `BUTTON@start-end`, and
each change of observed state after an A press reads `STATE@frame`. Both use completed core
frames, counted from the start of the macro.

The test was always against the game, never the log alone: the expected move had to appear in
the game ("LEET used X!") or its PP had to drop by one. Every tap was made on the DualDex
companion screen; the tester drove the game (encounters, cursor placement) with the physical
controls.

- **Session 1** (`31dff34`, 1x speed): taps were sent through `adb shell input -d 4 tap` on the
  companion screen, with a fresh screenshot before each tap, because the Battle tab scrolls.
  Every log line was recorded.
- **Session 2** (`31dff34`): the tester tapped by hand through the full checklist. The logcat
  ring buffer rotated, so only the last 16 lines survive. Rows whose lines were lost are marked
  *tester-reported*.
- **Session 3** (`313091a`): re-run of the refusal rows after the fix below.

## Matrix

### Selection: every move slot from every move-menu cursor

All 16 (starting move cursor → target slot) pairs passed with `Success` and the right move. The
move-menu walk is the same code after a FIGHT press, so pairs reached from the command menu
count too.

| # | Start | Cursor → slot | Timeline | Confirmed by |
| --- | --- | --- | --- | --- |
| 1 | command (FIGHT) | 0 → 0 | `A@1-3 ANIMATION_OR_TEXT@3 MOVE_MENU@6 A@6-8 ANIMATION_OR_TEXT@8 UNKNOWN@10 TURN_RUNNING@12` | Thunderbolt PP 15→14 |
| 2 | command (BAG) | 0 → 2 | `LEFT@0-22 A@22-24 … MOVE_MENU@27 DOWN@27-49 A@49-51 … TURN_RUNNING@55` | Tri Attack PP 10→9 |
| 3 | command (POKéMON) | 2 → 0 | `UP@1-23 A@23-25 … MOVE_MENU@28 UP@28-50 A@50-52 … TURN_RUNNING@56` | Thunderbolt PP 14→13 |
| 4 | command (RUN) | 3 → 0 | `LEFT@0-22 UP@22-44 A@44-46 … MOVE_MENU@49 LEFT@49-71 UP@71-93 A@93-95 … TURN_RUNNING@99` | Thunderbolt PP 13→12 |
| 5 | move menu | 0 → 3 | `RIGHT@0-22 DOWN@22-44 A@44-46 ANIMATION_OR_TEXT@46 UNKNOWN@48 TURN_RUNNING@50` | "LEET used DARK PULSE!" |
| 6 | move menu | 1 → 0 | `LEFT@0-22 A@22-24 … TURN_RUNNING@28` | "LEET used THUNDERBOLT!" |
| 7 | move menu | 0 → 1 | `RIGHT@1-23 A@23-25 … TURN_RUNNING@29` | Ice Beam PP 10→9 |
| 8 | move menu | 1 → 2 | `LEFT@0-22 DOWN@22-44 A@44-46 … TURN_RUNNING@50` | Tri Attack PP 9→8 |
| 9 | command (FIGHT) | 2 → 2 | `A@1-3 … MOVE_MENU@6 A@6-8 … TURN_RUNNING@12` | Tri Attack PP 8→7 |
| 10 | move menu | 3 → 2 | `LEFT@1-23 A@23-25 … TURN_RUNNING@29` | Tri Attack PP 7→6 |
| 11 | command (FIGHT) | 2 → 3 | `A@0-2 … MOVE_MENU@4 RIGHT@4-26 A@26-28 … TURN_RUNNING@32` | "LEET used DARK PULSE!" |
| 12 | move menu | 1 → 1 | `A@0-2 ANIMATION_OR_TEXT@2 UNKNOWN@4 TURN_RUNNING@6` | Ice Beam PP 9→8 |
| 13 | move menu | 1 → 3 | `DOWN@0-22 A@22-24 … TURN_RUNNING@28` | Dark Pulse PP 13→12 |
| 14 | move menu | 2 → 1 | `RIGHT@1-23 UP@23-45 A@45-47 … TURN_RUNNING@51` | Ice Beam PP 8→7 |
| 15 | move menu | 3 → 1 | `UP@0-22 A@22-24 … TURN_RUNNING@28` | Ice Beam PP 7→6 |
| 16 | move menu | 3 → 3 | `A@0-2 ANIMATION_OR_TEXT@2 UNKNOWN@4 TURN_RUNNING@6` | Dark Pulse PP 12→11 |

Opponents: wild Golett, Glaceon, Rhyhorn, Runerigus, Sawsbuck and Cubchoo, all Lv41–50, against
LEET (Porygon-Z, Lv75).

### Command-menu starts

The cursor started on each of FIGHT, BAG, POKéMON and RUN (rows 1–4). The macro walked to FIGHT
and confirmed it. BAG, POKéMON and RUN never opened; with the cursor on RUN, the game never ran
from battle.

### Remaining rows

| Row | Result | Evidence |
| --- | --- | --- |
| 1-, 2-, 3-move Pokémon: blank tiles not targetable | Pass | tester-reported |
| 0-PP move: tile disabled | Pass | tester-reported |
| Choice-locked move (Choice Scarf) | Pass on `313091a` (failed safely on `31dff34`; see below) | Session 2: two `Timeout` lines. Session 3: 3 × `MoveRefused`, each `SELECTION_MESSAGE` at +1 frame |
| Disable / Encore / Taunt | Same code path as the choice lock: `printselectionstring` → `Controller_WaitForString` (`TrySetCantSelectMoveBattleScript`) | Not logged separately; covered by the choice-lock re-run and by unit tests |
| Fast-forward at each speed | Pass: right move or safe failure | tester-reported; one surviving line shows fast-forward frame gaps with 0 retries |
| Emulator paused: tap refused | Pass | tester-reported |
| Physical button held at tap | Pass | 3 × `Cancelled(reason=Physical button held) frames=0..1 timeline=[]` |
| Physical D-pad during a macro; held across the end of a macro | Pass | tester-reported; logged macros show only the planned presses |
| Rapid double-tap / two tiles quickly | Pass: exactly one move | `Busy` then one `Success` (16:54:59); 3 × `Busy` then one `Success` (16:56:04–05) |
| Trainer intro, send-out, faint + forced switch, level-up / learn move, evolution | Pass: tiles read-only, no input | tester-reported; battle end observed read-only in session 1 |
| Leaving battle mid-macro | **Not reproduced on hardware** | A macro lasts ~0.1–1.6 s and cannot be timed against a flee. Covered by `BattleInputAdapterTest` (state change and authority loss mid-walk abort without A) and `LibretroCoreCoordinatorLockTest` (core generation invalidation). |
| Double battle: read-only, "requires a verified singles battle" | Pass | tester-reported |
| Settings toggle off: read-only | Pass | tester-reported |
| Wrong / unverified ROM: read-only with reason | Pass | tester-reported |

No row chose a wrong move or sent input outside a menu.

### Refusal failure and fix

On `31dff34`, a choice-locked move returned `Timeout`, not `MoveRefused`:

```
slot=3 start=MOVE_MENU moveCursor=1 result=Timeout(message=Move acceptance was not observed) frames=144 timeline=[DOWN@0-22 A@22-24 ANIMATION_OR_TEXT@24]
slot=2 start=MOVE_MENU moveCursor=3 result=Timeout(message=Move acceptance was not observed) frames=144 timeline=[LEFT@0-22 A@22-24 ANIMATION_OR_TEXT@24]
slot=0 start=MOVE_MENU moveCursor=2 result=Success frames=29 …   (the locked move afterwards)
```

The failure was safe: no wrong move, and no input after the A press. But it fell short of the
row's specified outcome, and the tester's own A press to dismiss the message was suppressed for
the 120-frame observation window.

**Cause (pinned source `1f42b74`).** The refusal text was not slow; it never ends on its own.
`BattleScript_SelectingNotAllowedMoveChoiceItem` runs
`printselectionstring STRINGID_ITEMALLOWSONLYYMOVE`, and that string is
`"{B_LAST_ITEM} only allows the use of {B_CURRENT_MOVE}!\p"`. In ordinary battles
`gTextFlags.autoScroll` is FALSE and `AUTO_SCROLL_TEXT` is FALSE, so the `\p` waits for A/B
(`TextPrinterWaitWithDownArrow`). The game stays in `HandleTurnActionSelectionState`
(`STATE_SELECTION_SCRIPT`) with the player's controller in `Controller_WaitForString` until the
player presses a button. No `OBSERVATION_FRAMES` value could produce `MoveRefused`, at any text
speed.

**Fix.**
- `Controller_WaitForString` is now pinned to the release ROM at `0x08061F44`. A unique 24-byte
  prologue match and `BtlController_HandlePrintString`'s stored pointer agree on it.
- The reader reports native state 8, `SELECTION_MESSAGE`, when that handler is active while action
  selection is still running.
- The adapter returns `MoveRefused` as soon as it observes `SELECTION_MESSAGE` after the move A,
  which ends the input session so the player's dismiss press reaches the game.
- A `SELECTION_MESSAGE` after the FIGHT press aborts before any move input.
- Tests: native `test_hns_battle_menu_reader`, `BattleInputAdapterTest`
  (`refusalMessageReportsRefusedWithoutWaitingAndReleasesInput`,
  `refusedFightMessageNeverSendsAMoveConfirm`) and `BattleInteractionPolicyTest`.

Session 3 re-validation on `313091a`. LEET held the Choice Scarf and was locked into Thunderbolt;
the log was captured to a file for the whole session:

```
slot=1 moveCursor=0 result=MoveRefused frames=25 timeline=[RIGHT@0-22 A@22-24 ANIMATION_OR_TEXT@24 SELECTION_MESSAGE@25]
slot=3 moveCursor=1 result=MoveRefused frames=25 timeline=[DOWN@0-22 A@22-24 ANIMATION_OR_TEXT@24 SELECTION_MESSAGE@25]
slot=2 moveCursor=3 result=MoveRefused frames=25 timeline=[LEFT@0-22 A@22-24 ANIMATION_OR_TEXT@24 SELECTION_MESSAGE@25]
slot=0 moveCursor=2 result=Success     frames=28 timeline=[UP@0-22 A@22-24 ANIMATION_OR_TEXT@24 UNKNOWN@26 TURN_RUNNING@28]
```

Results:
- Every locked-out move reported `MoveRefused` one frame after the A press. On `31dff34` the
  same row ended in a `Timeout` at 144 frames.
- DualDex showed "The game refused that move — dismiss its message", and the tester's first A
  press dismissed the game's text.
- No input was sent after the refusal, and the locked Thunderbolt then succeeded.
- The tester reported the same behaviour at Slow text speed.
- There were 0 write retries and no crash.

## Measurements and tunables

The logs cover 32 macros: 23 `Success`, 3 `Cancelled`, 4 `Busy` and 2 `Timeout`. They contain
34 directional presses and 32 A presses.

| Tunable | Value | Evidence | Decision |
| --- | --- | --- | --- |
| `CONFIRM_REGISTRATION_FRAMES` | 6 | In every logged confirm, the game left the menu on the first read after the A press completed (`ANIMATION_OR_TEXT` at +0), then `UNKNOWN` at +2 and `TURN_RUNNING` at +4. | Unchanged; 6 frames of margin. |
| `OBSERVATION_FRAMES` | 120 | The FIGHT → `MOVE_MENU` transition took 2–3 frames, and acceptance took at most 4 frames after the A press. Refusal never returns to the menu without input (see above), so no value of this constant affects it. | Unchanged. Refusal now ends on positive `SELECTION_MESSAGE` evidence, observed 1 frame after the A press on device. |
| `DIRECTION_SETTLE_FRAMES` | 20 | 34/34 directional presses reached the expected cursor; there was no cursor-mismatch abort. The menu handler moves the cursor on the `JOY_NEW` frame, but the post-settle read cannot measure a lower bound. | Unchanged; a direction costs about 0.37 s at 1x. |
| `WATCHDOG_FRAMES` | 1200 | The longest logged macro took 144 frames (the refusal timeouts). | Unchanged. |
| Write budgets | press 17 ms × 3, release 2 ms × 3 | 1 release retry (session 1, row 3; it succeeded on the next attempt); 0 press retries; 0 exhausted. Retry counts per fast-forward speed were in the rotated session-2 lines and were not retained. | Unchanged. |

## Known limitations

- **Singles only.** Doubles stay read-only ("move selection requires a verified singles battle").
- **Target selection:** the adapter reports `TargetSelectionRequired` and sends no further input.
- **Out of scope:** party switching, Bag and Run. `partyCursorVerified`, `partySwitchVerified`,
  `partyActionMenuVerified` and `interactiveControlsVerified` stay false. Every other profile
  stays non-interactive (`RomHackProfileTest`).
- **Not reproduced on hardware:** leaving battle mid-macro (unit-tested only).
- **Unrelated, seen during session 2:** a native crash on a calculator thread at 16:55:40
  (`Scudo ERROR: corrupted chunk header`, inside QuickJS `js_malloc_rt`). It points to
  cross-thread use of the shared QuickJS runtime and likely shares a cause with #177. It is
  tracked separately and was not counted against acceptance.
