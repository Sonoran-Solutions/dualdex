#!/usr/bin/env node
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { calculateUnboundDamage } = require('../calc-bundler/unbound-cfru');

// The committed host oracle is the only expected-damage authority.
const corpus = JSON.parse(fs.readFileSync(path.join(__dirname, 'corpus.json'), 'utf8'));
assert.ok(Array.isArray(corpus.cases) && corpus.cases.length > 0, 'Empty corpus');
let rolls = 0;
let mismatches = 0;
let errors = 0;
for (const entry of corpus.cases) {
  assert.equal(entry.rolls.length, 16, `Invalid oracle roll count: ${entry.id}`);
  try {
    const actual = calculateUnboundDamage(Object.freeze({ ...entry.input }));
    assert.ok(Array.isArray(actual) && actual.length === 16, 'Expected 16 rolls');
    for (let index = 0; index < 16; ++index) {
      ++rolls;
      if (!Number.isInteger(actual[index]) || actual[index] !== entry.rolls[index]) {
        if (mismatches < 10) {
          console.error(`case ${entry.id}, roll ${85 + index}: expected ${entry.rolls[index]}, got ${actual[index]}`);
        }
        ++mismatches;
      }
    }
  } catch (error) {
    if (errors < 10) console.error(`case ${entry.id}: ${error.message}`);
    ++errors;
  }
}

const base = corpus.cases[0].input;
const invalid = [
  null, undefined, [], {}, { ...base, terrain: 0 },
  ...['attacker_ability', 'defender_ability', 'attacker_hold_effect', 'defender_hold_effect']
    .map(field => ({ ...base, [field]: 255 })),
  ...['move_type', 'attacker_type_1', 'attacker_type_2', 'defender_type_1', 'defender_type_2']
    .flatMap(field => [9, 18, 20, 24, -1, '0'].map(value => ({ ...base, [field]: value }))),
  ...[2, 4, 33, -1, '0'].map(weather => ({ ...base, weather })),
  { ...base, attacker_ability: 47 }, { ...base, defender_ability: 37 },
  { ...base, attacker_hold_effect: 70 }, { ...base, defender_hold_effect: 74 },
  { ...base, attacker_status: 1 }, { ...base, category: 2 },
  { ...base, screens: 4 }, { ...base, critical: true },
];
for (const [field, low, high] of [
  ['level', 1, 100], ['power', 1, 150],
  ...['attack', 'sp_attack', 'defense', 'sp_defense'].map(field => [field, 1, 999]),
  ...['attack_stage', 'sp_attack_stage', 'defense_stage', 'sp_defense_stage'].map(field => [field, 0, 12]),
]) {
  for (const value of [low - 1, high + 1, 1.5, NaN, Infinity, '1']) {
    invalid.push({ ...base, [field]: value });
  }
}
for (const field of Object.keys(base)) {
  const missing = { ...base };
  delete missing[field];
  invalid.push(missing);
}
// Refuse unsupported state even when a type immunity would otherwise short-circuit.
invalid.push({ ...base, move_type: 16, defender_type_1: 23, attacker_ability: 255 });
for (const input of invalid) assert.throws(() => calculateUnboundDamage(input));

console.log(`unbound js parity: ${corpus.cases.length} cases, ${rolls} rolls, ${mismatches} mismatches`);
if (mismatches || errors) process.exitCode = 1;
