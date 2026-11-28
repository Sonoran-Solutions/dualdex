import unittest

from rowe_text import (
    EMPTY_OR_UNSET,
    INVALID_TERMINATION,
    MALFORMED_CONTROL,
    UNKNOWN_GLYPH,
    UNSUPPORTED_MODE,
    VALID_TEXT,
    decode_text,
)


def field(domain, content, width):
    raw = bytes(content)
    return (raw + b"\xff" + bytes(width - len(raw) - 1))[:width]


class RoweTextTest(unittest.TestCase):
    def test_mixed_case_and_twelve_byte_full_width_nickname(self):
        raw = bytes([0xBB, 0xD5, 0xBC, 0xD6, 0xBD, 0xD7,
                     0xBE, 0xD8, 0xBF, 0xD9, 0xC0, 0xDA])
        result = decode_text(raw, "party_nickname")
        self.assertEqual(result.status, VALID_TEXT)
        self.assertEqual(result.text, "AaBbCcDdEeFf")
        self.assertIsNone(result.terminator_index)
        self.assertEqual(result.raw_hex, raw.hex())

    def test_numbers_punctuation_and_gender_symbols(self):
        raw = bytes([0xA1, 0xAA, 0xAB, 0xAC, 0xAD, 0xAE,
                     0xB4, 0xB5, 0xB6, 0xB8, 0xBA, 0xF0])
        result = decode_text(raw, "party_nickname")
        self.assertEqual(result.status, VALID_TEXT)
        self.assertEqual(result.text, "09!?.-’♂♀,/:")

    def test_special_glyphs_and_table_terminator_padding(self):
        raw = field("species_name", [0xBB, 0x1B, 0xB0, 0xB1, 0xB2, 0xB3, 0xB4], 13)
        result = decode_text(raw, "species_name")
        self.assertEqual(result.status, VALID_TEXT)
        self.assertEqual(result.text, "Aé…“”‘’")
        self.assertEqual(result.terminator_index, 7)

    def test_empty_and_unknown_are_distinct(self):
        self.assertEqual(decode_text(bytes(12), "party_nickname").status, EMPTY_OR_UNSET)
        self.assertEqual(decode_text(bytes([0x01]) + bytes(11), "party_nickname").status,
                         UNKNOWN_GLYPH)

    def test_malformed_and_unsupported_controls_preserve_raw(self):
        malformed = bytes(11) + b"\xfd"
        result = decode_text(malformed, "party_nickname")
        self.assertEqual(result.status, MALFORMED_CONTROL)
        self.assertEqual(result.raw_hex, malformed.hex())
        supported_shape_but_unsupported = field("party_nickname", [0xFD, 0x08], 12)
        self.assertEqual(decode_text(supported_shape_but_unsupported,
                                     "party_nickname").status, UNSUPPORTED_MODE)

    def test_termination_and_padding_are_checked(self):
        missing = bytes([0xBB]) + bytes(12)
        self.assertEqual(decode_text(missing, "move_name").status, INVALID_TERMINATION)
        invalid_tail = bytes([0xBB, 0xFF, 0xBC]) + bytes(10)
        self.assertEqual(decode_text(invalid_tail, "party_nickname").status,
                         INVALID_TERMINATION)

    def test_domain_is_explicit_and_map_strings_are_bounded(self):
        self.assertEqual(decode_text(b"\xff", "unknown_domain").status, UNSUPPORTED_MODE)
        map_name = bytes([0xBB, 0xD5, 0xD6, 0xD7, 0xFF])
        self.assertEqual(decode_text(map_name, "map_section_name").text, "Aabc")
        control_name = bytes([0xBB, 0xFD, 0x08, 0xFF])
        self.assertEqual(decode_text(control_name, "map_section_name").status,
                         UNSUPPORTED_MODE)


if __name__ == "__main__":
    unittest.main()
