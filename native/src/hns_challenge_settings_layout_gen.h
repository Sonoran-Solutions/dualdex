/*
 * GENERATED FILE — do not edit by hand.
 *
 * ChallengeSettings layout for the exact Heart & Soul 2.0.5 build,
 * derived from the pinned upstream source by
 *   tools/hns-layout/generate_hns_challenge_layout.py
 *
 * Pinned upstream: PokemonHnS-Development/pokehns-expansion
 *   commit 1f42b74dff0e9fe942419845d040663dd829a973 (tag Release-v2.0.5)
 * Compiler:   arm-none-eabi-gcc (ARM GNU Toolchain 13.2.Rel1)
 * Flags:      -DMODERN=1 -DTESTING=0 -DPOKEMON_HNS -DEMERALD -std=gnu17 -mthumb -mthumb-interwork -O2 -mabi=apcs-gnu -mtune=arm7tdmi -march=armv4t
 *
 * Evidence chain:
 *   - sizeof/offsets: compiled probe objects read back from .rodata
 *     (see the generator), matching the official release ROM:
 *     gSaveblock3 == 52 bytes, challengeSettings at offset 16
 *     (ROM cross-checks: GetBattleMoveCategory reads optionStyle at
 *     gSaveBlock3Ptr + 0x11 bit 1; GetCurrentLevelCap and
 *     GetBaseStatEqualizerValue read SaveBlock3 + 0x18 bits 5-6/2-3;
 *     RandomizerFeatureEnabled reads SaveBlock3 + 0x14/0x15).
 *   - bit positions: LSB-first within each byte, identical across the
 *     pinned ARM toolchain and an independent host/clang probe.
 *
 * The SaveBlock3 never moves: SetSaveBlocksPointers() re-bases
 * gSaveBlock2Ptr/gSaveBlock1Ptr/gPokemonStoragePtr only, and
 * gSaveBlock3Ptr is statically initialised to &gSaveblock3.
 */

#ifndef DUALDEX_HNS_CHALLENGE_SETTINGS_LAYOUT_GEN_H
#define DUALDEX_HNS_CHALLENGE_SETTINGS_LAYOUT_GEN_H

#include <stdint.h>

#define HNS_CHALLENGE_SETTINGS_SIZEOF 32
#define HNS_SAVEBLOCK3_SIZEOF 52
#define HNS_SAVEBLOCK3_CHALLENGE_SETTINGS_OFFSET 16

/** One field's position inside struct ChallengeSettings (bit offset
 *  LSB-first within the byte). */
typedef struct {
    const char* name;      /* field name in the pinned source */
    uint8_t byte_offset;   /* bytes from the start of ChallengeSettings */
    uint8_t bit_offset;    /* first bit within byte_offset, LSB-first */
    uint8_t bit_width;     /* field width in bits */
    uint8_t domain_mask;   /* (1u << bit_width) - 1: the whole encoding */
    uint32_t valid_values; /* bitmask of values the pinned source assigns */
} HnsChallengeFieldLayout;

/* Byte/bit positions of every field DualDex reads.
 *
 * valid_values comes from the pinned source's own domains, not from the
 * encoding: challenge_menu.c choice tables (LevelCap OFF/NORMAL/HARD,
 * TrainerScalingIVs OFF/SCALE/HARD, MaxPartyIVs, ExpMultiplier
 * x1.0/x1.5/x2.0/x0.0) and GetBaseStatEqualizerValue's 0/100/255/500
 * table. 1-bit fields are always 0 or 1. A value outside valid_values
 * is reported out-of-domain by the reader, never coerced.
 */
