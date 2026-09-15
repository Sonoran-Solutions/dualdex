#ifndef DUALDEX_GBA_MEMORY_MAP_H
#define DUALDEX_GBA_MEMORY_MAP_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Emulated GBA address-space regions and bounds-checked reads.
 *
 * This module is deliberately free of any libretro or emulator dependency so that address
 * translation can be unit-tested on the host with synthetic region tables. libretro_host.c is
 * the only production caller: it copies the descriptors a core publishes through
 * RETRO_ENVIRONMENT_SET_MEMORY_MAPS into a table of this type and never retains the
 * caller-owned descriptor array.
 *
 * Unit discipline (never mixed implicitly):
 *   - `start` is an absolute emulated GBA address (e.g. 0x02000000 for EWRAM).
 *   - `length` is a byte count.
 *   - `base` is a host pointer into the core's own allocation.
 */

/** GBA address-space landmarks, for callers that need to name a region. */
#define DUALDEX_GBA_EWRAM_BASE 0x02000000u
#define DUALDEX_GBA_EWRAM_SIZE 0x00040000u
#define DUALDEX_GBA_IWRAM_BASE 0x03000000u
#define DUALDEX_GBA_IWRAM_SIZE 0x00008000u

/** Maximum number of regions a table can hold. */
#define DUALDEX_GBA_REGION_CAPACITY 16

typedef struct {
    const uint8_t* base;  // host pointer to the region's storage
    uint32_t start;       // absolute emulated GBA address of the first byte
    uint32_t length;      // region length in bytes
    uint32_t select;      // address bits that must match `start`; 0 means "always matches"
    uint32_t disconnect;  // address bits ignored during translation
    uint32_t offset;      // byte offset added after translation
    uint64_t flags;       // opaque descriptor flags (RETRO_MEMDESC_*) for diagnostics
    bool     present;     // false for an unused slot
} DualDexGbaRegion;

typedef struct {
    DualDexGbaRegion regions[DUALDEX_GBA_REGION_CAPACITY];
    size_t count;
} DualDexGbaRegionTable;

/** Empty the table. Safe with a NULL argument. */
void gba_memory_map_clear(DualDexGbaRegionTable* table);

/**
 * Append a region. Slots beyond DUALDEX_GBA_REGION_CAPACITY are dropped, and a region with no
 * base pointer or zero length is ignored: an unmappable descriptor can only produce bad reads.
 *
 * @return true when the region was stored.
 */
bool gba_memory_map_add(
    DualDexGbaRegionTable* table,
    const uint8_t* base,
    uint32_t start,
    uint32_t length,
    uint32_t select,
    uint32_t disconnect,
    uint32_t offset,
    uint64_t flags
);

/** Number of stored regions. Returns 0 for a NULL table. */
size_t gba_memory_map_count(const DualDexGbaRegionTable* table);

/**
 * Copy the region at @p index. Returns false when the index is out of range.
 */
bool gba_memory_map_get(const DualDexGbaRegionTable* table, size_t index, DualDexGbaRegion* out);

/**
 * Resolve an absolute GBA @p address to a host pointer for @p length bytes.
 *
 * Fails closed (returns NULL and stores NULL) unless one single region covers the entire
 * request:
 *   - the address must satisfy `(address & select) == (start & select)` (when select != 0);
 *   - `(address & ~disconnect) - start + offset`, plus @p length, must fit inside `length`.
 *
 * A request that starts in one region and ends in another is rejected rather than stitched,
 * and a mirrored/aliased address beyond the declared length is rejected rather than wrapped.
 */
const uint8_t* gba_memory_map_resolve(
    const DualDexGbaRegionTable* table,
    uint32_t address,
    size_t length,
    const uint8_t** out_base
);

/**
 * Bounds-checked read from an absolute GBA @p address into @p out.
 *
 * On failure @p out is left untouched and false is returned.
 */
bool gba_memory_map_read(
    const DualDexGbaRegionTable* table,
    uint32_t address,
    void* out,
    size_t length
);

#ifdef __cplusplus
}
#endif

#endif // DUALDEX_GBA_MEMORY_MAP_H
