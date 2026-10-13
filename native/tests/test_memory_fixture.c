#define _POSIX_C_SOURCE 200809L
#include "memory_fixture.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define CHECK(condition) do { if (!(condition)) { \
    fprintf(stderr, "%s:%d: %s\n", __FILE__, __LINE__, #condition); exit(1); \
} } while (0)

static MemoryFixture fixture;
static uint8_t ewram[DUALDEX_GBA_EWRAM_SIZE], iwram[DUALDEX_GBA_IWRAM_SIZE];
static char directory[] = "/tmp/dualdex-memory-fixture-XXXXXX";
static char ewram_path[128], iwram_path[128];
static MemoryFixtureRegion regions[2];
static unsigned checks;

/* Literal addresses deliberately independent of the production config. Provenance:
 * vanilla: docs/VANILLA_CALCULATOR_EVIDENCE.md section 7;
 * H&S release (not source-build): docs/HNS_2_0_5_COMPATIBILITY_EVIDENCE.md 11.5/11.6.
 * Every byte below is invented; no captured mon, ROM, save, or player identity. */
static const struct {
    GbaGameId game;
    uint32_t party, count, enemy, enemy_count;
} layouts[] = {
    {GAME_FIRERED, 0x24284, 0x24029, 0x2402C, 0x2402A},
    {GAME_EMERALD, 0x244EC, 0x244E9, 0x24744, 0x244EA},
    {GAME_HEART_AND_SOUL, 0x34764, 0x342A4, 0x342B4, 0x342A5}
};

static void put16(uint8_t* p, uint16_t v) { p[0] = (uint8_t)v; p[1] = (uint8_t)(v >> 8); }
static void put32(uint8_t* p, uint32_t v) {
    for (unsigned i = 0; i < 4; i++) p[i] = (uint8_t)(v >> (8 * i));
}

/* Minimal synthetic GAEM writer, not a decoder. PID 24 selects GAEM; the nonzero
 * checksum and XOR key exercise production decryption, including layout bitfields. */
static void mon(uint8_t* p, uint16_t species, bool expansion) {
    uint8_t plain[48] = {0};
    memset(p, 0, 100);
    put32(p, 24); put32(p + 4, 0x12345678u);
    p[8] = p[20] = 0xFF; /* Empty nickname and OT. */
    p[18] = expansion ? 4 << 3 : 0;
    put16(plain, species | (expansion ? 3 << 11 : 0));
    put16(plain + 2, 15); put32(plain + 4, 8000);
    put16(plain + 12, expansion ? (900 | (7 << 11)) : 33);
    plain[20] = expansion ? (20 | 0x80) : 20;
    plain[24] = 10;
    put32(plain + 40, 0x8000001Fu); /* HP IV=31; ability bit / gigantamax bit. */
    put32(plain + 44, expansion ? 2u << 29 : 0);
    uint16_t sum = 0;
    for (unsigned i = 0; i < sizeof(plain); i += 2)
        sum += (uint16_t)(plain[i] | ((uint16_t)plain[i + 1] << 8));
    CHECK(sum != 0);
    put16(p + 28, sum);
    const uint32_t key = 24u ^ 0x12345678u;
    for (unsigned i = 0; i < sizeof(plain); i++)
        p[32 + i] = plain[i] ^ (uint8_t)(key >> (8 * (i % 4)));
    p[84] = 20;
    put16(p + 86, 40); put16(p + 88, 60);
    for (unsigned i = 90; i < 100; i += 2) put16(p + i, 30 + (i - 90));
}

