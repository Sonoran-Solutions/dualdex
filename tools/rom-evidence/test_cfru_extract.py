import contextlib
import hashlib
import io
import json
from pathlib import Path
import struct
import tempfile
import unittest

import cfru_extract as ce
from ups_identity import MAX_ROM

MIB = 1024 * 1024
BASE_SIZE = 16 * MIB
CFRU_SIZE = 32 * MIB


def _encode_gen3(text: str) -> bytes:
    table = {" ": 0x00, "-": 0xAE, ".": 0xAD, "?": 0xAC, "!": 0xAB}
    for i in range(10):
        table[str(i)] = 0xA1 + i
    for i in range(26):
        table[chr(ord("A") + i)] = 0xBB + i
        table[chr(ord("a") + i)] = 0xD5 + i
    return bytes(table[ch] for ch in text) + b"\xFF"


def _write_gba_header(buf: bytearray, title: str = "POKEMON FIRE", code: str = "BPRE") -> None:
    buf[0xA0:0xA0 + len(title)] = title.encode("ascii")
    buf[0xAC:0xB0] = code.encode("ascii")
    buf[0xB2] = 0x96
    buf[0xBD] = (-(sum(buf[0xA0:0xBD]) + 0x19)) & 0xFF


def _write_species_names(buf: bytearray, offset: int, count: int) -> None:
    for i in range(count):
        label = "??????" if i == 0 else f"Mon{i}"
        encoded = _encode_gen3(label)[:10]
        if encoded[-1] != 0xFF:
            encoded = encoded[:-1] + b"\xFF"
        padded = encoded.ljust(11, b"\x00")
        buf[offset + i * 11:offset + (i + 1) * 11] = padded


def _write_base_stats(buf: bytearray, offset: int, count: int) -> None:
    for i in range(count):
        if i == 0:
            continue
        row = bytearray(28)
        row[0:6] = bytes([45 + (i % 40), 49, 49, 45, 65, 65])
        row[6] = 12 if i <= 3 else (i % 18)
        row[7] = 3 if i <= 3 else (i % 18)
        buf[offset + i * 28:offset + (i + 1) * 28] = row


def _write_battle_moves(buf: bytearray, offset: int, count: int) -> None:
    for i in range(count):
        row = bytearray(12)
        if i > 0:
            row[0] = 0
            row[1] = 40 if i in (1, 10, 33) else (0 if i in (14, 28) else 50)
            row[2] = i % 18
            row[3] = 100
            row[4] = 35
            row[10] = 2 if row[1] == 0 else (i % 2)
        buf[offset + i * 12:offset + (i + 1) * 12] = row


def _write_items(buf: bytearray, offset: int, count: int, pointer_name_offset: int) -> None:
    ptr_text = _encode_gen3("Special Item")
    buf[pointer_name_offset:pointer_name_offset + len(ptr_text)] = ptr_text
    ptr_gba = ce.ROM_BASE + pointer_name_offset
    for i in range(count):
        row = bytearray(44)
        if i == 0:
            row[0] = 0xFF
        elif i == 5:
            struct.pack_into("<I", row, 0, ptr_gba)
        else:
            enc = _encode_gen3(f"Item{i}")[:14]
            if enc[-1] != 0xFF:
                enc = enc[:-1] + b"\xFF"
            row[0:len(enc)] = enc
        struct.pack_into("<H", row, 14, i)
        buf[offset + i * 44:offset + (i + 1) * 44] = row


def _write_type_matrix(buf: bytearray, offset: int, dim: int = 24) -> None:
    matrix = bytearray([10] * (dim * dim))
    matrix[7] = 0  # Normal -> Ghost = 0
    buf[offset:offset + len(matrix)] = matrix


def _write_icon_table(buf: bytearray, offset: int, count: int, sprite_offset: int) -> None:
    for i in range(count):
        struct.pack_into("<I", buf, offset + i * 4, ce.ROM_BASE + sprite_offset + i * 16)


