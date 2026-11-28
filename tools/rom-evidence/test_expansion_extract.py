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
    table = {" ": 0x00, "\n": 0xFE, "-": 0xAE, ".": 0xAD, "?": 0xAC, "!": 0xAB}
    for i in range(10):
        table[str(i)] = 0xA1 + i
    for i in range(26):
        table[chr(ord("A") + i)] = 0xBB + i
        table[chr(ord("a") + i)] = 0xD5 + i
    return bytes(table[ch] for ch in text) + b"\xFF"


def _pack_expansion_move_words(
    *,
    move_type: int,
    category: int,
    power: int,
    accuracy: int,
    target: int = 0,
) -> tuple[int, int]:
    combat_word = (move_type & 0x1F) | ((category & 0x03) << 5) | ((power & 0x1FF) << 7)
    acc_word = (accuracy & 0x7F) | ((target & 0x1FF) << 7)
    return combat_word, acc_word


def _build_synthetic_expansion_rom(
    *,
    marker: bytes = b"RHHEXP",
    marker_offset: int = 0x108,
    layout: str = ee.LAYOUT_EXPANSION_V1_9_BITFIELD,
    species_stride: int = 88,
    moves_stride: int = 36,
    items_stride: int = 64,
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
    string_pool = 0x620000
    types_off = 0x700000
    is_packed_expansion = layout in (
        ee.LAYOUT_EXPANSION_V1_9_BITFIELD,
        ee.LAYOUT_EXPANSION_V1_10_PTR_ITEM,
    )

    def alloc_str(text: str) -> int:
        nonlocal string_pool
        enc = _encode_gen3(text)
        addr = ee.ROM_BASE + string_pool
        rom[string_pool:string_pool + len(enc)] = enc
        string_pool = (string_pool + len(enc) + 3) & ~3
        return addr

    # Populate gSpeciesInfo (row 0 = SPECIES_NONE sentinel: zero base stats + non-zero metadata)
    for i in range(40):
        row = bytearray(species_stride)
        if i == 0:
            if is_packed_expansion:
                row[6], row[7] = ee.TYPE_NORMAL, ee.TYPE_NORMAL
                enc_none = _encode_gen3("??????????")
                if species_stride >= 0x20:
                    row[0x10:0x10 + len(enc_none)] = enc_none
                    struct.pack_into("<I", row, species_stride - 4, ee.ROM_BASE + 0x710000)
            rom[species_off:species_off + species_stride] = row
            continue
        row[0:6] = bytes([45, 49, 49, 45, 65, 65])
        if is_packed_expansion:
            if i <= 3:
                row[6], row[7] = ee.TYPE_GRASS, ee.TYPE_POISON
            elif i == 4:
                row[6], row[7] = ee.TYPE_FIRE, ee.TYPE_NONE
            else:
                row[6], row[7] = ee.TYPE_FAIRY, ee.TYPE_NORMAL
        else:
            if i <= 3:
                row[6], row[7] = ee.GEN3_TYPE_GRASS, ee.GEN3_TYPE_POISON
            else:
                row[6], row[7] = ee.GEN3_TYPE_FIRE, ee.GEN3_TYPE_FIRE
        rom[species_off + i * species_stride:species_off + (i + 1) * species_stride] = row

    # Populate gMovesInfo (row 0 = MOVE_NONE sentinel: power=0, empty description pointer)
    for i in range(50):
        row = bytearray(moves_stride)
        if i == 0:
            if is_packed_expansion:
                struct.pack_into("<I", row, 0x00, alloc_str("-"))
                struct.pack_into("<I", row, 0x04, alloc_str(""))
                combat_word, acc_word = _pack_expansion_move_words(
                    move_type=ee.TYPE_NONE, category=2, power=0, accuracy=0, target=0
                )
                struct.pack_into("<HHB", row, 0x0A, combat_word, acc_word, 0)
        else:
            if i in (1, 33):
                power, mtype, cat = 40, (ee.TYPE_NORMAL if is_packed_expansion else 0), 0
            elif i in (14, 28):
                power, mtype, cat = 0, (ee.TYPE_NORMAL if is_packed_expansion else 0), 2
            elif i == 35 and is_packed_expansion:
                # Exercise 9-bit power > 255 and TYPE_FAIRY=19
                power, mtype, cat = 300, ee.TYPE_FAIRY, 1
            else:
                power = 80
                mtype = ee.TYPE_FAIRY if is_packed_expansion else (i % 18)
                cat = 1

            if is_packed_expansion:
                struct.pack_into("<I", row, 0x00, alloc_str(f"Move{i}"))
                struct.pack_into("<I", row, 0x04, alloc_str(f"Desc for\nmove {i}."))
                combat_word, acc_word = _pack_expansion_move_words(
                    move_type=mtype, category=cat, power=power, accuracy=100, target=1
                )
                struct.pack_into("<HHB", row, 0x0A, combat_word, acc_word, 35)
            else:
                row[1] = power
                row[2] = mtype
                row[3] = 100
                row[4] = 35
        rom[moves_off + i * moves_stride:moves_off + (i + 1) * moves_stride] = row

    # Populate gItemsInfo (row 0 = ITEM_NONE sentinel)
    for i in range(30):
        row = bytearray(items_stride)
        if i == 0:
            if layout == ee.LAYOUT_EXPANSION_V1_9_BITFIELD:
                struct.pack_into("<I", row, 0x0C, alloc_str(""))
                enc0 = _encode_gen3("????????")
                row[0x14:0x14 + len(enc0)] = enc0
            elif layout == ee.LAYOUT_EXPANSION_V1_10_PTR_ITEM:
                struct.pack_into("<I", row, 0x0C, alloc_str(""))
                struct.pack_into("<I", row, 0x14, alloc_str("????????"))
        else:
            if layout == ee.LAYOUT_EXPANSION_V1_9_BITFIELD:
                struct.pack_into("<I", row, 0x0C, alloc_str(f"Item desc\nline {i}."))
                enc = _encode_gen3(f"Item{i}")[:20]
                if enc[-1] != 0xFF:
                    enc = enc[:-1] + b"\xFF"
                row[0x14:0x14 + len(enc)] = enc
                # Verify pluralName at +0x28 does not interfere with inline name[20] at +0x14..+0x27
                if items_stride >= 0x3C:
                    plural_enc = _encode_gen3(f"Items{i}")[:20]
                    if plural_enc[-1] != 0xFF:
                        plural_enc = plural_enc[:-1] + b"\xFF"
                    row[0x28:0x28 + len(plural_enc)] = plural_enc
            elif layout == ee.LAYOUT_EXPANSION_V1_10_PTR_ITEM:
                struct.pack_into("<I", row, 0x0C, alloc_str(f"Item desc\nline {i}."))
                struct.pack_into("<I", row, 0x14, alloc_str(f"Item{i}"))
            else:
                enc = _encode_gen3(f"Item{i}")[:14]
                if enc[-1] != 0xFF:
                    enc = enc[:-1] + b"\xFF"
                row[0:len(enc)] = enc
                struct.pack_into("<I", row, 0x14, alloc_str(f"Item desc\nline {i}."))
        rom[items_off + i * items_stride:items_off + (i + 1) * items_stride] = row

    struct.pack_into("<I", rom, ee.GF_SLOT_SPECIES_INFO, ee.ROM_BASE + species_off)
    struct.pack_into("<I", rom, ee.GF_SLOT_ITEMS_INFO, ee.ROM_BASE + items_off)
    struct.pack_into("<I", rom, ee.GF_SLOT_MOVES_INFO, ee.ROM_BASE + moves_off)

    if include_types:
        dim = 21  # NUMBER_OF_MON_TYPES: TYPE_NONE(0) .. TYPE_STELLAR(20)
        vals = [0x1000] * (dim * dim)
        vals[ee.TYPE_NORMAL * dim + ee.TYPE_GHOST] = 0x0000
        vals[ee.TYPE_FIRE * dim + ee.TYPE_GRASS] = 0x2000
        vals[ee.TYPE_DRAGON * dim + ee.TYPE_FAIRY] = 0x0000
        struct.pack_into(f"<{dim * dim}H", rom, types_off, *vals)
        struct.pack_into("<I", rom, 0x1EC, ee.ROM_BASE + types_off)

    return rom


class ExpansionExtractTest(unittest.TestCase):
    def test_valid_expansion_rom_discovery(self):
        rom = bytes(_build_synthetic_expansion_rom())
        report = ee.extract_expansion_tables(rom)
        self.assertEqual(report["status"], "OK")
        self.assertEqual(report["layout_version"], ee.LAYOUT_EXPANSION_V1_9_BITFIELD)
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
        self.assertEqual(tables["gMovesInfo"]["stride"], 36)
        self.assertEqual(tables["gMovesInfo"]["count_estimate"], 50)
        self.assertTrue(tables["gItemsInfo"]["sanity_ok"])
        self.assertEqual(tables["gItemsInfo"]["stride"], 64)
        self.assertEqual(tables["gItemsInfo"]["count_estimate"], 30)
        self.assertEqual(tables["gItemsInfo"]["item_layout"], "EXPANSION_V1_9_INLINE_ITEM")
        self.assertTrue(tables["gTypesInfo"]["sanity_ok"])
        self.assertEqual(tables["gTypesInfo"]["count_estimate"], 21)
        self.assertNotIn("Item1", json.dumps(report))

        # Verify EXPANSION_V1_9_INLINE_ITEM alias resolves to EXPANSION_V1_9_BITFIELD
        alias_report = ee.extract_expansion_tables(rom, layout="EXPANSION_V1_9_INLINE_ITEM")
        self.assertEqual(alias_report["status"], "OK")
        self.assertEqual(alias_report["layout_version"], ee.LAYOUT_EXPANSION_V1_9_BITFIELD)

    def test_pointer_name_item_variant_has_distinct_layout_id(self):
        rom = bytes(_build_synthetic_expansion_rom(
            layout=ee.LAYOUT_EXPANSION_V1_10_PTR_ITEM,
            items_stride=44,
        ))
        report = ee.extract_expansion_tables(rom)
        self.assertEqual(report["status"], "OK")
        self.assertEqual(report["layout_version"], ee.LAYOUT_EXPANSION_V1_10_PTR_ITEM)
        self.assertTrue(report["tables"]["gSpeciesInfo"]["sanity_ok"])
        self.assertTrue(report["tables"]["gMovesInfo"]["sanity_ok"])
        self.assertTrue(report["tables"]["gItemsInfo"]["sanity_ok"])
        self.assertEqual(report["tables"]["gItemsInfo"]["stride"], 44)
        self.assertEqual(report["tables"]["gItemsInfo"]["item_layout"], "EXPANSION_V1_10_PTR_ITEM")

        # Forcing v1.9.0 inline-item layout on a pointer-name item ROM must reject gItemsInfo.
        forced_v19 = ee.extract_expansion_tables(rom, layout=ee.LAYOUT_EXPANSION_V1_9_BITFIELD)
        self.assertEqual(forced_v19["status"], "PARTIAL")
        self.assertFalse(forced_v19["tables"]["gItemsInfo"]["sanity_ok"])

        # Forcing pointer-name item layout on a v1.9.0 inline-item ROM must reject gItemsInfo.
        v19_rom = bytes(_build_synthetic_expansion_rom(layout=ee.LAYOUT_EXPANSION_V1_9_BITFIELD))
        forced_v110 = ee.extract_expansion_tables(v19_rom, layout=ee.LAYOUT_EXPANSION_V1_10_PTR_ITEM)
        self.assertEqual(forced_v110["status"], "PARTIAL")
        self.assertFalse(forced_v110["tables"]["gItemsInfo"]["sanity_ok"])

    def test_rhh_exp_marker_outside_header_window_and_gen3_vanilla_byte_layout(self):
        rom = _build_synthetic_expansion_rom(
            marker=b"RHH_EXP",
            marker_offset=0x80000,
            layout=ee.LAYOUT_GEN3_VANILLA_BYTE,
            moves_stride=24,
            include_types=False,
        )
        report = ee.extract_expansion_tables(bytes(rom), layout=ee.LAYOUT_GEN3_VANILLA_BYTE)
        self.assertEqual(report["status"], "OK")
        self.assertEqual(report["layout_version"], ee.LAYOUT_GEN3_VANILLA_BYTE)
        self.assertFalse(report["expansion_marker"]["in_rom_header_window"])
        self.assertEqual(report["tables"]["gMovesInfo"]["stride"], 24)
        self.assertFalse(report["tables"]["gTypesInfo"]["sanity_ok"])
        self.assertEqual(report["tables"]["gTypesInfo"]["reason"], "not_in_gf_header")

    def test_unknown_layout_version_fails_closed(self):
        rom = bytes(_build_synthetic_expansion_rom())
        with self.assertRaises(ValueError):
            ee.extract_expansion_tables(rom, layout="EXPANSION_UNKNOWN_V99")
        with tempfile.TemporaryDirectory() as tmp:
            rom_path = Path(tmp) / "exp.gba"
            rom_path.write_bytes(rom)
            err = io.StringIO()
            with contextlib.redirect_stderr(err):
                rc = ee.main(["--rom", str(rom_path), "--layout", "EXPANSION_UNKNOWN_V99"])
            self.assertEqual(rc, 2)
            self.assertIn("Unsupported layout version", err.getvalue())

    def test_item_probe_requires_both_name_and_description_fields(self):
        # Inline v1.9.0 item negatives:
        # 1) Valid description pointer at +0x0C, wiped inline name at +0x14 -> must fail closed!
        rom_inline = _build_synthetic_expansion_rom(
            layout=ee.LAYOUT_EXPANSION_V1_9_BITFIELD,
            items_stride=64,
        )
        items_off = 0x600000
        for i in range(1, 30):
            row_off = items_off + i * 64
            rom_inline[row_off + 0x14:row_off + 0x28] = b"\x00" * 20
        report_inline = ee.extract_expansion_tables(bytes(rom_inline), layout=ee.LAYOUT_EXPANSION_V1_9_BITFIELD)
        self.assertEqual(report_inline["status"], "PARTIAL")
        self.assertFalse(report_inline["tables"]["gItemsInfo"]["sanity_ok"])
        self.assertEqual(report_inline["tables"]["gItemsInfo"]["reason"], "items_sanity_probe_failed")
        self.assertIsNone(report_inline["tables"]["gItemsInfo"]["table_sha256"])

        # 2) Valid inline name at +0x14, wiped description pointer at +0x0C -> refused!
        rom_inline2 = _build_synthetic_expansion_rom(
            layout=ee.LAYOUT_EXPANSION_V1_9_BITFIELD,
            items_stride=64,
        )
        for i in range(1, 30):
            row_off = items_off + i * 64
            struct.pack_into("<I", rom_inline2, row_off + 0x0C, 0)
        report_inline2 = ee.extract_expansion_tables(bytes(rom_inline2), layout=ee.LAYOUT_EXPANSION_V1_9_BITFIELD)
        self.assertEqual(report_inline2["status"], "PARTIAL")
        self.assertFalse(report_inline2["tables"]["gItemsInfo"]["sanity_ok"])
        self.assertIsNone(report_inline2["tables"]["gItemsInfo"]["table_sha256"])

        # Pointer-name v1.10+ item negatives:
        # 3) Valid description pointer at +0x0C (and decoy pointer at +0x00), wiped name pointer at +0x14 -> refused!
        rom = _build_synthetic_expansion_rom(
            layout=ee.LAYOUT_EXPANSION_V1_10_PTR_ITEM,
            items_stride=44,
        )
        for i in range(1, 30):
            row_off = items_off + i * 44
            decoy_desc_ptr = struct.unpack_from("<I", rom, row_off + 0x0C)[0]
            struct.pack_into("<I", rom, row_off + 0x00, decoy_desc_ptr)
            struct.pack_into("<I", rom, row_off + 0x14, 0)  # wipe name pointer
        report = ee.extract_expansion_tables(bytes(rom), layout=ee.LAYOUT_EXPANSION_V1_10_PTR_ITEM)
        self.assertEqual(report["status"], "PARTIAL")
        self.assertFalse(report["tables"]["gItemsInfo"]["sanity_ok"])
        self.assertEqual(report["tables"]["gItemsInfo"]["reason"], "items_sanity_probe_failed")
        self.assertIsNone(report["tables"]["gItemsInfo"]["table_sha256"])

        # 4) Valid name pointer at +0x14 without valid description pointer at +0x0C -> refused!
        rom2 = _build_synthetic_expansion_rom(
            layout=ee.LAYOUT_EXPANSION_V1_10_PTR_ITEM,
            items_stride=44,
        )
        for i in range(1, 30):
            row_off = items_off + i * 44
            struct.pack_into("<I", rom2, row_off + 0x0C, 0)  # wipe description pointer
        report2 = ee.extract_expansion_tables(bytes(rom2), layout=ee.LAYOUT_EXPANSION_V1_10_PTR_ITEM)
        self.assertEqual(report2["status"], "PARTIAL")
        self.assertFalse(report2["tables"]["gItemsInfo"]["sanity_ok"])
        self.assertIsNone(report2["tables"]["gItemsInfo"]["table_sha256"])

    def test_v19_sentinel_rows_reject_nonzero_stats_and_invalid_move0(self):
        # Species row 0 with partial non-zero base stats must be rejected
        rom_bad_sp0 = _build_synthetic_expansion_rom()
        rom_bad_sp0[0x400000 + 0] = 45  # non-zero baseHP on SPECIES_NONE
        rep_sp0 = ee.extract_expansion_tables(bytes(rom_bad_sp0), layout=ee.LAYOUT_EXPANSION_V1_9_BITFIELD)
        self.assertEqual(rep_sp0["status"], "PARTIAL")
        self.assertFalse(rep_sp0["tables"]["gSpeciesInfo"]["sanity_ok"])

        # Move row 0 with non-zero power must be rejected
        rom_bad_mv0 = _build_synthetic_expansion_rom()
        bad_combat, bad_acc = _pack_expansion_move_words(
            move_type=ee.TYPE_NONE, category=2, power=40, accuracy=0, target=0
        )
        struct.pack_into("<HH", rom_bad_mv0, 0x500000 + 0x0A, bad_combat, bad_acc)
        rep_mv0 = ee.extract_expansion_tables(bytes(rom_bad_mv0), layout=ee.LAYOUT_EXPANSION_V1_9_BITFIELD)
        self.assertEqual(rep_mv0["status"], "PARTIAL")
        self.assertFalse(rep_mv0["tables"]["gMovesInfo"]["sanity_ok"])

    def test_moves_probe_rejects_unpacked_byte_power_in_bitfield_layout(self):
        rom = _build_synthetic_expansion_rom()
        moves_off = 0x500000
        for i in range(1, 50):
            row_off = moves_off + i * 36
            # Replace packed +0x0A/+0x0C with plain-byte fields at +0x08..+0x0B.
            struct.pack_into("<HH", rom, row_off + 0x0A, 0, 0)
            rom[row_off + 0x08] = 40 if i in (1, 33) else 80
            rom[row_off + 0x09] = ee.TYPE_NORMAL
        report = ee.extract_expansion_tables(bytes(rom), layout=ee.LAYOUT_EXPANSION_V1_9_BITFIELD)
        self.assertEqual(report["status"], "PARTIAL")
        self.assertFalse(report["tables"]["gMovesInfo"]["sanity_ok"])
        self.assertIsNone(report["tables"]["gMovesInfo"]["table_sha256"])

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

        # Valid pointer, but move 33 packed power is 99 instead of 40 -> sanity failure
        rom2 = _build_synthetic_expansion_rom()
        bad_combat, bad_acc = _pack_expansion_move_words(
            move_type=ee.TYPE_NORMAL, category=0, power=99, accuracy=100, target=1
        )
        struct.pack_into("<HH", rom2, 0x500000 + 33 * 36 + 0x0A, bad_combat, bad_acc)
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
