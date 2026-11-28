/*
 * GENERATED FILE - do not edit by hand.
 *
 * Source of truth: the gamedata/gen3/ JSON configs (schemaVersion 1)
 * Generator:       tools/gamedata/generate_gamedata.py
 * Regenerate:      python3 tools/gamedata/generate_gamedata.py --write
 * Drift check:     python3 tools/gamedata/generate_gamedata.py --check
 *
 * Defines one `static const GameMemoryConfig` per supported game. Included exactly once,
 * by native/src/pokemon_reader.c, whose pokemon_get_game_config() maps GbaGameId to these
 * objects and returns NULL for GAME_UNKNOWN. Undeclared members stay zero, which every
 * reader treats as "this layout does not declare the field" (fail closed).
 */
#ifndef DUALDEX_GAMEDATA_CONFIGS_GEN_H
#define DUALDEX_GAMEDATA_CONFIGS_GEN_H

#include "pokemon_reader.h"
#include "hns_battle_pokemon_layout_gen.h"
#include "hns_field_status_gen.h"
#include "hns_live_battle_layout_gen.h"

/*
 * CONFIG_EMERALD - gamedata/gen3/emerald.json
 *
 * Evidence:
 *   - pret/pokeemerald src/pokemon.c + sym_ewram.txt: gPlayerPartyCount 0x020244E9, gPlayerParty 0x020244EC.
 *   - pokeemerald src/pokemon.c:76-77 declares gPlayerPartyCount then gEnemyPartyCount as the first two EWRAM objects.
 *   - PR #70 layout audit: docs/VANILLA_CALCULATOR_EVIDENCE.md section 7; tools/calc-goldens/audit_vanilla_layout.py.
 *
 * Notes:
 *   Policy audit: PARTY_DISCOVERY_AUTHORITATIVE_STATIC. While SaveBlock1 is allocated on the heap,
 *   live player party memory is a fixed EWRAM global and never shifts at runtime.
 *   Enemy count: always the byte immediately after the player count, never a separate
 *   gEnemyParty - 4. The previous value 0x24740 named the byte after the enemy party array, not the
 *   symbol; it was latent because pokemon_read_enemy_party_gba returns before reading a count for
 *   any layout that does not declare the full battle lifecycle gate (every vanilla title today).
 */
static const GameMemoryConfig CONFIG_EMERALD = {
    .game_id = GAME_EMERALD,
    .game_name = "Pokemon Emerald",
    .player_party_offset = 0x244EC,
    .player_party_count_offset = 0x244E9,
    .enemy_party_offset = 0x24744,
    .enemy_party_count_offset = 0x244EA,
    .battle_mons_offset = 0x24064,
    .battle_mons_size = 88,
    .battle_mons_hp_offset = 0x28,
    .battle_mons_stat_stages_offset = 0x18,
    .player_party_policy = PARTY_DISCOVERY_AUTHORITATIVE_STATIC,
    .has_evs = true,
    .has_ivs = true,
};

/*
 * CONFIG_FIRERED - gamedata/gen3/firered_rev0.json
 *
 * Evidence:
 *   - pret/pokefirered src/pokemon.c + sym_ewram.txt: gPlayerPartyCount 0x02024029, gPlayerParty 0x02024284, gEnemyParty 0x0202402C.
 *   - pokefirered src/pokemon.c:59-60 declares gPlayerPartyCount then gEnemyPartyCount as the first two EWRAM objects.
 *   - PR #70 layout audit: docs/VANILLA_CALCULATOR_EVIDENCE.md section 7; tools/calc-goldens/audit_vanilla_layout.py.
 *   - audit_vanilla_layout.py check_revision_equivalence: Rev 0 and Rev 1 place every EWRAM/IWRAM symbol at identical addresses.
 *
 * Notes:
 *   Root of the FireRed-engine family (LeafGreen, Ghost Grey, Radical Red, Unbound inherit from it).
 *   The id names Rev 0 as the reference build; Rev 1 shares this RAM layout (see evidence) and the
 *   shipped vanilla_firered profile accepts both exact hashes.
 *   Policy audit: PARTY_DISCOVERY_AUTHORITATIVE_STATIC. Offset 0x24029 is the true gPlayerPartyCount
 *   global (upstream orders gEnemyParty at 0x2402C before gPlayerParty at 0x24284), not padding.
 */
