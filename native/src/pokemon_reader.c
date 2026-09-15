#include "pokemon_reader.h"
#include "pokemon_text.h"
#include "gba_memory_map.h"
#include <string.h>
#include <stdio.h>

#ifdef __ANDROID__
#include <android/log.h>
#define LOG_PARTY(...) __android_log_print(ANDROID_LOG_INFO, "DualDex_Party", __VA_ARGS__)
#else
#define LOG_PARTY(...) do {} while(0)
#endif

static const char* NATURE_NAMES[25] = {
    "Hardy", "Lonely", "Brave", "Adamant", "Naughty",
    "Bold", "Docile", "Relaxed", "Impish", "Lax",
    "Timid", "Hasty", "Serious", "Jolly", "Naive",
    "Modest", "Mild", "Quiet", "Bashful", "Rash",
    "Calm", "Gentle", "Sassy", "Careful", "Quirky"
};

// Substructure block indices for [G, A, E, M] in the 48-byte decrypted array (multiples of 12 bytes)
// Derived from the standard 24 permutation orders of GAEM
static const uint8_t SUBSTRUCT_BLOCK_INDEX[24][4] = {
    // G, A, E, M
    {0, 1, 2, 3}, // 0:  GAEM
    {0, 1, 3, 2}, // 1:  GAME
    {0, 2, 1, 3}, // 2:  GEAM
    {0, 3, 1, 2}, // 3:  GEMA
    {0, 2, 3, 1}, // 4:  GMAE
    {0, 3, 2, 1}, // 5:  GMEA
    {1, 0, 2, 3}, // 6:  AGEM
    {1, 0, 3, 2}, // 7:  AGME
    {2, 0, 1, 3}, // 8:  AEGM
    {3, 0, 1, 2}, // 9:  AEMG
    {2, 0, 3, 1}, // 10: AMGE
    {3, 0, 2, 1}, // 11: AMEG
    {1, 2, 0, 3}, // 12: EGAM
    {1, 3, 0, 2}, // 13: EGMA
    {2, 1, 0, 3}, // 14: EAGM
    {3, 1, 0, 2}, // 15: EAMG
    {2, 3, 0, 1}, // 16: EMGA
    {3, 2, 0, 1}, // 17: EMAG
    {1, 2, 3, 0}, // 18: MGAE
    {1, 3, 2, 0}, // 19: MGEA
    {2, 1, 3, 0}, // 20: MAGE
    {3, 1, 2, 0}, // 21: MAEG
    {2, 3, 1, 0}, // 22: MEGA
    {3, 2, 1, 0}  // 23: MEAG
};

// Standard game configurations
//
// Policy audit:
// Emerald and FireRed keep PARTY_DISCOVERY_HEURISTIC (default).
// In vanilla Emerald, SaveBlock1 is allocated on the heap (gSaveBlock1Ptr), so party memory
// can shift dynamically, and test_ewram_scan_ignores_box_pokemon_and_finds_real_party explicitly
// verifies fallback scanning when the static offset mismatches.
// In vanilla FireRed, player_party_count_offset (0x24029) is an unverified / heuristic offset
// (adjacent padding before gEnemyParty at 0x2402C) rather than an authoritative standalone symbol.
// Only layouts with exact compiled symbol and runtime evidence (Heart & Soul 2.0.5) use
// PARTY_DISCOVERY_AUTHORITATIVE_STATIC.
static const GameMemoryConfig CONFIG_EMERALD = {
    .game_id = GAME_EMERALD,
    .game_name = "Pokemon Emerald",
    .player_party_offset = 0x244EC,
    .player_party_count_offset = 0x244E9,
    .enemy_party_offset = 0x24744,
    .enemy_party_count_offset = 0x24740,
    .battle_mons_offset = 0x24064,
    .battle_mons_size = 88,
    .battle_mons_hp_offset = 40,
    .battle_mons_stat_stages_offset = 0x18,
    .player_party_policy = PARTY_DISCOVERY_HEURISTIC,
    .has_evs = true,
    .has_ivs = true
};

// ---------------------------------------------------------------------------
// Heart & Soul 2.0.5 (pokeemerald-expansion) — compiled evidence
//
// Every address below was read from the symbol table of the exact upstream build:
//   repository PokemonHnS-Development/pokehns-expansion
//   tag        Release-v2.0.5  @ 1f42b74dff0e9fe942419845d040663dd829a973
//   command    make -j hns TOOLCHAIN=<arm-gnu-toolchain-13.2.rel1>  (+ make BUILD=hns syms)
//   ABI        ARM7TDMI, -mabi=apcs-gnu, -mthumb -mthumb-interop, -O2
//
// Symbol                     absolute GBA address   size
//   gBattleTypeFlags         0x020000AC             4
//   gBattlersCount           0x020000B0             1
//   gBattleOutcome           0x0200012C             1
//   gBattlerPartyIndexes     0x02000144             8
//   gBattlerPositions        0x02000238             4
//   gBattleControllerExec... 0x020002F4             4
//   gBattleMons              0x02000420             0x220 (4 battlers x 136 bytes)
//   gSaveblock3              0x0200921C             0x34
//   gSaveblock1              0x020124A8             0x3E10 (15760-byte block + 128-byte window)
//   gPlayerPartyCount        0x020342A8             1
//   gEnemyPartyCount         0x020342A9             1
//   gEnemyParty              0x020342B8             0x258 (6 x 100)
//   gPlayerPartyBackup       0x02034510             0x258
//   gPlayerParty             0x02034768             0x258
//   gSaveBlock1Ptr           0x030041D8             4   (IWRAM; see the runtime note below)
//   gSaveBlock3Ptr           0x03000178             4   (IWRAM)
//
// The current H&S offsets that this replaces were stale (they matched neither the tagged
// source nor the compiled image), and `gBattlerPartyIndexes` was being read as
// `gBattleMons - 24`, which is wrong by 732 bytes on 2.0.5.
//
// IWRAM note: a from-source `make hns` build of this commit places gSaveBlock1Ptr at
// 0x030041C0, but the official 2.0.5 release binary uses 0x030041D8. That was established by
// running the release ROM in the bundled mGBA core: gSaveBlock1Ptr, gSaveBlock2Ptr and
// gPokemonStoragePtr were found as three adjacent words holding base+88 for the compiled
// gSaveblock1 / gSaveblock2 / gPokemonStorage EWRAM bases, which pins the IWRAM triple at
// 0x030041D4/0x030041D8/0x030041DC. The EWRAM layout is identical between the two builds;
// only the IWRAM offset of the pointer triple differs, so the runtime-verified value is used.
//
// Struct layout, compiled with the same headers and flags (DWARF-probed, not host sizeof):
//   sizeof(struct BoxPokemon)        = 80
//   sizeof(struct Pokemon)           = 100
//   sizeof(struct BattlePokemon)     = 136   (not 88)
//   sizeof(struct ChallengeSettings) = 32
//   sizeof(struct SaveBlock1)        = 15760
//   _Alignof(struct Coords16)        = 4 under -mabi=apcs-gnu, so SaveBlock1.pos is at 0x04
//                                      because H&S 2.0.5 added `u16 saveVersion` at 0x00.
//   BattlePokemon: statStages 0x18, ability 0x20, types 0x22, pp 0x25, hp 0x2A, level 0x2C,
//                  maxHP 0x2E, item 0x30, status1 0x50.
// ---------------------------------------------------------------------------
static const GameMemoryConfig CONFIG_HEART_AND_SOUL = {
    .game_id = GAME_HEART_AND_SOUL,
    .game_name = "Pokemon Heart & Soul",
    .player_party_offset = 0x34768,
    .player_party_count_offset = 0x342A8,
    .enemy_party_offset = 0x342B8,
    .enemy_party_count_offset = 0x342A9,
    .battle_mons_offset = 0x420,
    .battle_mons_size = 136,
    .battle_mons_hp_offset = 0x2A,
    .battle_mons_stat_stages_offset = 0x18,
    .battler_party_indexes_offset = 0x144,
    .battlers_count_offset = 0xB0,
    .battle_type_flags_offset = 0xAC,
    .battle_outcome_offset = 0x12C,
    .save_block1_ptr_gba_address = 0x030041D8,
    .save_block1_base_gba_address = DUALDEX_GBA_EWRAM_BASE + 0x124A8,
    .save_block1_aslr_range = 128,
    .save_block1_size = 15760,
    .save_block1_pos_offset = 0x04,
    .save_block1_location_offset = 0x08,
    .save_block1_escape_warp_offset = 0x28,
    .storage_layout = PKMN_STORAGE_EXPANSION,
    .player_party_policy = PARTY_DISCOVERY_AUTHORITATIVE_STATIC,
    .has_evs = true,
    .has_ivs = true
};

