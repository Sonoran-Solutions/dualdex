#!/usr/bin/env python3
"""Validate the bounded Phase 6F private debugger stream; emit no raw records.

A parser PASS is not execution proof: retain the private capture, diagnostic build
identity and reference-core parity evidence. No emulator or guest writes occur here.
"""
import argparse
import json
import os
from pathlib import Path
import struct
import sys
import tempfile

from rowe_data import load_exact_rom
from rowe_observe import CORE_SHA256, digest, require_private_directory, slot_fields
from rowe_save_identity import compare_records
from ups_identity import read_local, require

HERE = Path(__file__).resolve().parent
SOURCE = 'e31759b24e7a4e3899285ff720d7b573ac328ae7'
DIAGNOSTIC = 'ca73e52df7d4c699c43c0f1d58dc757b9f40455f02bcc4db6f15f5566748eceb'
CARTRIDGE = 'e626ee3675b51b5e07b86983f44c5770789edf1a09d7105de49a3cdad0d62cb2'
SLOT, DEST = 0x02025174, 0x02025190
PARTIAL_STATUSES = {'BOUNDARY_PARTIAL', 'CALLSITE_EXECUTED', 'FUNCTION_EXECUTED', 'WRITE_ATTRIBUTED'}


def admit(core_sha256, source_revision, cartridge_sha256):
    require(core_sha256 == DIAGNOSTIC and source_revision == SOURCE,
            'wrong diagnostic core/source; reference core has no trace API')
    require(cartridge_sha256 == CARTRIDGE, 'wrong captured cartridge')


def identity_gate(rom, before, after):
    require(len(before) == len(after) == 76, 'incomplete identity records')
    if [i for i in range(76) if before[i] != after[i]] != [28, 29]:
        return False
    result = compare_records(rom, before, after)
    a, b = slot_fields(before), slot_fields(after)
    return (result['same_static_identity'] and result['stable_decoded_fields_except_species']
            and result['changed_offsets'] == [28, 29]
            and [(s['species'], s['form']) for s in (a, b)] == [(1560, 1), (52, 1)]
            and all(r['metadata_lookup_key'] == 1560 for r in result['raw_identities'])
            and (struct.unpack_from('<I', before, 28)[0] & ~4095) ==
                (struct.unpack_from('<I', after, 28)[0] & ~4095))


