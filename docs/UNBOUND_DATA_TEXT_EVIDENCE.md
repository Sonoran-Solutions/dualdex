# Unbound species/form contract — source leads, ROM work blocked

Task `unbound-static`, issue [#26](https://github.com/Sonoran-Solutions/dualdex/issues/26).
**NOT_VERIFIED:** this is not an exact-ROM data contract. No candidate ROM was
inspected because the required private-log destination is outside this session's
writable roots; see [release checkpoint](UNBOUND_RELEASE_EVIDENCE.md).

## Public source observations

**VERIFIED (public source only):** read these immutable source revisions through
the web tool. Shell network access failed DNS resolution; no ROM or patch download
was attempted, and no public source file or full data table is bundled here.

- DPE [`include/species.h`, `cdfc053a56326a13dc5311b24488445e17536b7e`](https://github.com/Skeli789/Dynamic-Pokemon-Expansion/blob/cdfc053a56326a13dc5311b24488445e17536b7e/include/species.h).
- CFRU [`include/constants/species.h`, `b637a27898b14e25dd24d0f69a3e302f0069deb8`](https://github.com/Skeli789/Complete-Fire-Red-Upgrade/blob/b637a27898b14e25dd24d0f69a3e302f0069deb8/include/constants/species.h).
- CFRU [`BPRE.ld` at the same pin](https://github.com/Skeli789/Complete-Fire-Red-Upgrade/blob/b637a27898b14e25dd24d0f69a3e302f0069deb8/BPRE.ld)
  and [`include/pokemon.h`](https://github.com/Skeli789/Complete-Fire-Red-Upgrade/blob/b637a27898b14e25dd24d0f69a3e302f0069deb8/include/pokemon.h)
  provide accessor and structure leads, not this ROM's addresses or ABI.

The pinned public constants provide these investigation targets. Names in this
table are **source labels**, not names decoded from the candidate. Every exact-ROM
correlation below is **NOT_VERIFIED**.

| Target | Public internal ID | Source observation | Exact-ROM decoded name |
|---|---:|---|---|
| Ordinary Gen III | 277 (`0x115`) | Treecko in both headers | NOT_VERIFIED |
| Gen IV | 440 (`0x1B8`) | Turtwig in both | NOT_VERIFIED |
| Gen V | 548 (`0x224`) | Snivy in both | NOT_VERIFIED |
| Gen VI | 758 (`0x2F6`) | Chespin in both | NOT_VERIFIED |
| Gen VII | 939 (`0x3AB`) | Rowlet in both | NOT_VERIFIED |
| Gen VIII; above 1025 | 1102 (`0x44E`) | Grookey in both | NOT_VERIFIED |
| Regional form | 1020 (`0x3FC`) | Alolan Rattata in both | NOT_VERIFIED |
| Mega | 869 (`0x365`) | Mega Venusaur in both | NOT_VERIFIED |
| Custom-form lead | 919 (`0x397`) | DPE labels this Primal Dialga | NOT_VERIFIED; Unbound-specific semantics also NOT_VERIFIED |
| Hole lead | 252–276 | Neither header defines species in this interval | NOT_VERIFIED; do not assume ROM holes |
| Reserved lead | 0 / 412 | None / Egg in both | NOT_VERIFIED |

**VERIFIED (source disagreement):** DPE's terminal species ID is `0x4F3` (1267),
whereas CFRU's is `0x50D` (1293). DPE begins Gigantamax IDs at `0x4D2`; CFRU places
Hisuian forms/new species there and starts Gigantamax at `0x4EC`. Thus source choice
can change the meaning of an ID while leaving it in bounds. These maxima are not
the candidate ROM's maximum. Pinning either repository alone cannot prove an
Unbound mapping. Public sources are comparison inputs, not exact build provenance.

**VERIFIED (source lead only):** `BPRE.ld` places `GetSpeciesName` at Thumb entry
`0x08040FD1` and `GetBaseSpecies` at `0x08045971`. Its vanilla data labels include
species names `0x08245EE0` and base stats `0x08254784`. Commented move/ability labels
refer to vanilla `0x08247094` / `0x0824FC40`. These must not be installed as Unbound
offsets. Inspect the exact candidate's accessor instructions and relocated literal
pointers, then bound the resulting tables independently. The source `BaseStats`
layout has byte stats/types, two held-item halfwords, and ability bytes including a
hidden-ability field; that is a hypothesis to check, not a verified Unbound layout.

## Remaining exact-ROM work

**BLOCKED:** locating the species names, base stats, move names, ability names and
item tables, their accessor derivations, table lengths/digests, and species-count
bounds requires authorized bounded ROM inspection. None was substituted with a
vanilla address or inferred solely from these source constants.

**NOT_VERIFIED:** maximum ID, reserved/hole IDs, generation/form ranges, decoded
representative names, National Dex divergence, and Unbound-specific form semantics
for this candidate. A full source-to-ROM agreement fraction is **unknown**, not
0%, 100%, or a percentage measured from a convenient subset.

**NOT_VERIFIED (feasibility):** source-backed mapping is plausible because public
headers supply explicit IDs and form labels. Feasibility for this ROM cannot be
established until all bounded name rows are compared at the same IDs. Report both
the source-covered denominator and the complete ROM-domain denominator, preserve
aliases, distinguish punctuation/case normalization from semantic agreement, and
keep form semantics separate from repeated base display names. Resolve mismatches
and undocumented custom forms before the owner chooses a production mapping source.

`unbound_data.py` and its synthetic-byte tests have **not** been implemented:
writing an apparently exact contract around uninspected offsets would misrepresent
the evidence. After logging is authorized, derive the actual table contract first,
then implement the exact-hash inspector, bounded reads/refusals and synthetic tests.
The output must contain only counts, offsets, hashes and selected representatives.
No full extracted table, runtime form claim or production support should be added.

Coordinator README summary: a new `unbound_identity.py --rom PATH --base PATH`
inspector is prepared and has five passing synthetic tests, but its private run is
blocked; the exact species/form contract remains incomplete. The existing full
ROM-free suite also has the environmental failure documented in the release note.
Do not advertise this checkpoint as verified Unbound support or a completed gate.
