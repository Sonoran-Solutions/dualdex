# Unbound exact-ROM identity table generators (issue #26)

`generate_unbound_species.py` turns the owner's exact private Unbound ROM into the committed
numeric table `app/src/main/java/com/dualdex/pokemon/unbound/UnboundSpeciesTable.kt`, which
`UnboundDataPack` (pack id `unbound_exact`) reads. Python stdlib only.

- Refuses every ROM except SHA-256 `7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7`.
- Derives the table through `tools/rom-evidence/unbound_species_map.py` (accessor, literal, table
  hash, numeric-domain and runtime-pin gates) and its CFRU source-label form classes; see
  [the mapping evidence](../../docs/UNBOUND_SPECIES_MAPPING_EVIDENCE.md).
- Commits numbers only: internal species ID -> National Dex number and a form-class code
  (base, regional, mega/primal, gigantamax, cosmetic, other-alternate, custom, reserved), plus
  ROM, table, generator, inspector and CFRU-header hashes. No names, text or other ROM data.

Regenerate (developer only; ROM and pinned public checkouts stay outside Git):

```bash
(ulimit -f 4096; timeout --kill-after=5 600 python3 tools/unbound-data-pack/generate_unbound_species.py \
  --rom /private/unbound-candidate.gba --source-root /private/src)
```

`./ci.sh test` runs the ROM-free check and the synthetic tests:

```bash
python3 tools/unbound-data-pack/generate_unbound_species.py --verify
(cd tools/unbound-data-pack && python3 -m unittest test_generate_unbound_species -v)
```

`--verify` checks the ROM/table pins, row count, zero set, base-is-first-row rule, form-class
counts and mapping digest, and requires the file to be byte-for-byte generator output. The
recorded generator SHA-256 is provenance only; after editing the generator, regenerate.

## Move, ability and held-item identities

`generate_unbound_names.py` is the sibling exact-ROM generator. It reuses
`unbound_data.names`, `item_name` and `normalized`; no decoder is duplicated.
It checks the full ROM hash before reading offsets, then each table's SHA-256
against `tools/rom-evidence/evidence/unbound-data-contract.json`. It emits only
numeric arrays and hashes in `UnboundNameTables.kt`.

Bundled identity sources (no ROM text is retained):

- Moves: `MoveDatabase.kt`, the database used by `ModernDataPack`.
- Items: the explicit `expansionMap` entries in `ItemDatabase.kt` (not its cross-table fallback).
- Abilities: `HnsAbilityAuditData.kt` supplies the app's existing numeric ability/name
  catalogue. There is no generic numeric modern ability database. Only its names and IDs
  are reused via `HnsAbilityRegistry`; H&S mechanics classifications never apply to Unbound.

Matching uses the evidence reader's normalization (case, accents, punctuation and
spacing), with exactly one matching app ID required. No aliases, abbreviation expansion,
spelling corrections or fuzzy matches. Reserved ID 0, invalid text, missing/ambiguous
matches and **all** `record_id_mismatch_ids` remain unmapped. There are no mismatch
exceptions. Mapping proves display identity only, not move values, item effects or
ability mechanics. Numeric ability lookups return `Absent` on a miss; species-slot
ability declarations remain `Absent`, because name tables do not prove slot assignments
or species-dependent effective-name overrides. Returned entries retain Unbound IDs.
Both party screens share `resolveHeldItem`, which delegates game ID 9 to the pack.

```bash
python3 tools/unbound-data-pack/generate_unbound_names.py --rom /private/unbound-candidate.gba
# ROM-free provenance, catalogue domains, table dimensions, exclusions and digest check:
python3 tools/unbound-data-pack/generate_unbound_names.py --verify
# Re-derive from the exact ROM and compare as well:
python3 tools/unbound-data-pack/generate_unbound_names.py --rom /private/unbound-candidate.gba --verify
(cd tools/unbound-data-pack && python3 -m unittest -v)
```

The ROM-free check binds the artifact to the evidence contract, generator, table reader,
text decoder and bundled catalogue hashes and requires byte-for-byte generated formatting.
The mapping digest detects table edits; only private-ROM verification can re-derive the
name matches. `ci.sh` runs both generators' ROM-free checks and all synthetic tests.

Coverage includes reserved ID 0 in the unmapped count. Unmapped IDs below are numbers only.

| Table | Mapped | Unmapped | Total |
| --- | ---: | ---: | ---: |
| move | 688 | 235 | 923 |
| ability | 259 | 34 | 293 |
| item | 529 | 200 | 729 |

Unmapped move IDs:

