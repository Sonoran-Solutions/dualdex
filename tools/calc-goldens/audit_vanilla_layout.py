#!/usr/bin/env python3
"""Audit the read-only vanilla FireRed / Emerald memory layout against profile offsets.

Why this exists
---------------
Repairing the vanilla profile SHA-256 values made `RuntimeRomTrust.exactRuntimeVerified`
reachable for the first time, and that flag is the universal gate for *every* profile-dependent
read (player party, enemy party, battle presence, active battler, stat stages, location), not a
calculator-only switch. FireRed Rev 0 and Rev 1 share ONE profile and therefore ONE memory-layout
table, so "both revisions are accepted" is only sound if both revisions really do place every
audited symbol at the same address.

This tool turns that requirement into a check instead of an assumption. Given the ELF files built
from the pinned decompilations, it verifies:

  1. the player/enemy party group addresses the bundled profiles declare are the addresses the
     build actually produces (per game);
  2. FireRed Rev 0 and Rev 1 have *byte-identical* EWRAM and IWRAM symbol maps, so every
     exact-trust-gated read authorized through the shared profile is authorized for both;
  3. `sizeof(struct BattlePokemon)` and the `hp` / `statStages` fields match the offsets compiled
     into `native/src/pokemon_reader.c`, per game, without parsing C.

It deliberately does not guess: anything it cannot derive from its inputs is reported UNPROVEN and
the exit status stays 0 only when no check failed. Builds whose assets are stubbed (see below)
still give a valid *differential* answer for (2), and give an exact answer for (1) whenever the
linker places the party group at the address the real ROM uses.

Building the inputs
-------------------
    # pret/pokefirered @ the pinned commit, once per revision:
    make -j"$(nproc)" SETUP_PREREQS=0 firered        # -> pokefirered.elf
    make -j"$(nproc)" SETUP_PREREQS=0 firered_rev1   # -> pokefirered_rev1.elf
    # pret/pokeemerald @ the pinned commit:
    make -j"$(nproc)" SETUP_PREREQS=0                # -> pokeemerald.elf

Usage
-----
    python3 tools/calc-goldens/audit_vanilla_layout.py \
        --firered-rev0 /path/pokefirered.elf \
        --firered-rev1 /path/pokefirered_rev1.elf \
        --emerald      /path/pokeemerald.elf

Exit status is non-zero when a check fails, so the audit can be wired into a gate.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PROFILE_DIR = os.path.join(REPO_ROOT, "app", "src", "main", "assets", "profiles")
READER_C = os.path.join(REPO_ROOT, "native", "src", "pokemon_reader.c")

EWRAM_BASE = 0x02000000
IWRAM_BASE = 0x03000000

# `struct BattlePokemon` layout, from the pinned headers (`include/pokemon.h`):
#
#   /*0x00*/ u16 species;  /*0x02*/ u16 attack;   /*0x04*/ u16 defense;
#   /*0x06*/ u16 speed;    /*0x08*/ u16 spAttack; /*0x0A*/ u16 spDefense;
#   /*0x0C*/ u16 moves[4];
#   /*0x14*/ u32 hpIV:5, attackIV:5, defenseIV:5, speedIV:5, spAttackIV:5, spDefenseIV:5,
#            isEgg:1, abilityNum:1;          <- four packed bitfield words, 0x14..0x17
#   /*0x18*/ s8  statStages[NUM_BATTLE_STATS];   NUM_BATTLE_STATS == 8
#   /*0x20*/ u8  ability;  /*0x21*/ u8 type1;     /*0x22*/ u8 type2;
#   /*0x23*/ u8  unknown;  /*0x24*/ u8 pp[4];
#   /*0x28*/ u16 hp;       /*0x2A*/ u8 level;     /*0x2B*/ u8 friendship;
#   /*0x2C*/ u16 maxHP;    /*0x2E*/ u16 item;     ... /*0x54*/ u32 otId;
#
# The sizes are what the pinned decompilations compile to; both games agree.
BATTLE_POKEMON_SIZE = 88
BATTLE_MONS_HP_OFFSET = 0x28
BATTLE_MONS_STAT_STAGES_OFFSET = 0x18

# `struct Pokemon` is a BoxPokemon (80 bytes) plus status/level/hp/stat words: 100 bytes in both
# games, which is why a party slot stride of 100 is used everywhere.
POKEMON_SIZE = 100

# `struct SaveBlock1` offsets the location reader reads through the legacy party-relative path
# (`native/src/pokemon_reader.c` `pokemon_read_player_location_gba`): pos, location, escapeWarp.
SAVE_BLOCK1_POS_OFFSET = 0x00
SAVE_BLOCK1_LOCATION_OFFSET = 0x04
SAVE_BLOCK1_ESCAPE_WARP_OFFSET = 0x24

# Every symbol that must be identical between the two FireRed revisions, i.e. the whole
# exact-trust-gated read surface plus the party group. Reported individually so a diff is legible.
AUDITED_FIRERED_SYMBOLS = (
    "gPlayerPartyCount",
    "gPlayerParty",
    "gEnemyPartyCount",
    "gEnemyParty",
    "gBattleMons",
    "gBattlersCount",
    "gBattleTypeFlags",
    "gBattleOutcome",
    "gBattlerPartyIndexes",
    "gBattlerPositions",
    "gAbsentBattlerFlags",
    "gSideStatuses",
    "gMain",
    "gSaveBlock1Ptr",
    "gSaveBlock2Ptr",
    "gPokemonStoragePtr",
)


class Failure(Exception):
    """One audit check failed."""


def _nm() -> str:
    """Resolve `arm-none-eabi-nm`, preferring the toolchain the pret Makefiles use."""
    for candidate in (
        os.environ.get("ARM_NONE_EABI_NM"),
        "arm-none-eabi-nm",
    ):
        if not candidate:
            continue
        try:
            subprocess.run([candidate, "--version"], capture_output=True, check=True)
            return candidate
        except (OSError, subprocess.CalledProcessError):
            continue
    raise Failure(
        "arm-none-eabi-nm was not found; put the Arm GNU toolchain (or devkitARM) bin directory "
        "on PATH or set ARM_NONE_EABI_NM"
    )


def read_symbols(elf: str) -> dict[str, tuple[int, str, int]]:
    """Return {name: (address, type, size)} for every defined object symbol in `elf`."""
    out = subprocess.run(
        [_nm(), "-S", "-n", "--defined-only", elf],
        capture_output=True,
        text=True,
    )
    if out.returncode != 0:
        raise Failure(f"nm failed on {elf}: {out.stderr.strip()}")

    symbols: dict[str, tuple[int, str, int]] = {}
    # `nm -S` emits `ADDRESS SIZE TYPE NAME`; the size field is omitted for symbols it cannot size.
    # Parsing is positional from the RIGHT so a symbol whose name is a decimal-looking token (for
    # example `u32_2`) can never be mistaken for the size field.
    for line in out.stdout.splitlines():
        parts = line.split()
        if len(parts) >= 4:
            name = parts[-1]
            kind = parts[-2]
            address, size = parts[0], parts[1]
        elif len(parts) == 3:
            address, kind, name = parts
            size = "0"
        else:
            continue
        try:
            symbols[name] = (int(address, 16), kind, int(size, 16))
        except ValueError:
            continue
    return symbols


# ---------------------------------------------------------------------------
# Retail ROM probe.
#
# A decompilation build proves the address of a symbol *in that build*. For the EWRAM globals whose
# placement is linker-ordered inside the section that also holds asset arrays, a build with stubbed
# assets cannot prove the absolute retail address (see the UNPROVEN note in the report). The retail
# image can, directly: a Game Boy Advance program reaches a global by loading a literal word that
# holds its address, and those literal pools live in the ROM. Finding the configured address as a
# little-endian word in the retail dump therefore proves the retail program itself uses that address.
#
# Three properties keep this from being a coincidence hunt:
#   * it runs against the ROM the profile's own SHA-256 accepts, so the bytes are the exact build;
#   * the same scan is run over addresses the party-group checks have ALREADY proven structurally
#     (gPlayerParty, gEnemyParty), so the method is validated on known-good inputs in the same run;
#   * the hit count is reported. What the method concludes from it is one-sided, and the count's
#     magnitude is not part of that conclusion:
#       - ZERO hits means the scan did NOT prove the address. That is the only thing zero establishes
#         (see the caveat at the report site: a symbol can be reached through an offset from a nearby
#         base, so absence of its own literal is not proof that the configured value is wrong).
#       - ONE OR MORE hits means the retail program does reference the address. A large count is
#         expected, not suspicious: these are heavily used engine globals, so the same base word is
#         loaded from many functions and pools. On the two accepted retail dumps the positive
#         controls alone report 745 and 392 for FireRed (gPlayerParty, gEnemyParty) and 1091 and 532
#         for Emerald, and those controls passing is what makes the same scan's verdict on the battle
#         offsets meaningful.
# ---------------------------------------------------------------------------
ROM_BODY_START = 0x000000C0  # first byte after the 192-byte cartridge header


def find_literal_references(rom: bytes, address: int) -> list[int]:
    """Offsets in `rom` holding `address` as a little-endian word (4-byte aligned)."""
    needle = address.to_bytes(4, "little")
    hits: list[int] = []
    start = ROM_BODY_START
    while True:
        index = rom.find(needle, start)
        if index < 0:
            break
        if index % 4 == 0:
            hits.append(index)
        start = index + 1
    return hits


def read_rom(path: str) -> bytes:
    with open(path, "rb") as handle:
        return handle.read()


def sha256_of(path: str) -> str:
    import hashlib

    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def check_retail_rom(
    label: str,
    rom_path: str,
    profile: dict,
    config: dict[str, int],
) -> tuple[list[str], list[str]]:
    """Probe one retail dump. Returns (notes, problems)."""
    notes: list[str] = []
    problems: list[str] = []

    rom = read_rom(rom_path)
    digest = sha256_of(rom_path)
    if digest not in profile["sha256Hashes"]:
        problems.append(
            f"{label}: the supplied dump has SHA-256 {digest}, which the profile does not accept; "
            "a probe against unknown bytes proves nothing"
        )
        return notes, problems
    notes.append(f"{label}: dump SHA-256 {digest[:16]}... is an accepted profile hash")

    # Positive controls: addresses the structural checks already accepted. If the method does not
    # find these, it cannot be trusted for the address it is actually being asked to prove.
    for symbol in ("gPlayerParty", "gEnemyParty"):
        expected = EWRAM_BASE + config["player_party_offset" if symbol == "gPlayerParty" else "enemy_party_offset"]
        hits = find_literal_references(rom, expected)
        if not hits:
            problems.append(
                f"{label}: positive control failed - no literal reference to the already-proven "
                f"{symbol} address 0x{expected:08X} was found, so this probe cannot be trusted for "
                "the battle offsets either"
            )
        else:
            notes.append(
                f"{label}: positive control {symbol} 0x{expected:08X} appears as a literal at "
                f"{len(hits)} ROM offset(s)"
            )

    # The addresses under audit: the read surface that exact-hash trust newly unlocks.
    audited = [
        ("battle_mons_offset (gBattleMons base)", EWRAM_BASE + config["battle_mons_offset"]),
    ]
    for field in ("battlers_count_offset", "battle_type_flags_offset", "battle_outcome_offset",
                  "battler_party_indexes_offset", "battler_positions_offset",
                  "absent_battler_flags_offset", "side_statuses_offset"):
        if config.get(field):
            audited.append((field.replace("_offset", ""), EWRAM_BASE + config[field]))

    for name, address in audited:
        hits = find_literal_references(rom, address)
        if hits:
            notes.append(
                f"{label}: {name} 0x{address:08X} appears as a literal at {len(hits)} ROM "
                f"offset(s), starting 0x{hits[0]:08X}"
            )
        else:
            # What an absent literal establishes, and what it does not.
            #
            # It establishes that this scan did NOT prove the address: no instruction in the image
            # loads it as a whole word. It does NOT establish that the address is wrong. A compiler
            # may materialise a nearby base or group address and reach the symbol with an added
            # offset, and it may keep the address in a table the scan does not reach, so a symbol
            # can be used by the retail program without its own address ever appearing as a literal.
            #
            # The neighbouring count is therefore reported as context, not as a verdict: a cluster
            # of referenced addresses around this one is consistent with the address being reached
            # via an offset, and also consistent with the configured value being wrong. Deciding
            # between those needs the retail build's debug symbols, which `pret/pokefirered` does
            # not publish, so this tool reports UNPROVEN and changes nothing. A guessed replacement
            # is exactly what must not enter production.
            nearby = sum(
                1
                for delta in range(-0x200, 0x200, 4)
                if find_literal_references(rom, address + delta)
            )
            problems.append(
                f"{label}: {name} 0x{address:08X} was NOT proved: no literal in the image holds it "
                f"as a whole word ({nearby} addresses within +/-0x200 of it are referenced, which is "
                "consistent with the symbol being reached through an offset from a nearby base, or "
                "with the configured value being wrong - the scan cannot tell the two apart). The "
                "address stays UNPROVEN and the capability that consumes it stays closed."
            )
    return notes, problems


# Compiler-suffixed local statics: `sGengarScroll.182` in one revision is `sGengarScroll.185` in
# the other because an unrelated static in the same translation unit was added or removed. The
# SUFFIX is an artifact of how many such locals `gcc`/agbcc had already emitted, so it must not be
# compared across separate compilations; the addresses and the set of base names still must be.
_LOCAL_STATIC_SUFFIX = re.compile(r"\.\d+$")


def _canonical_symbol_name(name: str) -> str:
    return _LOCAL_STATIC_SUFFIX.sub("", name)


def ram_map(symbols: dict[str, tuple[int, str, int]]) -> dict[str, tuple[int, str, int]]:
    """The EWRAM+IWRAM slice of a symbol table: what an address-based reader can reach."""
    return {
        name: value
        for name, value in symbols.items()
        if EWRAM_BASE <= value[0] < EWRAM_BASE + 0x40000 or IWRAM_BASE <= value[0] < IWRAM_BASE + 0x8000
    }


def load_profile(name: str) -> dict:
    with open(os.path.join(PROFILE_DIR, name), encoding="utf-8") as handle:
        return json.load(handle)


def reader_config(name: str) -> dict[str, int]:
    """Read `CONFIG_<name>`'s numeric fields straight out of the production reader source.

    The audit must test the offsets the application actually compiles, not a copy of them, so the
    values are scraped from `native/src/pokemon_reader.c` rather than restated here.
    """
    with open(READER_C, encoding="utf-8") as handle:
        source = handle.read()

    match = re.search(
        r"static const GameMemoryConfig CONFIG_" + re.escape(name) + r"\s*=\s*\{(.*?)\n\};",
        source,
        re.S,
    )
    if not match:
        raise Failure(f"CONFIG_{name} not found in {READER_C}")

    body = match.group(1)
    fields: dict[str, int] = {}
    for field in re.finditer(r"\.([a-z0-9_]+)\s*=\s*(0x[0-9A-Fa-f]+|\d+)\s*,", body):
        fields[field.group(1)] = int(field.group(2), 0)
    return fields


def require_symbol(symbols: dict[str, tuple[int, str, int]], name: str, elf: str) -> tuple[int, str, int]:
    if name not in symbols:
        raise Failure(f"{name} is not defined in {os.path.basename(elf)}")
    return symbols[name]


def check_party_group(
    label: str,
    elf: str,
    symbols: dict[str, tuple[int, str, int]],
    profile: dict,
    config: dict[str, int],
) -> list[str]:
    """Check the profile's declared party offsets against the built ELF. Returns notes."""
    notes: list[str] = []

    player = require_symbol(symbols, "gPlayerParty", elf)
    player_count = require_symbol(symbols, "gPlayerPartyCount", elf)
    enemy = require_symbol(symbols, "gEnemyParty", elf)
    enemy_count = require_symbol(symbols, "gEnemyPartyCount", elf)

    # Two offset conventions meet here, and the audit must respect both:
    #   * `app/src/main/assets/profiles/<id>.json` stores ABSOLUTE GBA addresses
    #     (`vanilla_firered.json` playerPartyOffset == 33702532 == 0x02024284);
    #   * `native/src/pokemon_reader.c` stores EWRAM-RELATIVE offsets at the same addresses
    #     (`.player_party_offset = 0x24284`, used as `EWRAM_BASE + offset`).
    # Both are compared against the symbol table's absolute address.
    for symbol_name, value, relative in (
        ("gPlayerParty", player, config["player_party_offset"]),
        ("gEnemyParty", enemy, config["enemy_party_offset"]),
        ("gPlayerPartyCount", player_count, config["player_party_count_offset"]),
        ("gEnemyPartyCount", enemy_count, config["enemy_party_count_offset"]),
    ):
        expected = EWRAM_BASE + relative
        if value[0] != expected:
            raise Failure(
                f"{label}: {symbol_name} is at 0x{value[0]:08X} in the build but "
                f"native/src/pokemon_reader.c declares EWRAM+0x{relative:X} = 0x{expected:08X}"
            )
    for symbol_name, value, absolute in (
        ("gPlayerParty", player, profile["playerPartyOffset"]),
        ("gEnemyParty", enemy, profile["enemyPartyOffset"]),
    ):
        if value[0] != absolute:
            raise Failure(
                f"{label}: {symbol_name} is at 0x{value[0]:08X} in the build but the shipped "
                f"profile declares 0x{absolute:08X}"
            )
    notes.append(
        f"{label}: party group matches the shipped offsets (player 0x{player[0]:08X}, "
        f"enemy 0x{enemy[0]:08X})"
    )

    # Party strides and the count-before-party relationship.
    #
    # `nm -S` reports the size of an object only when the object file carries it; a `{}`-initialised
    # array in .bss can come back as a one-byte/zero size, so the stride is also proven from the
    # linked GAP: the enemy party array must start exactly one array-length before the player party
    # array, which is only possible if both really are six 100-byte party slots.
    for name, value in (("gPlayerParty", player), ("gEnemyParty", enemy)):
        if value[2] not in (0, 1) and value[2] != 6 * POKEMON_SIZE:
            raise Failure(f"{label}: {name} size is {value[2]}, expected {6 * POKEMON_SIZE}")
    # Both games place the two party arrays back to back. Which one comes first is the game's own
    # source order - FireRed declares gEnemyParty first, Emerald gPlayerParty - so only the
    # distance is asserted, not the direction.
    if abs(player[0] - enemy[0]) != 6 * POKEMON_SIZE:
        raise Failure(
            f"{label}: gPlayerParty (0x{player[0]:08X}) and gEnemyParty (0x{enemy[0]:08X}) are "
            f"{abs(player[0] - enemy[0])} bytes apart, expected {6 * POKEMON_SIZE}; they are not "
            "two adjacent six-slot party blocks"
        )
    notes.append(
        f"{label}: gPlayerParty and gEnemyParty are exactly {6 * POKEMON_SIZE} bytes apart "
        f"({'player first' if player[0] < enemy[0] else 'enemy first'}), consistent with two "
        f"adjacent 6 x {POKEMON_SIZE}-byte party blocks"
    )
    # In both games the two count bytes are the first two objects of the party translation unit,
    # immediately followed by the two party arrays - which is why `gEnemyPartyCount` is
    # `gPlayerPartyCount + 1` and never `gEnemyParty - 4` (that address is linker alignment
    # padding). Check that exact shape, and that the counts really are two distinct bytes.
    if player_count[0] + 1 != enemy_count[0]:
        raise Failure(
            f"{label}: gPlayerPartyCount (0x{player_count[0]:08X}) and gEnemyPartyCount "
            f"(0x{enemy_count[0]:08X}) are not adjacent bytes"
        )
    # The enemy party array is the next object after whichever party block the game declares
    # first, so it lies within one party block (plus alignment) of the count bytes: FireRed
    # declares gEnemyParty first (counts -> enemy party), Emerald declares gPlayerParty first
    # (counts -> player party -> enemy party). Anything further away means the count bytes are not
    # the head of this translation unit's EWRAM at all.
    if not (enemy_count[0] < enemy[0] <= enemy_count[0] + 6 * POKEMON_SIZE + 8):
        raise Failure(
            f"{label}: gEnemyParty (0x{enemy[0]:08X}) is not within one party block of the count "
            f"bytes (0x{enemy_count[0]:08X})"
        )
    notes.append(
        f"{label}: count block 0x{player_count[0]:08X}..0x{enemy_count[0]:08X} heads the party "
        f"translation unit's EWRAM, with gEnemyParty at 0x{enemy[0]:08X} and gPlayerParty at "
        f"0x{player[0]:08X} placed back to back after it"
    )

    # SaveBlock1 relationship used by the reader's legacy location path: saveBlock1 + 0x38 (FireRed)
    # or + 0x238 (Emerald) == gPlayerParty, so `gPlayerParty - that` is the SaveBlock1 base the
    # reader derives, and the audited pos/location/escapeWarp offsets are read from that base.
    save_block1_party_offset = 0x38 if "firered" in profile["id"] else 0x238
    save_block1_base = player[0] - save_block1_party_offset
    if save_block1_base < EWRAM_BASE:
        raise Failure(f"{label}: SaveBlock1 base underflow (0x{save_block1_base:08X})")
    notes.append(
        f"{label}: reader-derived SaveBlock1 base 0x{save_block1_base:08X} "
        f"(gPlayerParty == SaveBlock1+0x{save_block1_party_offset:X}); audited location offsets "
        f"pos=0x{SAVE_BLOCK1_POS_OFFSET:X} location=0x{SAVE_BLOCK1_LOCATION_OFFSET:X} "
        f"escapeWarp=0x{SAVE_BLOCK1_ESCAPE_WARP_OFFSET:X}"
    )
    return notes