static const GameMemoryConfig CONFIG_FIRERED = {
    .game_id = GAME_FIRERED,
    .game_name = "Pokemon FireRed",
    .player_party_offset = 0x24284,
    .player_party_count_offset = 0x24029,
    .enemy_party_offset = 0x2402C,
    // gEnemyPartyCount is the byte immediately after gPlayerPartyCount (src/pokemon.c:59-60); 0x24028 is the linker's .ALIGN(4) padding byte.
    .enemy_party_count_offset = 0x2402A,
    .battle_mons_offset = 0x23F90,
    .battle_mons_size = 88,
    .battle_mons_hp_offset = 0x28,
    .battle_mons_stat_stages_offset = 0x18,
    .player_party_policy = PARTY_DISCOVERY_AUTHORITATIVE_STATIC,
    .has_evs = true,
    .has_ivs = true,
};

/*
 * CONFIG_LEAFGREEN - gamedata/gen3/leafgreen.json
 * Inherits: firered_rev0.json
 *
 * Evidence:
 *   - Same FireRed/LeafGreen engine layout as firered_rev0 (pokefirered builds both from one source, src/pokemon.c:59-60).
 *   - Values migrated unchanged from native/src/pokemon_reader.c CONFIG_LEAFGREEN at 3d0a0e4.
 *
 * Notes:
 *   No exact LeafGreen symbol or runtime evidence is recorded, so party discovery stays heuristic.
 */
static const GameMemoryConfig CONFIG_LEAFGREEN = {
    .game_id = GAME_LEAFGREEN,
    .game_name = "Pokemon LeafGreen",
    .player_party_offset = 0x24284,
    .player_party_count_offset = 0x24029,
    .enemy_party_offset = 0x2402C,
    // gEnemyPartyCount is the byte immediately after gPlayerPartyCount (src/pokemon.c:59-60); 0x24028 is the linker's .ALIGN(4) padding byte.
    .enemy_party_count_offset = 0x2402A,
    .battle_mons_offset = 0x23F90,
    .battle_mons_size = 88,
    .battle_mons_hp_offset = 0x28,
    .battle_mons_stat_stages_offset = 0x18,
    .player_party_policy = PARTY_DISCOVERY_HEURISTIC,
    .has_evs = true,
    .has_ivs = true,
};

/*
 * CONFIG_RUBY - gamedata/gen3/ruby.json
 *
 * Evidence:
 *   - Values migrated unchanged from native/src/pokemon_reader.c CONFIG_RUBY at 3d0a0e4; no symbol or runtime evidence recorded.
 *
 * Validation waivers (gamedata/gen3 consistency checks deliberately waived):
 *   - party-count-inside-party: Legacy unverified value preserved bit-for-bit: 0x246E4 is the last word of the 600-byte window starting at player_party_offset 0x24490. No Ruby/Sapphire symbol audit exists; resolve with symbol evidence before promoting this layout.
 *
 * Notes:
 *   Root of the Ruby/Sapphire family. No gBattleMons or battle lifecycle symbols are declared, so
 *   battle readers fail closed; party discovery is heuristic.
 */
static const GameMemoryConfig CONFIG_RUBY = {
    .game_id = GAME_RUBY,
    .game_name = "Pokemon Ruby",
    .player_party_offset = 0x24490,
    .player_party_count_offset = 0x2448C,
    .enemy_party_offset = 0x246E8,
    .enemy_party_count_offset = 0x246E4,
    .player_party_policy = PARTY_DISCOVERY_HEURISTIC,
    .has_evs = true,
    .has_ivs = true,
};

/*
 * CONFIG_SAPPHIRE - gamedata/gen3/sapphire.json
 * Inherits: ruby.json
 *
 * Evidence:
 *   - Ruby and Sapphire share one engine build layout; values migrated unchanged from native/src/pokemon_reader.c CONFIG_SAPPHIRE at 3d0a0e4.
 *
 * Validation waivers (gamedata/gen3 consistency checks deliberately waived):
 *   - party-count-inside-party: Legacy unverified value preserved bit-for-bit: 0x246E4 is the last word of the 600-byte window starting at player_party_offset 0x24490. No Ruby/Sapphire symbol audit exists; resolve with symbol evidence before promoting this layout.
 */