static const HnsChallengeFieldLayout HNS_CHALLENGE_FIELD_LAYOUT[] = {
    { .name = "optionStyle", .byte_offset = 1, .bit_offset = 1, .bit_width = 1, .domain_mask = 1, .valid_values = 0x00000003u }, /* {0,1} (1-bit; PHYS/SP SPLIT: 0 = per-move split, 1 = type-decided) */
    { .name = "tx_Mode_Fairy_Types", .byte_offset = 28, .bit_offset = 6, .bit_width = 1, .domain_mask = 1, .valid_values = 0x00000003u }, /* {0,1} */
    { .name = "tx_Random_Type", .byte_offset = 4, .bit_offset = 5, .bit_width = 1, .domain_mask = 1, .valid_values = 0x00000003u }, /* {0,1} */
    { .name = "tx_Random_TypeEffectiveness", .byte_offset = 4, .bit_offset = 6, .bit_width = 1, .domain_mask = 1, .valid_values = 0x00000003u }, /* {0,1} */
    { .name = "tx_Random_Abilities", .byte_offset = 4, .bit_offset = 7, .bit_width = 1, .domain_mask = 1, .valid_values = 0x00000003u }, /* {0,1} */
    { .name = "tx_Random_Moves", .byte_offset = 5, .bit_offset = 0, .bit_width = 1, .domain_mask = 1, .valid_values = 0x00000003u }, /* {0,1} */
    { .name = "tx_Challenges_NoEVs", .byte_offset = 9, .bit_offset = 4, .bit_width = 1, .domain_mask = 1, .valid_values = 0x00000003u }, /* {0,1} */
    { .name = "tx_Challenges_BaseStatEqualizer", .byte_offset = 8, .bit_offset = 3, .bit_width = 2, .domain_mask = 3, .valid_values = 0x0000000fu }, /* {0,1,2,3} (0/100/255/500 BST table) */
    { .name = "tx_Challenges_Mirror", .byte_offset = 9, .bit_offset = 2, .bit_width = 1, .domain_mask = 1, .valid_values = 0x00000003u }, /* {0,1} */
    { .name = "tx_Challenges_Mirror_Thief", .byte_offset = 9, .bit_offset = 3, .bit_width = 1, .domain_mask = 1, .valid_values = 0x00000003u }, /* {0,1} */
    { .name = "tx_Challenges_TrainerScalingIVs", .byte_offset = 10, .bit_offset = 3, .bit_width = 2, .domain_mask = 3, .valid_values = 0x00000007u }, /* {0,1,2} (OFF/SCALE/HARD menu choices) */
    { .name = "tx_Challenges_TrainerScalingEVs", .byte_offset = 10, .bit_offset = 5, .bit_width = 2, .domain_mask = 3, .valid_values = 0x0000000fu }, /* {0,1,2,3} (menu choices) */
    { .name = "tx_Challenges_MaxPartyIVs", .byte_offset = 11, .bit_offset = 0, .bit_width = 2, .domain_mask = 3, .valid_values = 0x00000007u }, /* {0,1,2} (menu choices) */
    { .name = "tx_Mode_Sturdy", .byte_offset = 28, .bit_offset = 7, .bit_width = 1, .domain_mask = 1, .valid_values = 0x00000003u }, /* {0,1} */
    { .name = "tx_Challenges_LevelCap", .byte_offset = 8, .bit_offset = 5, .bit_width = 2, .domain_mask = 3, .valid_values = 0x00000007u }, /* {0,1,2} (OFF/NORMAL/HARD menu choices) */
    { .name = "tx_Challenges_ExpMultiplier", .byte_offset = 9, .bit_offset = 0, .bit_width = 2, .domain_mask = 3, .valid_values = 0x0000000fu }, /* {0,1,2,3} (x1.0/x1.5/x2.0/x0.0 menu choices) */
    { .name = "tx_Mode_Legendary_Abilities", .byte_offset = 29, .bit_offset = 1, .bit_width = 1, .domain_mask = 1, .valid_values = 0x00000003u }, /* {0,1} */
};

#define HNS_CHALLENGE_FIELD_COUNT (sizeof(HNS_CHALLENGE_FIELD_LAYOUT) / sizeof(HNS_CHALLENGE_FIELD_LAYOUT[0]))

#endif /* DUALDEX_HNS_CHALLENGE_SETTINGS_LAYOUT_GEN_H */
