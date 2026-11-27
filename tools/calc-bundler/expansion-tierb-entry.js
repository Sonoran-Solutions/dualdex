import { halfDown, createHnsModifierAccumulator } from './hns-fixed-point';

const UQ4_12_ONE = 4096;
const UQ4_12_HALF = 2048;
const UQ4_12_ONE_POINT_FIVE = 6144;

// Modern 18-type chart (Gen 6+, upstream pokeemerald-expansion default):
// Fairy present; Steel does NOT resist Ghost or Dark.
// Each row lists [half, double, immune] defender types; all other pairs are 1.0x.
const MODERN_TYPE_CHART = Object.freeze({
  Normal:   [['Rock', 'Steel'], [], ['Ghost']],
  Fighting: [['Flying', 'Poison', 'Bug', 'Psychic', 'Fairy'], ['Normal', 'Rock', 'Steel', 'Ice', 'Dark'], ['Ghost']],
  Flying:   [['Rock', 'Steel', 'Electric'], ['Fighting', 'Bug', 'Grass'], []],
  Poison:   [['Poison', 'Ground', 'Rock', 'Ghost'], ['Grass', 'Fairy'], ['Steel']],
  Ground:   [['Bug', 'Grass'], ['Poison', 'Rock', 'Steel', 'Fire', 'Electric'], ['Flying']],
  Rock:     [['Fighting', 'Ground', 'Steel'], ['Flying', 'Bug', 'Fire', 'Ice'], []],
  Bug:      [['Fighting', 'Flying', 'Poison', 'Ghost', 'Steel', 'Fire', 'Fairy'], ['Grass', 'Psychic', 'Dark'], []],
  Ghost:    [['Dark'], ['Ghost', 'Psychic'], ['Normal']],
  Steel:    [['Steel', 'Fire', 'Water', 'Electric'], ['Rock', 'Ice', 'Fairy'], []],
  Fire:     [['Rock', 'Fire', 'Water', 'Dragon'], ['Bug', 'Steel', 'Grass', 'Ice'], []],
  Water:    [['Water', 'Grass', 'Dragon'], ['Ground', 'Rock', 'Fire'], []],
  Grass:    [['Flying', 'Poison', 'Bug', 'Steel', 'Fire', 'Grass', 'Dragon'], ['Ground', 'Rock', 'Water'], []],
  Electric: [['Grass', 'Electric', 'Dragon'], ['Flying', 'Water'], ['Ground']],
  Psychic:  [['Steel', 'Psychic'], ['Fighting', 'Poison'], ['Dark']],
  Ice:      [['Steel', 'Fire', 'Water', 'Ice'], ['Flying', 'Ground', 'Grass', 'Dragon'], []],
  Dragon:   [['Steel'], ['Dragon'], ['Fairy']],
  Dark:     [['Fighting', 'Dark', 'Fairy'], ['Ghost', 'Psychic'], []],
  Fairy:    [['Poison', 'Steel', 'Fire'], ['Fighting', 'Dragon', 'Dark'], []],
});

const VALID_WEATHERS = ['None', 'Rain', 'Sun'];
const VALID_CATEGORIES = ['Physical', 'Special'];

const EXPANSION_FIELDS = [
  'level', 'power', 'category', 'moveType',
  'attack', 'spAttack', 'defense', 'spDefense',
  'attackStage', 'spAttackStage', 'defenseStage', 'spDefenseStage',
  'attackerTypes', 'defenderTypes',
  'attackerBurned', 'weather', 'isReflect', 'isLightScreen', 'isCrit',
];

function isValidType(typeName) {
  return typeof typeName === 'string' && Object.prototype.hasOwnProperty.call(MODERN_TYPE_CHART, typeName);
}

function hasExactlyFields(value, ...fields) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  const keys = Reflect.ownKeys(value);
  return keys.length === fields.length && fields.every(field => keys.includes(field));
}

function validateTypePair(types, fieldName) {
  if (!Array.isArray(types) || types.length < 1 || types.length > 2) {
    throw new RangeError(`Unsupported operand: ${fieldName}`);
  }
  for (const t of types) {
    if (!isValidType(t)) {
      throw new RangeError(`Unsupported operand: ${fieldName}`);
    }
  }
  return types.length === 2 && types[0] === types[1] ? [types[0]] : types;
}

