#define _POSIX_C_SOURCE 200809L
#include "rowe_raw_decoder.h"
#include "memory_fixture.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

static unsigned checks;
#define CHECK(condition) do { checks++; if (!(condition)) { \
    fprintf(stderr, "%s:%d: %s\n", __FILE__, __LINE__, #condition); exit(1); \
} } while (0)

/* Literal offsets/writer are independent of decoder constants. All data is invented. */
enum { SLOT = 76, REGION = 456, COUNT_OFFSET = 0x25125, PARTY_OFFSET = 0x25128 };
static MemoryFixture fixture;
static uint8_t ewram[DUALDEX_GBA_EWRAM_SIZE], iwram[DUALDEX_GBA_IWRAM_SIZE];
static char directory[] = "/tmp/dualdex-rowe-raw-XXXXXX";
static char ewram_path[128], iwram_path[128];

static void put16(uint8_t* p, uint16_t value) {
    p[0] = (uint8_t)value; p[1] = (uint8_t)(value >> 8);
}
static void put32(uint8_t* p, uint32_t value) {
    for (unsigned i = 0; i < 4; i++) p[i] = (uint8_t)(value >> (8 * i));
}
static void cleared(const void* value, size_t length) {
    const uint8_t* bytes = value;
    for (size_t i = 0; i < length; i++) CHECK(bytes[i] == 0);
}
static void invented(uint8_t* p, unsigned identity) {
    memset(p, 0xa5, SLOT);
    put32(p, 0x82104365u + identity);
    put32(p + 4, 0xf0e1d2c3u + identity);
    const uint8_t name[12] = {0,255,128,127,1,2,3,4,5,6,7,8};
    memcpy(p + 8, name, sizeof(name));
    put32(p + 20, 0x80000000u | (0x155555u << 10) | 1u);
    put32(p + 24, 0xfff00000u | (0x2aau << 10) | 0x155u);
    put32(p + 28, 0xf8000000u | (0x3ffu << 17) | (19u << 12) | (0xdabu + identity));
    p[39] = (27u << 2) | 0x83u;
    const uint8_t pp[4] = {0,127,128,255};
    memcpy(p + 52, pp, sizeof(pp));
    put32(p + 56, 0x80fedcbau);
    p[60] = 128;
    const uint16_t stats[7] = {0x1234,0xabcd,0x0456,0x6789,0xabba,0x8000,0xfffe};
    for (unsigned i = 0; i < 7; i++) put16(p + 62 + 2*i, stats[i]);
}