static void cleanup(void) {
    unlink(ewram_path); unlink(iwram_path); rmdir(directory);
}
static void write_file(const char* path, const uint8_t* bytes, size_t length, bool extra) {
    FILE* file = fopen(path, "wb");
    CHECK(file);
    CHECK(fwrite(bytes, 1, length, file) == length);
    if (extra) CHECK(fputc(0, file) != EOF);
    CHECK(fclose(file) == 0);
}
static void write_pair(void) {
    write_file(ewram_path, ewram, sizeof(ewram), false);
    write_file(iwram_path, iwram, sizeof(iwram), false);
}
static MemoryReplay replay(unsigned layout) {
    write_pair();
    CHECK(memory_fixture_load(&fixture, regions, 2) == FIXTURE_OK);
    MemoryReplay out;
    CHECK(memory_fixture_replay(&fixture, layouts[layout].game, &out));
    checks++;
    return out;
}
static void location(uint32_t base, bool hns) {
    uint8_t* p = ewram + base;
    unsigned pos = hns ? 4 : 0, loc = hns ? 8 : 4, escape = hns ? 0x28 : 0x24;
    put16(p + pos, 12); put16(p + pos + 2, 34);
    p[loc] = 3; p[loc + 1] = 7; p[loc + 2] = 2;
    put16(p + loc + 4, 100); put16(p + loc + 6, 200);
    p[escape] = 1; p[escape + 1] = 2;
}
static void synthetic(unsigned layout) {
    memset(ewram, 0, sizeof(ewram)); memset(iwram, 0, sizeof(iwram));
    bool hns = layout == 2;
    ewram[layouts[layout].count] = ewram[layouts[layout].enemy_count] = 1;
    mon(ewram + layouts[layout].party, hns ? 1001 : 25, hns);
    mon(ewram + layouts[layout].enemy, 19, hns);
    if (hns) {
        put32(iwram + 0x41D8, 0x020124A8u + 88);
        location(0x124A8 + 88, true);
        iwram[0x6011] = 2; /* gMain.inBattle, bit 1. */
        ewram[0xB0] = 2; /* Wild single; outcome/type flags are zero. */
        ewram[0x238] = 0; ewram[0x239] = 1;
        /* Both battlers map to party slot 0; live HP differs from party HP. */
        put16(ewram + 0x420, 1001); put16(ewram + 0x420 + 0x2A, 31);
        put16(ewram + 0x420 + 0x88, 19); put16(ewram + 0x420 + 0x88 + 0x2A, 22);
    } else {
        /* Pin the CURRENT vanilla location fallback, not a verified SaveBlock pointer. */
        location(layouts[layout].party - (layout == 0 ? 0x38 : 0x238), false);
    }
}

static void test_replay(void) {
    for (unsigned l = 0; l < 3; l++) {
        synthetic(l);
        MemoryReplay out = replay(l);
        CHECK(out.player.count == 1);
        const ParsedPokemon* p = &out.player.members[0];
        CHECK(p->is_valid && p->species == (l == 2 ? 1001 : 25));
        CHECK(p->level == 20 && p->max_hp == 60 && p->attack == 30);
        CHECK(p->held_item == 15 && p->experience == 8000 && p->hp_ev == 10 && p->hp_iv == 31);
        CHECK(p->moves[0] == (l == 2 ? 900 : 33) && p->pp[0] == 20);
        CHECK(p->ability_slot_known && p->ability_slot == (l == 2 ? 2 : 1));
        CHECK(p->gigantamax_factor == (l == 2));
        CHECK(p->hidden_nature == (l == 2 ? (24 ^ 4) : 24));
        CHECK(p->current_hp == (l == 2 ? 31 : 40));
        CHECK(out.location.is_valid && out.location.map_group == 3 && out.location.map_num == 7);
        CHECK(out.location.x == 100 && out.location.y == 200 && out.location.local_x == 12);
        if (l == 2) {
            CHECK(out.battle.lifecycle == BATTLE_LIFECYCLE_ACTIVE);
            CHECK(out.battle.kind == BATTLE_KIND_WILD_SINGLE);
            CHECK(out.player.active_battler_known && out.player.active_battler_slot == 0);
            CHECK(out.enemy.count == 1 && out.enemy.members[0].current_hp == 22);
            CHECK(out.enemy.active_battler_index == 1);
        } else {
            CHECK(out.battle.lifecycle == BATTLE_LIFECYCLE_UNKNOWN);
            CHECK(!out.player.active_battler_known && out.enemy.count == 0);
        }
        /* Determinism across a different prior game and repeated loads. */
        MemoryReplay ignored;
        CHECK(memory_fixture_replay(&fixture, GAME_HEART_AND_SOUL, &ignored));
        MemoryReplay again = replay(l);
        CHECK(memcmp(&out, &again, sizeof(out)) == 0);
    }
}

