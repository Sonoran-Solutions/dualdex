#!/usr/bin/env python3
"""Generate the H&S 2.0.5 live battle-state layout table (Gap C4e / switch-in settlement).

This is the authoritative source-check for the runtime readers added in C4e:

  * `struct BattlePokemon` HP / maxHP / status1 offsets and widths;
  * the byte offset of `struct BattlePokemon.volatiles` and the exact bit
    position of the damage-relevant volatile booleans the ordinary subset
    needs (`electrified`, `glaiveRush`, `chargeTimer`, `tarShot`) plus the two
    excluded by the move allow-list (`minimize`, `semiInvulnerable`), and the
    persistent volatiles the pinned damage path reads on ordinary moves
    (`roostActive`, `foresight`, `miracleEye`, `root`, `smackDown`,
    `telekinesis`, `magnetRise`, `gastroAcid`) plus the `GetAdjustedDamage`
    states (`substitute`, `endured`);
  * the offset of `struct BattleStruct.gimmick` and of
    `struct BattleGimmickData.activeGimmick`, plus its side/party strides;
  * the `BattleStruct.eventState`, `battlerState`, and `monToSwitchIntoId` offsets plus compiled
    bit positions used to prove that `SWITCH_IN_EVENTS_COUNT` was reached and every active
    switch-in flag was cleared;
  * the official-release `gBattleMainFunc` symbol and action-selection callback used to keep the
    whole replacement action/script/controller sequence pending.

How: the script compiles a probe translation unit against the pinned headers
with the pinned ARM toolchain and reads the values back out of the emitted
object. Ordinary members use `__builtin_offsetof`/`sizeof` emitted as
`.rodata` scalars. Bitfield members cannot take an offsetof, so the probe
declares one designated-initializer `const struct Volatiles` per boolean (all
other members zero, value 1) and the script locates the single set bit; the
`semiInvulnerable` probe uses the source count sentinel 7, setting all three
bits so both the base bit and the width are recovered. The enum domain is
separately source-pinned; the count sentinel is never an admitted runtime state. This is compiled-evidence layout, not a
hand-maintained copy of a source comment.

The committed artifact (`native/src/hns_live_battle_layout_gen.h`) is
self-contained, so ordinary `./ci.sh test` never needs the upstream checkout or
the ARM toolchain. Re-verification against upstream belongs to
`./ci.sh source-check` via `--verify`.

Pinned upstream revision: 1f42b74dff0e9fe942419845d040663dd829a973
(tag Release-v2.0.5).
"""

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

PINNED_COMMIT = "1f42b74dff0e9fe942419845d040663dd829a973"
PINNED_TAG = "Release-v2.0.5"

