# R.O.W.E. 2.1.9.1 Data and Text Evidence

Schema version: 1

Tracking issue: [#59](https://github.com/Sonoran-Solutions/dualdex/issues/59)

Starting `main`: `7b9fd6ff920846cd848cc0de593005eb67c8fd2a`
Reference source: [PokeDaisy `gen_rowe_tables.py` at pinned commit `eddffd90979ec12e6425e1d96c7aca41d5783b61`](https://github.com/lidor30/pokedaisy/blob/eddffd90979ec12e6425e1d96c7aca41d5783b61/scripts/gen_rowe_tables.py) (GPL-3.0)

This is exact-ROM static extraction evidence, not a production profile or a claim of full
gameplay semantics. The locally inspected ROM identified as **Pokémon R.O.W.E. 2.1.9.1
Experimental** has size 33,554,432 bytes, SHA-1
`81bd0f4bfa1c04ab2c6faab1bddd10e8a390ea77`, and SHA-256
`fdf6a69e9556efd4b7ff96829c0fba85072e6d91c3ec96faed744765c4a772d3`. The ROM remains private
and is not included in this repository. The exact release's original source tree, ELF, and build
provenance were not obtained, so those are **NOT_VERIFIED**.

PokeDaisy was used only as a lead and comparison. Its generator and generated tables were not
copied. DualDex independently checks the ROM hash, embedded symbols, accessor instructions,
bounded spans, record indexes, text decoding outcomes, and region digests. The machine-readable
sanitized report is [`rowe_data_contract.json`](../tools/rom-evidence/rowe_data_contract.json);
generation is described in [`tools/rom-evidence/README.md`](../tools/rom-evidence/README.md).

## Evidence matrix

| Area | Exact ROM source | PokeDaisy lead | Independent evidence | Status | Remaining gate |
|---|---|---|---|---|---|
| Species names | Embedded `gSpeciesNames` at `0x084180A4`; `gMoveNames` at `0x0841E42C`; name accessor at `0x08016970` multiplies the index by 13 | Address and 13-byte rows | Symbol span is exactly 25,480 bytes / 1,960 rows. 1,464 nonzero IDs have valid names; 495 rows are empty, including holes from 1026 through 1959. Example IDs 161 Sentret and 661 Fletchling match the #192 runtime/UI observations. | **STATIC_VERIFIED**; those two examples are also **RUNTIME_OBSERVED** | Keep holes and ID zero unsupported; no production profile activation |
| Move names | Embedded `gMoveNames` at `0x0841E42C`; accessor at `0x080782A4` uses ID × 17 | Address and 17-byte rows; generator uses empty-name stopping logic | Scanned every ID representable by the stored 10-bit move field, 0–1023. ID 0 is reserved; IDs 1–942 form the safe terminated prefix. ID 943 is the first invalid row. IDs 943–1023 are refused, even where later bytes resemble text. IDs 33, 45, 64, 98, 111, and 193 match #192 party/UI observations. | **STATIC_VERIFIED** safe prefix; examples also **RUNTIME_OBSERVED** | Runtime display cross-check for the remaining rows is not required for static lookup but is needed before broader support claims |
| Form identity | Exact code at `0x080A1874` indexes a pointer table at `0x085E700C` by species × 4, then reads a `u16` list by form × 2; `0xFFFF` ends the list. Null pointer returns the stored species ID. | Candidate table location and form species IDs beginning around 1551 | The candidate address is referenced by exact-ROM code but is not named in the 150-entry embedded symbol table. All 1,960 pointer slots were bounded; 600 have tables, containing 3,472 choices (including form zero), with 2,872 nonzero-form choices and 388 distinct alternate target IDs. Targets resolve to valid name rows. Rattata stored ID 19/form 1 statically maps to ID 1551, name candidate “Rattata”, type IDs `[17, 0]`. Other alternate targets begin at ID 1501. | Mapping algorithm **STATIC_VERIFIED**; PokeDaisy's “starting around 1551” as the first alternate output is **CONTRADICTED**; nonzero-form runtime behavior **NOT_VERIFIED** | No nonzero form may be displayed as verified until observed in gameplay. The game accessor has no form-list bounds check; invalid indexes must refuse. Egg ID 2300 semantics are **NOT_VERIFIED** and outside the name-table bound. |
| Base stats and species types | Embedded `gBaseStats` at `0x08502C54`; code at `0x080A192C` scales species ID by 64 and reads type bytes at `+0x0C/+0x0D` | 64-byte rows; types at `+0x0C/+0x0D`; other candidate fields | Exact symbol, scale, load offsets, and type ID domain were checked against the exact ROM. Type fields for all 1,464 named nonzero species rows were checked; values observed are 0–8 and 10–18. | **STATIC_VERIFIED** for stride and type fields | Numeric type IDs are established; a type-ID-to-label mapping, live ability slot, ability suppression, gender, and other base-stat field semantics remain **NOT_VERIFIED** |
| Battle move records | Embedded `gBattleMoves` at `0x08435298`; exact accessors scale IDs by 56 and load power at `+2` and type at `+3` | 56-byte rows; power `+2`, type `+3` | Pointer literals, multiply-by-56 instruction sequences, and field loads at `0x080440AA` / `0x08042266` were checked. The named subset IDs 1–942 fit the checked span; type IDs are 0–8 and 10–18. | **STATIC_VERIFIED** for named prefix and these fields | Static values do not establish battle-effective power/type. Effects, flags, abilities, held items, weather, and calculator behavior are out of scope. |
| Party nickname text | The #193 raw accessor copies exactly 12 bytes from party offset `+8`; observed raw examples are retained in the contract | `0xFF` terminator and selected glyphs | The bounded decoder recognizes the observed common Gen III glyph subset, digits, punctuation, gender symbols, and `é`. It requires exact domain widths for 13-byte species names, 17-byte move names, 18-byte item names, and 12-byte nicknames. It accepts a full-width nickname only when all 12 bytes are known glyphs. It preserves raw bytes and refuses unknown glyphs, malformed controls, invalid termination, and unsupported domains. Synthetic tests exercise these cases. | Width/copy **STATIC_VERIFIED**; decoder **SYNTHETIC_TESTED** | Actual UI max display length, a runtime full-width unterminated nickname, control-token rendering, and egg-name behavior are **NOT_VERIFIED**. The supported glyph map is intentionally partial. |
| Stored/effective nature, moves, level | Existing exact-ROM party accessor spans and #193 field extraction | Mode-dependent changes may occur | Stored nature is a 5-bit value; stored move IDs are 10-bit; stored level is a byte. Inspected accessors contain conditional paths for moves/level, but their triggers and effective results are not established. No neighboring bits are interpreted as a mode flag. | Stored fields **STATIC_VERIFIED**; effective values **NOT_VERIFIED** | Refuse effective values when mode/override status is unknown. Abilities remain **NOT_VERIFIED**. |
| Items | Embedded `gItems` at `0x08F76D78`; exact accessor clamps IDs above 999 to item 0 and scales accepted IDs by 56 | 56-byte rows and inline strings | Exact accessor clamp, ×56 index, and table pointer were checked. For IDs 0–999, 844 record ID fields match their index; inline-name statuses are 843 valid, 156 empty, and one unknown glyph. Five representative rows had in-ROM description pointers and valid names. | Bounded structure **STATIC_VERIFIED**; broader IDs **THIRD_PARTY_LEAD** only | Record/index mismatches and unsupported names refuse. Item effects and a complete supported item set remain **NOT_VERIFIED**. |
| Type effectiveness | Candidate at `0x0842C182`; exact battle accessor references the table and selects the normal path | 19 × 19 `u16` Q4.12 matrix; alternate table nearby | The bounded normal matrix contains 361 cells with values `0x0000`, `0x0800`, `0x1000`, and `0x2000`; both normal and adjacent alternate spans were hashed. A conditional code path references the adjacent region. | Normal table/path **STATIC_VERIFIED**; alternate trigger **NOT_VERIFIED** | No damage calculation or battle-effectiveness claim. |
| Region-map entries | Embedded `gRegionMapEntries` at `0x08FD278C`; candidate records are 8 bytes | 213 records, name pointers, multiple regions | Within the 213-record lead window, all 213 nonzero name pointers land in ROM; 212 bounded labels decode and entry 66 is an unsupported control-coded label. No pointer was out of range. | Pointer/string checks **STATIC_VERIFIED**; count 213 **THIRD_PARTY_LEAD** | The exact accessor has not established the table count or runtime region/mode selection. No map UI, image, or cursor behavior is claimed. |

## Lookup and refusal contract

`rowe_data.py` accepts only the exact size and both exact ROM hashes. All reads are ROM-base
bounded. Species and move lookup return a name only for a verified record; reserved zero, holes,
out-of-range IDs, invalid records, and absent provenance refuse without global fallback. The move
lookup is limited to IDs 1–942. Static form resolution keeps the stored species ID, stored form,
resolved name-table candidate, and metadata key distinct; it does not claim that a nonzero-form
candidate is the runtime identity. Invalid form IDs, malformed lists, and unresolved targets
refuse. The tool does not use type labels, generic Emerald/H&S tables, or PokeDaisy output as
fallbacks.

Safe future name lookups from this evidence are the exact-ROM species rows with `VALID_TEXT` and
the exact-ROM move prefix 1–942. The independently UI-correlated examples are species 161/form 0,
species 661/form 0, and move IDs 33, 45, 64, 98, 111, and 193. These are evidence contracts only;
the repo still has no R.O.W.E. production profile. Nonzero-form runtime identity, nickname display
policy, type labels, and all mode-dependent values must continue to refuse as unsupported.

## Reproduction and scope

With an authorized local copy of the exact ROM:

```bash
python3 tools/rom-evidence/rowe_data.py --rom /private/rowe-2.1.9.1.gba \
  > /tmp/rowe-data-contract.json
python3 -m unittest discover -s tools/rom-evidence -v
```

The committed JSON is sanitized: table/source spans are represented by digests, counts, bounds,
status classes, and selected previously observed nickname bytes; it contains no ROM bytes or full
extracted game-data tables. The private ROM run is distinct from the ROM-free synthetic tests.
Passing synthetic tests does not establish exact-ROM or runtime semantics. No source/ELF
provenance was available for the closed exact release. There is no production integration,
trusted-ROM registration, battle calculator, item effect, enemy-party reader, or map feature in
this evidence task.
