import { calculate, Generations, Pokemon, Move, Field, Side } from '@smogon/calc';

// @smogon/calc compares field.gameType against the canonical capitalised
// strings ('Singles'/'Doubles'); its own Field default is 'Singles'. DualDex
// callers historically sent lowercase values ('singles'), which silently
// selected the doubles damage path: in Gen III a spread move such as Rock
// Slide then dealt half damage in a single battle (issue #29). Accept either
// spelling here and reject anything else instead of coercing it.
//
// A Map, not an object literal: object lookup also resolves inherited
// Object.prototype members, so `GAME_TYPES['__proto__']`, `['constructor']`,
// `['toString']`, ... returned objects/functions that passed a truthiness
// check and were handed to `new Field(...)` - silently selecting the doubles
// path instead of being rejected as unsupported.
const GAME_TYPES = new Map([
  ['singles', 'Singles'],
  ['Singles', 'Singles'],
  ['doubles', 'Doubles'],
  ['Doubles', 'Doubles']
]);

// Omitted / null / empty gameType means "not specified" and selects the
// singles default, matching the library. Any other value must name a
// supported battle format exactly; unknown values (including other casings
// such as 'SINGLES', inherited property names such as '__proto__', and
// non-strings) are an error, never a silent singles or doubles coercion.
function normalizeGameType(raw) {
  if (raw === undefined || raw === null) return 'Singles';
  if (typeof raw !== 'string') {
    throw new Error('field.gameType must be a string ("Singles" or "Doubles"), got ' + typeof raw);
  }
  if (raw.trim() === '') return 'Singles';
  const canonical = GAME_TYPES.get(raw);
  if (canonical === undefined) {
    throw new Error('Unsupported field.gameType ' + JSON.stringify(raw) + '; expected "Singles" or "Doubles"');
  }
  return canonical;
}

const VALID_TYPES = new Set([
  'Normal', 'Fighting', 'Flying', 'Poison', 'Ground', 'Rock', 'Bug', 'Ghost', 'Steel',
  'Fire', 'Water', 'Grass', 'Electric', 'Psychic', 'Ice', 'Dragon', 'Dark', 'Fairy',
  'Stellar', '???'
]);
const VALID_CATEGORIES = new Set(['Physical', 'Special', 'Status']);
const STAT_NAMES = ['hp', 'atk', 'def', 'spa', 'spd', 'spe'];

function validateSpeciesOverrides(raw, label) {
  if (raw === undefined || raw === null) return undefined;
  if (typeof raw !== 'object' || Array.isArray(raw)) {
    throw new Error(label + ' overrides must be an object');
  }

  for (const key of Object.keys(raw)) {
    if (key !== 'baseStats' && key !== 'types') {
      throw new Error('Unknown field ' + JSON.stringify(key) + ' in ' + label + ' override');
    }
  }

  const result = {};

  if (raw.baseStats !== undefined) {
    if (typeof raw.baseStats !== 'object' || raw.baseStats === null || Array.isArray(raw.baseStats)) {
      throw new Error(label + ' overrides.baseStats must be an object');
    }
    for (const key of Object.keys(raw.baseStats)) {
      if (!STAT_NAMES.includes(key)) {
        throw new Error('Unknown stat ' + JSON.stringify(key) + ' in ' + label + ' baseStats');
      }
    }
    const baseStats = {};
    for (const stat of STAT_NAMES) {
      const val = raw.baseStats[stat];
      if (typeof val !== 'number' || !Number.isInteger(val) || val <= 0) {
        throw new Error('Invalid base stat ' + stat + ' in ' + label + ': expected positive integer, got ' + JSON.stringify(val));
      }
      baseStats[stat] = val;
    }
    result.baseStats = baseStats;
  }

  if (raw.types !== undefined) {
    if (!Array.isArray(raw.types) || raw.types.length === 0 || raw.types.length > 2) {
      throw new Error(label + ' overrides.types must be an array of 1 or 2 type names');
    }
    for (const t of raw.types) {
      if (typeof t !== 'string' || !VALID_TYPES.has(t)) {
        throw new Error('Invalid type ' + JSON.stringify(t) + ' in ' + label + ' overrides.types');
      }
    }
    result.types = raw.types.slice();
  }

  return result;
}

