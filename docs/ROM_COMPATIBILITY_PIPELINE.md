# ROM compatibility onboarding pipeline

How a new Gen III ROM hack gets from "a file someone asked about" to a reviewed memory config.
Everything here is host-only and ROM-free in CI; real ROMs, saves and RAM dumps stay private and
never enter the repository (AGENTS.md section 4). No step below is a support verdict.

```
rom_identify.py -> gamedata/gen3/<hack>.json (inherit a base) -> rom_observe.py captures
  -> generate_gamedata.py --write -> ROM-free fixture -> ./ci.sh fixture-test -> Tier B -> Tier A
```

## 1. Identify the ROM and pick a family base

```bash
python3 tools/rom-evidence/rom_identify.py /private/hack.gba          # or --json
```

Reads at most 64 MiB, then reports SHA-256, CRC32, header title/game code/maker/revision and the
header checksums, a **family**, and the closest base config in `gamedata/gen3/`.

| Family | Meaning (heuristic) | Base |
|---|---|---|
| `vanilla-retail` | retail code + title + 16 MiB (header-only; pin the SHA-256 to be sure) | `firered_rev0`, `leafgreen`, `emerald`, `ruby`, `sapphire` |
| `pokeemerald-expansion` | `RHHEXP`/`RHH_EXP` marker found | `emerald` |
| `cfru-inplace` | 32 MiB `BPRE` (Complete FireRed Upgrade layout) | `firered_rev0` (siblings: `radical_red`, `unbound`) |
| `firered-inplace` / `emerald-inplace` | retail game code, non-retail size or title | `firered_rev0` / `emerald` |
| `unknown` | no known Gen III game code | none |

Expansion builds relocate symbols per build, so treat their base as a starting point only.

## 2. Inherit a base config

Add `gamedata/gen3/<hack>.json` with `"base": "<base id>"` and put **only the fields the hack
changes** under `fields` (tagged addresses such as `{"ewram": "0x24284"}`; see the header comment in
`tools/gamedata/generate_gamedata.py` for the schema). `evidence` is mandatory and must say where each
value came from. Inspect the merged result with
`python3 tools/gamedata/generate_gamedata.py --resolve <hack>`.

## 3. Capture varied boots

```bash
python3 tools/rom-evidence/rom_observe.py --rom /private/hack.gba --core /private/core.so \
  --movie tools/rom-evidence/rowe-starter.movie --boots 3 --boot-jitter 7 \
  --probe party_count=0x02024029:1 --output /private/stability.json
```

Replays the movie (same bounded grammar as `rowe_observe.py`: `run`, `mash`, `save`, `restore`,
`observe <label>`) in 2-3 fresh boots; boot *n* idles `n x --boot-jitter` frames first because the core
is deterministic. At each `observe`, EWRAM and IWRAM are compared across boots and the report lists
drifting address ranges (counts exact, ranges capped), per-boot SHA-256 and a `stable`/`drifting`
verdict per `--probe`. RAM contents are never written. Run it from outside any Git checkout for private
outputs.

- A candidate address that is **stable** across boots *and* movie labels is worth evidence; one that
  **drifts** is a pointer, timer or allocator slot and must be resolved through a pointer (for example a
  save block pointer), not hard-coded.
- Stability is necessary, not sufficient: confirm meaning with a known value (species, HP, party count).

## 4. Regenerate the native header

```bash
python3 tools/gamedata/generate_gamedata.py --write   # then --check must pass
```

Never hand-edit `native/src/gamedata_configs_gen.h`; `./ci.sh test` checks it byte-for-byte.

## 5. Add a ROM-free fixture and run the focused gate

Synthesize EWRAM/IWRAM images from the captured *layout* (offsets and structure, no ROM or save
bytes) in `native/tests/`, following `test_memory_fixture.c`, and assert the reader decodes the party
the config describes. Then:

```bash
./ci.sh fixture-test        # focused; ./ci.sh all once per review candidate
```

## 6. Tier B (`ESTIMATED`) versus Tier A (`VERIFIED`)

- **Tier B - `ESTIMATED`**: offsets inherited from a base or taken from documentation or a community
  disassembly, supported by ROM-free fixtures and stable-across-boots captures. Keep the unverified
  storage mode (e.g. `PKMN_STORAGE_CFRU_UNVERIFIED`) so the UI never presents guessed ability/shininess
  data as fact. This is where a new hack lands.
- **Tier A - `VERIFIED`**: promoted only with exact-ROM evidence: the pinned SHA-256 of the release,
  symbol/runtime observations that match the config, and review evidence recorded under
  `tools/rom-evidence/evidence/`. Fields like `battleStateReadVerified` stay owner-gated; promotion
  is a human decision, never a consequence of green CI.

Do not promote on header-only classification, a single boot, or a drifting address.
