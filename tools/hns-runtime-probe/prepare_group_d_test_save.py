#!/usr/bin/env python3
"""Developer-only controlled PARTY fixtures for official-ROM reader evidence.

Never modifies a ROM or live RAM. Reads a fresh make-save.sh battery, copies its
lead into a requested party, and changes only specified party operands. These
are synthetic party fixtures, NOT claims of normally obtainable player saves.
Runtime volatile/timer/counter/turn-order values are never written: the official
ROM must establish them through ordinary battle inputs. All structure locations
and bitfields are compiled from the pinned source. Saves stay outside Git.
"""
import argparse
import importlib.util
import json
import struct
import tempfile
from pathlib import Path

ORDER = ['GAEM','GAME','GEAM','GEMA','GMAE','GMEA','AGEM','AGME','AEGM','AEMG','AMGE','AMEG',
         'EGAM','EGMA','EAGM','EAMG','EMGA','EMAG','MGAE','MGEA','MAGE','MAEG','MEGA','MEAG']


def authority(upstream):
    path = Path(__file__).resolve().parents[1] / 'hns-layout/generate_hns_live_battle_layout.py'
    spec = importlib.util.spec_from_file_location('layout', path)
    layout = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(layout)
    layout.verify_commit(upstream)
    fields = {'party': ('SaveBlock1','playerParty'), 'count': ('SaveBlock1','playerPartyCount'),
              'pid': ('BoxPokemon','personality'), 'otid': ('BoxPokemon','otId'),
              'secure': ('BoxPokemon','secure'), 'checksum': ('BoxPokemon','checksum'),
              'hp': ('Pokemon','hp'), 'maxHP': ('Pokemon','maxHP'),
              'attack': ('Pokemon','attack'), 'defense': ('Pokemon','defense'),
              'spAttack': ('Pokemon','spAttack'), 'spDefense': ('Pokemon','spDefense'),
              'speed': ('Pokemon','speed'), 'sectorId': ('SaveSector','id'),
              'sectorChecksum': ('SaveSector','checksum'), 'signature': ('SaveSector','signature')}
    source = '#include "global.h"\n#include "pokemon.h"\n#include "save.h"\n'
    source += '\n'.join(f'const unsigned d_{k}=__builtin_offsetof(struct {t},{f});' for k,(t,f) in fields.items())
    source += '\nconst unsigned d_size=sizeof(struct Pokemon);\nconst unsigned d_sectorSize=sizeof(struct SaveSector);\n'
    source += 'const struct PokemonSubstruct0 d_species={.species=2047};\n'
    source += 'const struct PokemonSubstruct0 d_item={.heldItem=1023};\n'
    source += 'const struct PokemonSubstruct3 d_ability={.abilityNum=3};\n'
    compiler = layout.find_arm_gcc(None)
    with tempfile.TemporaryDirectory() as temp:
        obj = layout.compile_probe(compiler,source,Path(temp),[str(upstream/'include'),str(upstream)])
        blob,base = layout._read_rodata(obj,compiler)
        syms = layout.read_symbols(obj,compiler)
        result = {k:layout.scalar(blob,base,syms,'d_'+k) for k in [*fields,'size','sectorSize']}
        for name in ['species','item','ability']:
            result[name] = layout.multi_bit(blob,base,syms,'d_'+name)
    return result


def set_bits(block, descriptor, value):
    bit,width = descriptor
    if not 0 <= value < (1<<width): raise ValueError('operand out of compiled domain')
    number=int.from_bytes(block,'little')
    number=(number & ~(((1<<width)-1)<<bit)) | (value<<bit)
    return bytearray(number.to_bytes(len(block),'little'))


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('input',type=Path)
    parser.add_argument('output',type=Path)
    parser.add_argument('--upstream',type=Path,required=True)
    parser.add_argument('--party',required=True,help='JSON list: species, abilitySlot, item, moves (4 IDs)')
    args=parser.parse_args()
    if args.input.resolve()==args.output.resolve(): raise ValueError('output must be a distinct fixture')
    a=authority(args.upstream)
    party=json.loads(args.party)
    if not 1<=len(party)<=6: raise ValueError('party size')
    sav=bytearray(args.input.read_bytes())
    if len(sav)!=131072: raise ValueError('expected 128 KiB battery')
    updated=0
    for sector in range(28):
        start=sector*a['sectorSize']
        if struct.unpack_from('<I',sav,start+a['signature'])[0]!=0x08012025: continue
        if struct.unpack_from('<H',sav,start+a['sectorId'])[0]!=1: continue
        lead=start+a['party']
        template=sav[lead:lead+a['size']]
        if sav[start+a['count']]!=1: raise ValueError('requires make-save.sh single-starter input')
        for i,entry in enumerate(party):
            mon=bytearray(template)
            pid=struct.unpack_from('<I',mon,a['pid'])[0]
            key=pid ^ struct.unpack_from('<I',mon,a['otid'])[0]
            data=bytearray().join(struct.pack('<I',w^key) for w in struct.unpack_from('<12I',mon,a['secure']))
            if sum(struct.unpack('<24H',data))&65535 != struct.unpack_from('<H',mon,a['checksum'])[0]:
                raise ValueError('input Pokemon checksum mismatch')
            order=ORDER[pid%24]
            blocks={letter:data[j*12:(j+1)*12] for j,letter in enumerate(order)}
            blocks['G']=set_bits(blocks['G'],a['species'],entry['species'])
            blocks['G']=set_bits(blocks['G'],a['item'],entry.get('item',0))
            blocks['M']=set_bits(blocks['M'],a['ability'],entry.get('abilitySlot',0))
            moves=entry.get('moves',[150,33,285,52])
            if len(moves)!=4 or any(not 0<=m<2048 for m in moves): raise ValueError('moves')
            # Pinned PokemonSubstruct1 declares four consecutive u16 move:11 fields,
            # followed by four pp:7 bytes. Preserve unrelated upper bitfields.
            for j,move in enumerate(moves):
                old=struct.unpack_from('<H',blocks['A'],j*2)[0]
                struct.pack_into('<H',blocks['A'],j*2,(old&~2047)|move)
                blocks['A'][8+j]=(blocks['A'][8+j]&128)|40
            data=bytearray().join(blocks[letter] for letter in order)
            struct.pack_into('<H',mon,a['checksum'],sum(struct.unpack('<24H',data))&65535)
            mon[a['secure']:a['secure']+48]=b''.join(struct.pack('<I',w^key) for w in struct.unpack('<12I',data))
            # Long-lived, low-damage fixture so bounded status turns can expose timers.
            for name,value in {'hp':500,'maxHP':500,'attack':10,'spAttack':10,'defense':200,'spDefense':200,'speed':10}.items():
                struct.pack_into('<H',mon,a[name],value)
            sav[lead+i*a['size']:lead+(i+1)*a['size']]=mon
        sav[start+a['count']]=len(party)
        total=sum(struct.unpack_from('<'+str(a['sectorId']//4)+'I',sav,start)) & 0xffffffff
        struct.pack_into('<H',sav,start+a['sectorChecksum'],((total>>16)+total)&65535)
        updated+=1
    if not updated: raise ValueError('no valid SaveBlock1 first sector')
    args.output.write_bytes(sav)
    print(json.dumps({'upstream':str(args.upstream),'compiled':a,'party':party,'sectorsUpdated':updated},sort_keys=True))


if __name__=='__main__': main()
