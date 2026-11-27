#!/usr/bin/env python3
"""Classify a local GBA ROM into an onboarding family and name the closest gamedata base config.

Host-only, standard library, read-only. Prints identity and header facts plus recommended next
steps; it is a triage aid, never a support verdict and never a trusted-hash registry. Classification
is header/size heuristic evidence: only an exact-hash comparison against a pinned release can prove
a ROM is unmodified retail.

    python3 tools/rom-evidence/rom_identify.py /private/hack.gba            # human-readable
    python3 tools/rom-evidence/rom_identify.py /private/hack.gba --json     # structured

Exit status: 0 classified (including `unknown`), 2 unreadable, oversized or header-less (< 0xC0 bytes) input.
"""
import argparse
import json
from pathlib import Path
import sys

from ups_identity import MAX_ROM, identity, read_local, require

HERE = Path(__file__).resolve().parent
CONFIG_DIR = HERE.parents[1] / 'gamedata' / 'gen3'

HEADER_END = 0xC0
MIB = 1024 * 1024
RETAIL_SIZE = 16 * MIB
CFRU_SIZE = 32 * MIB
# Header markers searched for in the whole image (bounded by MAX_ROM); heuristics, see FAMILY_NOTES.
EXPANSION_MARKERS = (b'RHHEXP', b'RHH_EXP')
EXPANSION_HEADER_WINDOW = (0x100, 0x200)  # gRomHeader lives at 0x08000100

# game code -> (retail title prefix, closest gamedata/gen3 config id)
RETAIL = {
    'BPRE': ('POKEMON FIRE', 'firered_rev0'),
    'BPGE': ('POKEMON LEAF', 'leafgreen'),
    'BPEE': ('POKEMON EMER', 'emerald'),
    'AXVE': ('POKEMON RUBY', 'ruby'),
    'AXPE': ('POKEMON SAPP', 'sapphire'),
}
FIRERED_CODES = ('BPRE', 'BPGE')
FAMILIES = ('vanilla-retail', 'pokeemerald-expansion', 'cfru-inplace',
            'firered-inplace', 'emerald-inplace', 'unknown')

FAMILY_NOTES = {
    'vanilla-retail': 'Known retail game code, title and 16 MiB size. Header-only evidence: a '
                      'same-size in-place hack keeps these fields, so pin the exact SHA-256.',
    'pokeemerald-expansion': 'RHH expansion marker found; pokeemerald-expansion builds have '
                             'relocated, build-specific symbols and layouts.',
    'cfru-inplace': '32 MiB image with a FireRed game code: the Complete FireRed Upgrade '
                    'family (e.g. Radical Red, Unbound) expands FireRed in place.',
    'firered-inplace': 'FireRed/LeafGreen game code with a non-retail size or title: an '
                       'in-place FireRed hack.',
    'emerald-inplace': 'Emerald game code with a non-retail size or title: an in-place '
                       'Emerald hack (not an expansion build).',
    'unknown': 'No known Gen III game code; no base can be recommended from the header alone.',
}


def printable(raw):
    return ''.join(chr(b) if 0x20 <= b < 0x7F else '?' for b in raw.rstrip(b'\0'))


def header_fields(rom):
    require(len(rom) >= HEADER_END, 'ROM is smaller than a GBA header')
    complement = (-(sum(rom[0xA0:0xBD]) + 0x19)) & 0xFF
    return dict(title=printable(rom[0xA0:0xAC]), game_code=printable(rom[0xAC:0xB0]),
                maker=printable(rom[0xB0:0xB2]), revision=rom[0xBC],
                fixed_byte_ok=rom[0xB2] == 0x96, complement_ok=rom[0xBD] == complement)


def find_expansion_marker(rom):
    for marker in EXPANSION_MARKERS:
        offset = rom.find(marker)
        if offset >= 0:
            low, high = EXPANSION_HEADER_WINDOW
            return dict(marker=marker.decode('ascii'), file_offset=offset,
                        in_rom_header_window=low <= offset < high)
    return None