static const GameMemoryConfig CONFIG_FIRERED = {
    .game_id = GAME_FIRERED,
    .game_name = "Pokemon FireRed",
    .player_party_offset = 0x24284,
    .player_party_count_offset = 0x24029,
    .enemy_party_offset = 0x2402C,
    .enemy_party_count_offset = 0x24028,
    .battle_mons_offset = 0x23F90,
    .battle_mons_size = 88,
    .battle_mons_hp_offset = 40,
    .battle_mons_stat_stages_offset = 0x18,
    .player_party_policy = PARTY_DISCOVERY_HEURISTIC,
    .has_evs = true,
    .has_ivs = true
};

static const GameMemoryConfig CONFIG_LEAFGREEN = {
    .game_id = GAME_LEAFGREEN,
    .game_name = "Pokemon LeafGreen",
    .player_party_offset = 0x24284,
    .player_party_count_offset = 0x24029,
    .enemy_party_offset = 0x2402C,
    .enemy_party_count_offset = 0x24028,
    .battle_mons_offset = 0x23F90,
    .battle_mons_size = 88,
    .battle_mons_hp_offset = 40,
    .battle_mons_stat_stages_offset = 0x18,
    .player_party_policy = PARTY_DISCOVERY_HEURISTIC,
    .has_evs = true,
    .has_ivs = true
};

static const GameMemoryConfig CONFIG_RUBY = {
    .game_id = GAME_RUBY,
    .game_name = "Pokemon Ruby",
    .player_party_offset = 0x24490,
    .player_party_count_offset = 0x2448C,
    .enemy_party_offset = 0x246E8,
    .enemy_party_count_offset = 0x246E4,
    .player_party_policy = PARTY_DISCOVERY_HEURISTIC,
    .has_evs = true,
    .has_ivs = true
};

static const GameMemoryConfig CONFIG_SAPPHIRE = {
    .game_id = GAME_SAPPHIRE,
    .game_name = "Pokemon Sapphire",
    .player_party_offset = 0x24490,
    .player_party_count_offset = 0x2448C,
    .enemy_party_offset = 0x246E8,
    .enemy_party_count_offset = 0x246E4,
    .player_party_policy = PARTY_DISCOVERY_HEURISTIC,
    .has_evs = true,
    .has_ivs = true
};

static const GameMemoryConfig CONFIG_GHOST_GREY = {
    .game_id = GAME_GHOST_GREY,
    .game_name = "Pokemon Ghost Grey",
    .player_party_offset = 0x24284,
    .player_party_count_offset = 0x24029,
    .enemy_party_offset = 0x2402C,
    .enemy_party_count_offset = 0x24028,
    .player_party_policy = PARTY_DISCOVERY_HEURISTIC,
    .has_evs = false, // Ghost Grey removes EVs
    .has_ivs = false  // Ghost Grey removes IVs
};

static const GameMemoryConfig CONFIG_RADICAL_RED = {
    .game_id = GAME_RADICAL_RED,
    .game_name = "Pokemon Radical Red",
    .player_party_offset = 0x24284,
    .player_party_count_offset = 0x24029,
    .enemy_party_offset = 0x2402C,
    .enemy_party_count_offset = 0x24028,
    .player_party_policy = PARTY_DISCOVERY_HEURISTIC,
    .has_evs = true,
    .has_ivs = true
};

static const GameMemoryConfig CONFIG_UNBOUND = {
    .game_id = GAME_UNBOUND,
    .game_name = "Pokemon Unbound",
    .player_party_offset = 0x24284,
    .player_party_count_offset = 0x24029,
    .enemy_party_offset = 0x2402C,
    .enemy_party_count_offset = 0x24028,
    .player_party_policy = PARTY_DISCOVERY_HEURISTIC,
    .has_evs = true,
    .has_ivs = true
};

const char* pokemon_get_nature_name(uint8_t nature_index) {
    if (nature_index < 25) {
        return NATURE_NAMES[nature_index];
    }
    return "Unknown";
}

GbaGameId pokemon_detect_game(const char* rom_title_16) {
    if (!rom_title_16) return GAME_UNKNOWN;

    char title_buf[17] = {0};
    strncpy(title_buf, rom_title_16, 16);
    title_buf[16] = '\0';

    // Check custom hack headers first
    if (strstr(title_buf, "HEARTSOUL") != NULL || strstr(title_buf, "HNS") != NULL || strstr(title_buf, "HEART") != NULL) {
        return GAME_HEART_AND_SOUL;
    }
    if (strstr(title_buf, "GHOST") != NULL || strstr(title_buf, "GREY") != NULL) {
        return GAME_GHOST_GREY;
    }
    if (strstr(title_buf, "RADICAL") != NULL) {
        return GAME_RADICAL_RED;
    }
    if (strstr(title_buf, "UNBOUND") != NULL) {
        return GAME_UNBOUND;
    }

    // Check title in ROM header (offset 0xA0)
    if (strncmp(title_buf, "POKEMON EMER", 12) == 0) return GAME_EMERALD;
    if (strncmp(title_buf, "POKEMON FIRE", 12) == 0) return GAME_FIRERED;
    if (strncmp(title_buf, "POKEMON LEAF", 12) == 0) return GAME_LEAFGREEN;
    if (strncmp(title_buf, "POKEMON RUBY", 12) == 0) return GAME_RUBY;
    if (strncmp(title_buf, "POKEMON SAPP", 12) == 0) return GAME_SAPPHIRE;

    return GAME_UNKNOWN;
}

const GameMemoryConfig* pokemon_get_game_config(GbaGameId game_id) {
    switch (game_id) {
        case GAME_HEART_AND_SOUL: return &CONFIG_HEART_AND_SOUL;
        case GAME_EMERALD: return &CONFIG_EMERALD;
        case GAME_FIRERED: return &CONFIG_FIRERED;
        case GAME_LEAFGREEN: return &CONFIG_LEAFGREEN;
        case GAME_RUBY: return &CONFIG_RUBY;
        case GAME_SAPPHIRE: return &CONFIG_SAPPHIRE;
        case GAME_GHOST_GREY: return &CONFIG_GHOST_GREY;
        case GAME_RADICAL_RED: return &CONFIG_RADICAL_RED;
        case GAME_UNBOUND: return &CONFIG_UNBOUND;
        // Fail closed: an unknown game has no memory layout. Returning a FireRed configuration
        // here would silently reinterpret arbitrary ROM memory as FireRed and is exactly the
        // fail-open behaviour this boundary exists to prevent.
        case GAME_UNKNOWN:
        default: return NULL;
    }
}

/**
 * A configuration is only usable when it names a real layout that is not the unknown sentinel.
 * Every memory reader below rejects an unusable configuration instead of guessing addresses.
 */
static bool config_is_usable(const GameMemoryConfig* config) {
    return config != NULL && config->game_id != GAME_UNKNOWN;
}

static inline uint16_t read16_le(const uint8_t* ptr) {
    return (uint16_t)ptr[0] | ((uint16_t)ptr[1] << 8);
}

static inline uint32_t read32_le(const uint8_t* ptr) {
    return (uint32_t)ptr[0] |
           ((uint32_t)ptr[1] << 8) |
           ((uint32_t)ptr[2] << 16) |
           ((uint32_t)ptr[3] << 24);
}

