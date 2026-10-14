# Local ROM evidence tools

Host-only, Python standard library. No downloads, production profiles or trusted hashes.
The R.O.W.E. pins are **diagnostic input guards**, not permission to enable support. See
[the release evidence matrix](../../docs/ROWE_2_1_9_1_RELEASE_EVIDENCE.md) for findings and limits.

## Independently verify a UPS release

Use your legally obtained **unheadered** base and the publisher's pinned patch. The tool never
writes the patched ROM: it checks the patch SHA-256, patch CRC, source length/CRC, bounded UPS
records, target length/CRC, and optionally byte-for-byte equality with an existing local ROM.
It prints full source/target hashes and descriptive GBA header fields only after success.

```bash
python3 tools/rom-evidence/ups_identity.py \
  --patch /private/pkmn_rowe_experimental.ups \
  --patch-sha256 41431249ec06033e77950603be22091bd0ea465dbf573a645eec642d397d26f9 \
  --source /private/emerald.gba --candidate /private/rowe.gba > /tmp/identity.json
```

Omitting `--source` validates only the patch and reports `rom_identity: NOT_VERIFIED`.
A candidate requires a source. No header stripping, checksum bypass, reverse patching or
network fallback. Files are bounded to 64 MiB ROM / 128 MiB patch. A wrong pin, CRC, length,
malformed integer, unterminated/out-of-range XOR record or unequal candidate exits nonzero.
SHA-256 pins bind provenance; CRC alone is not an authenticity mechanism. UPS footer patch CRC
covers all but its own last four bytes; whole-file CRC `2144df1c` is not that footer value.

## Inspect exact-release symbols

```bash
python3 tools/rom-evidence/rowe_symbols.py --rom /private/rowe.gba > /tmp/symbols.json
```

Refuses other ROM hashes. Bounds-checks all 150 pointer/address records and ASCII names, refuses
duplicates/unterminated names, and emits only six relevant symbols. It does not dereference
symbol target addresses, extract game tables, or claim field widths/runtime authority.

## Read-only runtime observations

Use the existing native developer adapter and a legally built local mGBA core:

```bash
./tools/vanilla-runtime-probe/build.sh
python3 tools/rom-evidence/rowe_observe.py \
  --rom /private/rowe.gba --core /private/mgba_libretro.so \
  --movie tools/rom-evidence/rowe-starter.movie \
  --output /tmp/rowe-observed.json --screens /tmp/rowe-private-screens
```

The pinned core and its source revision are documented in the evidence matrix. No production
party decoder or vanilla scenario is called. The existing bounds-checked native memory-map
reader, input stepping and temporary save-state API are reused. There are **no RAM writes**.
The report path may not overwrite a named input, and existing screenshots are never overwritten.
A temporary copy of the already hashed ROM is loaded; fresh cartridge RAM, no external save,
no BIOS. The tool records its source, adapter, core and movie hashes. Output is initialized to
FAIL before opening inputs, so a failed rerun cannot leave a stale successful report.
`OBSERVED` means capture completed; `runtime_layout` deliberately stays `NOT_VERIFIED`.

The input movie contains `run FRAMES [MASK]`, `mash COUNT [MASK]` (one pressed frame + 90 neutral;
default A), `observe LABEL`, `save`, and `restore`. Counts, masks, total frames and unique labels
are bounded. Save/restore refer only to a private temporary state made by this run. A neutral
frame after restoring refreshes the host's cached video; the displayed animation need not have
the same hash as the original frame. Button masks: A=256, B=1, Start=8, Up=16, Down=32, Left=64,
Right=128. Inputs step the emulator synchronously; no frame executes between snapshot reads.

The committed observations used this movie in one fresh process, then another with **only
the first `run 180` changed to `run 240`**. To reproduce the latter without editing the fixture:

```bash
python3 - <<'PY'
from pathlib import Path
p = Path('tools/rom-evidence/rowe-starter.movie')
Path('/tmp/rowe-delayed.movie').write_text(p.read_text().replace('run 180\n', 'run 240\n', 1))
PY
# Use /tmp/rowe-delayed.movie as --movie in the command above.
```

Game-generated starter/OT values can differ with timing/RTC. Input scripts are repeatable;
identical party identities are not a cross-environment promise. Check the actual screen and
record its displayed values. An `after_move24_input` label describes inputs, not proof a swap
happened: two-move Magikarp cannot swap into an empty slot. The four-move Throh run did perform
all three swaps. Neither run proves slot stride. The observer intentionally records raw fields,
including implausible/empty values, without claiming `ParsedPokemon` validity. Count >6, wrong
ROM/core, unavailable RAM or unexpected video fail. No scan or fallback guesses a party.

