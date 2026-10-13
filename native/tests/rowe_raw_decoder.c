#include "rowe_raw_decoder.h"

#include <string.h>

_Static_assert(ROWE_RAW_SLOT_BYTES * ROWE_RAW_CAPACITY == ROWE_RAW_PARTY_BYTES,
               "party capacity must cover exactly six slots");

static uint16_t le16(const uint8_t* p) {
    return (uint16_t)((uint32_t)p[0] | ((uint32_t)p[1] << 8));
}

static uint32_t le32(const uint8_t* p) {
    return (uint32_t)p[0] | ((uint32_t)p[1] << 8) |
           ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24);
}

bool rowe_raw_decode_slot(const uint8_t* bytes, size_t length, RoweRawSlot* out) {
    if (!out) return false;
    if (!bytes || length != ROWE_RAW_SLOT_BYTES) {
        memset(out, 0, sizeof(*out));
        return false;
    }
    RoweRawSlot decoded;
    memset(&decoded, 0, sizeof(decoded));
    memcpy(decoded.bytes, bytes, ROWE_RAW_SLOT_BYTES);
    decoded.pid = le32(bytes);
    decoded.otid = le32(bytes + 4);
    memcpy(decoded.nickname, bytes + 8, sizeof(decoded.nickname));
    uint32_t word20 = le32(bytes + 0x14);
    uint32_t word24 = le32(bytes + 0x18);
    uint32_t word28 = le32(bytes + 0x1c);
    decoded.moves[0] = (uint16_t)(word20 & 0x3ffu);
    decoded.experience = (word20 >> 10) & 0x1fffffu;
    decoded.moves[1] = (uint16_t)(word24 & 0x3ffu);
    decoded.moves[2] = (uint16_t)((word24 >> 10) & 0x3ffu);
    decoded.species = (uint16_t)(word28 & 0xfffu);
    decoded.form = (uint8_t)((word28 >> 12) & 0x1fu);
    decoded.moves[3] = (uint16_t)((word28 >> 17) & 0x3ffu);
    decoded.nature = (uint8_t)((bytes[0x27] >> 2) & 0x1fu);
    memcpy(decoded.pp, bytes + 0x34, sizeof(decoded.pp));
    decoded.status = le32(bytes + 0x38);
    decoded.level = bytes[0x3c];
    decoded.hp = le16(bytes + 0x3e);
    decoded.max_hp = le16(bytes + 0x40);
    decoded.attack = le16(bytes + 0x42);
    decoded.defense = le16(bytes + 0x44);
    decoded.speed = le16(bytes + 0x46);
    decoded.sp_attack = le16(bytes + 0x48);
    decoded.sp_defense = le16(bytes + 0x4a);
    memcpy(out, &decoded, sizeof(decoded));
    return true;
}

bool rowe_raw_decode_party(const uint8_t* bytes, size_t length, uint8_t count,
                           RoweRawParty* out) {
    if (!out) return false;
    if (!bytes || length != ROWE_RAW_PARTY_BYTES || count > ROWE_RAW_CAPACITY) {
        memset(out, 0, sizeof(*out));
        return false;
    }
    RoweRawParty decoded;
    memset(&decoded, 0, sizeof(decoded));
    for (uint8_t i = 0; i < count; i++) {
        if (!rowe_raw_decode_slot(bytes + i * ROWE_RAW_SLOT_BYTES, ROWE_RAW_SLOT_BYTES,
                                  &decoded.members[i])) {
            memset(out, 0, sizeof(*out));
            return false;
        }
    }
    decoded.count = count;
    memcpy(out, &decoded, sizeof(decoded));
    return true;
}

bool rowe_raw_read_player_party(RoweRawRead read, void* context, RoweRawParty* out) {
    if (!out) return false;
    uint8_t before, after, final;
    uint8_t first[ROWE_RAW_PARTY_BYTES], second[ROWE_RAW_PARTY_BYTES];
    if (!read || !context ||
        read(context, ROWE_RAW_PLAYER_COUNT_ADDRESS, &before, 1) != 1 ||
        before > ROWE_RAW_CAPACITY ||
        read(context, ROWE_RAW_PLAYER_PARTY_ADDRESS, first, sizeof(first)) != sizeof(first) ||
        read(context, ROWE_RAW_PLAYER_COUNT_ADDRESS, &after, 1) != 1 || after != before ||
        read(context, ROWE_RAW_PLAYER_PARTY_ADDRESS, second, sizeof(second)) != sizeof(second) ||
        read(context, ROWE_RAW_PLAYER_COUNT_ADDRESS, &final, 1) != 1 || final != before ||
        memcmp(first, second, sizeof(first)) != 0) {
        memset(out, 0, sizeof(*out));
        return false;
    }
    return rowe_raw_decode_party(first, sizeof(first), before, out);
}
