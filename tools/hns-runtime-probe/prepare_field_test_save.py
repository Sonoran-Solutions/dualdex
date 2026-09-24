#!/usr/bin/env python3
"""Prepare an H&S 2.0.5 save file with specific battle moves for field status testing.

This developer script takes a valid battery save (e.g. from make-save.sh) and sets the
first party Pokémon's moves to the specified move IDs, re-encrypting the BoxPokemon
data and recalculating the Pokémon and save sector checksums.

Usage:
  python3 prepare_field_test_save.py <input.sav> <output.sav> [move1 move2 move3 move4]

Default moves if omitted:
  move1 = 33  (Tackle)
  move2 = 478 (Magic Room)
  move3 = 433 (Trick Room)
  move4 = 604 (Electric Terrain)
"""
import struct
import sys
from pathlib import Path

SUBSTRUCT_ORDERS = [
    "GAEM", "GAME", "GEAM", "GEMA", "GMAE", "GMEA",
    "AGEM", "AGME", "AEGM", "AEMG", "AMGE", "AMEG",
    "EGAM", "EGMA", "EAGM", "EAMG", "EMGA", "EMAG",
    "MGAE", "MGEA", "MAGE", "MAEG", "MEGA", "MEAG"
]


def prepare_save(input_path: Path, output_path: Path, moves: list[int]):
    with open(input_path, "rb") as f:
        sav = bytearray(f.read())

    if len(sav) != 131072:
        raise ValueError(f"Save file must be 128KB (131072 bytes), got {len(sav)}")

    mon_offset = 0x1023C
    pid, otid = struct.unpack("<II", sav[mon_offset:mon_offset+8])
    key = pid ^ otid

    order = SUBSTRUCT_ORDERS[pid % 24]

    raw_data = bytearray(sav[mon_offset+32:mon_offset+80])
    dec_words = [struct.unpack("<I", raw_data[i:i+4])[0] ^ key for i in range(0, 48, 4)]
    dec_bytes = bytearray(b"".join(struct.pack("<I", w) for w in dec_words))

    blocks = {}
    for i, letter in enumerate(order):
        blocks[letter] = dec_bytes[i*12:(i+1)*12]

    # Attacks block: move1, move2, move3, move4, pp1, pp2, pp3, pp4
    m1, m2, m3, m4 = moves
    blocks["A"] = struct.pack("<HHHHBBBB", m1, m2, m3, m4, 35, 10, 10, 10)

    new_dec = bytearray()
    for letter in order:
        new_dec.extend(blocks[letter])

    # Recalculate Pokemon checksum
    new_chk = sum(struct.unpack("<24H", new_dec)) & 0xFFFF
    struct.pack_into("<H", sav, mon_offset+28, new_chk)

    # Re-encrypt
    new_enc = bytearray()
    for i in range(0, 48, 4):
        w = struct.unpack("<I", new_dec[i:i+4])[0]
        new_enc.extend(struct.pack("<I", w ^ key))

    sav[mon_offset+32:mon_offset+80] = new_enc

    # Recalculate Sector 1 checksum (0x10000 to 0x10FFC)
    s = 0
    for i in range(0, 4084, 4):
        s += struct.unpack("<I", sav[0x10000+i:0x10000+i+4])[0]
    calc_chk = ((s >> 16) + s) & 0xFFFF
    struct.pack_into("<H", sav, 0x10000+4086, calc_chk)

    with open(output_path, "wb") as f:
        f.write(sav)
    print(f"Wrote modified save to {output_path} (moves: {moves})")


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(1)

    input_sav = Path(sys.argv[1])
    output_sav = Path(sys.argv[2])
    if len(sys.argv) >= 7:
        moves = [int(x) for x in sys.argv[3:7]]
    else:
        moves = [33, 478, 433, 604]  # Tackle, Magic Room, Trick Room, Electric Terrain

    prepare_save(input_sav, output_sav, moves)


if __name__ == "__main__":
    main()
