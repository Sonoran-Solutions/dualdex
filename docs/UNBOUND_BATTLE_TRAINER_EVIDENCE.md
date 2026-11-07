# Unbound exact-candidate trainer / double / faint / switch battle evidence

Task E2 (issue [#60](https://github.com/Sonoran-Solutions/dualdex/issues/60) section C follow-up),
runtime observation 2026-11-06 on top of `e6184b2`. This extends the
[wild-battle lifecycle evidence](UNBOUND_BATTLE_LIFECYCLE_EVIDENCE.md) (one wild single battle)
to **one two-opponent trainer double battle** with enemy faints, enemy replacements and a player
switch. It is read-only layout evidence for one exact cartridge (SHA-256 `7aa25bbf…739f7`), one
owner battery save (`a561cab2…5e236`) and the pinned host core and adapter. It is not a support
verdict. Production code, profiles and readers are unchanged; the release label stays
**NOT_VERIFIED** (owner claim v2.1.1.1).

Evidence files:

* [manifest](../tools/rom-evidence/evidence/unbound-runtime-battle-trainer.json): numbers,
  addresses and hashes only. It holds the input/tool/movie hashes, static literal counts, a
  27-point timeline and the run-to-run comparison. Each point has the battle globals, the
  lifecycle signals, the battler map and, per battler, the species word, level, HP, PP, stages,
  status words and a field-by-field comparison with the mapped party record. It also has the
  enemy records. Raw RAM, PPM screens and the full private reports stay under
  `/home/dq/dualdex-private/unbound/e2-battle/` (mode 700, no Git ancestor). The primary
  report has SHA-256 `934a1daf…65fc6`.
* [route movie](../tools/rom-evidence/unbound-battle-trainer-double.movie): it reuses the prefix of
  the wild-battle movie (boot, Continue, out of the Hidden Grotto, onto the water). From there it
  surfs south and west to the shore and walks to the cave warp at map 3.6 (10,7). It then steps
  between two facing trainers in map 56.6 and plays the battle with Fight, a Pokémon/slot 3/Shift
  switch and Fight until the win, ending in the overworld. The inputs were recorded from a private
  closed-loop exploration driver and replay open-loop.
* [`unbound_battle_observe.py`](../tools/rom-evidence/unbound_battle_observe.py) and
  [its ROM-free tests](../tools/rom-evidence/test_unbound_battle_observe.py) (see "Tool").

Labels: **VERIFIED** means checked against source and the exact ROM, and confirmed at runtime.
**OBSERVED** means seen at runtime in this battle only. **NOT_VERIFIED** means the evidence is
insufficient. **NOT_RUN** means the scenario was not exercised. Source references are public
CFRU `b637a27` (same pin as the wild evidence).

## Verdicts

| # | Question | Verdict |
|---|---|---|
| 1 | gBattleTypeFlags address and bits | **VERIFIED** for address `0x02022B4C` and the bits DOUBLE `0x1`, IS_MASTER `0x4`, TRAINER `0x8` and TWO_OPPONENTS `0x200000`. Wild single gave `0x4` (earlier evidence); the two-opponent trainer double gave `0x20000D`. The remaining CFRU bits are source-only, **NOT_VERIFIED** at runtime |
| 2 | Trainer battle: enemy party, active enemy, opponent ID, start/end markers | **VERIFIED** (double, two opponents): enemy records at `0x0202402C` in CFRU per-trainer ranges, `gBattleMons` matches them, and `gTrainerBattleOpponent_A` (`0x020386AE`) = 341 = the trainer script ID. Start/end: `gMain.inBattle` (**OBSERVED**). **Single-opponent trainer battle NOT_RUN** |
| 3 | Double battle positions and slot mapping | **VERIFIED**: positions `[0,1,2,3]` = player-left, opponent-left, player-right, opponent-right. `gBattlerPartyIndexes` (u16 ×4 at `0x02023BCE`) indexes the side's party under CFRU `LoadPartyRange`. **Wild double NOT_RUN** |
| 4 | Faint and switch | **OBSERVED**: faint sets HP 0 in battler and record, and the battler keeps the fainted data until a replacement rewrites both the slot and the index. A switch changes only `gBattlerPartyIndexes[b]` and `gBattleMons[b]`, never `gPlayerParty` order. **Player faint / forced player replacement NOT_RUN** |

## Static evidence

* CFRU `include/constants/battle.h` defines `BATTLE_TYPE_DOUBLE 0x1`, `LINK 0x2`, `IS_MASTER 0x4`
  ("in not-link battles, it's always set"), `TRAINER 0x8`, `MULTI 0x40`, `TWO_OPPONENTS 0x200000`,
  `INGAME_PARTNER 0x400000`, and the CFRU bits up to `SHADOW_WARRIOR 0x80000000`. The tool
  decodes all of them (`TYPE_FLAG_BITS`). `BPRE.ld` places `gBattleTypeFlags` at `0x02022B4C`.
  It has 936 PC literal loads in the exact ROM (wild evidence table), with first accesses
  mostly 32-bit `ldr`.
* CFRU `src/battle_util.c` `LoadPartyRange`: in a `TWO_OPPONENTS` battle the opponent-right battler
  uses enemy slots 3..5 and opponent-left uses 0..2. With `INGAME_PARTNER` the player-right battler
  uses player slots 3..5. Otherwise (single, ordinary double, wild double) one 0..5 range per side
  applies. `battler_map()` implements exactly this.
* Literal-load counts in the exact ROM (`lifecycle_literal_references` in the manifest):
  `gMain` `0x030030F0` 756, `gBattleMainFunc` `0x03004F84` 62 (mostly `str`),
  `gAbsentBattlerFlags` `0x02023D70` 140 (`ldrb`), `gTrainerBattleOpponent_A` `0x020386AE` 84
  (`ldrh` 76, `strh` 8), `BattleMainCB2` literal `0x08011101` 18. CFRU `include/main.h` puts
  `inBattle` at byte `0x439` bit 1 of `struct Main`; the production H&S config uses the same.
* Wild doubles (source only): `src/wild_encounter.c` sets `FLAG_DOUBLE_WILD_BATTLE` (`0x910`) only
  on land or water tiles whose encounter-type attribute has `TILE_FLAG_WILD_DOUBLE` (4), with
  `WILD_DOUBLE_RANDOM_CHANCE` 50, or via Sweet Scent. This ROM's metatile attribute words do not
  use the vanilla FireRed bit layout, so double tiles could not be located statically. Twelve
  idle-delay variants on the grotto water all gave the same single encounter.
* Route planning only (not a reader input): map headers come from an XOR-obfuscated group table.
  `Overworld_GetMapHeaderByGroupAndId` (`0x08055238`) is hooked to `0x09ED15D4`, which loads
  table `0x08B70498` and XORs each group pointer with `0x00B749DE`. The vanilla table at
  `0x083526A8` has no aligned pointer references. Trainer flags were read at SaveBlock1 `+0xEE0`
  bit `0x500 + id` (CFRU `src/overworld.c`); IDs 341 and 342 were unset in the battery save.

## Runtime timeline (one two-opponent trainer double)

Two sight trainers (IDs 341/342, script type 0) in map 56.6 face each other across column 21.
Stepping into (21,12) triggered both. Opponent A sent enemy slot 0 (species word 353, Lv95) and
opponent B slot 3 (354, Lv94). The player sent party slots 1 and 2 (130, 230). Turn 1 used one attacking move each (the second
hits both foes); both foes fainted. A replaced with slot 1 (576) and B with slot 5 (26). Turn 2 battler 0
**Shifted** to party slot 3 (index 2, species 186), and 576 and 26 fainted. A had nothing
left; B sent slot 4 (399). Turn 3 won the battle.

| Point (frame) | inBattle / callback2 | Flags / count / active / indexes / absent / outcome | Battlers (species:HP) |
|---|---|---|---|
| cave_before_trainers (4232) | 0 / overworld `0x080565B5` | `0x0` / 0 / 0 / 0,0,0,0 / 0 / 0 | – |
| trainers_spotted (4494) | 0 / overworld | same; **opponent A = 341** already set | – |
| battle_start (5041) | **1** / `0x08010509` | `0x20000D` / 4 / 0 / **0,0,1,3** / 0 / 0 | all four `gBattleMons` zero; enemy records already present |
| trainers_sent_out (5942) | 1 / **BattleMainCB2** | same, active **4** | 130:195, 353:242, 230:291, 354:240 (all match records) |
| first_turn_menu (7417) | 1 / BattleMainCB2 | same | Intimidate: both foes Atk stage 5 |
| first_enemy_fainted (9465) | 1 / BattleMainCB2 | same | 353:**0** (record HP 0 too) |
| second_enemy_fainted (10457) | 1 / BattleMainCB2 | absent **2** | 354:**0** |
| replacements_sent (12451) | 1 / BattleMainCB2 | indexes **0,1,1,5**, absent 0 | 576:276, 26:247, stages reset to 6 |
| switch_party_menu (12815) | 1 / party menu `0x0811EBA1` | unchanged | unchanged |
| after_switch_turn2 (15331) | 1 / BattleMainCB2 | indexes **2**,1,1,5 | battler 0 = **186:177** = `gPlayerParty[2]`; 576:0 |
| trainer_a_out (17207) | 1 / BattleMainCB2 | indexes 2,1,1,**4**, absent **2** (stays) | battler 1 keeps 576:0; battler 3 = 399:245 |
| battle_won (21057) | 1 / BattleMainCB2 | outcome **1**, absent 0 | 399:0 |
| overworld_after_battle / overworld_later (22568 / 23168) | **0** / overworld | **still** `0x20000D` / 4 / 4 / 2,1,1,4 / 0 / 1 | `gBattleMons` digest unchanged since battle_won |

The full table has 27 points. At every point from send-out to the win, all four battlers equalled
their mapped records field by field. The fields were personality, OT ID, species, item, moves,
PP, HP, max HP, level, five stats and experience. Every battler index fell inside its owner's
`LoadPartyRange`.

Enemy records: slots 0, 1 (opponent A) and 3, 4, 5 (opponent B) were non-zero and slot 2 was
zeroed. The checksum word was `0x0000` in every record and the production CFRU parser accepted
all five. The player party order stayed unchanged (130, 230, 186, 1002, 1035, 942) at every point.
Only HP and experience-related bytes changed; slot 6 levelled up from Exp. Share.

The route is deterministic. Reports `run02` and `run03` of the final tool are byte-identical,
including the screen hashes (`repeat_run` in the manifest). `run01`, made before a summary-only
tool edit, has identical battle, lifecycle and party rows.

## Status matrix

| Item | Status | Evidence |
|---|---|---|
| gBattleTypeFlags `0x02022B4C` | **VERIFIED** bits 0x1/0x4/0x8/0x200000; other bits source-only | Source constants, 936 literal loads, runtime `0x4` (wild) and `0x20000D` (two-opponent double) |
| Live-battle gate `gMain.inBattle` (`0x03003529` bit 1) | **OBSERVED** authoritative in this battle | 0 → 1 at battle start, 1 through the win screen, 0 in the overworld while every battle global stayed stale |
| `gMain.callback2` | **OBSERVED** not sufficient alone | `BattleMainCB2` (`0x08011101`) most of the battle. During the transition it was `0x08010509` and in the battle party menu `0x0811EBA1` |
| gBattlersCount `0x02023BCC` | **OBSERVED** 4 in a double (2 in the wild single) | Set at battle start, before `gBattleMons` is filled |
| gBattlerPositions `0x02023BD6` | **VERIFIED** `[0,1,2,3]` in the double | bit 0 = side, bit 1 = flank |
| gBattlerPartyIndexes `0x02023BCE` | **VERIFIED** | Two-opponent ranges per `LoadPartyRange`. Opponent B indexes 3, 5, 4 |
| gBattleMons in a double | **OBSERVED** 4 × 0x58 at `0x02023BE4` | **Zero** at the battle_start point: a reader must not trust battlers until species ≠ 0 / send-out |
| gTrainerBattleOpponent_A `0x020386AE` | **OBSERVED** = 341 = script trainer ID | Set when spotted (before flags), stale after the battle. Opponent B is CFRU `VAR_SECOND_OPPONENT` (`0x5010`), **NOT_VERIFIED** |
| gEnemyPartyCount `0x0202402A` | **OBSERVED 0 throughout a trainer battle too** | Zero at all 27 points with five enemy records present. `0x02024028` (`sLearningMoveTableID`) became 16 after experience. Neither byte is an enemy occupancy signal on this ROM |
| gActiveBattler `0x02023BC4` | **NOT_VERIFIED** meaning | 0 at battle start, then always 4 (= gBattlersCount) at paused points; consistent with a loop-counter residue |
| gAbsentBattlerFlags `0x02023D70` | **OBSERVED** | bit 1 set while a fainted opponent-left battler awaits or lacks a replacement. It stayed set while opponent A was out of Pokémon and cleared at the win |
| Faint | **OBSERVED** (enemy only) | HP 0 in battler and record; species and stats stay in the battler until replaced |
| Replacement | **OBSERVED** (enemy only) | Index and battler rewritten together; stat stages reset to 6 |
| Player switch | **OBSERVED** | Index 0 → 2, `gBattleMons[0]` = `gPlayerParty[2]`, party order unchanged |
| gBattleOutcome `0x02023E8A` | **OBSERVED** 1 on win (4 on run, earlier) | Stale after exit |
| Teardown | **OBSERVED stale** | Flags, count, indexes, outcome, `gBattleMons` and the opponent ID all persisted into the overworld |
| Single-opponent trainer battle | **NOT_RUN** | The nearest unbeaten single trainers (map 56.8, and eight on map 3.1) sit behind an enclosed cave room and a warp maze. That was out of budget |
| Wild double | **NOT_RUN** | No double-flag tile located (see Static) |
| Player faint / forced player replacement | **NOT_RUN** | The Lv100 party took no damage in this battle |

## Owner save-state requests

To close the NOT_RUN rows, an mGBA quicksave (`.state` for the pinned core; the battery save
alone cannot reach these quickly) taken:

1. on the first action menu of a **single-opponent trainer battle** (any trainer), to verify flags
   `0xC` and single-trainer enemy ranges;
2. on the first action menu of a **wild double battle** (dark/double grass), to verify flags `0x5`
   and both wild records;
3. one turn before **the player's active Pokémon faints** (low-HP lead against a stronger foe),
   to observe the forced-replacement path, `gAbsentBattlerFlags` on the player side and the
   party-index change without a Shift;
4. optionally, on the first menu of a **multi/partner battle** (`INGAME_PARTNER`), to verify the
   player-right 3..5 range.

## Tool

`unbound_battle_observe.py` composes `unbound_observe.py`, which is unchanged. It keeps the
same pins, the paused read-twice snapshots, no RAM writes and no cartridge saves. On top it adds:

* `BattleObserver.lifecycle_snapshot()`: `gMain.callback1/2`, `gMain.inBattle`, `gBattleMainFunc`,
  `gAbsentBattlerFlags` and `gTrainerBattleOpponent_A`, read twice and required equal.
* An enemy `party_snapshot()` that counts by the static-verified `0x0202402A`. The inherited one
  counts by the historical `0x02024028`, which is CFRU `sLearningMoveTableID`. That byte reached
  16 after experience was awarded, so the inherited observer refused this battle with
  "party count exceeds six". The inherited code is left as is because the wild evidence is bound
  to it.
* `type_flag_names()`, `battler_map()` (`LoadPartyRange`), `inspect_rom()` with the extra literal
  counts, `summarize()` (the numeric-only manifest; it drops nickname and OT bytes and parser text)
  and `repeat_comparison()`.

Movies are limited to `run`/`mash`/`observe`. The tests (7, invented bytes only) cover flag
decoding, the party-range mapping (including out-of-range flagging and bounds), lifecycle
coherence, the enemy count source and bound, the absence of text in the summary, the repeat
comparison, and the binding of the committed movie, manifest and tool hashes.

## Reproduction and validation

```bash
P=/home/dq/dualdex-private/unbound
./tools/vanilla-runtime-probe/build.sh   # rowe_observe.verify_adapter() accepts it (pin ff51c01)
timeout 600 python3 tools/rom-evidence/unbound_battle_observe.py --rom $P/unbound-candidate.gba \
  --core /home/dq/Projects/upstream-hns/mgba-host/mgba_libretro.so \
  --battery-save $P/thor-2026-09-06.sav --movie tools/rom-evidence/unbound-battle-trainer-double.movie \
  --private-dir $P/e2-battle/run02 --output $P/e2-battle/run02.json
python3 tools/rom-evidence/unbound_battle_observe.py --summary $P/e2-battle/run02.json \
  --repeat $P/e2-battle/run03.json   # committed manifest = this output + task/scope/source notes
```

* Emulator runs, each under `timeout 600`: about 40 short private exploration runs from private
  states (route finding, battle stepping; none saved in-game, never a cartridge write) and 4
  observer runs. The first observer attempt failed in 6.7 s with "party count exceeds six" (the
  inherited enemy count, see Tool); its private directory was removed and the label `run01`
  reused for the rerun. The three successful runs took 12.4 s, 12.3 s and 12.4 s and exited 0.
  No process outlived its run.
* `TMPDIR=<task tmp> python3 -m unittest discover -s tools/rom-evidence`: Ran 126 tests, OK (7 new).

## Production implications (recommendations only; nothing here was run in production)

`CONFIG_UNBOUND` (in `native/src/pokemon_reader.c`) declares only party fields today. To mark
Unbound battle reads verified, a later production change would need:

1. **Battle globals** (EWRAM-relative unless noted): `battle_mons_offset 0x23BE4`,
   `battle_mons_size 0x58`, `battle_mons_hp_offset 0x28`, `battlers_count_offset 0x23BCC`,
   `battler_party_indexes_offset 0x23BCE`, `battler_positions_offset 0x23BD6`,
   `battle_type_flags_offset 0x22B4C`, `battle_outcome_offset 0x23E8A`,
   `absent_battler_flags_offset 0x23D70`. Lifecycle: `main_struct_gba_address 0x030030F0`,
   `main_in_battle_byte_offset 0x439`, `main_in_battle_bit 1`.
2. **Gate on `gMain.inBattle`**, never on flags, count, outcome or `gBattleMons` contents. All of
   these stay stale after exit, and `callback2` leaves `BattleMainCB2` inside the battle party
   menu.
3. **Do not gate the enemy party on `enemy_party_count_offset`.** `0x2402A` read 0 in both a wild
   and a trainer battle, so `read_authoritative_enemy_party()` would return 0 opponents on this
   ROM. Derive occupancy instead from opponent battlers (`positions & 1`) and their
   `gBattlerPartyIndexes`, or from non-zero records inside the CFRU `LoadPartyRange` range
   (0..2 / 3..5 for `TWO_OPPONENTS`, else 0..5).
4. **Map battlers through `gBattlerPartyIndexes` and positions**, not slot 0. Handle four battlers
   for `BATTLE_TYPE_DOUBLE` and per-trainer ranges for `TWO_OPPONENTS`/`MULTI`/`INGAME_PARTNER`.
5. **Treat a battler with HP 0 as fainted but still present** until its index changes. Use
   `gAbsentBattlerFlags` to suppress an empty flank (an opponent with no remaining Pokémon). Expect
   all-zero `gBattleMons` right after `inBattle` rises, before send-out.
6. Stat-stage validation must skip byte `0x18` (CFRU `type3`, observed 20), as the wild evidence
   already notes.
7. Species and item words are CFRU internal IDs (for example 576, 26 and 399 here). The internal →
   national mapping from the species evidence still applies before any calculator use.
8. Before claiming full support, the owner states listed above must close single-trainer,
   wild-double and player-faint behaviour on this ROM.
