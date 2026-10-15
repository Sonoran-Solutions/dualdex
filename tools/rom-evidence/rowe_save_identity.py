"""Read-only Phase 6E evidence checks, not a general save parser or support gate."""
import struct

from rowe_data import (rom_identity, lookup_party_identity, _require_symbols,
                       read_span, BASE_STATS_STRIDE)
from rowe_observe import digest, slot_fields
from ups_identity import require


def inspect_cartridge(rom, cartridge, party, count):
    """Locate a unique complete observed party in a checksum-valid section 1.

    This does not choose a save generation or establish the loader's executed path.
    The caller must bind the cartridge and party to independently verified evidence.
    """
    rom_identity(rom)
    require(len(cartridge) == 131072, 'unsupported cartridge size')
    require(len(party) == 456 and type(count) is int and 1 <= count <= 6,
            'complete occupied party required')
    matches = []
    for base in range(0, 0x1c000, 0x1000):
        section, checksum, signature, counter = struct.unpack_from('<HHII', cartridge, base + 0xff4)
        if section != 1 or signature != 0x08012025:
            continue
        total = sum(struct.unpack_from('<1021I', cartridge, base)) & 0xffffffff
        valid = ((total & 65535) + (total >> 16)) & 65535 == checksum
        if (valid and cartridge[base + 0x234] == count and
                cartridge[base + 0x238:base + 0x238 + 456] == party):
            matches.append(dict(sector_offset=base, section=section, counter=counter,
                                checksum=checksum, party_offset=base + 0x238))
    require(len(matches) == 1, 'missing or ambiguous checksum-valid serialized party')
    return dict(status='SERIALIZED_REPRESENTATION_VERIFIED', **matches[0],
                cartridge_sha256=digest(cartridge), party_sha256=digest(party),
                occupied=[dict(slot=i, record_sha256=digest(party[i*76:(i+1)*76]),
                               species=slot_fields(party[i*76:(i+1)*76])['species'],
                               form=slot_fields(party[i*76:(i+1)*76])['form'])
                          for i in range(count)],
                loader_selection_status='NOT_VERIFIED')


def compare_records(rom, before, after):
    """Compare complete records without promoting static identity into UI proof."""
    rom_identity(rom)
    a, b = slot_fields(before), slot_fields(after)
    identities = [lookup_party_identity(rom, s['species'], s['form']) for s in (a, b)]
    require(all(s['static_status'] == 'STATIC_VERIFIED' for s in identities),
            'unsupported static identity')
    types = [list(read_span(rom, _require_symbols(rom)['gBaseStats'] +
                             row['metadata_lookup_key'] * BASE_STATS_STRIDE + 12, 2))
             for row in identities]
    require(all(t in set(range(19)) - {9} for pair in types for t in pair),
            'unsupported static type metadata')
    stable = all(a[k] == b[k] for k in a if k != 'species')
    same_identity = all(identities[0][k] == identities[1][k] for k in
                        ('metadata_lookup_key', 'static_resolved_species_id', 'static_name_candidate'))
    return dict(status='STATIC_VERIFIED', raw_identities=identities,
                record_sha256=[digest(before), digest(after)],
                changed_offsets=[i for i in range(76) if before[i] != after[i]],
                stable_decoded_fields_except_species=stable,
                same_static_identity=same_identity and types[0] == types[1],
                raw_type_ids=types,
                runtime_confirmation='NOT_VERIFIED')