static const GameMemoryConfig CONFIG_SAPPHIRE = {
    .game_id = GAME_SAPPHIRE,
    .game_name = "Pokemon Sapphire",
    .player_party_offset = 0x24490,
    .player_party_count_offset = 0x2448C,
    .enemy_party_offset = 0x246E8,
    .enemy_party_count_offset = 0x246E4,
    .player_party_policy = PARTY_DISCOVERY_HEURISTIC,
    .has_evs = true,
    .has_ivs = true,
};

/*
 * CONFIG_GHOST_GREY - gamedata/gen3/ghost_grey.json
 * Inherits: firered_rev0.json
 *
 * Evidence:
 *   - Values migrated unchanged from native/src/pokemon_reader.c CONFIG_GHOST_GREY at 3d0a0e4; no symbol or runtime evidence recorded.
 *   - app/src/main/assets/profiles/ghost_grey.json declares a HexManiacAdvance FireRed binary hack with no exact hash.
 *
 * Notes:
 *   In-place FireRed binary hack: inherits the FireRed party addresses but declares no gBattleMons,
 *   so battle readers fail closed. The enemy count keeps the legacy 0x24028 value: the PR #70
 *   correction to 0x2402A was applied only to layouts with symbol evidence.
 */
static const GameMemoryConfig CONFIG_GHOST_GREY = {
    .game_id = GAME_GHOST_GREY,
    .game_name = "Pokemon Ghost Grey",
    .player_party_offset = 0x24284,
    .player_party_count_offset = 0x24029,
    .enemy_party_offset = 0x2402C,
    .enemy_party_count_offset = 0x24028,
    .player_party_policy = PARTY_DISCOVERY_HEURISTIC,
    // Ghost Grey removes EVs
    .has_evs = false,
    // Ghost Grey removes IVs
    .has_ivs = false,
};

/*
 * CONFIG_RADICAL_RED - gamedata/gen3/radical_red.json
 * Inherits: firered_rev0.json
 *
 * Evidence:
 *   - Values migrated unchanged from native/src/pokemon_reader.c CONFIG_RADICAL_RED at 3d0a0e4; no symbol or runtime evidence recorded.
 *   - app/src/main/assets/profiles/radical_red.json declares a CFRU FireRed hack with no exact hash.
 *
 * Notes:
 *   CFRU hack without exact-ROM evidence: storage is PKMN_STORAGE_CFRU_UNVERIFIED (never the exact
 *   Unbound semantics), no gBattleMons is declared and the enemy count keeps the legacy 0x24028 value.
 */
static const GameMemoryConfig CONFIG_RADICAL_RED = {
    .game_id = GAME_RADICAL_RED,
    .game_name = "Pokemon Radical Red",
    .player_party_offset = 0x24284,
    .player_party_count_offset = 0x24029,
    .enemy_party_offset = 0x2402C,
    .enemy_party_count_offset = 0x24028,
    .storage_layout = PKMN_STORAGE_CFRU_UNVERIFIED,
    .player_party_policy = PARTY_DISCOVERY_HEURISTIC,
    .has_evs = true,
    .has_ivs = true,
};

