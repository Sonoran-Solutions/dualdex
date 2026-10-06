#!/usr/bin/env python3
"""ROM-free issue #145 census check; reports opportunity, never predicts admission."""
import collections
import gzip
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MOVE_GATE = 'HNS_MOVE_MECHANICS_NOT_MODELLED'
census = json.loads(gzip.decompress((ROOT / 'tools/hns-calc-census/census.json.gz').read_bytes()))
inventory = json.loads((ROOT / 'tools/hns-calc-census/trainer_inventory.json').read_text())
metadata = json.loads((ROOT / 'tools/hns-move-mechanics/hns_move_damage_metadata.json').read_text())['moves']
ids = {m['name']: m['id'] for m in inventory['moves']}
family = {n: metadata[str(i)] for n, i in ids.items()
          if metadata.get(str(i), {}).get('effect') == 'EFFECT_HIT'
          and set(metadata[str(i)].get('damageFlags', {})) & {'strikeCount', 'multiHit'}}
rows = [r for r in census['requests'] if r['move'] in family and MOVE_GATE in r['limitations']]
expected = {'Arm Thrust':292,'Bone Rush':198,'Bonemerang':155,'Bullet Seed':331,'Comet Punch':4,
            'Double Hit':458,'Double Kick':24,'Double Slap':3,'Dual Chop':530,'Dual Wingbeat':742,
            'Fury Attack':31,'Fury Swipes':154,'Icicle Spear':333,'Pin Missile':42,'Rock Blast':350,
            'Scale Shot':727,'Spike Cannon':131,'Tail Slap':541,'Twin Beam':814,'Twineedle':41}
assert {r['move']: ids[r['move']] for r in rows} == expected

def metrics(rs):
    return dict(requests=len(rs), battles=len({r['trainer'] for r in rs}),
                leadPairs=len({(r['trainer'], r['referenceTeam']) for r in rs if r['partySlot']==0}),
                leadRequests=sum(r['partySlot']==0 for r in rs),
                moveOnlyUpperBound=sum(r['limitations']==[MOVE_GATE] for r in rs),
                overlaps=dict(sorted(collections.Counter(x for r in rs for x in r['limitations'] if x!=MOVE_GATE).items())))

assert list(metrics(rows).values())[:5] == [392,138,160,166,336]
fixed = [r for r in rows if 'strikeCount' in family[r['move']]['damageFlags']]
variable = [r for r in rows if 'multiHit' in family[r['move']]['damageFlags']]
assert len(fixed)+len(variable)==len(rows)
assert all(family[r['move']]['damageFlags']['strikeCount']==['2'] for r in fixed)
plain_fixed = [r for r in fixed if r['move']!='Twineedle']
plain_variable = [r for r in variable if r['move']!='Scale Shot']
distinct = {effect: metrics([r for r in census['requests'] if MOVE_GATE in r['limitations']
            and metadata.get(str(ids[r['move']]), {}).get('effect') == effect])
            for effect in ('EFFECT_TRIPLE_KICK', 'EFFECT_BEAT_UP', 'EFFECT_POPULATION_BOMB')}
assert list(distinct['EFFECT_TRIPLE_KICK'].values())[:5] == [10,5,6,6,6]
assert list(distinct['EFFECT_BEAT_UP'].values())[:5] == [10,5,6,6,8]
assert distinct['EFFECT_POPULATION_BOMB']['requests'] == 0
parental_requests = sum(r['attackerAbility']=='Parental Bond' or r['defenderAbility']=='Parental Bond'
                        for r in census['requests'])