function validateMoveOverrides(raw) {
  if (raw === undefined || raw === null) return undefined;
  if (typeof raw !== 'object' || Array.isArray(raw)) {
    throw new Error('move overrides must be an object');
  }

  for (const key of Object.keys(raw)) {
    if (key !== 'basePower' && key !== 'type' && key !== 'category') {
      throw new Error('Unknown field ' + JSON.stringify(key) + ' in move override');
    }
  }

  const result = {};

  if (raw.basePower !== undefined) {
    const bp = raw.basePower;
    if (typeof bp !== 'number' || !Number.isInteger(bp) || bp < 0) {
      throw new Error('Invalid move basePower: expected non-negative integer, got ' + JSON.stringify(bp));
    }
    result.basePower = bp;
  }

  if (raw.type !== undefined) {
    if (typeof raw.type !== 'string' || !VALID_TYPES.has(raw.type)) {
      throw new Error('Invalid move type: expected valid TypeName, got ' + JSON.stringify(raw.type));
    }
    result.type = raw.type;
  }

  if (raw.category !== undefined) {
    if (typeof raw.category !== 'string' || !VALID_CATEGORIES.has(raw.category)) {
      throw new Error('Unsupported move category ' + JSON.stringify(raw.category) + '; expected "Physical", "Special", or "Status"');
    }
    result.category = raw.category;
  }

  return result;
}

import HNS_TYPE_CHART from './hns_type_chart.json';

function toID(text) {
  return ('' + text).toLowerCase().replace(/[^a-z0-9]/g, '');
}

class HnsType {
  constructor(name, effectiveness) {
    this.kind = 'Type';
    this.id = toID(name);
    this.name = name;
    this.effectiveness = effectiveness;
  }
}

const HNS_TYPES_BY_ID = {};
for (const typeName of Object.keys(HNS_TYPE_CHART)) {
  const t = new HnsType(typeName, HNS_TYPE_CHART[typeName]);
  HNS_TYPES_BY_ID[t.id] = t;
}

const HNS_TYPES_PROVIDER = {
  get(id) {
    return HNS_TYPES_BY_ID[id];
  },
  *[Symbol.iterator]() {
    for (const id in HNS_TYPES_BY_ID) {
      yield HNS_TYPES_BY_ID[id];
    }
  }
};

function createHnsGeneration(baseGen) {
  return {
    num: 3,
    abilities: baseGen.abilities,
    items: baseGen.items,
    moves: baseGen.moves,
    species: baseGen.species,
    natures: baseGen.natures,
    types: HNS_TYPES_PROVIDER,
  };
}

