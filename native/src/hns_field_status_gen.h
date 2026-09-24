/*
 * GENERATED FILE - do not edit by hand.
 *
 * Pinned Heart & Soul 2.0.5 `gFieldStatuses` bits, parsed from include/constants/battle.h
 * by tools/hns-field-status/generate_hns_field_status.py.
 * Pinned upstream: PokemonHnS-Development/pokehns-expansion 1f42b74dff0e9fe942419845d040663dd829a973
 *
 * The native reader stores the raw word unmasked; these masks exist for the reader config
 * and the host tests, never to filter the observed value.
 */
#ifndef DUALDEX_HNS_FIELD_STATUS_GEN_H
#define DUALDEX_HNS_FIELD_STATUS_GEN_H

#define HNS_STATUS_FIELD_MAGIC_ROOM (1u << 0)
#define HNS_STATUS_FIELD_TRICK_ROOM (1u << 1)
#define HNS_STATUS_FIELD_WONDER_ROOM (1u << 2)
#define HNS_STATUS_FIELD_MUDSPORT (1u << 3)
#define HNS_STATUS_FIELD_WATERSPORT (1u << 4)
#define HNS_STATUS_FIELD_GRAVITY (1u << 5)
#define HNS_STATUS_FIELD_GRASSY_TERRAIN (1u << 6)
#define HNS_STATUS_FIELD_MISTY_TERRAIN (1u << 7)
#define HNS_STATUS_FIELD_ELECTRIC_TERRAIN (1u << 8)
#define HNS_STATUS_FIELD_PSYCHIC_TERRAIN (1u << 9)
#define HNS_STATUS_FIELD_ION_DELUGE (1u << 10)
#define HNS_STATUS_FIELD_FAIRY_LOCK (1u << 11)
#define HNS_STATUS_FIELD_TERRAIN_ANY (HNS_STATUS_FIELD_GRASSY_TERRAIN | HNS_STATUS_FIELD_MISTY_TERRAIN | HNS_STATUS_FIELD_ELECTRIC_TERRAIN | HNS_STATUS_FIELD_PSYCHIC_TERRAIN)
#define HNS_STATUS_FIELD_KNOWN_MASK 0x00000FFFu

#endif /* DUALDEX_HNS_FIELD_STATUS_GEN_H */
