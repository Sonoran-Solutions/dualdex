#ifndef DUALDEX_TEST_ROWE_IDENTITY_ADAPTER_H
#define DUALDEX_TEST_ROWE_IDENTITY_ADAPTER_H

#include "rowe_raw_decoder.h"

/* Host-only synthetic composition contract, never production permission. */
enum { ROWE_ID_TEXT_BYTES = 64 };
typedef struct {
    char release[ROWE_ID_TEXT_BYTES];
    uint64_t size;
    uint8_t sha256[32]; /* Supplied digest bytes; this API does not hash a ROM. */
} RoweIdentityRelease;

typedef enum {
    ROWE_CONFIG_UNKNOWN, ROWE_CONFIG_SYNTHETIC_ADMITTED, ROWE_CONFIG_UNSUPPORTED
} RoweIdentityConfiguration;

typedef struct {
    RoweIdentityRelease intended, observed;
    char metadata_revision[ROWE_ID_TEXT_BYTES];
    RoweIdentityConfiguration configuration;
    bool immutable_generation_owned, player_only;
} RoweIdentityAdmission;

typedef enum {
    ROWE_STATIC_REFUSED, ROWE_STATIC_VERIFIED,
    ROWE_UNKNOWN_SPECIES, ROWE_UNSUPPORTED_SPECIES_RECORD, ROWE_INVALID_FORM,
    ROWE_MALFORMED_FORM_TABLE, ROWE_MISSING_NAME, ROWE_UNSUPPORTED_RESOLVED_SPECIES,
    ROWE_METADATA_UNAVAILABLE, ROWE_CONTRADICTORY_RESOLUTION
} RoweStaticStatus;

typedef struct {
    uint16_t raw_species, resolved_species;
    uint8_t raw_form;
    uint16_t metadata_key; /* Exact species-table key: must equal resolved_species. */
    char name[ROWE_ID_TEXT_BYTES]; /* Owned, NUL-terminated static candidate. */
} RoweStaticResolution;

/* Callback writes only within out, echoes the input pair, and returns a typed
 * status. Refusal payloads must be zero. Provider owns domain/table/name checks,
 * including specific evidence for nonzero forms without a form table. No fallback.
 * The adapter checks structural contradictions, not metadata authenticity. */
typedef RoweStaticStatus (*RoweIdentityResolve)(void* context, uint16_t species,
                                              uint8_t form, RoweStaticResolution* out);
typedef struct {
    RoweIdentityRelease release;
    char revision[ROWE_ID_TEXT_BYTES];
    RoweIdentityResolve resolve;
    void* context; /* Required; immutable metadata for the entire request. */
} RoweIdentityMetadata;

typedef enum { ROWE_NOT_VERIFIED } RoweRuntimeAuthority;
typedef struct {
    uint8_t slot;
    uint16_t raw_species;
    uint8_t raw_form;
    uint16_t resolved_species, metadata_key;
    char name[ROWE_ID_TEXT_BYTES];
    RoweStaticStatus static_status;
    RoweRuntimeAuthority display_authority, effective_identity;
    uint8_t private_raw_record[ROWE_RAW_SLOT_BYTES]; /* Host diagnostics only. */
} RoweIdentityMember;

typedef enum {
    ROWE_ID_REQUEST_INVALID, ROWE_ID_PARTY_AVAILABLE, ROWE_ID_PARTY_EMPTY,
    ROWE_ID_ROM_UNVERIFIED, ROWE_ID_CONFIGURATION_UNVERIFIED,
    ROWE_ID_OWNERSHIP_UNVERIFIED, ROWE_ID_SCOPE_UNSUPPORTED,
    ROWE_ID_METADATA_UNAVAILABLE, ROWE_ID_IDENTITY_UNSUPPORTED,
    ROWE_ID_SNAPSHOT_INVALID
} RoweIdentityStatus;
typedef struct {
    RoweIdentityStatus status;
    RoweStaticStatus refusal; /* Resolver reason, otherwise STATIC_REFUSED (zero). */
    uint8_t count;
    RoweIdentityMember members[ROWE_RAW_CAPACITY];
} RoweIdentityParty;

/* All inputs/callback storage must remain valid and disjoint from out throughout
 * the call. Caller supplies a complete writable out; NULL returns REQUEST_INVALID
 * without callbacks. Every refusal clears count/members, retaining only reasons.
 * No pointers escape. Admission precedes every read/lookup. The raw reader owns
 * the five-read algorithm; SNAPSHOT_INVALID includes failed/short reads and
 * incoherence (its boolean API cannot distinguish them).
 * Caller MUST own an immutable memory generation for the entire call. Repeated
 * reads do not prevent concurrent writes/ABA. A synthetic bool is no lock/token. */
RoweIdentityStatus rowe_identity_read_player_party(
    const RoweIdentityAdmission* admission, const RoweIdentityMetadata* metadata,
    RoweRawRead read, void* read_context, RoweIdentityParty* out);

#endif
