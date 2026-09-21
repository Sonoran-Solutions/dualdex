/*
 * GENERATED FILE — do not edit by hand.
 *
 * Live battle-state layout for the exact Heart & Soul 2.0.5 build,
 * derived from the pinned upstream source by
 *   tools/hns-layout/generate_hns_live_battle_layout.py
 *
 * Pinned upstream: PokemonHnS-Development/pokehns-expansion
 *   commit 1f42b74dff0e9fe942419845d040663dd829a973 (tag Release-v2.0.5)
 * Compiler:   arm-none-eabi-gcc (ARM GNU Toolchain 13.2.Rel1)
 * Flags:      -DMODERN=1 -DTESTING=0 -DPOKEMON_HNS -DEMERALD -std=gnu17 -mthumb -mthumb-interwork -O2 -mabi=apcs-gnu -mtune=arm7tdmi -march=armv4t
 *
 * This is the ABI evidence for the C4e live-state readers: the
 * attacker's HP/maxHP (pinch-ability threshold), status1, the
 * BattlePokemon volatile bits, and the gimmick active array. Ordinary
 * members are compiled offsetof/sizeof scalars; bitfield members are
 * located by compiling one designated-initializer object per bit and
 * reading back the set bit.
 *
 * Struct member offsets (compiled, authoritative over the source
 * 0xNN member comments, which are stale):
 *   sizeof(struct Volatiles)     = 44
 *   BattlePokemon.hp             = 42
 *   BattlePokemon.maxHP          = 46
 *   BattlePokemon.status1        = 80
 *   BattlePokemon.volatiles      = 84
 *   volatile electrified bit     = 54
 *   volatile glaiveRush bit      = 64
 *   volatile minimize bit        = 72
 *   volatile semiInvulnerable    = bit 51 width 3
 *   volatile chargeTimer         = bit 73 width 2
 *   volatile tarShot bit         = 299
 *   volatile read window         = 38 bytes
 *   BattleStruct.gimmick         = 668
 *   BattleGimmickData.activeGimmick = 11

 * Source-text cross-check (compiled ABI wins on disagreement):
 *   DISCREPANCY — hp byte offset: compiled 42, source text 41 (compiled value is authoritative)
 *   DISCREPANCY — maxHP byte offset: compiled 46, source text 45 (compiled value is authoritative)
 *   DISCREPANCY — status1 byte offset: compiled 80, source text 77 (compiled value is authoritative)
 *   DISCREPANCY — volatiles byte offset: compiled 84, source text 81 (compiled value is authoritative)
 */

#ifndef DUALDEX_HNS_LIVE_BATTLE_LAYOUT_GEN_H
#define DUALDEX_HNS_LIVE_BATTLE_LAYOUT_GEN_H

#include "hns_battle_pokemon_layout_gen.h"

#define HNS_LIVE_BP_HP_OFFSET 42
#define HNS_LIVE_BP_HP_SIZE 2
#define HNS_LIVE_BP_MAX_HP_OFFSET 46
#define HNS_LIVE_BP_MAX_HP_SIZE 2
#define HNS_LIVE_BP_STATUS_OFFSET 80
#define HNS_LIVE_BP_STATUS_SIZE 4
#define HNS_LIVE_BP_VOLATILES_OFFSET 84
#define HNS_LIVE_BP_VOLATILE_ELECTRIFIED_BIT 54
#define HNS_LIVE_BP_VOLATILE_GLAIVE_RUSH_BIT 64
#define HNS_LIVE_BP_VOLATILE_MINIMIZE_BIT 72
#define HNS_LIVE_BP_VOLATILE_SEMI_INVULNERABLE_BIT 51
#define HNS_LIVE_BP_VOLATILE_SEMI_INVULNERABLE_WIDTH 3
#define HNS_LIVE_BP_VOLATILE_CHARGE_TIMER_BIT 73
#define HNS_LIVE_BP_VOLATILE_CHARGE_TIMER_WIDTH 2
#define HNS_LIVE_BP_VOLATILE_TAR_SHOT_BIT 299
#define HNS_LIVE_BP_VOLATILE_WINDOW_BYTES 38
#define HNS_LIVE_BATTLE_STRUCT_GIMMICK_OFFSET 668
#define HNS_LIVE_BATTLE_GIMMICK_ACTIVE_OFFSET 11
#define HNS_LIVE_BATTLE_GIMMICK_SIDE_COUNT 2
#define HNS_LIVE_BATTLE_GIMMICK_PARTY_COUNT 6
#define HNS_LIVE_BATTLE_GIMMICK_COUNT 6

/*
 * Field-domain sentinels from the pinned source, recorded here so the
 * reader never invents a value:
 *   SemiInvulnerableState: NONE=0 UNDERGROUND=1 UNDERWATER=2 ON_AIR=3
 *                          PHANTOM_FORCE=4 SKY_DROP=5 COMMANDER=6
 *   enum Gimmick:          NONE=0 MEGA=1 ULTRA_BURST=2 Z_MOVE=3
 *                          DYNAMAX=4 TERA=5
 */

#if HNS_LIVE_BP_HP_OFFSET + HNS_LIVE_BP_HP_SIZE > HNS_BATTLE_POKEMON_SIZEOF
#error "BattlePokemon hp field exceeds the compiled struct size"
#endif
#if HNS_LIVE_BP_MAX_HP_OFFSET + HNS_LIVE_BP_MAX_HP_SIZE > HNS_BATTLE_POKEMON_SIZEOF
#error "BattlePokemon maxHP field exceeds the compiled struct size"
#endif
#if HNS_LIVE_BP_STATUS_OFFSET + HNS_LIVE_BP_STATUS_SIZE > HNS_BATTLE_POKEMON_SIZEOF
#error "BattlePokemon status1 field exceeds the compiled struct size"
#endif
#if HNS_LIVE_BP_VOLATILES_OFFSET + HNS_LIVE_BP_VOLATILE_WINDOW_BYTES > HNS_BATTLE_POKEMON_SIZEOF
#error "BattlePokemon volatiles read window exceeds the compiled struct size"
#endif
#if HNS_LIVE_BP_VOLATILE_ELECTRIFIED_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || HNS_LIVE_BP_VOLATILE_GLAIVE_RUSH_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || HNS_LIVE_BP_VOLATILE_MINIMIZE_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || HNS_LIVE_BP_VOLATILE_SEMI_INVULNERABLE_BIT + HNS_LIVE_BP_VOLATILE_SEMI_INVULNERABLE_WIDTH >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || HNS_LIVE_BP_VOLATILE_CHARGE_TIMER_BIT + HNS_LIVE_BP_VOLATILE_CHARGE_TIMER_WIDTH >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || HNS_LIVE_BP_VOLATILE_TAR_SHOT_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES
#error "an exported volatile bit exceeds the generated read window"
#endif
#if HNS_LIVE_BATTLE_GIMMICK_ACTIVE_OFFSET + HNS_LIVE_BATTLE_GIMMICK_SIDE_COUNT * HNS_LIVE_BATTLE_GIMMICK_PARTY_COUNT > 64
#error "BattleGimmickData.activeGimmick implausibly large"
#endif

#endif /* DUALDEX_HNS_LIVE_BATTLE_LAYOUT_GEN_H */