function validateIntRange(value, low, high, fieldName) {
  if (!Number.isInteger(value) || value < low || value > high) {
    throw new RangeError(`Unsupported operand: ${fieldName}`);
  }
}

function validateBool(value, fieldName) {
  if (typeof value !== 'boolean') {
    throw new RangeError(`Unsupported operand: ${fieldName}`);
  }
}

function applyStatStage(stat, stage) {
  const scaled = stage >= 0
    ? Math.floor((stat * (2 + stage)) / 2)
    : Math.floor((stat * 2) / (2 - stage));
  return Math.max(1, scaled);
}

function typeMatchup(moveType, defenderType) {
  const [half, double, immune] = MODERN_TYPE_CHART[moveType];
  if (immune.includes(defenderType)) return 0;
  if (half.includes(defenderType)) return 0.5;
  if (double.includes(defenderType)) return 2;
  return 1;
}

/**
 * Upstream pokeemerald-expansion (modern Gen 8/9) ordinary-hit Singles UQ4.12 pipeline.
 * Tier-B ESTIMATED scope: Physical/Special split, modern 18-type chart, STAB, Sun/Rain,
 * Reflect/Light Screen (bypassed on crit), Burn (physical), stat stages (-6..+6, crit
 * ignores negative offense / positive defense), crit (1.5x UQ4.12), 16 rolls 85..100.
 */
function calculateExpansionTierBDamage(input) {
  if (!hasExactlyFields(input, ...EXPANSION_FIELDS)) {
    throw new TypeError('Expected exactly the Expansion Tier-B operand fields');
  }
  validateIntRange(input.level, 1, 100, 'level');
  validateIntRange(input.power, 1, 250, 'power');
  if (!VALID_CATEGORIES.includes(input.category)) {
    throw new RangeError('Unsupported operand: category');
  }
  if (!isValidType(input.moveType)) {
    throw new RangeError('Unsupported operand: moveType');
  }
  validateIntRange(input.attack, 1, 999, 'attack');
  validateIntRange(input.spAttack, 1, 999, 'spAttack');
  validateIntRange(input.defense, 1, 999, 'defense');
  validateIntRange(input.spDefense, 1, 999, 'spDefense');
  validateIntRange(input.attackStage, -6, 6, 'attackStage');
  validateIntRange(input.spAttackStage, -6, 6, 'spAttackStage');
  validateIntRange(input.defenseStage, -6, 6, 'defenseStage');
  validateIntRange(input.spDefenseStage, -6, 6, 'spDefenseStage');
  const attackerTypes = validateTypePair(input.attackerTypes, 'attackerTypes');
  const defenderTypes = validateTypePair(input.defenderTypes, 'defenderTypes');
  validateBool(input.attackerBurned, 'attackerBurned');
  if (!VALID_WEATHERS.includes(input.weather)) {
    throw new RangeError('Unsupported operand: weather');
  }
  validateBool(input.isReflect, 'isReflect');
  validateBool(input.isLightScreen, 'isLightScreen');
  validateBool(input.isCrit, 'isCrit');

  let effectiveness = 1;
  for (const defType of defenderTypes) {
    effectiveness *= typeMatchup(input.moveType, defType);
  }
  if (effectiveness === 0) {
    return { damage: Array(16).fill(0), effectiveness: 0 };
  }

  const isPhysical = input.category === 'Physical';
  let offenseStage = isPhysical ? input.attackStage : input.spAttackStage;
  let defenseStage = isPhysical ? input.defenseStage : input.spDefenseStage;
  if (input.isCrit) {
    if (offenseStage < 0) offenseStage = 0;
    if (defenseStage > 0) defenseStage = 0;
  }

  const offense = applyStatStage(isPhysical ? input.attack : input.spAttack, offenseStage);
  const defense = applyStatStage(isPhysical ? input.defense : input.spDefense, defenseStage);

  let dmg = Math.floor(Math.floor((Math.floor((2 * input.level) / 5) + 2) * input.power * offense / defense) / 50) + 2;

  // Weather modifier (UQ4.12 half-down)
  if (input.weather === 'Rain') {
    if (input.moveType === 'Water') dmg = halfDown(UQ4_12_ONE_POINT_FIVE, dmg);
    else if (input.moveType === 'Fire') dmg = halfDown(UQ4_12_HALF, dmg);
  } else if (input.weather === 'Sun') {
    if (input.moveType === 'Fire') dmg = halfDown(UQ4_12_ONE_POINT_FIVE, dmg);
    else if (input.moveType === 'Water') dmg = halfDown(UQ4_12_HALF, dmg);
  }

  // Modern Gen 8/9 critical hit modifier: 1.5x (UQ_4_12(1.5) = 6144)
  if (input.isCrit) {
    dmg = halfDown(UQ4_12_ONE_POINT_FIVE, dmg);
  }

  const hasStab = attackerTypes.includes(input.moveType);
  const isBurned = isPhysical && input.attackerBurned;

  const otherFinalModifier = createHnsModifierAccumulator();
  if (!input.isCrit) {
    if (isPhysical && input.isReflect) otherFinalModifier.add(UQ4_12_HALF);
    if (!isPhysical && input.isLightScreen) otherFinalModifier.add(UQ4_12_HALF);
  }
  const otherMod = otherFinalModifier.value();

  const postRollStages = [];
  if (hasStab) postRollStages.push(UQ4_12_ONE_POINT_FIVE);
  if (effectiveness !== 1) postRollStages.push(Math.round(effectiveness * UQ4_12_ONE));
  if (isBurned) postRollStages.push(UQ4_12_HALF);
  if (otherMod !== UQ4_12_ONE) postRollStages.push(otherMod);

  const damage = [];
  for (let r = 85; r <= 100; r++) {
    let x = Math.floor((dmg * r) / 100);
    for (const modifier of postRollStages) {
      x = halfDown(modifier, x);
    }
    if (x < 1) x = 1;
    damage.push(x);
  }

  return { damage, effectiveness };
}