static void test_bad_content(void) {
    for (unsigned l = 0; l < 3; l++) {
        synthetic(l);
        const uint8_t counts[] = {0, 2, 6, 7, 255};
        for (unsigned i = 0; i < sizeof(counts); i++) {
            ewram[layouts[l].count] = counts[i];
            MemoryReplay out = replay(l);
            CHECK(out.player.count == 0 && !out.player.active_battler_known);
            CHECK(!out.player.members[0].is_valid);
        }
        ewram[layouts[l].count] = 1;
        const uint16_t invalid[] = {0, 2000, 2047};
        for (unsigned i = 0; i < sizeof(invalid) / sizeof(invalid[0]); i++) {
            mon(ewram + layouts[l].party, invalid[i], l == 2);
            CHECK(replay(l).player.count == 0);
        }
        /* Native numeric validation cannot establish species membership. Preserve the raw
         * unknown ID; the production data-pack/UI boundary owns its unknown label. */
        mon(ewram + layouts[l].party, 1999, l == 2);
        MemoryReplay unknown = replay(l);
        CHECK(unknown.player.count == 1 && unknown.player.members[0].species == 1999);
        ewram[layouts[l].party + 32] ^= 1; /* Bad checksum. */
        CHECK(replay(l).player.count == 0);
    }
    synthetic(2);
    ewram[0x342A5] = 2; /* Enemy count names an empty second slot. */
    CHECK(replay(2).enemy.count == 0);
    ewram[0x342A5] = 1;
    iwram[0x6011] = 0; /* Stale enemy bytes after battle. */
    MemoryReplay ended = replay(2);
    CHECK(ended.enemy.count == 0 && ended.battle.lifecycle == BATTLE_LIFECYCLE_INACTIVE);
}

static void test_pointers(void) {
    const uint32_t shifts[] = {0, 4, 88, 124};
    for (unsigned i = 0; i < sizeof(shifts) / sizeof(shifts[0]); i++) {
        synthetic(2);
        memset(ewram + 0x124A8, 0, 256);
        location(0x124A8 + shifts[i], true);
        put32(iwram + 0x41D8, 0x020124A8u + shifts[i]);
        CHECK(replay(2).location.x == 100);
    }
    const uint32_t invalid[] = {
        0, 0x08000000, 0x030041D8, 0xFFFFFFFF, 0x02000000,
        0x020124A4, 0x020124A9, 0x020124AA, 0x02012528, 0x0203FFFC, 0x02040000
    };
    for (unsigned i = 0; i < sizeof(invalid) / sizeof(invalid[0]); i++) {
        synthetic(2);
        put32(iwram + 0x41D8, invalid[i]);
        MemoryReplay out = replay(2);
        CHECK(!out.location.is_valid && out.location.x == 0);
        CHECK(out.player.count == 1); /* Bad pointer must not destroy independent party data. */
    }
}

