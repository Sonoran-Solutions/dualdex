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
    if (key !== 'basePower' && key !== 'type' && key !== 'category' && key !== 'ateBoost') {
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

  if (raw.ateBoost !== undefined) {
    if (typeof raw.ateBoost !== 'boolean') {
      throw new Error('Invalid move ateBoost: expected boolean, got ' + JSON.stringify(raw.ateBoost));
    }
    result.ateBoost = raw.ateBoost;
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

function halfUp(mod, val) {
  return Math.floor((mod * val + 2048) / 4096);
}

const HNS_UQ4_12_ONE = 4096;
const HNS_STATUS1_ANY_MASK = 0x10ff; // pinned include/constants/battle.h: STATUS1_ANY
const HNS_STATUS1_BURN_MASK = 0x10; // pinned include/constants/battle.h: STATUS1_BURN
const HNS_TYPE_POWER_ITEMS = {
  'charcoal': 'Fire', 'mystic water': 'Water', 'miracle seed': 'Grass', 'magnet': 'Electric',
  'silk scarf': 'Normal', 'black belt': 'Fighting', 'sharp beak': 'Flying', 'poison barb': 'Poison',
  'soft sand': 'Ground', 'hard stone': 'Rock', 'silver powder': 'Bug', 'spell tag': 'Ghost',
  'metal coat': 'Steel', 'twisted spoon': 'Psychic', 'never-melt ice': 'Ice', 'dragon fang': 'Dragon',
  'black glasses': 'Dark'
};

// The stat stages accumulate UQ4.12 with half-down multiplication. The BP stage uses the
// pinned uq4_12_multiply (half-up) for modifier composition, then half-down for integer BP.
// Keep both behaviors explicit; the stage product is converted to its integer operand once.
function createHnsModifierAccumulator(multiply = halfDown) {
  let modifier = HNS_UQ4_12_ONE;
  return {
    add(next) {
      modifier = multiply(next, modifier);
    },
    value() {
      return modifier;
    },
    apply(integer) {
      return halfDown(modifier, integer);
    }
  };
}

function applyHnsFinalDamageModifiers(damage, modifiers) {
  // These are intentionally applied in sequence: the pinned damage path rounds after each
  // DAMAGE_APPLY_MODIFIER call. Only GetOtherModifiers' own internal product is accumulated.
  for (const modifier of modifiers) damage = halfDown(modifier, damage);
  return damage;
}

function calculateHnsBaseDamage(power, attack, defense, level) {
  return Math.floor(Math.floor(Math.floor(power * attack * (Math.floor((2 * level) / 5) + 2)) / defense) / 50) + 2;
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

const HNS_MOLD_BREAKER_FAMILY = new Set(['Mold Breaker', 'Teravolt', 'Turboblaze']);
// Explicit modeled subset whose pinned gAbilitiesInfo entries have `.breakable = TRUE`.
// Prism Armor and Shadow Shield are intentionally absent: both remain active through
// Mold Breaker and literal move-level ignoresTargetAbility bypasses.
const HNS_BREAKABLE_DEFENDER_ABILITIES = new Set([
  'Levitate', 'Wonder Guard',
  'Volt Absorb', 'Motor Drive', 'Lightning Rod', 'Water Absorb', 'Storm Drain', 'Dry Skin',
  'Sap Sipper', 'Earth Eater', 'Well-Baked Body', 'Flash Fire',
  'Soundproof', 'Bulletproof', 'Wind Rider',
  'Queenly Majesty', 'Dazzling', 'Armor Tail',
  'Heatproof', 'Water Bubble',
  'Filter', 'Solid Rock', 'Multiscale', 'Ice Scales', 'Punk Rock', 'Fur Coat'
]);

function calculateHnsDamage(gen, attacker, defender, move, field, input) {
  // The H&S request adapter supplies the already-authorized effective type here. Keep the
  // stage operand named explicitly so every type-sensitive modifier reads the same value.
  const effectiveMoveType = move.type;
  let typeEffectiveness = 1.0;
  const moveTypeRecord = gen.types.get(toID(effectiveMoveType));
  const defenderItem = String(input.defender?.item || defender.item || '').toLowerCase();
  const ringTarget = defenderItem === 'ring target';
  const defenderHasAbilityShield = input.defender?.hnsAbilityShield === true ||
    defenderItem === 'ability shield';
  if (moveTypeRecord && defender.types) {
    for (const defType of defender.types) {
      if (defType && moveTypeRecord.effectiveness[defType] !== undefined) {
        const cell = moveTypeRecord.effectiveness[defType];
        typeEffectiveness *= ringTarget && cell === 0 ? 1.0 : cell;
      }
    }
  }

  // Group C immunity evaluation is intentionally separate from the ADV damage arithmetic.
  // Keep the cause with its source so a type-chart zero can be distinguished from an ability,
  // held item, move flag, priority block, or Wonder Guard result.
  const immunityCauses = [];
  const addImmunity = (kind, source, name) => {
    immunityCauses.push({ kind, source, name });
    typeEffectiveness = 0;
  };
  const defenderAbility = defender.ability || '';
  const moveFlags = new Set(input.move?.hnsMoveFlags || []);
  const ignoresTargetAbility = moveFlags.has('ignoresTargetAbility');
  // Resolve target ability suppression once for every source-backed target branch. The pinned
  // GetBattlerAbilityInternal() checks Ability Shield before either bypass. Mold Breaker-family
  // and literal move-level bypasses both set moldBreakerActive; suppression then additionally
  // requires the defender's pinned breakable flag.
  const defenderAbilitySuppressed = !defenderHasAbilityShield &&
    HNS_BREAKABLE_DEFENDER_ABILITIES.has(defenderAbility) &&
    (ignoresTargetAbility || HNS_MOLD_BREAKER_FAMILY.has(attacker.ability));
  const bypassTargetAbility = defenderAbilitySuppressed;
  const hnsDamagingMove = move.category !== 'Status' && move.bp > 0;
  if (hnsDamagingMove && typeEffectiveness === 0) {
    addImmunity('type', 'src/data/types_info.h', 'type-chart');
  }

  if (hnsDamagingMove && effectiveMoveType === 'Ground') {
    const ironBall = defenderItem === 'iron ball';
    if (ironBall && defender.types?.some(t => String(t).toLowerCase() === 'flying')) {
      // Pinned Iron Ball override sets the accumulated effectiveness to neutral when it is the
      // only grounding source (the source explicitly ignores Iron Ball for this check).
      typeEffectiveness = 1.0;
      immunityCauses.splice(0, immunityCauses.length,
        ...immunityCauses.filter(cause => cause.kind !== 'type'));
    } else if (defenderAbility === 'Levitate' && !ironBall && !bypassTargetAbility) {
      addImmunity('ability', 'src/battle_util.c:8385', 'Levitate');
    } else if (defenderItem === 'air balloon') {
      addImmunity('item', 'src/battle_util.c:8392', 'Air Balloon');
    }
  }

  if (hnsDamagingMove && !bypassTargetAbility && defenderAbility === 'Wonder Guard' && typeEffectiveness <= 1.0) {
    addImmunity('ability', 'src/battle_util.c:8421', 'Wonder Guard');
  }

  if (hnsDamagingMove) {
    const typeAbsorbers = {
      'Volt Absorb': ['Electric', 'src/battle_util.c:2444'],
      'Motor Drive': ['Electric', 'src/battle_util.c:2457'],
      'Lightning Rod': ['Electric', 'src/battle_util.c:2461'],
      'Water Absorb': ['Water', 'src/battle_util.c:2448'],
      'Storm Drain': ['Water', 'src/battle_util.c:2465'],
      'Dry Skin': ['Water', 'src/battle_util.c:2448'],
      'Sap Sipper': ['Grass', 'src/battle_util.c:2469'],
      'Earth Eater': ['Ground', 'src/battle_util.c:2453'],
      'Well-Baked Body': ['Fire', 'src/battle_util.c:2473'],
      'Flash Fire': ['Fire', 'src/battle_util.c:2481']
    };
    const absorber = typeAbsorbers[defenderAbility];
    if (!bypassTargetAbility && absorber && absorber[0] === effectiveMoveType) {
      addImmunity('ability', absorber[1], defenderAbility);
    }
    const flagImmunity = defenderAbility === 'Soundproof' && moveFlags.has('soundMove')
      ? 'src/battle_util.c:2485'
      : defenderAbility === 'Bulletproof' && moveFlags.has('ballisticMove')
        ? 'src/battle_util.c:2489'
        : defenderAbility === 'Wind Rider' && moveFlags.has('windMove')
          ? 'src/battle_util.c:2477'
          : null;
    if (!bypassTargetAbility && flagImmunity) {
      addImmunity('ability', flagImmunity, defenderAbility);
    }
    const priority = input.move?.effectivePriority;
    const targetClass = input.move?.hnsTargetClass;
    if (!bypassTargetAbility && Number.isInteger(priority) && priority > 0 &&
        targetClass !== 12 && targetClass !== 13 &&
        ['Queenly Majesty', 'Dazzling', 'Armor Tail'].includes(defenderAbility)) {
      addImmunity('ability', 'src/battle_move_resolution.c:1406', defenderAbility);
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
      moveType: effectiveMoveType,
      movePower: move.bp,
      attackerName: attacker.name,
      attackerTypes: attacker.types,
      defenderName: defender.name,
      defenderTypes: defender.types,
      defenderMaxHP: maxHP,
      koChanceText: "",
      effectiveness: typeEffectiveness,
      immunityCause: immunityCauses[0] || null,
      immunityCauses,
      // Echo the exact ability/item operands this H&S path read, so host tests can prove that
      // no constructor/default substitution happened and that stripped items stayed absent.
      attackerAbility: attacker.ability || null,
      defenderAbility: defender.ability || null,
      attackerItem: attacker.item || null,
      defenderItem: defender.item || null
    };
  }

  const isPhysical = (move.category === 'Physical');
  const isSpecial = (move.category === 'Special');

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
  const attackAfterStages = Math.floor((rawAtk * atkRatio[0]) / atkRatio[1]);
  const defRatio = HNS_STAT_STAGE_RATIOS[defStage + 6];
  const defenseAfterStages = Math.floor((rawDef * defRatio[0]) / defRatio[1]);

  const attackerStatus = (attacker.status || input.attacker?.status || '').toLowerCase();
  const rawStatus1 = input.attacker?.status1;
  const hasStatusForGuts = Number.isInteger(rawStatus1)
    ? (rawStatus1 & HNS_STATUS1_ANY_MASK) !== 0
    : attackerStatus !== '';

  // CalcAttackStat's order: attacker ability modifiers, target ability modifiers, then the
  // offensive badge. Compose their UQ4.12 values in that order and apply once to the integer stat
  // after the ordinary stage ratio has been rounded.
  const attackModifier = createHnsModifierAccumulator();
  if (isPhysical && (attacker.ability === 'Huge Power' || attacker.ability === 'Pure Power')) {
    attackModifier.add(8192);
  }
  if (attacker.ability === 'Guts' && isPhysical && hasStatusForGuts) {
    attackModifier.add(6144);
  }
  if (attacker.ability === 'Hustle' && isPhysical) {
    attackModifier.add(6144);
  }
  if (attacker.ability === 'Solar Power' && isSpecial &&
      isBattlerWeatherAffected(attacker, 'Sun', field, attacker, defender, input)) {
    attackModifier.add(6144);
  }
  const attackerHp = input.attacker?.hp;
  const attackerMaxHp = input.attacker?.maxHP;
  if (attacker.ability === 'Defeatist' && Number.isInteger(attackerHp) &&
      Number.isInteger(attackerMaxHp) && attackerMaxHp > 0 && attackerHp >= 0 &&
      attackerHp <= attackerMaxHp && attackerHp <= Math.floor(attackerMaxHp / 2)) {
    attackModifier.add(2048);
  }

  // H&S 2.0.5 pinch abilities (src/battle_util.c CalcAttackStat): the attacker's ability
  // modifier is x1.5 when the effective move type matches the boosted type AND the live HP is at
  // or below maxHP/3 (integer division). This is an ATTACK-STAT modifier, composed with
  // uq4_12_multiply_half_down. Its position before defender abilities and badges mirrors
  // CalcAttackStat's attacker -> target -> hold effect -> badge order.
  // The HP operands are boundary-owned live gBattleMons values; when they are absent the branch
  // does not fire, and the Kotlin capability policy refuses a matching-type pinch request instead
  // of letting it be computed as inactive.
  const HNS_PINCH_TYPES = { Overgrow: 'Grass', Blaze: 'Fire', Torrent: 'Water', Swarm: 'Bug' };
  const pinchType = HNS_PINCH_TYPES[attacker.ability];
  const pinchHp = input.attacker?.hp;
  const pinchMaxHp = input.attacker?.maxHP;
  if (pinchType && effectiveMoveType === pinchType && Number.isInteger(pinchHp) && Number.isInteger(pinchMaxHp) &&
      pinchMaxHp > 0 && pinchHp <= Math.floor(pinchMaxHp / 3)) {
    attackModifier.add(6144);
  }

  if (defender.ability === 'Thick Fat' && (effectiveMoveType === 'Fire' || effectiveMoveType === 'Ice')) {
    attackModifier.add(2048);
  }

  const atkBadge = isPhysical ? !!input.attacker?.badgeBoosts?.atk : !!input.attacker?.badgeBoosts?.spa;
  if (atkBadge) attackModifier.add(4506);
  const userFinalAttack = Math.max(1, attackModifier.apply(attackAfterStages));

  // Defense-stage modifiers have their own accumulator so future defense abilities/items can be
  // inserted at the pinned CalcDefenseStat stage without changing base or final damage ordering.
  const defenseModifier = createHnsModifierAccumulator();
  if (defender.ability === 'Fur Coat' && isPhysical && !defenderAbilitySuppressed) {
    defenseModifier.add(8192);
  }
  const defBadge = isPhysical ? !!input.defender?.badgeBoosts?.def : !!input.defender?.badgeBoosts?.spd;
  if (defBadge) defenseModifier.add(4506);
  const targetFinalDefense = Math.max(1, defenseModifier.apply(defenseAfterStages));

  // CalcMoveBasePowerAfterModifiers has a separate fixed-point accumulator from the two stat
  // stages. Preserve the Group C Dry Skin × Wise Glasses composition correction.
  const basePowerModifier = createHnsModifierAccumulator(halfUp);
  // `move.bp` is the authoritative H&S move power supplied by the boundary. The ordinary
  // allow-list proves `GetMoveEffect(move) == EFFECT_HIT`, so `CalcMoveBasePower` leaves it
  // unchanged before Technician's `basePower <= 60` check. Matching move flags are generated
  // from the pinned MoveInfo table and rebound by move ID; caller move data cannot set them.
  const abilityMoveFlags = new Set(input.move?.hnsMoveAbilityFlags || []);
  const ateBoost = input.move?.overrides?.ateBoost === true;
  const status1 = input.attacker?.status1;
  const statusKnown = Number.isInteger(status1) && (status1 & ~0x1fff) === 0;
  const statusHas = (mask) => statusKnown && (status1 & mask) !== 0;
  switch (attacker.ability) {
    case 'Technician':
      if (move.bp <= 60) basePowerModifier.add(6144);
      break;
    case 'Iron Fist':
      if (abilityMoveFlags.has('punchingMove')) basePowerModifier.add(4915);
      break;
    case 'Strong Jaw':
      if (abilityMoveFlags.has('bitingMove')) basePowerModifier.add(6144);
      break;
    case 'Mega Launcher':
      if (abilityMoveFlags.has('pulseMove')) basePowerModifier.add(6144);
      break;
    case 'Sharpness':
      if (abilityMoveFlags.has('slicingMove')) basePowerModifier.add(6144);
      break;
    case 'Punk Rock':
      if (moveFlags.has('soundMove')) basePowerModifier.add(5325);
      break;
    case 'Water Bubble':
      if (effectiveMoveType === 'Water') basePowerModifier.add(8192);
      break;
    case 'Steelworker':
      if (effectiveMoveType === 'Steel') basePowerModifier.add(6144);
      break;
    case 'Steely Spirit':
      if (effectiveMoveType === 'Steel') basePowerModifier.add(6144);
      break;
    case 'Toxic Boost':
      if (isPhysical && statusHas(0x88)) basePowerModifier.add(6144);
      break;
    case 'Flare Boost':
      if (isSpecial && statusHas(0x10)) basePowerModifier.add(6144);
      break;
    case 'Refrigerate':
      if (effectiveMoveType === 'Ice' && ateBoost) basePowerModifier.add(4915);
      break;
    case 'Pixilate':
      if (effectiveMoveType === 'Fairy' && ateBoost) basePowerModifier.add(4915);
      break;
    case 'Aerilate':
      if (effectiveMoveType === 'Flying' && ateBoost) basePowerModifier.add(4915);
      break;
    case 'Galvanize':
      if (effectiveMoveType === 'Electric' && ateBoost) basePowerModifier.add(4915);
      break;
    case 'Normalize':
      if (effectiveMoveType === 'Normal' && ateBoost) basePowerModifier.add(4915);
      break;
    default:
      break;
  }
  // CalcMoveBasePowerAfterModifiers target-ability slot follows attacker, field, and partner
  // abilities and precedes held items. Compose it into the same half-up product before applying
  // the completed product once to integer base power.
  if (!bypassTargetAbility) {
    if ((defenderAbility === 'Heatproof' || defenderAbility === 'Water Bubble') &&
        effectiveMoveType === 'Fire') {
      basePowerModifier.add(2048);
    } else if (defenderAbility === 'Dry Skin' && effectiveMoveType === 'Fire') {
      basePowerModifier.add(5120);
    }
  }
  const item = (input.attacker?.item || attacker.item || '').toLowerCase();
  const typeBoostType = HNS_TYPE_POWER_ITEMS[item];
  if (typeBoostType === effectiveMoveType) basePowerModifier.add(4915);
  // H&S 2.0.5 Wise Glasses (src/battle_util.c:6818 CalcMoveBasePowerAfterModifiers):
  // HOLD_EFFECT_WISE_GLASSES multiplies base power by (1.0 + holdEffectParamAtk%), where
  // holdEffectParamAtk = 10, floored percent is (4096 * 10) / 100 = 409, so modifier is 4505 (UQ4.12).
  // Applies only to Special moves (IsBattleMoveSpecial(move)). Its UQ4.12 value is combined
  // with the ability modifier above before base power is rounded, as in the pinned pipeline.
  if (item === 'wise glasses' && isSpecial) basePowerModifier.add(4505);

  const bp = basePowerModifier.apply(move.bp);

  const level = attacker.level || 50;
  let dmg = calculateHnsBaseDamage(bp, userFinalAttack, targetFinalDefense, level);

  const gameType = normalizeGameType(field.gameType || input.field?.gameType);
  // H&S applies the Gen-III spread reduction only when GetMoveTargetCount(ctx) == 2, i.e. when
  // the move actually hits both present opposing battlers. The move's static target class alone
  // is NOT sufficient: Rock Slide against a single remaining foe has target count 1 and is not
  // halved, while the same move with both foes present has count 2 and is halved. That count is
  // live battle state, so it must be supplied explicitly as `field.targetCount`; when it is
  // absent this fails closed instead of guessing from gameType + target class.
  if (gameType === 'Doubles' && move.target === 'allAdjacentFoes') {
    const targetCount = input.field?.targetCount;
    if (targetCount === undefined || targetCount === null) {
      throw new Error(
        'H&S Doubles spread move requires field.targetCount (GetMoveTargetCount); refusing to guess the spread modifier'
      );
    }
    if (typeof targetCount !== 'number' || !Number.isInteger(targetCount) || targetCount < 1) {
      throw new Error('field.targetCount must be a positive integer, got ' + JSON.stringify(targetCount));
    }
    if (targetCount === 2) {
      dmg = applyHnsFinalDamageModifiers(dmg, [2048]);
    }
  }

  const weatherStr = (field.weather || input.field?.weather || '').toLowerCase();
  if (weatherStr.includes('rain')) {
    if (effectiveMoveType === 'Fire') dmg = applyHnsFinalDamageModifiers(dmg, [2048]);
    else if (effectiveMoveType === 'Water') dmg = applyHnsFinalDamageModifiers(dmg, [6144]);
  } else if (weatherStr.includes('sun')) {
    if (effectiveMoveType === 'Water') dmg = applyHnsFinalDamageModifiers(dmg, [2048]);
    else if (effectiveMoveType === 'Fire') dmg = applyHnsFinalDamageModifiers(dmg, [6144]);
  }

  if (move.isCrit) {
    dmg = applyHnsFinalDamageModifiers(dmg, [8192]);
  }

  const hasStab = attacker.types && attacker.types.some(t => t.toLowerCase() === effectiveMoveType.toLowerCase());
  const stabMod = (attacker.ability === 'Adaptability') ? 8192 : 6144;

  const isBurnedStatus = Number.isInteger(rawStatus1)
    ? (rawStatus1 & HNS_STATUS1_BURN_MASK) !== 0
    : attackerStatus === 'brn';
  const isBurned = isPhysical && isBurnedStatus &&
    !(attacker.ability === 'Guts' && hasStatusForGuts);

  let screenModifier = HNS_UQ4_12_ONE;
  if (!move.isCrit) {
    const defSide = field.defenderSide || input.field?.defenderSide;
    if (isPhysical && defSide?.isReflect) screenModifier = 2048;
    if (!isPhysical && defSide?.isLightScreen) screenModifier = 2048;
  }
  if (screenModifier !== HNS_UQ4_12_ONE && gameType === 'Doubles') screenModifier = 2732;

  // GetOtherModifiers is its own pinned UQ4.12 accumulation stage after STAB, effectiveness and
  // burn. Keep each named source slot in order, including neutral slots that this production
  // subset does not model: target state, screens, Collision Course/Electro Drift, ability slots,
  // defender partner, then item slots. The ability and item order depends on unmodified speeds.
  const otherFinalModifier = createHnsModifierAccumulator();
  const targetStateFinalModifier = HNS_UQ4_12_ONE; // Active Glaive Rush/Tar Shot contexts fail closed.
  const collisionCourseFinalModifier = HNS_UQ4_12_ONE; // Collision Course/Electro Drift are out of the ordinary move allow-list.
  const defenderPartnerAbilityFinalModifier = HNS_UQ4_12_ONE; // Doubles is not production-authorized.
  const attackerItemFinalModifier = HNS_UQ4_12_ONE; // No final attacker-item branch is admitted here.
  const defenderItemFinalModifier = HNS_UQ4_12_ONE; // No final defender-item branch is admitted here.
  otherFinalModifier.add(targetStateFinalModifier);
  otherFinalModifier.add(screenModifier);
  otherFinalModifier.add(collisionCourseFinalModifier);
  const rawAttackerSpeed = input.attacker?.rawStats?.speed !== undefined
    ? input.attacker.rawStats.speed
    : (attacker.rawStats ? attacker.rawStats.spe : attacker.stats.spe);
  const rawDefenderSpeed = input.defender?.rawStats?.speed !== undefined
    ? input.defender.rawStats.speed
    : (defender.rawStats ? defender.rawStats.spe : defender.stats.spe);
  // These are named slots in the pinned GetOtherModifiers product. Tinted Lens/Neuroforce/Sniper
  // use the attacker slot; Filter/Solid Rock/Prism Armor/Multiscale/Shadow Shield/Ice Scales join
  // Punk Rock in the defender slot, preserving source speed order and half-down product arithmetic.
  let attackerAbilityFinalModifier = HNS_UQ4_12_ONE;
  switch (attacker.ability) {
    case 'Neuroforce':
      if (typeEffectiveness >= 2.0) attackerAbilityFinalModifier = 5120;
      break;
    case 'Sniper':
      if (move.isCrit) attackerAbilityFinalModifier = 6144;
      break;
    case 'Tinted Lens':
      if (typeEffectiveness <= 0.5) attackerAbilityFinalModifier = 8192;
      break;
  }

  const defenderFinalAbility = defenderAbilitySuppressed ? '' : defender.ability;
  let defenderAbilityFinalModifier = HNS_UQ4_12_ONE;
  switch (defenderFinalAbility) {
    case 'Punk Rock':
      if (moveFlags.has('soundMove')) defenderAbilityFinalModifier = 2048;
      break;
    case 'Filter':
    case 'Solid Rock':
    case 'Prism Armor':
      if (typeEffectiveness >= 2.0) defenderAbilityFinalModifier = 3072;
      break;
    case 'Multiscale':
    case 'Shadow Shield': {
      const hpAtHit = input.defender?.hpAtHit;
      const maxHpAtHit = input.defender?.maxHpAtHit;
      if (Number.isInteger(hpAtHit) && Number.isInteger(maxHpAtHit) && maxHpAtHit > 0 &&
          hpAtHit === maxHpAtHit) defenderAbilityFinalModifier = 2048;
      break;
    }
    case 'Ice Scales':
      if (move.category === 'Special') defenderAbilityFinalModifier = 2048;
      break;
  }
  if (rawAttackerSpeed >= rawDefenderSpeed) {
    otherFinalModifier.add(attackerAbilityFinalModifier);
    otherFinalModifier.add(defenderAbilityFinalModifier);
    otherFinalModifier.add(defenderPartnerAbilityFinalModifier);
    otherFinalModifier.add(attackerItemFinalModifier);
    otherFinalModifier.add(defenderItemFinalModifier);
  } else {
    otherFinalModifier.add(defenderAbilityFinalModifier);
    otherFinalModifier.add(defenderPartnerAbilityFinalModifier);
    otherFinalModifier.add(attackerAbilityFinalModifier);
    otherFinalModifier.add(defenderItemFinalModifier);
    otherFinalModifier.add(attackerItemFinalModifier);
  }

  // ApplyModifiersAfterDmgRoll makes distinct sequential calls in pinned order: STAB, type
  // effectiveness, burn, and finally the accumulated GetOtherModifiers product.
  const postRollModifierStages = [];
  if (hasStab) postRollModifierStages.push(stabMod);
  if (typeEffectiveness !== 1.0) {
    postRollModifierStages.push(Math.round(typeEffectiveness * HNS_UQ4_12_ONE));
  }
  if (isBurned) postRollModifierStages.push(2048);
  postRollModifierStages.push(otherFinalModifier.value());

  const damageArray = [];
  for (let r = 85; r <= 100; r++) {
    let x = Math.floor((dmg * r) / 100);
    for (const modifier of postRollModifierStages) {
      x = applyHnsFinalDamageModifiers(x, [modifier]);
    }
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
    moveType: effectiveMoveType,
    movePower: move.bp,
    attackerName: attacker.name,
    attackerTypes: attacker.types,
    defenderName: defender.name,
    defenderTypes: defender.types,
    defenderMaxHP: maxHP,
    koChanceText: "",
    effectiveness: typeEffectiveness,
    immunityCause: null,
    immunityCauses: [],
    attackerAbility: attacker.ability || null,
    defenderAbility: defender.ability || null,
    attackerItem: attacker.item || null,
    defenderItem: defender.item || null
  };
}

// Source-equivalent subset of IsBattlerWeatherAffected for the request-local weather branch.
// The boundary has already rebound ordinary weather and effective abilities/items from live state.
function isBattlerWeatherAffected(battler, requestedWeather, field, attacker, defender, input) {
  if (field.weather !== requestedWeather) return false;
  const liveAbilities = [attacker.ability || '', defender.ability || ''];
  if (liveAbilities.includes('Cloud Nine') || liveAbilities.includes('Air Lock')) return false;
  // Utility Umbrella is consumed here only to decide the holder's weather-affected predicate;
  // the separate item capability policy remains responsible for its independent limitation.
  const item = String(input.attacker?.item || battler.item || '').toLowerCase();
  if (input.attacker?.hnsEffectiveItemId === 513 || item === 'utility umbrella') return false;
  return true;
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
