#!/usr/bin/env python3
"""Exact-candidate Unbound party-record semantics: ability, shiny, nature, checksum.

Read-only and host-only. `inspect_semantics()` rechecks manually disassembled
exact-ROM spans by SHA-256 and decodes a few numeric facts from them (hook
targets, the shiny compare immediate, checksum caller census, box geometry).
`battle_check()` and `party_check()` compare the exact-ROM ability/shiny/nature
rules with an `unbound_observe.py` battle report and the committed party
manifest. Output is numbers, hashes and booleans only: never code bytes,
table rows beyond the selected ability triples, or game text.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import sys

from unbound_data import TABLES, names
from unbound_observe import ROM_SHA256, pinned
from ups_identity import read_local, require

HERE = Path(__file__).resolve().parent
ROM_BASE = 0x08000000
BASE_STATS_POINTER = 0x1BC          # file offset of the gBaseStats pointer
BASE_STATS, SPECIES_COUNT, BASE_STRIDE = TABLES['base_stats']
ABILITY_OFFSETS = (0x16, 0x17, 0x1A)  # ability1, ability2, hiddenAbility (CFRU BaseStats)
SHINY_CMP = 0x080444B0               # `cmp r2, #imm` in IsShinyOtIdPersonality
CHECKSUM_FN = 0x0803E3E8             # CalculateBoxMonChecksum (vanilla FireRed address)
CHECKSUM_WRITER = 0x08825A30         # inserted routine that stores the checksum (see SPANS)
BOX_POINTER_TABLE, BOX_COUNT = 0x08A6CB2C, 25

# (start, end_exclusive, sha256, finding). Hashes are filled from the exact ROM and
# rechecked on every run; the findings were established by manual Thumb disassembly.
SPANS = {
    'ability': (
        (0x08040D38, 0x08040D40, 'd3d5019088e648d1e0681e98fd874c890b12eb3d0ca0d48b554b5b0b04895207',
         'Vanilla GetMonAbility entry is a hook: ldr r1,=0x089C51B9; bx r1.'),
        (0x089C51B8, 0x089C5220, '7ecd23875b23f7129136d26457ddb5537cd9cd6ebb91c1938962e56f7f89ad20',
         'GetMonAbility: species via GetMonData(11); if record byte 0x4B > 0x7F (IV word bit 31) '
         'and base[species]+0x1A != 0 -> hidden; else if personality bit 0 and base+0x17 != 0 '
         '-> ability2; else ability1.'),
        (0x08A13F1C, 0x08A13F70, '558518662ff785db0cded540d261896809b3b3fe2494f58751bd33ff905a83e7',
         'GetAbility1/2/Hidden read base[species] bytes 0x16/0x17/0x1A (stride 28, table via '
         'pointer 0x080001BC) and pass them through TryRandomizeAbility.'),
        (0x08A13B44, 0x08A13B70, '0b27d75ed657928a6dc9aa596ecf456d85ca5e98e54957c53356ede5a78120d9',
         'TryRandomizeAbility returns the table value unless FlagGet(0x1522) and !FlagGet(0x9F0) '
         'and the ability is not ban-listed (save-dependent override).'),
    ),
    'shiny': (
        (0x08044470, 0x080444C4, '7ca0f1664ed86ec612689c15d2872c29cc6364cfb5551957d5507481449b8ea8',
         'IsMonShiny passes OT ID and personality to IsShinyOtIdPersonality, which returns '
         '(tid ^ sid ^ pid_hi ^ pid_lo) <= 15 (cmp #0xF; bhi). No stored flag is read.'),
        (0x0803DAD2, 0x0803DADC, 'e6dec2f33feef4f6142702de9025dd9a334db3f78404d5e961afae66d4c36e34',
         'CreateBoxMon body is hooked: ldr r0,=0x089A43C5; bx r0.'),
        (0x089A43C4, 0x089A43DE, '28a20906a7096bdf831a25ca60abe69dbb8ed3d3cc1c68b7f5e13d9957107ac1',
         'Hook calls the inserted CreateBoxMon and resumes at 0x0803DD85, skipping the vanilla '
         'body (including its checksum store at 0x0803DB9E).'),
    ),
    'nature': (
        (0x08042E9C, 0x08042EC4, '1368e2fb46801b0d393cad2ed075208b47eaa464fe1a01a61bfa86ba04298f83',
         'GetNature = GetMonData(mon, 0 /*personality*/) % 25; GetNatureFromPersonality = p % 25 '
         '(both unhooked).'),
        (0x0803E47C, 0x0803E484, '7aca883e037eb2a7d4805fa404eecb4a71e5b1ef28ed01eab361648ca939e845',
         'CalculateMonStats is hooked to 0x089C4475; that function loads GetNature at 0x089C456E.'),
    ),
    'checksum': (
        (0x0803E3E8, 0x0803E47C, '15aa7ab0d75814ea88f0cf9028c50991677b1a9950491fa6b6ad704b3e96484b',
         'CalculateBoxMonChecksum is vanilla: 16-bit sum of the 24 halfwords of G/A/E/M '
         '(substructure lookup forces fixed order).'),
        (0x08040ADA, 0x08040AEE, '441365b471d5f98bf5e2e63424cf625ef182a569db5965634aa36c2277837727',
         'SetBoxMonData tail: checksum computed for fields > 10 but the store is a no-op '
         '(0x08040AE6); EncryptBoxMon follows (its XORs are no-ops).'),
        (0x0804051E, 0x08040532, '8d6e71a8b2e60adc1b7f5dbab02191a8cba24ab32f874600111e01b38d34d15b',
         'SetBoxMonData checksum validation replaced by no-ops and an unconditional branch.'),
        (0x08825A96, 0x08825AB6, '359a7f7519b5221128af62b1b19eecd09ba9d4a0e1ece8b8b2bc0fbfd779a7ec',
         'Inserted routine (entry 0x08825A30): stores CalculateBoxMonChecksum via '
         'SetBoxMonData(mon, 9 /*checksum*/), then EncryptBoxMon.'),
        (0x088CEE97, 0x088CEE9C, '55e9e115b06e83274f5abc0a83eb1fd5fc6275e59b5772889846b26da6df8844',
         'Script bytecode: callasm (0x23) 0x08825A31 (one of the sites listed by callasm_sites).'),
    ),
    'box': (
        (0x0808BA18, 0x0808BA20, '6181e76b0050d0d13ddb884a34d8644fc5716aa1c60e6944a4af90fc4fe2446c',
         'GetBoxMonDataAt is hooked: ldr r3,=0x08A04955; bx r3.'),
        (0x08A04954, 0x08A0499A, 'aaa09988ccb536e8923fae02ff9ac5b495d22ae41be07ab7b48d0cfb62ba729f',
         'Box accessor: box < 25, position < 30, record stride 0x3A (58), base from the 25-pointer '
         'table at 0x08A6CB2C; species = halfword at +0x1C.'),
        (0x08A0478C, 0x08A047D2, '856206caba341056089ef11a3bd34ffe750563616764dfda05b538b6cb2d58dd',
         'Compressed->BoxPokemon: zero 0x50 bytes, copy 0x1C header bytes (checksum +0x1C '
         'excluded), 11 G bytes to +0x20, 6 EVs to +0x38, 8 misc bytes (incl. IV word) to +0x44.'),
    ),
}


# Pool literals that the spans above load but do not contain: (expected value, meaning).
LITERALS = {
    0x08040D38: (0x089C51B9, 'GetMonAbility hook target'),
    0x089C51D6: (0x080001BC, 'GetMonAbility: address of the gBaseStats pointer'),
    0x08A13B48: (0x0806E6D1, 'TryRandomizeAbility: FlagGet'),
    0x08A13B4A: (0x00001522, 'TryRandomizeAbility: ability-randomizer flag id'),
    0x0803DAD2: (0x089A43C5, 'CreateBoxMon hook target'),
    0x089A43DA: (0x0803DD85, 'CreateBoxMon hook return (after the vanilla checksum store)'),
    0x0803E47C: (0x089C4475, 'CalculateMonStats hook target'),
    0x089C456E: (0x08042E9D, 'hooked CalculateMonStats loads GetNature'),
    0x08825A98: (0x0803E3E9, 'inserted routine loads CalculateBoxMonChecksum'),
    0x08825AA8: (0x080404D1, 'inserted routine loads SetBoxMonData (field 9 = checksum)'),
    0x08825AB0: (0x0803F8F9, 'inserted routine loads EncryptBoxMon'),
    0x0808BA18: (0x08A04955, 'GetBoxMonDataAt hook target'),
    0x08A0496A: (BOX_POINTER_TABLE, 'box accessor loads the box pointer table'),
}


def rom_offset(address):
    return address - ROM_BASE


def span_sha(rom, start, end):
    require(ROM_BASE <= start < end <= ROM_BASE + len(rom), 'span outside ROM')
    return hashlib.sha256(rom[rom_offset(start):rom_offset(end)]).hexdigest()


def halfword(rom, address):
    return struct.unpack_from('<H', rom, rom_offset(address))[0]


def ldr_literal(rom, address):
    """Value loaded by a Thumb `ldr rd, [pc, #imm]` at `address`."""
    h = halfword(rom, address)
    require(h >> 11 == 9, 'not a pc-relative literal load at ' + hex(address))
    return struct.unpack_from('<I', rom, rom_offset(((address + 4) & ~3) + (h & 255) * 4))[0]


def references(rom, target):
    """Thumb BL call sites of `target`, plus aligned words equal to `target | 1`."""
    calls, lits, want = [], [], struct.pack('<I', target | 1)
    for off in range(0, len(rom) - 3, 2):
        h1, h2 = struct.unpack_from('<HH', rom, off)
        if h1 >> 11 == 0b11110 and h2 >> 11 == 0b11111:
            delta = ((h1 & 0x7FF) << 12) | ((h2 & 0x7FF) << 1)
            if delta & 0x400000:
                delta -= 0x800000
            if ROM_BASE + off + 4 + delta == target:
                calls.append(ROM_BASE + off)
    at = rom.find(want)
    while at >= 0:
        if at % 4 == 0:
            lits.append(ROM_BASE + at)
        at = rom.find(want, at + 1)
    return calls, lits


def callasm_sites(rom, target):
    """Script `callasm` (0x23) commands whose 32-bit operand is `target | 1`, at any alignment."""
    sites, want = [], struct.pack('<I', target | 1)
    at = rom.find(want)
    while at >= 0:
        if at and rom[at - 1] == 0x23:
            sites.append(ROM_BASE + at - 1)
        at = rom.find(want, at + 1)
    return sites


def ability_triple(rom, species):
    base = struct.unpack_from('<I', rom, BASE_STATS_POINTER)[0]
    require(base == ROM_BASE + BASE_STATS, 'base-stats pointer mismatch')
    require(type(species) is int and 0 < species < SPECIES_COUNT, 'species out of table domain')
    row = BASE_STATS + species * BASE_STRIDE
    return tuple(rom[row + o] for o in ABILITY_OFFSETS)


def predict_ability(pid, iv_word, triple):
    """Exact-ROM GetMonAbility rule, without the save-dependent randomizer override."""
    ability1, ability2, hidden = triple
    if iv_word >> 31 and hidden:
        return dict(selector='hidden', ability=hidden)
    if pid & 1 and ability2:
        return dict(selector='ability2', ability=ability2)
    return dict(selector='ability1', ability=ability1)


def shiny_value(pid, otid):
    return (otid & 0xFFFF) ^ (otid >> 16) ^ (pid & 0xFFFF) ^ (pid >> 16)


def is_shiny(pid, otid, threshold=16):
    return shiny_value(pid, otid) < threshold


def nature(pid):
    return pid % 25


def raw_checksum(secure):
    require(len(secure) == 48, 'secure area must be 48 bytes')
    return sum(struct.unpack('<24H', secure)) & 0xFFFF


def inspect_semantics(rom, spans=SPANS, literals_map=LITERALS):
    out = {}
    for area, rows in spans.items():
        out[area] = []
        for start, end, sha, finding in rows:
            require(span_sha(rom, start, end) == sha, 'inspected ROM span mismatch at ' + hex(start))
            out[area].append(dict(start=hex(start), end_exclusive=hex(end), sha256=sha, finding=finding))
    cmp = halfword(rom, SHINY_CMP)
    require(cmp >> 8 == 0x2A and halfword(rom, SHINY_CMP + 2) >> 8 == 0xD8,
            'shiny compare is not cmp r2,#imm; bhi')
    calls, lits = references(rom, CHECKSUM_FN)
    writer_calls = references(rom, CHECKSUM_WRITER)[0]
    boxes = struct.unpack_from('<%dI' % BOX_COUNT, rom, rom_offset(BOX_POINTER_TABLE))
    literals = []
    for address, (expected, meaning) in sorted(literals_map.items()):
        require(ldr_literal(rom, address) == expected, 'literal mismatch at ' + hex(address))
        literals.append(dict(load_at=hex(address), value=hex(expected), meaning=meaning))
    return dict(
        spans=out, literal_loads=literals,
        shiny_compare_immediate=cmp & 0xFF, shiny_threshold_exclusive=(cmp & 0xFF) + 1,
        checksum_function_references=dict(bl_sites=[hex(a) for a in calls],
                                          aligned_literal_words=[hex(a) for a in lits],
                                          any_alignment_words=rom.count(struct.pack('<I', CHECKSUM_FN | 1))),
        checksum_writer_routine=dict(entry=hex(CHECKSUM_WRITER), bl_sites=[hex(a) for a in writer_calls],
                                     script_callasm_sites=[hex(a) for a in callasm_sites(rom, CHECKSUM_WRITER)],
                                     any_alignment_words=rom.count(struct.pack('<I', CHECKSUM_WRITER | 1))),
        box_storage=dict(boxes=BOX_COUNT, slots_per_box=30, record_bytes=0x3A,
                         box_base_addresses=[hex(b) for b in boxes]))


def battle_check(report, rom):
    """Each distinct observed battler: exact-ROM ability prediction vs gBattleMons byte."""
    seen = {}
    for obs in report['observations']:
        battle = obs['battle']
        enemies = {e['slot']: e for e in battle.get('enemy_box_nonzero_slots', [])}
        for b in battle['battlers']:
            fields = b['fields']
            if b['side'] == 'player':
                rec = obs['parties']['player']['slots'][b['party_index']]
            else:
                rec = enemies[b['party_index']]
            require(rec['pid'] == fields['personality'], 'battler/record personality mismatch')
            species = rec['fixed_raw_gaem']['species_word']
            triple = ability_triple(rom, species)
            pred = predict_ability(rec['pid'], rec['fixed_raw_gaem']['iv_word'], triple)
            row = dict(side=b['side'], party_index=b['party_index'], species_word=species,
                       pid_bit0=rec['pid'] & 1, iv_bit31=rec['fixed_raw_gaem']['iv_word'] >> 31,
                       base_ability_triple=list(triple), predicted_selector=pred['selector'],
                       predicted_ability=pred['ability'], battle_ability_byte=fields['ability_byte'],
                       vanilla_bit31_slot_ability=triple[rec['fixed_raw_gaem']['iv_word'] >> 31],
                       matches=pred['ability'] == fields['ability_byte'])
            key = (b['side'], rec['pid'])
            if key in seen:
                require(seen[key]['row'] == row, 'battler ability changed between observations')
                seen[key]['observations'] += 1
            else:
                seen[key] = dict(row=row, observations=1, first_label=obs['label'])
    return [dict(v['row'], observations=v['observations'], first_label=v['first_label'])
            for v in seen.values()]


def party_check(manifest, rom):
    """Committed party manifest slots: rules vs the manual summary-screen transcription."""
    ability_names = names(rom, *TABLES['ability_names'])
    rows = []
    for slot in manifest['party']:
        raw, ui = slot['raw_selected'], slot['ui_transcription']
        pid, otid, iv = int(raw['pid'], 16), int(raw['otid'], 16), int(raw['iv_word'], 16)
        species = int(raw['species_word'], 16)
        triple = ability_triple(rom, species)
        pred = predict_ability(pid, iv, triple)
        name = ability_names[pred['ability']] if pred['ability'] < len(ability_names) else {}
        rows.append(dict(
            slot=slot['slot'], species_word=species, pid_bit0=pid & 1, iv_bit31=iv >> 31,
            base_ability_triple=list(triple), predicted_selector=pred['selector'],
            predicted_ability=pred['ability'],
            predicted_default_name_equals_ui=name.get('name') == ui['ability'],
            discriminates_ability2_rule=bool(pid & 1 and triple[1] and not (iv >> 31 and triple[2])),
            shiny_value=shiny_value(pid, otid), predicted_shiny=is_shiny(pid, otid),
            vanilla_shiny_lt8=shiny_value(pid, otid) < 8, ui_shiny_star=ui['shiny_star'],
            shiny_matches_ui=is_shiny(pid, otid) == ui['shiny_star'],
            nature_pid_mod25=nature(pid), checksum_word=int(raw['checksum_word'], 16)))
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--rom', type=Path, required=True)
    parser.add_argument('--battle-report', type=Path, help='private unbound_observe.py report')
    parser.add_argument('--party-manifest', type=Path,
                        default=HERE / 'evidence/unbound-runtime-observation.json')
    args = parser.parse_args()
    try:
        rom = read_local(args.rom, 32 * 1024 * 1024)
        result = dict(schema_version=1, rom=pinned(rom, ROM_SHA256, 32 * 1024 * 1024, 'ROM'),
                      tool_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                      static=inspect_semantics(rom))
        manifest = read_local(args.party_manifest, 4 * 1024 * 1024)
        result['party_manifest_sha256'] = hashlib.sha256(manifest).hexdigest()
        result['party'] = party_check(json.loads(manifest), rom)
        if args.battle_report:
            report_bytes = read_local(args.battle_report, 64 * 1024 * 1024)
            report = json.loads(report_bytes)
            require(report.get('status') == 'OBSERVED', 'battle report did not complete')
            result['battle_report_sha256'] = hashlib.sha256(report_bytes).hexdigest()
            result['battle_observer_sha256'] = report['observer_sha256']
            result['battle_movie_sha256'] = report['movie_sha256']
            result['battle'] = battle_check(report, rom)
    except (ValueError, OSError, KeyError) as error:
        print('FAIL: ' + str(error), file=sys.stderr)
        return 1
    print(json.dumps(result, sort_keys=True, indent=2))
    return 0


if __name__ == '__main__':
    sys.exit(main())
