#!/usr/bin/env python3
"""Developer-only input replay and bounded observations; never a support verdict.

No RAM writes, external saves, scans or production party decoder. Observed slot
values use the reported 76-byte hypothesis; UI correlation is documented separately.
"""
import argparse
import ctypes as C
import hashlib
import json
import os
from pathlib import Path
import struct
import sys
import tempfile
from ups_identity import MAX_ROM, identity, read_local, require
from rowe_symbols import inspect_rom

HERE = Path(__file__).resolve().parent
CORE_SHA256 = 'fc7394f718d213131d5ec9002ddea6c72a9cd2094e0a2993c3ea3c9c964f203e'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def slot_fields(data):
    require(len(data) == 76, 'slot must be exactly 76 bytes')
    word = lambda offset: struct.unpack_from('<I', data, offset)[0]
    return dict(pid=word(0), otid=word(4), nickname_hex=data[8:20].hex(),
                species=word(28) & 4095, form=(word(28) >> 12) & 31,
                moves=[word(20) & 1023, word(24) & 1023,
                       (word(24) >> 10) & 1023, (word(28) >> 17) & 1023],
                pp=list(data[52:56]), nature=(data[39] >> 2) & 31,
                level=data[60], hp=struct.unpack_from('<H', data, 62)[0],
                stats=dict(zip(('max_hp', 'attack', 'defense', 'speed', 'sp_attack', 'sp_defense'),
                               struct.unpack_from('<6H', data, 64))))


def parse_movie(text):
    commands, frames, labels, saved = [], 0, set(), False
    for line in text.splitlines():
        parts = line.partition('#')[0].split()
        if not parts:
            continue
        op, args = parts[0], parts[1:]
        if op in ('run', 'mash'):
            require(len(args) in (1, 2), 'invalid input arguments')
            count, mask = int(args[0]), int(args[1]) if len(args) == 2 else (256 if op == 'mash' else 0)
            require(0 < count <= 100000 and 0 <= mask <= 0x3fff, 'input out of range')
            frames += count * (91 if op == 'mash' else 1)
            require(frames <= 500000, 'movie exceeds frame budget')
            commands.append((op, count, mask))
        elif op == 'observe':
            require(len(args) == 1 and args[0].isascii() and args[0].isidentifier()
                    and args[0] not in labels, 'invalid or duplicate observation label')
            labels.add(args[0])
            commands.append((op, args[0]))
        else:
            require(op in ('save', 'restore') and not args, 'unknown movie command')
            require(op != 'restore' or saved, 'restore before save')
            saved = True
            commands.append((op,))
    require(labels, 'movie needs observations')
    return commands


class Video(C.Structure):
    _fields_ = [('width', C.c_uint), ('height', C.c_uint), ('fmt', C.c_int),
                ('pixels', C.c_void_p), ('pitch', C.c_size_t)]