The optional screenshots are private PPMs; never commit them, ROMs, saves, states, or RAM dumps.
Committed evidence contains selected numerical observations, name-field hex, checksums and
manual UI transcriptions only. Checkpoint hashes bind private captures; they are not substitutes
for independently observing a new run. Extend the movie/protocol for additional scenes; do not
promote these narrow records into a general supported-layout flag.

## ROM-free checks

```bash
python3 -m unittest discover -s tools/rom-evidence -v
./ci.sh all
HNS_UPSTREAM_DIR=/path/to/pinned/pokehns-expansion ./ci.sh source-check
```

Canonical CI runs only invented UPS/symbol/slot bytes, input/read failure cases and evidence
metadata checks. It never loads a ROM/core or runs the observation movie. No third-party code,
fixtures, source headers, generated game tables or copyrighted game assets are bundled.

## Revision 3: party indexing, battle and cartridge experiments

[Revision-3 matrix/contract](../../docs/ROWE_2_1_9_1_PARTY_LAYOUT_EVIDENCE.md) supersedes
revision 2's unknown stride/width findings without changing production support.

```bash
python3 tools/rom-evidence/rowe_static.py --rom /private/rowe.gba > /tmp/accessors.json
python3 tools/rom-evidence/rowe_observe.py \
  --rom /private/rowe.gba --core /private/mgba_libretro.so \
  --initial-state /private/own-two-slot.state \
  --movie tools/rom-evidence/rowe-party-save.movie \
  --cartridge-dir /tmp/own-new-cartridge --output /tmp/save-observed.json \
  --screens /tmp/own-private-save-captures
# After that process exits: a new process; no initial state.
python3 tools/rom-evidence/rowe_observe.py \
  --rom /private/rowe.gba --core /private/mgba_libretro.so \
  --load-cartridge --cartridge-dir /tmp/own-new-cartridge \
  --movie tools/rom-evidence/rowe-party-reload.movie \
  --output /tmp/reload-observed.json --screens /tmp/own-private-reload-captures
# Battle/HP/PP/Potion capture starts at the own-gameplay battle checkpoint.
python3 tools/rom-evidence/rowe_observe.py \
  --rom /private/rowe.gba --core /private/mgba_libretro.so \
  --initial-state /private/own-battle.state \
  --movie tools/rom-evidence/rowe-battle-heal.movie \
  --output /tmp/battle-observed.json --screens /tmp/own-private-battle-captures
```

These movies reproduce from privately retained compatible experiment checkpoints, made only
by ordinary gameplay. They do not manufacture Pokémon or guarantee a fresh catch route. Make
and validate your own two-slot party first; bootstrap hashes and UI values can differ. This
tool's `save`/`restore` remain temporary emulator states. `--initial-state` reports
`PRIVATE_EXPERIMENT_STATE` explicitly; it cannot be combined with `--load-cartridge`.
`cartridge_save` only flushes cartridge RAM; the movie must first perform the actual UI save.
The native host does not automatically flush on cleanup. A new exclusive cartridge file and
experiment marker bind ROM/save digests; existing files fail rather than being overwritten.
A cartridge load requires both the matching marker and save, and reports `CARTRIDGE_RELOAD`.
It never loads a Downloads or personal save by default.

[adapter-pin.json](adapter-pin.json) checks the existing adapter's source/build inputs and
expected binary before CDLL/core initialization. It intentionally refuses another compiler's
output until separately reviewed; the pin is diagnostic only. Count/data/count plus repeated
456-byte reads fail on any mismatch while stepping is paused. All six slot digests and excluded
species are recorded as diagnostics; only counted slots are parsed as occupied. Counts >6 fail;
a zero enemy count with nonzero excluded species does not authorize enemy scanning.

`--screens` now retains **private** 456-byte party snapshots beside exclusive PPMs for whole-record
comparison. Never commit either, compatible states or cartridge data. The committed sanitized
revision-3 JSON preserves hashes/numerical fields and manual UI transcriptions. The static
accessor JSON's inspector digest binds the source used; spans bind the independently inspected
ROM regions without distributing bytes or GPL implementation. Historical revision-2 captures
retain their original observer hash at the reviewed parent. Failed routes, unrun cases and
limited scope are explicit in revision 3; OBSERVED is still not a correctness/support verdict.
