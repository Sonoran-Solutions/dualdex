# ROM-free native memory fixture replay (#152)

`./ci.sh fixture-test` builds and runs the host-only replay suite. It also runs as a
required step of `./ci.sh test` and `./ci.sh all`, including the existing GitHub
Actions test job. No ROM, emulator, Android SDK, QuickJS, upstream source checkout,
or network is needed for the focused target. A POSIX host with a C11 compiler is
required; the production reader retains its existing little-endian host assumption.

The committed fixtures are **synthetic recipes** in
`native/tests/test_memory_fixture.c`, not captured binary data. Each test creates
full `ewram.bin` and `iwram.bin` files in a unique temporary directory, loads them,
asserts decoded observations, and removes the files. All Pokémon bytes, names,
IDs, battle state and coordinates are invented. The literal address expectations
come from the existing vanilla symbol audit and H&S release evidence, independently
of the production config used to read them.

## Boundary and production path

`native/tests/memory_fixture.{h,c}` owns fixed-size, four-byte-aligned storage and
registers it with the **production** `gba_memory_map_add`. `memory_fixture_read`
calls `gba_memory_map_read`; the EWRAM view is obtained using
`gba_memory_map_resolve`. Replay calls the production
`pokemon_read_player_party_gba`, `pokemon_read_enemy_party_gba`,
`pokemon_read_player_location_gba`, and `pokemon_read_battle_lifecycle` APIs with
`pokemon_get_game_config`. No copied decoder or emulator host is linked.

| Input | Required absolute base | Required file length |
| --- | --- | --- |
| EWRAM | `0x02000000` | `0x40000` (262144 bytes) |
| IWRAM | `0x03000000` | `0x8000` (32768 bytes) |

Both regions must occur exactly once; either order is accepted. Other bases,
offsets, duplicate/missing regions, null paths, missing files, empty/truncated
files, swapped files and trailing bytes fail. The loader reads at most the expected
length plus one byte per file; it never allocates based on input metadata, pads,
wraps, mirrors, or stitches ranges. Any failed load clears the entire fixture and
all mappings, so callers cannot replay a previous or partially loaded snapshot.
Return codes distinguish invalid region metadata, I/O failure, and bad length.

GBA pointer values stay **emulated addresses**, never host pointers. The production
map rejects unmapped, overflowing and cross-boundary accesses without changing the
output buffer. Production H&S SaveBlock1 resolution additionally requires an
EWRAM pointer, four-byte alignment, the declared ASLR window, and a fitting save
block. Ordinary byte reads may be unaligned: imposing alignment on every callback
read would reject legitimate packed fields. Tests exercise pointer alignment in
the production pointer consumer, not in a parallel harness validator.

The fixture object must remain at its original address after loading because its
map points into its owned buffers. Do not copy it. Replay resets production reader
caches for each independent snapshot; this suite is single-threaded and does not
model simultaneous mutation or frame-to-frame races. Existing runtime probe and
reader lifecycle tests retain that separate role.

## Assertions and present limits

- FireRed and Emerald: encrypted party records, checksum rejection, moves, PP,
  stats, IVs/EVs, vanilla ability bits, authoritative party counts and current
  location fallback behavior.
- H&S 2.0.5: expansion packed species/moves/PP, ability and gigantamax bits, modified
  nature, 136-byte BattlePokemon stride/live HP, IWRAM battle gate, enemy selection,
  stale enemy rejection, and relocated SaveBlock1 locations at four ASLR offsets.
- Invalid inputs: truncated/oversized regions, invalid addresses and pointers,
  unaligned/out-of-window SaveBlock1, empty/missing claimed party slots, counts
  above six, corrupt checksums, invalid species values and unsupported layouts.
- An in-range unknown species (`1999`) remains a raw numeric ID. Native code
  currently validates numeric plausibility, **not data-pack membership**; the
  production data-pack/UI boundary owns unknown naming. Values `0`, `2000`, and
  `2047` fail native parsing. This harness neither adds a fallback name nor changes
  that policy.
- Vanilla lifecycle remains `UNKNOWN`, and its enemy party is unavailable through
  the authoritative API. Its location test pins the existing EWRAM fallback, not
  a verified vanilla SaveBlock pointer. H&S supplies the pointer/relocation proof.

