#!/usr/bin/env python3
"""Read-only exact-candidate Thumb evidence. No runtime support verdict or text extraction.

Names are manual control-flow attributions, checked by numeric instruction probes,
call edges and inspected-span hashes. This is deliberately not a general symbol finder.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import sys

from rowe_observe import require_private_directory
from unbound_data import span
from unbound_identity import ROM_SIZE, exact_rom
from unbound_location import ROM_BASE, inspect_save_blocks, thumb_literal
from unbound_observe import literal_references
from ups_identity import read_local, require

HERE = Path(__file__).resolve().parent
CORPUS = HERE.parent / 'unbound-damage-oracle/corpus.json'

ROUTINES = {
    'AbilityCanChangeTypeAndBoost': (0x89cd468, 0x89cd4e4, 'b2323f48f3be8d098cabd5780f43facad1b494e819e7c8850fb178cfb0a56da9'),
    'atk04_critcalc': (0x89ce360, 0x89ce64c, 'f6a7028dd79117886f37e9002e23cf7c5630d1a659cb40ae4c94f3e6bbed9ec1'),
    'atk05_damagecalc': (0x89d3310, 0x89d3494, 'c021e39f8895a55290e5928a696245790bc1397c9f443456bb74b627204a0cd1'),
    'atk06_typecalc': (0x89ce8e4, 0x89cec48, 'ab8a4a66cf67d64438bde07e22091c3f62797ce85e93650874502e46e9ffd241'),
    'CalculateBaseDamage': (0x89d1940, 0x89d28c4, 'a2fa57d779b9a69a890aa69e7c5f2249a5d9567f853de2b5403a690142864887'),
    'GetBasePower': (0x89d06b4, 0x89d16bc, '2970f4c3a97771931dff33308751071a5ca48e4df907348123c1b58a6dc135f8'),
    'AdjustBasePower': (0x89cd4e4, 0x89cdab0, '24c23095e5c0cc951d10d9ea3d6e007b11c3befa99bd19116c4139e6b2ce4b08'),
    'ModulateDmgByType': (0x89cdc9e, 0x89cdf0c, 'f5b0f5372cae2a08009b758f9c5374fa6b3abe5bc8d78cb1fc20764d8e9ee194'),
    'ApplyRandomDmgMultiplier': (0x89cd33c, 0x89cd374, '295b3cd109393ae761be15e65986b1060744f8528f577ddd8c2ad60c3b729b9e'),
    'ScreensWeakenDamage': (0x89d18d4, 0x89d1940, '27c0fa034de974421ed6e2c87e38cf2d7d13ae2570f342b21f4d972daa80533b'),
    'PopulateDamageCalcStructWithBaseAttackerData': (0x89cefec, 0x89cf1bc, 'd537562c374c54345645248bd049b3e0514bb7fd348a678bbfd086ad03d26a31'),
    'PopulateDamageCalcStructWithBaseDefenderData': (0x89cf1bc, 0x89cf508, '80a32151d2f96deccec488fabfa49a0138e81e710a312daf38bce60b0258bb50'),
    'CalcMoveSplit': (0x89c0584, 0x89c063c, 'fa763e488b76c1f37e7d2f274923f3a129e6ae4f8844fc85b600267d77964b38'),
    'IsInverseBattle': (0x89c1f50, 0x89c1f88, '52b9721976cde0b9cbcf39c5c4c0aebec58b22a7c737c8491df6f1a9ed8eae05'),
    'IsScaleMonsBattle': (0x89e6eec, 0x89e6f28, 'a0d68778787c2db23b2b8839aa36bdfb962e6bf35b6d1029aa50e317e6129e3a'),
    'IsRaidBattle': (0x89da900, 0x89da92c, 'a09805ee240533936d0fbaab988dcb24e8a0b687c87cdd1b0a7c3c15de9a3a84'),
    'IsDynamaxed': (0x89d9edc, 0x89d9ef8, 'c2ecb6bdfd491cd01a85cb9323d796d08b37d2bb5755f062f99970e4b01d838a'),
    'IsTargetAbilityIgnored': (0x89a9520, 0x89a9554, 'eaf18eb8af309d5d35e49376979a3df603e7bd138efea9a68708f9e493f82477'),
    'atk07_adjustnormaldamage': (0x89cef58, 0x89cef66, '0019b69f74953888c39a27ccca9241ed41fada07fe6fa1583852aabd8e97eb97'),
    'AdjustDamage': (0x89cdfbc, 0x89ce360, 'd766821356b7d088e35440fd7b03874104219eab869f61c9f4eb8988b0192d1a'),
    'FlagGet': (0x806e6d0, 0x806e6fc, 'e76fe63ce7357e14ade8b78ccefc0493f95123f0f5daf4ecaa13ed0ea85edafb'),
    'FlagResolverHook': (0x89a2f6c, 0x89a2f88, '4ee5612cec72a9e85f33bc3c2f9539dda72d3f6b5eba02f7a61b27be00febab1'),
    'ExtendedFlagResolver': (0x8a0722c, 0x8a0727c, 'c48b525b7e735bd36b4590bc40436097cdebdc29bc9ee09663803caab371fca6'),
    'ExtendedVarResolver': (0x8a0727c, 0x8a072b8, '8c2f285d0f1116693b14b4d482f6d156ec2c2b6006f6deec64b84f670e146cd9'),
    'VarGet': (0x806e568, 0x806e584, '117970004516ef3e86cab382ec57912f8a331fe9a9c76924d7ff5ebac3dde4dd'),
    'VarResolverHook': (0x89a2f44, 0x89a2f6c, '227d65273b90380210f39c2dcc76cfd219a3ade210289627388a108758c2fd22'),
    'FlagResolverFallback': (0x806e5c0, 0x806e680, '4f3e86727395d60ca6d4a04d135319719efce7777782888e41ad09f96b29bf4a'),
    'VarResolverFallback': (0x806e454, 0x806e528, '555a69e333c4152f5ca7c8fb33727691adc7a3f497d6e55b40ed73bd66a80710'),
    'DivisionVeneers': (0x8964b0e, 0x8964b16, '5aee0a38836848ee9233df21cf02ba6972cfb52cc2fe859d59bc17acd44ed3b3'),
}

PROBES = {  # name: (pc, halfword count, SHA-256 of those instruction bytes)
    'crit_neutral': (0x89ce402, 3, '92d523b61e86773049a70694d763e02644ed25df33bf59a4acba1a8f16bf1bc7'),
    'crit_boost': (0x89ce5f8, 3, '46888a79a0b4e5e18dbcb2c44e5ef09b4bf308ec56f75fa29d91abb5651e63d7'),
    'crit_divisor': (0x89d344c, 6, 'a6ecefe27dbd2a0d887b8cd24ad854e0c4fa126a6f9374bcc0b5d996588c3582'),
    'consumed_gem': (0x89cd586, 14, '42f731c9a9c32b853d15fa9233b143553858aeb23cb4b9ad10c7839f52fbae12'),
    'held_gem': (0x89cd9a4, 13, 'eacd881ff0dc151cb335b27075230803410c856dcfdb9795094ce0bbf6bf0727'),
    'ate_dispatch': (0x89cd662, 28, 'a20147b7c83a5aa4125527663224745a9c358fc9c3d8eb407518a2c7a70dd646'),
    'ate_result': (0x89cd830, 4, 'fd1edcba8d986035e7eea9fe21101364be1ac25a94700da4d2009b2d1252e36b'),
    'ate_multiplier': (0x89cd620, 2, 'f2908c8a097521225f8c0e767d7274705d3c52121926c39090efaf85d44e653a'),
    'power_multiply': (0x89cd6fa, 5, '2d249ccb02c374382bbc8194c4aae589a5528a0b5a29761458e0ef0afe0752d4'),
    'power_divide': (0x89cd76c, 4, '4a276bf3a97714c035827078e4415554e06b263c2db2d3f2d0875b835ccbe15f'),
    'held_power_multiply': (0x89cd924, 4, 'ced8de3708944246e3cddf13faafdc427e3f4b7ac1a985820e0d0b58ce78db7b'),
    'held_power_divide': (0x89cd8f4, 5, '287ff55a0238c10eef42b86ec5423ec8d79861b1ec123e5834031b0afea8aab4'),
    'normal_power_path': (0x89cd538, 7, 'a20869d9bd622fb815f235547562f6eb265bc86524717fa8925872779cbd2b33'),
    'primal_weather': (0x89d1dbc, 10, 'd6dcdafa833de991431a68b1d10bbff5dc0a4e40b6aaa68ad3aefc22f9829ab5'),
    'weather_masks': (0x89d2410, 14, 'a6fca4c21726b6a7a5e48ae1358ab5fa2b7c35102087948cb06155a61f974825'),
    'rain_arithmetic': (0x89d2638, 8, 'de14ca86d356c77053a3c6996a495287d8322cb10593a38e857a85807a679226'),
    'sun_arithmetic': (0x89d264a, 16, '75cf43875c5e4e1ea30b9daf9ec9c4a611907d96c5684d9e3ed58885ad0058d2'),
    'stab_check': (0x89ce990, 18, '85e78bae60540d9ab9d7884925a4db680e5c1b104d2c184aa2fd541b983a11cb'),
    'stab': (0x89ceab6, 7, '5421bb7c08bcf90524a0b70a66b38391364c7271ee3d8c05be2a6f1386aaa374'),
    'burn': (0x89d2540, 22, '6e66cffacba028c0e589a92ba188767431c2f817dc8b6f3e95cc6ffde1332b69'),
    'nonphysical_path': (0x89d27d2, 7, 'b08d5c0ea2253fa8f8f0e2f14be91ffada58b625e5079f5ba3dff4922c6d6d30'),
    'parental_bond': (0x89d27de, 9, 'c6a5c05307c49a63a160023df9dc91cb17b6c9d8479d87ecbe922bbfb81c2922'),
    'screen_masks': (0x89d27ae, 18, 'ae5fb3692715b02d4f1c116f35743b1d01b3008f6a995f1935a95a9166bb0ff6'),
    'screen_arithmetic': (0x89d18fa, 13, '47777d79603d888a8d83f5d734d49a118d2d6a4ed23a41f620b46ca7d33a7bfa'),
    'screen_single': (0x89d192c, 2, '676725e69db88144b513eede337ef0f0dc39bd1fc683e7cfaa61f9d3eb168d47'),
    'screen_crit': (0x89d18e0, 4, '2850382b7488a1fd68dec2aedafde0e6ab77e3689de7abc741a696b04c738f50'),
    'random': (0x89cd33e, 23, '6d29e2194dd6d2f12b6b48ebd65efd67b1859be668679db3fcbf20a2b35bd732'),
    'portal_dispatch': (0x89d1fee, 15, '136eaa4dca1264317f0719507af2eda419c045a78857dec87b6b41528e037679'),
    'portal_arithmetic': (0x89d200c, 14, 'f5d39162b4c117a120d34746525596fb792f1b2b209c088a1b2bff4572c9e3ea'),
    'move_split': (0x89c0610, 7, '0ccda068f6df11281fb80952c52373abebf71c37a52c4bd52d31f491f40511f1'),
    'ability_bypass': (0x89a9524, 18, '50a23bb90691ab4419842834908f1593dd1e1e8fe973dab4cc53e64b5b1846e1'),
    'flash_fire': (0x89d1cc2, 9, 'bf1732929de91cc47925ea96a9b02c1565c001d18516d26909ff4a46e2e67d06'),
    'side_timers': (0x89cf0bc, 9, 'e2bae47a8137fc6939354ed1e8ac318ccb90247c61c85b76f34f56d2adfb0540'),
    'side_statuses': (0x89cf2ba, 7, 'cdda2d8ad116f8d21e3c6ba5998bcc7b58624a545d097bb6b35fd44b5e70e3db'),
    'statuses3': (0x89cdd28, 5, '9794057e09bc70d9e001f1672035ef72b2e55846b6b5eb38b8bedacc34dfc527'),
    'disable_stride': (0x89d08aa, 9, '913456822f03bdea2205d09891f9f2aeea832e0f497e7264dd912b0fe2896980'),
    'protect_stride': (0x89d23e0, 7, 'fa75b31ea207fd0c8cb5fc3b4131a859195528d5ad89529839ff32e36eea988f'),
    'terrain_read': (0x89cd5c4, 6, 'dcdfbb938b0cffb787e0de37f3c817ef942d07db2f136baeb49d07333e958acd'),
    'newbs_damage': (0x89d3382, 15, 'bd795595c1278182205fabe578478b31bcb1336c73cda531843e02b74dab7da3'),
    'newbs_crit': (0x89ce430, 10, 'd86eeb7ed15023b6a3f312a00722be3dad9d5fddfc5a1c1d705159da6d0eedb4'),
    'dynamax_counter': (0x89d9edc, 11, '8f608bb8c262d8de5fec37040fb95989063417616d8ab7b1ad61bd4dc7cb778a'),
    'inverse': (0x89c1f50, 19, 'd40524a624795502038c4541936b00ace769b807899c8f2664ce5811b31930bb'),
    'raid': (0x89da900, 15, '726de47cdf18bca3da8fe30d572c6ab7d2192f60c7e2b408a2c3ee52f41ed310'),
    'scalemons': (0x89e6eec, 22, '6e5d4c25bf5ae7e83616e193963ce2933d2388864eb8a1787e8d6ff8faa3cd27'),
    'flag_decode': (0x806e6d0, 20, '49d50efbb3670f05c0416641c20bdcb740c8167d2c41929500dd8620851d9e2b'),
    'flag_hook': (0x89a2f6c, 11, '5b5761f662aebe53a4193e1287835c6574d6d19ac7fb68f2fbf2bdae79a4e01f'),
    'flag_extended': (0x8a0722c, 13, '03bbe415a848419655421cff6ed62187a6079c97948cf6f2bbc0956786ab680e'),
    'flag_fallback': (0x806e644, 7, '271eb3a83c6a08e915436d77777343c76a2e452b71496e68e51f68ec13b29f07'),
    'var_hook': (0x89a2f44, 16, 'fbc878fae1fb9b63d7dcfdd4bcba45a8bdbfe442065eb8b983c0cf4841ec8e7e'),
    'var_extended': (0x8a0727c, 11, 'e1e19fb4d90f51716c432bdefe3596ae0872280177d5cbbff3684e48b9462f7f'),
    'fairy_type': (0x89d2438, 5, '45a4cb7241c9195ce26efe2c346bf1c48c604583084513392da9fc1a80d9fc3f'),
    'terrain_electric': (0x89cd9ce, 15, 'bbc37efaed71093e116d6539c7992bb8eb34cb8c5bc98ccac246b346709e408b'),
    'terrain_grassy': (0x89cda06, 16, '20723c07c16330b38cde048a4dcc225a04054b6adebd3e1f1733c650c4e80dd4'),
    'terrain_psychic': (0x89cda52, 9, '233376070d35fed2d45e80a315f719e125b7bd30bc4b160263d6a75dcacb671f'),
}

GLOBAL_USES = {
    'gBattleWeather': ('CalculateBaseDamage', 0x89d1dc4, 0x89d1dc6),
    'gTerrainType': ('AdjustBasePower', 0x89cd5c4, 0x89cd5c6),
    'gSideStatuses': ('PopulateDamageCalcStructWithBaseDefenderData', 0x89cf2ba, 0x89cf2be, 0x2, 0x2),
    'gSideTimers': ('PopulateDamageCalcStructWithBaseAttackerData', 0x89cf0c2, 0x89cf0c6, 0xc, 0x2),
    'gStatuses3': ('ModulateDmgByType', 0x89cdd28, 0x89cdd2c, 0x4, 0x4),
    'gDisableStructs': ('GetBasePower', 0x89d08b0, 0x89d08b6, 0x1c, 0x4),
    'gProtectStructs': ('CalculateBaseDamage', 0x89d23e2, 0x89d23e6, 0x10, 0x4),
    'gBattleStruct': ('atk05_damagecalc', 0x89d332e, 0x89d3330),
    'gBattleResources': ('CalculateBaseDamage', 0x89d1cc2, 0x89d1cc4),
    'gNewBS': ('atk05_damagecalc', 0x89d3340, 0x89d3342),
    'gBattleCircusFlags': ('IsInverseBattle', 0x89c1f6a, 0x89c1f6c),
    'gCritMultiplier': ('atk04_critcalc', 0x89ce404, 0x89ce406),
}

CALLS = (
    (0x89d3406, 0x89d1940),
    (0x89d3446, 0x89d1940),
    (0x89d1c74, 0x89d06b4),
    (0x89d1c7c, 0x89cd4e4),
    (0x89cef5a, 0x89cd33c),
    (0x89cef60, 0x89cdfbc),
    (0x89cdcb8, 0x89c1f50),
    (0x89d1ebe, 0x89e6eec),
    (0x89d208e, 0x89e6eec),
    (0x89ce162, 0x89da900),
    (0x89d25d6, 0x89d9edc),
    (0x89d27be, 0x89d18d4),
    (0x89d1982, 0x89a9520),
    (0x89d1b58, 0x89c0584),
    (0x89d27d4, 0x89d27de),
    (0x89d256a, 0x89d27de),
    (0x89cd834, 0x89cd620),
    (0x89cd7a8, 0x89cd6fc),
    (0x89cd9bc, 0x89cd924),
    (0x89cd92a, 0x89cd8f4),
    (0x806e6d8, 0x806e5c0),
    (0x89a2f76, 0x8a0722c),
    (0x806e570, 0x806e454),
    (0x89a2f4e, 0x8a0727c),
    (0x89cd622, 0x89cd6fc),
    (0x89cd692, 0x89cd468),
    (0x89d27dc, 0x89d2568),
    (0x89d27da, 0x89d27de),
)

def halfword(rom, pc):
    require(type(pc) is int and pc % 2 == 0, 'unaligned Thumb instruction')
    return struct.unpack('<H', span(rom, pc - ROM_BASE, 2))[0]


def literal(rom, pc):
    # Bound both reads before using the existing aligned-PC decoder.
    h = halfword(rom, pc)
    require(h >> 11 == 9, 'expected Thumb literal load')
    span(rom, (((pc + 4) & ~3) + (h & 255) * 4) - ROM_BASE, 4)
    return thumb_literal(rom, pc)


def branch_target(rom, pc):
    """ARMv4T Thumb BL, B, or conditional B; reject other encodings."""
    h = halfword(rom, pc)
    if h >> 11 == 0b11110:
        low = halfword(rom, pc + 2)
        require(low >> 11 == 0b11111, 'expected Thumb BL suffix')
        displacement = ((h & 2047) << 12) | ((low & 2047) << 1)
        if displacement & (1 << 22):
            displacement -= 1 << 23
    elif h >> 11 == 0b11100:
        displacement = (h & 2047) << 1
        if displacement & 2048:
            displacement -= 4096
    elif h >> 12 == 0b1101 and (h >> 8) & 15 < 14:
        displacement = (h & 255) << 1
        if displacement & 256:
            displacement -= 512
    else:
        raise ValueError('expected Thumb branch')
    return pc + 4 + displacement


def memory_access(rom, pc):
    """Decode immediate/register-offset Thumb memory widths, never execute code."""
    h = halfword(rom, pc)
    kind = {12: ('str', 32), 13: ('ldr', 32), 14: ('strb', 8),
            15: ('ldrb', 8), 16: ('strh', 16), 17: ('ldrh', 16)}.get(h >> 11)
    if kind:
        scale = kind[1] // 8
        return dict(operation=kind[0], width_bits=kind[1], base_register=(h >> 3) & 7,
                    register=h & 7, offset_bytes=((h >> 6) & 31) * scale)
    if h >> 12 == 5:
        op, width = (('str', 32), ('strh', 16), ('strb', 8), ('ldrsb', 8),
                     ('ldr', 32), ('ldrh', 16), ('ldrb', 8), ('ldrsh', 16))[(h >> 9) & 7]
        return dict(operation=op, width_bits=width, base_register=(h >> 3) & 7,
                    register=h & 7, offset_register=(h >> 6) & 7)
    raise ValueError('expected Thumb memory access')


def numeric_probe(rom, pc, count, sha256):
    """Pin `count` Thumb halfwords at `pc` by digest only; no instruction bytes are emitted."""
    actual = hashlib.sha256(span(rom, pc-ROM_BASE, 2*count)).hexdigest()
    require(actual == sha256, 'inspected instruction mismatch')
    return dict(pc=hex(pc), halfword_count=count, sha256=actual)


def inspected_spans(rom):
    result = {}
    for name, (start, end, digest) in ROUTINES.items():
        actual = hashlib.sha256(span(rom, start - ROM_BASE, end - start)).hexdigest()
        require(actual == digest, 'inspected span mismatch: ' + name)
        result[name] = dict(status='VERIFIED', entry=hex(start),
                            inspected_end_exclusive=hex(end), sha256=actual)
    return result


def global_reference(rom, routine, load_pc, access_pc, stride=None, count=None):
    rd, pool, address = literal(rom, load_pc)
    require(0x02000000 <= address < 0x02040000 or 0x03000000 <= address < 0x03008000,
            'global literal outside GBA RAM')
    access = memory_access(rom, access_pc)
    result = dict(status='VERIFIED', address=hex(address), routine=routine,
                  routine_address=hex(ROUTINES[routine][0]), load_pc=hex(load_pc),
                  literal_pool=hex(pool), literal_register=rd, access_pc=hex(access_pc),
                  access=access)
    if stride is not None:
        result['stride_bytes'] = stride
    if count is not None:
        # Count is a caller/topology bound, not an allocation-size proof.
        result['index_bound'] = count
        result['allocation_extent_status'] = 'UNVERIFIED'
    return result


def flag_storage(flag_id):
    """Selected resolver domains proved by probes; exclude virtual/special IDs."""
    require(type(flag_id) is int and 0 < flag_id <= 0x18ff, 'flag outside inspected domains')
    if flag_id >= 0x900:
        return dict(address=hex(0x0203b174 + ((flag_id - 0x900) >> 3)),
                    mask=1 << (flag_id & 7), width_bits=8)
    return dict(pointer_address=hex(0x03005008), offset_bytes=0xee0 + (flag_id >> 3),
                mask=1 << (flag_id & 7), width_bits=8)


def inspect(rom):
    identity = exact_rom(rom)  # Before offsets, source hypotheses, or oracle reads.
    routines = inspected_spans(rom)
    probes = {name: numeric_probe(rom, *pin) for name, pin in PROBES.items()}
    table_loads = [0x08014c1c, 0x08015a28, 0x08015c6c, 0x08015c98, 0x0801d054]
    tables = [struct.unpack('<I', span(rom, pc-ROM_BASE, 4))[0] for pc in table_loads]
    require(len(set(tables)) == 1, 'script dispatch tables disagree')
    dispatch = {}
    for command, name in ((4, 'atk04_critcalc'), (5, 'atk05_damagecalc'),
                          (6, 'atk06_typecalc'), (7, 'atk07_adjustnormaldamage')):
        target = struct.unpack('<I', span(rom, tables[0]-ROM_BASE+4*command, 4))[0]
        require(target & 1 and target - 1 == ROUTINES[name][0], 'dispatch target mismatch')
        dispatch[name] = dict(command_id=command, table_slot=hex(tables[0]+4*command),
                              thumb_pointer=hex(target))
    edges = []
    for pc, target in CALLS:
        require(branch_target(rom, pc) == target, 'inspected control-flow edge mismatch')
        edges.append(dict(pc=hex(pc), target=hex(target)))
    globals_ = {name: global_reference(rom, *spec) for name, spec in GLOBAL_USES.items()}
    globals_['gBattleResources']['flags'] = dict(status='VERIFIED', pointer_offset_bytes=4,
        element_width_bits=32, stride_bytes=4, mask=1, probe='flash_fire')
    globals_['gNewBS']['selected_offsets'] = dict(critical_multiplier=0x1a0,
        damage_taken=0x180, dynamax_counter=0x1ea, consumed_gem_byte=0x106,
        consumed_gem_mask=2)
    # Whole-ROM counts are corroboration only; they do not assign names to code.
    counts = literal_references(rom, {name: int(row['address'], 16)
                                     for name, row in globals_.items()})
    for name, row in globals_.items():
        row['literal_search'] = counts[name]

    switches = {}
    for name, defined, witnesses in (
            ('OLD_CRIT_DAMAGE', False, ['crit_neutral', 'crit_boost', 'crit_divisor']),
            ('OLD_TERRAIN_BOOST', False, ['terrain_read', 'terrain_electric', 'terrain_grassy', 'terrain_psychic']),
            ('OLD_ATE_BOOST', True, ['ate_dispatch', 'ate_result', 'ate_multiplier']),
            ('OLD_GEM_BOOST', True, ['consumed_gem', 'held_gem']),
            ('PORTAL_POWER', True, ['portal_dispatch', 'portal_arithmetic']),
            ('FROSTBITE', True, ['burn', 'nonphysical_path', 'parental_bond']),
            ('OLD_MOVE_SPLIT', False, ['move_split'])):
        switches[name] = dict(status='VERIFIED', defined=defined, probes=witnesses)
    switches['BADGE_BOOSTS'] = dict(status='UNVERIFIED', defined=None)
    # NO_MOLD_BREAKERS is a function-like macro, not an #ifdef in the supplied source.
    switches['NO_MOLD_BREAKERS'] = dict(status='UNVERIFIED', defined=None,
        helper_status='VERIFIED', helper=hex(0x089a9520), probes=['ability_bypass'])
    constants = dict(critical_multiplier=dict(numerator=15, denominator=10),
        neutral_critical_multiplier=10, burn_status=16, burn=dict(numerator=1, denominator=2),
        frostbite_status=32, frostbite=dict(numerator=1, denominator=2),
        stab=dict(numerator=15, denominator=10), adaptability=dict(numerator=2, denominator=1),
        reflect=1, light_screen=2, singles_screen=dict(numerator=1, denominator=2),
        doubles_screen=dict(numerator=2, denominator=3),
        rain_mask=0x1007, sun_mask=0x860, sand_mask=0x4018,
        weather_boost=dict(numerator=15, denominator=10),
        weather_cut=dict(numerator=1, denominator=2),
        terrain=dict(numerator=13, denominator=10), ate=dict(numerator=13, denominator=10),
        gem=dict(numerator=15, denominator=10), portal=dict(numerator=75, denominator=100),
        random=dict(numerator_min=85, numerator_max=100, denominator=100, minimum_nonzero=1),
        fairy_type=23)

    rules = {}
    for name, ids, entry, callers in (
            ('inverse', [0x23d], 0x089c1f50, [0x089cdcb8]),
            ('raid', [0x2fe], 0x089da900, [0x089ce162]),
            ('scalemons', [0x9f0, 0x16e2], 0x089e6eec, [0x089d1ebe, 0x089d208e])):
        rules[name] = dict(status='VERIFIED', routine=hex(entry), caller_pcs=list(map(hex, callers)),
            flags=[dict(id=i, **flag_storage(i)) for i in ids])
    rules['scalemons']['vars'] = [dict(id=0x50c4, address=hex(0x02031374+2*0x50c4),
                                                  width_bits=16, comparison=11)]
    rules['inverse']['circus_mask'] = 1 << 17
    rules['inverse']['circus_battle_type_mask'] = 1 << 26
    rules['raid']['excluded_battle_type_mask'] = 8
    rules['dynamax'] = dict(status='VERIFIED', routine=hex(0x089d9edc),
        caller_pcs=[hex(0x089d25d6)], pointer_address=globals_['gNewBS']['address'],
        battler_byte_offset=0x1ea, width_bits=8, signed=True, saveblock_enable_flag_status='UNVERIFIED')
    rules['storage'] = dict(status='VERIFIED', flag_get=hex(0x0806e6d0),
        flag_hook=hex(0x089a2f6c), extended_flag_resolver=hex(0x08a0722c),
        var_get=hex(0x0806e568), var_hook=hex(0x089a2f44),
        extended_var_resolver=hex(0x08a0727c), save_blocks=inspect_save_blocks(rom))

    corpus_bytes = read_local(CORPUS, 4*1024*1024)
    oracle = json.loads(corpus_bytes)['provenance']['constants']
    comparison = {}
    scalar = dict(critical_multiplier=15, burn_status=16, fairy_type=23, reflect=1, light_screen=2)
    for name, value in oracle.items():
        if name in scalar:
            measured, mode = scalar[name], 'EQUAL'
            agrees = value == measured
        elif name in ('rain_weather', 'sun_weather'):
            measured = constants['rain_mask' if name == 'rain_weather' else 'sun_mask']
            mode = 'NONZERO_SUBSET_OF_COMPILED_MASK'
            agrees = type(value) is int and value > 0 and value & measured == value
        else:
            comparison[name] = dict(status='UNVERIFIED', oracle=value)
            continue
        comparison[name] = dict(status='VERIFIED', oracle=value, compiled=measured,
                                comparison=mode, agrees=agrees)
    return dict(schema_version=1, status='VERIFIED', scope='STATIC_SELECTED_PATHS_ONLY',
        runtime_status='UNVERIFIED', source_status='SOURCE_APPROXIMATE',
        tool_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        cfru_commit='b637a27898b14e25dd24d0f69a3e302f0069deb8', rom=identity,
        routines=routines, script_dispatch=dict(table=hex(tables[0]),
            pointer_locations=list(map(hex, table_loads)), commands=dispatch),
        control_flow_edges=edges, globals=globals_, probes=probes, build_switches=switches,
        constants=dict(status='VERIFIED', values=constants, probes=dict(
            critical=['crit_neutral', 'crit_boost', 'crit_divisor'],
            burn=['burn'], frostbite=['nonphysical_path', 'burn'],
            stab=['stab_check', 'stab'], screens=['screen_masks', 'screen_arithmetic', 'screen_single'],
            weather=['weather_masks', 'rain_arithmetic', 'sun_arithmetic', 'primal_weather'],
            random=['random'], terrain=['terrain_electric', 'terrain_grassy', 'terrain_psychic'],
            ate=['ate_dispatch', 'ate_result', 'ate_multiplier'], gem=['consumed_gem', 'held_gem'],
            portal=['portal_dispatch', 'portal_arithmetic'], fairy=['fairy_type'])), rule_reads=rules,
        oracle=dict(corpus_sha256=hashlib.sha256(corpus_bytes).hexdigest(), constants=comparison))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--rom', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    protected = [args.rom, CORPUS, *HERE.glob('*.py')]
    if any(args.out.resolve() == p.resolve() or
           (args.out.exists() and p.exists() and args.out.samefile(p)) for p in protected):
        parser.error('output must not overwrite an input or tool')
    if args.out.suffix != '.json':
        parser.error('output must be a .json file')
    try:
        # Invalidate stale success; errors never emit host paths into evidence.
        args.out.write_text('{"status": "FAIL"}\n')
        rom = read_local(args.rom, ROM_SIZE)
        exact_rom(rom)
        require_private_directory(args.rom.parent)
        result = inspect(rom)
        args.out.write_text(json.dumps(result, indent=2, sort_keys=True) + '\n')
    except (OSError, ValueError, struct.error) as error:
        print(type(error).__name__ + ': battle-global inspection failed', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
