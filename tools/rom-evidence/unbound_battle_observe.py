#!/usr/bin/env python3
"""Exact-candidate, read-only Unbound battle-lifecycle diagnostics (trainer/double/faint/switch).

Composes `unbound_observe` (same pins, same paused read-twice snapshots, no RAM writes,
no cartridge saves) and adds: the CFRU gBattleTypeFlags bit names, a battler -> position/
side/flank/owner/party-slot map, live-battle signals (gMain.inBattle, gMain.callback2,
gBattleMainFunc), gAbsentBattlerFlags and gTrainerBattleOpponent_A. `--summary` turns a
private report into the committable numeric-only manifest (no names or text).
"""
import argparse
import json
from pathlib import Path
import struct
import sys

import rowe_observe as rowe
import unbound_observe as base
from ups_identity import read_local, require

HERE = Path(__file__).resolve().parent
# Public CFRU b637a27 names: BPRE.ld (gMain, gBattleMainFunc, gAbsentBattlerFlags, BattleMainCB2)
# and include/new/ram_locs_battle.h (gTrainerBattleOpponent_A). Diagnostic until runtime agrees.
LIFECYCLE_SYMBOLS = dict(gMain=0x030030f0, gBattleMainFunc=0x03004f84,
                         gAbsentBattlerFlags=0x02023d70, gTrainerBattleOpponent_A=0x020386ae)
MAIN_IN_BATTLE_BYTE, MAIN_IN_BATTLE_BIT = 0x439, 1   # struct Main: oamLoadDisabled:1, inBattle:1
BATTLE_MAIN_CB2 = 0x08011101                          # BattleMainCB2 | 1 (Thumb)
# CFRU include/constants/battle.h. Bits not listed here are reported numerically only.
TYPE_FLAG_BITS = {
    0x1: 'DOUBLE', 0x2: 'LINK', 0x4: 'IS_MASTER', 0x8: 'TRAINER', 0x10: 'OAK_TUTORIAL',
    0x20: 'LINK_ESTABLISHED', 0x40: 'MULTI', 0x80: 'SAFARI', 0x100: 'BATTLE_TOWER',
    0x200: 'OLD_MAN', 0x400: 'ROAMER', 0x800: 'EREADER_TRAINER', 0x1000: 'KYOGRE_GROUDON',
    0x2000: 'SCRIPTED_WILD_1', 0x4000: 'REGI', 0x8000: 'GHOST', 0x10000: 'POKE_DUDE',
    0x20000: 'SCRIPTED_WILD_2', 0x40000: 'LEGENDARY_FRLG', 0x80000: 'TRAINER_TOWER',
    0x200000: 'TWO_OPPONENTS', 0x400000: 'INGAME_PARTNER', 0x800000: 'RING_CHALLENGE',
    0x1000000: 'MOCK_BATTLE', 0x2000000: 'BATTLE_SANDS', 0x4000000: 'BATTLE_CIRCUS',
    0x8000000: 'BENJAMIN_BUTTERFREE', 0x10000000: 'CAMOMONS', 0x20000000: 'MEGA_BRAWL',
    0x40000000: 'DYNAMAX', 0x80000000: 'SHADOW_WARRIOR'}
POSITIONS = ('PLAYER_LEFT', 'OPPONENT_LEFT', 'PLAYER_RIGHT', 'OPPONENT_RIGHT')


def type_flag_names(flags):
    """Named CFRU bits of gBattleTypeFlags plus any unnamed residue."""
    names = [name for bit, name in sorted(TYPE_FLAG_BITS.items()) if flags & bit]
    rest = flags & ~sum(TYPE_FLAG_BITS)
    return dict(names=names, unnamed_bits=hex(rest))


