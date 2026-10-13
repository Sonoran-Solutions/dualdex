#include "memory_fixture.h"

#include <stdio.h>
#include <string.h>

MemoryFixtureResult memory_fixture_load(MemoryFixture* fixture,
                                       const MemoryFixtureRegion* regions, size_t count) {
    if (!fixture) return FIXTURE_INVALID_REGION;
    memset(fixture, 0, sizeof(*fixture));
    MemoryFixtureResult result = FIXTURE_INVALID_REGION;
    if (!regions || count != 2) return result;

    if (regions[0].start == regions[1].start) return result;
    for (size_t i = 0; i < count; i++) {
        if (!regions[i].path || (regions[i].start != DUALDEX_GBA_EWRAM_BASE &&
                                regions[i].start != DUALDEX_GBA_IWRAM_BASE)) return result;
    }
    for (size_t i = 0; i < count; i++) {
        uint8_t* bytes;
        size_t length;
        switch (regions[i].start) {
            case DUALDEX_GBA_EWRAM_BASE:
                bytes = fixture->ewram; length = sizeof(fixture->ewram); break;
            case DUALDEX_GBA_IWRAM_BASE:
                bytes = fixture->iwram; length = sizeof(fixture->iwram); break;
            default: goto fail;
        }
        FILE* file = fopen(regions[i].path, "rb");
        if (!file) { result = FIXTURE_IO_ERROR; goto fail; }
        size_t got = fread(bytes, 1, length, file);
        int extra = fgetc(file);
        bool io_error = ferror(file) != 0;
        if (fclose(file) != 0) io_error = true;
        if (io_error) { result = FIXTURE_IO_ERROR; goto fail; }
        if (got != length || extra != EOF) { result = FIXTURE_BAD_LENGTH; goto fail; }
        if (!gba_memory_map_add(&fixture->table, bytes, regions[i].start, (uint32_t)length,
                                0xFF000000u, 0, 0, 0)) goto fail;
    }
    return FIXTURE_OK;
fail:
    memset(fixture, 0, sizeof(*fixture));
    return result;
}

bool memory_fixture_read(void* fixture, uint32_t address, uint8_t* out, size_t length) {
    if (!fixture) return false;
    return gba_memory_map_read(&((MemoryFixture*)fixture)->table, address, out, length);
}

const uint8_t* memory_fixture_ewram(const MemoryFixture* fixture) {
    if (!fixture) return NULL;
    return gba_memory_map_resolve(&fixture->table, DUALDEX_GBA_EWRAM_BASE,
                                  DUALDEX_GBA_EWRAM_SIZE, NULL);
}

bool memory_fixture_replay(MemoryFixture* fixture, GbaGameId game, MemoryReplay* out) {
    pokemon_reader_reset();
    if (!out) return false;
    memset(out, 0, sizeof(*out));
    out->player.active_battler_slot = out->player.active_battler_index = -1;
    out->enemy.active_battler_slot = out->enemy.active_battler_index = -1;
    out->battle.kind = BATTLE_KIND_UNKNOWN;
    const uint8_t* ewram = memory_fixture_ewram(fixture);
    if (!ewram) return false;
    /* ponytail: replay covers three existing layouts; add a layout here only with its
     * own fixture assertions. This allowlist is test coverage, never runtime ROM trust. */
    if (game != GAME_FIRERED && game != GAME_EMERALD && game != GAME_HEART_AND_SOUL)
        return false;
    const GameMemoryConfig* config = pokemon_get_game_config(game);
    pokemon_read_player_party_gba(memory_fixture_read, fixture, ewram,
                                 DUALDEX_GBA_EWRAM_SIZE, config, &out->player);
    pokemon_read_enemy_party_gba(memory_fixture_read, fixture, ewram,
                                DUALDEX_GBA_EWRAM_SIZE, config, &out->enemy);
    pokemon_read_player_location_gba(memory_fixture_read, fixture, ewram,
                                    DUALDEX_GBA_EWRAM_SIZE, config, &out->location);
    pokemon_read_battle_lifecycle(memory_fixture_read, fixture, ewram,
                                 DUALDEX_GBA_EWRAM_SIZE, config, &out->battle);
    return true;
}