/*
 * CONFIG_HEART_AND_SOUL - gamedata/gen3/heart_and_soul_2_0_5.json
 *
 * Evidence:
 *   - Symbol table of the exact upstream build: PokemonHnS-Development/pokehns-expansion tag Release-v2.0.5 @ 1f42b74dff0e9fe942419845d040663dd829a973.
 *   - Build: make -j hns TOOLCHAIN=<arm-gnu-toolchain-13.2.rel1> (+ make BUILD=hns syms); ARM7TDMI, -mabi=apcs-gnu, -mthumb -mthumb-interop, -O2.
 *   - Official release ROM SHA-256 edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b; party group, gMain and IWRAM pointers runtime-verified in the bundled mGBA core.
 *   - docs/HNS_2_0_5_COMPATIBILITY_EVIDENCE.md sections 11.5, 11.6 and 14.
 *   - BattlePokemon / live battle ABI: native/src/hns_battle_pokemon_layout_gen.h and hns_live_battle_layout_gen.h (generated from the pinned source).
 *
 * Notes:
 *   Deliberately a root config (no base): every value is pinned from this exact build, and inheriting
 *   vanilla Emerald values would let an Emerald edit silently change an exact-build layout.
 *
 *   Symbol                     absolute GBA address   size
 *     gBattleTypeFlags         0x020000AC             4
 *     gBattlersCount           0x020000B0             1
 *     gBattleOutcome           0x0200012C             1
 *     gBattlerPartyIndexes     0x02000144             8
 *     gBattlerPositions        0x02000238             4
 *     gAbsentBattlerFlags      0x0200030A             1
 *     gBattleControllerExec... 0x020002F4             4
 *     gBattleMons              0x02000420             0x220 (4 battlers x 136 bytes)
 *     gSaveblock3              0x0200921C             0x34 (source build; release ROM 0x02009218)
 *     gSaveblock1              0x020124A8             0x3E10 (15760-byte block + 128-byte window)
 *     gSaveBlock1Ptr           0x030041D8             4   (IWRAM; release ROM value, see below)
 *     gSaveBlock3Ptr           0x03000178             4   (IWRAM)
 *     gMain                    0x03005BD8             0x43C (IWRAM)
 *
 *   Release-ROM party group (used below): gPlayerPartyCount 0x020342A4, gEnemyPartyCount 0x020342A5,
 *   gEnemyParty 0x020342B4, gPlayerParty 0x02034764. A from-source make hns build reports the party
 *   group 4 bytes higher and gMain 0x18 lower; reading the release ROM at those compiled addresses
 *   yields a shifted party, an empty enemy party and a permanently-clear in-battle flag.
 *
 *   IWRAM note: a from-source build places gSaveBlock1Ptr at 0x030041C0, but the official 2.0.5
 *   release binary uses 0x030041D8: gSaveBlock1Ptr, gSaveBlock2Ptr and gPokemonStoragePtr were found
 *   as three adjacent words holding base+88 for the compiled gSaveblock1/gSaveblock2/gPokemonStorage
 *   EWRAM bases, pinning the IWRAM triple at 0x030041D4/0x030041D8/0x030041DC.
 *
 *   Struct layout (DWARF-probed with the same headers and flags, not host sizeof): BoxPokemon 80,
 *   Pokemon 100, BattlePokemon 136, ChallengeSettings 32, SaveBlock1 15760. Coords16 is 4-aligned
 *   under -mabi=apcs-gnu, so SaveBlock1.pos is at 0x04 because 2.0.5 added u16 saveVersion at 0x00.
 *   BattlePokemon: statStages 0x18, ability 0x20, types 0x22, pp 0x25, hp 0x2A, level 0x2C,
 *   maxHP 0x2E, item 0x30, status1 0x50.
 *
 *   gMain.inBattle is set by CB2_InitBattleInternal() once battle data is prepared and cleared by
 *   ReturnFromBattleToOverworld()/FreeRestoreBattleData(), so it is the authoritative "the engine
 *   owns a battle" signal that gBattleMons[0].species is not.
 *
 *   The previous H&S offsets were stale (they matched neither the tagged source nor the compiled
 *   image), and gBattlerPartyIndexes was being read as gBattleMons - 24, wrong by 732 bytes on 2.0.5.
 */
