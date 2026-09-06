#include "pokemon_reader.h"
#include "pokemon_text.h"
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
    .has_evs = true,
    .has_ivs = true
};

static const GameMemoryConfig CONFIG_HEART_AND_SOUL = {
    .game_id = GAME_HEART_AND_SOUL,
    .game_name = "Pokemon Heart & Soul",
    .player_party_offset = 0x340F4,
    .player_party_count_offset = 0x340F0,
    .enemy_party_offset = 0x345A4,
    .enemy_party_count_offset = 0x345A0,
    .battle_mons_offset = 0x3A5A4,
    .battle_mons_size = 88,
    .battle_mons_hp_offset = 40,
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
        default: return &CONFIG_FIRERED; // Default fallback
    }
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
    if (!raw_bytes || !out) return false;

    memset(out, 0, sizeof(ParsedPokemon));

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
    uint32_t iv_word = read32_le(m_ptr + 4);
    out->hp_iv = (uint8_t)((iv_word >> 0) & 0x1F);
    out->attack_iv = (uint8_t)((iv_word >> 5) & 0x1F);
    out->defense_iv = (uint8_t)((iv_word >> 10) & 0x1F);
    out->speed_iv = (uint8_t)((iv_word >> 15) & 0x1F);
    out->sp_attack_iv = (uint8_t)((iv_word >> 20) & 0x1F);
    out->sp_defense_iv = (uint8_t)((iv_word >> 25) & 0x1F);
    out->is_egg = (bool)((iv_word >> 30) & 0x01);
    out->ability_slot = (uint8_t)((iv_word >> 31) & 0x01);

    // 8. Derived Properties
    out->nature = (uint8_t)(out->pid % 25);
    out->nature_name = pokemon_get_nature_name(out->nature);

    uint16_t pid_hi = (uint16_t)((out->pid >> 16) & 0xFFFF);
    uint16_t pid_lo = (uint16_t)(out->pid & 0xFFFF);
    out->is_shiny = (((out->tid ^ out->sid) ^ (pid_hi ^ pid_lo)) < 8);

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

