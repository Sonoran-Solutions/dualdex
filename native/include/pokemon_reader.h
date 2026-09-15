#ifndef DUALDEX_POKEMON_READER_H
#define DUALDEX_POKEMON_READER_H

#include "pokemon_structs.h"
#include <stddef.h>
#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Supported Game Identifiers
 */
typedef enum {
    GAME_UNKNOWN = 0,
    GAME_EMERALD,
    GAME_FIRERED,
    GAME_LEAFGREEN,
    GAME_RUBY,
    GAME_SAPPHIRE,
    GAME_GHOST_GREY,     // Custom FireRed binary hack
    GAME_RADICAL_RED,    // CFRU FireRed hack
    GAME_HEART_AND_SOUL, // pokeemerald-expansion Heart & Soul 2.0
    GAME_UNBOUND         // CFRU FireRed hack (Pokemon Unbound)
} GbaGameId;

/**
 * Policy for discovering the active player party in EWRAM.
 */
typedef enum {
    PARTY_DISCOVERY_HEURISTIC = 0,            // Legacy/hack discovery: static candidate -> cached candidate -> pattern scan
    PARTY_DISCOVERY_AUTHORITATIVE_STATIC = 1  // Exact symbol evidence: authoritative count bounds and validates static party; fail-closed, no scan
} PartyDiscoveryPolicy;

/**
 * Game memory offset configuration.
 *
 * Unit discipline, stated once and never mixed implicitly:
 *   - every `*_offset` field is relative to the start of the EWRAM buffer
 *     (absolute GBA address 0x02000000) unless its name says otherwise;
 *   - `save_block1_ptr_gba_address` is an ABSOLUTE GBA address, because the active SaveBlock1
 *     pointer lives in IWRAM (0x03000000...) while SaveBlock1 itself lives in EWRAM;
 *   - `save_block1_*_offset` are offsets relative to the resolved SaveBlock1 base;
 *   - `save_block1_aslr_range` / `save_block1_size` are byte counts.
 *
 * A zero value means "this layout does not declare the field", which is how a
 * GAME_UNKNOWN or an unsupported hack fails closed instead of inheriting another game's
 * addresses.
 */
typedef struct {
    GbaGameId game_id;
    const char* game_name;
    uint32_t player_party_offset;      // EWRAM-relative
    uint32_t player_party_count_offset;// EWRAM-relative
    uint32_t enemy_party_offset;       // EWRAM-relative (gEnemyParty)
    uint32_t enemy_party_count_offset; // EWRAM-relative (gEnemyPartyCount; never derived from
                                       // enemy_party_offset, which is a separate symbol)
    uint32_t battle_mons_offset;       // EWRAM-relative for gBattleMons
    uint32_t battle_mons_size;         // sizeof(struct BattlePokemon)
    uint32_t battle_mons_hp_offset;    // offset of hp within struct BattlePokemon
    uint32_t battle_mons_stat_stages_offset; // offset of statStages within struct BattlePokemon
    uint32_t battler_party_indexes_offset;   // EWRAM-relative gBattlerPartyIndexes, 0 = unavailable
    uint32_t battlers_count_offset;          // EWRAM-relative gBattlersCount, 0 = unavailable
    uint32_t battle_type_flags_offset;       // EWRAM-relative gBattleTypeFlags, 0 = unavailable
    uint32_t battle_outcome_offset;          // EWRAM-relative gBattleOutcome, 0 = unavailable

    // SaveBlock1 resolution. When `save_block1_ptr_gba_address` is non-zero the active base is
    // read from that absolute GBA address (IWRAM) on every call; a zero value selects the legacy
    // "derive from the party offset" behaviour used by the vanilla games.
    uint32_t save_block1_ptr_gba_address;    // absolute GBA address, 0 = legacy derivation
    uint32_t save_block1_base_gba_address;   // absolute GBA address of the ASLR storage block
    uint32_t save_block1_aslr_range;         // size of the randomized window, 0 = no window
    uint32_t save_block1_size;               // sizeof(struct SaveBlock1)
    uint32_t save_block1_pos_offset;         // struct-relative offset of SaveBlock1.pos
    uint32_t save_block1_location_offset;    // struct-relative offset of SaveBlock1.location
    uint32_t save_block1_escape_warp_offset; // struct-relative offset of SaveBlock1.escapeWarp

    PokemonStorageLayout storage_layout;     // which BoxPokemon bit layout to parse
    PartyDiscoveryPolicy player_party_policy;// Discovery policy: authoritative static vs heuristic

    bool     has_evs;                  // False for Ghost Grey
    bool     has_ivs;                  // False for Ghost Grey
} GameMemoryConfig;