static void test_fields_and_boundaries(void) {
    uint8_t bytes[SLOT]; invented(bytes, 0);
    RoweRawSlot out;
    CHECK(rowe_raw_decode_slot(bytes, sizeof(bytes), &out));
    CHECK(out.pid == 0x82104365u && out.otid == 0xf0e1d2c3u);
    CHECK(memcmp(out.nickname, bytes + 8, 12) == 0);
    CHECK(out.moves[0] == 1 && out.moves[1] == 0x155 && out.moves[2] == 0x2aa && out.moves[3] == 0x3ff);
    CHECK(out.experience == 0x155555 && out.species == 0xdab && out.form == 19);
    CHECK(out.nature == 27 && out.nature != out.pid % 25);
    CHECK(memcmp(out.pp, (uint8_t[]){0,127,128,255}, 4) == 0);
    CHECK(out.status == 0x80fedcbau && out.level == 128);
    CHECK(out.hp == 0x1234 && out.max_hp == 0xabcd && out.attack == 0x0456);
    CHECK(out.defense == 0x6789 && out.speed == 0xabba && out.sp_attack == 0x8000 && out.sp_defense == 0xfffe);
    CHECK(memcmp(out.bytes, bytes, SLOT) == 0);
    CHECK(rowe_raw_decode_slot(out.bytes, SLOT, &out)); /* Input/output overlap. */
    CHECK(memcmp(out.bytes, bytes, SLOT) == 0 && out.pid == 0x82104365u);
    uint8_t unaligned[SLOT + 1]; memcpy(unaligned + 1, bytes, SLOT);
    CHECK(rowe_raw_decode_slot(unaligned + 1, SLOT, &out));
    CHECK(memcmp(out.bytes, bytes, SLOT) == 0);

    memset(bytes, 255, sizeof(bytes));
    CHECK(rowe_raw_decode_slot(bytes, SLOT, &out));
    CHECK(out.pid == UINT32_MAX && out.otid == UINT32_MAX && out.status == UINT32_MAX);
    CHECK(out.species == 4095 && out.form == 31 && out.nature == 31 && out.experience == 0x1fffff);
    CHECK(out.level == 255 && out.hp == 65535 && out.max_hp == 65535);
    CHECK(out.attack == 65535 && out.defense == 65535 && out.speed == 65535);
    CHECK(out.sp_attack == 65535 && out.sp_defense == 65535);
    for (unsigned i = 0; i < 4; i++) CHECK(out.moves[i] == 1023 && out.pp[i] == 255);
    memset(bytes, 0, sizeof(bytes));
    CHECK(rowe_raw_decode_slot(bytes, SLOT, &out));
    CHECK(out.pid == 0 && out.otid == 0 && out.species == 0 && out.level == 0 && out.hp == 0);
    CHECK(out.status == 0 && out.max_hp == 0); /* Raw values, not a valid-Pokémon verdict. */

    /* Every packed-word bit independently, including experience bit30/reserved bit31. */
    for (unsigned offset = 20; offset <= 28; offset += 4) {
        for (unsigned bit = 0; bit < 32; bit++) {
            memset(bytes, 0, sizeof(bytes)); put32(bytes + offset, UINT32_C(1) << bit);
            CHECK(rowe_raw_decode_slot(bytes, SLOT, &out));
            CHECK(out.moves[0] == (offset == 20 && bit < 10 ? 1u << bit : 0));
            CHECK(out.experience == (offset == 20 && bit >= 10 && bit <= 30 ? 1u << (bit-10) : 0));
            CHECK(out.moves[1] == (offset == 24 && bit < 10 ? 1u << bit : 0));
            CHECK(out.moves[2] == (offset == 24 && bit >= 10 && bit < 20 ? 1u << (bit-10) : 0));
            CHECK(out.species == (offset == 28 && bit < 12 ? 1u << bit : 0));
            CHECK(out.form == (offset == 28 && bit >= 12 && bit < 17 ? 1u << (bit-12) : 0));
            CHECK(out.moves[3] == (offset == 28 && bit >= 17 && bit < 27 ? 1u << (bit-17) : 0));
            CHECK(memcmp(out.bytes, bytes, SLOT) == 0);
        }
    }
    memset(bytes, 0, sizeof(bytes));
    for (unsigned value = 0; value <= 255; value++) {
        bytes[39] = (uint8_t)value;
        CHECK(rowe_raw_decode_slot(bytes, SLOT, &out));
        CHECK(out.nature == ((value >> 2) & 31));
        CHECK(out.bytes[39] == value);
    }
}