static void assert_unloaded(void) {
    uint8_t byte = 0xA5;
    CHECK(memory_fixture_ewram(&fixture) == NULL);
    CHECK(!memory_fixture_read(&fixture, 0x02000000, &byte, 1) && byte == 0xA5);
    CHECK(!memory_fixture_read(&fixture, 0x03000000, &byte, 1));
    MemoryReplay out;
    memset(&out, 0xA5, sizeof(out));
    CHECK(!memory_fixture_replay(&fixture, GAME_FIRERED, &out));
    CHECK(out.player.count == 0 && !out.location.is_valid);
    checks++;
}
static void test_loading(void) {
    synthetic(0); write_pair();
    CHECK(memory_fixture_load(&fixture, regions, 2) == FIXTURE_OK);
    MemoryFixtureRegion reversed[] = {regions[1], regions[0]};
    CHECK(memory_fixture_load(&fixture, reversed, 2) == FIXTURE_OK);
    CHECK(memcmp(memory_fixture_ewram(&fixture), ewram, sizeof(ewram)) == 0);
    uint8_t bytes[4] = {0};
    CHECK(memory_fixture_read(&fixture, 0x0203FFFF, bytes, 1));
    CHECK(memory_fixture_read(&fixture, 0x03007FFC, bytes, 4));
    const uint32_t bad[] = {0x01FFFFFF, 0x0203FFFF, 0x02040000, 0x03007FFF,
                            0x03008000, 0x08000000, 0xFFFFFFFF};
    for (unsigned i = 0; i < sizeof(bad) / sizeof(bad[0]); i++) {
        memset(bytes, 0xA5, sizeof(bytes));
        CHECK(!memory_fixture_read(&fixture, bad[i], bytes, sizeof(bytes)));
        CHECK(bytes[0] == 0xA5 && bytes[3] == 0xA5);
    }
    CHECK(!memory_fixture_read(&fixture, 0x02000000, bytes, SIZE_MAX));
    CHECK(!memory_fixture_read(&fixture, 0x02000000, bytes, 0));
    CHECK(!memory_fixture_read(&fixture, 0x02000000, NULL, 1));
    CHECK(!memory_fixture_read(NULL, 0x02000000, bytes, 1));
    /* Byte reads need not be aligned; pointer alignment is checked by production readers. */
    CHECK(memory_fixture_read(&fixture, 0x02000001, bytes, 3));

    for (unsigned r = 0; r < 2; r++) {
        const uint8_t* data = r ? iwram : ewram;
        size_t length = r ? sizeof(iwram) : sizeof(ewram);
        const size_t short_lengths[] = {0, 1, length - 1};
        for (unsigned i = 0; i < 3; i++) {
            write_pair();
            CHECK(memory_fixture_load(&fixture, regions, 2) == FIXTURE_OK);
            write_file(regions[r].path, data, short_lengths[i], false);
            CHECK(memory_fixture_load(&fixture, regions, 2) == FIXTURE_BAD_LENGTH);
            assert_unloaded();
        }
        write_pair(); write_file(regions[r].path, data, length, true);
        CHECK(memory_fixture_load(&fixture, regions, 2) == FIXTURE_BAD_LENGTH);
        assert_unloaded();
    }
    write_pair();
    MemoryFixtureRegion bad_regions[] = {regions[0], regions[1]};
    const uint32_t bad_bases[] = {0, 0x08000000, 0x02000001, 0x02000004, 0x03000000};
    for (unsigned i = 0; i < sizeof(bad_bases) / sizeof(bad_bases[0]); i++) {
        bad_regions[0].start = bad_bases[i];
        CHECK(memory_fixture_load(&fixture, bad_regions, 2) == FIXTURE_INVALID_REGION);
        assert_unloaded();
    }
    CHECK(memory_fixture_load(&fixture, regions, 1) == FIXTURE_INVALID_REGION);
    CHECK(memory_fixture_load(&fixture, regions, 3) == FIXTURE_INVALID_REGION);
    CHECK(memory_fixture_load(&fixture, NULL, 2) == FIXTURE_INVALID_REGION);
    CHECK(memory_fixture_load(NULL, regions, 2) == FIXTURE_INVALID_REGION);
    bad_regions[0] = regions[0]; bad_regions[1].path = NULL;
    CHECK(memory_fixture_load(&fixture, bad_regions, 2) == FIXTURE_INVALID_REGION);
    assert_unloaded();
    CHECK(unlink(iwram_path) == 0);
    CHECK(memory_fixture_load(&fixture, regions, 2) == FIXTURE_IO_ERROR);
    assert_unloaded();
    write_pair();
    MemoryFixtureRegion swapped[] = {{0x02000000, iwram_path}, {0x03000000, ewram_path}};
    CHECK(memory_fixture_load(&fixture, swapped, 2) == FIXTURE_BAD_LENGTH);
    assert_unloaded();
}