/**
 * Bounds-checked reader for an absolute emulated GBA address.
 *
 * Implementations must return false rather than perform an unchecked access, so a reader built
 * on top of this can never be handed memory outside a verified region.
 */
typedef bool (*DualDexGbaReadFn)(void* user, uint32_t gba_address, uint8_t* out, size_t length);

/**
 * Get nature name string from index (0 - 24).
 */
const char* pokemon_get_nature_name(uint8_t nature_index);

/**
 * Decrypt and parse a single 100-byte GBA Pokémon structure using the vanilla Gen 3 layout.
 *
 * @param raw_bytes Pointer to 100 bytes of Pokémon data (or 80 for PC box)
 * @param is_party_mon True if full 100 bytes (party), false if 80 bytes (box)
 * @param out_pokemon Pointer to ParsedPokemon destination struct
 * @return True if parsing succeeded and checksum is valid
 */
bool pokemon_parse_single(const uint8_t* raw_bytes, bool is_party_mon, ParsedPokemon* out_pokemon);

/**
 * Decrypt and parse a single 100-byte GBA Pokémon structure with an explicit storage layout.
 *
 * The vanilla entry point above is exactly this function with PKMN_STORAGE_VANILLA_GEN3, so
 * FireRed/Emerald parsing is unchanged. The expansion layout changes only how the ability slot,
 * the mint nature and the shiny verdict are derived; species/move/item/IV extraction is shared
 * because H&S 2.0.5 uses the same widened fields DualDex already reads.
 */
bool pokemon_parse_single_layout(
    const uint8_t* raw_bytes,
    bool is_party_mon,
    PokemonStorageLayout storage_layout,
    ParsedPokemon* out_pokemon
);

/**
 * Detect game version from a 16-byte ROM header title string (at ROM offset 0xA0).
 */
GbaGameId pokemon_detect_game(const char* rom_title_16);

/**
 * Reset cached party memory offsets (e.g. on ROM load or core reset).
 */
void pokemon_reader_reset(void);

/**
 * Get memory configuration for a detected game.
 *
 * @return The configuration for a supported game, or NULL when the game is unknown/unsupported.
 *
 * Fail-closed contract: there is no FireRed (or any other) fallback. A caller that receives NULL
 * must not parse memory; every reader in this header also rejects an unusable configuration on its
 * own, so an unknown ROM can never be interpreted through an arbitrary supported layout.
 */
const GameMemoryConfig* pokemon_get_game_config(GbaGameId game_id);

/**
 * Parse the active player party from a 256 KB EWRAM buffer.
 *
 * Fails closed: returns 0 with a zeroed snapshot when @p config is NULL or describes
 * GAME_UNKNOWN.
 *
 * When @p config->player_party_policy is PARTY_DISCOVERY_AUTHORITATIVE_STATIC, the reader
 * treats player_party_count_offset as an authoritative bounds check. If the count is 0,
 * it returns 0 immediately and clears any cached offset without scanning EWRAM. If the
 * count is 1..6, exactly those slots are parsed at player_party_offset; any invalid slot
 * fails closed (all-or-nothing, no scan). A count > 6 fails closed.
 *
 * When @p config->player_party_policy is PARTY_DISCOVERY_HEURISTIC, the configured-offset path,
 * the cached-offset path, and the blind EWRAM scan are used as fallbacks for layouts lacking
 * exact symbol authority.
 *
 * @param ewram Pointer to the 256 KB EWRAM memory block (base 0x02000000)
 * @param ewram_size Size of EWRAM buffer (typically 262144 bytes)
 * @param config Game configuration defining memory offsets
 * @param out_snapshot Pointer to PartySnapshot destination struct
 * @return Number of valid Pokémon parsed into the snapshot (0 when empty or the layout is unusable)
 */
uint8_t pokemon_read_player_party(
    const uint8_t* ewram,
    size_t ewram_size,
    const GameMemoryConfig* config,
    PartySnapshot* out_snapshot
);

/**
 * Parse enemy/opponent party from EWRAM during a battle.
 *
 * Fails closed: returns 0 with a zeroed snapshot when @p config is NULL or describes GAME_UNKNOWN.
 *
 * @param ewram Pointer to EWRAM
 * @param ewram_size Size of EWRAM
 * @param config Game configuration
 * @param out_snapshot Destination struct
 * @return Number of valid enemy Pokémon parsed (0 when the layout is unusable)
 */
uint8_t pokemon_read_enemy_party(
    const uint8_t* ewram,
    size_t ewram_size,
    const GameMemoryConfig* config,
    PartySnapshot* out_snapshot
);

