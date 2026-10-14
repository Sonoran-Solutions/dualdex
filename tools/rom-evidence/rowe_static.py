#!/usr/bin/env python3
"""Read exact-ROM Thumb accessor facts. Emits semantics/digests, never code bytes."""
import argparse,json,struct,sys
import hashlib
from pathlib import Path
from ups_identity import read_local,MAX_ROM,identity,require
from rowe_symbols import ROM_SHA256


def half(rom,address):
    offset=address-0x08000000
    require(address%2==0 and 0<=offset<=len(rom)-2,'instruction outside ROM')
    return struct.unpack_from('<H',rom,offset)[0]


def load_instruction(value):
    kind=value&0xf800
    require(kind in (0x6800,0x7800,0x8800),'expected Thumb immediate load')
    size={0x6800:4,0x7800:1,0x8800:2}[kind]
    return dict(bytes=size,offset=((value>>6)&31)*size,base=(value>>3)&7,destination=value&7)


def shift_instruction(value):
    require(value&0xf800 in (0x0000,0x0800),'expected logical immediate shift')
    return dict(direction='left' if value&0xf800==0 else 'right',
                amount=(((value>>6)&31) or (32 if value&0xf800==0x0800 else 0)),source=(value>>3)&7,destination=value&7)


def extraction(rom,address,bias=0):
    load=load_instruction(half(rom,address));tail=address+2
    branch=half(rom,tail)
    if branch&0xf800==0xe000:
        immediate=branch&2047
        if immediate&1024:immediate-=2048
        tail=tail+4+immediate*2
    left=shift_instruction(half(rom,tail));right=shift_instruction(half(rom,tail+2))
    require(left['direction']=='left' and right['direction']=='right' and
            left['source']==load['destination'] and right['source']==left['destination'],
            'inconsistent extraction dataflow')
    bit=right['amount']-left['amount'];width=32-right['amount']
    require(0<=bit and 0<width<=load['bytes']*8-bit,'invalid extraction width')
    return dict(offset=bias+load['offset'],storage_bytes=load['bytes'],bit=bit,width=width,
                instruction_address=f'0x{address:08x}')


def inspect(rom):
    # ponytail: selected stored-field paths only; add exact override/egg control-flow
    # checks before widening this evidence into effective gameplay semantics.
    require(identity(rom)['sha256']==ROM_SHA256,'exact diagnostic ROM identity mismatch')
    fields={}
    for name,address in [('pid',0x809b2c0),('otid',0x809b2c4),('status',0x809b09c),('hp',0x809b0ce)]:
        load=load_instruction(half(rom,address));require(load['base']==4,'unexpected slot base')
        fields[name]=dict(offset=load['offset'],width=load['bytes']*8,instruction_address=f'0x{address:08x}')
    for name,address,bias in [('species',0x809b4d0,0),('form',0x809b344,0),
                              ('move1',0x809b376,0),('move2',0x809b394,0),
                              ('move3',0x809b3b2,0),('move4',0x809b3d4,0),
                              ('experience',0x809b350,0),('nature',0x809b544,39)]:
        fields[name]=extraction(rom,address,bias)
    require(half(rom,0x809b542)==0x3027,'nature offset changed')
    for name,add,load in [('level',0x809b0ba,0x809b0bc),('max_hp',0x809b0d4,0x809b0d6),
                         ('attack',0x809b0dc,0x809b0de),('defense',0x809b0e4,0x809b0e6),
                         ('speed',0x809b0ec,0x809b0ee),('sp_attack',0x809b0f4,0x809b0f6),
                         ('sp_defense',0x809b0fc,0x809b0fe)]:
        instruction=half(rom,add);require(instruction&0xff00==0x3000,'expected slot offset addition')
        memory=load_instruction(half(rom,load));require(memory['base']==0 and memory['offset']==0,'expected indirect field load')
        fields[name]=dict(offset=instruction&255,width=memory['bytes']*8,instruction_address=f'0x{load:08x}')
    # Verify jump-table dispatch ties the selected extraction paths to accessor selectors.
    selectors={0:0x809b2c0,1:0x809b2c4,2:0x809b2d4,11:0x809b4d0,13:0x809b36a,
               14:0x809b388,15:0x809b3a6,16:0x809b3c8,25:0x809b350,89:0x809b344,90:0x809b540}
    for selector,target in selectors.items():
        require(struct.unpack_from('<I',rom,0x9b13c+selector*4)[0]==target,'accessor dispatch changed')
    require(half(rom,0x809b2fc)==0x3308 and half(rom,0x809b308)==0x2a0b,'nickname copy bound changed')
    fields['nickname']=dict(offset=8,bytes=12,loop_address='0x0809b2fe')
    require(half(rom,0x809b10c)==0x3023 and load_instruction(half(rom,0x809b10e))['bytes']==1,'PP accessor changed')
    for selector in range(17,21):
        require(struct.unpack_from('<I',rom,0x9af7c+(selector-17)*4)[0]==0x809b10a,'PP dispatch changed')
    fields['pp']=dict(offsets=[52,53,54,55],width=8,instruction_address='0x0809b10e')
    require(half(rom,0x809be3e)&0xf800==0x7000 and half(rom,0x809be52)&0xf800==0x7800,'count is not byte load/store')
    require(struct.unpack_from('<I',rom,0x9be44)[0]==0x02025125,'count literal changed')
    require(half(rom,0x809be5a)==0x204c and half(rom,0x809be5c)==0x4348 and
            struct.unpack_from('<I',rom,0x9be78)[0]==0x02025128,'indexed party arithmetic changed')
    spans=[(0x801823c,0x98),(0x809af5c,0x624),(0x809bd40,0x3c),(0x809be38,0x44)]
    return dict(schema_version=3,status='STATIC',inspector_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),rom_sha256=ROM_SHA256,count=dict(address='0x02025125',width=8,load='0x0809be52',store='0x0809be3e'),stride=dict(bytes=76,multiply='0x0809be5c',indexed_base='0x02025128'),fields=fields,spans=[dict(address=f'0x{a:08x}',bytes=n,sha256=hashlib.sha256(rom[a-0x8000000:a-0x8000000+n]).hexdigest()) for a,n in spans])


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--rom',required=True);args=parser.parse_args()
    try:print(json.dumps(inspect(read_local(args.rom,MAX_ROM)),sort_keys=True,indent=2))
    except (ValueError,OSError,struct.error) as error:print('FAIL:',error,file=sys.stderr);return 1
    return 0


if __name__=='__main__':sys.exit(main())
