"""Invented records/events only; real execution is separate private evidence."""
import copy
import json
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch

import rowe_startup_trace as trace
import test_rowe_save_identity as fixtures


class StartupTraceTest(unittest.TestCase):
    def setUp(self):
        fixture = fixtures.SaveIdentityTest()
        fixture.setUp()
        self.addCleanup(fixture.doCleanups)
        self.rom, self.before, self.after = fixture.rom, fixture.before, fixture.after
        self.events = []
        def hit(pc, raw=None, **regs):
            raw = self.before if raw is None else raw
            registers = [0]*16
            registers[15] = pc+2
            for key, val in regs.items(): registers[int(key[1:])] = val
            self.events.append(dict(order=len(self.events), reason=2, address=pc,
                registers=registers, record_hex=raw.hex(), word=struct.unpack_from('<I', raw, 28)[0],
                save_record_sha256=trace.digest(self.before)))
        def write(pc, raw, width, new):
            hit(pc, raw, r5=trace.SLOT)
            e=self.events[-1]
            e.update(reason=3, address=trace.DEST, width=width, old=e['word'] & ((1 << (width*8))-1), new=new)
            e['registers'][15]=pc+4
        hit(0x0803014a)
        hit(0x080a4044, r14=0x0803014f)
        hit(0x080a4062, r5=0x02025128, r8=0)
        hit(0x080a4062, r5=trace.SLOT, r8=1)
        hit(0x080a4092, r5=trace.SLOT, r4=1560)
        hit(0x080a4098, r0=52)
        hit(0x080a40a2, r0=1)
        hit(0x080a40aa, r0=trace.SLOT, r1=11)
        hit(0x0809b57c, r0=trace.SLOT, r1=11)
        write(0x0809b980, self.before, 2, 52|4096)
        hit(0x080a40ae, self.after)
        hit(0x080a40b4, self.after, r0=trace.SLOT, r1=89, r2=2)
        hit(0x0809b57c, self.after, r0=trace.SLOT, r1=89, r2=2)
        write(0x0809b9ae, self.after, 4, struct.unpack_from('<I', self.after, 28)[0])
        hit(0x080a40b8, self.after)
        for i in range(2,6): hit(0x080a4062, self.after, r5=0x02025128+i*76, r8=i)
        hit(0x0803014e, self.after)
        # Insert selected getter and helper entry witnesses without hard-coding event indices.
        for target, pc, regs in ((0x080a4092,0x080a407c,{0:1560,5:trace.SLOT}),
                                 (0x080a4098,0x080a18e8,{0:1560,5:trace.SLOT}),
                                 (0x080a40a2,0x080a18a0,{0:1560,5:trace.SLOT})):
            idx=next(i for i,e in enumerate(self.events) if e['address']==target)
            e=copy.deepcopy(self.events[idx]);e['address']=pc;e['registers'][15]=pc+2
            for reg,val in regs.items():e['registers'][reg]=val
            self.events.insert(idx,e)
        for i,e in enumerate(self.events):e['order']=i
        self.capture=dict(core_sha256=trace.DIAGNOSTIC, source_revision=trace.SOURCE,
            cartridge_sha256=trace.CARTRIDGE, bootstrap='CARTRIDGE_ONLY',
            enabled_before_first_run=True, complete=True, errors=[], events=self.events)

    def run_trace(self):
        return trace.validate(self.rom, self.capture)

    def remove(self, predicate):
        self.capture['events'] = [e for e in self.capture['events'] if not predicate(e)]
        for i,e in enumerate(self.capture['events']): e['order']=i

    def test_valid_transition_and_full_execution(self):
        r=self.run_trace()
        self.assertEqual(r['status'], 'EXECUTED_PATH_VERIFIED')
        self.assertEqual(r['record_sha256'], [trace.digest(self.before),trace.digest(self.after)])
        self.assertNotIn('record_hex', json.dumps(r))

    def test_admission_and_wrong_rom_core_source_cartridge(self):
        trace.admit(trace.DIAGNOSTIC, trace.SOURCE, trace.CARTRIDGE)
        for key in ('core_sha256','source_revision','cartridge_sha256'):
            with self.subTest(key=key), patch.dict(self.capture, {key:'wrong'}), self.assertRaises(ValueError): self.run_trace()
        with patch.dict(self.capture, core_sha256=trace.CORE_SHA256), self.assertRaises(ValueError): self.run_trace()
        with self.assertRaises(ValueError): trace.validate(b'wrong',self.capture)

    def test_callsite_without_function(self):
        self.remove(lambda e:e['address']==0x080a4044)
        self.assertEqual(self.run_trace()['status'],'CALLSITE_EXECUTED')

    def test_entry_without_branch(self):
        self.remove(lambda e:e['address']==0x080a4092)
        self.assertEqual(self.run_trace()['status'],'FUNCTION_EXECUTED')

    def test_branch_without_verified_write(self):
        self.remove(lambda e:e['reason']==3)
        r=self.run_trace()
        self.assertEqual(r['selected_branch'],'VERIFIED')
        self.assertEqual(r['write'],'NOT_VERIFIED')
        self.assertNotEqual(r['status'],'EXECUTED_PATH_VERIFIED')

    def test_unrelated_slot_or_destination(self):
        for key,val in (('address',trace.DEST+76),('address',trace.DEST+2)):
            cap=copy.deepcopy(self.capture);next(e for e in cap['events'] if e['reason']==3)[key]=val
            with self.assertRaises(ValueError): trace.validate(self.rom,cap)
        cap=copy.deepcopy(self.capture)
        for e in cap['events']:
            if e['address'] in (0x080a40b4,0x0809b57c) and e['registers'][1]==89:
                e['registers'][2]+=4
        with self.assertRaisesRegex(ValueError,'source linkage'):trace.validate(self.rom,cap)
        next(e for e in self.events if e['reason']==3)['registers'][5]+=76
        with self.assertRaises(ValueError): self.run_trace()

    def test_negative_identity_gate_for_every_unrelated_byte(self):
        self.assertTrue(trace.identity_gate(self.rom,self.before,self.after))
        for i in range(76):
            if i in (28,29):continue
            changed=self.after.copy();changed[i]^=1
            with self.subTest(offset=i):self.assertFalse(trace.identity_gate(self.rom,self.before,changed))
        changed=self.after.copy();changed[0]^=1
        next(e for e in self.events if e['address']==0x080a40ae)['record_hex']=changed.hex()
        with self.assertRaises(ValueError):self.run_trace()

    def test_unexpected_form_index(self):
        next(e for e in self.events if e['address']==0x080a40a2)['registers'][0]=2
        with self.assertRaises(ValueError):self.run_trace()

    def test_duplicate_conflict_and_invalid_pc(self):
        for mutation in ('order','pc','word','duplicate'):
            cap=copy.deepcopy(self.capture)
            if mutation=='order':cap['events'][5]['order']=4
            if mutation=='pc':cap['events'][5]['registers'][15]+=2
            if mutation=='word':cap['events'][5]['word']^=1
            if mutation=='duplicate':
                cap['events'].insert(6,copy.deepcopy(cap['events'][5]))
                for i,e in enumerate(cap['events']):e['order']=i
            with self.subTest(mutation=mutation),self.assertRaises(ValueError):trace.validate(self.rom,cap)

    def test_missing_truncated_incomplete_and_no_static_promotion(self):
        for changes in ({'events':[]},{'complete':False},{'errors':['callback failed']},
                        {'bootstrap':'STATE'},{'enabled_before_first_run':False}):
            with patch.dict(self.capture,changes),self.assertRaises(ValueError):self.run_trace()
        self.remove(lambda e:e['address']==0x0803014e)
        self.assertEqual(self.run_trace()['status'],'WRITE_ATTRIBUTED')
        self.remove(lambda e:e['address']==0x0803014a)
        self.assertEqual(self.run_trace()['status'],'BOUNDARY_PARTIAL')

    def test_stale_success_cleared_after_failed_trace(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);output=root/'result.json';output.write_text('{"status":"EXECUTED_PATH_VERIFIED"}')
            core=root/'core';core.write_bytes(b'wrong')
            with patch.object(trace,'require_private_directory'):
                code=trace.main(['--rom',str(root/'missing'),'--core',str(core),
                                 '--events',str(root/'missing-events'),'--output',str(output)])
            self.assertEqual(code,1)
            self.assertEqual(json.loads(output.read_text())['status'],'NOT_VERIFIED')

    def test_cli_partial_status_requires_explicit_allowance(self):
        partial = copy.deepcopy(self.capture)
        partial['events'] = [e for e in partial['events'] if e['address'] != 0x0803014a]
        for i, event in enumerate(partial['events']): event['order'] = i
        for allowed, expected in ((False, 1), (True, 0)):
            with self.subTest(allow_partial=allowed), tempfile.TemporaryDirectory() as folder:
                root=Path(folder);rom=root/'rom';core=root/'core';events=root/'events.json';output=root/'result.json'
                rom.write_bytes(b'rom');core.write_bytes(b'core');events.write_text(json.dumps(partial))
                args=['--rom',str(rom),'--core',str(core),'--events',str(events),'--output',str(output)]
                if allowed: args.append('--allow-partial')
                real_digest=trace.digest
                def fake_digest(data): return trace.DIAGNOSTIC if data == b'core' else real_digest(data)
                with patch.object(trace,'read_local',side_effect=lambda path, limit: Path(path).read_bytes()), \
                     patch.object(trace,'load_exact_rom',return_value=self.rom), \
                     patch.object(trace,'digest',side_effect=fake_digest), \
                     patch.object(trace,'require_private_directory'):
                    self.assertEqual(trace.main(args),expected)
                self.assertEqual(json.loads(output.read_text())['status'],'BOUNDARY_PARTIAL')

    def test_output_aliases_and_symlinks_are_refused_before_invalidation(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);source=root/'input';source.write_text('input')
            alias=root/'alias';alias.hardlink_to(source)
            with patch.object(trace,'require_private_directory'), \
                 self.assertRaises(ValueError): trace._prepare_output(alias,(source,))
            self.assertEqual(source.read_text(),'input')
            target=root/'target';target.write_text('keep')
            symlink=root/'output-link';symlink.symlink_to(target)
            with patch.object(trace,'require_private_directory'), \
                 self.assertRaises(ValueError): trace._prepare_output(symlink,())
            self.assertEqual(target.read_text(),'keep')

    def test_invalid_private_paths_do_not_write_and_failed_admission_clears_stale_output(self):
        protected=Path(__file__).resolve()
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);unsafe=protected.parent/'.must-not-touch-rowe-trace.json'
            with self.assertRaises(ValueError): trace._prepare_output(unsafe,(root/'input',))
            self.assertFalse(unsafe.exists())
            output=root/'result.json';output.write_text('{"status":"EXECUTED_PATH_VERIFIED"}')
            private_events=protected
            def require_private(path):
                if Path(path).resolve() == private_events:
                    raise ValueError('private path rejected')
            with patch.object(trace,'require_private_directory',side_effect=require_private), \
                 patch.object(trace,'read_local',side_effect=OSError('unused')):
                code=trace.main(['--rom',str(root/'rom'),'--core',str(root/'core'),
                    '--events',str(private_events),'--output',str(output)])
            self.assertEqual(code,1)
            self.assertEqual(json.loads(output.read_text())['status'],'NOT_VERIFIED')


