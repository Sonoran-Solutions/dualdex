# R.O.W.E. 2.1.9.1 startup guard evidence (Phase 6G)

## Determination

The caller's guard chain remains **NOT_VERIFIED**. The Phase 6F trace proves that
the Normal-mode cartridge reached the call at `0x0803014A` and that the call
normalized the observed regional Meowth, but it does not contain the caller's
preceding compare/test instructions, operands, or branch destinations. The exact
ROM and private Phase 6F capture files needed for this bounded follow-up were not
available in the inspected task workspace. No predicates or mode compatibility
are inferred from the callsite hit.

PR #200 is merged. Before work, `origin/main` was `e535a4777338dc7c3b6e95f2316c3f7eef788d1b`;
its Git tree exactly matched reviewed head `23e2ad66efa15fdc31d93980174e10133dc48dbe`.
The senior review was a technical pass for Phase 6F and identified the two
nonblocking validator findings corrected here. No Phase 6F files or evidence
manifests were rewritten.

## Caller and configuration findings

| Evidence | Finding | Classification |
|---|---|---|
| Callsite `0x0803014A` | Reached in the Phase 6F Normal-mode cartridge; calls `0x080A4044` | Historical runtime event; does not establish preceding guards |
| Caller entry / incoming arguments | Not captured in the Phase 6F event set | NOT_VERIFIED |
| Guard compare/test instructions and operands | No exact-ROM instruction bytes or disassembly were available for this follow-up | NOT_VERIFIED |
| Conditional branch outcomes / bypass destinations | Not captured; no false path inferred | NOT_VERIFIED |
| UI configuration | Phase 6D setup selected Normal difficulty, no additional modes, Spring, 10:00 AM, Chinchou and Two Island | Historical setup only; no caller linkage |
| Flags `0x28`, `0x30`, `0x3C`, `0x2B`, `0x470` | Earlier evidence reports these clear; their use by the caller is not established | NOT_LINKED_TO_CALLER |
| Randomized, alternate-difficulty and other configurations | No comparison trace | ALTERNATE_CONFIGURATION_NOT_VERIFIED |

Consequently, caller control flow, guard operand sources, configuration
provenance to the guards, and Normal-mode guard execution are all **PARTIAL** or
**NOT_VERIFIED**. Static false-path behavior is also **NOT_VERIFIED**. The only
verified execution is the downstream Phase 6F normalization path.

## Retained Phase 6F observations

The existing evidence remains narrowly valid for the tested cartridge/startup:

- The selected 76-byte record changed from stored species/form `1560/1` to
  `52/1`; only bytes 28–29 changed.
- The static resolved metadata key remained 1560. The observed UI identified the
  regional Meowth as Dark type.
- The callsite `0x0803014A` entered `0x080A4044`; the routine processed the
  selected party record and returned with the normalized record.
- The species setter store at `0x0809B980` changed packed word
  `0x38581618` to `0x38581034`.
- Phase 6F reports parity against the unmodified pinned core for the selected
  record, party, SaveBlock-copy contents, map/coordinates and video at the two
  recorded checkpoints. That historical result is not a new guard-path parity
  run.

Original and normalized record SHA-256 values are
`af20b2255c921973b4ad9c4229256058b67679f8e707a42e480284aeec5e84d7` and
`db78be9c01606da1caa7718662fad661a95c84052df5d784b07e196ddbb5a175`.
The Phase 6F report and manifest remain the authority for their full context and
private-artifact hashes.

## Trace-validator P3 corrections

`rowe_startup_trace.py` now returns nonzero by default for
`BOUNDARY_PARTIAL`, `CALLSITE_EXECUTED`, `FUNCTION_EXECUTED`, and
`WRITE_ATTRIBUTED`. `--allow-partial` opts into exit zero for those known partial
statuses while the JSON retains the actual status. A full successful status is
still required for default exit zero.

Output handling now validates a private, non-symlink regular-file destination,
rejects resolved-path and hardlink aliases to any input, removes an existing
valid destination before input/evidence admission, and publishes results with an
atomic replace. Invalid destinations are refused before invalidation: touching
one could overwrite an input, follow a symlink into a checkout, or write outside
the permitted private evidence directory. A failure after a valid destination is
prepared publishes `NOT_VERIFIED`, so a stale success cannot represent the new
run. The added tests use invented records/events and cover partial CLI status,
hardlink/symlink rejection, prohibited paths and stale-result invalidation.

## Integration-readiness contract

**Safe to retain:** the exact tested 76-byte player-party record observation,
the two observed Meowth species/form representations, their static metadata key,
the executed startup conversion, and its runtime UI identity as described by
Phase 6F.

**Not established:** caller eligibility or its operand/configuration provenance;
other modes; general species/forms; invalid/unknown forms; effective stats;
boxed Pokémon; reorder/lifecycle changes; enemy/battle mappings; calculator
mechanics; or broad ROM compatibility. Do not use this evidence to activate
production support or a global species/form rule. Keep production compatibility
HOLD and issue #59 OPEN.

## Remaining gate

To resolve the guard question, provide the exact ROM identified in the Phase 6F
manifest and the private Phase 6F capture set in an accessible private directory.
Then disassemble the bounded caller region from that exact ROM, record the full
guard chain and operand provenance, and extend the developer-only trace to
capture each compare/test operand and branch destination. Do not manipulate
RAM/save configuration to force a branch. Until those inputs are available,
alternate paths remain unverified.
