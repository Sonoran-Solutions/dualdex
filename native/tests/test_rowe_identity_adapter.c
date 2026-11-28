#include "rowe_identity_adapter.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static unsigned checks, scenarios;
#define CHECK(c) do { checks++; if (!(c)) { \
    fprintf(stderr, "%s:%d: %s\n", __FILE__, __LINE__, #c); exit(1); \
} } while (0)

typedef struct {
    uint8_t bytes[456], count;
    unsigned reads, lookups, fail_read, change_count, change_bytes, fail_lookup;
    size_t returned_length;
    RoweStaticStatus refusal;
    unsigned contradiction;
} Fixture;
static Fixture f;
static RoweIdentityAdmission admission;
static RoweIdentityMetadata metadata;
static RoweIdentityParty out;

/* Invented writer, not another decoder. Reserved bytes deliberately nonzero. */
static void record(unsigned slot, unsigned species, unsigned form) {
    uint8_t* p = f.bytes + slot * 76;
    memset(p, 0xa5, 76);
    unsigned word = 0xf8000000u | species | (form << 12);
    for (unsigned i = 0; i < 4; i++) p[28 + i] = (uint8_t)(word >> (8 * i));
}

static size_t read_player(void* context, uint32_t address, uint8_t* bytes, size_t length) {
    CHECK(context == &f);
    unsigned call = ++f.reads;
    CHECK(call <= 5);
    CHECK(address == (call % 2 ? 0x02025125u : 0x02025128u));
    CHECK(length == (call % 2 ? 1u : 456u)); /* No enemy/scan route permitted. */
    if (call == f.fail_read) return f.returned_length;
    if (call == f.change_count) f.count++;
    if (call == f.change_bytes) f.bytes[455] ^= 0x80;
    if (call % 2) *bytes = f.count;
    else memcpy(bytes, f.bytes, length);
    return length;
}

static RoweStaticStatus resolve(void* context, uint16_t species, uint8_t form,
                                RoweStaticResolution* r) {
    CHECK(context == &f);
    f.lookups++;
    if (f.lookups == f.fail_lookup) return f.refusal;
    /* Tiny invented form table: both raw aliases map to artificial entry 3. */
    static const uint16_t forms[] = {1, 3};
    if (species != 1 && species != 3) return ROWE_UNKNOWN_SPECIES;
    if (form >= sizeof(forms) / sizeof(forms[0])) return ROWE_INVALID_FORM;
    r->raw_species = species;
    r->raw_form = form;
    r->resolved_species = forms[form];
    r->metadata_key = r->resolved_species;
    strcpy(r->name, form ? "Synthetic Variant" : "Synthetic Base");
    switch (f.contradiction) {
        case 1: r->raw_species++; break;
        case 2: r->raw_form++; break;
        case 3: r->metadata_key++; break;
        case 4: r->resolved_species = r->metadata_key = 0; break;
        case 5: r->resolved_species = r->metadata_key = 4096; break;
        case 6: r->name[0] = 0; break;
        case 7: memset(r->name, 'x', sizeof(r->name)); break;
        case 8: return ROWE_UNKNOWN_SPECIES; /* Refusal with a success payload. */
        case 9: return (RoweStaticStatus)999;
        case 10: memset(r->name, 'x', sizeof(r->name) - 1); break;
    }
    return ROWE_STATIC_VERIFIED;
}

static void setup(void) {
    memset(&f, 0, sizeof(f));
    f.count = 2;
    for (unsigned i = 0; i < 6; i++) record(i, i % 2 ? 3 : 1, 1);
    memset(&admission, 0, sizeof(admission));
    strcpy(admission.intended.release, "Invented release for ROWE ABI composition");
    admission.intended.size = 33554432;
    memset(admission.intended.sha256, 0x12, 32);
    admission.observed = admission.intended;
    strcpy(admission.metadata_revision, "invented-metadata-v1");
    admission.configuration = ROWE_CONFIG_SYNTHETIC_ADMITTED;
    admission.immutable_generation_owned = admission.player_only = true;
    memset(&metadata, 0, sizeof(metadata));
    metadata.release = admission.intended;
    strcpy(metadata.revision, admission.metadata_revision);
    metadata.resolve = resolve;
    metadata.context = &f;
}

static RoweIdentityStatus run(void) {
    scenarios++;
    return rowe_identity_read_player_party(&admission, &metadata, read_player, &f, &out);
}

static void cleared(void) {
    RoweIdentityMember zero[6] = {0};
    CHECK(out.count == 0);
    CHECK(memcmp(out.members, zero, sizeof(zero)) == 0);
}

static void reject(RoweIdentityStatus status, bool before_read) {
    CHECK(run() == status);
    CHECK(out.status == status);
    cleared();
    if (before_read) CHECK(f.reads == 0 && f.lookups == 0);
}