def battler_map(count, positions, party_indexes, flags):
    """CFRU LoadPartyRange (src/battle_util.c) applied to observed battlers.

    Opponent-right uses enemy slots 3..5 in TWO_OPPONENTS (and MULTI) battles; player-right
    uses 3..5 with an INGAME_PARTNER. Everything else is a single 0..5 range.
    """
    require(count <= 4, 'battler count exceeds four')
    rows = []
    for n in range(count):
        pos, index = positions[n], party_indexes[n]
        require(pos < 4, 'battler position out of range')
        side = ('player', 'opponent')[pos & 1]
        split = (flags & 0x400000 if side == 'player' else flags & 0x200040)
        first = 3 if split and pos >> 1 else 0
        last = first + 3 if split else 6
        owner = side if not split else (
            ('player', 'partner') if side == 'player' else ('opponent_a', 'opponent_b'))[first == 3]
        rows.append(dict(battler=n, position=POSITIONS[pos], side=side, flank=('left', 'right')[pos >> 1],
                         owner=owner, party_index=index, slot_range=[first, last],
                         index_in_owner_range=first <= index < last))
    return rows


class BattleObserver(base.Observer):
    def party_snapshot(self, side):
        """Enemy side counts by the static-verified byte 0x0202402A, not the historical
        0x02024028 candidate (CFRU sLearningMoveTableID, which exceeds six in battle)."""
        if side != 'Enemy':
            return super().party_snapshot(side)
        read, count_address = self.engine.read, base.BATTLE_SYMBOLS['gEnemyPartyCount']
        base_address = base.SYMBOLS['gEnemyParty']
        count = read(count_address, 1)[0]
        data = read(base_address, 300) + read(base_address+300, 300)
        again, final = read(base_address, 300) + read(base_address+300, 300), read(count_address, 1)[0]
        require(count == final and data == again, 'incoherent paused enemy snapshot')
        require(count <= 6, 'enemy party count exceeds six')
        slots = [data[i*100:(i+1)*100] for i in range(6)]
        return data, dict(count=count, count_address=hex(count_address), sha256=rowe.digest(data),
                          slot_sha256=[rowe.digest(s) for s in slots])

    def lifecycle_snapshot(self):
        read, s = self.engine.read, LIFECYCLE_SYMBOLS
        u32 = lambda a: struct.unpack('<I', read(a, 4))[0]
        def grab():
            return dict(main_callback1=u32(s['gMain']), main_callback2=u32(s['gMain']+4),
                        main_in_battle=read(s['gMain']+MAIN_IN_BATTLE_BYTE, 1)[0] >> MAIN_IN_BATTLE_BIT & 1,
                        battle_main_func=u32(s['gBattleMainFunc']),
                        absent_battler_flags=read(s['gAbsentBattlerFlags'], 1)[0],
                        trainer_opponent_a=struct.unpack('<H', read(s['gTrainerBattleOpponent_A'], 2))[0])
        first, second = grab(), grab()
        require(first == second, 'incoherent paused lifecycle snapshot')
        first['callback2_is_battle_main'] = first['main_callback2'] == BATTLE_MAIN_CB2
        return first

    def observe(self, label, private):
        row = super().observe(label, private)
        battle = row['battle']
        row['lifecycle'] = self.lifecycle_snapshot()
        row['type_flags'] = type_flag_names(battle['flags'])
        if battle['count'] <= 4:
            row['battler_map'] = battler_map(battle['count'], battle['positions'],
                                             battle['party_indexes'], battle['flags'])
        return row


def inspect_rom(data):
    report = base.inspect_rom(data)
    refs = base.literal_references(data, dict(LIFECYCLE_SYMBOLS, BattleMainCB2=BATTLE_MAIN_CB2))
    return dict(report, lifecycle_literal_references=refs)


def repeat_comparison(first, second):
    """Run-to-run determinism: everything but screen hashes must match point by point."""
    def core(obs):
        return [(o['label'], o['frame'], o['battle'], o['lifecycle'], o['parties']['player']['sha256'],
                 o['parties']['enemy']['sha256']) for o in obs]
    a, b = first['observations'], second['observations']
    return dict(observations=len(b), battle_party_lifecycle_identical=core(a) == core(b),
                screen_hash_differences=[x['label'] for x, y in zip(a, b) if x['rgb565_sha256'] != y['rgb565_sha256']])


