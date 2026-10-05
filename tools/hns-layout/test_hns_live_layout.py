"""ROM-free generated-layout/domain and phase-binding regression checks."""
import re
import struct
import unittest
import importlib.util
from pathlib import Path
import generate_hns_live_battle_layout as layout
import generate_hns_release_phase_evidence as phase


class LayoutTest(unittest.TestCase):
    def test_retained_official_rom_transitions_and_phase_invalidation(self):
        path = Path(__file__).resolve().parents[1] / 'hns-runtime-probe/verify_group_d_evidence.py'
        spec = importlib.util.spec_from_file_location('evidence', path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        self.assertGreaterEqual(len(module.check_evidence()), 20)

    def test_bitfield_probes_include_every_operand(self):
        source = layout.build_probe_c()
        for name in ['slowStartTimer','flashFireBoosted','transformed','boosterEnergyActivated',
                     'paradoxBoostedStat','vesselOfRuin','swordOfRuin','tabletsOfRuin','beadsOfRuin',
                     'neutralizingGas','isFirstTurn','personality','supremeOverlordCounter',
                     'usableGimmick','playerSelect','healBlock','embargo','metronomeItemCounter',
                     'transformedMonSpecies']:
            self.assertIn(name, source)

    def test_noncontiguous_bitfield_is_rejected(self):
        with self.assertRaises(SystemExit):
            layout.multi_bit(bytearray([5]), 0, {'probe':(0,1)}, 'probe')
        self.assertEqual(layout.multi_bit(bytearray([0, 28]),0,{'probe':(0,2)},'probe'),(10,3))

    def test_window_is_minimum_for_furthest_compiled_operand(self):
        header=(Path(__file__).resolve().parents[2]/'native/src/hns_live_battle_layout_gen.h').read_text()
        values={name:int(value,0) for name,value in re.findall(r'#define (\w+) (0x[\dA-Fa-f]+|\d+)u?',header)}
        end=max(values[name]+values.get(name[:-4]+'_WIDTH',1) for name in values
                if name.startswith('HNS_LIVE_BP_VOLATILE_') and name.endswith('_BIT'))
        self.assertEqual(values['HNS_LIVE_BP_VOLATILE_WINDOW_BYTES'],(end+7)//8)

    def test_phase_evidence_is_release_bound_and_unique(self):
        p=layout.PHASE_EVIDENCE
        self.assertEqual(p['releaseRomSha256'],phase.ROM_SHA256)
        self.assertEqual(p['upstreamCommit'],layout.PINNED_COMMIT)
        self.assertTrue(all(v['matches']==1 for v in p['functions'].values()))
        self.assertNotEqual(p['functions']['RunTurnActionsFunctions']['sourceAddress'],
                            p['functions']['RunTurnActionsFunctions']['releaseAddress'])

    def test_unique_relocated_instruction_match_and_duplicate_failure(self):
        # Tiny synthetic Thumb instructions, not cartridge bytes.
        code=struct.pack('<8H',0xb510,0x2200,0x2101,0x2302,0x4803,0xf001,0xf812,0x4770)
        changed=struct.pack('<8H',0xb510,0x2200,0x2101,0x2302,0x48fa,0xf123,0xfabc,0x4770)
        found=phase.bind(code,b'\0'*32+changed,0x08000000,len(code))
        self.assertEqual(found['releaseAddress'],0x08000020)
        with self.assertRaises(ValueError):
            phase.bind(code,changed+changed,0x08000000,len(code))
        with self.assertRaises(ValueError):
            phase.bind(code,bytes(64),0x08000000,len(code))


if __name__=='__main__': unittest.main()