static const GameMemoryConfig CONFIG_HEART_AND_SOUL = {
    .game_id = GAME_HEART_AND_SOUL,
    .game_name = "Pokemon Heart & Soul",
    .player_party_offset = 0x34764,
    .player_party_count_offset = 0x342A4,
    .enemy_party_offset = 0x342B4,
    .enemy_party_count_offset = 0x342A5,
    .battle_mons_offset = 0x420,
    .battle_mons_size = 136,
    .battle_mons_hp_offset = 0x2A,
    .battle_mons_stat_stages_offset = 0x18,
    // Live BattlePokemon observation: generated ABI table macros, so the production layout and the source-check evidence cannot drift apart without failing to compile (cross-checked at runtime by battle_pokemon_layout_matches_pinned_abi).
    .battle_mons_ability_offset = HNS_BATTLE_POKEMON_ABILITY_OFFSET,
    .battle_mons_ability_size = HNS_BATTLE_POKEMON_ABILITY_SIZE,
    .battle_mons_types_offset = HNS_BATTLE_POKEMON_TYPES_OFFSET,
    .battle_mons_type_count = HNS_BATTLE_POKEMON_TYPE_COUNT,
    .battle_mons_type_width = HNS_BATTLE_POKEMON_TYPE_ELEMENT_SIZE,
    .battle_mons_item_offset = HNS_BATTLE_POKEMON_ITEM_OFFSET,
    .battle_mons_item_size = HNS_BATTLE_POKEMON_ITEM_SIZE,
    .battle_mons_attack_offset = HNS_BATTLE_POKEMON_ATTACK_OFFSET,
    .battle_mons_attack_size = HNS_BATTLE_POKEMON_ATTACK_SIZE,
    .battle_mons_defense_offset = HNS_BATTLE_POKEMON_DEFENSE_OFFSET,
    .battle_mons_defense_size = HNS_BATTLE_POKEMON_DEFENSE_SIZE,
    .battle_mons_speed_offset = HNS_BATTLE_POKEMON_SPEED_OFFSET,
    .battle_mons_speed_size = HNS_BATTLE_POKEMON_SPEED_SIZE,
    .battle_mons_spattack_offset = HNS_BATTLE_POKEMON_SPATTACK_OFFSET,
    .battle_mons_spattack_size = HNS_BATTLE_POKEMON_SPATTACK_SIZE,
    .battle_mons_spdefense_offset = HNS_BATTLE_POKEMON_SPDEFENSE_OFFSET,
    .battle_mons_spdefense_size = HNS_BATTLE_POKEMON_SPDEFENSE_SIZE,
    // Gap C4e live damage operands, all from the generated live-battle ABI table.
    .battle_mons_max_hp_offset = HNS_LIVE_BP_MAX_HP_OFFSET,
    .battle_mons_status_offset = HNS_LIVE_BP_STATUS_OFFSET,
    .battle_mons_personality_offset = HNS_LIVE_BP_PERSONALITY_OFFSET,
    .battle_mons_personality_size = HNS_LIVE_BP_PERSONALITY_SIZE,
    .battle_mons_volatiles_offset = HNS_LIVE_BP_VOLATILES_OFFSET,
    .battle_mons_volatile_electrified_bit = HNS_LIVE_BP_VOLATILE_ELECTRIFIED_BIT,
    .battle_mons_volatile_glaive_rush_bit = HNS_LIVE_BP_VOLATILE_GLAIVE_RUSH_BIT,
    .battle_mons_volatile_minimize_bit = HNS_LIVE_BP_VOLATILE_MINIMIZE_BIT,
    .battle_mons_volatile_semi_invulnerable_bit = HNS_LIVE_BP_VOLATILE_SEMI_INVULNERABLE_BIT,
    .battle_mons_volatile_semi_invulnerable_width = HNS_LIVE_BP_VOLATILE_SEMI_INVULNERABLE_WIDTH,
    .battle_mons_volatile_charge_timer_bit = HNS_LIVE_BP_VOLATILE_CHARGE_TIMER_BIT,
    .battle_mons_volatile_charge_timer_width = HNS_LIVE_BP_VOLATILE_CHARGE_TIMER_WIDTH,
    .battle_mons_volatile_tar_shot_bit = HNS_LIVE_BP_VOLATILE_TAR_SHOT_BIT,
    .battle_mons_volatile_foresight_bit = HNS_LIVE_BP_VOLATILE_FORESIGHT_BIT,
    .battle_mons_volatile_miracle_eye_bit = HNS_LIVE_BP_VOLATILE_MIRACLE_EYE_BIT,
    .battle_mons_volatile_root_bit = HNS_LIVE_BP_VOLATILE_ROOT_BIT,
    .battle_mons_volatile_smack_down_bit = HNS_LIVE_BP_VOLATILE_SMACK_DOWN_BIT,
    .battle_mons_volatile_telekinesis_bit = HNS_LIVE_BP_VOLATILE_TELEKINESIS_BIT,
    .battle_mons_volatile_magnet_rise_bit = HNS_LIVE_BP_VOLATILE_MAGNET_RISE_BIT,
    .battle_mons_volatile_gastro_acid_bit = HNS_LIVE_BP_VOLATILE_GASTRO_ACID_BIT,
    .battle_mons_volatile_roost_active_bit = HNS_LIVE_BP_VOLATILE_ROOST_ACTIVE_BIT,
    .battle_mons_volatile_substitute_bit = HNS_LIVE_BP_VOLATILE_SUBSTITUTE_BIT,
    .battle_mons_volatile_endured_bit = HNS_LIVE_BP_VOLATILE_ENDURED_BIT,
    .battle_mons_volatile_slow_start_timer_bit = HNS_LIVE_BP_VOLATILE_SLOW_START_TIMER_BIT,
    .battle_mons_volatile_slow_start_timer_width = HNS_LIVE_BP_VOLATILE_SLOW_START_TIMER_WIDTH,
    .battle_mons_volatile_flash_fire_boosted_bit = HNS_LIVE_BP_VOLATILE_FLASH_FIRE_BOOSTED_BIT,
    .battle_mons_volatile_transformed_bit = HNS_LIVE_BP_VOLATILE_TRANSFORMED_BIT,
    .battle_mons_volatile_booster_energy_activated_bit = HNS_LIVE_BP_VOLATILE_BOOSTER_ENERGY_ACTIVATED_BIT,
    .battle_mons_volatile_paradox_boosted_stat_bit = HNS_LIVE_BP_VOLATILE_PARADOX_BOOSTED_STAT_BIT,
    .battle_mons_volatile_paradox_boosted_stat_width = HNS_LIVE_BP_VOLATILE_PARADOX_BOOSTED_STAT_WIDTH,
    .battle_mons_volatile_vessel_of_ruin_bit = HNS_LIVE_BP_VOLATILE_VESSEL_OF_RUIN_BIT,
    .battle_mons_volatile_sword_of_ruin_bit = HNS_LIVE_BP_VOLATILE_SWORD_OF_RUIN_BIT,
    .battle_mons_volatile_tablets_of_ruin_bit = HNS_LIVE_BP_VOLATILE_TABLETS_OF_RUIN_BIT,
    .battle_mons_volatile_beads_of_ruin_bit = HNS_LIVE_BP_VOLATILE_BEADS_OF_RUIN_BIT,
    .battle_mons_volatile_embargo_bit = HNS_LIVE_BP_VOLATILE_EMBARGO_BIT,
    .battle_mons_volatile_metronome_item_counter_bit = HNS_LIVE_BP_VOLATILE_METRONOME_ITEM_COUNTER_BIT,
    .battle_mons_volatile_metronome_item_counter_width = HNS_LIVE_BP_VOLATILE_METRONOME_ITEM_COUNTER_WIDTH,
    .battle_mons_volatile_transformed_mon_species_bit = HNS_LIVE_BP_VOLATILE_TRANSFORMED_MON_SPECIES_BIT,
    .battle_mons_volatile_transformed_mon_species_width = HNS_LIVE_BP_VOLATILE_TRANSFORMED_MON_SPECIES_WIDTH,
    .save_block1_flags_offset = 0x198C,
    .save_block1_badges_offset = 0x1A98,
    .battler_party_indexes_offset = 0x144,
    .battlers_count_offset = 0xB0,
    .battle_type_flags_offset = 0xAC,
    .battle_outcome_offset = 0x12C,
    .battler_positions_offset = 0x238,
    .absent_battler_flags_offset = 0x30A,
    // gFieldStatuses is 0x020002E8 on the official release ROM (artifacts-default/pokehns.map); 0x020002F4 there is gBattleControllerExecFlags. Runtime transitions via runtime_battle_probe verify 0x2E8.
    .field_statuses_offset = 0x2E8,
    // Generated from the pinned battle.h.
    .field_status_ion_deluge_mask = HNS_STATUS_FIELD_ION_DELUGE,
    // Gap C4e: gBattleWeather 0x02000390 and gSideStatuses 0x02000324 in pokehns-release.elf and the from-source build; runtime-verified (compatibility evidence section 14).
    .battle_weather_offset = 0x390,
    .side_statuses_offset = 0x324,
    // sizeof(u32)
    .side_statuses_stride = 4,
    // SIDE_STATUS_REFLECT (1u << 0)
    .side_status_reflect_mask = 0x1,
    // SIDE_STATUS_LIGHTSCREEN (1u << 1)
    .side_status_light_screen_mask = 0x2,
    // gBattleStruct is a POINTER to the heap-allocated battle struct, read afresh each observation and required to point inside EWRAM before any byte is dereferenced.
    .battle_struct_ptr_offset = 0xB4,
    .battle_struct_gimmick_offset = HNS_LIVE_BATTLE_STRUCT_GIMMICK_OFFSET,
    .battle_struct_event_state_offset = HNS_LIVE_BATTLE_STRUCT_EVENT_STATE_OFFSET,
    .event_state_switch_in_bit = HNS_LIVE_EVENT_STATE_SWITCH_IN_BIT,
    .event_state_switch_in_width = HNS_LIVE_EVENT_STATE_SWITCH_IN_WIDTH,
    .battle_struct_battler_state_offset = HNS_LIVE_BATTLE_STRUCT_BATTLER_STATE_OFFSET,
    .battler_state_size = HNS_LIVE_BATTLER_STATE_SIZE,
    .battler_state_switch_in_bit = HNS_LIVE_BATTLER_STATE_SWITCH_IN_BIT,
    .battler_state_is_first_turn_bit = HNS_LIVE_BATTLER_STATE_IS_FIRST_TURN_BIT,
    .battler_state_is_first_turn_width = HNS_LIVE_BATTLER_STATE_IS_FIRST_TURN_WIDTH,
    .battle_gimmick_usable_offset = HNS_LIVE_BATTLE_GIMMICK_USABLE_OFFSET,
    .battle_gimmick_player_select_offset = HNS_LIVE_BATTLE_GIMMICK_PLAYER_SELECT_OFFSET,
    .battle_struct_supreme_overlord_counter_offset = HNS_LIVE_BATTLE_STRUCT_SUPREME_OVERLORD_COUNTER_OFFSET,
    .supreme_overlord_counter_stride = HNS_LIVE_SUPREME_OVERLORD_COUNTER_STRIDE,
    .supreme_overlord_counter_count = HNS_LIVE_SUPREME_OVERLORD_COUNTER_COUNT,
    .battle_gimmick_dynamax_value = HNS_LIVE_GIMMICK_DYNAMAX_VALUE,
    .battle_action_use_move_value = HNS_LIVE_B_ACTION_USE_MOVE,
    .battle_action_exec_script_value = HNS_LIVE_B_ACTION_EXEC_SCRIPT,
    .battle_current_move_gba_address = HNS_LIVE_GCURRENTMOVE_GBA_ADDRESS,
    .run_turn_actions_func_ptr = HNS_LIVE_RUN_TURN_ACTIONS_FUNC_PTR,
    .battle_main_cb1_func_ptr = HNS_LIVE_BATTLE_MAIN_CB1_FUNC_PTR,
    .battle_main_cb1_offset = HNS_LIVE_MAIN_CALLBACK1_OFFSET,
    .battle_turn_action_number_gba_address = HNS_LIVE_GCURRENTTURNACTIONNUMBER_GBA_ADDRESS,
    .battle_actions_by_turn_order_gba_address = HNS_LIVE_GACTIONSBYTURNORDER_GBA_ADDRESS,
    .battle_battler_by_turn_order_gba_address = HNS_LIVE_GBATTLERBYTURNORDER_GBA_ADDRESS,
    .battle_current_action_func_id_gba_address = HNS_LIVE_GCURRENTACTIONFUNCID_GBA_ADDRESS,
    .battle_battler_attacker_gba_address = HNS_LIVE_GBATTLERATTACKER_GBA_ADDRESS,
    .battle_battlers_count_gba_address = HNS_LIVE_GBATTLERSCOUNT_GBA_ADDRESS,
    .max_battlers_count = HNS_LIVE_MAX_BATTLERS_COUNT,
    .switch_in_events_count = HNS_LIVE_SWITCH_IN_EVENTS_COUNT,
    .battle_main_func_gba_address = HNS_LIVE_BATTLE_MAIN_FUNC_GBA_ADDRESS,
    .action_selection_func_ptr = HNS_LIVE_ACTION_SELECTION_FUNC_PTR,
    .battle_gimmick_active_offset = HNS_LIVE_BATTLE_GIMMICK_ACTIVE_OFFSET,
    .battle_gimmick_side_stride = HNS_LIVE_BATTLE_GIMMICK_PARTY_COUNT,
    .battle_gimmick_party_count = HNS_LIVE_BATTLE_GIMMICK_PARTY_COUNT,
    .battle_gimmick_count = HNS_LIVE_BATTLE_GIMMICK_COUNT,
    .battle_struct_party_state_offset = HNS_LIVE_BATTLE_STRUCT_PARTY_STATE_OFFSET,
    .party_state_entry_size = HNS_LIVE_PARTY_STATE_SIZE,
    .party_state_side_stride = HNS_LIVE_BATTLE_PARTY_STATE_SIDE_STRIDE,
    .party_state_side_count = HNS_LIVE_BATTLE_PARTY_STATE_SIDE_COUNT,
    .party_state_party_count = HNS_LIVE_BATTLE_PARTY_STATE_PARTY_COUNT,
    .party_state_ate_berry_bit = HNS_LIVE_PARTY_STATE_ATE_BERRY_BIT,
    // gMain (IWRAM, 0x43C bytes); inBattle is in the byte at +0x439 (DWARF DW_AT_data_bit_offset 8649).
    .main_struct_gba_address = 0x03005BD8,
    .main_in_battle_byte_offset = 0x439,
    .main_in_battle_bit = 1,
    .save_block1_ptr_gba_address = 0x030041D8,
    // Compiled gSaveblock1 0x020124A8; the 128-byte window covers the ASLR offsets.
    .save_block1_base_gba_address = 0x020124A8,
    .save_block1_aslr_range = 128,
    .save_block1_size = 15760,
    .save_block1_pos_offset = 0x4,
    .save_block1_location_offset = 0x8,
    .save_block1_escape_warp_offset = 0x28,
    // SaveBlock3 (ChallengeSettings): gSaveBlock3Ptr runtime-verified at 0x03000178 holding 0x02009218 (4 below the from-source 0x0200921C). The reader requires the pointer VALUE to equal this base exactly.
    .save_block3_ptr_gba_address = 0x03000178,
    .save_block3_base_gba_address = 0x02009218,
    .storage_layout = PKMN_STORAGE_EXPANSION,
    .player_party_policy = PARTY_DISCOVERY_AUTHORITATIVE_STATIC,
    .has_evs = true,
    .has_ivs = true,
};

