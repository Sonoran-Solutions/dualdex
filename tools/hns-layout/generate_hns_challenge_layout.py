#!/usr/bin/env python3
"""Generate the H&S 2.0.5 ChallengeSettings layout table from the pinned upstream source.

This is the authoritative source-check for the runtime challenge-settings reader
(issue #9 slice "H&S 2.0.5 runtime challenge-settings reader").

What it establishes, from the pinned upstream checkout compiled with the pinned
ARM toolchain (the same toolchain and flags recorded in
docs/HNS_2_0_5_COMPATIBILITY_EVIDENCE.md §10):

  * sizeof(struct ChallengeSettings)
  * offsetof(struct SaveBlock3, challengeSettings)
  * sizeof(struct SaveBlock3)
  * the byte/bit position of every field DualDex reads (LSB-first within each
    byte, as compiled by arm-none-eabi-gcc -mabi=apcs-gnu)

How: the script extracts the `struct ChallengeSettings` body verbatim from the
pinned include/global.h, wraps it in a probe translation unit together with
designated-initializer probe objects (one per field, all other members zero),
compiles that with the pinned ARM toolchain, and reads the resulting .rodata
patterns back out of the ELF object. A second probe includes the pinned
global.h itself to derive the SaveBlock3 constants. No emulation is required;
the compiler statically lays out the probe objects.

The committed artifact (`native/src/hns_challenge_settings_layout_gen.h`) is
self-contained, so ordinary `./ci.sh test` never needs the upstream checkout or
the ARM toolchain. Re-verification against upstream belongs to
`./ci.sh source-check` via `--verify`.

Usage:
  python3 tools/hns-layout/generate_hns_challenge_layout.py \
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

# §10 flags from docs/HNS_2_0_5_COMPATIBILITY_EVIDENCE.md.
ARM_FLAGS = [
    "-DMODERN=1", "-DTESTING=0", "-DPOKEMON_HNS", "-DEMERALD",
    "-std=gnu17", "-mthumb", "-mthumb-interwork", "-O2",
    "-mabi=apcs-gnu", "-mtune=arm7tdmi", "-march=armv4t",
]

# Header the struct body lives in, and the struct we extract.
GLOBAL_H = "include/global.h"

# Fields DualDex reads, with the source-domain width parsed from the struct and
# the valid domain recorded here. The domain list is derived from the pinned
# source, not invented: see docs/HNS_2_0_5_COMPATIBILITY_EVIDENCE.md and the
# challenge_menu.c choice tables cited in the generated header.
#   name -> (choice-domain note, valid values)
FIELD_DOMAINS = {
    "optionStyle": "{0,1} (1-bit; PHYS/SP SPLIT: 0 = per-move split, 1 = type-decided)",
    "tx_Random_Type": "{0,1}",
    "tx_Random_TypeEffectiveness": "{0,1}",
    "tx_Random_Abilities": "{0,1}",
    "tx_Random_Moves": "{0,1}",
    "tx_Challenges_NoEVs": "{0,1}",
    "tx_Challenges_BaseStatEqualizer": "{0,1,2,3} (0/100/255/500 BST table)",
    "tx_Challenges_Mirror": "{0,1}",
    "tx_Challenges_Mirror_Thief": "{0,1}",
    "tx_Challenges_TrainerScalingIVs": "{0,1,2} (OFF/SCALE/HARD menu choices)",
    "tx_Challenges_TrainerScalingEVs": "{0,1,2,3} (menu choices)",
    "tx_Challenges_MaxPartyIVs": "{0,1,2} (menu choices)",
    "tx_Mode_Sturdy": "{0,1}",
    "tx_Challenges_LevelCap": "{0,1,2} (OFF/NORMAL/HARD menu choices)",
    "tx_Challenges_ExpMultiplier": "{0,1,2,3} (x1.0/x1.5/x2.0/x0.0 menu choices)",
    "tx_Mode_Fairy_Types": "{0,1}",
    "tx_Mode_Legendary_Abilities": "{0,1}",
}

# Order of the emitted table (grouped like the capability matrix).
FIELD_ORDER = [
    "optionStyle",
    "tx_Mode_Fairy_Types",
    "tx_Random_Type",
    "tx_Random_TypeEffectiveness",
    "tx_Random_Abilities",
    "tx_Random_Moves",
    "tx_Challenges_NoEVs",
    "tx_Challenges_BaseStatEqualizer",
    "tx_Challenges_Mirror",
    "tx_Challenges_Mirror_Thief",
    "tx_Challenges_TrainerScalingIVs",
    "tx_Challenges_TrainerScalingEVs",
    "tx_Challenges_MaxPartyIVs",
    "tx_Mode_Sturdy",
    "tx_Challenges_LevelCap",
    "tx_Challenges_ExpMultiplier",
    "tx_Mode_Legendary_Abilities",
]

OUTPUT_HEADER = "native/src/hns_challenge_settings_layout_gen.h"


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


def fail(message: str):
    print(f"error: {message}", file=sys.stderr)
    sys.exit(1)


def parse_struct_fields(global_h_text: str) -> list[tuple[str, int]]:
    """Extract the ChallengeSettings field declarations verbatim.

    Returns a list of (field name, bit width). Fails closed on anything
    unexpected (conditional compilation inside the struct, unknown widths,
    non-bitfield members other than bool8 autoRun).
    """
    match = re.search(
        r"struct ChallengeSettings\s*\{(.*?)\n\};", global_h_text, re.S
    )
    if not match:
        fail("struct ChallengeSettings not found in include/global.h")
    body = match.group(1)
    if re.search(r"^\s*#\s*(if|ifdef|ifndef|else|elif|endif)", body, re.M):
        fail("conditional compilation inside struct ChallengeSettings is not supported")
    fields = []
    for line in body.splitlines():
        line = line.split("//")[0].strip().rstrip(";")
        if not line:
            continue
        m = re.match(r"^(u8|bool8)\s+(\w+)(?::(\d+))?$", line)
        if not m:
            # A non-bitfield array member (nuzlockeEncounterFlags[16]) is legal
            # in the pinned struct; it shifts later fields but is never read.
            m = re.match(r"^u8\s+(\w+)\[(\d+)\]$", line)
            if m:
                fields.append((m.group(1) + "[]", -int(m.group(2))))
                continue
            fail(f"unrecognised member declaration in ChallengeSettings: {line!r}")
        name = m.group(2)
        width = int(m.group(3)) if m.group(3) else 8
        if name == "autoRun" and width != 8:
            fail("autoRun must be a full byte (bool8)")
        fields.append((name, width))
    return fields


def build_field_probe_c(fields: list[tuple[str, int]]) -> str:
    lines = [
        "/* Generated by tools/hns-layout/generate_hns_challenge_layout.py;",
        "   do not edit. */",
        "typedef unsigned char u8;",
        "typedef u8 bool8;",
        "",
        "struct ChallengeSettings {",
    ]
    # Re-emit the pinned declarations verbatim (same text the parser accepted).
    for name, width in fields:
        if width < 0:
            lines.append(f"    u8 {name[:-2]}[{-width}];")
        elif width == 8:
            lines.append(f"    bool8 {name};")
        else:
            lines.append(f"    u8 {name}:{width};")
    lines += [
        "};",
        "",
        "const struct ChallengeSettings ddx_probe_zero;",
    ]
    for name, width in fields:
        if width <= 0:
            continue  # array member: no single-value probe
        if width == 8:
            value = 1
        else:
            value = (1 << width) - 1
        lines.append(
            f"const struct ChallengeSettings ddx_probe_{name} = {{ .{name} = {value} }};"
        )
    lines += [
        "const unsigned long ddx_sizeof_cs = sizeof(struct ChallengeSettings);",
    ]
    return "\n".join(lines) + "\n"


def build_saveblock3_probe_c() -> str:
    return "\n".join(
        [
            "/* Generated by tools/hns-layout/generate_hns_challenge_layout.py;",
            "   do not edit. */",
            "#include \"global.h\"",
            "const unsigned long ddx_sizeof_sb3 = sizeof(struct SaveBlock3);",
            "const unsigned long ddx_off_sb3_cs =",
            "    __builtin_offsetof(struct SaveBlock3, challengeSettings);",
        ]
    ) + "\n"


def compile_probe(arm_gcc: str, source: str, workdir: Path, name: str,
                  extra_includes: list[str] | None = None) -> bytes:
    src_path = workdir / f"{name}.c"
    obj_path = workdir / f"{name}.o"
    src_path.write_text(source)
    cmd = [arm_gcc, "-c", str(src_path), "-o", str(obj_path)] + ARM_FLAGS
    for inc in extra_includes or []:
        cmd += ["-iquote", inc]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        fail(f"ARM probe {name} failed to compile:\n{result.stderr}")
    return obj_path.read_bytes()


def parse_symbols_and_rodata(arm_gcc: str, obj: bytes) -> dict[str, bytes]:
    """Map symbol name -> its bytes in .rodata (or .data)."""
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
        # format: <value> <size> <type> <name>  (size may be '-' for symbols without size)
        value_s, size_s, _type, name = parts[0], parts[1], parts[2], parts[3]
        if size_s == "-" or size_s == "":
            continue
        sizes[int(value_s, 16)] = (name, int(size_s, 16))

    dump = subprocess.run(
        [str(objdump), "-s", "-j", ".rodata", tmp_path], capture_output=True, text=True
    )
    if dump.returncode != 0 or not dump.stdout.strip():
        dump = subprocess.run(
            [str(objdump), "-s", "-j", ".data", tmp_path], capture_output=True, text=True
        )
        if dump.returncode != 0:
            fail("could not dump .rodata or .data from the probe object")
    blob = bytearray()
    base_addr = None
    for line in dump.stdout.splitlines():
        m = re.match(r"^\s*([0-9a-f]+)\s((?:[0-9a-f]{2,8}\s){1,4})", line)
        if not m:
            continue
        addr = int(m.group(1), 16)
        if base_addr is None:
            base_addr = addr
        hexdata = m.group(2).replace(" ", "")
        blob += bytes.fromhex(hexdata)
    if base_addr is None:
        fail("no data bytes found in the probe object")

    out: dict[str, bytes] = {}
    for value, (name, size) in sizes.items():
        offset = value - base_addr
        if 0 <= offset and offset + size <= len(blob):
            out[name] = bytes(blob[offset:offset + size])
    return out


def pattern_to_bits(pattern: bytes) -> list[tuple[int, int]]:
    """Yield (byte_index, bit_index) for every set bit, LSB-first per byte."""
    bits = []
    for byte_index, byte in enumerate(pattern):
        for bit in range(8):
            if byte & (1 << bit):
                bits.append((byte_index, bit))
    return bits


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--upstream-dir", required=False,
                        default=None, help="pinned pokehns-expansion checkout")
    parser.add_argument("--arm-gcc", default=None,
                        help="arm-none-eabi-gcc binary (default: discover)")
    parser.add_argument("--verify", action="store_true",
                        help="regenerate and compare instead of writing")
    parser.add_argument("--output", default=None,
                        help="output path (default: native/src/hns_challenge_settings_layout_gen.h)")
    args = parser.parse_args()

    upstream = (
        args.upstream_dir
        or os.environ.get("HNS_UPSTREAM_DIR")
        or discover_upstream()
    )
    upstream_path = Path(upstream)
    if not (upstream_path / GLOBAL_H).is_file():
        fail(f"{GLOBAL_H} not found under {upstream_path}")
    commit = git_head(upstream_path)
    if commit != PINNED_COMMIT:
        fail(
            f"{upstream_path} is at {commit}, expected the pinned "
            f"{PINNED_COMMIT} ({PINNED_TAG})"
        )

    arm_gcc = find_arm_gcc(args.arm_gcc)
    verify_commit(arm_gcc, upstream_path)

    global_h_text = (upstream_path / GLOBAL_H).read_text()
    fields = parse_struct_fields(global_h_text)

    with tempfile.TemporaryDirectory() as tmp:
        workdir = Path(tmp)

        # Probe 1: ChallengeSettings-internal layout.
        probe_src = build_field_probe_c(fields)
        obj = compile_probe(arm_gcc, probe_src, workdir, "field_probe")
        symbols = parse_symbols_and_rodata(arm_gcc, obj)

        sizeof_cs = int.from_bytes(symbols["ddx_sizeof_cs"], "little")
        zero = symbols["ddx_probe_zero"]
        if len(zero) != sizeof_cs or any(zero):
            fail("zero probe is not a zeroed struct of sizeof(ChallengeSettings) bytes")

        layout: dict[str, tuple[int, int, int]] = {}
        for name, width in fields:
            if width < 0:
                continue  # array member; never read by DualDex
            key = f"ddx_probe_{name}"
            if key not in symbols:
                fail(f"probe symbol {key} missing from the compiled object")
            pattern = symbols[key]
            if len(pattern) != sizeof_cs:
                fail(f"probe {name}: pattern length {len(pattern)} != sizeof")
            bits = pattern_to_bits(pattern)
            zero_bits = set(pattern_to_bits(zero))
            changed = [b for b in bits if b not in zero_bits]
            # The probe value is the field's maximum (or 1 for a bool8), so the
            # changed-bit count equals the popcount of the assigned value.
            probe_value = 1 if width == 8 else (1 << width) - 1
            expected_changed = bin(probe_value).count("1")
            if len(changed) != expected_changed:
                fail(
                    f"probe {name}: expected {expected_changed} changed bit(s), "
                    f"got {changed}"
                )
            byte_index = changed[0][0]
            if any(b[0] != byte_index for b in changed):
                fail(f"probe {name}: changed bits span multiple bytes: {changed}")
            bit_positions = sorted(b[1] for b in changed)
            expected_bits = list(
                range(bit_positions[0], bit_positions[0] + width)
            )
            if probe_value != 1 and bit_positions != expected_bits:
                fail(f"probe {name}: changed bits are not contiguous: {bit_positions}")
            layout[name] = (byte_index, bit_positions[0], width)

        # Probe 2: SaveBlock3 relationship, compiled against the pinned headers.
        sb3_obj = compile_probe(
            arm_gcc, build_saveblock3_probe_c(), workdir, "sb3_probe",
            extra_includes=[str(upstream_path / "include"), str(upstream_path)],
        )
        sb3_symbols = parse_symbols_and_rodata(arm_gcc, sb3_obj)
        sizeof_sb3 = int.from_bytes(sb3_symbols["ddx_sizeof_sb3"], "little")
        off_sb3_cs = int.from_bytes(sb3_symbols["ddx_off_sb3_cs"], "little")

    for name in FIELD_ORDER:
        if name not in layout:
            fail(f"required field {name} missing from struct ChallengeSettings")

    content = render_header(
        commit, arm_gcc, sizeof_cs, sizeof_sb3, off_sb3_cs, layout
    )
    output = Path(args.output) if args.output else Path(OUTPUT_HEADER)
    if args.verify:
        if not output.is_file():
            fail(f"{output} does not exist; run the generator without --verify first")
        committed = output.read_text()
        if committed != content:
            fail(f"{output} does not match a fresh generation from {upstream_path}")
        print(f"  challenge-settings layout verified against {upstream_path} @ {commit}")
    else:
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(content)
        print(f"  wrote {output}")


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


def verify_commit(arm_gcc: str, upstream_path: Path) -> None:
    # Fail closed on a dirty checkout: the probe compiles the checkout's own
    # global.h, so uncommitted edits would silently change the evidence.
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


def render_header(commit, arm_gcc, sizeof_cs, sizeof_sb3, off_sb3_cs, layout) -> str:
    lines = [
        "/*",
        " * GENERATED FILE — do not edit by hand.",
        " *",
        " * ChallengeSettings layout for the exact Heart & Soul 2.0.5 build,",
        " * derived from the pinned upstream source by",
        " *   tools/hns-layout/generate_hns_challenge_layout.py",
        " *",
        f" * Pinned upstream: PokemonHnS-Development/pokehns-expansion",
        f" *   commit {commit} (tag Release-v2.0.5)",
        f" * Compiler:   {Path(arm_gcc).name} (ARM GNU Toolchain 13.2.Rel1)",
        f" * Flags:      {' '.join(ARM_FLAGS)}",
        " *",
        " * Evidence chain:",
        " *   - sizeof/offsets: compiled probe objects read back from .rodata",
        " *     (see the generator), matching the official release ROM:",
        " *     gSaveblock3 == 52 bytes, challengeSettings at offset 16",
        " *     (ROM cross-checks: GetBattleMoveCategory reads optionStyle at",
        " *     gSaveBlock3Ptr + 0x11 bit 1; GetCurrentLevelCap and",
        " *     GetBaseStatEqualizerValue read SaveBlock3 + 0x18 bits 5-6/2-3;",
        " *     RandomizerFeatureEnabled reads SaveBlock3 + 0x14/0x15).",
        " *   - bit positions: LSB-first within each byte, identical across the",
        " *     pinned ARM toolchain and an independent host/clang probe.",
        " *",
        " * The SaveBlock3 never moves: SetSaveBlocksPointers() re-bases",
        " * gSaveBlock2Ptr/gSaveBlock1Ptr/gPokemonStoragePtr only, and",
        " * gSaveBlock3Ptr is statically initialised to &gSaveblock3.",
        " */",
        "",
        "#ifndef DUALDEX_HNS_CHALLENGE_SETTINGS_LAYOUT_GEN_H",
        "#define DUALDEX_HNS_CHALLENGE_SETTINGS_LAYOUT_GEN_H",
        "",
        "#include <stdint.h>",
        "",
        f"#define HNS_CHALLENGE_SETTINGS_SIZEOF {sizeof_cs}",
        f"#define HNS_SAVEBLOCK3_SIZEOF {sizeof_sb3}",
        f"#define HNS_SAVEBLOCK3_CHALLENGE_SETTINGS_OFFSET {off_sb3_cs}",
        "",
        "/** One field's position inside struct ChallengeSettings (bit offset",
        " *  LSB-first within the byte). */",
        "typedef struct {",
        "    const char* name;      /* field name in the pinned source */",
        "    uint8_t byte_offset;   /* bytes from the start of ChallengeSettings */",
        "    uint8_t bit_offset;    /* first bit within byte_offset, LSB-first */",
        "    uint8_t bit_width;     /* field width in bits */",
        "    uint8_t domain_mask;   /* (1u << bit_width) - 1: the whole encoding */",
        "    uint32_t valid_values; /* bitmask of values the pinned source assigns */",
        "} HnsChallengeFieldLayout;",
        "",
        "/* Byte/bit positions of every field DualDex reads.",
        " *",
        " * valid_values comes from the pinned source's own domains, not from the",
        " * encoding: challenge_menu.c choice tables (LevelCap OFF/NORMAL/HARD,",
        " * TrainerScalingIVs OFF/SCALE/HARD, MaxPartyIVs, ExpMultiplier",
        " * x1.0/x1.5/x2.0/x0.0) and GetBaseStatEqualizerValue's 0/100/255/500",
        " * table. 1-bit fields are always 0 or 1. A value outside valid_values",
        " * is reported out-of-domain by the reader, never coerced.",
        " */",
        "static const HnsChallengeFieldLayout HNS_CHALLENGE_FIELD_LAYOUT[] = {",
    ]
    for name in FIELD_ORDER:
        byte_offset, bit_offset, width = layout[name]
        valid_mask = 0
        for v in valid_values_for(name, width):
            valid_mask |= 1 << v
        domain_note = FIELD_DOMAINS[name]
        lines.append(
            f"    {{ .name = \"{name}\", .byte_offset = {byte_offset}, "
            f".bit_offset = {bit_offset}, .bit_width = {width}, "
            f".domain_mask = {(1 << width) - 1}, "
            f".valid_values = 0x{valid_mask:08x}u }},"
            f" /* {domain_note} */"
        )
    lines += [
        "};",
        "",
        f"#define HNS_CHALLENGE_FIELD_COUNT "
        f"(sizeof(HNS_CHALLENGE_FIELD_LAYOUT) / sizeof(HNS_CHALLENGE_FIELD_LAYOUT[0]))",
        "",
        "#endif /* DUALDEX_HNS_CHALLENGE_SETTINGS_LAYOUT_GEN_H */",
        "",
    ]
    return "\n".join(lines)


def valid_values_for(name: str, width: int) -> list[int]:
    """The pinned source's own value domain for a field."""
    if width == 8:
        return [1]  # bool8 autoRun never read by DualDex; 0/1 both legal
    note = FIELD_DOMAINS[name]
    # The recorded notes are the authority; parse the braces for clarity.
    m = re.match(r"\{([0-9,]+)\}", note)
    if not m:
        fail(f"no domain recorded for {name}")
    values = [int(v) for v in m.group(1).split(",")]
    for v in values:
        if v >= (1 << width):
            fail(f"domain value {v} does not fit {width} bit(s) for {name}")
    return values


if __name__ == "__main__":
    main()
