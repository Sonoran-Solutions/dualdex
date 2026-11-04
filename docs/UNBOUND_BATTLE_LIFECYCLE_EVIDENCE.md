# Unbound exact-candidate battle lifecycle evidence

Task `unbound-battle`, issue [#60](https://github.com/Sonoran-Solutions/dualdex/issues/60)
section C. Runtime observation 2026-11-03 on top of `389b0bb`. This is read-only layout
evidence for one exact cartridge, one save and **one wild single battle**. It is not a
support verdict. Production profiles, hashes, readers, Kotlin, CI and the shared README
are unchanged. The release label stays **NOT_VERIFIED** (owner claim v2.1.1.1). The
identities of the ROM, battery, core and adapter are the ones recorded in
[the party layout evidence](UNBOUND_PARTY_LAYOUT_EVIDENCE.md).

Evidence files:

* [battle manifest](../tools/rom-evidence/evidence/unbound-runtime-battle.json): input,
  tool and movie hashes; static span and literal-reference counts; a 16-point timeline.
  Each point has the battle globals, the selected `gBattleMons` fields with a field-by-field
  comparison against the mapped party/enemy record, the selected enemy-record fields, the
  production parser output, screen hashes and a manual screen transcription. Raw RAM
  (`*_battle.mons`, `*.party`) and PPM screens stay private under
  `/home/dq/dualdex-private/unbound/battle-opus/` (mode 700, no Git ancestor).
* [route movie](../tools/rom-evidence/unbound-battle-wild-run.movie): boot, Continue,
  walk out of Hidden Grotto, step onto the water, two tiles down and up until the encounter,
  then Fight/Waterfall, Pokémon/Toady/Shift, Run, overworld and the party menu.
  The route is deterministic from boot: two runs gave identical screens and `gBattleMons` digests.
* [`unbound_observe.py`](../tools/rom-evidence/unbound_observe.py) has been extended (see "Tool
  change").

Labels: **VERIFIED** means a stated static operation or hash was checked. **OBSERVED** means
seen at runtime and/or on screen in this one battle. **NOT_VERIFIED** means the evidence is
insufficient. **NOT_RUN** means the scenario was not exercised.

## Static exact-ROM evidence

* `0x08040C80..0x08040CC4` (SHA-256 `96aaea06…`, rechecked by `inspect_rom()`): this follows
  the player-count routine. It clears the byte at literal `0x0202402A`, then loops while the
  byte is ≤ 5. It multiplies it by 100, adds literal `0x0202402C` and calls the party getter
  with selector 11 (species), incrementing the byte on non-zero species. This is the enemy
  equivalent of the player routine: **VERIFIED** `gEnemyPartyCount` = u8 `0x0202402A`,
  `gEnemyParty` = `0x0202402C`, stride 100.
* `literal_references()` counts Thumb `ldr rd,[pc,#imm]` loads of each aligned literal word
  in the exact ROM. It also records the width of the first load/store through `rd`, and
  `movs #0x58`/`#0x64` within ±16 bytes. Public CFRU `b637a27` `BPRE.ld` names the addresses:

| Address | CFRU name | PC literal loads | First access | Nearby `#0x58` / `#0x64` |
|---|---|---|---|---|
| `0x02022B4C` | gBattleTypeFlags | 936 | ldr 873, str 34 | 137 / 11 |
| `0x02023BC4` | gActiveBattler | 1439 | ldrb 1121, strb 276 | 114 / 187 |
| `0x02023BCC` | gBattlersCount | 402 | ldrb 360, strb 4 | 39 / 3 |
| `0x02023BCE` | gBattlerPartyIndexes | 448 | ldrh 48 (u16 array) | 5 / **214** |
| `0x02023BD6` | gBattlerPositions | 32 | ldrb 13, strb 9 | 0 / 0 |
| `0x02023BE4` | gBattleMons | 1856 | ldrh 342, ldrb 257 | **1725** / 20 |
| `0x02023E8A` | gBattleOutcome | 58 | ldrb 42, strb 9 | 0 / 1 |
| `0x0202402A` | gEnemyPartyCount | 3 | ldrb 2, strb 1 | 0 / 0 |
| `0x02024028` | sLearningMoveTableID (`ram_locs.h`) | 4 | ldrb 2, strb 2 | 0 / 0 |
| `0x0202402C` | gEnemyParty | 551 | mixed | 6 / **479** |

  These counts are consistent with a 0x58-byte battler stride and 100-byte enemy records,
  with party indexes used to address 100-byte records. They are counts, not caller proofs.
  The 0x58-byte `BattlePokemon` field layout comes from public CFRU `include/pokemon.h`. In it,
  byte `0x18` is `type3`, and `statStages[1..7]` (Atk..Evasion) are at `0x19..0x1F`. The runtime
  comparison below supports the layout.
* Production `CONFIG_FIRERED.battle_mons_offset` = `0x23F90`: `0x02023F90` has **0** PC literal
  loads in this ROM and in vanilla FireRed rev0. `0x02023BE4` has 1856 and 441.

## Runtime timeline (one wild battle)

Wild **Jellicent ♂ Lv58** (internal species word 646; national dex 593) on the water south of
the Hidden Grotto exit ("Cube Corp." banner). Lead Gary (Gyarados), then Toady (Politoed,
party slot 3) swapped in, then a successful Run.

| Point (frame) | Flags / count / active / party idx / outcome | Battler 0 (player) | Battler 1 (enemy) | Screen (manual) |
|---|---|---|---|---|
| pre_battle_overworld (2718) | `0x0` / 0 / 0 / 0,0 / 0 | – | – | overworld on water |
| battle_start (5212) | `0x4` / 2 / 2 / 0,0 / 0 | 130, 195/332, PP 10/20/15/10 | 646, 201/201 | transition wipe |
| wild_appeared (5812) | same | same | same | "A wild Jellicent appeared!", Lv58, full bar |
| first_turn_menu (6603) | same | byte18 20, stages all 6 | Atk stage **5** (Intimidate) | "What will Gary do?", Gary Lv100 195/332, bar 48/48 px |
| after_move (8015) | same | Waterfall PP **9** | HP **48**/201 | Gary 195/332, enemy bar 11/48 px yellow |
| after_switch (10090) | `0x4` / 2 / 2 / **2**,0 / 0 | **186** (Politoed) 177/384 | 48/201, PP slot 2 **4**, status2 `0x02000000` | "What will Toady do?", Toady Lv100 177/384 |
| got_away (10963) | outcome **4** | unchanged | unchanged | "Got away safely!" |
| overworld_after_exit / overworld_later (11454 / 12054) | **still** `0x4` / 2 / 2 / 2,0 / 4 | unchanged digest | unchanged digest | overworld |
| party_menu_after (12538) | same | – | – | order Gary, Reyna, Toady, … unchanged |

`gBattlerPositions` was `[0, 1, 255, 255]` in every battle observation. In all 13 battle
observations both battlers equalled the mapped record field by field: personality, OT ID,
species, item, moves, PP, HP, max HP, level, five stats and experience. Battler 0 was checked
against `gPlayerParty[idx]` and battler 1 against `gEnemyParty[idx]`. Battle bytes agree with
the summary-screen facts in the party evidence. (The player party digest also changed before the
encounter. Friendship `+41` went up for slots 3, 4 and 6 from the walking steps; that is not a battle
effect.) The bytes show: Politoed's battle ability byte is 2 (vanilla
Drizzle), Gyarados's is 22 (Intimidate), and the types are Water/Flying, Water/Water and
Jellicent Water/Ghost (`11,2`, `11,11`, `11,7` in vanilla type numbering). Enemy status2
`0x02000000` after its second move is consistent with vanilla `STATUS2_DESTINY_BOND`: its move
word 194 is Destiny Bond, it lost one PP, and it did no damage. That reading is
**NOT_VERIFIED** beyond this consistency.

## Status matrix

| Item | Status | Evidence |
|---|---|---|
| Enemy party base | **VERIFIED static + OBSERVED** `0x0202402C`, 100-byte stride | Static routine. At runtime slot 0 held the Jellicent record equal to battler 1 |
| Enemy count offset | **VERIFIED static** `0x0202402A`; `0x02024028` is not the count (CFRU `sLearningMoveTableID`, 4 refs). **OBSERVED**: *both* bytes read **0** throughout this wild battle | The enemy-count routine evidently did not run for a wild battle. Neither byte indicates wild-battle occupancy. Trainer battle (where it should be written) **NOT_RUN** |
| Enemy record format | **OBSERVED** same as party format: fixed G/A/E/M, unencrypted | One record with `pid % 24` = 19 decodes coherently in fixed order; the PID-order raw/XOR species words (56, 54199) are not. All fields equal `gBattleMons[1]`. Production parser accepted it (species 646, Lv58, HP live). Other five slots are zeroed except `+85` = `0xFF` (mail none) |
| Checksum word in battle | **OBSERVED** `0x0000` in 6/6 player records and the enemy record at every battle point | Matches neither raw nor XOR sum (enemy raw `0x608E`, XOR `0xC68A`). The #24 claim of non-zero in battle was not reproduced in this wild battle |
| gBattleMons | **OBSERVED** `0x02023BE4`, 0x58 stride (+ static counts) | 2 battlers × 11 compared fields (stats counted as one) agree with the records |
| gBattlersCount | **OBSERVED** `0x02023BCC` = 2 in a single battle; 0 before the first battle after boot | |
| gBattlerPartyIndexes | **OBSERVED** `0x02023BCE` u16 ×4; `[0,0]` → `[2,0]` after Shift to party slot 3 | |
| gBattlerPositions | **OBSERVED** `0x02023BD6` `[0,1,255,255]` | Bit 0 = side, used for the side mapping |
| gActiveBattler | **NOT_VERIFIED** meaning; `0x02023BC4` read 2 at every paused battle point | Static use as a byte index is consistent; the paused value is not the "current" battler |
| gBattleTypeFlags | **OBSERVED** `0x02022B4C` `0x0` → `0x4` for a wild single | Bit meanings beyond this one value **NOT_VERIFIED** |
| gBattleOutcome | **OBSERVED** `0x02023E8A` 0 → 4 on Run | |
| Party mapping after switch | **OBSERVED** battler 0 = `gPlayerParty[2]` after the switch; `gPlayerParty` order **not** changed during or after the battle (raw diff and party-menu UI) | A reader must map via `gBattlerPartyIndexes`, not assume slot 0 |
| Live HP / PP | **OBSERVED** written to both battler and record live: enemy record `+86` 201→48, player `+52` (A+8) 10→9, enemy `+53` 5→4 | |
| Stat stages | **OBSERVED** `0x19..0x1F` neutral 6; enemy Atk 6→5 after Intimidate. Byte `0x18` = 6 at battle start, then 20 (CFRU `type3` = `TYPE_BLANK` 0x14) | |
| Teardown | **OBSERVED stale**: 1,575 frames after the Run (to the party menu), flags `0x4`, count 2, indexes `[2,0]`, outcome 4, `gBattleMons` digest and enemy slot 0 (HP 48) were unchanged | No clearing at exit. These globals cannot by themselves tell "in battle" from "after a battle" |
| Species ID domain in battle | **OBSERVED** internal 646 for national 593 | Same internal-ID finding as the party evidence |
| Trainer battle, doubles, faint/forced replacement | **NOT_RUN** | No trainer reachable cheaply from this route |

## Disagreements with production `native/src/pokemon_reader.c`

Everything here is reasoned from source; none of it was run in production.

1. `CONFIG_UNBOUND.enemy_party_count_offset = 0x24028` is CFRU `sLearningMoveTableID`. The
   exact-ROM count is `0x0202402A`, which `CONFIG_FIRERED` already uses. As with the vanilla
   audit comment above `CONFIG_EMERALD`, the value is latent: `CONFIG_UNBOUND` declares no
   `battlers_count_offset`/`battle_type_flags_offset`. So `pokemon_read_enemy_party_gba` returns
   0 before it reads a count (around line 1960).
2. Even the correct count byte was 0 during this wild battle. A count-gated enemy reader would
   publish no wild opponent on this ROM. Occupancy has to come from elsewhere, e.g. battlers
   plus `gBattlerPartyIndexes`. Which signal is authoritative is **NOT_VERIFIED**.
3. `CONFIG_UNBOUND` has no `battle_mons_*` fields. The observed layout is vanilla-sized (0x58,
   HP `+0x28`, stages base `0x18`) at `0x02023BE4`. The header selects `CONFIG_FIRERED`, as the
   party evidence notes. Its `battle_mons_offset = 0x23F90` has no literal loads in this ROM
   or in vanilla FireRed.
4. This would apply if a battle layout were declared: today
   `pokemon_read_battler_runtime_state_gba` returns before the stage check unless
   `battle_pokemon_layout_matches_pinned_abi` (only the H&S layout). The later stat-stage
   validation rejects any of the 8 bytes from the stage base outside 0..12. On this ROM
   byte `0x18` is `type3` (observed 20), so that check would set `stages_invalid` for a
   correct battler. The `out_stages` path (indexes 1..7) is unaffected.
5. Teardown leaves every battle global and the enemy record stale. Battle state must be gated
   on a live battle signal. Which one (e.g. `gMain` callback / `gBattleMainFunc`) is **NOT_VERIFIED**.

## Tool change

`unbound_observe.py` gains `BATTLE_SYMBOLS`, a battle static span, `literal_references()`,
`battle_mon_fields()`, `compare_battler()` and `Observer.battle_snapshot()`. The snapshot is
paused and read twice, it decodes battlers only within `gBattlersCount` ≤ 4, and it decodes
enemy records whose 80-byte box part is non-zero. Each observation now also stores private
`*_battle.mons`. The existing party, SaveBlock and enemy-count-candidate paths and `SYMBOLS`
are unchanged.

The observer SHA-256 changed from `0c57a1bd…` (attempt 3) to `9f0f1803…`. Both
`unbound-runtime-static.json` and `unbound-runtime-observation.json` preserve the original
producer hash. The observation manifest adds a `revalidated` record binding the current
observer and the battery-route rerun report hash recorded in the battle manifest's regression
section; its scope is battery route only, with the quicksave not rerun. The static manifest
notes that its block predates the battle spans and points here via `unbound-runtime-battle.json`
for the current observer's battle spans. The attempt-3 route (`unbound-continue-summary.movie`)
was rerun with the new observer.
All 26 observations had identical frames, party snapshots, production parser output and
SaveBlock copy rows to the attempt-3 `run04.json`. Only the three animated overworld/menu screen
hashes differed, which the README already documents. Five new ROM-free tests cover the counter,
battler decoding and comparison, snapshot coherence and bounds, zeroed-enemy exclusion, and the
committed movie/manifest binding.

## Reproduction and validation

```bash
P=/home/dq/dualdex-private/unbound
timeout 600 python3 tools/rom-evidence/unbound_observe.py --rom $P/unbound-candidate.gba \
  --core /home/dq/Projects/upstream-hns/mgba-host/mgba_libretro.so \
  --battery-save $P/thor-2026-09-06.sav --movie tools/rom-evidence/unbound-battle-wild-run.movie \
  --private-dir $P/battle-opus/run02 --output $P/battle-opus/run02.json
```

* ROM/core runs, each under `timeout 600` with output in private files: 20 private
  exploration steps (route finding from private states; never saved in game) plus 3 observer
  runs. Those were run01 and run02 of the battle movie (12.0 s and 12.1 s, identical) and run03
  of the continue regression (3.5 s). All exited 0. Total emulator wall time was under 1.5
  minutes. No process outlived its run.
* `TMPDIR=/home/dq/dualdex-private/unbound/battle-opus python3 -m unittest discover -s tools/rom-evidence -v`:
  see the task report for counts. `git diff --check` was clean. `./ci.sh` and Gradle were not run.

Outstanding: a trainer battle (enemy count write, multi-record enemy party), doubles
(`gBattlersCount` 4, positions 2/3), faint/forced replacement, the authoritative in-battle
signal for teardown gating, `gBattleTypeFlags` bit meanings, `gActiveBattler` semantics, the
Android-core behaviour, and the exact release identity.
