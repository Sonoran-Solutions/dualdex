#!/usr/bin/env python3
"""Generate the H&S 2.0.5 BattlePokemon layout table from the pinned upstream source.

This is the authoritative source-check for the live battler ability/type reader
(issue #9 slice "authoritative live battler ability + effective type state").

What it establishes, from the pinned upstream checkout compiled with the pinned
ARM toolchain (the same toolchain and flags recorded in
docs/HNS_2_0_5_COMPATIBILITY_EVIDENCE.md §10):

  * sizeof(struct BattlePokemon)
  * byte offset and byte width of current `species` and `ability`
  * byte offset, element count and element width of `types`
  * the structural domain of the two enums: ABILITIES_COUNT and
    NUMBER_OF_MON_TYPES (an observed ID above these bounds is outside the
    pinned source's value domain and must never be coerced)

How: the script compiles a probe translation unit against the pinned headers
(`include/global.h`, which pulls in `struct BattlePokemon` from pokemon.h and
the two constant enums) with the pinned ARM toolchain, and reads the resulting
.rodata constants back out of the ELF object — the same compiled-evidence
approach as generate_hns_challenge_layout.py. The compiled values are then
cross-checked against the pinned source text (`pokemon.h` field comments and
the constant enums' final entries); compiled ABI evidence wins, and any
disagreement is recorded in the emitted header instead of being silently
resolved.

The committed artifact (`native/src/hns_battle_pokemon_layout_gen.h`) is
self-contained, so ordinary `./ci.sh test` never needs the upstream checkout or
the ARM toolchain. Re-verification against upstream belongs to
`./ci.sh source-check` via `--verify`.

Usage:
  python3 tools/hns-layout/generate_hns_battle_pokemon_layout.py \
      --upstream-dir <pokehns-expansion checkout> [--verify]

Pinned upstream revision: 1f42b74dff0e9fe942419845d040663dd829a973
(tag Release-v2.0.5).
"""

import argparse
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

PINNED_COMMIT = "1f42b74dff0e9fe942419845d040663dd829a973"
PINNED_TAG = "Release-v2.0.5"

# §10 flags from docs/HNS_2_0_5_COMPATIBILITY_EVIDENCE.md (same as the
# challenge-settings generator: one ABI evidence framework, two tables).
ARM_FLAGS = [
    "-DMODERN=1", "-DTESTING=0", "-DPOKEMON_HNS", "-DEMERALD",
    "-std=gnu17", "-mthumb", "-mthumb-interwork", "-O2",
    "-mabi=apcs-gnu", "-mtune=arm7tdmi", "-march=armv4t",
]

GLOBAL_H = "include/global.h"
POKEMON_H = "include/pokemon.h"
CONSTANTS_ABILITIES_H = "include/constants/abilities.h"
CONSTANTS_POKEMON_H = "include/constants/pokemon.h"

OUTPUT_HEADER = "native/src/hns_battle_pokemon_layout_gen.h"


def fail(message: str):
    print(f"error: {message}", file=sys.stderr)
    sys.exit(1)


def find_arm_gcc(explicit: str | None) -> str:
    if not explicit:
        explicit = os.environ.get("HNS_ARM_GCC") or None
    if explicit:
        if not Path(explicit).is_file():
            fail(f"ARM compiler not found at {explicit}")
        return explicit
    for candidate in (
        str(Path.home() / "opt/arm-gnu-toolchain-13.2.Rel1-x86_64-arm-none-eabi/bin/arm-none-eabi-gcc"),
        "/opt/devkitpro/devkitARM/bin/arm-none-eabi-gcc",
        "/usr/bin/arm-none-eabi-gcc",
    ):
        if Path(candidate).is_file():
            return candidate
    fail("arm-none-eabi-gcc not found; set --arm-gcc or HNS_ARM_GCC")


