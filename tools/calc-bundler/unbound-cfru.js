'use strict';

// SOURCE_APPROXIMATE: isolated ordinary-hit Singles arithmetic for the host
// oracle's numeric schema. No shipping move/item records or production adapter.
const TYPES = [0, 1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 13, 14, 15, 16, 17, 23];
const RANGES = {
  level: [1, 100], power: [1, 150], category: [0, 1],
  attack: [1, 999], sp_attack: [1, 999], defense: [1, 999], sp_defense: [1, 999],
  attack_stage: [0, 12], sp_attack_stage: [0, 12],
  defense_stage: [0, 12], sp_defense_stage: [0, 12], screens: [0, 3], critical: [0, 1],
};
const VALUES = {
  move_type: TYPES, attacker_type_1: TYPES, attacker_type_2: TYPES,
  defender_type_1: TYPES, defender_type_2: TYPES,
  attacker_status: [0, 16], weather: [0, 1, 32],
  attacker_ability: [0, 37, 55, 62, 82, 85, 88, 99],
  defender_ability: [0, 47, 97, 148, 248],
  attacker_hold_effect: [0, 74, 92, 112, 119], defender_hold_effect: [0, 70],
};
const FIELDS = [...Object.keys(RANGES), ...Object.keys(VALUES)];

// Source-derived numeric chart, ROM-unverified. Each row lists defender IDs
// for half, double, and zero effectiveness; all other admitted pairs are neutral.
// ponytail: only the corpus types are admitted; replace with a ROM-pinned table
// and extend oracle coverage before accepting any further type IDs.
const CHART = {
  0: [[5, 8], [], [7]],
  1: [[2, 3, 6, 14, 23], [0, 5, 8, 15, 17], [7]],
  2: [[5, 8, 13], [1, 6, 12], []],
  3: [[3, 4, 5, 7], [12, 23], [8]],
  4: [[6, 12], [3, 5, 8, 10, 13], [2]],
  5: [[1, 4, 8], [2, 6, 10, 15], []],
  6: [[1, 2, 3, 7, 8, 10, 23], [12, 14, 17], []],
  7: [[17], [7, 14], [0]],
  8: [[8, 10, 11, 13], [5, 15, 23], []],
  10: [[5, 10, 11, 16], [6, 8, 12, 15], []],
  11: [[11, 12, 16], [4, 5, 10], []],
  12: [[2, 3, 6, 8, 10, 12, 16], [4, 5, 11], []],
  13: [[12, 13, 16], [2, 11], [4]],
  14: [[8, 14], [1, 3], [17]],
  15: [[8, 10, 11, 15], [2, 4, 12, 16], []],
  16: [[8], [16], [23]],
  17: [[1, 17, 23], [7, 14], []],
  23: [[3, 8, 10], [1, 16, 17], []],
};

function staged(stat, stage) {
  return stage < 6 ? Math.floor(stat * 2 / (8 - stage))
    : Math.floor(stat * (stage - 4) / 2);
}

function calculateUnboundDamage(input) {
  if (input === null || typeof input !== 'object' || Array.isArray(input)
      || Reflect.ownKeys(input).length !== FIELDS.length
      || FIELDS.some(field => !Object.prototype.hasOwnProperty.call(input, field))) {
    throw new TypeError('Expected exactly the host oracle input fields');
  }
  for (const [field, [low, high]] of Object.entries(RANGES)) {
    if (!Number.isInteger(input[field]) || input[field] < low || input[field] > high) {
      throw new RangeError(`Unsupported operand: ${field}`);
    }
  }
  for (const [field, values] of Object.entries(VALUES)) {
    if (!values.includes(input[field])) throw new RangeError(`Unsupported operand: ${field}`);
  }

  const physical = input.category === 0;
  const ability = input.attacker_ability;
  const defenderAbility = input.defender_ability;
  const item = input.attacker_hold_effect;
  const type = input.move_type;
  const critical = input.critical === 1;
  const defenderTypes = input.defender_type_1 === input.defender_type_2
    ? [input.defender_type_1] : [input.defender_type_1, input.defender_type_2];
  const [half, double, immune] = CHART[type];
  const multipliers = defenderTypes.map(t => immune.includes(t) ? 0
    : half.includes(t) ? 5 : double.includes(t) ? 20 : 10);
  if (multipliers.includes(0)) return Array(16).fill(0);
  const effectiveness = multipliers.reduce((product, multiplier) => product * multiplier / 10, 1);

  let power = input.power;
  if (ability === 82 && input.power <= 60) power = Math.floor(power * 15 / 10);
  if (item === 74) power = Math.floor(power * 13 / 10);
  if ((item === 112 && physical) || (item === 119 && !physical)) {
    power = Math.floor(power * 11 / 10);
  }

  let offense = physical ? input.attack : input.sp_attack;
  let defense = physical ? input.defense : input.sp_defense;
  if (physical) {
    if (ability === 37) offense *= 2;
    if (ability === 55 || (ability === 62 && input.attacker_status !== 0)) {
      offense = Math.floor(offense * 15 / 10);
    }
  }
  if (defenderAbility === 47 && (type === 10 || type === 15)) offense = Math.floor(offense / 2);
  if (physical && defenderAbility === 148) defense *= 2;
  if (!physical && input.defender_hold_effect === 70) defense = Math.floor(defense * 15 / 10);

  let offenseStage = physical ? input.attack_stage : input.sp_attack_stage;
  let defenseStage = physical ? input.defense_stage : input.sp_defense_stage;
  if (critical) {
    offenseStage = Math.max(6, offenseStage);
    defenseStage = Math.min(6, defenseStage);
  }
  offense = staged(offense, offenseStage);
  defense = Math.max(1, staged(defense, defenseStage));

  // Every division below truncates at its own pipeline boundary.
  let damage = Math.floor((Math.floor(input.level * 2 / 5) + 2) * power * offense / defense);
  damage = Math.floor(damage / 50) + 2;
  if ((input.weather === 1 && type === 11) || (input.weather === 32 && type === 10)) {
    damage = Math.floor(damage * 15 / 10);
  } else if ((input.weather === 1 && type === 10) || (input.weather === 32 && type === 11)) {
    damage = Math.floor(damage / 2);
  }
  if (ability === 99 && effectiveness < 1) damage *= 2;
  if (ability === 85 && critical) damage = Math.floor(damage * 15 / 10);
  if (item === 92 && effectiveness > 1) damage = Math.floor(damage * 12 / 10);
  if ((defenderAbility === 97 && type === 10) || (defenderAbility === 248 && !physical)) {
    damage = Math.floor(damage / 2);
  }
  if (!critical && (input.screens & (physical ? 1 : 2))) damage = Math.floor(damage / 2);
  if (physical && input.attacker_status === 16 && ability !== 62) damage = Math.floor(damage / 2);
  damage = Math.max(1, damage);
  if (critical) damage = Math.floor(damage * 15 / 10);
  if (input.attacker_type_1 === type || input.attacker_type_2 === type) {
    damage = ability === 88 ? damage * 2 : Math.floor(damage * 15 / 10);
  }
  for (const multiplier of multipliers) damage = Math.max(1, Math.floor(damage * multiplier / 10));
  return Array.from({ length: 16 }, (_, index) => Math.max(1, Math.floor(damage * (85 + index) / 100)));
}

module.exports = { calculateUnboundDamage };
