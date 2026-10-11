"""ROM-free generated-layout/domain and phase-binding regression checks."""
import re
import struct
import unittest
import importlib.util
from pathlib import Path
import generate_hns_live_battle_layout as layout
import generate_hns_release_phase_evidence as phase


class LayoutTest(unittest.TestCase):
    def test_menu_bindings_and_generated_defines(self):
        header = (Path(__file__).resolve().parents[2] / 'native/src/hns_live_battle_layout_gen.h').read_text()
        p = layout.PHASE_EVIDENCE
        self.assertEqual(set(phase.MENU_PREFIXES), set(layout.MENU_HANDLER_ADDRESSES))
        for line in layout.menu_defines():
            self.assertEqual(header.count(line + '\n'), 1)
        for name in ('gActionSelectionCursor', 'gMoveSelectionCursor'):
            v = p['globals'][name]
            self.assertEqual(v['sourceValue'], v['address'])
            self.assertIn(f'#define HNS_LIVE_{name.upper()}_GBA_ADDRESS 0x{v["address"]:08X}u\n', header)
        funcs = p['globals']['gBattlerControllerFuncs']
        self.assertEqual(funcs['address'], funcs['corroboration']['address'])
        self.assertNotEqual(funcs['sourceValue'], funcs['address'])
        for name in ('HandleInputChooseMove', 'HandleInputChooseTarget'):
            self.assertEqual(p['functions'][name]['interior']['matches'], 1)
        self.assertEqual(p['functions']['HandleInputChooseMove']['reference']['address'],
                         p['functions']['HandleInputChooseMove']['releaseAddress'] | 1)

    def test_static_action_symbol_is_resolved_only_in_player_translation_unit(self):
        symbols = '\n'.join(f'{address:08x} l 00000080 {name}' for name, address in [
            ('HandleInputChooseAction', 0x08000100), ('HandleInputChooseAction', 0x08000200),
            ('HandleInputChooseAction', 0x08000300), ('HandleInputChooseMove', 0x08000220),
            ('HandleInputChooseTarget', 0x08000240)])
        map_text = ' .text 0x08000200 0x80 src/battle_controller_player.o\n'
        self.assertEqual(phase.player_symbols(symbols, map_text)['HandleInputChooseAction'], 0x08000200)
        with self.assertRaises(ValueError):
            phase.player_symbols(symbols + '\n08000202 l 00000010 HandleInputChooseAction', map_text)
        with self.assertRaises(ValueError):
            phase.player_symbols(symbols, '')

    def test_release_data_literal_is_resolved_independently_of_source_address(self):
        # Synthetic instructions and pools; no cartridge bytes.
        code = struct.pack('<8H', 0x4803, 0x2200, 0x2101, 0x2302, 0x2403, 0x2504, 0x2605, 0x4770)
        source = code + struct.pack('<I', 0x03000020)
        release = bytes(32) + code + struct.pack('<I', 0x03000038)
        result = phase.bind_literal(source, release, 0x08000000, 16, 0x03000020)
        self.assertEqual(result['address'], 0x03000038)
        with self.assertRaises(ValueError):
            phase.bind_literal(source, release, 0x08000000, 16, 0x03000021)
        with self.assertRaises(ValueError):
            phase.literal_load(bytes(32), 0x08000000)

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
