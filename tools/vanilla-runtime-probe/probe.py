#!/usr/bin/env python3
"""Controlled retail-ROM damage experiment; never a product lifecycle authority."""
from __future__ import annotations
import argparse
import ctypes as C
import hashlib
import json
import os
from pathlib import Path
import struct
import sys
import tempfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(ROOT / 'tools/calc-goldens'))
import gen3_reference as oracle

GOLDENS = ROOT / 'tools/calc-goldens/vanilla_gen3_goldens.json'
CASES = {'physical': ('vg3_a_neutral_physical', 157),
         'special': ('vg3_b_neutral_special_type_split', 242)}
# Diagnostic addresses only. Never imported into a bundled profile or production reader.
BUILDS = {
    'firered': dict(profile='vanilla_firered', game=2, base=0x23BE4,
        sha='3d0c79f1627022e18765766f6cb5ea067f6b5bf7dca115552189ad65a5c3a8ac',
        species=[1, 4], menu='6c51db711cb6427e8f73bc1349656f9443eca1083552a798648f87a55e6d0728'),
    'emerald': dict(profile='vanilla_emerald', game=1, base=0x24084,
        sha='a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af',
        species=[280, 288], menu='e526e0e99587b6649121300167110a355e8593f740800a01e48be8a936e3273b'),
}


def require(ok, message):
    if not ok:
        raise ValueError(message)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def identity(rom, build):
    profile = json.loads((ROOT / 'app/src/main/assets/profiles' /
                          (build['profile'] + '.json')).read_text())
    sha = digest(rom)
    require(sha in profile['sha256Hashes'] and sha == build['sha'], 'ROM identity mismatch')
    require(profile['battleStateReadVerified'] is False, 'vanilla battle trust boundary changed')
    return sha


def fixture(case, document=None, calculate=None):
    document = document or json.loads(GOLDENS.read_text())
    row = next(f for f in document['fixtures'] if f['id'] == CASES[case][0])
    request = json.loads(row['request'])
    # A changed request must not silently stage the old combatants under a new label.
    for who, species in [('attacker', 'Machamp'), ('defender', 'Snorlax')]:
        p = request[who]
        require(p == dict(species=species, level=50, nature='Hardy',
                         ivs={k: 31 for k in ('hp','atk','def','spa','spd','spe')},
                         evs={k: 0 for k in ('hp','atk','def','spa','spd','spe')}),
                'unsupported fixture operands')
    require(request['field'] == {'gameType': 'Singles'} and
            request['move'] == {'name': 'Rock Slide' if case == 'physical' else 'Crunch',
                                'isCrit': False}, 'unsupported fixture state')
    calculated = (calculate or oracle.calculate)(request)
    require(set(row['expected']) == {'damage', 'minDamage', 'maxDamage',
                                      'moveType', 'moveCategory', 'movePower'},
            'incomplete expected vector metadata')
    require(row['expected'] == {k: calculated[k] for k in row['expected']},
            'committed vector disagrees with independent oracle')
    return row


def validate_hit(row, hit):
    require(hit['preHP'] == 235 and 0 < hit['postHP'] < hit['preHP'], 'invalid HP transition')
    damage = hit['preHP'] - hit['postHP']
    require(damage == hit['damage'] == hit['engineDamage'], 'HP delta / engine damage mismatch')
    require(hit['attacker'] == 0 and hit['target'] == 1 and hit['critMultiplier'] == 1,
            'wrong attacker/target/critical state')
    move = 157 if row['id'] == CASES['physical'][0] else 242
    require(hit['move'] == move, 'wrong executed move')
    require(damage in row['expected']['damage'], 'HP delta outside committed vector')


def decode_combatant(b):
    species, atk, defense, speed, spa, spd = struct.unpack_from('<6H', b)
    return dict(speciesId=species, stats=dict(atk=atk, defense=defense, speed=speed,
                spa=spa, spd=spd), moves=list(struct.unpack_from('<4H', b, 12)),
                stages=list(b[24:32]), abilityId=b[32], types=list(b[33:35]),
                hp=struct.unpack_from('<H', b, 40)[0], level=b[42],
                maxHP=struct.unpack_from('<H', b, 44)[0], item=struct.unpack_from('<H', b, 46)[0],
                personality=struct.unpack_from('<I', b, 72)[0],
                status1=struct.unpack_from('<I', b, 76)[0], status2=struct.unpack_from('<I', b, 80)[0])


