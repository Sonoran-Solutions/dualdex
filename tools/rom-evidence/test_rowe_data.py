import struct
import unittest
from types import SimpleNamespace
from unittest.mock import patch

import rowe_data
from rowe_data import (
    ROM_BASE,
    ROM_SIZE,
    _form_tables,
    _lookup_name,
    _map_section_evidence,
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

    def test_form_pointer_reader_reads_tables_and_reports_missing_sentinel(self):
        raw = bytearray(96)
        pointer_table = ROM_BASE + 16
        struct.pack_into("<5I", raw, 16, 0, ROM_BASE + 64, 0,
                         ROM_BASE + 72, ROM_BASE + 80)
        struct.pack_into("<3H", raw, 64, 1, 3, rowe_data.FORM_SENTINEL)
        struct.pack_into("<2H", raw, 72, 2, rowe_data.FORM_SENTINEL)
        struct.pack_into("<4H", raw, 80, 1, 2, 3, 4)
        records = [SimpleNamespace(status=EMPTY_OR_UNSET)] + [
            SimpleNamespace(status=VALID_TEXT) for _ in range(4)
        ]
        with patch.object(rowe_data, "FORM_POINTER_TABLE", pointer_table), \
                patch.object(rowe_data, "FORM_LIMIT", 4):
            tables, summary = _form_tables(bytes(raw), records)

        self.assertEqual(tables, {1: [1, 3], 3: [2]})
        self.assertEqual(summary["pointer_slots"], 5)
        self.assertEqual(summary["species_with_form_table"], 2)
        self.assertEqual(summary["form_choices_including_form_zero"], 3)
        self.assertEqual(summary["nonzero_form_choices"], 1)
        self.assertEqual(summary["nonzero_form_species_id_ranges"], [[3, 3]])
        self.assertEqual(summary["malformed_species_ids"], [4])

    def test_map_section_scanner_records_unterminated_name_as_unsupported(self):
        raw = bytearray(180)
        map_start = ROM_BASE + 16
        for index, pointer in enumerate((ROM_BASE + 80, ROM_BASE + 100,
                                         ROM_BASE + ROM_SIZE + 1)):
            struct.pack_into("<I", raw, 20 + index * rowe_data.MAP_ENTRY_STRIDE, pointer)
        raw[80:82] = bytes((0xBB, 0xFF))
        raw[100:164] = bytes([0xBB]) * 64
        with patch.object(rowe_data, "MAP_ENTRY_LEAD_COUNT", 3):
            evidence = _map_section_evidence(bytes(raw), map_start)

        self.assertEqual(evidence["nonzero_name_pointers_in_rom"], 2)
        self.assertEqual(evidence["invalid_name_pointers"], 1)
        self.assertEqual(evidence["bounded_name_status_counts"], {
            "INVALID_TERMINATION": 1,
            VALID_TEXT: 1,
        })
        self.assertEqual(evidence["unsupported_name_entry_ids"], [1])

    def test_lookup_contracts_refuse_without_exact_rom_provenance(self):
        for lookup, args in ((lookup_species, (1,)), (lookup_move, (1,)),
                             (resolve_form, (1, 0))):
            with self.subTest(lookup=lookup.__name__):
                with self.assertRaisesRegex(ValueError, "size"):
                    lookup(b"synthetic", *args)

    def test_party_identity_aliases_preserve_raw_pair_and_static_authority(self):
        # Artificial names/table: tests the public composition, not runtime evidence.
        records = [SimpleNamespace(status=EMPTY_OR_UNSET, text="")] + [
            SimpleNamespace(status=VALID_TEXT, text=name)
            for name in ("Base", "Unused", "Variant")
        ]
        raw = bytearray(64)
        table = ROM_BASE + 16
        struct.pack_into("<I", raw, 20, ROM_BASE + 40)
        struct.pack_into("<I", raw, 28, ROM_BASE + 40)
        struct.pack_into("<3H", raw, 40, 1, 3, rowe_data.FORM_SENTINEL)
        with patch.object(rowe_data, "rom_identity"), \
                patch.object(rowe_data, "_require_symbols", return_value={}), \
                patch.object(rowe_data, "_species_records", return_value=(records, {}, [])), \
                patch.object(rowe_data, "FORM_POINTER_TABLE", table):
            for species in (1, 3):
                result = rowe_data.lookup_party_identity(bytes(raw), species, 1)
                self.assertEqual((result["raw_stored_species_id"], result["raw_form_id"]),
                                 (species, 1))
                self.assertEqual(result["static_resolved_species_id"], 3)
                self.assertEqual(result["metadata_lookup_key"], 3)
                self.assertEqual(result["static_name_candidate"], "Variant")
                self.assertEqual(result["static_status"], "STATIC_VERIFIED")
                self.assertIsNone(result["runtime_display_name"])
                self.assertEqual(result["runtime_effective_identity_status"], "NOT_VERIFIED")
            for species, form in ((4, 0), (1, 2)):
                result = rowe_data.lookup_party_identity(bytes(raw), species, form)
                self.assertIsNone(result["metadata_lookup_key"])
                self.assertIsNone(result["static_name_candidate"])

    def test_party_identity_requires_exact_rom_before_metadata_access(self):
        with patch.object(rowe_data, "_require_symbols") as symbols:
            with self.assertRaisesRegex(ValueError, "size"):
                rowe_data.lookup_party_identity(b"invented", 1, 1)
            symbols.assert_not_called()

    def test_unknown_provenance_and_digests_are_deterministic(self):
        with self.assertRaisesRegex(ValueError, "outside ROM"):
            _species_records(b"synthetic", {"gSpeciesNames": 0x08500000,
                                             "gMoveNames": 0x0850001A})
        self.assertEqual(_sha(b"invented table"), _sha(b"invented table"))
        self.assertNotEqual(_sha(b"invented table"), _sha(b"other table"))


if __name__ == "__main__":
    unittest.main()
