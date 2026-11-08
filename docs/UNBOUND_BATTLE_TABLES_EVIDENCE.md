# Unbound static battle-table evidence

Phase 0.2 a–c, exact candidate only. The generated
[numeric manifest](../tools/rom-evidence/evidence/unbound-battle-tables.json)
pins ROM SHA-256
`7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7`,
33,554,432 bytes, GBA base `0x08000000`.

**VERIFIED** means measured from that binary by the stated method.
**SOURCE_APPROXIMATE** identifies a hypothesis from CFRU commit
`b637a27898b14e25dd24d0f69a3e302f0069deb8`; source agreement alone is not ROM-code
proof. **UNVERIFIED** means the stated claim has not been established.
This is static evidence, not a release-identity or calculator-support verdict.

## Method and storage spans

The read-only [inspector](../tools/rom-evidence/unbound_battle_tables.py) first uses
`exact_rom()` to reject any other size or SHA-256. It reuses the existing private-directory
guard, bounded spans, Thumb PC-literal decoder and literal-reference search. Regeneration
needs no private source checkout, disassembler, emulator, ROM text decoder or network.

Discovery used Capstone 5.0.7 in Thumb mode. Each field below was traced from a
table literal through the index arithmetic to the consuming load; the manifest pins
the reviewed instruction spans by address, size and SHA-256. Regeneration checks those
hashes, decodes byte loads and stride constants, verifies all table-pointer links, and
rechecks selected loop/sign-extension instructions. A nearby literal count alone is
not treated as a field proof. No instructions are executed.

| Table | Status | File offset | GBA address | Records × stride | Bytes |
|---|---|---|---|---|---|
| `gBattleMoves` | **VERIFIED** | `0x00A769AF` | `0x08A769AF` | 923 × 12 | 11,076 |
| `gSpecialMoveFlags` storage | **VERIFIED** | `0x00A7E266` | `0x08A7E266` | 923 × 7 | 6,461 |
| `gTypeEffectiveness` | **VERIFIED** | `0x00A6959E` | `0x08A6959E` | 24 × 24, one-byte entries | 576 |
| `base_stats` | **VERIFIED** | `0x019E0C9C` | `0x099E0C9C` | 1,294 × 28 | 36,232 |
| `items` | **VERIFIED** | `0x00876200` | `0x08876200` | 729 × 44 | 32,076 |

| Table | VERIFIED span SHA-256 |
|---|---|
| `gBattleMoves` | `ba323b25b132a23523a194625a8a5f8bdf28e405349aa8ec0d922a435b9f6cd2` |
| `gSpecialMoveFlags` | `392473a9ae2bfc2ab96a6c66ea42882393c4c9f043ed84c70a5765f0ab3ab7ad` |
| `gTypeEffectiveness` | `998b13d8e17de1898a79ecd1d3ec84b539cbcc338f0441cf559d63a14012b34f` |
| `base_stats` | `beed70bc8ff0136bec0a8ba1ca3fcd1128a5b25a4d7f79155232ee92c97e13c1` |
| `items` | `95016c36d620f7c592f4d33011fc373a8206fef0c6eb4a08806f076615b7ed18` |

**VERIFIED storage extents:** move, special-flag and chart start literals are loaded
at `0x0800E338`, `0x089A952E` and `0x089CEE4E`. Independently referenced adjacent
table starts are loaded at `0x089C74B6`, `0x089B7730` and `0x089D377A`, respectively.
Their gaps, divided by the measured strides, give 923, 923 and 24 rows. This pins
contiguous storage extents; it does not prove every caller validates its IDs.
The move pointer also equals the word at `0x080001CC`.

**VERIFIED strides:** `0x089BA97A` supplies 12 to the move-index multiply;
other consumers use `(id*2 + id)*4`. `0x089A9528` supplies 7 for special flags.
`0x089CEE46` supplies 24 for chart row indexing. `0x089C4512` supplies 28 for
base stats. `0x089F3370` supplies 44 for items. The base-stat count reuses the
[existing static contract](../tools/rom-evidence/evidence/unbound-data-contract.json).
The item accessor compares against `0xB6 << 2 = 728`, replacing larger IDs with 0,
independently confirming the 729-record domain.

## Move field consumers

All fields below are **VERIFIED**, width one byte. The load PCs and full consumer-span
hashes are also machine-readable in the manifest. Omitted fields are **UNVERIFIED**.

| Field | Offset | ROM load PC | Traced use |
|---|---|---|---|
| `effect` | `+0x00` | `0x0801633E` | Current move ×12; byte indexes a four-byte effect dispatch table |
| `power` | `+0x01` | `0x08015FFA` | Current move ×12; power tested against 0 |
| `type` | `+0x02` | `0x0800E344` | Move ×12; byte copied to the outgoing battle data at `+0x0B` |
| `accuracy` | `+0x03` | `0x08028A26` | Move ×12; byte added to the level difference before the bounded random comparison |
| `target` | `+0x06` | `0x08015F78` | Current move ×12; target tested against 0 |
| `priority` | `+0x07` | `0x089BA98C` | Move ×12; ability adjustments; returned through `LSL 24; ASR 24` at `0x089BA9CC` |
| `flags` | `+0x08` | `0x0801AAC6` | Move ×12; bit 0 mask tested |
| `z_move_power` | `+0x09` | `0x089D0738` | Helper move ID ×12; alternate branch reads signature power from `+1`; common power-result path |
| `split` | `+0x0A` | `0x089F7436` | Move ×12; category byte indexes a 192-byte icon span |

**VERIFIED numeric checks:** all 923 rows have type IDs below 24, accuracy at most
100, and split values in `{0,1,2}`. Priority is decoded as a signed byte; its measured
range is recorded in the manifest. These checks corroborate the loads, not replace them.

