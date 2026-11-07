#!/usr/bin/env python3
"""Exact-candidate, read-only Unbound player-location diagnostics. Never a support verdict.

Static: decodes the exact-ROM instructions that place SaveBlock1, read the current map,
resolve (group, num) to a map header and a header to a region-map section name pointer.
Runtime (optional): replays a movie from a private battery copy with the pinned adapter and
records SaveBlock1 warp fields, gMapHeader and the ROM header they resolve to. Emits numbers,
addresses and hashes only; map-name bytes are hashed, never decoded or printed.
"""
import argparse
import ctypes as C
import json
import os
from pathlib import Path
import struct
import sys

import rowe_observe as rowe
import unbound_observe as uo
from ups_identity import read_local, require

HERE = Path(__file__).resolve().parent
ROM_BASE, ROM_SIZE = 0x08000000, 32*1024*1024
# Public CFRU BPRE.ld names; each is re-derived from exact-ROM instructions below.
SET_SAVE_BLOCKS_POINTERS = 0x0804C058
GET_MAP_HEADER = 0x08055238          # Overworld_GetMapHeaderByGroupAndId (hooked in this ROM)
GET_SAVED_WARP_MAPSEC = 0x08056238   # GetSavedWarpRegionMapSectionId
GET_CURRENT_MAPSEC = 0x08056260      # GetCurrentRegionMapSectionId
GET_MAP_NAME = 0x080C4D78
G_MAP_HEADER = 0x02036DFC
MAP_HEADER_SIZE, MAPSEC_OFFSET = 0x1C, 0x14
SB1_HEAD = 0x2C  # pos, location, continueGameWarp, dynamicWarp, lastHealLocation, escapeWarp
WARPS = (('location', 0x04), ('continue_game_warp', 0x0C), ('dynamic_warp', 0x14),
         ('last_heal_location', 0x1C), ('escape_warp', 0x24))
# CONFIG_UNBOUND has no save_block1_ptr_gba_address, so pokemon_reader.c takes its legacy
# path: sb1 = player_party_offset - 0x238 (non-FireRed game_id), pos +0, location +4, escape +0x24.
LEGACY_PARTY_OFFSET, LEGACY_SB1_PARTY = 0x24284, 0x238


def u16(rom, address):
    return struct.unpack_from('<H', rom, address - ROM_BASE)[0]


def u32(rom, address):
    return struct.unpack_from('<I', rom, address - ROM_BASE)[0]


def is_rom_pointer(value, size=ROM_SIZE):
    return ROM_BASE <= value < ROM_BASE + size and value % 4 == 0


def thumb_literal(rom, pc):
    """Decode Thumb `ldr rd, [pc, #imm]` at pc; returns (rd, literal address, word)."""
    h = u16(rom, pc)
    require(h >> 11 == 0b01001, 'expected ldr rd, [pc, #imm] at ' + hex(pc))
    at = ((pc + 4) & ~3) + (h & 255) * 4
    return (h >> 8) & 7, at, u32(rom, at)


def expect(rom, address, halfwords, what):
    got = tuple(u16(rom, address + 2*i) for i in range(len(halfwords)))
    require(got == tuple(halfwords), what + ' instruction mismatch at ' + hex(address))


