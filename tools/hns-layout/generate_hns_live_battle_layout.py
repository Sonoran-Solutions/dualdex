#!/usr/bin/env python3
"""Generate the H&S 2.0.5 live battle-state layout table (Gap C4e).

This is the authoritative source-check for the runtime readers added in C4e:

  * `struct BattlePokemon` HP / maxHP / status1 offsets and widths;
  * the byte offset of `struct BattlePokemon.volatiles` and the exact bit
    position of the damage-relevant volatile booleans the ordinary subset
    needs (`electrified`, `glaiveRush`) plus the two excluded by the move
    allow-list (`minimize`, `semiInvulnerable`);
  * the offset of `struct BattleStruct.gimmick` and of
    `struct BattleGimmickData.activeGimmick`, plus its side/party strides.

How: the script compiles a probe translation unit against the pinned headers
with the pinned ARM toolchain and reads the values back out of the emitted
object. Ordinary members use `__builtin_offsetof`/`sizeof` emitted as
`.rodata` scalars. Bitfield members cannot take an offsetof, so the probe
declares one designated-initializer `const struct Volatiles` per boolean (all
other members zero, value 1) and the script locates the single set bit; the
`semiInvulnerable` probe uses the width-3 state value 3 so both the base bit
and the width are recovered. This is compiled-evidence layout, not a
hand-maintained copy of a source comment.

The committed artifact (`native/src/hns_live_battle_layout_gen.h`) is
self-contained, so ordinary `./ci.sh test` never needs the upstream checkout or
the ARM toolchain. Re-verification against upstream belongs to
`./ci.sh source-check` via `--verify`.

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

# Same compiled-evidence flags as the other two H&S layout generators (§10 of
# the compatibility evidence).
ARM_FLAGS = [
    "-DMODERN=1", "-DTESTING=0", "-DPOKEMON_HNS", "-DEMERALD",
    "-std=gnu17", "-mthumb", "-mthumb-interwork", "-O2",
    "-mabi=apcs-gnu", "-mtune=arm7tdmi", "-march=armv4t",
]

GLOBAL_H = "include/global.h"
BATTLE_H = "include/battle.h"
POKEMON_H = "include/pokemon.h"
BATTLE_POKEMON_GEN_H = "native/src/hns_battle_pokemon_layout_gen.h"

OUTPUT_HEADER = "native/src/hns_live_battle_layout_gen.h"


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
            "/* Probe for tools/hns-layout/generate_hns_live_battle_layout.py;",
            "   not part of any build. */",
            "#include \"global.h\"",
            "#include \"battle.h\"",
            "const unsigned long ddx_sizeof_volatiles = sizeof(struct Volatiles);",
            "const unsigned long ddx_hp_offset =",
            "    __builtin_offsetof(struct BattlePokemon, hp);",
            "const unsigned long ddx_hp_size =",
            "    sizeof(((struct BattlePokemon *)0)->hp);",
            "const unsigned long ddx_max_hp_offset =",
            "    __builtin_offsetof(struct BattlePokemon, maxHP);",
            "const unsigned long ddx_max_hp_size =",
            "    sizeof(((struct BattlePokemon *)0)->maxHP);",
            "const unsigned long ddx_status_offset =",
            "    __builtin_offsetof(struct BattlePokemon, status1);",
            "const unsigned long ddx_status_size =",
            "    sizeof(((struct BattlePokemon *)0)->status1);",
            "const unsigned long ddx_volatiles_offset =",
            "    __builtin_offsetof(struct BattlePokemon, volatiles);",
            "const unsigned long ddx_gimmick_offset =",
            "    __builtin_offsetof(struct BattleStruct, gimmick);",
            "const unsigned long ddx_active_gimmick_offset =",
            "    __builtin_offsetof(struct BattleGimmickData, activeGimmick);",
            "const unsigned long ddx_gimmick_side_count = NUM_BATTLE_SIDES;",
            "const unsigned long ddx_gimmick_party_count = PARTY_SIZE;",
            "const unsigned long ddx_gimmick_count = GIMMICKS_COUNT;",
            "const struct Volatiles ddx_v_none = {0};",
            "const struct Volatiles ddx_v_electrified = { .electrified = 1 };",
            "const struct Volatiles ddx_v_glaive_rush = { .glaiveRush = 1 };",
            "const struct Volatiles ddx_v_minimize = { .minimize = 1 };",
            "const struct Volatiles ddx_v_semi_invulnerable = { .semiInvulnerable = SEMI_INVULNERABLE_COUNT };",
            "",
        ]
    )


def compile_probe(arm_gcc: str, source: str, workdir: Path,
                  extra_includes: list[str]) -> Path:
    src_path = workdir / "live_battle_probe.c"
    obj_path = workdir / "live_battle_probe.o"
    src_path.write_text(source)
    cmd = [arm_gcc, "-c", str(src_path), "-o", str(obj_path)] + ARM_FLAGS
    for inc in extra_includes:
        cmd += ["-iquote", inc]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        fail(f"ARM probe failed to compile:\n{result.stderr}")
    return obj_path


def _read_rodata(obj_path: Path, arm_gcc: str) -> tuple[bytearray, int]:
    toolchain_bin = Path(arm_gcc).parent
    objdump = toolchain_bin / "arm-none-eabi-objdump"
    dump = subprocess.run(
        [str(objdump), "-s", "-j", ".rodata", str(obj_path)],
        capture_output=True, text=True,
    )
    if dump.returncode != 0:
        fail(f"arm-none-eabi-objdump failed:\n{dump.stderr}")
    blob = bytearray()
    base_addr = None
    for line in dump.stdout.splitlines():
        m = re.match(r"^\s*([0-9a-f]+)\s+((?:[0-9a-f]{2,8}\s?)+)", line)
        if not m:
            continue
        addr = int(m.group(1), 16)
        if base_addr is None:
            base_addr = addr
        blob += bytes.fromhex(m.group(2).replace(" ", ""))
    if base_addr is None:
        fail("no .rodata bytes found in the probe object")
    return blob, base_addr


def read_symbols(obj_path: Path, arm_gcc: str) -> dict[str, tuple[int, int]]:
    toolchain_bin = Path(arm_gcc).parent
    nm = toolchain_bin / "arm-none-eabi-nm"
    out = subprocess.run(
        [str(nm), "-S", "--defined-only", str(obj_path)],
        capture_output=True, text=True,
    )
    if out.returncode != 0:
        fail(f"arm-none-eabi-nm failed:\n{out.stderr}")
    syms: dict[str, tuple[int, int]] = {}
    for line in out.stdout.splitlines():
        parts = line.split()
        if len(parts) < 4 or parts[1] in ("-", ""):
            continue
        syms[parts[3]] = (int(parts[0], 16), int(parts[1], 16))
    return syms


def scalar(blob: bytearray, base_addr: int, syms: dict, name: str) -> int:
    if name not in syms:
        fail(f"probe symbol {name} was not emitted")
    value, size = syms[name]
    offset = value - base_addr
    return int.from_bytes(blob[offset:offset + size], "little")


def single_bit(blob: bytearray, base_addr: int, syms: dict, name: str) -> int:
    """The bit index of the one set bit in a designated-initializer probe object."""
    if name not in syms:
        fail(f"probe symbol {name} was not emitted")
    value, size = syms[name]
    data = blob[value - base_addr:value - base_addr + size]
    bits = [(byte, bit) for byte, b in enumerate(data) for bit in range(8) if (b >> bit) & 1]
    if len(bits) != 1:
        fail(f"{name}: expected exactly one set bit, found {bits}")
    return bits[0][0] * 8 + bits[0][1]


def multi_bit(blob: bytearray, base_addr: int, syms: dict, name: str) -> tuple[int, int]:
    """The base bit and contiguous width of a multi-bit designated-initializer value."""
    if name not in syms:
        fail(f"probe symbol {name} was not emitted")
    value, size = syms[name]
    data = blob[value - base_addr:value - base_addr + size]
    bits = [byte * 8 + bit for byte, b in enumerate(data) for bit in range(8) if (b >> bit) & 1]
    if not bits:
        fail(f"{name}: no set bit found")
    base = min(bits)
    width = max(bits) - base + 1
    if bits != list(range(base, base + width)):
        fail(f"{name}: set bits {bits} are not contiguous")
    return base, width


def parse_source_pins(upstream_path: Path) -> dict[str, int]:
    """Source-text cross-checks for the ordinary members (compiled ABI wins)."""
    pins: dict[str, int] = {}
    text = (upstream_path / POKEMON_H).read_text()
    struct_m = re.search(r"struct BattlePokemon\s*\{(.*?)\n\};", text, re.S)
    if not struct_m:
        fail("struct BattlePokemon not found in include/pokemon.h")
    body = struct_m.group(1)
    checks = {
        "source_hp_offset": r"/\*0x([0-9A-Fa-f]+)\*/\s*u16 hp;",
        "source_max_hp_offset": r"/\*0x([0-9A-Fa-f]+)\*/\s*u16 maxHP;",
        "source_status_offset": r"/\*0x([0-9A-Fa-f]+)\*/\s*u32 status1;",
        "source_volatiles_offset": r"/\*0x([0-9A-Fa-f]+)\*/\s*struct Volatiles volatiles;",
    }
    for key, pattern in checks.items():
        m = re.search(pattern, body)
        if not m:
            fail(f"could not parse {key} from include/pokemon.h; the pinned source shape changed")
        pins[key] = int(m.group(1), 16)
    return pins


def render_header(arm_gcc, compiled: dict, pins: dict, previous: str | None) -> str:
    hp_offset = compiled["hp_offset"]
    max_hp_offset = compiled["max_hp_offset"]
    status_offset = compiled["status_offset"]
    volatiles_offset = compiled["volatiles_offset"]
    sizeof_volatiles = compiled["sizeof_volatiles"]

    agreements = []
    disagreements = []
    for label, compiled_v, source_v in (
        ("hp byte offset", hp_offset, pins["source_hp_offset"]),
        ("maxHP byte offset", max_hp_offset, pins["source_max_hp_offset"]),
        ("status1 byte offset", status_offset, pins["source_status_offset"]),
        ("volatiles byte offset", volatiles_offset, pins["source_volatiles_offset"]),
    ):
        (agreements if compiled_v == source_v else disagreements).append(
            f"{label}: compiled {compiled_v}, source text {source_v}"
        )

    def hx(v: int) -> str:
        return f"0x{v:02X}"

    lines = [
        "/*",
        " * GENERATED FILE — do not edit by hand.",
        " *",
        " * Live battle-state layout for the exact Heart & Soul 2.0.5 build,",
        " * derived from the pinned upstream source by",
        " *   tools/hns-layout/generate_hns_live_battle_layout.py",
        " *",
        " * Pinned upstream: PokemonHnS-Development/pokehns-expansion",
        f" *   commit {PINNED_COMMIT} (tag {PINNED_TAG})",
        f" * Compiler:   {Path(arm_gcc).name} (ARM GNU Toolchain 13.2.Rel1)",
        f" * Flags:      {' '.join(ARM_FLAGS)}",
        " *",
        " * This is the ABI evidence for the C4e live-state readers: the",
        " * attacker's HP/maxHP (pinch-ability threshold), status1, the",
        " * BattlePokemon volatile bits, and the gimmick active array. Ordinary",
        " * members are compiled offsetof/sizeof scalars; bitfield members are",
        " * located by compiling one designated-initializer object per bit and",
        " * reading back the set bit.",
        " *",
        " * Struct member offsets (compiled, authoritative over the source",
        " * 0xNN member comments, which are stale):",
        f" *   sizeof(struct Volatiles)     = {sizeof_volatiles}",
        f" *   BattlePokemon.hp             = {hp_offset}",
        f" *   BattlePokemon.maxHP          = {max_hp_offset}",
        f" *   BattlePokemon.status1        = {status_offset}",
        f" *   BattlePokemon.volatiles      = {volatiles_offset}",
        f" *   volatile electrified bit     = {compiled['volatile_electrified_bit']}",
        f" *   volatile glaiveRush bit      = {compiled['volatile_glaive_rush_bit']}",
        f" *   volatile minimize bit        = {compiled['volatile_minimize_bit']}",
        " *   volatile semiInvulnerable    = "
        f"bit {compiled['volatile_semi_invulnerable_bit']} width "
        f"{compiled['volatile_semi_invulnerable_width']}",
        f" *   BattleStruct.gimmick         = {compiled['gimmick_offset']}",
        f" *   BattleGimmickData.activeGimmick = {compiled['active_gimmick_offset']}",
        "",
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
        "#ifndef DUALDEX_HNS_LIVE_BATTLE_LAYOUT_GEN_H",
        "#define DUALDEX_HNS_LIVE_BATTLE_LAYOUT_GEN_H",
        "",
        '#include "hns_battle_pokemon_layout_gen.h"',
        "",
        f"#define HNS_LIVE_BP_HP_OFFSET {hp_offset}",
        f"#define HNS_LIVE_BP_HP_SIZE {compiled['hp_size']}",
        f"#define HNS_LIVE_BP_MAX_HP_OFFSET {max_hp_offset}",
        f"#define HNS_LIVE_BP_MAX_HP_SIZE {compiled['max_hp_size']}",
        f"#define HNS_LIVE_BP_STATUS_OFFSET {status_offset}",
        f"#define HNS_LIVE_BP_STATUS_SIZE {compiled['status_size']}",
        f"#define HNS_LIVE_BP_VOLATILES_OFFSET {volatiles_offset}",
        f"#define HNS_LIVE_BP_VOLATILE_ELECTRIFIED_BIT {compiled['volatile_electrified_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_GLAIVE_RUSH_BIT {compiled['volatile_glaive_rush_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_MINIMIZE_BIT {compiled['volatile_minimize_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_SEMI_INVULNERABLE_BIT {compiled['volatile_semi_invulnerable_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_SEMI_INVULNERABLE_WIDTH {compiled['volatile_semi_invulnerable_width']}",
        f"#define HNS_LIVE_BATTLE_STRUCT_GIMMICK_OFFSET {compiled['gimmick_offset']}",
        f"#define HNS_LIVE_BATTLE_GIMMICK_ACTIVE_OFFSET {compiled['active_gimmick_offset']}",
        f"#define HNS_LIVE_BATTLE_GIMMICK_SIDE_COUNT {compiled['gimmick_side_count']}",
        f"#define HNS_LIVE_BATTLE_GIMMICK_PARTY_COUNT {compiled['gimmick_party_count']}",
        f"#define HNS_LIVE_BATTLE_GIMMICK_COUNT {compiled['gimmick_count']}",
        "",
        "/*",
        " * Field-domain sentinels from the pinned source, recorded here so the",
        " * reader never invents a value:",
        " *   SemiInvulnerableState: NONE=0 UNDERGROUND=1 UNDERWATER=2 ON_AIR=3",
        " *                          PHANTOM_FORCE=4 SKY_DROP=5 COMMANDER=6",
        " *   enum Gimmick:          NONE=0 MEGA=1 ULTRA_BURST=2 Z_MOVE=3",
        " *                          DYNAMAX=4 TERA=5",
        " */",
        "",
        "#if HNS_LIVE_BP_HP_OFFSET + HNS_LIVE_BP_HP_SIZE > HNS_BATTLE_POKEMON_SIZEOF",
        "#error \"BattlePokemon hp field exceeds the compiled struct size\"",
        "#endif",
        "#if HNS_LIVE_BP_MAX_HP_OFFSET + HNS_LIVE_BP_MAX_HP_SIZE > HNS_BATTLE_POKEMON_SIZEOF",
        "#error \"BattlePokemon maxHP field exceeds the compiled struct size\"",
        "#endif",
        "#if HNS_LIVE_BP_STATUS_OFFSET + HNS_LIVE_BP_STATUS_SIZE > HNS_BATTLE_POKEMON_SIZEOF",
        "#error \"BattlePokemon status1 field exceeds the compiled struct size\"",
        "#endif",
        "#if HNS_LIVE_BP_VOLATILES_OFFSET + 1 > HNS_BATTLE_POKEMON_SIZEOF",
        "#error \"BattlePokemon volatiles field exceeds the compiled struct size\"",
        "#endif",
        "#if HNS_LIVE_BP_VOLATILE_ELECTRIFIED_BIT >= 8 * 12 || HNS_LIVE_BP_VOLATILE_GLAIVE_RUSH_BIT >= 8 * 12",
        "#error \"volatile bits exceed the first 12 volatile bytes\"",
        "#endif",
        "#if HNS_LIVE_BATTLE_GIMMICK_ACTIVE_OFFSET + HNS_LIVE_BATTLE_GIMMICK_SIDE_COUNT * HNS_LIVE_BATTLE_GIMMICK_PARTY_COUNT > 64",
        "#error \"BattleGimmickData.activeGimmick implausibly large\"",
        "#endif",
        "",
        "#endif /* DUALDEX_HNS_LIVE_BATTLE_LAYOUT_GEN_H */",
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
    parser.add_argument("--upstream-dir", default=None,
                        help="pinned pokehns-expansion checkout")
    parser.add_argument("--arm-gcc", default=None,
                        help="arm-none-eabi-gcc binary (default: discover)")
    parser.add_argument("--verify", action="store_true",
                        help="regenerate and compare instead of writing")
    parser.add_argument("--output", default=None,
                        help="override output path (default: committed header)")
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parent.parent.parent
    upstream = (
        args.upstream_dir
        or os.environ.get("HNS_UPSTREAM_DIR")
        or discover_upstream()
    )
    upstream_path = Path(upstream).resolve()
    if not (upstream_path / GLOBAL_H).is_file():
        fail(f"{upstream_path} is not a pokehns-expansion checkout")

    verify_commit(upstream_path)
    head = git_head(upstream_path)
    if head != PINNED_COMMIT:
        fail(f"upstream HEAD is {head}, expected pinned {PINNED_COMMIT}")

    arm_gcc = find_arm_gcc(args.arm_gcc)
    pins = parse_source_pins(upstream_path)

    with tempfile.TemporaryDirectory() as tmp:
        workdir = Path(tmp)
        obj_path = compile_probe(
            arm_gcc, build_probe_c(), workdir,
            [str(upstream_path / "include"), str(upstream_path)],
        )
        blob, base_addr = _read_rodata(obj_path, arm_gcc)
        syms = read_symbols(obj_path, arm_gcc)
        compiled = {
            "sizeof_volatiles": scalar(blob, base_addr, syms, "ddx_sizeof_volatiles"),
            "hp_offset": scalar(blob, base_addr, syms, "ddx_hp_offset"),
            "hp_size": scalar(blob, base_addr, syms, "ddx_hp_size"),
            "max_hp_offset": scalar(blob, base_addr, syms, "ddx_max_hp_offset"),
            "max_hp_size": scalar(blob, base_addr, syms, "ddx_max_hp_size"),
            "status_offset": scalar(blob, base_addr, syms, "ddx_status_offset"),
            "status_size": scalar(blob, base_addr, syms, "ddx_status_size"),
            "volatiles_offset": scalar(blob, base_addr, syms, "ddx_volatiles_offset"),
            "volatile_electrified_bit": single_bit(blob, base_addr, syms, "ddx_v_electrified"),
            "volatile_glaive_rush_bit": single_bit(blob, base_addr, syms, "ddx_v_glaive_rush"),
            "volatile_minimize_bit": single_bit(blob, base_addr, syms, "ddx_v_minimize"),
            "gimmick_offset": scalar(blob, base_addr, syms, "ddx_gimmick_offset"),
            "active_gimmick_offset": scalar(blob, base_addr, syms, "ddx_active_gimmick_offset"),
            "gimmick_side_count": scalar(blob, base_addr, syms, "ddx_gimmick_side_count"),
            "gimmick_party_count": scalar(blob, base_addr, syms, "ddx_gimmick_party_count"),
            "gimmick_count": scalar(blob, base_addr, syms, "ddx_gimmick_count"),
        }
        semibit, semiwidth = multi_bit(blob, base_addr, syms, "ddx_v_semi_invulnerable")
        compiled["volatile_semi_invulnerable_bit"] = semibit
        compiled["volatile_semi_invulnerable_width"] = semiwidth

    generated = render_header(arm_gcc, compiled, pins, None)
    out_path = Path(args.output) if args.output else (repo_root / OUTPUT_HEADER)
    if args.verify:
        if not out_path.is_file():
            fail(f"{out_path} does not exist; run without --verify to generate it")
        if out_path.read_text() != generated:
            fail(
                f"{out_path} differs from the pinned-source regeneration; "
                "the committed layout artifact is stale"
            )
        print(f"verified {out_path.relative_to(repo_root)}")
        return
    out_path.write_text(generated)
    print(f"wrote {out_path.relative_to(repo_root)}")


if __name__ == "__main__":
    main()