class CrossPhaseContractTest(unittest.TestCase):
    def test_committed_contract_and_current_sources(self):
        root=Path(__file__).resolve().parent
        load=lambda name:json.loads((root/'evidence'/name).read_text())
        new=load('startup-normalization-v1.json')
        guards=load('startup-guards-v1.json')
        old=load('form-runtime-v3.json');prior=load('save-load-identity-v1.json')
        self.assertEqual(guards['schema_version'],1)
        self.assertEqual(guards['phase'],'6G')
        self.assertEqual(guards['starting_main_sha'],'e535a4777338dc7c3b6e95f2316c3f7eef788d1b')
        self.assertEqual(guards['phase_6f_head'],'23e2ad66efa15fdc31d93980174e10133dc48dbe')
        self.assertEqual(guards['phase_6f_tree_sha'],guards['starting_main_tree_sha'])
        self.assertEqual(guards['phase_6f_pull_request']['state'],'MERGED')
        self.assertEqual(guards['source_identity']['rom_sha256'],new['rom']['sha256'])
        self.assertEqual(guards['source_identity']['reference_core_sha256'],new['reference_core_sha256'])
        self.assertEqual(guards['source_identity']['diagnostic_core_sha256'],new['diagnostic']['core_sha256'])
        self.assertEqual(guards['source_identity']['adapter_sha256'],new['adapter_sha256'])
        self.assertEqual(guards['source_identity']['cartridge_sha256'],new['cartridge_sha256'])
        self.assertEqual(guards['prior_evidence']['manifest_sha256'],trace.digest(
            (root/'evidence/startup-normalization-v1.json').read_bytes()))
        self.assertEqual(guards['new_trace_source_sha256'],trace.digest((root/'rowe_startup_trace.py').read_bytes()))
        self.assertEqual(guards['validator_p3_fixes']['status'],'PASS')
        self.assertEqual(guards['phase_6f_normalization']['record_sha256'],new['trace_result']['record_sha256'])
        investigation=guards['guard_investigation']
        self.assertEqual(investigation['caller_control_flow'],'NOT_VERIFIED')
        self.assertEqual(investigation['guard_operand_sources'],'NOT_VERIFIED')
        self.assertEqual(investigation['normal_mode_guard_execution'],'NOT_VERIFIED')
        self.assertEqual(investigation['false_branch_conditions'],'NOT_VERIFIED')
        self.assertEqual(investigation['static_guard_instructions'],[])
        self.assertEqual(investigation['runtime_guard_events'],[])
        self.assertEqual(guards['integration_contract']['status'],'PARTIAL')
        self.assertEqual(guards['integration_contract']['production_compatibility'],'HOLD')
        for name,sha in new['prior_manifests'].items():
            self.assertEqual(trace.digest((root/'evidence'/name).read_bytes()),sha,name)
        # These historical pins remain the exact Phase 6F source identities; the
        # current validator and its tests continue evolving on top of that evidence.
        self.assertEqual(new['tools']['rowe_startup_trace.py'],
                         '122056b84cc84a4442396e98b39363361f8ad8e796513d0b743d93ecac2996a5')
        self.assertEqual(new['tools']['test_rowe_startup_trace.py'],
                         '75fc269a1b6730940ede162f8ee83fcb129a00ae2c0b45b83121948cb7b46fbc')
        for name,sha in new['tools'].items():
            if name not in ('rowe_startup_trace.py','test_rowe_startup_trace.py'):
                self.assertEqual(trace.digest((root/name).read_bytes()),sha,name)
        self.assertEqual(new['rom'],prior['rom']);self.assertEqual(new['rom'],old['rom'])
        self.assertEqual(new['reference_core_sha256'],old['core_sha256'])
        self.assertEqual(new['adapter_sha256'],old['adapter']['binary_sha256'])
        for name,sha in old['adapter']['inputs'].items():
            self.assertEqual(trace.digest((root.parents[1]/name).read_bytes()),sha,name)
        for name,sha in old['tools'].items():
            if name != 'rowe_startup_trace.py':
                self.assertEqual(trace.digest((root/name).read_bytes()),sha,name)
        self.assertEqual(new['cartridge_sha256'],trace.CARTRIDGE)
        self.assertEqual(old['private_artifact_sha256']['reload/cartridge.sav'],trace.CARTRIDGE)
        self.assertEqual(new['serialized'],prior['serialized'])
        self.assertEqual(new['identity'],prior['identity'])
        self.assertEqual(new['diagnostic']['core_sha256'],trace.DIAGNOSTIC)
        self.assertEqual(new['diagnostic']['source_revision'],old['core_attributed_source'])
        self.assertEqual(new['diagnostic']['patch_sha256'],trace.digest((root/'rowe_startup_debugger.patch').read_bytes()))
        self.assertEqual(new['trace_result']['record_sha256'],prior['identity']['record_sha256'])
        self.assertEqual(new['trace_result']['status'],'EXECUTED_PATH_VERIFIED')
        for comparison in old['comparisons']:
            self.assertEqual(comparison['status'],'RUNTIME_CONFIRMED')
            self.assertEqual(comparison['static']['metadata_lookup_key'],1560)
            self.assertIn(comparison['record_sha256'],new['trace_result']['record_sha256'])
        pairs=prior['identity']['raw_identities']
        self.assertEqual([(r['raw_stored_species_id'],r['raw_form_id']) for r in pairs],[(1560,1),(52,1)])
        observed={o['label']:o for o in old['observations']}
        self.assertEqual(new['serialized']['party_sha256'],observed['captured_party']['party_region_sha256'])
        for check in new['parity']['checks']:
            self.assertEqual(check['party_sha256'],observed['persisted_party']['party_region_sha256'])
            self.assertEqual(check['record_sha256'],new['trace_result']['record_sha256'][1])
            self.assertEqual(check['saved_record_sha256'],new['trace_result']['record_sha256'][0])
        self.assertEqual(prior['boundary']['status'],'BOUNDARY_PARTIALLY_LOCALIZED')


if __name__ == '__main__':unittest.main()