static void test_admission(void) {
    for (unsigned mode = 0; mode < 23; mode++) {
        setup(); CHECK(run() == ROWE_ID_PARTY_AVAILABLE); /* stale output */
        f.reads = f.lookups = 0;
        RoweIdentityStatus status = ROWE_ID_ROM_UNVERIFIED;
        switch (mode) {
            case 0: admission.observed.sha256[31] ^= 1; break; /* correct size */
            case 1: admission.observed.size--; break;
            case 2: admission.observed.release[0] ^= 1; break;
            case 3: admission.intended.release[0] = 0; break;
            case 4: memset(admission.observed.release, 'x', 64); break;
            case 5: memset(admission.intended.sha256, 0, 32); break;
            case 6: admission.intended.size = admission.observed.size = 0; break;
            case 7: admission.configuration = ROWE_CONFIG_UNKNOWN;
                    status = ROWE_ID_CONFIGURATION_UNVERIFIED; break;
            case 8: admission.configuration = ROWE_CONFIG_UNSUPPORTED;
                    status = ROWE_ID_CONFIGURATION_UNVERIFIED; break;
            case 9: admission.configuration = (RoweIdentityConfiguration)99;
                    status = ROWE_ID_CONFIGURATION_UNVERIFIED; break;
            case 10: admission.immutable_generation_owned = false;
                     status = ROWE_ID_OWNERSHIP_UNVERIFIED; break;
            case 11: admission.player_only = false; status = ROWE_ID_SCOPE_UNSUPPORTED; break;
            default:
                status = ROWE_ID_METADATA_UNAVAILABLE;
                switch (mode) {
                    case 12: admission.metadata_revision[0] = 0; break;
                    case 13: metadata.revision[0] = 0; break;
                    case 14: metadata.revision[0] ^= 1; break;
                    case 15: metadata.release.sha256[0] ^= 1; break;
                    case 16: metadata.release.size--; break;
                    case 17: metadata.release.release[0] ^= 1; break;
                    case 18: metadata.resolve = NULL; break;
                    case 19: metadata.context = NULL; break;
                    case 20: memset(admission.metadata_revision, 'x', 64); break;
                    case 21: memset(metadata.revision, 'x', 64); break;
                    case 22: memset(metadata.release.release, 'x', 64); break;
                }
        }
        reject(status, true);
    }
    for (unsigned mode = 0; mode < 5; mode++) {
        setup(); CHECK(run() == ROWE_ID_PARTY_AVAILABLE);
        f.reads = f.lookups = 0;
        scenarios++;
        CHECK(rowe_identity_read_player_party(mode == 0 ? NULL : &admission,
              mode == 1 ? NULL : &metadata, mode == 2 ? NULL : read_player,
              mode == 3 ? NULL : &f, mode == 4 ? NULL : &out) ==
              (mode == 1 ? ROWE_ID_METADATA_UNAVAILABLE : ROWE_ID_REQUEST_INVALID));
        CHECK(f.reads == 0 && f.lookups == 0);
        if (mode != 4) cleared();
    }
}

static void test_success(void) {
    setup();
    for (unsigned count = 0; count <= 6; count++) {
        f.count = (uint8_t)count; f.reads = f.lookups = 0;
        CHECK(run() == (count ? ROWE_ID_PARTY_AVAILABLE : ROWE_ID_PARTY_EMPTY));
        CHECK(out.count == count && f.reads == 5 && f.lookups == count);
        for (unsigned i = 0; i < count; i++) {
            const RoweIdentityMember* m = &out.members[i];
            CHECK(m->slot == i && m->raw_species == (i % 2 ? 3 : 1) && m->raw_form == 1);
            CHECK(m->resolved_species == 3 && m->metadata_key == 3);
            CHECK(strcmp(m->name, "Synthetic Variant") == 0);
            CHECK(m->static_status == ROWE_STATIC_VERIFIED);
            CHECK(m->display_authority == ROWE_NOT_VERIFIED && m->effective_identity == ROWE_NOT_VERIFIED);
            CHECK(memcmp(m->private_raw_record, f.bytes + i*76, 76) == 0);
        }
        RoweIdentityMember zero = {0};
        for (unsigned i = count; i < 6; i++) CHECK(memcmp(&out.members[i], &zero, sizeof(zero)) == 0);
    }
    f.count = 0; f.reads = f.lookups = 0; /* success -> valid empty */
    CHECK(run() == ROWE_ID_PARTY_EMPTY); cleared(); CHECK(f.lookups == 0);
    setup(); f.count = 1;
    record(0, 1, 0); record(1, 0, 31); /* Excluded invalid slot stays excluded. */
    CHECK(run() == ROWE_ID_PARTY_AVAILABLE && f.lookups == 1);
    CHECK(out.members[0].raw_form == 0 && out.members[0].resolved_species == 1);
    CHECK(strcmp(out.members[0].name, "Synthetic Base") == 0);
    setup(); f.contradiction = 10; CHECK(run() == ROWE_ID_PARTY_AVAILABLE);
    CHECK(strlen(out.members[0].name) == 63);
    memset(f.bytes, 0, sizeof(f.bytes)); /* Result owns bytes and name. */
    CHECK(out.members[0].private_raw_record[0] == 0xa5);
    CHECK(out.members[0].name[0] == 'x');
}

