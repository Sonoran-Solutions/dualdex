#!/usr/bin/env python3
"""Hack-agnostic headless capture: replay a movie in 2-3 varied boots and diff EWRAM/IWRAM.

Reuses the bounded movie grammar and libretro host binding of rowe_observe.py (left byte-identical:
committed evidence pins its hash) and adds what any hack needs to onboard: a bounded RAM reader, a
movie executor, and a multi-boot comparison that separates addresses that are stable across
boots from addresses that drift (pointers, RNG, timers, allocator state).

The core is deterministic, so identical boots produce identical RAM. Each boot therefore idles for
`boot_index * --boot-jitter` frames before the movie; the movie itself is unchanged. Only
region sizes, hashes and address ranges are reported - never RAM contents. Developer evidence,
never a support verdict.

    python3 tools/rom-evidence/rom_observe.py --rom hack.gba --core core.so \\
        --movie tools/rom-evidence/rowe-starter.movie --boots 3 --output /tmp/stability.json \\
        --probe saveblock1_ptr=0x03005008:4
"""
import argparse
import ctypes as C
import json
import os
from pathlib import Path
import re
import sys
import tempfile

from rowe_observe import digest, parse_movie
from ups_identity import MAX_ROM, read_local, require

HERE = Path(__file__).resolve().parent
REGIONS = {'ewram': (0x02000000, 0x40000), 'iwram': (0x03000000, 0x8000)}
CHUNK = 4096
MAX_BOOTS = 3
MAX_RANGES = 256       # per region in the report; the counts stay exact
MAX_JITTER = 10000
# Every observation holds a full EWRAM+IWRAM dump (288 KiB) per boot until the comparison runs, so
# the count is capped up front: 32 observations x 3 boots is about 27 MiB of buffered RAM.
MAX_OBSERVATIONS = 32


def check_ram_range(address, size, limit=CHUNK):
    """Refuse any read that is not wholly inside EWRAM or IWRAM and at most `limit` bytes."""
    require(0 < size <= limit, 'read size out of range')
    for base, length in REGIONS.values():
        if base <= address and address + size <= base + length:
            return
    raise ValueError('read outside bounded RAM')


class Session:
    """One booted core. `host` is the libretro host binding (a fake in unit tests)."""

    def __init__(self, host):
        self.host, self.frames = host, 0

    @classmethod
    def open(cls, library, core, rom):
        host = C.CDLL(str(library))
        for name in ('init', 'load_rom', 'save_state', 'load_state'):
            fn = getattr(host, 'libretro_host_' + name)
            fn.argtypes, fn.restype = [C.c_char_p], C.c_bool
        host.libretro_host_read_gba_address.argtypes = [C.c_uint32, C.c_void_p, C.c_size_t]
        host.libretro_host_read_gba_address.restype = C.c_bool
        host.libretro_host_set_input_buttons.argtypes = [C.c_uint32]
        for name in ('set_input_buttons', 'step_frame', 'cleanup'):
            getattr(host, 'libretro_host_' + name).restype = None
        session = cls(host)
        require(host.libretro_host_init(os.fsencode(core)), 'core initialization failed')
        require(host.libretro_host_load_rom(os.fsencode(rom)), 'ROM load failed')
        return session

    def read(self, address, size):
        check_ram_range(address, size)
        data = (C.c_ubyte * size)()
        require(self.host.libretro_host_read_gba_address(address, data, size), 'unmapped RAM')
        return bytes(data)

    def run(self, frames, mask):
        self.host.libretro_host_set_input_buttons(mask)
        for _ in range(frames):
            self.host.libretro_host_step_frame()
        self.frames += frames

    def dump(self):
        """Whole EWRAM and IWRAM, read between frames (the single-threaded core is paused)."""
        return {name: b''.join(self.read(base + off, CHUNK) for off in range(0, length, CHUNK))
                for name, (base, length) in REGIONS.items()}

    def close(self):
        self.host.libretro_host_cleanup()


def execute_movie(session, commands, on_observe, state_path):
    """Run parsed movie commands; call on_observe(label, session) at each `observe`."""
    saved_frame = None
    for command in commands:
        op = command[0]
        if op == 'run':
            session.run(command[1], command[2])
        elif op == 'mash':
            for _ in range(command[1]):
                session.run(1, command[2])
                session.run(90, 0)
        elif op == 'observe':
            on_observe(command[1], session)
        elif op == 'save':
            require(session.host.libretro_host_save_state(os.fsencode(state_path)), 'save state failed')
            saved_frame = session.frames
        elif op == 'restore':
            require(session.host.libretro_host_load_state(os.fsencode(state_path)), 'restore failed')
            session.frames = saved_frame
        else:
            raise ValueError(f'movie command {op} is not supported by generic capture')


def capture_boot(session, commands, jitter_frames, state_path):
    """Return {label: {region: bytes}} for one boot, after `jitter_frames` idle frames."""
    require(0 <= jitter_frames <= MAX_JITTER, 'boot jitter out of range')
    session.run(jitter_frames, 0)
    dumps = {}
    execute_movie(session, commands, lambda label, s: dumps.__setitem__(label, s.dump()), state_path)
    return dumps


def drift_ranges(dumps):
    """Byte ranges [(offset, length)] where any boot differs from the first, plus drifting-byte count."""
    require(len(dumps) >= 2 and len({len(d) for d in dumps}) == 1, 'need >= 2 equal-length dumps')
    first = int.from_bytes(dumps[0], 'big')
    mask = 0
    for other in dumps[1:]:
        mask |= first ^ int.from_bytes(other, 'big')
    flags = mask.to_bytes(len(dumps[0]), 'big')
    return [(m.start(), m.end() - m.start()) for m in re.finditer(rb'[^\x00]+', flags)]


