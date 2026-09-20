/*
 * GENERATED FILE — do not edit by hand.
 *
 * struct BattlePokemon layout for the exact Heart & Soul 2.0.5 build,
 * derived from the pinned upstream source by
 *   tools/hns-layout/generate_hns_battle_pokemon_layout.py
 *
 * Pinned upstream: PokemonHnS-Development/pokehns-expansion
 *   commit 1f42b74dff0e9fe942419845d040663dd829a973 (tag Release-v2.0.5)
 * Compiler:   arm-none-eabi-gcc (ARM GNU Toolchain 13.2.Rel1)
 * Flags:      -DMODERN=1 -DTESTING=0 -DPOKEMON_HNS -DEMERALD -std=gnu17 -mthumb -mthumb-interwork -O2 -mabi=apcs-gnu -mtune=arm7tdmi -march=armv4t
 *
 * Evidence chain:
 *   - sizeof/offsets/widths: compiled probe objects read back from
 *     .rodata (see the generator). The probe compiles the pinned
 *     headers with the same §10 flags as the challenge-settings table;
 *     struct BattlePokemon is a packed APCS-GNU layout.
 *   - structural domains: ABILITIES_COUNT and NUMBER_OF_MON_TYPES as
 *     compiled from the pinned constant headers. An observed value
 *     above the maximum is outside the pinned source's value domain
 *     and is reported as such by the reader — never coerced.
 *
 * Sentinel semantics recorded from the pinned source, not invented:
 *   - Type: TYPE_NONE (0) is the empty-slot sentinel (a monotype's
 *     second slot); TYPE_MYSTERY (10) is the battle-only 'typeless'
 *     value (Roost removal, Camouflage-less conversion); TYPE_STELLAR
 *     (20) exists as a real battle type. Duplicate slots are
 *     meaningful (Terastallization sets all three slots equal via
 *     GetBattlerTypes' consumers), so values are exposed verbatim.
 *   - Ability: ABILITY_NONE (0) is part of the pinned enum; the reader
 *     preserves it as an observed value and never substitutes.
 *   - Item: ITEM_NONE (0) is part of the pinned enum and means an
 *     authoritative empty held-item slot. The item domain is the pinned
 *     ITEMS_COUNT; an observed ID above ITEM_ID_MAX is reported as such.
 *
 * Source-text cross-check (compiled ABI wins on disagreement):
 *   agree — ability byte offset: compiled 32, source text 32
 *   agree — types byte offset: compiled 34, source text 34
 *   agree — type slot count: compiled 3, source text 3
 *   agree — highest ability ID: compiled 310, source text 310
 *   agree — highest type ID: compiled 20, source text 20
 *   DISCREPANCY — item byte offset: compiled 48, source text 47 (compiled value is authoritative)
 */

#ifndef DUALDEX_HNS_BATTLE_POKEMON_LAYOUT_GEN_H
#define DUALDEX_HNS_BATTLE_POKEMON_LAYOUT_GEN_H

#define HNS_BATTLE_POKEMON_SIZEOF 136
#define HNS_BATTLE_POKEMON_ABILITY_OFFSET 32
#define HNS_BATTLE_POKEMON_ABILITY_SIZE 2
#define HNS_BATTLE_POKEMON_ABILITY_ID_MAX 310
#define HNS_BATTLE_POKEMON_TYPES_OFFSET 34
#define HNS_BATTLE_POKEMON_TYPE_COUNT 3
#define HNS_BATTLE_POKEMON_TYPE_ELEMENT_SIZE 1
#define HNS_BATTLE_POKEMON_TYPE_ID_MAX 20
#define HNS_BATTLE_POKEMON_ITEM_OFFSET 48
#define HNS_BATTLE_POKEMON_ITEM_SIZE 2
#define HNS_BATTLE_POKEMON_ITEM_ID_MAX 900

#if HNS_BATTLE_POKEMON_ABILITY_OFFSET + HNS_BATTLE_POKEMON_ABILITY_SIZE > HNS_BATTLE_POKEMON_SIZEOF
#error "BattlePokemon ability field exceeds the compiled struct size"
#endif
#if HNS_BATTLE_POKEMON_TYPES_OFFSET + HNS_BATTLE_POKEMON_TYPE_COUNT * HNS_BATTLE_POKEMON_TYPE_ELEMENT_SIZE > HNS_BATTLE_POKEMON_SIZEOF
#error "BattlePokemon types field exceeds the compiled struct size"
#endif
#if HNS_BATTLE_POKEMON_ITEM_OFFSET + HNS_BATTLE_POKEMON_ITEM_SIZE > HNS_BATTLE_POKEMON_SIZEOF
#error "BattlePokemon item field exceeds the compiled struct size"
#endif
#if HNS_BATTLE_POKEMON_ABILITY_SIZE > 4 || HNS_BATTLE_POKEMON_TYPE_ELEMENT_SIZE > 4 || HNS_BATTLE_POKEMON_ITEM_SIZE > 4
#error "BattlePokemon field widths are implausible for this ABI"
#endif

#endif /* DUALDEX_HNS_BATTLE_POKEMON_LAYOUT_GEN_H */
