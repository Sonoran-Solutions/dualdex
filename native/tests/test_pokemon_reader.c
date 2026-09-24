#include "pokemon_reader.h"
#include "pokemon_text.h"
#include "gba_memory_map.h"
#include "../src/hns_battle_pokemon_layout_gen.h"
#include "../src/hns_live_battle_layout_gen.h"
#include "../src/hns_field_status_gen.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>

#define ANSI_GREEN "\033[0;32m"
#define ANSI_RED   "\033[0;31m"
#define ANSI_RESET "\033[0m"

static int g_tests_passed = 0;
static int g_tests_failed = 0;

#define TEST_ASSERT(cond, msg) do { \
    if (!(cond)) { \
        printf(ANSI_RED "  [FAIL] %s: line %d (%s)" ANSI_RESET "\n", msg, __LINE__, #cond); \
        g_tests_failed++; \
        return; \
    } \
} while (0)

// Helper: Encrypt 48 bytes of substructures and compute checksum
static void pack_and_encrypt(
    uint32_t pid,
    uint32_t otid,
    const uint8_t* g_block,
    const uint8_t* a_block,
    const uint8_t* e_block,
    const uint8_t* m_block,
    uint8_t* out_encrypted,
    uint16_t* out_checksum
) {
    uint32_t order = pid % 24;
    // Map of GAEM blocks into 4 slots
    static const char* const ORDERS[24] = {
        "GAEM", "GAME", "GEAM", "GEMA", "GMAE", "GMEA",
        "AGEM", "AGME", "AEGM", "AEMG", "AMGE", "AMEG",
        "EGAM", "EGMA", "EAGM", "EAMG", "EMGA", "EMAG",
        "MGAE", "MGEA", "MAGE", "MAEG", "MEGA", "MEAG"
    };

    uint8_t plain[48];
    const char* o = ORDERS[order];
    for (int slot = 0; slot < 4; slot++) {
        const uint8_t* src = NULL;
        switch (o[slot]) {
            case 'G': src = g_block; break;
            case 'A': src = a_block; break;
            case 'E': src = e_block; break;
            case 'M': src = m_block; break;
        }
        memcpy(plain + (slot * 12), src, 12);
    }

    // 16-bit Checksum over 24 half-words
    uint16_t sum = 0;
    const uint16_t* hwords = (const uint16_t*)plain;
    for (int i = 0; i < 24; i++) {
        sum += hwords[i];
    }
    *out_checksum = sum;

    // XOR encrypt with key = pid ^ otid
    uint32_t key = pid ^ otid;
    const uint32_t* pwords = (const uint32_t*)plain;
    uint32_t* ewords = (uint32_t*)out_encrypted;
    for (int i = 0; i < 12; i++) {
        ewords[i] = pwords[i] ^ key;
    }
}

// Helper: pack substructures in fixed GAEM order (Unbound/CFRU), unencrypted,
// with a zeroed checksum — exactly how Unbound stores party mons in EWRAM.
static void pack_cfru_fixed(
    const uint8_t* g_block,
    const uint8_t* a_block,
    const uint8_t* e_block,
    const uint8_t* m_block,
    uint8_t* out_subs,
    uint16_t* out_checksum
) {
    memcpy(out_subs + 0,  g_block, 12); // Growth
    memcpy(out_subs + 12, a_block, 12); // Attacks
    memcpy(out_subs + 24, e_block, 12); // EVs
    memcpy(out_subs + 36, m_block, 12); // Misc
    *out_checksum = 0;                  // CFRU zeroes the checksum word
}

static void test_text_decoding(void) {
    printf("Running test_text_decoding...\n");

    // Gen 3 encoding for "PIKA" + terminator
    // P = 0xCA, I = 0xC3, K = 0xC5, A = 0xBB, 0xFF = end
    uint8_t raw_pika[5] = {0xCA, 0xC3, 0xC5, 0xBB, 0xFF};
    char decoded[32];
    size_t len = pokemon_decode_string(raw_pika, sizeof(raw_pika), decoded, sizeof(decoded));

    TEST_ASSERT(len == 4, "Decoded string length mismatch");
    TEST_ASSERT(strcmp(decoded, "PIKA") == 0, "Decoded content mismatch for PIKA");

    // Test numbers, punctuation and special symbols
    // '0' = 0xA1, '9' = 0xAA, '!' = 0xAB, '?' = 0xAC, space = 0x00, male = 0xB5
    uint8_t raw_symbols[] = {0xA1, 0xAA, 0x00, 0xAB, 0xAC, 0xB5, 0xFF};
    pokemon_decode_string(raw_symbols, sizeof(raw_symbols), decoded, sizeof(decoded));
    TEST_ASSERT(strstr(decoded, "09 !?♂") != NULL, "Special symbols and male icon decoded");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_text_decoding" ANSI_RESET "\n");
}

static void test_single_pokemon_decryption(void) {
    printf("Running test_single_pokemon_decryption...\n");

    RawGbaPokemon raw;
    memset(&raw, 0, sizeof(raw));

    raw.pid = 0x87654321;
    raw.otid = 0x12345678;

    // Nickname: "TORCHIC" in Gen 3 encoding
    // T=0xCE, O=0xC9, R=0xCC, C=0xBD, H=0xC2, I=0xC3, C=0xBD, 0xFF
    uint8_t nick_encoded[10] = {0xCE, 0xC9, 0xCC, 0xBD, 0xC2, 0xC3, 0xBD, 0xFF, 0x00, 0x00};
    memcpy(raw.nickname, nick_encoded, 10);

    // Prepare substructures
    SubstructGrowth g;
    memset(&g, 0, sizeof(g));
    g.species = 255; // Torchic
    g.held_item = 15; // e.g. Oran Berry
    g.experience = 125000;
    g.friendship = 200;

    SubstructAttacks a;
    memset(&a, 0, sizeof(a));
    a.moves[0] = 52;  // Ember
    a.moves[1] = 10;  // Scratch
    a.moves[2] = 28;  // Sand Attack
    a.moves[3] = 45;  // Growl
    a.pp[0] = 25; a.pp[1] = 35; a.pp[2] = 15; a.pp[3] = 40;

    SubstructEVs e;
    memset(&e, 0, sizeof(e));
    e.hp_ev = 12;
    e.attack_ev = 120;
    e.defense_ev = 40;
    e.speed_ev = 85;
    e.sp_attack_ev = 50;
    e.sp_defense_ev = 30;

    SubstructMisc m;
    memset(&m, 0, sizeof(m));
    // IV bitfield: HP=31, Atk=28, Def=15, Spe=30, SpA=25, SpD=20, isEgg=0, ability=1
    uint32_t ivs = (31 << 0) | (28 << 5) | (15 << 10) | (30 << 15) | (25 << 20) | (20 << 25) | (0 << 30) | (1U << 31);
    m.iv_egg_ability = ivs;

    // Encrypt
    pack_and_encrypt(raw.pid, raw.otid,
                     (uint8_t*)&g, (uint8_t*)&a, (uint8_t*)&e, (uint8_t*)&m,
                     raw.raw_substructures, &raw.checksum);

    // Battle stats
    raw.level = 36;
    raw.current_hp = 88;
    raw.max_hp = 95;
    raw.attack = 72;
    raw.defense = 48;
    raw.speed = 65;
    raw.sp_attack = 55;
    raw.sp_defense = 45;

    // Parse
    ParsedPokemon parsed;
    bool ok = pokemon_parse_single((const uint8_t*)&raw, true, &parsed);

    TEST_ASSERT(ok, "pokemon_parse_single should succeed");
    TEST_ASSERT(parsed.is_valid, "parsed.is_valid should be true");
    TEST_ASSERT(strcmp(parsed.nickname, "TORCHIC") == 0, "Nickname should match TORCHIC");
    TEST_ASSERT(parsed.species == 255, "Species should be 255 (Torchic)");
    TEST_ASSERT(parsed.held_item == 15, "Held item should be 15");
    TEST_ASSERT(parsed.experience == 125000, "EXP should match");
    TEST_ASSERT(parsed.friendship == 200, "Friendship should match");

    TEST_ASSERT(parsed.moves[0] == 52 && parsed.pp[0] == 25, "Move 1 should be Ember (52)");
    TEST_ASSERT(parsed.moves[1] == 10 && parsed.pp[1] == 35, "Move 2 should be Scratch (10)");

    TEST_ASSERT(parsed.hp_ev == 12, "HP EV should be 12");
    TEST_ASSERT(parsed.attack_ev == 120, "Atk EV should be 120");
    TEST_ASSERT(parsed.speed_ev == 85, "Spe EV should be 85");

    TEST_ASSERT(parsed.hp_iv == 31, "HP IV should be 31");
    TEST_ASSERT(parsed.attack_iv == 28, "Atk IV should be 28");
    TEST_ASSERT(parsed.defense_iv == 15, "Def IV should be 15");
    TEST_ASSERT(parsed.speed_iv == 30, "Spe IV should be 30");
    TEST_ASSERT(parsed.sp_attack_iv == 25, "SpA IV should be 25");
    TEST_ASSERT(parsed.sp_defense_iv == 20, "SpD IV should be 20");
    TEST_ASSERT(!parsed.is_egg, "is_egg should be false");
    TEST_ASSERT(parsed.ability_slot == 1, "ability_slot should be 1");

    TEST_ASSERT(parsed.level == 36, "Level should be 36");
    TEST_ASSERT(parsed.current_hp == 88, "Current HP should be 88");
    TEST_ASSERT(parsed.max_hp == 95, "Max HP should be 95");

    uint8_t expected_nature = (uint8_t)(raw.pid % 25);
    TEST_ASSERT(parsed.nature == expected_nature, "Nature index matches PID % 25");
    TEST_ASSERT(strcmp(parsed.nature_name, pokemon_get_nature_name(expected_nature)) == 0, "Nature name matches");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_single_pokemon_decryption" ANSI_RESET "\n");
}

static void test_all_substructure_orders(void) {
    printf("Running test_all_substructure_orders (verifying all 24 permutations)...\n");

    SubstructGrowth g = {.species = 384, .held_item = 0, .experience = 1000000, .friendship = 100};
    SubstructAttacks a = {.moves = {1, 2, 3, 4}, .pp = {10, 20, 30, 40}};
    SubstructEVs e = {.hp_ev = 10, .attack_ev = 20, .defense_ev = 30, .speed_ev = 40, .sp_attack_ev = 50, .sp_defense_ev = 60};
    SubstructMisc m = {.iv_egg_ability = (31 << 0) | (31 << 5) | (31 << 10) | (31 << 15) | (31 << 20) | (31 << 25)};

    // Loop through all 24 permutations (order 0 to 23)
    for (uint32_t target_order = 0; target_order < 24; target_order++) {
        RawGbaPokemon raw;
        memset(&raw, 0, sizeof(raw));

        // Ensure (pid % 24) == target_order
        raw.pid = (100 * 24) + target_order;
        raw.otid = 0x98765432;

        pack_and_encrypt(raw.pid, raw.otid,
                         (uint8_t*)&g, (uint8_t*)&a, (uint8_t*)&e, (uint8_t*)&m,
                         raw.raw_substructures, &raw.checksum);

        ParsedPokemon parsed;
        bool ok = pokemon_parse_single((const uint8_t*)&raw, false, &parsed);

        char fail_msg[64];
        snprintf(fail_msg, sizeof(fail_msg), "Permutation order %u failed", target_order);
        TEST_ASSERT(ok, fail_msg);
        TEST_ASSERT(parsed.species == 384, fail_msg);
        TEST_ASSERT(parsed.moves[0] == 1 && parsed.moves[3] == 4, fail_msg);
        TEST_ASSERT(parsed.hp_ev == 10 && parsed.sp_defense_ev == 60, fail_msg);
        TEST_ASSERT(parsed.hp_iv == 31 && parsed.sp_defense_iv == 31, fail_msg);
    }

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_all_substructure_orders (all 24 orders verified)" ANSI_RESET "\n");
}

static void test_checksum_corruption_detection(void) {
    printf("Running test_checksum_corruption_detection...\n");

    RawGbaPokemon raw;
    memset(&raw, 0, sizeof(raw));
    raw.pid = 0x11223344;
    raw.otid = 0x55667788;

    SubstructGrowth g = {.species = 1};
    SubstructAttacks a = {0};
    SubstructEVs e = {0};
    SubstructMisc m = {0};

    pack_and_encrypt(raw.pid, raw.otid,
                     (uint8_t*)&g, (uint8_t*)&a, (uint8_t*)&e, (uint8_t*)&m,
                     raw.raw_substructures, &raw.checksum);

    // Corrupt one byte of encrypted data
    raw.raw_substructures[7] ^= 0x01;

    ParsedPokemon parsed;
    bool ok = pokemon_parse_single((const uint8_t*)&raw, false, &parsed);

    TEST_ASSERT(!ok, "Corrupted substructure must fail validation");
    TEST_ASSERT(!parsed.is_valid, "is_valid must be false on corrupted checksum");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_checksum_corruption_detection" ANSI_RESET "\n");
}

static void test_shininess_calculation(void) {
    printf("Running test_shininess_calculation...\n");

    // Shininess formula: ((TID ^ SID) ^ (PID_hi ^ PID_lo)) < 8
    // Let TID = 0x1234, SID = 0x5678.
    // TID ^ SID = 0x444C
    // Choose PID_hi = 0x444C, PID_lo = 0x0000 -> (PID_hi ^ PID_lo) = 0x444C
    // ((TID ^ SID) ^ (PID_hi ^ PID_lo)) = 0 < 8 -> SHINY!
    uint32_t otid_shiny = (0x5678U << 16) | 0x1234U;
    uint32_t pid_shiny  = (0x444CU << 16) | 0x0002U; // 0x444C ^ 0x0002 = 0x444E -> 0x444C ^ 0x444E = 2 < 8

    RawGbaPokemon raw_s;
    memset(&raw_s, 0, sizeof(raw_s));
    raw_s.pid = pid_shiny;
    raw_s.otid = otid_shiny;

    SubstructGrowth g = {.species = 6};
    SubstructAttacks a = {0};
    SubstructEVs e = {0};
    SubstructMisc m = {0};

    pack_and_encrypt(raw_s.pid, raw_s.otid,
                     (uint8_t*)&g, (uint8_t*)&a, (uint8_t*)&e, (uint8_t*)&m,
                     raw_s.raw_substructures, &raw_s.checksum);

    ParsedPokemon shiny_mon;
    pokemon_parse_single((const uint8_t*)&raw_s, false, &shiny_mon);
    TEST_ASSERT(shiny_mon.is_shiny == true, "Calculated Pokemon should be shiny");

    // Non-shiny:
    uint32_t pid_non_shiny = (0x1111U << 16) | 0x2222U;
    raw_s.pid = pid_non_shiny;
    pack_and_encrypt(raw_s.pid, raw_s.otid,
                     (uint8_t*)&g, (uint8_t*)&a, (uint8_t*)&e, (uint8_t*)&m,
                     raw_s.raw_substructures, &raw_s.checksum);

    ParsedPokemon regular_mon;
    pokemon_parse_single((const uint8_t*)&raw_s, false, &regular_mon);
    TEST_ASSERT(regular_mon.is_shiny == false, "Calculated Pokemon should NOT be shiny");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_shininess_calculation" ANSI_RESET "\n");
}

static void test_ewram_party_parsing(void) {
    printf("Running test_ewram_party_parsing (mock 256KB EWRAM)...\n");

    const size_t EWRAM_SIZE = 256 * 1024;
    uint8_t* ewram = (uint8_t*)calloc(1, EWRAM_SIZE);
    TEST_ASSERT(ewram != NULL, "Memory allocation for EWRAM failed");

    const GameMemoryConfig* emerald_cfg = pokemon_get_game_config(GAME_EMERALD);
    TEST_ASSERT(emerald_cfg != NULL, "Emerald config should exist");

    // Set player party count to 3
    ewram[emerald_cfg->player_party_count_offset] = 3;

    // Create 3 Pokemon in party
    for (int i = 0; i < 3; i++) {
        RawGbaPokemon raw;
        memset(&raw, 0, sizeof(raw));
        raw.pid = 0x1000 + (i * 24);
        raw.otid = 0x2000;
        raw.level = 20 + (i * 5);
        raw.max_hp = 50 + (i * 10);
        raw.current_hp = raw.max_hp;
        raw.attack = 30 + (i * 5);
        raw.defense = 30 + (i * 5);
        raw.speed = 30 + (i * 5);
        raw.sp_attack = 30 + (i * 5);
        raw.sp_defense = 30 + (i * 5);

        SubstructGrowth g = {.species = (uint16_t)(1 + i)}; // Bulbasaur, Ivysaur, Venusaur
        SubstructAttacks a = {.moves = {10, 20, 0, 0}, .pp = {30, 20, 0, 0}};
        SubstructEVs e = {.hp_ev = (uint8_t)(i * 10)};
        SubstructMisc m = {.iv_egg_ability = 31};

        pack_and_encrypt(raw.pid, raw.otid,
                         (uint8_t*)&g, (uint8_t*)&a, (uint8_t*)&e, (uint8_t*)&m,
                         raw.raw_substructures, &raw.checksum);

        uint8_t* slot = ewram + emerald_cfg->player_party_offset + (i * sizeof(RawGbaPokemon));
        memcpy(slot, &raw, sizeof(RawGbaPokemon));
    }

    PartySnapshot snapshot;
    uint8_t parsed_count = pokemon_read_player_party(ewram, EWRAM_SIZE, emerald_cfg, &snapshot);

    TEST_ASSERT(parsed_count == 3, "Should have parsed 3 party Pokemon");
    TEST_ASSERT(snapshot.count == 3, "Snapshot count should be 3");
    TEST_ASSERT(snapshot.members[0].species == 1, "First member should be Bulbasaur (1)");
    TEST_ASSERT(snapshot.members[1].species == 2, "Second member should be Ivysaur (2)");
    TEST_ASSERT(snapshot.members[2].species == 3, "Third member should be Venusaur (3)");
    TEST_ASSERT(snapshot.members[0].level == 20, "First member level should be 20");
    TEST_ASSERT(snapshot.members[2].level == 30, "Third member level should be 30");

    free(ewram);
    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_ewram_party_parsing" ANSI_RESET "\n");
}

static void test_ewram_scan_ignores_box_pokemon_and_finds_real_party(void) {
    printf("Running test_ewram_scan_ignores_box_pokemon_and_finds_real_party...\n");

    const size_t EWRAM_SIZE = 256 * 1024;
    uint8_t* ewram = (uint8_t*)calloc(1, EWRAM_SIZE);
    TEST_ASSERT(ewram != NULL, "Memory allocation for EWRAM failed");

    pokemon_reader_reset();

    // 1. Place an 80-byte Box Pokémon (Numel, species 322) earlier in EWRAM at 0x10000
    // Box Pokémon do NOT have runtime battle stats (offsets 0x50..0x63 are 0)
    {
        RawGbaPokemon box_numel;
        memset(&box_numel, 0, sizeof(box_numel));
        box_numel.pid = 0x55443322;
        box_numel.otid = 0x99887766;
        // Nickname "NUMEL"
        uint8_t numel_name[] = {0xC8, 0xCE, 0xC7, 0xBF, 0xC6, 0xFF};
        memcpy(box_numel.nickname, numel_name, sizeof(numel_name));

        SubstructGrowth g = {.species = 322}; // Numel
        SubstructAttacks a = {0};
        SubstructEVs e = {0};
        SubstructMisc m = {.iv_egg_ability = 31};

        pack_and_encrypt(box_numel.pid, box_numel.otid,
                         (uint8_t*)&g, (uint8_t*)&a, (uint8_t*)&e, (uint8_t*)&m,
                         box_numel.raw_substructures, &box_numel.checksum);

        // Crucially, level and max_hp are 0 for Box Pokemon
        box_numel.level = 0;
        box_numel.max_hp = 0;
        box_numel.current_hp = 0;
        box_numel.attack = 0;
        box_numel.defense = 0;

        memcpy(ewram + 0x10000, &box_numel, 80);
    }

    // 2. Place the player's true 3-mon party at 0x28000 (dynamic offset, e.g. Heart & Soul)
    // Put gPlayerPartyCount = 3 at 0x27FFC (off - 4)
    ewram[0x27FFC] = 3;

    for (int i = 0; i < 3; i++) {
        RawGbaPokemon party_mon;
        memset(&party_mon, 0, sizeof(party_mon));
        party_mon.pid = 0x7700 + (i * 24);
        party_mon.otid = 0x1234;
        party_mon.level = 25 + (i * 5);
        party_mon.max_hp = 60 + (i * 12);
        party_mon.current_hp = party_mon.max_hp;
        party_mon.attack = 40 + (i * 5);
        party_mon.defense = 35 + (i * 5);
        party_mon.speed = 45 + (i * 5);
        party_mon.sp_attack = 40 + (i * 5);
        party_mon.sp_defense = 40 + (i * 5);

        SubstructGrowth g = {.species = (uint16_t)(152 + i)}; // Chikorita, Bayleef, Meganium
        SubstructAttacks a = {.moves = {33, 75, 0, 0}, .pp = {25, 15, 0, 0}}; // Tackle, Razor Leaf
        SubstructEVs e = {.hp_ev = 10};
        SubstructMisc m = {.iv_egg_ability = 31};

        pack_and_encrypt(party_mon.pid, party_mon.otid,
                         (uint8_t*)&g, (uint8_t*)&a, (uint8_t*)&e, (uint8_t*)&m,
                         party_mon.raw_substructures, &party_mon.checksum);

        uint8_t* slot = ewram + 0x28000 + (i * sizeof(RawGbaPokemon));
        memcpy(slot, &party_mon, sizeof(RawGbaPokemon));
    }

    // 3. Scan EWRAM
    PartySnapshot snapshot;
    uint8_t count = pokemon_scan_ewram_for_party(ewram, EWRAM_SIZE, &snapshot);

    TEST_ASSERT(count == 3, "Scanner must find all 3 genuine party members");
    TEST_ASSERT(snapshot.count == 3, "Snapshot count must be 3");
    TEST_ASSERT(snapshot.members[0].species == 152, "First member must be Chikorita (152), NOT Box Numel!");
    TEST_ASSERT(snapshot.members[1].species == 153, "Second member must be Bayleef (153)");
    TEST_ASSERT(snapshot.members[2].species == 154, "Third member must be Meganium (154)");
    TEST_ASSERT(snapshot.members[0].level == 25, "First member level must be 25");

    // 4. Test pokemon_read_player_party with heuristic policy and mismatched static config offset.
    // It should reject the invalid static offset and fallback to scanning, returning the true party.
    const GameMemoryConfig* emerald_cfg = pokemon_get_game_config(GAME_EMERALD);
    GameMemoryConfig scan_cfg = *emerald_cfg;
    scan_cfg.player_party_policy = PARTY_DISCOVERY_HEURISTIC;
    pokemon_reader_reset();
    PartySnapshot read_snapshot;
    uint8_t read_count = pokemon_read_player_party(ewram, EWRAM_SIZE, &scan_cfg, &read_snapshot);
    TEST_ASSERT(read_count == 3, "pokemon_read_player_party with heuristic policy must fallback and find the 3 party members");
    TEST_ASSERT(read_snapshot.members[0].species == 152, "First member must be Chikorita (152)");

    free(ewram);
    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_ewram_scan_ignores_box_pokemon_and_finds_real_party" ANSI_RESET "\n");
}

// ===========================================================================
// Heart & Soul 2.0.5 evidence-backed regression coverage.
//
// Every expected address/layout value below is taken from the exact upstream build:
//   PokemonHnS-Development/pokehns-expansion @ Release-v2.0.5
//   commit 1f42b74dff0e9fe942419845d040663dd829a973
//   built with `make hns` (arm-none-eabi-gcc 13.2.rel1, -mabi=apcs-gnu, -mthumb, -O2),
//   symbols read from pokehns.sym / arm-none-eabi-nm on pokehns.elf, struct layouts from a
//   DWARF-instrumented probe compiled against the tagged headers with the same flags.
//
// Regression intent: if someone "simplifies" these constants back into derived arithmetic, or
// reintroduces the vanilla ability-slot meaning for the expansion layout, these tests fail.
// ===========================================================================

static void write16_le_t(uint8_t* p, uint16_t v) {
    p[0] = (uint8_t)(v & 0xFF);
    p[1] = (uint8_t)((v >> 8) & 0xFF);
}

static void write32_le_t(uint8_t* p, uint32_t v) {
    p[0] = (uint8_t)(v & 0xFF);
    p[1] = (uint8_t)((v >> 8) & 0xFF);
    p[2] = (uint8_t)((v >> 16) & 0xFF);
    p[3] = (uint8_t)((v >> 24) & 0xFF);
}

/**
 * Build one exact-layout Heart & Soul party slot.
 *
 * @param iv_word      the 32-bit word at PokemonSubstruct3 offset 4 (IVs, isEgg, gmax)
 * @param ribbon_word  the 32-bit word at PokemonSubstruct3 offset 8 (ribbons, abilityNum)
 * @param hidden_nature_modifier raw 5-bit BoxPokemon.hiddenNatureModifier
 * @param shiny_modifier         BoxPokemon.shinyModifier bit
 */
static void build_hns_mon(
    RawGbaPokemon* out,
    uint32_t pid,
    uint32_t otid,
    uint16_t species,
    uint8_t level,
    uint16_t max_hp,
    uint32_t iv_word,
    uint32_t ribbon_word,
    uint8_t hidden_nature_modifier,
    bool shiny_modifier
) {
    memset(out, 0, sizeof(*out));
    out->pid = pid;
    out->otid = otid;
    out->level = level;
    out->max_hp = max_hp;
    out->current_hp = max_hp;
    out->attack = 30;
    out->defense = 30;
    out->speed = 30;
    out->sp_attack = 30;
    out->sp_defense = 30;

    uint8_t g[12];
    uint8_t a[12];
    uint8_t e[12];
    uint8_t m[12];
    memset(g, 0, sizeof(g));
    memset(a, 0, sizeof(a));
    memset(e, 0, sizeof(e));
    memset(m, 0, sizeof(m));

    write16_le_t(g + 0, species);
    a[0] = 33;   // Tackle
    a[8] = 35;   // PP
    write32_le_t(m + 4, iv_word);
    write32_le_t(m + 8, ribbon_word);

    pack_and_encrypt(pid, otid, g, a, e, m, out->raw_substructures, &out->checksum);

    // Unencrypted BoxPokemon header bits: byte 0x12 packs language:3 + hiddenNatureModifier:5,
    // and the 16-bit word at 0x1E packs hpLost:14 + shinyModifier:1 + unused:1.
    uint8_t* raw = (uint8_t*)out;
    raw[0x12] = (uint8_t)((hidden_nature_modifier & 0x1F) << 3);
    write16_le_t(raw + 0x1E, shiny_modifier ? 0x4000 : 0x0000);
}

/**
 * Build one exact-layout standard vanilla GBA party slot.
 */
static void build_vanilla_mon(
    RawGbaPokemon* out,
    uint32_t pid,
    uint32_t otid,
    uint16_t species,
    uint8_t level,
    uint16_t max_hp,
    uint32_t iv_egg_ability
) {
    memset(out, 0, sizeof(*out));
    out->pid = pid;
    out->otid = otid;
    out->level = level;
    out->max_hp = max_hp;
    out->current_hp = max_hp;
    out->attack = 30;
    out->defense = 30;
    out->speed = 30;
    out->sp_attack = 30;
    out->sp_defense = 30;

    uint8_t g[12];
    uint8_t a[12];
    uint8_t e[12];
    uint8_t m[12];
    memset(g, 0, sizeof(g));
    memset(a, 0, sizeof(a));
    memset(e, 0, sizeof(e));
    memset(m, 0, sizeof(m));

    write16_le_t(g + 0, species);
    write16_le_t(a + 0, 33); // Tackle
    a[8] = 35;              // PP
    e[0] = 10;              // HP EV
    write32_le_t(m + 4, iv_egg_ability);

    pack_and_encrypt(pid, otid, g, a, e, m, out->raw_substructures, &out->checksum);
}

/** A synthetic GBA with a real region table, so SaveBlock1 tests exercise production translation. */
typedef struct {
    uint8_t ewram[0x40000];
    uint8_t iwram[0x8000];
    DualDexGbaRegionTable table;
} FakeGba;

static bool fake_gba_read(void* user, uint32_t address, uint8_t* out, size_t length) {
    return gba_memory_map_read((const DualDexGbaRegionTable*)user, address, out, length);
}

/** Map IWRAM + EWRAM exactly the way mGBA publishes them (select = 0xFF000000). */
static void fake_gba_init(FakeGba* gba, bool map_iwram, bool map_ewram) {
    memset(gba, 0, sizeof(*gba));
    gba_memory_map_clear(&gba->table);
    if (map_iwram) {
        gba_memory_map_add(&gba->table, gba->iwram, 0x03000000u, 0x8000u, 0xFF000000u, 0u, 0u, 0u);
    }
    if (map_ewram) {
        gba_memory_map_add(&gba->table, gba->ewram, 0x02000000u, 0x40000u, 0xFF000000u, 0u, 0u, 0u);
    }
}

/**
 * A synthetic Heart & Soul 2.0.5 battle.
 *
 * The fixture writes the exact compiled globals DualDex reads, so a test can move the game
 * through a lifecycle (overworld -> wild/trainer battle -> switch/faint -> exit) by changing the
 * same words the real engine changes, instead of by planting plausible-looking Pokémon.
 */
typedef struct {
    FakeGba*  gba;
    const GameMemoryConfig* cfg;
    uint32_t  in_battle_byte_off;   // IWRAM offset of the byte holding the `inBattle` bit
} HnsBattleFixture;

static void hns_battle_fixture_init(HnsBattleFixture* fx, FakeGba* gba, const GameMemoryConfig* cfg) {
    fake_gba_init(gba, true, true);
    fx->gba = gba;
    fx->cfg = cfg;
    fx->in_battle_byte_off = (cfg->main_struct_gba_address - 0x03000000u) +
                             cfg->main_in_battle_byte_offset;
    memset(gba->ewram, 0, sizeof(gba->ewram));
    memset(gba->iwram, 0, sizeof(gba->iwram));
    // gSaveBlock1Ptr, so the location reader is not what these tests are measuring.
    write32_le_t(gba->iwram + (cfg->save_block1_ptr_gba_address - 0x03000000u),
                 cfg->save_block1_base_gba_address + 88u);
}

/** Set the engine's own lifecycle flag: `gMain.inBattle`. */
static void hns_battle_set_in_battle(HnsBattleFixture* fx, bool in_battle) {
    uint8_t* byte = fx->gba->iwram + fx->in_battle_byte_off;
    if (in_battle) *byte |= (uint8_t)(1u << fx->cfg->main_in_battle_bit);
    else           *byte &= (uint8_t)~(1u << fx->cfg->main_in_battle_bit);
}

/** Write gBattlersCount / gBattleTypeFlags / gBattleOutcome in one call. */
static void hns_battle_set_counters(HnsBattleFixture* fx, uint8_t battlers, uint32_t type_flags, uint8_t outcome) {
    fx->gba->ewram[fx->cfg->battlers_count_offset] = battlers;
    write32_le_t(fx->gba->ewram + fx->cfg->battle_type_flags_offset, type_flags);
    fx->gba->ewram[fx->cfg->battle_outcome_offset] = outcome;
}

/** Write a battler's side/position and its authoritative party slot. */
static void hns_battle_set_battler(HnsBattleFixture* fx, uint8_t battler, uint8_t position, uint16_t party_index) {
    fx->gba->ewram[fx->cfg->battler_positions_offset + battler] = position;
    write16_le_t(fx->gba->ewram + fx->cfg->battler_party_indexes_offset + (battler * 2), party_index);
}

/** Write a live `struct BattlePokemon` species/HP pair for one battler (136-byte stride). */
static void hns_battle_set_mon(HnsBattleFixture* fx, uint8_t battler, uint16_t species, uint16_t hp) {
    uint8_t* mon = fx->gba->ewram + fx->cfg->battle_mons_offset +
                   ((size_t)battler * fx->cfg->battle_mons_size);
    write16_le_t(mon, species);
    write16_le_t(mon + fx->cfg->battle_mons_hp_offset, hp);
}

/**
 * A single wild battle with one opponent.
 *
 * positions: player left = 0, opponent left = 1 (B_POSITION_PLAYER_LEFT / B_POSITION_OPPONENT_LEFT).
 */
static void hns_battle_begin_single_wild(HnsBattleFixture* fx, uint16_t enemy_slot, uint16_t enemy_species) {
    hns_battle_set_in_battle(fx, true);
    hns_battle_set_counters(fx, 2, 0u /* no TRAINER, no DOUBLE */, 0);
    fx->gba->ewram[fx->cfg->absent_battler_flags_offset] = 0;
    hns_battle_set_battler(fx, 0, 0, 0);                 // player left -> player party slot 0
    hns_battle_set_battler(fx, 1, 1, enemy_slot);        // opponent left -> enemy party slot
    hns_battle_set_mon(fx, 0, 155, 50);
    hns_battle_set_mon(fx, 1, enemy_species, 40);
}

/** Populate the authoritative enemy party with `count` valid Pokémon at gEnemyParty. */
static void hns_battle_fill_enemy_party(HnsBattleFixture* fx, uint8_t count) {
    static const uint16_t SPECIES[6] = {16, 19, 21, 23, 25, 27};
    fx->gba->ewram[fx->cfg->enemy_party_count_offset] = count;
    for (uint8_t i = 0; i < count && i < 6; i++) {
        RawGbaPokemon mon;
        build_hns_mon(&mon, 0xAABB0000u + i, 0x99990000, SPECIES[i], (uint8_t)(12 + i), 40, 25, 0, 0, false);
        memcpy(fx->gba->ewram + fx->cfg->enemy_party_offset + (i * sizeof(RawGbaPokemon)),
               &mon, sizeof(RawGbaPokemon));
    }
}

/** Populate the authoritative player party with `count` valid Pokémon at gPlayerParty. */
static void hns_battle_fill_player_party(HnsBattleFixture* fx, uint8_t count) {
    static const uint16_t SPECIES[6] = {155, 158, 152, 252, 255, 258};
    static const uint32_t OTID = 0x11112222u;
    fx->gba->ewram[fx->cfg->player_party_count_offset] = count;
    for (uint8_t i = 0; i < count && i < 6; i++) {
        RawGbaPokemon mon;
        build_hns_mon(&mon, 0x11220000u + i, OTID, SPECIES[i], (uint8_t)(14 + i), 50, 31, 0, 0, false);
        memcpy(fx->gba->ewram + fx->cfg->player_party_offset + (i * sizeof(RawGbaPokemon)),
               &mon, sizeof(RawGbaPokemon));
    }
}

/**
 * Read `gBattleMons[b].species` straight out of the fixture, for "the stale word is really there"
 * preconditions. Reading the fixture directly keeps those assertions independent of the reader
 * under test.
 */
static uint16_t pokemon_test_species_word(const FakeGba* gba, const GameMemoryConfig* cfg, uint8_t battler) {
    const uint8_t* mon = gba->ewram + cfg->battle_mons_offset +
                         ((size_t)battler * cfg->battle_mons_size);
    return (uint16_t)(mon[0] | (mon[1] << 8));
}

/**
 * H&S 2.0.5 addresses: source-build symbol vs official-release runtime address.
 *
 * The two are NOT the same, and this test pins down which is which:
 *
 *   COMPILED SYMBOL VERIFIED   - from a local `make hns` build of the tagged commit
 *   RUNTIME VERIFIED           - observed live on the official 2.0.5 release ROM
 *
 * The official binary shifts the whole IWRAM block by +0x18 and the EWRAM party group by -4
 * relative to the source build. The values asserted here are the release ones, because those are
 * the ones DualDex must read at runtime. The source-build values are recorded in the comments and
 * in docs/HNS_2_0_5_COMPATIBILITY_EVIDENCE.md so the distinction stays visible.
 *
 * Reading the release ROM at the source-build addresses yields a 4-byte-shifted party, an empty
 * enemy party and a permanently-clear in-battle flag - reproduced by
 * `test_hns_release_rom_party_fixture` below.
 */
static void test_hns_config_matches_release_runtime_evidence(void) {
    printf("Running test_hns_config_matches_release_runtime_evidence...\n");

    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    TEST_ASSERT(cfg != NULL, "Heart and Soul config must exist");

    // Party group. Source build: gPlayerParty 0x02034768, gPlayerPartyCount 0x020342A8,
    // gEnemyParty 0x020342B8, gEnemyPartyCount 0x020342A9.
    TEST_ASSERT(cfg->player_party_offset == 0x34764, "gPlayerParty must be 0x02034764 in the release ROM");
    TEST_ASSERT(cfg->player_party_count_offset == 0x342A4, "gPlayerPartyCount must be 0x020342A4");
    TEST_ASSERT(cfg->enemy_party_offset == 0x342B4, "gEnemyParty must be 0x020342B4");
    TEST_ASSERT(cfg->enemy_party_count_offset == 0x342A5, "gEnemyPartyCount must be 0x020342A5");

    // `gMain`. Source-build symbol is 0x03005BC0; the official 2.0.5 release places it at
    // 0x03005BD8 (+0x18), so the release inBattle byte is 0x03006011 - NOT the 0x03005FF9 the
    // source-build symbol resolves to. The release address was established by a semantic test
    // (gMain.heldKeys tracks live button input there; gMain.callback1 is NULL there), not by
    // readability: an earlier boot-only probe read 0x03005FF9 as a constant zero and wrongly
    // treated that as confirmation.
    TEST_ASSERT(cfg->main_struct_gba_address == 0x03005BD8,
                "official H&S 2.0.5 release gMain must be 0x03005BD8");
    TEST_ASSERT(cfg->main_in_battle_byte_offset == 0x439,
                "gMain.inBattle storage byte offset must be 0x439");
    TEST_ASSERT(cfg->main_in_battle_bit == 1,
                "gMain.inBattle must be bit 1 of the flag byte");
    TEST_ASSERT(cfg->main_struct_gba_address + cfg->main_in_battle_byte_offset == 0x03006011,
                "official release inBattle byte must resolve to 0x03006011");
    // Guard against a regression back to the source-build address.
    TEST_ASSERT(cfg->main_struct_gba_address != 0x03005BC0,
                "the source-build gMain address must not be used for the release ROM");

    TEST_ASSERT(cfg->battle_mons_offset == 0x420, "gBattleMons must be 0x02000420");
    TEST_ASSERT(cfg->battler_party_indexes_offset == 0x144, "gBattlerPartyIndexes must be 0x02000144");
    TEST_ASSERT(cfg->battle_type_flags_offset == 0xAC, "gBattleTypeFlags must be 0x020000AC");
    TEST_ASSERT(cfg->battlers_count_offset == 0xB0, "gBattlersCount must be 0x020000B0");
    TEST_ASSERT(cfg->battle_outcome_offset == 0x12C, "gBattleOutcome must be 0x0200012C");
    TEST_ASSERT(cfg->battler_positions_offset == 0x238, "gBattlerPositions must be 0x02000238");
    TEST_ASSERT(cfg->absent_battler_flags_offset == 0x30A, "gAbsentBattlerFlags must be 0x0200030A");

    // sizeof(struct BattlePokemon) is 136: gBattleMons spans 0x220 bytes over 4 battlers.
    TEST_ASSERT(cfg->battle_mons_size == 136, "sizeof(struct BattlePokemon) must be 136, not 88");
    TEST_ASSERT(cfg->battle_mons_hp_offset == 0x2A, "BattlePokemon.hp must be 0x2A");
    TEST_ASSERT(cfg->battle_mons_stat_stages_offset == 0x18, "BattlePokemon.statStages must be 0x18");

    // IWRAM pointer symbol and the 128-byte ASLR window around gSaveblock1.
    // Runtime-verified against the official 2.0.5 release ROM: gSaveBlock1Ptr, gSaveBlock2Ptr and
    // gPokemonStoragePtr were observed as three adjacent IWRAM words holding base+88 for the
    // compiled EWRAM bases, pinning the pointer at 0x030041D8 (a from-source `make hns` build
    // places it at 0x030041C0, which is NOT the address the release binary uses).
    TEST_ASSERT(cfg->save_block1_ptr_gba_address == 0x030041D8, "gSaveBlock1Ptr must be 0x030041D8");
    TEST_ASSERT(cfg->save_block1_base_gba_address == 0x020124A8, "gSaveblock1 must be 0x020124A8");
    TEST_ASSERT(cfg->save_block1_aslr_range == 128, "SaveBlock1 ASLR window must be 128 bytes");
    TEST_ASSERT(cfg->save_block1_size == 15760, "sizeof(struct SaveBlock1) must be 15760");
    TEST_ASSERT(cfg->save_block1_pos_offset == 0x04,
                "SaveBlock1.pos must be 0x04 (u16 saveVersion at 0x00 + 4-byte Coords16 alignment)");
    TEST_ASSERT(cfg->save_block1_location_offset == 0x08, "SaveBlock1.location must be 0x08");
    TEST_ASSERT(cfg->save_block1_escape_warp_offset == 0x28, "SaveBlock1.escapeWarp must be 0x28");

    TEST_ASSERT(cfg->storage_layout == PKMN_STORAGE_EXPANSION, "H&S must use the expansion storage layout");
    TEST_ASSERT(cfg->has_evs && cfg->has_ivs, "H&S keeps EVs and IVs");

    // Vanilla configs must be untouched by the expansion work.
    const GameMemoryConfig* emerald = pokemon_get_game_config(GAME_EMERALD);
    TEST_ASSERT(emerald->storage_layout == PKMN_STORAGE_VANILLA_GEN3, "Emerald keeps the vanilla layout");
    TEST_ASSERT(emerald->player_party_offset == 0x244EC, "Emerald party offset unchanged");
    TEST_ASSERT(emerald->battle_mons_hp_offset == 40, "Emerald BattlePokemon.hp unchanged");
    TEST_ASSERT(emerald->save_block1_ptr_gba_address == 0, "Emerald must not declare an IWRAM SaveBlock1 pointer");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_config_matches_release_runtime_evidence" ANSI_RESET "\n");
}

static void test_hns_party_counts_are_independent_symbols(void) {
    printf("Running test_hns_party_counts_are_independent_symbols...\n");

    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    TEST_ASSERT(cfg != NULL, "H&S config required");

    // The compiled image places the two count symbols adjacent to each other but NOT adjacent to
    // either party array, so "count == party - 4" and "enemy count == player count + 600" are both
    // wrong on 2.0.5.
    TEST_ASSERT(cfg->enemy_party_count_offset != cfg->enemy_party_offset - 4,
                "enemy count must not be derived as gEnemyParty - 4");
    TEST_ASSERT(cfg->player_party_count_offset != cfg->player_party_offset - 4,
                "player count must not be derived as gPlayerParty - 4");
    TEST_ASSERT(cfg->player_party_count_offset + 1 == cfg->enemy_party_count_offset,
                "gPlayerPartyCount and gEnemyPartyCount are adjacent bytes");
    TEST_ASSERT(cfg->player_party_count_offset != cfg->enemy_party_count_offset,
                "player and enemy counts are distinct addresses");

    // End-to-end: the reader must use gEnemyPartyCount, not the player count.
    const size_t EWRAM_SIZE = 256 * 1024;
    uint8_t* ewram = (uint8_t*)calloc(1, EWRAM_SIZE);
    TEST_ASSERT(ewram != NULL, "EWRAM allocation failed");

    pokemon_reader_reset();
    ewram[cfg->player_party_count_offset] = 1;
    ewram[cfg->enemy_party_count_offset] = 0; // enemy count differs from player count

    RawGbaPokemon mon;
    build_hns_mon(&mon, 0x0000ABCD, 0x11112222, 155, 14, 50, 31, 0, 0, false);
    memcpy(ewram + cfg->player_party_offset, &mon, sizeof(RawGbaPokemon));

    PartySnapshot snap;
    uint8_t count = pokemon_read_player_party(ewram, EWRAM_SIZE, cfg, &snap);
    TEST_ASSERT(count == 1, "one party member must be read from the compiled gPlayerParty offset");
    TEST_ASSERT(snap.members[0].species == 155, "Cyndaquil must parse at the compiled offset");

    // A count symbol that disagrees with the parsed party must never INFLATE the reported party:
    // the reader may fall through to its cached/scan paths, but it may not present members that
    // the count symbol did not authorise.
    ewram[cfg->player_party_count_offset] = 4;
    PartySnapshot rejected;
    uint8_t rejected_count = pokemon_read_player_party(ewram, EWRAM_SIZE, cfg, &rejected);
    TEST_ASSERT(rejected_count <= 1,
                "a mismatched party count must not inflate the reported party size");

    // The two count symbols are addressed independently, so an enemy count of zero must not
    // change what the player party read reports.
    ewram[cfg->player_party_count_offset] = 1;
    ewram[cfg->enemy_party_count_offset] = 0;
    PartySnapshot still_player;
    TEST_ASSERT(pokemon_read_player_party(ewram, EWRAM_SIZE, cfg, &still_player) == 1,
                "the player party read must follow gPlayerPartyCount, not gEnemyPartyCount");

    free(ewram);
    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_party_counts_are_independent_symbols" ANSI_RESET "\n");
}

/**
 * Vanilla party-symbol layout invariant (PR #70 layout audit).
 *
 * `gPlayerPartyCount` and `gEnemyPartyCount` are the first two EWRAM objects of the party
 * translation unit in both pinned decompilations (`pokefirered src/pokemon.c:59-60`,
 * `pokeemerald src/pokemon.c:76-77`), so in every vanilla layout the enemy count is the byte
 * immediately after the player count.
 *
 * `gEnemyPartyCount` is NOT `gEnemyParty - 4`: in FireRed the linker emits `.ALIGN(4)` padding
 * before `src/pokemon.o`'s EWRAM block, so `gEnemyParty - 4` is `0x02024028`, a padding byte:
 *
 *     src/pokemon.o(ewram_data)
 *     ewram_data 0x02024028  0x4d0 src/pokemon.o
 *     0x02024029                gPlayerPartyCount
 *     0x0202402a                gEnemyPartyCount
 *     0x0202402c                gEnemyParty
 *     0x02024284                gPlayerParty
 *
 * The configurations carried 0x24028 (FireRed/LeafGreen) and 0x24740 (Emerald) until PR #70's
 * audit corrected them. This test pins the corrected values so a future edit cannot silently
 * reintroduce an offset that names padding, and pins the invariant itself rather than only the
 * numbers.
 *
 * The values come from the exact addresses the pinned builds resolve; see
 * `tools/calc-goldens/audit_vanilla_layout.py` and
 * `docs/VANILLA_CALCULATOR_EVIDENCE.md` §7.2. This test does not need those builds: it asserts the
 * relationship (adjacency, and enemy count != enemy party - 4) that the build evidence establishes.
 */
static void test_vanilla_party_count_symbols_are_adjacent(void) {
    printf("Running test_vanilla_party_count_symbols_are_adjacent...\n");

    const GameMemoryConfig* emerald = pokemon_get_game_config(GAME_EMERALD);
    const GameMemoryConfig* firered = pokemon_get_game_config(GAME_FIRERED);
    const GameMemoryConfig* leafgreen = pokemon_get_game_config(GAME_LEAFGREEN);
    TEST_ASSERT(emerald != NULL && firered != NULL && leafgreen != NULL,
                "Emerald, FireRed and LeafGreen configs are required");

    /* The old, wrong values must not come back. */
    TEST_ASSERT(firered->enemy_party_count_offset != 0x24028,
                "FireRed gEnemyPartyCount must not be the 0x02024028 alignment padding byte");
    TEST_ASSERT(leafgreen->enemy_party_count_offset != 0x24028,
                "LeafGreen gEnemyPartyCount must not be the 0x02024028 alignment padding byte");
    TEST_ASSERT(emerald->enemy_party_count_offset != 0x24740,
                "Emerald gEnemyPartyCount must not be the byte after gEnemyParty");

    /* The invariant: the two count symbols are adjacent bytes, whatever the addresses are. */
    TEST_ASSERT(firered->player_party_count_offset + 1 == firered->enemy_party_count_offset,
                "FireRed party counts must be adjacent bytes");
    TEST_ASSERT(leafgreen->player_party_count_offset + 1 == leafgreen->enemy_party_count_offset,
                "LeafGreen party counts must be adjacent bytes");
    TEST_ASSERT(emerald->player_party_count_offset + 1 == emerald->enemy_party_count_offset,
                "Emerald party counts must be adjacent bytes");

    /* ...and neither is derived from its party array. */
    TEST_ASSERT(firered->enemy_party_count_offset != firered->enemy_party_offset - 4,
                "FireRed enemy count must not be derived as gEnemyParty - 4");
    TEST_ASSERT(emerald->enemy_party_count_offset != emerald->enemy_party_offset - 4,
                "Emerald enemy count must not be derived as gEnemyParty - 4");
    TEST_ASSERT(firered->player_party_count_offset != firered->player_party_offset - 4,
                "FireRed player count must not be derived as gPlayerParty - 4");
    TEST_ASSERT(emerald->player_party_count_offset != emerald->player_party_offset - 4,
                "Emerald player count must not be derived as gPlayerParty - 4");

    /* The exact symbol addresses the pinned builds resolve (audit evidence). */
    TEST_ASSERT(firered->player_party_count_offset == 0x24029, "FireRed gPlayerPartyCount is 0x02024029");
    TEST_ASSERT(firered->enemy_party_count_offset == 0x2402A, "FireRed gEnemyPartyCount is 0x0202402A");
    TEST_ASSERT(firered->enemy_party_offset == 0x2402C, "FireRed gEnemyParty is 0x0202402C");
    TEST_ASSERT(firered->player_party_offset == 0x24284, "FireRed gPlayerParty is 0x02024284");
    TEST_ASSERT(emerald->player_party_count_offset == 0x244E9, "Emerald gPlayerPartyCount is 0x020244E9");
    TEST_ASSERT(emerald->enemy_party_count_offset == 0x244EA, "Emerald gEnemyPartyCount is 0x020244EA");

    /* The party arrays are adjacent six-slot blocks, which is what makes a count meaningful. */
    TEST_ASSERT(firered->player_party_offset - firered->enemy_party_offset == 600,
                "FireRed party arrays must be adjacent 6 x 100-byte blocks");
    TEST_ASSERT(emerald->enemy_party_offset - emerald->player_party_offset == 600,
                "Emerald party arrays must be adjacent 6 x 100-byte blocks");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_vanilla_party_count_symbols_are_adjacent" ANSI_RESET "\n");
}

/**
 * Authoritative gEnemyPartyCount contract for H&S 2.0.5.
 *
 * The compiled symbol 0x020342A9 is the ONLY authority for how many enemy slots exist. A count
 * of zero means "no enemy party", so contiguous valid-looking Pokémon sitting at gEnemyParty are
 * stale leftovers and must not be presented.
 */
static void test_hns_enemy_party_count_is_authoritative(void) {
    printf("Running test_hns_enemy_party_count_is_authoritative...\n");

    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    TEST_ASSERT(cfg != NULL, "H&S config required");
    TEST_ASSERT(cfg->enemy_party_count_offset == 0x342A5, "gEnemyPartyCount symbol must be 0x342A5");

    static FakeGba gba;
    HnsBattleFixture fx;
    pokemon_reader_reset();
    hns_battle_fixture_init(&fx, &gba, cfg);
    hns_battle_fill_player_party(&fx, 1);
    hns_battle_fill_enemy_party(&fx, 6);
    hns_battle_begin_single_wild(&fx, 0, 16);

    PartySnapshot snap;
    uint8_t* enemy_bytes = gba.ewram + cfg->enemy_party_offset;
    const size_t STRIDE = sizeof(RawGbaPokemon);

    // count == 0 -> empty, and the contiguous decoy data must NOT be returned.
    gba.ewram[cfg->enemy_party_count_offset] = 0;
    TEST_ASSERT(pokemon_read_enemy_party_gba(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                             cfg, &snap) == 0,
                "an authoritative enemy count of 0 must defeat contiguous decoy slots");
    TEST_ASSERT(snap.count == 0, "a zero enemy count must leave an empty snapshot");

    // count == 1 -> exactly slot 0.
    gba.ewram[cfg->enemy_party_count_offset] = 1;
    TEST_ASSERT(pokemon_read_enemy_party_gba(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                             cfg, &snap) == 1,
                "an authoritative enemy count of 1 must yield exactly one member");
    TEST_ASSERT(snap.members[0].species == 16, "the single enemy must be slot 0");

    // count == 6 -> exactly six.
    gba.ewram[cfg->enemy_party_count_offset] = 6;
    TEST_ASSERT(pokemon_read_enemy_party_gba(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                             cfg, &snap) == 6,
                "an authoritative enemy count of 6 must yield exactly six members");

    // count == 7 and 255 -> fail closed: never truncated to six, never scanned past.
    gba.ewram[cfg->enemy_party_count_offset] = 7;
    TEST_ASSERT(pokemon_read_enemy_party_gba(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                             cfg, &snap) == 0,
                "an enemy count of 7 must fail closed");
    TEST_ASSERT(snap.count == 0, "a rejected enemy count must not leave a populated snapshot");
    gba.ewram[cfg->enemy_party_count_offset] = 255;
    TEST_ASSERT(pokemon_read_enemy_party_gba(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                             cfg, &snap) == 0,
                "an enemy count of 255 must fail closed");

    // A corrupt claimed slot fails closed (all-or-nothing): it is never skipped over.
    gba.ewram[cfg->enemy_party_count_offset] = 2;
    memset(enemy_bytes + STRIDE, 0, STRIDE);
    TEST_ASSERT(pokemon_read_enemy_party_gba(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                             cfg, &snap) == 0,
                "a corrupt claimed enemy slot must fail closed rather than be skipped");

    // A count that the authoritative symbol does not authorise must not be clamped either.
    gba.ewram[cfg->enemy_party_count_offset] = 3;
    TEST_ASSERT(pokemon_read_enemy_party_gba(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                             cfg, &snap) == 0,
                "a corrupt slot inside a legal count must fail the whole enemy read closed");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_enemy_party_count_is_authoritative" ANSI_RESET "\n");
}

/**
 * Runtime-evidence fixture: party bytes captured from the official H&S 2.0.5 release ROM.
 *
 * These 100 bytes are verbatim `gPlayerParty[0]` / `gEnemyParty[0]` as read out of EWRAM while the
 * official release ROM was running (`tools/hns-runtime-probe`). The player slot is the level 5
 * Chikorita handed out in New Bark Town's lab; its BoxPokemon checksum validates against the
 * captured substructs, its nickname decodes to "CHIKORITA", and its decrypted species word is 152.
 *
 * The fixture exists because the from-source symbol table is 4 bytes away from the release ROM for
 * this whole EWRAM group. Placing these exact bytes at the compiled offsets yields a checksum
 * mismatch, so the old addresses cannot decode the same party - which is the discrepancy this test
 * pins down.
 */
static const uint8_t kHnsReleasePlayerParty0[100] = {
    0xFB, 0x7B, 0xE9, 0x99, 0x42, 0x14, 0x23, 0xCE, 0xBD, 0xC2,
    0xC3, 0xC5, 0xC9, 0xCC, 0xC3, 0xCE, 0xBB, 0xFF, 0x02, 0x02,
    0xBB, 0xBB, 0xBB, 0xBB, 0xBB, 0xBB, 0xBB, 0x00, 0xBA, 0xC2,
    0x00, 0x00, 0xB9, 0x50, 0x4F, 0x56, 0xD1, 0xF8, 0xB6, 0x45,
    0xB9, 0x6F, 0xCA, 0x57, 0x21, 0x07, 0xCA, 0x57, 0x3E, 0x6F,
    0xCA, 0x57, 0xB9, 0x29, 0x0B, 0x57, 0xB9, 0x6F, 0xCA, 0x57,
    0xB9, 0x6F, 0xCA, 0x57, 0xB9, 0x6F, 0xCA, 0x57, 0x98, 0x6F,
    0xE7, 0x57, 0xB9, 0x6F, 0xCA, 0x57, 0x9A, 0x47, 0xCA, 0x57,
    0x00, 0x00, 0x00, 0x00, 0x05, 0xFF, 0x13, 0x00, 0x13, 0x00,
    0x0B, 0x00, 0x0B, 0x00, 0x0A, 0x00, 0x0A, 0x00, 0x0B, 0x00,
};

/** Verbatim `gEnemyParty[0]` captured during the same session, mid wild battle (species 19). */
static const uint8_t kHnsReleaseEnemyParty0[100] = {
    0x6D, 0x72, 0x1A, 0x6B, 0x42, 0x14, 0x23, 0xCE, 0xCC, 0xBB,
    0xCE, 0xCE, 0xBB, 0xCE, 0xBB, 0xFF, 0x96, 0x17, 0x02, 0x02,
    0xBB, 0xBB, 0xBB, 0xBB, 0xBB, 0xBB, 0xBB, 0x00, 0x50, 0xF8,
    0x00, 0x00, 0x3C, 0x6E, 0x39, 0xA5, 0x34, 0x66, 0x39, 0xA5,
    0x2F, 0x20, 0xB8, 0xA5, 0x2F, 0x2A, 0xBA, 0xA4, 0xA4, 0x82,
    0x11, 0x9C, 0x2F, 0x66, 0x39, 0x85, 0x2F, 0x66, 0x39, 0xA5,
    0x2F, 0x66, 0x39, 0xA5, 0x2F, 0x66, 0x39, 0xA5, 0x0E, 0x66,
    0x1E, 0xA5, 0x2F, 0x66, 0x39, 0xA5, 0x0C, 0x78, 0x39, 0xA5,
    0x00, 0x00, 0x00, 0x00, 0x03, 0xFF, 0x0F, 0x00, 0x0F, 0x00,
    0x08, 0x00, 0x06, 0x00, 0x09, 0x00, 0x07, 0x00, 0x07, 0x00,
};

#define HNS_TEST_SPECIES_CHIKORITA 152

/**
 * The release-ROM party bytes must parse at the release-ROM addresses, and must NOT parse at the
 * from-source compiled addresses.
 */
static void test_hns_release_rom_party_fixture(void) {
    printf("Running test_hns_release_rom_party_fixture...\n");

    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    TEST_ASSERT(cfg != NULL, "H&S config required");

    const size_t EWRAM_SIZE = 256 * 1024;

    // --- Positive: runtime-captured bytes at the runtime-proven addresses. ---------------------
    {
        uint8_t* ewram = (uint8_t*)calloc(1, EWRAM_SIZE);
        TEST_ASSERT(ewram != NULL, "EWRAM allocation failed");

        pokemon_reader_reset();
        ewram[cfg->player_party_count_offset] = 1;
        ewram[cfg->enemy_party_count_offset] = 1;
        memcpy(ewram + cfg->player_party_offset, kHnsReleasePlayerParty0, sizeof(kHnsReleasePlayerParty0));
        memcpy(ewram + cfg->enemy_party_offset, kHnsReleaseEnemyParty0, sizeof(kHnsReleaseEnemyParty0));

        PartySnapshot snapshot;
        uint8_t count = pokemon_read_player_party(ewram, EWRAM_SIZE, cfg, &snapshot);
        TEST_ASSERT(count == 1, "release-ROM fixture must yield exactly one party member");
        TEST_ASSERT(snapshot.members[0].species == HNS_TEST_SPECIES_CHIKORITA,
                    "release-ROM party slot 0 must decode as Chikorita (152)");
        TEST_ASSERT(snapshot.members[0].level == 5, "captured starter must decode as level 5");
        TEST_ASSERT(snapshot.members[0].current_hp == 19 && snapshot.members[0].max_hp == 19,
                    "captured starter must decode as 19/19 HP");

        PartySnapshot enemy;
        uint8_t enemy_count = pokemon_read_enemy_party_gba(NULL, NULL, ewram, EWRAM_SIZE, cfg, &enemy);
        TEST_ASSERT(enemy_count == 0,
                    "the enemy party must stay fail-closed without an absolute-address reader");

        free(ewram);
    }

    // --- The captured enemy slot must decode through the battle-gated enemy reader. --------------
    {
        static FakeGba gba;
        HnsBattleFixture fx;
        pokemon_reader_reset();
        hns_battle_fixture_init(&fx, &gba, cfg);
        hns_battle_fill_player_party(&fx, 1);
        hns_battle_fill_enemy_party(&fx, 6);
        hns_battle_begin_single_wild(&fx, 0, 19);
        memcpy(gba.ewram + cfg->enemy_party_offset, kHnsReleaseEnemyParty0,
               sizeof(kHnsReleaseEnemyParty0));
        gba.ewram[cfg->enemy_party_count_offset] = 1;

        PartySnapshot enemy;
        uint8_t enemy_count = pokemon_read_enemy_party_gba(
            fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram), cfg, &enemy);
        TEST_ASSERT(enemy_count == 1, "release-ROM enemy fixture must yield exactly one member");
        TEST_ASSERT(enemy.members[0].species == 19,
                    "release-ROM enemy slot 0 must decode as the captured wild species");
    }

    // --- Negative control: the same bytes at the from-source compiled addresses must be REJECTED. --
    // The 4-byte shift puts the BoxPokemon at the wrong phase, so the stored checksum no longer
    // matches the substructs it claims to describe. The parser must therefore reject the slot
    // outright rather than decode a shifted, plausible-looking Pokemon out of it.
    {
        uint8_t* ewram = (uint8_t*)calloc(1, EWRAM_SIZE);
        TEST_ASSERT(ewram != NULL, "EWRAM allocation failed");

        pokemon_reader_reset();
        ewram[0x342A8] = 1;  /* compiled gPlayerPartyCount */
        memcpy(ewram + 0x34768, kHnsReleasePlayerParty0, sizeof(kHnsReleasePlayerParty0));

        PartySnapshot snapshot;
        uint8_t count = pokemon_read_player_party(ewram, EWRAM_SIZE, cfg, &snapshot);
        TEST_ASSERT(count == 0,
                    "the source-build party address must reject the release-ROM bytes outright "
                    "(checksum mismatch); decoding anything here would mean the fixture no longer "
                    "reproduces the discrepancy");
        TEST_ASSERT(snapshot.members[0].species != HNS_TEST_SPECIES_CHIKORITA,
                    "the source-build party address must never yield the release-ROM Chikorita");

        free(ewram);
    }

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_release_rom_party_fixture" ANSI_RESET "\n");
}


static void test_expansion_ability_num_is_not_the_gigantamax_bit(void) {
    printf("Running test_expansion_ability_num_is_not_the_gigantamax_bit...\n");

    // IV word with every IV maxed and bit 31 (gigantamaxFactor) SET.
    const uint32_t iv_word = 0xFFFFFFFFu;
    // abilityNum = 2 lives at bits 29..30 of the word at substruct3 offset 8.
    const uint32_t ribbon_word = (uint32_t)2 << 29;

    RawGbaPokemon mon;
    build_hns_mon(&mon, 100, 200, 500, 50, 150, iv_word, ribbon_word, 0, false);

    ParsedPokemon expand;
    TEST_ASSERT(pokemon_parse_single_layout((const uint8_t*)&mon, true, PKMN_STORAGE_EXPANSION, &expand),
                "expansion parse must succeed");
    TEST_ASSERT(expand.gigantamax_factor == true,
                "bit 31 of the IV word must decode as gigantamaxFactor");
    TEST_ASSERT(expand.is_egg == true, "bit 30 of the IV word stays the egg flag");
    TEST_ASSERT(expand.ability_num == 2, "abilityNum must come from bits 29..30 of the ribbon word");
    TEST_ASSERT(expand.ability_slot == 2, "the reported ability slot is the decoded abilityNum");
    TEST_ASSERT(expand.ability_slot != 1,
                "the Gigantamax bit must NOT be reported as ability slot 1");

    // Same bytes under the vanilla layout: bit 31 IS the ability slot there.
    ParsedPokemon vanilla;
    TEST_ASSERT(pokemon_parse_single((const uint8_t*)&mon, true, &vanilla), "vanilla parse must succeed");
    TEST_ASSERT(vanilla.ability_slot == 1, "vanilla layout still reads bit 31 as the ability slot");
    TEST_ASSERT(vanilla.gigantamax_factor == false, "vanilla layout must not claim a Gigantamax flag");

    // abilityNum 0 and 3 must round-trip through the same field.
    RawGbaPokemon mon3;
    build_hns_mon(&mon3, 100, 200, 500, 50, 150, 0x0000001Fu, (uint32_t)3 << 29, 0, false);
    ParsedPokemon parsed3;
    TEST_ASSERT(pokemon_parse_single_layout((const uint8_t*)&mon3, true, PKMN_STORAGE_EXPANSION, &parsed3),
                "expansion parse of abilityNum 3 must succeed");
    TEST_ASSERT(parsed3.ability_num == 3, "abilityNum 3 must decode as 3");
    TEST_ASSERT(parsed3.gigantamax_factor == false, "gigantamaxFactor must be clear here");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_expansion_ability_num_is_not_the_gigantamax_bit" ANSI_RESET "\n");
}

static void test_expansion_nature_and_shiny_are_reported_honestly(void) {
    printf("Running test_expansion_nature_and_shiny_are_reported_honestly...\n");

    // pid % 25 = 100 % 25 = 0 (Hardy). Mint modifier 4 -> hidden nature 4 (Naughty).
    RawGbaPokemon mon;
    build_hns_mon(&mon, 100, 200, 155, 20, 60, 31, 0, 4, false);

    ParsedPokemon parsed;
    TEST_ASSERT(pokemon_parse_single_layout((const uint8_t*)&mon, true, PKMN_STORAGE_EXPANSION, &parsed),
                "parse must succeed");
    TEST_ASSERT(parsed.nature == 0, "displayed nature stays GetNature() == pid % 25");
    TEST_ASSERT(parsed.hidden_nature_modifier == 4, "hiddenNatureModifier must be read from byte 0x12");
    TEST_ASSERT(parsed.hidden_nature == 4, "stat-effective nature is (pid % 25) ^ modifier");
    TEST_ASSERT(parsed.nature_modified, "a non-zero modifier must be flagged as a modified nature");

    // Without a mint the two natures coincide and nothing is flagged.
    RawGbaPokemon plain;
    build_hns_mon(&plain, 100, 200, 155, 20, 60, 31, 0, 0, false);
    ParsedPokemon plain_parsed;
    TEST_ASSERT(pokemon_parse_single_layout((const uint8_t*)&plain, true, PKMN_STORAGE_EXPANSION, &plain_parsed),
                "parse must succeed");
    TEST_ASSERT(plain_parsed.hidden_nature == plain_parsed.nature, "unminted natures must agree");
    TEST_ASSERT(!plain_parsed.nature_modified, "unminted Pokémon must not be flagged");

    // Shiny: shinyValue = tid ^ sid ^ pid_hi ^ pid_lo.
    // 1) shinyValue 0 -> shiny for every possible odds value.
    // pid_hi ^ pid_lo == 0 and otid == 0, so shinyValue == 0. The PID is part of the
    // substructure encryption key, so it must be the PID the fixture is actually built with.
    RawGbaPokemon shiny_mon;
    build_hns_mon(&shiny_mon, 0x00010001, 0x00000000, 155, 20, 60, 31, 0, 0, false);
    ParsedPokemon shiny_parsed;
    TEST_ASSERT(pokemon_parse_single_layout((const uint8_t*)&shiny_mon, true, PKMN_STORAGE_EXPANSION, &shiny_parsed),
                "shiny parse must succeed");
    TEST_ASSERT(shiny_parsed.shiny_value == 0, "shinyValue must be 0 here");
    TEST_ASSERT(shiny_parsed.shiny_state == PKMN_SHINY_YES, "shinyValue 0 is shiny at every odds value");
    TEST_ASSERT(shiny_parsed.is_shiny, "is_shiny must agree with an exact YES");

    // 2) shinyModifier inverts it, so the same bytes are definitely NOT shiny in H&S.
    RawGbaPokemon modified;
    build_hns_mon(&modified, 0x00010001, 0x00000000, 155, 20, 60, 31, 0, 0, true);
    ParsedPokemon modified_parsed;
    TEST_ASSERT(pokemon_parse_single_layout((const uint8_t*)&modified, true, PKMN_STORAGE_EXPANSION, &modified_parsed),
                "modified parse must succeed");
    TEST_ASSERT(modified_parsed.shiny_modifier == 1, "shinyModifier must be read from bit 14 of 0x1E");
    TEST_ASSERT(modified_parsed.shiny_state == PKMN_SHINY_NO, "shinyModifier must invert the verdict");
    TEST_ASSERT(!modified_parsed.is_shiny, "is_shiny must follow the corrected verdict");

    // 3) An ambiguous shinyValue must be reported as UNKNOWN, never as a confident answer.
    // pid_hi ^ pid_lo == 16 with a zero OT id puts shinyValue in the odds-dependent band.
    RawGbaPokemon ambiguous;
    build_hns_mon(&ambiguous, 0x00000010, 0x00000000, 155, 20, 60, 31, 0, 0, false);
    ParsedPokemon ambiguous_parsed;
    TEST_ASSERT(pokemon_parse_single_layout((const uint8_t*)&ambiguous, true, PKMN_STORAGE_EXPANSION, &ambiguous_parsed),
                "ambiguous parse must succeed");
    TEST_ASSERT(ambiguous_parsed.shiny_value == 16, "shinyValue must be 16 here");
    TEST_ASSERT(ambiguous_parsed.shiny_state == PKMN_SHINY_UNKNOWN,
                "16 is inside the odds-dependent band and must be reported as UNKNOWN");
    TEST_ASSERT(!ambiguous_parsed.is_shiny, "an unknown verdict must not be presented as shiny");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_expansion_nature_and_shiny_are_reported_honestly" ANSI_RESET "\n");
}

static void test_hns_fallback_scan_uses_expansion_layout(void) {
    printf("Running test_hns_fallback_scan_uses_expansion_layout...\n");

    const GameMemoryConfig* hns_cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    TEST_ASSERT(hns_cfg != NULL, "H&S config required");
    TEST_ASSERT(hns_cfg->storage_layout == PKMN_STORAGE_EXPANSION, "H&S must configure expansion layout");

    const size_t EWRAM_SIZE = 256 * 1024;
    uint8_t* ewram = (uint8_t*)calloc(1, EWRAM_SIZE);
    TEST_ASSERT(ewram != NULL, "EWRAM allocation failed");

    pokemon_reader_reset();

    // 1. Force the configured static offset path to be rejected:
    // Keep ewram + hns_cfg->player_party_offset all zeroed (no valid pokemon).
    // Also clear count byte just in case.
    ewram[hns_cfg->player_party_count_offset] = 0;

    // 2. Build a synthetic H&S expansion mon at a fallback scan location (e.g. 0x28000).
    // Mon properties designed to distinguish vanilla from expansion parsing:
    // - pid = 0x00010001 (pid % 25 = 12, Jolly; pid_hi ^ pid_lo = 0)
    // - otid = 0 -> shinyValue = 0 (deterministic shiny)
    // - shiny_modifier = true -> expansion inverts to PKMN_SHINY_NO (is_shiny = false).
    //   Vanilla ignores modifier and treats shinyValue 0 < 8 as is_shiny = true.
    // - iv_word with bit 31 set -> expansion decodes gigantamaxFactor = true.
    //   Vanilla decodes bit 31 as ability_slot = 1 and gigantamax_factor = false.
    // - ribbon_word with bits 29..30 = 2 -> expansion decodes abilityNum = 2 and ability_slot = 2.
    //   Vanilla ignores ribbon word and gets ability_slot = 1 from IV bit 31.
    // - hidden_nature_modifier = 4 -> expansion decodes hidden_nature = (12 ^ 4) = 8, nature_modified = true.
    //   Vanilla ignores byte 0x12 and keeps hidden_nature = nature = 12, nature_modified = false.
    const size_t party_off = 0x28000;
    RawGbaPokemon mon;
    build_hns_mon(
        &mon,
        0x00010001u,            // pid
        0x00010001u,            // otid (tid=1, sid=1 -> tid^sid=0, shiny_value=0)
        155,                    // species: Cyndaquil
        20,                     // level
        60,                     // max_hp
        0x8000001Fu,            // iv_word: bit 31 set (gigantamaxFactor), hp_iv = 31
        (uint32_t)2 << 29,      // ribbon_word: bits 29..30 = 2 (abilityNum = 2)
        4,                      // hidden_nature_modifier: 4 (mint nature)
        true                    // shiny_modifier: true (inverts shiny)
    );
    memcpy(ewram + party_off, &mon, sizeof(RawGbaPokemon));
    // Provide count hint in preceding bytes to boost scan score
    ewram[party_off - 1] = 1;

    // 3. Read player party using an expansion-layout configuration with heuristic discovery.
    // (Under authoritative policy, H&S count=0 suppresses scanning; here we test that when
    // scanning IS allowed, it uses config->storage_layout PKMN_STORAGE_EXPANSION, not vanilla).
    GameMemoryConfig scan_cfg = *hns_cfg;
    scan_cfg.player_party_policy = PARTY_DISCOVERY_HEURISTIC;
    PartySnapshot snap;
    uint8_t count = pokemon_read_player_party(ewram, EWRAM_SIZE, &scan_cfg, &snap);

    TEST_ASSERT(count == 1, "fallback scan must locate the synthetic expansion party");
    TEST_ASSERT(snap.count == 1, "snapshot count must be 1");
    const ParsedPokemon* parsed = &snap.members[0];
    TEST_ASSERT(parsed->species == 155, "parsed species must be Cyndaquil");

    // Distinguish vanilla from expansion:
    // (a) abilityNum vs Gigantamax bit:
    TEST_ASSERT(parsed->gigantamax_factor == true,
                "fallback must decode bit 31 of IV word as gigantamaxFactor");
    TEST_ASSERT(parsed->ability_num == 2,
                "fallback must decode abilityNum from ribbon word bits 29..30");
    TEST_ASSERT(parsed->ability_slot == 2,
                "fallback must report ability_slot matching abilityNum");
    TEST_ASSERT(parsed->ability_slot != 1,
                "fallback must NOT decode ability_slot as 1 from IV word bit 31 (vanilla regression)");

    // (b) gigantamaxFactor:
    TEST_ASSERT(parsed->gigantamax_factor != false,
                "gigantamaxFactor must not silently revert to false");

    // (c) hidden/mint nature:
    TEST_ASSERT(parsed->nature == 12, "displayed nature must remain pid % 25 (12)");
    TEST_ASSERT(parsed->hidden_nature_modifier == 4, "hiddenNatureModifier must be decoded from byte 0x12");
    TEST_ASSERT(parsed->hidden_nature == 8, "hiddenNature must be (12 ^ 4) = 8");
    TEST_ASSERT(parsed->nature_modified == true, "nature_modified must be flagged true");
    TEST_ASSERT(parsed->hidden_nature != parsed->nature,
                "hiddenNature must not equal displayed nature when mint is applied (vanilla regression)");

    // (d) shiny state/modifier where deterministic:
    TEST_ASSERT(parsed->shiny_value == 0, "shinyValue must be 0");
    TEST_ASSERT(parsed->shiny_modifier == 1, "shinyModifier must be decoded from bit 14 of 0x1E");
    TEST_ASSERT(parsed->shiny_state == PKMN_SHINY_NO,
                "shinyModifier must invert shinyValue 0 to PKMN_SHINY_NO");
    TEST_ASSERT(parsed->is_shiny == false,
                "is_shiny must be false; vanilla fallback would have erroneously reported true");

    free(ewram);
    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_fallback_scan_uses_expansion_layout" ANSI_RESET "\n");
}

// ===========================================================================
// Authoritative player party discovery regression tests (Issue #42).
//
// For layouts explicitly designated as having an authoritative static player party:
// - Count 0 returns 0 immediately, clears cache, and suppresses blind scan.
// - Count N bounds result to exactly slots 0..N-1; slots N..5 are never exposed.
// - Corrupt occupied slot fails closed (all-or-nothing, no scan recovery).
// - Count > 6 fails closed (no scan recovery).
// - Stale discovery cache cannot override authoritative zero count.
// ===========================================================================

static void test_hns_authoritative_zero_count_defeats_decoy_scan(void) {
    printf("Running test_hns_authoritative_zero_count_defeats_decoy_scan...\n");

    const GameMemoryConfig* hns_cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    TEST_ASSERT(hns_cfg != NULL, "H&S config required");
    TEST_ASSERT(hns_cfg->player_party_policy == PARTY_DISCOVERY_AUTHORITATIVE_STATIC,
                "H&S must use PARTY_DISCOVERY_AUTHORITATIVE_STATIC");

    const size_t EWRAM_SIZE = 256 * 1024;
    uint8_t* ewram = (uint8_t*)calloc(1, EWRAM_SIZE);
    TEST_ASSERT(ewram != NULL, "EWRAM allocation failed");
    pokemon_reader_reset();

    // 1. Authoritative count is 0, party array at gPlayerParty is empty (zeroed).
    ewram[hns_cfg->player_party_count_offset] = 0;

    // 2. Plant a completely valid expansion-layout Pokémon at 0x28000 (typical scan location).
    // Provide count hint in preceding byte so the pattern scanner would score/accept it if invoked.
    RawGbaPokemon decoy;
    build_hns_mon(&decoy, 0x12345678, 0x99887766, 155, 15, 45, 31, 0, 0, false);
    memcpy(ewram + 0x28000, &decoy, sizeof(RawGbaPokemon));
    ewram[0x28000 - 1] = 1;

    // 3. Read player party. Authoritative count 0 MUST return 0 immediately and NOT scan EWRAM.
    PartySnapshot snap;
    uint8_t count = pokemon_read_player_party(ewram, EWRAM_SIZE, hns_cfg, &snap);

    TEST_ASSERT(count == 0, "authoritative count == 0 must return 0 immediately");
    TEST_ASSERT(snap.count == 0, "snapshot count must be 0");
    TEST_ASSERT(snap.members[0].species == 0, "decoy pokemon must never appear in snapshot");

    free(ewram);
    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_authoritative_zero_count_defeats_decoy_scan" ANSI_RESET "\n");
}

static void test_hns_stale_cache_cannot_override_zero(void) {
    printf("Running test_hns_stale_cache_cannot_override_zero...\n");

    const GameMemoryConfig* hns_cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    TEST_ASSERT(hns_cfg != NULL, "H&S config required");

    const size_t EWRAM_SIZE = 256 * 1024;
    uint8_t* ewram = (uint8_t*)calloc(1, EWRAM_SIZE);
    TEST_ASSERT(ewram != NULL, "EWRAM allocation failed");
    pokemon_reader_reset();

    // 1. Plant a valid expansion-layout Pokémon at 0x28000.
    RawGbaPokemon mon;
    build_hns_mon(&mon, 0x12345678, 0x99887766, 155, 15, 45, 31, 0, 0, false);
    memcpy(ewram + 0x28000, &mon, sizeof(RawGbaPokemon));
    ewram[0x28000 - 1] = 1;

    // 2. Perform a read with a heuristic configuration to populate s_cached_player_party_offset.
    GameMemoryConfig heuristic_cfg = *hns_cfg;
    heuristic_cfg.player_party_policy = PARTY_DISCOVERY_HEURISTIC;
    PartySnapshot heuristic_snap;
    uint8_t h_count = pokemon_read_player_party(ewram, EWRAM_SIZE, &heuristic_cfg, &heuristic_snap);
    TEST_ASSERT(h_count == 1, "heuristic read should find party at 0x28000 and populate cache");

    // 3. Now read through H&S authoritative config where authoritative count is 0.
    ewram[hns_cfg->player_party_count_offset] = 0;
    PartySnapshot snap;
    uint8_t count = pokemon_read_player_party(ewram, EWRAM_SIZE, hns_cfg, &snap);

    TEST_ASSERT(count == 0, "authoritative count 0 must return 0 despite existing cached offset");
    TEST_ASSERT(snap.count == 0, "snapshot count must be 0");
    TEST_ASSERT(snap.members[0].species == 0, "cached party must not be returned");

    // 4. Behavioral proof: the cached player party offset was cleared.
    // pokemon_read_enemy_party requires s_cached_player_party_offset to read player OTID.
    // With cache cleared, enemy party reader fails closed immediately (returns 0).
    PartySnapshot enemy_snap;
    uint8_t enemy_count = pokemon_read_enemy_party(ewram, EWRAM_SIZE, hns_cfg, &enemy_snap);
    TEST_ASSERT(enemy_count == 0, "enemy party reader must return 0 because cached player offset was cleared");

    // A second authoritative read also remains 0.
    PartySnapshot snap2;
    TEST_ASSERT(pokemon_read_player_party(ewram, EWRAM_SIZE, hns_cfg, &snap2) == 0,
                "subsequent authoritative read must remain 0");

    free(ewram);
    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_stale_cache_cannot_override_zero" ANSI_RESET "\n");
}

static void test_hns_authoritative_count_bounds_stale_slots(void) {
    printf("Running test_hns_authoritative_count_bounds_stale_slots...\n");

    const GameMemoryConfig* hns_cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    TEST_ASSERT(hns_cfg != NULL, "H&S config required");

    const size_t EWRAM_SIZE = 256 * 1024;
    uint8_t* ewram = (uint8_t*)calloc(1, EWRAM_SIZE);
    TEST_ASSERT(ewram != NULL, "EWRAM allocation failed");
    pokemon_reader_reset();

    // 1. Authoritative count = 1.
    ewram[hns_cfg->player_party_count_offset] = 1;

    // 2. Slot 0: valid Cyndaquil (species 155).
    // Slots 1..5: valid-looking stale expansion Pokémon (Quilava, Typhlosion, Totodile, Croconaw, Feraligatr).
    const uint16_t species_list[6] = {155, 156, 157, 158, 159, 160};
    for (int i = 0; i < 6; i++) {
        RawGbaPokemon mon;
        build_hns_mon(&mon, 0x1000 + i, 0x2000, species_list[i], 10 + i, 30 + i * 5, 31, 0, 0, false);
        memcpy(ewram + hns_cfg->player_party_offset + (i * sizeof(RawGbaPokemon)), &mon, sizeof(RawGbaPokemon));
    }

    // 3. Read player party. Exactly 1 member must be returned. Slots 1..5 must NOT be exposed.
    PartySnapshot snap;
    uint8_t count = pokemon_read_player_party(ewram, EWRAM_SIZE, hns_cfg, &snap);

    TEST_ASSERT(count == 1, "authoritative count=1 must bound snapshot to 1 member");
    TEST_ASSERT(snap.count == 1, "snapshot count must be 1");
    TEST_ASSERT(snap.members[0].species == 155, "slot 0 must be Cyndaquil");

    for (int i = 1; i < 6; i++) {
        TEST_ASSERT(snap.members[i].species == 0, "stale slot must not be exposed in snapshot");
    }

    free(ewram);
    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_authoritative_count_bounds_stale_slots" ANSI_RESET "\n");
}

static void test_hns_authoritative_six_members(void) {
    printf("Running test_hns_authoritative_six_members...\n");

    const GameMemoryConfig* hns_cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    TEST_ASSERT(hns_cfg != NULL, "H&S config required");

    const size_t EWRAM_SIZE = 256 * 1024;
    uint8_t* ewram = (uint8_t*)calloc(1, EWRAM_SIZE);
    TEST_ASSERT(ewram != NULL, "EWRAM allocation failed");
    pokemon_reader_reset();

    // 1. Authoritative count = 6.
    ewram[hns_cfg->player_party_count_offset] = 6;

    // 2. Slots 0..5: 6 valid expansion Pokémon.
    const uint16_t species_list[6] = {152, 155, 158, 25, 133, 149};
    for (int i = 0; i < 6; i++) {
        RawGbaPokemon mon;
        build_hns_mon(&mon, 0x3000 + i * 16, 0x5000, species_list[i], 20 + i, 50 + i * 5, 31, 0, 0, false);
        memcpy(ewram + hns_cfg->player_party_offset + (i * sizeof(RawGbaPokemon)), &mon, sizeof(RawGbaPokemon));
    }

    PartySnapshot snap;
    uint8_t count = pokemon_read_player_party(ewram, EWRAM_SIZE, hns_cfg, &snap);

    TEST_ASSERT(count == 6, "authoritative count=6 must return exactly 6 members");
    TEST_ASSERT(snap.count == 6, "snapshot count must be 6");
    for (int i = 0; i < 6; i++) {
        TEST_ASSERT(snap.members[i].species == species_list[i], "all 6 members must match expected species");
    }

    free(ewram);
    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_authoritative_six_members" ANSI_RESET "\n");
}

static void test_hns_invalid_authoritative_count_fails_closed(void) {
    printf("Running test_hns_invalid_authoritative_count_fails_closed...\n");

    const GameMemoryConfig* hns_cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    TEST_ASSERT(hns_cfg != NULL, "H&S config required");

    const size_t EWRAM_SIZE = 256 * 1024;
    uint8_t* ewram = (uint8_t*)calloc(1, EWRAM_SIZE);
    TEST_ASSERT(ewram != NULL, "EWRAM allocation failed");
    pokemon_reader_reset();

    // Plant valid Pokémon at static party address and at decoy location 0x28000.
    RawGbaPokemon mon;
    build_hns_mon(&mon, 0x1111, 0x2222, 155, 15, 45, 31, 0, 0, false);
    memcpy(ewram + hns_cfg->player_party_offset, &mon, sizeof(RawGbaPokemon));
    memcpy(ewram + 0x28000, &mon, sizeof(RawGbaPokemon));
    ewram[0x28000 - 1] = 1;

    // Test count = 7: invalid count outside 0..6 must fail closed, all-or-nothing, no scan.
    ewram[hns_cfg->player_party_count_offset] = 7;
    PartySnapshot snap7;
    uint8_t count7 = pokemon_read_player_party(ewram, EWRAM_SIZE, hns_cfg, &snap7);
    TEST_ASSERT(count7 == 0, "count=7 must fail closed and return 0");
    TEST_ASSERT(snap7.count == 0, "count=7 snapshot count must be 0");
    TEST_ASSERT(snap7.members[0].species == 0, "decoy must not rescue count=7");

    // Test count = 255: invalid count outside 0..6 must fail closed, no scan.
    ewram[hns_cfg->player_party_count_offset] = 255;
    PartySnapshot snap255;
    uint8_t count255 = pokemon_read_player_party(ewram, EWRAM_SIZE, hns_cfg, &snap255);
    TEST_ASSERT(count255 == 0, "count=255 must fail closed and return 0");
    TEST_ASSERT(snap255.count == 0, "count=255 snapshot count must be 0");
    TEST_ASSERT(snap255.members[0].species == 0, "decoy must not rescue count=255");

    free(ewram);
    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_invalid_authoritative_count_fails_closed" ANSI_RESET "\n");
}

static void test_hns_corrupt_authoritative_slot_fails_closed(void) {
    printf("Running test_hns_corrupt_authoritative_slot_fails_closed...\n");

    const GameMemoryConfig* hns_cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    TEST_ASSERT(hns_cfg != NULL, "H&S config required");

    const size_t EWRAM_SIZE = 256 * 1024;
    uint8_t* ewram = (uint8_t*)calloc(1, EWRAM_SIZE);
    TEST_ASSERT(ewram != NULL, "EWRAM allocation failed");
    pokemon_reader_reset();

    // Plant a decoy valid expansion Pokémon at 0x28000.
    RawGbaPokemon decoy;
    build_hns_mon(&decoy, 0x4321, 0x8765, 152, 12, 40, 31, 0, 0, false);
    memcpy(ewram + 0x28000, &decoy, sizeof(RawGbaPokemon));
    ewram[0x28000 - 1] = 1;

    // Case 1: count = 1, slot 0 is corrupt garbage.
    ewram[hns_cfg->player_party_count_offset] = 1;
    memset(ewram + hns_cfg->player_party_offset, 0xAA, sizeof(RawGbaPokemon)); // bad checksum/garbage

    PartySnapshot snap1;
    uint8_t count1 = pokemon_read_player_party(ewram, EWRAM_SIZE, hns_cfg, &snap1);
    TEST_ASSERT(count1 == 0, "corrupt slot 0 with count=1 must fail closed");
    TEST_ASSERT(snap1.count == 0, "snapshot count must be 0");
    TEST_ASSERT(snap1.members[0].species == 0, "decoy must not rescue corrupt slot 0");

    // Case 2: count = 2, slot 0 is valid, slot 1 is corrupt.
    // Reader must fail closed all-or-nothing: do not return partial party or decoy.
    ewram[hns_cfg->player_party_count_offset] = 2;
    RawGbaPokemon slot0;
    build_hns_mon(&slot0, 0x1111, 0x2222, 155, 15, 45, 31, 0, 0, false);
    memcpy(ewram + hns_cfg->player_party_offset, &slot0, sizeof(RawGbaPokemon));
    memset(ewram + hns_cfg->player_party_offset + sizeof(RawGbaPokemon), 0xBB, sizeof(RawGbaPokemon)); // corrupt slot 1

    PartySnapshot snap2;
    uint8_t count2 = pokemon_read_player_party(ewram, EWRAM_SIZE, hns_cfg, &snap2);
    TEST_ASSERT(count2 == 0, "corrupt slot 1 with count=2 must fail closed all-or-nothing");
    TEST_ASSERT(snap2.count == 0, "snapshot count must be 0");
    TEST_ASSERT(snap2.members[0].species == 0, "partial party must not be returned");

    free(ewram);
    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_corrupt_authoritative_slot_fails_closed" ANSI_RESET "\n");
}

// ===========================================================================
// Emerald authoritative player party discovery regression suite (issue #42).
// Upstream pokeemerald evidence (src/pokemon.c + sym_ewram.txt):
//   gPlayerPartyCount = 0x020244E9 (EWRAM 0x244E9)
//   gPlayerParty      = 0x020244EC (EWRAM 0x244EC)
// ===========================================================================

static void test_emerald_authoritative_zero_count_defeats_decoy_scan(void) {
    printf("Running test_emerald_authoritative_zero_count_defeats_decoy_scan...\n");

    const GameMemoryConfig* emerald_cfg = pokemon_get_game_config(GAME_EMERALD);
    TEST_ASSERT(emerald_cfg != NULL, "Emerald config required");
    TEST_ASSERT(emerald_cfg->player_party_policy == PARTY_DISCOVERY_AUTHORITATIVE_STATIC,
                "Emerald must use PARTY_DISCOVERY_AUTHORITATIVE_STATIC");

    const size_t EWRAM_SIZE = 256 * 1024;
    uint8_t* ewram = (uint8_t*)calloc(1, EWRAM_SIZE);
    TEST_ASSERT(ewram != NULL, "EWRAM allocation failed");
    pokemon_reader_reset();

    // 1. Authoritative count is 0, party array at gPlayerParty is empty (zeroed).
    ewram[emerald_cfg->player_party_count_offset] = 0;

    // 2. Plant a completely valid vanilla Pokémon at 0x28000 (typical scan location).
    RawGbaPokemon decoy;
    build_vanilla_mon(&decoy, 0x12345678, 0x99887766, 1, 15, 45, 31);
    memcpy(ewram + 0x28000, &decoy, sizeof(RawGbaPokemon));
    ewram[0x28000 - 1] = 1;

    // 3. Read player party. Authoritative count 0 MUST return 0 immediately and NOT scan EWRAM.
    PartySnapshot snap;
    uint8_t count = pokemon_read_player_party(ewram, EWRAM_SIZE, emerald_cfg, &snap);

    TEST_ASSERT(count == 0, "Emerald authoritative count == 0 must return 0 immediately");
    TEST_ASSERT(snap.count == 0, "snapshot count must be 0");
    TEST_ASSERT(snap.members[0].species == 0, "decoy pokemon must never appear in snapshot");

    free(ewram);
    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_emerald_authoritative_zero_count_defeats_decoy_scan" ANSI_RESET "\n");
}

static void test_emerald_stale_cache_cannot_override_zero(void) {
    printf("Running test_emerald_stale_cache_cannot_override_zero...\n");

    const GameMemoryConfig* emerald_cfg = pokemon_get_game_config(GAME_EMERALD);
    TEST_ASSERT(emerald_cfg != NULL, "Emerald config required");

    const size_t EWRAM_SIZE = 256 * 1024;
    uint8_t* ewram = (uint8_t*)calloc(1, EWRAM_SIZE);
    TEST_ASSERT(ewram != NULL, "EWRAM allocation failed");
    pokemon_reader_reset();

    // 1. Plant a valid vanilla Pokémon at 0x28000.
    RawGbaPokemon mon;
    build_vanilla_mon(&mon, 0x12345678, 0x99887766, 1, 15, 45, 31);
    memcpy(ewram + 0x28000, &mon, sizeof(RawGbaPokemon));
    ewram[0x28000 - 1] = 1;

    // 2. Perform a read with a heuristic configuration to populate s_cached_player_party_offset.
    GameMemoryConfig heuristic_cfg = *emerald_cfg;
    heuristic_cfg.player_party_policy = PARTY_DISCOVERY_HEURISTIC;
    PartySnapshot heuristic_snap;
    uint8_t h_count = pokemon_read_player_party(ewram, EWRAM_SIZE, &heuristic_cfg, &heuristic_snap);
    TEST_ASSERT(h_count == 1, "heuristic read should find party at 0x28000 and populate cache");

    // 3. Now read through Emerald authoritative config where authoritative count is 0.
    ewram[emerald_cfg->player_party_count_offset] = 0;
    PartySnapshot snap;
    uint8_t count = pokemon_read_player_party(ewram, EWRAM_SIZE, emerald_cfg, &snap);

    TEST_ASSERT(count == 0, "Emerald authoritative count 0 must return 0 despite existing cached offset");
    TEST_ASSERT(snap.count == 0, "snapshot count must be 0");
    TEST_ASSERT(snap.members[0].species == 0, "cached party must not be returned");

    // 4. Behavioral proof: the cached player party offset was cleared.
    PartySnapshot enemy_snap;
    uint8_t enemy_count = pokemon_read_enemy_party(ewram, EWRAM_SIZE, emerald_cfg, &enemy_snap);
    TEST_ASSERT(enemy_count == 0, "enemy party reader must return 0 because cached player offset was cleared");

    // A second authoritative read also remains 0.
    PartySnapshot snap2;
    TEST_ASSERT(pokemon_read_player_party(ewram, EWRAM_SIZE, emerald_cfg, &snap2) == 0,
                "subsequent authoritative read must remain 0");

    free(ewram);
    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_emerald_stale_cache_cannot_override_zero" ANSI_RESET "\n");
}

static void test_emerald_authoritative_count_bounds_stale_slots(void) {
    printf("Running test_emerald_authoritative_count_bounds_stale_slots...\n");

    const GameMemoryConfig* emerald_cfg = pokemon_get_game_config(GAME_EMERALD);
    TEST_ASSERT(emerald_cfg != NULL, "Emerald config required");

    const size_t EWRAM_SIZE = 256 * 1024;
    uint8_t* ewram = (uint8_t*)calloc(1, EWRAM_SIZE);
    TEST_ASSERT(ewram != NULL, "EWRAM allocation failed");
    pokemon_reader_reset();

    // 1. Authoritative count = 1.
    ewram[emerald_cfg->player_party_count_offset] = 1;

    // 2. Slot 0: valid Bulbasaur (species 1).
    // Slots 1..5: valid-looking stale vanilla Pokémon (Ivysaur, Venusaur, Charmander, Charmeleon, Charizard).
    const uint16_t species_list[6] = {1, 2, 3, 4, 5, 6};
    for (int i = 0; i < 6; i++) {
        RawGbaPokemon mon;
        build_vanilla_mon(&mon, 0x1000 + (i * 24), 0x2000, species_list[i], 10 + i, 30 + i * 5, 31);
        memcpy(ewram + emerald_cfg->player_party_offset + (i * sizeof(RawGbaPokemon)), &mon, sizeof(RawGbaPokemon));
    }

    // 3. Read player party. Exactly 1 member must be returned. Slots 1..5 must NOT be exposed.
    PartySnapshot snap;
    uint8_t count = pokemon_read_player_party(ewram, EWRAM_SIZE, emerald_cfg, &snap);

    TEST_ASSERT(count == 1, "Emerald authoritative count=1 must bound snapshot to 1 member");
    TEST_ASSERT(snap.count == 1, "snapshot count must be 1");
    TEST_ASSERT(snap.members[0].species == 1, "slot 0 must be Bulbasaur");

    for (int i = 1; i < 6; i++) {
        TEST_ASSERT(snap.members[i].species == 0, "stale slot must not be exposed in snapshot");
    }

    free(ewram);
    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_emerald_authoritative_count_bounds_stale_slots" ANSI_RESET "\n");
}

static void test_emerald_authoritative_six_members(void) {
    printf("Running test_emerald_authoritative_six_members...\n");

    const GameMemoryConfig* emerald_cfg = pokemon_get_game_config(GAME_EMERALD);
    TEST_ASSERT(emerald_cfg != NULL, "Emerald config required");

    const size_t EWRAM_SIZE = 256 * 1024;
    uint8_t* ewram = (uint8_t*)calloc(1, EWRAM_SIZE);
    TEST_ASSERT(ewram != NULL, "EWRAM allocation failed");
    pokemon_reader_reset();

    // 1. Authoritative count = 6.
    ewram[emerald_cfg->player_party_count_offset] = 6;

    // 2. Slots 0..5: 6 valid vanilla Pokémon.
    const uint16_t species_list[6] = {1, 4, 7, 25, 133, 143};
    for (int i = 0; i < 6; i++) {
        RawGbaPokemon mon;
        build_vanilla_mon(&mon, 0x3000 + (i * 24), 0x5000, species_list[i], 20 + i, 50 + i * 5, 31);
        memcpy(ewram + emerald_cfg->player_party_offset + (i * sizeof(RawGbaPokemon)), &mon, sizeof(RawGbaPokemon));
    }

    PartySnapshot snap;
    uint8_t count = pokemon_read_player_party(ewram, EWRAM_SIZE, emerald_cfg, &snap);

    TEST_ASSERT(count == 6, "Emerald authoritative count=6 must return exactly 6 members");
    TEST_ASSERT(snap.count == 6, "snapshot count must be 6");
    for (int i = 0; i < 6; i++) {
        TEST_ASSERT(snap.members[i].species == species_list[i], "all 6 members must match expected species");
    }

    free(ewram);
    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_emerald_authoritative_six_members" ANSI_RESET "\n");
}

static void test_emerald_invalid_authoritative_count_fails_closed(void) {
    printf("Running test_emerald_invalid_authoritative_count_fails_closed...\n");

    const GameMemoryConfig* emerald_cfg = pokemon_get_game_config(GAME_EMERALD);
    TEST_ASSERT(emerald_cfg != NULL, "Emerald config required");

    const size_t EWRAM_SIZE = 256 * 1024;
    uint8_t* ewram = (uint8_t*)calloc(1, EWRAM_SIZE);
    TEST_ASSERT(ewram != NULL, "EWRAM allocation failed");
    pokemon_reader_reset();

    // Plant valid Pokémon at static party address and at decoy location 0x28000.
    RawGbaPokemon mon;
    build_vanilla_mon(&mon, 0x1111, 0x2222, 1, 15, 45, 31);
    memcpy(ewram + emerald_cfg->player_party_offset, &mon, sizeof(RawGbaPokemon));
    memcpy(ewram + 0x28000, &mon, sizeof(RawGbaPokemon));
    ewram[0x28000 - 1] = 1;

    // Test count = 7: invalid count outside 0..6 must fail closed, all-or-nothing, no scan.
    ewram[emerald_cfg->player_party_count_offset] = 7;
    PartySnapshot snap7;
    uint8_t count7 = pokemon_read_player_party(ewram, EWRAM_SIZE, emerald_cfg, &snap7);
    TEST_ASSERT(count7 == 0, "count=7 must fail closed and return 0");
    TEST_ASSERT(snap7.count == 0, "count=7 snapshot count must be 0");
    TEST_ASSERT(snap7.members[0].species == 0, "decoy must not rescue count=7");

    // Test count = 255: invalid count outside 0..6 must fail closed, no scan.
    ewram[emerald_cfg->player_party_count_offset] = 255;
    PartySnapshot snap255;
    uint8_t count255 = pokemon_read_player_party(ewram, EWRAM_SIZE, emerald_cfg, &snap255);
    TEST_ASSERT(count255 == 0, "count=255 must fail closed and return 0");
    TEST_ASSERT(snap255.count == 0, "count=255 snapshot count must be 0");
    TEST_ASSERT(snap255.members[0].species == 0, "decoy must not rescue count=255");

    free(ewram);
    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_emerald_invalid_authoritative_count_fails_closed" ANSI_RESET "\n");
}

static void test_emerald_corrupt_authoritative_slot_fails_closed(void) {
    printf("Running test_emerald_corrupt_authoritative_slot_fails_closed...\n");

    const GameMemoryConfig* emerald_cfg = pokemon_get_game_config(GAME_EMERALD);
    TEST_ASSERT(emerald_cfg != NULL, "Emerald config required");

    const size_t EWRAM_SIZE = 256 * 1024;
    uint8_t* ewram = (uint8_t*)calloc(1, EWRAM_SIZE);
    TEST_ASSERT(ewram != NULL, "EWRAM allocation failed");
    pokemon_reader_reset();

    // Plant a decoy valid vanilla Pokémon at 0x28000.
    RawGbaPokemon decoy;
    build_vanilla_mon(&decoy, 0x4321, 0x8765, 1, 12, 40, 31);
    memcpy(ewram + 0x28000, &decoy, sizeof(RawGbaPokemon));
    ewram[0x28000 - 1] = 1;

    // Case 1: count = 1, slot 0 is corrupt garbage.
    ewram[emerald_cfg->player_party_count_offset] = 1;
    memset(ewram + emerald_cfg->player_party_offset, 0xAA, sizeof(RawGbaPokemon));

    PartySnapshot snap1;
    uint8_t count1 = pokemon_read_player_party(ewram, EWRAM_SIZE, emerald_cfg, &snap1);
    TEST_ASSERT(count1 == 0, "corrupt slot 0 with count=1 must fail closed");
    TEST_ASSERT(snap1.count == 0, "snapshot count must be 0");
    TEST_ASSERT(snap1.members[0].species == 0, "decoy must not rescue corrupt slot 0");

    // Case 2: count = 2, slot 0 is valid, slot 1 is corrupt.
    ewram[emerald_cfg->player_party_count_offset] = 2;
    RawGbaPokemon slot0;
    build_vanilla_mon(&slot0, 0x1111, 0x2222, 1, 15, 45, 31);
    memcpy(ewram + emerald_cfg->player_party_offset, &slot0, sizeof(RawGbaPokemon));
    memset(ewram + emerald_cfg->player_party_offset + sizeof(RawGbaPokemon), 0xBB, sizeof(RawGbaPokemon));

    PartySnapshot snap2;
    uint8_t count2 = pokemon_read_player_party(ewram, EWRAM_SIZE, emerald_cfg, &snap2);
    TEST_ASSERT(count2 == 0, "corrupt slot 1 with count=2 must fail closed all-or-nothing");
    TEST_ASSERT(snap2.count == 0, "snapshot count must be 0");
    TEST_ASSERT(snap2.members[0].species == 0, "partial party must not be returned");

    free(ewram);
    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_emerald_corrupt_authoritative_slot_fails_closed" ANSI_RESET "\n");
}

// ===========================================================================
// FireRed authoritative player party discovery regression suite (issue #42).
// Upstream pokefirered evidence (src/pokemon.c + sym_ewram.txt):
//   gPlayerPartyCount = 0x02024029 (EWRAM 0x24029)
//   gPlayerParty      = 0x02024284 (EWRAM 0x24284)
// ===========================================================================

static void test_firered_authoritative_zero_count_defeats_decoy_scan(void) {
    printf("Running test_firered_authoritative_zero_count_defeats_decoy_scan...\n");

    const GameMemoryConfig* firered_cfg = pokemon_get_game_config(GAME_FIRERED);
    TEST_ASSERT(firered_cfg != NULL, "FireRed config required");
    TEST_ASSERT(firered_cfg->player_party_policy == PARTY_DISCOVERY_AUTHORITATIVE_STATIC,
                "FireRed must use PARTY_DISCOVERY_AUTHORITATIVE_STATIC");

    const size_t EWRAM_SIZE = 256 * 1024;
    uint8_t* ewram = (uint8_t*)calloc(1, EWRAM_SIZE);
    TEST_ASSERT(ewram != NULL, "EWRAM allocation failed");
    pokemon_reader_reset();

    // 1. Authoritative count is 0, party array at gPlayerParty is empty (zeroed).
    ewram[firered_cfg->player_party_count_offset] = 0;

    // 2. Plant a completely valid vanilla Pokémon at 0x28000 (typical scan location).
    RawGbaPokemon decoy;
    build_vanilla_mon(&decoy, 0x12345678, 0x99887766, 4, 15, 45, 31);
    memcpy(ewram + 0x28000, &decoy, sizeof(RawGbaPokemon));
    ewram[0x28000 - 1] = 1;

    // 3. Read player party. Authoritative count 0 MUST return 0 immediately and NOT scan EWRAM.
    PartySnapshot snap;
    uint8_t count = pokemon_read_player_party(ewram, EWRAM_SIZE, firered_cfg, &snap);

    TEST_ASSERT(count == 0, "FireRed authoritative count == 0 must return 0 immediately");
    TEST_ASSERT(snap.count == 0, "snapshot count must be 0");
    TEST_ASSERT(snap.members[0].species == 0, "decoy pokemon must never appear in snapshot");

    free(ewram);
    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_firered_authoritative_zero_count_defeats_decoy_scan" ANSI_RESET "\n");
}

static void test_firered_stale_cache_cannot_override_zero(void) {
    printf("Running test_firered_stale_cache_cannot_override_zero...\n");

    const GameMemoryConfig* firered_cfg = pokemon_get_game_config(GAME_FIRERED);
    TEST_ASSERT(firered_cfg != NULL, "FireRed config required");

    const size_t EWRAM_SIZE = 256 * 1024;
    uint8_t* ewram = (uint8_t*)calloc(1, EWRAM_SIZE);
    TEST_ASSERT(ewram != NULL, "EWRAM allocation failed");
    pokemon_reader_reset();

    // 1. Plant a valid vanilla Pokémon at 0x28000.
    RawGbaPokemon mon;
    build_vanilla_mon(&mon, 0x12345678, 0x99887766, 4, 15, 45, 31);
    memcpy(ewram + 0x28000, &mon, sizeof(RawGbaPokemon));
    ewram[0x28000 - 1] = 1;

    // 2. Perform a read with a heuristic configuration to populate s_cached_player_party_offset.
    GameMemoryConfig heuristic_cfg = *firered_cfg;
    heuristic_cfg.player_party_policy = PARTY_DISCOVERY_HEURISTIC;
    PartySnapshot heuristic_snap;
    uint8_t h_count = pokemon_read_player_party(ewram, EWRAM_SIZE, &heuristic_cfg, &heuristic_snap);
    TEST_ASSERT(h_count == 1, "heuristic read should find party at 0x28000 and populate cache");

    // 3. Now read through FireRed authoritative config where authoritative count is 0.
    ewram[firered_cfg->player_party_count_offset] = 0;
    PartySnapshot snap;
    uint8_t count = pokemon_read_player_party(ewram, EWRAM_SIZE, firered_cfg, &snap);

    TEST_ASSERT(count == 0, "FireRed authoritative count 0 must return 0 despite existing cached offset");
    TEST_ASSERT(snap.count == 0, "snapshot count must be 0");
    TEST_ASSERT(snap.members[0].species == 0, "cached party must not be returned");

    // 4. Behavioral proof: the cached player party offset was cleared.
    PartySnapshot enemy_snap;
    uint8_t enemy_count = pokemon_read_enemy_party(ewram, EWRAM_SIZE, firered_cfg, &enemy_snap);
    TEST_ASSERT(enemy_count == 0, "enemy party reader must return 0 because cached player offset was cleared");

    // A second authoritative read also remains 0.
    PartySnapshot snap2;
    TEST_ASSERT(pokemon_read_player_party(ewram, EWRAM_SIZE, firered_cfg, &snap2) == 0,
                "subsequent authoritative read must remain 0");

    free(ewram);
    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_firered_stale_cache_cannot_override_zero" ANSI_RESET "\n");
}

static void test_firered_authoritative_count_bounds_stale_slots(void) {
    printf("Running test_firered_authoritative_count_bounds_stale_slots...\n");

    const GameMemoryConfig* firered_cfg = pokemon_get_game_config(GAME_FIRERED);
    TEST_ASSERT(firered_cfg != NULL, "FireRed config required");

    const size_t EWRAM_SIZE = 256 * 1024;
    uint8_t* ewram = (uint8_t*)calloc(1, EWRAM_SIZE);
    TEST_ASSERT(ewram != NULL, "EWRAM allocation failed");
    pokemon_reader_reset();

    // 1. Authoritative count = 1.
    ewram[firered_cfg->player_party_count_offset] = 1;

    // 2. Slot 0: valid Charmander (species 4).
    // Slots 1..5: valid-looking stale vanilla Pokémon (Charmeleon, Charizard, Squirtle, Wartortle, Blastoise).
    const uint16_t species_list[6] = {4, 5, 6, 7, 8, 9};
    for (int i = 0; i < 6; i++) {
        RawGbaPokemon mon;
        build_vanilla_mon(&mon, 0x1000 + (i * 24), 0x2000, species_list[i], 10 + i, 30 + i * 5, 31);
        memcpy(ewram + firered_cfg->player_party_offset + (i * sizeof(RawGbaPokemon)), &mon, sizeof(RawGbaPokemon));
    }

    // 3. Read player party. Exactly 1 member must be returned. Slots 1..5 must NOT be exposed.
    PartySnapshot snap;
    uint8_t count = pokemon_read_player_party(ewram, EWRAM_SIZE, firered_cfg, &snap);

    TEST_ASSERT(count == 1, "FireRed authoritative count=1 must bound snapshot to 1 member");
    TEST_ASSERT(snap.count == 1, "snapshot count must be 1");
    TEST_ASSERT(snap.members[0].species == 4, "slot 0 must be Charmander");

    for (int i = 1; i < 6; i++) {
        TEST_ASSERT(snap.members[i].species == 0, "stale slot must not be exposed in snapshot");
    }

    free(ewram);
    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_firered_authoritative_count_bounds_stale_slots" ANSI_RESET "\n");
}

static void test_firered_authoritative_six_members(void) {
    printf("Running test_firered_authoritative_six_members...\n");

    const GameMemoryConfig* firered_cfg = pokemon_get_game_config(GAME_FIRERED);
    TEST_ASSERT(firered_cfg != NULL, "FireRed config required");

    const size_t EWRAM_SIZE = 256 * 1024;
    uint8_t* ewram = (uint8_t*)calloc(1, EWRAM_SIZE);
    TEST_ASSERT(ewram != NULL, "EWRAM allocation failed");
    pokemon_reader_reset();

    // 1. Authoritative count = 6.
    ewram[firered_cfg->player_party_count_offset] = 6;

    // 2. Slots 0..5: 6 valid vanilla Pokémon.
    const uint16_t species_list[6] = {4, 1, 7, 25, 133, 143};
    for (int i = 0; i < 6; i++) {
        RawGbaPokemon mon;
        build_vanilla_mon(&mon, 0x3000 + (i * 24), 0x5000, species_list[i], 20 + i, 50 + i * 5, 31);
        memcpy(ewram + firered_cfg->player_party_offset + (i * sizeof(RawGbaPokemon)), &mon, sizeof(RawGbaPokemon));
    }

    PartySnapshot snap;
    uint8_t count = pokemon_read_player_party(ewram, EWRAM_SIZE, firered_cfg, &snap);

    TEST_ASSERT(count == 6, "FireRed authoritative count=6 must return exactly 6 members");
    TEST_ASSERT(snap.count == 6, "snapshot count must be 6");
    for (int i = 0; i < 6; i++) {
        TEST_ASSERT(snap.members[i].species == species_list[i], "all 6 members must match expected species");
    }

    free(ewram);
    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_firered_authoritative_six_members" ANSI_RESET "\n");
}

static void test_firered_invalid_authoritative_count_fails_closed(void) {
    printf("Running test_firered_invalid_authoritative_count_fails_closed...\n");

    const GameMemoryConfig* firered_cfg = pokemon_get_game_config(GAME_FIRERED);
    TEST_ASSERT(firered_cfg != NULL, "FireRed config required");

    const size_t EWRAM_SIZE = 256 * 1024;
    uint8_t* ewram = (uint8_t*)calloc(1, EWRAM_SIZE);
    TEST_ASSERT(ewram != NULL, "EWRAM allocation failed");
    pokemon_reader_reset();

    // Plant valid Pokémon at static party address and at decoy location 0x28000.
    RawGbaPokemon mon;
    build_vanilla_mon(&mon, 0x1111, 0x2222, 4, 15, 45, 31);
    memcpy(ewram + firered_cfg->player_party_offset, &mon, sizeof(RawGbaPokemon));
    memcpy(ewram + 0x28000, &mon, sizeof(RawGbaPokemon));
    ewram[0x28000 - 1] = 1;

    // Test count = 7: invalid count outside 0..6 must fail closed, all-or-nothing, no scan.
    ewram[firered_cfg->player_party_count_offset] = 7;
    PartySnapshot snap7;
    uint8_t count7 = pokemon_read_player_party(ewram, EWRAM_SIZE, firered_cfg, &snap7);
    TEST_ASSERT(count7 == 0, "count=7 must fail closed and return 0");
    TEST_ASSERT(snap7.count == 0, "count=7 snapshot count must be 0");
    TEST_ASSERT(snap7.members[0].species == 0, "decoy must not rescue count=7");

    // Test count = 255: invalid count outside 0..6 must fail closed, no scan.
    ewram[firered_cfg->player_party_count_offset] = 255;
    PartySnapshot snap255;
    uint8_t count255 = pokemon_read_player_party(ewram, EWRAM_SIZE, firered_cfg, &snap255);
    TEST_ASSERT(count255 == 0, "count=255 must fail closed and return 0");
    TEST_ASSERT(snap255.count == 0, "count=255 snapshot count must be 0");
    TEST_ASSERT(snap255.members[0].species == 0, "decoy must not rescue count=255");

    free(ewram);
    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_firered_invalid_authoritative_count_fails_closed" ANSI_RESET "\n");
}

static void test_firered_corrupt_authoritative_slot_fails_closed(void) {
    printf("Running test_firered_corrupt_authoritative_slot_fails_closed...\n");

    const GameMemoryConfig* firered_cfg = pokemon_get_game_config(GAME_FIRERED);
    TEST_ASSERT(firered_cfg != NULL, "FireRed config required");

    const size_t EWRAM_SIZE = 256 * 1024;
    uint8_t* ewram = (uint8_t*)calloc(1, EWRAM_SIZE);
    TEST_ASSERT(ewram != NULL, "EWRAM allocation failed");
    pokemon_reader_reset();

    // Plant a decoy valid vanilla Pokémon at 0x28000.
    RawGbaPokemon decoy;
    build_vanilla_mon(&decoy, 0x4321, 0x8765, 4, 12, 40, 31);
    memcpy(ewram + 0x28000, &decoy, sizeof(RawGbaPokemon));
    ewram[0x28000 - 1] = 1;

    // Case 1: count = 1, slot 0 is corrupt garbage.
    ewram[firered_cfg->player_party_count_offset] = 1;
    memset(ewram + firered_cfg->player_party_offset, 0xAA, sizeof(RawGbaPokemon));

    PartySnapshot snap1;
    uint8_t count1 = pokemon_read_player_party(ewram, EWRAM_SIZE, firered_cfg, &snap1);
    TEST_ASSERT(count1 == 0, "corrupt slot 0 with count=1 must fail closed");
    TEST_ASSERT(snap1.count == 0, "snapshot count must be 0");
    TEST_ASSERT(snap1.members[0].species == 0, "decoy must not rescue corrupt slot 0");

    // Case 2: count = 2, slot 0 is valid, slot 1 is corrupt.
    ewram[firered_cfg->player_party_count_offset] = 2;
    RawGbaPokemon slot0;
    build_vanilla_mon(&slot0, 0x1111, 0x2222, 4, 15, 45, 31);
    memcpy(ewram + firered_cfg->player_party_offset, &slot0, sizeof(RawGbaPokemon));
    memset(ewram + firered_cfg->player_party_offset + sizeof(RawGbaPokemon), 0xBB, sizeof(RawGbaPokemon));

    PartySnapshot snap2;
    uint8_t count2 = pokemon_read_player_party(ewram, EWRAM_SIZE, firered_cfg, &snap2);
    TEST_ASSERT(count2 == 0, "corrupt slot 1 with count=2 must fail closed all-or-nothing");
    TEST_ASSERT(snap2.count == 0, "snapshot count must be 0");
    TEST_ASSERT(snap2.members[0].species == 0, "partial party must not be returned");

    free(ewram);
    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_firered_corrupt_authoritative_slot_fails_closed" ANSI_RESET "\n");
}

static void test_party_discovery_policy_assignments(void) {
    printf("Running test_party_discovery_policy_assignments...\n");

    // Heart & Soul 2.0.5, vanilla Emerald, and vanilla FireRed have authoritative compiled/decomp
    // symbol evidence for both player party count and player party buffer:
    const GameMemoryConfig* hns = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    TEST_ASSERT(hns != NULL, "H&S config required");
    TEST_ASSERT(hns->player_party_policy == PARTY_DISCOVERY_AUTHORITATIVE_STATIC,
                "H&S must use PARTY_DISCOVERY_AUTHORITATIVE_STATIC");

    const GameMemoryConfig* emerald = pokemon_get_game_config(GAME_EMERALD);
    TEST_ASSERT(emerald != NULL && emerald->player_party_policy == PARTY_DISCOVERY_AUTHORITATIVE_STATIC,
                "Emerald must use PARTY_DISCOVERY_AUTHORITATIVE_STATIC");

    const GameMemoryConfig* firered = pokemon_get_game_config(GAME_FIRERED);
    TEST_ASSERT(firered != NULL && firered->player_party_policy == PARTY_DISCOVERY_AUTHORITATIVE_STATIC,
                "FireRed must use PARTY_DISCOVERY_AUTHORITATIVE_STATIC");

    // Other vanilla titles and unverified hacks keep PARTY_DISCOVERY_HEURISTIC:
    // - Unverified hacks (Ghost Grey, Radical Red, Unbound) do not have proven symbol authority.
    const GameMemoryConfig* leafgreen = pokemon_get_game_config(GAME_LEAFGREEN);
    TEST_ASSERT(leafgreen != NULL && leafgreen->player_party_policy == PARTY_DISCOVERY_HEURISTIC,
                "LeafGreen must use PARTY_DISCOVERY_HEURISTIC");

    const GameMemoryConfig* ruby = pokemon_get_game_config(GAME_RUBY);
    TEST_ASSERT(ruby != NULL && ruby->player_party_policy == PARTY_DISCOVERY_HEURISTIC,
                "Ruby must use PARTY_DISCOVERY_HEURISTIC");

    const GameMemoryConfig* sapphire = pokemon_get_game_config(GAME_SAPPHIRE);
    TEST_ASSERT(sapphire != NULL && sapphire->player_party_policy == PARTY_DISCOVERY_HEURISTIC,
                "Sapphire must use PARTY_DISCOVERY_HEURISTIC");

    const GameMemoryConfig* ghost_grey = pokemon_get_game_config(GAME_GHOST_GREY);
    TEST_ASSERT(ghost_grey != NULL && ghost_grey->player_party_policy == PARTY_DISCOVERY_HEURISTIC,
                "Ghost Grey must use PARTY_DISCOVERY_HEURISTIC");

    const GameMemoryConfig* radical_red = pokemon_get_game_config(GAME_RADICAL_RED);
    TEST_ASSERT(radical_red != NULL && radical_red->player_party_policy == PARTY_DISCOVERY_HEURISTIC,
                "Radical Red must use PARTY_DISCOVERY_HEURISTIC");

    const GameMemoryConfig* unbound = pokemon_get_game_config(GAME_UNBOUND);
    TEST_ASSERT(unbound != NULL && unbound->player_party_policy == PARTY_DISCOVERY_HEURISTIC,
                "Unbound must use PARTY_DISCOVERY_HEURISTIC");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_party_discovery_policy_assignments" ANSI_RESET "\n");
}

static void test_vanilla_ability_slot_parsing_unchanged(void) {
    printf("Running test_vanilla_ability_slot_parsing_unchanged...\n");

    // Bit 31 set, everything else zero: the classic vanilla ability-slot-1 fixture.
    RawGbaPokemon raw;
    memset(&raw, 0, sizeof(raw));
    raw.pid = 0x00000018; // pid % 24 == 0 -> GAEM order
    raw.otid = 0x00000042;
    raw.level = 25;
    raw.max_hp = 70;
    raw.current_hp = 70;
    raw.attack = 40;
    raw.defense = 40;
    raw.speed = 40;
    raw.sp_attack = 40;
    raw.sp_defense = 40;

    uint8_t g[12], a[12], e[12], m[12];
    memset(g, 0, sizeof(g));
    memset(a, 0, sizeof(a));
    memset(e, 0, sizeof(e));
    memset(m, 0, sizeof(m));
    write16_le_t(g, 25);  // Pikachu
    write32_le_t(m + 4, 0x8000001F); // ability-slot bit 31 + max HP IV
    pack_and_encrypt(raw.pid, raw.otid, g, a, e, m, raw.raw_substructures, &raw.checksum);

    ParsedPokemon parsed;
    TEST_ASSERT(pokemon_parse_single((const uint8_t*)&raw, true, &parsed), "vanilla parse must succeed");
    TEST_ASSERT(parsed.ability_slot == 1, "vanilla bit 31 must still mean ability slot 1");
    TEST_ASSERT(parsed.storage_layout == PKMN_STORAGE_VANILLA_GEN3, "vanilla entry point keeps the vanilla layout");
    TEST_ASSERT(!parsed.gigantamax_factor, "vanilla parsing must never expose a Gigantamax flag");
    TEST_ASSERT(parsed.shiny_state != PKMN_SHINY_UNKNOWN, "vanilla shiny verdict is always exact");
    TEST_ASSERT(parsed.hidden_nature == parsed.nature, "vanilla parsing has no separate mint nature");

    // The public vanilla entry point and the explicit vanilla layout must agree bit for bit.
    ParsedPokemon explicit_vanilla;
    TEST_ASSERT(pokemon_parse_single_layout((const uint8_t*)&raw, true, PKMN_STORAGE_VANILLA_GEN3, &explicit_vanilla),
                "explicit vanilla parse must succeed");
    TEST_ASSERT(explicit_vanilla.ability_slot == parsed.ability_slot, "ability slot must match");
    TEST_ASSERT(explicit_vanilla.is_shiny == parsed.is_shiny, "shiny verdict must match");
    TEST_ASSERT(explicit_vanilla.nature == parsed.nature, "nature must match");
    TEST_ASSERT(explicit_vanilla.hp_iv == parsed.hp_iv && explicit_vanilla.sp_defense_iv == parsed.sp_defense_iv,
                "IV decoding must match");

    // Clearing bit 31 yields ability slot 0 in vanilla, while the expansion layout reads the
    // ability from a different word entirely.
    write32_le_t(m + 4, 0x0000001F);
    uint8_t encrypted2[48];
    uint16_t checksum2;
    pack_and_encrypt(raw.pid, raw.otid, g, a, e, m, encrypted2, &checksum2);
    memcpy(raw.raw_substructures, encrypted2, sizeof(encrypted2));
    raw.checksum = checksum2;

    ParsedPokemon slot0;
    TEST_ASSERT(pokemon_parse_single((const uint8_t*)&raw, true, &slot0), "vanilla parse must succeed");
    TEST_ASSERT(slot0.ability_slot == 0, "vanilla bit 31 clear must mean ability slot 0");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_vanilla_ability_slot_parsing_unchanged" ANSI_RESET "\n");
}

static void test_gba_memory_region_translation(void) {
    printf("Running test_gba_memory_region_translation...\n");

    static uint8_t ewram[0x40000];
    static uint8_t iwram[0x8000];
    DualDexGbaRegionTable table;
    gba_memory_map_clear(&table);

    TEST_ASSERT(gba_memory_map_count(&table) == 0, "a cleared table has no regions");
    TEST_ASSERT(!gba_memory_map_read(&table, 0x02000000u, ewram, 1), "an empty table must reject every read");

    // Exactly what mGBA publishes for GBA: select = 0xFF000000 for both RAM regions.
    TEST_ASSERT(gba_memory_map_add(&table, iwram, 0x03000000u, 0x8000u, 0xFF000000u, 0u, 0u, 0u),
                "IWRAM region must be accepted");
    TEST_ASSERT(gba_memory_map_add(&table, ewram, 0x02000000u, 0x40000u, 0xFF000000u, 0u, 0u, 0u),
                "EWRAM region must be accepted");
    TEST_ASSERT(gba_memory_map_count(&table) == 2, "both regions must be stored");

    // EWRAM and IWRAM hold different sentinels at the same relative offset.
    ewram[0x1234] = 0xAA;
    iwram[0x1234] = 0xBB;
    uint8_t value = 0;
    TEST_ASSERT(gba_memory_map_read(&table, 0x02001234u, &value, 1) && value == 0xAA,
                "0x02001234 must resolve into EWRAM");
    TEST_ASSERT(gba_memory_map_read(&table, 0x03001234u, &value, 1) && value == 0xBB,
                "0x03001234 must resolve into IWRAM");

    // The gSaveBlock1Ptr symbol address itself is in IWRAM and must be reachable.
    uint32_t ptr_address = 0x030041C0u;
    TEST_ASSERT(gba_memory_map_resolve(&table, ptr_address, 4, NULL) == iwram + 0x41C0,
                "the compiled gSaveBlock1Ptr address must translate to IWRAM + 0x41C0");

    // Multi-byte reads must be contiguous little-endian data.
    iwram[0x41C0] = 0xA8; iwram[0x41C1] = 0x24; iwram[0x41C2] = 0x01; iwram[0x41C3] = 0x02;
    uint32_t decoded = 0;
    TEST_ASSERT(gba_memory_map_read(&table, ptr_address, &decoded, 4), "4-byte read must succeed");
    TEST_ASSERT(decoded == 0x020124A8u, "the pointer value must decode little-endian");

    // A descriptor with no base pointer or zero length maps nothing.
    DualDexGbaRegionTable empty;
    gba_memory_map_clear(&empty);
    TEST_ASSERT(!gba_memory_map_add(&empty, NULL, 0x02000000u, 0x40000u, 0u, 0u, 0u, 0u),
                "a NULL base pointer must be rejected");
    TEST_ASSERT(!gba_memory_map_add(&empty, ewram, 0x02000000u, 0u, 0u, 0u, 0u, 0u),
                "a zero-length region must be rejected");
    TEST_ASSERT(gba_memory_map_count(&empty) == 0, "rejected regions must not be stored");

    // Capacity is bounded: the table cannot be grown without limit by a core.
    DualDexGbaRegionTable full;
    gba_memory_map_clear(&full);
    for (size_t i = 0; i < DUALDEX_GBA_REGION_CAPACITY; i++) {
        TEST_ASSERT(gba_memory_map_add(&full, ewram, (uint32_t)i * 0x1000u, 0x1000u, 0u, 0u, 0u, 0u),
                    "region within capacity must be accepted");
    }
    TEST_ASSERT(!gba_memory_map_add(&full, ewram, 0x90000000u, 0x1000u, 0u, 0u, 0u, 0u),
                "a region beyond capacity must be dropped");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_gba_memory_region_translation" ANSI_RESET "\n");
}

static void test_gba_memory_bounds_rejection(void) {
    printf("Running test_gba_memory_bounds_rejection...\n");

    static uint8_t ewram[0x40000];
    static uint8_t iwram[0x8000];
    DualDexGbaRegionTable table;
    gba_memory_map_clear(&table);
    gba_memory_map_add(&table, iwram, 0x03000000u, 0x8000u, 0xFF000000u, 0u, 0u, 0u);
    gba_memory_map_add(&table, ewram, 0x02000000u, 0x40000u, 0xFF000000u, 0u, 0u, 0u);

    uint8_t buffer[8];
    memset(buffer, 0x5A, sizeof(buffer));

    // Unmapped address spaces.
    TEST_ASSERT(!gba_memory_map_read(&table, 0x08000000u, buffer, 1), "ROM space is not mapped here");
    TEST_ASSERT(!gba_memory_map_read(&table, 0x00000000u, buffer, 1), "BIOS space is not mapped here");
    TEST_ASSERT(!gba_memory_map_read(&table, 0x04000000u, buffer, 1), "I/O space is not mapped here");

    // Just past the end of each region.
    TEST_ASSERT(!gba_memory_map_read(&table, 0x02040000u, buffer, 1), "one byte past EWRAM must be rejected");
    TEST_ASSERT(!gba_memory_map_read(&table, 0x03008000u, buffer, 1), "one byte past IWRAM must be rejected");

    // A read that starts inside a region but spills out of it must be rejected whole, not
    // truncated and not stitched into the neighbouring region.
    TEST_ASSERT(!gba_memory_map_read(&table, 0x0203FFFFu, buffer, 2), "a read spilling past EWRAM must fail");
    TEST_ASSERT(!gba_memory_map_read(&table, 0x03007FFFu, buffer, 4), "a read spilling past IWRAM must fail");
    TEST_ASSERT(!gba_memory_map_read(&table, 0x0203FFFFu, buffer, 2), "repeated spill attempts must also fail");

    // The last fully-contained byte of each region still works.
    TEST_ASSERT(gba_memory_map_read(&table, 0x0203FFFFu, buffer, 1), "the last EWRAM byte must be readable");
    TEST_ASSERT(gba_memory_map_read(&table, 0x03007FFFu, buffer, 1), "the last IWRAM byte must be readable");
    TEST_ASSERT(gba_memory_map_read(&table, 0x0203FFFCu, buffer, 4), "a 4-byte read ending at the EWRAM limit must work");
    TEST_ASSERT(gba_memory_map_read(&table, 0x03007FFCu, buffer, 4), "a 4-byte read ending at the IWRAM limit must work");

    // A request that wraps the 32-bit address space can never be satisfied.
    TEST_ASSERT(!gba_memory_map_read(&table, 0xFFFFFFFEu, buffer, 4), "an address-space-wrapping read must fail");

    // Degenerate requests.
    TEST_ASSERT(!gba_memory_map_read(&table, 0x02000000u, NULL, 1), "a NULL destination must be rejected");
    TEST_ASSERT(!gba_memory_map_read(&table, 0x02000000u, buffer, 0), "a zero-length read must be rejected");
    TEST_ASSERT(!gba_memory_map_read(NULL, 0x02000000u, buffer, 1), "a NULL table must be rejected");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_gba_memory_bounds_rejection" ANSI_RESET "\n");
}

// gSaveblock1 (compiled) and the ASLR window SetSaveBlocksPointers() randomizes inside.
#define HNS_SB1_BASE_ABS 0x020124A8u

static void test_hns_saveblock1_pointer_resolution(void) {
    printf("Running test_hns_saveblock1_pointer_resolution...\n");

    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    TEST_ASSERT(cfg != NULL, "H&S config required");

    static FakeGba gba;
    // Every 4-byte-aligned offset SetSaveBlocksPointers() can produce: (x & 124).
    const uint32_t offsets[] = {0, 4, 8, 64, 124};

    for (size_t i = 0; i < sizeof(offsets) / sizeof(offsets[0]); i++) {
        fake_gba_init(&gba, true, true);
        uint32_t base = HNS_SB1_BASE_ABS + offsets[i];
        write32_le_t(gba.iwram + (cfg->save_block1_ptr_gba_address - 0x03000000u), base);

        // SaveBlock1.pos at 0x04, .location at 0x08, .escapeWarp at 0x28.
        size_t sb1_index = base - 0x02000000u;
        write16_le_t(gba.ewram + sb1_index + cfg->save_block1_pos_offset, 12);   // pos.x
        write16_le_t(gba.ewram + sb1_index + cfg->save_block1_pos_offset + 2, 34); // pos.y
        gba.ewram[sb1_index + cfg->save_block1_location_offset + 0] = 24;  // mapGroup (Johto)
        gba.ewram[sb1_index + cfg->save_block1_location_offset + 1] = 7;   // mapNum
        gba.ewram[sb1_index + cfg->save_block1_location_offset + 2] = 3;   // warpId
        write16_le_t(gba.ewram + sb1_index + cfg->save_block1_location_offset + 4, 100); // x
        write16_le_t(gba.ewram + sb1_index + cfg->save_block1_location_offset + 6, 200); // y
        gba.ewram[sb1_index + cfg->save_block1_escape_warp_offset + 0] = 1; // escape group
        gba.ewram[sb1_index + cfg->save_block1_escape_warp_offset + 1] = 2; // escape num

        PlayerLocationRaw loc;
        bool ok = pokemon_read_player_location_gba(fake_gba_read, &gba.table, NULL, 0, cfg, &loc);
        if (!ok) {
            printf(ANSI_RED "  [FAIL] ASLR offset %u did not resolve" ANSI_RESET "\n", offsets[i]);
            g_tests_failed++;
            return;
        }
        TEST_ASSERT(loc.map_group == 24 && loc.map_num == 7, "location must come from the resolved base");
        TEST_ASSERT(loc.local_x == 12 && loc.local_y == 34, "pos must come from the resolved base");
        TEST_ASSERT(loc.x == 100 && loc.y == 200, "warp coordinates must come from the resolved base");
        TEST_ASSERT(loc.warp_id == 3, "warp id must be read");
        TEST_ASSERT(loc.escape_map_group == 1 && loc.escape_map_num == 2, "escapeWarp must be read");
        TEST_ASSERT(loc.is_valid, "location must be valid");
    }

    // The reader must follow the pointer, not a fixed EWRAM base: put a different (also valid
    // looking) SaveBlock1 at the EWRAM base and confirm it is NOT the one that is read.
    fake_gba_init(&gba, true, true);
    uint32_t base = HNS_SB1_BASE_ABS + 64;
    write32_le_t(gba.iwram + (cfg->save_block1_ptr_gba_address - 0x03000000u), base);
    gba.ewram[0x0000 + cfg->save_block1_location_offset + 0] = 3; // decoy at EWRAM base
    gba.ewram[0x0000 + cfg->save_block1_location_offset + 1] = 9;
    write16_le_t(gba.ewram + (base - 0x02000000u) + cfg->save_block1_location_offset + 4, 55);

    PlayerLocationRaw followed;
    TEST_ASSERT(pokemon_read_player_location_gba(fake_gba_read, &gba.table, NULL, 0, cfg, &followed),
                "pointer-following read must succeed");
    TEST_ASSERT(followed.map_group == 0 && followed.map_num == 0,
                "the EWRAM-base decoy must NOT be used as SaveBlock1");
    TEST_ASSERT(followed.x == 55, "fields must come from the pointer target");

    // A plain EWRAM snapshot with no IWRAM access cannot resolve an IWRAM SaveBlock1 pointer,
    // so the legacy entry point must fail closed rather than fall back to EWRAM base.
    static uint8_t ewram_snapshot[0x40000];
    PlayerLocationRaw snapshot_loc;
    TEST_ASSERT(!pokemon_read_player_location(ewram_snapshot, sizeof(ewram_snapshot), cfg, &snapshot_loc),
                "H&S location must fail closed without a region-checked reader");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_saveblock1_pointer_resolution" ANSI_RESET "\n");
}

static void test_hns_saveblock1_invalid_pointer_fails_closed(void) {
    printf("Running test_hns_saveblock1_invalid_pointer_fails_closed...\n");

    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    const uint32_t ptr_off = cfg->save_block1_ptr_gba_address - 0x03000000u;
    static FakeGba gba;
    PlayerLocationRaw loc;

    // 1. Pointer outside EWRAM entirely (ROM space).
    fake_gba_init(&gba, true, true);
    write32_le_t(gba.iwram + ptr_off, 0x08000000u);
    TEST_ASSERT(!pokemon_read_player_location_gba(fake_gba_read, &gba.table, NULL, 0, cfg, &loc),
                "a pointer into ROM must fail closed");

    // 2. Pointer just below EWRAM and just past the end of EWRAM.
    write32_le_t(gba.iwram + ptr_off, 0x01FFFFFFu);
    TEST_ASSERT(!pokemon_read_player_location_gba(fake_gba_read, &gba.table, NULL, 0, cfg, &loc),
                "a pointer below EWRAM must fail closed");
    write32_le_t(gba.iwram + ptr_off, 0x02040000u);
    TEST_ASSERT(!pokemon_read_player_location_gba(fake_gba_read, &gba.table, NULL, 0, cfg, &loc),
                "a pointer at the EWRAM limit must fail closed");

    // 3. In-range but outside the randomized window (e.g. the EWRAM base assumption).
    write32_le_t(gba.iwram + ptr_off, 0x02000000u);
    TEST_ASSERT(!pokemon_read_player_location_gba(fake_gba_read, &gba.table, NULL, 0, cfg, &loc),
                "the old 'SaveBlock1 is at EWRAM base' assumption must now fail closed");
    write32_le_t(gba.iwram + ptr_off, HNS_SB1_BASE_ABS + 128u);
    TEST_ASSERT(!pokemon_read_player_location_gba(fake_gba_read, &gba.table, NULL, 0, cfg, &loc),
                "a pointer past the 128-byte ASLR window must fail closed");
    write32_le_t(gba.iwram + ptr_off, HNS_SB1_BASE_ABS - 4u);
    TEST_ASSERT(!pokemon_read_player_location_gba(fake_gba_read, &gba.table, NULL, 0, cfg, &loc),
                "a pointer below the ASLR window must fail closed");

    // 4. Misaligned pointer: SetSaveBlocksPointers() always produces a 4-byte-aligned base.
    write32_le_t(gba.iwram + ptr_off, HNS_SB1_BASE_ABS + 2u);
    TEST_ASSERT(!pokemon_read_player_location_gba(fake_gba_read, &gba.table, NULL, 0, cfg, &loc),
                "a misaligned SaveBlock1 pointer must fail closed");

    // 5. Null pointer.
    write32_le_t(gba.iwram + ptr_off, 0u);
    TEST_ASSERT(!pokemon_read_player_location_gba(fake_gba_read, &gba.table, NULL, 0, cfg, &loc),
                "a null SaveBlock1 pointer must fail closed");

    // 6. Truncated memory range: IWRAM present but SaveBlock1 is not fully mapped.
    fake_gba_init(&gba, true, false); // no EWRAM region at all
    write32_le_t(gba.iwram + ptr_off, HNS_SB1_BASE_ABS);
    TEST_ASSERT(!pokemon_read_player_location_gba(fake_gba_read, &gba.table, NULL, 0, cfg, &loc),
                "a missing EWRAM region must fail closed");

    // 7. EWRAM mapped but too short to reach the location fields.
    fake_gba_init(&gba, true, true);
    write32_le_t(gba.iwram + ptr_off, HNS_SB1_BASE_ABS);
    DualDexGbaRegionTable truncated;
    gba_memory_map_clear(&truncated);
    gba_memory_map_add(&truncated, gba.iwram, 0x03000000u, 0x8000u, 0xFF000000u, 0u, 0u, 0u);
    // EWRAM region ends before the pointer target, so SaveBlock1 is unreachable.
    gba_memory_map_add(&truncated, gba.ewram, 0x02000000u, 0x1000u, 0xFF000000u, 0u, 0u, 0u);
    TEST_ASSERT(!pokemon_read_player_location_gba(fake_gba_read, &truncated, NULL, 0, cfg, &loc),
                "a truncated EWRAM region must fail closed");

    // 8. Unreadable pointer: no IWRAM region mapped at all.
    fake_gba_init(&gba, false, true);
    TEST_ASSERT(!pokemon_read_player_location_gba(fake_gba_read, &gba.table, NULL, 0, cfg, &loc),
                "an unmapped IWRAM must fail closed");

    // 9. Structurally valid pointer but invalid map coordinates must be rejected.
    fake_gba_init(&gba, true, true);
    write32_le_t(gba.iwram + ptr_off, HNS_SB1_BASE_ABS);
    size_t sb1_index = HNS_SB1_BASE_ABS - 0x02000000u;
    gba.ewram[sb1_index + cfg->save_block1_location_offset + 0] = 90;  // impossible map group
    gba.ewram[sb1_index + cfg->save_block1_location_offset + 1] = 5;
    TEST_ASSERT(!pokemon_read_player_location_gba(fake_gba_read, &gba.table, NULL, 0, cfg, &loc),
                "an out-of-range map group must be rejected");
    gba.ewram[sb1_index + cfg->save_block1_location_offset + 0] = 1;
    gba.ewram[sb1_index + cfg->save_block1_location_offset + 1] = 0xFF; // -1 as s8
    TEST_ASSERT(!pokemon_read_player_location_gba(fake_gba_read, &gba.table, NULL, 0, cfg, &loc),
                "a negative map number must be rejected");

    // 10. NULL reader and unknown/absent configs must fail closed.
    TEST_ASSERT(!pokemon_read_player_location_gba(NULL, NULL, NULL, 0, cfg, &loc),
                "a NULL reader must fail closed");
    TEST_ASSERT(!pokemon_read_player_location_gba(fake_gba_read, &gba.table, NULL, 0, NULL, &loc),
                "a NULL config must fail closed");
    GameMemoryConfig unknown = {0};
    unknown.game_id = GAME_UNKNOWN;
    TEST_ASSERT(!pokemon_read_player_location_gba(fake_gba_read, &gba.table, NULL, 0, &unknown, &loc),
                "a GAME_UNKNOWN config must fail closed");
    TEST_ASSERT(!pokemon_read_player_location_gba(fake_gba_read, &gba.table, NULL, 0, cfg, NULL),
                "a NULL output must fail closed");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_saveblock1_invalid_pointer_fails_closed" ANSI_RESET "\n");
}

static void test_hns_saveblock1_aslr_window_is_not_fixed(void) {
    printf("Running test_hns_saveblock1_aslr_window_is_not_fixed...\n");

    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    const uint32_t ptr_off = cfg->save_block1_ptr_gba_address - 0x03000000u;
    static FakeGba gba;

    // Two different windows must produce two different locations from the same EWRAM contents:
    // this is what proves the reader is not pinned to one hard-coded base.
    uint32_t seen_groups[2];
    const uint32_t offsets[2] = {0, 124};

    for (int i = 0; i < 2; i++) {
        fake_gba_init(&gba, true, true);
        uint32_t base = HNS_SB1_BASE_ABS + offsets[i];
        write32_le_t(gba.iwram + ptr_off, base);
        size_t sb1_index = base - 0x02000000u;
        gba.ewram[sb1_index + cfg->save_block1_location_offset + 0] = (uint8_t)(10 + i);
        gba.ewram[sb1_index + cfg->save_block1_location_offset + 1] = 1;

        PlayerLocationRaw loc;
        TEST_ASSERT(pokemon_read_player_location_gba(fake_gba_read, &gba.table, NULL, 0, cfg, &loc),
                    "each ASLR offset must resolve");
        seen_groups[i] = (uint32_t)loc.map_group;
    }

    TEST_ASSERT(seen_groups[0] == 10 && seen_groups[1] == 11,
                "each ASLR window must be read from its own base");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_saveblock1_aslr_window_is_not_fixed" ANSI_RESET "\n");
}

static void test_heart_and_soul_party_and_battle_hp_sync(void) {
    printf("Running test_heart_and_soul_party_and_battle_hp_sync...\n");

    // 1. Verify game detection
    GbaGameId detected = pokemon_detect_game("POKEMON HNS");
    TEST_ASSERT(detected == GAME_HEART_AND_SOUL, "'POKEMON HNS' (the upstream hns TITLE) must detect as H&S");

    const GameMemoryConfig* hns_cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    TEST_ASSERT(hns_cfg != NULL, "Heart and Soul config must exist");

    static FakeGba gba;
    HnsBattleFixture fx;
    pokemon_reader_reset();
    hns_battle_fixture_init(&fx, &gba, hns_cfg);
    hns_battle_fill_player_party(&fx, 2);   // Cyndaquil, Totodile
    hns_battle_fill_enemy_party(&fx, 2);    // Pidgey, Rattata
    hns_battle_begin_single_wild(&fx, 0, 16);

    // Live gBattleMons values for the two battlers, at the compiled 0x2A HP offset.
    TEST_ASSERT(hns_cfg->battle_mons_size == 136, "BattlePokemon stride must be 136");
    TEST_ASSERT(hns_cfg->battle_mons_hp_offset == 0x2A, "BattlePokemon.hp must be 0x2A");
    hns_battle_set_mon(&fx, 0, 155, 28);
    hns_battle_set_mon(&fx, 1, 16, 12);

    // gBattlerPartyIndexes is an independent symbol at 0x144. Plant a decoy exactly where the
    // old code looked (gBattleMons - 24) so that any reintroduced derivation is caught.
    TEST_ASSERT(hns_cfg->battler_party_indexes_offset == 0x144,
                "gBattlerPartyIndexes must be declared as its own symbol");
    TEST_ASSERT(hns_cfg->battler_party_indexes_offset != hns_cfg->battle_mons_offset - 24,
                "the compiled offset must differ from the legacy gBattleMons - 24 arithmetic");
    write16_le_t(gba.ewram + hns_cfg->battle_mons_offset - 24, 1); // decoy slot 1

    PartySnapshot player_snap;
    uint8_t player_count = pokemon_read_player_party_gba(fake_gba_read, &gba.table, gba.ewram,
                                                         sizeof(gba.ewram), hns_cfg, &player_snap);
    TEST_ASSERT(player_count == 2, "Player party count should be 2");
    TEST_ASSERT(player_snap.members[0].species == 155, "Slot 0 should be Cyndaquil");
    TEST_ASSERT(player_snap.members[0].current_hp == 28, "Cyndaquil HP must sync from gBattleMons[0].hp");
    TEST_ASSERT(player_snap.members[1].current_hp == 50, "Totodile HP must stay at its party value");
    TEST_ASSERT(player_snap.active_battler_known, "the active player slot must be known");
    TEST_ASSERT(player_snap.active_battler_slot == 0,
                "the active slot must come from the real gBattlerPartyIndexes symbol, not the decoy");

    // Expansion parsing flows through the party reader.
    TEST_ASSERT(player_snap.members[1].ability_num == 0 &&
                player_snap.members[1].ability_slot_known,
                "Totodile's abilityNum must come from the party reader's expansion layout");

    // 2. Enemy party + live HP sync.
    PartySnapshot enemy_snap;
    uint8_t enemy_count = pokemon_read_enemy_party_gba(fake_gba_read, &gba.table, gba.ewram,
                                                       sizeof(gba.ewram), hns_cfg, &enemy_snap);
    TEST_ASSERT(enemy_count == 2, "Enemy party count should be 2");
    TEST_ASSERT(enemy_snap.members[0].species == 16, "Enemy slot 0 should be Pidgey");
    TEST_ASSERT(enemy_snap.members[0].current_hp == 12, "Pidgey HP must sync from gBattleMons[1].hp");
    TEST_ASSERT(enemy_snap.active_battler_known && enemy_snap.active_battler_slot == 0,
                "the active enemy slot must come from gBattlerPositions + gBattlerPartyIndexes");

    // 3. Faint: count must survive, slot must stay.
    hns_battle_set_mon(&fx, 1, 16, 0);
    ((RawGbaPokemon*)(gba.ewram + hns_cfg->enemy_party_offset))->current_hp = 0;
    PartySnapshot fainted;
    pokemon_read_enemy_party_gba(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram), hns_cfg, &fainted);
    TEST_ASSERT(fainted.count == 2, "the enemy party count must survive a faint");
    TEST_ASSERT(fainted.members[0].species == 16, "slot 0 must remain Pidgey after fainting");
    TEST_ASSERT(fainted.members[0].current_hp == 0, "the fainted HP must be reported");

    // 4. Opponent sends out Rattata: gBattlerPartyIndexes[1] is at symbol + 2.
    hns_battle_set_mon(&fx, 1, 19, 35);
    hns_battle_set_battler(&fx, 1, 1, 1);
    write16_le_t(gba.ewram + hns_cfg->battler_party_indexes_offset - 24 + 2, 0); // decoy
    PartySnapshot sendout;
    pokemon_read_enemy_party_gba(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram), hns_cfg, &sendout);
    TEST_ASSERT(sendout.active_battler_known && sendout.active_battler_slot == 1,
                "the enemy active slot must come from gBattlerPartyIndexes[1]");
    TEST_ASSERT(sendout.members[1].species == 19, "slot 1 must be Rattata");
    TEST_ASSERT(sendout.members[1].current_hp == 35, "Rattata HP must sync after the send-out");

    // 5. Player switches to Totodile.
    hns_battle_set_mon(&fx, 0, 158, 48);
    hns_battle_set_battler(&fx, 0, 0, 1);
    PartySnapshot switched;
    pokemon_read_player_party_gba(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram), hns_cfg, &switched);
    TEST_ASSERT(switched.active_battler_known && switched.active_battler_slot == 1,
                "the player active slot must follow the symbol");
    TEST_ASSERT(switched.members[1].current_hp == 48, "Totodile HP must sync after the switch");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_heart_and_soul_party_and_battle_hp_sync" ANSI_RESET "\n");
}

/**
 * The lifecycle gate: DualDex must not present an opponent unless the engine says a battle is
 * running, and it must clear battle-derived state at the exit edge.
 */
static void test_hns_battle_lifecycle_gates_enemy_state(void) {
    printf("Running test_hns_battle_lifecycle_gates_enemy_state...\n");

    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    static FakeGba gba;
    HnsBattleFixture fx;
    pokemon_reader_reset();
    hns_battle_fixture_init(&fx, &gba, cfg);
    hns_battle_fill_player_party(&fx, 2);
    hns_battle_fill_enemy_party(&fx, 4);

    PartySnapshot snap;
    BattleStateRaw state;

    // --- Overworld before any battle -------------------------------------------------------
    hns_battle_set_in_battle(&fx, false);
    hns_battle_set_counters(&fx, 0, 0, 0);
    TEST_ASSERT(pokemon_read_battle_lifecycle(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                              cfg, &state) == BATTLE_LIFECYCLE_INACTIVE,
                "the overworld must report INACTIVE from the engine's own flag");
    TEST_ASSERT(state.in_battle_flag_readable, "gMain.inBattle must be readable through IWRAM");
    TEST_ASSERT(pokemon_read_enemy_party_gba(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                             cfg, &snap) == 0,
                "no enemy party may be reported outside a battle");
    TEST_ASSERT(snap.active_battler_slot == -1 && !snap.active_battler_known,
                "an inactive battle must not report an active enemy slot");

    ActiveEnemyInfo info;
    TEST_ASSERT(pokemon_resolve_active_enemy(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                             cfg, &snap, &info) == ACTIVE_ENEMY_NONE_ACTIVE,
                "the overworld must resolve to NONE_ACTIVE");

    // --- Battle enter ----------------------------------------------------------------------
    hns_battle_begin_single_wild(&fx, 0, 16);
    TEST_ASSERT(pokemon_read_battle_lifecycle(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                              cfg, &state) == BATTLE_LIFECYCLE_ACTIVE,
                "a fully described single battle must report ACTIVE");
    TEST_ASSERT(state.kind == BATTLE_KIND_WILD_SINGLE, "a wild single battle must classify as such");
    TEST_ASSERT(state.battlers_count == 2, "gBattlersCount must be read as 2");
    TEST_ASSERT(pokemon_read_enemy_party_gba(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                             cfg, &snap) == 4,
                "an active battle must report the authoritative enemy party");
    TEST_ASSERT(snap.active_battler_known && snap.active_battler_slot == 0,
                "the active enemy slot must be slot 0 here");
    TEST_ASSERT(pokemon_battle_is_single_opponent(fake_gba_read, &gba.table, gba.ewram,
                                                  sizeof(gba.ewram), cfg),
                "a single-opponent battle must be reported as presentable");

    // --- Opponent switch 0 -> 2 -------------------------------------------------------------
    hns_battle_set_battler(&fx, 1, 1, 2);
    hns_battle_set_mon(&fx, 1, 21, 33);
    TEST_ASSERT(pokemon_read_enemy_party_gba(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                             cfg, &snap) == 4,
                "the enemy party is still authoritative after a switch");
    TEST_ASSERT(snap.active_battler_known && snap.active_battler_slot == 2,
                "the opponent switch must move the active slot to the new party slot");
    TEST_ASSERT(snap.members[2].current_hp == 33, "the new opponent's HP must sync");

    // --- Party index outside the authoritative count is not clamped --------------------------
    hns_battle_set_battler(&fx, 1, 1, 5);
    hns_battle_set_mon(&fx, 1, 25, 30);
    pokemon_read_enemy_party_gba(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram), cfg, &snap);
    TEST_ASSERT(snap.active_battler_slot == -1 && !snap.active_battler_known,
                "a party index outside the authoritative enemy count must fail closed");

    // --- Faint transition: the engine's slot is unchanged but the opponent is at 0 HP --------
    hns_battle_set_battler(&fx, 1, 1, 1);
    hns_battle_set_mon(&fx, 1, 19, 0);
    TEST_ASSERT(pokemon_resolve_active_enemy(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                             cfg, &snap, &info) == ACTIVE_ENEMY_SLOT,
                "a fainted opponent still has an authoritative slot");
    TEST_ASSERT(info.party_slot == 1, "the fainted opponent's slot must be the engine's slot");
    TEST_ASSERT(info.fainted, "the faint must be reported so the caller can degrade");

    // --- Player faint transition ---------------------------------------------------------------
    hns_battle_set_battler(&fx, 1, 1, 1);
    hns_battle_set_mon(&fx, 1, 19, 40);
    hns_battle_set_mon(&fx, 0, 155, 0);           // player's active battler faints
    PartySnapshot player_fainted;
    pokemon_read_player_party_gba(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                  cfg, &player_fainted);
    TEST_ASSERT(player_fainted.active_battler_slot == -1 && !player_fainted.active_battler_known,
                "a fainted player battler must leave the active player slot unknown until it moves");

    // --- Battle exit --------------------------------------------------------------------------
    hns_battle_set_in_battle(&fx, false);
    hns_battle_set_counters(&fx, 0, 0, 0);
    TEST_ASSERT(pokemon_read_battle_lifecycle(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                              cfg, &state) == BATTLE_LIFECYCLE_INACTIVE,
                "battle exit must report INACTIVE");
    // The engine did not clear gEnemyPartyCount on this exit path; the gate must still hold.
    TEST_ASSERT(gba.ewram[cfg->enemy_party_count_offset] == 4,
                "fixture precondition: a stale enemy count is still in memory");
    TEST_ASSERT(pokemon_read_enemy_party_gba(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                             cfg, &snap) == 0,
                "a stale enemy count must not survive into the overworld");
    TEST_ASSERT(snap.active_battler_slot == -1, "no active enemy may survive a battle exit");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_battle_lifecycle_gates_enemy_state" ANSI_RESET "\n");
}

/**
 * A stale gBattleMons species word is not battle evidence.
 *
 * This is the regression for the original bug: after a battle ends, EWRAM .bss still holds the
 * opponent's BattlePokemon. Nothing about that word may produce an opponent.
 */
static void test_hns_stale_battle_mon_cannot_invent_opponent(void) {
    printf("Running test_hns_stale_battle_mon_cannot_invent_opponent...\n");

    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    static FakeGba gba;
    HnsBattleFixture fx;
    pokemon_reader_reset();
    hns_battle_fixture_init(&fx, &gba, cfg);
    hns_battle_fill_player_party(&fx, 2);
    hns_battle_fill_enemy_party(&fx, 6);

    // Previous battle: opponent at party slot 3.
    hns_battle_begin_single_wild(&fx, 3, 23);
    PartySnapshot snap;
    ActiveEnemyInfo info;
    TEST_ASSERT(pokemon_resolve_active_enemy(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                             cfg, &snap, &info) == ACTIVE_ENEMY_SLOT,
                "the previous battle must resolve to a slot");
    TEST_ASSERT(info.party_slot == 3, "the previous battle's slot must be 3");

    // Battle ends. gBattleMons, gBattlerPartyIndexes and gEnemyPartyCount are all left dirty,
    // exactly as an abrupt engine teardown can leave them.
    hns_battle_set_in_battle(&fx, false);
    hns_battle_set_counters(&fx, 0, 0, 0);

    TEST_ASSERT(pokemon_resolve_active_enemy(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                             cfg, &snap, &info) == ACTIVE_ENEMY_NONE_ACTIVE,
                "a stale previous battle must resolve to NONE_ACTIVE");
    TEST_ASSERT(info.party_slot == -1, "a stale previous slot must never be returned");
    TEST_ASSERT(snap.active_battler_slot == -1 && !snap.active_battler_known,
                "a stale previous slot must not survive in the snapshot");

    // A new battle initializing (the engine is in, but the battler set is not usable yet) must
    // not fall back to the previous slot either.
    hns_battle_set_in_battle(&fx, true);
    hns_battle_set_counters(&fx, 0, 0, 0);
    BattleStateRaw state;
    TEST_ASSERT(pokemon_read_battle_lifecycle(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                              cfg, &state) == BATTLE_LIFECYCLE_INITIALIZING,
                "an engine-held battle with no battler count must report INITIALIZING");
    TEST_ASSERT(pokemon_resolve_active_enemy(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                             cfg, &snap, &info) == ACTIVE_ENEMY_NONE_ACTIVE,
                "an initializing battle must not name an opponent");
    TEST_ASSERT(info.party_slot == -1, "an initializing battle must not reuse the previous slot");

    // A recorded outcome means teardown is under way: also not presentable.
    hns_battle_begin_single_wild(&fx, 3, 23);
    hns_battle_set_counters(&fx, 2, 0, 1 /* B_OUTCOME_WON */);
    TEST_ASSERT(pokemon_read_battle_lifecycle(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                              cfg, &state) == BATTLE_LIFECYCLE_ENDING,
                "a recorded battle outcome must report ENDING");
    TEST_ASSERT(pokemon_read_enemy_party_gba(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                             cfg, &snap) == 0,
                "a battle that is ending must not report an enemy party");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_stale_battle_mon_cannot_invent_opponent" ANSI_RESET "\n");
}

/**
 * Production battle presence for H&S must come from the authoritative lifecycle and from nothing
 * else.
 *
 * This exercises `pokemon_read_battle_presence_gba()`, which is the function the JNI
 * `nativeReadBattlePresence` entry point calls, i.e. the actual signal the app's `isInBattle`
 * is built from. The old reading tested `gBattleMons[0].species` and would report a stale
 * opponent as a running battle.
 */
static void test_hns_production_battle_presence_uses_lifecycle(void) {
    printf("Running test_hns_production_battle_presence_uses_lifecycle...\n");

    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    static FakeGba gba;
    HnsBattleFixture fx;
    pokemon_reader_reset();
    hns_battle_fixture_init(&fx, &gba, cfg);
    hns_battle_fill_player_party(&fx, 2);
    hns_battle_fill_enemy_party(&fx, 4);
    hns_battle_begin_single_wild(&fx, 1, 19);

    uint8_t presence;

    // --- 1. Stale battle state with gMain.inBattle clear -> ABSENT ---------------------------
    // gBattleMons[0..3] and gEnemyPartyCount are left populated on purpose: an abrupt engine
    // teardown can leave exactly this behind, and the old species-based reader reported it as a
    // battle.
    hns_battle_set_in_battle(&fx, false);
    hns_battle_set_counters(&fx, 0, 0, 0);
    TEST_ASSERT(pokemon_test_species_word(&gba, cfg, 0) != 0,
                "fixture precondition: a plausible stale gBattleMons species must remain in EWRAM");
    TEST_ASSERT(gba.ewram[cfg->enemy_party_count_offset] != 0,
                "fixture precondition: a stale gEnemyPartyCount must remain in EWRAM");
    presence = pokemon_read_battle_presence_gba(fake_gba_read, &gba.table, gba.ewram,
                                                sizeof(gba.ewram), cfg);
    TEST_ASSERT(presence == 0,
                "stale gBattleMons + gMain.inBattle == false must report ABSENT, never PRESENT");

    // --- 2. Fully described single battle -> PRESENT ------------------------------------------
    hns_battle_begin_single_wild(&fx, 1, 19);
    presence = pokemon_read_battle_presence_gba(fake_gba_read, &gba.table, gba.ewram,
                                                sizeof(gba.ewram), cfg);
    TEST_ASSERT(presence == 1, "a fully described single battle must report PRESENT");

    // --- 3. Engine holding a battle but the battler set is not usable -> UNKNOWN ---------------
    hns_battle_set_counters(&fx, 0, 0, 0);
    presence = pokemon_read_battle_presence_gba(fake_gba_read, &gba.table, gba.ewram,
                                                sizeof(gba.ewram), cfg);
    TEST_ASSERT(presence == 2,
                "gMain.inBattle == true with gBattlersCount == 0 must report UNKNOWN, not PRESENT");

    // --- 4. Teardown (outcome recorded) -> UNKNOWN ----------------------------------------------
    hns_battle_begin_single_wild(&fx, 1, 19);
    hns_battle_set_counters(&fx, 2, 0, 1 /* B_OUTCOME_WON */);
    presence = pokemon_read_battle_presence_gba(fake_gba_read, &gba.table, gba.ewram,
                                                sizeof(gba.ewram), cfg);
    TEST_ASSERT(presence == 2,
                "a battle that is ending must report UNKNOWN, never PRESENT");

    // --- 5. Unreadable gate -> UNKNOWN ------------------------------------------------------------
    {
        static FakeGba no_iwram;
        fake_gba_init(&no_iwram, false, true);
        hns_battle_begin_single_wild(&fx, 1, 19);
        memcpy(no_iwram.ewram, gba.ewram, sizeof(no_iwram.ewram));
        presence = pokemon_read_battle_presence_gba(fake_gba_read, &no_iwram.table, no_iwram.ewram,
                                                    sizeof(no_iwram.ewram), cfg);
        TEST_ASSERT(presence == 2,
                    "an unreadable gMain.inBattle must report UNKNOWN, never PRESENT or ABSENT");
    }

    // --- 6. The reader-less entry point cannot answer for H&S ------------------------------------
    TEST_ASSERT(pokemon_read_battle_presence(gba.ewram, sizeof(gba.ewram), cfg) == 2,
                "the reader-less presence entry point must report UNKNOWN for H&S");

    // --- 7. NULL / unknown configurations fail closed ---------------------------------------------
    TEST_ASSERT(pokemon_read_battle_presence_gba(fake_gba_read, &gba.table, gba.ewram,
                                                 sizeof(gba.ewram), NULL) == 2,
                "a NULL config must report UNKNOWN presence");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_production_battle_presence_uses_lifecycle" ANSI_RESET "\n");
}

/**
 * A perfectly battle-shaped EWRAM snapshot must not become ACTIVE when the authoritative IWRAM
 * gate is unavailable.
 *
 * The gate is the only evidence that distinguishes "a battle is running" from "EWRAM still holds
 * the last battle", so an unreadable gate cannot be reconstructed from EWRAM.
 */
static void test_hns_unreadable_lifecycle_gate_never_active(void) {
    printf("Running test_hns_unreadable_lifecycle_gate_never_active...\n");

    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    static FakeGba gba;
    static FakeGba no_iwram;
    HnsBattleFixture fx;
    BattleStateRaw state;
    PartySnapshot snap;
    ActiveEnemyInfo info;

    // --- doubles-shaped, four battlers, everything internally consistent ----------------------
    pokemon_reader_reset();
    hns_battle_fixture_init(&fx, &gba, cfg);
    hns_battle_fill_player_party(&fx, 2);
    hns_battle_fill_enemy_party(&fx, 4);

    const uint32_t BATTLE_TYPE_DOUBLE = 1u << 0;
    hns_battle_set_in_battle(&fx, true);
    hns_battle_set_counters(&fx, 4, BATTLE_TYPE_DOUBLE, 0);
    gba.ewram[cfg->absent_battler_flags_offset] = 0;
    hns_battle_set_battler(&fx, 0, 0, 0);
    hns_battle_set_battler(&fx, 1, 1, 0);
    hns_battle_set_battler(&fx, 2, 2, 1);
    hns_battle_set_battler(&fx, 3, 3, 1);
    hns_battle_set_mon(&fx, 0, 155, 50);
    hns_battle_set_mon(&fx, 1, 16, 40);
    hns_battle_set_mon(&fx, 2, 158, 60);
    hns_battle_set_mon(&fx, 3, 19, 35);

    // With IWRAM present this is a genuine, ACTIVE doubles battle.
    TEST_ASSERT(pokemon_read_battle_lifecycle(fake_gba_read, &gba.table, gba.ewram,
                                              sizeof(gba.ewram), cfg, &state) == BATTLE_LIFECYCLE_ACTIVE,
                "fixture precondition: the doubles snapshot must be ACTIVE while IWRAM is readable");

    // Remove IWRAM only. The EWRAM snapshot is byte-for-byte identical and still perfectly
    // battle-shaped, which is exactly the trap.
    fake_gba_init(&no_iwram, false, true);
    memcpy(no_iwram.ewram, gba.ewram, sizeof(no_iwram.ewram));

    TEST_ASSERT(pokemon_read_battle_lifecycle(fake_gba_read, &no_iwram.table, no_iwram.ewram,
                                              sizeof(no_iwram.ewram), cfg, &state) != BATTLE_LIFECYCLE_ACTIVE,
                "an unreadable gate must never produce ACTIVE, even with four valid battlers");
    TEST_ASSERT(state.lifecycle == BATTLE_LIFECYCLE_UNKNOWN,
                "an unreadable gate must report UNKNOWN");
    TEST_ASSERT(!state.in_battle_flag_readable, "the unreadable flag must not be reported as read");

    ActiveEnemyState unreadable_doubles =
        pokemon_resolve_active_enemy(fake_gba_read, &no_iwram.table, no_iwram.ewram,
                                     sizeof(no_iwram.ewram), cfg, &snap, &info);
    TEST_ASSERT(unreadable_doubles == ACTIVE_ENEMY_UNKNOWN,
                "an unreadable gate over doubles-shaped EWRAM must report UNKNOWN");
    TEST_ASSERT(unreadable_doubles != ACTIVE_ENEMY_AMBIGUOUS,
                "ambiguity requires a PROVEN active battle; an unreadable gate proves nothing");
    TEST_ASSERT(unreadable_doubles != ACTIVE_ENEMY_NONE_ACTIVE,
                "an unreadable gate must not assert that there is definitely no opponent");
    TEST_ASSERT(info.party_slot == -1, "an unreadable gate must not yield a party slot");
    TEST_ASSERT(info.battler_index == -1, "an unreadable gate must not yield a battler index");
    TEST_ASSERT(info.opponent_battlers == 0,
                "no opponent battler may be counted while the battle authority is unreadable");

    // --- singles-shaped, two battlers -----------------------------------------------------------
    pokemon_reader_reset();
    hns_battle_fixture_init(&fx, &gba, cfg);
    hns_battle_fill_player_party(&fx, 2);
    hns_battle_fill_enemy_party(&fx, 4);
    hns_battle_begin_single_wild(&fx, 2, 21);
    TEST_ASSERT(pokemon_read_battle_lifecycle(fake_gba_read, &gba.table, gba.ewram,
                                              sizeof(gba.ewram), cfg, &state) == BATTLE_LIFECYCLE_ACTIVE,
                "fixture precondition: the singles snapshot must be ACTIVE while IWRAM is readable");

    fake_gba_init(&no_iwram, false, true);
    memcpy(no_iwram.ewram, gba.ewram, sizeof(no_iwram.ewram));

    TEST_ASSERT(pokemon_read_battle_lifecycle(fake_gba_read, &no_iwram.table, no_iwram.ewram,
                                              sizeof(no_iwram.ewram), cfg, &state) == BATTLE_LIFECYCLE_UNKNOWN,
                "an unreadable gate with two valid battlers must report UNKNOWN");
    ActiveEnemyState unreadable_singles =
        pokemon_resolve_active_enemy(fake_gba_read, &no_iwram.table, no_iwram.ewram,
                                     sizeof(no_iwram.ewram), cfg, &snap, &info);
    TEST_ASSERT(unreadable_singles == ACTIVE_ENEMY_UNKNOWN,
                "an unreadable gate over singles-shaped EWRAM must report UNKNOWN");
    TEST_ASSERT(info.party_slot == -1, "no party slot may be produced without the gate");
    TEST_ASSERT(info.battler_index == -1, "no battler index may be produced without the gate");
    TEST_ASSERT(!pokemon_battle_is_single_opponent(fake_gba_read, &no_iwram.table, no_iwram.ewram,
                                                   sizeof(no_iwram.ewram), cfg),
                "an unreadable gate is not a presentable single-opponent battle");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_unreadable_lifecycle_gate_never_active" ANSI_RESET "\n");
}

/**
 * The lifecycle -> ActiveEnemyState mapping must preserve the difference between
 * "the authoritative state says there is no opponent" (NONE_ACTIVE) and
 * "the authoritative state could not be read" (UNKNOWN).
 */
static void test_hns_lifecycle_maps_to_distinct_active_enemy_states(void) {
    printf("Running test_hns_lifecycle_maps_to_distinct_active_enemy_states...\n");

    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    static FakeGba gba;
    HnsBattleFixture fx;
    BattleStateRaw state;
    PartySnapshot snap;
    ActiveEnemyInfo info;

    pokemon_reader_reset();
    hns_battle_fixture_init(&fx, &gba, cfg);
    hns_battle_fill_player_party(&fx, 2);
    hns_battle_fill_enemy_party(&fx, 4);
    hns_battle_begin_single_wild(&fx, 1, 19);

    // --- INACTIVE -> NONE_ACTIVE ---------------------------------------------------------------
    hns_battle_set_in_battle(&fx, false);
    hns_battle_set_counters(&fx, 0, 0, 0);
    TEST_ASSERT(pokemon_read_battle_lifecycle(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                              cfg, &state) == BATTLE_LIFECYCLE_INACTIVE,
                "the overworld must be INACTIVE");
    TEST_ASSERT(pokemon_resolve_active_enemy(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                             cfg, &snap, &info) == ACTIVE_ENEMY_NONE_ACTIVE,
                "INACTIVE must map to NONE_ACTIVE");

    // --- INITIALIZING -> NONE_ACTIVE -------------------------------------------------------------
    hns_battle_set_in_battle(&fx, true);
    hns_battle_set_counters(&fx, 0, 0, 0);
    TEST_ASSERT(pokemon_read_battle_lifecycle(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                              cfg, &state) == BATTLE_LIFECYCLE_INITIALIZING,
                "an engine-held battle without a battler count must be INITIALIZING");
    TEST_ASSERT(pokemon_resolve_active_enemy(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                             cfg, &snap, &info) == ACTIVE_ENEMY_NONE_ACTIVE,
                "INITIALIZING must map to NONE_ACTIVE");

    // --- ENDING -> NONE_ACTIVE --------------------------------------------------------------------
    hns_battle_begin_single_wild(&fx, 1, 19);
    hns_battle_set_counters(&fx, 2, 0, 1 /* B_OUTCOME_WON */);
    TEST_ASSERT(pokemon_read_battle_lifecycle(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                              cfg, &state) == BATTLE_LIFECYCLE_ENDING,
                "a recorded outcome must be ENDING");
    TEST_ASSERT(pokemon_resolve_active_enemy(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                             cfg, &snap, &info) == ACTIVE_ENEMY_NONE_ACTIVE,
                "ENDING must map to NONE_ACTIVE");

    // --- ACTIVE single -> SLOT ---------------------------------------------------------------------
    hns_battle_begin_single_wild(&fx, 2, 21);
    TEST_ASSERT(pokemon_read_battle_lifecycle(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                              cfg, &state) == BATTLE_LIFECYCLE_ACTIVE,
                "a fully described single battle must be ACTIVE");
    TEST_ASSERT(pokemon_resolve_active_enemy(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                             cfg, &snap, &info) == ACTIVE_ENEMY_SLOT,
                "ACTIVE with one opponent must map to SLOT");
    TEST_ASSERT(info.party_slot == 2, "the ACTIVE single must resolve the engine's slot (2)");

    // --- ACTIVE doubles with two opponents -> AMBIGUOUS ----------------------------------------------
    const uint32_t BATTLE_TYPE_DOUBLE = 1u << 0;
    hns_battle_set_in_battle(&fx, true);
    hns_battle_set_counters(&fx, 4, BATTLE_TYPE_DOUBLE, 0);
    gba.ewram[cfg->absent_battler_flags_offset] = 0;
    hns_battle_set_battler(&fx, 0, 0, 0);
    hns_battle_set_battler(&fx, 1, 1, 0);
    hns_battle_set_battler(&fx, 2, 2, 1);
    hns_battle_set_battler(&fx, 3, 3, 1);
    hns_battle_set_mon(&fx, 0, 155, 50);
    hns_battle_set_mon(&fx, 1, 16, 40);
    hns_battle_set_mon(&fx, 2, 158, 60);
    hns_battle_set_mon(&fx, 3, 19, 35);
    TEST_ASSERT(pokemon_read_battle_lifecycle(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                              cfg, &state) == BATTLE_LIFECYCLE_ACTIVE,
                "a fully described doubles battle must be ACTIVE");
    TEST_ASSERT(pokemon_resolve_active_enemy(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                             cfg, &snap, &info) == ACTIVE_ENEMY_AMBIGUOUS,
                "ACTIVE with two opponents must map to AMBIGUOUS");
    TEST_ASSERT(info.party_slot == -1, "AMBIGUOUS must carry no slot");

    // --- UNKNOWN (unreadable gate) -> UNKNOWN, never NONE_ACTIVE --------------------------------------
    {
        static FakeGba no_iwram;
        fake_gba_init(&no_iwram, false, true);
        hns_battle_begin_single_wild(&fx, 2, 21);
        memcpy(no_iwram.ewram, gba.ewram, sizeof(no_iwram.ewram));
        TEST_ASSERT(pokemon_read_battle_lifecycle(fake_gba_read, &no_iwram.table, no_iwram.ewram,
                                                  sizeof(no_iwram.ewram), cfg, &state) ==
                        BATTLE_LIFECYCLE_UNKNOWN,
                    "an unreadable gate must be UNKNOWN");
        ActiveEnemyState mapped =
            pokemon_resolve_active_enemy(fake_gba_read, &no_iwram.table, no_iwram.ewram,
                                         sizeof(no_iwram.ewram), cfg, &snap, &info);
        TEST_ASSERT(mapped == ACTIVE_ENEMY_UNKNOWN, "UNKNOWN must map to ACTIVE_ENEMY_UNKNOWN");
        TEST_ASSERT(mapped != ACTIVE_ENEMY_NONE_ACTIVE,
                    "UNKNOWN must not be collapsed into NONE_ACTIVE");
        TEST_ASSERT(info.party_slot == -1, "UNKNOWN must carry no slot");
        TEST_ASSERT(info.battler_index == -1, "UNKNOWN must carry no battler index");
    }

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_lifecycle_maps_to_distinct_active_enemy_states" ANSI_RESET "\n");
}

/**
 * `ActiveEnemyInfo.battler_index` must be the actual resolved battler index, never the number of
 * active opponent battlers.
 */
static void test_hns_active_battler_index_is_the_real_battler(void) {
    printf("Running test_hns_active_battler_index_is_the_real_battler...\n");

    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    static FakeGba gba;
    HnsBattleFixture fx;
    PartySnapshot snap;
    ActiveEnemyInfo info;

    // --- ordinary single battle: opponent is battler 1 ----------------------------------------
    pokemon_reader_reset();
    hns_battle_fixture_init(&fx, &gba, cfg);
    hns_battle_fill_player_party(&fx, 2);
    hns_battle_fill_enemy_party(&fx, 4);
    hns_battle_begin_single_wild(&fx, 2, 21);
    TEST_ASSERT(pokemon_resolve_active_enemy(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                             cfg, &snap, &info) == ACTIVE_ENEMY_SLOT,
                "the single battle must resolve a slot");
    TEST_ASSERT(info.party_slot == 2, "the resolved slot must be the engine's slot (2)");
    TEST_ASSERT(info.battler_index == 1,
                "the resolved battler index must be 1 for a standard single battle");
    TEST_ASSERT(info.opponent_battlers == 1,
                "the opponent battler count must be reported separately");

    // --- single battle whose only opponent battler is NOT trivially battler 1 -------------------
    // gBattlerPositions encodes the side in bit 1, so an opponent-side battler must have an odd
    // position. Placing it on battler 0 proves the index comes from the resolution, not from a
    // hard-coded 1 and not from opponent_battlers.
    pokemon_reader_reset();
    hns_battle_fixture_init(&fx, &gba, cfg);
    hns_battle_fill_player_party(&fx, 2);
    hns_battle_fill_enemy_party(&fx, 4);
    hns_battle_set_in_battle(&fx, true);
    hns_battle_set_counters(&fx, 2, 0, 0);
    gba.ewram[cfg->absent_battler_flags_offset] = 0;
    hns_battle_set_battler(&fx, 0, 1, 3);   // opponent left, enemy party slot 3
    hns_battle_set_battler(&fx, 1, 0, 0);   // player left, player party slot 0
    hns_battle_set_mon(&fx, 0, 25, 44);
    hns_battle_set_mon(&fx, 1, 155, 50);

    TEST_ASSERT(pokemon_resolve_active_enemy(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                             cfg, &snap, &info) == ACTIVE_ENEMY_SLOT,
                "the non-trivial topology must still resolve a slot");
    TEST_ASSERT(info.party_slot == 3, "the resolved slot must be 3");
    TEST_ASSERT(info.battler_index == 0,
                "the battler index must be the real resolved battler (0), not opponent_battlers (1)");
    TEST_ASSERT(info.opponent_battlers == 1, "exactly one opponent battler must be counted");
    TEST_ASSERT(info.battler_index != (int8_t)info.opponent_battlers ||
                info.battler_index == 0,
                "battler_index must not be inferred from opponent_battlers");

    // The player side records its own battler index from the snapshot.
    PartySnapshot player_snap;
    pokemon_read_player_party_gba(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                  cfg, &player_snap);
    TEST_ASSERT(player_snap.active_battler_known, "the player's active battler must be known");
    TEST_ASSERT(player_snap.active_battler_index == 1,
                "the player's active battler index must be the real resolved battler (1)");
    TEST_ASSERT(player_snap.active_battler_slot == 0, "the player's active slot must be 0");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_active_battler_index_is_the_real_battler" ANSI_RESET "\n");
}

/**
 * Battle exit must clear production presence as well as the opponent, even while stale battle
 * state remains in EWRAM.
 */
static void test_hns_battle_exit_clears_production_presence(void) {
    printf("Running test_hns_battle_exit_clears_production_presence...\n");

    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    static FakeGba gba;
    HnsBattleFixture fx;
    pokemon_reader_reset();
    hns_battle_fixture_init(&fx, &gba, cfg);
    hns_battle_fill_player_party(&fx, 2);
    hns_battle_fill_enemy_party(&fx, 6);

    // Previous battle with the opponent at party slot 3.
    hns_battle_begin_single_wild(&fx, 3, 23);
    PartySnapshot snap;
    ActiveEnemyInfo info;
    TEST_ASSERT(pokemon_read_battle_presence_gba(fake_gba_read, &gba.table, gba.ewram,
                                                 sizeof(gba.ewram), cfg) == 1,
                "the previous battle must report PRESENT");
    TEST_ASSERT(pokemon_resolve_active_enemy(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                             cfg, &snap, &info) == ACTIVE_ENEMY_SLOT,
                "the previous battle must resolve a slot");

    // Battle ends; gBattleMons, gBattlerPartyIndexes and gEnemyPartyCount all stay dirty.
    hns_battle_set_in_battle(&fx, false);
    hns_battle_set_counters(&fx, 0, 0, 0);

    TEST_ASSERT(pokemon_test_species_word(&gba, cfg, 0) != 0,
                "fixture precondition: the stale battle mon must remain readable");
    TEST_ASSERT(pokemon_read_battle_presence_gba(fake_gba_read, &gba.table, gba.ewram,
                                                 sizeof(gba.ewram), cfg) == 0,
                "battle exit must report ABSENT production presence");
    TEST_ASSERT(pokemon_resolve_active_enemy(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                             cfg, &snap, &info) == ACTIVE_ENEMY_NONE_ACTIVE,
                "battle exit must resolve to NONE_ACTIVE");
    TEST_ASSERT(info.party_slot == -1, "battle exit must not leave a slot");
    TEST_ASSERT(info.battler_index == -1, "battle exit must not leave a battler index");
    TEST_ASSERT(snap.active_battler_slot == -1 && !snap.active_battler_known,
                "battle exit must leave the snapshot slot unknown");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_battle_exit_clears_production_presence" ANSI_RESET "\n");
}

/**
 * Doubles: two active opponent battlers must degrade, not silently pick one.
 */
static void test_hns_doubles_degrades_instead_of_guessing(void) {
    printf("Running test_hns_doubles_degrades_instead_of_guessing...\n");

    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    static FakeGba gba;
    HnsBattleFixture fx;
    pokemon_reader_reset();
    hns_battle_fixture_init(&fx, &gba, cfg);
    hns_battle_fill_player_party(&fx, 2);
    hns_battle_fill_enemy_party(&fx, 2);

    hns_battle_set_in_battle(&fx, true);
    const uint32_t BATTLE_TYPE_DOUBLE = 1u << 0;
    hns_battle_set_counters(&fx, 4, BATTLE_TYPE_DOUBLE, 0);
    gba.ewram[cfg->absent_battler_flags_offset] = 0;
    // positions: 0 = player left, 1 = opponent left, 2 = player right, 3 = opponent right
    hns_battle_set_battler(&fx, 0, 0, 0);
    hns_battle_set_battler(&fx, 1, 1, 0);
    hns_battle_set_battler(&fx, 2, 2, 1);
    hns_battle_set_battler(&fx, 3, 3, 1);
    hns_battle_set_mon(&fx, 0, 155, 50);
    hns_battle_set_mon(&fx, 1, 16, 40);
    hns_battle_set_mon(&fx, 2, 158, 60);
    hns_battle_set_mon(&fx, 3, 19, 35);

    BattleStateRaw state;
    TEST_ASSERT(pokemon_read_battle_lifecycle(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                              cfg, &state) == BATTLE_LIFECYCLE_ACTIVE,
                "a doubles battle must report ACTIVE");
    TEST_ASSERT(state.kind == BATTLE_KIND_DOUBLES, "a doubles battle must classify as DOUBLES");
    TEST_ASSERT(state.battlers_count == 4, "a doubles battle must report gBattlersCount == 4");

    PartySnapshot snap;
    ActiveEnemyInfo info;
    TEST_ASSERT(pokemon_resolve_active_enemy(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                             cfg, &snap, &info) == ACTIVE_ENEMY_AMBIGUOUS,
                "two active opponent battlers must be reported as ambiguous");
    TEST_ASSERT(info.party_slot == -1, "doubles must not select an opponent");
    TEST_ASSERT(info.opponent_battlers == 2, "both opponent battlers must be counted");
    TEST_ASSERT(snap.active_battler_slot == -1 && !snap.active_battler_known,
                "doubles must leave the active enemy slot unknown");
    TEST_ASSERT(snap.active_enemy_ambiguous, "the ambiguity must be explicit in the snapshot");
    TEST_ASSERT(!pokemon_battle_is_single_opponent(fake_gba_read, &gba.table, gba.ewram,
                                                   sizeof(gba.ewram), cfg),
                "a doubles battle is not a single-opponent battle");

    // The player's own active slot is equally ambiguous with a partner on the field.
    PartySnapshot player_snap;
    pokemon_read_player_party_gba(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                  cfg, &player_snap);
    TEST_ASSERT(player_snap.active_battler_slot == -1 && !player_snap.active_battler_known,
                "two player-side battlers must leave the active player slot unknown");

    // Multi / partner battles are never presented as singles either.
    const uint32_t BATTLE_TYPE_INGAME_PARTNER = 1u << 22;
    hns_battle_set_counters(&fx, 4, BATTLE_TYPE_DOUBLE | BATTLE_TYPE_INGAME_PARTNER, 0);
    TEST_ASSERT(pokemon_read_battle_lifecycle(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                              cfg, &state) == BATTLE_LIFECYCLE_ACTIVE,
                "a partner battle still reports ACTIVE");
    TEST_ASSERT(state.kind == BATTLE_KIND_MULTI_OR_PARTNER,
                "a partner battle must classify as MULTI_OR_PARTNER, never as doubles or single");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_doubles_degrades_instead_of_guessing" ANSI_RESET "\n");
}

/** Reuse a resolved snapshot across Singles -> permuted four-battler -> Singles. */
static void test_hns_single_to_multi_clears_prior_enemy(void) {
    printf("Running test_hns_single_to_multi_clears_prior_enemy...\n");
    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    static FakeGba gba;
    HnsBattleFixture fx;
    PartySnapshot snap;
    ActiveEnemyInfo info;
    pokemon_reader_reset();
    hns_battle_fixture_init(&fx, &gba, cfg);
    hns_battle_fill_player_party(&fx, 2);
    hns_battle_fill_enemy_party(&fx, 2);
    for (int a = 0; a < 4; a++) for (int b = 0; b < 4; b++)
    for (int c = 0; c < 4; c++) for (int d = 0; d < 4; d++) {
        if (a == b || a == c || a == d || b == c || b == d || c == d) continue;
        const int positions[4] = {a, b, c, d};
        for (int multi = 0; multi < 2; multi++) {
            hns_battle_begin_single_wild(&fx, 1, 19);
            TEST_ASSERT(pokemon_resolve_active_enemy(fake_gba_read, &gba.table, gba.ewram,
                sizeof(gba.ewram), cfg, &snap, &info) == ACTIVE_ENEMY_SLOT && info.party_slot == 1,
                "Singles must resolve before each ambiguous transition (including recovery)");
            hns_battle_set_counters(&fx, 4, 1u | (multi ? (1u << 15) : 0u), 0);
            gba.ewram[cfg->absent_battler_flags_offset] = 0;
            for (int i = 0; i < 4; i++) {
                hns_battle_set_battler(&fx, i, positions[i], positions[i] / 2);
                hns_battle_set_mon(&fx, i, (positions[i] & 1) ? 19 : 155, 20);
            }
            BattleStateRaw state;
            TEST_ASSERT(pokemon_read_battle_lifecycle(fake_gba_read, &gba.table, gba.ewram,
                sizeof(gba.ewram), cfg, &state) == BATTLE_LIFECYCLE_ACTIVE,
                "all position permutations retain authoritative ACTIVE lifecycle");
            TEST_ASSERT(state.kind == (multi ? BATTLE_KIND_MULTI_OR_PARTNER : BATTLE_KIND_DOUBLES),
                "two-opponent flag distinguishes MULTI_OR_PARTNER from ordinary DOUBLES");
            TEST_ASSERT(pokemon_resolve_active_enemy(fake_gba_read, &gba.table, gba.ewram,
                sizeof(gba.ewram), cfg, &snap, &info) == ACTIVE_ENEMY_AMBIGUOUS,
                "two opponents must stay ambiguous regardless of battler order or prior Singles");
            TEST_ASSERT(info.party_slot == -1 && info.battler_index == -1 && info.opponent_battlers == 2,
                "prior Singles slot/battler must be erased, both opponents counted");
            TEST_ASSERT(!snap.active_battler_known && snap.active_battler_slot == -1 &&
                snap.active_battler_index == -1 && snap.active_enemy_ambiguous,
                "reused snapshot must not retain a single live enemy selection");
            int remaining_enemy = -1;
            for (int i = 0; i < 4; i++) {
                if (positions[i] == 3) gba.ewram[cfg->absent_battler_flags_offset] = (uint8_t)(1u << i);
                if (positions[i] == 1) remaining_enemy = i;
            }
            TEST_ASSERT(pokemon_resolve_active_enemy(fake_gba_read, &gba.table, gba.ewram,
                sizeof(gba.ewram), cfg, &snap, &info) == ACTIVE_ENEMY_SLOT,
                "one present opponent after absence must resolve by position, not index parity");
            TEST_ASSERT(info.opponent_battlers == 1 && info.party_slot == 0 &&
                info.battler_index == remaining_enemy && !snap.active_enemy_ambiguous,
                "permuted late-Doubles resolves the correct remaining opponent and clears ambiguity");
        }
    }
    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_single_to_multi_clears_prior_enemy" ANSI_RESET "\n");
}

/**
 * Invalid battler state must fail closed, not invent a slot.
 */
static void test_hns_invalid_battler_indexes_fail_closed(void) {
    printf("Running test_hns_invalid_battler_indexes_fail_closed...\n");

    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    static FakeGba gba;
    HnsBattleFixture fx;
    PartySnapshot snap;
    ActiveEnemyInfo info;
    BattleStateRaw state;

    // --- party index 6 (one past the last legal slot) -----------------------------------------
    pokemon_reader_reset();
    hns_battle_fixture_init(&fx, &gba, cfg);
    hns_battle_fill_player_party(&fx, 2);
    hns_battle_fill_enemy_party(&fx, 6);
    hns_battle_begin_single_wild(&fx, 6, 16);
    TEST_ASSERT(pokemon_resolve_active_enemy(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                             cfg, &snap, &info) != ACTIVE_ENEMY_SLOT,
                "party index 6 must never resolve to a presentable opponent");
    TEST_ASSERT(info.party_slot == -1, "party index 6 must not yield a slot");

    // --- party index 255 ----------------------------------------------------------------------
    pokemon_reader_reset();
    hns_battle_fixture_init(&fx, &gba, cfg);
    hns_battle_fill_player_party(&fx, 2);
    hns_battle_fill_enemy_party(&fx, 6);
    hns_battle_begin_single_wild(&fx, 255, 16);
    TEST_ASSERT(pokemon_read_battle_lifecycle(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                              cfg, &state) != BATTLE_LIFECYCLE_ACTIVE,
                "party index 255 must not produce an ACTIVE battle snapshot");
    TEST_ASSERT(pokemon_resolve_active_enemy(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                             cfg, &snap, &info) != ACTIVE_ENEMY_SLOT,
                "party index 255 must never resolve to a presentable opponent");

    // --- absent battler ------------------------------------------------------------------------
    pokemon_reader_reset();
    hns_battle_fixture_init(&fx, &gba, cfg);
    hns_battle_fill_player_party(&fx, 2);
    hns_battle_fill_enemy_party(&fx, 2);
    hns_battle_begin_single_wild(&fx, 0, 16);
    gba.ewram[cfg->absent_battler_flags_offset] = (uint8_t)(1u << 1); // opponent battler absent
    TEST_ASSERT(pokemon_resolve_active_enemy(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                             cfg, &snap, &info) == ACTIVE_ENEMY_NONE_ACTIVE,
                "an absent opponent battler must report NONE_ACTIVE, not a slot");
    TEST_ASSERT(info.party_slot == -1, "an absent battler must not yield a slot");
    TEST_ASSERT(pokemon_read_enemy_party_gba(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                             cfg, &snap) == 2,
                "the enemy party itself stays readable while its battler is absent");
    TEST_ASSERT(!snap.active_battler_known, "an absent opponent battler must not mark a slot known");

    // --- unreadable IWRAM ------------------------------------------------------------------------
    // gMain lives in IWRAM. When it cannot be read, the engine's own flag is unavailable and the
    // remaining counters cannot be interpreted: an engine-held battle with no usable battler
    // count must degrade to a transitional state, never to ACTIVE and never to INACTIVE.
    pokemon_reader_reset();
    hns_battle_fixture_init(&fx, &gba, cfg);
    FakeGba no_iwram;
    fake_gba_init(&no_iwram, false, true);
    no_iwram.ewram[cfg->battlers_count_offset] = 0;
    TEST_ASSERT(pokemon_read_battle_lifecycle(fake_gba_read, &no_iwram.table, no_iwram.ewram,
                                              sizeof(no_iwram.ewram), cfg, &state) == BATTLE_LIFECYCLE_UNKNOWN,
                "an unreadable authoritative gate must be UNKNOWN, never ACTIVE or INACTIVE");
    TEST_ASSERT(!state.in_battle_flag_readable, "the unreadable flag must not be reported as read");
    TEST_ASSERT(pokemon_resolve_active_enemy(fake_gba_read, &no_iwram.table, no_iwram.ewram,
                                             sizeof(no_iwram.ewram), cfg, &snap, &info) == ACTIVE_ENEMY_UNKNOWN,
                "an unreadable lifecycle flag must report UNKNOWN, not NONE_ACTIVE");
    TEST_ASSERT(info.party_slot == -1, "an unreadable lifecycle flag must not produce a slot");
    TEST_ASSERT(info.battler_index == -1, "an unreadable lifecycle flag must not produce a battler");

    // --- NULL reader / NULL config ---------------------------------------------------------------
    TEST_ASSERT(pokemon_read_battle_lifecycle(NULL, NULL, gba.ewram, sizeof(gba.ewram), cfg, &state) ==
                    BATTLE_LIFECYCLE_UNKNOWN,
                "a NULL reader must report UNKNOWN lifecycle");
    TEST_ASSERT(pokemon_read_battle_lifecycle(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                              NULL, &state) == BATTLE_LIFECYCLE_UNKNOWN,
                "a NULL config must report UNKNOWN lifecycle");
    GameMemoryConfig unknown_cfg = {0};
    unknown_cfg.game_id = GAME_UNKNOWN;
    TEST_ASSERT(pokemon_read_battle_lifecycle(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                              &unknown_cfg, &state) == BATTLE_LIFECYCLE_UNKNOWN,
                "a GAME_UNKNOWN config must report UNKNOWN lifecycle");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_invalid_battler_indexes_fail_closed" ANSI_RESET "\n");
}

/**
 * A layout with an authoritative enemy count but no absolute-address reader cannot gate on a
 * battle, so it must report nothing rather than fall back to the blind scan.
 */
static void test_hns_authoritative_enemy_count_requires_reader_gate(void) {
    printf("Running test_hns_authoritative_enemy_count_requires_reader_gate...\n");

    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    static FakeGba gba;
    HnsBattleFixture fx;
    pokemon_reader_reset();
    hns_battle_fixture_init(&fx, &gba, cfg);
    hns_battle_fill_player_party(&fx, 2);
    hns_battle_fill_enemy_party(&fx, 2);
    hns_battle_begin_single_wild(&fx, 0, 16);

    PartySnapshot with_reader;
    TEST_ASSERT(pokemon_read_enemy_party_gba(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                             cfg, &with_reader) == 2,
                "an active battle with a reader must read the enemy party");

    PartySnapshot without_reader;
    TEST_ASSERT(pokemon_read_enemy_party(gba.ewram, sizeof(gba.ewram), cfg, &without_reader) == 0,
                "without a battle-capable reader no enemy party may be reported");
    TEST_ASSERT(without_reader.active_battler_slot == -1,
                "a reader-less enemy read must not claim an active slot");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_authoritative_enemy_count_requires_reader_gate" ANSI_RESET "\n");
}

static void test_hns_battle_pokemon_layout_fields(void) {
    printf("Running test_hns_battle_pokemon_layout_fields...\n");

    const size_t EWRAM_SIZE = 256 * 1024;
    uint8_t* ewram = (uint8_t*)calloc(1, EWRAM_SIZE);
    TEST_ASSERT(ewram != NULL, "EWRAM allocation failed");
    pokemon_reader_reset();

    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    const GameMemoryConfig* emerald = pokemon_get_game_config(GAME_EMERALD);

    // The two layouts disagree on stride and HP offset, so an H&S read through the Emerald
    // constants (or vice versa) must produce different, distinguishable results.
    TEST_ASSERT(cfg->battle_mons_size != emerald->battle_mons_size,
                "H&S and Emerald BattlePokemon strides must differ (136 vs 88)");
    TEST_ASSERT(cfg->battle_mons_hp_offset != emerald->battle_mons_hp_offset,
                "H&S HP offset (0x2A) must differ from the vanilla 40");
    TEST_ASSERT(cfg->battle_mons_stat_stages_offset == 0x18,
                "statStages is at 0x18 in the H&S layout");

    // statStages: battler 0 is at stage +1 (Atk). Neutral is 6, so 9 must read as +3.
    uint8_t* b0 = ewram + cfg->battle_mons_offset;
    write16_le_t(b0, 155);
    uint8_t* stages = b0 + cfg->battle_mons_stat_stages_offset;
    memset(stages, 6, 8);
    stages[1] = 9;  // Atk +3
    stages[2] = 3;  // Def -3
    stages[7] = 12; // Eva +6

    int8_t out[7];
    TEST_ASSERT(pokemon_read_battle_stat_stages(ewram, EWRAM_SIZE, cfg, 0, out),
                "stat stages must read from the H&S layout");
    TEST_ASSERT(out[0] == 3, "Atk stage must be +3");
    TEST_ASSERT(out[1] == -3, "Def stage must be -3");
    TEST_ASSERT(out[6] == 6, "Eva stage must be +6");

    // Battler 1 must be reached through the 136-byte stride, not 88.
    uint8_t* b1 = ewram + cfg->battle_mons_offset + cfg->battle_mons_size;
    write16_le_t(b1, 158);
    uint8_t* stages1 = b1 + cfg->battle_mons_stat_stages_offset;
    memset(stages1, 6, 8);
    stages1[3] = 4; // Speed -2
    int8_t out1[7];
    TEST_ASSERT(pokemon_read_battle_stat_stages(ewram, EWRAM_SIZE, cfg, 1, out1),
                "battler 1 stat stages must read");
    TEST_ASSERT(out1[2] == -2, "battler 1 Speed must be -2 (proves the 136-byte stride)");

    // A battler index whose slot runs past the buffer must be rejected, not read out of bounds.
    TEST_ASSERT(!pokemon_read_battle_stat_stages(ewram, cfg->battle_mons_offset + 136, cfg, 1, out1),
                "a battler outside the buffer must be rejected");

    // Battle presence for H&S no longer derives from the gBattleMons species word at all: the
    // layout declares an authoritative lifecycle gate, so the reader without that gate reports
    // UNKNOWN and never infers a battle from EWRAM.
    write16_le_t(b0, 155);
    TEST_ASSERT(pokemon_read_battle_presence(ewram, EWRAM_SIZE, cfg) == 2,
                "a plausible species alone must NOT report presence for H&S");
    TEST_ASSERT(pokemon_read_battle_ui_state(ewram, EWRAM_SIZE, cfg) == 5,
                "battle UI state must stay non-authoritative without controller evidence");

    // Out-of-range species is not even a candidate: still UNKNOWN, never a confident verdict.
    write16_le_t(b0, 2500);
    TEST_ASSERT(pokemon_read_battle_presence(ewram, EWRAM_SIZE, cfg) == 2,
                "an implausible species must degrade to UNKNOWN");

    free(ewram);
    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_battle_pokemon_layout_fields" ANSI_RESET "\n");
}

static void test_unknown_game_still_fails_closed(void) {
    printf("Running test_unknown_game_still_fails_closed...\n");

    const size_t EWRAM_SIZE = 256 * 1024;
    uint8_t* ewram = (uint8_t*)calloc(1, EWRAM_SIZE);
    TEST_ASSERT(ewram != NULL, "EWRAM allocation failed");

    // A config that declares the H&S addresses but NOT the H&S identity must still be rejected:
    // the trust decision is the game id, never a plausible-looking address set.
    GameMemoryConfig unknown_hns_shaped = *pokemon_get_game_config(GAME_HEART_AND_SOUL);
    unknown_hns_shaped.game_id = GAME_UNKNOWN;

    PartySnapshot snapshot;
    PlayerLocationRaw loc;
    int8_t stages[7];

    TEST_ASSERT(pokemon_read_player_party(ewram, EWRAM_SIZE, &unknown_hns_shaped, &snapshot) == 0,
                "an unknown game must not read a party through H&S addresses");
    TEST_ASSERT(pokemon_read_enemy_party(ewram, EWRAM_SIZE, &unknown_hns_shaped, &snapshot) == 0,
                "an unknown game must not read an enemy party through H&S addresses");
    TEST_ASSERT(!pokemon_read_player_location(ewram, EWRAM_SIZE, &unknown_hns_shaped, &loc),
                "an unknown game must not read a location through H&S addresses");
    TEST_ASSERT(!pokemon_read_player_location_gba(fake_gba_read, NULL, ewram, EWRAM_SIZE, &unknown_hns_shaped, &loc),
                "an unknown game must not resolve SaveBlock1 even with a reader present");
    TEST_ASSERT(!pokemon_read_battle_stat_stages(ewram, EWRAM_SIZE, &unknown_hns_shaped, 0, stages),
                "an unknown game must not read battle stat stages");
    TEST_ASSERT(pokemon_read_battle_presence(ewram, EWRAM_SIZE, &unknown_hns_shaped) == 2,
                "an unknown game must report UNKNOWN battle presence, not NOT_OBSERVED");
    TEST_ASSERT(pokemon_get_game_config(GAME_UNKNOWN) == NULL,
                "GAME_UNKNOWN must have no configuration at all");

    free(ewram);
    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_unknown_game_still_fails_closed" ANSI_RESET "\n");
}

static void test_unbound_cfru_fixed_substructures(void) {
    printf("Running test_unbound_cfru_fixed_substructures...\n");

    RawGbaPokemon raw;
    memset(&raw, 0, sizeof(raw));
    // pid % 24 == 1 would scramble into "GAME" order under vanilla rules; a
    // correct Unbound read must ignore that and use the fixed GAEM order.
    raw.pid = 0x00000001;
    raw.otid = 0x00000002;

    SubstructGrowth g = {.species = 384, .held_item = 15, .experience = 999999, .friendship = 150};
    SubstructAttacks a = {.moves = {94, 85, 0, 0}, .pp = {20, 15, 0, 0}};
    SubstructEVs e = {.hp_ev = 1, .attack_ev = 2, .defense_ev = 3, .speed_ev = 4, .sp_attack_ev = 5, .sp_defense_ev = 6};
    SubstructMisc m = {.iv_egg_ability = (31U << 0) | (20U << 5) | (15U << 10) | (10U << 15) | (5U << 20)};

    pack_cfru_fixed((uint8_t*)&g, (uint8_t*)&a, (uint8_t*)&e, (uint8_t*)&m,
                    raw.raw_substructures, &raw.checksum);

    raw.level = 50;
    raw.max_hp = 150;
    raw.current_hp = 120;
    raw.attack = 100; raw.defense = 90; raw.speed = 80; raw.sp_attack = 70; raw.sp_defense = 60;

    ParsedPokemon parsed;
    bool ok = pokemon_parse_single((const uint8_t*)&raw, true, &parsed);

    TEST_ASSERT(ok, "Unbound fixed-order mon should parse successfully");
    TEST_ASSERT(parsed.is_valid, "Unbound mon should be valid");
    TEST_ASSERT(parsed.species == 384, "Species must read from fixed Growth substructure (384)");
    TEST_ASSERT(parsed.moves[0] == 94 && parsed.moves[1] == 85, "Moves must read from fixed Attacks substructure");
    TEST_ASSERT(parsed.hp_ev == 1 && parsed.sp_defense_ev == 6, "EVs must read from fixed EVs substructure");
    TEST_ASSERT(parsed.hp_iv == 31 && parsed.defense_iv == 15 && parsed.sp_attack_iv == 5, "IVs must read from fixed Misc substructure");
    TEST_ASSERT(parsed.level == 50 && parsed.current_hp == 120 && parsed.max_hp == 150, "Battle stats must read");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_unbound_cfru_fixed_substructures" ANSI_RESET "\n");
}

static void test_battle_presence_and_unknown_ui_state(void) {
    printf("Running test_battle_presence_and_unknown_ui_state...\n");
    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_FIRERED);
    const size_t ewram_size = 0x40000;
    uint8_t* ewram = calloc(1, ewram_size);
    TEST_ASSERT(cfg != NULL && ewram != NULL, "FireRed config and EWRAM allocation required");

    TEST_ASSERT(pokemon_read_battle_presence(ewram, ewram_size, cfg) == 0,
                "No valid battle mon must be NOT_OBSERVED");
    TEST_ASSERT(pokemon_read_battle_ui_state(ewram, ewram_size, cfg) == 0,
                "No valid battle mon must have unavailable UI state");

    uint8_t* battle_mon = ewram + cfg->battle_mons_offset;
    battle_mon[0] = 25; // plausible Pikachu species
    battle_mon[1] = 0;
    battle_mon[cfg->battle_mons_hp_offset] = 20;
    TEST_ASSERT(pokemon_read_battle_presence(ewram, ewram_size, cfg) == 1,
                "Live battle mon must be OBSERVED");
    TEST_ASSERT(pokemon_read_battle_ui_state(ewram, ewram_size, cfg) == 5,
                "Live battle mon must not infer a menu state");

    battle_mon[cfg->battle_mons_hp_offset] = 0;
    TEST_ASSERT(pokemon_read_battle_presence(ewram, ewram_size, cfg) == 1,
                "Fainted battle mon remains observed during transitions");
    TEST_ASSERT(pokemon_read_battle_ui_state(ewram, ewram_size, cfg) == 5,
                "Fainted battle mon must not infer PARTY_MENU");

    TEST_ASSERT(pokemon_read_battle_presence(ewram, 1, cfg) == 2,
                "Malformed memory range must be unavailable/unknown");
    free(ewram);
    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_battle_presence_and_unknown_ui_state" ANSI_RESET "\n");
}

static void test_unknown_game_fails_closed(void) {
    printf("Running test_unknown_game_fails_closed...\n");

    // 1. GAME_UNKNOWN must return NULL and never fall back to FireRed
    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_UNKNOWN);
    TEST_ASSERT(cfg == NULL, "GAME_UNKNOWN must return NULL GameMemoryConfig");

    const GameMemoryConfig* invalid_cfg = pokemon_get_game_config((GbaGameId)9999);
    TEST_ASSERT(invalid_cfg == NULL, "Out-of-range game ID must return NULL GameMemoryConfig");

    const size_t ewram_size = 0x40000;
    uint8_t* ewram = calloc(1, ewram_size);
    TEST_ASSERT(ewram != NULL, "EWRAM allocation required");

    PartySnapshot snapshot;
    memset(&snapshot, 0xFF, sizeof(snapshot));
    uint8_t party_count = pokemon_read_player_party(ewram, ewram_size, NULL, &snapshot);
    TEST_ASSERT(party_count == 0, "NULL config player party must return 0");
    TEST_ASSERT(snapshot.active_battler_slot == -1, "NULL config active battler slot must be -1");

    memset(&snapshot, 0xFF, sizeof(snapshot));
    uint8_t enemy_count = pokemon_read_enemy_party(ewram, ewram_size, NULL, &snapshot);
    TEST_ASSERT(enemy_count == 0, "NULL config enemy party must return 0");
    TEST_ASSERT(snapshot.active_battler_slot == -1, "NULL config enemy battler slot must be -1");

    PlayerLocationRaw loc;
    memset(&loc, 0xFF, sizeof(loc));
    bool loc_ok = pokemon_read_player_location(ewram, ewram_size, NULL, &loc);
    TEST_ASSERT(!loc_ok, "NULL config player location must return false");
    TEST_ASSERT(!loc.is_valid, "NULL config location must not be valid");

    int8_t stages[7] = {0};
    bool stages_ok = pokemon_read_battle_stat_stages(ewram, ewram_size, NULL, 0, stages);
    TEST_ASSERT(!stages_ok, "NULL config battle stat stages must return false");

    uint8_t ui_state = pokemon_read_battle_ui_state(ewram, ewram_size, NULL);
    TEST_ASSERT(ui_state == 0, "NULL config battle UI state must return 0 (UNKNOWN)");

    uint8_t presence = pokemon_read_battle_presence(ewram, ewram_size, NULL);
    TEST_ASSERT(presence == 2, "NULL config battle presence must return 2 (UNKNOWN)");

    // 2. Explicit GAME_UNKNOWN config must also fail closed
    GameMemoryConfig sentinel_cfg = {0};
    sentinel_cfg.game_id = GAME_UNKNOWN;
    sentinel_cfg.player_party_offset = 0x02000000;

    TEST_ASSERT(pokemon_read_player_party(ewram, ewram_size, &sentinel_cfg, &snapshot) == 0,
                "GAME_UNKNOWN sentinel must fail player party");
    TEST_ASSERT(pokemon_read_enemy_party(ewram, ewram_size, &sentinel_cfg, &snapshot) == 0,
                "GAME_UNKNOWN sentinel must fail enemy party");
    TEST_ASSERT(!pokemon_read_player_location(ewram, ewram_size, &sentinel_cfg, &loc),
                "GAME_UNKNOWN sentinel must fail location");
    TEST_ASSERT(!pokemon_read_battle_stat_stages(ewram, ewram_size, &sentinel_cfg, 0, stages),
                "GAME_UNKNOWN sentinel must fail stat stages");
    TEST_ASSERT(pokemon_read_battle_ui_state(ewram, ewram_size, &sentinel_cfg) == 0,
                "GAME_UNKNOWN sentinel must fail battle UI state");
    TEST_ASSERT(pokemon_read_battle_presence(ewram, ewram_size, &sentinel_cfg) == 2,
                "GAME_UNKNOWN sentinel must return 2 for battle presence");

    free(ewram);
    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_unknown_game_fails_closed" ANSI_RESET "\n");
}

/*
 * H&S 2.0.5 trainer-battle lifecycle and multi-party switch/faint invariants.
 *
 * These synthetic fixtures verify the production reader contracts that future
 * official-ROM runtime scenarios are expected to exercise. They do not constitute
 * live runtime verification of trainer battles, multi-party replacement, player
 * switching, or player faint/replacement.
 *
 * BATTLE_TYPE_TRAINER is bit 3 of gBattleTypeFlags (battle.h, sourced from the `make hns`
 * symbol table, identical to vanilla pokeemerald). No other flag-bit matters for the
 * single-trainer classification path.
 */

#define HNS_BATTLE_TYPE_TRAINER  (1u << 3)

/**
 * A trainer battle must classify as BATTLE_KIND_TRAINER_SINGLE, not WILD_SINGLE.
 *
 * BATTLE_TYPE_TRAINER bit determines BattleKind.
 */
static void test_hns_trainer_battle_classification(void) {
    printf("Running test_hns_trainer_battle_classification...\n");

    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    static FakeGba gba;
    HnsBattleFixture fx;
    BattleStateRaw state;
    ActiveEnemyInfo info;
    PartySnapshot snap;

    /* --- trainer single: BATTLE_TYPE_TRAINER bit set ------------------------------------ */
    pokemon_reader_reset();
    hns_battle_fixture_init(&fx, &gba, cfg);
    hns_battle_fill_player_party(&fx, 2);
    hns_battle_fill_enemy_party(&fx, 2);
    hns_battle_set_in_battle(&fx, true);
    hns_battle_set_counters(&fx, 2, HNS_BATTLE_TYPE_TRAINER, 0);
    gba.ewram[cfg->absent_battler_flags_offset] = 0;
    hns_battle_set_battler(&fx, 0, 0, 0);  /* player left -> player slot 0 */
    hns_battle_set_battler(&fx, 1, 1, 0);  /* opponent left -> enemy slot 0 */
    hns_battle_set_mon(&fx, 0, 155, 50);
    hns_battle_set_mon(&fx, 1, 16, 35);

    BattleLifecycleState lc = pokemon_read_battle_lifecycle(
        fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram), cfg, &state);
    TEST_ASSERT(lc == BATTLE_LIFECYCLE_ACTIVE,
                "a fully described trainer battle must be ACTIVE");
    TEST_ASSERT(state.kind == BATTLE_KIND_TRAINER_SINGLE,
                "BATTLE_TYPE_TRAINER bit must classify as TRAINER_SINGLE");
    TEST_ASSERT((state.battle_type_flags & HNS_BATTLE_TYPE_TRAINER) != 0,
                "gBattleTypeFlags must have the TRAINER bit set");
    TEST_ASSERT(state.battlers_count == 2,
                "gBattlersCount must be 2 for a single trainer battle");

    ActiveEnemyState es = pokemon_resolve_active_enemy(
        fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram), cfg, &snap, &info);
    TEST_ASSERT(es == ACTIVE_ENEMY_SLOT,
                "a trainer battle must resolve an active enemy slot");
    TEST_ASSERT(info.party_slot == 0,
                "the initial trainer opponent must be at enemy slot 0");
    TEST_ASSERT(info.battler_index == 1,
                "the opponent battler index must be 1 in a standard single battle");
    TEST_ASSERT(snap.count == 2,
                "gEnemyPartyCount == 2 must bound the snapshot");

    /* --- wild single with same fixture but TRAINER bit cleared: must revert ------------ */
    hns_battle_set_counters(&fx, 2, 0u /* no TRAINER */, 0);
    lc = pokemon_read_battle_lifecycle(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                       cfg, &state);
    TEST_ASSERT(lc == BATTLE_LIFECYCLE_ACTIVE,
                "clearing TRAINER bit keeps the battle ACTIVE");
    TEST_ASSERT(state.kind == BATTLE_KIND_WILD_SINGLE,
                "clearing TRAINER bit must reclassify to WILD_SINGLE");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_trainer_battle_classification" ANSI_RESET "\n");
}

/**
 * Opponent slot resolves exclusively from gBattlerPositions -> gBattlerPartyIndexes.
 *
 * The resolved chain must hold for a trainer battle with 2 enemy party
 * members. No species match, no HP match, no slot-0 fallback.
 */
static void test_hns_trainer_opponent_slot_resolves_from_battler_index(void) {
    printf("Running test_hns_trainer_opponent_slot_resolves_from_battler_index...\n");

    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    static FakeGba gba;
    HnsBattleFixture fx;
    PartySnapshot snap;
    ActiveEnemyInfo info;

    /* trainer with 2-member enemy party; first active is slot 0 */
    pokemon_reader_reset();
    hns_battle_fixture_init(&fx, &gba, cfg);
    hns_battle_fill_player_party(&fx, 2);
    hns_battle_fill_enemy_party(&fx, 2);
    hns_battle_set_in_battle(&fx, true);
    hns_battle_set_counters(&fx, 2, HNS_BATTLE_TYPE_TRAINER, 0);
    gba.ewram[cfg->absent_battler_flags_offset] = 0;
    hns_battle_set_battler(&fx, 0, 0, 0);  /* player left -> player slot 0 */
    hns_battle_set_battler(&fx, 1, 1, 0);  /* opponent left -> enemy slot 0 */
    hns_battle_set_mon(&fx, 0, 155, 50);
    hns_battle_set_mon(&fx, 1, 16, 35);

    ActiveEnemyState es = pokemon_resolve_active_enemy(
        fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram), cfg, &snap, &info);
    TEST_ASSERT(es == ACTIVE_ENEMY_SLOT, "trainer battle must resolve a slot");
    TEST_ASSERT(info.party_slot == 0, "initial trainer opponent is enemy slot 0");
    TEST_ASSERT(info.battler_index == 1, "battler 1 is the opponent in a standard single");
    TEST_ASSERT(!info.fainted, "the opponent has HP and must not be reported fainted");
    TEST_ASSERT(snap.count == 2, "enemy party must have exactly 2 members");

    /* gBattlerPartyIndexes[1] is the only source; verify by pointing it at slot 1 */
    hns_battle_set_battler(&fx, 1, 1, 1);  /* move to enemy slot 1 without species change */
    hns_battle_set_mon(&fx, 1, 19, 28);    /* new species + HP for slot 1 */

    es = pokemon_resolve_active_enemy(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                      cfg, &snap, &info);
    TEST_ASSERT(es == ACTIVE_ENEMY_SLOT, "slot-1 trainer opponent must still resolve");
    TEST_ASSERT(info.party_slot == 1, "slot must follow gBattlerPartyIndexes, now slot 1");
    TEST_ASSERT(snap.members[1].current_hp == 28,
                "HP must sync from gBattleMons[battler].hp, not from party parsing");

    /* Confirm no slot-0 fallback: if we point the battler at slot 1 but it has hp==0,
       the slot must still be reported as 1 with fainted=true, not reverted to slot 0. */
    hns_battle_set_mon(&fx, 1, 19, 0);  /* hp=0, fainted */
    es = pokemon_resolve_active_enemy(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                      cfg, &snap, &info);
    TEST_ASSERT(es == ACTIVE_ENEMY_SLOT, "fainted trainer opponent still has a slot");
    TEST_ASSERT(info.party_slot == 1,
                "no slot-0 fallback when the opponent faints at slot 1");
    TEST_ASSERT(info.fainted, "the faint must be reported");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_trainer_opponent_slot_resolves_from_battler_index" ANSI_RESET "\n");
}

/**
 * Opponent faint -> replacement: slot must follow the new gBattlerPartyIndexes, not retain old.
 *
 * Sequence proven:
 * 1. Before faint: ACTIVE_ENEMY_SLOT, party_slot=0, fainted=false.
 * 2. HP -> 0: authoritative opponent slot remains available (ACTIVE_ENEMY_SLOT, party_slot=0, fainted=true).
 * 3. Replacement window: engine marks battler absent in gAbsentBattlerFlags -> ACTIVE_ENEMY_NONE_ACTIVE,
 *    party_slot=-1, battler_index=-1.
 * 4. Replacement committed: engine clears absent and updates gBattlerPartyIndexes -> ACTIVE_ENEMY_SLOT,
 *    party_slot=1, fainted=false without retaining old slot 0.
 */
static void test_hns_trainer_multi_party_faint_transition(void) {
    printf("Running test_hns_trainer_multi_party_faint_transition...\n");

    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    static FakeGba gba;
    HnsBattleFixture fx;
    PartySnapshot snap;
    ActiveEnemyInfo info;

    pokemon_reader_reset();
    hns_battle_fixture_init(&fx, &gba, cfg);
    hns_battle_fill_player_party(&fx, 2);
    hns_battle_fill_enemy_party(&fx, 2);
    hns_battle_set_in_battle(&fx, true);
    hns_battle_set_counters(&fx, 2, HNS_BATTLE_TYPE_TRAINER, 0);
    gba.ewram[cfg->absent_battler_flags_offset] = 0;
    hns_battle_set_battler(&fx, 0, 0, 0);
    hns_battle_set_battler(&fx, 1, 1, 0);  /* enemy slot 0 active */
    hns_battle_set_mon(&fx, 0, 155, 50);
    hns_battle_set_mon(&fx, 1, 16, 35);

    /* === Before faint: slot 0 active, HP > 0 === */
    ActiveEnemyState es = pokemon_resolve_active_enemy(
        fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram), cfg, &snap, &info);
    TEST_ASSERT(es == ACTIVE_ENEMY_SLOT && info.party_slot == 0,
                "before faint: must resolve enemy slot 0");
    TEST_ASSERT(!info.fainted, "before faint: opponent must not be reported fainted");

    /* === Faint: hp -> 0, index unchanged (engine hasn't sent out replacement yet) === */
    hns_battle_set_mon(&fx, 1, 16, 0);  /* same species, hp=0 */
    es = pokemon_resolve_active_enemy(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                      cfg, &snap, &info);
    TEST_ASSERT(es == ACTIVE_ENEMY_SLOT,
                "fainted opponent still has an authoritative slot");
    TEST_ASSERT(info.party_slot == 0,
                "fainted slot 0 must not silently become slot 1");
    TEST_ASSERT(info.fainted,
                "hp==0 on the active opponent must set fainted=true");

    /* === Forced-switch window: engine marks battler absent while choosing replacement === */
    gba.ewram[cfg->absent_battler_flags_offset] = (uint8_t)(1u << 1); /* battler 1 absent */
    es = pokemon_resolve_active_enemy(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                      cfg, &snap, &info);
    TEST_ASSERT(es == ACTIVE_ENEMY_NONE_ACTIVE,
                "absent opponent during forced switch must be NONE_ACTIVE");
    TEST_ASSERT(info.party_slot == -1,
                "no slot may survive while the replacement is being chosen");
    TEST_ASSERT(info.battler_index == -1,
                "no battler index may survive the replacement window");

    /* === Replacement complete: engine clears absent, updates gBattlerPartyIndexes === */
    gba.ewram[cfg->absent_battler_flags_offset] = 0;
    hns_battle_set_battler(&fx, 1, 1, 1);  /* new: enemy slot 1 */
    hns_battle_set_mon(&fx, 1, 19, 42);    /* Rattata hp=42 */
    es = pokemon_resolve_active_enemy(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                      cfg, &snap, &info);
    TEST_ASSERT(es == ACTIVE_ENEMY_SLOT,
                "replacement complete must resolve a new slot");
    TEST_ASSERT(info.party_slot == 1,
                "replacement must resolve to enemy slot 1, not retained slot 0");
    TEST_ASSERT(info.battler_index == 1,
                "battler index must still be 1 (the opponent battler)");
    TEST_ASSERT(!info.fainted,
                "the replacement has HP > 0 and must not be reported fainted");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_trainer_multi_party_faint_transition" ANSI_RESET "\n");
}

/**
 * Player voluntary switch: active player slot follows gBattlerPartyIndexes.
 *
 * Before the switch the player slot is authoritative; after the engine
 * updates gBattlerPartyIndexes the new slot must be resolved immediately.
 */
static void test_hns_player_switch_slot_follows_battler_indexes(void) {
    printf("Running test_hns_player_switch_slot_follows_battler_indexes...\n");

    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    static FakeGba gba;
    HnsBattleFixture fx;
    PartySnapshot snap;

    pokemon_reader_reset();
    hns_battle_fixture_init(&fx, &gba, cfg);
    hns_battle_fill_player_party(&fx, 2);   /* 2 player party members */
    hns_battle_fill_enemy_party(&fx, 2);
    hns_battle_set_in_battle(&fx, true);
    hns_battle_set_counters(&fx, 2, HNS_BATTLE_TYPE_TRAINER, 0);
    gba.ewram[cfg->absent_battler_flags_offset] = 0;
    hns_battle_set_battler(&fx, 0, 0, 0);  /* player left -> player slot 0 */
    hns_battle_set_battler(&fx, 1, 1, 0);  /* opponent left -> enemy slot 0 */
    hns_battle_set_mon(&fx, 0, 155, 50);
    hns_battle_set_mon(&fx, 1, 16, 35);

    /* === Before switch: player active slot == 0 === */
    uint8_t pcount = pokemon_read_player_party_gba(fake_gba_read, &gba.table,
                                                   gba.ewram, sizeof(gba.ewram),
                                                   cfg, &snap);
    TEST_ASSERT(pcount == 2, "player party must have 2 members");
    TEST_ASSERT(snap.active_battler_known, "player active battler must be known before switch");
    TEST_ASSERT(snap.active_battler_slot == 0,
                "before switch: active player slot must be 0");
    TEST_ASSERT(snap.active_battler_index == 0,
                "before switch: active player battler index must be 0");

    /* === Switch window: engine marks player battler absent while party menu is open === */
    gba.ewram[cfg->absent_battler_flags_offset] = (uint8_t)(1u << 0); /* battler 0 absent */
    pokemon_read_player_party_gba(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                  cfg, &snap);
    TEST_ASSERT(!snap.active_battler_known,
                "during switch window player active slot must be unknown");
    TEST_ASSERT(snap.active_battler_slot == -1,
                "switch window must not retain old slot 0");

    /* === Switch complete: engine updates gBattlerPartyIndexes[0] -> 1 === */
    gba.ewram[cfg->absent_battler_flags_offset] = 0;
    hns_battle_set_battler(&fx, 0, 0, 1);  /* player battler 0 now -> party slot 1 */
    hns_battle_set_mon(&fx, 0, 158, 44);   /* Croconaw hp=44 */
    pokemon_read_player_party_gba(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                  cfg, &snap);
    TEST_ASSERT(snap.active_battler_known,
                "after switch player active slot must be known again");
    TEST_ASSERT(snap.active_battler_slot == 1,
                "after switch active player slot must be 1, not retained 0");
    TEST_ASSERT(snap.active_battler_index == 0,
                "after switch battler index is still 0 (same battler position)");
    TEST_ASSERT(snap.members[1].current_hp == 44,
                "HP from gBattleMons must sync to the new party slot");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_player_switch_slot_follows_battler_indexes" ANSI_RESET "\n");
}

/**
 * Player faint -> forced replacement: slot must be withheld until the new battler is committed.
 *
 * hp==0 on the active player makes the slot unknown; after the engine
 * commits the replacement the new slot is authoritative.
 */
static void test_hns_player_faint_forces_unknown_until_replacement(void) {
    printf("Running test_hns_player_faint_forces_unknown_until_replacement...\n");

    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    static FakeGba gba;
    HnsBattleFixture fx;
    PartySnapshot snap;

    pokemon_reader_reset();
    hns_battle_fixture_init(&fx, &gba, cfg);
    hns_battle_fill_player_party(&fx, 2);
    hns_battle_fill_enemy_party(&fx, 2);
    hns_battle_set_in_battle(&fx, true);
    hns_battle_set_counters(&fx, 2, HNS_BATTLE_TYPE_TRAINER, 0);
    gba.ewram[cfg->absent_battler_flags_offset] = 0;
    hns_battle_set_battler(&fx, 0, 0, 0);
    hns_battle_set_battler(&fx, 1, 1, 0);
    hns_battle_set_mon(&fx, 0, 155, 50);
    hns_battle_set_mon(&fx, 1, 16, 35);

    /* === Before faint: player slot 0 known === */
    pokemon_read_player_party_gba(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                  cfg, &snap);
    TEST_ASSERT(snap.active_battler_known && snap.active_battler_slot == 0,
                "before faint: player active slot must be 0");

    /* === Player faints: hp -> 0. Engine has not yet committed a replacement. === */
    hns_battle_set_mon(&fx, 0, 155, 0);  /* player hp -> 0 */
    pokemon_read_player_party_gba(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                  cfg, &snap);
    TEST_ASSERT(!snap.active_battler_known,
                "a fainted player battler must leave the active slot unknown");
    TEST_ASSERT(snap.active_battler_slot == -1,
                "fainted player slot must not be retained");

    /* === Forced-replacement window: engine marks battler absent === */
    gba.ewram[cfg->absent_battler_flags_offset] = (uint8_t)(1u << 0); /* battler 0 absent */
    hns_battle_set_mon(&fx, 0, 155, 0);  /* hp still 0 */
    pokemon_read_player_party_gba(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                  cfg, &snap);
    TEST_ASSERT(!snap.active_battler_known,
                "during forced-replacement window player slot must remain unknown");
    TEST_ASSERT(snap.active_battler_slot == -1,
                "no cached previous slot may survive");

    /* === Replacement committed: battler 0 now at party slot 1, hp restored === */
    gba.ewram[cfg->absent_battler_flags_offset] = 0;
    hns_battle_set_battler(&fx, 0, 0, 1);  /* player battler 0 -> party slot 1 */
    hns_battle_set_mon(&fx, 0, 158, 38);   /* Croconaw hp=38 */
    pokemon_read_player_party_gba(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                  cfg, &snap);
    TEST_ASSERT(snap.active_battler_known,
                "after forced replacement player slot must be known");
    TEST_ASSERT(snap.active_battler_slot == 1,
                "forced replacement must set active slot to 1, not retained 0");
    TEST_ASSERT(snap.members[1].current_hp == 38,
                "HP must sync to the replacement battler's live HP");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_player_faint_forces_unknown_until_replacement" ANSI_RESET "\n");
}

/**
 * Stale enemy slot cannot survive a completed replacement.
 *
 * Once the engine commits a new gBattlerPartyIndexes value the old slot
 * must not appear anywhere in the reader's output, even if gBattleMons still holds the old
 * opponent's species word.
 */
static void test_hns_stale_enemy_slot_cannot_survive_replacement(void) {
    printf("Running test_hns_stale_enemy_slot_cannot_survive_replacement...\n");

    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    static FakeGba gba;
    HnsBattleFixture fx;
    PartySnapshot snap;
    ActiveEnemyInfo info;

    pokemon_reader_reset();
    hns_battle_fixture_init(&fx, &gba, cfg);
    hns_battle_fill_player_party(&fx, 2);
    hns_battle_fill_enemy_party(&fx, 2);
    hns_battle_set_in_battle(&fx, true);
    hns_battle_set_counters(&fx, 2, HNS_BATTLE_TYPE_TRAINER, 0);
    gba.ewram[cfg->absent_battler_flags_offset] = 0;
    hns_battle_set_battler(&fx, 0, 0, 0);
    hns_battle_set_battler(&fx, 1, 1, 0);  /* enemy slot 0 */
    hns_battle_set_mon(&fx, 0, 155, 50);
    hns_battle_set_mon(&fx, 1, 16, 35);    /* Pidgey hp=35 */

    /* Confirm initial: slot 0 */
    ActiveEnemyState es = pokemon_resolve_active_enemy(
        fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram), cfg, &snap, &info);
    TEST_ASSERT(es == ACTIVE_ENEMY_SLOT && info.party_slot == 0,
                "precondition: initial enemy must be slot 0");

    /* Faint slot 0 */
    hns_battle_set_mon(&fx, 1, 16, 0);

    /* Replacement committed to slot 1; gBattleMons[1] species updated to Rattata. */
    hns_battle_set_battler(&fx, 1, 1, 1);  /* gBattlerPartyIndexes[1] -> 1 */
    hns_battle_set_mon(&fx, 1, 19, 42);    /* Rattata hp=42 at gBattleMons[1] */
    gba.ewram[cfg->absent_battler_flags_offset] = 0;

    es = pokemon_resolve_active_enemy(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                      cfg, &snap, &info);
    TEST_ASSERT(es == ACTIVE_ENEMY_SLOT,
                "replacement complete: must resolve a slot");
    TEST_ASSERT(info.party_slot == 1,
                "replacement: slot must be 1 (new gBattlerPartyIndexes), not stale 0");
    TEST_ASSERT(!info.fainted,
                "replacement: new battler has hp > 0 and must not be fainted");
    TEST_ASSERT(snap.active_battler_slot == 1,
                "snapshot active_battler_slot must be 1, not stale 0");
    TEST_ASSERT(snap.active_battler_known,
                "snapshot active_battler_known must be true after replacement");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_stale_enemy_slot_cannot_survive_replacement" ANSI_RESET "\n");
}

/**
 * Stale player slot cannot survive a completed forced replacement.
 *
 * After the player's active Pokémon faints and the engine commits the
 * replacement battler, the old slot must not appear anywhere in the reader's output.
 */
static void test_hns_stale_player_slot_cannot_survive_faint(void) {
    printf("Running test_hns_stale_player_slot_cannot_survive_faint...\n");

    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    static FakeGba gba;
    HnsBattleFixture fx;
    PartySnapshot snap;

    pokemon_reader_reset();
    hns_battle_fixture_init(&fx, &gba, cfg);
    hns_battle_fill_player_party(&fx, 2);
    hns_battle_fill_enemy_party(&fx, 2);
    hns_battle_set_in_battle(&fx, true);
    hns_battle_set_counters(&fx, 2, HNS_BATTLE_TYPE_TRAINER, 0);
    gba.ewram[cfg->absent_battler_flags_offset] = 0;
    hns_battle_set_battler(&fx, 0, 0, 0);  /* player battler 0 -> party slot 0 */
    hns_battle_set_battler(&fx, 1, 1, 0);
    hns_battle_set_mon(&fx, 0, 155, 50);
    hns_battle_set_mon(&fx, 1, 16, 35);

    /* Verify initial player slot */
    pokemon_read_player_party_gba(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                  cfg, &snap);
    TEST_ASSERT(snap.active_battler_known && snap.active_battler_slot == 0,
                "precondition: initial player must be slot 0");

    /* Player faints; gBattleMons[0].species remains 155 in EWRAM */
    hns_battle_set_mon(&fx, 0, 155, 0);  /* hp -> 0, species still 155 */

    /* Forced replacement committed: gBattlerPartyIndexes[0] -> 1, hp restored */
    hns_battle_set_battler(&fx, 0, 0, 1);  /* party slot -> 1 */
    hns_battle_set_mon(&fx, 0, 158, 38);   /* Croconaw hp=38 */

    pokemon_read_player_party_gba(fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram),
                                  cfg, &snap);
    TEST_ASSERT(snap.active_battler_known,
                "after forced replacement player slot must be known");
    TEST_ASSERT(snap.active_battler_slot == 1,
                "stale slot 0 must not survive; active slot must be 1");
    TEST_ASSERT(snap.members[1].current_hp == 38,
                "HP must come from the new battler's gBattleMons hp field");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_stale_player_slot_cannot_survive_faint" ANSI_RESET "\n");
}

// ===========================================================================
// H&S 2.0.5 runtime challenge settings (issue #9).
//
// The semantic expectations below are hand-pinned in the SAVEBLOCK3 frame —
// byte/bit positions read out of the official release ROM's own code and out of
// the pinned source text — deliberately independent from the production
// decoder's ChallengeSettings-relative generated table. A reader that swapped
// two settings, or a generated table that drifted, must fail these pins.
// ===========================================================================

// GetBattleMoveCategory (ROM 0x080CF604): ldrb [gSaveBlock3Ptr, #0x11]; bit 1.
#define PIN_OPTION_STYLE_BYTE 17
#define PIN_OPTION_STYLE_BIT 1
// GetBaseStatEqualizerValue (ROM 0x080F0230): ldrb [gSaveBlock3Ptr, #0x18];
// `lsls #0x1b; lsrs #0x1e` extracts bits 3-4.
#define PIN_BST_EQ_BYTE 24
#define PIN_BST_EQ_BIT 3
#define PIN_BST_EQ_WIDTH 2
// GetCurrentLevelCap (ROM 0x080EE674): ldrb [gSaveBlock3Ptr, #0x18]; bits 5-6.
#define PIN_LEVEL_CAP_BYTE 24
#define PIN_LEVEL_CAP_BIT 5
#define PIN_LEVEL_CAP_WIDTH 2
// SetDefaultChallengeSettings (devkit .o): halfword RMW at +0x2c with mask 0x3fb
// sets bit 6 of byte 0x2c (=44) — tx_Mode_Fairy_Types.
#define PIN_FAIRY_BYTE 44
#define PIN_FAIRY_BIT 6
// Same RMW mask sets bit 7 of byte 0x2c — tx_Mode_Sturdy; byte 0x2d (45) bit 1
// — tx_Mode_Legendary_Abilities.
#define PIN_STURDY_BYTE 44
#define PIN_STURDY_BIT 7
#define PIN_LEGENDARY_ABILITIES_BYTE 45
#define PIN_LEGENDARY_ABILITIES_BIT 1
// RandomizerFeatureEnabled (ROM 0x081F024C): ldrb [gSaveBlock3Ptr, #0x14];
// `lsls #0x1a; lsrs #0x1f` extracts bit 5 — tx_Random_Type; bit 6 is
// tx_Random_TypeEffectiveness, bit 7 tx_Random_Abilities.
#define PIN_RANDOM_TYPE_BYTE 20
#define PIN_RANDOM_TYPE_BIT 5
#define PIN_RANDOM_TYPE_EFFECTIVENESS_BYTE 20
#define PIN_RANDOM_TYPE_EFFECTIVENESS_BIT 6
#define PIN_RANDOM_ABILITIES_BYTE 20
#define PIN_RANDOM_ABILITIES_BIT 7
// NoEVs: challenge-menu RMW (byte 0x19 = 25) bit 4 — pinned from the challenge
// menu write set against the pinned source declaration order.
#define PIN_NO_EVS_BYTE 25
#define PIN_NO_EVS_BIT 4
#define PIN_EXP_MULT_BYTE 25
#define PIN_EXP_MULT_BIT 0
#define PIN_EXP_MULT_WIDTH 2
#define PIN_MIRROR_BYTE 25
#define PIN_MIRROR_BIT 2
#define PIN_MIRROR_THIEF_BYTE 25
#define PIN_MIRROR_THIEF_BIT 3
#define PIN_SCALING_IVS_BYTE 26
#define PIN_SCALING_IVS_BIT 3
#define PIN_SCALING_IVS_WIDTH 2
#define PIN_SCALING_EVS_BYTE 26
#define PIN_SCALING_EVS_BIT 5
#define PIN_SCALING_EVS_WIDTH 2
#define PIN_MAX_PARTY_IVS_BYTE 27
#define PIN_MAX_PARTY_IVS_BIT 0
#define PIN_MAX_PARTY_IVS_WIDTH 2

#define HNS_SB3_BASE_ABS 0x02009218u // official release ROM's gSaveblock3 (runtime-observed pointer value; the from-source build sits 4 bytes higher)
#define HNS_SB3_PTR_ABS 0x03000178u

/** Point gSaveBlock3Ptr at the compiled gSaveblock3 and zero the struct. */
static void hns_cs_fixture_init(FakeGba* gba) {
    fake_gba_init(gba, true, true);
    write32_le_t(gba->iwram + (HNS_SB3_PTR_ABS - 0x03000000u), HNS_SB3_BASE_ABS);
    memset(gba->ewram + (HNS_SB3_BASE_ABS - 0x02000000u), 0, 64);
}

/** Set one bit of SaveBlock3 (SB3-relative byte index). */
static void hns_cs_set_bit(FakeGba* gba, uint32_t byte, uint8_t bit, unsigned value) {
    uint8_t* p = gba->ewram + (HNS_SB3_BASE_ABS - 0x02000000u) + byte;
    if (value) *p |= (uint8_t)(1u << bit);
    else       *p &= (uint8_t)~(1u << bit);
}

/** Write a 2-bit field of SaveBlock3 (SB3-relative byte index). */
static void hns_cs_set_field2(FakeGba* gba, uint32_t byte, uint8_t bit, unsigned value) {
    uint8_t* p = gba->ewram + (HNS_SB3_BASE_ABS - 0x02000000u) + byte;
    *p = (uint8_t)((*p & ~(0x3u << bit)) | ((value & 0x3u) << bit));
}

/** A zeroed snapshot must be all-unobserved: the reader's own contract. */
static void expect_unavailable(const ChallengeSettingsSnapshot* snap, const char* what) {
    TEST_ASSERT(snap->status == CHALLENGE_SETTINGS_UNAVAILABLE, what);
    TEST_ASSERT(!snap->option_style.observed && !snap->tx_mode_fairy_types.observed &&
                !snap->tx_random_type.observed && !snap->tx_random_type_effectiveness.observed &&
                !snap->tx_random_abilities.observed && !snap->tx_random_moves.observed &&
                !snap->tx_challenges_no_evs.observed &&
                !snap->tx_challenges_base_stat_equalizer.observed &&
                !snap->tx_challenges_mirror.observed && !snap->tx_challenges_mirror_thief.observed &&
                !snap->tx_challenges_trainer_scaling_ivs.observed &&
                !snap->tx_challenges_trainer_scaling_evs.observed &&
                !snap->tx_challenges_max_party_ivs.observed && !snap->tx_mode_sturdy.observed &&
                !snap->tx_challenges_level_cap.observed &&
                !snap->tx_challenges_exp_multiplier.observed &&
                !snap->tx_mode_legendary_abilities.observed,
                "an unavailable read must leave every field unobserved");
}

/** All-zero struct: every field observed, every value 0 — observed, NOT unknown. */
static void test_hns_challenge_settings_zero_is_observed_not_unknown(void) {
    printf("Running test_hns_challenge_settings_zero_is_observed_not_unknown...\n");
    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    TEST_ASSERT(cfg != NULL, "H&S config must exist");

    static FakeGba gba;
    hns_cs_fixture_init(&gba);

    ChallengeSettingsSnapshot snap;
    TEST_ASSERT(pokemon_read_challenge_settings_gba(fake_gba_read, &gba.table, NULL, 0, cfg, &snap),
                "an all-zero trusted layout must read");
    TEST_ASSERT(snap.status == CHALLENGE_SETTINGS_OBSERVED,
                "all-zero must be OBSERVED, not unavailable");
    TEST_ASSERT(snap.option_style.observed && snap.option_style.raw == 0 && !snap.option_style.invalid,
                "observed optionStyle == 0 is a legitimate observed value, not unknown");
    TEST_ASSERT(snap.tx_mode_fairy_types.observed && snap.tx_mode_fairy_types.raw == 0,
                "observed Fairy off is a legitimate observed value, not unknown");
    TEST_ASSERT(snap.tx_challenges_level_cap.observed && snap.tx_challenges_level_cap.raw == 0,
                "observed LevelCap OFF is a legitimate observed value, not unknown");
    TEST_ASSERT(snap.tx_mode_legendary_abilities.observed && snap.tx_mode_legendary_abilities.raw == 0,
                "observed Legendary-abilities off is a legitimate observed value, not unknown");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_challenge_settings_zero_is_observed_not_unknown" ANSI_RESET "\n");
}

/** Representative enabled settings, decoded against the hand-pinned SB3 positions. */
static void test_hns_challenge_settings_representative_enabled(void) {
    printf("Running test_hns_challenge_settings_representative_enabled...\n");
    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    TEST_ASSERT(cfg != NULL, "H&S config must exist");

    static FakeGba gba;
    hns_cs_fixture_init(&gba);
    hns_cs_set_bit(&gba, PIN_OPTION_STYLE_BYTE, PIN_OPTION_STYLE_BIT, 1);
    hns_cs_set_bit(&gba, PIN_FAIRY_BYTE, PIN_FAIRY_BIT, 1);
    hns_cs_set_bit(&gba, PIN_RANDOM_TYPE_BYTE, PIN_RANDOM_TYPE_BIT, 1);
    hns_cs_set_bit(&gba, PIN_RANDOM_TYPE_EFFECTIVENESS_BYTE, PIN_RANDOM_TYPE_EFFECTIVENESS_BIT, 1);
    hns_cs_set_bit(&gba, PIN_NO_EVS_BYTE, PIN_NO_EVS_BIT, 1);
    hns_cs_set_field2(&gba, PIN_BST_EQ_BYTE, PIN_BST_EQ_BIT, 3);      // 500 BST
    hns_cs_set_bit(&gba, PIN_MIRROR_BYTE, PIN_MIRROR_BIT, 1);
    // Mirror_Thief deliberately left 0: an asymmetric pair across the byte so a
    // mutation that swaps the two settings must fail the decodes below.
    hns_cs_set_field2(&gba, PIN_SCALING_IVS_BYTE, PIN_SCALING_IVS_BIT, 2); // HARD
    hns_cs_set_field2(&gba, PIN_SCALING_EVS_BYTE, PIN_SCALING_EVS_BIT, 3);
    hns_cs_set_field2(&gba, PIN_MAX_PARTY_IVS_BYTE, PIN_MAX_PARTY_IVS_BIT, 1);
    hns_cs_set_field2(&gba, PIN_LEVEL_CAP_BYTE, PIN_LEVEL_CAP_BIT, 1);     // NORMAL
    hns_cs_set_field2(&gba, PIN_EXP_MULT_BYTE, PIN_EXP_MULT_BIT, 2);       // x2.0
    hns_cs_set_bit(&gba, PIN_STURDY_BYTE, PIN_STURDY_BIT, 1);

    ChallengeSettingsSnapshot snap;
    TEST_ASSERT(pokemon_read_challenge_settings_gba(fake_gba_read, &gba.table, NULL, 0, cfg, &snap),
                "representative enabled settings must read");
    TEST_ASSERT(snap.status == CHALLENGE_SETTINGS_OBSERVED, "status must be OBSERVED");
    TEST_ASSERT(!snap.option_style.invalid && !snap.tx_challenges_level_cap.invalid &&
                !snap.tx_challenges_base_stat_equalizer.invalid,
                "in-domain values must never be flagged invalid");
    TEST_ASSERT(snap.option_style.raw == 1, "optionStyle must decode as set");
    TEST_ASSERT(snap.tx_mode_fairy_types.raw == 1, "Fairy types must decode as set");
    TEST_ASSERT(snap.tx_random_type.raw == 1, "tx_Random_Type must decode as set");
    TEST_ASSERT(snap.tx_random_type_effectiveness.raw == 1, "tx_Random_TypeEffectiveness must decode as set");
    TEST_ASSERT(snap.tx_challenges_no_evs.raw == 1, "NoEVs must decode as set");
    TEST_ASSERT(snap.tx_challenges_base_stat_equalizer.raw == 3, "BST equalizer must decode as 3 (500)");
    TEST_ASSERT(snap.tx_challenges_mirror.raw == 1, "Mirror must decode as set");
    TEST_ASSERT(snap.tx_challenges_mirror_thief.raw == 0 && snap.tx_challenges_mirror_thief.observed,
                "Mirror_Thief must stay observed 0 while Mirror is 1 (asymmetric pin)");
    TEST_ASSERT(snap.tx_challenges_trainer_scaling_ivs.raw == 2, "ScalingIVs must decode as 2 (HARD)");
    TEST_ASSERT(snap.tx_challenges_trainer_scaling_evs.raw == 3, "ScalingEVs must decode as 3");
    TEST_ASSERT(snap.tx_challenges_max_party_ivs.raw == 1, "MaxPartyIVs must decode as 1");
    TEST_ASSERT(snap.tx_challenges_level_cap.raw == 1, "LevelCap must decode as 1 (NORMAL)");
    TEST_ASSERT(snap.tx_challenges_exp_multiplier.raw == 2, "ExpMultiplier must decode as 2 (x2.0)");
    TEST_ASSERT(snap.tx_mode_sturdy.raw == 1, "Sturdy mode must decode as set");
    // Fields left at zero in this fixture are observed OFF, not unknown.
    TEST_ASSERT(snap.tx_random_abilities.observed && snap.tx_random_abilities.raw == 0,
                "tx_Random_Abilities must stay observed 0");
    TEST_ASSERT(snap.tx_random_moves.observed && snap.tx_random_moves.raw == 0,
                "tx_Random_Moves must stay observed 0");
    TEST_ASSERT(snap.tx_mode_legendary_abilities.observed && snap.tx_mode_legendary_abilities.raw == 0,
                "Legendary abilities must stay observed 0");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_challenge_settings_representative_enabled" ANSI_RESET "\n");
}

/** optionStyle alternate value plus independent single-field changes. */
static void test_hns_challenge_settings_option_style_and_isolation(void) {
    printf("Running test_hns_challenge_settings_option_style_and_isolation...\n");
    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    TEST_ASSERT(cfg != NULL, "H&S config must exist");

    static FakeGba gba;
    hns_cs_fixture_init(&gba);

    ChallengeSettingsSnapshot snap;
    TEST_ASSERT(pokemon_read_challenge_settings_gba(fake_gba_read, &gba.table, NULL, 0, cfg, &snap),
                "baseline read must succeed");
    TEST_ASSERT(snap.option_style.raw == 0, "optionStyle must start at 0");

    hns_cs_set_bit(&gba, PIN_OPTION_STYLE_BYTE, PIN_OPTION_STYLE_BIT, 1);
    TEST_ASSERT(pokemon_read_challenge_settings_gba(fake_gba_read, &gba.table, NULL, 0, cfg, &snap),
                "flip read must succeed");
    TEST_ASSERT(snap.option_style.raw == 1, "optionStyle must decode the alternate value 1");
    TEST_ASSERT(snap.tx_mode_fairy_types.raw == 0,
                "flipping optionStyle must not disturb any other field");

    // Multiple fields changed independently, one at a time.
    hns_cs_set_bit(&gba, PIN_FAIRY_BYTE, PIN_FAIRY_BIT, 1);
    hns_cs_set_field2(&gba, PIN_LEVEL_CAP_BYTE, PIN_LEVEL_CAP_BIT, 2);
    TEST_ASSERT(pokemon_read_challenge_settings_gba(fake_gba_read, &gba.table, NULL, 0, cfg, &snap),
                "two-field read must succeed");
    TEST_ASSERT(snap.tx_mode_fairy_types.raw == 1 && snap.tx_challenges_level_cap.raw == 2,
                "both changed fields must decode");
    TEST_ASSERT(snap.option_style.raw == 1,
                "earlier field must keep its observed value");
    TEST_ASSERT(snap.tx_random_type.raw == 0,
                "untouched field must stay observed 0");

    // Asymmetric neighbour pair inside one byte: only the Thief bit set.
    hns_cs_set_bit(&gba, PIN_MIRROR_BYTE, PIN_MIRROR_BIT, 0);
    hns_cs_set_bit(&gba, PIN_MIRROR_THIEF_BYTE, PIN_MIRROR_THIEF_BIT, 1);
    TEST_ASSERT(pokemon_read_challenge_settings_gba(fake_gba_read, &gba.table, NULL, 0, cfg, &snap),
                "thief-only read must succeed");
    TEST_ASSERT(snap.tx_challenges_mirror.raw == 0 &&
                snap.tx_challenges_mirror_thief.raw == 1,
                "Mirror/Mirror_Thief must decode independently (swap-detecting pin)");

    // Back to zero: the observed value must follow memory both ways.
    hns_cs_set_bit(&gba, PIN_OPTION_STYLE_BYTE, PIN_OPTION_STYLE_BIT, 0);
    TEST_ASSERT(pokemon_read_challenge_settings_gba(fake_gba_read, &gba.table, NULL, 0, cfg, &snap),
                "reset read must succeed");
    TEST_ASSERT(snap.option_style.raw == 0 && snap.option_style.observed,
                "optionStyle must be observed 0 after a reset, never unknown");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_challenge_settings_option_style_and_isolation" ANSI_RESET "\n");
}

/** Values the pinned source never assigns must be flagged, never coerced. */
static void test_hns_challenge_settings_out_of_domain(void) {
    printf("Running test_hns_challenge_settings_out_of_domain...\n");
    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    TEST_ASSERT(cfg != NULL, "H&S config must exist");

    static FakeGba gba;
    hns_cs_fixture_init(&gba);
    hns_cs_set_field2(&gba, PIN_LEVEL_CAP_BYTE, PIN_LEVEL_CAP_BIT, 3); // OFF/NORMAL/HARD only
    hns_cs_set_field2(&gba, PIN_SCALING_IVS_BYTE, PIN_SCALING_IVS_BIT, 3); // OFF/SCALE/HARD only
    hns_cs_set_field2(&gba, PIN_MAX_PARTY_IVS_BYTE, PIN_MAX_PARTY_IVS_BIT, 3);

    ChallengeSettingsSnapshot snap;
    TEST_ASSERT(pokemon_read_challenge_settings_gba(fake_gba_read, &gba.table, NULL, 0, cfg, &snap),
                "the bytes were readable, so the read itself succeeds");
    TEST_ASSERT(snap.status == CHALLENGE_SETTINGS_OBSERVED_INVALID,
                "out-of-domain values must mark the snapshot OBSERVED_INVALID");
    TEST_ASSERT(snap.tx_challenges_level_cap.observed && snap.tx_challenges_level_cap.raw == 3 &&
                snap.tx_challenges_level_cap.invalid,
                "LevelCap == 3 must be preserved raw and flagged out-of-domain");
    TEST_ASSERT(snap.tx_challenges_trainer_scaling_ivs.observed &&
                snap.tx_challenges_trainer_scaling_ivs.raw == 3 &&
                snap.tx_challenges_trainer_scaling_ivs.invalid,
                "ScalingIVs == 3 must be preserved raw and flagged out-of-domain");
    TEST_ASSERT(snap.tx_challenges_max_party_ivs.observed &&
                snap.tx_challenges_max_party_ivs.raw == 3 &&
                snap.tx_challenges_max_party_ivs.invalid,
                "MaxPartyIVs == 3 must be preserved raw and flagged out-of-domain");
    // In-domain fields in the same snapshot stay clean.
    TEST_ASSERT(snap.option_style.observed && !snap.option_style.invalid,
                "optionStyle must stay in-domain in an OBSERVED_INVALID snapshot");
    TEST_ASSERT(snap.tx_mode_fairy_types.observed && snap.tx_mode_fairy_types.raw == 0,
                "other fields must stay observed in an OBSERVED_INVALID snapshot");
    // ExpMultiplier's source domain really uses all four encodings.
    hns_cs_set_field2(&gba, PIN_EXP_MULT_BYTE, PIN_EXP_MULT_BIT, 3);
    TEST_ASSERT(pokemon_read_challenge_settings_gba(fake_gba_read, &gba.table, NULL, 0, cfg, &snap),
                "ExpMultiplier=3 (x0.0) must read");
    TEST_ASSERT(!snap.tx_challenges_exp_multiplier.invalid && snap.tx_challenges_exp_multiplier.raw == 3,
                "ExpMultiplier == 3 is a real source value, not out-of-domain");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_challenge_settings_out_of_domain" ANSI_RESET "\n");
}

/** Trust and memory failures: the reader must never authorize an untrusted read. */
static void test_hns_challenge_settings_fail_closed(void) {
    printf("Running test_hns_challenge_settings_fail_closed...\n");
    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    const GameMemoryConfig* fr = pokemon_get_game_config(GAME_FIRERED);
    const GameMemoryConfig* em = pokemon_get_game_config(GAME_EMERALD);
    TEST_ASSERT(cfg != NULL && fr != NULL && em != NULL, "configs must exist");

    ChallengeSettingsSnapshot snap;

    // A populated fixture that must never be readable through a wrong profile.
    static FakeGba gba;
    hns_cs_fixture_init(&gba);
    hns_cs_set_bit(&gba, PIN_FAIRY_BYTE, PIN_FAIRY_BIT, 1);

    // Wrong profile: FireRed/Emerald layouts do not declare SaveBlock3.
    TEST_ASSERT(!pokemon_read_challenge_settings_gba(fake_gba_read, &gba.table, NULL, 0, fr, &snap),
                "FireRed layout must not authorize the H&S SaveBlock3 read");
    expect_unavailable(&snap, "wrong profile must produce an UNAVAILABLE snapshot");
    TEST_ASSERT(!pokemon_read_challenge_settings_gba(fake_gba_read, &gba.table, NULL, 0, em, &snap),
                "Emerald layout must not authorize the H&S SaveBlock3 read");

    // No absolute-address reader.
    TEST_ASSERT(!pokemon_read_challenge_settings_gba(NULL, NULL, NULL, 0, cfg, &snap),
                "a NULL reader must fail closed");
    expect_unavailable(&snap, "NULL reader must produce an UNAVAILABLE snapshot");

    // Null / unknown configuration.
    TEST_ASSERT(!pokemon_read_challenge_settings_gba(fake_gba_read, &gba.table, NULL, 0, NULL, &snap),
                "a NULL configuration must fail closed");

    // Unreadable gSaveBlock3Ptr (IWRAM not mapped).
    static FakeGba no_iwram;
    hns_cs_fixture_init(&no_iwram);
    fake_gba_init(&no_iwram, false, true); // re-init without IWRAM
    TEST_ASSERT(!pokemon_read_challenge_settings_gba(fake_gba_read, &no_iwram.table, NULL, 0, cfg, &snap),
                "an unreadable gSaveBlock3Ptr must fail closed");
    expect_unavailable(&snap, "unreadable pointer must produce an UNAVAILABLE snapshot");

    // Pointer not equal to the compiled gSaveblock3: stale / another binary / misaligned.
    static const uint32_t bad_bases[] = {
        HNS_SB3_BASE_ABS + 4u,   // ASLR-style shift SaveBlock3 never gets
        HNS_SB3_BASE_ABS - 4u,
        0x02000000u,             // raw EWRAM base
        0x0202FFFFu,             // plausible but wrong
        0x03000000u              // IWRAM: outside EWRAM entirely
    };
    for (size_t i = 0; i < sizeof(bad_bases) / sizeof(bad_bases[0]); i++) {
        static FakeGba wrong_ptr;
        hns_cs_fixture_init(&wrong_ptr);
        write32_le_t(wrong_ptr.iwram + (HNS_SB3_PTR_ABS - 0x03000000u), bad_bases[i]);
        TEST_ASSERT(!pokemon_read_challenge_settings_gba(fake_gba_read, &wrong_ptr.table, NULL, 0, cfg, &snap),
                    "a pointer that is not the compiled gSaveblock3 must fail closed");
        expect_unavailable(&snap, "wrong pointer must produce an UNAVAILABLE snapshot");
    }

    // Truncated memory window: EWRAM mapped only through the middle of the struct.
    static FakeGba truncated;
    hns_cs_fixture_init(&truncated);
    gba_memory_map_clear(&truncated.table);
    gba_memory_map_add(&truncated.table, truncated.iwram, 0x03000000u, 0x8000u, 0xFF000000u, 0u, 0u, 0u);
    gba_memory_map_add(&truncated.table, truncated.ewram, 0x02000000u, 0x9230u, 0xFF000000u, 0u, 0u, 0u);
    TEST_ASSERT(!pokemon_read_challenge_settings_gba(fake_gba_read, &truncated.table, NULL, 0, cfg, &snap),
                "a truncated window must fail closed");
    expect_unavailable(&snap, "truncated window must produce an UNAVAILABLE snapshot");

    // No bytes written at all (pointer sane, struct blank) must still read as all-zero.
    static FakeGba blank;
    hns_cs_fixture_init(&blank);
    TEST_ASSERT(pokemon_read_challenge_settings_gba(fake_gba_read, &blank.table, NULL, 0, cfg, &snap),
                "the blank-but-trusted fixture must read");
    TEST_ASSERT(snap.status == CHALLENGE_SETTINGS_OBSERVED && snap.tx_mode_fairy_types.raw == 0,
                "blank struct reads as observed zero, not as the earlier fixture");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_challenge_settings_fail_closed" ANSI_RESET "\n");
}

/** No caching: a game/profile switch can never inherit the previous game's settings. */
static void test_hns_challenge_settings_no_stale_across_games(void) {
    printf("Running test_hns_challenge_settings_no_stale_across_games...\n");
    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    const GameMemoryConfig* fr = pokemon_get_game_config(GAME_FIRERED);
    TEST_ASSERT(cfg != NULL && fr != NULL, "configs must exist");

    static FakeGba gba;
    hns_cs_fixture_init(&gba);
    hns_cs_set_bit(&gba, PIN_FAIRY_BYTE, PIN_FAIRY_BIT, 1);
    hns_cs_set_field2(&gba, PIN_LEVEL_CAP_BYTE, PIN_LEVEL_CAP_BIT, 2);

    ChallengeSettingsSnapshot snap;
    TEST_ASSERT(pokemon_read_challenge_settings_gba(fake_gba_read, &gba.table, NULL, 0, cfg, &snap),
                "precondition: trusted H&S read must succeed");
    TEST_ASSERT(snap.tx_mode_fairy_types.raw == 1 && snap.tx_challenges_level_cap.raw == 2,
                "precondition: fixture values must decode");

    // The "ROM switched" world: same reader, different game config. The reader must not serve
    // the previous game's settings and must not cache them between calls.
    TEST_ASSERT(!pokemon_read_challenge_settings_gba(fake_gba_read, &gba.table, NULL, 0, fr, &snap),
                "after a profile switch the previous settings must not be served");
    expect_unavailable(&snap, "a profile switch must produce an UNAVAILABLE snapshot");

    // Back on the exact build, with different memory content: the fresh values must decode.
    hns_cs_set_bit(&gba, PIN_FAIRY_BYTE, PIN_FAIRY_BIT, 0);
    hns_cs_set_bit(&gba, PIN_RANDOM_TYPE_BYTE, PIN_RANDOM_TYPE_BIT, 1);
    TEST_ASSERT(pokemon_read_challenge_settings_gba(fake_gba_read, &gba.table, NULL, 0, cfg, &snap),
                "re-authorized read must succeed");
    TEST_ASSERT(snap.tx_mode_fairy_types.raw == 0 && snap.tx_random_type.raw == 1 &&
                snap.tx_challenges_level_cap.raw == 2,
                "fresh values must replace the previous game's settings");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_challenge_settings_no_stale_across_games" ANSI_RESET "\n");
}

// ===========================================================================
// H&S 2.0.5 live battler ability + effective types (issue #9).
//
// The semantic pins below are hand-pinned independently from the production
// layout table: offsets from the labelled /*0xNN*/ member comments in the
// pinned include/pokemon.h (compiled ABI evidence agrees with every one of
// them; see docs/HNS_2_0_5_COMPATIBILITY_EVIDENCE.md), widths from the packed
// APCS-GNU ABI, and the ID domains from the pinned constant enums
// (include/constants/abilities.h, include/constants/pokemon.h). A production
// table or reader that drifts from these pins must fail these tests at the
// semantic level, not just a checksum.
// ===========================================================================

#define PIN_BATTLE_POKEMON_SIZEOF 136
#define PIN_BATTLE_POKEMON_SPECIES_OFFSET 0x00
#define PIN_BATTLE_POKEMON_SPECIES_SIZE 2
#define PIN_BATTLE_POKEMON_ABILITY_OFFSET 0x20
#define PIN_BATTLE_POKEMON_ABILITY_SIZE 2
#define PIN_BATTLE_POKEMON_TYPES_OFFSET 0x22
#define PIN_BATTLE_POKEMON_TYPE_COUNT 3
#define PIN_BATTLE_POKEMON_TYPE_ELEMENT_SIZE 1
#define PIN_BATTLE_POKEMON_ABILITY_ID_MAX 310
#define PIN_BATTLE_POKEMON_TYPE_ID_MAX 20
// The compiled item offset is 0x30, NOT the stale /*0x2F*/ source comment: the
// expansion's struct BattlePokemon is naturally aligned and `enum Item` is a
// 2-byte packed enum, so `u16 maxHP` at 0x2D rounds to 0x2E and `item` lands at
// 0x30. The same compiled layout gives hp = 0x2A, which the production config
// already uses. Independently pinned from the compiled ABI probe.
#define PIN_BATTLE_POKEMON_ITEM_OFFSET 0x30
#define PIN_BATTLE_POKEMON_ITEM_SIZE 2
#define PIN_BATTLE_POKEMON_ITEM_ID_MAX 900

#define PIN_BATTLE_POKEMON_ATTACK_OFFSET 2
#define PIN_BATTLE_POKEMON_ATTACK_SIZE 2
#define PIN_BATTLE_POKEMON_DEFENSE_OFFSET 4
#define PIN_BATTLE_POKEMON_DEFENSE_SIZE 2
#define PIN_BATTLE_POKEMON_SPEED_OFFSET 6
#define PIN_BATTLE_POKEMON_SPEED_SIZE 2
#define PIN_BATTLE_POKEMON_SPATTACK_OFFSET 8
#define PIN_BATTLE_POKEMON_SPATTACK_SIZE 2
#define PIN_BATTLE_POKEMON_SPDEFENSE_OFFSET 10
#define PIN_BATTLE_POKEMON_SPDEFENSE_SIZE 2
#define PIN_BATTLE_POKEMON_STAT_STAGES_OFFSET 0x18
#define PIN_BATTLE_POKEMON_STAT_STAGES_COUNT 8
#define PIN_SAVE_BLOCK1_FLAGS_OFFSET 0x198C
#define PIN_SAVE_BLOCK1_BADGES_OFFSET 0x1A98

// Pinned H&S item identities used by the observation tests, taken from the pinned
// enum Item (independently of the generated Kotlin catalogue).
#define PIN_ITEM_NONE 0
#define PIN_ITEM_CHARCOAL 426
#define PIN_ITEM_CHOICE_BAND 442
#define PIN_ITEM_OUT_OF_DOMAIN (PIN_BATTLE_POKEMON_ITEM_ID_MAX + 1)

// Pinned Gen III + H&S ability identities for observation tests (from the
// pinned include/constants/abilities.h, independently of the data pack).
#define PIN_ABILITY_OVERGROW 65
#define PIN_ABILITY_BLAZE 66
#define PIN_ABILITY_TORRENT 67

// Pinned H&S type IDs (from the pinned include/constants/pokemon.h).
#define PIN_TYPE_NONE 0
#define PIN_TYPE_NORMAL 1
#define PIN_TYPE_FLYING 3
#define PIN_TYPE_POISON 4
#define PIN_TYPE_MYSTERY 10
#define PIN_TYPE_FIRE 11
#define PIN_TYPE_GRASS 12
#define PIN_TYPE_ELECTRIC 14

/** A failed read must leave no field carrying a value: no stale battler, slot, ability or item. */
static void expect_battler_unavailable(const BattlerRuntimeState* st, const char* msg) {
    TEST_ASSERT(st->status == BATTLER_RUNTIME_STATE_UNAVAILABLE, msg);
    TEST_ASSERT(st->battler_index == -1 && st->party_slot == -1 && !st->party_slot_known,
                "an unavailable observation must not carry a battler or slot");
    TEST_ASSERT(!st->ability_observed && !st->types_observed && st->type_count == 0,
                "an unavailable observation must not carry an ability or types");
    TEST_ASSERT(!st->item_observed && !st->item_invalid && st->item_id == 0,
                "an unavailable observation must not carry a stale held item");
    TEST_ASSERT(!st->stats_observed && st->raw_attack == 0 && st->raw_defense == 0 &&
                st->raw_speed == 0 && st->raw_sp_attack == 0 && st->raw_sp_defense == 0,
                "an unavailable observation must not carry stats");
    TEST_ASSERT(!st->stages_observed, "an unavailable observation must not carry stat stages");
    TEST_ASSERT(!st->badges_observed && !st->badge_boost_atk && !st->badge_boost_def &&
                !st->badge_boost_spe && !st->badge_boost_spa && !st->badge_boost_spd &&
                st->raw_badges_byte == 0,
                "an unavailable observation must not carry badges");
    /* Gap C4e operands: no stale HP/status/volatile/gimmick value may survive. */
    TEST_ASSERT(!st->hp_observed && st->hp == 0 && st->max_hp == 0,
                "an unavailable observation must not carry HP");
    TEST_ASSERT(!st->status_observed && st->status1 == 0,
                "an unavailable observation must not carry a status");
    TEST_ASSERT(!st->volatiles_observed && !st->volatile_electrified &&
                !st->volatile_glaive_rush && !st->volatile_minimize &&
                st->volatile_semi_invulnerable == 0 &&
                st->volatile_charge_timer == 0 && !st->volatile_tar_shot &&
                !st->volatile_foresight && !st->volatile_miracle_eye &&
                !st->volatile_root && !st->volatile_smack_down &&
                !st->volatile_telekinesis && !st->volatile_magnet_rise &&
                !st->volatile_gastro_acid && !st->volatile_roost_active &&
                !st->volatile_substitute && !st->volatile_endured,
                "an unavailable observation must not carry volatile state");
    TEST_ASSERT(!st->gimmick_observed && st->active_gimmick == 0,
                "an unavailable observation must not carry a gimmick");
    TEST_ASSERT(!st->field_statuses_readable && st->field_statuses == 0,
                "an unavailable observation must not carry field statuses");
    TEST_ASSERT(!st->weather_readable && st->battle_weather == 0,
                "an unavailable observation must not carry weather");
    TEST_ASSERT(!st->side_statuses_readable && st->side_statuses == 0,
                "an unavailable observation must not carry side statuses");
}

/**
 * Write the engine's current effective ability + type words for one battler,
 * at the independently pinned offsets (NOT at values read from the production
 * table): this is what makes a layout mutation fail these tests semantically.
 */
static void hns_battle_set_battler_ability_types(HnsBattleFixture* fx, uint8_t battler,
                                                 uint16_t ability, const uint8_t types[3]) {
    uint8_t* mon = fx->gba->ewram + fx->cfg->battle_mons_offset +
                   ((size_t)battler * fx->cfg->battle_mons_size);
    write16_le_t(mon + PIN_BATTLE_POKEMON_ABILITY_OFFSET, ability);
    for (unsigned t = 0; t < PIN_BATTLE_POKEMON_TYPE_COUNT; t++) {
        mon[PIN_BATTLE_POKEMON_TYPES_OFFSET + t] = types[t];
    }
}

/** Write the engine's current battle held-item word for one battler, at the pinned offset. */
static void hns_battle_set_battler_item(HnsBattleFixture* fx, uint8_t battler, uint16_t item) {
    uint8_t* mon = fx->gba->ewram + fx->cfg->battle_mons_offset +
                   ((size_t)battler * fx->cfg->battle_mons_size);
    write16_le_t(mon + PIN_BATTLE_POKEMON_ITEM_OFFSET, item);
}

/** Write the engine's battle raw stats for one battler, at the pinned offsets. */
static void hns_battle_set_battler_stats(HnsBattleFixture* fx, uint8_t battler,
                                         uint16_t atk, uint16_t def, uint16_t spe,
                                         uint16_t spa, uint16_t spd) {
    uint8_t* mon = fx->gba->ewram + fx->cfg->battle_mons_offset +
                   ((size_t)battler * fx->cfg->battle_mons_size);
    write16_le_t(mon + PIN_BATTLE_POKEMON_ATTACK_OFFSET, atk);
    write16_le_t(mon + PIN_BATTLE_POKEMON_DEFENSE_OFFSET, def);
    write16_le_t(mon + PIN_BATTLE_POKEMON_SPEED_OFFSET, spe);
    write16_le_t(mon + PIN_BATTLE_POKEMON_SPATTACK_OFFSET, spa);
    write16_le_t(mon + PIN_BATTLE_POKEMON_SPDEFENSE_OFFSET, spd);
}

/** Write the engine's battle stat stages for one battler, at the pinned offset. */
static void hns_battle_set_battler_stat_stages(HnsBattleFixture* fx, uint8_t battler,
                                               const uint8_t stages[8]) {
    uint8_t* mon = fx->gba->ewram + fx->cfg->battle_mons_offset +
                   ((size_t)battler * fx->cfg->battle_mons_size);
    for (unsigned s = 0; s < PIN_BATTLE_POKEMON_STAT_STAGES_COUNT; s++) {
        mon[PIN_BATTLE_POKEMON_STAT_STAGES_OFFSET + s] = stages[s];
    }
}

/** Write the player's Johto badge state into the two SaveBlock1.flags bytes used by H&S.
 *
 * H&S badge flag layout (derived from pinned upstream source):
 *   byte0 (save_block1_badges_offset + 0, SaveBlock1+0x1A98):
 *     bit 7 = FLAG_BADGE01_GET (Falkner/Zephyr -> Atk)
 *   byte1 (save_block1_badges_offset + 1, SaveBlock1+0x1A99):
 *     bit 1 = FLAG_BADGE03_GET (Whitney/Plain  -> Spe)
 *     bit 4 = FLAG_BADGE06_GET (Jasmine/Mineral-> Def)
 *     bit 5 = FLAG_BADGE07_GET (Pryce/Glacier  -> SpA+SpD)
 *
 * @param badge_atk  Falkner badge (Atk boost)
 * @param badge_spe  Whitney badge (Spe boost)
 * @param badge_def  Jasmine badge (Def boost)
 * @param badge_spa  Pryce badge (SpA+SpD boost)
 */
static void hns_battle_set_badges(HnsBattleFixture* fx,
                                  bool badge_atk, bool badge_spe,
                                  bool badge_def, bool badge_spa) {
    uint32_t sb1_addr = fx->cfg->save_block1_base_gba_address + 88u;
    uint32_t byte0_addr = sb1_addr + fx->cfg->save_block1_badges_offset;
    uint32_t byte1_addr = byte0_addr + 1u;
    uint8_t byte0 = badge_atk ? (1u << 7) : 0u;
    uint8_t byte1 = (badge_spe ? (1u << 1) : 0u)
                  | (badge_def ? (1u << 4) : 0u)
                  | (badge_spa ? (1u << 5) : 0u);
    fx->gba->ewram[byte0_addr - 0x02000000u] = byte0;
    fx->gba->ewram[byte1_addr - 0x02000000u] = byte1;
}

static bool read_battler_state(const HnsBattleFixture* fx, BattlerRole role,
                               BattlerRuntimeState* out) {
    return pokemon_read_battler_runtime_state_gba(fake_gba_read, &fx->gba->table,
                                                  fx->gba->ewram, sizeof(fx->gba->ewram),
                                                  fx->cfg, role, out);
}

/**
 * The generated BattlePokemon layout table and the H&S production config must
 * match the independently pinned values exactly, and no other game may declare
 * the live fields.
 */
static void test_hns_battle_pokemon_live_layout_pins(void) {
    printf("Running test_hns_battle_pokemon_live_layout_pins...\n");

    TEST_ASSERT(HNS_BATTLE_POKEMON_SIZEOF == PIN_BATTLE_POKEMON_SIZEOF,
                "generated BattlePokemon stride must equal the pinned 136");
    TEST_ASSERT(HNS_BATTLE_POKEMON_SPECIES_OFFSET == PIN_BATTLE_POKEMON_SPECIES_OFFSET &&
                HNS_BATTLE_POKEMON_SPECIES_SIZE == PIN_BATTLE_POKEMON_SPECIES_SIZE,
                "generated current-species offset and width must equal the pinned field");
    TEST_ASSERT(HNS_BATTLE_POKEMON_ABILITY_OFFSET == PIN_BATTLE_POKEMON_ABILITY_OFFSET,
                "generated ability offset must equal the pinned 0x20");
    TEST_ASSERT(HNS_BATTLE_POKEMON_ABILITY_SIZE == PIN_BATTLE_POKEMON_ABILITY_SIZE,
                "generated ability width must equal the pinned 2");
    TEST_ASSERT(HNS_BATTLE_POKEMON_TYPES_OFFSET == PIN_BATTLE_POKEMON_TYPES_OFFSET,
                "generated types offset must equal the pinned 0x22");
    TEST_ASSERT(HNS_BATTLE_POKEMON_TYPE_COUNT == PIN_BATTLE_POKEMON_TYPE_COUNT,
                "generated type count must equal the pinned 3");
    TEST_ASSERT(HNS_BATTLE_POKEMON_TYPE_ELEMENT_SIZE == PIN_BATTLE_POKEMON_TYPE_ELEMENT_SIZE,
                "generated type element width must equal the pinned 1");
    TEST_ASSERT(HNS_BATTLE_POKEMON_ABILITY_ID_MAX == PIN_BATTLE_POKEMON_ABILITY_ID_MAX,
                "generated ability domain must equal the pinned ABILITY_ID_MAX");
    TEST_ASSERT(HNS_BATTLE_POKEMON_TYPE_ID_MAX == PIN_BATTLE_POKEMON_TYPE_ID_MAX,
                "generated type domain must equal the pinned TYPE_ID_MAX");
    TEST_ASSERT(HNS_BATTLE_POKEMON_ITEM_OFFSET == PIN_BATTLE_POKEMON_ITEM_OFFSET,
                "generated item offset must equal the compiled pinned 0x30");
    TEST_ASSERT(HNS_BATTLE_POKEMON_ITEM_SIZE == PIN_BATTLE_POKEMON_ITEM_SIZE,
                "generated item width must equal the pinned 2");
    TEST_ASSERT(HNS_BATTLE_POKEMON_ITEM_ID_MAX == PIN_BATTLE_POKEMON_ITEM_ID_MAX,
                "generated item domain must equal the pinned ITEMS_COUNT - 1");
    TEST_ASSERT(HNS_BATTLE_POKEMON_ATTACK_OFFSET == PIN_BATTLE_POKEMON_ATTACK_OFFSET,
                "generated attack offset must equal the pinned 2");
    TEST_ASSERT(HNS_BATTLE_POKEMON_ATTACK_SIZE == PIN_BATTLE_POKEMON_ATTACK_SIZE,
                "generated attack width must equal the pinned 2");
    TEST_ASSERT(HNS_BATTLE_POKEMON_DEFENSE_OFFSET == PIN_BATTLE_POKEMON_DEFENSE_OFFSET,
                "generated defense offset must equal the pinned 4");
    TEST_ASSERT(HNS_BATTLE_POKEMON_DEFENSE_SIZE == PIN_BATTLE_POKEMON_DEFENSE_SIZE,
                "generated defense width must equal the pinned 2");
    TEST_ASSERT(HNS_BATTLE_POKEMON_SPEED_OFFSET == PIN_BATTLE_POKEMON_SPEED_OFFSET,
                "generated speed offset must equal the pinned 6");
    TEST_ASSERT(HNS_BATTLE_POKEMON_SPEED_SIZE == PIN_BATTLE_POKEMON_SPEED_SIZE,
                "generated speed width must equal the pinned 2");
    TEST_ASSERT(HNS_BATTLE_POKEMON_SPATTACK_OFFSET == PIN_BATTLE_POKEMON_SPATTACK_OFFSET,
                "generated spAttack offset must equal the pinned 8");
    TEST_ASSERT(HNS_BATTLE_POKEMON_SPATTACK_SIZE == PIN_BATTLE_POKEMON_SPATTACK_SIZE,
                "generated spAttack width must equal the pinned 2");
    TEST_ASSERT(HNS_BATTLE_POKEMON_SPDEFENSE_OFFSET == PIN_BATTLE_POKEMON_SPDEFENSE_OFFSET,
                "generated spDefense offset must equal the pinned 10");
    TEST_ASSERT(HNS_BATTLE_POKEMON_SPDEFENSE_SIZE == PIN_BATTLE_POKEMON_SPDEFENSE_SIZE,
                "generated spDefense width must equal the pinned 2");
    TEST_ASSERT(HNS_BATTLE_POKEMON_STAT_STAGES_OFFSET == PIN_BATTLE_POKEMON_STAT_STAGES_OFFSET,
                "generated statStages offset must equal the pinned 0x18");
    TEST_ASSERT(HNS_BATTLE_POKEMON_STAT_STAGES_COUNT == PIN_BATTLE_POKEMON_STAT_STAGES_COUNT,
                "generated statStages count must equal the pinned 8");

    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    TEST_ASSERT(cfg->battle_mons_size == PIN_BATTLE_POKEMON_SIZEOF &&
                cfg->battle_mons_ability_offset == PIN_BATTLE_POKEMON_ABILITY_OFFSET &&
                cfg->battle_mons_ability_size == PIN_BATTLE_POKEMON_ABILITY_SIZE &&
                cfg->battle_mons_types_offset == PIN_BATTLE_POKEMON_TYPES_OFFSET &&
                cfg->battle_mons_type_count == PIN_BATTLE_POKEMON_TYPE_COUNT &&
                cfg->battle_mons_type_width == PIN_BATTLE_POKEMON_TYPE_ELEMENT_SIZE &&
                cfg->battle_mons_item_offset == PIN_BATTLE_POKEMON_ITEM_OFFSET &&
                cfg->battle_mons_item_size == PIN_BATTLE_POKEMON_ITEM_SIZE &&
                cfg->battle_mons_attack_offset == PIN_BATTLE_POKEMON_ATTACK_OFFSET &&
                cfg->battle_mons_attack_size == PIN_BATTLE_POKEMON_ATTACK_SIZE &&
                cfg->battle_mons_defense_offset == PIN_BATTLE_POKEMON_DEFENSE_OFFSET &&
                cfg->battle_mons_defense_size == PIN_BATTLE_POKEMON_DEFENSE_SIZE &&
                cfg->battle_mons_speed_offset == PIN_BATTLE_POKEMON_SPEED_OFFSET &&
                cfg->battle_mons_speed_size == PIN_BATTLE_POKEMON_SPEED_SIZE &&
                cfg->battle_mons_spattack_offset == PIN_BATTLE_POKEMON_SPATTACK_OFFSET &&
                cfg->battle_mons_spattack_size == PIN_BATTLE_POKEMON_SPATTACK_SIZE &&
                cfg->battle_mons_spdefense_offset == PIN_BATTLE_POKEMON_SPDEFENSE_OFFSET &&
                cfg->battle_mons_spdefense_size == PIN_BATTLE_POKEMON_SPDEFENSE_SIZE &&
                cfg->battle_mons_stat_stages_offset == PIN_BATTLE_POKEMON_STAT_STAGES_OFFSET &&
                cfg->save_block1_flags_offset == PIN_SAVE_BLOCK1_FLAGS_OFFSET &&
                cfg->save_block1_badges_offset == PIN_SAVE_BLOCK1_BADGES_OFFSET,
                "the H&S config must carry the pinned BattlePokemon layout and SaveBlock1 offsets");

    // Vanilla games must not inherit the H&S live-field interpretation.
    static const GbaGameId VANILLA[] = {
        GAME_FIRERED, GAME_LEAFGREEN, GAME_EMERALD, GAME_RUBY, GAME_SAPPHIRE
    };
    for (size_t i = 0; i < sizeof(VANILLA) / sizeof(VANILLA[0]); i++) {
        const GameMemoryConfig* v = pokemon_get_game_config(VANILLA[i]);
        TEST_ASSERT(v != NULL && v->battle_mons_ability_offset == 0 &&
                    v->battle_mons_types_offset == 0 && v->battle_mons_type_count == 0 &&
                    v->battle_mons_item_offset == 0 && v->battle_mons_item_size == 0 &&
                    v->battle_mons_attack_offset == 0 && v->battle_mons_defense_offset == 0 &&
                    v->battle_mons_speed_offset == 0 && v->battle_mons_spattack_offset == 0 &&
                    v->battle_mons_spdefense_offset == 0 && v->save_block1_badges_offset == 0,
                    "a vanilla layout must not declare the H&S live fields");
    }

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_battle_pokemon_live_layout_pins" ANSI_RESET "\n");
}

/** A standard singles battle whose two live battlers carry distinct abilities and types. */
static void hns_battler_fixture_two_battlers(HnsBattleFixture* fx, FakeGba* gba,
                                             const GameMemoryConfig* cfg) {
    pokemon_reader_reset();
    hns_battle_fixture_init(fx, gba, cfg);
    hns_battle_fill_player_party(fx, 2);
    hns_battle_fill_enemy_party(fx, 2);
    hns_battle_set_in_battle(fx, true);
    hns_battle_set_counters(fx, 2, 0u, 0);
    gba->ewram[cfg->absent_battler_flags_offset] = 0;
    hns_battle_set_battler(fx, 0, 0, 0);
    hns_battle_set_battler(fx, 1, 1, 0);
    hns_battle_set_mon(fx, 0, 155, 50);
    hns_battle_set_mon(fx, 1, 16, 40);
    { const uint8_t types[3] = {PIN_TYPE_FIRE, PIN_TYPE_NONE, PIN_TYPE_NONE};
      hns_battle_set_battler_ability_types(fx, 0, PIN_ABILITY_BLAZE, types); }
    hns_battle_set_battler_item(fx, 0, PIN_ITEM_CHARCOAL);
    { const uint8_t types[3] = {PIN_TYPE_NORMAL, PIN_TYPE_FLYING, PIN_TYPE_NONE};
      hns_battle_set_battler_ability_types(fx, 1, 51, types); }
    hns_battle_set_battler_item(fx, 1, PIN_ITEM_CHOICE_BAND);
}

/**
 * The active player battler's effective ability and types are read straight
 * from gBattleMons[0], with authoritative provenance (battler 0, party slot
 * from gBattlerPartyIndexes[0]).
 */
static void test_hns_battler_state_observes_active_player(void) {
    printf("Running test_hns_battler_state_observes_active_player...\n");
    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    static FakeGba gba;
    HnsBattleFixture fx;
    hns_battler_fixture_two_battlers(&fx, &gba, cfg);

    BattlerRuntimeState st;
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_PLAYER, &st),
                "an active player battler must be observable");
    TEST_ASSERT(st.status == BATTLER_RUNTIME_STATE_OBSERVED, "status must be OBSERVED");
    TEST_ASSERT(st.battler_index == 0, "the player observation must be battler 0");
    TEST_ASSERT(st.party_slot_known && st.party_slot == 0,
                "the player slot must come from gBattlerPartyIndexes[0]");
    TEST_ASSERT(st.ability_observed && !st.ability_invalid && st.ability_id == PIN_ABILITY_BLAZE,
                "the effective ability must be the engine's current word, not a declaration");
    TEST_ASSERT(st.types_observed && st.type_count == 3 &&
                st.types[0] == PIN_TYPE_FIRE && st.types[1] == PIN_TYPE_NONE &&
                st.types[2] == PIN_TYPE_NONE && !st.types_invalid,
                "a monotype battler must preserve the TYPE_NONE sentinels verbatim");
    TEST_ASSERT(st.item_observed && !st.item_invalid && st.item_id == PIN_ITEM_CHARCOAL,
                "the current held item must be the engine's item word, not the party structure's");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_battler_state_observes_active_player" ANSI_RESET "\n");
}

/** The active single opponent battler is observed through the authoritative enemy resolution. */
static void test_hns_battler_state_observes_active_opponent(void) {
    printf("Running test_hns_battler_state_observes_active_opponent...\n");
    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    static FakeGba gba;
    HnsBattleFixture fx;
    hns_battler_fixture_two_battlers(&fx, &gba, cfg);

    BattlerRuntimeState st;
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_OPPONENT, &st),
                "an active single opponent must be observable");
    TEST_ASSERT(st.status == BATTLER_RUNTIME_STATE_OBSERVED, "status must be OBSERVED");
    TEST_ASSERT(st.battler_index == 1, "the opponent observation must be the real enemy battler");
    TEST_ASSERT(st.party_slot_known && st.party_slot == 0,
                "the enemy slot must come from the authoritative battler path");
    TEST_ASSERT(st.ability_observed && st.ability_id == 51,
                "the opponent's effective ability must be its own, never the player's");
    TEST_ASSERT(st.types_observed && st.types[0] == PIN_TYPE_NORMAL &&
                st.types[1] == PIN_TYPE_FLYING && st.types[2] == PIN_TYPE_NONE,
                "the opponent's current dual typing must be observed");
    TEST_ASSERT(st.item_observed && st.item_id == PIN_ITEM_CHOICE_BAND,
                "the opponent's current item must be its own, never the player's");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_battler_state_observes_active_opponent" ANSI_RESET "\n");
}

/**
 * A player switch commits through gBattlerPartyIndexes[0] and a rewritten
 * gBattleMons[0]; the observation must follow the new battler and the old
 * ability must be gone.
 */
static void test_hns_battler_state_player_switch_follows_authority(void) {
    printf("Running test_hns_battler_state_player_switch_follows_authority...\n");
    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    static FakeGba gba;
    HnsBattleFixture fx;
    hns_battler_fixture_two_battlers(&fx, &gba, cfg);

    BattlerRuntimeState before;
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_PLAYER, &before),
                "precondition: initial player observation");
    TEST_ASSERT(before.ability_id == PIN_ABILITY_BLAZE && before.party_slot == 0,
                "precondition: initial ability/slot");

    // The engine commits the switch: new party slot + a rewritten gBattleMons[0].
    hns_battle_set_battler(&fx, 0, 0, 1);
    hns_battle_set_mon(&fx, 0, 158, 38);
    { const uint8_t types[3] = {PIN_TYPE_GRASS, PIN_TYPE_POISON, PIN_TYPE_NONE};
      hns_battle_set_battler_ability_types(&fx, 0, PIN_ABILITY_OVERGROW, types); }

    BattlerRuntimeState after;
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_PLAYER, &after),
                "post-switch player must be observable");
    TEST_ASSERT(after.party_slot == 1, "the slot must follow the authoritative party index");
    TEST_ASSERT(after.ability_id == PIN_ABILITY_OVERGROW,
                "the stale Blaze ability must disappear; the new battler's ability is authoritative");
    TEST_ASSERT(after.types[0] == PIN_TYPE_GRASS && after.types[1] == PIN_TYPE_POISON,
                "the new battler's current types must be observed");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_battler_state_player_switch_follows_authority" ANSI_RESET "\n");
}

/** An opponent voluntary switch commits through the enemy battler's party index; observation follows. */
static void test_hns_battler_state_opponent_switch_follows_authority(void) {
    printf("Running test_hns_battler_state_opponent_switch_follows_authority...\n");
    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    static FakeGba gba;
    HnsBattleFixture fx;
    hns_battler_fixture_two_battlers(&fx, &gba, cfg);

    BattlerRuntimeState before;
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_OPPONENT, &before),
                "precondition: initial opponent observation");
    TEST_ASSERT(before.ability_id == 51 && before.party_slot == 0,
                "precondition: initial ability/slot");

    // Voluntary switch: same enemy battler, new party slot, rewritten gBattleMons[1].
    hns_battle_set_battler(&fx, 1, 1, 1);
    hns_battle_set_mon(&fx, 1, 21, 35);
    { const uint8_t types[3] = {PIN_TYPE_NORMAL, PIN_TYPE_FLYING, PIN_TYPE_NONE};
      hns_battle_set_battler_ability_types(&fx, 1, 16 /* PINNED: *not* the old ability */, types); }

    BattlerRuntimeState after;
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_OPPONENT, &after),
                "post-switch opponent must be observable");
    TEST_ASSERT(after.battler_index == 1 && after.party_slot == 1,
                "the observation must follow the authoritative battler/party state");
    TEST_ASSERT(after.ability_id == 16,
                "the previous opponent's ability must not survive the switch");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_battler_state_opponent_switch_follows_authority" ANSI_RESET "\n");
}

/** A faint + forced replacement must not leave the outgoing battler's ability anywhere. */
static void test_hns_battler_state_stale_ability_gone_after_replacement(void) {
    printf("Running test_hns_battler_state_stale_ability_gone_after_replacement...\n");
    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    static FakeGba gba;
    HnsBattleFixture fx;
    hns_battler_fixture_two_battlers(&fx, &gba, cfg);

    // Player active mon faints (hp -> 0, engine keeps the battler until the switch window).
    hns_battle_set_mon(&fx, 0, 155, 0);
    // The engine commits the replacement: same battler index, new party slot, new mon.
    hns_battle_set_battler(&fx, 0, 0, 1);
    hns_battle_set_mon(&fx, 0, 158, 38);
    { const uint8_t types[3] = {PIN_TYPE_GRASS, PIN_TYPE_NONE, PIN_TYPE_NONE};
      hns_battle_set_battler_ability_types(&fx, 0, PIN_ABILITY_OVERGROW, types); }

    BattlerRuntimeState st;
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_PLAYER, &st),
                "the committed replacement battler must be observable");
    TEST_ASSERT(st.party_slot == 1, "the replacement's own party slot must be reported");
    TEST_ASSERT(st.ability_id == PIN_ABILITY_OVERGROW,
                "the fainted mon's ability must be gone, never retained");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_battler_state_stale_ability_gone_after_replacement" ANSI_RESET "\n");
}

/**
 * Player doubles: two present player-side battlers mean neither role may publish an
 * observation. The opponent side was already AMBIGUOUS; the player side must degrade the
 * same way instead of publishing defaulted battler 0 while a partner is also active.
 */
static void test_hns_battler_state_player_doubles_is_ambiguous(void) {
    printf("Running test_hns_battler_state_player_doubles_is_ambiguous...\n");
    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    static FakeGba gba;
    HnsBattleFixture fx;
    pokemon_reader_reset();
    hns_battle_fixture_init(&fx, &gba, cfg);
    hns_battle_fill_player_party(&fx, 2);
    hns_battle_fill_enemy_party(&fx, 2);
    hns_battle_set_in_battle(&fx, true);
    hns_battle_set_counters(&fx, 4, 1u /* BATTLE_TYPE_DOUBLE */, 0);
    gba.ewram[cfg->absent_battler_flags_offset] = 0;
    hns_battle_set_battler(&fx, 0, 0, 0);   // player left  -> player party slot 0
    hns_battle_set_battler(&fx, 1, 1, 0);   // opponent left -> enemy party slot 0
    hns_battle_set_battler(&fx, 2, 2, 1);   // player right  -> player party slot 1
    hns_battle_set_battler(&fx, 3, 3, 1);   // opponent right -> enemy party slot 1
    hns_battle_set_mon(&fx, 0, 155, 50);
    hns_battle_set_mon(&fx, 1, 16, 40);
    hns_battle_set_mon(&fx, 2, 158, 60);
    hns_battle_set_mon(&fx, 3, 19, 35);
    { const uint8_t t[3] = {PIN_TYPE_FIRE, PIN_TYPE_NONE, PIN_TYPE_NONE};
      hns_battle_set_battler_ability_types(&fx, 0, PIN_ABILITY_BLAZE, t); }
    { const uint8_t t[3] = {PIN_TYPE_GRASS, PIN_TYPE_POISON, PIN_TYPE_NONE};
      hns_battle_set_battler_ability_types(&fx, 2, PIN_ABILITY_OVERGROW, t); }

    BattleStateRaw life;
    TEST_ASSERT(pokemon_read_battle_lifecycle(fake_gba_read, &gba.table, gba.ewram,
                                              sizeof(gba.ewram), cfg, &life)
                    == BATTLE_LIFECYCLE_ACTIVE,
                "fixture precondition: the doubles battle must be ACTIVE");

    // The opponent side must stay AMBIGUOUS (unchanged behaviour) ...
    BattlerRuntimeState enemy;
    TEST_ASSERT(!read_battler_state(&fx, BATTLER_ROLE_OPPONENT, &enemy),
                "opponent doubles must not name a first enemy");
    TEST_ASSERT(enemy.status == BATTLER_RUNTIME_STATE_AMBIGUOUS,
                "opponent doubles observation must be AMBIGUOUS");
    TEST_ASSERT(!enemy.ability_observed && enemy.battler_index == -1,
                "an ambiguous opponent observation must carry nothing");

    // ... and the player side must degrade the same way, never publish battler 0.
    BattlerRuntimeState player;
    TEST_ASSERT(!read_battler_state(&fx, BATTLER_ROLE_PLAYER, &player),
                "player doubles must not name defaulted battler 0");
    TEST_ASSERT(player.status == BATTLER_RUNTIME_STATE_AMBIGUOUS,
                "player doubles observation must be AMBIGUOUS, not battler 0");
    TEST_ASSERT(player.battler_index == -1 && player.party_slot == -1 && !player.party_slot_known,
                "an ambiguous player observation must carry no battler or slot");
    TEST_ASSERT(!player.ability_observed && !player.types_observed,
                "an ambiguous player observation must carry no ability or types");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_battler_state_player_doubles_is_ambiguous" ANSI_RESET "\n");
}

/**
 * The real faint/replacement window: while the outgoing battler sits at 0 HP and the
 * replacement has NOT yet been installed, that side's observation must be UNAVAILABLE (no
 * stale ability/types from the fainted mon). Only once the engine commits the replacement
 * (new party slot, rewritten gBattleMons, new words) does the side become observable again.
 * Verified for both sides independently; the alive side keeps observing throughout.
 */
static void test_hns_battler_state_faint_window_unavailable_before_replacement(void) {
    printf("Running test_hns_battler_state_faint_window_unavailable_before_replacement...\n");
    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    static FakeGba gba;
    HnsBattleFixture fx;
    hns_battler_fixture_two_battlers(&fx, &gba, cfg);

    // --- Player faint window: player mon at 0 HP, replacement not yet installed. -------------
    hns_battle_set_mon(&fx, 0, 155, 0);
    BattlerRuntimeState player;
    TEST_ASSERT(!read_battler_state(&fx, BATTLER_ROLE_PLAYER, &player),
                "a fainted player battler before its replacement must not be observable");
    expect_battler_unavailable(&player,
                               "the pre-replacement window must publish no player ability/types");
    // The still-alive opponent is unaffected: the gate is per-battler, not global.
    BattlerRuntimeState enemy;
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_OPPONENT, &enemy),
                "the alive opponent must stay observable during the player's faint window");
    TEST_ASSERT(enemy.status == BATTLER_RUNTIME_STATE_OBSERVED && enemy.ability_id == 51,
                "the opponent observation must be intact while the player mon is fainted");

    // The engine commits the player replacement: new slot, rewritten gBattleMons[0].
    hns_battle_set_battler(&fx, 0, 0, 1);
    hns_battle_set_mon(&fx, 0, 158, 38);
    { const uint8_t t[3] = {PIN_TYPE_GRASS, PIN_TYPE_NONE, PIN_TYPE_NONE};
      hns_battle_set_battler_ability_types(&fx, 0, PIN_ABILITY_OVERGROW, t); }
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_PLAYER, &player),
                "the committed player replacement must be observable");
    TEST_ASSERT(player.party_slot == 1 && player.ability_id == PIN_ABILITY_OVERGROW,
                "after the replacement commits, its own slot and ability are authoritative");

    // --- Opponent faint window: enemy mon at 0 HP, replacement not yet installed. ------------
    hns_battle_set_mon(&fx, 1, 16, 0);
    TEST_ASSERT(!read_battler_state(&fx, BATTLER_ROLE_OPPONENT, &enemy),
                "a fainted opponent before its replacement must not be observable");
    expect_battler_unavailable(&enemy,
                               "the pre-replacement window must publish no enemy ability/types");
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_PLAYER, &player),
                "the alive player must stay observable during the opponent's faint window");
    TEST_ASSERT(player.ability_id == PIN_ABILITY_OVERGROW,
                "the player observation must be intact while the enemy mon is fainted");

    // The engine commits the opponent replacement: new slot, rewritten gBattleMons[1].
    hns_battle_set_battler(&fx, 1, 1, 1);
    hns_battle_set_mon(&fx, 1, 21, 35);
    { const uint8_t t[3] = {PIN_TYPE_NORMAL, PIN_TYPE_FLYING, PIN_TYPE_NONE};
      hns_battle_set_battler_ability_types(&fx, 1, 16 /* PINNED: *not* the old ability */, t); }
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_OPPONENT, &enemy),
                "the committed opponent replacement must be observable");
    TEST_ASSERT(enemy.party_slot == 1 && enemy.ability_id == 16,
                "after the replacement commits, its own slot and ability are authoritative");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_battler_state_faint_window_unavailable_before_replacement" ANSI_RESET "\n");
}

/**
 * An ability ID outside the pinned catalogue domain stays explicit: reported
 * raw, flagged, never substituted with slot 0 / the party's abilityNum / the
 * first declared ability.
 */
static void test_hns_battler_state_unresolved_ability_stays_raw(void) {
    printf("Running test_hns_battler_state_unresolved_ability_stays_raw...\n");
    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    static FakeGba gba;
    HnsBattleFixture fx;
    hns_battler_fixture_two_battlers(&fx, &gba, cfg);

    // An ID the pinned source never assigns (ABILITIES_COUNT == 311, max ID 310).
    { const uint8_t types[3] = {PIN_TYPE_FIRE, PIN_TYPE_NONE, PIN_TYPE_NONE};
      hns_battle_set_battler_ability_types(&fx, 0, PIN_BATTLE_POKEMON_ABILITY_ID_MAX + 7, types); }

    BattlerRuntimeState st;
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_PLAYER, &st),
                "an out-of-domain ability is still an observation");
    TEST_ASSERT(st.species_observed && st.species_id == 155,
                "current BattlePokemon species must be observed from the live battler record");
    TEST_ASSERT(st.status == BATTLER_RUNTIME_STATE_OBSERVED_INVALID,
                "the status must degrade honestly, not silently pass");
    TEST_ASSERT(st.ability_observed && st.ability_invalid &&
                st.ability_id == PIN_BATTLE_POKEMON_ABILITY_ID_MAX + 7,
                "the raw observation must be preserved verbatim");
    TEST_ASSERT(!st.types_invalid && st.types[0] == PIN_TYPE_FIRE,
                "the types must still be observed normally");

    // ABILITY_NONE (0) is part of the pinned enum: a legitimate observed state,
    // preserved explicitly and never substituted.
    hns_battle_set_battler_ability_types(&fx, 0, 0, (const uint8_t[3]){PIN_TYPE_FIRE, 0, 0});
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_PLAYER, &st),
                "ABILITY_NONE is in the pinned domain");
    TEST_ASSERT(st.status == BATTLER_RUNTIME_STATE_OBSERVED &&
                st.ability_id == 0 && !st.ability_invalid,
                "ABILITY_NONE must be reported as observed zero, never substituted");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_battler_state_unresolved_ability_stays_raw" ANSI_RESET "\n");
}

/** Current type representation: dual, monotype sentinels, duplicates, invalid encoding. */
static void test_hns_battler_state_type_representations(void) {
    printf("Running test_hns_battler_state_type_representations...\n");
    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    static FakeGba gba;
    HnsBattleFixture fx;
    hns_battler_fixture_two_battlers(&fx, &gba, cfg);

    BattlerRuntimeState st;

    // Duplicate slots are meaningful and preserved (e.g. the Tera shape).
    { const uint8_t types[3] = {PIN_TYPE_ELECTRIC, PIN_TYPE_ELECTRIC, PIN_TYPE_ELECTRIC};
      hns_battle_set_battler_ability_types(&fx, 0, PIN_ABILITY_BLAZE, types); }
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_PLAYER, &st), "duplicates must read");
    TEST_ASSERT(st.types[0] == PIN_TYPE_ELECTRIC && st.types[1] == PIN_TYPE_ELECTRIC &&
                st.types[2] == PIN_TYPE_ELECTRIC && st.status == BATTLER_RUNTIME_STATE_OBSERVED,
                "duplicate slots must be exposed verbatim, never de-duplicated");

    // TYPE_MYSTERY is a battle-only value and must survive verbatim.
    { const uint8_t types[3] = {PIN_TYPE_MYSTERY, PIN_TYPE_MYSTERY, PIN_TYPE_MYSTERY};
      hns_battle_set_battler_ability_types(&fx, 0, PIN_ABILITY_BLAZE, types); }
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_PLAYER, &st), "MYSTERY must read");
    TEST_ASSERT(st.types[0] == PIN_TYPE_MYSTERY && !st.types_invalid,
                "the battle-only typeless value must be preserved, never mapped to Normal");

    // An encoding the pinned source never assigns degrades honestly.
    { const uint8_t types[3] = {PIN_TYPE_FIRE, PIN_BATTLE_POKEMON_TYPE_ID_MAX + 1, PIN_TYPE_NONE};
      hns_battle_set_battler_ability_types(&fx, 0, PIN_ABILITY_BLAZE, types); }
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_PLAYER, &st),
                "an invalid type encoding is still an observation");
    TEST_ASSERT(st.status == BATTLER_RUNTIME_STATE_OBSERVED_INVALID && st.types_invalid,
                "the invalid encoding must be flagged");
    TEST_ASSERT(st.types[1] == PIN_BATTLE_POKEMON_TYPE_ID_MAX + 1,
                "the raw value must be preserved, never coerced to a valid type");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_battler_state_type_representations" ANSI_RESET "\n");
}

/**
 * `ITEM_NONE` (0) is an authoritative observation, not a gap: the engine word is
 * decoded, flagged valid, and preserved as zero.
 */
static void test_hns_battler_state_item_none_is_observed_zero(void) {
    printf("Running test_hns_battler_state_item_none_is_observed_zero...\n");
    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    static FakeGba gba;
    HnsBattleFixture fx;
    hns_battler_fixture_two_battlers(&fx, &gba, cfg);
    hns_battle_set_battler_item(&fx, 0, PIN_ITEM_NONE);

    BattlerRuntimeState st;
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_PLAYER, &st), "ITEM_NONE must be observable");
    TEST_ASSERT(st.status == BATTLER_RUNTIME_STATE_OBSERVED, "ITEM_NONE is inside the domain");
    TEST_ASSERT(st.item_observed && !st.item_invalid && st.item_id == PIN_ITEM_NONE,
                "ITEM_NONE must be reported as observed zero, never as unreadable");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_battler_state_item_none_is_observed_zero" ANSI_RESET "\n");
}

/**
 * The current battle item follows the authoritative battler: a switch/consume rewrite
 * must replace the old item, and an engine ITEM_NONE must never retain the old value.
 */
static void test_hns_battler_state_current_item_follows_rewrite(void) {
    printf("Running test_hns_battler_state_current_item_follows_rewrite...\n");
    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    static FakeGba gba;
    HnsBattleFixture fx;
    hns_battler_fixture_two_battlers(&fx, &gba, cfg);

    BattlerRuntimeState before;
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_PLAYER, &before),
                "precondition: initial player observation");
    TEST_ASSERT(before.item_id == PIN_ITEM_CHARCOAL, "precondition: initial item");

    // The engine commits a switch and consumes the new battler's item in the same window.
    hns_battle_set_battler(&fx, 0, 0, 1);
    hns_battle_set_mon(&fx, 0, 158, 38);
    { const uint8_t types[3] = {PIN_TYPE_GRASS, PIN_TYPE_POISON, PIN_TYPE_NONE};
      hns_battle_set_battler_ability_types(&fx, 0, PIN_ABILITY_OVERGROW, types); }
    hns_battle_set_battler_item(&fx, 0, PIN_ITEM_NONE);

    BattlerRuntimeState after;
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_PLAYER, &after),
                "post-switch player must be observable");
    TEST_ASSERT(after.item_observed && after.item_id == PIN_ITEM_NONE,
                "the previous battler's item must not survive; ITEM_NONE is the current truth");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_battler_state_current_item_follows_rewrite" ANSI_RESET "\n");
}

/** An item ID outside ITEMS_COUNT is reported raw and flagged, never coerced. */
static void test_hns_battler_state_out_of_domain_item_stays_raw(void) {
    printf("Running test_hns_battler_state_out_of_domain_item_stays_raw...\n");
    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    static FakeGba gba;
    HnsBattleFixture fx;
    hns_battler_fixture_two_battlers(&fx, &gba, cfg);
    hns_battle_set_battler_item(&fx, 0, PIN_ITEM_OUT_OF_DOMAIN);

    BattlerRuntimeState st;
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_PLAYER, &st),
                "an out-of-domain item is still an observation");
    TEST_ASSERT(st.status == BATTLER_RUNTIME_STATE_OBSERVED_INVALID,
                "the status must degrade honestly, not silently pass");
    TEST_ASSERT(st.item_observed && st.item_invalid && st.item_id == PIN_ITEM_OUT_OF_DOMAIN,
                "the raw item value must be preserved verbatim, never coerced");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_battler_state_out_of_domain_item_stays_raw" ANSI_RESET "\n");
}

/** Trust and lifecycle failure matrix: no path may default to battler 0/slot 0 or retain state. */
static void test_hns_battler_state_trust_and_lifecycle_failures(void) {
    printf("Running test_hns_battler_state_trust_and_lifecycle_failures...\n");
    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    const GameMemoryConfig* fr = pokemon_get_game_config(GAME_FIRERED);
    static FakeGba gba;
    HnsBattleFixture fx;
    hns_battler_fixture_two_battlers(&fx, &gba, cfg);

    BattlerRuntimeState st;

    // Wrong game (recognized but not the exact trusted layout for this surface).
    TEST_ASSERT(!pokemon_read_battler_runtime_state_gba(
                    fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram), fr,
                    BATTLER_ROLE_PLAYER, &st),
                "a vanilla layout must never reinterpret its BattlePokemon through H&S");
    expect_battler_unavailable(&st, "wrong game must produce a clean UNAVAILABLE state");

    // Inactive battle: the engine's own gate is false.
    hns_battle_set_in_battle(&fx, false);
    TEST_ASSERT(!read_battler_state(&fx, BATTLER_ROLE_PLAYER, &st),
                "an inactive battle has no live battler state");
    expect_battler_unavailable(&st, "inactive battle must produce UNAVAILABLE");
    hns_battle_set_in_battle(&fx, true);

    // Initializing battle: the controllers are not authoritative yet.
    hns_battle_set_counters(&fx, 0, 0u, 0);
    TEST_ASSERT(!read_battler_state(&fx, BATTLER_ROLE_PLAYER, &st),
                "an initializing battle must not publish an observation");
    expect_battler_unavailable(&st, "initializing battle must produce UNAVAILABLE");

    // Ending battle: the outcome is already recorded.
    hns_battle_set_counters(&fx, 2, 0u, 1);
    TEST_ASSERT(!read_battler_state(&fx, BATTLER_ROLE_PLAYER, &st),
                "an ending battle must not publish an observation");
    expect_battler_unavailable(&st, "ending battle must produce UNAVAILABLE");
    hns_battle_set_counters(&fx, 2, 0u, 0);

    // Unknown lifecycle: the gate is unreadable (no IWRAM).
    static FakeGba no_iwram;
    fake_gba_init(&no_iwram, false, true);
    memcpy(no_iwram.ewram, gba.ewram, sizeof(gba.ewram));
    TEST_ASSERT(!pokemon_read_battler_runtime_state_gba(
                    fake_gba_read, &no_iwram.table, no_iwram.ewram, sizeof(no_iwram.ewram),
                    cfg, BATTLER_ROLE_PLAYER, &st),
                "an unreadable lifecycle gate must degrade to UNKNOWN");
    expect_battler_unavailable(&st, "unknown lifecycle must produce UNAVAILABLE");

    // Unreadable gBattleMons: EWRAM mapped only up to the gBattleMons base.
    static FakeGba truncated;
    fake_gba_init(&truncated, true, false);
    gba_memory_map_clear(&truncated.table);
    gba_memory_map_add(&truncated.table, truncated.iwram, 0x03000000u, 0x8000u, 0xFF000000u, 0u, 0u, 0u);
    gba_memory_map_add(&truncated.table, truncated.ewram, 0x02000000u, cfg->battle_mons_offset, 0xFF000000u, 0u, 0u, 0u);
    memcpy(truncated.ewram, gba.ewram, cfg->battle_mons_offset);
    TEST_ASSERT(!pokemon_read_battler_runtime_state_gba(
                    fake_gba_read, &truncated.table, truncated.ewram, sizeof(truncated.ewram),
                    cfg, BATTLER_ROLE_PLAYER, &st),
                "an unreadable gBattleMons must fail closed");
    expect_battler_unavailable(&st, "unreadable BattlePokemon must produce UNAVAILABLE");

    // Absent battler: the engine has flagged the player's battler as absent.
    gba.ewram[cfg->absent_battler_flags_offset] = 0x01;
    TEST_ASSERT(!read_battler_state(&fx, BATTLER_ROLE_PLAYER, &st),
                "an absent battler is not a live battler");
    expect_battler_unavailable(&st, "absent battler must produce UNAVAILABLE");
    gba.ewram[cfg->absent_battler_flags_offset] = 0;

    // Doubles: two opponent battlers are present, so a single-opponent surface
    // must report AMBIGUOUS and observe nothing.
    hns_battle_set_counters(&fx, 4, 1u /* BATTLE_TYPE_DOUBLE */, 0);
    hns_battle_set_battler(&fx, 2, 2, 1);
    hns_battle_set_battler(&fx, 3, 3, 1);
    hns_battle_set_mon(&fx, 2, 19, 40);
    hns_battle_set_mon(&fx, 3, 25, 40);
    TEST_ASSERT(!read_battler_state(&fx, BATTLER_ROLE_OPPONENT, &st),
                "doubles must not name a first enemy");
    TEST_ASSERT(st.status == BATTLER_RUNTIME_STATE_AMBIGUOUS,
                "multi-opponent observation must be explicitly AMBIGUOUS");
    TEST_ASSERT(!st.ability_observed && st.battler_index == -1,
                "an ambiguous observation must carry no ability or battler");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_battler_state_trust_and_lifecycle_failures" ANSI_RESET "\n");
}

/** Battle teardown clears the observation; nothing survives into the overworld or a profile switch. */
static void test_hns_battler_state_teardown_and_profile_switch(void) {
    printf("Running test_hns_battler_state_teardown_and_profile_switch...\n");
    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    const GameMemoryConfig* fr = pokemon_get_game_config(GAME_FIRERED);
    static FakeGba gba;
    HnsBattleFixture fx;
    hns_battler_fixture_two_battlers(&fx, &gba, cfg);

    BattlerRuntimeState st;
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_PLAYER, &st),
                "precondition: active observation");
    TEST_ASSERT(st.ability_id == PIN_ABILITY_BLAZE,
                "precondition: observed ability");

    // The battle engine tears down: gMain.inBattle clears while the stale
    // gBattleMons words remain in EWRAM. Nothing may be published from them.
    hns_battle_set_in_battle(&fx, false);
    TEST_ASSERT(!read_battler_state(&fx, BATTLER_ROLE_PLAYER, &st),
                "teardown must stop the observation");
    expect_battler_unavailable(&st, "after teardown no stale ability may survive");

    // A ROM/profile switch: the same memory through another game's config.
    hns_battle_set_in_battle(&fx, true);
    TEST_ASSERT(!pokemon_read_battler_runtime_state_gba(
                    fake_gba_read, &gba.table, gba.ewram, sizeof(gba.ewram), fr,
                    BATTLER_ROLE_PLAYER, &st),
                "after a profile switch the previous observation must not be served");
    expect_battler_unavailable(&st, "profile switch must produce UNAVAILABLE");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_battler_state_teardown_and_profile_switch" ANSI_RESET "\n");
}

static void test_hns_badge_state_reading(void) {
    printf("Running test_hns_badge_state_reading...\n");
    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    const GameMemoryConfig* fr = pokemon_get_game_config(GAME_FIRERED);
    static FakeGba gba;
    HnsBattleFixture fx;
    hns_battle_fixture_init(&fx, &gba, cfg);

    HnsBadgeState badges;
    memset(&badges, 0, sizeof(badges));

    // Null checks
    TEST_ASSERT(!pokemon_read_hns_badge_state_gba(NULL, &gba.table, cfg, &badges),
                "null reader must fail");
    TEST_ASSERT(!pokemon_read_hns_badge_state_gba(fake_gba_read, &gba.table, NULL, &badges),
                "null config must fail");
    TEST_ASSERT(!pokemon_read_hns_badge_state_gba(fake_gba_read, &gba.table, cfg, NULL),
                "null out must fail");
    TEST_ASSERT(!pokemon_read_hns_badge_state_gba(fake_gba_read, &gba.table, fr, &badges),
                "non-H&S config must fail");

    /*
     * Valid reading: set all four badge classes via the corrected H&S bit layout.
     *
     * Pinned upstream source (pokehns-expansion commit 1f42b74):
     *   SYSTEM_FLAGS = 0x860
     *   FLAG_BADGE01_GET (Atk)    = 0x867 -> byte0 (SaveBlock1+0x1A98), bit 7
     *   FLAG_BADGE03_GET (Spe)    = 0x869 -> byte1 (SaveBlock1+0x1A99), bit 1
     *   FLAG_BADGE06_GET (Def)    = 0x86C -> byte1 (SaveBlock1+0x1A99), bit 4
     *   FLAG_BADGE07_GET (SpA+SpD)= 0x86D -> byte1 (SaveBlock1+0x1A99), bit 5
     *
     * hns_battle_set_badges writes:
     *   byte0 = 0x80  (bit 7 = Atk)
     *   byte1 = 0x32  (bit 1 = Spe, bit 4 = Def, bit 5 = SpA/SpD)
     */
    hns_battle_set_badges(&fx, /*atk*/true, /*spe*/true, /*def*/true, /*spa*/true);
    TEST_ASSERT(pokemon_read_hns_badge_state_gba(fake_gba_read, &gba.table, cfg, &badges),
                "valid badge read must succeed");
    TEST_ASSERT(badges.raw_badges_byte == 0x80, "raw_badges_byte must be byte0 = 0x80 (Atk bit 7)");
    TEST_ASSERT(badges.badge_atk, "Falkner Zephyr badge must boost Atk");
    TEST_ASSERT(badges.badge_spe, "Whitney Plain badge must boost Spe");
    TEST_ASSERT(badges.badge_def, "Jasmine Mineral badge must boost Def");
    TEST_ASSERT(badges.badge_spa, "Pryce Glacier badge must boost SpA");
    TEST_ASSERT(badges.badge_spd, "Pryce Glacier badge must boost SpD");

    /* Partial: only Atk badge (byte0 only) */
    hns_battle_set_badges(&fx, /*atk*/true, /*spe*/false, /*def*/false, /*spa*/false);
    TEST_ASSERT(pokemon_read_hns_badge_state_gba(fake_gba_read, &gba.table, cfg, &badges),
                "partial badge read (Atk only) must succeed");
    TEST_ASSERT(badges.badge_atk,  "Atk badge set, must be true");
    TEST_ASSERT(!badges.badge_spe, "Spe badge not set, must be false");
    TEST_ASSERT(!badges.badge_def, "Def badge not set, must be false");
    TEST_ASSERT(!badges.badge_spa, "SpA badge not set, must be false");
    TEST_ASSERT(!badges.badge_spd, "SpD badge not set, must be false");

    /* Partial: only Def+Spe badges (byte1 only) */
    hns_battle_set_badges(&fx, /*atk*/false, /*spe*/true, /*def*/true, /*spa*/false);
    TEST_ASSERT(pokemon_read_hns_badge_state_gba(fake_gba_read, &gba.table, cfg, &badges),
                "partial badge read (Spe+Def) must succeed");
    TEST_ASSERT(!badges.badge_atk, "Atk badge not set, must be false");
    TEST_ASSERT(badges.badge_spe,  "Spe badge set, must be true");
    TEST_ASSERT(badges.badge_def,  "Def badge set, must be true");
    TEST_ASSERT(!badges.badge_spa, "SpA badge not set, must be false");

    /* No badges: both bytes zeroed */
    hns_battle_set_badges(&fx, /*atk*/false, /*spe*/false, /*def*/false, /*spa*/false);
    TEST_ASSERT(pokemon_read_hns_badge_state_gba(fake_gba_read, &gba.table, cfg, &badges),
                "no-badge read must succeed");
    TEST_ASSERT(!badges.badge_atk && !badges.badge_spe && !badges.badge_def &&
                !badges.badge_spa && !badges.badge_spd,
                "no badges set: all boost flags must be false");

    // Invalid pointer fails closed
    write32_le_t(gba.iwram + (cfg->save_block1_ptr_gba_address - 0x03000000u), 0x02000000u); // below sb1 base
    TEST_ASSERT(!pokemon_read_hns_badge_state_gba(fake_gba_read, &gba.table, cfg, &badges),
                "corrupt sb1 pointer must fail closed");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_badge_state_reading" ANSI_RESET "\n");
}

static void test_hns_battler_state_stats_stages_badges(void) {
    printf("Running test_hns_battler_state_stats_stages_badges...\n");
    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    static FakeGba gba;
    HnsBattleFixture fx;
    hns_battler_fixture_two_battlers(&fx, &gba, cfg);

    // Player (battler 0) stats and stages:
    hns_battle_set_battler_stats(&fx, 0, 120, 95, 110, 85, 90);
    uint8_t stages_player[8] = {6, 8, 5, 6, 7, 4, 6, 6}; // atk +2, def -1, spa +1, spd -2
    hns_battle_set_battler_stat_stages(&fx, 0, stages_player);
    hns_battle_set_badges(&fx, /*atk*/true, /*spe*/true, /*def*/true, /*spa*/true); // all boost badges active

    // Opponent (battler 1) stats and stages:
    hns_battle_set_battler_stats(&fx, 1, 80, 70, 90, 60, 65);
    uint8_t stages_enemy[8] = {6, 6, 7, 6, 6, 6, 6, 6}; // def +1
    hns_battle_set_battler_stat_stages(&fx, 1, stages_enemy);

    BattlerRuntimeState st_player;
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_PLAYER, &st_player), "player read must succeed");
    TEST_ASSERT(st_player.stats_observed, "player stats must be observed");
    TEST_ASSERT(st_player.raw_attack == 120, "player raw attack must be 120");
    TEST_ASSERT(st_player.raw_defense == 95, "player raw defense must be 95");
    TEST_ASSERT(st_player.raw_speed == 110, "player raw speed must be 110");
    TEST_ASSERT(st_player.raw_sp_attack == 85, "player raw sp_attack must be 85");
    TEST_ASSERT(st_player.raw_sp_defense == 90, "player raw sp_defense must be 90");

    TEST_ASSERT(st_player.stages_observed, "player stages must be observed");
    TEST_ASSERT(!st_player.stages_invalid, "player stages must all be in-domain");
    TEST_ASSERT(st_player.stat_stages[1] == 2, "player atk stage must be +2");
    TEST_ASSERT(st_player.stat_stages[2] == -1, "player def stage must be -1");
    TEST_ASSERT(st_player.stat_stages[3] == 0, "player spe stage must be 0");
    TEST_ASSERT(st_player.stat_stages[4] == 1, "player spa stage must be +1");
    TEST_ASSERT(st_player.stat_stages[5] == -2, "player spd stage must be -2");

    TEST_ASSERT(st_player.badges_observed, "player badges must be observed");
    /* raw_badges_byte is byte0 (SaveBlock1+0x1A98): bit 7 = Atk = 0x80 */
    TEST_ASSERT(st_player.raw_badges_byte == 0x80, "player raw badges byte (byte0) must be 0x80");
    TEST_ASSERT(st_player.badge_boost_atk, "player atk badge boost must be active");
    TEST_ASSERT(st_player.badge_boost_def, "player def badge boost must be active");
    TEST_ASSERT(st_player.badge_boost_spe, "player spe badge boost must be active");
    TEST_ASSERT(st_player.badge_boost_spa, "player spa badge boost must be active");
    TEST_ASSERT(st_player.badge_boost_spd, "player spd badge boost must be active");

    // Opponent read:
    BattlerRuntimeState st_enemy;
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_OPPONENT, &st_enemy), "enemy read must succeed");
    TEST_ASSERT(st_enemy.stats_observed, "enemy stats must be observed");
    TEST_ASSERT(st_enemy.raw_attack == 80, "enemy raw attack must be 80");
    TEST_ASSERT(st_enemy.raw_defense == 70, "enemy raw defense must be 70");
    TEST_ASSERT(st_enemy.raw_speed == 90, "enemy raw speed must be 90");
    TEST_ASSERT(st_enemy.raw_sp_attack == 60, "enemy raw sp_attack must be 60");
    TEST_ASSERT(st_enemy.raw_sp_defense == 65, "enemy raw sp_defense must be 65");

    TEST_ASSERT(st_enemy.stages_observed, "enemy stages must be observed");
    TEST_ASSERT(st_enemy.stat_stages[2] == 1, "enemy def stage must be +1");

    // Opponents NEVER get badge boosts!
    TEST_ASSERT(!st_enemy.badges_observed, "enemy must not have badges observed");
    TEST_ASSERT(!st_enemy.badge_boost_atk && !st_enemy.badge_boost_def && !st_enemy.badge_boost_spe &&
                !st_enemy.badge_boost_spa && !st_enemy.badge_boost_spd,
                "enemy must never receive badge boosts");

    // Badge exclusions on player: Link battle (0x02)
    hns_battle_set_counters(&fx, 2, 0x02u, 0); // BATTLE_TYPE_LINK
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_PLAYER, &st_player), "player read in link battle");
    TEST_ASSERT(st_player.badges_observed, "badges are still read from save block");
    TEST_ASSERT(st_player.raw_badges_byte == 0x80, "raw badges byte (byte0) still observed as 0x80");
    TEST_ASSERT(!st_player.badge_boost_atk && !st_player.badge_boost_def && !st_player.badge_boost_spe &&
                !st_player.badge_boost_spa && !st_player.badge_boost_spd,
                "badge boosts must be disabled in link battle");

    /* Out-of-domain stat stage: raw stage byte 255 (> 12) must set stages_invalid and
     * produce OBSERVED_INVALID. The reader must not coerce it to +6. */
    hns_battle_set_counters(&fx, 2, 0x00u, 0); // back to non-link
    uint8_t stages_oob[8] = {6, 255, 5, 6, 7, 4, 6, 6}; /* atk stage = 255: out of domain */
    hns_battle_set_battler_stat_stages(&fx, 0, stages_oob);
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_PLAYER, &st_player), "OOB stage read must not crash");
    TEST_ASSERT(st_player.stages_invalid, "OOB stage byte 255 must set stages_invalid");
    TEST_ASSERT(st_player.status == BATTLER_RUNTIME_STATE_OBSERVED_INVALID,
                "OOB stage must produce OBSERVED_INVALID status");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_battler_state_stats_stages_badges" ANSI_RESET "\n");
}

/* ---- Gap C4e: live HP/status/volatile/gimmick operands ---- */

/** Write the live HP / maxHP pair for one battler. */
static void hns_battle_set_battler_hp(HnsBattleFixture* fx, uint8_t battler,
                                      uint16_t hp, uint16_t max_hp) {
    uint8_t* mon = fx->gba->ewram + fx->cfg->battle_mons_offset +
                   ((size_t)battler * fx->cfg->battle_mons_size);
    write16_le_t(mon + fx->cfg->battle_mons_hp_offset, hp);
    write16_le_t(mon + fx->cfg->battle_mons_max_hp_offset, max_hp);
}

/** Write the live status1 word for one battler. */
static void hns_battle_set_battler_status(HnsBattleFixture* fx, uint8_t battler, uint32_t status1) {
    uint8_t* mon = fx->gba->ewram + fx->cfg->battle_mons_offset +
                   ((size_t)battler * fx->cfg->battle_mons_size);
    write32_le_t(mon + fx->cfg->battle_mons_status_offset, status1);
}

/** Set one volatile bit at its compiled position within `volatiles`. */
static void hns_battle_set_volatile_bit(HnsBattleFixture* fx, uint8_t battler,
                                        uint32_t bit, bool value) {
    uint8_t* mon = fx->gba->ewram + fx->cfg->battle_mons_offset +
                   ((size_t)battler * fx->cfg->battle_mons_size);
    uint8_t* byte = mon + fx->cfg->battle_mons_volatiles_offset + (bit / 8);
    uint8_t mask = (uint8_t)(1u << (bit % 8));
    if (value) *byte |= mask; else *byte &= (uint8_t)~mask;
}

/** Write a multi-bit volatile field at its compiled position within `volatiles`. */
static void hns_battle_set_volatile_field(HnsBattleFixture* fx, uint8_t battler,
                                          uint32_t bit, uint32_t width, uint32_t value) {
    for (uint32_t i = 0; i < width; i++) {
        hns_battle_set_volatile_bit(fx, battler, bit + i, ((value >> i) & 1u) != 0);
    }
}

/** Point gBattleStruct at an in-EWRAM buffer and write one active gimmick byte. */
static void hns_battle_set_gimmick(HnsBattleFixture* fx, uint8_t battler, uint8_t gimmick) {
    const uint32_t bs_base = 0x02030000u; /* inside the fake EWRAM window */
    write32_le_t(fx->gba->ewram + fx->cfg->battle_struct_ptr_offset, bs_base);
    const uint32_t side = (battler == 0) ? 0u : 1u; /* player side 0, opponent side 1 */
    const uint16_t party_slot = 0;
    uint32_t off = fx->cfg->battle_struct_gimmick_offset +
                   fx->cfg->battle_gimmick_active_offset +
                   side * fx->cfg->battle_gimmick_side_stride + party_slot;
    fx->gba->ewram[(bs_base - 0x02000000u) + off] = gimmick;
}

/* The live `gFieldStatuses` reader must preserve the raw battle-global word: a readable zero
 * stays an observed 0 (never "unread"), every pinned bit and any unexpected high bit survive
 * unchanged, nothing is masked, and both battle-level observations report the same word. */
static void test_hns_field_statuses_raw_word(void) {
    printf("Running test_hns_field_statuses_raw_word...\n");
    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    static FakeGba gba;
    HnsBattleFixture fx;
    hns_battler_fixture_two_battlers(&fx, &gba, cfg);

    TEST_ASSERT(cfg->field_statuses_offset == 0x2F4, "gFieldStatuses stays at EWRAM+0x2F4");
    TEST_ASSERT(cfg->field_status_ion_deluge_mask == HNS_STATUS_FIELD_ION_DELUGE,
                "Ion Deluge mask comes from the generated pinned header");
    TEST_ASSERT(HNS_STATUS_FIELD_KNOWN_MASK == 0x00000FFFu, "twelve pinned field bits");
    TEST_ASSERT(HNS_STATUS_FIELD_TERRAIN_ANY == 0x000003C0u, "terrain composition bits 6..9");

    const uint32_t words[] = {
        0u,
        HNS_STATUS_FIELD_MAGIC_ROOM, HNS_STATUS_FIELD_TRICK_ROOM, HNS_STATUS_FIELD_WONDER_ROOM,
        HNS_STATUS_FIELD_MUDSPORT, HNS_STATUS_FIELD_WATERSPORT, HNS_STATUS_FIELD_GRAVITY,
        HNS_STATUS_FIELD_GRASSY_TERRAIN, HNS_STATUS_FIELD_MISTY_TERRAIN,
        HNS_STATUS_FIELD_ELECTRIC_TERRAIN, HNS_STATUS_FIELD_PSYCHIC_TERRAIN,
        HNS_STATUS_FIELD_ION_DELUGE, HNS_STATUS_FIELD_FAIRY_LOCK,
        HNS_STATUS_FIELD_TRICK_ROOM | HNS_STATUS_FIELD_ELECTRIC_TERRAIN,
        HNS_STATUS_FIELD_KNOWN_MASK,
        0x00002000u,                                   /* first bit above the pinned mask */
        0x80000000u | HNS_STATUS_FIELD_ELECTRIC_TERRAIN, /* high bit survives the u32 read */
        0xFFFFFFFFu
    };
    for (size_t i = 0; i < sizeof(words) / sizeof(words[0]); i++) {
        write32_le_t(gba.ewram + cfg->field_statuses_offset, words[i]);
        BattlerRuntimeState player, enemy;
        TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_PLAYER, &player), "player read must succeed");
        TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_OPPONENT, &enemy), "enemy read must succeed");
        TEST_ASSERT(player.field_statuses_readable && enemy.field_statuses_readable,
                    "field word is readable on both observations (0 included)");
        TEST_ASSERT(player.field_statuses == words[i], "player word is the raw word, unmasked");
        TEST_ASSERT(enemy.field_statuses == words[i], "enemy word is the raw word, unmasked");
        TEST_ASSERT(player.field_statuses == enemy.field_statuses, "battle-global word agrees");
    }

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_field_statuses_raw_word" ANSI_RESET "\n");
}

static void test_hns_battler_state_c4e_live_operands(void) {
    printf("Running test_hns_battler_state_c4e_live_operands...\n");
    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    static FakeGba gba;
    HnsBattleFixture fx;
    hns_battler_fixture_two_battlers(&fx, &gba, cfg);

    hns_battle_set_battler_hp(&fx, 0, 14, 20);
    hns_battle_set_battler_status(&fx, 0, 0);
    hns_battle_set_volatile_bit(&fx, 0, HNS_LIVE_BP_VOLATILE_ELECTRIFIED_BIT, false);
    hns_battle_set_volatile_bit(&fx, 1, HNS_LIVE_BP_VOLATILE_GLAIVE_RUSH_BIT, false);
    hns_battle_set_battler_hp(&fx, 1, 15, 15);
    hns_battle_set_battler_status(&fx, 1, 0);
    hns_battle_set_gimmick(&fx, 0, 0);
    hns_battle_set_gimmick(&fx, 1, 0);
    write32_le_t(gba.ewram + cfg->field_statuses_offset, 0);

    BattlerRuntimeState st;
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_PLAYER, &st), "player read must succeed");
    TEST_ASSERT(st.hp_observed && st.hp == 14 && st.max_hp == 20,
                "player live HP/maxHP must be observed");
    TEST_ASSERT(st.status_observed && st.status1 == 0,
                "player live status must be observed neutral");
    TEST_ASSERT(st.volatiles_observed && !st.volatile_electrified,
                "player electrified must be observed false");
    TEST_ASSERT(st.volatile_charge_timer == 0 && !st.volatile_tar_shot,
                "player chargeTimer/tarShot must be observed neutral");
    TEST_ASSERT(st.gimmick_observed && st.active_gimmick == 0,
                "player gimmick must be observed NONE");
    TEST_ASSERT(st.field_statuses_readable && st.field_statuses == 0,
                "field statuses must be observed neutral");

    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_OPPONENT, &st), "enemy read must succeed");
    TEST_ASSERT(st.hp_observed && st.hp == 15 && st.max_hp == 15, "enemy HP/maxHP observed");
    TEST_ASSERT(st.volatiles_observed && !st.volatile_glaive_rush, "enemy Glaive Rush false");
    TEST_ASSERT(st.gimmick_observed && st.active_gimmick == 0, "enemy gimmick NONE");

    /* Positive transitions: each bit is independently readable. */
    hns_battle_set_volatile_bit(&fx, 0, HNS_LIVE_BP_VOLATILE_ELECTRIFIED_BIT, true);
    hns_battle_set_volatile_bit(&fx, 1, HNS_LIVE_BP_VOLATILE_GLAIVE_RUSH_BIT, true);
    hns_battle_set_volatile_field(&fx, 0, HNS_LIVE_BP_VOLATILE_CHARGE_TIMER_BIT,
                                  HNS_LIVE_BP_VOLATILE_CHARGE_TIMER_WIDTH, 3);
    hns_battle_set_volatile_bit(&fx, 1, HNS_LIVE_BP_VOLATILE_TAR_SHOT_BIT, true);
    hns_battle_set_gimmick(&fx, 0, 5 /* GIMMICK_TERA */);
    write32_le_t(gba.ewram + cfg->field_statuses_offset, 1u << 10 /* ION_DELUGE */);
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_PLAYER, &st), "player transition read");
    TEST_ASSERT(st.volatile_electrified, "player electrified must be observed true");
    TEST_ASSERT(st.volatile_charge_timer == 3,
                "player chargeTimer must be observed at its width-2 maximum");
    TEST_ASSERT(st.active_gimmick == 5, "player gimmick must be observed TERA");
    TEST_ASSERT(st.field_statuses == (1u << 10), "Ion Deluge bit must be observed");
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_OPPONENT, &st), "enemy transition read");
    TEST_ASSERT(st.volatile_glaive_rush, "enemy Glaive Rush must be observed true");
    TEST_ASSERT(st.volatile_tar_shot, "enemy Tar Shot must be observed true");

    /* Review round 4: each persistent damage-path volatile is independently readable at its
     * generated bit. Player carries the type/grounding/ability/roost states; enemy carries the
     * GetAdjustedDamage states. */
    hns_battle_set_volatile_bit(&fx, 0, HNS_LIVE_BP_VOLATILE_FORESIGHT_BIT, true);
    hns_battle_set_volatile_bit(&fx, 0, HNS_LIVE_BP_VOLATILE_MIRACLE_EYE_BIT, true);
    hns_battle_set_volatile_bit(&fx, 0, HNS_LIVE_BP_VOLATILE_ROOT_BIT, true);
    hns_battle_set_volatile_bit(&fx, 0, HNS_LIVE_BP_VOLATILE_SMACK_DOWN_BIT, true);
    hns_battle_set_volatile_bit(&fx, 0, HNS_LIVE_BP_VOLATILE_TELEKINESIS_BIT, true);
    hns_battle_set_volatile_bit(&fx, 0, HNS_LIVE_BP_VOLATILE_MAGNET_RISE_BIT, true);
    hns_battle_set_volatile_bit(&fx, 0, HNS_LIVE_BP_VOLATILE_GASTRO_ACID_BIT, true);
    hns_battle_set_volatile_bit(&fx, 0, HNS_LIVE_BP_VOLATILE_ROOST_ACTIVE_BIT, true);
    hns_battle_set_volatile_bit(&fx, 1, HNS_LIVE_BP_VOLATILE_SUBSTITUTE_BIT, true);
    hns_battle_set_volatile_bit(&fx, 1, HNS_LIVE_BP_VOLATILE_ENDURED_BIT, true);
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_PLAYER, &st), "player persistent read");
    TEST_ASSERT(st.volatile_foresight && st.volatile_miracle_eye, "player foresight/miracleEye true");
    TEST_ASSERT(st.volatile_root && st.volatile_smack_down, "player root/smackDown true");
    TEST_ASSERT(st.volatile_telekinesis && st.volatile_magnet_rise, "player telekinesis/magnetRise true");
    TEST_ASSERT(st.volatile_gastro_acid && st.volatile_roost_active, "player gastroAcid/roostActive true");
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_OPPONENT, &st), "enemy persistent read");
    TEST_ASSERT(st.volatile_substitute && st.volatile_endured, "enemy substitute/endured true");

    /* A neutral window must report every persistent volatile observed false. */
    for (int b = 0; b < 2; b++) {
        hns_battle_set_volatile_bit(&fx, (uint8_t)b, HNS_LIVE_BP_VOLATILE_FORESIGHT_BIT, false);
        hns_battle_set_volatile_bit(&fx, (uint8_t)b, HNS_LIVE_BP_VOLATILE_MIRACLE_EYE_BIT, false);
        hns_battle_set_volatile_bit(&fx, (uint8_t)b, HNS_LIVE_BP_VOLATILE_ROOT_BIT, false);
        hns_battle_set_volatile_bit(&fx, (uint8_t)b, HNS_LIVE_BP_VOLATILE_SMACK_DOWN_BIT, false);
        hns_battle_set_volatile_bit(&fx, (uint8_t)b, HNS_LIVE_BP_VOLATILE_TELEKINESIS_BIT, false);
        hns_battle_set_volatile_bit(&fx, (uint8_t)b, HNS_LIVE_BP_VOLATILE_MAGNET_RISE_BIT, false);
        hns_battle_set_volatile_bit(&fx, (uint8_t)b, HNS_LIVE_BP_VOLATILE_GASTRO_ACID_BIT, false);
        hns_battle_set_volatile_bit(&fx, (uint8_t)b, HNS_LIVE_BP_VOLATILE_ROOST_ACTIVE_BIT, false);
        hns_battle_set_volatile_bit(&fx, (uint8_t)b, HNS_LIVE_BP_VOLATILE_SUBSTITUTE_BIT, false);
        hns_battle_set_volatile_bit(&fx, (uint8_t)b, HNS_LIVE_BP_VOLATILE_ENDURED_BIT, false);
    }
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_PLAYER, &st), "player neutral persistent read");
    TEST_ASSERT(st.volatiles_observed && !st.volatile_foresight && !st.volatile_miracle_eye &&
                !st.volatile_root && !st.volatile_smack_down && !st.volatile_telekinesis &&
                !st.volatile_magnet_rise && !st.volatile_gastro_acid && !st.volatile_roost_active &&
                !st.volatile_substitute && !st.volatile_endured,
                "a neutral persistent window must be observed false, not unobserved");

    /* A null/zero gBattleStruct pointer means the gimmick is unobserved, never NONE. */
    write32_le_t(gba.ewram + cfg->battle_struct_ptr_offset, 0);
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_PLAYER, &st), "read with null gBattleStruct");
    TEST_ASSERT(!st.gimmick_observed && st.active_gimmick == 0,
                "a null gBattleStruct must leave the gimmick unobserved");
    /* An out-of-EWRAM pointer is also unobserved (never dereferenced into another region). */
    write32_le_t(gba.ewram + cfg->battle_struct_ptr_offset, 0x08000000u);
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_PLAYER, &st), "read with ROM pointer");
    TEST_ASSERT(!st.gimmick_observed && st.active_gimmick == 0,
                "an out-of-EWRAM gBattleStruct must leave the gimmick unobserved");

    /* Teardown must not retain any C4e operand. */
    hns_battle_set_in_battle(&fx, false);
    read_battler_state(&fx, BATTLER_ROLE_PLAYER, &st);
    expect_battler_unavailable(&st, "post-teardown observation must carry no C4e operand");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_battler_state_c4e_live_operands" ANSI_RESET "\n");
}

/**
 * Gap C4e correction: the live Ready path must observe the battle-global weather word and the
 * defender-side status word, distinguishing an observed neutral (0) from never-read so an active
 * Rain / Reflect battle can never be calculated as clear / screenless.
 */
static void test_hns_battler_state_c4e_field_conditions(void) {
    printf("Running test_hns_battler_state_c4e_field_conditions...\n");
    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    static FakeGba gba;
    HnsBattleFixture fx;
    hns_battler_fixture_two_battlers(&fx, &gba, cfg);

    write16_le_t(gba.ewram + cfg->battle_weather_offset, 0);
    write32_le_t(gba.ewram + cfg->side_statuses_offset + 0 * cfg->side_statuses_stride, 0);
    write32_le_t(gba.ewram + cfg->side_statuses_offset + 1 * cfg->side_statuses_stride, 0);

    BattlerRuntimeState st;
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_PLAYER, &st), "player read must succeed");
    TEST_ASSERT(st.weather_readable && st.battle_weather == 0,
                "clear weather must be observed neutral");
    TEST_ASSERT(st.side_statuses_readable && st.side_statuses == 0,
                "the player side status word must be observed neutral");

    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_OPPONENT, &st), "enemy read must succeed");
    TEST_ASSERT(st.weather_readable && st.battle_weather == 0,
                "the opponent observation must carry the same battle-global weather word");
    TEST_ASSERT(st.side_statuses_readable && st.side_statuses == 0,
                "the opponent side status word must be observed neutral");

    /* Positive transitions: weather and the defender-side status word are independently read. */
    write16_le_t(gba.ewram + cfg->battle_weather_offset, 1u << 0 /* BATTLE_WEATHER_RAIN */);
    write32_le_t(gba.ewram + cfg->side_statuses_offset + 1 * cfg->side_statuses_stride,
                 cfg->side_status_reflect_mask);
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_OPPONENT, &st), "transition read");
    TEST_ASSERT(st.weather_readable && st.battle_weather == (1u << 0),
                "observed Rain must be decoded verbatim");
    TEST_ASSERT(st.side_statuses_readable &&
                (st.side_statuses & cfg->side_status_reflect_mask) != 0,
                "observed defender-side Reflect must be decoded");
    /* The player side word is a DIFFERENT array element: the enemy Reflect must not leak to it. */
    TEST_ASSERT(read_battler_state(&fx, BATTLER_ROLE_PLAYER, &st), "player isolation read");
    TEST_ASSERT(st.side_statuses_readable && st.side_statuses == 0,
                "the player side status word must not carry the opponent's Reflect bit");

    /* Teardown must not retain the new operands. */
    hns_battle_set_in_battle(&fx, false);
    read_battler_state(&fx, BATTLER_ROLE_PLAYER, &st);
    expect_battler_unavailable(&st, "post-teardown observation must carry no field condition");

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_battler_state_c4e_field_conditions" ANSI_RESET "\n");
}

/* ---- Gap C4c: runtime target count computation ---- */

static void test_hns_target_count_computation(void) {
    printf("Running test_hns_target_count_computation...\n");

    /*
     * H&S GetMoveTargetCount semantics (from pinned source, battle_util.c:6122):
     *
     * TARGET_BOTH:
     *   return !(gAbsentBattlerFlags & (1u << battlerDef))
     *        + !(gAbsentBattlerFlags & (1u << BATTLE_PARTNER(battlerDef)));
     *
     * TARGET_FOES_AND_ALLY:
     *   return !(gAbsentBattlerFlags & (1u << battlerDef))
     *        + !(gAbsentBattlerFlags & (1u << BATTLE_PARTNER(battlerDef)))
     *        + !(gAbsentBattlerFlags & (1u << BATTLE_PARTNER(battlerAtk)));
     *
     * TARGET_OPPONENTS_FIELD: always 1.
     * TARGET_SELECTED / TARGET_RANDOM / TARGET_OPPONENT: IsBattlerAlive(battlerDef).
     * TARGET_USER: IsBattlerAlive(battlerAtk).
     *
     * Battler positions: 0=PlayerLeft, 1=OpponentLeft, 2=PlayerRight, 3=OpponentRight.
     * BATTLE_PARTNER(id) = (id) ^ 2.
     *
     * Singles (battlers_count=2): only battlers 0 and 1 exist.
     *   Partner of 1 is 1^2=3, but 3 >= battlers_count(2), so partner is out of range.
     * Doubles (battlers_count=4): all four battlers exist.
     *   Partner of 1 is 3, partner of 0 is 2.
     */

    /* --- Singles: TARGET_BOTH against a present defender --- */
    {
        /* Singles, battler 0 attacks battler 1. No absent flags. */
        uint8_t count = pokemon_compute_hns_target_count(
            /*absent_flags=*/0, /*battlers_count=*/2,
            /*attacker=*/0, /*defender=*/1,
            /*target_class=*/HNS_MOVE_TARGET_BOTH);
        /* Partner of 1 is 3, which is >= 2, so only defender counts. */
        TEST_ASSERT(count == 1,
            "Singles TARGET_BOTH with present defender must return 1");
    }

    /* --- Singles: TARGET_BOTH with absent defender (should still return count) */
    {
        /* Defender (battler 1) is absent */
        uint8_t count = pokemon_compute_hns_target_count(
            /*absent_flags=*/(1u << 1), /*battlers_count=*/2,
            /*attacker=*/0, /*defender=*/1,
            /*target_class=*/HNS_MOVE_TARGET_BOTH);
        /* Defender absent: 0. Partner of 1 is 3, which >= 2. Total = 0. */
        TEST_ASSERT(count == 0,
            "Singles TARGET_BOTH with absent defender must return 0");
    }

    /* --- Doubles: TARGET_BOTH with both opponents present --- */
    {
        /* Doubles, attacker=0 (PlayerLeft), defender=1 (OpponentLeft). */
        /* No absent flags: all 4 battlers present. */
        uint8_t count = pokemon_compute_hns_target_count(
            /*absent_flags=*/0, /*battlers_count=*/4,
            /*attacker=*/0, /*defender=*/1,
            /*target_class=*/HNS_MOVE_TARGET_BOTH);
        /* Defender 1 present: 1. Partner of 1 is 3, present: 1. Total = 2. */
        TEST_ASSERT(count == 2,
            "Doubles TARGET_BOTH with both opponents present must return 2");
    }

    /* --- Doubles: TARGET_BOTH with one opponent fainted --- */
    {
        /* Battler 3 (OpponentRight) is absent (fainted). */
        uint8_t count = pokemon_compute_hns_target_count(
            /*absent_flags=*/(1u << 3), /*battlers_count=*/4,
            /*attacker=*/0, /*defender=*/1,
            /*target_class=*/HNS_MOVE_TARGET_BOTH);
        /* Defender 1 present: 1. Partner of 1 is 3, absent: 0. Total = 1. */
        TEST_ASSERT(count == 1,
            "Doubles TARGET_BOTH with one opponent fainted must return 1");
    }

    /* --- Doubles: TARGET_BOTH targeting the other opponent --- */
    {
        /* Defender is 3 (OpponentRight), attacker is 0. */
        /* Battler 1 is absent. */
        uint8_t count = pokemon_compute_hns_target_count(
            /*absent_flags=*/(1u << 1), /*battlers_count=*/4,
            /*attacker=*/0, /*defender=*/3,
            /*target_class=*/HNS_MOVE_TARGET_BOTH);
        /* Defender 3 present: 1. Partner of 3 is 1, absent: 0. Total = 1. */
        TEST_ASSERT(count == 1,
            "Doubles TARGET_BOTH targeting absent-side opponent must return 1");
    }

    /* --- Doubles: TARGET_FOES_AND_ALLY with all present --- */
    {
        uint8_t count = pokemon_compute_hns_target_count(
            /*absent_flags=*/0, /*battlers_count=*/4,
            /*attacker=*/0, /*defender=*/1,
            /*target_class=*/HNS_MOVE_TARGET_FOES_AND_ALLY);
        /* Defender 1 present: 1. Partner of 1 is 3, present: 1.
         * Partner of attacker(0) is 2, present: 1. Total = 3. */
        TEST_ASSERT(count == 3,
            "Doubles TARGET_FOES_AND_ALLY with all present must return 3");
    }

    /* --- Doubles: TARGET_FOES_AND_ALLY with attacker's partner fainted --- */
    {
        uint8_t count = pokemon_compute_hns_target_count(
            /*absent_flags=*/(1u << 2), /*battlers_count=*/4,
            /*attacker=*/0, /*defender=*/1,
            /*target_class=*/HNS_MOVE_TARGET_FOES_AND_ALLY);
        /* Defender 1 present: 1. Partner of 1 is 3, present: 1.
         * Partner of attacker(0) is 2, absent: 0. Total = 2. */
        TEST_ASSERT(count == 2,
            "Doubles TARGET_FOES_AND_ALLY with attacker partner fainted must return 2");
    }

    /* --- TARGET_OPPONENTS_FIELD always returns 1 --- */
    {
        uint8_t count = pokemon_compute_hns_target_count(
            /*absent_flags=*/0, /*battlers_count=*/4,
            /*attacker=*/0, /*defender=*/1,
            /*target_class=*/HNS_MOVE_TARGET_OPPONENTS_FIELD);
        TEST_ASSERT(count == 1,
            "TARGET_OPPONENTS_FIELD must always return 1");
    }

    /* --- TARGET_SELECTED fails closed (single-target, ambiguous) --- */
    {
        /* TARGET_SELECTED is NOT a supported spread class: upstream
         * GetMoveTargetCount returns IsBattlerAlive(battlerDef), which this
         * pure function cannot verify authoritatively. The agreed rule
         * (native, header and Kotlin) is fail-closed: return 0, never a
         * fabricated 1. */
        uint8_t count = pokemon_compute_hns_target_count(
            /*absent_flags=*/0, /*battlers_count=*/4,
            /*attacker=*/0, /*defender=*/1,
            /*target_class=*/1); /* TARGET_SELECTED == 1 in the pinned enum */
        TEST_ASSERT(count == 0,
            "TARGET_SELECTED must fail closed (return 0, not a fabricated 1)");
    }

    /* Unknown/unsupported target classes fail closed, exactly like
     * TARGET_SELECTED: no fabricated count is ever returned. */
    {
        uint8_t count = pokemon_compute_hns_target_count(
            /*absent_flags=*/0, /*battlers_count=*/4,
            /*attacker=*/0, /*defender=*/1,
            /*target_class=*/0); /* TARGET_NONE == 0, not a spread class */
        TEST_ASSERT(count == 0,
            "TARGET_NONE (unsupported class) must fail closed (return 0)");
    }
    {
        uint8_t count = pokemon_compute_hns_target_count(
            /*absent_flags=*/0, /*battlers_count=*/4,
            /*attacker=*/0, /*defender=*/1,
            /*target_class=*/99); /* outside the pinned enum domain */
        TEST_ASSERT(count == 0,
            "Out-of-domain target class must fail closed (return 0)");
    }

    /* --- Invalid inputs fail closed --- */
    {
        /* battlers_count = 3 (invalid: must be 2 or 4) */
        uint8_t count = pokemon_compute_hns_target_count(
            0, /*battlers_count=*/3, 0, 1, HNS_MOVE_TARGET_BOTH);
        TEST_ASSERT(count == 0,
            "Invalid battlers_count must fail closed (return 0)");
    }
    {
        /* attacker out of range */
        uint8_t count = pokemon_compute_hns_target_count(
            0, /*battlers_count=*/2, /*attacker=*/5, 1, HNS_MOVE_TARGET_BOTH);
        TEST_ASSERT(count == 0,
            "Out-of-range attacker must fail closed (return 0)");
    }
    {
        /* defender out of range */
        uint8_t count = pokemon_compute_hns_target_count(
            0, /*battlers_count=*/2, 0, /*defender=*/5, HNS_MOVE_TARGET_BOTH);
        TEST_ASSERT(count == 0,
            "Out-of-range defender must fail closed (return 0)");
    }

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_target_count_computation" ANSI_RESET "\n");
}

/*
 * Gap C4c anti-spoof test: proves that a caller-supplied target count cannot
 * bypass the boundary. The native function is purely computational (no boundary),
 * so this test verifies that the inputs are derived from authoritative battle
 * state rather than from caller-supplied values.
 *
 * The real anti-spoof enforcement happens in CalcRequestBoundary, which overwrites
 * moveTargetCount from the exact-trusted runtime observation. Here we verify that
 * the native computation itself produces deterministic, fail-closed results.
 */
static void test_hns_target_count_anti_spoof(void) {
    printf("Running test_hns_target_count_anti_spoof...\n");

    /*
     * A caller who supplies a "favorable" absent_battler_flags (claiming all
     * opponents are absent) must not get a valid count from authoritative state.
     * In production, CalcRequestBoundary binds moveTargetCount from the native
     * reader, not from caller input. Here we verify the native function's
     * fail-closed behavior with adversarial inputs.
     */

    /* Adversary claims all opponents absent (flags=0xFF) but battlers_count is 4.
     * The function correctly returns 0 for TARGET_BOTH because both defenders are
     * marked absent. This is the CORRECT behavior: if the flags are authoritative,
     * absent battlers should reduce the count. */
    {
        uint8_t count = pokemon_compute_hns_target_count(
            /*absent_flags=*/0xFF, /*battlers_count=*/4,
            /*attacker=*/0, /*defender=*/1,
            /*target_class=*/HNS_MOVE_TARGET_BOTH);
        /* Both defender(1) and partner(3) are absent. Count = 0. */
        TEST_ASSERT(count == 0,
            "All-absent flags with TARGET_BOTH must return 0");
    }

    /* Verify that a zero absent-flags value (no battlers absent) produces count 2
     * for TARGET_BOTH in doubles. This is the "all present" baseline. */
    {
        uint8_t count = pokemon_compute_hns_target_count(
            /*absent_flags=*/0, /*battlers_count=*/4,
            /*attacker=*/0, /*defender=*/1,
            /*target_class=*/HNS_MOVE_TARGET_BOTH);
        TEST_ASSERT(count == 2,
            "Zero absent flags with TARGET_BOTH in doubles must return 2");
    }

    /* Verify that an unsupported single-target class (TARGET_SELECTED = 1 in
     * the pinned enum) fails closed regardless of absent flags: a caller cannot
     * coax a fabricated "1" out of the native computation. */
    {
        uint8_t count = pokemon_compute_hns_target_count(
            /*absent_flags=*/0, /*battlers_count=*/4,
            /*attacker=*/0, /*defender=*/1,
            /*target_class=*/1); /* TARGET_SELECTED */
        TEST_ASSERT(count == 0,
            "TARGET_SELECTED must fail closed (0) regardless of absent flags");
    }

    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_hns_target_count_anti_spoof" ANSI_RESET "\n");
}

int main(void) {
    printf("===================================================\n");
    printf("   DualDex Gen 3 Memory Parser Test Suite\n");
    printf("===================================================\n");

    test_text_decoding();
    test_single_pokemon_decryption();
    test_all_substructure_orders();
    test_checksum_corruption_detection();
    test_shininess_calculation();
    test_ewram_party_parsing();
    test_ewram_scan_ignores_box_pokemon_and_finds_real_party();

    // Heart & Soul 2.0.5 evidence-backed foundation (issue #40, first implementation phase).
    test_hns_config_matches_release_runtime_evidence();
    test_hns_release_rom_party_fixture();
    test_hns_party_counts_are_independent_symbols();
    test_vanilla_party_count_symbols_are_adjacent();
    test_hns_enemy_party_count_is_authoritative();
    test_expansion_ability_num_is_not_the_gigantamax_bit();
    test_expansion_nature_and_shiny_are_reported_honestly();
    test_hns_fallback_scan_uses_expansion_layout();
    test_vanilla_ability_slot_parsing_unchanged();
    test_gba_memory_region_translation();
    test_gba_memory_bounds_rejection();
    test_hns_saveblock1_pointer_resolution();
    test_hns_saveblock1_invalid_pointer_fails_closed();
    test_hns_saveblock1_aslr_window_is_not_fixed();
    test_hns_battle_pokemon_layout_fields();
    test_unknown_game_still_fails_closed();
    test_heart_and_soul_party_and_battle_hp_sync();

    // H&S 2.0.5 battle lifecycle and active-battler authority (issue #1).
    test_hns_battle_lifecycle_gates_enemy_state();
    test_hns_stale_battle_mon_cannot_invent_opponent();
    test_hns_doubles_degrades_instead_of_guessing();
    test_hns_single_to_multi_clears_prior_enemy();
    test_hns_invalid_battler_indexes_fail_closed();
    test_hns_authoritative_enemy_count_requires_reader_gate();
    test_hns_production_battle_presence_uses_lifecycle();
    test_hns_unreadable_lifecycle_gate_never_active();
    test_hns_lifecycle_maps_to_distinct_active_enemy_states();
    test_hns_active_battler_index_is_the_real_battler();
    test_hns_battle_exit_clears_production_presence();

    // H&S 2.0.5 trainer-battle, switch/faint invariants (issue #1, trainer-runtime-validation).
    test_hns_trainer_battle_classification();
    test_hns_trainer_opponent_slot_resolves_from_battler_index();
    test_hns_trainer_multi_party_faint_transition();
    test_hns_player_switch_slot_follows_battler_indexes();
    test_hns_player_faint_forces_unknown_until_replacement();
    test_hns_stale_enemy_slot_cannot_survive_replacement();
    test_hns_stale_player_slot_cannot_survive_faint();

    test_unbound_cfru_fixed_substructures();
    test_battle_presence_and_unknown_ui_state();
    test_unknown_game_fails_closed();

    // Authoritative player party discovery regression suite (issue #42).
    test_hns_authoritative_zero_count_defeats_decoy_scan();
    test_hns_stale_cache_cannot_override_zero();
    test_hns_authoritative_count_bounds_stale_slots();
    test_hns_authoritative_six_members();
    test_hns_invalid_authoritative_count_fails_closed();
    test_hns_corrupt_authoritative_slot_fails_closed();

    test_emerald_authoritative_zero_count_defeats_decoy_scan();
    test_emerald_stale_cache_cannot_override_zero();
    test_emerald_authoritative_count_bounds_stale_slots();
    test_emerald_authoritative_six_members();
    test_emerald_invalid_authoritative_count_fails_closed();
    test_emerald_corrupt_authoritative_slot_fails_closed();

    test_firered_authoritative_zero_count_defeats_decoy_scan();
    test_firered_stale_cache_cannot_override_zero();
    test_firered_authoritative_count_bounds_stale_slots();
    test_firered_authoritative_six_members();
    test_firered_invalid_authoritative_count_fails_closed();
    test_firered_corrupt_authoritative_slot_fails_closed();

    test_party_discovery_policy_assignments();

    // H&S 2.0.5 runtime challenge settings (issue #9).
    test_hns_challenge_settings_zero_is_observed_not_unknown();
    test_hns_challenge_settings_representative_enabled();
    test_hns_challenge_settings_option_style_and_isolation();
    test_hns_challenge_settings_out_of_domain();
    test_hns_challenge_settings_fail_closed();
    test_hns_challenge_settings_no_stale_across_games();

    // H&S 2.0.5 live battler ability + effective types (issue #9).
    test_hns_battle_pokemon_live_layout_pins();
    test_hns_battler_state_observes_active_player();
    test_hns_battler_state_observes_active_opponent();
    test_hns_battler_state_player_switch_follows_authority();
    test_hns_battler_state_opponent_switch_follows_authority();
    test_hns_battler_state_stale_ability_gone_after_replacement();
    test_hns_battler_state_player_doubles_is_ambiguous();
    test_hns_battler_state_faint_window_unavailable_before_replacement();
    test_hns_battler_state_unresolved_ability_stays_raw();
    test_hns_battler_state_type_representations();
    test_hns_battler_state_item_none_is_observed_zero();
    test_hns_battler_state_current_item_follows_rewrite();
    test_hns_battler_state_out_of_domain_item_stays_raw();
    test_hns_battler_state_trust_and_lifecycle_failures();
    test_hns_battler_state_teardown_and_profile_switch();
    test_hns_badge_state_reading();
    test_hns_battler_state_stats_stages_badges();
    test_hns_battler_state_c4e_live_operands();
    test_hns_field_statuses_raw_word();
    test_hns_battler_state_c4e_field_conditions();
    test_hns_target_count_computation();
    test_hns_target_count_anti_spoof();

    printf("===================================================\n");
    printf("Results: %d Passed, %d Failed\n", g_tests_passed, g_tests_failed);
    printf("===================================================\n");

    return (g_tests_failed == 0) ? 0 : 1;
}