bool pokemon_parse_single(const uint8_t* raw_bytes, bool is_party_mon, ParsedPokemon* out) {
    return pokemon_parse_single_layout(raw_bytes, is_party_mon, PKMN_STORAGE_VANILLA_GEN3, out);
}

/**
 * Shiny verdict for the expansion layout.
 *
 * H&S 2.0.5 computes `(shinyValue < shinyOdds) ^ shinyModifier`, and `shinyOdds` is read from
 * the player's `SaveBlock3.challengeSettings.tx_Features_ShinyChance`, selecting from
 * {8, 16, 32, 64, 128}. DualDex does not read SaveBlock3 challenge settings in this phase, so
 * the verdict is only reported when every possible odds value agrees:
 *   - shinyValue < 8    -> shiny for all odds;
 *   - shinyValue >= 128 -> not shiny for all odds;
 *   - otherwise the odds decide, and DualDex reports UNKNOWN rather than inventing a value.
 */
static PokemonShinyState expansion_shiny_state(uint16_t shiny_value, uint8_t shiny_modifier) {
    bool base;
    if (shiny_value < 8) {
        base = true;
    } else if (shiny_value >= 128) {
        base = false;
    } else {
        return PKMN_SHINY_UNKNOWN;
    }

    bool shiny = base ^ (shiny_modifier != 0);
    return shiny ? PKMN_SHINY_YES : PKMN_SHINY_NO;
}

bool pokemon_parse_single_layout(
    const uint8_t* raw_bytes,
    bool is_party_mon,
    PokemonStorageLayout storage_layout,
    ParsedPokemon* out
) {
    if (!raw_bytes || !out) return false;

    memset(out, 0, sizeof(ParsedPokemon));
    out->storage_layout = storage_layout;

    const RawGbaPokemon* raw = (const RawGbaPokemon*)raw_bytes;

    // Check if empty slot
    if (raw->pid == 0 && raw->otid == 0) {
        out->is_empty = true;
        return false;
    }

    out->pid = raw->pid;
    out->tid = (uint16_t)(raw->otid & 0xFFFF);
    out->sid = (uint16_t)((raw->otid >> 16) & 0xFFFF);

    // Decode nickname & OT name
    pokemon_decode_string(raw->nickname, sizeof(raw->nickname), out->nickname, sizeof(out->nickname));
    pokemon_decode_string(raw->ot_name, sizeof(raw->ot_name), out->ot_name, sizeof(out->ot_name));

    // 1. Decrypt 48 bytes of substructures
    uint32_t key = raw->pid ^ raw->otid;
    uint32_t decrypted_words[12];
    const uint8_t* raw_subs = raw->raw_substructures;

    // CFRU hacks (Pokemon Unbound, Radical Red) store party substructures in a
    // FIXED GAEM order and leave the checksum word (offset 0x1C) zeroed — the
    // value is reused by the CFRU battle engine. Vanilla Gen 3 party mons always
    // carry a non-zero checksum, so a zero checksum is a reliable CFRU signature:
    // read the raw substructures directly and skip checksum validation.
    uint32_t order;
    if (raw->checksum == 0) {
        for (int i = 0; i < 12; i++) {
            decrypted_words[i] = read32_le(raw_subs + (i * 4));
        }
        order = 0; // fixed GAEM order: Growth, Attacks, EVs, Misc
    } else {
        // Attempt A: Standard GBA encryption (XOR with pid ^ otid)
        for (int i = 0; i < 12; i++) {
            decrypted_words[i] = read32_le(raw_subs + (i * 4)) ^ key;
        }

        // Checksum verification
        uint16_t calc_checksum = 0;
        const uint8_t* dec_u8 = (const uint8_t*)decrypted_words;
        for (int i = 0; i < 24; i++) {
            calc_checksum += read16_le(dec_u8 + (i * 2));
        }

        order = raw->pid % 24;

        if (calc_checksum != raw->checksum) {
            // Attempt B: Unencrypted substructures (key = 0, as in some decompilation hacks)
            uint16_t raw_checksum = 0;
            for (int i = 0; i < 24; i++) {
                raw_checksum += read16_le(raw_subs + (i * 2));
            }

            if (raw_checksum == raw->checksum) {
                for (int i = 0; i < 12; i++) {
                    decrypted_words[i] = read32_le(raw_subs + (i * 4));
                }
                calc_checksum = raw_checksum;
            } else {
                out->is_valid = false;
                return false;
            }
        }
    }
    out->is_valid = true;

    // Locate substructures G, A, E, M using permutation table
    const uint8_t* dec_bytes = (const uint8_t*)decrypted_words;
    const uint8_t* g_ptr = dec_bytes + (SUBSTRUCT_BLOCK_INDEX[order][0] * 12);
    const uint8_t* a_ptr = dec_bytes + (SUBSTRUCT_BLOCK_INDEX[order][1] * 12);
    const uint8_t* e_ptr = dec_bytes + (SUBSTRUCT_BLOCK_INDEX[order][2] * 12);
    const uint8_t* m_ptr = dec_bytes + (SUBSTRUCT_BLOCK_INDEX[order][3] * 12);

    // 4. Growth (G)
    // In pokeemerald-expansion: species is 11 bits (0..2047), bits 11..15 are teraType (0..30)
    uint16_t raw_species = read16_le(g_ptr + 0);
    out->species = raw_species & 0x07FF;

    uint16_t raw_item = read16_le(g_ptr + 2);
    out->held_item = raw_item & 0x03FF;

    uint32_t raw_exp = read32_le(g_ptr + 4);
    out->experience = raw_exp & 0x001FFFFF;

    uint8_t pp_bonuses = g_ptr[8];
    out->pp_bonuses[0] = (pp_bonuses >> 0) & 0x03;
    out->pp_bonuses[1] = (pp_bonuses >> 2) & 0x03;
    out->pp_bonuses[2] = (pp_bonuses >> 4) & 0x03;
    out->pp_bonuses[3] = (pp_bonuses >> 6) & 0x03;
    out->friendship = g_ptr[9];

    // 5. Attacks (A)
    // In pokeemerald-expansion: each move is 11 bits (0..2047), evolutionTracker in upper bits
    for (int i = 0; i < 4; i++) {
        uint16_t raw_move = read16_le(a_ptr + (i * 2));
        out->moves[i] = raw_move & 0x07FF;
        out->pp[i] = a_ptr[8 + i] & 0x7F;
    }

    // 6. EVs (E)
    out->hp_ev = e_ptr[0];
    out->attack_ev = e_ptr[1];
    out->defense_ev = e_ptr[2];
    out->speed_ev = e_ptr[3];
    out->sp_attack_ev = e_ptr[4];
    out->sp_defense_ev = e_ptr[5];

    // 7. Misc (M): IVs, Egg, Ability
    //
    // Both layouts agree on the IV packing and on the egg bit; they disagree on what bit 31 of
    // the IV word means and on where the ability slot lives, so the two are resolved separately
    // rather than by reusing one game's meaning for the other.
    uint32_t iv_word = read32_le(m_ptr + 4);
    out->hp_iv = (uint8_t)((iv_word >> 0) & 0x1F);
    out->attack_iv = (uint8_t)((iv_word >> 5) & 0x1F);
    out->defense_iv = (uint8_t)((iv_word >> 10) & 0x1F);
    out->speed_iv = (uint8_t)((iv_word >> 15) & 0x1F);
    out->sp_attack_iv = (uint8_t)((iv_word >> 20) & 0x1F);
    out->sp_defense_iv = (uint8_t)((iv_word >> 25) & 0x1F);
    out->is_egg = (bool)((iv_word >> 30) & 0x01);

    uint16_t pid_hi = (uint16_t)((out->pid >> 16) & 0xFFFF);
    uint16_t pid_lo = (uint16_t)(out->pid & 0xFFFF);
    out->shiny_value = (uint16_t)((out->tid ^ out->sid ^ pid_hi ^ pid_lo) & 0xFFFF);

    if (storage_layout == PKMN_STORAGE_EXPANSION) {
        // pokeemerald-expansion: bit 31 of the IV word is `gigantamaxFactor`.
        // `abilityNum` is 2 bits at 29..30 of the word at PokemonSubstruct3 offset 8.
        out->gigantamax_factor = (bool)((iv_word >> 31) & 0x01);
        uint32_t ribbon_word = read32_le(m_ptr + 8);
        out->ability_num = (uint8_t)((ribbon_word >> 29) & 0x03);
        out->ability_slot = out->ability_num;
        out->ability_slot_known = true;

        // `hiddenNatureModifier` is 5 bits at bits 3..7 of the byte at BoxPokemon 0x12 and XORs
        // the personality nature into the stat-effective "mint" nature used by
        // CalculateMonStats(). GetNature() (and the summary screen's displayed nature) stays
        // pid % 25.
        uint8_t nature_byte = raw_bytes[0x12];
        uint8_t hidden_nature_modifier = (uint8_t)((nature_byte >> 3) & 0x1F);
        // `shinyModifier` is bit 14 of the 16-bit word at BoxPokemon 0x1E.
        uint16_t shiny_word = read16_le(raw_bytes + 0x1E);
        out->shiny_modifier = (uint8_t)((shiny_word >> 14) & 0x01);
        out->shiny_state = expansion_shiny_state(out->shiny_value, out->shiny_modifier);
        out->is_shiny = (out->shiny_state == PKMN_SHINY_YES);
        out->hidden_nature_modifier = hidden_nature_modifier;
        out->hidden_nature = (uint8_t)((out->pid % 25) ^ hidden_nature_modifier);
        out->nature_modified = (hidden_nature_modifier != 0);
    } else {
        // Vanilla Gen 3: bit 31 of the IV word is the ability slot. Unchanged.
        out->ability_slot = (uint8_t)((iv_word >> 31) & 0x01);
        out->ability_slot_known = true;
        out->ability_num = out->ability_slot;
    }

    // 8. Derived Properties
    // Displayed nature is pid % 25 in both layouts: H&S's GetNature() is
    // `personality % NUM_NATURES` and the summary screen stores it in `sum->nature`.
    out->nature = (uint8_t)(out->pid % 25);
    out->nature_name = pokemon_get_nature_name(out->nature);
    if (storage_layout != PKMN_STORAGE_EXPANSION) {
        out->hidden_nature = out->nature;
    }

    if (storage_layout != PKMN_STORAGE_EXPANSION) {
        out->is_shiny = (((out->tid ^ out->sid) ^ (pid_hi ^ pid_lo)) < 8);
        out->shiny_state = out->is_shiny ? PKMN_SHINY_YES : PKMN_SHINY_NO;
    }

    // 9. Battle stats (if party Pokémon, offsets 0x50 - 0x63)
    if (is_party_mon) {
        out->status_condition = raw->status_condition;
        out->level = raw->level;
        out->current_hp = raw->current_hp;
        out->max_hp = raw->max_hp;
        out->attack = raw->attack;
        out->defense = raw->defense;
        out->speed = raw->speed;
        out->sp_attack = raw->sp_attack;
        out->sp_defense = raw->sp_defense;

        // A valid party Pokémon MUST have real runtime battle stats.
        // Box Pokémon (80 bytes) do not have battle stats (level and max_hp are 0).
        if (out->level == 0 || out->level > 100 ||
            out->max_hp == 0 || out->max_hp > 2000 ||
            out->current_hp > out->max_hp) {
            out->is_valid = false;
            return false;
        }
    } else {
        out->level = 0;
    }

    return (out->species > 0 && out->species < 2000);
}

