/* Host-only hypothesis, evidence revision 1; see docs/ROWE_2_1_9_1_SPIKE.md.
 * Independently written from the cited offset/bit-width facts. No upstream code,
 * ROM bytes or captured fixtures. Never linked into the app or production reader.
 */
#include "pokemon_text.h"
#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

enum { CANDIDATE_SLOT_BYTES = 0x4c };

typedef struct {
    uint32_t pid, otid;
    uint16_t species_id, moves[4], hp, max_hp, stats[5];
    uint8_t form_id, level, pp[4], nickname_bytes[12];
    char nickname_preview[49]; /* Existing Gen III codec; encoding still unverified. */
} CandidateFields;

/* Bit positions are relative to the slot, least significant bit first.
 * All call sites below are constants within the checked 76-byte input. */
static uint32_t bits(const uint8_t *slot, unsigned start, unsigned width) {
    uint32_t value = 0;
    for (unsigned i = 0; i < width; ++i)
        value |= (uint32_t)((slot[(start + i) / 8] >> ((start + i) % 8)) & 1u) << i;
    return value;
}

/* True means only "plausible under this hypothesis", never verified or trusted.
 * Caller supplies ONE immutable slot, not a live address or an arbitrary RAM scan.
 * Scope: ordinary non-egg slots only; exact-release egg/level/stat semantics and
 * a coherent snapshot protocol must be established before expanding this boundary.
 */
static bool decode_candidate(const uint8_t *slot, size_t size, CandidateFields *out) {
    if (!out) return false;
    memset(out, 0, sizeof(*out));
    if (!slot || size != CANDIDATE_SLOT_BYTES) return false;
    if (slot[0x27] & 0x80) return false; /* Reported egg flag: unsupported here. */

    CandidateFields result = {0};
    result.pid = bits(slot, 0, 32);
    result.otid = bits(slot, 32, 32);
    result.species_id = (uint16_t)bits(slot, 0x1c * 8, 12);
    result.form_id = (uint8_t)bits(slot, 0x1c * 8 + 12, 5);
    const unsigned move_bits[] = {0x14 * 8, 0x18 * 8, 0x18 * 8 + 10, 0x1c * 8 + 17};
    for (unsigned i = 0; i < 4; ++i) {
        result.moves[i] = (uint16_t)bits(slot, move_bits[i], 10);
        result.pp[i] = slot[0x34 + i]; /* Preserve full bytes; no H&S 7-bit mask. */
    }
    result.level = slot[0x3c];
    result.hp = (uint16_t)bits(slot, 0x3e * 8, 16);
    result.max_hp = (uint16_t)bits(slot, 0x40 * 8, 16);
    if (!result.species_id || !result.level || result.level > 100 ||
        !result.max_hp || result.hp > result.max_hp) return false;
    for (unsigned i = 0; i < 5; ++i) {
        result.stats[i] = (uint16_t)bits(slot, (0x42 + 2 * i) * 8, 16);
        if (!result.stats[i]) return false;
    }
    memcpy(result.nickname_bytes, slot + 8, sizeof(result.nickname_bytes));
    pokemon_decode_string(result.nickname_bytes, sizeof(result.nickname_bytes),
                          result.nickname_preview, sizeof(result.nickname_preview));
    *out = result;
    return true;
}

/* Arbitrary synthetic values, intentionally not a real Pokémon or RAM capture.
 * Literal bytes keep the fixture independent of the decoder's bit extraction.
 * w14=DEADBFFF; w18=FFFAAA55; w1c=84011ABC. Unrelated packed bits are nonzero.
 */
static const uint8_t fixture[CANDIDATE_SLOT_BYTES] = {
    [0x00] = 0x44, 0x33, 0x22, 0x11, 0x88, 0x77, 0x66, 0x55,
    [0x08] = 0xbb, 0xbc, 0xbd, 0xbe, 0xbf, 0xc0, 0xc1, 0xc2, 0xc3, 0xc4, 0xc5, 0xc6,
    [0x14] = 0xff, 0xbf, 0xad, 0xde, 0x55, 0xaa, 0xfa, 0xff, 0xbc, 0x1a, 0x01, 0x84,
    [0x34] = 0xff, 0x80, 0x01, 0x00,
    [0x3c] = 42,
    [0x3e] = 0x23, 0x01, 0x34, 0x02,
    [0x42] = 101, 0, 102, 0, 103, 0, 104, 0, 105, 0,
};