static void test_lengths_nulls_and_clear(void) {
    uint8_t bytes[SLOT]; invented(bytes, 0);
    RoweRawSlot out;
    for (size_t length = 0; length < SLOT; length++) {
        uint8_t* short_bytes = malloc(length ? length : 1); CHECK(short_bytes);
        memcpy(short_bytes, bytes, length);
        CHECK(rowe_raw_decode_slot(bytes, SLOT, &out));
        CHECK(!rowe_raw_decode_slot(short_bytes, length, &out));
        cleared(&out, sizeof(out)); free(short_bytes);
    }
    uint8_t oversized[456]; memset(oversized, 0xa5, sizeof(oversized));
    memcpy(oversized, bytes, SLOT);
    const size_t too_long[] = {77,100,456,SIZE_MAX};
    for (unsigned i = 0; i < sizeof(too_long)/sizeof(too_long[0]); i++) {
        CHECK(rowe_raw_decode_slot(bytes, SLOT, &out));
        CHECK(!rowe_raw_decode_slot(oversized, too_long[i], &out)); cleared(&out, sizeof(out));
    }
    CHECK(rowe_raw_decode_slot(bytes, SLOT, &out));
    CHECK(!rowe_raw_decode_slot(NULL, SLOT, &out)); cleared(&out, sizeof(out));
    CHECK(!rowe_raw_decode_slot(bytes, SLOT, NULL));

    uint8_t party[REGION];
    for (unsigned i = 0; i < 6; i++) invented(party + i*SLOT, i);
    RoweRawParty group;
    for (unsigned count = 0; count <= 6; count++) {
        CHECK(rowe_raw_decode_party(party, REGION, (uint8_t)count, &group));
        CHECK(group.count == count);
        for (unsigned i = 0; i < count; i++) CHECK(group.members[i].pid == 0x82104365u + i);
        for (unsigned i = count; i < 6; i++) cleared(&group.members[i], sizeof(group.members[i]));
    }
    const uint8_t bad_counts[] = {7,255};
    for (unsigned i = 0; i < sizeof(bad_counts); i++) {
        CHECK(rowe_raw_decode_party(party, REGION, 6, &group));
        CHECK(!rowe_raw_decode_party(party, REGION, bad_counts[i], &group)); cleared(&group, sizeof(group));
    }
    for (size_t length = 0; length < REGION; length++) {
        CHECK(rowe_raw_decode_party(party, REGION, 2, &group));
        CHECK(!rowe_raw_decode_party(party, length, 2, &group)); cleared(&group, sizeof(group));
    }
    CHECK(!rowe_raw_decode_party(party, REGION + 1, 0, &group)); cleared(&group, sizeof(group));
    CHECK(!rowe_raw_decode_party(party, SIZE_MAX, 2, &group)); cleared(&group, sizeof(group));
    CHECK(!rowe_raw_decode_party(NULL, REGION, 2, &group)); cleared(&group, sizeof(group));
    CHECK(!rowe_raw_decode_party(party, REGION, 2, NULL));
    union { RoweRawParty output; uint8_t input[sizeof(RoweRawParty)]; } overlap;
    memcpy(overlap.input, party, REGION);
    CHECK(rowe_raw_decode_party(overlap.input, REGION, 2, &overlap.output));
    CHECK(memcmp(overlap.output.members[1].bytes, party + SLOT, SLOT) == 0);
}

static void cleanup(void) {
    unlink(ewram_path); unlink(iwram_path); rmdir(directory);
}
static void write_file(const char* path, const uint8_t* bytes, size_t length) {
    FILE* f = fopen(path, "wb"); CHECK(f);
    CHECK(fwrite(bytes, 1, length, f) == length); CHECK(fclose(f) == 0);
}
static void load_full_fixture(void) {
    memset(ewram, 0, sizeof(ewram)); memset(iwram, 0, sizeof(iwram));
    for (unsigned i = 0; i < 6; i++) invented(ewram + PARTY_OFFSET + i*SLOT, i);
    ewram[COUNT_OFFSET] = 2;
    /* Invented enemy region/count: deliberately never used by this player API. */
    ewram[0x25126] = 6; memset(ewram + 0x252f0, 0xfe, REGION);
    write_file(ewram_path, ewram, sizeof(ewram)); write_file(iwram_path, iwram, sizeof(iwram));
    MemoryFixtureRegion regions[] = {{0x02000000u,ewram_path},{0x03000000u,iwram_path}};
    CHECK(memory_fixture_load(&fixture, regions, 2) == FIXTURE_OK);
}

typedef struct {
    unsigned calls, fail_call, short_call, long_call, count_change, party_change;
} ReadControl;