def compare_boots(boots, probes=()):
    """Diff per label and region across boots; `probes` are (name, address, size) stability questions.

    Raises unless every boot observed the same labels (a mismatch means the boots diverged
    structurally, which is a failed capture rather than drift).
    """
    require(2 <= len(boots) <= MAX_BOOTS, f'need 2-{MAX_BOOTS} boots')
    labels = list(boots[0])
    require(all(list(boot) == labels for boot in boots), 'boots observed different labels')
    report = {}
    for label in labels:
        regions, ranges_by_region = {}, {}
        for name, (base, length) in REGIONS.items():
            ranges = drift_ranges([boot[label][name] for boot in boots])
            ranges_by_region[name] = ranges
            drifting = sum(size for _, size in ranges)
            regions[name] = dict(bytes=length, stable_bytes=length - drifting, drifting_bytes=drifting,
                                 drifting_range_count=len(ranges),
                                 drifting_ranges=[dict(address=f'0x{base + o:08x}', length=n)
                                                  for o, n in ranges[:MAX_RANGES]],
                                 drifting_ranges_truncated=len(ranges) > MAX_RANGES,
                                 boot_sha256=[digest(boot[label][name]) for boot in boots])
        report[label] = dict(regions=regions, probes=[
            probe_verdict(name, address, size, ranges_by_region) for name, address, size in probes])
    return report


def probe_verdict(name, address, size, ranges_by_region):
    check_ram_range(address, size, REGIONS['ewram'][1])
    for region, (base, length) in REGIONS.items():
        if base <= address < base + length:  # check_ram_range proved it fits wholly inside
            lo = address - base
            drift = any(o < lo + size and lo < o + n for o, n in ranges_by_region[region])
            return dict(name=name, address=f'0x{address:08x}', size=size,
                        verdict='drifting' if drift else 'stable')


def parse_probe(text):
    """`name=0xADDRESS:size` (size decimal or 0x hex)."""
    name, sep, rest = text.partition('=')
    address, sep2, size = rest.partition(':')
    require(sep and sep2 and name.isascii() and name.isidentifier(), 'probe must be name=0xADDR:size')
    probe = (name, int(address, 16), int(size, 0))
    check_ram_range(probe[1], probe[2], REGIONS['ewram'][1])
    return probe


def check_observation_budget(commands):
    """Refuse a movie with too many `observe` commands before any core is booted."""
    count = sum(1 for command in commands if command[0] == 'observe')
    require(count <= MAX_OBSERVATIONS, f'movie has {count} observations; at most {MAX_OBSERVATIONS} are allowed')


def run_boots(args, rom, commands, probes):
    check_observation_budget(commands)
    boots = []
    with tempfile.TemporaryDirectory(prefix='dualdex-rom-observe-') as temp:
        staged = Path(temp) / 'local.gba'
        staged.write_bytes(rom)
        for index in range(args.boots):
            session = Session.open(args.library, args.core.resolve(), staged)
            try:
                boots.append(capture_boot(session, commands, index * args.boot_jitter,
                                          Path(temp) / f'boot{index}.state'))
            finally:
                session.close()
    return compare_boots(boots, probes)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--rom', type=Path, required=True)
    parser.add_argument('--core', type=Path, required=True)
    parser.add_argument('--library', type=Path, default=HERE.parent / 'vanilla-runtime-probe/build/probe_host.so')
    parser.add_argument('--movie', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--boots', type=int, default=2, choices=range(2, MAX_BOOTS + 1))
    parser.add_argument('--boot-jitter', type=int, default=7, help='extra idle frames per boot index')
    parser.add_argument('--probe', action='append', default=[], metavar='NAME=0xADDR:SIZE')
    args = parser.parse_args(argv)
    protected = [args.rom, args.core, args.movie, args.library, Path(__file__)]
    # `resolve()` catches symlinks; `samefile` additionally catches hard links to an input.
    if any(args.output.resolve() == p.resolve() or
           (args.output.exists() and p.exists() and os.path.samefile(args.output, p)) for p in protected):
        parser.error('output must not overwrite an input or tool')
    report = dict(schema_version=1, status='FAIL', verdict='NOT_A_SUPPORT_VERDICT')
    args.output.write_text(json.dumps(report) + '\n')  # invalidate any older success first
    try:
        probes = [parse_probe(p) for p in args.probe]
        rom = read_local(args.rom, MAX_ROM)
        movie = read_local(args.movie, 65536)
        commands = parse_movie(movie.decode('ascii'))
        report.update(rom_sha256=digest(rom), core_sha256=digest(read_local(args.core, 64 * 1024 * 1024)),
                      library_sha256=digest(read_local(args.library, 16 * 1024 * 1024)),
                      movie_sha256=digest(movie), boots=args.boots, boot_jitter_frames=args.boot_jitter,
                      observations=run_boots(args, rom, commands, probes))
        report['status'] = 'OBSERVED'
    except (ValueError, OSError, UnicodeError, KeyError, TypeError) as error:
        report.pop('observations', None)
        report['error'] = str(error)
        print(f'FAIL: {error}', file=sys.stderr)
    args.output.write_text(json.dumps(report, sort_keys=True, indent=2) + '\n')
    return 0 if report['status'] == 'OBSERVED' else 1


if __name__ == '__main__':
    sys.exit(main())