static uint32_t s_cached_player_party_offset = 0;
static uint32_t s_cached_enemy_party_offset = 0;

void pokemon_reader_reset(void) {
    s_cached_player_party_offset = 0;
    s_cached_enemy_party_offset = 0;
}

static uint8_t scan_ewram_for_party_layout(
    const uint8_t* ewram,
    size_t ewram_size,
    PokemonStorageLayout storage_layout,
    PartySnapshot* out_snapshot
);

static bool parse_party_mon(
    const uint8_t* raw,
    PokemonStorageLayout storage_layout,
    ParsedPokemon* out
);

uint8_t pokemon_scan_ewram_for_party(
    const uint8_t* ewram,
    size_t ewram_size,
    PartySnapshot* out_snapshot
) {
    // The layout-blind entry point keeps vanilla semantics; layout-aware callers go through the
    // implementation below with their own storage layout.
    return scan_ewram_for_party_layout(ewram, ewram_size, PKMN_STORAGE_VANILLA_GEN3, out_snapshot);
}

static uint8_t scan_ewram_for_party_layout(
    const uint8_t* ewram,
    size_t ewram_size,
    PokemonStorageLayout storage_layout,
    PartySnapshot* out_snapshot
) {
    if (!ewram || ewram_size < sizeof(RawGbaPokemon) || !out_snapshot) return 0;

    memset(out_snapshot, 0, sizeof(PartySnapshot));

    size_t best_offset = 0;
    int best_score = -1;
    PartySnapshot best_snapshot;
    memset(&best_snapshot, 0, sizeof(PartySnapshot));

    size_t max_offset = ewram_size - sizeof(RawGbaPokemon);
    for (size_t off = 0; off <= max_offset; off += 4) {
        const RawGbaPokemon* raw = (const RawGbaPokemon*)(ewram + off);

        // Fast rejection filter - party mon MUST have valid battle stats
        if (raw->pid == 0 || raw->otid == 0) continue;
        if (raw->max_hp == 0 || raw->max_hp > 2000) continue;
        if (raw->current_hp > raw->max_hp) continue;
        if (raw->level == 0 || raw->level > 100) continue;
        if (raw->attack == 0 || raw->defense == 0) continue;

        // Substructure decryption & parsing
        ParsedPokemon test_mon;
        if (!parse_party_mon((const uint8_t*)raw, storage_layout, &test_mon)) continue;
        if (test_mon.species == 0 || test_mon.species >= 2000) continue;

        // Ensure this is slot 0 of the party, not slot 1-5.
        // If the preceding 100 bytes is ALREADY a valid party Pokemon, skip 'off'
        if (off >= sizeof(RawGbaPokemon)) {
            ParsedPokemon prev_mon;
            if (parse_party_mon(ewram + off - sizeof(RawGbaPokemon), storage_layout, &prev_mon)) {
                if (prev_mon.species > 0 && prev_mon.species < 2000) {
                    continue; // Skip: preceding slot is part of the party
                }
            }
        }

        // Candidate party starting at 'off'
        PartySnapshot candidate_snap;
        memset(&candidate_snap, 0, sizeof(PartySnapshot));
        candidate_snap.members[candidate_snap.count++] = test_mon;

        for (uint8_t slot = 1; slot < 6; slot++) {
            size_t next_off = off + (slot * sizeof(RawGbaPokemon));
            if (next_off + sizeof(RawGbaPokemon) > ewram_size) break;
            ParsedPokemon next_mon;
            if (parse_party_mon(ewram + next_off, storage_layout, &next_mon)) {
                if (next_mon.species > 0 && next_mon.species < 2000) {
                    candidate_snap.members[candidate_snap.count++] = next_mon;
                } else break;
            } else break;
        }

        // Calculate confidence score for this candidate
        int score = candidate_snap.count * 100;

        // Live EWRAM .bss section bonus: live game party resides in BSS (>= 0x20000).
        // Low memory (< 0x20000) holds SaveBlock serialization buffers which are static snapshots.
        if (off >= 0x20000) {
            score += 1000;
        }

        // Check preceding 4 bytes for gPlayerPartyCount matching candidate_snap.count
        bool party_count_matched = false;
        if (off >= 4) {
            for (int b = 1; b <= 4; b++) {
                if (ewram[off - b] == candidate_snap.count) {
                    party_count_matched = true;
                    break;
                }
            }
        }
        if (party_count_matched) {
            score += 500;
        }

        // Battle stats realism bonus
        for (uint8_t i = 0; i < candidate_snap.count; i++) {
            const ParsedPokemon* m = &candidate_snap.members[i];
            if (m->max_hp >= 10 && m->attack > 0 && m->defense > 0 && m->speed > 0) {
                score += 50;
            }
            if (m->moves[0] > 0) {
                score += 20;
            }
        }

        // Empty slot following party check
        if (candidate_snap.count < 6) {
            size_t next_empty_off = off + (candidate_snap.count * sizeof(RawGbaPokemon));
            if (next_empty_off + sizeof(RawGbaPokemon) <= ewram_size) {
                const RawGbaPokemon* empty_raw = (const RawGbaPokemon*)(ewram + next_empty_off);
                if (empty_raw->pid == 0 && empty_raw->otid == 0) {
                    score += 50;
                }
            }
        }

        if (score >= best_score) {
            best_score = score;
            best_offset = off;
            best_snapshot = candidate_snap;
        }
    }

    if (best_score > 0 && best_snapshot.count > 0) {
        *out_snapshot = best_snapshot;
        s_cached_player_party_offset = (uint32_t)best_offset;
        s_cached_enemy_party_offset = (uint32_t)(best_offset + (6 * sizeof(RawGbaPokemon)));
        LOG_PARTY("EWRAM scan found party at offset 0x%X (count=%d, score=%d, lead='%s', species=%d, lvl=%d, hp=%d/%d)",
                  (unsigned int)best_offset, best_snapshot.count, best_score,
                  best_snapshot.members[0].nickname, best_snapshot.members[0].species,
                  best_snapshot.members[0].level, best_snapshot.members[0].current_hp,
                  best_snapshot.members[0].max_hp);
        return best_snapshot.count;
    }

    return 0;
}

