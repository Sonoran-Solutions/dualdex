"""Invented bytes only; these tests cannot establish a real form observation."""
import copy
import json
from pathlib import Path
import struct
import sys
import tempfile
import unittest
from types import SimpleNamespace
from unittest.mock import patch

import rowe_data
import rowe_form_observe as form
from rowe_observe import CORE_SHA256, HERE, digest, slot_fields
from rowe_text import VALID_TEXT, EMPTY_OR_UNSET


class FormObservationTest(unittest.TestCase):
    def setUp(self):
        self.raw = bytearray(456)
        struct.pack_into('<I', self.raw, 28, 1 | (1 << 12))
        self.raw[60] = 5
        struct.pack_into('<HH', self.raw, 62, 10, 20)
        self.party = bytes(self.raw)
        self.hashes = [digest(self.party[i*76:(i+1)*76]) for i in range(6)]
        self.player = dict(count=1, count_after=1, count_final=1, sha256=digest(self.party),
                           slot_sha256=self.hashes, slots=[slot_fields(self.party[:76])])
        self.report = dict(status='OBSERVED', rom={'sha256': 'synthetic'},
            core_sha256=CORE_SHA256, adapter_sha256=json.loads(form.ADAPTER_PIN.read_text())['binary_sha256'],
            observer_sha256=digest((HERE/'rowe_observe.py').read_bytes()), player_only=True,
            observations=[dict(label='summary', rgb565_sha256='a'*64, parties={'player': self.player})])
        self.ui = dict(source='MANUAL_GAME_UI', label='summary', slot_index=0,
            rgb565_sha256='a'*64, record_sha256=self.hashes[0], species_name='Invented',
            level=5, type_labels='untranslated synthetic labels', party_summary_context='slot 0 summary',
            mode='invented mode', mode_verified=True, process='synthetic process', checkpoint='synthetic checkpoint',
            observer='synthetic observer', form_cue='distinctive invented appearance',
            form_cue_matches_static=True)
        self.static = dict(raw_stored_species_id=1, raw_form_id=1, static_resolved_species_id=3,
                           metadata_lookup_key=3, static_name_candidate='Invented',
                           static_status='STATIC_VERIFIED', runtime_effective_identity_status='NOT_VERIFIED')

    def compare(self, ui=None):
        with patch.object(form, 'rom_identity', return_value=self.report['rom']), \
             patch.object(form, 'lookup_party_identity', return_value=self.static), \
             patch.object(form, '_require_symbols', return_value={'gBaseStats': 0x08000000}), \
             patch.object(form, 'read_span', return_value=bytes([17, 0])):
            return form.compare(b'invented', self.report, 'summary', self.party, 0,
                                self.ui if ui is None else ui)

    def test_nonzero_identity_distinction_and_explicit_ui_corroboration(self):
        result = self.compare()
        self.assertEqual(result['status'], 'RUNTIME_CONFIRMED')
        self.assertEqual(result['static']['raw_stored_species_id'], 1)
        self.assertEqual(result['static']['static_resolved_species_id'], 3)
        self.assertEqual(result['effective_form_status'], 'NOT_VERIFIED')
        self.assertNotIn('nickname_hex', result)
        self.assertNotIn('pid', result)
        self.ui['mode_verified'] = False
        self.assertEqual(self.compare()['status'], 'RUNTIME_OBSERVED')
        self.ui['mode_verified'] = True
        self.ui['form_cue_matches_static'] = None
        self.ui.pop('form_cue')
        self.assertEqual(self.compare()['status'], 'RUNTIME_OBSERVED')

    def test_contradiction_is_preserved_even_when_names_match(self):
        self.ui['form_cue_matches_static'] = False
        self.assertEqual(self.compare()['status'], 'CONTRADICTED')
        self.ui['form_cue_matches_static'] = True
        self.ui['species_name'] = 'Different identity'
        self.assertEqual(self.compare()['status'], 'CONTRADICTED')
        self.ui['species_name'] = 'Invented'
        self.ui['level'] = 6
        self.assertEqual(self.compare()['status'], 'RUNTIME_OBSERVED')

    def test_form_zero_with_positive_human_evidence_never_confirms(self):
        struct.pack_into('<I', self.raw, 28, 1)
        self.party = bytes(self.raw)
        self.player.update(sha256=digest(self.party),
            slot_sha256=[digest(self.party[i*76:(i+1)*76]) for i in range(6)],
            slots=[slot_fields(self.party[:76])])
        self.ui['record_sha256'] = self.player['slot_sha256'][0]
        self.static.update(raw_form_id=0, static_resolved_species_id=1, metadata_lookup_key=1)
        self.assertEqual(self.compare()['status'], 'RUNTIME_OBSERVED')

    def test_level_difference_does_not_attribute_identity_contradiction(self):
        self.ui['level'] = 6
        for verified in (False, True):
            self.ui['mode_verified'] = verified
            result = self.compare()
            self.assertEqual(result['status'], 'RUNTIME_OBSERVED')
            self.assertEqual(result['level_comparison'], 'LEVEL_DIFFERENCE_' +
                             ('VERIFIED_MODE' if verified else 'UNVERIFIED_MODE'))
            self.assertEqual((result['stored_level'], result['ui']['level']), (5, 6))
            for field, value in [('species_name', 'Wrong'), ('form_cue_matches_static', False)]:
                ui = dict(self.ui, **{field: value})
                self.assertEqual(self.compare(ui)['status'], 'CONTRADICTED')

    def test_missing_or_unbound_ui_and_invalid_snapshot_refuse(self):
        for field in self.ui:
            if field == 'form_cue_matches_static': continue
            ui = copy.deepcopy(self.ui);ui.pop(field)
            with self.subTest(field=field), self.assertRaises(ValueError): self.compare(ui)
        for field in ('rgb565_sha256', 'record_sha256'):
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, 'unbound'):
                self.compare(dict(self.ui, **{field: 'b'*64}))
        for value in ('', None, 'not-a-hash'):
            self.report['observations'][0]['rgb565_sha256'] = value
            with self.assertRaisesRegex(ValueError, 'screen digest'):
                self.compare(dict(self.ui, rgb565_sha256=value))
        self.report['observations'][0]['rgb565_sha256'] = 'a'*64
        for field in ('count_after', 'count_final'):
            self.player[field] = 2
            with self.assertRaisesRegex(ValueError, 'incoherent'): self.compare()
            self.player[field] = 1
        self.player['slots'][0]['form'] = 0
        with self.assertRaisesRegex(ValueError, 'decoder/report'): self.compare()
        self.player['slots'][0]['form'] = 1
        for status in ('UNSUPPORTED_FORM_ID', 'UNSUPPORTED_FORM_TABLE', 'UNSUPPORTED_RESOLVED_SPECIES'):
            self.static['static_status'] = status
            with self.subTest(status=status), self.assertRaisesRegex(ValueError, 'static mapping'): self.compare()
        self.static['static_status'] = 'STATIC_VERIFIED'
        self.report['player_only'] = False
        with self.assertRaisesRegex(ValueError, 'player authority'): self.compare()

    def test_pointer_reader_zero_nonzero_null_sentinel_invalid_and_hole(self):
        raw = bytearray(128)
        records = [SimpleNamespace(status=EMPTY_OR_UNSET, text=None)] + [
            SimpleNamespace(status=VALID_TEXT, text='Invented') for _ in range(4)]
        struct.pack_into('<I', raw, 4, rowe_data.ROM_BASE + 32)
        struct.pack_into('<3H', raw, 32, 1, 3, 0xffff)
        with patch.object(rowe_data, 'rom_identity'), \
             patch.object(rowe_data, 'FORM_POINTER_TABLE', rowe_data.ROM_BASE):
            def resolve(s, f):return rowe_data.resolve_form(bytes(raw), s, f, records)
            self.assertEqual(resolve(1,0)['resolved_species_id'], 1)
            self.assertEqual(resolve(1,1)['resolved_species_id'], 3)
            for f in (-1, 2, 32): self.assertEqual(resolve(1,f)['status'], 'UNSUPPORTED_FORM_ID')
            # The exact game's null-pointer branch returns the stored species even for nonzero form.
            self.assertEqual(resolve(2,1)['resolved_species_id'], 2)
            for v in (0, 5, 65534):
                struct.pack_into('<H', raw, 34, v)
                self.assertEqual(resolve(1,1)['status'], 'UNSUPPORTED_RESOLVED_SPECIES')
            struct.pack_into('<H', raw, 34, 3)
            records[3] = SimpleNamespace(status=EMPTY_OR_UNSET, text=None)
            self.assertEqual(resolve(1,1)['status'], 'UNSUPPORTED_RESOLVED_SPECIES')
            raw[32:96] = struct.pack('<32H', *([1]*32))
            self.assertEqual(resolve(1,1)['status'], 'UNSUPPORTED_FORM_TABLE')
            struct.pack_into('<I', raw, 4, rowe_data.ROM_BASE+127)
            with self.assertRaisesRegex(ValueError, 'outside ROM'): resolve(1,1)

    def test_committed_evidence_cannot_promote_static_candidates(self):
        evidence = json.loads((HERE/'evidence/form-runtime-v1.json').read_text())
        self.assertEqual(evidence['overall_status'], 'BLOCKED')
        self.assertEqual(evidence['nonzero_form_runtime_status'], 'NOT_VERIFIED')
        self.assertEqual(len(evidence['acquisition']['attempts']), 3)
        self.assertTrue(all(v == 'NOT_RUN' for v in evidence['nonzero_persistence'].values()))
        self.assertEqual(evidence['observer_sha256'], digest((HERE/'rowe_observe.py').read_bytes()))
        for name, sha in evidence['tools'].items():
            if name == 'rowe_form_observe.py':
                # Historical Phase 6 comparator pin; do not rewrite the old evidence.
                self.assertEqual(sha, 'c2e516a115a4ef4aad694a89a6de8cb11f88b87162f0846b67db13b94d9e72e6')
            else:
                self.assertEqual(sha, digest((HERE/name).read_bytes()))
        for row in evidence['baseline_comparisons']:
            self.assertEqual(row['status'], 'RUNTIME_OBSERVED')
            self.assertEqual(row['static']['raw_form_id'], 0)
            self.assertEqual(row['effective_form_status'], 'NOT_VERIFIED')
        candidate = evidence['static_candidates'][1]
        self.assertEqual((candidate['raw_stored_species_id'], candidate['raw_form_id'],
                          candidate['static_resolved_species_id'], candidate['raw_type_ids']),
                         (19, 1, 1551, [17, 0]))
        self.assertEqual(candidate['runtime_effective_identity_status'], 'NOT_VERIFIED')
        replay = evidence['acquisition']['replay']
        self.assertEqual(replay['movie_sha256'], digest((HERE/replay['movie']).read_bytes()))

    def test_success_then_invalid_cli_clears_and_never_uses_other_tables(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder); output=root/'result.json'; rom=root/'rom.bin'
            rom.write_bytes(b'invented non-ROM'); output.write_text('{"status":"RUNTIME_CONFIRMED","static":{}}')
            command=[sys.executable,str(Path(form.__file__)), '--rom',str(rom),
                     '--report',str(root/'absent-report'), '--party',str(root/'absent-party'),
                     '--ui',str(root/'absent-ui'), '--output',str(output),'--label','summary','--slot','0']
            # Use the CLI function with a private-directory mock: /tmp may have a sandbox .git marker.
            with patch.object(sys, 'argv', command[1:]), patch.object(form, 'require_private_directory'):
                self.assertEqual(form.main(),1)
            result=json.loads(output.read_text())
            self.assertEqual(result['status'],'BLOCKED')
            self.assertNotIn('static',result)
            self.assertIn('size',result['error'])
        for rom in (b'Emerald', b'H&S', b'global Pokemon table'):
            with self.assertRaises(ValueError): form.compare(rom, self.report,'summary',self.party,0,self.ui)


if __name__ == '__main__': unittest.main()