static void rejects(const uint8_t *slot, size_t size) {
    CandidateFields out;
    memset(&out, 0xa5, sizeof(out));
    assert(!decode_candidate(slot, size, &out));
    const uint8_t *bytes = (const uint8_t *)&out;
    for (size_t i = 0; i < sizeof(out); ++i) assert(bytes[i] == 0);
}

int main(void) {
    CandidateFields out;
    assert(decode_candidate(fixture, sizeof(fixture), &out));
    assert(out.pid == 0x11223344 && out.otid == 0x55667788);
    assert(out.species_id == 0xabc && out.form_id == 17); /* No 11-bit truncation. */
    assert(out.moves[0] == 1023 && out.moves[1] == 597 &&
           out.moves[2] == 682 && out.moves[3] == 512);
    assert(out.pp[0] == 255 && out.pp[1] == 128 && out.pp[2] == 1 && out.pp[3] == 0);
    assert(out.level == 42 && out.hp == 291 && out.max_hp == 564);
    for (unsigned i = 0; i < 5; ++i) assert(out.stats[i] == 101 + i);
    assert(memcmp(out.nickname_bytes, fixture + 8, 12) == 0);
    assert(strcmp(out.nickname_preview, "ABCDEFGHIJKL") == 0); /* Full-width, no terminator. */

    rejects(NULL, sizeof(fixture));
    assert(!decode_candidate(fixture, sizeof(fixture), NULL));
    for (size_t n = 0; n < sizeof(fixture); ++n) rejects(fixture, n);
    rejects(fixture, sizeof(fixture) + 1);
    rejects(fixture, 100); /* Never reinterpret a vanilla/H&S-sized record. */
    rejects(fixture, SIZE_MAX);

    uint8_t slot[CANDIDATE_SLOT_BYTES];
    memcpy(slot, fixture, sizeof(slot));
    slot[0x1c] = 0; slot[0x1d] &= 0xf0;
    rejects(slot, sizeof(slot)); /* Packed neighbours cannot create a species. */
    const unsigned invalid_zero_bytes[] = {0x3c, 0x42, 0x44, 0x46, 0x48, 0x4a};
    for (unsigned i = 0; i < sizeof(invalid_zero_bytes) / sizeof(*invalid_zero_bytes); ++i) {
        memcpy(slot, fixture, sizeof(slot));
        slot[invalid_zero_bytes[i]] = 0;
        rejects(slot, sizeof(slot));
    }
    memcpy(slot, fixture, sizeof(slot)); slot[0x3c] = 101; rejects(slot, sizeof(slot));
    memcpy(slot, fixture, sizeof(slot)); slot[0x27] = 0x80; rejects(slot, sizeof(slot));
    memcpy(slot, fixture, sizeof(slot)); slot[0x40] = slot[0x41] = 0; rejects(slot, sizeof(slot));
    memcpy(slot, fixture, sizeof(slot)); slot[0x3e] = slot[0x3f] = 0xff; rejects(slot, sizeof(slot));

    /* Zero identity values and a fainted slot are not inherently corruption. */
    memcpy(slot, fixture, sizeof(slot));
    memset(slot, 0, 8); slot[0x3e] = slot[0x3f] = 0; slot[8] = 0xff;
    assert(decode_candidate(slot, sizeof(slot), &out));
    assert(out.pid == 0 && out.otid == 0 && out.hp == 0 && out.nickname_preview[0] == '\0');
    /* Reading an unaligned byte buffer must not rely on host struct layout. */
    uint8_t unaligned[CANDIDATE_SLOT_BYTES + 1];
    memcpy(unaligned + 1, fixture, sizeof(fixture));
    assert(decode_candidate(unaligned + 1, sizeof(fixture), &out));
    assert(out.species_id == 0xabc && out.moves[3] == 512);
    puts("R.O.W.E. hypothesis v1: synthetic decoder checks passed (NOT runtime validation)");
    return 0;
}