function calculateExpansionTierBRequest(input) {
  const { typeSystem, field, expansion, display } = input;
  if (!hasExactlyFields(input, 'typeSystem', 'field', 'expansion', 'display') ||
      !hasExactlyFields(field, 'gameType') || field.gameType !== 'Singles') {
    throw new TypeError('Expected an Expansion Tier-B Singles request');
  }
  if (!hasExactlyFields(display, 'attackerName', 'defenderName', 'moveName', 'defenderMaxHP') ||
      !['attackerName', 'defenderName', 'moveName'].every(key =>
        typeof display[key] === 'string' && display[key].trim().length > 0) ||
      !Number.isInteger(display.defenderMaxHP) || display.defenderMaxHP < 1 || display.defenderMaxHP > 999) {
    throw new TypeError('Invalid Expansion Tier-B result metadata');
  }
  const { damage, effectiveness } = calculateExpansionTierBDamage(expansion);
  const minDamage = damage[0];
  const maxDamage = damage[15];
  return {
    ...display,
    success: true,
    typeSystem,
    damage,
    range: [minDamage, maxDamage],
    minDamage,
    maxDamage,
    effectiveness,
    movePower: expansion.power,
    moveType: expansion.moveType,
    moveCategory: expansion.category,
    desc: `${display.attackerName} ${display.moveName} vs. ${display.defenderName}: ` +
      `${minDamage}–${maxDamage} HP (${(100 * minDamage / display.defenderMaxHP).toFixed(1)}–` +
      `${(100 * maxDamage / display.defenderMaxHP).toFixed(1)}%)`,
  };
}

// Modular Expansion Tier-B addon layered on top of the frozen core calc_bundle.js.
// Delegates every non-expansion_tierb request untouched to the wrapped calculateDamage.
(function attachExpansionTierBAddon() {
  const host = globalThis.DualDexCalc;
  if (!host || typeof host.calculateDamage !== 'function') {
    throw new Error('DualDexCalc base bundle must be initialized before expansion_tierb_calc_bundle.js');
  }
  const baseCalculateDamage = host.calculateDamage;
  host.calculateDamage = function(inputJsonStr) {
    let parsed;
    try {
      parsed = typeof inputJsonStr === 'string' ? JSON.parse(inputJsonStr) : inputJsonStr;
    } catch (_) {
      return baseCalculateDamage.call(host, inputJsonStr);
    }
    if (parsed && parsed.typeSystem === 'expansion_tierb') {
      try {
        return JSON.stringify(calculateExpansionTierBRequest(parsed));
      } catch (e) {
        return JSON.stringify({ success: false, error: e && e.message ? e.message : String(e) });
      }
    }
    return baseCalculateDamage.call(host, inputJsonStr);
  };
})();