def inspect_save_blocks(rom):
    """SetSaveBlocksPointers: Random() result discarded, offset register forced to 0."""
    a = SET_SAVE_BLOCKS_POINTERS
    expect(rom, a, (0xb530,), 'push')
    ptr1 = thumb_literal(rom, a+2)
    expect(rom, a+4, (0x6825,), 'ldr r5,[r4] (old SB1)')
    require(u16(rom, a+6) >> 11 == 0b11110, 'bl Random expected')
    expect(rom, a+10, (0x2100, 0x2100), 'movs r1,#0 x2 (offset)')
    sb2_ptr, sb2 = thumb_literal(rom, a+14), thumb_literal(rom, a+16)
    expect(rom, a+18, (0x1808, 0x6010), 'adds r0,r1,r0; str r0,[r2]')
    sb1 = thumb_literal(rom, a+22)
    expect(rom, a+24, (0x1808, 0x6020), 'adds r0,r1,r0; str r0,[r4]')
    storage_ptr, storage = thumb_literal(rom, a+28), thumb_literal(rom, a+30)
    expect(rom, a+32, (0x1809, 0x6011), 'adds r1,r1,r0; str r1,[r2]')
    require(ptr1[0] == 4 and sb2_ptr[0] == 2 and storage_ptr[0] == 2, 'register roles changed')
    return dict(function=hex(a), offset_register='CONSTANT_ZERO',
                gSaveBlock1Ptr=hex(ptr1[2]), save_block1=hex(sb1[2]),
                gSaveBlock2Ptr=hex(sb2_ptr[2]), save_block2=hex(sb2[2]),
                gPokemonStoragePtr=hex(storage_ptr[2]), pokemon_storage=hex(storage[2]),
                sha256=rowe.digest(rom[a-ROM_BASE:a-ROM_BASE+0x4c]))


def inspect_mapsec_getters(rom, sb1_ptr):
    """GetCurrent/SavedWarp RegionMapSectionId: SB1->warp group/num -> header+0x14."""
    out = {}
    for name, address, group_offset in (('current', GET_CURRENT_MAPSEC, 4),
                                        ('saved_warp', GET_SAVED_WARP_MAPSEC, 0x14)):
        expect(rom, address, (0xb500,), 'push')
        literal = thumb_literal(rom, address+2)
        require(literal[2] == sb1_ptr, 'mapsec getter reads another pointer')
        expect(rom, address+4, (0x6801, 0x2000 | group_offset, 0x5608, 0x0400, 0x0c00,
                                0x7809 | (group_offset+1) << 6, 0x0609, 0x1609, 0x0409, 0x0c09),
               name + ' mapsec getter')
        require(u16(rom, address+24) >> 11 == 0b11110, 'bl expected')
        expect(rom, address+28, (0x7d00,), 'ldrb r0,[r0,#0x14]')
        out[name] = dict(function=hex(address), group_offset=hex(group_offset),
                         num_offset=hex(group_offset+1), header_field_offset=hex(MAPSEC_OFFSET))
    return out


def inspect_map_header_path(rom):
    """Overworld_GetMapHeaderByGroupAndId: `ldr r2,=hook; bx r2`, hook XORs group pointers."""
    rd, _, target = thumb_literal(rom, GET_MAP_HEADER)
    expect(rom, GET_MAP_HEADER+2, (0x4700 | rd << 3,), 'bx hook')
    require(target & 1 and is_rom_pointer(target - 1), 'hook target is not Thumb ROM code')
    hook = target - 1
    table_rd, _, table = thumb_literal(rom, hook)
    expect(rom, hook+2, (0x0080, 0x58c2), 'lsls r0,#2; ldr r2,[r0,r3]')
    key_rd, _, key = thumb_literal(rom, hook+6)
    expect(rom, hook+8, (0x0089, 0x4053, 0x58c8, 0x4770), 'lsls r1,#2; eors r3,r2; ldr r0,[r1,r3]; bx lr')
    require(table_rd == key_rd == 3, 'hook register roles changed')
    return dict(function=hex(GET_MAP_HEADER), hook=hex(hook), map_groups_table=hex(table),
                group_pointer_xor_key=hex(key), group_stride=4, map_stride=4,
                header_pointer_xor='NONE', formula='header=*(u32*)((groups[g]^key)+4*num)',
                hook_sha256=rowe.digest(rom[hook-ROM_BASE:hook-ROM_BASE+0x18]))