def build_probe_c() -> str:
    return "\n".join(
        [
            "/* Probe for tools/hns-layout/generate_hns_battle_pokemon_layout.py;",
            "   not part of any build. */",
            "#include \"global.h\"",
            "const unsigned long ddx_sizeof_bp = sizeof(struct BattlePokemon);",
            "const unsigned long ddx_species_offset =",
            "    __builtin_offsetof(struct BattlePokemon, species);",
            "const unsigned long ddx_species_size =",
            "    sizeof(((struct BattlePokemon *)0)->species);",
            "const unsigned long ddx_attack_offset =",
            "    __builtin_offsetof(struct BattlePokemon, attack);",
            "const unsigned long ddx_attack_size =",
            "    sizeof(((struct BattlePokemon *)0)->attack);",
            "const unsigned long ddx_defense_offset =",
            "    __builtin_offsetof(struct BattlePokemon, defense);",
            "const unsigned long ddx_defense_size =",
            "    sizeof(((struct BattlePokemon *)0)->defense);",
            "const unsigned long ddx_speed_offset =",
            "    __builtin_offsetof(struct BattlePokemon, speed);",
            "const unsigned long ddx_speed_size =",
            "    sizeof(((struct BattlePokemon *)0)->speed);",
            "const unsigned long ddx_spattack_offset =",
            "    __builtin_offsetof(struct BattlePokemon, spAttack);",
            "const unsigned long ddx_spattack_size =",
            "    sizeof(((struct BattlePokemon *)0)->spAttack);",
            "const unsigned long ddx_spdefense_offset =",
            "    __builtin_offsetof(struct BattlePokemon, spDefense);",
            "const unsigned long ddx_spdefense_size =",
            "    sizeof(((struct BattlePokemon *)0)->spDefense);",
            "const unsigned long ddx_stat_stages_offset =",
            "    __builtin_offsetof(struct BattlePokemon, statStages);",
            "const unsigned long ddx_stat_stages_size =",
            "    sizeof(((struct BattlePokemon *)0)->statStages);",
            "const unsigned long ddx_stat_stage_count = NUM_BATTLE_STATS;",
            "const unsigned long ddx_ability_offset =",
            "    __builtin_offsetof(struct BattlePokemon, ability);",
            "const unsigned long ddx_ability_size =",
            "    sizeof(((struct BattlePokemon *)0)->ability);",
            "const unsigned long ddx_types_offset =",
            "    __builtin_offsetof(struct BattlePokemon, types);",
            "const unsigned long ddx_types_size =",
            "    sizeof(((struct BattlePokemon *)0)->types);",
            "const unsigned long ddx_type_element_size =",
            "    sizeof(((struct BattlePokemon *)0)->types[0]);",
            "const unsigned long ddx_abilities_count = ABILITIES_COUNT;",
            "const unsigned long ddx_mon_types_count = NUMBER_OF_MON_TYPES;",
            "const unsigned long ddx_item_offset =",
            "    __builtin_offsetof(struct BattlePokemon, item);",
            "const unsigned long ddx_item_size =",
            "    sizeof(((struct BattlePokemon *)0)->item);",
            "const unsigned long ddx_items_count = ITEMS_COUNT;",
        ]
    ) + "\n"


def compile_probe(arm_gcc: str, source: str, workdir: Path,
                  extra_includes: list[str]) -> bytes:
    src_path = workdir / "battle_pokemon_probe.c"
    obj_path = workdir / "battle_pokemon_probe.o"
    src_path.write_text(source)
    cmd = [arm_gcc, "-c", str(src_path), "-o", str(obj_path)] + ARM_FLAGS
    for inc in extra_includes:
        cmd += ["-iquote", inc]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        fail(f"ARM probe failed to compile:\n{result.stderr}")
    return obj_path.read_bytes()


def read_constant_symbols(arm_gcc: str, obj: bytes) -> dict[str, int]:
    """Map symbol name -> its 4-byte little-endian value in .rodata."""
    toolchain_bin = Path(arm_gcc).parent
    nm = toolchain_bin / "arm-none-eabi-nm"
    objdump = toolchain_bin / "arm-none-eabi-objdump"

    with tempfile.NamedTemporaryFile(suffix=".o", delete=False) as tmp:
        tmp.write(obj)
        tmp_path = tmp.name
    sym_out = subprocess.run(
        [str(nm), "-S", "--defined-only", tmp_path], capture_output=True, text=True
    )
    if sym_out.returncode != 0:
        fail(f"arm-none-eabi-nm failed:\n{sym_out.stderr}")
    sizes: dict[int, tuple[str, int]] = {}
    for line in sym_out.stdout.splitlines():
        parts = line.split()
        if len(parts) < 4:
            continue
        value_s, size_s, _type, name = parts[0], parts[1], parts[2], parts[3]
        if size_s in ("-", ""):
            continue
        sizes[int(value_s, 16)] = (name, int(size_s, 16))

    dump = subprocess.run(
        [str(objdump), "-s", "-j", ".rodata", tmp_path], capture_output=True, text=True
    )
    if dump.returncode != 0:
        fail(f"arm-none-eabi-objdump failed:\n{dump.stderr}")
    blob = bytearray()
    base_addr = None
    for line in dump.stdout.splitlines():
        m = re.match(r"^\s*([0-9a-f]+)\s((?:[0-9a-f]{2,8}\s){1,4})", line)
        if not m:
            continue
        addr = int(m.group(1), 16)
        if base_addr is None:
            base_addr = addr
        blob += bytes.fromhex(m.group(2).replace(" ", ""))
    if base_addr is None:
        fail("no .rodata bytes found in the probe object")

    out: dict[str, int] = {}
    for value, (name, size) in sizes.items():
        offset = value - base_addr
        if name.startswith("ddx_") and 0 <= offset and offset + size <= len(blob):
            out[name] = int.from_bytes(blob[offset:offset + size], "little")
    return out


