#!/usr/bin/env python3
"""Bounded inspection of one exact ROM's embedded symbols; no production authority."""
import argparse
import json
import struct
import sys
from ups_identity import MAX_ROM, identity, read_local, require

ROM_SHA256 = 'fdf6a69e9556efd4b7ff96829c0fba85072e6d91c3ec96faed744765c4a772d3'
TABLE = 0x08fb0670
COUNT = 150
NAMES = ('gPlayerParty', 'gPlayerPartyCount', 'gEnemyParty', 'gEnemyPartyCount',
         'gSaveBlock1Ptr', 'gSaveBlock2Ptr')


def symbol_table(rom, address, count):
    """Parse pointer/address pairs without dereferencing their target addresses."""
    offset = address - 0x08000000
    require(0 < count <= 512 and 0 <= offset <= len(rom) - count * 8,
            'symbol table outside ROM')
    symbols = {}
    for i in range(count):
        pointer, value = struct.unpack_from('<II', rom, offset + i * 8)
        start = pointer - 0x08000000
        require(0 <= start < len(rom), 'symbol name pointer outside ROM')
        end = rom.find(b'\0', start, min(len(rom), start + 128))
        require(end > start, 'empty or unterminated symbol name')
        raw = rom[start:end]
        require(all(c == 95 or 48 <= c <= 57 or 65 <= c <= 90 or 97 <= c <= 122
                    for c in raw), 'invalid symbol name')
        name = raw.decode('ascii')
        require(name not in symbols, 'duplicate symbol name')
        symbols[name] = value
    return symbols


def inspect_rom(rom):
    require(identity(rom)['sha256'] == ROM_SHA256, 'exact diagnostic ROM identity mismatch')
    symbols = symbol_table(rom, TABLE, COUNT)
    require(all(name in symbols for name in NAMES), 'required symbols missing')
    return dict(schema_version=1, rom=identity(rom), table_address=f'0x{TABLE:08x}',
                entry_count=COUNT, symbols={name: f'0x{symbols[name]:08x}' for name in NAMES},
                status='STATIC_SYMBOLS', runtime_layout='NOT_VERIFIED')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--rom', required=True)
    args = parser.parse_args()
    try:
        print(json.dumps(inspect_rom(read_local(args.rom, MAX_ROM)), sort_keys=True, indent=2))
    except (ValueError, OSError) as error:
        print(f'FAIL: {error}', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