def inspect_map_names(rom):
    """GetMapName: index = mapsec - base, bounded by `cmp r5,#limit; bhi`, 4-byte pointers."""
    a = GET_MAP_NAME
    expect(rom, a, (0xb570, 0x1c06, 0x0409, 0x0412, 0x0c14, 0x0c08), 'GetMapName prologue')
    _, _, bias = thumb_literal(rom, a+12)
    expect(rom, a+14, (0x1889, 0x0c0d), 'adds r1,r1,r2; lsrs r5,r1,#16')
    limit = u16(rom, a+18)
    require(limit >> 8 == 0x2d and u16(rom, a+20) >> 8 == 0xd8, 'cmp r5,#imm; bhi expected')
    require(bias & 0xffff == 0 and bias >> 16 >= 0x8000, 'negative mapsec bias expected')
    base, count = 0x10000 - (bias >> 16), (limit & 255) + 1
    expect(rom, a+0x1a, (0x2801, 0xd106), 'cmp r0,#1; bne table')
    _, _, alternative = thumb_literal(rom, a+0x1e)
    _, _, table = thumb_literal(rom, a+0x2c)
    expect(rom, a+0x2e, (0x00a9, 0x1809, 0x6809), 'lsls r1,r5,#2; adds; ldr r1,[r1]')
    pointers = [u32(rom, table + 4*i) for i in range(count)]
    require(all(ROM_BASE <= p < ROM_BASE + ROM_SIZE for p in pointers), 'name pointer outside ROM')
    lengths, text = [], bytearray()
    for p in pointers:
        end = rom.find(b'\xff', p - ROM_BASE, p - ROM_BASE + 64)
        require(end >= 0, 'unterminated name')
        lengths.append(end - (p - ROM_BASE))
        text += rom[p-ROM_BASE:end+1]
    return dict(function=hex(a), table=hex(table), first_mapsec=hex(base),
                last_mapsec=hex(base+count-1), count=count, stride=4,
                predicate_alternative_name_pointer=hex(alternative),
                pointer_table_sha256=rowe.digest(rom[table-ROM_BASE:table-ROM_BASE+4*count]),
                terminated_names_sha256=rowe.digest(bytes(text)),
                name_length_min=min(lengths), name_length_max=max(lengths))


def map_header(rom, table, key, group, num):
    return u32(rom, (u32(rom, table + 4*group) ^ key) + 4*num)


def enumerate_maps(rom, table, key):
    """Structural enumeration: groups until a decoded pointer leaves ROM; a group's maps end at
    the next group list start, the table itself, or the first entry not pointing at a header
    whose layout pointer is in ROM. A bound for evidence, not a game-defined count."""
    groups = []
    while len(groups) < 256:
        pointer = u32(rom, table + 4*len(groups)) ^ key
        if not is_rom_pointer(pointer):
            break
        groups.append(pointer)
    starts = sorted(set(groups) | {table})
    rows, counts = [], []
    for g, start in enumerate(groups):
        end, n = min([s for s in starts if s > start] + [ROM_BASE + ROM_SIZE]), 0
        while start + 4*n < end:
            header = u32(rom, start + 4*n)
            if not is_rom_pointer(header) or not is_rom_pointer(u32(rom, header)):
                break
            rows.append((g, n, rom[header - ROM_BASE + MAPSEC_OFFSET]))
            n += 1
        counts.append(n)
    secs = sorted({r[2] for r in rows})
    return dict(groups=len(groups), maps_per_group=counts, maps=len(rows),
                max_map_num=max(counts) - 1, mapsec_min=hex(secs[0]), mapsec_max=hex(secs[-1]),
                distinct_mapsecs=len(secs),
                rows_sha256=rowe.digest(b''.join(bytes(r) for r in rows)),
                rows_encoding='concatenated u8 (group, num, regionMapSectionId)'), rows


def decode_warp(data):
    group, num, warp = struct.unpack_from('<bbb', data)
    x, y = struct.unpack_from('<hh', data, 4)
    return dict(map_group=group, map_num=num, warp_id=warp, x=x, y=y)