# Generated source-build -> official-release bindings, independently observed during
# real battle transitions. Source-build .sym code/IWRAM addresses do not match this ROM.
PHASE_EVIDENCE = json.loads(Path(__file__).with_name("release_phase_evidence.json").read_text())
BATTLE_MAIN_FUNC_GBA_ADDRESS = PHASE_EVIDENCE["gBattleMainFunc"]["address"]
ACTION_SELECTION_FUNC_PTR = PHASE_EVIDENCE["functions"]["HandleTurnActionSelectionState"]["releaseAddress"] | 1
RUN_TURN_ACTIONS_FUNC_PTR = PHASE_EVIDENCE["functions"]["RunTurnActionsFunctions"]["releaseAddress"] | 1
TURN_ORDER_GLOBAL_ADDRESSES = {
    "gLockedMoves": 0x02000378,
    "gChosenMoveByBattler": 0x020002DC,
    "gProtectStructs": 0x020000B8,
    "gSideTimers": 0x02000258,
    "gBattlersCount": 0x020000B0,
    "gBattlerAttacker": 0x02000124,
    "gCurrentMove": 0x020003A0,
    "gCurrentActionFuncId": 0x02000125,
    "gCurrentTurnActionNumber": 0x02000302,
    "gActionsByTurnOrder": 0x02000304,
    "gBattlerByTurnOrder": 0x020003B8,
}

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
            "#include \"main.h\"",
            "#include \"constants/battle_switch_in.h\"",
            "const unsigned long ddx_protect_size = sizeof(struct ProtectStruct);",
            "const unsigned long ddx_battle_type_mask = BATTLE_TYPE_MORE_THAN_TWO_BATTLERS;",
            "const unsigned long ddx_side_timer_size = sizeof(struct SideTimer);",
            "const struct ProtectStruct ddx_helping_hand = { .helpingHand = 7 };",
            "const struct SideTimer ddx_follow_me = { .followmeTimer = 15 };",
            "const struct BattleStruct ddx_mold_breaker = { .moldBreakerActive = 1 };",
            "const struct BattleStruct ddx_pledge = { .pledgeMove = 1 };",
            "const unsigned long ddx_sizeof_volatiles = sizeof(struct Volatiles);",
            "const unsigned long ddx_main_callback1_offset = __builtin_offsetof(struct Main, callback1);",
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
            "const unsigned long ddx_personality_offset =",
            "    __builtin_offsetof(struct BattlePokemon, personality);",
            "const unsigned long ddx_personality_size =",
            "    sizeof(((struct BattlePokemon *)0)->personality);",
            "const unsigned long ddx_volatiles_offset =",
            "    __builtin_offsetof(struct BattlePokemon, volatiles);",
            "const unsigned long ddx_gimmick_offset =",
            "    __builtin_offsetof(struct BattleStruct, gimmick);",
            "const unsigned long ddx_battle_struct_event_state_offset =",
            "    __builtin_offsetof(struct BattleStruct, eventState);",
            "const unsigned long ddx_battle_struct_battler_state_offset =",
            "    __builtin_offsetof(struct BattleStruct, battlerState);",
            "const unsigned long ddx_battle_struct_mon_to_switch_into_id_offset =",
            "    __builtin_offsetof(struct BattleStruct, monToSwitchIntoId);",
            "const unsigned long ddx_battler_state_size = sizeof(struct BattlerState);",
            "const unsigned long ddx_max_battlers_count = MAX_BATTLERS_COUNT;",
            "const unsigned long ddx_switch_in_events_count = SWITCH_IN_EVENTS_COUNT;",
            "const struct EventStates ddx_event_switch_in_one = { .switchIn = 1 };",
            "const struct EventStates ddx_event_switch_in_all = { .switchIn = (enum SwitchInEvents)0xFF };",
            "const struct BattlerState ddx_battler_switch_in = { .switchIn = 1 };",
            "const struct BattlerState ddx_battler_first_turn_two = { .isFirstTurn = 3 };",
            "const unsigned long ddx_active_gimmick_offset =",
            "    __builtin_offsetof(struct BattleGimmickData, activeGimmick);",
            "const unsigned long ddx_usable_gimmick_offset =",
            "    __builtin_offsetof(struct BattleGimmickData, usableGimmick);",
            "const unsigned long ddx_player_select_offset =",
            "    __builtin_offsetof(struct BattleGimmickData, playerSelect);",
            "const unsigned long ddx_battle_struct_supreme_overlord_counter_offset =",
            "    __builtin_offsetof(struct BattleStruct, supremeOverlordCounter);",
            "const unsigned long ddx_supreme_overlord_counter_stride =",
            "    sizeof(((struct BattleStruct *)0)->supremeOverlordCounter[0]);",
            "const unsigned long ddx_supreme_overlord_counter_count =",
            "    sizeof(((struct BattleStruct *)0)->supremeOverlordCounter) / sizeof(((struct BattleStruct *)0)->supremeOverlordCounter[0]);",
            "const unsigned long ddx_b_action_use_move = B_ACTION_USE_MOVE;",
            "const unsigned long ddx_b_action_exec_script = B_ACTION_EXEC_SCRIPT;",
            "const unsigned long ddx_gimmick_side_count = NUM_BATTLE_SIDES;",
            "const unsigned long ddx_gimmick_party_count = PARTY_SIZE;",
            "const unsigned long ddx_gimmick_count = GIMMICKS_COUNT;",
            "const unsigned long ddx_gimmick_dynamax = GIMMICK_DYNAMAX;",
            "const unsigned long ddx_num_stats = NUM_STATS;",
            "const unsigned long ddx_locked_move_stride = sizeof(gLockedMoves[0]);",
            "const unsigned long ddx_chosen_move_stride = sizeof(gChosenMoveByBattler[0]);",
            "const unsigned long ddx_moves_count = MOVES_COUNT_ALL;",
            "const unsigned long ddx_move_beak_blast = MOVE_BEAK_BLAST;",
            "const struct ProtectStruct ddx_protected = { .protected = ~0u };",
            "const struct Volatiles ddx_v_recharge_timer = { .rechargeTimer = ~0u };",
            "const struct Volatiles ddx_v_rollout_timer = { .rolloutTimer = UINT8_MAX };",
            "const struct Volatiles ddx_v_defense_curl = { .defenseCurl = 1 };",
            "const struct Volatiles ddx_v_multiple_turns = { .multipleTurns = 1 };",
            "const struct Volatiles ddx_v_none = {0};",
            "const struct Volatiles ddx_v_neutralizing_gas = { .neutralizingGas = 1 };",
            "const struct Volatiles ddx_v_electrified = { .electrified = 1 };",
            "const struct Volatiles ddx_v_glaive_rush = { .glaiveRush = 1 };",
            "const struct Volatiles ddx_v_minimize = { .minimize = 1 };",
            "const struct Volatiles ddx_v_semi_invulnerable = { .semiInvulnerable = SEMI_INVULNERABLE_COUNT };",
            "const struct Volatiles ddx_v_charge_timer = { .chargeTimer = 7 };",
            "const struct Volatiles ddx_v_tar_shot = { .tarShot = 1 };",
            # Gap C4e correction: persistent volatiles read by the pinned type-effectiveness,
            # groundedness and effective-ability paths on ordinary EFFECT_HIT moves.
            "const struct Volatiles ddx_v_foresight = { .foresight = 1 };",
            "const struct Volatiles ddx_v_miracle_eye = { .miracleEye = 1 };",
            "const struct Volatiles ddx_v_root = { .root = 1 };",
            "const struct Volatiles ddx_v_smack_down = { .smackDown = 1 };",
            "const struct Volatiles ddx_v_telekinesis = { .telekinesis = 1 };",
            "const struct Volatiles ddx_v_magnet_rise = { .magnetRise = 1 };",
            "const struct Volatiles ddx_v_gastro_acid = { .gastroAcid = 1 };",
            "const struct Volatiles ddx_v_roost_active = { .roostActive = 1 };",
            # GetAdjustedDamage reads these on the ordinary path: a substitute redirects the
            # computed damage and `endured` caps it at HP-1.
            "const struct Volatiles ddx_v_substitute = { .substitute = 1 };",
            "const struct Volatiles ddx_v_endured = { .endured = 1 };",
            "const struct Volatiles ddx_v_slow_start_timer = { .slowStartTimer = ~0u };",
            "const struct Volatiles ddx_v_flash_fire_boosted = { .flashFireBoosted = 1 };",
            "const struct Volatiles ddx_v_transformed = { .transformed = 1 };",
            "const struct Volatiles ddx_v_booster_energy_activated = { .boosterEnergyActivated = 1 };",
            "const struct Volatiles ddx_v_paradox_boosted_stat = { .paradoxBoostedStat = (enum Stat)~0u };",
            "const struct Volatiles ddx_v_vessel_of_ruin = { .vesselOfRuin = 1 };",
            "const struct Volatiles ddx_v_sword_of_ruin = { .swordOfRuin = 1 };",
            "const struct Volatiles ddx_v_tablets_of_ruin = { .tabletsOfRuin = 1 };",
            "const struct Volatiles ddx_v_beads_of_ruin = { .beadsOfRuin = 1 };",
            "const struct Volatiles ddx_v_heal_block = { .healBlock = 1 };",
            "const struct Volatiles ddx_v_embargo = { .embargo = 1 };",
            "const struct Volatiles ddx_v_metronome_item_counter = { .metronomeItemCounter = UINT8_MAX };",
            "const struct Volatiles ddx_v_transformed_mon_species = { .transformedMonSpecies = ~0u };",
            "const unsigned long ddx_num_species = NUM_SPECIES;",
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

    # Validate the action-selection callback gate against the pinned engine.
    # The event counter and BattlerState.switchIn flags are reset only in
    # Cmd_switchineffects, after Cmd_switchindataupdate has installed the new
    # battler. Requiring the main callback to return to action selection also
    # blocks the gap before switchineffects begins (and script/controller work
    # through the rest of the replacement sequence).
    battle_h = (upstream_path / "include/battle.h").read_text()
    battle_main = (upstream_path / "src/battle_main.c").read_text()
    battle_util = (upstream_path / "src/battle_util.c").read_text()
    commands = (upstream_path / "src/battle_script_commands.c").read_text()
    if not re.search(r"extern\s+void\s*\(\*gBattleMainFunc\)\(void\);", battle_h):
        fail("gBattleMainFunc declaration changed; re-audit the switch-in phase gate")
    for symbol in TURN_ORDER_GLOBAL_ADDRESSES:
        if not re.search(rf"\b{symbol}\b", battle_h) and not re.search(rf"\b{symbol}\b", battle_main):
            fail(f"turn-order source symbol {symbol} disappeared")
    runtime_declarations = ((upstream_path / POKEMON_H).read_text() + "\n" +
                            (upstream_path / "include/constants/battle.h").read_text())
    for required in ("u32 personality;", "slowStartTimer,", "flashFireBoosted,",
                     "transformed,", "boosterEnergyActivated,", "paradoxBoostedStat,",
                     "vesselOfRuin,", "swordOfRuin,", "tabletsOfRuin,", "beadsOfRuin,"):
        if required not in runtime_declarations:
            fail(f"pinned runtime field declaration changed: {required}")
    if not re.search(r"u16\s+isFirstTurn\s*:\s*2\s*;", battle_h):
        fail("BattlerState.isFirstTurn source domain changed; re-audit Stakeout")
    if "u8 supremeOverlordCounter[MAX_BATTLERS_COUNT]" not in battle_h:
        fail("BattleStruct.supremeOverlordCounter source declaration changed")
    if not re.search(r"gBattleMainFunc\s*=\s*HandleTurnActionSelectionState\s*;", battle_main):
        fail("pinned source no longer assigns the action-selection callback")
    use_move = re.search(r"void HandleAction_UseMove\(void\)\s*\{(.*?)\n\}", battle_util, re.S)
    for statement in ("gBattlerAttacker = gBattlerByTurnOrder[gCurrentTurnActionNumber];",
                      "gCurrentActionFuncId = B_ACTION_EXEC_SCRIPT;"):
        if not use_move or statement not in use_move.group(1):
            fail("current move execution provenance changed; re-audit Analytic")
    if "[B_ACTION_EXEC_SCRIPT]            = HandleAction_RunBattleScript" not in battle_main:
        fail("current action script dispatch changed")
    if "gBattleMainFunc = RunTurnActionsFunctions;" not in battle_main:
        fail("turn action execution callback changed")
    data_update = re.search(
        r"static void Cmd_switchindataupdate\(void\)\s*\{(.*?)\n\}", commands, re.S
    )
    switch_effects = re.search(
        r"static void Cmd_switchineffects\(void\)\s*\{(.*?)\n\}", commands, re.S
    )
    sent_flags = re.search(
        r"static void UpdateSentMonFlags\([^)]*\)\s*\{(.*?)\n\}", commands, re.S
    )
    if not data_update or "monData[i] = gBattleResources->bufferB[battler][4 + i]" not in data_update.group(1):
        fail("Cmd_switchindataupdate no longer installs the replacement battler as expected")
    if not switch_effects or "gBattleStruct->eventState.switchIn = 0" not in switch_effects.group(1):
        fail("Cmd_switchineffects no longer initializes the switch-in event counter")
    sent_flags_index = switch_effects.group(1).find("UpdateSentMonFlags(battler)")
    event_reset_index = switch_effects.group(1).find("gBattleStruct->eventState.switchIn = 0")
    if sent_flags_index < 0 or event_reset_index < 0 or sent_flags_index >= event_reset_index:
        fail("switch-in flags/event reset ordering changed; re-audit the phase gate")
    if not sent_flags or "gBattleStruct->battlerState[battler].switchIn = TRUE" not in sent_flags.group(1):
        fail("UpdateSentMonFlags no longer marks the battler entering before events")

    pins["battle_main_func_gba_address"] = BATTLE_MAIN_FUNC_GBA_ADDRESS
    pins["action_selection_func_ptr"] = ACTION_SELECTION_FUNC_PTR
    pins["run_turn_actions_func_ptr"] = RUN_TURN_ACTIONS_FUNC_PTR
    pins.update({f"{name.lower()}_gba_address": address
                 for name, address in TURN_ORDER_GLOBAL_ADDRESSES.items()})

    if PHASE_EVIDENCE["upstreamCommit"] != PINNED_COMMIT or PHASE_EVIDENCE["releaseRomSha256"] != (
        "edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b"
    ):
        fail("release phase evidence identity changed")
    for path, expected in PHASE_EVIDENCE["sourceFiles"].items():
        if hashlib.sha256((upstream_path / path).read_bytes()).hexdigest() != expected:
            fail(f"release phase source changed: {path}")
    if any(binding["matches"] != 1 for binding in PHASE_EVIDENCE["functions"].values()):
        fail("release phase binding is not unique")
    # Reuse the committed official-release map excerpt, independent of a locally built .sym.
    release_symbols = (Path(__file__).resolve().parents[1] /
                       "hns-runtime-probe/evidence/hns205-field-layout-symbols.txt").read_text()
    if PINNED_COMMIT not in release_symbols or not re.search(
            r"^\s*0x020002dc\s+gChosenMoveByBattler\s*$", release_symbols, re.M):
        fail("official chosen-move release-symbol evidence changed")
    # EWRAM globals retain source-build addresses, independently checked by live
    # reader transitions. Do not use the ignored source-build .sym as release code authority.
    symbols_path = upstream_path / "pokehns.sym"
    if symbols_path.is_file():
        symbols = {}
        for line in symbols_path.read_text().splitlines():
            match = re.match(r"^([0-9a-fA-F]+)\s+\S+\s+\S+\s+(\S+)\s*$", line)
            if match:
                symbols[match.group(2)] = int(match.group(1), 16)
        for symbol, expected in TURN_ORDER_GLOBAL_ADDRESSES.items():
            if symbols.get(symbol) != expected:
                fail(f"pinned EWRAM symbol {symbol} changed")
    return pins