def classify(rom, header):
    """Return (family, base_config_id | None, evidence list)."""
    code, size = header['game_code'], len(rom)
    evidence = [f'game code {code or "(empty)"}, {size} bytes']
    marker = find_expansion_marker(rom)
    if marker:
        evidence.append(f"{marker['marker']} marker at file offset 0x{marker['file_offset']:x}"
                        + (' (inside the 0x100 ROM header window)' if marker['in_rom_header_window'] else ''))
        return 'pokeemerald-expansion', 'emerald', evidence
    if code not in RETAIL:
        return 'unknown', None, evidence
    title_prefix, base = RETAIL[code]
    retail = size == RETAIL_SIZE and header['title'].startswith(title_prefix)
    if retail:
        evidence.append(f"title '{header['title']}' and 16 MiB size match retail")
        return 'vanilla-retail', base, evidence
    evidence.append(f"title '{header['title']}' / size do not match retail {code}")
    if code in FIRERED_CODES:
        if code == 'BPRE' and size == CFRU_SIZE:
            evidence.append('32 MiB BPRE image (CFRU in-place layout)')
            return 'cfru-inplace', 'firered_rev0', evidence
        return 'firered-inplace', base, evidence
    if code == 'BPEE':
        return 'emerald-inplace', base, evidence
    return 'unknown', base, evidence  # Ruby/Sapphire hacks: no in-place family is defined


def load_configs(config_dir=CONFIG_DIR):
    configs = {}
    for path in sorted(Path(config_dir).glob('*.json')):
        data = json.loads(path.read_text())
        configs[data['id']] = dict(id=data['id'], file=f'gamedata/gen3/{path.name}',
                                   gameId=data.get('gameId'), gameName=data.get('gameName'),
                                   base=data.get('base'))
    return configs


def next_steps(family, base, base_exists):
    if family == 'unknown' and base is None:
        return ['Identify the base game from its documentation; this tool only knows Gen III '
                'FireRed/LeafGreen/Emerald/Ruby/Sapphire codes.']
    steps = []
    if family == 'vanilla-retail':
        steps.append('Compare the SHA-256 with a pinned release before relying on an existing config.')
    if base and base_exists:
        steps.append(f'Create gamedata/gen3/<hack>.json with "base": "{base}" and list only the '
                     'fields the hack changes.')
    elif base:
        steps.append(f'Base config {base} is not present in gamedata/gen3/; add it first.')
    steps += ['Run 2-3 varied-boot captures with tools/rom-evidence/rom_observe.py to separate '
              'stable from drifting RAM addresses.',
              'python3 tools/gamedata/generate_gamedata.py --write, then add a ROM-free fixture and '
              'run ./ci.sh fixture-test.',
              'Keep the profile ESTIMATED (Tier B) until exact-ROM runtime evidence exists.']
    return steps


def inspect(rom, config_dir=CONFIG_DIR):
    require(len(rom) <= MAX_ROM, 'local file exceeds size limit')
    header = header_fields(rom)
    ident = identity(rom)
    family, base, evidence = classify(rom, header)
    configs = load_configs(config_dir)
    base_exists = base in configs
    return dict(schema_version=1, rom=dict(bytes=ident['bytes'], sha256=ident['sha256'],
                                           crc32=ident['crc32']),
                header=header, family=family, family_note=FAMILY_NOTES[family], evidence=evidence,
                base_config=configs[base] if base_exists else None,
                base_config_id=base,
                sibling_configs=sorted(c for c, v in configs.items() if v['base'] == base),
                next_steps=next_steps(family, base, base_exists),
                verdict='NOT_A_SUPPORT_VERDICT')


def render(report):
    h, r = report['header'], report['rom']
    lines = [f"SHA-256  {r['sha256']}", f"CRC32    {r['crc32']}   size {r['bytes']} bytes",
             f"title    {h['title']!r}   code {h['game_code']!r}   maker {h['maker']!r}   "
             f"revision {h['revision']}",
             f"header   fixed byte {'ok' if h['fixed_byte_ok'] else 'BAD'}, "
             f"complement {'ok' if h['complement_ok'] else 'BAD'}",
             f"family   {report['family']}  - {report['family_note']}"]
    lines += [f'  evidence: {e}' for e in report['evidence']]
    base = report['base_config']
    lines.append('base     ' + (f"{base['id']} ({base['file']})" if base else
                                f"{report['base_config_id']} (missing)" if report['base_config_id'] else 'none'))
    if report['sibling_configs']:
        lines.append('inherit  ' + ', '.join(report['sibling_configs']))
    lines.append('next steps:')
    lines += [f'  {n}. {s}' for n, s in enumerate(report['next_steps'], 1)]
    return '\n'.join(lines)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('rom', type=Path)
    parser.add_argument('--json', action='store_true', help='emit structured JSON')
    args = parser.parse_args(argv)
    try:
        report = inspect(read_local(args.rom, MAX_ROM))
    except (ValueError, OSError) as error:
        print(f'FAIL: {error}', file=sys.stderr)
        return 2
    print(json.dumps(report, indent=2, sort_keys=True) if args.json else render(report))
    return 0


if __name__ == '__main__':
    sys.exit(main())
