# Group E production dispositions

`decisions.json` is the reviewed tier/reason authority for the ability/item audit remainder.
It is not a clearance allow-list: exact and request-local rules run first, UNKNOWN still refuses,
and only an explicit caveat tier plus positive RELEVANT evidence can be neutralized.

From the repository root:

```sh
python3 tools/hns-group-e/generate_closure.py
python3 tools/hns-group-e/generate_closure.py --check --upstream-dir "$HNS_UPSTREAM_DIR"
python3 tools/hns-group-e/compare_census.py
```

Regenerate the ability/item inventories first using their source-pinned generators. The closure
generator refuses missing/extra ability, hold-effect or identity-exception assignments, emits the
production Kotlin table and exhaustive Markdown matrix, and optionally checks pinned source
assertions. `./ci.sh test` runs artifact reconciliation and negative mutation tests;
`./ci.sh source-check` also checks the upstream assertions and the upstream-derived inventories.
Never hand-edit the generated matrix or Kotlin data.

The comparison reads the immutable starting main SHA from Git and the current production census.
It checks unchanged request keys and records all no-result battles and named refusal counts.
The full per-request enumeration remains in the census gzip. Regenerate it using the canonical
commands in `docs/HNS_CALC_CENSUS.md`, including the full-census environment flag.

This audit does not certify closure while any pinned trainer battle has no displayed result.