def parse_source_pins(upstream_path: Path) -> dict[str, int]:
    """Independent source-text expectations, read from the pinned checkout.

    These are the pins the compiled ABI is cross-checked against; they are
    deliberately NOT derived from the probe. Compiled evidence wins on
    disagreement, and the disagreement is recorded in the emitted header.
    """
    pins: dict[str, int] = {}

    pokemon_text = (upstream_path / POKEMON_H).read_text()
    struct_m = re.search(r"struct BattlePokemon\s*\{(.*?)\n\};", pokemon_text, re.S)
    if not struct_m:
        fail("struct BattlePokemon not found in include/pokemon.h")
    body = struct_m.group(1)

    # Source-comment offsets: the pinned source labels every member with a
    # /*0xNN*/ byte offset comment. Extract the ones DualDex reads.
    species_m = re.search(r"/\*0x([0-9A-Fa-f]+)\*/\s*u16 species;", body)
    attack_m = re.search(r"/\*0x([0-9A-Fa-f]+)\*/\s*u16 attack;", body)
    defense_m = re.search(r"/\*0x([0-9A-Fa-f]+)\*/\s*u16 defense;", body)
    speed_m = re.search(r"/\*0x([0-9A-Fa-f]+)\*/\s*u16 speed;", body)
    spattack_m = re.search(r"/\*0x([0-9A-Fa-f]+)\*/\s*u16 spAttack;", body)
    spdefense_m = re.search(r"/\*0x([0-9A-Fa-f]+)\*/\s*u16 spDefense;", body)
    stat_stages_m = re.search(r"/\*0x([0-9A-Fa-f]+)\*/\s*s8 statStages\[NUM_BATTLE_STATS\];", body)
    ability_m = re.search(r"/\*0x([0-9A-Fa-f]+)\*/\s*enum Ability ability;", body)
    types_m = re.search(r"/\*0x([0-9A-Fa-f]+)\*/\s*enum Type types\[(\d+)\];", body)
    item_m = re.search(r"/\*0x([0-9A-Fa-f]+)\*/\s*enum Item item;", body)
    if not species_m or not attack_m or not defense_m or not speed_m or not spattack_m or not spdefense_m or not stat_stages_m or not ability_m or not types_m or not item_m:
        fail("could not parse the labelled member offsets from "
             "include/pokemon.h; the pinned source shape changed")
    pins["source_species_offset"] = int(species_m.group(1), 16)
    pins["source_attack_offset"] = int(attack_m.group(1), 16)
    pins["source_defense_offset"] = int(defense_m.group(1), 16)
    pins["source_speed_offset"] = int(speed_m.group(1), 16)
    pins["source_spattack_offset"] = int(spattack_m.group(1), 16)
    pins["source_spdefense_offset"] = int(spdefense_m.group(1), 16)
    pins["source_stat_stages_offset"] = int(stat_stages_m.group(1), 16)
    pins["source_ability_offset"] = int(ability_m.group(1), 16)
    pins["source_types_offset"] = int(types_m.group(1), 16)
    pins["source_type_count"] = int(types_m.group(2))
    pins["source_item_offset"] = int(item_m.group(1), 16)

    # ABILITIES_COUNT is the last enum constant; the highest explicit ability ID
    # appears just above it. Same for TYPE_STELLAR / NUMBER_OF_MON_TYPES.
    abilities_text = (upstream_path / CONSTANTS_ABILITIES_H).read_text()
    count_m = re.search(r"ABILITIES_COUNT_GEN9\s*,", abilities_text)
    if not count_m:
        fail("ABILITIES_COUNT_GEN9 not found in include/constants/abilities.h")
    ids = [int(v) for v in re.findall(r"^\s+ABILITY_\w+\s*=\s*(\d+)\s*,", abilities_text, re.M)]
    if not ids:
        fail("no explicit ability IDs found in include/constants/abilities.h")
    pins["source_ability_id_max"] = max(ids)

    types_text = (upstream_path / CONSTANTS_POKEMON_H).read_text()
    type_ids = [int(v) for v in re.findall(r"^\s+TYPE_\w+\s*=\s*(\d+)\s*,", types_text, re.M)]
    if "NUMBER_OF_MON_TYPES" not in types_text:
        fail("NUMBER_OF_MON_TYPES not found in include/constants/pokemon.h")
    if not type_ids:
        fail("no explicit type IDs found in include/constants/pokemon.h")
    pins["source_type_id_max"] = max(type_ids)
    return pins