static size_t read_fixture(void* context, uint32_t address, uint8_t* out, size_t length) {
    ReadControl* control = context;
    CHECK(control);
    unsigned call = ++control->calls;
    /* No enemy address and no scan: exactly count/party/count/party/count. */
    CHECK(call <= 5);
    CHECK(address == (call % 2 ? 0x02025125u : 0x02025128u));
    CHECK(length == (call % 2 ? 1u : REGION));
    if (control->count_change == call) fixture.ewram[COUNT_OFFSET]++;
    if (control->party_change == call) fixture.ewram[PARTY_OFFSET + REGION - 1] ^= 0x80;
    if (control->fail_call == call) return 0;
    size_t actual = length - (control->short_call == call ? 1 : 0);
    if (!actual || !memory_fixture_read(&fixture, address, out, actual)) return 0;
    return actual + (control->long_call == call ? 1 : 0);
}

static void test_fixture_counts_reorder_and_scope(void) {
    load_full_fixture();
    RoweRawParty group;
    for (unsigned count = 0; count <= 6; count++) {
        fixture.ewram[COUNT_OFFSET] = (uint8_t)count;
        ReadControl c = {0};
        CHECK(rowe_raw_read_player_party(read_fixture, &c, &group));
        CHECK(c.calls == 5 && group.count == count);
        for (unsigned i = 0; i < count; i++) {
            CHECK(group.members[i].pid == 0x82104365u + i);
            CHECK(memcmp(group.members[i].bytes, fixture.ewram + PARTY_OFFSET + i*SLOT, SLOT) == 0);
        }
        for (unsigned i = count; i < 6; i++) cleared(&group.members[i], sizeof(group.members[i]));
    }
    fixture.ewram[COUNT_OFFSET] = 2;
    ReadControl c = {0}; CHECK(rowe_raw_read_player_party(read_fixture, &c, &group));
    uint8_t before[2][SLOT];
    memcpy(before[0], group.members[0].bytes, SLOT); memcpy(before[1], group.members[1].bytes, SLOT);
    memcpy(fixture.ewram + PARTY_OFFSET, before[1], SLOT);
    memcpy(fixture.ewram + PARTY_OFFSET + SLOT, before[0], SLOT);
    c = (ReadControl){0}; CHECK(rowe_raw_read_player_party(read_fixture, &c, &group));
    CHECK(group.members[0].pid == 0x82104366u && group.members[1].pid == 0x82104365u);
    CHECK(memcmp(group.members[0].bytes, before[1], SLOT) == 0);
    CHECK(memcmp(group.members[1].bytes, before[0], SLOT) == 0);

    /* Production selection never routes this shape into the diagnostic decoder.
     * No ROWE enum/config exists; unknown IDs fail the existing fixture allowlist. */
    load_full_fixture();
    MemoryReplay native;
    CHECK(!memory_fixture_replay(&fixture, GAME_UNKNOWN, &native));
    CHECK(!memory_fixture_replay(&fixture, (GbaGameId)255, &native));
    CHECK(pokemon_get_game_config((GbaGameId)255) == NULL);
    CHECK(memory_fixture_replay(&fixture, GAME_EMERALD, &native));
    CHECK(native.player.count == 0); /* Emerald's count/address, not ROWE's two records. */
    fixture.ewram[COUNT_OFFSET] = 0;
    c = (ReadControl){0}; CHECK(rowe_raw_read_player_party(read_fixture, &c, &group));
    CHECK(group.count == 0); /* A nonzero enemy count grants no enemy support. */
    for (unsigned i = 0; i < 6; i++) cleared(&group.members[i], sizeof(group.members[i]));
}