def validate(rom, capture):
    # Validate the ROM even for an empty or failed trace.
    from rowe_data import rom_identity
    rom_identity(rom)
    admit(capture['core_sha256'], capture['source_revision'], capture['cartridge_sha256'])
    require(capture.get('bootstrap') == 'CARTRIDGE_ONLY' and
            capture.get('enabled_before_first_run') is True, 'unverified trace bootstrap')
    require(capture.get('complete') is True and capture.get('errors') == [], 'incomplete/failed trace')
    events = capture['events']
    require(isinstance(events, list) and 0 < len(events) <= 1000, 'missing/oversized trace')
    for i, e in enumerate(events):
        require(e['order'] == i and type(e['order']) is int, 'duplicate/conflicting event order')
        require(e['reason'] in (2, 3) and len(e['registers']) == 16 and
                all(type(v) is int and 0 <= v <= 0xffffffff for v in e['registers']),
                'invalid guest register event')
        raw = bytes.fromhex(e['record_hex'])
        require(len(raw) == 76 and struct.unpack_from('<I', raw, 28)[0] == e['word'],
                'incomplete/conflicting selected record')
        if e['reason'] == 2:
            require(e['address'] == e['registers'][15] - 2 and not e['address'] & 1,
                    'invalid Thumb hardware breakpoint PC')
        else:
            require(e['width'] in (1, 2, 4), 'invalid write width')
    result = dict(status='BOUNDARY_PARTIAL', callsite='NOT_VERIFIED', function='NOT_VERIFIED',
                  selected_branch='NOT_VERIFIED', write='NOT_VERIFIED', identity='NOT_VERIFIED')
    def hits(pc):
        return [e for e in events if e['reason'] == 2 and e['address'] == pc]
    def one(pc):
        rows = hits(pc)
        require(len(rows) == 1, 'missing/duplicate PC: ' + hex(pc))
        return rows[0]
    if not hits(0x0803014a):
        return result
    caller = one(0x0803014a)
    result.update(status='CALLSITE_EXECUTED', callsite='VERIFIED')
    if not hits(0x080a4044):
        return result
    entry = one(0x080a4044)
    require(caller['order'] < entry['order'] and entry['registers'][14] == 0x0803014f,
            'unlinked caller/function')
    result.update(status='FUNCTION_EXECUTED', function='VERIFIED')
    if not hits(0x080a4092):
        return result
    # This deliberately admits only the single observed subject and call.
    branch, base, form = (one(pc) for pc in (0x080a4092, 0x080a4098, 0x080a40a2))
    require(branch['registers'][5] == SLOT and branch['registers'][4] == 1560 and
            base['registers'][0] == 52 and form['registers'][0] == 1,
            'unexpected slot/base/form recomputation')
    require(entry['order'] < branch['order'] < base['order'] < form['order'], 'unlinked branch')
    result['selected_branch'] = 'VERIFIED'
    writes = [e for e in events if e['reason'] == 3 and form['order'] < e['order'] and
              e['registers'][15] - 4 == 0x0809b980]
    if not writes:
        return result
    require(len(writes) == 1, 'duplicate/conflicting species write')
    write = writes[0]
    require(write['address'] == DEST and write['width'] == 2 and write['registers'][5] == SLOT,
            'mismatched destination/slot')
    before = bytes.fromhex(write['record_hex'])
    require(write['old'] == (write['word'] & 65535) and
            write['new'] == ((write['word'] & 0xf000) | 52) and
            (write['word'] & 0x1ffff) == (1560 | 4096), 'invalid packed-word transition')
    if not hits(0x080a40ae):
        return result
    after_event = one(0x080a40ae)
    after = bytes.fromhex(after_event['record_hex'])
    require(write['order'] < after_event['order'] and identity_gate(rom, before, after),
            'unexplained record/identity change')
    result.update(status='WRITE_ATTRIBUTED', write='VERIFIED', identity='PASS',
                  writing_pc='0x0809b980', destination=f'0x{DEST:08x}',
                  packed_words=[f'0x{v:08x}' for v in (write['word'], after_event['word'])],
                  record_sha256=[digest(before), digest(after)])
    if not hits(0x0803014e):
        return result
    returned = one(0x0803014e)
    species_call, form_call, after_form = (one(pc) for pc in (0x080a40aa, 0x080a40b4, 0x080a40b8))
    require(form['order'] < species_call['order'] < write['order'] < after_event['order'] <
            form_call['order'] < after_form['order'] < returned['order'], 'unlinked setter/return')
    require(all(e['registers'][0] == SLOT and e['registers'][1] == field
                for e, field in ((species_call, 11), (form_call, 89))), 'wrong setter linkage')
    require(species_call['registers'][2] == species_call['registers'][13] == form_call['registers'][13] and
            form_call['registers'][2] == form_call['registers'][13] + 2, 'wrong setter source linkage')
    dispatch = hits(0x0809b57c)
    require(len(dispatch) == 2 and all(c['order'] < d['order'] < end['order'] and
            d['registers'][:3] == c['registers'][:3]
            for c, d, end in zip((species_call, form_call), dispatch, (write, after_form))),
            'missing/conflicting setter dispatch')
    form_writes = [e for e in events if e['reason'] == 3 and
                   form_call['order'] < e['order'] < after_form['order']]
    require(len(form_writes) == 1 and form_writes[0]['address'] == DEST and
            form_writes[0]['width'] == 4 and form_writes[0]['old'] == form_writes[0]['new'] == after_event['word'] and
            form_writes[0]['registers'][15] - 4 == 0x0809b9ae, 'unexpected form write')
    loop = hits(0x080a4062)
    require(len(loop) == 6 and [e['registers'][8] for e in loop] == list(range(6)) and
            [e['registers'][5] for e in loop] == [0x02025128+i*76 for i in range(6)] and
            entry['order'] < loop[0]['order'] < branch['order'] and loop[-1]['order'] < returned['order'],
            'incomplete/conflicting six-slot loop')
    inputs = [e for e in hits(0x080a407c) if e['registers'][5] == SLOT]
    require(len(inputs) == 1 and inputs[0]['registers'][0] == 1560 and
            loop[1]['order'] < inputs[0]['order'] < branch['order'], 'missing selected-slot input')
    helpers = [one(pc) for pc in (0x080a18e8, 0x080a18a0)]
    require(branch['order'] < helpers[0]['order'] < base['order'] < helpers[1]['order'] < form['order'] and
            all(e['registers'][0] == 1560 and e['registers'][5] == SLOT for e in helpers),
            'unlinked form helper inputs')
    inside = [e for e in events if caller['order'] <= e['order'] <= returned['order']]
    require(len([e for e in inside if e['reason'] == 3]) == 2 and
            all(bytes.fromhex(e['record_hex']) == (before if e['order'] <= write['order'] else after)
                for e in inside), 'unexplained in-call write/record change')
    require(all(bytes.fromhex(e['record_hex']) == before for e in (caller, entry, branch, base, form)) and
            all(bytes.fromhex(e['record_hex']) == after for e in (after_form, returned)),
            'unrelated record changes inside call')
    require(all(e.get('save_record_sha256') == digest(before) for e in (caller, entry, returned)),
            'unlinked restored SaveBlock copy')
    result.update(status='EXECUTED_PATH_VERIFIED', normalization_scope='VERIFIED_NARROW',
                  return_record_sha256=digest(after))
    return result