def render_header(arm_gcc, compiled: dict, pins: dict, previous: str | None) -> str:
    hp_offset = compiled["hp_offset"]
    personality_offset = compiled["personality_offset"]
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
        " * Release-symbol source: official ROM SHA-256 edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b",
        f" * Compiler:   {Path(arm_gcc).name} (ARM GNU Toolchain 13.2.Rel1)",
        f" * Flags:      {' '.join(ARM_FLAGS)}",
        " *",
        " * This is the ABI evidence for the C4e live-state readers and the Group B",
        " * switch-in settlement proof:",
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
        f" *   BattlePokemon.personality    = {personality_offset} ({compiled['personality_size']} bytes)",
        f" *   BattlePokemon.maxHP          = {max_hp_offset}",
        f" *   BattlePokemon.status1        = {status_offset}",
        f" *   BattlePokemon.volatiles      = {volatiles_offset}",
        f" *   volatile electrified bit     = {compiled['volatile_electrified_bit']}",
        f" *   volatile glaiveRush bit      = {compiled['volatile_glaive_rush_bit']}",
        f" *   volatile minimize bit        = {compiled['volatile_minimize_bit']}",
        " *   volatile semiInvulnerable    = "
        f"bit {compiled['volatile_semi_invulnerable_bit']} width "
        f"{compiled['volatile_semi_invulnerable_width']}",
        " *   volatile chargeTimer         = "
        f"bit {compiled['volatile_charge_timer_bit']} width "
        f"{compiled['volatile_charge_timer_width']}",
        f" *   volatile tarShot bit         = {compiled['volatile_tar_shot_bit']}",
        f" *   volatile foresight bit       = {compiled['volatile_foresight_bit']}",
        f" *   volatile miracleEye bit      = {compiled['volatile_miracle_eye_bit']}",
        f" *   volatile root bit            = {compiled['volatile_root_bit']}",
        f" *   volatile smackDown bit       = {compiled['volatile_smack_down_bit']}",
        f" *   volatile telekinesis bit     = {compiled['volatile_telekinesis_bit']}",
        f" *   volatile magnetRise bit      = {compiled['volatile_magnet_rise_bit']}",
        f" *   volatile gastroAcid bit      = {compiled['volatile_gastro_acid_bit']}",
        f" *   volatile roostActive bit     = {compiled['volatile_roost_active_bit']}",
        f" *   volatile substitute bit       = {compiled['volatile_substitute_bit']}",
        f" *   volatile endured bit         = {compiled['volatile_endured_bit']}",
        f" *   volatile slowStartTimer      = bit {compiled['volatile_slow_start_timer_bit']} width {compiled['volatile_slow_start_timer_width']}",
        f" *   volatile flashFireBoosted    = bit {compiled['volatile_flash_fire_boosted_bit']}",
        f" *   volatile transformed        = bit {compiled['volatile_transformed_bit']}",
        f" *   volatile boosterEnergyActivated = bit {compiled['volatile_booster_energy_activated_bit']}",
        f" *   volatile paradoxBoostedStat = bit {compiled['volatile_paradox_boosted_stat_bit']} width {compiled['volatile_paradox_boosted_stat_width']}",
        f" *   volatile vesselOfRuin       = bit {compiled['volatile_vessel_of_ruin_bit']}",
        f" *   volatile swordOfRuin        = bit {compiled['volatile_sword_of_ruin_bit']}",
        f" *   volatile tabletsOfRuin      = bit {compiled['volatile_tablets_of_ruin_bit']}",
        f" *   volatile beadsOfRuin        = bit {compiled['volatile_beads_of_ruin_bit']}",
        f" *   volatile embargo            = bit {compiled['volatile_embargo_bit']}",
        f" *   volatile metronomeItemCounter = bit {compiled['volatile_metronome_item_counter_bit']} width {compiled['volatile_metronome_item_counter_width']}",
        f" *   volatile transformedMonSpecies = bit {compiled['volatile_transformed_mon_species_bit']} width {compiled['volatile_transformed_mon_species_width']} (NUM_SPECIES={compiled['num_species']})",
        f" *   volatile read window         = {compiled['volatile_window_bytes']} bytes",
        f" *   BattleStruct.gimmick         = {compiled['gimmick_offset']}",
        f" *   BattleStruct.eventState      = {compiled['battle_struct_event_state_offset']}",
        f" *   EventStates.switchIn         = bit {compiled['event_state_switch_in_bit']} width {compiled['event_state_switch_in_width']}",
        f" *   BattleStruct.battlerState    = {compiled['battle_struct_battler_state_offset']}",
        f" *   BattleStruct.monToSwitchIntoId = {compiled['battle_struct_mon_to_switch_into_id_offset']}",
        f" *   sizeof(struct BattlerState)  = {compiled['battler_state_size']}",
        f" *   BattlerState.switchIn        = bit {compiled['battler_state_switch_in_bit']}",
        f" *   BattlerState.isFirstTurn     = bit {compiled['battler_state_is_first_turn_bit']} width {compiled['battler_state_is_first_turn_width']}",
        f" *   SWITCH_IN_EVENTS_COUNT      = {compiled['switch_in_events_count']}",
        f" *   BattleGimmickData.activeGimmick = {compiled['active_gimmick_offset']}",
        f" *   BattleGimmickData.usableGimmick = {compiled['usable_gimmick_offset']}",
        f" *   BattleGimmickData.playerSelect = {compiled['player_select_offset']}",
        f" *   BattleStruct.supremeOverlordCounter = {compiled['supreme_overlord_counter_offset']} stride {compiled['supreme_overlord_counter_stride']} count {compiled['supreme_overlord_counter_count']}",
        f" *   gBattleMainFunc (IWRAM)      = 0x{pins['battle_main_func_gba_address']:08X}",
        f" *   action-selection callback  = 0x{pins['action_selection_func_ptr']:08X}",
        f" *   RunTurnActionsFunctions   = 0x{pins['run_turn_actions_func_ptr']:08X}",
        *[f" *   {name} (IWRAM/EWRAM) = 0x{address:08X}" for name, address in TURN_ORDER_GLOBAL_ADDRESSES.items()],
        "",
        " * Source and official-symbol cross-check:",
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
        f"#define HNS_LIVE_BP_PERSONALITY_OFFSET {personality_offset}",
        f"#define HNS_LIVE_BP_PERSONALITY_SIZE {compiled['personality_size']}",
        f"#define HNS_LIVE_BP_VOLATILES_OFFSET {volatiles_offset}",
        f"#define HNS_LIVE_BP_VOLATILE_ELECTRIFIED_BIT {compiled['volatile_electrified_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_GLAIVE_RUSH_BIT {compiled['volatile_glaive_rush_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_MINIMIZE_BIT {compiled['volatile_minimize_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_SEMI_INVULNERABLE_BIT {compiled['volatile_semi_invulnerable_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_SEMI_INVULNERABLE_WIDTH {compiled['volatile_semi_invulnerable_width']}",
        f"#define HNS_LIVE_BP_VOLATILE_CHARGE_TIMER_BIT {compiled['volatile_charge_timer_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_CHARGE_TIMER_WIDTH {compiled['volatile_charge_timer_width']}",
        f"#define HNS_LIVE_BP_VOLATILE_TAR_SHOT_BIT {compiled['volatile_tar_shot_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_FORESIGHT_BIT {compiled['volatile_foresight_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_MIRACLE_EYE_BIT {compiled['volatile_miracle_eye_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_ROOT_BIT {compiled['volatile_root_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_SMACK_DOWN_BIT {compiled['volatile_smack_down_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_TELEKINESIS_BIT {compiled['volatile_telekinesis_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_MAGNET_RISE_BIT {compiled['volatile_magnet_rise_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_GASTRO_ACID_BIT {compiled['volatile_gastro_acid_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_ROOST_ACTIVE_BIT {compiled['volatile_roost_active_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_SUBSTITUTE_BIT {compiled['volatile_substitute_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_ENDURED_BIT {compiled['volatile_endured_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_SLOW_START_TIMER_BIT {compiled['volatile_slow_start_timer_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_SLOW_START_TIMER_WIDTH {compiled['volatile_slow_start_timer_width']}",
        f"#define HNS_LIVE_BP_VOLATILE_FLASH_FIRE_BOOSTED_BIT {compiled['volatile_flash_fire_boosted_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_TRANSFORMED_BIT {compiled['volatile_transformed_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_BOOSTER_ENERGY_ACTIVATED_BIT {compiled['volatile_booster_energy_activated_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_PARADOX_BOOSTED_STAT_BIT {compiled['volatile_paradox_boosted_stat_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_PARADOX_BOOSTED_STAT_WIDTH {compiled['volatile_paradox_boosted_stat_width']}",
        f"#define HNS_LIVE_BP_VOLATILE_VESSEL_OF_RUIN_BIT {compiled['volatile_vessel_of_ruin_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_SWORD_OF_RUIN_BIT {compiled['volatile_sword_of_ruin_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_TABLETS_OF_RUIN_BIT {compiled['volatile_tablets_of_ruin_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_BEADS_OF_RUIN_BIT {compiled['volatile_beads_of_ruin_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_RECHARGE_TIMER_BIT {compiled['volatile_recharge_timer_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_RECHARGE_TIMER_WIDTH {compiled['volatile_recharge_timer_width']}",
        f"#define HNS_LIVE_LOCKED_MOVE_STRIDE {compiled['locked_move_stride']}",
        f"#define HNS_LIVE_CHOSEN_MOVE_STRIDE {compiled['chosen_move_stride']}",
        f"#define HNS_LIVE_MOVES_COUNT {compiled['moves_count']}",
        f"#define HNS_LIVE_MOVE_BEAK_BLAST {compiled['move_beak_blast']}",
        f"#define HNS_LIVE_PROTECTED_BIT {compiled['protected_bit']}",
        f"#define HNS_LIVE_PROTECTED_WIDTH {compiled['protected_width']}",
        f"#define HNS_LIVE_BP_VOLATILE_ROLLOUT_TIMER_BIT {compiled['volatile_rollout_timer_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_ROLLOUT_TIMER_WIDTH {compiled['volatile_rollout_timer_width']}",
        f"#define HNS_LIVE_BP_VOLATILE_DEFENSE_CURL_BIT {compiled['volatile_defense_curl_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_MULTIPLE_TURNS_BIT {compiled['volatile_multiple_turns_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_HEAL_BLOCK_BIT {compiled['volatile_heal_block_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_EMBARGO_BIT {compiled['volatile_embargo_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_METRONOME_ITEM_COUNTER_BIT {compiled['volatile_metronome_item_counter_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_METRONOME_ITEM_COUNTER_WIDTH {compiled['volatile_metronome_item_counter_width']}",
        f"#define HNS_LIVE_BP_VOLATILE_TRANSFORMED_MON_SPECIES_BIT {compiled['volatile_transformed_mon_species_bit']}",
        f"#define HNS_LIVE_BP_VOLATILE_TRANSFORMED_MON_SPECIES_WIDTH {compiled['volatile_transformed_mon_species_width']}",
        *[f"#define HNS_LIVE_DOUBLES_{key.upper()} {compiled[key]}" for key in
          ("helping_hand_bit", "helping_hand_width", "follow_me_bit", "follow_me_width", "mold_breaker_bit", "pledge_bit", "protect_size", "side_timer_size", "battle_type_mask")],
        f"#define HNS_LIVE_NUM_SPECIES {compiled['num_species']}",
        f"#define HNS_LIVE_BP_VOLATILE_WINDOW_BYTES {compiled['volatile_window_bytes']}",
        f"#define HNS_LIVE_BATTLE_STRUCT_GIMMICK_OFFSET {compiled['gimmick_offset']}",
        f"#define HNS_LIVE_BATTLE_STRUCT_EVENT_STATE_OFFSET {compiled['battle_struct_event_state_offset']}",
        f"#define HNS_LIVE_EVENT_STATE_SWITCH_IN_BIT {compiled['event_state_switch_in_bit']}",
        f"#define HNS_LIVE_EVENT_STATE_SWITCH_IN_WIDTH {compiled['event_state_switch_in_width']}",
        f"#define HNS_LIVE_BATTLE_STRUCT_BATTLER_STATE_OFFSET {compiled['battle_struct_battler_state_offset']}",
        f"#define HNS_LIVE_BATTLE_STRUCT_MON_TO_SWITCH_INTO_ID_OFFSET {compiled['battle_struct_mon_to_switch_into_id_offset']}",
        f"#define HNS_LIVE_BATTLER_STATE_SIZE {compiled['battler_state_size']}",
        f"#define HNS_LIVE_BATTLER_STATE_SWITCH_IN_BIT {compiled['battler_state_switch_in_bit']}",
        f"#define HNS_LIVE_BATTLER_STATE_IS_FIRST_TURN_BIT {compiled['battler_state_is_first_turn_bit']}",
        f"#define HNS_LIVE_BATTLER_STATE_IS_FIRST_TURN_WIDTH {compiled['battler_state_is_first_turn_width']}",
        f"#define HNS_LIVE_MAX_BATTLERS_COUNT {compiled['max_battlers_count']}",
        f"#define HNS_LIVE_SWITCH_IN_EVENTS_COUNT {compiled['switch_in_events_count']}",
        f"#define HNS_LIVE_BATTLE_MAIN_FUNC_GBA_ADDRESS 0x{pins['battle_main_func_gba_address']:08X}u",
        f"#define HNS_LIVE_MAIN_CALLBACK1_OFFSET {compiled['main_callback1_offset']}",
        f"#define HNS_LIVE_BATTLE_MAIN_CB1_FUNC_PTR 0x{PHASE_EVIDENCE['functions']['BattleMainCB1']['releaseAddress'] | 1:08X}u",
        f"#define HNS_LIVE_ACTION_SELECTION_FUNC_PTR 0x{pins['action_selection_func_ptr']:08X}u",
        f"#define HNS_LIVE_RUN_TURN_ACTIONS_FUNC_PTR 0x{pins['run_turn_actions_func_ptr']:08X}u",
        f"#define HNS_LIVE_BATTLE_GIMMICK_ACTIVE_OFFSET {compiled['active_gimmick_offset']}",
        f"#define HNS_LIVE_BATTLE_GIMMICK_USABLE_OFFSET {compiled['usable_gimmick_offset']}",
        f"#define HNS_LIVE_BATTLE_GIMMICK_PLAYER_SELECT_OFFSET {compiled['player_select_offset']}",
        f"#define HNS_LIVE_BATTLE_GIMMICK_SIDE_COUNT {compiled['gimmick_side_count']}",
        f"#define HNS_LIVE_BATTLE_GIMMICK_PARTY_COUNT {compiled['gimmick_party_count']}",
        f"#define HNS_LIVE_BATTLE_GIMMICK_COUNT {compiled['gimmick_count']}",
        f"#define HNS_LIVE_GIMMICK_DYNAMAX_VALUE {compiled['gimmick_dynamax']}",
        f"#define HNS_LIVE_NUM_STATS {compiled['num_stats']}",
        f"#define HNS_LIVE_BATTLE_STRUCT_SUPREME_OVERLORD_COUNTER_OFFSET {compiled['supreme_overlord_counter_offset']}",
        f"#define HNS_LIVE_SUPREME_OVERLORD_COUNTER_STRIDE {compiled['supreme_overlord_counter_stride']}",
        f"#define HNS_LIVE_SUPREME_OVERLORD_COUNTER_COUNT {compiled['supreme_overlord_counter_count']}",
        f"#define HNS_LIVE_B_ACTION_EXEC_SCRIPT {compiled['b_action_exec_script']}",
        f"#define HNS_LIVE_BP_VOLATILE_NEUTRALIZING_GAS_BIT {compiled['volatile_neutralizing_gas_bit']}",
        f"#define HNS_LIVE_B_ACTION_USE_MOVE {compiled['b_action_use_move']}",
        *[f"#define HNS_LIVE_{name.upper()}_GBA_ADDRESS 0x{address:08X}u" for name, address in TURN_ORDER_GLOBAL_ADDRESSES.items()],
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
        "#if HNS_LIVE_BP_PERSONALITY_OFFSET + HNS_LIVE_BP_PERSONALITY_SIZE > HNS_BATTLE_POKEMON_SIZEOF",
        "#error \"BattlePokemon personality field exceeds the compiled struct size\"",
        "#endif",
        "#if HNS_LIVE_BP_VOLATILES_OFFSET + HNS_LIVE_BP_VOLATILE_WINDOW_BYTES > HNS_BATTLE_POKEMON_SIZEOF",
        "#error \"BattlePokemon volatiles read window exceeds the compiled struct size\"",
        "#endif",
        "#if HNS_LIVE_BP_VOLATILE_ELECTRIFIED_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || "
        "HNS_LIVE_BP_VOLATILE_GLAIVE_RUSH_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || "
        "HNS_LIVE_BP_VOLATILE_MINIMIZE_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || "
        "HNS_LIVE_BP_VOLATILE_SEMI_INVULNERABLE_BIT + HNS_LIVE_BP_VOLATILE_SEMI_INVULNERABLE_WIDTH "
        ">= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || "
        "HNS_LIVE_BP_VOLATILE_CHARGE_TIMER_BIT + HNS_LIVE_BP_VOLATILE_CHARGE_TIMER_WIDTH "
        ">= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || "
        "HNS_LIVE_BP_VOLATILE_TAR_SHOT_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || "
        "HNS_LIVE_BP_VOLATILE_FORESIGHT_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || "
        "HNS_LIVE_BP_VOLATILE_MIRACLE_EYE_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || "
        "HNS_LIVE_BP_VOLATILE_ROOT_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || "
        "HNS_LIVE_BP_VOLATILE_SMACK_DOWN_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || "
        "HNS_LIVE_BP_VOLATILE_TELEKINESIS_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || "
        "HNS_LIVE_BP_VOLATILE_MAGNET_RISE_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || "
        "HNS_LIVE_BP_VOLATILE_GASTRO_ACID_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || "
        "HNS_LIVE_BP_VOLATILE_ROOST_ACTIVE_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || "
        "HNS_LIVE_BP_VOLATILE_SUBSTITUTE_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || "
        "HNS_LIVE_BP_VOLATILE_ENDURED_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || "
        "HNS_LIVE_BP_VOLATILE_SLOW_START_TIMER_BIT + HNS_LIVE_BP_VOLATILE_SLOW_START_TIMER_WIDTH > 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || "
        "HNS_LIVE_BP_VOLATILE_FLASH_FIRE_BOOSTED_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || "
        "HNS_LIVE_BP_VOLATILE_TRANSFORMED_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || "
        "HNS_LIVE_BP_VOLATILE_BOOSTER_ENERGY_ACTIVATED_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || "
        "HNS_LIVE_BP_VOLATILE_PARADOX_BOOSTED_STAT_BIT + HNS_LIVE_BP_VOLATILE_PARADOX_BOOSTED_STAT_WIDTH > 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || "
        "HNS_LIVE_BP_VOLATILE_VESSEL_OF_RUIN_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || "
        "HNS_LIVE_BP_VOLATILE_SWORD_OF_RUIN_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || "
        "HNS_LIVE_BP_VOLATILE_TABLETS_OF_RUIN_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || "
        "HNS_LIVE_BP_VOLATILE_BEADS_OF_RUIN_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || "
        "HNS_LIVE_BP_VOLATILE_ROLLOUT_TIMER_BIT + HNS_LIVE_BP_VOLATILE_ROLLOUT_TIMER_WIDTH > 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || "
        "HNS_LIVE_BP_VOLATILE_RECHARGE_TIMER_BIT + HNS_LIVE_BP_VOLATILE_RECHARGE_TIMER_WIDTH > 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || "
        "HNS_LIVE_BP_VOLATILE_DEFENSE_CURL_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || "
        "HNS_LIVE_BP_VOLATILE_MULTIPLE_TURNS_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || "
        "HNS_LIVE_BP_VOLATILE_EMBARGO_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || "
        "HNS_LIVE_BP_VOLATILE_METRONOME_ITEM_COUNTER_BIT + HNS_LIVE_BP_VOLATILE_METRONOME_ITEM_COUNTER_WIDTH > 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || "
        "HNS_LIVE_BP_VOLATILE_TRANSFORMED_MON_SPECIES_BIT + HNS_LIVE_BP_VOLATILE_TRANSFORMED_MON_SPECIES_WIDTH > 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES",
        "#error \"an exported volatile bit exceeds the generated read window\"",
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
            "main_callback1_offset": scalar(blob, base_addr, syms, "ddx_main_callback1_offset"),
            "sizeof_volatiles": scalar(blob, base_addr, syms, "ddx_sizeof_volatiles"),
            "hp_offset": scalar(blob, base_addr, syms, "ddx_hp_offset"),
            "hp_size": scalar(blob, base_addr, syms, "ddx_hp_size"),
            "personality_offset": scalar(blob, base_addr, syms, "ddx_personality_offset"),
            "personality_size": scalar(blob, base_addr, syms, "ddx_personality_size"),
            "max_hp_offset": scalar(blob, base_addr, syms, "ddx_max_hp_offset"),
            "max_hp_size": scalar(blob, base_addr, syms, "ddx_max_hp_size"),
            "status_offset": scalar(blob, base_addr, syms, "ddx_status_offset"),
            "status_size": scalar(blob, base_addr, syms, "ddx_status_size"),
            "volatiles_offset": scalar(blob, base_addr, syms, "ddx_volatiles_offset"),
            "volatile_electrified_bit": single_bit(blob, base_addr, syms, "ddx_v_electrified"),
            "volatile_glaive_rush_bit": single_bit(blob, base_addr, syms, "ddx_v_glaive_rush"),
            "volatile_minimize_bit": single_bit(blob, base_addr, syms, "ddx_v_minimize"),
            "volatile_tar_shot_bit": single_bit(blob, base_addr, syms, "ddx_v_tar_shot"),
            "volatile_foresight_bit": single_bit(blob, base_addr, syms, "ddx_v_foresight"),
            "volatile_miracle_eye_bit": single_bit(blob, base_addr, syms, "ddx_v_miracle_eye"),
            "volatile_root_bit": single_bit(blob, base_addr, syms, "ddx_v_root"),
            "volatile_smack_down_bit": single_bit(blob, base_addr, syms, "ddx_v_smack_down"),
            "volatile_telekinesis_bit": single_bit(blob, base_addr, syms, "ddx_v_telekinesis"),
            "volatile_magnet_rise_bit": single_bit(blob, base_addr, syms, "ddx_v_magnet_rise"),
            "volatile_gastro_acid_bit": single_bit(blob, base_addr, syms, "ddx_v_gastro_acid"),
            "volatile_roost_active_bit": single_bit(blob, base_addr, syms, "ddx_v_roost_active"),
            "volatile_substitute_bit": single_bit(blob, base_addr, syms, "ddx_v_substitute"),
            "volatile_endured_bit": single_bit(blob, base_addr, syms, "ddx_v_endured"),
            "gimmick_offset": scalar(blob, base_addr, syms, "ddx_gimmick_offset"),
            "usable_gimmick_offset": scalar(blob, base_addr, syms, "ddx_usable_gimmick_offset"),
            "player_select_offset": scalar(blob, base_addr, syms, "ddx_player_select_offset"),
            "supreme_overlord_counter_offset": scalar(blob, base_addr, syms, "ddx_battle_struct_supreme_overlord_counter_offset"),
            "supreme_overlord_counter_stride": scalar(blob, base_addr, syms, "ddx_supreme_overlord_counter_stride"),
            "supreme_overlord_counter_count": scalar(blob, base_addr, syms, "ddx_supreme_overlord_counter_count"),
            "b_action_exec_script": scalar(blob, base_addr, syms, "ddx_b_action_exec_script"),
            "volatile_neutralizing_gas_bit": single_bit(blob, base_addr, syms, "ddx_v_neutralizing_gas"),
            "volatile_heal_block_bit": single_bit(blob, base_addr, syms, "ddx_v_heal_block"),
            "volatile_embargo_bit": single_bit(blob, base_addr, syms, "ddx_v_embargo"),
            "num_species": scalar(blob, base_addr, syms, "ddx_num_species"),
            "b_action_use_move": scalar(blob, base_addr, syms, "ddx_b_action_use_move"),
            "battle_struct_event_state_offset": scalar(blob, base_addr, syms, "ddx_battle_struct_event_state_offset"),
            "battle_struct_battler_state_offset": scalar(blob, base_addr, syms, "ddx_battle_struct_battler_state_offset"),
            "battle_struct_mon_to_switch_into_id_offset": scalar(blob, base_addr, syms, "ddx_battle_struct_mon_to_switch_into_id_offset"),
            "battler_state_size": scalar(blob, base_addr, syms, "ddx_battler_state_size"),
            "max_battlers_count": scalar(blob, base_addr, syms, "ddx_max_battlers_count"),
            "switch_in_events_count": scalar(blob, base_addr, syms, "ddx_switch_in_events_count"),
            "active_gimmick_offset": scalar(blob, base_addr, syms, "ddx_active_gimmick_offset"),
            "gimmick_side_count": scalar(blob, base_addr, syms, "ddx_gimmick_side_count"),
            "gimmick_party_count": scalar(blob, base_addr, syms, "ddx_gimmick_party_count"),
            "gimmick_count": scalar(blob, base_addr, syms, "ddx_gimmick_count"),
            "gimmick_dynamax": scalar(blob, base_addr, syms, "ddx_gimmick_dynamax"),
            "num_stats": scalar(blob, base_addr, syms, "ddx_num_stats"),
        }
        semibit, semiwidth = multi_bit(blob, base_addr, syms, "ddx_v_semi_invulnerable")
        for key, symbol in (("helping_hand", "ddx_helping_hand"), ("follow_me", "ddx_follow_me")):
            compiled[key + "_bit"], compiled[key + "_width"] = multi_bit(blob, base_addr, syms, symbol)
        compiled["mold_breaker_bit"] = single_bit(blob, base_addr, syms, "ddx_mold_breaker")
        compiled["pledge_bit"] = single_bit(blob, base_addr, syms, "ddx_pledge")
        for key in ("protect_size", "side_timer_size", "battle_type_mask"):
            compiled[key] = scalar(blob, base_addr, syms, "ddx_" + key)
        compiled["volatile_semi_invulnerable_bit"] = semibit
        compiled["volatile_semi_invulnerable_width"] = semiwidth
        chargebit, chargewidth = multi_bit(blob, base_addr, syms, "ddx_v_charge_timer")
        compiled["volatile_charge_timer_bit"] = chargebit
        compiled["volatile_charge_timer_width"] = chargewidth
        event_bit, event_width = multi_bit(blob, base_addr, syms, "ddx_event_switch_in_all")
        compiled["event_state_switch_in_bit"] = event_bit
        compiled["event_state_switch_in_width"] = event_width
        compiled["battler_state_switch_in_bit"] = single_bit(
            blob, base_addr, syms, "ddx_battler_switch_in"
        )
        first_turn_bit, first_turn_width = multi_bit(blob, base_addr, syms, "ddx_battler_first_turn_two")
        compiled["battler_state_is_first_turn_bit"] = first_turn_bit
        compiled["battler_state_is_first_turn_width"] = first_turn_width
        if first_turn_width != 2:
            fail(f"BattlerState.isFirstTurn probe found width {first_turn_width}, expected 2")
        for field, probe in (
            ("volatile_defense_curl_bit", "ddx_v_defense_curl"),
            ("volatile_multiple_turns_bit", "ddx_v_multiple_turns"),
            ("volatile_flash_fire_boosted_bit", "ddx_v_flash_fire_boosted"),
            ("volatile_transformed_bit", "ddx_v_transformed"),
            ("volatile_booster_energy_activated_bit", "ddx_v_booster_energy_activated"),
            ("volatile_vessel_of_ruin_bit", "ddx_v_vessel_of_ruin"),
            ("volatile_sword_of_ruin_bit", "ddx_v_sword_of_ruin"),
            ("volatile_tablets_of_ruin_bit", "ddx_v_tablets_of_ruin"),
            ("volatile_beads_of_ruin_bit", "ddx_v_beads_of_ruin"),
        ):
            compiled[field] = single_bit(blob, base_addr, syms, probe)
        compiled["volatile_recharge_timer_bit"], compiled["volatile_recharge_timer_width"] = multi_bit(blob, base_addr, syms, "ddx_v_recharge_timer")
        compiled["chosen_move_stride"] = scalar(blob, base_addr, syms, "ddx_chosen_move_stride")
        compiled["moves_count"] = scalar(blob, base_addr, syms, "ddx_moves_count")
        compiled["move_beak_blast"] = scalar(blob, base_addr, syms, "ddx_move_beak_blast")
        compiled["protected_bit"], compiled["protected_width"] = multi_bit(blob, base_addr, syms, "ddx_protected")
        if compiled["chosen_move_stride"] != 2 or compiled["protected_bit"] != 0 or compiled["protected_width"] != 7:
            fail("Changed chosen move / protect domain")
        compiled["locked_move_stride"] = scalar(blob, base_addr, syms, "ddx_locked_move_stride")
        if compiled["locked_move_stride"] != 2:
            fail("gLockedMoves element width changed")
        compiled["volatile_rollout_timer_bit"], compiled["volatile_rollout_timer_width"] = multi_bit(blob, base_addr, syms, "ddx_v_rollout_timer")
        slow_start_bit, slow_start_width = multi_bit(blob, base_addr, syms, "ddx_v_slow_start_timer")
        compiled["volatile_slow_start_timer_bit"] = slow_start_bit
        compiled["volatile_slow_start_timer_width"] = slow_start_width
        paradox_bit, paradox_width = multi_bit(blob, base_addr, syms, "ddx_v_paradox_boosted_stat")
        compiled["volatile_paradox_boosted_stat_bit"] = paradox_bit
        compiled["volatile_paradox_boosted_stat_width"] = paradox_width
        metronome_bit, metronome_width = multi_bit(blob, base_addr, syms, "ddx_v_metronome_item_counter")
        compiled["volatile_metronome_item_counter_bit"] = metronome_bit
        compiled["volatile_metronome_item_counter_width"] = metronome_width
        transformed_species_bit, transformed_species_width = multi_bit(
            blob, base_addr, syms, "ddx_v_transformed_mon_species"
        )
        compiled["volatile_transformed_mon_species_bit"] = transformed_species_bit
        compiled["volatile_transformed_mon_species_width"] = transformed_species_width
        if single_bit(blob, base_addr, syms, "ddx_event_switch_in_one") != event_bit:
            fail("EventStates.switchIn bit probe disagrees between value 1 and full-width probe")
        # Read window: enough bytes to cover the highest exported volatile bit. Reading only
        # the first 10 bytes was sufficient for the original four bits but silently truncated
        # `tarShot` (and any later damage-relevant volatile), so the window is derived from the
        # bit positions the reader actually consumes rather than hand-maintained.
        volatile_last_bits = [
            compiled["volatile_recharge_timer_bit"] + compiled["volatile_recharge_timer_width"] - 1,
            compiled["volatile_defense_curl_bit"], compiled["volatile_multiple_turns_bit"],
            compiled["volatile_rollout_timer_bit"] + compiled["volatile_rollout_timer_width"] - 1,
            compiled["volatile_electrified_bit"],
            compiled["volatile_glaive_rush_bit"],
            compiled["volatile_minimize_bit"],
            compiled["volatile_semi_invulnerable_bit"] + compiled["volatile_semi_invulnerable_width"] - 1,
            compiled["volatile_charge_timer_bit"] + compiled["volatile_charge_timer_width"] - 1,
            compiled["volatile_tar_shot_bit"],
            compiled["volatile_foresight_bit"],
            compiled["volatile_miracle_eye_bit"],
            compiled["volatile_root_bit"],
            compiled["volatile_smack_down_bit"],
            compiled["volatile_telekinesis_bit"],
            compiled["volatile_magnet_rise_bit"],
            compiled["volatile_gastro_acid_bit"],
            compiled["volatile_roost_active_bit"],
            compiled["volatile_substitute_bit"],
            compiled["volatile_endured_bit"],
            compiled["volatile_slow_start_timer_bit"] + compiled["volatile_slow_start_timer_width"] - 1,
            compiled["volatile_flash_fire_boosted_bit"],
            compiled["volatile_transformed_bit"],
            compiled["volatile_booster_energy_activated_bit"],
            compiled["volatile_paradox_boosted_stat_bit"] + compiled["volatile_paradox_boosted_stat_width"] - 1,
            compiled["volatile_vessel_of_ruin_bit"],
            compiled["volatile_sword_of_ruin_bit"],
            compiled["volatile_tablets_of_ruin_bit"],
            compiled["volatile_beads_of_ruin_bit"],
            compiled["volatile_neutralizing_gas_bit"],
            compiled["volatile_heal_block_bit"],
            compiled["volatile_embargo_bit"],
            compiled["volatile_metronome_item_counter_bit"] + compiled["volatile_metronome_item_counter_width"] - 1,
            compiled["volatile_transformed_mon_species_bit"] + compiled["volatile_transformed_mon_species_width"] - 1,
        ]
        compiled["volatile_window_bytes"] = max(volatile_last_bits) // 8 + 1

    enum_text = (upstream_path / "include/constants/battle.h").read_text()
    semi_members = re.search(r"enum SemiInvulnerableState\s*\{(.*?)\}", enum_text, re.S)
    expected = ["STATE_NONE", "STATE_UNDERGROUND", "STATE_UNDERWATER", "STATE_ON_AIR",
                "STATE_PHANTOM_FORCE", "STATE_SKY_DROP", "STATE_COMMANDER", "SEMI_INVULNERABLE_COUNT"]
    if semi_members is None or [x.strip() for x in semi_members[1].split(",")] != expected:
        fail("SemiInvulnerableState domain changed; review execution authority")
    semi_domain = dict(zip(expected, range(len(expected))))
    generated = render_header(arm_gcc, compiled, pins, None)
    generated = generated.replace("#endif /* DUALDEX_HNS_LIVE_BATTLE_LAYOUT_GEN_H */", "".join(f"#define HNS_LIVE_{name} {value}\n" for name, value in semi_domain.items()) + "\n#endif /* DUALDEX_HNS_LIVE_BATTLE_LAYOUT_GEN_H */")
    out_path = Path(args.output) if args.output else (repo_root / OUTPUT_HEADER)
    domains = {"RECHARGE_TIMER_MAX": (1 << compiled["volatile_recharge_timer_width"]) - 1, "RECHARGE_TIMER_WIDTH": compiled["volatile_recharge_timer_width"], "ROLLOUT_TIMER_MAX": (1 << compiled["volatile_rollout_timer_width"]) - 1, "ROLLOUT_TIMER_WIDTH": compiled["volatile_rollout_timer_width"], "NUM_STATS": compiled["num_stats"], "GIMMICKS_COUNT": compiled["gimmick_count"],
               "GIMMICK_DYNAMAX": compiled["gimmick_dynamax"],
               "METRONOME_ITEM_COUNTER_MAX": (1 << compiled["volatile_metronome_item_counter_width"]) - 1,
               "METRONOME_ITEM_COUNTER_WIDTH": compiled["volatile_metronome_item_counter_width"],
               "TRANSFORMED_MON_SPECIES_WIDTH": compiled["volatile_transformed_mon_species_width"],
               "SPECIES_COUNT": compiled["num_species"],
               "SLOW_START_MAX": (1 << compiled["volatile_slow_start_timer_width"]) - 1,
               "FIRST_TURN_MAX": (1 << compiled["battler_state_is_first_turn_width"]) - 1}
    domains.update(semi_domain)
    kotlin = "// Generated by tools/hns-layout/generate_hns_live_battle_layout.py; do not edit.\n"
    kotlin += f"// Pinned source: {PINNED_COMMIT}\npackage com.dualdex.pokemon.hns\n\n"
    kotlin += "object HnsGroupDLayout {\n" + "".join(
        f"    const val {name} = {value}\n" for name, value in domains.items()) + "}\n"
    ability_source = (upstream_path / "src/data/abilities.h").read_text()
    enum_source = (upstream_path / "include/constants/abilities.h").read_text()
    unsuppressible = []
    for symbol, body in re.findall(r"\[(ABILITY_\w+)\]\s*=\s*\{(.*?)\n    \}", ability_source, re.S):
        match = re.search(r"\.cantBeSuppressed\s*=\s*([^,]+)", body)
        if not match:
            continue
        if match[1].strip() not in ("TRUE", "B_UPDATED_ABILITY_DATA >= GEN_7"):
            fail("unreviewed cantBeSuppressed expression")
        if match[1].strip() != "TRUE" and not re.search(r"#define B_UPDATED_ABILITY_DATA\s+GEN_LATEST", (upstream_path / "include/config/battle.h").read_text()):
            fail("updated ability metadata config changed")
        identity = re.search(rf"\b{symbol}\s*=\s*(\d+)", enum_source)
        if not identity:
            fail("unsuppressible identity missing")
        unsuppressible.append(int(identity[1]))
    if len(unsuppressible) != ability_source.count(".cantBeSuppressed"):
        fail("incomplete unsuppressible metadata extraction")
    doubles_kotlin = "// Generated by tools/hns-layout/generate_hns_live_battle_layout.py; do not edit.\n"
    doubles_kotlin += "package com.dualdex.pokemon.hns\n\nobject HnsDoublesLayout {\n"
    doubles_kotlin += "    val UNSUPPRESSIBLE_ABILITIES = setOf(" + ", ".join(map(str, sorted(unsuppressible))) + ")\n}\n"
    additional = {} if args.output else {
        repo_root / "app/src/main/java/com/dualdex/pokemon/hns/HnsDoublesLayout.kt": doubles_kotlin,
        repo_root / "tools/hns-layout/group_d_domains.json": json.dumps(domains, indent=2, sort_keys=True) + "\n",
        repo_root / "app/src/main/java/com/dualdex/pokemon/hns/HnsGroupDLayout.kt": kotlin,
    }
    if args.verify:
        if not out_path.is_file():
            fail(f"{out_path} does not exist; run without --verify to generate it")
        if out_path.read_text() != generated:
            fail(
                f"{out_path} differs from the pinned-source regeneration; "
                "the committed layout artifact is stale"
            )
        print(f"verified {out_path.relative_to(repo_root)}")
        for path, content in additional.items():
            if not path.is_file() or path.read_text() != content:
                fail(f"{path} is stale; regenerate the live layout")
        return
    out_path.write_text(generated)
    for path, content in additional.items():
        path.write_text(content)
    print(f"wrote {out_path.relative_to(repo_root)}")


if __name__ == "__main__":
    main()
