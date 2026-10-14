import struct
import unittest
from rowe_symbols import inspect_rom, symbol_table


class SymbolTest(unittest.TestCase):
    def test_invented_table(self):
        data = struct.pack('<IIII', 0x08000010, 0x02000000, 0x08000014, 0x03000000) + b'foo\0bar\0'
        self.assertEqual(symbol_table(data, 0x08000000, 2), {'foo': 0x02000000, 'bar': 0x03000000})
        for bad in (data[:8], data[:16] + b'foo\0foo\0', data[:16] + b'\0oo\0bar\0',
                    data[:16] + b'foo\0bar!', data[:16] + b'foo\0b\xffr\0',
                    struct.pack('<I', 0x07000000) + data[4:]):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                symbol_table(bad, 0x08000000, 2)
        for address, count in [(0x07ffffff, 1), (0x08000000, 0), (0x08000000, 513)]:
            with self.assertRaises(ValueError):
                symbol_table(data, address, count)
        with self.assertRaisesRegex(ValueError, 'identity mismatch'):
            inspect_rom(data)


if __name__ == '__main__':
    unittest.main()