static void test_fixture_failures_and_coherence(void) {
    RoweRawParty out;
    for (unsigned mode = 0; mode < 3; mode++) {
        for (unsigned call = 1; call <= 5; call++) {
            load_full_fixture();
            ReadControl good = {0}; CHECK(rowe_raw_read_player_party(read_fixture, &good, &out));
            ReadControl bad = {0};
            if (mode == 0) bad.fail_call = call;
            if (mode == 1) bad.short_call = call;
            if (mode == 2) bad.long_call = call;
            CHECK(!rowe_raw_read_player_party(read_fixture, &bad, &out)); cleared(&out, sizeof(out));
        }
    }
    for (unsigned call = 2; call <= 5; call++) {
        load_full_fixture();
        ReadControl good = {0}; CHECK(rowe_raw_read_player_party(read_fixture, &good, &out));
        ReadControl bad = {.count_change=call};
        CHECK(!rowe_raw_read_player_party(read_fixture, &bad, &out)); cleared(&out, sizeof(out));
    }
    for (unsigned call = 3; call <= 4; call++) {
        load_full_fixture();
        ReadControl good = {0}; CHECK(rowe_raw_read_player_party(read_fixture, &good, &out));
        ReadControl bad = {.party_change=call}; /* Mutation in an EXCLUDED slot. */
        CHECK(!rowe_raw_read_player_party(read_fixture, &bad, &out)); cleared(&out, sizeof(out));
    }
    const uint8_t invalid[] = {7,255};
    for (unsigned i = 0; i < sizeof(invalid); i++) {
        load_full_fixture();
        ReadControl good = {0}; CHECK(rowe_raw_read_player_party(read_fixture, &good, &out));
        fixture.ewram[COUNT_OFFSET] = invalid[i];
        ReadControl bad = {0}; CHECK(!rowe_raw_read_player_party(read_fixture, &bad, &out));
        CHECK(bad.calls == 1); cleared(&out, sizeof(out));
    }
    load_full_fixture();
    ReadControl good = {0}; CHECK(rowe_raw_read_player_party(read_fixture, &good, &out));
    CHECK(!rowe_raw_read_player_party(NULL, &good, &out)); cleared(&out, sizeof(out));
    CHECK(!rowe_raw_read_player_party(read_fixture, NULL, &out)); cleared(&out, sizeof(out));
    good.calls = 0; CHECK(!rowe_raw_read_player_party(read_fixture, &good, NULL)); CHECK(good.calls == 0);

    /* Bounded production map: unmapped, short, and split-region spans cannot be read. */
    for (unsigned mode = 0; mode < 3; mode++) {
        load_full_fixture();
        ReadControl ok = {0}; CHECK(rowe_raw_read_player_party(read_fixture, &ok, &out));
        gba_memory_map_clear(&fixture.table);
        if (mode == 1) {
            CHECK(gba_memory_map_add(&fixture.table, fixture.ewram, 0x02000000u,
                                     PARTY_OFFSET + REGION - 1, 0,0,0,0));
        } else if (mode == 2) {
            unsigned split = PARTY_OFFSET + REGION/2;
            CHECK(gba_memory_map_add(&fixture.table, fixture.ewram, 0x02000000u, split, 0,0,0,0));
            CHECK(gba_memory_map_add(&fixture.table, fixture.ewram + split, 0x02000000u + split,
                                     sizeof(fixture.ewram) - split, 0,0,0,0));
        }
        ReadControl bad = {0}; CHECK(!rowe_raw_read_player_party(read_fixture, &bad, &out));
        cleared(&out, sizeof(out));
    }
}

int main(void) {
    CHECK(ROWE_RAW_SLOT_BYTES == 76 && ROWE_RAW_PARTY_BYTES == 456 && ROWE_RAW_CAPACITY == 6);
    CHECK(ROWE_RAW_PLAYER_COUNT_ADDRESS == 0x02025125u && ROWE_RAW_PLAYER_PARTY_ADDRESS == 0x02025128u);
    CHECK(mkdtemp(directory)); CHECK(atexit(cleanup) == 0);
    CHECK(snprintf(ewram_path, sizeof(ewram_path), "%s/ewram.bin", directory) > 0);
    CHECK(snprintf(iwram_path, sizeof(iwram_path), "%s/iwram.bin", directory) > 0);
    test_fields_and_boundaries();
    test_lengths_nulls_and_clear();
    test_fixture_counts_reorder_and_scope();
    test_fixture_failures_and_coherence();
    printf("ROWE raw decoder: %u assertions passed (invented bytes; no ROM or gameplay verdict).\n", checks);
    return 0;
}