static void test_unsupported(void) {
    synthetic(0); write_pair();
    CHECK(memory_fixture_load(&fixture, regions, 2) == FIXTURE_OK);
    const GbaGameId unsupported[] = {GAME_UNKNOWN, GAME_UNBOUND, GAME_RUBY, (GbaGameId)999};
    for (unsigned i = 0; i < sizeof(unsupported) / sizeof(unsupported[0]); i++) {
        MemoryReplay out;
        CHECK(!memory_fixture_replay(&fixture, unsupported[i], &out));
        CHECK(out.player.count == 0 && out.enemy.count == 0 && !out.location.is_valid);
        CHECK(out.battle.lifecycle == BATTLE_LIFECYCLE_UNKNOWN);
        checks++;
    }
    /* Also exercise the production unknown-layout boundary directly; the harness
     * allowlist alone would not detect a reader regression to a fallback config. */
    CHECK(pokemon_get_game_config(GAME_UNKNOWN) == NULL);
    PartySnapshot party;
    CHECK(pokemon_read_player_party_gba(memory_fixture_read, &fixture,
          memory_fixture_ewram(&fixture), sizeof(ewram), NULL, &party) == 0);
}

int main(int argc, char** argv) {
    if (argc == 5 && strcmp(argv[1], "--replay") == 0) {
        GbaGameId game = strcmp(argv[2], "firered") == 0 ? GAME_FIRERED :
                         strcmp(argv[2], "emerald") == 0 ? GAME_EMERALD :
                         strcmp(argv[2], "hns205") == 0 ? GAME_HEART_AND_SOUL : GAME_UNKNOWN;
        MemoryFixtureRegion input[] = {{DUALDEX_GBA_EWRAM_BASE, argv[3]},
                                       {DUALDEX_GBA_IWRAM_BASE, argv[4]}};
        MemoryFixtureResult result = memory_fixture_load(&fixture, input, 2);
        MemoryReplay out;
        if (result != FIXTURE_OK || !memory_fixture_replay(&fixture, game, &out)) {
            fprintf(stderr, "Replay rejected: loader=%d (0=OK, 1=region, 2=I/O, 3=length), layout=%d\n",
                    result, game);
            return 1;
        }
        printf("player=%u enemy=%u location_valid=%d lifecycle=%d\n",
               out.player.count, out.enemy.count, out.location.is_valid, out.battle.lifecycle);
        return 0;
    }
    if (argc != 1) {
        fprintf(stderr, "usage: %s [--replay firered|emerald|hns205 ewram.bin iwram.bin]\n", argv[0]);
        return 2;
    }
    CHECK(mkdtemp(directory));
    CHECK(snprintf(ewram_path, sizeof(ewram_path), "%s/ewram.bin", directory) > 0);
    CHECK(snprintf(iwram_path, sizeof(iwram_path), "%s/iwram.bin", directory) > 0);
    CHECK(atexit(cleanup) == 0);
    regions[0] = (MemoryFixtureRegion){DUALDEX_GBA_EWRAM_BASE, ewram_path};
    regions[1] = (MemoryFixtureRegion){DUALDEX_GBA_IWRAM_BASE, iwram_path};
    test_replay(); test_bad_content(); test_pointers(); test_loading(); test_unsupported();
    printf("Memory fixture replay: %u replay/rejection cases passed (all synthetic, ROM-free).\n", checks);
    return 0;
}