/**
 * EWRAM-relative offset of `gBattlerPartyIndexes`, or 0 when the layout cannot supply it.
 *
 * Layouts that carry compiled symbol evidence declare `battler_party_indexes_offset` directly.
 * The remaining layouts fall back to the historical `gBattleMons - 24` back-off that shipped
 * before any symbol evidence existed: it is retained ONLY so that FireRed/Emerald behaviour is
 * unchanged, is never applied to a layout that declares the symbol (Heart & Soul 2.0.5 declares
 * it, and on 2.0.5 the real distance is 732 bytes, not 24), and is explicitly unverified for
 * the vanilla games.
 */
#define LEGACY_BATTLER_PARTY_INDEXES_BACKOFF 24u

static uint32_t battler_party_indexes_offset(const GameMemoryConfig* config) {
    if (!config) return 0;
    if (config->battler_party_indexes_offset != 0) return config->battler_party_indexes_offset;
    if (config->battle_mons_offset >= LEGACY_BATTLER_PARTY_INDEXES_BACKOFF) {
        return config->battle_mons_offset - LEGACY_BATTLER_PARTY_INDEXES_BACKOFF;
    }
    return 0;
}

/**
 * Parse one party-slot candidate using the storage layout the caller's configuration declares.
 * A NULL configuration keeps the vanilla layout, which is what every FireRed/Emerald path uses.
 */
static bool parse_party_mon(
    const uint8_t* raw,
    PokemonStorageLayout storage_layout,
    ParsedPokemon* out
) {
    return pokemon_parse_single_layout(raw, true, storage_layout, out);
}

static void sync_live_player_battle_mon(
    const uint8_t* ewram,
    size_t ewram_size,
    const GameMemoryConfig* config,
    PartySnapshot* out_snapshot
) {
    if (!config || config->battle_mons_offset == 0 ||
        config->battle_mons_offset + config->battle_mons_size > ewram_size ||
        out_snapshot->count == 0) {
        return;
    }

    const uint8_t* b0 = ewram + config->battle_mons_offset;
    uint16_t b0_species = read16_le(b0);
    if (b0_species == 0 || b0_species >= 2000) return;

    uint16_t b0_hp = read16_le(b0 + config->battle_mons_hp_offset);
    // Battler 0's party slot comes from gBattlerPartyIndexes[0], an independent symbol.
    uint32_t indexes_offset = battler_party_indexes_offset(config);
    if (indexes_offset != 0 && indexes_offset + 2 <= ewram_size) {
        uint16_t b0_idx = read16_le(ewram + indexes_offset);
        if (b0_idx < out_snapshot->count && out_snapshot->members[b0_idx].species == b0_species) {
            if (b0_hp <= out_snapshot->members[b0_idx].max_hp) {
                out_snapshot->members[b0_idx].current_hp = b0_hp;
            }
            out_snapshot->active_battler_slot = (int8_t)b0_idx;
        }
    }

    // A species match alone cannot identify an active party slot (duplicates are valid).
    // Without gBattlerPartyIndexes evidence, retain -1 rather than inventing a slot.
}

static void sync_live_enemy_battle_mon(
    const uint8_t* ewram,
    size_t ewram_size,
    const GameMemoryConfig* config,
    PartySnapshot* out_snapshot
) {
    if (out_snapshot->count == 0) return;

    if (!config || config->battle_mons_offset == 0 ||
        config->battle_mons_offset + (2 * config->battle_mons_size) > ewram_size) {
        return;
    }

    const uint8_t* b1 = ewram + config->battle_mons_offset + config->battle_mons_size;
    uint16_t b1_species = read16_le(b1);
    if (b1_species == 0 || b1_species >= 2000) {
        return;
    }

    uint16_t b1_hp = read16_le(b1 + config->battle_mons_hp_offset);
    // Battler 1's party slot is gBattlerPartyIndexes[1]: a u16 array indexed by battler.
    uint32_t indexes_offset = battler_party_indexes_offset(config);
    if (indexes_offset != 0 && indexes_offset + 4 <= ewram_size) {
        uint16_t b1_idx = read16_le(ewram + indexes_offset + 2);
        if (b1_idx < out_snapshot->count && out_snapshot->members[b1_idx].species == b1_species) {
            if (b1_hp <= out_snapshot->members[b1_idx].max_hp) {
                out_snapshot->members[b1_idx].current_hp = b1_hp;
            }
            out_snapshot->active_battler_slot = (int8_t)b1_idx;
        }
    }

    // Do not infer an opponent slot from species/HP heuristics; duplicates and transitions are
    // ambiguous.  Only the indexed controller-side mapping above may establish an active slot.

}

static uint8_t read_authoritative_player_party(
    const uint8_t* ewram,
    size_t ewram_size,
    const GameMemoryConfig* config,
    PartySnapshot* out_snapshot
) {
    // 1. Config validation: must declare player_party_offset and player_party_count_offset
    if (config->player_party_offset == 0 || config->player_party_count_offset == 0) {
        s_cached_player_party_offset = 0;
        return 0;
    }

    // 2. Count offset bounds check
    if (config->player_party_count_offset >= ewram_size) {
        s_cached_player_party_offset = 0;
        return 0;
    }

    // 3. Read authoritative count
    uint8_t count = ewram[config->player_party_count_offset];

    // 4. Validate count range:
    // A count of 0 is a legitimate empty party at boot.
    // Return empty snapshot immediately, clear any cached offset, and DO NOT scan EWRAM.
    if (count == 0) {
        s_cached_player_party_offset = 0;
        return 0;
    }

    // Counts outside 0..6 are invalid. Fail closed, clear cache, and DO NOT scan.
    if (count > 6) {
        s_cached_player_party_offset = 0;
        return 0;
    }

    // 5. Bounds check the entire count * sizeof(RawGbaPokemon) range
    size_t party_bytes = (size_t)count * sizeof(RawGbaPokemon);
    if (config->player_party_offset + party_bytes > ewram_size) {
        s_cached_player_party_offset = 0;
        return 0;
    }

    // 6. Parse exactly count slots using config->storage_layout.
    // Every claimed occupied slot must parse successfully. If any slot is corrupt or
    // fails validation, fail closed (all-or-nothing), clear cache, and do not scan.
    for (uint8_t i = 0; i < count; i++) {
        const uint8_t* mon_ptr = ewram + config->player_party_offset + (i * sizeof(RawGbaPokemon));
        if (!parse_party_mon(mon_ptr, config->storage_layout, &out_snapshot->members[i]) ||
            out_snapshot->members[i].species == 0 ||
            out_snapshot->members[i].species >= 2000) {
            // Corrupt or invalid authoritative slot: fail closed all-or-nothing
            s_cached_player_party_offset = 0;
            memset(out_snapshot, 0, sizeof(PartySnapshot));
            out_snapshot->active_battler_slot = -1;
            return 0;
        }
    }

    // 7. Authoritative snapshot valid:
    // Snapshot count is bounded to exactly the authoritative count.
    // Slots count..5 remain zeroed from the initial memset in pokemon_read_player_party.
    out_snapshot->count = count;
    s_cached_player_party_offset = (uint32_t)config->player_party_offset;

    // 8. Synchronize live battle HP if in battle
    sync_live_player_battle_mon(ewram, ewram_size, config, out_snapshot);

    return count;
}