/*
 * CONFIG_UNBOUND - gamedata/gen3/unbound.json
 * Inherits: firered_rev0.json
 *
 * Evidence:
 *   - Party: exact-ROM getters and observed count/base/100-byte stride, docs/UNBOUND_PARTY_LAYOUT_EVIDENCE.md.
 *   - Battle globals: exact-candidate globals, docs/UNBOUND_BATTLE_TRAINER_EVIDENCE.md (Production implications).
 *   - Battle lifecycle and BattlePokemon layout: docs/UNBOUND_BATTLE_LIFECYCLE_EVIDENCE.md.
 *   - SaveBlock1: fixed base, the pointer must match exactly, docs/UNBOUND_LOCATION_EVIDENCE.md.
 *   - Exact ROM SHA-256 7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7 (app/src/main/assets/profiles/unbound.json).
 *
 * Notes:
 *   CFRU on FireRed: player party, party counts, BattlePokemon size (0x58), hp (+0x28) and statStages
 *   (+0x18) are inherited unchanged from firered_rev0. Unbound's +0x18 is type3; stages 1..7 are at
 *   +0x19..0x1F. The enemy count byte stays zero; only mapped opponent battlers establish occupancy.
 */
static const GameMemoryConfig CONFIG_UNBOUND = {
    .game_id = GAME_UNBOUND,
    .game_name = "Pokemon Unbound",
    .player_party_offset = 0x24284,
    .player_party_count_offset = 0x24029,
    .enemy_party_offset = 0x2402C,
    // gEnemyPartyCount is the byte immediately after gPlayerPartyCount (src/pokemon.c:59-60); 0x24028 is the linker's .ALIGN(4) padding byte.
    .enemy_party_count_offset = 0x2402A,
    .battle_mons_offset = 0x23BE4,
    .battle_mons_size = 88,
    .battle_mons_hp_offset = 0x28,
    .battle_mons_stat_stages_offset = 0x18,
    .battler_party_indexes_offset = 0x23BCE,
    .battlers_count_offset = 0x23BCC,
    .battle_type_flags_offset = 0x22B4C,
    .battle_outcome_offset = 0x23E8A,
    .battler_positions_offset = 0x23BD6,
    .absent_battler_flags_offset = 0x23D70,
    .main_struct_gba_address = 0x030030F0,
    .main_in_battle_byte_offset = 0x439,
    .main_in_battle_bit = 1,
    .save_block1_ptr_gba_address = 0x03005008,
    .save_block1_base_gba_address = 0x0202552C,
    .save_block1_aslr_range = 4,
    // CFRU/FireRed extent for bounds only; not runtime-verified.
    .save_block1_size = 15720,
    .save_block1_pos_offset = 0x0,
    .save_block1_location_offset = 0x4,
    .save_block1_escape_warp_offset = 0x24,
    .location_max_map_group = 56,
    .storage_layout = PKMN_STORAGE_CFRU,
    .player_party_policy = PARTY_DISCOVERY_AUTHORITATIVE_STATIC,
    .has_evs = true,
    .has_ivs = true,
};

#endif /* DUALDEX_GAMEDATA_CONFIGS_GEN_H */