class Observer:
    def __init__(self, library, core, rom, symbols):
        self.host = C.CDLL(str(library))
        h = self.host
        for name in ('init', 'load_rom', 'save_state', 'load_state'):
            fn = getattr(h, 'libretro_host_' + name)
            fn.argtypes, fn.restype = [C.c_char_p], C.c_bool
        h.libretro_host_read_gba_address.argtypes = [C.c_uint32, C.c_void_p, C.c_size_t]
        h.libretro_host_read_gba_address.restype = C.c_bool
        h.libretro_host_get_video_frame.argtypes = [C.POINTER(Video)]
        h.libretro_host_get_video_frame.restype = C.c_bool
        h.libretro_host_set_input_buttons.argtypes = [C.c_uint32]
        for name in ('set_input_buttons', 'step_frame', 'cleanup'):
            getattr(h, 'libretro_host_' + name).restype = None
        self.symbols, self.frames = symbols, 0
        require(h.libretro_host_init(os.fsencode(core)), 'core initialization failed')
        require(h.libretro_host_load_rom(os.fsencode(rom)), 'ROM load failed')

    def read(self, address, size):
        require(0 < size <= 456 and
                (0x02000000 <= address <= 0x02040000 - size or
                 0x03000000 <= address <= 0x03008000 - size), 'read outside bounded RAM')
        data = (C.c_ubyte * size)()
        require(self.host.libretro_host_read_gba_address(address, data, size), 'unmapped RAM')
        return bytes(data)

    def run(self, frames, mask):
        self.host.libretro_host_set_input_buttons(mask)
        for _ in range(frames):
            self.host.libretro_host_step_frame()
        self.frames += frames

    def observe(self, label, screens):
        # No retro_run occurs between reads: the single-threaded core is paused.
        row = dict(label=label, frame=self.frames, parties={}, save_blocks={})
        for side in ('Player', 'Enemy'):
            count = self.read(self.symbols['g' + side + 'PartyCount'], 1)[0]
            require(count <= 6, 'party count exceeds six')
            data = self.read(self.symbols['g' + side + 'Party'], 456)
            row['parties'][side.lower()] = dict(count=count, sha256=digest(data),
                slots=[slot_fields(data[i*76:(i+1)*76]) for i in range(count)])
        for n in (1, 2):
            pointer = struct.unpack('<I', self.read(self.symbols[f'gSaveBlock{n}Ptr'], 4))[0]
            row['save_blocks'][str(n)] = f'0x{pointer:08x}'
            if n == 1 and 0x02000000 <= pointer <= 0x02040000 - 0x238 - 456:
                row['reported_save_party_copy_sha256'] = digest(self.read(pointer + 0x238, 456))
        video = Video()
        require(self.host.libretro_host_get_video_frame(C.byref(video)) and video.pixels and
                video.fmt == 2 and video.width == 240 and video.height == 160 and
                480 <= video.pitch <= 4096, 'unexpected video frame')
        pixels = b''.join(C.string_at(video.pixels + y * video.pitch, 480) for y in range(160))
        row['rgb565_sha256'] = digest(pixels)
        if screens:
            rgb = bytearray()
            for (value,) in struct.iter_unpack('<H', pixels):
                rgb.extend(((value >> 11) * 255 // 31, ((value >> 5) & 63) * 255 // 63,
                            (value & 31) * 255 // 31))
            with (screens / (label + '.ppm')).open('xb') as image:
                image.write(b'P6\n240 160\n255\n' + rgb)
        return row


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--rom', type=Path, required=True)
    parser.add_argument('--core', type=Path, required=True)
    parser.add_argument('--movie', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--screens', type=Path, help='optional private PPM directory; never commit images')
    args = parser.parse_args()
    library = HERE.parent / 'vanilla-runtime-probe/build/probe_host.so'
    if args.output.resolve() in {p.resolve() for p in
                                 (args.rom, args.core, args.movie, library, Path(__file__))}:
        parser.error('output must not overwrite an input or tool')
    report = dict(schema_version=1, status='FAIL', runtime_layout='NOT_VERIFIED')
    host = None
    # Invalidate an older success before opening any input.
    args.output.write_text(json.dumps(report) + '\n')
    try:
        rom = read_local(args.rom, MAX_ROM)
        static = inspect_rom(rom)  # Exact diagnostic pin, not the production trusted registry.
        core = args.core.resolve()
        require(digest(read_local(core, 64*1024*1024)) == CORE_SHA256, 'diagnostic core identity mismatch')
        movie = read_local(args.movie, 65536)
        commands = parse_movie(movie.decode('ascii'))
        report.update(rom=identity(rom), core_sha256=CORE_SHA256,
                      adapter_sha256=digest(read_local(library, 16*1024*1024)),
                      observer_sha256=digest(Path(__file__).read_bytes()),
                      symbols_tool_sha256=digest((HERE / 'rowe_symbols.py').read_bytes()),
                      movie_sha256=digest(movie), observations=[])
        if args.screens:
            args.screens.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(prefix='dualdex-rowe-observe-') as temp:
            # Load only the already verified bytes; no user ROM re-read or modification.
            staged, state = Path(temp) / 'local.gba', Path(temp) / 'local.state'
            staged.write_bytes(rom)
            host = Observer(library, core, staged,
                            {name: int(value, 16) for name, value in static['symbols'].items()})
            saved_frame = 0
            for command in commands:
                op = command[0]
                if op == 'run':
                    host.run(*command[1:])
                elif op == 'mash':
                    for _ in range(command[1]):
                        host.run(1, command[2]); host.run(90, 0)
                elif op == 'observe':
                    report['observations'].append(host.observe(command[1], args.screens))
                elif op == 'save':
                    require(host.host.libretro_host_save_state(os.fsencode(state)), 'save state failed')
                    saved_frame = host.frames
                else:
                    require(host.host.libretro_host_load_state(os.fsencode(state)), 'restore failed')
                    host.frames = saved_frame
            report['status'] = 'OBSERVED'  # Captured successfully, not a layout correctness verdict.
    except (ValueError, OSError, UnicodeError) as error:
        report['error'] = str(error)
        print(f'FAIL: {error}', file=sys.stderr)
    finally:
        if host:
            host.host.libretro_host_cleanup()
        args.output.write_text(json.dumps(report, sort_keys=True, indent=2) + '\n')
    return 0 if report['status'] == 'OBSERVED' else 1


if __name__ == '__main__':
    sys.exit(main())