/**
 * Dynamically scan the entire 256 KB EWRAM for the active party.
 * Essential for pokeemerald-expansion (Heart & Soul) and ROM hacks
 * where gPlayerParty is placed at non-standard memory addresses.
 *
 * Layout-independent, but NOT a trust decision: only call this for a game whose layout was
 * verified (i.e. from pokemon_read_player_party, which rejects unknown games first).
 */
uint8_t pokemon_scan_ewram_for_party(
    const uint8_t* ewram,
    size_t ewram_size,
    PartySnapshot* out_snapshot
);

typedef struct {
    int16_t map_group;
    int16_t map_num;
    int8_t  warp_id;
    int16_t x;
    int16_t y;
    int16_t local_x;
    int16_t local_y;
    int16_t escape_map_group;
    int16_t escape_map_num;
    bool    is_indoors;
    bool    is_valid;
} PlayerLocationRaw;

/**
 * Read the active player position and map coordinates from SaveBlock1 in EWRAM.
 *
 * Fails closed: returns false when @p config is NULL or describes GAME_UNKNOWN, and never falls
 * back to a cached party offset or a FireRed-style SaveBlock1 base for an unknown game.
 *
 * This entry point has no IWRAM access, so for a layout that resolves SaveBlock1 through an
 * IWRAM pointer (Heart & Soul 2.0.5) it always fails closed. Use
 * pokemon_read_player_location_gba() with a region-checked reader for those layouts.
 */
bool pokemon_read_player_location(
    const uint8_t* ewram,
    size_t ewram_size,
    const GameMemoryConfig* config,
    PlayerLocationRaw* out_location
);

/**
 * Read the active player position and map coordinates using a bounds-checked absolute-address
 * reader.
 *
 * When @p config declares `save_block1_ptr_gba_address`, the active SaveBlock1 base is read from
 * that absolute GBA address on every call (Heart & Soul 2.0.5 randomizes it inside
 * `gSaveblock1` by an aligned offset in a 128-byte window), validated to fall inside the
 * expected EWRAM window, and then used for the location fields. Nothing is scanned or guessed:
 * an out-of-range, misaligned, truncated or unmapped pointer fails closed.
 *
 * When @p config declares no pointer address, behaviour is identical to
 * pokemon_read_player_location() and @p read is unused.
 *
 * @param read Bounds-checked absolute GBA address reader, or NULL when unavailable.
 * @param user Opaque pointer forwarded to @p read.
 */
bool pokemon_read_player_location_gba(
    DualDexGbaReadFn read,
    void* user,
    const uint8_t* ewram,
    size_t ewram_size,
    const GameMemoryConfig* config,
    PlayerLocationRaw* out_location
);

/**
 * Read battle stat stages for a battler (0 = player, 1 = opponent).
 * Returns true if in battle and stat stages read successfully into out_stages (7 signed ints: atk, def, spe, spa, spd, acc, eva).
 * Values are stages in range -6..+6.
 * Returns false for a NULL or GAME_UNKNOWN configuration.
 */
bool pokemon_read_battle_stat_stages(
    const uint8_t* ewram,
    size_t ewram_size,
    const GameMemoryConfig* config,
    uint8_t battler_index,
    int8_t out_stages[7]
);

/**
 * Read battle UI state:
 * 0 = UNKNOWN / NOT_IN_BATTLE
 * 1 = COMMAND_MENU (Fight, Bag, Pokemon, Run)
 * 2 = MOVE_MENU (4 move selection)
 * 3 = PARTY_MENU (Party screen)
 * 4 = ANIMATION_OR_TEXT (Battle script running / dialogue)
 * 5 = UNKNOWN / TRANSITION (Active battle, but menu state not authoritatively verified)
 *
 * Returns 0 for a NULL or GAME_UNKNOWN configuration.
 */
uint8_t pokemon_read_battle_ui_state(
    const uint8_t* ewram,
    size_t ewram_size,
    const GameMemoryConfig* config
);

/**
 * Read battle presence independently from the UI controller state:
 * 0 = NOT_OBSERVED, 1 = OBSERVED, 2 = UNKNOWN (reader/configuration unavailable).
 *
 * A NULL or GAME_UNKNOWN configuration always reports 2 (UNKNOWN), never NOT_OBSERVED.
 */
uint8_t pokemon_read_battle_presence(
    const uint8_t* ewram,
    size_t ewram_size,
    const GameMemoryConfig* config
);

#ifdef __cplusplus
}
#endif

#endif // DUALDEX_POKEMON_READER_H
