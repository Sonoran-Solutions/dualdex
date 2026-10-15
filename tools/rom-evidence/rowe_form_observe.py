#!/usr/bin/env python3
"""Compare a private paused player capture with independently transcribed UI.

The form-cue comparison is an explicit human assessment, never inferred from RAM.
No production reader, global table, enemy data, or runtime mode inference.
"""
import argparse
import json
from pathlib import Path
import sys

from rowe_data import (BASE_STATS_STRIDE, FORM_LIMIT, load_exact_rom, lookup_party_identity,
                       read_span, rom_identity, _require_symbols)
from rowe_observe import (ADAPTER_PIN, CORE_SHA256, HERE, digest,
                          require_private_directory, slot_fields)
from ups_identity import read_local, require


def compare(rom, report, label, party, slot_index, ui):
    """All inputs are evidence, not trusted identity assertions. Fail as a whole."""
    identity = rom_identity(rom)
    require(report['status'] == 'OBSERVED' and report['rom'] == identity,
            'missing exact-ROM observation')
    require(report['core_sha256'] == CORE_SHA256 and
            report['adapter_sha256'] == json.loads(ADAPTER_PIN.read_text())['binary_sha256'] and
            report['observer_sha256'] == digest((HERE / 'rowe_observe.py').read_bytes()) and
            report.get('player_only') is True, 'unverified observer/core/adapter or player authority')
    rows = [row for row in report['observations'] if row['label'] == label]
    require(len(rows) == 1, 'missing or ambiguous observation')
    row = rows[0]
    screen_hash = row['rgb565_sha256']
    require(isinstance(screen_hash, str) and len(screen_hash) == 64 and
            all(c in '0123456789abcdef' for c in screen_hash), 'missing screen digest')
    player = row['parties']['player']
    count = player['count']
    require(type(count) is int and 0 <= count <= 6 and
            all(type(player[k]) is int for k in ('count_after', 'count_final')) and
            count == player['count_after'] == player['count_final'] and
            len(party) == 456 and digest(party) == player['sha256'], 'incoherent player snapshot')
    require(type(slot_index) is int and 0 <= slot_index < count and
            len(player['slots']) == count, 'unoccupied player slot')
    hashes = [digest(party[i*76:(i+1)*76]) for i in range(6)]
    require(hashes == player['slot_sha256'], 'record digest mismatch')
    slots = [slot_fields(party[i*76:(i+1)*76]) for i in range(count)]
    require(slots == player['slots'], 'raw decoder/report mismatch')
    raw = party[slot_index*76:(slot_index+1)*76]
    slot = slots[slot_index]
    require(0 <= slot['form'] < FORM_LIMIT, 'unsupported stored form domain')
    static = lookup_party_identity(rom, slot['species'], slot['form'])
    require(static['static_status'] == 'STATIC_VERIFIED', 'unsupported static mapping')
    key = static['metadata_lookup_key']
    types = list(read_span(rom, _require_symbols(rom)['gBaseStats'] +
                           key * BASE_STATS_STRIDE + 12, 2))
    require(all(t in set(range(19)) - {9} for t in types), 'unsupported static types')
    require(isinstance(ui, dict) and ui.get('source') == 'MANUAL_GAME_UI' and
            ui.get('label') == label and ui.get('slot_index') == slot_index and
            ui.get('rgb565_sha256') == row['rgb565_sha256'] and
            ui.get('record_sha256') == hashes[slot_index], 'missing or unbound UI evidence')
    for field in ('species_name', 'party_summary_context', 'mode', 'process',
                  'checkpoint', 'observer', 'type_labels'):
        require(isinstance(ui.get(field), str) and bool(ui[field].strip()),
                'missing UI evidence: ' + field)
    require(type(ui.get('level')) is int, 'missing UI level')
    cue_match = ui.get('form_cue_matches_static')
    require(cue_match is None or type(cue_match) is bool, 'invalid human form assessment')
    require(type(ui.get('mode_verified')) is bool, 'missing mode verification assessment')
    if cue_match is not None:
        require(isinstance(ui.get('form_cue'), str) and bool(ui['form_cue'].strip()),
                'missing distinctive UI form cue')
    status = 'RUNTIME_OBSERVED'
    level_comparison = ('MATCH' if ui['level'] == slot['level'] else
                        'LEVEL_DIFFERENCE_VERIFIED_MODE' if ui['mode_verified'] else
                        'LEVEL_DIFFERENCE_UNVERIFIED_MODE')
    if ui['species_name'] != static['static_name_candidate'] or cue_match is False:
        status = 'CONTRADICTED'
    elif slot['form'] > 0 and cue_match is True and ui['mode_verified'] and level_comparison == 'MATCH':
        status = 'RUNTIME_CONFIRMED'
    # A verified configuration alone does not establish its effective-level accessor.
    # Any level difference blocks confirmation without attributing a form contradiction.
    # A shared name alone never confirms a nonzero-form mapping.
    return dict(schema_version=1, status=status, rom=identity, label=label,
                slot_index=slot_index, player_count=count, record_sha256=hashes[slot_index],
                pid_sha256=digest(raw[:4]), otid_sha256=digest(raw[4:8]),
                nickname_sha256=digest(raw[8:20]), stored_level=slot['level'],
                stored_moves=slot['moves'], hp=slot['hp'], max_hp=slot['stats']['max_hp'],
                static=static, raw_type_ids=types, ui=ui, level_comparison=level_comparison,
                effective_form_status='NOT_VERIFIED',
                comparison_basis='exact-ROM name and explicit human UI form-cue assessment; level compared separately',
                scope='this player record and transcribed mode only; no production support')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for option in ('rom', 'report', 'party', 'ui', 'output'):
        parser.add_argument('--' + option, type=Path, required=True)
    parser.add_argument('--label', required=True)
    parser.add_argument('--slot', type=int, required=True)
    args = parser.parse_args()
    inputs = [args.rom, args.report, args.party, args.ui, Path(__file__),
              *HERE.glob('*.py'), ADAPTER_PIN]
    if any(args.output.resolve() == p.resolve() or
           (args.output.exists() and p.exists() and args.output.samefile(p)) for p in inputs):
        parser.error('output must not overwrite input or tool')
    # Reports/UI contain private source details; keep all capture inputs/output outside Git.
    try:
        require_private_directory(args.output)
    except ValueError as error:
        print('BLOCKED: ' + str(error), file=sys.stderr)
        return 1
    result = dict(schema_version=1, status='BLOCKED')
    args.output.write_text(json.dumps(result) + '\n')  # Invalidate last success first.
    try:
        for path in (args.report, args.party, args.ui):
            require_private_directory(path)
        result = compare(load_exact_rom(args.rom), json.loads(read_local(args.report, 4*1024*1024)),
                         args.label, read_local(args.party, 456), args.slot,
                         json.loads(read_local(args.ui, 65536)))
        result['comparison_tool_sha256'] = digest(Path(__file__).read_bytes())
        result['source_sha256'] = {key: digest(path.read_bytes()) for key, path in
                                  [('report', args.report), ('party', args.party), ('ui', args.ui)]}
    except (ValueError, OSError, KeyError, TypeError, IndexError) as error:
        result = dict(schema_version=1, status='BLOCKED', error=str(error))
        print('BLOCKED: ' + str(error), file=sys.stderr)
    args.output.write_text(json.dumps(result, sort_keys=True, indent=2) + '\n')
    return 0 if result['status'] in ('RUNTIME_OBSERVED', 'RUNTIME_CONFIRMED') else 1


if __name__ == '__main__':
    sys.exit(main())
