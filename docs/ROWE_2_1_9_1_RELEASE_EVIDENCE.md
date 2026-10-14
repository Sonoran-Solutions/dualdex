# R.O.W.E. 2.1.9.1 Experimental — release evidence revision 2

Audit: **2026-10-13 UTC / 2026-10-12 America/Phoenix**. Scope: exact-release identity and
independent static/runtime observations for [#59](https://github.com/Sonoran-Solutions/dualdex/issues/59).
Baseline: `5148bf28dc08cdd0f093a9741f420f6f24ca313a` (main when this branch was created).
The evidence branch was subsequently rebased onto `94e21aeeb2f85ae5036f6ce4e79a7514c1c1c431`
to preserve main's newly added native fixture suite alongside its CI hook.
This branch is independent of unmerged [PR #189](https://github.com/Sonoran-Solutions/dualdex/pull/189).
Its [revision-1 matrix](https://github.com/Sonoran-Solutions/dualdex/blob/f5aa9b0aaf91e685fbcb1f81243604ea3e8a274b/docs/ROWE_2_1_9_1_SPIKE.md)
is the starting hypothesis, not the authority for the new observations.

**Decision: proceed with bounded evidence work; do not enable production integration.** Exact
patch-to-ROM identity is now reproduced. Embedded symbols and several slot-zero fields have
independent evidence from that ROM. Neither a complete 76-byte ABI nor a safe general party
reader is established. No profile, production hash registration, memory permission, game-data
pack, native production decoder or Android build target changes. #59 stays open.

## 1. Provenance and the CRC discrepancy

The publisher's [pinned patch selection](https://github.com/BelialClover/Rowe-Patcher/blob/8c38977519f0a3c1726bf75567f718fee5581cca/index.html#L66-L76)
names **Pokemon ROWE v2.1.9.1 - Experimental** and selects
`patches/pkmn_rowe_experimental.ups`. The separate direct-download BPS button is a different
artifact. “Expansion” is not the independently established release label. Runtime's menu says
`v2.1.9.1 (Exp.)`, which corroborates the version but does not independently expand that abbreviation.

The configured `d5ec24e4` CRC and UPS source `1f1c08fb` are **not competing active validators**:

1. [`parseCustomPatch`](https://github.com/BelialClover/Rowe-Patcher/blob/8c38977519f0a3c1726bf75567f718fee5581cca/js/RomPatcher.js#L106-L125)
   installs the custom CRC validator only when the parsed format has no `validateSource`.
2. [UPS already defines that method](https://github.com/BelialClover/Rowe-Patcher/blob/8c38977519f0a3c1726bf75567f718fee5581cca/js/formats/ups.js#L54-L58)
   using the patch's input CRC. Therefore the generic `d5ec24e4` configuration is unused for this
   UPS. Its historical origin remains unknown; there is no evidence it identifies another
   required base or a different experimental revision.
3. The [UI validator](https://github.com/BelialClover/Rowe-Patcher/blob/8c38977519f0a3c1726bf75567f718fee5581cca/js/RomPatcher.js#L543-L555)
   marks a mismatch with a warning. The [Apply handler](https://github.com/BelialClover/Rowe-Patcher/blob/8c38977519f0a3c1726bf75567f718fee5581cca/js/RomPatcher.js#L457-L459)
   supplies `false` for checksum validation; [worker/fallback dispatch](https://github.com/BelialClover/Rowe-Patcher/blob/8c38977519f0a3c1726bf75567f718fee5581cca/js/RomPatcher.js#L673-L693)
   propagates it. UPS input/output checks are conditional, although its
   [patch CRC check](https://github.com/BelialClover/Rowe-Patcher/blob/8c38977519f0a3c1726bf75567f718fee5581cca/js/formats/ups.js#L145-L176)
   is unconditional. A successful browser download alone would not prove a correct output.

This is a static control-flow audit of that pin, not a claim about every deployed patcher version.
The independently written [UPS verifier](../tools/rom-evidence/ups_identity.py) always checks
all three CRCs plus the pinned patch SHA-256, source/output sizes and bounded XOR records.
No upstream implementation is imported or vendored. The local base and candidate were supplied
from the user's Downloads directory with explicit local-testing authorization. No ROM was
downloaded. The verifier constructed the output in memory and compared **every byte** with the
local candidate; equality passed. Input files were not altered and no derived ROM was saved.

### Exact identities

Machine-readable result: [patch-identity.json](../tools/rom-evidence/evidence/patch-identity.json).

| Property | Base ROM | Reproduced Experimental output |
|---|---|---|
| Length | 16,777,216 | 33,554,432 |
| CRC32 | `1f1c08fb` | `ad252fd3` |
| SHA-1 | `f3ae088181bf583e55daf962a92bb46f4f1d07b7` | `81bd0f4bfa1c04ab2c6faab1bddd10e8a390ea77` |
| SHA-256 | `a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af` | `fdf6a69e9556efd4b7ff96829c0fba85072e6d91c3ec96faed744765c4a772d3` |

Patch: 30,599,197 bytes, SHA-256
`41431249ec06033e77950603be22091bd0ea465dbf573a645eec642d397d26f9`,
stored/recomputed patch CRC `e31147fe`. Output header: title `POKEMON EMER`, game code `BPEE`,
maker `01`, revision 0, fixed byte `0x96`, complement checksum `0x72` (valid). The retained
Emerald header cannot itself distinguish this hack/version. Provenance binds the bytes to the
publisher's pinned **label and patch**, not to an unavailable source build or compiler recipe.
The third-party SHA-1 prefix `81bd0f4b...` is consistent with this full identity but still cannot
prove the provenance of that project's captures.

Exact 2.1.9.1 source, compiler flags and publisher ELF/map remain **NOT_VERIFIED**. The public
[Iamdivinefox source](https://github.com/Iamdivinefox/Rowe/blob/e8c86a4a6119376e41771c3cbf99178633e63c58/README.md)
and creator-linked [BelialClover/RoweSource](https://github.com/BelialClover/RoweSource/blob/292eab691310e8b0cdb91a3905c34105261e24e1/README.md)
describe **1.9.4 Source Edition**. Revision 1's source inventory and version-boundary finding
still apply; compiling those headers would not verify this release's ABI.

## 2. Independent observations and their limits

Status vocabulary: **STATIC** = measured in the identified ROM; **OBSERVED** = independently
read during the specified local run and correlated with UI/input where stated; **REPORTED** =
third-party layout lead; **SYNTHETIC** = invented test bytes only; **NOT_VERIFIED** = unmet gate.
An observed value does not by itself prove a field's entire width, semantics, or every mode.

### Embedded symbols

The [bounded symbol inspector](../tools/rom-evidence/rowe_symbols.py) independently parsed 150
unique ASCII name-pointer/address pairs at ROM address `0x08FB0670` (file offset `0x00FB0670`).
Every name pointer and terminator was inside the local exact ROM. No external symbol file or
PokeDaisy data was loaded. [Selected results](../tools/rom-evidence/evidence/static-symbols.json):

| Named symbol | STATIC address |
|---|---|
| `gPlayerParty` | `0x02025128` |
| `gPlayerPartyCount` | `0x02025125` |
| `gEnemyParty` | `0x020252F0` |
| `gEnemyPartyCount` | `0x02025126` |
| `gSaveBlock1Ptr` | `0x03004AFC` |
| `gSaveBlock2Ptr` | `0x03004B00` |

The enemy count is now a named static symbol, not inferred from adjacency. The table does not
provide object sizes, types or an ABI. The party-address difference is `6 * 76`; that remains
corroboration, not independent proof of stride. No accessor disassembly was completed here.

### Runtime experiment

The [observer and movie](../tools/rom-evidence/README.md) reuse DualDex's existing developer
adapter, native libretro host and bounds-checked memory map. They do not use the production
100-byte party parser, scan RAM, write emulator RAM, load a private existing save, or stage
Pokémon fields. All reads occur while synchronous frame stepping is paused. Only input buttons
and the host's private temporary save-state API change game state.

Core: Linux x86_64 mGBA libretro SHA-256
`fc7394f718d213131d5ec9002ddea6c72a9cd2094e0a2993c3ea3c9c964f203e`;
local build is documented by the existing [vanilla probe](../tools/vanilla-runtime-probe/README.md)
as source `e31759b24e7a4e3899285ff720d7b573ac328ae7`. Core hash was independently checked;
this task did not rebuild/independently reproduce the core. No external BIOS or battery save.

[Committed observations](../tools/rom-evidence/evidence/runtime-observations.json) retain selected
values, tool/movie/core/adapter hashes, frame numbers, RAM-region digests and screenshot digests.
Private screenshots were visually inspected; images, states and complete RAM are not committed.
The committed pair comes from two fresh processes: the ordinary movie and a run changing only
the initial delay from 180 to 240 frames. Repeated runs produced different starter identities. Starter
selection/identities can vary with timing/RTC, so these observations are not universal goldens.
Other exploratory Sentret/Voltorb/Fomantis runs informed the experiment but are **not** the committed
replay evidence and are not required to justify the matrix below.

| UI-correlated value | Ordinary movie | Initial delay +60 frames |
|---|---|---|
| Nickname / species / displayed dex | Throh / Throh / 538 | Magikarp / Magikarp / 129 |
| Level / current HP / max HP | 10 / 44 / 44 | 10 / 24 / 24 |
| Attack / Defense / Speed / Sp. Atk / Sp. Def | 25 / 24 / 14 / 11 / 19 | 7 / 16 / 18 / 8 / 9 |
| Displayed trainer ID / nature | 61656 / Lax | 61647 / Sassy |
| Moves initially (raw IDs from candidate extraction) | Bind (20), Leer (43), Mat Block (561), Bide (117) | Splash (150), Tackle (33), empty, empty |
| Displayed current PP | 20, 30, 15, 10 | 40, 35, empty, empty |

Throh's move order/PP changed through normal summary-screen inputs:
`[20,43,561,117] / [20,30,15,10]` → `[43,20,561,117] / [30,20,15,10]` →
`[43,117,561,20] / [30,10,15,20]` → `[43,117,20,561] / [30,10,20,15]`.
The final screen independently displayed Leer, Bide, Bind, Mat Block and matching PP. This
exercises all four field positions, including bit 9 of an ID in positions 3 and 4. It does not
establish the complete exact-release move-name table. Magikarp's empty-slot swap attempts do
not imply successful swaps; the report uses input labels, not success labels.

Player count was 0 at boot, then 1 after receiving a starter. Enemy count was always 0 in these
scenes. The named live-party global changed with move swaps. The SaveBlock1 pointer moved
`0x02026460 → 0x0202644C` in the ordinary run and `0x0202648C → 0x02026480` in the delayed run;
SaveBlock2 moved correspondingly. The 456-byte region at the **reported** SaveBlock1 `+0x238`
copy offset stayed different from the live party. This rules out using that candidate copy for
these observations; it does **not** prove its saved-party semantics or offset in this release.

Temporary save-state restore recovered both complete party-region digests and decoded move/PP
values. One neutral frame refreshes the cached host video after restore; animation means its
hash need not equal the original frame. This is emulator save-state evidence, **not** an in-game
save followed by cartridge reload. Only slot zero was occupied. Counts 2–6, distinct simultaneous
slots, slot reorder, nonzero enemy count, battle HP/PP consumption and save-copy serialization
remain untested. No claim is upgraded by assuming them.

## 3. Field-by-field matrix v2

Contract ID: `rowe-2.1.9.1-experimental-evidence-v2`, exact ROM SHA-256 above. All multi-byte
candidate reads are little-endian. Bit positions start at zero. Offsets are relative to a slot.

| Field / candidate | Independent evidence | Remaining gate / status |
|---|---|---|
| Player party `0x02025128` | STATIC named symbol; OBSERVED slot-zero/UI and mutation agreement over two documented boots and state restore | General lifecycle/coherence beyond these paused scenes NOT_VERIFIED |
| Player count `0x02025125`, u8 | STATIC named symbol; OBSERVED first byte 0→1 consistent with UI | Storage width/accessor and counts 2–6 NOT_VERIFIED |
| Stride `0x4C` / 76 bytes | STATIC six-stride adjacency; REPORTED accessor layout; SYNTHETIC extraction bounds | Multiple distinct occupied slots or independent accessor indexing needed; NOT_VERIFIED |
| PID u32 `+0x00` | OBSERVED varying word, stable under move swaps and restore | Identity meaning, full width, shiny/gender derivation NOT_VERIFIED; no PID-derived nature |
| OTID u32 `+0x04` | OBSERVED low 16 bits agree with displayed trainer IDs 61647 / 61656 | High half/SID and full semantic contract NOT_VERIFIED |
| Nickname `+0x08`, candidate 12 bytes | OBSERVED Gen III bytes spelling Magikarp/Throh, terminated before end | Maximum length, control codes, languages, user renaming NOT_VERIFIED |
| Species low 12 bits of word `+0x1C` | OBSERVED IDs 129 / 538 match displayed species/dex | Upper species bits, full 12-bit mask, custom IDs, national-dex equivalence NOT_VERIFIED |
| Form bits 12–16 of `+0x1C` | Both observations zero; REPORTED / SYNTHETIC mask | Nonzero forms and interaction with species/move fields NOT_VERIFIED |
| Level u8 `+0x3C` | OBSERVED displayed 10 in two species; contradicts #152's `+0x44` (Defense here) | Level changes, limits/modes and byte width NOT_VERIFIED |
| HP u16 `+0x3E`, max HP `+0x40` | OBSERVED 24/24 and 44/44 match UI | Damage, fainting, healing and high-byte values NOT_VERIFIED |
| Atk/Def/Spe/SpA/SpD u16 at `+0x42/44/46/48/4A` | OBSERVED all five match both summary screens, different stat order from UI handled explicitly | Wider values and level/stat recalculation NOT_VERIFIED; old #152 tail contradicted |
| Move 1 `+0x14` bits 0–9 | OBSERVED Bind→Leer and Splash→Tackle swaps | Upper boundary and experience-neighbour independence beyond SYNTHETIC tests NOT_VERIFIED |
| Move 2 `+0x18` bits 0–9 | OBSERVED Leer→Bind→Bide | Same full-width/mapping gate |
| Move 3 `+0x18` bits 10–19 | OBSERVED Mat Block (561)→Bind | Bit 9 exercised; exact mask ceiling and neighbouring fields still need accessor/high-ID evidence |
| Move 4 `+0x1C` bits 17–26 | OBSERVED Bide→Bind→Mat Block (561), species unchanged | Nonzero-form interaction/full mask ceiling NOT_VERIFIED |
| PP bytes `+0x34..37` | OBSERVED all four displayed values follow all four move positions during swaps | Use, zero/restoration, PP Ups and high-bit semantics NOT_VERIFIED; do not import H&S's 7-bit mask |
| Nature `(byte +0x27 >> 2) & 31` | OBSERVED 22=Sassy, 9=Lax, matching UI; both also equal PID%25 | Independent nature storage/changes, other values, modes and adjacent packed bits NOT_VERIFIED |
| Encryption/checksum | OBSERVED named fields directly readable and move mutations visible without XOR | Gen III container incompatible; absence of every alternative integrity field NOT_VERIFIED |
| Enemy party `0x020252F0`, count `0x02025126` | STATIC both named; OBSERVED count zero outside battle | Nonzero count, enemy stride, authority in trainer/wild/doubles/teardown NOT_VERIFIED |
| SaveBlock1 party copy `ptr+0x238` | REPORTED location; observed differing digest from live party | Exact offset, count location, serialization/cartridge reload NOT_VERIFIED |
| OT name, experience, status, IVs/EVs, ability, item, PP bonuses | No added independent semantic evidence | REPORTED/unknown; out of this bounded capture, NOT_VERIFIED |

The absence of a checksum means plausibility cannot prove coherent snapshots. The probe's raw
measurement function intentionally has **no valid-Pokémon verdict**. Its tests accept invented
out-of-domain values to prove extraction does not silently become support authorization.
PR #189's separate host C plausibility decoder remains synthetic and unmerged.

## 4. Reader/data/ABI reuse boundary

The revision-1 native-flow audit still holds: production `RawGbaPokemon` is 100 bytes; vanilla,
Expansion and plaintext fallbacks use its checksum/secure-container/tail assumptions. Counted
party loops and scanning paths also advance by that size. Disabling XOR cannot adapt them.
A separate length-aware packed slot decoder and a narrow verified stride/decoder dispatch are
needed after the remaining gates. Do not pass a 76-byte slot to the existing unbounded parser.

| Existing infrastructure | Classification for this task / eventual integration |
|---|---|
| Native memory-map bounds, libretro stepping, temporary state API | **Reusable**; actually reused by this probe |
| [Native memory fixture replay](NATIVE_MEMORY_FIXTURES.md) from #190 | **Small adapter**: reusable bounded EWRAM/IWRAM loading/map and failure controls; current 100-byte production-reader assertions cannot prove the R.O.W.E. slot layout |
| Exact-hash trust/refusal policy | **Reusable**; unchanged; diagnostic pins are isolated |
| Bounded text codec | **Small adapter/validation**; observed short names only; full character/length contract pending |
| Gen III/H&S slot parser, 100-byte loops, checksum scanner | **Incompatible**; retain all current behavior, add a separate decoder only after proof |
| Authoritative-count/all-or-nothing policy and paused snapshot transport | **Small adapter** for verified stride, IDs/forms and coherence; never a species scan |
| H&S source pins/digest/regen discipline | **Reusable method, small adapter** for a separate provenance manifest; exact source still missing |
| H&S source data extractors and generated species/move/item tables | **Incompatible as-is**; older R.O.W.E. source and matching numeric IDs cannot stand in for exact-release data |
| ARM ABI/bitfield compiler probes | **Small adapter in principle; blocked** by missing exact source/toolchain contract; embedded symbols do not replace field ABI |
| Generated H&S battle layout, controller/lifecycle tuples, challenge rules | **Incompatible**; independent R.O.W.E. evidence required |
| `GameDataPack` no-global-fallback contract | **Reusable with small adapter** for an independently verified release-specific pack; no pack supplied here |
| QuickJS transport and capability refusal policy | **Reusable**; H&S mechanics/goldens/oracle results are **incompatible evidence** for R.O.W.E. |

No #8 generic profile rewrite is needed for this evidence step. The new generic UPS verifier is
narrowly reusable; symbol/field constants and input scenes remain explicitly R.O.W.E.-specific.
No GPL implementation, generated tables or captured third-party fixtures were copied.

## 5. Next implementation gates, in priority order

1. **Finish player layout proof.** Independently inspect exact-ROM accessors or acquire an exact
   source/ELF. Create 2–6 distinct occupied slots by ordinary gameplay; compare all displayed
   fields, reorder the party, exercise count transitions including empty/full and boxed slots.
   Prove count width, 76-byte indexing and upper packed bit boundaries. These are blockers,
   not additional confidence obtainable from the current slot-zero fixture.
2. **Mutation and persistence.** Observe nickname changes, multiple levels, nonzero forms,
   damage/faint/heal, move learning/deletion, PP use/restore/bonuses. Perform an actual in-game
   save, flush only the experiment's own save locally, then fresh cartridge load. Trace global
   vs saved-copy count/data and moving pointers. Repeat menu/battle transitions with paused
   coherent captures; reject torn/ambiguous state rather than guessing.
3. **Isolated decoder contract.** Once (1–2) pass, replace only the proven hypothesis portions
   with a length-bounded decoder and synthetic regression cases plus independent UI-correlation
   checks. Keep raw release IDs/forms. Audit every player/enemy/JNI caller before any production
   dispatch; retain FireRed/Emerald/H&S stride, checksum and bounds behavior.
4. **Exact data and trust review.** Establish species/form/name/move/PP/ability/item mappings and
   text semantics from legitimate exact-release sources or independently validated local
   extraction. Do not ship ROM tables or use global fallback. Separately review whether the
   observed ROM hash may enter production trust only after the layout/runtime gates pass.
5. **Enemy/battle/calculator follow-up.** Validate nonzero authoritative enemy count and lifecycle,
   battler indexes, switches/faints/teardown, singles/doubles and mode settings independently.
   Mechanics parity is a separate project; player-party success must not enable H&S calculator
   capabilities for this ROM.

### Safe protocol for the next capture

Reproduce UPS identity first; refuse any mismatch. Run the symbol inspector against the same
bytes. Use the pinned core and adapter, record their hashes and the input movie, use a private
output directory, and retain no copyrighted artifacts in Git. Start with an empty cartridge and
only synthetic trainer data. At each occupied-slot/count transition, pause stepping, capture
bounded EWRAM party/count and IWRAM pointer values, record displayed values independently, then
resume. Read no arbitrary pointer without validating its entire target span. Compare field
mutations and save/load effects; record unknown/contradictory observations explicitly. A ROM,
core or scene mismatch is a failed or new experiment, never a reason to waive an integrity check.
Do not equate a fresh `OBSERVED` report with a production support verdict.

## 6. Validation and handoff

The focused ROM-free suite checks UPS pin/all CRCs/lengths, unequal candidates, malformed and
bounded records, synthetic symbol pointers/names/duplicates, exact-ROM refusal, packed-field
sentinels, all truncated/oversized slots, bounded input/read failures and the committed evidence's
tool/movie hashes and manual UI correlations. These checks validate tooling and records, not
uncaptured gameplay. Canonical `./ci.sh all` includes this suite; `./ci.sh source-check` remains
the unchanged pinned H&S regression contract. Exact-head validation results are recorded in the
PR handoff. Private runtime replays are developer experiments, never CI prerequisites.

PR #189 cleanup head: `f5aa9b0aaf91e685fbcb1f81243604ea3e8a274b`. Its local `./ci.sh all`,
`./ci.sh source-check`, and diff check passed; [exact-head hosted run](https://github.com/Sonoran-Solutions/dualdex/actions/runs/38344051801)
passed Native & Unit Tests, Build Debug APK and H&S Source Validation. The wording/canonical-link
corrections are separate from this new evidence branch. Both PRs must remain draft and unmerged,
without auto-merge; the authorized scheduling exception does not waive integration gates.
