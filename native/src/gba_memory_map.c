#include "gba_memory_map.h"

#include <string.h>

void gba_memory_map_clear(DualDexGbaRegionTable* table) {
    if (!table) return;
    memset(table, 0, sizeof(*table));
}

bool gba_memory_map_add(
    DualDexGbaRegionTable* table,
    const uint8_t* base,
    uint32_t start,
    uint32_t length,
    uint32_t select,
    uint32_t disconnect,
    uint32_t offset,
    uint64_t flags
) {
    if (!table) return false;
    if (!base || length == 0) return false;
    if (table->count >= DUALDEX_GBA_REGION_CAPACITY) return false;

    DualDexGbaRegion* region = &table->regions[table->count];
    region->base = base;
    region->start = start;
    region->length = length;
    region->select = select;
    region->disconnect = disconnect;
    region->offset = offset;
    region->flags = flags;
    region->present = true;
    table->count++;
    return true;
}

size_t gba_memory_map_count(const DualDexGbaRegionTable* table) {
    return table ? table->count : 0;
}

bool gba_memory_map_get(const DualDexGbaRegionTable* table, size_t index, DualDexGbaRegion* out) {
    if (!table || !out) return false;
    if (index >= table->count) return false;
    *out = table->regions[index];
    return true;
}

const uint8_t* gba_memory_map_resolve(
    const DualDexGbaRegionTable* table,
    uint32_t address,
    size_t length,
    const uint8_t** out_base
) {
    if (out_base) *out_base = NULL;
    if (!table || length == 0) return NULL;

    // Reject requests that wrap the 32-bit address space before any arithmetic.
    if (length > (size_t)(0xFFFFFFFFu - address) + 1u) return NULL;

    for (size_t i = 0; i < table->count; i++) {
        const DualDexGbaRegion* region = &table->regions[i];
        if (!region->present || !region->base) continue;

        // `select` names the address bits that must equal the region's `start` bits. A zero
        // select is documented as "the whole address space is described by start/length", so
        // the exact range check below is what keeps such a region honest.
        if (region->select != 0 && (address & region->select) != (region->start & region->select)) {
            continue;
        }

        uint32_t masked = address & ~region->disconnect;
        if (masked < region->start) continue;

        uint32_t delta = masked - region->start;
        if (delta > region->length) continue;
        if (region->offset > 0xFFFFFFFFu - delta) continue;

        uint32_t region_offset = delta + region->offset;
        if (region_offset > region->length) continue;
        if (length > (size_t)(region->length - region_offset)) continue;

        if (out_base) *out_base = region->base + region_offset;
        return region->base + region_offset;
    }

    return NULL;
}

bool gba_memory_map_read(
    const DualDexGbaRegionTable* table,
    uint32_t address,
    void* out,
    size_t length
) {
    if (!out || length == 0) return false;

    const uint8_t* base = NULL;
    if (!gba_memory_map_resolve(table, address, length, &base) || !base) return false;

    memcpy(out, base, length);
    return true;
}