def decode_sb1_head(data):
    require(len(data) >= SB1_HEAD, 'SaveBlock1 head too short')
    x, y = struct.unpack_from('<hh', data)
    return dict(pos=dict(x=x, y=y), **{n: decode_warp(data[o:o+8]) for n, o in WARPS})


def native_decode(data, pos, location, escape):
    """Mirror of pokemon_reader.c decode_location_fields, including its validity bounds."""
    loc = decode_warp(data[location:location+8])
    if not (0 <= loc['map_group'] <= 35 and 0 <= loc['map_num'] <= 130):
        return dict(is_valid=False)
    x, y = struct.unpack_from('<hh', data, pos)
    esc = decode_warp(data[escape:escape+8])
    return dict(is_valid=True, map_group=loc['map_group'], map_num=loc['map_num'],
                warp_id=loc['warp_id'], local_x=x, local_y=y,
                escape_map_group=esc['map_group'], escape_map_num=esc['map_num'])


def inspect_rom(data):
    rom = uo.pinned(data, uo.ROM_SHA256, ROM_SIZE, 'ROM')
    saves = inspect_save_blocks(data)
    sb1_ptr = int(saves['gSaveBlock1Ptr'], 16)
    require(sb1_ptr == uo.SYMBOLS['gSaveBlock1Ptr'], 'SaveBlock1 pointer differs from observer symbol')
    path = inspect_map_header_path(data)
    summary, _ = enumerate_maps(data, int(path['map_groups_table'], 16), int(path['group_pointer_xor_key'], 16))
    names = inspect_map_names(data)
    return dict(status='VERIFIED', scope='STATIC_SELECTED_PATHS_ONLY', rom=rom, save_blocks=saves,
                mapsec_getters=inspect_mapsec_getters(data, sb1_ptr), map_header_path=path,
                map_table=summary, map_names=names,
                gMapHeader_literal_references=uo.literal_references(data, dict(gMapHeader=G_MAP_HEADER))['gMapHeader'])


