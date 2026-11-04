#!/usr/bin/env python3
"""Exact-candidate, read-only Unbound diagnostics. Never a support verdict.

Uses the pinned ROWE developer adapter by composition. No scans, RAM pokes,
cartridge flushes or production discovery. Field interpretations are hypotheses;
the report keeps raw words, XOR/permutation alternatives and native results apart.
"""
import argparse
import ctypes as C
import itertools
import json
import os
from pathlib import Path
import struct
import sys

import rowe_observe as rowe
from ups_identity import identity, read_local, require

HERE = Path(__file__).resolve().parent
ROM_SHA256 = '7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7'
SAVE_SHA256 = 'a561cab2c9f4ecd32997d34a6f6b82ca592ec71f28d8713a16c9950e797e5236'
STATE_SHA256 = 'eb64974477c908ab5a46f52642047fd55db2ee02b7999c67191bff7615727482'
# Diagnostic candidates until exact-ROM instructions and runtime independently agree.
SYMBOLS = dict(gPlayerPartyCount=0x02024029, gPlayerParty=0x02024284,
               gEnemyPartyCount=0x02024028, gEnemyParty=0x0202402c,
               gSaveBlock1Ptr=0x03005008, gSaveBlock2Ptr=0x0300500c)
ORDERS = tuple(itertools.permutations('GAEM'))
STATS = ('max_hp', 'attack', 'defense', 'speed', 'sp_attack', 'sp_defense')
STATIC_SPANS = (
    (0x08040c3c, 0x08040c80, 'ba4c5c4bcb49d691a36cf16af8d40fe07fd98d648659541ebb6446b4451ce51a',
     'Byte count at 02024029; 0..5 loop indexes 02024284 by 100 bytes.'),
    (0x0803f8f8, 0x0803f940, '797f78ff087ec0110378b662bca0a9da368ef7b3a1c534cad0ebafc4b6f3f1d5',
     'Both former XOR loops read/write unchanged words; XOR instructions replaced by no-ops.'),
    (0x0803f940, 0x0803f9a0, 'd86a3796c2251582365a04bfef46c18fb3bf25923323d75b24fd7db116c74613',
     'Substructure lookup forces permutation index zero, independent of personality.'),
    (0x0803f9c8, 0x0803fa1c, '3930e832c0b09ad59505cbc288ec9ef1d83c29d24a2d53123c13b59343c960e8',
     'Zero-index substructure branch selects fixed growth/attacks/EVs/misc tails.'),
    (0x0803fbcc, 0x0803fbe8, 'df4070c6bbf3e2c84411cc64a45d0f481ab13ab20c1e5b735a4d73c3706e457c',
     'Substructure pointer tails return slot+20/2c/38/44 (hex).'),
    (0x0803fd44, 0x0803fdc2, '264f2fb0a0131ca646b8a256b2cee67a388e421b35c10d42c10fe215da75dcb9',
     'Field getter obtains four fixed pointers; checksum/decrypt block is no-ops and a skip branch.'),
    (0x0804002e, 0x080400c0, '96e0514115a3d0564e3cfdaddd6279c71075f62f4ed9f788b3467d0215b0308c',
     'Non-bad-egg species and item use full halfwords; moves use indexed halfwords; EVs use bytes.'),
)


def pinned(data, sha, size, label):
    require(len(data) == size and rowe.digest(data) == sha, label + ' identity mismatch')
    return identity(data)


def inspect_rom(data):
    """Recheck manually inspected exact-ROM spans; emits semantics, never code bytes.

    This is an identity-bound inspection record, not a general ARM disassembler
    or an executed-PC trace. See the evidence document for manual method/limits.
    """
    rom = pinned(data, ROM_SHA256, 32*1024*1024, 'ROM')
    spans = []
    for start, end, sha, meaning in STATIC_SPANS:
        require(rowe.digest(data[start-0x08000000:end-0x08000000]) == sha,
                'inspected ROM span mismatch')
        spans.append(dict(start=hex(start), end_exclusive=hex(end), sha256=sha, finding=meaning))
    return dict(status='VERIFIED', scope='STATIC_SELECTED_PATHS_ONLY', rom=rom, spans=spans,
                player_count_address=hex(SYMBOLS['gPlayerPartyCount']), player_count_width_bits=8,
                player_party_address=hex(SYMBOLS['gPlayerParty']), player_stride_bytes=100,
                substructures='FIXED_GAEM_UNENCRYPTED', species_load_width_bits=16,
                internal_species_id_mapping='NOT_VERIFIED', checksum_zero_runtime='NOT_VERIFIED',
                save_block_pointer_authority='NOT_VERIFIED', runtime_layout='NOT_VERIFIED')


