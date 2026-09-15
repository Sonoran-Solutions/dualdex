#ifndef DUALDEX_POKEMON_STRUCTS_H
#define DUALDEX_POKEMON_STRUCTS_H

#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

#pragma pack(push, 1)

/**
 * Raw 100-byte GBA Gen 3 Party Pokemon in memory (little-endian).
 * For box Pokemon, only the first 80 bytes (0x00 - 0x4F) exist.
 */
typedef struct {
    // 0x00 - 0x1F: Unencrypted Header (32 bytes)
    uint32_t pid;           // Personality Value (governs nature, gender, shininess, order)
    uint32_t otid;          // Full Trainer ID (Low 16 = TID, High 16 = SID)
    uint8_t  nickname[10];  // Gen 3 character encoded nickname (0xFF terminated)
    uint16_t language;      // Language code (e.g. 0x0201 = English)
    uint8_t  ot_name[7];    // Trainer name in Gen 3 encoding (0xFF terminated)
    uint8_t  markings;      // Bitfield: circle, triangle, square, heart
    uint16_t checksum;      // 16-bit sum of decrypted substructure words
    uint16_t unused;        // 0x0000

    // 0x20 - 0x4F: Encrypted Substructures (48 bytes)
    uint8_t  raw_substructures[48];

    // 0x50 - 0x63: Unencrypted Party-Only Battle Stats (20 bytes)
    uint32_t status_condition; // Bits: 0-2 Sleep, 3 Poison, 4 Burn, 5 Freeze, 6 Paralyze, 7 Bad Poison
    uint8_t  level;            // Current level (1 - 100)
    uint8_t  mail_id;          // Mail ID (-1 / 0xFF if none)
    uint16_t current_hp;       // Current HP
    uint16_t max_hp;           // Max HP
    uint16_t attack;           // Calculated Attack
    uint16_t defense;          // Calculated Defense
    uint16_t speed;            // Calculated Speed
    uint16_t sp_attack;        // Calculated Special Attack
    uint16_t sp_defense;       // Calculated Special Defense
} RawGbaPokemon;

/** Substructure G: Growth (12 bytes) */
typedef struct {
    uint16_t species;     // National/Internal Species ID
    uint16_t held_item;   // Held item ID
    uint32_t experience;  // Total EXP points
    uint8_t  pp_bonuses;  // 2 bits per move (PP Ups used, 0-3)
    uint8_t  friendship;  // Current friendship (0 - 255)
    uint16_t padding;
} SubstructGrowth;

/** Substructure A: Attacks (12 bytes) */
typedef struct {
    uint16_t moves[4];    // Move IDs (0 = None)
    uint8_t  pp[4];       // Current PP remaining for each move
} SubstructAttacks;

/** Substructure E: EVs & Contest Condition (12 bytes) */
typedef struct {
    uint8_t hp_ev;
    uint8_t attack_ev;
    uint8_t defense_ev;
    uint8_t speed_ev;
    uint8_t sp_attack_ev;
    uint8_t sp_defense_ev;
    uint8_t coolness;
    uint8_t beauty;
    uint8_t cuteness;
    uint8_t smartness;
    uint8_t toughness;
    uint8_t feel;
} SubstructEVs;

/** Substructure M: Miscellaneous (12 bytes) */
typedef struct {
    uint8_t  pokerus;           // Strain & days remaining
    uint8_t  met_location;      // Location ID
    uint16_t origins;           // Met level, origin game, Poké Ball, OT gender
    uint32_t iv_egg_ability;    // IVs (30 bits), isEgg (bit 30), abilitySlot (bit 31)
    uint32_t ribbons_obedience; // Ribbons, Bit 31 = obedience flag
} SubstructMisc;

#pragma pack(pop)

/**
 * Gender enum
 */
typedef enum {
    GENDER_MALE = 0,
    GENDER_FEMALE = 1,
    GENDER_GENDERLESS = 2
} PokemonGender;

/**
 * On-cartridge Pokemon storage layout.
 *
 * DualDex originally parsed every game with the vanilla Gen 3 bit layout. Heart & Soul 2.0.5
 * is built on pokeemerald-expansion, whose `struct BoxPokemon` / `PokemonSubstruct3` moved two
 * fields that the vanilla layout reads from fixed bit positions:
 *
 *   VANILLA_GEN3
 *     - ability slot = bit 31 of the 32-bit word at PokemonSubstruct3 offset 4.
 *     - no mint nature, no shiny modifier.
 *
 *   EXPANSION (pokeemerald-expansion, verified against H&S 2.0.5)
 *     - bit 31 of the word at PokemonSubstruct3 offset 4 is `gigantamaxFactor`, NOT the
 *       ability slot.
 *     - `abilityNum` is a 2-bit field at bits 29..30 of the word at PokemonSubstruct3
 *       offset 8.
 *     - `hiddenNatureModifier` (5 bits) lives in the byte at BoxPokemon offset 0x12 bits 3..7
 *       and XORs the personality nature to produce the stat-effective "mint" nature.
 *     - `shinyModifier` is bit 14 of the 16-bit word at BoxPokemon offset 0x1E and XORs the
 *       shiny verdict.
 *
 * The distinction is explicit configuration rather than a guess from species ID or ROM name.
 */