def _build_synthetic_pair() -> tuple[bytes, bytes]:
    base = bytearray(BASE_SIZE)
    _write_gba_header(base)
    # Place vanilla tables in base 16 MiB
    v_names, v_stats, v_moves, v_items, v_types, v_icons = (
        0x245EE0, 0x254784, 0x250C04, 0x3DB028, 0x24F050, 0x3D37A0
    )
    _write_species_names(base, v_names, 40)
    _write_base_stats(base, v_stats, 40)
    _write_battle_moves(base, v_moves, 40)
    _write_items(base, v_items, 40, 0x3E0000)
    _write_type_matrix(base, v_types, 18)
    _write_icon_table(base, v_icons, 40, 0x400000)

    # Wire literal-pool words in base
    struct.pack_into("<I", base, 0x040FEC, ce.ROM_BASE + v_names)
    struct.pack_into("<I", base, 0x03F7C8, ce.ROM_BASE + v_stats)
    struct.pack_into("<I", base, 0x00E358, ce.ROM_BASE + v_moves)
    struct.pack_into("<I", base, 0x09A958, ce.ROM_BASE + v_items)
    struct.pack_into("<I", base, 0x01E300, ce.ROM_BASE + v_types)
    struct.pack_into("<I", base, 0x096C68, ce.ROM_BASE + v_icons)

    # Build 32 MiB CFRU ROM with repointed tables in upper 16 MiB
    cfru = bytearray(CFRU_SIZE)
    cfru[:BASE_SIZE] = base
    c_names, c_stats, c_moves, c_items, c_types, c_icons = (
        0x1200000, 0x1210000, 0x1220000, 0x1230000, 0x1240000, 0x1250000
    )
    _write_species_names(cfru, c_names, 64)
    _write_base_stats(cfru, c_stats, 64)
    _write_battle_moves(cfru, c_moves, 48)
    _write_items(cfru, c_items, 32, 0x1238000)
    _write_type_matrix(cfru, c_types, 24)
    _write_icon_table(cfru, c_icons, 64, 0x1260000)

    struct.pack_into("<I", cfru, 0x040FEC, ce.ROM_BASE + c_names)
    struct.pack_into("<I", cfru, 0x03F7C8, ce.ROM_BASE + c_stats)
    struct.pack_into("<I", cfru, 0x00E358, ce.ROM_BASE + c_moves)
    struct.pack_into("<I", cfru, 0x09A958, ce.ROM_BASE + c_items)
    struct.pack_into("<I", cfru, 0x01E300, ce.ROM_BASE + c_types)
    struct.pack_into("<I", cfru, 0x096C68, ce.ROM_BASE + c_icons)
    return bytes(base), bytes(cfru)


