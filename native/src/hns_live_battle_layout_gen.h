/*
 * GENERATED FILE — do not edit by hand.
 *
 * Live battle-state layout for the exact Heart & Soul 2.0.5 build,
 * derived from the pinned upstream source by
 *   tools/hns-layout/generate_hns_live_battle_layout.py
 *
 * Pinned upstream: PokemonHnS-Development/pokehns-expansion
 *   commit 1f42b74dff0e9fe942419845d040663dd829a973 (tag Release-v2.0.5)
 * Release-symbol source: official ROM SHA-256 edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b
 * Compiler:   arm-none-eabi-gcc (ARM GNU Toolchain 13.2.Rel1)
 * Flags:      -DMODERN=1 -DTESTING=0 -DPOKEMON_HNS -DEMERALD -std=gnu17 -mthumb -mthumb-interwork -O2 -mabi=apcs-gnu -mtune=arm7tdmi -march=armv4t
 *
 * This is the ABI evidence for the C4e live-state readers and the Group B
 * switch-in settlement proof:
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
 *   BattlePokemon.personality    = 76 (4 bytes)
 *   BattlePokemon.maxHP          = 46
 *   BattlePokemon.status1        = 80
 *   BattlePokemon.volatiles      = 84
 *   volatile electrified bit     = 54
 *   volatile glaiveRush bit      = 64
 *   volatile minimize bit        = 72
 *   volatile semiInvulnerable    = bit 51 width 3
 *   volatile chargeTimer         = bit 73 width 2
 *   volatile tarShot bit         = 299
 *   volatile foresight bit       = 45
 *   volatile miracleEye bit      = 84
 *   volatile root bit            = 75
 *   volatile smackDown bit       = 82
 *   volatile telekinesis bit     = 83
 *   volatile magnetRise bit      = 85
 *   volatile gastroAcid bit      = 80
 *   volatile roostActive bit     = 318
 *   volatile substitute bit       = 39
 *   volatile endured bit         = 322
 *   volatile slowStartTimer      = bit 264 width 3
 *   volatile flashFireBoosted    = bit 307
 *   volatile transformed        = bit 37
 *   volatile boosterEnergyActivated = bit 308
 *   volatile paradoxBoostedStat = bit 328 width 3
 *   volatile vesselOfRuin       = bit 91
 *   volatile swordOfRuin        = bit 92
 *   volatile tabletsOfRuin      = bit 93
 *   volatile beadsOfRuin        = bit 94
 *   volatile embargo            = bit 81
 *   volatile metronomeItemCounter = bit 232 width 8
 *   volatile transformedMonSpecies = bit 128 width 11 (NUM_SPECIES=1573)
 *   volatile read window         = 42 bytes
 *   BattleStruct.gimmick         = 668
 *   BattleStruct.eventState      = 144
 *   EventStates.switchIn         = bit 76 width 8
 *   BattleStruct.battlerState    = 0
 *   BattleStruct.monToSwitchIntoId = 229
 *   sizeof(struct BattlerState)  = 12
 *   BattlerState.switchIn        = bit 80
 *   BattlerState.isFirstTurn     = bit 82 width 2
 *   SWITCH_IN_EVENTS_COUNT      = 12
 *   BattleGimmickData.activeGimmick = 11
 *   BattleGimmickData.usableGimmick = 0
 *   BattleGimmickData.playerSelect = 4
 *   BattleStruct.supremeOverlordCounter = 864 stride 1 count 4
 *   gBattleMainFunc (IWRAM)      = 0x03002F74
 *   action-selection callback  = 0x08088DED
 *   RunTurnActionsFunctions   = 0x0808AA35
 *   gProtectStructs (IWRAM/EWRAM) = 0x020000B8
 *   gSideTimers (IWRAM/EWRAM) = 0x02000258
 *   gBattlersCount (IWRAM/EWRAM) = 0x020000B0
 *   gBattlerAttacker (IWRAM/EWRAM) = 0x02000124
 *   gCurrentMove (IWRAM/EWRAM) = 0x020003A0
 *   gCurrentActionFuncId (IWRAM/EWRAM) = 0x02000125
 *   gCurrentTurnActionNumber (IWRAM/EWRAM) = 0x02000302
 *   gActionsByTurnOrder (IWRAM/EWRAM) = 0x02000304
 *   gBattlerByTurnOrder (IWRAM/EWRAM) = 0x020003B8

 * Source and official-symbol cross-check:
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
#define HNS_LIVE_BP_PERSONALITY_OFFSET 76
#define HNS_LIVE_BP_PERSONALITY_SIZE 4
#define HNS_LIVE_BP_VOLATILES_OFFSET 84
#define HNS_LIVE_BP_VOLATILE_ELECTRIFIED_BIT 54
#define HNS_LIVE_BP_VOLATILE_GLAIVE_RUSH_BIT 64
#define HNS_LIVE_BP_VOLATILE_MINIMIZE_BIT 72
#define HNS_LIVE_BP_VOLATILE_SEMI_INVULNERABLE_BIT 51
#define HNS_LIVE_BP_VOLATILE_SEMI_INVULNERABLE_WIDTH 3
#define HNS_LIVE_BP_VOLATILE_CHARGE_TIMER_BIT 73
#define HNS_LIVE_BP_VOLATILE_CHARGE_TIMER_WIDTH 2
#define HNS_LIVE_BP_VOLATILE_TAR_SHOT_BIT 299
#define HNS_LIVE_BP_VOLATILE_FORESIGHT_BIT 45
#define HNS_LIVE_BP_VOLATILE_MIRACLE_EYE_BIT 84
#define HNS_LIVE_BP_VOLATILE_ROOT_BIT 75
#define HNS_LIVE_BP_VOLATILE_SMACK_DOWN_BIT 82
#define HNS_LIVE_BP_VOLATILE_TELEKINESIS_BIT 83
#define HNS_LIVE_BP_VOLATILE_MAGNET_RISE_BIT 85
#define HNS_LIVE_BP_VOLATILE_GASTRO_ACID_BIT 80
#define HNS_LIVE_BP_VOLATILE_ROOST_ACTIVE_BIT 318
#define HNS_LIVE_BP_VOLATILE_SUBSTITUTE_BIT 39
#define HNS_LIVE_BP_VOLATILE_ENDURED_BIT 322
#define HNS_LIVE_BP_VOLATILE_SLOW_START_TIMER_BIT 264
#define HNS_LIVE_BP_VOLATILE_SLOW_START_TIMER_WIDTH 3
#define HNS_LIVE_BP_VOLATILE_FLASH_FIRE_BOOSTED_BIT 307
#define HNS_LIVE_BP_VOLATILE_TRANSFORMED_BIT 37
#define HNS_LIVE_BP_VOLATILE_BOOSTER_ENERGY_ACTIVATED_BIT 308
#define HNS_LIVE_BP_VOLATILE_PARADOX_BOOSTED_STAT_BIT 328
#define HNS_LIVE_BP_VOLATILE_PARADOX_BOOSTED_STAT_WIDTH 3
#define HNS_LIVE_BP_VOLATILE_VESSEL_OF_RUIN_BIT 91
#define HNS_LIVE_BP_VOLATILE_SWORD_OF_RUIN_BIT 92
#define HNS_LIVE_BP_VOLATILE_TABLETS_OF_RUIN_BIT 93
#define HNS_LIVE_BP_VOLATILE_BEADS_OF_RUIN_BIT 94
#define HNS_LIVE_BP_VOLATILE_HEAL_BLOCK_BIT 86
#define HNS_LIVE_BP_VOLATILE_EMBARGO_BIT 81
#define HNS_LIVE_BP_VOLATILE_METRONOME_ITEM_COUNTER_BIT 232
#define HNS_LIVE_BP_VOLATILE_METRONOME_ITEM_COUNTER_WIDTH 8
#define HNS_LIVE_BP_VOLATILE_TRANSFORMED_MON_SPECIES_BIT 128
#define HNS_LIVE_BP_VOLATILE_TRANSFORMED_MON_SPECIES_WIDTH 11
#define HNS_LIVE_DOUBLES_HELPING_HAND_BIT 32
#define HNS_LIVE_DOUBLES_HELPING_HAND_WIDTH 3
#define HNS_LIVE_DOUBLES_FOLLOW_ME_BIT 144
#define HNS_LIVE_DOUBLES_FOLLOW_ME_WIDTH 4
#define HNS_LIVE_DOUBLES_MOLD_BREAKER_BIT 7559
#define HNS_LIVE_DOUBLES_PLEDGE_BIT 6725
#define HNS_LIVE_DOUBLES_PROTECT_SIZE 12
#define HNS_LIVE_DOUBLES_SIDE_TIMER_SIZE 32
#define HNS_LIVE_DOUBLES_BATTLE_TYPE_MASK 4227137
#define HNS_LIVE_NUM_SPECIES 1573
#define HNS_LIVE_BP_VOLATILE_WINDOW_BYTES 42
#define HNS_LIVE_BATTLE_STRUCT_GIMMICK_OFFSET 668
#define HNS_LIVE_BATTLE_STRUCT_EVENT_STATE_OFFSET 144
#define HNS_LIVE_EVENT_STATE_SWITCH_IN_BIT 76
#define HNS_LIVE_EVENT_STATE_SWITCH_IN_WIDTH 8
#define HNS_LIVE_BATTLE_STRUCT_BATTLER_STATE_OFFSET 0
#define HNS_LIVE_BATTLE_STRUCT_MON_TO_SWITCH_INTO_ID_OFFSET 229
#define HNS_LIVE_BATTLER_STATE_SIZE 12
#define HNS_LIVE_BATTLER_STATE_SWITCH_IN_BIT 80
#define HNS_LIVE_BATTLER_STATE_IS_FIRST_TURN_BIT 82
#define HNS_LIVE_BATTLER_STATE_IS_FIRST_TURN_WIDTH 2
#define HNS_LIVE_MAX_BATTLERS_COUNT 4
#define HNS_LIVE_SWITCH_IN_EVENTS_COUNT 12
#define HNS_LIVE_BATTLE_MAIN_FUNC_GBA_ADDRESS 0x03002F74u
#define HNS_LIVE_MAIN_CALLBACK1_OFFSET 0
#define HNS_LIVE_BATTLE_MAIN_CB1_FUNC_PTR 0x080822F1u
#define HNS_LIVE_ACTION_SELECTION_FUNC_PTR 0x08088DEDu
#define HNS_LIVE_RUN_TURN_ACTIONS_FUNC_PTR 0x0808AA35u
#define HNS_LIVE_BATTLE_GIMMICK_ACTIVE_OFFSET 11
#define HNS_LIVE_BATTLE_GIMMICK_USABLE_OFFSET 0
#define HNS_LIVE_BATTLE_GIMMICK_PLAYER_SELECT_OFFSET 4
#define HNS_LIVE_BATTLE_GIMMICK_SIDE_COUNT 2
#define HNS_LIVE_BATTLE_GIMMICK_PARTY_COUNT 6
#define HNS_LIVE_BATTLE_GIMMICK_COUNT 6
#define HNS_LIVE_GIMMICK_DYNAMAX_VALUE 4
#define HNS_LIVE_NUM_STATS 6
#define HNS_LIVE_BATTLE_STRUCT_SUPREME_OVERLORD_COUNTER_OFFSET 864
#define HNS_LIVE_SUPREME_OVERLORD_COUNTER_STRIDE 1
#define HNS_LIVE_SUPREME_OVERLORD_COUNTER_COUNT 4
#define HNS_LIVE_B_ACTION_EXEC_SCRIPT 10
#define HNS_LIVE_BP_VOLATILE_NEUTRALIZING_GAS_BIT 320
#define HNS_LIVE_B_ACTION_USE_MOVE 0
#define HNS_LIVE_GPROTECTSTRUCTS_GBA_ADDRESS 0x020000B8u
#define HNS_LIVE_GSIDETIMERS_GBA_ADDRESS 0x02000258u
#define HNS_LIVE_GBATTLERSCOUNT_GBA_ADDRESS 0x020000B0u
#define HNS_LIVE_GBATTLERATTACKER_GBA_ADDRESS 0x02000124u
#define HNS_LIVE_GCURRENTMOVE_GBA_ADDRESS 0x020003A0u
#define HNS_LIVE_GCURRENTACTIONFUNCID_GBA_ADDRESS 0x02000125u
#define HNS_LIVE_GCURRENTTURNACTIONNUMBER_GBA_ADDRESS 0x02000302u
#define HNS_LIVE_GACTIONSBYTURNORDER_GBA_ADDRESS 0x02000304u
#define HNS_LIVE_GBATTLERBYTURNORDER_GBA_ADDRESS 0x020003B8u

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
#if HNS_LIVE_BP_PERSONALITY_OFFSET + HNS_LIVE_BP_PERSONALITY_SIZE > HNS_BATTLE_POKEMON_SIZEOF
#error "BattlePokemon personality field exceeds the compiled struct size"
#endif
#if HNS_LIVE_BP_VOLATILES_OFFSET + HNS_LIVE_BP_VOLATILE_WINDOW_BYTES > HNS_BATTLE_POKEMON_SIZEOF
#error "BattlePokemon volatiles read window exceeds the compiled struct size"
#endif
#if HNS_LIVE_BP_VOLATILE_ELECTRIFIED_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || HNS_LIVE_BP_VOLATILE_GLAIVE_RUSH_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || HNS_LIVE_BP_VOLATILE_MINIMIZE_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || HNS_LIVE_BP_VOLATILE_SEMI_INVULNERABLE_BIT + HNS_LIVE_BP_VOLATILE_SEMI_INVULNERABLE_WIDTH >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || HNS_LIVE_BP_VOLATILE_CHARGE_TIMER_BIT + HNS_LIVE_BP_VOLATILE_CHARGE_TIMER_WIDTH >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || HNS_LIVE_BP_VOLATILE_TAR_SHOT_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || HNS_LIVE_BP_VOLATILE_FORESIGHT_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || HNS_LIVE_BP_VOLATILE_MIRACLE_EYE_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || HNS_LIVE_BP_VOLATILE_ROOT_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || HNS_LIVE_BP_VOLATILE_SMACK_DOWN_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || HNS_LIVE_BP_VOLATILE_TELEKINESIS_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || HNS_LIVE_BP_VOLATILE_MAGNET_RISE_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || HNS_LIVE_BP_VOLATILE_GASTRO_ACID_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || HNS_LIVE_BP_VOLATILE_ROOST_ACTIVE_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || HNS_LIVE_BP_VOLATILE_SUBSTITUTE_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || HNS_LIVE_BP_VOLATILE_ENDURED_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || HNS_LIVE_BP_VOLATILE_SLOW_START_TIMER_BIT + HNS_LIVE_BP_VOLATILE_SLOW_START_TIMER_WIDTH > 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || HNS_LIVE_BP_VOLATILE_FLASH_FIRE_BOOSTED_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || HNS_LIVE_BP_VOLATILE_TRANSFORMED_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || HNS_LIVE_BP_VOLATILE_BOOSTER_ENERGY_ACTIVATED_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || HNS_LIVE_BP_VOLATILE_PARADOX_BOOSTED_STAT_BIT + HNS_LIVE_BP_VOLATILE_PARADOX_BOOSTED_STAT_WIDTH > 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || HNS_LIVE_BP_VOLATILE_VESSEL_OF_RUIN_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || HNS_LIVE_BP_VOLATILE_SWORD_OF_RUIN_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || HNS_LIVE_BP_VOLATILE_TABLETS_OF_RUIN_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || HNS_LIVE_BP_VOLATILE_BEADS_OF_RUIN_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || HNS_LIVE_BP_VOLATILE_EMBARGO_BIT >= 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || HNS_LIVE_BP_VOLATILE_METRONOME_ITEM_COUNTER_BIT + HNS_LIVE_BP_VOLATILE_METRONOME_ITEM_COUNTER_WIDTH > 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES || HNS_LIVE_BP_VOLATILE_TRANSFORMED_MON_SPECIES_BIT + HNS_LIVE_BP_VOLATILE_TRANSFORMED_MON_SPECIES_WIDTH > 8 * HNS_LIVE_BP_VOLATILE_WINDOW_BYTES
#error "an exported volatile bit exceeds the generated read window"
#endif
#if HNS_LIVE_BATTLE_GIMMICK_ACTIVE_OFFSET + HNS_LIVE_BATTLE_GIMMICK_SIDE_COUNT * HNS_LIVE_BATTLE_GIMMICK_PARTY_COUNT > 64
#error "BattleGimmickData.activeGimmick implausibly large"
#endif

#endif /* DUALDEX_HNS_LIVE_BATTLE_LAYOUT_GEN_H */