def parse_movie(text):
    commands = rowe.parse_movie(text)
    require(len(commands) <= 4096 and sum(c[0] == 'observe' for c in commands) <= 64,
            'movie exceeds command/capture budget')
    require(all(c[0] != 'cartridge_save' for c in commands), 'cartridge writes forbidden')
    return commands


def sub_fields(data, order):
    """Selected raw GAEM hypothesis fields, with no ID translation or masking."""
    blocks = {name: data[i*12:(i+1)*12] for i, name in enumerate(order)}
    g, a, e, m = (blocks[n] for n in 'GAEM')
    iv = struct.unpack_from('<I', m, 4)[0]
    return dict(species_word=struct.unpack_from('<H', g)[0],
                item_word=struct.unpack_from('<H', g, 2)[0],
                experience_word=struct.unpack_from('<I', g, 4)[0],
                moves_words=list(struct.unpack_from('<4H', a)), pp_bytes=list(a[8:12]),
                ev_bytes=list(e[:6]), iv_word=iv,
                ivs_hypothesis=[(iv >> (5*i)) & 31 for i in range(6)],
                egg_bit_hypothesis=(iv >> 30) & 1, ability_bit31_hypothesis=iv >> 31)


def slot_fields(data):
    require(len(data) == 100, 'slot must be exactly 100 bytes')
    pid, otid = struct.unpack_from('<II', data)
    checksum = struct.unpack_from('<H', data, 28)[0]
    secure = data[32:80]
    xor = b''.join(struct.pack('<I', w ^ pid ^ otid) for (w,) in struct.iter_unpack('<I', secure))
    sums = [sum(struct.unpack('<24H', b)) & 65535 for b in (secure, xor)]
    order = ''.join(ORDERS[pid % 24])
    return dict(pid=pid, otid=otid, nickname_hex=data[8:18].hex(),
                ot_name_hex=data[20:27].hex(), checksum_word=checksum, pid_mod24=pid % 24,
                raw_sum16=sums[0], xor_sum16=sums[1],
                raw_checksum_matches=checksum == sums[0], xor_checksum_matches=checksum == sums[1],
                fixed_raw_gaem=sub_fields(secure, 'GAEM'),
                pid_order_raw=sub_fields(secure, order), pid_order_xor=sub_fields(xor, order),
                pid_order=order, level_byte=data[84], hp_word=struct.unpack_from('<H', data, 86)[0],
                stats_words=dict(zip(STATS, struct.unpack_from('<6H', data, 88))))


# Exact native ParsedPokemon ABI from the adapter-pinned header. The source pin
# is checked before CDLL. This calls the real parser, not a Python approximation.
class Parsed(C.Structure):
    _fields_ = [('is_valid', C.c_bool), ('is_empty', C.c_bool), ('pid', C.c_uint32),
                ('tid', C.c_uint16), ('sid', C.c_uint16), ('nickname', C.c_char*32),
                ('ot_name', C.c_char*32), ('species', C.c_uint16), ('held_item', C.c_uint16),
                ('level', C.c_uint8), ('nature', C.c_uint8), ('nature_name', C.c_void_p),
                ('is_shiny', C.c_bool), ('ability_slot', C.c_uint8), ('is_egg', C.c_bool),
                ('friendship', C.c_uint8), ('experience', C.c_uint32)] + [
                (name + '_' + suffix, C.c_uint8) for suffix in ('iv', 'ev')
                for name in ('hp', 'attack', 'defense', 'speed', 'sp_attack', 'sp_defense')] + [
                ('moves', C.c_uint16*4), ('pp', C.c_uint8*4), ('pp_bonuses', C.c_uint8*4),
                ('storage_layout', C.c_int), ('ability_slot_known', C.c_bool),
                ('ability_num', C.c_uint8), ('gigantamax_factor', C.c_bool),
                ('hidden_nature', C.c_uint8), ('hidden_nature_modifier', C.c_uint8),
                ('nature_modified', C.c_bool), ('shiny_value', C.c_uint16),
                ('shiny_modifier', C.c_uint8), ('shiny_state', C.c_int),
                ('current_hp', C.c_uint16)] + [(n, C.c_uint16) for n in STATS] + [
                ('status_condition', C.c_uint32)]