class Video(C.Structure):
    _fields_ = [('width', C.c_uint), ('height', C.c_uint), ('fmt', C.c_int),
                ('pixels', C.c_void_p), ('pitch', C.c_size_t)]


class Runtime:
    def __init__(self, core, rom_path, build):
        self.h = C.CDLL(str(HERE / 'build/probe_host.so'))
        h = self.h
        for name in ('libretro_host_init', 'libretro_host_load_rom',
                     'libretro_host_read_gba_address', 'libretro_host_get_video_frame',
                     'probe_party', 'probe_layout'):
            getattr(h, name).restype = C.c_bool
        h.libretro_host_init.argtypes = [C.c_char_p]
        h.libretro_host_load_rom.argtypes = [C.c_char_p]
        h.libretro_host_get_ewram.restype = C.POINTER(C.c_ubyte)
        h.libretro_host_read_gba_address.argtypes = [C.c_uint32, C.c_void_p, C.c_size_t]
        require(h.libretro_host_init(os.fsencode(core)), 'core load failed')
        require(h.libretro_host_load_rom(os.fsencode(rom_path)), 'ROM load failed')
        n = C.c_size_t()
        self.ram = h.libretro_host_get_ewram(C.byref(n))
        require(n.value == 0x40000, 'EWRAM unavailable')
        self.build, self.frame, self.sealed = build, 0, False
        layout = (C.c_uint * 3)()
        require(h.probe_layout(build['game'], layout) and list(layout) == [88, 40, 24],
                'production BattlePokemon structure contract changed')

    def read(self, offset, size):
        out = (C.c_ubyte * size)()
        require(self.h.libretro_host_read_gba_address(0x02000000 + offset, out, size),
                'unreadable diagnostic address')
        return bytes(out)

    def value(self, relative, size=1):
        return int.from_bytes(self.read(self.build['base'] + relative, size), 'little')

    def run(self, frames, buttons=0):
        self.h.libretro_host_set_input_buttons(buttons)
        for _ in range(frames):
            self.h.libretro_host_step_frame()
            self.frame += 1

    def route(self, path):
        for line in path.read_text().splitlines():
            words = line.split('#')[0].split()
            if not words:
                continue
            if words[0] == 'run' and len(words) == 3:
                n, buttons = map(int, words[1:])
                require(0 < n <= 10000 and 0 <= buttons <= 4095, 'invalid input step')
                self.run(n, buttons)
            elif words[0] == 'mash' and len(words) in (2, 3):
                n = int(words[1])
                buttons = int(words[2]) if len(words) == 3 else 256
                require(0 < n <= 100 and 0 <= buttons <= 4095, 'invalid mash step')
                for _ in range(n):
                    self.run(1, buttons)
                    self.run(90)
            else:
                raise ValueError('unknown scenario command: ' + line)

    def checkpoint(self):
        v = Video()
        require(self.h.libretro_host_get_video_frame(C.byref(v)), 'no video checkpoint')
        require((v.width, v.height, v.fmt, v.pitch) == (240,160,2,480), 'unexpected video format')
        sha = digest(C.string_at(v.pixels, v.pitch * v.height))
        require(sha == self.build['menu'], 'opening battle menu checkpoint mismatch: ' + sha)
        parties = []
        for side in (0, 1):
            parsed = (C.c_ushort * 13)()
            require(self.h.probe_party(self.build['game'], side, parsed), 'production party parse failed')
            b = self.read(self.build['base'] + side * 88, 88)
            observed = list(struct.unpack_from('<10H', b)) + [
                struct.unpack_from('<H', b, 40)[0], b[42], struct.unpack_from('<H', b, 44)[0]]
            require(list(parsed) == observed, 'battle snapshot disagrees with production party parser')
            require(parsed[0] == self.build['species'][side] and parsed[11] in (2,5),
                    'unexpected original combatant')
            require(b[24:32] == bytes([6])*8 and b[76:84] == bytes(8), 'non-neutral opening battle')
            parties.append(observed)
        # gBattlersCount / positions immediately before BattlePokemon, pinned declaration order.
        require(self.value(-24) == 2 and self.read(self.build['base']-14, 2) == b'\x00\x01',
                'not the expected opening Singles topology')
        return {'frame': self.frame, 'menuSHA256': sha, 'originalParties': parties}

    def stage(self, case):
        require(not self.sealed, 'writes sealed')
        staged = []
        for side, species, stats, hp, ability, types, move in [
                (0,68,[150,100,75,85,105],165,62,[1,1],CASES[case][1]),
                (1,143,[130,85,50,85,130],235,17,[0,0],150)]:
            offset = self.build['base'] + side*88
            b = bytearray(self.read(offset, 88))
            struct.pack_into('<6H', b, 0, species, *stats)
            struct.pack_into('<4H', b, 12, move, 0, 0, 0)
            struct.pack_into('<I', b, 20, 0x3fffffff)  # IVs=31; not an egg; ability slot 0
            b[24:32] = bytes([6])*8
            b[32:36] = bytes([ability, *types, 0])
            b[36:40] = bytes([10,0,0,0])
            struct.pack_into('<HBBHH', b, 40, hp, 50, 70, hp, 0)
            struct.pack_into('<I', b, 72, 0)  # Hardy personality
            b[76:84] = bytes(8)
            C.memmove(C.addressof(self.ram.contents)+offset, bytes(b), 88)
            require(self.read(offset,88) == b, 'staged snapshot mismatch')
            staged.append(bytes(b))
        self.sealed = True
        return staged

    def observe(self, staged, budget=3000):
        base = self.build['base']
        for i in range(budget):
            self.run(1, 256 if i % 90 == 0 else 0)
            # No second hit/retry selection: the FIRST defender HP transition decides the run.
            hp = self.value(88+40, 2)
            if hp != 235:
                # Relevant operands must survive move execution; PP/HP may change.
                for side, before in enumerate(staged):
                    after = self.read(base+side*88,88)
                    for start,end in [(0,36),(42,48),(72,84)]:
                        require(before[start:end] == after[start:end], 'combatant operands changed before hit')
                return dict(frame=self.frame, preHP=235, postHP=hp, damage=235-hp,
                    engineDamage=self.value(0x16c,4), move=self.value(0x166,2),
                    attacker=self.value(0x187), target=self.value(0x188),
                    critMultiplier=self.value(0x18d))
        raise ValueError('no defender HP transition before frame budget')


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--core', required=True, type=Path)
    p.add_argument('--rom', required=True, type=Path)
    p.add_argument('--game', required=True, choices=BUILDS)
    p.add_argument('--case', choices=CASES, default='physical')
    p.add_argument('--output', required=True, type=Path)
    args = p.parse_args()
    args.output.write_text(json.dumps({'verdict': 'FAIL', 'reason': 'run not completed'}) + '\n')
    build = BUILDS[args.game]
    rom = args.rom.read_bytes()
    sha = identity(rom, build)  # BEFORE core initialization / evidence collection
    row = fixture(args.case)
    print('[IDENTITY] PASS ' + build['profile'] + ' SHA256=' + sha, flush=True)
    scenario = HERE / 'scenarios' / (args.game + '.txt')
    # Load exactly the verified bytes. No path re-read race, no user battery save.
    with tempfile.TemporaryDirectory(prefix='vanilla-probe-') as tmp:
        verified = Path(tmp) / 'verified.gba'
        verified.write_bytes(rom)
        runtime = Runtime(args.core, verified, build)
        try:
            runtime.route(scenario)
            checkpoint = runtime.checkpoint()
            staged = runtime.stage(args.case)
            hit = runtime.observe(staged)
            validate_hit(row, hit)
            result = dict(schema='dualdex.vanilla_runtime.v1', verdict='PASS',
                profile=build['profile'], romSHA256=sha, coreSHA256=digest(args.core.read_bytes()),
                scenarioSHA256=digest(scenario.read_bytes()), fixture=row['id'],
                probeSHA256=digest(Path(__file__).read_bytes()),
                hostSHA256=digest((HERE / 'build/probe_host.so').read_bytes()),
                goldensSHA256=digest(GOLDENS.read_bytes()), request=json.loads(row['request']),
                expectedDamage=row['expected']['damage'], checkpoint=checkpoint,
                diagnosticBattleMonsAddress=hex(0x02000000+build['base']),
                setup='controlled BattlePokemon RAM operands in an input-driven opening battle; writes sealed before move',
                combatants=[decode_combatant(b) for b in staged], hit=hit)
            args.output.write_text(json.dumps(result, indent=2)+'\n')
            print('[RUNTIME] PASS ' + json.dumps(hit), flush=True)
        finally:
            runtime.h.libretro_host_cleanup()


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, KeyError, StopIteration) as e:
        print('[RUNTIME] FAIL ' + str(e), file=sys.stderr)
        sys.exit(1)