This is host regression evidence, not newly verified ROM support. It does not
implement #8, alter profile JSON, detection, trust, live emulator/save behavior,
or provide a headless capture tool. It completes only the bounded replay
infrastructure portion of #152; the broader issue remains open. No PokeDaisey code,
scripts, tables or capture data were copied.

## Replaying local captures

After building the focused target, an investigator can run:

```bash
./native/build/test_memory_fixture --replay hns205 /private/case/ewram.bin /private/case/iwram.bin
# Layout names currently accepted: firered, emerald, hns205
```

This prints only party counts, location validity and the lifecycle enum. Exit zero
means the files/layout were accepted and the production readers ran; **it does not
mean the observations are correct or the ROM is verified**. Empty or malformed
party content is reported through the reader's normal empty/unknown result. Add
explicit expected-value assertions for a regression. Unbound and all other layouts
are rejected by this host-only coverage allowlist until their fixtures are added.

### Adding capture-backed evidence for #59 / #60

1. Select an exact release and record its source revision, ROM digest (not ROM
   bytes), emulator/core revision, capture tool/version and scenario. Capture both
   regions at the same paused instant. Keep raw captures and personal saves
   **outside the repository**. `ewram.bin`/`iwram.bin` are ignored as an additional
   accidental-staging guard, not a sanitizer.
2. Repeat the scenario across 3–5 boots with varied timing. Record pointer values
   and observed relocation separately from static globals. Include overworld,
   battle entry, switch/faint/replacement and exit where relevant. A synthetic
   test alone cannot prove an address is stable or a lifecycle flag authoritative.
3. Build an allowlist of the exact byte ranges needed by the production API under
   test. Start sanitized fixtures from zero-filled regions and copy only reviewed
   fields. Exclude code/graphics/text copied from ROM, storage boxes, mail, link
   records, names, trainer IDs, unique Pokémon identities and unrelated SaveBlock
   data. Never publish whole raw RAM dumps or assume RAM is free of copyrighted
   assets or identifying data.
4. Prefer a deterministic synthetic recipe reproducing the observed relationships.
   Replace PID/OTID, nickname and OT name with documented fictitious values; rebuild
   encryption/checksums and any dependent identity/battle fields consistently.
   Preserve relevant pointers, offsets, packed flags and relative field structure.
   Record exactly which bytes were reconstructed versus observed. If sanitized
   binary evidence is truly necessary, require a byte-range/privacy/provenance
   review before explicitly adding it; do not force-add unreviewed dumps.
5. Store an evidence note beside the test describing the exact release, source/ABI
   basis, scenario, region sizes and SHA-256 digests of the **sanitized** artifacts,
   retained ranges, transformations, and expected output with its independent
   basis (source, debugger or observed game state). Do not include local personal
   paths or player identifiers. Review the staged diff/file list before committing.
6. Add a case using `memory_fixture_load` and the existing production APIs. Assert
   both expected results and failure cases, especially a relocated pointer, wrong
   layout, unknown ID and missing bytes. Keep evidence-derived expected values
   independent of the current decoder. Run `./ci.sh fixture-test`, `./ci.sh all`,
   and `./ci.sh source-check` where the pinned H&S source is available.

For **R.O.W.E. (#59)**, this supplies the bounded memory transport and deterministic
regression runner for investigating the reported custom unencrypted party layout.
That layout still needs its own proven production decoder; do not select H&S to
make a R.O.W.E. dump parse. For **Unbound (#60)**, it enables the existing CFRU path
to be audited against exact-build party, ID, pointer and battle evidence without
repeated emulator boots. Its existing config is not proof of correctness. Extend
the replay allowlist only alongside explicit assertions for that investigation;
new runtime verification remains subject to each issue's exact-release evidence
and trust requirements. This infrastructure does not start either ROM integration.

See [the H&S closure audit](HNS_ISSUE_40_CLOSURE_AUDIT.md),
[H&S compatibility evidence](HNS_2_0_5_COMPATIBILITY_EVIDENCE.md), and
[vanilla evidence](VANILLA_CALCULATOR_EVIDENCE.md). Existing generated H&S ABI/source
tests and `source-check` remain the authority for generated layout consistency;
replay complements them by exercising the production reader on complete regions.
