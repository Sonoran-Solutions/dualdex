'use strict';

const assert = require('node:assert/strict');
const { createHnsModifierAccumulator } = require('./hns-fixed-point');

// Pinned include/fpmath.h:15 UQ_4_12(n) == (u32)(n * 4096 + 0.5).
const pinnedMacro = value => Math.trunc(value * 4096 + 0.5);
assert.equal(pinnedMacro(1.3), 5325);
assert.equal(pinnedMacro(1.5), 6144);
assert.equal(pinnedMacro(1.3333), 5461);
assert.equal(pinnedMacro(1.33), 5448); // Aura, distinct from 1.3333
assert.equal(pinnedMacro(1.25), 5120); // Rivalry same gender
assert.equal(pinnedMacro(0.75), 3072); // Rivalry / Aura Break / Ruin
// Pinned PercentToUQ4_12(counter * 10), not a rational approximation at final damage.
assert.deepEqual(Array.from({length: 6}, (_, count) =>
  4096 + Math.floor((count * 10 * 4096 + 50) / 100)),
  [4096, 4506, 4915, 5325, 5734, 6144]);

function product(operators) {
  const accumulator = createHnsModifierAccumulator();
  for (const [operator, factor] of operators) accumulator[operator](factor);
  return accumulator;
}

// These UQ4.12 inputs (0.5, 4505/4096, and 1.5) hit fixed-point ties. They pin the per-factor API even
// though the current H&S Attack-stat ability switch has a single ability before later slots.
const mixed = product([
  ['addHalfDown', 2048], ['addHalfUp', 4505], ['addHalfDown', 6144]
]);
const allHalfDown = product([
  ['addHalfDown', 2048], ['addHalfDown', 4505], ['addHalfDown', 6144]
]);
const allHalfUp = product([
  ['addHalfUp', 2048], ['addHalfUp', 4505], ['addHalfUp', 6144]
]);
assert.deepEqual([mixed.value(), allHalfDown.value(), allHalfUp.value()], [3379, 3378, 3380]);
assert.deepEqual([mixed.apply(4096), allHalfDown.apply(4096), allHalfUp.apply(4096)], [3379, 3378, 3380]);

// The final integer conversion is fixed to half-down for every composition policy.
const integerTie = product([['addHalfUp', 6144]]);
assert.equal(integerTie.apply(1), 1);

console.log('H&S mixed fixed-point accumulator self-tests passed');
