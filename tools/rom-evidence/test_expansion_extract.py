import contextlib
import hashlib
import io
import json
from pathlib import Path
import struct
import tempfile
import unittest

import expansion_extract as ee
from ups_identity import MAX_ROM

ROM_SIZE = 32 * 1024 * 1024


def _encode_gen3(text: str) -> bytes:
    table = {" ": 0x00, "-": 0xAE, ".": 0xAD, "?": 0xAC, "!": 0xAB}
    for i in range(10):
        table[str(i)] = 0xA1 + i
    for i in range(26):
        table[chr(ord("A") + i)] = 0xBB + i
        table[chr(ord("a") + i)] = 0xD5 + i
    return bytes(table[ch] for ch in text) + b"\xFF"


def _build_synthetic_expansion_rom(
    *,
    marker: bytes = b"RHHEXP",
    marker_offset: int = 0x108,
    species_stride: int = 88,
    moves_stride: int = 24,
    items_stride: int = 48,
    include_types: bool = True,
) -> bytearray:
    rom = bytearray(ROM_SIZE)
    rom[0xA0:0xAC] = b"POKEMON EMER"
    rom[0xAC:0xB0] = b"BPEE"
    rom[0xB2] = 0x96
    rom[0xBD] = (-(sum(rom[0xA0:0xBD]) + 0x19)) & 0xFF
    if marker:
        rom[marker_offset:marker_offset + len(marker)] = marker

    species_off = 0x400000
    moves_off = 0x500000
    items_off = 0x600000
    item_names_off = 0x610000
    types_off = 0x700000

    # Populate gSpeciesInfo (stride 88, Grass=12 / Poison=3 for 1..3, Fire=10 for 4)
    for i in range(40):
        if i == 0:
            continue
        row = bytearray(species_stride)
        row[0:6] = bytes([45, 49, 49, 45, 65, 65])
        if i <= 3:
            row[6], row[7] = ee.TYPE_GRASS, ee.TYPE_POISON
        else:
            row[6], row[7] = ee.TYPE_FIRE, ee.TYPE_FIRE
        rom[species_off + i * species_stride:species_off + (i + 1) * species_stride] = row

    # Populate gMovesInfo (stride 24, pointer-prefixed or classic; test both layouts!)
    for i in range(50):
        row = bytearray(moves_stride)
        if i > 0:
            power = 40 if i in (1, 10, 33) else (0 if i in (14, 28) else 80)
            row[1] = power
            row[2] = i % 18
            row[3] = 100
            row[4] = 35
        rom[moves_off + i * moves_stride:moves_off + (i + 1) * moves_stride] = row

    # Populate gItemsInfo (stride 48, pointer-backed Gen 3 names)
    cursor = item_names_off
    for i in range(30):
        row = bytearray(items_stride)
        if i > 0:
            enc = _encode_gen3(f"Item{i}")
            rom[cursor:cursor + len(enc)] = enc
            struct.pack_into("<I", row, 0, ee.ROM_BASE + cursor)
            cursor += len(enc)
        rom[items_off + i * items_stride:items_off + (i + 1) * items_stride] = row

    struct.pack_into("<I", rom, ee.GF_SLOT_SPECIES_INFO, ee.ROM_BASE + species_off)
    struct.pack_into("<I", rom, ee.GF_SLOT_ITEMS_INFO, ee.ROM_BASE + items_off)
    struct.pack_into("<I", rom, ee.GF_SLOT_MOVES_INFO, ee.ROM_BASE + moves_off)

    if include_types:
        dim = 19
        vals = [0x1000] * (dim * dim)
        vals[7] = 0x0000  # Normal -> Ghost = 0
        struct.pack_into(f"<{dim * dim}H", rom, types_off, *vals)
        struct.pack_into("<I", rom, 0x1EC, ee.ROM_BASE + types_off)

    return rom