// Global API attached to globalThis for QuickJS / headless engine
globalThis.DualDexCalc = {
  calculateDamage: function(inputJsonStr) {
    try {
      const input = typeof inputJsonStr === 'string' ? JSON.parse(inputJsonStr) : inputJsonStr;
      const genNum = input.gen || 3;
      const baseGen = Generations.get(genNum);

      let gen = baseGen;
      if (input.typeSystem !== undefined && input.typeSystem !== null) {
        if (input.typeSystem === 'hns_2_0_5') {
          gen = createHnsGeneration(baseGen);
        } else {
          throw new Error('Unsupported typeSystem: ' + JSON.stringify(input.typeSystem));
        }
      }

      function resolveAbility(rawAbility, isHns) {
        if (isHns) {
          if (!rawAbility || rawAbility.trim() === '' || rawAbility.trim().toLowerCase() === 'none' || rawAbility.trim().toLowerCase() === '(other)') {
            return '(other)';
          }
          return rawAbility;
        }
        return rawAbility || undefined;
      }

      const isHns = input.typeSystem === 'hns_2_0_5';

      const attackerOptions = {
        level: input.attacker.level || 50
      };
      if (input.attacker.item) attackerOptions.item = input.attacker.item;
      if (input.attacker.nature) attackerOptions.nature = input.attacker.nature;
      const resolvedAttackerAbility = resolveAbility(input.attacker.ability, isHns);
      if (resolvedAttackerAbility) attackerOptions.ability = resolvedAttackerAbility;
      if (input.attacker.ivs) attackerOptions.ivs = input.attacker.ivs;
      if (input.attacker.evs) attackerOptions.evs = input.attacker.evs;
      if (input.attacker.boosts) attackerOptions.boosts = input.attacker.boosts;
      if (input.attacker.status) attackerOptions.status = input.attacker.status;
      if (input.attacker.curHP !== undefined) attackerOptions.curHP = input.attacker.curHP;

      const rawAttackerOverrides = input.attacker?.overrides || input.attackerOverride;
      const attackerOverrides = validateSpeciesOverrides(rawAttackerOverrides, 'attacker');
      if (attackerOverrides) {
        attackerOptions.overrides = attackerOverrides;
      }

      const attacker = new Pokemon(gen, input.attacker.species, attackerOptions);

      const defenderOptions = {
        level: input.defender.level || 50
      };
      if (input.defender.item) defenderOptions.item = input.defender.item;
      if (input.defender.nature) defenderOptions.nature = input.defender.nature;
      const resolvedDefenderAbility = resolveAbility(input.defender.ability, isHns);
      if (resolvedDefenderAbility) defenderOptions.ability = resolvedDefenderAbility;
      if (input.defender.ivs) defenderOptions.ivs = input.defender.ivs;
      if (input.defender.evs) defenderOptions.evs = input.defender.evs;
      if (input.defender.boosts) defenderOptions.boosts = input.defender.boosts;
      if (input.defender.status) defenderOptions.status = input.defender.status;
      if (input.defender.curHP !== undefined) defenderOptions.curHP = input.defender.curHP;

      const rawDefenderOverrides = input.defender?.overrides || input.defenderOverride;
      const defenderOverrides = validateSpeciesOverrides(rawDefenderOverrides, 'defender');
      if (defenderOverrides) {
        defenderOptions.overrides = defenderOverrides;
      }

      const defender = new Pokemon(gen, input.defender.species, defenderOptions);

      const moveOptions = {};
      if (input.move.isCrit) moveOptions.isCrit = input.move.isCrit;

      const rawMoveOverrides = input.move?.overrides || input.moveOverride;
      const moveOverrides = validateMoveOverrides(rawMoveOverrides);
      const baseMove = gen.moves.get(toID(input.move.name));
      const isBaseStatus = baseMove && baseMove.category === 'Status';
      if (input.typeSystem === 'hns_2_0_5' && moveOverrides && moveOverrides.category === undefined && moveOverrides.type === 'Fairy' && !isBaseStatus) {
        moveOverrides.category = 'Special';
      }
      if (moveOverrides) {
        moveOptions.overrides = moveOverrides;
      }

      const move = new Move(gen, input.move.name, moveOptions);

      const fieldOptions = {
        gameType: normalizeGameType(input.field?.gameType)
      };
      if (input.field?.weather) fieldOptions.weather = input.field.weather;
      if (input.field?.terrain) fieldOptions.terrain = input.field.terrain;
      if (input.field?.attackerSide) fieldOptions.attackerSide = new Side(input.field.attackerSide);
      if (input.field?.defenderSide) fieldOptions.defenderSide = new Side(input.field.defenderSide);

function halfDown(mod, val) {
  return Math.floor((mod * val + 2047) / 4096);
}

const HNS_STAT_STAGE_RATIOS = [
  [10, 40], // -6
  [10, 35], // -5
  [10, 30], // -4
  [10, 25], // -3
  [10, 20], // -2
  [10, 15], // -1
  [10, 10], //  0
  [15, 10], // +1
  [20, 10], // +2
  [25, 10], // +3
  [30, 10], // +4
  [35, 10], // +5
  [40, 10]  // +6
];

function calculateHnsDamage(gen, attacker, defender, move, field, input) {
  let typeEffectiveness = 1.0;
  const moveTypeRecord = gen.types.get(toID(move.type));
  if (moveTypeRecord && defender.types) {
    for (const defType of defender.types) {
      if (defType && moveTypeRecord.effectiveness[defType] !== undefined) {
        typeEffectiveness *= moveTypeRecord.effectiveness[defType];
      }
    }
  }

  const maxHP = defender.maxHP();

  if (move.category === 'Status' || move.bp === 0 || typeEffectiveness === 0) {
    const zeroRolls = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0];
    const descStr = attacker.name + ' ' + move.name + ' vs. ' + defender.name + ': 0-0 (0 - 0%)';
    return {
      success: true,
      damage: zeroRolls,
      minDamage: 0,
      maxDamage: 0,
      range: [0, 0],
      desc: descStr,
      moveName: move.name,
      moveCategory: move.category,
      moveType: move.type,
      movePower: move.bp,
      attackerName: attacker.name,
      attackerTypes: attacker.types,
      defenderName: defender.name,
      defenderTypes: defender.types,
      defenderMaxHP: maxHP,
      koChanceText: "",
      effectiveness: typeEffectiveness
    };
  }

  const isPhysical = (move.category === 'Physical');

  let rawAtk;
  if (isPhysical) {
    rawAtk = (input.attacker?.rawStats?.attack !== undefined)
      ? input.attacker.rawStats.attack
      : (attacker.rawStats ? attacker.rawStats.atk : attacker.stats.atk);
  } else {
    rawAtk = (input.attacker?.rawStats?.spAttack !== undefined)
      ? input.attacker.rawStats.spAttack
      : (attacker.rawStats ? attacker.rawStats.spa : attacker.stats.spa);
  }

  let rawDef;
  if (isPhysical) {
    rawDef = (input.defender?.rawStats?.defense !== undefined)
      ? input.defender.rawStats.defense
      : (defender.rawStats ? defender.rawStats.def : defender.stats.def);
  } else {
    rawDef = (input.defender?.rawStats?.spDefense !== undefined)
      ? input.defender.rawStats.spDefense
      : (defender.rawStats ? defender.rawStats.spd : defender.stats.spd);
  }

  let atkStage = 0;
  if (Array.isArray(input.attacker?.statStages) && input.attacker.statStages.length >= 6) {
    atkStage = isPhysical ? input.attacker.statStages[1] : input.attacker.statStages[4];
  } else if (input.attacker?.boosts) {
    atkStage = isPhysical ? (input.attacker.boosts.atk || 0) : (input.attacker.boosts.spa || 0);
  } else if (attacker.boosts) {
    atkStage = isPhysical ? (attacker.boosts.atk || 0) : (attacker.boosts.spa || 0);
  }

  let defStage = 0;
  if (Array.isArray(input.defender?.statStages) && input.defender.statStages.length >= 6) {
    defStage = isPhysical ? input.defender.statStages[2] : input.defender.statStages[5];
  } else if (input.defender?.boosts) {
    defStage = isPhysical ? (input.defender.boosts.def || 0) : (input.defender.boosts.spd || 0);
  } else if (defender.boosts) {
    defStage = isPhysical ? (defender.boosts.def || 0) : (defender.boosts.spd || 0);
  }

  if (move.isCrit) {
    if (atkStage < 0) atkStage = 0;
    if (defStage > 0) defStage = 0;
  }
  atkStage = Math.max(-6, Math.min(6, atkStage));
  defStage = Math.max(-6, Math.min(6, defStage));

  const atkRatio = HNS_STAT_STAGE_RATIOS[atkStage + 6];
  let userFinalAttack = Math.floor((rawAtk * atkRatio[0]) / atkRatio[1]);

  const defRatio = HNS_STAT_STAGE_RATIOS[defStage + 6];
  let targetFinalDefense = Math.floor((rawDef * defRatio[0]) / defRatio[1]);

  if (defender.ability === 'Thick Fat' && (move.type === 'Fire' || move.type === 'Ice')) {
    userFinalAttack = halfDown(2048, userFinalAttack);
  }
  const attackerStatus = (attacker.status || input.attacker?.status || '').toLowerCase();
  if (attacker.ability === 'Guts' && attackerStatus) {
    userFinalAttack = halfDown(6144, userFinalAttack);
  }
  if (isPhysical && (attacker.ability === 'Huge Power' || attacker.ability === 'Pure Power')) {
    userFinalAttack = userFinalAttack * 2;
  }

  const atkBadge = isPhysical ? !!input.attacker?.badgeBoosts?.atk : !!input.attacker?.badgeBoosts?.spa;
  if (atkBadge) {
    userFinalAttack = halfDown(4506, userFinalAttack);
  }
  const defBadge = isPhysical ? !!input.defender?.badgeBoosts?.def : !!input.defender?.badgeBoosts?.spd;
  if (defBadge) {
    targetFinalDefense = halfDown(4506, targetFinalDefense);
  }

  userFinalAttack = Math.max(1, userFinalAttack);
  targetFinalDefense = Math.max(1, targetFinalDefense);

  let bp = move.bp;
  const item = (input.attacker?.item || attacker.item || '').toLowerCase();
  if (item === 'charcoal' && move.type === 'Fire') bp = halfDown(4915, bp);
  else if (item === 'mystic water' && move.type === 'Water') bp = halfDown(4915, bp);
  else if (item === 'miracle seed' && move.type === 'Grass') bp = halfDown(4915, bp);
  else if (item === 'magnet' && move.type === 'Electric') bp = halfDown(4915, bp);
  else if (item === 'silk scarf' && move.type === 'Normal') bp = halfDown(4915, bp);
  else if (item === 'black belt' && move.type === 'Fighting') bp = halfDown(4915, bp);
  else if (item === 'sharp beak' && move.type === 'Flying') bp = halfDown(4915, bp);
  else if (item === 'poison barb' && move.type === 'Poison') bp = halfDown(4915, bp);
  else if (item === 'soft sand' && move.type === 'Ground') bp = halfDown(4915, bp);
  else if (item === 'hard stone' && move.type === 'Rock') bp = halfDown(4915, bp);
  else if (item === 'silver powder' && move.type === 'Bug') bp = halfDown(4915, bp);
  else if (item === 'spell tag' && move.type === 'Ghost') bp = halfDown(4915, bp);
  else if (item === 'metal coat' && move.type === 'Steel') bp = halfDown(4915, bp);
  else if (item === 'twisted spoon' && move.type === 'Psychic') bp = halfDown(4915, bp);
  else if (item === 'never-melt ice' && move.type === 'Ice') bp = halfDown(4915, bp);
  else if (item === 'dragon fang' && move.type === 'Dragon') bp = halfDown(4915, bp);
  else if (item === 'black glasses' && move.type === 'Dark') bp = halfDown(4915, bp);

  const level = attacker.level || 50;
  let dmg = Math.floor(Math.floor(Math.floor(bp * userFinalAttack * (Math.floor((2 * level) / 5) + 2)) / targetFinalDefense) / 50) + 2;

  const gameType = normalizeGameType(field.gameType || input.field?.gameType);
  // H&S applies the Gen-III spread halving only when GetMoveTargetCount(ctx) == 2,
  // i.e. the move actually targets all adjacent foes (Rock Slide, Earthquake, etc.).
  // A single-target move (Tackle, Flamethrower) in a doubles battle must NOT be halved.
  // The @smogon/calc Move object exposes move.target; 'allAdjacentFoes' is the Gen-III
  // multi-target value, matching the upstream mechanic exactly (gen3.js:333).
  if (gameType === 'Doubles' && move.target === 'allAdjacentFoes') {
    dmg = halfDown(2048, dmg);
  }

  const weatherStr = (field.weather || input.field?.weather || '').toLowerCase();
  if (weatherStr.includes('rain')) {
    if (move.type === 'Fire') dmg = halfDown(2048, dmg);
    else if (move.type === 'Water') dmg = halfDown(6144, dmg);
  } else if (weatherStr.includes('sun')) {
    if (move.type === 'Water') dmg = halfDown(2048, dmg);
    else if (move.type === 'Fire') dmg = halfDown(6144, dmg);
  }

  if (move.isCrit) {
    dmg = halfDown(8192, dmg);
  }

  const hasStab = attacker.types && attacker.types.some(t => t.toLowerCase() === move.type.toLowerCase());
  const stabMod = (attacker.ability === 'Adaptability') ? 8192 : 6144;

  const isBurned = isPhysical && (attackerStatus === 'brn') && (attacker.ability !== 'Guts');

  let hasScreen = false;
  if (!move.isCrit) {
    const defSide = field.defenderSide || input.field?.defenderSide;
    if (isPhysical && defSide?.isReflect) hasScreen = true;
    if (!isPhysical && defSide?.isLightScreen) hasScreen = true;
  }
  const screenMod = (gameType === 'Doubles') ? 2732 : 2048;

  const damageArray = [];
  for (let r = 85; r <= 100; r++) {
    let x = Math.floor((dmg * r) / 100);
    if (hasStab) x = halfDown(stabMod, x);
    if (typeEffectiveness !== 1.0) {
      x = halfDown(Math.round(typeEffectiveness * 4096), x);
    }
    if (isBurned) x = halfDown(2048, x);
    if (hasScreen) x = halfDown(screenMod, x);
    if (x === 0) x = 1;
    damageArray.push(x);
  }

  const minDmg = damageArray[0];
  const maxDmg = damageArray[15];
  const range = [minDmg, maxDmg];
  const minPct = ((minDmg / maxHP) * 100).toFixed(1);
  const maxPct = ((maxDmg / maxHP) * 100).toFixed(1);
  const descStr = attacker.name + ' ' + move.name + ' vs. ' + defender.name + ': ' + minDmg + '-' + maxDmg + ' (' + minPct + ' - ' + maxPct + '%)';

  return {
    success: true,
    damage: damageArray,
    minDamage: minDmg,
    maxDamage: maxDmg,
    range: range,
    desc: descStr,
    moveName: move.name,
    moveCategory: move.category,
    moveType: move.type,
    movePower: move.bp,
    attackerName: attacker.name,
    attackerTypes: attacker.types,
    defenderName: defender.name,
    defenderTypes: defender.types,
    defenderMaxHP: maxHP,
    koChanceText: "",
    effectiveness: typeEffectiveness
  };
}

      const field = new Field(fieldOptions);

      if (input.typeSystem === 'hns_2_0_5') {
        return JSON.stringify(calculateHnsDamage(gen, attacker, defender, move, field, input));
      }

      const result = calculate(gen, attacker, defender, move, field);

      const damageArray = Array.isArray(result.damage) ? result.damage : [result.damage];
      const minDmg = damageArray[0] || 0;
      const maxDmg = damageArray[damageArray.length - 1] || 0;
      const isImmune = maxDmg === 0;
      const range = result.range ? result.range() : [minDmg, maxDmg];
      const ko = (!isImmune && result.kochance) ? result.kochance(false) : null;
      let descStr = '';
      if (isImmune) {
        descStr = attacker.name + ' ' + move.name + ' vs. ' + defender.name + ': 0-0 (0 - 0%)';
      } else {
        try {
          descStr = result.fullDesc ? result.fullDesc('%', false) : (result.desc ? result.desc() : '');
        } catch (e) {
          descStr = '';
        }
      }

      let typeEffectiveness = 1;
      const moveTypeRecord = gen.types.get(toID(move.type));
      if (moveTypeRecord && defender.types) {
        for (const defType of defender.types) {
          if (defType && moveTypeRecord.effectiveness[defType] !== undefined) {
            typeEffectiveness *= moveTypeRecord.effectiveness[defType];
          }
        }
      }

      return JSON.stringify({
        success: true,
        damage: damageArray,
        minDamage: minDmg,
        maxDamage: maxDmg,
        range: range,
        desc: descStr,
        moveName: move.name,
        moveCategory: move.category,
        moveType: move.type,
        movePower: move.bp,
        attackerName: attacker.name,
        attackerTypes: attacker.types,
        defenderName: defender.name,
        defenderTypes: defender.types,
        defenderMaxHP: defender.maxHP(),
        koChanceText: ko ? ko.text : "",
        effectiveness: typeEffectiveness
      });
    } catch (e) {
      return JSON.stringify({
        success: false,
        error: e.message || String(e)
      });
    }
  }
};
