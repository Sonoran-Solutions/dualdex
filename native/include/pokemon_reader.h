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
 * Battle lifecycle as reported by the game's own battle state, deliberately finer-grained than
 * "in battle / not in battle" because the transition windows are exactly where a reader that
 * guesses will show a stale opponent.
 *
 *   INACTIVE      the engine does not own a battle; no battle-derived value may be presented.
 *   INITIALIZING  the engine has taken over but the battler set is not yet authoritative.
 *   ACTIVE        gBattlersCount is a supported value (2 or 4) and battle state may be read.
 *   ENDING        an outcome has been recorded; the battle is finishing. Treated as not-active
 *                 for presentation so teardown can never surface the previous opponent.
 *   UNKNOWN       the layout cannot answer (no symbol declaration, unreadable memory, or a
 *                 self-inconsistent snapshot). Never treated as either in or out of battle.
 */
typedef enum {
    BATTLE_LIFECYCLE_UNKNOWN = 0,
    BATTLE_LIFECYCLE_INACTIVE = 1,
    BATTLE_LIFECYCLE_INITIALIZING = 2,
    BATTLE_LIFECYCLE_ACTIVE = 3,
    BATTLE_LIFECYCLE_ENDING = 4
} BattleLifecycleState;

/**
 * Battle shape, derived only from exact compiled battle globals. MULTI_OR_PARTNER covers every
 * battle the single-opponent history cannot describe (multi battles, in-game partners, two
 * opponents); it is reported as unsupported rather than reduced to one enemy.
 */
typedef enum {
    BATTLE_KIND_NONE = 0,
    BATTLE_KIND_WILD_SINGLE = 1,
    BATTLE_KIND_TRAINER_SINGLE = 2,
    BATTLE_KIND_DOUBLES = 3,
    BATTLE_KIND_MULTI_OR_PARTNER = 4,
    BATTLE_KIND_UNKNOWN = 5
} BattleKind;

/**
 * Whether an opponent can be named for the battle currently being fought.
 *
 *   NONE_ACTIVE     no battle is active (or it is initializing/ending); the enemy side is empty.
 *   SLOT            exactly one opponent battler is present and its party slot is authoritative.
 *   AMBIGUOUS       more than one opponent battler is present. A single-opponent surface must
 *                   not pick one, so no slot is reported.
 *   UNKNOWN         the battle is active but the authoritative state cannot be read.
 */
typedef enum {
    ACTIVE_ENEMY_UNKNOWN = 0,      // the authoritative state could not be read
    ACTIVE_ENEMY_NONE_ACTIVE = 1,  // the authoritative state says there is no active opponent
    ACTIVE_ENEMY_SLOT = 2,         // exactly one opponent, with an authoritative party slot
    ACTIVE_ENEMY_AMBIGUOUS = 3     // more than one opponent; no single enemy may be named
} ActiveEnemyState;

/** Maximum number of battlers the upstream battle engine can have (MAX_BATTLERS_COUNT). */
#define DUALDEX_MAX_BATTLERS 4

/**
 * A point-in-time, self-consistent snapshot of the exact compiled battle globals.
 *
 * Every field is reported as read, including the ones a caller does not need, because the
 * lifecycle decision is a consistency judgement over the whole set rather than over one word.
 */
typedef struct {
    BattleLifecycleState lifecycle;
    BattleKind           kind;
    bool     in_battle_flag_readable;  // gMain.inBattle was readable through a verified region
    bool     in_battle_flag;           // its decoded value
    bool     counters_readable;        // gBattlersCount / gBattleTypeFlags / gBattleOutcome read
    uint8_t  battlers_count;           // gBattlersCount
    uint32_t battle_type_flags;        // gBattleTypeFlags
    uint8_t  battle_outcome;           // gBattleOutcome
    bool     party_indexes_readable;   // gBattlerPartyIndexes read for 0..battlers_count-1
    bool     positions_readable;       // gBattlerPositions read for 0..battlers_count-1
    bool     absent_flags_readable;    // gAbsentBattlerFlags read
    uint8_t  absent_battler_flags;     // gAbsentBattlerFlags
    int16_t  party_index[DUALDEX_MAX_BATTLERS];   // gBattlerPartyIndexes[], -1 when unavailable
    uint8_t  position[DUALDEX_MAX_BATTLERS];      // gBattlerPositions[], 0xFF when unavailable
} BattleStateRaw;

