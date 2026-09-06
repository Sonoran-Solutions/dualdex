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
 * Game memory offset configuration.
 */
typedef struct {
    GbaGameId game_id;
    const char* game_name;
    uint32_t player_party_offset;      // Offset relative to EWRAM (0x02000000)
    uint32_t player_party_count_offset;// Offset relative to EWRAM
    uint32_t enemy_party_offset;       // Offset relative to EWRAM (for battle reading)
    uint32_t enemy_party_count_offset; // Offset relative to EWRAM
    uint32_t battle_mons_offset;       // Offset relative to EWRAM for gBattleMons
    uint32_t battle_mons_size;         // Size of struct BattlePokemon
    uint32_t battle_mons_hp_offset;    // Offset of hp within struct BattlePokemon
    bool     has_evs;                  // False for Ghost Grey
    bool     has_ivs;                  // False for Ghost Grey
} GameMemoryConfig;

/**
 * Get nature name string from index (0 - 24).
 */
const char* pokemon_get_nature_name(uint8_t nature_index);

/**
 * Decrypt and parse a single 100-byte GBA Pokémon structure.
 *
 * @param raw_bytes Pointer to 100 bytes of Pokémon data (or 80 for PC box)
 * @param is_party_mon True if full 100 bytes (party), false if 80 bytes (box)
 * @param out_pokemon Pointer to ParsedPokemon destination struct
 * @return True if parsing succeeded and checksum is valid
 */
bool pokemon_parse_single(const uint8_t* raw_bytes, bool is_party_mon, ParsedPokemon* out_pokemon);

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
 */
const GameMemoryConfig* pokemon_get_game_config(GbaGameId game_id);

/**
 * Parse the active player party from a 256 KB EWRAM buffer.
 *
 * @param ewram Pointer to the 256 KB EWRAM memory block (base 0x02000000)
 * @param ewram_size Size of EWRAM buffer (typically 262144 bytes)
 * @param config Game configuration defining memory offsets
 * @param out_snapshot Pointer to PartySnapshot destination struct
 * @return Number of valid Pokémon parsed into the snapshot
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
 * @param ewram Pointer to EWRAM
 * @param ewram_size Size of EWRAM
 * @param config Game configuration
 * @param out_snapshot Destination struct
 * @return Number of valid enemy Pokémon parsed
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
 */
bool pokemon_read_player_location(
    const uint8_t* ewram,
    size_t ewram_size,
    const GameMemoryConfig* config,
    PlayerLocationRaw* out_location
);

#ifdef __cplusplus
}
#endif

#endif // DUALDEX_POKEMON_READER_H