uint8_t pokemon_scan_ewram_for_party(
    const uint8_t* ewram,
    size_t ewram_size,
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
        if (!pokemon_parse_single((const uint8_t*)raw, true, &test_mon)) continue;
        if (test_mon.species == 0 || test_mon.species >= 2000) continue;

        // Ensure this is slot 0 of the party, not slot 1-5.
        // If the preceding 100 bytes is ALREADY a valid party Pokemon, skip 'off'
        if (off >= sizeof(RawGbaPokemon)) {
            ParsedPokemon prev_mon;
            if (pokemon_parse_single(ewram + off - sizeof(RawGbaPokemon), true, &prev_mon)) {
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
            if (pokemon_parse_single(ewram + next_off, true, &next_mon)) {
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
    bool matched = false;

    // Check gBattlerPartyIndexes[0] at (battle_mons_offset - 24)
    if (config->battle_mons_offset >= 24) {
        uint16_t b0_idx = read16_le(ewram + config->battle_mons_offset - 24);
        if (b0_idx < out_snapshot->count && out_snapshot->members[b0_idx].species == b0_species) {
            if (b0_hp <= out_snapshot->members[b0_idx].max_hp) {
                out_snapshot->members[b0_idx].current_hp = b0_hp;
            }
            out_snapshot->active_battler_slot = (int8_t)b0_idx;
            matched = true;
        }
    }

    if (!matched) {
        for (uint8_t i = 0; i < out_snapshot->count; i++) {
            if (out_snapshot->members[i].species == b0_species) {
                if (b0_hp <= out_snapshot->members[i].max_hp) {
                    out_snapshot->members[i].current_hp = b0_hp;
                }
                out_snapshot->active_battler_slot = (int8_t)i;
                break;
            }
        }
    }
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
        if (out_snapshot->active_battler_slot < 0) {
            out_snapshot->active_battler_slot = 0;
        }
        return;
    }

    const uint8_t* b1 = ewram + config->battle_mons_offset + config->battle_mons_size;
    uint16_t b1_species = read16_le(b1);
    if (b1_species == 0 || b1_species >= 2000) {
        if (out_snapshot->active_battler_slot < 0) {
            out_snapshot->active_battler_slot = 0;
        }
        return;
    }

    uint16_t b1_hp = read16_le(b1 + config->battle_mons_hp_offset);
    bool matched = false;

    // Check gBattlerPartyIndexes[1] at (battle_mons_offset - 22)
    if (config->battle_mons_offset >= 24) {
        uint16_t b1_idx = read16_le(ewram + config->battle_mons_offset - 22);
        if (b1_idx < out_snapshot->count && out_snapshot->members[b1_idx].species == b1_species) {
            if (b1_hp <= out_snapshot->members[b1_idx].max_hp) {
                out_snapshot->members[b1_idx].current_hp = b1_hp;
            }
            out_snapshot->active_battler_slot = (int8_t)b1_idx;
            matched = true;
        }
    }

    if (!matched) {
        // If multiple members have same species, prefer member with current_hp > 0 when b1_hp > 0
        int chosen_slot = -1;
        for (uint8_t i = 0; i < out_snapshot->count; i++) {
            if (out_snapshot->members[i].species == b1_species) {
                if (chosen_slot == -1) chosen_slot = i;
                if (b1_hp > 0 && out_snapshot->members[i].current_hp > 0) {
                    chosen_slot = i;
                    break;
                }
            }
        }
        if (chosen_slot >= 0) {
            if (b1_hp <= out_snapshot->members[chosen_slot].max_hp) {
                out_snapshot->members[chosen_slot].current_hp = b1_hp;
            }
            out_snapshot->active_battler_slot = (int8_t)chosen_slot;
        }
    }

    if (out_snapshot->active_battler_slot < 0) {
        out_snapshot->active_battler_slot = 0;
    }
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

    // 1. Try configured static offset (fast, reliable path for vanilla & supported hacks)
    if (config && config->player_party_offset + sizeof(RawGbaPokemon) <= ewram_size) {
        ParsedPokemon first_mon;
        const uint8_t* mon_ptr = ewram + config->player_party_offset;
        if (pokemon_parse_single(mon_ptr, true, &first_mon) && first_mon.species > 0 && first_mon.species < 2000) {
            uint8_t valid_count = 0;
            out_snapshot->members[valid_count++] = first_mon;
            for (uint8_t i = 1; i < 6; i++) {
                const uint8_t* p = ewram + config->player_party_offset + (i * sizeof(RawGbaPokemon));
                if (pokemon_parse_single(p, true, &out_snapshot->members[valid_count])) {
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
        if (pokemon_parse_single(mon_ptr, true, &first_mon) && first_mon.species > 0 && first_mon.species < 2000) {
            uint8_t valid_count = 0;
            out_snapshot->members[valid_count++] = first_mon;
            for (uint8_t i = 1; i < 6; i++) {
                const uint8_t* p = ewram + s_cached_player_party_offset + (i * sizeof(RawGbaPokemon));
                if (pokemon_parse_single(p, true, &out_snapshot->members[valid_count])) {
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
    uint8_t count = pokemon_scan_ewram_for_party(ewram, ewram_size, out_snapshot);
    if (count > 0) {
        sync_live_player_battle_mon(ewram, ewram_size, config, out_snapshot);
    }
    return count;
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
        if (!pokemon_parse_single((const uint8_t*)raw, true, &test_mon)) continue;
        if (test_mon.species == 0 || test_mon.species >= 2000) continue;
        // Do NOT reject test_mon.current_hp == 0!

        // Found active enemy party!
        uint8_t count = 0;
        out_snapshot->members[count++] = test_mon;
        for (uint8_t slot = 1; slot < 6; slot++) {
            size_t next_off = off + (slot * sizeof(RawGbaPokemon));
            if (next_off + sizeof(RawGbaPokemon) > ewram_size) break;
            ParsedPokemon next_mon;
            if (pokemon_parse_single(ewram + next_off, true, &next_mon)) {
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
                if (pokemon_parse_single(ewram + off - sizeof(RawGbaPokemon), true, &prev_mon)) {
                    if (prev_mon.species > 0 && prev_mon.species < 2000) {
                        continue; // Preceding slot is already a party mon
                    }
                }
            }

            ParsedPokemon test_mon;
            if (!pokemon_parse_single((const uint8_t*)raw, true, &test_mon)) continue;
            if (test_mon.species == 0 || test_mon.species >= 2000) continue;

            // Found enemy party via scan!
            uint8_t count = 0;
            out_snapshot->members[count++] = test_mon;
            for (uint8_t slot = 1; slot < 6; slot++) {
                size_t next_off = off + (slot * sizeof(RawGbaPokemon));
                if (next_off + sizeof(RawGbaPokemon) > ewram_size) break;
                ParsedPokemon next_mon;
                if (pokemon_parse_single(ewram + next_off, true, &next_mon)) {
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
    if (!ewram || ewram_size == 0 || !out_location) return false;
    memset(out_location, 0, sizeof(PlayerLocationRaw));

    const uint8_t* sb1 = NULL;
    if (config && config->game_id == GAME_HEART_AND_SOUL) {
        // In Heart & Soul, SaveBlock1 is located at EWRAM base (0x02000000)
        sb1 = ewram;
    } else {
        uint32_t party_off = s_cached_player_party_offset;
        if (party_off == 0) {
            if (config && config->player_party_offset > 0 && config->player_party_offset + 100 <= ewram_size) {
                party_off = (uint32_t)config->player_party_offset;
            }
        }

        if (party_off == 0) return false;

        // Determine SaveBlock1 offset relative to player party
        // In Emerald / Ruby / Sapphire: SaveBlock1.playerParty is at offset 0x238
        // In FireRed / LeafGreen: SaveBlock1.playerParty is at offset 0x38
        bool is_firered = (config && config->game_id == GAME_FIRERED);
        size_t sb1_party_offset = is_firered ? 0x38 : 0x238;

        if (party_off >= sb1_party_offset && party_off - sb1_party_offset + sizeof(PlayerLocationRaw) <= ewram_size) {
            sb1 = ewram + (party_off - sb1_party_offset);
        } else {
            sb1 = ewram;
        }
    }

    // Read Coords16 pos (offset 0x00)
    int16_t pos_x = (int16_t)(sb1[0] | (sb1[1] << 8));
    int16_t pos_y = (int16_t)(sb1[2] | (sb1[3] << 8));

    // Read WarpData location (offset 0x04)
    int16_t map_group = (int16_t)sb1[4];
    int16_t map_num = (int16_t)sb1[5];
    int8_t warp_id = (int8_t)sb1[6];
    int16_t warp_x = (int16_t)(sb1[8] | (sb1[9] << 8));
    int16_t warp_y = (int16_t)(sb1[10] | (sb1[11] << 8));

    // Read WarpData escapeWarp (offset 0x24)
    int16_t esc_group = (int16_t)sb1[0x24];
    int16_t esc_num = (int16_t)sb1[0x25];

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

