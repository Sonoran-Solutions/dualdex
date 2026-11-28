#include "rowe_identity_adapter.h"

#include <string.h>

static bool text_valid(const char text[ROWE_ID_TEXT_BYTES]) {
    return text[0] && memchr(text, 0, ROWE_ID_TEXT_BYTES);
}

static bool release_valid(const RoweIdentityRelease* release) {
    static const uint8_t zero[32];
    return text_valid(release->release) && release->size &&
           memcmp(release->sha256, zero, sizeof(zero)) != 0;
}

static bool release_equal(const RoweIdentityRelease* a, const RoweIdentityRelease* b) {
    return release_valid(a) && release_valid(b) && a->size == b->size &&
           strcmp(a->release, b->release) == 0 &&
           memcmp(a->sha256, b->sha256, sizeof(a->sha256)) == 0;
}

static RoweIdentityStatus refuse(RoweIdentityParty* out, RoweIdentityStatus status,
                                 RoweStaticStatus reason) {
    memset(out, 0, sizeof(*out));
    out->status = status;
    out->refusal = reason;
    return status;
}

static RoweStaticStatus validate_resolution(RoweStaticStatus status,
                                            const RoweStaticResolution* r,
                                            const RoweRawSlot* raw) {
    if (status < ROWE_STATIC_REFUSED || status > ROWE_CONTRADICTORY_RESOLUTION)
        return ROWE_CONTRADICTORY_RESOLUTION;
    if (status != ROWE_STATIC_VERIFIED) {
        static const char empty[ROWE_ID_TEXT_BYTES];
        if (r->raw_species || r->raw_form || r->resolved_species || r->metadata_key ||
            memcmp(r->name, empty, sizeof(empty))) return ROWE_CONTRADICTORY_RESOLUTION;
        return status;
    }
    if (r->raw_species != raw->species || r->raw_form != raw->form ||
        !r->resolved_species || r->resolved_species > 4095 ||
        r->metadata_key != r->resolved_species) return ROWE_CONTRADICTORY_RESOLUTION;
    if (!text_valid(r->name)) return ROWE_MISSING_NAME;
    return ROWE_STATIC_VERIFIED;
}

RoweIdentityStatus rowe_identity_read_player_party(
    const RoweIdentityAdmission* a, const RoweIdentityMetadata* m,
    RoweRawRead read, void* read_context, RoweIdentityParty* out) {
    if (!out) return ROWE_ID_REQUEST_INVALID;
    if (!a || !read || !read_context)
        return refuse(out, ROWE_ID_REQUEST_INVALID, ROWE_STATIC_REFUSED);
    if (!release_equal(&a->intended, &a->observed))
        return refuse(out, ROWE_ID_ROM_UNVERIFIED, ROWE_STATIC_REFUSED);
    if (a->configuration != ROWE_CONFIG_SYNTHETIC_ADMITTED)
        return refuse(out, ROWE_ID_CONFIGURATION_UNVERIFIED, ROWE_STATIC_REFUSED);
    if (!a->immutable_generation_owned)
        return refuse(out, ROWE_ID_OWNERSHIP_UNVERIFIED, ROWE_STATIC_REFUSED);
    if (!a->player_only)
        return refuse(out, ROWE_ID_SCOPE_UNSUPPORTED, ROWE_STATIC_REFUSED);
    if (!m || !m->resolve || !m->context || !release_equal(&a->intended, &m->release) ||
        !text_valid(a->metadata_revision) || !text_valid(m->revision) ||
        strcmp(a->metadata_revision, m->revision))
        return refuse(out, ROWE_ID_METADATA_UNAVAILABLE, ROWE_METADATA_UNAVAILABLE);

    RoweRawParty raw;
    if (!rowe_raw_read_player_party(read, read_context, &raw))
        return refuse(out, ROWE_ID_SNAPSHOT_INVALID, ROWE_STATIC_REFUSED);
    RoweIdentityParty candidate;
    memset(&candidate, 0, sizeof(candidate));
    for (uint8_t i = 0; i < raw.count; i++) {
        const RoweRawSlot* slot = &raw.members[i];
        if (!slot->species)
            return refuse(out, ROWE_ID_IDENTITY_UNSUPPORTED, ROWE_UNKNOWN_SPECIES);
        RoweStaticResolution resolved;
        memset(&resolved, 0, sizeof(resolved));
        RoweStaticStatus status = m->resolve(m->context, slot->species, slot->form, &resolved);
        status = validate_resolution(status, &resolved, slot);
        if (status != ROWE_STATIC_VERIFIED)
            return refuse(out, status == ROWE_METADATA_UNAVAILABLE ?
                          ROWE_ID_METADATA_UNAVAILABLE : ROWE_ID_IDENTITY_UNSUPPORTED, status);
        RoweIdentityMember* member = &candidate.members[i];
        member->slot = i;
        member->raw_species = slot->species;
        member->raw_form = slot->form;
        member->resolved_species = resolved.resolved_species;
        member->metadata_key = resolved.metadata_key;
        memcpy(member->name, resolved.name, strlen(resolved.name) + 1);
        member->static_status = ROWE_STATIC_VERIFIED;
        member->display_authority = ROWE_NOT_VERIFIED;
        member->effective_identity = ROWE_NOT_VERIFIED;
        memcpy(member->private_raw_record, slot->bytes, sizeof(slot->bytes));
    }
    candidate.count = raw.count;
    candidate.status = raw.count ? ROWE_ID_PARTY_AVAILABLE : ROWE_ID_PARTY_EMPTY;
    memcpy(out, &candidate, sizeof(candidate));
    return out->status;
}