def _prepare_output(output, inputs):
    # Validate before invalidation: an unsafe destination must never be touched.
    require_private_directory(output)
    require(output.parent.is_dir(), 'output parent is not a directory')
    require(not output.is_symlink(), 'output must not be a symlink')
    if output.exists():
        require(output.is_file(), 'output must be a regular file')
    output_path = output.resolve()
    for source in inputs:
        require(output_path != source.resolve(), 'output would overwrite input')
        if output.exists() and source.exists():
            try:
                same_file = os.path.samefile(output, source)
            except OSError:
                same_file = False
            require(not same_file, 'output aliases input')
    # Remove stale results before any evidence admission (including input privacy checks).
    output.unlink(missing_ok=True)


def _publish(output, result):
    payload = json.dumps(result, indent=2) + '\n'
    fd, temporary = tempfile.mkstemp(prefix='.' + output.name + '.', dir=output.parent)
    try:
        with os.fdopen(fd, 'w') as stream:
            stream.write(payload)
        os.replace(temporary, output)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('rom', 'core', 'events', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--allow-partial', action='store_true',
                        help='return success for a valid partial trace while retaining its status')
    args = parser.parse_args(argv)
    try:
        _prepare_output(args.output, (args.rom, args.core, args.events))
    except (ValueError, OSError) as error:
        print(f'unsafe output destination: {error}', file=sys.stderr)
        return 1
    try:
        require_private_directory(args.events)
        require(digest(read_local(args.core, 64*1024*1024)) == DIAGNOSTIC, 'wrong diagnostic binary')
        result = validate(load_exact_rom(args.rom), json.loads(read_local(args.events, 16*1024*1024)))
    except (ValueError, KeyError, TypeError, OSError) as error:
        _publish(args.output, dict(status='NOT_VERIFIED', error=str(error)))
        return 1
    _publish(args.output, result)
    status = result['status']
    return 0 if status == 'EXECUTED_PATH_VERIFIED' or (args.allow_partial and status in PARTIAL_STATUSES) else 1


if __name__ == '__main__':
    raise SystemExit(main())
