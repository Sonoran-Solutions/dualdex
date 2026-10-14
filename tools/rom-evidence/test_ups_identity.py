"""Invented bytes only; test encoding is independent of the production decoder."""
import hashlib
import struct
import unittest
import zlib

from ups_identity import inspect_patch, verify


def patch(body, source, target):
    data = b'UPS1' + body + struct.pack('<II', zlib.crc32(source), zlib.crc32(target))
    return data + struct.pack('<I', zlib.crc32(data))


def check(data, source=None, candidate=None):
    return verify(data, hashlib.sha256(data).hexdigest(), source, candidate)


class UpsIdentityTest(unittest.TestCase):
    def test_forward_identity_extension_and_end_boundary(self):
        source, target = b'ABC', b'AXC\0Z'
        # Sizes 3/5, skip 1, B xor X = 0x1a, skip 1 after terminator, extension Z.
        data = patch(b'\x83\x85\x81\x1a\0\x81Z\0', source, target)
        report = check(data, source, target)
        self.assertEqual(report['target']['sha256'], hashlib.sha256(target).hexdigest())
        self.assertTrue(report['existing_rom_byte_identical'])
        self.assertEqual(report['runtime_layout'], 'NOT_VERIFIED')
        self.assertEqual(check(data)['rom_identity'], 'NOT_VERIFIED')
        with self.assertRaisesRegex(ValueError, 'requires the source'):
            check(data, candidate=target)

    def test_shrink_noop_empty_and_multibyte_delta(self):
        for source, target, body in [(b'ABC', b'A', b'\x83\x81\x81BC\0'),
                                     (b'ABC', b'ABC', b'\x83\x83'),
                                     (b'', b'', b'\x80\x80'),
                                     (bytes(129), bytes(128) + b'A',
                                      b'\x01\x80\x01\x80\x00\x80A\0')]:
            self.assertEqual(check(patch(body, source, target), source, target)['rom_identity'],
                             'PATCH_REPRODUCED')

    def test_all_integrity_checks_and_candidate_equality(self):
        source, target = b'A', b'B'
        data = patch(b'\x81\x81\x80\x03\0', source, target)
        with self.assertRaisesRegex(ValueError, 'SHA-256 mismatch'):
            verify(data, '0' * 64, source)
        with self.assertRaisesRegex(ValueError, 'patch CRC'):
            check(data[:-1] + bytes([data[-1] ^ 1]), source)
        with self.assertRaisesRegex(ValueError, 'source size'):
            check(data, b'')
        with self.assertRaisesRegex(ValueError, 'source CRC'):
            check(data, b'C')
        with self.assertRaisesRegex(ValueError, 'target CRC'):
            check(patch(b'\x81\x81\x80\x01\0', source, target), source)
        with self.assertRaisesRegex(ValueError, 'differs'):
            check(data, source, b'C')

    def test_malformed_and_bounded_records(self):
        for body in [b'', b'\0', b'\x81\x81\x80A', b'\x81\x81\x82A\0',
                     b'\x81\x81\x80AB\0', b'\0' * 6,
                     b'\xff\xff\xff\xff\xff\x80']:
            with self.subTest(body=body), self.assertRaises(ValueError):
                inspect_patch(patch(body, b'A', b'B'))
        with self.assertRaises(ValueError):
            inspect_patch(b'UPS1')


if __name__ == '__main__':
    unittest.main()
