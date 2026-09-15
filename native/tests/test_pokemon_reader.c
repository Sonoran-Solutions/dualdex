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

    // 4. Test pokemon_read_player_party with mismatched static config offset
    // It should reject the invalid static offset and fallback to scanning, returning the true party
    const GameMemoryConfig* emerald_cfg = pokemon_get_game_config(GAME_EMERALD);
    pokemon_reader_reset();
    PartySnapshot read_snapshot;
    uint8_t read_count = pokemon_read_player_party(ewram, EWRAM_SIZE, emerald_cfg, &read_snapshot);
    TEST_ASSERT(read_count == 3, "pokemon_read_player_party must fallback and find the 3 party members");
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

    const size_t EWRAM_SIZE = 256 * 1024;
    uint8_t* ewram = (uint8_t*)calloc(1, EWRAM_SIZE);
    TEST_ASSERT(ewram != NULL, "Memory allocation for EWRAM failed");

    pokemon_reader_reset();

    // 1. Verify game detection
    GbaGameId detected = pokemon_detect_game("POKEMON HNS");
    TEST_ASSERT(detected == GAME_HEART_AND_SOUL, "'POKEMON HNS' (the upstream hns TITLE) must detect as H&S");

    const GameMemoryConfig* hns_cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    TEST_ASSERT(hns_cfg != NULL, "Heart and Soul config must exist");

    // 2. Player party: Cyndaquil + Totodile at the compiled gPlayerParty address.
    ewram[hns_cfg->player_party_count_offset] = 2;

    const uint32_t player_otid = 0x88776655;
    RawGbaPokemon cyndaquil;
    RawGbaPokemon totodile;
    build_hns_mon(&cyndaquil, 0x11223344, player_otid, 155, 14, 50, 31, 0, 0, false);
    build_hns_mon(&totodile, 0x55667788, player_otid, 158, 15, 60, 31, (uint32_t)1 << 29, 0, false);
    memcpy(ewram + hns_cfg->player_party_offset, &cyndaquil, sizeof(RawGbaPokemon));
    memcpy(ewram + hns_cfg->player_party_offset + sizeof(RawGbaPokemon), &totodile, sizeof(RawGbaPokemon));

    // 3. Enemy party at the compiled gEnemyParty address.
    ewram[hns_cfg->enemy_party_count_offset] = 2;
    RawGbaPokemon pidgey;
    RawGbaPokemon rattata;
    build_hns_mon(&pidgey, 0xAABBCCDD, 0x99990000, 16, 13, 40, 25, 0, 0, false);
    build_hns_mon(&rattata, 0xCCDDEEFF, 0x99990000, 19, 12, 35, 20, 0, 0, false);
    memcpy(ewram + hns_cfg->enemy_party_offset, &pidgey, sizeof(RawGbaPokemon));
    memcpy(ewram + hns_cfg->enemy_party_offset + sizeof(RawGbaPokemon), &rattata, sizeof(RawGbaPokemon));

    // 4. Live gBattleMons: HP at the compiled 0x2A (not the old 40), 136-byte stride.
    TEST_ASSERT(hns_cfg->battle_mons_size == 136, "BattlePokemon stride must be 136");
    TEST_ASSERT(hns_cfg->battle_mons_hp_offset == 0x2A, "BattlePokemon.hp must be 0x2A");

    uint8_t* b0 = ewram + hns_cfg->battle_mons_offset;
    uint8_t* b1 = ewram + hns_cfg->battle_mons_offset + hns_cfg->battle_mons_size;
    write16_le_t(b0, 155);      // species
    write16_le_t(b0 + 0x2A, 28); // damaged HP
    write16_le_t(b1, 16);
    write16_le_t(b1 + 0x2A, 12);

    // gBattlerPartyIndexes is an independent symbol at 0x144. Plant a decoy exactly where the
    // old code looked (gBattleMons - 24) so that any reintroduced derivation is caught.
    TEST_ASSERT(hns_cfg->battler_party_indexes_offset == 0x144,
                "gBattlerPartyIndexes must be declared as its own symbol");
    TEST_ASSERT(hns_cfg->battler_party_indexes_offset != hns_cfg->battle_mons_offset - 24,
                "the compiled offset must differ from the legacy gBattleMons - 24 arithmetic");
    write16_le_t(ewram + hns_cfg->battle_mons_offset - 24, 1); // decoy slot 1
    write16_le_t(ewram + hns_cfg->battler_party_indexes_offset, 0); // real slot 0

    PartySnapshot player_snap;
    uint8_t player_count = pokemon_read_player_party(ewram, EWRAM_SIZE, hns_cfg, &player_snap);
    TEST_ASSERT(player_count == 2, "Player party count should be 2");
    TEST_ASSERT(player_snap.members[0].species == 155, "Slot 0 should be Cyndaquil");
    TEST_ASSERT(player_snap.members[0].current_hp == 28, "Cyndaquil HP must sync from gBattleMons[0].hp");
    TEST_ASSERT(player_snap.members[1].current_hp == 60, "Totodile HP must stay at its party value");
    TEST_ASSERT(player_snap.active_battler_slot == 0,
                "the active slot must come from the real gBattlerPartyIndexes symbol, not the decoy");

    // 5. Expansion parsing flows through the party reader.
    TEST_ASSERT(player_snap.members[1].ability_num == 1,
                "Totodile's abilityNum must come from the party reader's expansion layout");

    // 6. Enemy party + live HP sync.
    PartySnapshot enemy_snap;
    uint8_t enemy_count = pokemon_read_enemy_party(ewram, EWRAM_SIZE, hns_cfg, &enemy_snap);
    TEST_ASSERT(enemy_count == 2, "Enemy party count should be 2");
    TEST_ASSERT(enemy_snap.members[0].species == 16, "Enemy slot 0 should be Pidgey");
    TEST_ASSERT(enemy_snap.members[0].current_hp == 12, "Pidgey HP must sync from gBattleMons[1].hp");

    // 7. Faint: count must survive, slot must stay.
    write16_le_t(b1 + 0x2A, 0);
    ((RawGbaPokemon*)(ewram + hns_cfg->enemy_party_offset))->current_hp = 0;
    PartySnapshot fainted;
    pokemon_read_enemy_party(ewram, EWRAM_SIZE, hns_cfg, &fainted);
    TEST_ASSERT(fainted.count == 2, "the enemy party count must survive a faint");
    TEST_ASSERT(fainted.members[0].species == 16, "slot 0 must remain Pidgey after fainting");
    TEST_ASSERT(fainted.members[0].current_hp == 0, "the fainted HP must be reported");

    // 8. Opponent sends out Rattata; gBattlerPartyIndexes[1] is at symbol + 2.
    write16_le_t(b1, 19);
    write16_le_t(b1 + 0x2A, 35);
    write16_le_t(ewram + hns_cfg->battler_party_indexes_offset - 24 + 2, 0); // decoy
    write16_le_t(ewram + hns_cfg->battler_party_indexes_offset + 2, 1);      // real
    PartySnapshot sendout;
    pokemon_read_enemy_party(ewram, EWRAM_SIZE, hns_cfg, &sendout);
    TEST_ASSERT(sendout.active_battler_slot == 1,
                "the enemy active slot must come from gBattlerPartyIndexes[1]");
    TEST_ASSERT(sendout.members[1].species == 19, "slot 1 must be Rattata");

    // 9. Player switches to Totodile.
    write16_le_t(b0, 158);
    write16_le_t(b0 + 0x2A, 48);
    write16_le_t(ewram + hns_cfg->battler_party_indexes_offset, 1);
    PartySnapshot switched;
    pokemon_read_player_party(ewram, EWRAM_SIZE, hns_cfg, &switched);
    TEST_ASSERT(switched.active_battler_slot == 1, "the player active slot must follow the symbol");
    TEST_ASSERT(switched.members[1].current_hp == 48, "Totodile HP must sync after the switch");

    free(ewram);
    g_tests_passed++;
    printf(ANSI_GREEN "  [PASS] test_heart_and_soul_party_and_battle_hp_sync" ANSI_RESET "\n");
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

    // Battle presence still derives from the H&S gBattleMons species word.
    write16_le_t(b0, 155);
    TEST_ASSERT(pokemon_read_battle_presence(ewram, EWRAM_SIZE, cfg) == 1,
                "a live battler species must report presence");
    write16_le_t(b0, 0);
    TEST_ASSERT(pokemon_read_battle_presence(ewram, EWRAM_SIZE, cfg) == 0,
                "an empty battler slot must report no battle");
    TEST_ASSERT(pokemon_read_battle_ui_state(ewram, EWRAM_SIZE, cfg) == 0,
                "battle UI state must stay UNKNOWN without controller evidence");

    // Out-of-range species still reports UNKNOWN, never a confident verdict.
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
    test_expansion_ability_num_is_not_the_gigantamax_bit();
    test_expansion_nature_and_shiny_are_reported_honestly();
    test_vanilla_ability_slot_parsing_unchanged();
    test_gba_memory_region_translation();
    test_gba_memory_bounds_rejection();
    test_hns_saveblock1_pointer_resolution();
    test_hns_saveblock1_invalid_pointer_fails_closed();
    test_hns_saveblock1_aslr_window_is_not_fixed();
    test_hns_battle_pokemon_layout_fields();
    test_unknown_game_still_fails_closed();
    test_heart_and_soul_party_and_battle_hp_sync();

    test_unbound_cfru_fixed_substructures();
    test_battle_presence_and_unknown_ui_state();
    test_unknown_game_fails_closed();

    printf("===================================================\n");
    printf("Results: %d Passed, %d Failed\n", g_tests_passed, g_tests_failed);
    printf("===================================================\n");

    return (g_tests_failed == 0) ? 0 : 1;
}