def render_header(commit, arm_gcc, compiled: dict[str, int], pins: dict[str, int]) -> str:
    sizeof_bp = compiled["ddx_sizeof_bp"]
    species_offset = compiled["ddx_species_offset"]
    species_size = compiled["ddx_species_size"]
    attack_offset = compiled["ddx_attack_offset"]
    attack_size = compiled["ddx_attack_size"]
    defense_offset = compiled["ddx_defense_offset"]
    defense_size = compiled["ddx_defense_size"]
    speed_offset = compiled["ddx_speed_offset"]
    speed_size = compiled["ddx_speed_size"]
    spattack_offset = compiled["ddx_spattack_offset"]
    spattack_size = compiled["ddx_spattack_size"]
    spdefense_offset = compiled["ddx_spdefense_offset"]
    spdefense_size = compiled["ddx_spdefense_size"]
    stat_stages_offset = compiled["ddx_stat_stages_offset"]
    stat_stages_size = compiled["ddx_stat_stages_size"]
    stat_stage_count = compiled["ddx_stat_stage_count"]
    ability_offset = compiled["ddx_ability_offset"]
    ability_size = compiled["ddx_ability_size"]
    types_offset = compiled["ddx_types_offset"]
    type_count = compiled["ddx_types_size"] // compiled["ddx_type_element_size"]
    type_element_size = compiled["ddx_type_element_size"]
    ability_id_max = compiled["ddx_abilities_count"] - 1
    type_id_max = compiled["ddx_mon_types_count"] - 1
    item_offset = compiled["ddx_item_offset"]
    item_size = compiled["ddx_item_size"]
    item_id_max = compiled["ddx_items_count"] - 1

    agreements = []
    disagreements = []
    checks = [
        ("species byte offset", species_offset, pins["source_species_offset"]),
        ("attack byte offset", attack_offset, pins["source_attack_offset"]),
        ("defense byte offset", defense_offset, pins["source_defense_offset"]),
        ("speed byte offset", speed_offset, pins["source_speed_offset"]),
        ("spAttack byte offset", spattack_offset, pins["source_spattack_offset"]),
        ("spDefense byte offset", spdefense_offset, pins["source_spdefense_offset"]),
        ("statStages byte offset", stat_stages_offset, pins["source_stat_stages_offset"]),
        ("ability byte offset", ability_offset, pins["source_ability_offset"]),
        ("types byte offset", types_offset, pins["source_types_offset"]),
        ("type slot count", type_count, pins["source_type_count"]),
        ("highest ability ID", ability_id_max, pins["source_ability_id_max"]),
        ("highest type ID", type_id_max, pins["source_type_id_max"]),
        ("item byte offset", item_offset, pins["source_item_offset"]),
    ]
    for label, compiled_v, source_v in checks:
        (agreements if compiled_v == source_v else disagreements).append(
            f"{label}: compiled {compiled_v}, source text {source_v}"
        )

    lines = [
        "/*",
        " * GENERATED FILE — do not edit by hand.",
        " *",
        " * struct BattlePokemon layout for the exact Heart & Soul 2.0.5 build,",
        " * derived from the pinned upstream source by",
        " *   tools/hns-layout/generate_hns_battle_pokemon_layout.py",
        " *",
        f" * Pinned upstream: PokemonHnS-Development/pokehns-expansion",
        f" *   commit {commit} (tag Release-v2.0.5)",
        f" * Compiler:   {Path(arm_gcc).name} (ARM GNU Toolchain 13.2.Rel1)",
        f" * Flags:      {' '.join(ARM_FLAGS)}",
        " *",
        " * Evidence chain:",
        " *   - sizeof/offsets/widths: compiled probe objects read back from",
        " *     .rodata (see the generator). The probe compiles the pinned",
        " *     headers with the same §10 flags as the challenge-settings table;",
        " *     struct BattlePokemon is a packed APCS-GNU layout.",
        " *   - structural domains: ABILITIES_COUNT and NUMBER_OF_MON_TYPES as",
        " *     compiled from the pinned constant headers. An observed value",
        " *     above the maximum is outside the pinned source's value domain",
        " *     and is reported as such by the reader — never coerced.",
        " *",
        " * Sentinel semantics recorded from the pinned source, not invented:",
        " *   - Type: TYPE_NONE (0) is the empty-slot sentinel (a monotype's",
        " *     second slot); TYPE_MYSTERY (10) is the battle-only 'typeless'",
        " *     value (Roost removal, Camouflage-less conversion); TYPE_STELLAR",
        " *     (20) exists as a real battle type. Duplicate slots are",
        " *     meaningful (Terastallization sets all three slots equal via",
        " *     GetBattlerTypes' consumers), so values are exposed verbatim.",
        " *   - Ability: ABILITY_NONE (0) is part of the pinned enum; the reader",
        " *     preserves it as an observed value and never substitutes.",
        " *   - Item: ITEM_NONE (0) is part of the pinned enum and means an",
        " *     authoritative empty held-item slot. The item domain is the pinned",
        " *     ITEMS_COUNT; an observed ID above ITEM_ID_MAX is reported as such.",
        " *",
        " * Source-text cross-check (compiled ABI wins on disagreement):",
    ]
    for a in agreements:
        lines.append(f" *   agree — {a}")
    for d in disagreements:
        lines.append(f" *   DISCREPANCY — {d} (compiled value is authoritative)")
    if not disagreements:
        lines.append(" *   no discrepancies: every cross-checked value agrees")
    lines += [
        " */",
        "",
        "#ifndef DUALDEX_HNS_BATTLE_POKEMON_LAYOUT_GEN_H",
        "#define DUALDEX_HNS_BATTLE_POKEMON_LAYOUT_GEN_H",
        "",
        "#define HNS_BATTLE_POKEMON_SIZEOF " + str(sizeof_bp),
        "#define HNS_BATTLE_POKEMON_SPECIES_OFFSET " + str(species_offset),
        "#define HNS_BATTLE_POKEMON_SPECIES_SIZE " + str(species_size),
        "#define HNS_BATTLE_POKEMON_ATTACK_OFFSET " + str(attack_offset),
        "#define HNS_BATTLE_POKEMON_ATTACK_SIZE " + str(attack_size),
        "#define HNS_BATTLE_POKEMON_DEFENSE_OFFSET " + str(defense_offset),
        "#define HNS_BATTLE_POKEMON_DEFENSE_SIZE " + str(defense_size),
        "#define HNS_BATTLE_POKEMON_SPEED_OFFSET " + str(speed_offset),
        "#define HNS_BATTLE_POKEMON_SPEED_SIZE " + str(speed_size),
        "#define HNS_BATTLE_POKEMON_SPATTACK_OFFSET " + str(spattack_offset),
        "#define HNS_BATTLE_POKEMON_SPATTACK_SIZE " + str(spattack_size),
        "#define HNS_BATTLE_POKEMON_SPDEFENSE_OFFSET " + str(spdefense_offset),
        "#define HNS_BATTLE_POKEMON_SPDEFENSE_SIZE " + str(spdefense_size),
        "#define HNS_BATTLE_POKEMON_STAT_STAGES_OFFSET " + str(stat_stages_offset),
        "#define HNS_BATTLE_POKEMON_STAT_STAGES_COUNT " + str(stat_stage_count),
        "#define HNS_BATTLE_POKEMON_ABILITY_OFFSET " + str(ability_offset),
        "#define HNS_BATTLE_POKEMON_ABILITY_SIZE " + str(ability_size),
        "#define HNS_BATTLE_POKEMON_ABILITY_ID_MAX " + str(ability_id_max),
        "#define HNS_BATTLE_POKEMON_TYPES_OFFSET " + str(types_offset),
        "#define HNS_BATTLE_POKEMON_TYPE_COUNT " + str(type_count),
        "#define HNS_BATTLE_POKEMON_TYPE_ELEMENT_SIZE " + str(type_element_size),
        "#define HNS_BATTLE_POKEMON_TYPE_ID_MAX " + str(type_id_max),
        "#define HNS_BATTLE_POKEMON_ITEM_OFFSET " + str(item_offset),
        "#define HNS_BATTLE_POKEMON_ITEM_SIZE " + str(item_size),
        "#define HNS_BATTLE_POKEMON_ITEM_ID_MAX " + str(item_id_max),
        "",
        "#if HNS_BATTLE_POKEMON_ATTACK_OFFSET + HNS_BATTLE_POKEMON_ATTACK_SIZE > HNS_BATTLE_POKEMON_SIZEOF",
        "#error \"BattlePokemon attack field exceeds the compiled struct size\"",
        "#endif",
        "#if HNS_BATTLE_POKEMON_DEFENSE_OFFSET + HNS_BATTLE_POKEMON_DEFENSE_SIZE > HNS_BATTLE_POKEMON_SIZEOF",
        "#error \"BattlePokemon defense field exceeds the compiled struct size\"",
        "#endif",
        "#if HNS_BATTLE_POKEMON_SPEED_OFFSET + HNS_BATTLE_POKEMON_SPEED_SIZE > HNS_BATTLE_POKEMON_SIZEOF",
        "#error \"BattlePokemon speed field exceeds the compiled struct size\"",
        "#endif",
        "#if HNS_BATTLE_POKEMON_SPATTACK_OFFSET + HNS_BATTLE_POKEMON_SPATTACK_SIZE > HNS_BATTLE_POKEMON_SIZEOF",
        "#error \"BattlePokemon spAttack field exceeds the compiled struct size\"",
        "#endif",
        "#if HNS_BATTLE_POKEMON_SPDEFENSE_OFFSET + HNS_BATTLE_POKEMON_SPDEFENSE_SIZE > HNS_BATTLE_POKEMON_SIZEOF",
        "#error \"BattlePokemon spDefense field exceeds the compiled struct size\"",
        "#endif",
        "#if HNS_BATTLE_POKEMON_STAT_STAGES_OFFSET + HNS_BATTLE_POKEMON_STAT_STAGES_COUNT > HNS_BATTLE_POKEMON_SIZEOF",
        "#error \"BattlePokemon statStages field exceeds the compiled struct size\"",
        "#endif",
        "#if HNS_BATTLE_POKEMON_ABILITY_OFFSET + HNS_BATTLE_POKEMON_ABILITY_SIZE > HNS_BATTLE_POKEMON_SIZEOF",
        "#error \"BattlePokemon ability field exceeds the compiled struct size\"",
        "#endif",
        "#if HNS_BATTLE_POKEMON_TYPES_OFFSET + HNS_BATTLE_POKEMON_TYPE_COUNT * HNS_BATTLE_POKEMON_TYPE_ELEMENT_SIZE > HNS_BATTLE_POKEMON_SIZEOF",
        "#error \"BattlePokemon types field exceeds the compiled struct size\"",
        "#endif",
        "#if HNS_BATTLE_POKEMON_ITEM_OFFSET + HNS_BATTLE_POKEMON_ITEM_SIZE > HNS_BATTLE_POKEMON_SIZEOF",
        "#error \"BattlePokemon item field exceeds the compiled struct size\"",
        "#endif",
        "#if HNS_BATTLE_POKEMON_ABILITY_SIZE > 4 || HNS_BATTLE_POKEMON_TYPE_ELEMENT_SIZE > 4 || HNS_BATTLE_POKEMON_ITEM_SIZE > 4",
        "#error \"BattlePokemon field widths are implausible for this ABI\"",
        "#endif",
        "",
        "#endif /* DUALDEX_HNS_BATTLE_POKEMON_LAYOUT_GEN_H */",
        "",
    ]
    return "\n".join(lines)


