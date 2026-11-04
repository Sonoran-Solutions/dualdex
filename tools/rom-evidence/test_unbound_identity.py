"""Invented bytes only: no ROM, save, patch or network needed."""
import unittest
from unittest.mock import patch

import unbound_identity as subject


class UnboundIdentityTest(unittest.TestCase):
    def test_exact_gate_rejects_wrong_size_and_hash(self):
        with self.assertRaisesRegex(ValueError, "size"):
            subject.exact_rom(b"invented")
        with patch.object(subject, "ROM_SIZE", 8):
            with self.assertRaisesRegex(ValueError, "SHA-256"):
                subject.exact_rom(b"invented")

    def test_header_fields_and_checksum(self):
        data = bytearray(192)
        data[0xA0:0xAC] = b"TEST HEADER!"
        data[0xAC:0xB0] = b"TEST"
        data[0xB2] = 0x96
        data[0xBD] = (-sum(data[0xA0:0xBD]) - 0x19) & 255
        result = subject.header(data)
        self.assertEqual(result["title"], "TEST HEADER!")
        self.assertTrue(result["checksum_valid"])
        data[0xBC] = 1
        self.assertFalse(subject.header(data)["checksum_valid"])
        with self.assertRaisesRegex(ValueError, "truncated"):
            subject.header(data[:-1])

    def test_versions_decode_gen_three_not_ascii(self):
        raw = b"\xff\xa3\xad\xa2\xad\xa2\xad\xa2\xffASCII 9.8.7\xff"
        found = subject.version_candidates(raw)
        self.assertEqual(len(found), 1)
        self.assertEqual(found[0]["text"], "2.1.1.1")
        self.assertEqual(found[0]["status"], "OBSERVED")
        self.assertEqual(subject.version_candidates(raw[:8]), [])

    def test_version_output_is_bounded(self):
        with self.assertRaisesRegex(ValueError, "too many"):
            subject.version_candidates(b"\xff\xa2\xad\xa1\xad\xa1\xff" * 17)

    def test_base_comparison_excludes_uniform_padding(self):
        base = bytes(4096) + b"AB" * 2048 + b"CD" * 2048
        changed = base[:-1] + b"X"
        result = subject.base_comparison(changed, base)
        self.assertEqual(result["equal_bytes"], len(base) - 1)
        self.assertEqual(result["unchanged_nonuniform_blocks"], 1)
        self.assertEqual(result["selected_unchanged_blocks"][0]["offset"], "0x00001000")
        with self.assertRaisesRegex(ValueError, "invalid base"):
            subject.base_comparison(b"", base)


if __name__ == "__main__":
    unittest.main()