class Observer:
    def __init__(self, library, core, staged_rom, rom, static):
        self.rom, self.static = rom, static
        self.host = uo.Observer(library, core, staged_rom)
        self.engine = self.host.engine

    def snapshot(self):
        read = self.engine.read
        word = lambda a: struct.unpack('<I', read(a, 4))[0]
        ptrs = dict(gSaveBlock1Ptr=word(uo.SYMBOLS['gSaveBlock1Ptr']),
                    gSaveBlock2Ptr=word(uo.SYMBOLS['gSaveBlock2Ptr']))
        sb1 = ptrs['gSaveBlock1Ptr']
        require(0x02000000 <= sb1 <= 0x02040000 - SB1_HEAD, 'SaveBlock1 pointer outside EWRAM')
        legacy = 0x02000000 + LEGACY_PARTY_OFFSET - LEGACY_SB1_PARTY
        return dict(ptrs=ptrs, sb1=read(sb1, SB1_HEAD), header=read(G_MAP_HEADER, MAP_HEADER_SIZE),
                    legacy=read(legacy, SB1_HEAD), legacy_address=legacy)

    def observe(self, label, private):
        rowe.require_private_directory(private)
        first, second = self.snapshot(), self.snapshot()
        require(first == second, 'incoherent paused location snapshot')
        head = decode_sb1_head(first['sb1'])
        path = self.static['map_header_path']
        table, key = int(path['map_groups_table'], 16), int(path['group_pointer_xor_key'], 16)
        loc = head['location']
        row = dict(label=label, frame=self.engine.frames,
                   pointers={k: hex(v) for k, v in first['ptrs'].items()},
                   sb1_head_sha256=rowe.digest(first['sb1']), sb1=head,
                   gMapHeader_sha256=rowe.digest(first['header']),
                   gMapHeader_mapsec=hex(first['header'][MAPSEC_OFFSET]),
                   native_pointer_layout=native_decode(first['sb1'], 0, 4, 0x24),
                   native_legacy=dict(address=hex(first['legacy_address']),
                                      decoded=native_decode(first['legacy'], 0, 4, 0x24)))
        groups = self.static['map_table']['maps_per_group']
        if 0 <= loc['map_group'] < len(groups) and 0 <= loc['map_num'] < groups[loc['map_group']]:
            header = map_header(self.rom, table, key, loc['map_group'], loc['map_num'])
            rom_bytes = self.rom[header-ROM_BASE:header-ROM_BASE+MAP_HEADER_SIZE]
            row['rom_header'] = dict(address=hex(header), sha256=rowe.digest(rom_bytes),
                                     mapsec=hex(rom_bytes[MAPSEC_OFFSET]),
                                     equals_gMapHeader=rom_bytes == first['header'])
        else:
            row['rom_header'] = dict(status='LOCATION_OUTSIDE_ENUMERATED_TABLE')
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
    parser.add_argument('--rom', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--static-only', action='store_true')
    for name in ('core', 'movie', 'private-dir', 'battery-save'):
        parser.add_argument('--'+name, type=Path)
    args = parser.parse_args()
    library = HERE.parent / 'vanilla-runtime-probe/build/probe_host.so'
    inputs = [args.rom, args.core, args.movie, args.battery_save, library, Path(__file__),
              HERE/'unbound_observe.py', HERE/'rowe_observe.py', rowe.ADAPTER_PIN]
    if any(p and (p.resolve() == args.output.resolve() or
           (p.exists() and args.output.exists() and p.samefile(args.output))) for p in inputs):
        parser.error('output must not overwrite an input or tool')
    if not args.static_only and not all((args.core, args.movie, args.private_dir, args.battery_save)):
        parser.error('runtime needs --core, --movie, --private-dir and --battery-save')
    report = dict(schema_version=1, status='FAIL', observations=[])
    host = None
    args.output.write_text(json.dumps(report)+'\n')
    try:
        rom = read_local(args.rom, ROM_SIZE)
        report['static'] = inspect_rom(rom)
        report['tool_sha256'] = rowe.digest(Path(__file__).read_bytes())
        if args.static_only:
            report['status'] = 'STATIC'
            return 0
        rowe.require_private_directory(args.private_dir)
        report['core_sha256'] = rowe.digest(read_local(args.core, 64*1024*1024))
        require(report['core_sha256'] == rowe.CORE_SHA256, 'diagnostic core identity mismatch')
        report['adapter_pin'] = rowe.verify_adapter(library)
        movie = read_local(args.movie, 65536)
        commands = uo.parse_movie(movie.decode('ascii'))
        report.update(movie_sha256=rowe.digest(movie),
                      observer_sha256=rowe.digest((HERE/'unbound_observe.py').read_bytes()),
                      helper_sha256=rowe.digest((HERE/'rowe_observe.py').read_bytes()))
        saved = read_local(args.battery_save, 16*1024*1024)
        report['bootstrap_input'] = uo.pinned(saved, uo.SAVE_SHA256, 131072, 'save')
        args.private_dir.mkdir(parents=True, exist_ok=False, mode=0o700)
        staged_rom, staged_save = args.private_dir/'cartridge.gba', args.private_dir/'cartridge.sav'
        staged_rom.write_bytes(rom)
        staged_save.write_bytes(saved)
        host = Observer(library, args.core.resolve(), staged_rom, rom, report['static'])
        require(host.engine.host.libretro_host_load_save_ram(os.fsencode(staged_save)), 'battery load failed')
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
    except (ValueError, OSError, UnicodeError, KeyError, TypeError, IndexError, struct.error) as error:
        report['observations'] = []
        report['error'] = str(error)
        print('FAIL: '+str(error), file=sys.stderr)
    finally:
        if host:
            host.engine.host.libretro_host_cleanup()
        args.output.write_text(json.dumps(report, sort_keys=True, indent=2)+'\n')
    return 0 if report['status'] in ('OBSERVED', 'STATIC') else 1


if __name__ == '__main__':
    sys.exit(main())