typedef enum {
    PKMN_STORAGE_VANILLA_GEN3 = 0,
    PKMN_STORAGE_EXPANSION    = 1
} PokemonStorageLayout;

/**
 * Tri-state shiny verdict.
 *
 * H&S 2.0.5 computes shininess as
 *     (shinyValue < shinyOdds) ^ shinyModifier
 * where `shinyOdds` is drawn from the player's `SaveBlock3.challengeSettings`
 * (`tx_Features_ShinyChance`, values {8, 16, 32, 64, 128}). Because DualDex does not read
 * SaveBlock3 challenge settings yet, the verdict is only exactly determinable when it is
 * independent of the odds: shinyValue < 8 is always shiny and shinyValue >= 128 is never
 * shiny. Everything in between is reported as unknown instead of guessed.
 */
typedef enum {
    PKMN_SHINY_UNKNOWN = 0,
    PKMN_SHINY_NO      = 1,
    PKMN_SHINY_YES     = 2
} PokemonShinyState;

/**
 * Clean, fully decrypted & parsed representation of a Pokémon.
 */
typedef struct {
    bool     is_valid;          // True if checksum matched
    bool     is_empty;          // True if slot is empty (all zeros or PID=0)
    uint32_t pid;
    uint16_t tid;
    uint16_t sid;
    char     nickname[32];      // UTF-8 decoded
    char     ot_name[32];       // UTF-8 decoded

    // Core attributes
    uint16_t species;
    uint16_t held_item;
    uint8_t  level;
    uint8_t  nature;            // 0 - 24
    const char* nature_name;    // e.g. "Adamant", "Modest"
    bool     is_shiny;
    uint8_t  ability_slot;      // 0 = Ability 1, 1 = Ability 2 / Hidden
    bool     is_egg;
    uint8_t  friendship;
    uint32_t experience;

    // IVs (0 - 31)
    uint8_t  hp_iv;
    uint8_t  attack_iv;
    uint8_t  defense_iv;
    uint8_t  speed_iv;
    uint8_t  sp_attack_iv;
    uint8_t  sp_defense_iv;

    // EVs (0 - 255)
    uint8_t  hp_ev;
    uint8_t  attack_ev;
    uint8_t  defense_ev;
    uint8_t  speed_ev;
    uint8_t  sp_attack_ev;
    uint8_t  sp_defense_ev;

    // Moves & PP
    uint16_t moves[4];
    uint8_t  pp[4];
    uint8_t  pp_bonuses[4];     // 0 - 3 PP Ups applied

    // Layout-dependent interpretation (see PokemonStorageLayout)
    PokemonStorageLayout storage_layout;
    bool     ability_slot_known;  // false when the layout does not expose a usable ability slot
    uint8_t  ability_num;         // raw abilityNum (0..3) for the expansion layout
    bool     gigantamax_factor;   // expansion-only: bit that vanilla parsing mistook for ability
    uint8_t  hidden_nature;       // stat-effective nature: (pid % 25) ^ hiddenNatureModifier
    uint8_t  hidden_nature_modifier; // raw 5-bit value; 0 means the mint nature equals pid % 25
    bool     nature_modified;     // true when hiddenNatureModifier != 0
    uint16_t shiny_value;         // raw GET_SHINY_VALUE(otid, pid)
    uint8_t  shiny_modifier;      // expansion-only shiny inversion bit
    PokemonShinyState shiny_state;

    // Runtime battle stats
    uint16_t current_hp;
    uint16_t max_hp;
    uint16_t attack;
    uint16_t defense;
    uint16_t speed;
    uint16_t sp_attack;
    uint16_t sp_defense;
    uint32_t status_condition;
} ParsedPokemon;

/**
 * Snapshot of the player's party or enemy party (up to 6 Pokémon).
 */
typedef struct {
    uint8_t       count;
    ParsedPokemon members[6];
    // Active battler -> party slot, or -1 when unknown. -1 is the only "unknown" encoding:
    // there is deliberately no "0 means unknown" reading, because slot 0 is a real and
    // frequently active slot and conflating it with "not in battle" is the exact bug this
    // field exists to prevent.
    int8_t        active_battler_slot;
    // True only when active_battler_slot came from authoritative battler/controller state.
    // A caller must check this before presenting the slot; !known means "unknown", never slot 0.
    bool          active_battler_known;
    // True when a battle is active and more than one opponent battler is present, so no single
    // opponent may be named. The UI must degrade instead of picking the first one.
    bool          active_enemy_ambiguous;
    // Number of active opponent battlers observed while resolving active_battler_slot.
    uint8_t       opponent_battlers;
    // The actual battler index whose gBattlerPartyIndexes entry produced active_battler_slot, or
    // -1 when none did. This is an index into gBattlerPositions[] / gBattlerPartyIndexes[] /
    // gBattleMons[] and is never derived from opponent_battlers.
    int8_t        active_battler_index;
} PartySnapshot;

#ifdef __cplusplus
}
#endif

#endif // DUALDEX_POKEMON_STRUCTS_H
