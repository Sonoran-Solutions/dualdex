#!/usr/bin/env python3
"""Read-only, forward UPS verification. Prints identities, never writes a ROM.

Independent implementation of UPS1's size/relative-XOR/CRC format. No patcher
implementation is imported. Scope: local files <=64 MiB, forward application;
no header stripping, reverse patching, CRC overrides or production trust changes.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import sys
import zlib

MAX_ROM = 64 * 1024 * 1024
MAX_PATCH = 128 * 1024 * 1024


def require(condition, message):
    if not condition:
        raise ValueError(message)


def identity(data):
    return dict(bytes=len(data), sha256=hashlib.sha256(data).hexdigest(),
                sha1=hashlib.sha1(data).hexdigest(), crc32=f"{zlib.crc32(data):08x}")


def read_local(path, limit):
    with Path(path).open('rb') as stream:
        data = stream.read(limit + 1)
    require(len(data) <= limit, 'local file exceeds size limit')
    return data


def inspect_patch(patch, source=None):
    require(18 <= len(patch) <= MAX_PATCH and patch[:4] == b'UPS1', 'invalid UPS1 file')
    source_crc, target_crc, patch_crc = struct.unpack('<III', patch[-12:])
    require(zlib.crc32(patch[:-4]) == patch_crc, 'patch CRC mismatch')
    end, position = len(patch) - 12, 4

    def number():
        nonlocal position
        value = 0
        for group in range(5):
            require(position < end, 'truncated UPS integer')
            byte = patch[position]
            position += 1
            value += (byte & 127) << (7 * group)
            if byte & 128:
                require(value <= MAX_ROM + 1, 'UPS integer exceeds size limit')
                return value
            value += 1 << (7 * (group + 1))
        raise ValueError('overlong UPS integer')

    source_size, target_size = number(), number()
    require(max(source_size, target_size) <= MAX_ROM, 'ROM exceeds size limit')
    output = None
    if source is not None:
        require(len(source) == source_size, 'source size mismatch')
        require(zlib.crc32(source) == source_crc, 'source CRC mismatch')
        output = bytearray(max(source_size, target_size))
        output[:len(source)] = source
    records, cursor = 0, 0
    while position < end:
        cursor += number()
        stop = patch.find(b'\0', position, end)
        require(stop != -1, 'unterminated UPS XOR run')
        count = stop - position
        require(cursor <= max(source_size, target_size) and
                count <= max(source_size, target_size) - cursor, 'UPS XOR run outside ROM')
        if output is not None:
            for i in range(count):
                output[cursor + i] ^= patch[position + i]
        records += 1
        cursor += count + 1
        position = stop + 1
    if output is not None:
        del output[target_size:]
        require(zlib.crc32(output) == target_crc, 'target CRC mismatch')
    return dict(source_bytes=source_size, target_bytes=target_size,
                source_crc32=f'{source_crc:08x}', target_crc32=f'{target_crc:08x}',
                patch_crc32=f'{patch_crc:08x}', xor_runs=records), output


def verify(patch, expected_sha256, source=None, candidate=None):
    require(len(expected_sha256) == 64 and
            all(c in '0123456789abcdef' for c in expected_sha256), 'invalid patch SHA-256 pin')
    require(hashlib.sha256(patch).hexdigest() == expected_sha256, 'patch SHA-256 mismatch')
    metadata, output = inspect_patch(patch, source)
    report = dict(schema_version=1, patch=identity(patch), ups=metadata,
                  rom_identity='NOT_VERIFIED', runtime_layout='NOT_VERIFIED')
    if source is None:
        require(candidate is None, 'candidate comparison requires the source ROM')
        return report
    report.update(rom_identity='PATCH_REPRODUCED', source=identity(source), target=identity(output))
    if len(output) >= 0xc0:
        # Header identity is descriptive, never proof of hack/version or authenticity.
        report['gba_header'] = dict(title_hex=output[0xa0:0xac].hex(),
            game_code_hex=output[0xac:0xb0].hex(), maker_hex=output[0xb0:0xb2].hex(),
            revision=output[0xbc], fixed_byte=output[0xb2], checksum=output[0xbd],
            checksum_valid=((-sum(output[0xa0:0xbd]) - 0x19) & 255) == output[0xbd])
    if candidate is not None:
        require(candidate == output, 'existing ROM differs from reproduced output')
        report['existing_rom_byte_identical'] = True
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--patch', type=Path, required=True)
    parser.add_argument('--patch-sha256', required=True)
    parser.add_argument('--source', type=Path)
    parser.add_argument('--candidate', type=Path)
    args = parser.parse_args()
    try:
        report = verify(read_local(args.patch, MAX_PATCH), args.patch_sha256,
                        read_local(args.source, MAX_ROM) if args.source else None,
                        read_local(args.candidate, MAX_ROM) if args.candidate else None)
    except (ValueError, OSError) as error:
        print(f'FAIL: {error}', file=sys.stderr)
        return 1
    print(json.dumps(report, sort_keys=True, indent=2))
    return 0


if __name__ == '__main__':
    sys.exit(main())
