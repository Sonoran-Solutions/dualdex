'use strict';

const HNS_UQ4_12_ONE = 4096;

function halfDown(modifier, value) {
  return Math.floor((modifier * value + 2047) / HNS_UQ4_12_ONE);
}

function halfUp(modifier, value) {
  return Math.floor((modifier * value + 2048) / HNS_UQ4_12_ONE);
}

/**
 * UQ4.12 source-ordered modifier product. Callers choose the pinned operator for each factor;
 * applying the completed product to an integer always uses uq4_12_multiply_by_int_half_down.
 */
function createHnsModifierAccumulator(defaultMultiply = halfDown) {
  let modifier = HNS_UQ4_12_ONE;
  const addWith = (multiply, next) => { modifier = multiply(next, modifier); };
  return {
    add(next) { addWith(defaultMultiply, next); },
    addHalfDown(next) { addWith(halfDown, next); },
    addHalfUp(next) { addWith(halfUp, next); },
    value() { return modifier; },
    apply(integer) { return halfDown(modifier, integer); }
  };
}

module.exports = { halfDown, halfUp, createHnsModifierAccumulator };