def check_battle_pokemon(label: str, elf: str, symbols: dict[str, tuple[int, str, int]], config: dict[str, int]) -> list[str]:
    """Check sizeof(struct BattlePokemon) and the compiled field offsets."""
    mons = require_symbol(symbols, "gBattleMons", elf)
    if mons[2] == 0:
        raise Failure(f"{label}: gBattleMons has no recorded size; cannot check the battler stride")
    if mons[2] % BATTLE_POKEMON_SIZE != 0:
        raise Failure(
            f"{label}: gBattleMons is {mons[2]} bytes, which is not a multiple of "
            f"sizeof(struct BattlePokemon) == {BATTLE_POKEMON_SIZE}"
        )
    combatants = mons[2] // BATTLE_POKEMON_SIZE
    if combatants not in (2, 4):
        raise Failure(f"{label}: gBattleMons covers {combatants} battlers, expected 2 or 4")

    for field_name, expected in (
        ("battle_mons_size", BATTLE_POKEMON_SIZE),
        ("battle_mons_hp_offset", BATTLE_MONS_HP_OFFSET),
        ("battle_mons_stat_stages_offset", BATTLE_MONS_STAT_STAGES_OFFSET),
    ):
        if config.get(field_name) != expected:
            raise Failure(
                f"{label}: reader {field_name} is {config.get(field_name)}, expected {expected}"
            )
    if config["battle_mons_hp_offset"] + 2 > config["battle_mons_size"]:
        raise Failure(f"{label}: reader hp offset escapes the battler stride")

    return [
        f"{label}: gBattleMons stride is {BATTLE_POKEMON_SIZE} bytes x {combatants} battlers; "
        f"reader hp=0x{BATTLE_MONS_HP_OFFSET:X} statStages=0x{BATTLE_MONS_STAT_STAGES_OFFSET:X}"
    ]


