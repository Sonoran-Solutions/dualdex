# Unbound static species/form and data contract

Task `unbound-static`, issue [#26](https://github.com/Sonoran-Solutions/dualdex/issues/26),
2026-11-04 UTC. This replaces the source-only attempt-1 note.

**VERIFIED:** a read-only, exact-SHA-256-gated inspector now measures the candidate's
five requested tables and internal species-name domain.
[unbound_data.py](../tools/rom-evidence/unbound_data.py) and its
[sanitized contract JSON](../tools/rom-evidence/evidence/unbound-data-contract.json)
contain offsets, counts, span hashes, accessor hashes, source-agreement counts and
21 selected species IDs, not a copied mapping table. It reuses the existing bounded
file reader, identity implementation and strict Gen III glyph decoder. It needs only
Python's standard library; the development disassembler is not a runtime dependency.

**NOT_VERIFIED:** numeric release label, patch/source-build provenance, stored party
species/form representation, battle-effective data, runtime form resolution and
production support. See [release evidence](UNBOUND_RELEASE_EVIDENCE.md) for measured
candidate/base identities and the exact publisher-patch prerequisite. The candidate
SHA-256 is `7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7`.

## Pointer derivation and table bounds

The table addresses below are **GBA ROM addresses**; subtract `0x08000000` for file
offsets. The JSON records both. Development-only Capstone disassembly was used to
trace Thumb instructions from the exact ROM. The committed inspector verifies the
reviewed instruction-span digests and literal pointers without requiring Capstone.
Pointers in the extended header are corroboration, not the sole authority.

| Table | GBA address / file offset | Rows × stride | Evidence and limits |
|---|---|---|---|
| Species names | `0x0966A98C` / `0x0166A98C` | 1294 × 11 | **VERIFIED:** `GetSpeciesName` at `0x08040FD0`; literal `0x08040FEC`, header pointer `0x08000144`. `mov #11; mul` at `0x08040FE2/4`, terminated byte copy. The old comparison against 412 remains but its rejection branch is NOPed, so it is not a species upper bound. |
| Base stats | `0x099E0C9C` / `0x019E0C9C` | 1294 × 28 | **VERIFIED:** gender accessor `0x0803F78C`, literal `0x0803F7C8`, header `0x080001BC`; `(id << 3) - id`, then `<< 2`, and byte load at `+16`. Count uses the independently bounded species domain. Other field semantics **NOT_VERIFIED** by this inspector. |
| Move names | `0x08A40A10` / `0x00A40A10` | 923 × 13 | **VERIFIED:** battle move-menu code `0x08030858–64` uses ID ×13 and literal `0x080308A4`; header `0x08000148`. IDs 0–922 are a fully terminated, decodable prefix; 923 fails field structure. This is a safe static prefix, not proof of every runtime-legal move. |
| Ability names | `0x08A36398` / `0x00A36398` | 293 × 17 | **VERIFIED:** default name path `0x089A9CB4–DC` multiplies ID ×17, literal `0x089A9CDC`, header `0x080001C0`. Rows 0–292 decode; 293 fails. **NOT_VERIFIED:** effective names, because the caller at `0x089A9CA8` first calls species-dependent override code at `0x089A9944`. Do not infer an 8-bit ability domain from the base-stat bytes. |
| Items | `0x08876200` / `0x00876200` | 729 × 44 | **VERIFIED:** old accessors at `0x0809A8A4` and `0x0809A8BC` branch through literals to `0x089F3240` and `0x089F324C`. Clamp is `0xB6 << 2` = 728; accepted ID ×44. Literal `0x089F3284` points to header pointer `0x080001C8`, then dereferences it. Name lookup returns an in-record ROM pointer if byte 3 is 8 or 9; otherwise the inline field. |

**VERIFIED — species count:** the exact search loop at `0x0965BB6C` loads
`1294` from `0x0965BBC0`; it increments the candidate at `0x0965BBAA`, compares
against that exclusive bound and loops. The separate evolution scan at
`0x089E0B9C–A4` uses the same exclusive bound from `0x089E0BC8`. These corroborate
1294 name/base-stat rows; the next 11-byte species-name candidate is invalid text.
All 1294 species, 923 move and 293 ability fields pass the supported glyph/termination
checks. A readable name alone does not make an ID a valid Pokémon.

**VERIFIED — measured item outcomes:** 696 inline names and 33 pointer names;
682 pass strict decoding, 47 refuse because non-padding bytes follow the first
terminator in the inline field. This does not mean those items crash the game:
its display can stop at the terminator. There are 83 item record-ID/index mismatches
(listed numerically in the JSON). No item effect, complete item support, or automatic
fallback is inferred. Pointer names are bounded to ROM and 64 bytes. Unknown glyphs,
controls, missing termination, invalid pointer spans and wrong ROM hashes refuse.

Table SHA-256 values are generated in the contract, alongside lengths and exact
accessor digests. No ROM instructions or extracted whole tables are distributed.

## Source comparison and identity domain

The exact public files were read through the GitHub connector and retained privately.
No public header or full source-derived mapping is vendored. Both comparison inputs
are immutable and the optional `--source-dir` mode verifies their file SHA-256:

- **VERIFIED source:** [DPE species constants at `cdfc053a56326a13dc5311b24488445e17536b7e`](https://github.com/Skeli789/Dynamic-Pokemon-Expansion/blob/cdfc053a56326a13dc5311b24488445e17536b7e/include/species.h),
  file SHA-256 `153baad3b2d13ad2264ddaf734cfc9b675ea82e46849c1c18ec272e79d4f0914`.
- **VERIFIED source:** [CFRU species constants at `b637a27898b14e25dd24d0f69a3e302f0069deb8`](https://github.com/Skeli789/Complete-Fire-Red-Upgrade/blob/b637a27898b14e25dd24d0f69a3e302f0069deb8/include/constants/species.h),
  file SHA-256 `8c5acd30a4f06cbfe224ee282b60bac1c8a4898d3c75f632225c2200d8a59c43`.
- **VERIFIED source leads:** CFRU's pinned [BPRE.ld](https://github.com/Skeli789/Complete-Fire-Red-Upgrade/blob/b637a27898b14e25dd24d0f69a3e302f0069deb8/BPRE.ld)
  supplies original function entry points. Its [pokemon.h](https://github.com/Skeli789/Complete-Fire-Red-Upgrade/blob/b637a27898b14e25dd24d0f69a3e302f0069deb8/include/pokemon.h)
  describes the 28-byte base-stat layout, including gender at +16;
  [item.h](https://github.com/Skeli789/Complete-Fire-Red-Upgrade/blob/b637a27898b14e25dd24d0f69a3e302f0069deb8/include/item.h)
  describes the 44-byte item structure with 14-byte inline name and ID at +14.
  These agree with the inspected paths but do not prove that the ROM was built from
  either repository revision. Unbound's pointer-name/override paths still need the
  exact-ROM checks above.

**VERIFIED — table domain:** maximum ID **1293**, including reserved rows. ID 0
is `??????`; 252 is `Egg`; 253 is `MISSINGNO.`; 254–276 are `?` with entirely zero
28-byte base-stat records; 412 is `Bad Egg`. All are refused by species lookup.
Thus the source-header gap 252–276 is **not** uniformly absent in this ROM.
There are **1267 remaining named rows**, not 1267 distinct canonical species or
runtime-legal party IDs. ID 1294 and higher are out of this verified table domain.

**OBSERVED — source-correlated regions:** IDs 1–251 follow the original two-generation
prefix; 277–411 hold Gen III's internal ordering; 413–439 name Unown forms;
440–546 hold Gen IV and 547–702 Gen V; 703–757 contain forms/custom entries;
758–830 Gen VI (with Hoopa Unbound interleaved); 831–938 additional forms,
including the mega/primal range 869–920; 939–1019 Gen VII; 1020–1101 more
regional/alternate/new species; 1102–1190 Gen VIII; 1191–1211 alternate forms;
1212–1233 Galarian labels; 1234–1259 Hisuian labels/new species; 1260–1293
Gigantamax labels. These are source-region descriptions correlated with display
names, **not** independently verified form transformations. The Alolan-labelled
range 1020–1039 even includes Exeggcute/Cubone source slots; their runtime purposes
are **NOT_VERIFIED**. A fixed arithmetic shift to National Dex numbers is unsafe.

Selected independent decoded rows (all name bytes **VERIFIED**; form semantics
remain **NOT_VERIFIED** unless only an ordinary species name is being stated):

| Role | Internal ID | Decoded name | Source correlation / status |
|---|---:|---|---|
| Gen III | 277 | Treecko | **VERIFIED** name; differs from National Dex 252 |
| Gen IV | 440 | Turtwig | **VERIFIED** name; differs from National Dex 387 |
| Gen V | 548 | Snivy | **VERIFIED** name |
| Gen VI | 758 | Chespin | **VERIFIED** name |
| Gen VII | 939 | Rowlet | **VERIFIED** name |
| Gen VIII / above National Dex range | 1102 | Grookey | **VERIFIED** name; ID exceeds 1025 |
| Alolan form candidate | 1020 | Rattata | **OBSERVED** same-ID `RATTATA_A` source label; display name cannot distinguish form |
| Mega candidate | 869 | Venusaur | **OBSERVED** `VENUSAUR_MEGA` source label |
| Galarian candidate | 1212 | Meowth | **OBSERVED** `MEOWTH_G` source label |
| Hisuian candidate | 1234 | Growlithe | **OBSERVED** CFRU `GROWLITHE_H`; DPE instead labels this Venusaur Gigantamax |
| Gigantamax candidate | 1260 | Venusaur | **OBSERVED** CFRU `VENUSAUR_GIGA` |
| Custom Unbound candidate | 706 | Warrior | **OBSERVED** both headers label `SHADOW_WARRIOR`; in-ROM narrative contains “Shadow Warrior”. This identifies a custom species slot, **not** a verified alternate form of a canonical species. Runtime role and canonical mapping are **NOT_VERIFIED**. |
| Ambiguous source form | 919 | Dialga | **NOT_VERIFIED** form: DPE `DIALGA_PRIMAL` versus CFRU `DIALGA_ORIGIN`; identical base display name cannot settle this |
| Last table row | 1293 | Urshifu | **OBSERVED** CFRU `URSHIFU_RAPID_GIGA`; readable static row only |
| Reserved / unknown | 253 / 254 | MISSINGNO. / ? | **VERIFIED** diagnostic names; lookup refuses |
| Outside domain | 1294 | no lookup | **NOT_VERIFIED**, refused before indexing |

## Mapping feasibility and measured agreement

**OBSERVED:** a source-backed internal-ID → canonical-name mapping looks feasible
using the pinned CFRU revision, with explicit reviewed exceptions and reserved-ID
refusals. This is an engineering inference, not permission to build or enable it.
Using the older DPE revision unchanged is demonstrably unsuitable for the terminal
range: 1234–1267 disagrees, and 1268–1293 is absent.

The tool compares every numeric `SPECIES_*` constant, groups aliases by ID, and
reports both denominators. “Literal” folds case, Unicode accents, punctuation and
underscores (gender symbols become F/M). “Decorated” additionally permits dropping
whole trailing underscore-separated tokens, so `RATTATA_A` can agree with Rattata.
That is a **base-name comparison only**, not evidence for Alolan identity. No fuzzy
matching, spelling repair, source-name replacement or arbitrary prefix test is used.

| Source | Literal | Decorated base name only | Combined / source-covered IDs | Combined / all 1294 ROM rows |
|---|---:|---:|---:|---:|
| CFRU pin | 916 | 334 | 1250 / 1269 = 98.50% | 1250 / 1294 = 96.60% |
| DPE pin | 910 | 284 | 1194 / 1243 = 96.06% | 1194 / 1294 = 92.27% |

**VERIFIED counts / OBSERVED interpretation:** CFRU leaves 19 text mismatches:
IDs 0 and 412 (reserved labels); 706 (Shadow Warrior → Warrior); 737 and 839
(unseparated DARMANITANZEN/ASHGRENINJA labels); and 14 abbreviated display-name
rows. The latter include Fletchinder → Fletchindr, Crabominable → Crabminble,
and Basculegion → Basculegon. The exact mismatch IDs are in the contract.
Another 25 ROM IDs (252–276) have no constant in either header. Excluding the
27 reserved/placeholder ROM rows leaves 1250/1267 textual matches for CFRU;
the stricter full-domain denominator remains visible above. Neither percentage
measures semantic form agreement; that percentage is **NOT_VERIFIED**.

Outstanding owner decision: whether a later production mapping uses reviewed source
constants plus explicit aliases/abbreviations or another licensed data source.
Before either choice, resolve the 17 nonreserved text exceptions and source-form
ambiguities, obtain official patch identity, and independently correlate stored
species/forms with UI. No mapping file, reader, profile or production trust change
is made by this task.

## Reproduction, checks and coordinator handoff

The two source files are optional: without them the report explicitly says source
comparison **NOT_VERIFIED**; it never fetches replacements. To reproduce the full
report, place the exact pinned public file bytes under the following private names:
`$TMPDIR/private/dpe-species.h` and `$TMPDIR/private/cfru-species.h`.

```bash
(ulimit -f 2048; timeout --kill-after=5 600 \
  python3 tools/rom-evidence/unbound_data.py \
  --rom /home/dq/dualdex-private/unbound/unbound-candidate.gba \
  --source-dir "$TMPDIR/private" \
  > "$TMPDIR/private/data-contract-final.json" 2> "$TMPDIR/private/data-contract-final.err")

(ulimit -f 2048; timeout --kill-after=5 600 \
  python3 -m unittest discover -s tools/rom-evidence -v \
  > "$TMPDIR/private/tests-final.log" 2>&1)
git diff --check
```

**VERIFIED:** exact-ROM data and identity commands exited 0, each under two seconds.
Both final JSON files bind their inspector SHA-256. All reads were local; no ROM,
patch, state, save, RAM, screenshot, complete extracted table or upstream header
was copied into the worktree. The 10 new `UnboundDataTest` tests cover gates, bounds,
text refusal, reserved/hole IDs, inline/pointer item names, accessor/pointer mutation,
source aliases, denominators and refusal of fuzzy matches. The existing five identity
tests also pass. No existing tests changed.

**BLOCKED:** the required complete discovery command exited **1**: **80 executed,
79 passed, 1 error**, in 0.208 seconds. The unchanged
`test_private_capture_guard_checkout_worktree_and_symlinks` encounters the sandbox's
read-only `$TMPDIR/.git` ancestor. TMPDIR was not changed or overridden; the test and
private-output guard were not bypassed. Failed output is retained in
`$TMPDIR/private/tests-final.log`. The coordinator must rerun discovery outside
this synthetic marker before claiming the required gate passed. Do not rerun broad
Android/native suites to diagnose this environment issue.

**VERIFIED:** `git diff --check` is clean. Tested inputs are checkpoint `982224b`
plus these worktree edits, Python 3.12, invented test bytes, no random seeds.
The final inspector-hash report fields and inert documentation do not change the
logic exercised by the discovery run. Private investigation scripts and bounded logs
remain under `$TMPDIR/private`; total artifacts are below 1 MiB and total command
runtime below one minute, well within the 60-minute validation budget. No live jobs,
core processes, Gradle jobs or engine imports were started. No commits were attempted
in this sandbox; the coordinator owns commit and combined CI.

Coordinator README text (shared README deliberately untouched):
`unbound_identity.py --rom PATH --base PATH` measures the exact diagnostic candidate
and supplied FireRed base; `unbound_data.py --rom PATH [--source-dir DIR]` verifies
static table/accessor spans and emits sanitized counts, hashes and selected rows.
Wrong ROMs and optional source-header hash mismatches fail. Reserved species/holes,
unknown text and invalid item-name pointers refuse; public form labels and effective
runtime data remain unverified. These tools do not enable Unbound support.
