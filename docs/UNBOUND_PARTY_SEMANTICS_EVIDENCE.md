# Unbound exact-candidate party semantics: ability, shiny, nature, checksum

Task E1 (orchestrated run `20261106T232257Z-ebca10`), issue
[#60](https://github.com/Sonoran-Solutions/dualdex/issues/60). Inspection and capture
2026-11-06 on base `e6184b2` (`ff51c01` plus the adapter re-pin). This is semantic
evidence for one exact cartridge
(SHA-256 `7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7`, owner claim
v2.1.1.1, release label **NOT_VERIFIED**). It is not a support verdict. Production code
(`native/`, `app/`), profiles and existing evidence files are unchanged.

It builds on the [party layout](UNBOUND_PARTY_LAYOUT_EVIDENCE.md) evidence (fixed G/A/E/M,
unencrypted, zero checksum in 6/6 records) and the
[battle lifecycle](UNBOUND_BATTLE_LIFECYCLE_EVIDENCE.md) evidence (`gBattleMons` at
`0x02023BE4`, 0x58 stride, ability byte `+0x20`).

Evidence files:

* [semantics manifest](../tools/rom-evidence/evidence/unbound-party-semantics.json): input,
  tool and private-report hashes; every inspected span (start, end, SHA-256, finding);
  every pool literal those spans load; the shiny compare immediate; the checksum caller
  census; box geometry; per-slot and per-battler rule checks; and the verdicts. It holds
  numbers, hashes, booleans and fixed descriptions only. It has no names or game text.
* [`unbound_semantics.py`](../tools/rom-evidence/unbound_semantics.py) +
  [`test_unbound_semantics.py`](../tools/rom-evidence/test_unbound_semantics.py): new
  identity-bound inspector and rule checker, plus 11 ROM-free tests. It reuses
  `unbound_observe.pinned`/`ROM_SHA256` and `unbound_data.TABLES`/`names` by import.
  No existing tool was modified.
* Private (not committed), under `/home/dq/dualdex-private/unbound/e1-semantics/` (mode 700,
  no Git ancestor): `battle01.json` (SHA-256 `5f2189f5…a88`) with its RAM/PPM captures, and
  `semantics02.json` (SHA-256 `b66d7fae…6cf7`). The full hashes are in the manifest.

Labels are the same as in the earlier Unbound documents. **VERIFIED** means the exact-ROM
bytes were disassembled and pinned by hash, plus runtime agreement where stated.
**OBSERVED** means seen at runtime or on screen. **NOT_VERIFIED** means the evidence is
insufficient, and **NOT_RUN** means the scenario was not exercised. Public CFRU `b637a27` /
DPE `cdfc053` source was used to name functions and as a cross-check. Wherever it is quoted,
the exact-ROM disassembly agreed with it.

## Verdicts

| Question | Verdict | Rule on this cartridge |
|---|---|---|
| 1. Ability | **VERIFIED** (static + runtime 3/3 battlers + summary 6/6) | `hidden` if IV word bit 31 is set and `base[0x1A] != 0`; else `ability2` if personality bit 0 is set and `base[0x17] != 0`; else `ability1` = `base[0x16]`. `base` = `gBaseStats[species]`, stride 28, table `0x099E0C9C` |
| 2. Shiny | **VERIFIED** (static + summary 6/6, including value 15) | `(tid ^ sid ^ pid_hi ^ pid_lo) < 16`. There is no stored shiny flag. Charm and forced-shiny act only when the Pokémon is generated |
| 3. Nature | **VERIFIED** (static + summary/stat colours 6/6) | `personality % 25`. There is no stored, hidden or mint nature |
| 4. Checksum | **VERIFIED static**; nonzero at runtime **NOT_RUN** | The engine never validates the word. Normal creation, editing and PC storage never write it. One script-only gift-style routine stores the plaintext raw sum, which later edits leave stale. It is not an encryption or validity signal |
| 4b. Box storage | **VERIFIED static** (geometry, header copy); runtime **NOT_RUN** | 25 boxes × 30 slots of 58-byte packed records. The checksum is not stored |

## 1. Ability

Exact-ROM path:

* `0x08040D38` (vanilla `GetMonAbility`) is `ldr r1,=0x089C51B9; bx r1`, a CFRU hook.
  `hooks:335` names it.
* `0x089C51B8..0x089C5220` gets the species with `GetMonData(mon, 11)`. It then tests
  record byte `0x4B` against `0x7F`. That byte is the top byte of the M-block IV word at
  record `+0x48`, so the test reads bit 31 (CFRU `hiddenAbility:1`). It loads
  `gBaseStats` through the pointer `0x080001BC` (literal at `0x089C51D6`). If bit 31 is
  set and `base[0x1A] != 0`, it returns `GetHiddenAbility`. Otherwise it reads the
  personality with `GetMonData(mon, 0)`. If bit 0 is set and `base[0x17] != 0`, it
  returns `GetAbility2`; otherwise it returns `GetAbility1`. This matches CFRU
  `src/build_pokemon.c:GetMonAbility`.
* `0x08A13F1C..0x08A13F70` (`GetAbility1/2/Hidden`): these read `base[0x16]`,
  `base[0x17]` and `base[0x1A]` with `ldrb`, so ability IDs are u8. Each value goes
  through `TryRandomizeAbility` at `0x08A13B44`.
* **Override:** `TryRandomizeAbility` returns the table value unless `FlagGet(0x1522)`
  (literal at `0x08A13B4A`) is set, `FlagGet(0x9F0)` (battle facility) is clear, and the
  ability is not on the ban list. This is a **save-dependent** randomizer, the CFRU
  `FLAG_ABILITY_RANDOMIZER` compiled in with flag `0x1522`. In this save, every observed
  ability equals the table value. The flag's own value and RAM location are
  **NOT_VERIFIED**.
* No species or form special case appears in this path. Forms are separate species IDs
  with their own rows (for example Alolan Muk 1035), so form overrides come from the table.
  Display names have separate species-dependent overrides (see the
  [data/text evidence](UNBOUND_DATA_TEXT_EVIDENCE.md)), and the ability ID is not
  affected by them. In-battle changes (Trace, Skill Swap, and so on) act on
  `gBattleMons[].ability`, not on the party record. They were not exercised.

Runtime (`unbound-battle-wild-run.movie`, 16 observations; the battle struct is
`gBattleMons` `+0x20`):

| Battler | Species word | pid bit 0 | IV bit 31 | Base triple (a1, a2, hidden) | Rule → | Battle byte | Vanilla "bit31 = slot" reading |
|---|---|---|---|---|---|---|---|
| player, party idx 0 | 130 | 0 | 0 | 22, 0, 118 | ability1 = 22 | **22** (8 obs) | 22 |
| enemy, wild | 646 | **1** | 0 | 11, 120, 6 | **ability2 = 120** | **120** (13 obs) | 11 ✗ |
| player, party idx 2 | 186 | 0 | **1** | 11, 6, 2 | **hidden = 2** | **2** (5 obs) | 6 ✗ |

The wild Jellicent is the first discriminating record for `personality & 1` choosing
ability 2. The earlier evidence marked that rule **NOT_VERIFIED** because all five
even-PID party records had no case that tested it. Politoed confirms the hidden-ability
bit. The summary-screen transcription in the committed party manifest also agrees for
6/6 slots: the ROM default name of each predicted ID equals the transcribed ability. Litten
(pid bit 0 = 1, `base[0x17] = 0`) exercises the empty-ability2 fallback.

## 2. Shiny

* `0x0804449C` (`IsShinyOtIdPersonality`, not hooked) XORs the four halfwords, then runs
  `cmp r2,#0xF; bhi`. The result is shiny exactly when the value is **< 16** (vanilla
  FireRed uses `cmp #7`). `IsMonShiny` (`0x08044470`) only fetches the OT ID and
  personality and calls this function. No stored bit is read.
* The test has 1 BL caller (`IsMonShiny`) and 10 aligned literal references (CFRU code).
  `IsMonShiny` has 5 BL callers and 22 literal references. An inline copy of the test
  elsewhere cannot be excluded statically, but the summary screen agrees.
* Summary star versus the rule over 6/6 slots: values 15 and 5 show stars. Values 50580,
  2596, 41573 and 16064 do not. Value 15 is the discriminating case: the vanilla `< 8`
  rule fails it.
* Generation only: CFRU `CreateBoxMonHook` (exact ROM `0x0803DAD2` → `0x089A43C4` →
  inserted `CreateBoxMon`, return `0x0803DD85`). In source, the shiny charm adds two
  extra rerolls, the fishing streak adds more, and `FLAG_SHINY_CREATION` forces a shiny.
  All of them rewrite the **personality** (`ForceMonShiny`). They do not set a flag, so
  the stored-record test above stays complete. The charm and streak details are
  source-only (**NOT_VERIFIED** in exact-ROM code; this does not affect reading).

## 3. Nature

* `0x08042E9C` `GetNature` is `GetMonData(mon, 0) % 25` (unsigned-modulo helper at `0x081E4684`, the vanilla `__umodsi3` address).
  `0x08042EB4` `GetNatureFromPersonality` is `p % 25`. Neither is hooked.
* Stats: `0x0803E47C` `CalculateMonStats` is hooked to `0x089C4474`. That function loads
  `GetNature` (literal load at `0x089C456E`), so stat nature is also `pid % 25`. CFRU
  `b637a27` has no mint or hidden-nature field. Prior evidence confirmed the nature for
  6/6 slots and the stat colours.
* A hypothetical in-game "nature changer" would have to rewrite the personality, as
  `ForceMonShiny` does. Under this rule a reader stays correct.

## 4. Checksum and box storage

`CalculateBoxMonChecksum` (`0x0803E3E8..0x0803E47C`) is unchanged vanilla: a 16-bit sum
of the 24 G/A/E/M halfwords. The substructure lookup forces fixed order, so the sum
covers plaintext. Exact-ROM reference census: **2 BL sites** (`0x0803DB9E`, `0x08040AE2`)
and **1 literal word at any alignment** (`0x08825C98`).

| Site | Effect |
|---|---|
| `0x0803DB9E` (vanilla `CreateBoxMon` body) | Unreachable from the function entry. The hook at `0x0803DAD2` branches away and resumes at `0x0803DD85`. The CFRU `CreateBoxMon` replacement never sets `MON_DATA_CHECKSUM`, so new Pokémon keep the zero from `ZeroBoxMonData` |
| `0x08040AE2` (`SetBoxMonData` tail, fields > 10) | Computed, then discarded. The store at `0x08040AE6` is a no-op (`##Decryption##` in CFRU `bytereplacement`) |
| `0x0804051E..0x08040532` (`SetBoxMonData`) | Validation replaced by no-ops plus a branch. `GetBoxMonData` validation is also no-oped (`0x0803FD94`, prior evidence). No Bad Egg path from a mismatch |
| `0x08825A98` (inserted routine, entry `0x08825A30`) | **Writes it**: `SetBoxMonData(mon, 9, &CalculateBoxMonChecksum(mon))`, then `EncryptBoxMon` (no-op). The routine fills a Pokémon from script variables (`0x020370B8`…), computes stats and calls `0x08040B15` (the vanilla `GiveMonToPlayer` address; name **NOT_VERIFIED**). The only occurrences of its address are **10 script `callasm` (0x23) operands** (`0x087404C5` … `0x088CEE97`). There are no BL callers |

Meaning: a **nonzero** checksum can legitimately appear on a Pokémon created by one of
those 10 scripted events (gift-style), if the Pokémon went to the party and was never
deposited in the PC. At creation it equals the **plaintext raw sum**. Every later edit
(EXP, moves, friendship, PP) goes through `SetBoxMonData`, which no longer stores the
checksum, so the word becomes stale. It never means "encrypted" and never means
"invalid". The record is still fixed-order plaintext. Runtime: all 6 party records and
the wild enemy record read `0x0000` in this run (and in the prior evidence). No nonzero
record exists in this save, so the nonzero case is **NOT_RUN**. The missing input is a
capture of a party Pokémon received from one of the 10 scripted events.

Box storage (**VERIFIED static**, exact ROM):

* `GetBoxMonDataAt` (`0x0808BA18`) is hooked to `0x08A04954`. It bounds the box below 25
  and the position below 30, uses record stride **`0x3A` = 58 bytes**, takes the box base
  from a 25-entry pointer table at `0x08A6CB2C`, and reads the species halfword at `+0x1C`.
  The box bases are listed in the manifest. Boxes 1–19 are contiguous from `0x02029318`;
  boxes 20–25 are spread over other RAM, including `0x0203CB44` and `0x02024638`.
* Expanding a box record to a `BoxPokemon` (`0x08A0478C`) zeroes 0x50 bytes, copies header
  bytes `0x00..0x1B` (personality … markings, **checksum `+0x1C` excluded**), 11 G bytes to
  `+0x20`, 6 EVs to `+0x38` and 8 misc bytes (pokérus, met location/info, **IV word with
  egg and hidden-ability bits**) to `+0x44`. In source, PP are then recomputed from the
  moves and PP-ups; the pinned exact-ROM span ends before that loop.
  The packed 10-bit move fields and the `metInfo` packing come from CFRU
  `include/new/pokemon_storage_system.h` (source-only). So PC records carry no checksum,
  and a withdrawn Pokémon always has checksum 0. Contest stats and ribbons are dropped.

## Reproduction

```bash
export TMPDIR=<private tmp>   # /tmp has a stray .git on this host
P=/home/dq/dualdex-private/unbound; E=$P/e1-semantics
./tools/vanilla-runtime-probe/build.sh            # adapter d693404b…, accepted by verify_adapter()
timeout 600 python3 tools/rom-evidence/unbound_observe.py --rom $P/unbound-candidate.gba \
  --core /home/dq/Projects/upstream-hns/mgba-host/mgba_libretro.so --battery-save $P/thor-2026-09-06.sav \
  --movie tools/rom-evidence/unbound-battle-wild-run.movie --private-dir $E/battle01 --output $E/battle01.json
timeout 600 python3 tools/rom-evidence/unbound_semantics.py --rom $P/unbound-candidate.gba \
  --battle-report $E/battle01.json > $E/semantics02.json
```

* Emulator: one run (16.2 s, exit 0, 16 observations `OBSERVED`), under `timeout 600`.
  Observer `32c673bc…` (current), adapter `d693404b…` (source `ff51c01`), core `fc7394f7…`,
  battery `a561cab2…`, movie `607d75f9…`. Compared with the battle-lifecycle `run02`: frames
  and player-party digests matched 16/16. `gBattleMons` digests matched 11/16. In the 5
  observations after the switch, the only differing bytes were battler 0's `0x23` (unused)
  and `0x36..0x3A` (name buffer after the terminator). Every compared field and the ability
  byte matched. So the earlier "identical digests" statement holds only up to the switch.
* Inspector: two runs (`semantics01`, then `semantics02` after adding the `callasm` census),
  1.2 s each, with the same party and battle rows. The committed manifest comes from
  `semantics02.json`.
* Static disassembly used local Capstone 5.0.7 in a private scratch script. The committed
  inspector needs no disassembler: it pins span hashes and decodes literals, one compare
  immediate and BL/`callasm` encodings directly.

## Production implications (recommendations only; nothing changed)

For `native/src/pokemon_reader.c`, `pokemon_parse_single_layout()` with
`PKMN_STORAGE_CFRU`:

1. **Storage/checksum branch.** Today the plaintext fixed-GAEM path is taken only when
   `raw->checksum == 0`. A nonzero checksum goes to XOR + `pid % 24` + checksum
   verification and fails closed. On this cartridge, a party Pokémon from one of the 10
   scripted gift events carries the plaintext raw sum. That sum goes stale after its first
   EXP or move change. Such a Pokémon would be **rejected** (a false negative, not wrong
   data). Recommendation: for CFRU, always decode fixed-order plaintext and ignore the
   checksum, as the engine does. A narrower alternative is to accept `0` or `== plaintext
   raw sum`, but that still rejects a gift Pokémon that has since been edited. Before
   widening, capture one such Pokémon at runtime (the missing input above). Keep the other
   fail-closed checks (species domain, empty record).
2. **`ability_slot_known` / `ability_num`.** Set `ability_slot_known = true` for CFRU with
   `ability_num = (iv_word >> 31) ? 2 : (pid & 1)`, which uses the expansion meaning
   (2 = hidden). Document the fallback that consumers must apply with the CFRU base table
   (`0x16/0x17/0x1A`, stride 28): hidden with `base[0x1A] == 0` falls back to the
   personality rule, and slot 1 with `base[0x17] == 0` falls back to slot 0. The reader has
   no base table, so it should not map to an ability ID itself. Caveat: save flag `0x1522`
   randomizes the ID. When a battle is live, `gBattleMons[].ability` (`+0x20`) is the
   authoritative effective ability.
3. **`shiny_state` / `is_shiny`.** Replace `PKMN_SHINY_UNKNOWN` with
   `shiny_value < 16 ? PKMN_SHINY_YES : PKMN_SHINY_NO` and set `is_shiny` to match. Do not
   use a modifier bit (none exists). This fixes slot 1 (value 15), which the old vanilla
   `< 8` rule got wrong.
4. **Nature.** No change: `nature = pid % 25`, `hidden_nature = nature`,
   `nature_modified = false` is already right for CFRU.
5. **Boxes (if PC reading is ever added).** Use the 58-byte packed format through the box
   pointer table, not 80-byte `BoxPokemon` records, and expect no checksum field.

Outstanding: a runtime capture of a nonzero-checksum (scripted gift) Pokémon; the RAM
location and owner state of randomizer flag `0x1522`; exact-ROM confirmation of the
charm/streak generation code; box-record runtime decoding; in-battle ability changes; the
exact release identity.