def native_fields(host, data):
    require(len(data) == 100, 'native input must be exactly 100 bytes')
    fn = host.pokemon_parse_single
    fn.argtypes, fn.restype = [C.c_void_p, C.c_bool, C.POINTER(Parsed)], C.c_bool
    raw, out = (C.c_ubyte*100).from_buffer_copy(data), Parsed()
    ok = bool(fn(raw, True, C.byref(out)))
    # Failed parser output is never presented as a usable Pokemon.
    if not ok:
        return dict(accepted=False)
    return dict(accepted=True, species=out.species, held_item=out.held_item,
                moves=list(out.moves), pp=list(out.pp), level=out.level, hp=out.current_hp,
                stats={n: getattr(out, n) for n in STATS}, ability_slot=out.ability_slot,
                ivs=[getattr(out, n+'_iv') for n in ('hp', *STATS[1:])],
                evs=[getattr(out, n+'_ev') for n in ('hp', *STATS[1:])])


class Observer:
    def __init__(self, library, core, rom):
        try:
            self.engine = rowe.Observer(library, core, rom, SYMBOLS)
        except Exception:
            # The reused constructor may have initialized the host before refusal.
            C.CDLL(str(library)).libretro_host_cleanup()
            raise

    def party_snapshot(self, side):
        read = self.engine.read
        count_address, base = SYMBOLS['g'+side+'PartyCount'], SYMBOLS['g'+side+'Party']
        count = read(count_address, 1)[0]
        require(count <= 6, 'party count exceeds six')
        # The inherited bounds-checked API permits <=456 bytes per read.
        data = read(base, 300) + read(base+300, 300)
        after = read(count_address, 1)[0]
        again = read(base, 300) + read(base+300, 300)
        final = read(count_address, 1)[0]
        require(count == after == final and data == again, 'incoherent paused party snapshot')
        slots = [data[i*100:(i+1)*100] for i in range(6)]
        return data, dict(count=count, count_after=after, count_final=final,
                         sha256=rowe.digest(data), slot_sha256=[rowe.digest(s) for s in slots],
                         slots=[dict(slot_fields(s), production=native_fields(self.engine.host, s))
                                for s in slots[:count]])

    def observe(self, label, private):
        rowe.require_private_directory(private)
        row = dict(label=label, frame=self.engine.frames, parties={}, save_blocks={})
        for side in ('Player', 'Enemy'):
            raw, row['parties'][side.lower()] = self.party_snapshot(side)
            with (private / (label+'_'+side.lower()+'.party')).open('xb') as f:
                f.write(raw)
        for n in (1, 2):
            pointer = struct.unpack('<I', self.engine.read(SYMBOLS[f'gSaveBlock{n}Ptr'], 4))[0]
            row['save_blocks'][str(n)] = f'0x{pointer:08x}'
        video = rowe.Video()
        require(self.engine.host.libretro_host_get_video_frame(C.byref(video)) and video.pixels
                and video.fmt == 2 and video.width == 240 and video.height == 160
                and 480 <= video.pitch <= 4096, 'unexpected video frame')
        pixels = b''.join(C.string_at(video.pixels+y*video.pitch, 480) for y in range(160))
        row['rgb565_sha256'] = rowe.digest(pixels)
        rgb = bytearray()
        for (v,) in struct.iter_unpack('<H', pixels):
            rgb.extend(((v >> 11)*255//31, ((v >> 5) & 63)*255//63, (v & 31)*255//31))
        with (private / (label+'.ppm')).open('xb') as f:
            f.write(b'P6\n240 160\n255\n'+rgb)
        return row


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('rom', 'core', 'movie', 'output', 'private-dir'):
        parser.add_argument('--'+name, type=Path, required=True)
    bootstrap = parser.add_mutually_exclusive_group(required=True)
    bootstrap.add_argument('--battery-save', type=Path)
    bootstrap.add_argument('--initial-state', type=Path)
    args = parser.parse_args()
    library = HERE.parent / 'vanilla-runtime-probe/build/probe_host.so'
    inputs = [args.rom, args.core, args.movie, args.battery_save, args.initial_state,
              library, Path(__file__), HERE/'rowe_observe.py', HERE/'ups_identity.py', rowe.ADAPTER_PIN]
    if any(p and (p.resolve() == args.output.resolve() or
           (p.exists() and args.output.exists() and p.samefile(args.output))) for p in inputs):
        parser.error('output must not overwrite an input or tool')
    report = dict(schema_version=1, status='FAIL', runtime_layout='NOT_VERIFIED', observations=[])
    host = None
    # Invalidate stale success even when input validation later fails.
    args.output.write_text(json.dumps(report)+'\n')
    try:
        rom = read_local(args.rom, 32*1024*1024)
        report['rom'] = pinned(rom, ROM_SHA256, 32*1024*1024, 'ROM')
        rowe.require_private_directory(args.private_dir)
        report['static'] = inspect_rom(rom)
        report['release_label'] = 'NOT_VERIFIED'
        report['core_sha256'] = rowe.digest(read_local(args.core, 64*1024*1024))
        require(report['core_sha256'] == rowe.CORE_SHA256, 'diagnostic core identity mismatch')
        report['adapter_pin'] = rowe.verify_adapter(library)
        movie = read_local(args.movie, 65536)
        commands = parse_movie(movie.decode('ascii'))
        report.update(observer_sha256=rowe.digest(Path(__file__).read_bytes()),
                      helper_sha256=rowe.digest((HERE/'rowe_observe.py').read_bytes()),
                      identity_helper_sha256=rowe.digest((HERE/'ups_identity.py').read_bytes()),
                      movie_sha256=rowe.digest(movie), diagnostic_addresses={k: hex(v) for k,v in SYMBOLS.items()})
        source = args.battery_save or args.initial_state
        saved = read_local(source, 16*1024*1024)
        is_battery = args.battery_save is not None
        report['bootstrap_input'] = pinned(saved, SAVE_SHA256 if is_battery else STATE_SHA256,
                                          131072 if is_battery else 528448, 'save' if is_battery else 'state')
        report['bootstrap'] = 'OWNER_BATTERY_COPY' if is_battery else 'OWNER_QUICKSAVE_COPY'
        args.private_dir.mkdir(parents=True, exist_ok=False, mode=0o700)
        staged_rom = args.private_dir/'cartridge.gba'
        staged_save = args.private_dir/('cartridge.sav' if is_battery else 'initial.state')
        staged_rom.write_bytes(rom)
        staged_save.write_bytes(saved)
        host = Observer(library, args.core.resolve(), staged_rom)
        fn = host.engine.host.libretro_host_load_save_ram if is_battery else host.engine.host.libretro_host_load_state
        accepted = bool(fn(os.fsencode(staged_save)))
        report['bootstrap_accepted'] = accepted
        require(accepted, 'battery load failed' if is_battery else 'initial state load failed')
        state, saved_frame = args.private_dir/'temporary.state', 0
        for command in commands:
            op = command[0]
            if op == 'run':
                host.engine.run(*command[1:])
            elif op == 'mash':
                for _ in range(command[1]):
                    host.engine.run(1, command[2]); host.engine.run(90, 0)
            elif op == 'observe':
                report['observations'].append(host.observe(command[1], args.private_dir))
            elif op == 'save':
                require(host.engine.host.libretro_host_save_state(os.fsencode(state)), 'save state failed')
                saved_frame = host.engine.frames
            elif op == 'restore':
                require(host.engine.host.libretro_host_load_state(os.fsencode(state)), 'restore failed')
                host.engine.frames = saved_frame
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
