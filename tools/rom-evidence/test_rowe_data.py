import struct
import unittest
from unittest.mock import patch

import rowe_data
from rowe_data import (
    ROM_BASE,
    ROM_SIZE,
    _lookup_name,
    _move_records,
    _record_count,
    _resolve_form_entry,
    _sha,
    _species_records,
    _valid_text_prefix,
    lookup_move,
    resolve_form,
    lookup_species,
    read_span,
    read_u16,
    read_u32,
    rom_identity,
)
from rowe_text import EMPTY_OR_UNSET, INVALID_TERMINATION, VALID_TEXT, decode_text


class RoweDataTest(unittest.TestCase):
    def test_exact_size_and_hash_guards_reject_synthetic_rom(self):
        with self.assertRaisesRegex(ValueError, "size"):
            rom_identity(bytes(ROM_SIZE - 1))
        with self.assertRaisesRegex(ValueError, "size"):
            rom_identity(bytes(ROM_SIZE + 1))
        with self.assertRaisesRegex(ValueError, "SHA-1"):
            rom_identity(bytes(ROM_SIZE))

    def test_both_exact_hash_fields_are_required(self):
        raw = b"synthetic exact identity fixture"
        digest = rowe_data.identity(raw)
        with patch.object(rowe_data, "ROM_SIZE", len(raw)), \
                patch.object(rowe_data, "ROM_SHA1", digest["sha1"]), \
                patch.object(rowe_data, "ROM_SHA256", digest["sha256"]):
            self.assertEqual(rom_identity(raw), digest)
            with patch.object(rowe_data, "ROM_SHA256", "0" * 64):
                with self.assertRaisesRegex(ValueError, "SHA-256"):
                    rom_identity(raw)

    def test_address_and_pointer_reads_are_bounded(self):
        raw = bytes.fromhex("0102030405060708")
        self.assertEqual(read_span(raw, ROM_BASE + 7, 1), b"\x08")
        self.assertEqual(read_u16(raw, ROM_BASE + 2), 0x0403)
        self.assertEqual(read_u32(raw, ROM_BASE + 0), 0x04030201)
        for address, length in ((ROM_BASE - 1, 1), (ROM_BASE + 7, 2),
                                (ROM_BASE, -1), (ROM_BASE + 9, 0)):
            with self.subTest(address=address, length=length):
                with self.assertRaises(ValueError):
                    read_span(raw, address, length)
        with self.assertRaisesRegex(ValueError, "non-integer"):
            read_span(raw, True, 1)

    def test_species_records_preserve_gaps_and_refuse_unknown_ids(self):
        width = 13
        reserved = bytes([0xAC, 0xAC, 0xAC, 0xFF]) + bytes(width - 4)
        known = bytes([0xBC, 0xFF]) + bytes(width - 2)
        gap = bytes(width)
        raw = reserved + known + gap
        records, names, empty = _species_records(
            raw, {"gSpeciesNames": ROM_BASE, "gMoveNames": ROM_BASE + len(raw)})
        self.assertEqual(names, {1: "B"})
        self.assertEqual(empty, [2])
        self.assertEqual(_lookup_name(records, 0)["status"], EMPTY_OR_UNSET)
        self.assertEqual(_lookup_name(records, 1)["name"], "B")
        self.assertEqual(_lookup_name(records, 2)["status"], "UNSUPPORTED_RECORD")
        self.assertEqual(_lookup_name(records, 3)["status"], "UNSUPPORTED_ID")
        self.assertEqual(_lookup_name(records, True)["status"], "UNSUPPORTED_ID")

    def test_record_stride_and_move_prefix_fail_closed(self):
        with self.assertRaisesRegex(ValueError, "record stride"):
            _record_count(ROM_BASE, ROM_BASE + 14, 13)
        records = [
            decode_text(bytes([0xAE, 0xFF]) + bytes(15), "move_name"),
            decode_text(bytes([0xBB, 0xFF]) + bytes(15), "move_name"),
            decode_text(bytes(17), "move_name"),
            decode_text(bytes([0xBC, 0xFF]) + bytes(15), "move_name"),
        ]
        self.assertEqual(_valid_text_prefix(records), 2)
        self.assertEqual(_valid_text_prefix(records), _valid_text_prefix(records))

    def test_form_resolution_keeps_raw_values_and_has_no_fallback(self):
        valid_names = {1, 3}
        base = _resolve_form_entry(1, 0, 4, valid_names, None)
        self.assertEqual(base["resolved_species_id"], 1)
        self.assertEqual(base["runtime_status"], "NOT_VERIFIED")
        mapped = _resolve_form_entry(1, 0, 4, valid_names, [3])
        self.assertEqual(mapped["resolved_species_id"], 3)
        self.assertEqual(mapped["stored_species_id"], 1)
        self.assertEqual(mapped["stored_form_id"], 0)
        self.assertEqual(_resolve_form_entry(1, 1, 4, valid_names, [3])["status"],
                         "UNSUPPORTED_FORM_ID")
        self.assertEqual(_resolve_form_entry(2, 0, 4, valid_names, None)["status"],
                         "UNSUPPORTED_SPECIES_RECORD")
        self.assertEqual(_resolve_form_entry(4, 0, 4, valid_names, None)["status"],
                         "UNSUPPORTED_SPECIES_ID")
        self.assertEqual(_resolve_form_entry(1, 32, 4, valid_names, [3])["status"],
                         "UNSUPPORTED_FORM_ID")
        self.assertEqual(_resolve_form_entry(0, 0, 4, valid_names, None)["status"],
                         EMPTY_OR_UNSET)
        self.assertEqual(_resolve_form_entry(False, 0, 4, valid_names, None)["status"],
                         "UNSUPPORTED_SPECIES_ID")
        self.assertEqual(_resolve_form_entry(1, True, 4, valid_names, [3])["status"],
                         "UNSUPPORTED_FORM_ID")
        self.assertEqual(_resolve_form_entry(1, 0, 4, valid_names, [2])["status"],
                         "UNSUPPORTED_RESOLVED_SPECIES")

    def test_lookup_contracts_refuse_without_exact_rom_provenance(self):
        for lookup, args in ((lookup_species, (1,)), (lookup_move, (1,)),
                             (resolve_form, (1, 0))):
            with self.subTest(lookup=lookup.__name__):
                with self.assertRaisesRegex(ValueError, "size"):
                    lookup(b"synthetic", *args)

    def test_unknown_provenance_and_digests_are_deterministic(self):
        with self.assertRaisesRegex(ValueError, "outside ROM"):
            _species_records(b"synthetic", {"gSpeciesNames": 0x08500000,
                                             "gMoveNames": 0x0850001A})
        self.assertEqual(_sha(b"invented table"), _sha(b"invented table"))
        self.assertNotEqual(_sha(b"invented table"), _sha(b"other table"))


if __name__ == "__main__":
    unittest.main()