static void test_refusal(void) {
    for (unsigned slot = 0; slot < 6; slot++) {
        for (unsigned reason = ROWE_UNKNOWN_SPECIES; reason <= ROWE_CONTRADICTORY_RESOLUTION; reason++) {
            setup(); CHECK(run() == ROWE_ID_PARTY_AVAILABLE);
            f.reads = f.lookups = 0; f.count = 6;
            f.fail_lookup = slot + 1; f.refusal = (RoweStaticStatus)reason;
            reject(reason == ROWE_METADATA_UNAVAILABLE ? ROWE_ID_METADATA_UNAVAILABLE : ROWE_ID_IDENTITY_UNSUPPORTED, false);
            CHECK(out.refusal == (RoweStaticStatus)reason && f.lookups == slot + 1);
        }
        for (unsigned kind = 0; kind < 3; kind++) {
            setup(); f.count = 6;
            record(slot, kind == 0 ? 0 : kind == 1 ? 4000 : 1, kind == 2 ? 31 : 1);
            reject(ROWE_ID_IDENTITY_UNSUPPORTED, false);
            CHECK(out.refusal == (kind == 2 ? ROWE_INVALID_FORM : ROWE_UNKNOWN_SPECIES));
            CHECK(f.lookups == slot + (kind == 0 ? 0 : 1));
        }
    }
    for (unsigned kind = 1; kind <= 9; kind++) {
        setup(); f.contradiction = kind;
        reject(ROWE_ID_IDENTITY_UNSUPPORTED, false);
        CHECK(out.refusal == (kind == 6 || kind == 7 ? ROWE_MISSING_NAME : ROWE_CONTRADICTORY_RESOLUTION));
    }
    setup(); f.fail_lookup = 2; f.refusal = ROWE_STATIC_REFUSED;
    reject(ROWE_ID_IDENTITY_UNSUPPORTED, false);
}

static void test_snapshots(void) {
    for (unsigned call = 1; call <= 5; call++) {
        for (unsigned mode = 0; mode < 3; mode++) {
            setup(); CHECK(run() == ROWE_ID_PARTY_AVAILABLE);
            f.reads = f.lookups = 0; f.fail_read = call;
            size_t length = call % 2 ? 1 : 456;
            f.returned_length = mode == 0 ? 0 : mode == 1 ? length - 1 : length + 1;
            reject(ROWE_ID_SNAPSHOT_INVALID, false);
            CHECK(f.lookups == 0);
        }
    }
    for (unsigned call = 2; call <= 5; call++) {
        setup(); f.change_count = call; reject(ROWE_ID_SNAPSHOT_INVALID, false);
        CHECK(f.lookups == 0);
    }
    for (unsigned call = 3; call <= 4; call++) {
        setup(); f.change_bytes = call; reject(ROWE_ID_SNAPSHOT_INVALID, false);
        CHECK(f.lookups == 0);
    }
    const uint8_t invalid[] = {7, 255};
    for (unsigned i = 0; i < sizeof(invalid); i++) {
        setup(); f.count = invalid[i]; reject(ROWE_ID_SNAPSHOT_INVALID, false);
        CHECK(f.reads == 1 && f.lookups == 0);
    }
    /* One truncated occupied record and every invalid region length. No alternate
     * byte-buffer adapter API: the raw reader requires all 456 bytes even if empty. */
    for (size_t length = 0; length < 456; length++) {
        setup(); f.fail_read = 2; f.returned_length = length;
        reject(ROWE_ID_SNAPSHOT_INVALID, false); CHECK(f.lookups == 0);
    }
    setup(); f.count = 0; f.change_bytes = 4;
    reject(ROWE_ID_SNAPSHOT_INVALID, false); CHECK(f.lookups == 0);
    setup(); f.count = 0; f.fail_read = 4;
    reject(ROWE_ID_SNAPSHOT_INVALID, false); CHECK(f.lookups == 0);
}

int main(void) {
    test_admission(); test_success(); test_refusal(); test_snapshots();
    printf("ROWE identity adapter: %u scenarios, %u assertions passed (synthetic only).\n", scenarios, checks);
    return 0;
}