class CfruExtractTest(unittest.TestCase):
    def test_repointed_table_discovery_with_base_and_standalone(self):
        base, cfru = _build_synthetic_pair()
        for label, report in (
            ("with_base", ce.extract_tables(cfru, base)),
            ("standalone", ce.extract_tables(cfru)),
        ):
            with self.subTest(mode=label):
                self.assertEqual(report["status"], "OK")
                self.assertEqual(report["rom_size"], CFRU_SIZE)
                self.assertEqual(report["rom_sha256"], hashlib.sha256(cfru).hexdigest())
                tables = report["tables"]
                self.assertEqual(set(tables.keys()), {
                    "gSpeciesNames", "gBaseStats", "gBattleMoves",
                    "gItems", "gTypeEffectiveness", "gMonIconTable",
                })
                self.assertEqual(tables["gSpeciesNames"]["gba_address"], "0x09200000")
                self.assertEqual(tables["gSpeciesNames"]["stride"], 11)
                self.assertEqual(tables["gSpeciesNames"]["count_estimate"], 64)
                self.assertTrue(tables["gSpeciesNames"]["sanity_ok"])
                self.assertEqual(
                    tables["gSpeciesNames"]["table_sha256"],
                    hashlib.sha256(cfru[0x1200000:0x1200000 + 64 * 11]).hexdigest(),
                )
                self.assertEqual(tables["gBaseStats"]["gba_address"], "0x09210000")
                self.assertEqual(tables["gBaseStats"]["stride"], 28)
                self.assertEqual(tables["gBaseStats"]["count_estimate"], 64)
                self.assertEqual(tables["gBattleMoves"]["gba_address"], "0x09220000")
                self.assertEqual(tables["gBattleMoves"]["stride"], 12)
                self.assertEqual(tables["gBattleMoves"]["count_estimate"], 48)
                self.assertEqual(tables["gItems"]["gba_address"], "0x09230000")
                self.assertEqual(tables["gItems"]["stride"], 44)
                self.assertEqual(tables["gItems"]["count_estimate"], 32)
                self.assertEqual(tables["gTypeEffectiveness"]["gba_address"], "0x09240000")
                self.assertEqual(tables["gTypeEffectiveness"]["stride"], 24)
                self.assertEqual(tables["gTypeEffectiveness"]["count_estimate"], 24)
                self.assertEqual(tables["gMonIconTable"]["gba_address"], "0x09250000")
                self.assertEqual(tables["gMonIconTable"]["stride"], 4)
                self.assertEqual(tables["gMonIconTable"]["count_estimate"], 64)
                serialized = json.dumps(report)
                self.assertNotIn("Mon1", serialized)
                self.assertNotIn("Special Item", serialized)

    def test_base_comparison_discovers_non_primary_literal_site_and_thumb_ldr(self):
        base, cfru = _build_synthetic_pair()
        base_mut = bytearray(base)
        cfru_mut = bytearray(cfru)
        # Clear primary literal pool site for gBattleMoves in both; put vanilla ptr at custom aligned site
        # and repointed ptr in cfru_mut, plus a Thumb `ldr r0, [pc, #0]` at 0x00E338 pointing to 0x00E33C.
        struct.pack_into("<I", base_mut, 0x00E358, 0)
        struct.pack_into("<I", cfru_mut, 0x00E358, 0)
        struct.pack_into("<H", base_mut, 0x00E338, 0x4800)  # ldr r0, [pc, #0] -> 0x00E33C
        struct.pack_into("<I", base_mut, 0x00E33C, ce.ROM_BASE + 0x250C04)
        struct.pack_into("<H", cfru_mut, 0x00E338, 0x4800)
        struct.pack_into("<I", cfru_mut, 0x00E33C, ce.ROM_BASE + 0x1220000)

        # Also test GF header indirection for gBaseStats via 0x080001BC
        struct.pack_into("<I", cfru_mut, 0x03F7C8, 0x080001BC)
        struct.pack_into("<I", cfru_mut, 0x0001BC, ce.ROM_BASE + 0x1210000)

        report = ce.extract_tables(bytes(cfru_mut), bytes(base_mut))
        self.assertEqual(report["status"], "OK")
        self.assertEqual(report["tables"]["gBattleMoves"]["gba_address"], "0x09220000")
        self.assertEqual(report["tables"]["gBaseStats"]["gba_address"], "0x09210000")

    def test_out_of_range_pointer_refusal(self):
        _, cfru = _build_synthetic_pair()
        bad = bytearray(cfru)
        # Point gSpeciesNames into EWRAM and gBaseStats past 0x0A000000.
        struct.pack_into("<I", bad, 0x040FEC, 0x02024000)
        struct.pack_into("<I", bad, 0x03F7C8, 0x0B000000)
        report = ce.extract_tables(bytes(bad))
        self.assertEqual(report["status"], "PARTIAL")
        for key in ("gSpeciesNames", "gBaseStats"):
            self.assertFalse(report["tables"][key]["sanity_ok"])
            self.assertIsNone(report["tables"][key]["gba_address"])
            self.assertIsNone(report["tables"][key]["table_sha256"])
            self.assertEqual(report["tables"][key]["reason"], "pointer_out_of_range")

    def test_sanity_failure_refusal(self):
        _, cfru = _build_synthetic_pair()
        bad = bytearray(cfru)
        # Wipe repointed gBaseStats and gBattleMoves contents to zeros (valid ROM pointer, failed sanity).
        bad[0x1210000:0x1210000 + 64 * 28] = b"\x00" * (64 * 28)
        bad[0x1220000:0x1220000 + 48 * 12] = b"\xFF" * (48 * 12)
        report = ce.extract_tables(bytes(bad))
        self.assertEqual(report["status"], "PARTIAL")
        self.assertFalse(report["tables"]["gBaseStats"]["sanity_ok"])
        self.assertIsNone(report["tables"]["gBaseStats"]["table_sha256"])
        self.assertFalse(report["tables"]["gBattleMoves"]["sanity_ok"])
        self.assertIsNone(report["tables"]["gBattleMoves"]["table_sha256"])

    def test_size_cap_and_cli_refusal(self):
        with self.assertRaises(ValueError):
            ce.extract_tables(b"\x00" * (ce.MIN_ROM_HEADER - 1))
        with tempfile.TemporaryDirectory() as tmp:
            oversize = Path(tmp) / "too_big.gba"
            with oversize.open("wb") as f:
                f.seek(MAX_ROM)
                f.write(b"\x00")
            err = io.StringIO()
            with contextlib.redirect_stderr(err):
                rc = ce.main(["--rom", str(oversize)])
            self.assertEqual(rc, 2)
            self.assertIn("size limit", err.getvalue())

            _, cfru = _build_synthetic_pair()
            rom_path = Path(tmp) / "cfru.gba"
            rom_path.write_bytes(cfru)
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                rc = ce.main(["--rom", str(rom_path)])
            self.assertEqual(rc, 0)
            parsed = json.loads(out.getvalue())
            self.assertEqual(parsed["status"], "OK")


if __name__ == "__main__":
    unittest.main()