static uint8_t read_heuristic_player_party(
    const uint8_t* ewram,
    size_t ewram_size,
    const GameMemoryConfig* config,
    PartySnapshot* out_snapshot
) {
    // 1. Try configured static offset (fast, reliable path for vanilla & supported hacks)
    if (config->player_party_offset + sizeof(RawGbaPokemon) <= ewram_size) {
        ParsedPokemon first_mon;
        const uint8_t* mon_ptr = ewram + config->player_party_offset;
        if (parse_party_mon(mon_ptr, config->storage_layout, &first_mon) && first_mon.species > 0 && first_mon.species < 2000) {
            uint8_t valid_count = 0;
            out_snapshot->members[valid_count++] = first_mon;
            for (uint8_t i = 1; i < 6; i++) {
                const uint8_t* p = ewram + config->player_party_offset + (i * sizeof(RawGbaPokemon));
                if (parse_party_mon(p, config->storage_layout, &out_snapshot->members[valid_count])) {
                    if (out_snapshot->members[valid_count].species > 0 && out_snapshot->members[valid_count].species < 2000) {
                        valid_count++;
                    } else break;
                } else break;
            }

            bool count_verified = true;
            if (config->player_party_count_offset > 0 && config->player_party_count_offset < ewram_size) {
                uint8_t expected_count = ewram[config->player_party_count_offset];
                if (expected_count != valid_count || expected_count == 0 || expected_count > 6) {
                    count_verified = false;
                }
            }

            if (count_verified && valid_count > 0) {
                out_snapshot->count = valid_count;
                s_cached_player_party_offset = (uint32_t)config->player_party_offset;
                sync_live_player_battle_mon(ewram, ewram_size, config, out_snapshot);
                return valid_count;
            }
        }
    }

    // 2. Try cached dynamically scanned offset
    if (s_cached_player_party_offset > 0 && s_cached_player_party_offset + sizeof(RawGbaPokemon) <= ewram_size) {
        ParsedPokemon first_mon;
        const uint8_t* mon_ptr = ewram + s_cached_player_party_offset;
        if (parse_party_mon(mon_ptr, config->storage_layout, &first_mon) && first_mon.species > 0 && first_mon.species < 2000) {
            uint8_t valid_count = 0;
            out_snapshot->members[valid_count++] = first_mon;
            for (uint8_t i = 1; i < 6; i++) {
                const uint8_t* p = ewram + s_cached_player_party_offset + (i * sizeof(RawGbaPokemon));
                if (parse_party_mon(p, config->storage_layout, &out_snapshot->members[valid_count])) {
                    if (out_snapshot->members[valid_count].species > 0 && out_snapshot->members[valid_count].species < 2000) {
                        valid_count++;
                    } else break;
                } else break;
            }
            out_snapshot->count = valid_count;
            sync_live_player_battle_mon(ewram, ewram_size, config, out_snapshot);
            return valid_count;
        } else {
            s_cached_player_party_offset = 0;
        }
    }

    // 3. Fallback: Dynamic EWRAM Pattern Scan (ROM hacks, custom builds)
    uint8_t count = scan_ewram_for_party_layout(ewram, ewram_size, config->storage_layout, out_snapshot);
    if (count > 0) {
        sync_live_player_battle_mon(ewram, ewram_size, config, out_snapshot);
    }
    return count;
}

uint8_t pokemon_read_player_party(
    const uint8_t* ewram,
    size_t ewram_size,
    const GameMemoryConfig* config,
    PartySnapshot* out_snapshot
) {
    if (!ewram || !out_snapshot) return 0;
    memset(out_snapshot, 0, sizeof(PartySnapshot));
    out_snapshot->active_battler_slot = -1;

    // Fail closed: without an explicitly supported layout there is no party to read. Neither the
    // static offsets, the cached scan offset, nor the blind EWRAM scan may run for an unknown
    // game, or arbitrary memory would be presented as a party.
    if (!config_is_usable(config)) return 0;

    if (config->player_party_policy == PARTY_DISCOVERY_AUTHORITATIVE_STATIC) {
        return read_authoritative_player_party(ewram, ewram_size, config, out_snapshot);
    }
    return read_heuristic_player_party(ewram, ewram_size, config, out_snapshot);
}