/**
 * Authoritative resolution of the opponent for the frame.
 *
 * The only way this can produce a slot is:
 *     opponent battler -> gBattlerPositions[battler] side bit -> gBattlerPartyIndexes[battler]
 *     -> a slot inside the authoritative enemy count
 * with the battler itself known present, alive and carrying a plausible live species word.
 * There is no species comparison, no HP comparison, no "first living enemy" and no slot-0
 * default anywhere on that path.
 */
typedef struct {
    ActiveEnemyState state;
    int8_t  battler_index;    // opponent battler the slot came from, -1 when unknown
    int8_t  party_slot;       // enemy party slot, -1 when unknown/ambiguous/none
    uint8_t opponent_battlers; // number of active opponent battlers observed
    bool    fainted;          // the resolved opponent is at 0 HP (a forced-switch window)
} ActiveEnemyInfo;

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

    // Live BattlePokemon observation (Heart & Soul 2.0.5 only; every other layout leaves all
    // five zero so an unsupported game fails closed instead of reinterpreting a vanilla
    // BattlePokemon through the H&S structure). The values must equal the generated ABI table
    // in native/src/hns_battle_pokemon_layout_gen.h; the reader cross-checks them at runtime.
    uint32_t battle_mons_ability_offset;     // offset of `ability` within struct BattlePokemon
    uint32_t battle_mons_ability_size;       // compiled byte width of `ability`
    uint32_t battle_mons_types_offset;       // offset of `types` within struct BattlePokemon
    uint32_t battle_mons_type_count;         // element count of `types`
    uint32_t battle_mons_type_width;         // compiled byte width of one `types` element

    uint32_t battler_party_indexes_offset;   // EWRAM-relative gBattlerPartyIndexes, 0 = unavailable
    uint32_t battlers_count_offset;          // EWRAM-relative gBattlersCount, 0 = unavailable
    uint32_t battle_type_flags_offset;       // EWRAM-relative gBattleTypeFlags, 0 = unavailable
    uint32_t battle_outcome_offset;          // EWRAM-relative gBattleOutcome, 0 = unavailable
    uint32_t battler_positions_offset;       // EWRAM-relative gBattlerPositions, 0 = unavailable
    uint32_t absent_battler_flags_offset;    // EWRAM-relative gAbsentBattlerFlags, 0 = unavailable

    // Battle lifecycle gate. `gMain.inBattle` is the upstream flag the battle engine itself sets
    // on entering a battle and clears when returning to the overworld, so it distinguishes
    // "the engine owns the frame" from "an old gBattleMons species word is still lying around".
    // Both fields are ABSOLUTE GBA addresses because `struct Main gMain` lives in IWRAM
    // (0x03000000...) while the battle globals live in EWRAM; 0 means "this layout does not
    // declare the symbol" and every lifecycle decision then degrades to UNKNOWN rather than
    // guessing.
    uint32_t main_struct_gba_address;        // absolute GBA address of `gMain`, 0 = unavailable
    uint32_t main_in_battle_byte_offset;     // byte containing the `inBattle` bit
    uint8_t  main_in_battle_bit;             // bit index of `inBattle` inside that byte (zero-based)

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

    // SaveBlock3 resolution (Heart & Soul 2.0.5 ChallengeSettings). Both fields are absolute
    // GBA addresses; a zero value means "this layout does not declare SaveBlock3" and the
    // challenge-settings reader fails closed. Unlike SaveBlock1, SaveBlock3 never moves:
    // SetSaveBlocksPointers() re-bases only gSaveBlock2Ptr/gSaveBlock1Ptr/gPokemonStoragePtr,
    // and gSaveBlock3Ptr is statically initialised to &gSaveblock3. The pointer is still read
    // from IWRAM on every call and required to equal the compiled base, so a stale pointer,
    // a different binary, or an unreadable IWRAM byte can never authorize a read.
    uint32_t save_block3_ptr_gba_address;    // absolute GBA address of `gSaveBlock3Ptr`, 0 = unavailable
    uint32_t save_block3_base_gba_address;   // absolute GBA address of the compiled `gSaveblock3`

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
 * This entry point has no absolute-address reader, so a layout that gates its battle state
 * through an IWRAM symbol (Heart & Soul 2.0.5 `gMain.inBattle`) reports no active battler from
 * it. Use pokemon_read_player_party_gba() for those layouts.
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
 * Player party plus authoritative battle state, using a bounds-checked absolute-address reader.
 *
 * Identical to pokemon_read_player_party() except that the battle lifecycle and the active
 * battler -> party slot mapping are resolved from the exact compiled battle globals. When
 * @p read is NULL, or the configuration does not declare the battle symbols, the party is still
 * parsed but no active battler is reported: the snapshot's `active_battler_slot` stays -1 and
 * `active_battler_known` stays false rather than defaulting to slot 0.
 *
 * @param read Bounds-checked absolute GBA address reader, or NULL when unavailable.
 * @param user Opaque pointer forwarded to @p read.
 */