def discover_upstream() -> str:
    for candidate in (
        Path("upstream-hns/pokehns-expansion"),
        Path.home() / "Projects/upstream-hns/pokehns-expansion",
        Path("../upstream-hns/pokehns-expansion"),
    ):
        if (candidate / GLOBAL_H).is_file():
            return str(candidate)
    fail("pinned upstream checkout not found; pass --upstream-dir or HNS_UPSTREAM_DIR")


def git_head(repo: Path) -> str:
    result = subprocess.run(
        ["git", "-C", str(repo), "rev-parse", "HEAD"], capture_output=True, text=True
    )
    if result.returncode != 0:
        fail(f"git rev-parse failed for {repo}:\n{result.stderr}")
    return result.stdout.strip()


def verify_commit(upstream_path: Path) -> None:
    # Fail closed on a dirty checkout: the probe compiles the checkout's own
    # headers, so uncommitted edits would silently change the evidence.
    status = subprocess.run(
        ["git", "-C", str(upstream_path), "status", "--porcelain",
         "--untracked-files=no"],
        capture_output=True, text=True,
    )
    if status.returncode != 0:
        fail(f"git status failed for {upstream_path}:\n{status.stderr}")
    if status.stdout.strip():
        fail(
            f"{upstream_path} has uncommitted tracked changes; refusing to "
            "derive layout evidence from a dirty checkout"
        )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--upstream-dir", required=False,
                        default=None, help="pinned pokehns-expansion checkout")
    parser.add_argument("--arm-gcc", default=None,
                        help="arm-none-eabi-gcc binary (default: discover)")
    parser.add_argument("--verify", action="store_true",
                        help="regenerate and compare instead of writing")
    parser.add_argument("--output", default=None,
                        help="output path (default: native/src/hns_battle_pokemon_layout_gen.h)")
    args = parser.parse_args()

    upstream = (
        args.upstream_dir
        or os.environ.get("HNS_UPSTREAM_DIR")
        or discover_upstream()
    )
    upstream_path = Path(upstream)
    for required in (GLOBAL_H, POKEMON_H, CONSTANTS_ABILITIES_H, CONSTANTS_POKEMON_H):
        if not (upstream_path / required).is_file():
            fail(f"{required} not found under {upstream_path}")
    commit = git_head(upstream_path)
    if commit != PINNED_COMMIT:
        fail(
            f"{upstream_path} is at {commit}, expected the pinned "
            f"{PINNED_COMMIT} ({PINNED_TAG})"
        )
    verify_commit(upstream_path)

    arm_gcc = find_arm_gcc(args.arm_gcc)
    pins = parse_source_pins(upstream_path)

    with tempfile.TemporaryDirectory() as tmp:
        obj = compile_probe(
            arm_gcc, build_probe_c(), Path(tmp),
            [str(upstream_path / "include"), str(upstream_path)],
        )
        compiled = read_constant_symbols(arm_gcc, obj)

    required_symbols = [
        "ddx_sizeof_bp",
        "ddx_attack_offset", "ddx_attack_size",
        "ddx_defense_offset", "ddx_defense_size",
        "ddx_speed_offset", "ddx_speed_size",
        "ddx_spattack_offset", "ddx_spattack_size",
        "ddx_spdefense_offset", "ddx_spdefense_size",
        "ddx_stat_stages_offset", "ddx_stat_stages_size", "ddx_stat_stage_count",
        "ddx_ability_offset", "ddx_ability_size",
        "ddx_types_offset", "ddx_types_size", "ddx_type_element_size",
        "ddx_abilities_count", "ddx_mon_types_count",
        "ddx_item_offset", "ddx_item_size", "ddx_items_count",
    ]
    for sym in required_symbols:
        if sym not in compiled:
            fail(f"probe symbol {sym} missing from the compiled object")

    if compiled["ddx_types_size"] % compiled["ddx_type_element_size"] != 0:
        fail("types array size is not a whole number of elements")
    if compiled["ddx_mon_types_count"] < 1 or compiled["ddx_abilities_count"] < 1:
        fail("enum counts compiled to implausible values")

    content = render_header(commit, arm_gcc, compiled, pins)
    output = Path(args.output) if args.output else Path(OUTPUT_HEADER)
    if args.verify:
        if not output.is_file():
            fail(f"{output} does not exist; run the generator without --verify first")
        committed = output.read_text()
        if committed != content:
            fail(f"{output} does not match a fresh generation from {upstream_path}")
        print(f"  battle-pokemon layout verified against {upstream_path} @ {commit}")
    else:
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(content)
        print(f"  wrote {output}")


if __name__ == "__main__":
    main()