```text
0, 136, 265, 355, 462, 465, 466, 467, 478, 490, 499, 509, 511, 518, 520, 562, 565, 566, 571, 572, 573, 574, 580, 584, 585, 586, 588, 590, 592, 598, 600, 603, 604, 606, 612, 613, 615, 617, 619, 620, 625, 626, 627, 628, 635, 640, 651, 658, 660, 667, 673, 678, 691, 697, 698, 700, 706, 709, 710, 713, 718, 720, 723, 726, 731, 732, 734, 735, 736, 741, 744, 747, 759, 760, 761, 762, 763, 764, 765, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776, 777, 778, 779, 780, 781, 782, 783, 784, 785, 786, 787, 788, 789, 790, 791, 792, 793, 794, 795, 796, 797, 798, 799, 800, 801, 802, 803, 804, 805, 806, 807, 808, 809, 810, 811, 812, 813, 814, 815, 816, 817, 818, 819, 820, 821, 822, 823, 824, 825, 826, 827, 828, 829, 830, 831, 832, 833, 834, 835, 836, 837, 838, 839, 840, 841, 842, 843, 844, 845, 846, 847, 848, 849, 850, 851, 852, 853, 854, 855, 856, 857, 858, 859, 860, 861, 862, 863, 864, 865, 866, 867, 868, 869, 870, 871, 872, 873, 874, 875, 876, 877, 878, 879, 880, 881, 882, 883, 884, 885, 886, 887, 888, 889, 890, 891, 892, 893, 894, 895, 896, 897, 898, 899, 900, 901, 902, 903, 904, 905, 906, 907, 908, 909, 910, 911, 912, 913, 914, 915, 916, 917, 918, 919, 920, 921, 922
```

Unmapped ability IDs:

```text
0, 74, 77, 153, 154, 164, 178, 208, 235, 239, 255, 269, 271, 272, 273, 274, 275, 276, 277, 278, 279, 280, 281, 282, 283, 284, 285, 286, 287, 288, 289, 290, 291, 292
```

Unmapped item IDs:

```text
0, 18, 46, 47, 54, 59, 72, 87, 89, 90, 91, 92, 102, 105, 113, 115, 116, 117, 119, 120, 175, 186, 227, 241, 266, 267, 269, 270, 272, 278, 281, 282, 283, 284, 339, 340, 341, 342, 343, 344, 345, 346, 347, 348, 349, 350, 351, 352, 355, 356, 359, 360, 363, 366, 367, 368, 369, 370, 373, 374, 375, 376, 377, 378, 379, 380, 381, 382, 383, 384, 385, 386, 387, 388, 389, 390, 391, 392, 393, 394, 395, 396, 397, 398, 399, 400, 401, 402, 403, 404, 405, 406, 407, 408, 409, 410, 411, 412, 413, 414, 415, 416, 417, 418, 419, 420, 421, 422, 423, 424, 425, 426, 427, 428, 429, 430, 431, 432, 433, 434, 435, 436, 437, 438, 439, 440, 441, 442, 443, 444, 445, 447, 448, 449, 452, 454, 455, 456, 458, 462, 463, 465, 475, 479, 484, 489, 490, 492, 513, 517, 520, 527, 528, 529, 530, 531, 561, 562, 565, 570, 578, 584, 588, 604, 608, 609, 610, 611, 612, 613, 614, 615, 619, 621, 638, 639, 640, 641, 649, 650, 652, 655, 656, 659, 660, 661, 662, 675, 679, 684, 687, 688, 690, 696, 699, 707, 708, 725, 727, 728
```

## N1 validation handoff (2026-11-06)

Tested working-tree implementation based on `ff51c01d38d650d8917fcd45c8061971ddd8a798`;
no commit created. The final README-only handoff update does not affect these inputs.
Python 3 stdlib checks passed (15 tests, about 0.2 seconds), as did `git diff --check`:

```bash
export TMPDIR=/home/dq/.local/state/orchestrator/runs/20261106T232257Z-ebca10/tmp/n1-names
python3 tools/unbound-data-pack/generate_unbound_names.py --rom /home/dq/dualdex-private/unbound/unbound-candidate.gba
python3 tools/unbound-data-pack/generate_unbound_names.py --rom /home/dq/dualdex-private/unbound/unbound-candidate.gba --verify
python3 tools/unbound-data-pack/generate_unbound_names.py --verify
python3 tools/unbound-data-pack/generate_unbound_species.py --verify
(cd tools/unbound-data-pack && python3 -m unittest -v)
```

Generation and private-ROM verification ran under 120-second deadlines with 2 MiB
file-output limits; all finished in less than one second. Numeric-only logs:
`/tmp/n1-generation.log`, `/tmp/n1-rom-verify.log`, `/tmp/n1-integrity.log`.

**Outstanding: Kotlin compilation/tests have not run successfully.** The requested
focused command (Gradle 8.8 / JDK 17.0.20.1) was supervised with the host lock,
30-minute timeout, 64 MiB produced-output ceiling and bounded retained logs:

```bash
flock /home/dq/.local/state/orchestrator/gradle-host.lock timeout --kill-after=5 1800 \
  ./gradlew :app:testDebugUnitTest --no-daemon -Pkotlin.compiler.execution.strategy=in-process \
  --tests 'com.dualdex.pokemon.unbound.*' \
  --tests 'com.dualdex.companion.ui.NavigatorPartyUiTest'
```

The initial attempt failed before tests because the shared wrapper cache is read-only.
One recovery used a temporary writable `GRADLE_USER_HOME`, the existing read-only
wrapper distribution/dependency cache and `--offline`; it failed before tests because
this sandbox forbids Gradle's local daemon socket (`Operation not permitted`). Both
commands exited 1 (0 tests executed), in about 0.1 and 1.0 seconds respectively;
no test job remains active. Bounded command/result/log records are under
`/tmp/n1-gradle-validation/` and `/tmp/n1-gradle-validation-recovery/` (under 50 KiB
combined); the temporary Gradle home uses about 1.7 MiB. The coordinator must rerun
the focused command in a suitable environment and owns the combined `./ci.sh all` gate.