uint8_t pokemon_read_player_party_gba(
    DualDexGbaReadFn read,
    void* user,
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
 * When @p config declares `enemy_party_count_offset`, that symbol is authoritative:
 *   count == 0    -> empty snapshot, nothing scanned;
 *   count 1..6    -> exactly those slots at enemy_party_offset;
 *   count > 6     -> fail closed (no scan, no truncation);
 *   any corrupt claimed slot -> fail closed.
 * The player-party blind-scan fallback is never used for a layout with an authoritative count.
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
 * Enemy party plus authoritative active-opponent resolution, using an absolute-address reader.
 *
 * This is the battle-lifecycle-aware form of pokemon_read_enemy_party():
 *   - the enemy party is only populated while a battle is ACTIVE, bounded by the authoritative
 *     gEnemyPartyCount;
 *   - `out_snapshot->active_battler_slot` is set only from
 *     gBattlerPositions -> side, then gBattlerPartyIndexes, then the authoritative enemy count;
 *   - in a battle with two active opponent battlers nothing is chosen and
 *     `out_snapshot->active_enemy_ambiguous` is set instead;
 *   - during encounter transitions (opponent fainted, replacement not yet resolvable) the slot
 *     stays unknown rather than retaining the previous opponent.
 *
 * @param read Bounds-checked absolute GBA address reader, or NULL when unavailable.
 * @param user Opaque pointer forwarded to @p read.
 */
uint8_t pokemon_read_enemy_party_gba(
    DualDexGbaReadFn read,
    void* user,
    const uint8_t* ewram,
    size_t ewram_size,
    const GameMemoryConfig* config,
    PartySnapshot* out_snapshot
);

/**
 * Classify the battle lifecycle from exact compiled battle state.
 *
 * Uses, in order of authority: `gMain.inBattle` (the engine's own flag), `gBattlersCount`,
 * `gBattlerPositions`, `gBattlerPartyIndexes`, `gAbsentBattlerFlags`, `gBattleTypeFlags` and
 * `gBattleOutcome`. A snapshot that is not self-consistent (an unreadable region, a battler
 * count outside {2,4}, a party index outside the authoritative party bounds, or a side bit that
 * contradicts the declared battler count) degrades to UNKNOWN instead of being interpreted.
 *
 * Returns BATTLE_LIFECYCLE_UNKNOWN when @p read or @p state is NULL or the configuration does
 * not declare the symbols.
 */
BattleLifecycleState pokemon_read_battle_lifecycle(
    DualDexGbaReadFn read,
    void* user,
    const uint8_t* ewram,
    size_t ewram_size,
    const GameMemoryConfig* config,
    BattleStateRaw* out_state
);

/**
 * Resolve the active opponent through authoritative battler state.
 *
 * `out_info->party_slot` is only ever set from `gBattlerPartyIndexes[opponent battler]` where the
 * opponent battler is identified by `gBattlerPositions`' side bit and the resulting slot is
 * inside the authoritative enemy party count. Species/HP coincidence, first-living-enemy and
 * slot-0 fallbacks are deliberately absent.
 *
 * The returned state preserves the difference between "the authoritative state says there is no
 * opponent" and "the authoritative state could not be read":
 *
 *   BATTLE_LIFECYCLE_INACTIVE      -> ACTIVE_ENEMY_NONE_ACTIVE
 *   BATTLE_LIFECYCLE_INITIALIZING  -> ACTIVE_ENEMY_NONE_ACTIVE
 *   BATTLE_LIFECYCLE_ENDING        -> ACTIVE_ENEMY_NONE_ACTIVE
 *   BATTLE_LIFECYCLE_UNKNOWN       -> ACTIVE_ENEMY_UNKNOWN
 *   BATTLE_LIFECYCLE_ACTIVE        -> ACTIVE_ENEMY_SLOT, or ACTIVE_ENEMY_AMBIGUOUS when two
 *                                     opponent battlers are present, or ACTIVE_ENEMY_UNKNOWN
 *                                     when the battle is real but the enemy party is unreadable
 *
 * ACTIVE_ENEMY_UNKNOWN is what an unreadable `gMain.inBattle` gate produces: the reader cannot
 * tell "no battle" from "a battle it cannot see", so it must not claim either. UNKNOWN carries no
 * slot, so it stays fail-closed.
 */
ActiveEnemyState pokemon_resolve_active_enemy(
    DualDexGbaReadFn read,
    void* user,
    const uint8_t* ewram,
    size_t ewram_size,
    const GameMemoryConfig* config,
    PartySnapshot* out_enemy_snapshot,
    ActiveEnemyInfo* out_info
);

/**
 * True when a battle is active and exactly one opponent battler is present, so the caller may
 * present an opponent. Never true for doubles, partner or multi battles.
 */
bool pokemon_battle_is_single_opponent(
    DualDexGbaReadFn read,
    void* user,
    const uint8_t* ewram,
    size_t ewram_size,
    const GameMemoryConfig* config
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
 * Status of a challenge-settings snapshot.
 *
 *   UNAVAILABLE       the read never happened: the configuration does not declare SaveBlock3,
 *                     the pointer is unreadable/invalid, or the bytes are unreadable. No field
 *                     carries a value and no default may be substituted.
 *   OBSERVED          every field was decoded from live memory and is inside the pinned
 *                     source's value domain.
 *   OBSERVED_INVALID  the bytes were read, but at least one multi-bit field holds a value the
 *                     pinned source never assigns (e.g. LevelCap == 3). Each such field is
 *                     flagged; observed fields are still reported, nothing is coerced.
 */
typedef enum {
    CHALLENGE_SETTINGS_UNAVAILABLE = 0,
    CHALLENGE_SETTINGS_OBSERVED = 1,
    CHALLENGE_SETTINGS_OBSERVED_INVALID = 2
} ChallengeSettingsStatus;

/**
 * One decoded field. `observed` is the provenance flag: a source field being 0 is a legitimate
 * observed value and must never be confused with "unknown" (observed == false). For multi-bit
 * fields `invalid` marks a value outside the pinned source's domain; `raw` always holds the
 * decoded bits when observed. 1-bit fields can never be invalid (any bit is in domain).
 */
typedef struct {
    bool    observed;
    bool    invalid;
    uint8_t raw;
} ChallengeSettingField;

/**
 * A point-in-time snapshot of SaveBlock3.challengeSettings for the exact H&S 2.0.5 build.
 *
 * `status` is the authoritative verdict for the whole struct; the individual fields preserve
 * provenance so a consumer can distinguish "observed off" from "not read" without consulting
 * source defaults, which are never used as runtime observations.
 */
typedef struct {
    ChallengeSettingsStatus status;
    ChallengeSettingField option_style;               // 0 = per-move split, 1 = type-decided
    ChallengeSettingField tx_mode_fairy_types;
    ChallengeSettingField tx_random_type;
    ChallengeSettingField tx_random_type_effectiveness;
    ChallengeSettingField tx_random_abilities;
    ChallengeSettingField tx_random_moves;
    ChallengeSettingField tx_challenges_no_evs;
    ChallengeSettingField tx_challenges_base_stat_equalizer;
    ChallengeSettingField tx_challenges_mirror;
    ChallengeSettingField tx_challenges_mirror_thief; // source: tx_Challenges_Mirror_Thief
    ChallengeSettingField tx_challenges_trainer_scaling_ivs;
    ChallengeSettingField tx_challenges_trainer_scaling_evs;
    ChallengeSettingField tx_challenges_max_party_ivs;
    ChallengeSettingField tx_mode_sturdy;
    ChallengeSettingField tx_challenges_level_cap;
    ChallengeSettingField tx_challenges_exp_multiplier;
    ChallengeSettingField tx_mode_legendary_abilities;
} ChallengeSettingsSnapshot;

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
 * Read the pinned H&S 2.0.5 SaveBlock3.challengeSettings through the bounds-checked
 * absolute-address reader, and decode it into a typed snapshot.
 *
 * This is a MEMORY-READ / OBSERVABILITY API only: it changes no emulator memory and enables no
 * calculator capability.
 *
 * Fail-closed contract. The snapshot is OBSERVED only when, in order:
 *   1. @p config declares SaveBlock3 (both `save_block3_*` addresses non-zero; every other
 *      game's layout zeroes them, so a wrong profile can never authorize the read);
 *   2. `gSaveBlock3Ptr` (IWRAM) is readable through @p read;
 *   3. the pointer equals the compiled `gSaveblock3` address exactly (SaveBlock3 never moves;
 *      a stale pointer, another binary's pointer, or an out-of-EWRAM value fails closed);
 *   4. the whole 32-byte struct is readable through @p read.
 * Any failure returns false with a zeroed, UNAVAILABLE snapshot. Nothing is ever scanned,
 * guessed, cached, or substituted with a source default.
 *
 * Decoding uses the generated layout table
 * (`native/src/hns_challenge_settings_layout_gen.h`), which is compiled evidence from the pinned
 * upstream source. A multi-bit field whose value the pinned source never assigns (its
 * `valid_values` mask rejects it) is flagged invalid and the snapshot status is
 * CHALLENGE_SETTINGS_OBSERVED_INVALID; 1-bit fields are always in domain. optionStyle is a
 * 1-bit source field (0 = per-move split, 1 = type decides physical/special), so it has no
 * out-of-domain encoding; its source enum meaning is preserved in the raw value.
 *
 * @return true only when the snapshot status is OBSERVED or OBSERVED_INVALID.
 */
bool pokemon_read_challenge_settings_gba(
    DualDexGbaReadFn read,
    void* user,
    const uint8_t* ewram,
    size_t ewram_size,
    const GameMemoryConfig* config,
    ChallengeSettingsSnapshot* out_snapshot
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
 * Which active battler a live observation is requested for.
 *
 * The resolution of the concrete battler index is never the caller's job: the reader reuses the
 * same authoritative machinery the HP/stat-stage surfaces use.
 */
typedef enum {
    BATTLER_ROLE_PLAYER   = 0,  // the active player-side battler
    BATTLER_ROLE_OPPONENT = 1   // the single active opponent battler
} BattlerRole;

/**
 * Status of a live battler runtime observation.
 *
 *   UNAVAILABLE       no observation exists: the layout does not declare the live fields, the
 *                     lifecycle is not authoritatively ACTIVE, the battler could not be resolved,
 *                     or the required BattlePokemon bytes were unreadable. No field carries a
 *                     value and nothing is retained from a previous observation.
 *   AMBIGUOUS         the battle is active but more than one opponent battler is present, so a
 *                     single-opponent surface must not name one. Nothing is observed.
 *   OBSERVED          every field was decoded from live `gBattleMons` state.
 *   OBSERVED_INVALID  the bytes were read, but at least one observed value is outside the pinned
 *                     source's domain. Values are reported verbatim and flagged, never coerced.
 */
typedef enum {
    BATTLER_RUNTIME_STATE_UNAVAILABLE = 0,
    BATTLER_RUNTIME_STATE_AMBIGUOUS = 1,
    BATTLER_RUNTIME_STATE_OBSERVED = 2,
    BATTLER_RUNTIME_STATE_OBSERVED_INVALID = 3
} BattlerRuntimeStateStatus;

/**
 * The effective ability and current type state of one authoritative active battler, read
 * directly from the running battle engine's `gBattleMons[battler]` (Heart & Soul 2.0.5).
 *
 * This is LIVE COMBAT STATE, not a declaration:
 *   - `ability_id` is the engine's CURRENT identity for the battler. It is not the party's
 *     stored ability slot, not the species' declared slot ability, and not derived from any
 *     challenge setting. Naming an observed ID against the pinned ability catalogue happens
 *     above this layer; the catalogue is never used to produce the ID.
 *   - `types` are the engine's current type words, exposed verbatim (TYPE_NONE 0 stays 0,
 *     duplicates are preserved). They are not the species' static typings.
 */
typedef struct {
    BattlerRuntimeStateStatus status;
    int8_t   battler_index;        // the gBattleMons entry that was read, -1 when not observed
    int8_t   party_slot;           // authoritative gBattlerPartyIndexes slot, -1 when unknown
    bool     party_slot_known;     // true only when the slot came from authoritative state
    bool     ability_observed;     // the ability word was decoded from live memory
    bool     ability_invalid;      // outside the pinned enum Ability domain (still reported raw)
    uint16_t ability_id;           // the engine's current effective ability identity
    bool     types_observed;       // the type words were decoded from live memory
    uint8_t  type_count;           // number of observed type slots (0 when not observed)
    uint8_t  types[3];             // current type values, verbatim
    bool     types_invalid;        // at least one value outside the pinned enum Type domain
} BattlerRuntimeState;

/**
 * Read the effective ability and current types of the active battler for @p role, straight
 * from the running H&S 2.0.5 battle engine.
 *
 * Fail-closed contract. An OBSERVED/AMBIGUOUS verdict requires, in order:
 *   1. @p config declares the live BattlePokemon layout (only the exact H&S 2.0.5 layout does;
 *      vanilla and every other game fail closed with UNAVAILABLE);
 *   2. the declared layout equals the generated ABI table exactly (drift cannot read);
 *   3. the authoritative lifecycle (gMain.inBattle + the compiled battle globals) is ACTIVE —
 *      INACTIVE, INITIALIZING, ENDING and UNKNOWN all return false with no state retained, so
 *      battle teardown and pre-battle frames can never publish an observation;
 *   4. the battler is resolved through the same authoritative path the HP/stat-stage surfaces
 *      use (player: battler 0 + gBattlerPartyIndexes[0]; opponent:
 *      pokemon_resolve_active_enemy's battler, which is AMBIGUOUS in doubles and never "the
 *      first enemy");
 *   5. the battler index is inside the compiled battler count and the battler is not absent;
 *   6. the complete ability and types bytes are readable through the bounds-checked reader.
 *
 * Observed values outside the pinned enum domains (ability > HNS_BATTLE_POKEMON_ABILITY_ID_MAX,
 * a type byte > HNS_BATTLE_POKEMON_TYPE_ID_MAX) are reported verbatim with
 * status OBSERVED_INVALID — never substituted, defaulted or renamed.
 *
 * @return true only when the snapshot status is OBSERVED or OBSERVED_INVALID.
 */
bool pokemon_read_battler_runtime_state_gba(
    DualDexGbaReadFn read,
    void* user,
    const uint8_t* ewram,
    size_t ewram_size,
    const GameMemoryConfig* config,
    BattlerRole role,
    BattlerRuntimeState* out_state
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
 *
 * This entry point has no absolute-address reader. For a layout that declares the authoritative
 * lifecycle gate (Heart & Soul 2.0.5 `gMain.inBattle`) the gate is unreachable, so it always
 * reports 2 (UNKNOWN). Use pokemon_read_battle_presence_gba() for those layouts.
 */
uint8_t pokemon_read_battle_presence(
    const uint8_t* ewram,
    size_t ewram_size,
    const GameMemoryConfig* config
);

/**
 * Authoritative battle presence via the bounds-checked absolute-address reader.
 *
 * For a layout that declares the lifecycle gate, presence is derived from
 * pokemon_read_battle_lifecycle() and from nothing else:
 *
 *   ACTIVE              -> 1 (OBSERVED)
 *   INACTIVE            -> 0 (NOT_OBSERVED)
 *   INITIALIZING        -> 2 (UNKNOWN)
 *   ENDING              -> 2 (UNKNOWN)
 *   UNKNOWN             -> 2 (UNKNOWN)
 *
 * In particular, a stale but plausible `gBattleMons[0].species` together with
 * `gMain.inBattle == false` reports NOT_OBSERVED, never OBSERVED, and a battle that is starting or
 * tearing down is never reported as running.
 *
 * Layouts that declare no lifecycle gate (FireRed, Emerald and the other vanilla titles) keep the
 * historical EWRAM-only reading unchanged.
 */
uint8_t pokemon_read_battle_presence_gba(
    DualDexGbaReadFn read,
    void* user,
    const uint8_t* ewram,
    size_t ewram_size,
    const GameMemoryConfig* config
);

#ifdef __cplusplus
}
#endif

#endif // DUALDEX_POKEMON_READER_H
