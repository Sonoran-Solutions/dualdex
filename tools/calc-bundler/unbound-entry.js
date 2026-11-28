import { calculateUnboundDamage } from './unbound-cfru';

const CFRU_TYPE_NAMES = [
  'Normal', 'Fighting', 'Flying', 'Poison', 'Ground', 'Rock', 'Bug', 'Ghost', 'Steel', null,
  'Fire', 'Water', 'Grass', 'Electric', 'Psychic', 'Ice', 'Dragon', 'Dark',
];
CFRU_TYPE_NAMES[23] = 'Fairy';

function hasExactlyFields(value, ...fields) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  const keys = Reflect.ownKeys(value);
  return keys.length === fields.length && fields.every(field => keys.includes(field));
}

function calculateUnboundRequest(input) {
  const { typeSystem, field, unbound, display } = input;
  if (!hasExactlyFields(input, 'typeSystem', 'field', 'unbound', 'display') ||
      !hasExactlyFields(field, 'gameType') || field.gameType !== 'Singles') {
    throw new TypeError('Expected an Unbound CFRU Singles request');
  }
  if (!hasExactlyFields(display, 'attackerName', 'defenderName', 'moveName', 'defenderMaxHP') ||
      !['attackerName', 'defenderName', 'moveName'].every(key =>
        typeof display[key] === 'string' && display[key].trim().length > 0) ||
      !Number.isInteger(display.defenderMaxHP) || display.defenderMaxHP < 1 || display.defenderMaxHP > 999) {
    throw new TypeError('Invalid Unbound result metadata');
  }
  const damage = calculateUnboundDamage(unbound);
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
    movePower: unbound.power,
    moveType: CFRU_TYPE_NAMES[unbound.move_type],
    moveCategory: unbound.category === 0 ? 'Physical' : 'Special',
    desc: `${display.attackerName} ${display.moveName} vs. ${display.defenderName}: ` +
      `${minDamage}–${maxDamage} HP (${(100 * minDamage / display.defenderMaxHP).toFixed(1)}–` +
      `${(100 * maxDamage / display.defenderMaxHP).toFixed(1)}%)`,
  };
}

// Modular Unbound CFRU addon layered on top of the frozen core calc_bundle.js.
// Delegates every non-Unbound request untouched to the frozen calculateDamage.
(function attachUnboundAddon() {
  const host = globalThis.DualDexCalc;
  if (!host || typeof host.calculateDamage !== 'function') {
    throw new Error('DualDexCalc base bundle must be initialized before unbound_calc_bundle.js');
  }
  const baseCalculateDamage = host.calculateDamage;
  host.calculateDamage = function(inputJsonStr) {
    let parsed;
    try {
      parsed = typeof inputJsonStr === 'string' ? JSON.parse(inputJsonStr) : inputJsonStr;
    } catch (_) {
      return baseCalculateDamage.call(host, inputJsonStr);
    }
    if (parsed && parsed.typeSystem === 'unbound_cfru') {
      try {
        return JSON.stringify(calculateUnboundRequest(parsed));
      } catch (e) {
        return JSON.stringify({ success: false, error: e && e.message ? e.message : String(e) });
      }
    }
    return baseCalculateDamage.call(host, inputJsonStr);
  };
})();