uint8_t pokemon_read_enemy_party(
    const uint8_t* ewram,
    size_t ewram_size,
    const GameMemoryConfig* config,
    PartySnapshot* out_snapshot
) {
    if (!ewram || !out_snapshot) return 0;
    memset(out_snapshot, 0, sizeof(PartySnapshot));
    out_snapshot->active_battler_slot = -1;

    // Fail closed: enemy-party candidates are derived from layout offsets, so an unknown game
    // must not be scanned at all.
    if (!config_is_usable(config)) return 0;

    // Runtime boundary: As documented in docs/HNS_2_0_5_COMPATIBILITY_EVIDENCE.md and PR #41,
    // enemy_party_count_offset is compiled and configured from symbols, but runtime consumption
    // of gEnemyPartyCount to authoritatively bound enemy party parsing or prune stale slots remains
    // pending live battle/runtime validation (tracked in #1) before H&S can become VERIFIED.
    // This PR strictly addresses the player party blind-scan false positive problem (#42).

    // Player party must be located to know the player's OTID
    if (s_cached_player_party_offset == 0 || s_cached_player_party_offset + sizeof(RawGbaPokemon) > ewram_size) {
        return 0;
    }

    const RawGbaPokemon* player_mon = (const RawGbaPokemon*)(ewram + s_cached_player_party_offset);
    uint32_t player_otid = player_mon->otid;

    // Candidate enemy party locations:
    // 1. Configured enemy_party_offset (e.g. 0x345A4 for Heart & Soul, 0x24744 for Emerald)
    // 2. Expansion layout: player_offset + 1200 (gPlayerParty 600B + gPlayerPartyBackup 600B)
    // 3. Standard layout: player_offset + 600 (gPlayerParty 600B)
    // 4. Cached dynamic offset
    size_t candidate_offsets[4];
    int num_candidates = 0;

    if (config && config->enemy_party_offset > 0 && config->enemy_party_offset + sizeof(RawGbaPokemon) <= ewram_size) {
        if (config->enemy_party_offset != s_cached_player_party_offset) {
            candidate_offsets[num_candidates++] = config->enemy_party_offset;
        }
    }
    if (s_cached_enemy_party_offset > 0 && s_cached_enemy_party_offset + sizeof(RawGbaPokemon) <= ewram_size) {
        candidate_offsets[num_candidates++] = s_cached_enemy_party_offset;
    }
    if (s_cached_player_party_offset + (12 * sizeof(RawGbaPokemon)) + sizeof(RawGbaPokemon) <= ewram_size) {
        candidate_offsets[num_candidates++] = s_cached_player_party_offset + (12 * sizeof(RawGbaPokemon));
    }
    if (s_cached_player_party_offset + (6 * sizeof(RawGbaPokemon)) + sizeof(RawGbaPokemon) <= ewram_size) {
        candidate_offsets[num_candidates++] = s_cached_player_party_offset + (6 * sizeof(RawGbaPokemon));
    }

    for (int c = 0; c < num_candidates; c++) {
        size_t off = candidate_offsets[c];
        const RawGbaPokemon* raw = (const RawGbaPokemon*)(ewram + off);

        // Fast reject: not an enemy mon
        if (raw->pid == 0 || raw->otid == 0 || raw->otid == player_otid) continue;
        if (raw->max_hp == 0 || raw->max_hp > 2000) continue;
        if (raw->current_hp > raw->max_hp) continue;
        // Do NOT reject fainted mon (raw->current_hp == 0)! Slot 0 is still slot 0 when fainted!
        if (raw->level == 0 || raw->level > 100) continue;
        if (raw->attack == 0 || raw->defense == 0) continue;

        ParsedPokemon test_mon;
        if (!parse_party_mon((const uint8_t*)raw, config->storage_layout, &test_mon)) continue;
        if (test_mon.species == 0 || test_mon.species >= 2000) continue;
        // Do NOT reject test_mon.current_hp == 0!

        // Found active enemy party!
        uint8_t count = 0;
        out_snapshot->members[count++] = test_mon;
        for (uint8_t slot = 1; slot < 6; slot++) {
            size_t next_off = off + (slot * sizeof(RawGbaPokemon));
            if (next_off + sizeof(RawGbaPokemon) > ewram_size) break;
            ParsedPokemon next_mon;
            if (parse_party_mon(ewram + next_off, config->storage_layout, &next_mon)) {
                if (next_mon.species > 0 && next_mon.species < 2000) {
                    out_snapshot->members[count++] = next_mon;
                } else break;
            } else break;
        }
        out_snapshot->count = count;
        s_cached_enemy_party_offset = (uint32_t)off;

        sync_live_enemy_battle_mon(ewram, ewram_size, config, out_snapshot);
        return count;
    }

    // Fallback: Full EWRAM scan for an active enemy party (wild or trainer battle)
    // Live parties in GBA games reside in the BSS area (>= 0x20000)
    size_t scan_start = 0x20000;
    if (scan_start + sizeof(RawGbaPokemon) < ewram_size) {
        size_t scan_end = ewram_size - sizeof(RawGbaPokemon);
        for (size_t off = scan_start; off <= scan_end; off += 4) {
            if (off == s_cached_player_party_offset) continue;
            const RawGbaPokemon* raw = (const RawGbaPokemon*)(ewram + off);
            if (raw->pid == 0 || raw->otid == 0 || raw->otid == player_otid) continue;
            if (raw->max_hp == 0 || raw->max_hp > 2000) continue;
            if (raw->current_hp > raw->max_hp) continue;
            if (raw->level == 0 || raw->level > 100) continue;
            if (raw->attack == 0 || raw->defense == 0) continue;

            // Ensure this is slot 0 of enemy party, not slot 1-5
            if (off >= sizeof(RawGbaPokemon)) {
                ParsedPokemon prev_mon;
                if (parse_party_mon(ewram + off - sizeof(RawGbaPokemon), config->storage_layout, &prev_mon)) {
                    if (prev_mon.species > 0 && prev_mon.species < 2000) {
                        continue; // Preceding slot is already a party mon
                    }
                }
            }

            ParsedPokemon test_mon;
            if (!parse_party_mon((const uint8_t*)raw, config->storage_layout, &test_mon)) continue;
            if (test_mon.species == 0 || test_mon.species >= 2000) continue;

            // Found enemy party via scan!
            uint8_t count = 0;
            out_snapshot->members[count++] = test_mon;
            for (uint8_t slot = 1; slot < 6; slot++) {
                size_t next_off = off + (slot * sizeof(RawGbaPokemon));
                if (next_off + sizeof(RawGbaPokemon) > ewram_size) break;
                ParsedPokemon next_mon;
                if (parse_party_mon(ewram + next_off, config->storage_layout, &next_mon)) {
                    if (next_mon.species > 0 && next_mon.species < 2000) {
                        out_snapshot->members[count++] = next_mon;
                    } else break;
                } else break;
            }
            out_snapshot->count = count;
            s_cached_enemy_party_offset = (uint32_t)off;

            sync_live_enemy_battle_mon(ewram, ewram_size, config, out_snapshot);
            return count;
        }
    }

    s_cached_enemy_party_offset = 0;
    return 0;
}

bool pokemon_read_player_location(
    const uint8_t* ewram,
    size_t ewram_size,
    const GameMemoryConfig* config,
    PlayerLocationRaw* out_location
) {
    return pokemon_read_player_location_gba(NULL, NULL, ewram, ewram_size, config, out_location);
}

/**
 * Resolve the active SaveBlock1 base through the compiled `gSaveBlock1Ptr` symbol.
 *
 * Heart & Soul 2.0.5 calls `SetSaveBlocksPointers()`, which does
 *     gSaveBlock1Ptr = (void *)(&gSaveblock1) + ((offset + Random()) & (SAVEBLOCK_MOVE_RANGE - 4));
 * so the active SaveBlock1 base is NOT the EWRAM base and not even a fixed address: it moves by
 * a 4-byte-aligned amount inside a 128-byte window on every save-block rebuild.
 *
 * The pointer is therefore read from IWRAM on every call, validated against the exact compiled
 * window, and translated through the region-checked reader. A pointer that is unreadable,
 * misaligned, outside the window, or whose SaveBlock1 would not fit in EWRAM fails closed; no
 * EWRAM scan or "plausible-looking" base is ever substituted.
 *
 * @param out_base Receives the absolute GBA address of the active SaveBlock1.
 */
static bool resolve_save_block1_base(
    DualDexGbaReadFn read,
    void* user,
    const GameMemoryConfig* config,
    uint32_t* out_base
) {
    if (!read || !config || !out_base) return false;
    if (config->save_block1_ptr_gba_address == 0) return false;
    if (config->save_block1_base_gba_address == 0 || config->save_block1_aslr_range == 0) return false;

    uint8_t raw[4];
    if (!read(user, config->save_block1_ptr_gba_address, raw, sizeof(raw))) {
        return false; // gSaveBlock1Ptr is not readable through a verified region.
    }

    uint32_t base = read32_le(raw);

    // The pointer is a 32-bit GBA address; anything outside 32-bit EWRAM is already invalid.
    if (base < DUALDEX_GBA_EWRAM_BASE ||
        base >= DUALDEX_GBA_EWRAM_BASE + DUALDEX_GBA_EWRAM_SIZE) {
        return false;
    }

    // `SetSaveBlocksPointers` aligns the offset to 4 bytes, so an unaligned base did not come
    // from the game's own save-block setup.
    if ((base & 0x3u) != 0) return false;

    // The randomized window starts at the compiled gSaveblock1 symbol. Requiring the base to
    // fall inside it rejects both a stale pointer from another ROM and EWRAM base itself.
    uint32_t window_start = config->save_block1_base_gba_address;
    uint32_t window_last = window_start + config->save_block1_aslr_range - 4;
    if (base < window_start || base > window_last) return false;

    // The whole SaveBlock1 must still live inside EWRAM from that base.
    if (config->save_block1_size == 0) return false;
    if (base > DUALDEX_GBA_EWRAM_BASE + DUALDEX_GBA_EWRAM_SIZE - config->save_block1_size) {
        return false;
    }

    *out_base = base;
    return true;
}

