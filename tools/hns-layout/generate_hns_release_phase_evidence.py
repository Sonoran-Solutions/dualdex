#!/usr/bin/env python3
"""Bind pinned-source battle phase functions to the SHA-pinned official release.

The source build's IWRAM/code addresses are NOT official release addresses. Match
unique Thumb instruction sequences from the named source symbols, allowing only
relocated BL targets, PC literal distances, and branch displacements. Resolve the
actual gBattleMainFunc literal from the matched BattleMainCB1. Emit addresses,
hashes and match counts only; never emit ROM bytes. Runtime transition evidence
is an additional, independent requirement before these bindings authorize a hit.
"""
import argparse
import hashlib
import importlib.util
import json
import re
import struct
from pathlib import Path

ROM_SHA256 = 'edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b'
OUTPUT = Path(__file__).with_name('release_phase_evidence.json')
MENU_PREFIXES = {'HandleInputChooseAction': 128, 'HandleInputChooseMove': 16,
                 'HandleInputChooseTarget': 30}


def digest(data): return hashlib.sha256(data).hexdigest()


def relocation_mask(code):
    mask=bytearray(b'\xff'*len(code))
    i=0
    while i<len(code):
        op=struct.unpack_from('<H',code,i)[0]
        if op&0xf800==0xf000:
            if i+4>len(code) or struct.unpack_from('<H',code,i+2)[0]&0xf800!=0xf800:
                raise ValueError('expected complete Thumb BL pair')
            mask[i:i+4]=bytes(4)
            i+=4
            continue
        if op&0xf800==0x4800 or op&0xf000==0xd000: mask[i]=0
        if op&0xf800==0xe000: mask[i:i+2]=bytes([0,248])
        i+=2
    return mask


def bind(source, release, address, length):
    code=source[address-0x08000000:address-0x08000000+length]
    mask=relocation_mask(code)
    normalized=bytes(c&m for c,m in zip(code,mask))
    matches=[]
    pos=0
    # The first literal load in CB1 relocates too; use an unmasked anchor.
    anchor=next(i for i in range(length-6) if mask[i:i+6]==b'\xff'*6)
    while (pos:=release.find(code[anchor:anchor+6],pos))>=0:
        start=pos-anchor
        if start>=0 and start%2==0 and bytes(c&m for c,m in zip(release[start:start+length],mask))==normalized:
            matches.append(start+0x08000000)
        pos+=1
    if len(matches)!=1: raise ValueError(f'phase signature has {len(matches)} matches, expected exactly one')
    return {'sourceAddress':address,'releaseAddress':matches[0], 'prefixBytes':length,
            'matchedInstructionSha256':digest(normalized),'matches':len(matches)}


def literal_load(rom, pc):
    op = struct.unpack_from('<H', rom, pc - 0x08000000)[0]
    if op & 0xf800 != 0x4800:
        raise ValueError('expected Thumb PC-relative literal load')
    literal = ((pc + 4) & ~3) + (op & 255) * 4
    return literal, struct.unpack_from('<I', rom, literal - 0x08000000)[0]


def bind_literal(source, release, pc, length, expected):
    if literal_load(source, pc)[1] != expected:
        raise ValueError('source literal no longer references the declared symbol')
    binding = bind(source, release, pc, length)
    literal, value = literal_load(release, binding['releaseAddress'])
    return {**binding, 'sourceValue': expected, 'literalAddress': literal, 'address': value}


