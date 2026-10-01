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
import struct
from pathlib import Path

ROM_SHA256 = 'edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b'
OUTPUT = Path(__file__).with_name('release_phase_evidence.json')


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


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--upstream',type=Path,required=True)
    parser.add_argument('--source-rom',type=Path,required=True)
    parser.add_argument('--source-symbols',type=Path,required=True)
    parser.add_argument('--release-rom',type=Path,required=True)
    parser.add_argument('--verify',action='store_true')
    args=parser.parse_args()
    spec=importlib.util.spec_from_file_location('layout',Path(__file__).with_name('generate_hns_live_battle_layout.py'))
    layout=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(layout)
    layout.verify_commit(args.upstream)
    release=args.release_rom.read_bytes()
    if digest(release)!=ROM_SHA256: raise ValueError('not the official H&S 2.0.5 release identity')
    source=args.source_rom.read_bytes()
    symbols={p[3]:int(p[0],16) for line in args.source_symbols.read_text().splitlines() if len(p:=line.split())==4}
    functions={name:bind(source,release,symbols[name],length) for name,length in [
        ('BattleMainCB1',48),('HandleTurnActionSelectionState',128),('RunTurnActionsFunctions',128)]}
    cb1=functions['BattleMainCB1']['releaseAddress']
    instruction=struct.unpack_from('<H',release,cb1-0x08000000)[0]
    if instruction&0xff00!=0x4b00: raise ValueError('BattleMainCB1 must start with LDR r3,[pc,#literal]')
    literal=((cb1+4)&~3)+(instruction&255)*4
    slot=struct.unpack_from('<I',release,literal-0x08000000)[0]
    if not 0x03000000<=slot<0x03008000: raise ValueError('dispatch pointer is not IWRAM')
    result={'schemaVersion':1,'upstreamCommit':layout.PINNED_COMMIT,'releaseRomSha256':ROM_SHA256,
            'sourceBuildRomSha256':digest(source),'sourceSymbolsSha256':digest(args.source_symbols.read_bytes()),
            'functions':functions,'gBattleMainFunc':{'literalAddress':literal,'address':slot},
            'sourceFiles':{p:digest((args.upstream/p).read_bytes()) for p in ['src/battle_main.c','src/battle_util.c']}}
    rendered=json.dumps(result,indent=2,sort_keys=True)+'\n'
    if args.verify:
        if OUTPUT.read_text()!=rendered: raise ValueError('release phase evidence is stale')
        print('verified',OUTPUT)
    else:
        OUTPUT.write_text(rendered)
        print('wrote',OUTPUT)


if __name__=='__main__': main()