def check_revision_equivalence(
    rev0_elf: str,
    rev1_elf: str,
    rev0_symbols: dict[str, tuple[int, str, int]],
    rev1_symbols: dict[str, tuple[int, str, int]],
) -> list[str]:
    """The core claim: FireRed Rev 0 and Rev 1 share one RAM layout."""
    rev0_ram = ram_map(rev0_symbols)
    rev1_ram = ram_map(rev1_symbols)

    def canonical(ram: dict[str, tuple[int, str, int]]) -> dict[str, int]:
        """{canonical name: address}, refusing collisions so a rename cannot hide a difference."""
        result: dict[str, int] = {}
        for name, value in ram.items():
            key = _canonical_symbol_name(name)
            if key in result and result[key] != value[0]:
                raise Failure(
                    f"two RAM symbols canonicalise to {key} at different addresses "
                    f"(0x{result[key]:08X} and 0x{value[0]:08X}); the comparison would be ambiguous"
                )
            result[key] = value[0]
        return result

    rev0_canonical = canonical(rev0_ram)
    rev1_canonical = canonical(rev1_ram)

    if set(rev0_canonical) != set(rev1_canonical):
        only0 = sorted(set(rev0_canonical) - set(rev1_canonical))
        only1 = sorted(set(rev1_canonical) - set(rev0_canonical))
        raise Failure(
            "the two FireRed revisions do not define the same RAM symbols "
            f"(rev0 only: {only0[:8]}, rev1 only: {only1[:8]})"
        )

    mismatched = [
        (name, rev0_canonical[name], rev1_canonical[name])
        for name in sorted(rev0_canonical)
        if rev0_canonical[name] != rev1_canonical[name]
    ]
    if mismatched:
        detail = "; ".join(
            f"{name} 0x{a:08X} vs 0x{b:08X}" for name, a, b in mismatched[:10]
        )
        raise Failure(
            f"{len(mismatched)} RAM symbols differ between FireRed Rev 0 and Rev 1: {detail}"
        )

    for name in AUDITED_FIRERED_SYMBOLS:
        if name not in rev0_ram:
            raise Failure(f"audited symbol {name} is missing from the FireRed RAM map")

    # ROM addresses are expected to differ (the rev1 change shifts them); RAM must not.
    rom0 = {n: v[0] for n, v in rev0_symbols.items() if 0x08000000 <= v[0] < 0x0A000000}
    rom1 = {n: v[0] for n, v in rev1_symbols.items() if 0x08000000 <= v[0] < 0x0A000000}
    rom_differing = sum(1 for n in set(rom0) & set(rom1) if rom0[n] != rom1[n])

    return [
        f"FireRed Rev 0 and Rev 1: {len(rev0_ram)} EWRAM+IWRAM symbols, all at identical "
        f"addresses ({len(AUDITED_FIRERED_SYMBOLS)} audited individually)",
        f"FireRed Rev 0 and Rev 1 ROM-address deltas: {rom_differing} symbols moved "
        "(expected: the rev1 change shifts code and rodata)",
    ]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--firered-rev0", help="ELF built from pret/pokefirered with GAME_REVISION=0")
    parser.add_argument("--firered-rev1", help="ELF built from pret/pokefirered with GAME_REVISION=1")
    parser.add_argument("--emerald", help="ELF built from pret/pokeemerald")
    parser.add_argument("--firered-rom", help="exact retail FireRed (Rev 0) dump to probe")
    parser.add_argument("--firered-rev1-rom", help="exact retail FireRed Rev 1 dump to probe")
    parser.add_argument("--emerald-rom", help="exact retail Emerald dump to probe")
    args = parser.parse_args(argv)

    if not any((args.firered_rev0, args.firered_rev1, args.emerald,
                args.firered_rom, args.firered_rev1_rom, args.emerald_rom)):
        parser.error(
            "at least one of --firered-rev0/--firered-rev1/--emerald (pinned builds) or "
            "--firered-rom/--firered-rev1-rom/--emerald-rom (retail dumps) is required"
        )

    notes: list[str] = []
    try:
        firered_profile = load_profile("vanilla_firered.json")
        emerald_profile = load_profile("vanilla_emerald.json")
        firered_config = reader_config("FIRERED")
        emerald_config = reader_config("EMERALD")

        rev0_symbols = rev1_symbols = None

        if args.firered_rev0:
            rev0_symbols = read_symbols(args.firered_rev0)
            notes += check_party_group("FireRed Rev 0", args.firered_rev0, rev0_symbols, firered_profile, firered_config)
            notes += check_battle_pokemon("FireRed Rev 0", args.firered_rev0, rev0_symbols, firered_config)
        if args.firered_rev1:
            rev1_symbols = read_symbols(args.firered_rev1)
            notes += check_party_group("FireRed Rev 1", args.firered_rev1, rev1_symbols, firered_profile, firered_config)
            notes += check_battle_pokemon("FireRed Rev 1", args.firered_rev1, rev1_symbols, firered_config)

        if rev0_symbols is not None and rev1_symbols is not None:
            notes += check_revision_equivalence(
                args.firered_rev0, args.firered_rev1, rev0_symbols, rev1_symbols
            )

        if args.emerald:
            emerald_symbols = read_symbols(args.emerald)
            notes += check_party_group("Emerald", args.emerald, emerald_symbols, emerald_profile, emerald_config)
            notes += check_battle_pokemon("Emerald", args.emerald, emerald_symbols, emerald_config)

        rom_problems: list[str] = []
        if args.firered_rom:
            rom_notes, rom_problems = check_retail_rom(
                "FireRed Rev 0 (retail)", args.firered_rom, firered_profile, firered_config
            )
            notes += rom_notes
        if args.firered_rev1_rom:
            rom_notes, problems = check_retail_rom(
                "FireRed Rev 1 (retail)", args.firered_rev1_rom, firered_profile, firered_config
            )
            notes += rom_notes
            rom_problems += problems
        if args.emerald_rom:
            rom_notes, problems = check_retail_rom(
                "Emerald (retail)", args.emerald_rom, emerald_profile, emerald_config
            )
            notes += rom_notes
            rom_problems += problems

    except Failure as failure:
        print(f"FAIL: {failure}", file=sys.stderr)
        return 1

    print("Vanilla read-only layout audit")
    for note in notes:
        print(f"  [OK] {note}")
    for problem in rom_problems:
        print(f"  [UNPROVEN] {problem}")
    if not any((args.firered_rom, args.firered_rev1_rom, args.emerald_rom)):
        print(
            "  [UNPROVEN] battle_mons_offset and the battle-lifecycle/UI offsets are not covered by "
            "the pinned builds: they are LinkerScript-ordered EWRAM placements that move with asset "
            "sizes, so a build with stubbed assets cannot confirm their absolute values. Re-run with "
            "--firered-rom/--emerald-rom to probe the retail images directly; until that passes, the "
            "battle-state read surface stays unauthorized (RomHackProfile.battleStateReadVerified)."
        )
    return 1 if rom_problems else 0


if __name__ == "__main__":
    sys.exit(main())
