#include "pokemon_reader.h"
#include "pokemon_text.h"
#include "gba_memory_map.h"
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

static void test_hns_config_matches_compiled_evidence(void) {
    printf("Running test_hns_config_matches_compiled_evidence...\n");

    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    TEST_ASSERT(cfg != NULL, "Heart and Soul config must exist");

    // Compiled symbol addresses (pokehns.sym), expressed as EWRAM-relative offsets.
    TEST_ASSERT(cfg->player_party_offset == 0x34768, "gPlayerParty must be 0x02034768 in 2.0.5");
    TEST_ASSERT(cfg->player_party_count_offset == 0x342A8, "gPlayerPartyCount must be 0x020342A8");
    TEST_ASSERT(cfg->enemy_party_offset == 0x342B8, "gEnemyParty must be 0x020342B8");
    TEST_ASSERT(cfg->enemy_party_count_offset == 0x342A9, "gEnemyPartyCount must be 0x020342A9");
    TEST_ASSERT(cfg->battle_mons_offset == 0x420, "gBattleMons must be 0x02000420");
    TEST_ASSERT(cfg->battler_party_indexes_offset == 0x144, "gBattlerPartyIndexes must be 0x02000144");
    TEST_ASSERT(cfg->battle_type_flags_offset == 0xAC, "gBattleTypeFlags must be 0x020000AC");
    TEST_ASSERT(cfg->battlers_count_offset == 0xB0, "gBattlersCount must be 0x020000B0");
    TEST_ASSERT(cfg->battle_outcome_offset == 0x12C, "gBattleOutcome must be 0x0200012C");

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
    printf(ANSI_GREEN "  [PASS] test_hns_config_matches_compiled_evidence" ANSI_RESET "\n");
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
    TEST_ASSERT(cfg->enemy_party_count_offset == 0x342A9, "gEnemyPartyCount symbol must be 0x342A9");

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
 * H&S 2.0.5 trainer-battle lifecycle and multi-party switch/faint invariants (issue #1).
 *
 * Every test here corresponds to a scenario the runtime probe will exercise on the real ROM
 * (tools/hns-runtime-probe/scenarios/). The synthetic tests prove the readers' state-machine
 * logic before the ROM is available; the runtime probe records the raw addresses DualDex
 * actually follows.
 *
 * BATTLE_TYPE_TRAINER is bit 3 of gBattleTypeFlags (battle.h, sourced from the `make hns`
 * symbol table, identical to vanilla pokeemerald). No other flag-bit matters for the
 * single-trainer classification path.
 */

#define HNS_BATTLE_TYPE_TRAINER  (1u << 3)

/**
 * A trainer battle must classify as BATTLE_KIND_TRAINER_SINGLE, not WILD_SINGLE.
 *
 * Phase 2 assertion: BATTLE_TYPE_TRAINER bit determines BattleKind.
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
 * Phase 2 assertion: the resolved chain must hold for a trainer battle with 2 enemy party
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
 * Phase 3 assertion: the transition slot-0 -> slot-1 is captured correctly.
 *
 * During the forced-switch window (opponent absent) the reader must not retain slot 0. When
 * the engine commits the new battler the slot must resolve to gBattlerPartyIndexes[battler]==1.
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
 * Phase 4 assertion: before the switch the player slot is authoritative; after the engine
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
 * Phase 5 assertion: hp==0 on the active player makes the slot unknown; after the engine
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
 * Phase 8 invariant: once the engine commits a new gBattlerPartyIndexes value the old slot
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
 * Phase 8 invariant: after the player's active Pokémon faints and the engine commits the
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
    test_hns_config_matches_compiled_evidence();
    test_hns_party_counts_are_independent_symbols();
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

    printf("===================================================\n");
    printf("Results: %d Passed, %d Failed\n", g_tests_passed, g_tests_failed);
    printf("===================================================\n");

    return (g_tests_failed == 0) ? 0 : 1;
}