def summarize(report, report_sha256):
    """Numeric-only manifest rows: no nickname/OT bytes, no parser text fields."""
    require(report.get('status') == 'OBSERVED', 'report is not a successful observation')
    rows = []
    for obs in report['observations']:
        b = obs['battle']
        battlers = []
        for e in b['battlers']:
            f = e['fields']
            battlers.append(dict(battler=e['battler'], side=e['side'], party_index=e['party_index'],
                                 species_word=f['species_word'], level=f['level_byte'], hp=f['hp_word'],
                                 max_hp=f['max_hp_word'], pp=f['pp_bytes'], stat_stages=f['stat_stage_bytes'],
                                 status1=f['status1'], status2=f['status2'],
                                 matches_party_record=e.get('matches_party_record')))
        enemy = [dict(slot=s['slot'], species_word=s['fixed_raw_gaem']['species_word'], level=s['level_byte'],
                      hp=s['hp_word'], max_hp=s['stats_words']['max_hp'], checksum_word=s['checksum_word'],
                      production_accepted=s['production']['accepted'])
                 for s in b.get('enemy_box_nonzero_slots', [])]
        rows.append(dict(label=obs['label'], frame=obs['frame'], gBattleTypeFlags=hex(b['flags']),
                         type_flag_names=obs['type_flags']['names'], gBattlersCount=b['count'],
                         gActiveBattler=b['active'], gBattlerPartyIndexes=b['party_indexes'],
                         gBattlerPositions=b['positions'], gBattleOutcome=b['outcome'],
                         enemy_count_bytes=b['enemy_count_bytes'], gBattleMons_sha256=b['gBattleMons_sha256'],
                         lifecycle=obs['lifecycle'], battler_map=obs.get('battler_map', []),
                         battlers=battlers, enemy_records=enemy,
                         player_count=obs['parties']['player']['count'],
                         player_party_sha256=obs['parties']['player']['sha256'],
                         enemy_party_sha256=obs['parties']['enemy']['sha256'],
                         rgb565_sha256=obs['rgb565_sha256']))
    keys = ('observer_sha256', 'battle_observer_sha256', 'helper_sha256', 'identity_helper_sha256', 'movie_sha256')
    inputs = {k: report[k] for k in keys}
    inputs.update(rom_sha256=report['rom']['sha256'], core_sha256=report['core_sha256'],
                  adapter_binary_sha256=report['adapter_pin']['binary_sha256'],
                  adapter_source_revision=report['adapter_pin']['source_revision'],
                  bootstrap_sha256=report['bootstrap_input']['sha256'], bootstrap=report['bootstrap'],
                  private_report_sha256=report_sha256)
    static = report['static']
    return dict(schema_version=1, status='OBSERVED', support_verdict='NONE',
                release_label='NOT_VERIFIED (owner claim v2.1.1.1)',
                battle_addresses={k: hex(v) for k, v in base.BATTLE_SYMBOLS.items()},
                lifecycle_addresses={k: hex(v) for k, v in LIFECYCLE_SYMBOLS.items()},
                in_battle_bit=dict(byte=hex(LIFECYCLE_SYMBOLS['gMain'] + MAIN_IN_BATTLE_BYTE), bit=MAIN_IN_BATTLE_BIT),
                battle_main_cb2=hex(BATTLE_MAIN_CB2), inputs=inputs, static=dict(battle_spans=static['battle_spans'],
                                           battle_literal_references=static['battle_literal_references'],
                                           lifecycle_literal_references=static['lifecycle_literal_references']),
                timeline=rows)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--summary', type=Path, help='summarize this private report to stdout and exit')
    parser.add_argument('--repeat', type=Path, help='with --summary: a second report of the same movie')
    for name in ('rom', 'core', 'movie', 'output', 'private-dir'):
        parser.add_argument('--'+name, type=Path)
    bootstrap = parser.add_mutually_exclusive_group()
    bootstrap.add_argument('--battery-save', type=Path)
    bootstrap.add_argument('--initial-state', type=Path)
    args = parser.parse_args()
    if args.summary:
        raw = read_local(args.summary, 64*1024*1024)
        summary = summarize(json.loads(raw), rowe.digest(raw))
        if args.repeat:
            again = read_local(args.repeat, 64*1024*1024)
            summary['repeat_run'] = dict(repeat_comparison(json.loads(raw), json.loads(again)),
                                         private_report_sha256=rowe.digest(again))
        print(json.dumps(summary, sort_keys=True, indent=1))
        return 0
    if not all((args.rom, args.core, args.movie, args.output, args.private_dir)) or not (
            args.battery_save or args.initial_state):
        parser.error('--rom --core --movie --output --private-dir and a bootstrap input are required')
    library = HERE.parent / 'vanilla-runtime-probe/build/probe_host.so'
    tools = [Path(__file__), Path(base.__file__), HERE/'rowe_observe.py', HERE/'ups_identity.py', rowe.ADAPTER_PIN]
    inputs = [args.rom, args.core, args.movie, args.battery_save, args.initial_state, library] + tools
    if any(p and (p.resolve() == args.output.resolve() or
           (p.exists() and args.output.exists() and p.samefile(args.output))) for p in inputs):
        parser.error('output must not overwrite an input or tool')
    report = dict(schema_version=1, status='FAIL', runtime_layout='NOT_VERIFIED', observations=[])
    args.output.write_text(json.dumps(report)+'\n')  # invalidate stale success first
    host = None
    try:
        rom = read_local(args.rom, 32*1024*1024)
        report['rom'] = base.pinned(rom, base.ROM_SHA256, 32*1024*1024, 'ROM')
        rowe.require_private_directory(args.private_dir)
        report['static'] = inspect_rom(rom)
        report['release_label'] = 'NOT_VERIFIED'
        report['core_sha256'] = rowe.digest(read_local(args.core, 64*1024*1024))
        require(report['core_sha256'] == rowe.CORE_SHA256, 'diagnostic core identity mismatch')
        report['adapter_pin'] = rowe.verify_adapter(library)
        movie = read_local(args.movie, 65536)
        commands = base.parse_movie(movie.decode('ascii'))
        report.update(battle_observer_sha256=rowe.digest(Path(__file__).read_bytes()),
                      observer_sha256=rowe.digest(Path(base.__file__).read_bytes()),
                      helper_sha256=rowe.digest((HERE/'rowe_observe.py').read_bytes()),
                      identity_helper_sha256=rowe.digest((HERE/'ups_identity.py').read_bytes()),
                      movie_sha256=rowe.digest(movie),
                      lifecycle_addresses={k: hex(v) for k, v in LIFECYCLE_SYMBOLS.items()},
                      battle_addresses={k: hex(v) for k, v in base.BATTLE_SYMBOLS.items()})
        source = args.battery_save or args.initial_state
        saved = read_local(source, 16*1024*1024)
        is_battery = args.battery_save is not None
        report['bootstrap_input'] = base.pinned(
            saved, base.SAVE_SHA256 if is_battery else base.STATE_SHA256,
            131072 if is_battery else 528448, 'save' if is_battery else 'state')
        report['bootstrap'] = 'OWNER_BATTERY_COPY' if is_battery else 'OWNER_QUICKSAVE_COPY'
        args.private_dir.mkdir(parents=True, exist_ok=False, mode=0o700)
        staged_rom = args.private_dir/'cartridge.gba'
        staged_save = args.private_dir/('cartridge.sav' if is_battery else 'initial.state')
        staged_rom.write_bytes(rom)
        staged_save.write_bytes(saved)
        host = BattleObserver(library, args.core.resolve(), staged_rom)
        load = (host.engine.host.libretro_host_load_save_ram if is_battery
                else host.engine.host.libretro_host_load_state)
        report['bootstrap_accepted'] = bool(load(bytes(staged_save)))
        require(report['bootstrap_accepted'], 'bootstrap load failed')
        for command in commands:
            op = command[0]
            require(op in ('run', 'mash', 'observe'), 'battle movies use run/mash/observe only')
            if op == 'run':
                host.engine.run(*command[1:])
            elif op == 'mash':
                for _ in range(command[1]):
                    host.engine.run(1, command[2]); host.engine.run(90, 0)
            else:
                report['observations'].append(host.observe(command[1], args.private_dir))
        report['status'] = 'OBSERVED'
    except (ValueError, OSError, UnicodeError, KeyError, TypeError) as error:
        report['observations'] = []
        report['error'] = str(error)
        print('FAIL: '+str(error), file=sys.stderr)
    finally:
        if host:
            host.engine.host.libretro_host_cleanup()
        args.output.write_text(json.dumps(report, sort_keys=True, indent=2)+'\n')
    return 0 if report['status'] == 'OBSERVED' else 1


if __name__ == '__main__':
    sys.exit(main())