## Special flags and type chart

`gSpecialMoveFlags` was initially found by comparing a numeric packed hypothesis made
from the private public-source checkout's declarations and flag-list input. All 6,461
bytes agreed (**VERIFIED equality; SOURCE_APPROXIMATE semantic authority**). No source
or numeric table fixture was copied into the repository. The committed locator instead
follows ROM consumers and the adjacent-table boundary.

**VERIFIED raw layout:** seven addressable bytes per move. `0x089A9532` reads byte 3,
then shifts right by 7 and tests it. `0x089A5982` reads byte 2, then shifts left by 27
and branches on its sign, testing bit 4. Both paths multiply a move ID by 7 before
adding the same table base. The complete mapping from named source flags to bits is
**UNVERIFIED**; the manifest deliberately provides no such mapping. Bit names must be
audited against further consumers before use as calculator policy.

**VERIFIED chart layout:** `0x089CEE44..0x089CEE90` computes `table + 24*row`, then
loads bytes indexed by two column IDs at `0x089CEE52` and `0x089CEE54`. The storage
extent gives 24 rows and 24 columns, IDs `0..23`, including row/column 23. The numeric
value set is exactly `{0,1,5,20}`. No multiplier scale is inferred from this set.
Row and column `0x14` both contain only 0 (**VERIFIED blank storage handling**).
The source labels for ID 23 (Fairy) and ID `0x14` (blank) remain
**SOURCE_APPROXIMATE** naming; their numeric positions and contents are **VERIFIED**.
Blank-type gameplay semantics and all type-modulation rules beyond these selected
indexing paths are **UNVERIFIED**.

## Base-stat and item field consumers

| Fields | Status | ROM proof |
|---|---|---|
| `hp +0x00` | **VERIFIED** | `0x089C450E..0x089C451A`: dereference table cell, species ×28, byte load at `0x089C4518`; feeds HP arithmetic |
| `attack +0x01`, `defense +0x02`, `speed +0x03`, `sp_attack +0x04`, `sp_defense +0x05` | **VERIFIED** | `0x089C46B2..0x089C4778`: species ×28 held in `r7`; loop `r4=1..5` reads `table[r7+r4]` at `0x089C472C`; stat calculation writes selectors `r4+0x3A` (`0x3B..0x3F`) |
| `type1 +0x06`, `type2 +0x07` | **VERIFIED** | `0x08012F20..0x08012F4E`: `(species*8-species)*4`; loads at `0x08012F2C`/`0x08012F48` copied to battler bytes `+0x21`/`+0x22` |
| `ability1 +0x16`, `ability2 +0x17`, `hidden_ability +0x1A` | **VERIFIED** | `0x08040D7A..0x08040DA6`: `(species*8-species)*4`, indirect table cell, hidden/alternate/default branch loads at `0x08040D8A`, `0x08040D96`, `0x08040D9E` |
| `holdEffect +0x12` | **VERIFIED** | Hook at `0x0809A924` targets `0x089F3360`; clamp ID, ×44, dereference `0x080001C8`, load at `0x089F3378` |
| `holdEffectParam +0x13` | **VERIFIED** | Same clamp/×44/table-cell path at `0x089F3380`, load at `0x089F3398`; independently, accessor `0x0809A948` loads the direct table literal and reads `+0x13` at `0x0809A95E` |

**UNVERIFIED:** ability alias/override resolution, held-item gameplay semantics, complete
special-flag bit meanings, and damage arithmetic. Base-stat abilities here are declared
one-byte values, not a claim that those bytes alone resolve every effective ability.

## Regeneration and ROM-free validation

Set `PRIVATE_ROM` to the exact private candidate and `SCRATCH` to the task scratch
directory; neither private input is copied. Only the JSON report is written.

```sh
export TMPDIR="$SCRATCH"
python3 -m unittest discover -s tools/rom-evidence -p 'test_unbound_battle_tables.py' -v
python3 -m unittest discover -s tools/rom-evidence -p 'test_unbound_*.py'
python3 tools/rom-evidence/unbound_battle_tables.py --rom "$PRIVATE_ROM" --out tools/rom-evidence/evidence/unbound-battle-tables.json
python3 tools/rom-evidence/unbound_battle_tables.py --rom "$PRIVATE_ROM" --out "$SCRATCH/unbound-battle-tables-repeat.json"
cmp tools/rom-evidence/evidence/unbound-battle-tables.json "$SCRATCH/unbound-battle-tables-repeat.json"
```

The new tests construct a 1,024-byte invented image in memory. They cover literal
alignment/indirection and span boundaries, count mismatches, numeric record/chart
parsers, signed priority, instruction decoding, consumer-hash mutation, size/SHA refusal
before interpretation, the private-directory guard, and preservation of existing output
on failure. No real ROM is needed for either unittest command. JSON is sorted and has
no timestamps, host paths, game text, raw table dumps or disassembly bytes.

Validation on baseline `7e6fff1` plus the new inspector/tests: focused discovery
reported `Ran 11 tests` / `OK`; the final `test_unbound_*.py` sweep reported
`Ran 94 tests` / `OK` (exit 0, 2.354 seconds, 120-second deadline). Printed negative
fixture diagnostics in that sweep were expected. Both real-ROM CLI invocations
exited 0 and produced byte-identical JSON, SHA-256
`0b0abfa246e17b80106d45c62d1b8f3385843cd488a7df13689db25b662f68e2`.
The subsequent change only records these results in this inert document; it does not
invalidate the test or generator evidence. CI, Gradle and emulation were not run.