def player_symbols(symbol_text, map_text):
    # HandleInputChooseAction is static in THREE controller translation units. Never let
    # a name-keyed dict silently select the Safari/Oak handler instead of the player's.
    match = re.search(r'^\s*\.text\s+(0x[0-9a-f]+)\s+(0x[0-9a-f]+)\s+src/battle_controller_player.o$',
                      map_text, re.M)
    if not match:
        raise ValueError('missing player controller translation-unit range')
    start, size = (int(v, 16) for v in match.groups())
    entries = [p for line in symbol_text.splitlines() if len(p := line.split()) == 4]
    result = {}
    for name in MENU_PREFIXES:
        candidates = [int(p[0], 16) for p in entries
                      if p[3] == name and start <= int(p[0], 16) < start + size]
        if len(candidates) != 1:
            raise ValueError(f'{name}: expected one player-controller symbol')
        result[name] = candidates[0]
    return result


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--upstream',type=Path,required=True)
    parser.add_argument('--source-rom',type=Path,required=True)
    parser.add_argument('--source-symbols',type=Path,required=True)
    parser.add_argument('--source-map',type=Path,required=True)
    parser.add_argument('--release-rom',type=Path,required=True)
    parser.add_argument('--verify',action='store_true')
    args=parser.parse_args()
    spec=importlib.util.spec_from_file_location('layout',Path(__file__).with_name('generate_hns_live_battle_layout.py'))
    layout=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(layout)
    layout.verify_commit(args.upstream)
    if layout.git_head(args.upstream) != layout.PINNED_COMMIT:
        raise ValueError('upstream is not the pinned H&S 2.0.5 commit')
    release=args.release_rom.read_bytes()
    if digest(release)!=ROM_SHA256: raise ValueError('not the official H&S 2.0.5 release identity')
    source=args.source_rom.read_bytes()
    symbols={p[3]:int(p[0],16) for line in args.source_symbols.read_text().splitlines() if len(p:=line.split())==4}
    functions={name:bind(source,release,symbols[name],length) for name,length in [
        ('BattleMainCB1',48),('HandleTurnActionSelectionState',128),('RunTurnActionsFunctions',128)]}
    player = player_symbols(args.source_symbols.read_text(), args.source_map.read_text())
    for name, length in MENU_PREFIXES.items():
        functions[name] = bind(source, release, player[name], length)
    # Compiler scheduling/stack sizes differ after these short, unique prologues.
    # Independently match 64-byte interior instruction signatures as corroboration.
    for name, offset in [('HandleInputChooseMove', 82), ('HandleInputChooseTarget', 68)]:
        interior = bind(source, release, player[name] + offset, 64)
        if not functions[name]['releaseAddress'] < interior['releaseAddress'] < functions[name]['releaseAddress'] + 256:
            raise ValueError('menu interior signature does not belong to its bound entry')
        functions[name]['interior'] = interior
    action, move, target = (player[n] for n in MENU_PREFIXES)
    globals_ = {
        'gActionSelectionCursor': bind_literal(source, release, action + 142, 28, symbols['gActionSelectionCursor']),
        'gMoveSelectionCursor': bind_literal(source, release, move + 180, 32, symbols['gMoveSelectionCursor']),
        'gBattlerControllerFuncs': bind_literal(source, release, move + 282, 32, symbols['gBattlerControllerFuncs']),
    }
    second = bind_literal(source, release, target + 372, 32, symbols['gBattlerControllerFuncs'])
    if second['address'] != globals_['gBattlerControllerFuncs']['address']:
        raise ValueError('independent controller-array references disagree')
    globals_['gBattlerControllerFuncs']['corroboration'] = second
    move_pointer = bind_literal(source, release, target + 374, 32, move | 1)
    if move_pointer['address'] != functions['HandleInputChooseMove']['releaseAddress'] | 1:
        raise ValueError('target handler does not reference the bound move entry')
    functions['HandleInputChooseMove']['reference'] = move_pointer
    # Selection-time refusal text (choice lock, Disable, Taunt, no PP) parks the player's controller
    # in Controller_WaitForString while gBattleMainFunc stays in action selection. Its 24-byte
    # prologue is unique; BtlController_HandlePrintString's stored pointer corroborates it.
    functions['Controller_WaitForString'] = bind(source, release, symbols['Controller_WaitForString'], 24)
    wait_pointer = bind_literal(source, release, symbols['BtlController_HandlePrintString'] + 64, 32,
                                symbols['Controller_WaitForString'] | 1)
    if wait_pointer['address'] != functions['Controller_WaitForString']['releaseAddress'] | 1:
        raise ValueError('print-string handler does not reference the bound wait-for-string entry')
    functions['Controller_WaitForString']['reference'] = wait_pointer
    for name in ('gActionSelectionCursor', 'gMoveSelectionCursor'):
        if globals_[name]['address'] != symbols[name]:
            raise ValueError(f'{name}: release/source EWRAM addresses differ')
    if not 0x03000000 <= globals_['gBattlerControllerFuncs']['address'] <= 0x03008000 - 16:
        raise ValueError('controller array is not in IWRAM')
    cb1=functions['BattleMainCB1']['releaseAddress']
    instruction=struct.unpack_from('<H',release,cb1-0x08000000)[0]
    if instruction&0xff00!=0x4b00: raise ValueError('BattleMainCB1 must start with LDR r3,[pc,#literal]')
    literal=((cb1+4)&~3)+(instruction&255)*4
    slot=struct.unpack_from('<I',release,literal-0x08000000)[0]
    if not 0x03000000<=slot<0x03008000: raise ValueError('dispatch pointer is not IWRAM')
    result={'schemaVersion':1,'upstreamCommit':layout.PINNED_COMMIT,'releaseRomSha256':ROM_SHA256,
            'sourceBuildRomSha256':digest(source),'sourceSymbolsSha256':digest(args.source_symbols.read_bytes()),
            'sourceMapSha256':digest(args.source_map.read_bytes()),
            'functions':functions,'globals':globals_,'gBattleMainFunc':{'literalAddress':literal,'address':slot},
            'sourceFiles':{p:digest((args.upstream/p).read_bytes()) for p in ['src/battle_main.c','src/battle_util.c','src/battle_controller_player.c',
                                                                     'src/battle_controllers.c']}}
    rendered=json.dumps(result,indent=2,sort_keys=True)+'\n'
    if args.verify:
        if OUTPUT.read_text()!=rendered: raise ValueError('release phase evidence is stale')
        print('verified',OUTPUT)
    else:
        OUTPUT.write_text(rendered)
        print('wrote',OUTPUT)


if __name__=='__main__': main()