static bool decode_location_fields(
    const uint8_t* sb1,
    size_t sb1_available,
    const GameMemoryConfig* config,
    PlayerLocationRaw* out_location
) {
    // The highest field this reader touches is escapeWarp (WarpData, 8 bytes) and the reader
    // only proceeds when the whole span is present, so a truncated range can never be read past.
    size_t needed = config->save_block1_escape_warp_offset + 8;
    if (config->save_block1_location_offset + 8 > needed) {
        needed = config->save_block1_location_offset + 8;
    }
    if (config->save_block1_pos_offset + 4 > needed) {
        needed = config->save_block1_pos_offset + 4;
    }
    if (sb1_available < needed) return false;

    const uint8_t* pos = sb1 + config->save_block1_pos_offset;
    const uint8_t* location = sb1 + config->save_block1_location_offset;
    const uint8_t* escape = sb1 + config->save_block1_escape_warp_offset;

    // Coords16 pos
    int16_t pos_x = (int16_t)read16_le(pos + 0);
    int16_t pos_y = (int16_t)read16_le(pos + 2);

    // WarpData location: s8 mapGroup, s8 mapNum, s8 warpId, padding, s16 x, s16 y
    int16_t map_group = (int16_t)(int8_t)location[0];
    int16_t map_num = (int16_t)(int8_t)location[1];
    int8_t warp_id = (int8_t)location[2];
    int16_t warp_x = (int16_t)read16_le(location + 4);
    int16_t warp_y = (int16_t)read16_le(location + 6);

    // WarpData escapeWarp
    int16_t esc_group = (int16_t)(int8_t)escape[0];
    int16_t esc_num = (int16_t)(int8_t)escape[1];

    // Basic validity sanity check: valid map groups are typically 0..35 and map nums 0..130
    if (map_group < 0 || map_group > 35 || map_num < 0 || map_num > 130) {
        return false;
    }

    out_location->map_group = map_group;
    out_location->map_num = map_num;
    out_location->warp_id = warp_id;
    out_location->x = warp_x;
    out_location->y = warp_y;
    out_location->local_x = pos_x;
    out_location->local_y = pos_y;
    out_location->escape_map_group = esc_group;
    out_location->escape_map_num = esc_num;
    out_location->is_indoors = (map_group != 0);
    out_location->is_valid = true;
    return true;
}

bool pokemon_read_player_location_gba(
    DualDexGbaReadFn read,
    void* user,
    const uint8_t* ewram,
    size_t ewram_size,
    const GameMemoryConfig* config,
    PlayerLocationRaw* out_location
) {
    if (!out_location) return false;
    memset(out_location, 0, sizeof(PlayerLocationRaw));

    // Fail closed: an unknown game has no SaveBlock1 layout at all.
    if (!config_is_usable(config)) return false;

    // Layouts whose active SaveBlock1 base comes from a compiled pointer symbol. The pointer and
    // the fields are both read through the bounds-checked absolute-address reader, so no raw
    // pointer arithmetic escapes to a caller.
    if (config->save_block1_ptr_gba_address != 0) {
        uint32_t base = 0;
        if (!resolve_save_block1_base(read, user, config, &base)) return false;

        uint8_t sb1[64];
        size_t span = config->save_block1_escape_warp_offset + 8;
        if (config->save_block1_location_offset + 8 > span) span = config->save_block1_location_offset + 8;
        if (config->save_block1_pos_offset + 4 > span) span = config->save_block1_pos_offset + 4;
        if (span > sizeof(sb1)) return false;

        if (!read(user, base, sb1, span)) return false;
        return decode_location_fields(sb1, span, config, out_location);
    }

    // Legacy layouts: SaveBlock1 is located relative to the live party offset.
    if (!ewram || ewram_size == 0) return false;

    const uint8_t* sb1 = NULL;
    uint32_t party_off = s_cached_player_party_offset;
    if (party_off == 0) {
        if (config->player_party_offset > 0 && config->player_party_offset + 100 <= ewram_size) {
            party_off = (uint32_t)config->player_party_offset;
        }
    }

    if (party_off == 0) return false;

    // In Emerald / Ruby / Sapphire: SaveBlock1.playerParty is at offset 0x238
    // In FireRed / LeafGreen: SaveBlock1.playerParty is at offset 0x38
    bool is_firered = (config->game_id == GAME_FIRERED);
    size_t sb1_party_offset = is_firered ? 0x38 : 0x238;

    if (party_off >= sb1_party_offset && party_off - sb1_party_offset + sizeof(PlayerLocationRaw) <= ewram_size) {
        sb1 = ewram + (party_off - sb1_party_offset);
    } else {
        sb1 = ewram;
    }

    GameMemoryConfig legacy = *config;
    legacy.save_block1_pos_offset = 0x00;
    legacy.save_block1_location_offset = 0x04;
    legacy.save_block1_escape_warp_offset = 0x24;

    return decode_location_fields(sb1, ewram_size - (size_t)(sb1 - ewram), &legacy, out_location);
}

bool pokemon_read_battle_stat_stages(
    const uint8_t* ewram,
    size_t ewram_size,
    const GameMemoryConfig* config,
    uint8_t battler_index,
    int8_t out_stages[7]
) {
    if (!ewram || !config_is_usable(config) || config->battle_mons_offset == 0 ||
        config->battle_mons_offset + ((size_t)(battler_index + 1) * config->battle_mons_size) > ewram_size) {
        return false;
    }

    const uint8_t* b = ewram + config->battle_mons_offset + (battler_index * config->battle_mons_size);
    uint16_t species = read16_le(b);
    if (species == 0 || species >= 2000) return false;

    // Stat stages in struct BattlePokemon live at a layout-declared offset (0x18 for both the
    // vanilla Gen 3 and the H&S 2.0.5 layouts, but declared per game rather than assumed).
    // statStages[8]: 0=HP(unused), 1=ATK, 2=DEF, 3=SPEED, 4=SPATK, 5=SPDEF, 6=ACC, 7=EVASION
    // Default neutral stage in Gen 3 is 6 (range 0..12)
    uint32_t stages_offset = config->battle_mons_stat_stages_offset;
    if (stages_offset == 0 || stages_offset + 8 > config->battle_mons_size ||
        config->battle_mons_offset + (battler_index * config->battle_mons_size) + stages_offset + 8 > ewram_size) {
        return false;
    }
    const uint8_t* stages = b + stages_offset;
    out_stages[0] = (int8_t)((int)stages[1] - 6); // Atk
    out_stages[1] = (int8_t)((int)stages[2] - 6); // Def
    out_stages[2] = (int8_t)((int)stages[3] - 6); // Spe
    out_stages[3] = (int8_t)((int)stages[4] - 6); // SpA
    out_stages[4] = (int8_t)((int)stages[5] - 6); // SpD
    out_stages[5] = (int8_t)((int)stages[6] - 6); // Acc
    out_stages[6] = (int8_t)((int)stages[7] - 6); // Eva

    for (int i = 0; i < 7; i++) {
        if (out_stages[i] < -6) out_stages[i] = -6;
        if (out_stages[i] > 6) out_stages[i] = 6;
    }
    return true;
}

uint8_t pokemon_read_battle_ui_state(
    const uint8_t* ewram,
    size_t ewram_size,
    const GameMemoryConfig* config
) {
    if (!ewram || !config_is_usable(config) || config->battle_mons_offset == 0 ||
        config->battle_mons_offset + config->battle_mons_size > ewram_size) {
        return 0; // UNKNOWN
    }

    const uint8_t* b0 = ewram + config->battle_mons_offset;
    uint16_t b0_species = read16_le(b0);
    if (b0_species == 0 || b0_species >= 2000) {
        return 0; // NOT IN BATTLE
    }

    // EWRAM battle-mon data proves neither menu nor cursor state.  This includes a fainted
    // battler: HP == 0 may be animation, text, forced-switch transition, or opponent action.
    // Menu readers must come from authoritative controller-state evidence in future work.
    return 5;
}

uint8_t pokemon_read_battle_presence(
    const uint8_t* ewram,
    size_t ewram_size,
    const GameMemoryConfig* config
) {
    if (!ewram || !config_is_usable(config) || config->battle_mons_offset == 0 ||
        config->battle_mons_offset + config->battle_mons_size > ewram_size) {
        return 2; // UNKNOWN: reader unavailable or malformed memory range
    }

    uint16_t species = read16_le(ewram + config->battle_mons_offset);
    if (species == 0) return 0;
    if (species >= 2000) return 2;
    return 1; // A plausible live battle-mon is observed; UI state remains unknown.
}