assert parental_requests == 0
print(json.dumps({'distinctFamilies':distinct,'parentalBondParticipantRequests':parental_requests,'all':metrics(rows),'fixed':metrics(fixed),'variable':metrics(variable),
    'sliceA_plainFixed':metrics(plain_fixed),'twineedle':metrics([r for r in fixed if r['move']=='Twineedle']),
    'sliceB_plainVariable':metrics(plain_variable),'scaleShot':metrics([r for r in variable if r['move']=='Scale Shot']),
    'moves':{n:dict(id=ids[n],kind='fixed2' if 'strikeCount' in family[n]['damageFlags'] else 'variable2to5',
                      **metrics([r for r in rows if r['move']==n])) for n in sorted(expected)}},indent=2))

# Optional immutable-source verification; local checkout content is never trusted.
if __name__ == '__main__':
    import argparse
    import re
    import subprocess
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, help='Pinned H&S git checkout for source/anchor checks')
    args = parser.parse_args()
    if args.source:
        pin = '1f42b74dff0e9fe942419845d040663dd829a973'
        def source(path):
            return subprocess.check_output(['git', '-C', str(args.source), 'show', f'{pin}:{path}'], text=True)
        moves = source('src/data/moves_info.h')
        constants = source('include/constants/moves.h')
        for name, move_id in expected.items():
            symbol = 'MOVE_' + name.upper().replace(' ', '_').replace('-', '_')
            assert re.search(rf'\b{symbol}\s*=\s*{move_id}\b', constants), symbol
            block = moves.split(f'[{symbol}] =', 1)[1].split('\n    [MOVE_', 1)[0]
            assert '.effect = EFFECT_HIT,' in block, symbol
            flag = '.strikeCount = 2,' if 'strikeCount' in family[name]['damageFlags'] else '.multiHit = TRUE,'
            assert flag in block, (symbol, flag)
        doc = (ROOT/'docs/HNS_REPEATED_STRIKE_RESULT_CONTRACT.md').read_text()
        links = re.findall(rf'blob/{pin}/([^)#]+)#L(\d+)\) — `([^`]+)`', doc)
        assert len(links) >= 40
        for path, line, token in links:
            assert token in source(path).splitlines()[int(line)-1], (path, line, token)
        assert 'RandomWeighted(RNG_HITS, 0, 0, 7, 7, 3, 3)' in source('src/battle_move_resolution.c')
        assert 'RandomUniform(RNG_LOADED_DICE, 4, 5)' in source('src/battle_move_resolution.c')
        assert 'RandomUniform(RNG_LOADED_DICE, 4, 10)' in source('src/battle_move_resolution.c')
        assert '#define GEN_LATEST GEN_9' in source('include/config/general.h')
        # Status application precedes another strike and can change its defensive operand.
        handlers = source('src/battle_move_resolution.c').split('sMoveEndHandlers[]', 1)[1]
        assert handlers.index('[MOVEEND_ABILITIES_ATTACKER]') < handlers.index('[MOVEEND_MULTIHIT_MOVE]')
        commands = source('src/battle_script_commands.c')
        assert 'RandomWeighted(RNG_TOXIC_CHAIN, 7, 3)' in commands
        utility = source('src/battle_util.c')
        attacker_reactions = utility.split('case ABILITYEFFECT_MOVE_END_ATTACKER:', 1)[1].split('case ABILITYEFFECT_FORM_CHANGE_ON_HIT:', 1)[0]
        assert 'RandomPercentage(RNG_POISON_TOUCH, 30)' in attacker_reactions
        assert 'gBattleScripting.moveEffect = MOVE_EFFECT_POISON;' in attacker_reactions
        assert 'gBattleScripting.moveEffect = MOVE_EFFECT_TOXIC;' in attacker_reactions
        marvel_scale = utility.split('case ABILITY_MARVEL_SCALE:', 1)[1].split('case ABILITY_FUR_COAT:', 1)[0]
        assert 'status1 & STATUS1_ANY && usesDefStat' in marvel_scale
        assert 'UQ_4_12(1.5)' in marvel_scale
        print(f'PASS: {len(expected)} pinned move records, {len(links)} exact source anchors, count rules/config, attacker-status phase/Defense dependency')
