#ifndef DUALDEX_TEST_MEMORY_FIXTURE_H
#define DUALDEX_TEST_MEMORY_FIXTURE_H

#include "gba_memory_map.h"
#include "pokemon_reader.h"

/* Host-only, owned storage. Do not copy this object after loading: table points into it. */
typedef struct {
    _Alignas(4) uint8_t ewram[DUALDEX_GBA_EWRAM_SIZE];
    _Alignas(4) uint8_t iwram[DUALDEX_GBA_IWRAM_SIZE];
    DualDexGbaRegionTable table;
} MemoryFixture;

typedef struct {
    uint32_t start; /* Absolute GBA base, not a host pointer or relative offset. */
    const char* path;
} MemoryFixtureRegion;

typedef enum {
    FIXTURE_OK,
    FIXTURE_INVALID_REGION,
    FIXTURE_IO_ERROR,
    FIXTURE_BAD_LENGTH
} MemoryFixtureResult;

/* Exactly one full EWRAM and one full IWRAM file, in either order. Any failure clears
 * ALL storage/mappings, including a previously loaded fixture. No padding or truncation. */
MemoryFixtureResult memory_fixture_load(MemoryFixture* fixture,
                                       const MemoryFixtureRegion* regions, size_t count);
bool memory_fixture_read(void* fixture, uint32_t address, uint8_t* out, size_t length);
const uint8_t* memory_fixture_ewram(const MemoryFixture* fixture);

/* Independent snapshot replay: reset production reader caches, then call the same
 * production entry points as JNI. No profile overrides or replacement decoders. */
typedef struct {
    PartySnapshot player;
    PartySnapshot enemy;
    PlayerLocationRaw location;
    BattleStateRaw battle;
} MemoryReplay;
bool memory_fixture_replay(MemoryFixture* fixture, GbaGameId game, MemoryReplay* out);

#endif