class ExpansionExtractTest(unittest.TestCase):
    def test_valid_expansion_rom_discovery(self):
        rom = bytes(_build_synthetic_expansion_rom())
        report = ee.extract_expansion_tables(rom)
        self.assertEqual(report["status"], "OK")
        self.assertTrue(report["gf_header_valid"])
        self.assertEqual(report["rom_sha256"], hashlib.sha256(rom).hexdigest())
        self.assertEqual(report["expansion_marker"], {
            "marker": "RHHEXP",
            "file_offset": "0x00000108",
            "in_rom_header_window": True,
        })
        tables = report["tables"]
        self.assertTrue(tables["gSpeciesInfo"]["sanity_ok"])
        self.assertEqual(tables["gSpeciesInfo"]["stride"], 88)
        self.assertEqual(tables["gSpeciesInfo"]["count_estimate"], 40)
        self.assertTrue(tables["gMovesInfo"]["sanity_ok"])
        self.assertEqual(tables["gMovesInfo"]["stride"], 24)
        self.assertEqual(tables["gMovesInfo"]["count_estimate"], 50)
        self.assertTrue(tables["gItemsInfo"]["sanity_ok"])
        self.assertEqual(tables["gItemsInfo"]["stride"], 48)
        self.assertEqual(tables["gItemsInfo"]["count_estimate"], 30)
        self.assertTrue(tables["gTypesInfo"]["sanity_ok"])
        self.assertEqual(tables["gTypesInfo"]["count_estimate"], 19)
        self.assertNotIn("Item1", json.dumps(report))

    def test_rhh_exp_marker_outside_header_window_and_pointer_prefixed_moves(self):
        rom = _build_synthetic_expansion_rom(
            marker=b"RHH_EXP", marker_offset=0x80000, moves_stride=32, include_types=False
        )
        # Re-encode moves_off with pointer-prefixed layout: power at +8, type at +12, acc at +10, pp at +11
        moves_off = 0x500000
        rom[moves_off:moves_off + 50 * 32] = b"\x00" * (50 * 32)
        for i in range(1, 50):
            row = bytearray(32)
            row[8] = 40 if i in (1, 10, 33) else (0 if i in (14, 28) else 80)
            row[10] = 100
            row[11] = 35
            row[12] = i % 18
            rom[moves_off + i * 32:moves_off + (i + 1) * 32] = row
        report = ee.extract_expansion_tables(bytes(rom))
        self.assertEqual(report["status"], "OK")
        self.assertFalse(report["expansion_marker"]["in_rom_header_window"])
        self.assertEqual(report["tables"]["gMovesInfo"]["stride"], 32)
        self.assertFalse(report["tables"]["gTypesInfo"]["sanity_ok"])
        self.assertEqual(report["tables"]["gTypesInfo"]["reason"], "not_in_gf_header")

    def test_missing_marker_refusal(self):
        rom = bytes(_build_synthetic_expansion_rom(marker=b""))
        report = ee.extract_expansion_tables(rom)
        self.assertEqual(report["status"], "NOT_EXPANSION")
        self.assertFalse(report["gf_header_valid"])
        self.assertIsNone(report["expansion_marker"])

    def test_out_of_range_pointer_and_sanity_failures(self):
        rom = _build_synthetic_expansion_rom()
        struct.pack_into("<I", rom, ee.GF_SLOT_SPECIES_INFO, 0x02020000)  # EWRAM -> out of ROM
        report = ee.extract_expansion_tables(bytes(rom))
        self.assertEqual(report["status"], "INVALID_HEADER")
        self.assertFalse(report["gf_header_valid"])
        self.assertFalse(report["tables"]["gSpeciesInfo"]["sanity_ok"])
        self.assertEqual(report["tables"]["gSpeciesInfo"]["reason"], "pointer_out_of_range")

        # Valid pointer, but move 33 power is wrong (not 40) -> sanity failure
        rom2 = _build_synthetic_expansion_rom()
        rom2[0x500000 + 33 * 24 + 1] = 99
        report2 = ee.extract_expansion_tables(bytes(rom2))
        self.assertEqual(report2["status"], "PARTIAL")
        self.assertFalse(report2["tables"]["gMovesInfo"]["sanity_ok"])
        self.assertIsNone(report2["tables"]["gMovesInfo"]["table_sha256"])

    def test_size_cap_and_cli_refusal(self):
        with self.assertRaises(ValueError):
            ee.extract_expansion_tables(b"\x00" * (ee.GF_HEADER_END - 1))
        with tempfile.TemporaryDirectory() as tmp:
            oversize = Path(tmp) / "oversize.gba"
            with oversize.open("wb") as f:
                f.seek(MAX_ROM)
                f.write(b"\x00")
            err = io.StringIO()
            with contextlib.redirect_stderr(err):
                rc = ee.main(["--rom", str(oversize)])
            self.assertEqual(rc, 2)

            rom_path = Path(tmp) / "exp.gba"
            rom_path.write_bytes(bytes(_build_synthetic_expansion_rom()))
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                rc = ee.main(["--rom", str(rom_path)])
            self.assertEqual(rc, 0)
            self.assertEqual(json.loads(out.getvalue())["status"], "OK")


if __name__ == "__main__":
    unittest.main()
