# Unbound internal species → National Dex evidence

Task `unbound-species`, issue [#26](https://github.com/Sonoran-Solutions/dualdex/issues/26),
within the [#60 exact-release gate](https://github.com/Sonoran-Solutions/dualdex/issues/60).
Inspected 2026-11-04 UTC, base `389b0bb48da6d4cd6f8b43d0143235cfcd4f1f42`.

**VERIFIED:** the exact candidate has a species-to-National-Dex `u16` table at
file **`0x01A41FEC`**, derived from its accessor's literal load. All 1,294 internal
IDs have a bounded numeric result, including reserved and custom zero results.
**OBSERVED:** all six previously captured party/UI pins agree. **NOT_VERIFIED:**
general runtime form semantics, canonical form display names, publisher identity,
and production support. The owner identifies the ROM as v2.1.1.1; that remains an
owner claim, not independently established release provenance.

The [inspector](../tools/rom-evidence/unbound_species_map.py),
[16 ROM-free tests](../tools/rom-evidence/test_unbound_species_map.py), and
[sanitized report](../tools/rom-evidence/evidence/unbound-species-map-contract.json)
add no production mapping or support. The report contains counts, bounds, hashes,
30 representative rows and source disagreements, never the full mapping.
Private scratch and extracts remain under `$TMPDIR/private/`, outside the worktree.

## Exact-ROM derivation and bounds

**VERIFIED identity:** 33,554,432 bytes, SHA-256
`7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7`.
The inspector calls the existing `unbound_identity.exact_rom()` before interpreting
offsets or reading comparison evidence. Other sizes/hashes fail, with no scan or
alternate-ROM fallback. See [the identity gate](UNBOUND_RELEASE_EVIDENCE.md) for
the missing publisher-patch prerequisite.

The pinned CFRU `BPRE.ld` and DPE `BPRE.ld` both identify the original
`SpeciesToNationalPokedexNum` Thumb entry as **`0x08043298`**. This was a source
lead only. Local Capstone 5.0.7 was used to inspect that function in the exact ROM;
the shipped Python tool uses reviewed instruction-span hashes, not Capstone.

**VERIFIED static flow:**

1. `0x0804329A–9C` keeps the low 16 bits of the species argument.
2. `0x0804329E–A0` branches for species zero; `0x080432B4` returns zero without
   reading a table row.
3. The PC-relative load at `0x080432A2` reads literal **`0x080432B0`**, whose value
   is **`0x09A41FEC`**. Subtracting GBA ROM base `0x08000000` gives file offset
   `0x01A41FEC`. No name-pattern/table-value search supplies this address.
4. `0x080432A4` subtracts one, `0x080432A6` multiplies by two, `0x080432A8` adds
   the pointer, and `0x080432AA` loads an unsigned halfword.

Thus the physical index is **`internal_id − 1`**, stride **2**, little-endian;
there is **no stored row for internal zero**. The accessor itself has **no upper
bound check**. The inspector refuses to extend the established domain beyond 1293;
truncating arbitrary species inputs to 16 bits is not a safe production policy.

**VERIFIED count corroboration:** the inverse entry at `0x08043200` branches through
the Thumb literal at `0x08043204` to `0x0965BB34`. That exact-ROM routine loads
the same table pointer from `0x0965BBB4`. Its search uses `(candidate − 1) × 2`,
increments the candidate at `0x0965BBAA`, and compares it with the exclusive bound
**1294** from `0x0965BBC0`. This independently matches the name/base-stat domain
established in [the data/text evidence](UNBOUND_DATA_TEXT_EVIDENCE.md). It bounds
the relevant mapping span; adjacent halfwords are not evidence for more species.

| Checked span | File offset | Length | SHA-256 |
|---|---|---:|---|
| Species accessor, including literal | `0x00043298` | 36 | `f39c323ae1455155cec8e74185107905116e184420b238fb08063d3e62d95ae1` |
| Inverse-entry trampoline | `0x00043200` | 8 | `b06070f9c4f872fbe2fff47369a87f6a52bcec4724098f69fc607c1c2339e994` |
| Inverse search and literals | `0x0165BB34` | 148 | `a47a5aea921f515302e703620e2aadb20408cea50fef5c2c78629320fc7325d3` |
| Mapping for IDs 1–1293 | `0x01A41FEC` | 2586 | `412e36320450f467b0f25208f82e6e3bee89b0590ad98a0a7247b6d4da9b3d9b` |

The mapping span ends at file **`0x01A42A06`**, exclusive. These are static
instruction/data checks, **not an executed-PC trace** or a proof of every caller.

**VERIFIED numeric domain:** 1,266 rows are nonzero, covering every National Dex
number **1–905**. There are 905 distinct positive numbers; 194 numbers have multiple
rows, accounting for 361 additional rows. The maximum multiplicity is 28.
Zero results are **0, 252–276, 412, 706**. The first 27 are reserved/placeholder
rows (including Egg, MISSINGNO. and Bad Egg); **706 is the custom Warrior row**.
A zero is an explicit unmapped/refusal result, not Bulbasaur, Cresselia or a hole
that may be filled from public constants. No Gen IX or dex >905 is established.

## Runtime cross-check, reused evidence

**OBSERVED:** the existing six-party summary capture agrees **6/6** with this
static table. The inspector re-reads its sanitized manifest, checks its exact ROM
identity and the complete expected six-pin set, and compares every pin.
It binds the reused manifest SHA-256
`ce24775da4c2cafe41f716bcd1ccee3069e3c48e6171017375cc4d145f1fb147`.
No core, battery save or quicksave was loaded for this task.

| Stored internal species | Static dex | Previously observed summary |
|---:|---:|---|
| 130 | 130 | Gyarados |
| 230 | 230 | Kingdra |
| 186 | 186 | Politoed |
| 1002 | 785 | Tapu Koko |
| 942 | 725 | Litten |
| 1035 | 89 | Alolan Muk (screen name Muk) |

These pins come from [party layout evidence](UNBOUND_PARTY_LAYOUT_EVIDENCE.md),
not from source-name guesses. They do not prove runtime identity for the other
1,288 IDs, any battle transformation, or serialized form normalization.

## Pinned public-source comparison

The local public checkouts were confirmed at CFRU
`b637a27898b14e25dd24d0f69a3e302f0069deb8` and DPE
`cdfc053a56326a13dc5311b24488445e17536b7e`. **NOT_VERIFIED:** either is the source
of this private Unbound build. No network, patch or download was used.
The report includes immutable source URLs and hashes for five files:

| Source input | SHA-256 |
|---|---|
| [CFRU species constants](https://github.com/Skeli789/Complete-Fire-Red-Upgrade/blob/b637a27898b14e25dd24d0f69a3e302f0069deb8/include/constants/species.h) | `8c5acd30a4f06cbfe224ee282b60bac1c8a4898d3c75f632225c2200d8a59c43` |
| [CFRU national constants](https://github.com/Skeli789/Complete-Fire-Red-Upgrade/blob/b637a27898b14e25dd24d0f69a3e302f0069deb8/include/constants/pokedex.h) | `5bafc6e53329ba75048848e4aa9a17e1ec20e8c4204d359d500d008b17488a51` |
| [DPE species constants](https://github.com/Skeli789/Dynamic-Pokemon-Expansion/blob/cdfc053a56326a13dc5311b24488445e17536b7e/include/species.h) | `153baad3b2d13ad2264ddaf734cfc9b675ea82e46849c1c18ec272e79d4f0914` |
| [DPE national constants](https://github.com/Skeli789/Dynamic-Pokemon-Expansion/blob/cdfc053a56326a13dc5311b24488445e17536b7e/include/pokedex.h) | `b8e1f6e288482dd150f9c851cf90d27074d4e787bba09aeb5cb72f0faef7458b` |
| [DPE species-to-dex table](https://github.com/Skeli789/Dynamic-Pokemon-Expansion/blob/cdfc053a56326a13dc5311b24488445e17536b7e/src/Species_To_Pokdex_Table.c) | `91cfa0c092ae55da81b6cf564e385e6d6a7d549a87edcac02381117a6d659b58` |

**VERIFIED comparison mechanics:** DPE has an actual designated-initializer table;
the parser accepts its bounded grammar and resolves its constants without executing
C. Explicit zero rows count. Missing, duplicate, unknown or unparsed entries refuse.
Identical repeated national constants are accepted; conflicting redefinitions refuse.
CFRU has no corresponding table in this checkout. Its separate **inferred** comparison
uses the longest exact canonical label or underscore-delimited canonical prefix;
this is not a claim that CFRU supplied the compiled ROM table. No fuzzy matching,
abbreviation repair, or alias fallback enters that comparison.

| Comparison | Compared IDs | Numeric agreements | Numeric disagreements |
|---|---:|---:|---:|
| Actual DPE table, IDs 1–1267 | 1267 | 1232 | 35 |
| DPE constants with recognizable canonical labels | 1239 | 1205 | 34 |
| CFRU constants with recognizable canonical labels | 1265 | 1265 | 0 |

**VERIFIED counts / OBSERVED interpretation — complete disagreement classes:**

- **Custom mapping:** ID **706** is ROM dex **0**, DPE table dex **488** (Cresselia).
  Public resemblance must not supply a canonical identity for Shadow Warrior.
- **Shifted terminal layout:** all **34 IDs 1234–1267** differ numerically from
  DPE. DPE begins Gigantamax at 1234; the ROM/CFRU layout inserts Hisuian forms and
  new species first and begins Gigantamax at 1260. DPE has **no IDs 1268–1293**.
  Every differing numeric row is retained in the report, not just examples.
- **Form-label conflict despite equal dex:** **919/920** are CFRU
  `DIALGA_ORIGIN`/`PALKIA_ORIGIN`, DPE `DIALGA_PRIMAL`/`PALKIA_PRIMAL`;
  ROM numbers **483/484** agree with both. Exact form names remain **NOT_VERIFIED**.
- **Benign source alias:** ID **83** has DPE `FARFETCHED` plus `FARFETCHD`, versus
  CFRU `FARFETCHD`. Both resolve to 83. Together with the two form conflicts and
  34 terminal differences, these account for all **37** source-label disagreements.
- **Recognizable-label gaps:** both headers leave **412, 706, 737, 839** unresolved
  by the strict canonical-prefix rule. Egg/Warrior are separately zero-checked.
  The explicit source-label exceptions `DARMANITANZEN` → Darmanitan and
  `ASHGRENINJA` → Greninja agree with ROM/DPE numbers **555/658**; they are reported
  separately and do not inflate the strict comparison denominator.
- **Missing species constants:** both omit **252–276**; DPE additionally omits
  **1268–1293**. ID zero is handled in code, not a DPE table row. None is silently
  added to the public table denominator or treated as a verified form.
- **Text-only differences:** the existing name comparison is reproduced unchanged:
  CFRU **1250/1269**, DPE **1194/1243**. CFRU's 19 mismatches comprise two reserved
  labels, Warrior, the two unseparated labels above, and 14 abbreviated names.
  DPE has 15 mismatches before 1234 plus all 34 older terminal-layout rows
  (49 text mismatches total).
  Exact mismatch IDs are retained. Numeric agreement resolves the dex question
  even where a display name is abbreviated; it cannot resolve form nomenclature.

## Per-ID classification, with limits

**OBSERVED taxonomy:** the tool classifies **all 1,294 IDs in memory** using the
ROM mapping and pinned CFRU labels, then emits counts and representatives only.
Without `--source-root`, numeric evidence still works but source comparisons and
classifications explicitly remain **NOT_VERIFIED**. A supplied source hash mismatch
fails; it never falls back to an unpinned header.

| Class | Count | Representative internal → dex |
|---|---:|---|
| Base species | 905 | 277 → 252 Treecko; 1102 → 810 Grookey; 1251 → 899 Wyrdeer |
| Regional-form source labels | 59 | 1020 → 19 Rattata A; 1035 → 89 Muk A; 1212 → 52 Meowth G; 1234 → 58 Growlithe H |
| Mega / primal source labels | 50 | 869 → 3 Venusaur Mega; 909 → 383 Groudon Primal |
| Gigantamax source labels | 34 | 1260 → 3 Venusaur; 1293 → 892 Urshifu Rapid |
| Cosmetic-form label candidates | 102 | 413 → 201 Unown B; 707 → 412 Burmy Sandy; 1194 → 854 Sinistea Chipped |
| Other alternate-form labels | 116 | 713 → 479 Rotom Heat; 737 → 555 Darmanitan Zen; 919 → 483 disputed Dialga form; 1255 → 902 Basculegion F |
| Custom Unbound | 1 | 706 → 0 Warrior / source Shadow Warrior |
| Reserved / hole | 27 | 0, 252–276, 412 → 0 |

**NOT_VERIFIED semantics:** “base” means the first internal row for each positive
dex, not an independently observed canonical default. Thus Basculegion M is the
first row for 902, while F remains an alternate. “Cosmetic” is a source visual-label
bucket, not proof of identical stats, abilities, moves, evolution or legality.
Other alternates include stat/type/stance/fusion and ambiguous forms rather than
forcing them into a cosmetic bucket. Regional labels include **Exeggcute A,
Cubone A, Koffing G and Mime Jr G** precursor slots whose runtime purpose remains
**NOT_VERIFIED**. Hisuian Basculin and Galarian Zen also follow source labels;
this count is not a claim about an official regional-form roster.

Unown G/H are cosmetic letter candidates, not regional suffixes; a regression
checks that collision. Dialga/Palkia 919/920 are kept in the other-alternate class
because their source form labels conflict. The class counts are measurements of
this explicit taxonomy; source labels are not exact-ROM form display names.
Forms sharing a dex are necessarily lossy if reduced to the dex alone. Preserve
the internal ID as a separate identity component; a dex cannot reconstruct a form.

## Recommendation for the owner

**OBSERVED engineering recommendation:** generate the **numeric** production mapping
from the **exact ROM table**, at build time from the owner's private, hash-gated ROM,
after the owner authorizes the production work. This is safer than making pinned
public constants the authority: it follows the game's accessor, handles all 1,294
IDs, captures the inserted terminal range, and preserves the custom zero result.
Use a fixed reviewed accessor/table contract and expected digest; do not ship a
heuristic table finder. Keep the ROM and full extracted outputs outside Git.
This evidence tool intentionally provides no full-table export or build integration.

Pinned CFRU labels can supply **reviewed annotation candidates**, with the exact ROM
serving as a hard verification gate. They should not override zero results or settle
form names merely because a canonical prefix matches. If the owner instead chooses
a public-source mapping, it needs explicit handling for the unresolved labels,
reserved rows and custom species, plus complete exact-ROM numeric verification.
The older DPE table cannot be used unchanged. Licensing/distribution of any eventual
mapping asset is an owner decision; this task establishes no redistribution policy.

**NOT_VERIFIED prerequisites for a complete production identity:** exact publisher
patch/release provenance; exact form display labels and regional precursor roles;
the two conflicting origin/primal labels; battle-effective transformations and
stat/move selection; alternate forms stored under a shared species ID, personality
or other fields; persistence and enemy-party behavior; and production fail-closed
handling of unsupported forms. The table supplies dex numbers, not a separate form
ordinal or complete calculator key. Numeric verification must not enable support
while those independent gates remain open.

## Reproduction and validation

All runs used Python **3.12.3**, no seeds, no network, no core process and no Android
build. The tested inputs are base `389b0bb` plus the three new tool/test/report files;
this document is inert and was added afterward. Inspector/dependency hashes bind the
tested source in the report. Prior party evidence was reused as identified above.

```bash
# TMPDIR remains /home/dq/dualdex-private/unbound/species-tmp.
(ulimit -f 1024; timeout --kill-after=5 600 \
  python3 tools/rom-evidence/unbound_species_map.py \
  --rom /home/dq/dualdex-private/unbound/unbound-candidate.gba \
  --source-root /home/dq/dualdex-private/unbound/src \
  > "$TMPDIR/private/species-map-contract.json" \
  2> "$TMPDIR/private/species-map-contract.err")
(ulimit -f 1024; timeout --kill-after=5 600 \
  python3 -m unittest discover -s tools/rom-evidence -v \
  > "$TMPDIR/private/species-tests-first.log" 2>&1)
git diff --check
```

**VERIFIED:** final exact-ROM command **exit 0**, about **0.50 s**, including the
five pinned public inputs, complete numeric span, all classifications and six reused
runtime pins. The same bounded command without `--source-root` also exited 0;
its numeric results and runtime pins match, while source comparisons and classes
correctly remain **NOT_VERIFIED** (private `species-map-no-source.json`).
One initial source-parser run refused identical repeated `CASTFORM`
definitions; the parser was corrected to allow identical values and reject conflicts,
then one recovery passed. Both failed and successful attempt logs remain private.
Other bounded research runs inspected the accessor and public comparisons; total
validation/research runtime is below one minute, far below the 60-minute budget.
Each ROM run had `timeout 600`, a five-second kill grace and bounded redirected output.

**VERIFIED:** all **16 new tests passed** within full discovery. They cover exact-hash
admission order, species-minus-one indexing, little-endian widths, span/domain bounds,
accessor/literal mutation, source hashes, restricted initializer parsing, identical
and conflicting redefinitions, canonical-prefix denominators, all eight classes,
Unown suffix collisions, unresolved identities, six-pin evidence reuse, negative CLI
behavior and the sanitized report's source binding/counts. No existing test changed.

Coordinator rerun with a Git-free TMPDIR: full rom-evidence discovery passed; `./ci.sh all` passed on c42b43f.
