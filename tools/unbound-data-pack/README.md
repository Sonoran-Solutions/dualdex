# Unbound species table generator (issue #26)

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
