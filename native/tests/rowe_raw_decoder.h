#ifndef DUALDEX_TEST_ROWE_RAW_DECODER_H
#define DUALDEX_TEST_ROWE_RAW_DECODER_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/* Diagnostic, host-only storage contract from PR #192. No ROM permission or
 * semantic Pokémon verdict. This header/source are absent from the app target. */
enum { ROWE_RAW_SLOT_BYTES = 76, ROWE_RAW_CAPACITY = 6, ROWE_RAW_PARTY_BYTES = 456 };
#define ROWE_RAW_PLAYER_COUNT_ADDRESS UINT32_C(0x02025125)
#define ROWE_RAW_PLAYER_PARTY_ADDRESS UINT32_C(0x02025128)

typedef struct {
    uint8_t bytes[ROWE_RAW_SLOT_BYTES]; /* Preserve every unknown/reserved bit. */
    uint32_t pid, otid;
    uint8_t nickname[12]; /* Raw bytes, not a terminated/displayable string. */
    uint16_t moves[4];
    uint32_t experience;
    uint16_t species;
    uint8_t form, nature, pp[4];
    uint32_t status;
    uint8_t level;
    uint16_t hp, max_hp, attack, defense, speed, sp_attack, sp_defense;
} RoweRawSlot;

typedef struct {
    uint8_t count;
    RoweRawSlot members[ROWE_RAW_CAPACITY]; /* Excluded slots are cleared. */
} RoweRawParty;

/* Complete writable output required. On failure every output byte is cleared;
 * a NULL output returns false. Input/output overlap is supported. No domain,
 * checksum, encryption, text, PID-derived or ID-mapping interpretation. */
bool rowe_raw_decode_slot(const uint8_t* bytes, size_t length, RoweRawSlot* out);
bool rowe_raw_decode_party(const uint8_t* bytes, size_t length, uint8_t count,
                           RoweRawParty* out);

/* Callback must bound writes to length, return the actual byte count copied,
 * and use a paused/immutable memory generation for the entire call. Short,
 * failed, or over-reported reads fail. Context and output must be non-NULL.
 * Repeated reads detect observed mismatches, not running-core races or ABA.
 * Explicit PLAYER-only entry point: no layout selection or enemy API. */
typedef size_t (*RoweRawRead)(void* context, uint32_t address, uint8_t* out, size_t length);
bool rowe_raw_read_player_party(RoweRawRead read, void* context, RoweRawParty* out);

#endif
