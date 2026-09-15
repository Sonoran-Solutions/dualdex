/*
 * DualDex Heart & Soul 2.0.5 runtime battle-state probe (developer tool).
 *
 * NOT part of the shipped app. It exists so the runtime half of the H&S battle-lifecycle evidence
 * can be reproduced on a developer machine against the exact upstream release lineage, without
 * granting Heart & Soul any trust in the product.
 *
 * What it does:
 *   1. loads the bundled mGBA libretro core and a legally obtained H&S 2.0.5 ROM;
 *   2. drives it frame by frame while sampling the exact compiled battle globals that DualDex
 *      reads (`gBattleTypeFlags`, `gBattlersCount`, `gBattleOutcome`, `gBattlerPartyIndexes`,
 *      `gBattlerPositions`, `gAbsentBattlerFlags`, `gBattleMons`, `gPlayerPartyCount`,
 *      `gEnemyPartyCount`, `gMain.inBattle`);
 *   3. emits a lifecycle table (one row per observable state change);
 *   4. feeds the LIVE emulated memory into the *production* readers
 *      (`pokemon_read_battle_lifecycle`, `pokemon_resolve_active_enemy`,
 *      `pokemon_read_player_party_gba`, `pokemon_read_enemy_party_gba`) and asserts that they
 *      fail closed: outside a battle they must never invent an opponent, and without an exact
 *      verified hash the product path must not read live memory at all.
 *
 * It never writes to the emulated machine, never applies cheats, never patches the ROM, and never
 * stores or prints ROM bytes. No ROM, save or save state is committed to the repository.
 *
 * Usage:
 *   build.sh && ./runtime_battle_probe <mgba_libretro.so> <hns_2.0.5.gba> [frames]
 */

#include "libretro_host.h"
#include "pokemon_reader.h"
#include "gba_memory_map.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <stdbool.h>

static bool probe_read(void* user, uint32_t address, uint8_t* out, size_t length) {
    (void)user;
    return libretro_host_read_gba_address(address, out, length);
}

static bool read_u8(uint32_t address, uint8_t* out) {
    return libretro_host_read_gba_address(address, out, 1);
}

typedef struct {
    int      frame;
    bool     in_battle_readable;
    bool     in_battle;
    bool     counters_readable;
    uint8_t  battlers;
    uint32_t type_flags;
    uint8_t  outcome;
    uint8_t  party_index[DUALDEX_MAX_BATTLERS];
    uint8_t  position[DUALDEX_MAX_BATTLERS];
    uint8_t  absent_flags;
    uint16_t mon_species[DUALDEX_MAX_BATTLERS];
    uint16_t mon_hp[DUALDEX_MAX_BATTLERS];
    uint8_t  player_party_count;
    uint8_t  enemy_party_count;
} Sample;

static void sample_state(const GameMemoryConfig* cfg, int frame, Sample* out) {
    memset(out, 0, sizeof(*out));
    out->frame = frame;
    if (!cfg) return;

    uint8_t byte = 0;
    if (read_u8(cfg->main_struct_gba_address + cfg->main_in_battle_byte_offset, &byte)) {
        out->in_battle_readable = true;
        out->in_battle = (byte & (uint8_t)(1u << cfg->main_in_battle_bit)) != 0;
    }

    size_t ewram_sz = 0;
    uint8_t* ewram = libretro_host_get_ewram(&ewram_sz);
    if (!ewram || ewram_sz == 0) return;

    if (cfg->battlers_count_offset < ewram_sz &&
        cfg->battle_type_flags_offset + 4 <= ewram_sz &&
        cfg->battle_outcome_offset < ewram_sz) {
        out->counters_readable = true;
        out->battlers = ewram[cfg->battlers_count_offset];
        out->type_flags = (uint32_t)ewram[cfg->battle_type_flags_offset] |
                          ((uint32_t)ewram[cfg->battle_type_flags_offset + 1] << 8) |
                          ((uint32_t)ewram[cfg->battle_type_flags_offset + 2] << 16) |
                          ((uint32_t)ewram[cfg->battle_type_flags_offset + 3] << 24);
        out->outcome = ewram[cfg->battle_outcome_offset];
    }
    if (cfg->absent_battler_flags_offset < ewram_sz) {
        out->absent_flags = ewram[cfg->absent_battler_flags_offset];
    }
    if (cfg->player_party_count_offset < ewram_sz) {
        out->player_party_count = ewram[cfg->player_party_count_offset];
    }
    if (cfg->enemy_party_count_offset < ewram_sz) {
        out->enemy_party_count = ewram[cfg->enemy_party_count_offset];
    }

    for (uint8_t b = 0; b < DUALDEX_MAX_BATTLERS; b++) {
        if (cfg->battler_positions_offset != 0 &&
            cfg->battler_positions_offset + b < ewram_sz) {
            out->position[b] = ewram[cfg->battler_positions_offset + b];
        }
        if (cfg->battler_party_indexes_offset != 0 &&
            cfg->battler_party_indexes_offset + (b * 2) + 1 < ewram_sz) {
            out->party_index[b] = ewram[cfg->battler_party_indexes_offset + (b * 2)];
        }
        size_t mon_off = (size_t)cfg->battle_mons_offset + ((size_t)b * cfg->battle_mons_size);
        if (cfg->battle_mons_offset != 0 && mon_off + cfg->battle_mons_size <= ewram_sz) {
            out->mon_species[b] = (uint16_t)(ewram[mon_off] | (ewram[mon_off + 1] << 8));
            size_t hp_off = mon_off + cfg->battle_mons_hp_offset;
            out->mon_hp[b] = (uint16_t)(ewram[hp_off] | (ewram[hp_off + 1] << 8));
        }
    }
}

/** Compare only observed state: the frame number is a label, not part of the state. */
static bool sample_changed(const Sample* a, const Sample* b) {
    Sample left = *a, right = *b;
    left.frame = 0;
    right.frame = 0;
    return memcmp(&left, &right, sizeof(Sample)) != 0;
}

static int g_observed_rows = 0;
static bool g_saw_in_battle_true = false;
// One flag per possible byte value. A 32-bit bitmask cannot represent 0..255 and would invoke
// undefined behaviour for any value >= 32, so the tracker is a plain table.
static bool g_gmin_byte_seen[256];
static bool g_gmin_always_readable = true;
static int  g_gmin_frames_sampled = 0;
static int  g_gmin_frames_unreadable = 0;

static void print_table_header(void) {
    printf("frame  inB  battlers  flags       outcome  idx[0..3]        pos[0..3]        "
           "monSpecies[0..3]  monHP[0..3]      pParty  eParty\n");
}

static void print_row(const Sample* s) {
    printf("%5d  %s   %u         0x%08X  %-7u  "
           "%2u,%2u,%2u,%2u   %2u,%2u,%2u,%2u   "
           "%4u,%4u,%4u,%4u  %4u,%4u,%4u,%4u  %5u   %5u\n",
           s->frame,
           s->in_battle_readable ? (s->in_battle ? "1" : "0") : "?",
           s->battlers, s->type_flags, s->outcome,
           s->party_index[0], s->party_index[1], s->party_index[2], s->party_index[3],
           s->position[0], s->position[1], s->position[2], s->position[3],
           s->mon_species[0], s->mon_species[1], s->mon_species[2], s->mon_species[3],
           s->mon_hp[0], s->mon_hp[1], s->mon_hp[2], s->mon_hp[3],
           s->player_party_count, s->enemy_party_count);
}

static const char* lifecycle_name(BattleLifecycleState state) {
    switch (state) {
        case BATTLE_LIFECYCLE_INACTIVE: return "INACTIVE";
        case BATTLE_LIFECYCLE_INITIALIZING: return "INITIALIZING";
        case BATTLE_LIFECYCLE_ACTIVE: return "ACTIVE";
        case BATTLE_LIFECYCLE_ENDING: return "ENDING";
        default: return "UNKNOWN";
    }
}

static const char* kind_name(BattleKind kind) {
    switch (kind) {
        case BATTLE_KIND_WILD_SINGLE: return "WILD_SINGLE";
        case BATTLE_KIND_TRAINER_SINGLE: return "TRAINER_SINGLE";
        case BATTLE_KIND_DOUBLES: return "DOUBLES";
        case BATTLE_KIND_MULTI_OR_PARTNER: return "MULTI_OR_PARTNER";
        case BATTLE_KIND_NONE: return "NONE";
        default: return "UNKNOWN";
    }
}

static const char* active_enemy_name(ActiveEnemyState state) {
    switch (state) {
        case ACTIVE_ENEMY_NONE_ACTIVE: return "NONE_ACTIVE";
        case ACTIVE_ENEMY_SLOT: return "SLOT";
        case ACTIVE_ENEMY_AMBIGUOUS: return "AMBIGUOUS";
        default: return "UNKNOWN";
    }
}

/** Feed live emulated memory through the production readers and report what they decided. */
static int run_production_reader_checks(const GameMemoryConfig* cfg, int frame) {
    int failures = 0;

    size_t ewram_sz = 0;
    uint8_t* ewram = libretro_host_get_ewram(&ewram_sz);
    if (!ewram || ewram_sz == 0) {
        printf("  [reader] EWRAM unavailable; readers must fail closed\n");
        return 0;
    }

    BattleStateRaw state;
    BattleLifecycleState lifecycle =
        pokemon_read_battle_lifecycle(probe_read, NULL, ewram, ewram_sz, cfg, &state);

    PartySnapshot enemy_snapshot;
    ActiveEnemyInfo info;
    ActiveEnemyState enemy_state = pokemon_resolve_active_enemy(
        probe_read, NULL, ewram, ewram_sz, cfg, &enemy_snapshot, &info);

    PartySnapshot player_snapshot;
    uint8_t player_count = pokemon_read_player_party_gba(
        probe_read, NULL, ewram, ewram_sz, cfg, &player_snapshot);

    printf("  [reader@%d] lifecycle=%s kind=%s inBattleFlag=%s battlers=%u "
           "positionsReadable=%d indexesReadable=%d\n",
           frame, lifecycle_name(lifecycle), kind_name(state.kind),
           state.in_battle_flag_readable ? (state.in_battle_flag ? "true" : "false") : "unreadable",
           state.battlers_count, state.positions_readable, state.party_indexes_readable);
    uint8_t presence = pokemon_read_battle_presence_gba(probe_read, NULL, ewram, ewram_sz, cfg);
    printf("  [reader@%d] productionBattlePresence=%s (%u)\n",
           frame, presence == 1 ? "PRESENT" : (presence == 0 ? "ABSENT" : "UNKNOWN"), presence);
    printf("  [reader@%d] activeEnemy=%s slot=%d battler=%d opponents=%u fainted=%d "
           "enemyParty=%u activeEnemySlot=%d known=%d ambiguous=%d playerParty=%u "
           "activePlayerSlot=%d known=%d\n",
           frame, active_enemy_name(enemy_state), info.party_slot, info.battler_index,
           info.opponent_battlers, info.fainted ? 1 : 0, enemy_snapshot.count,
           enemy_snapshot.active_battler_slot, enemy_snapshot.active_battler_known,
           enemy_snapshot.active_enemy_ambiguous, player_count,
           player_snapshot.active_battler_slot, player_snapshot.active_battler_known);

    // Invariant 1: an unresolved slot is NEVER slot 0.
    if (!enemy_snapshot.active_battler_known && enemy_snapshot.active_battler_slot != -1) {
        printf("  [reader] FAIL: unknown active enemy slot was reported as %d\n",
               enemy_snapshot.active_battler_slot);
        failures++;
    }
    if (!player_snapshot.active_battler_known && player_snapshot.active_battler_slot != -1) {
        printf("  [reader] FAIL: unknown active player slot was reported as %d\n",
               player_snapshot.active_battler_slot);
        failures++;
    }

    // Invariant 2: outside an active battle the readers must not produce an opponent.
    if (lifecycle != BATTLE_LIFECYCLE_ACTIVE) {
        if (enemy_snapshot.count != 0) {
            printf("  [reader] FAIL: %u enemy party members reported while lifecycle=%s\n",
                   enemy_snapshot.count, lifecycle_name(lifecycle));
            failures++;
        }
        if (enemy_state == ACTIVE_ENEMY_SLOT) {
            printf("  [reader] FAIL: an opponent slot was named while lifecycle=%s\n",
                   lifecycle_name(lifecycle));
            failures++;
        }
    }

    // Invariant 3: production battle presence must agree with the authoritative lifecycle.
    // A stale gBattleMons species word must never be reported as a running battle.
    if (lifecycle != BATTLE_LIFECYCLE_ACTIVE && presence == 1) {
        printf("  [reader] FAIL: production battle presence reported PRESENT while lifecycle=%s\n",
               lifecycle_name(lifecycle));
        failures++;
    }
    if (lifecycle == BATTLE_LIFECYCLE_INACTIVE && presence != 0) {
        printf("  [reader] FAIL: production battle presence reported %u while lifecycle INACTIVE "
               "(expected ABSENT)\n", presence);
        failures++;
    }
    if (lifecycle == BATTLE_LIFECYCLE_ACTIVE && presence != 1) {
        printf("  [reader] FAIL: production battle presence reported %u while lifecycle ACTIVE "
               "(expected PRESENT)\n", presence);
        failures++;
    }

    // Invariant 4: a named slot must be inside the authoritative enemy party bounds.
    if (enemy_state == ACTIVE_ENEMY_SLOT &&
        (info.party_slot < 0 || info.party_slot >= (int)enemy_snapshot.count)) {
        printf("  [reader] FAIL: named opponent slot %d is outside the authoritative enemy "
               "party (%u members)\n", info.party_slot, enemy_snapshot.count);
        failures++;
    }

    return failures;
}

int main(int argc, char** argv) {
    if (argc < 3) {
        fprintf(stderr, "usage: %s <mgba_libretro.so> <hns_2.0.5.gba> [frames]\n", argv[0]);
        return 2;
    }
    const char* core_path = argv[1];
    const char* rom_path = argv[2];
    const int frames = (argc > 3) ? atoi(argv[3]) : 1800;

    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    if (!cfg) {
        fprintf(stderr, "FAIL: Heart & Soul configuration unavailable\n");
        return 1;
    }

    printf("== DualDex H&S 2.0.5 runtime battle-state probe ==\n");
    printf("core   : %s\n", core_path);
    printf("rom    : %s\n", rom_path);
    printf("frames : %d\n", frames);
    printf("layout : playerParty=0x%X enemyParty=0x%X enemyPartyCount=0x%X battleMons=0x%X "
           "battlersCount=0x%X battleTypeFlags=0x%X outcome=0x%X partyIndexes=0x%X "
           "positions=0x%X absentFlags=0x%X gMain=0x%08X+0x%X bit %u\n",
           cfg->player_party_offset, cfg->enemy_party_offset, cfg->enemy_party_count_offset,
           cfg->battle_mons_offset, cfg->battlers_count_offset, cfg->battle_type_flags_offset,
           cfg->battle_outcome_offset, cfg->battler_party_indexes_offset,
           cfg->battler_positions_offset, cfg->absent_battler_flags_offset,
           cfg->main_struct_gba_address, cfg->main_in_battle_byte_offset, cfg->main_in_battle_bit);

    if (!libretro_host_init(core_path)) {
        fprintf(stderr, "FAIL: libretro_host_init(%s)\n", core_path);
        return 1;
    }
    if (!libretro_host_load_rom(rom_path)) {
        fprintf(stderr, "FAIL: libretro_host_load_rom(%s)\n", rom_path);
        libretro_host_cleanup();
        return 1;
    }

    printf("\n-- memory regions after load: %zu --\n", libretro_host_get_gba_region_count());
    for (size_t i = 0; i < libretro_host_get_gba_region_count(); i++) {
        DualDexGbaMemoryRegion region;
        if (!libretro_host_get_gba_region(i, &region)) continue;
        printf("  [%zu] start=0x%08X len=0x%08X select=0x%08X\n",
               i, region.start, region.len, region.select);
    }

    int reader_failures = 0;
    Sample previous;
    memset(&previous, 0, sizeof(previous));
    bool have_previous = false;

    printf("\n-- battle lifecycle table (rows appear on every observed state change) --\n");
    print_table_header();

    for (int f = 0; f < frames; f++) {
        // Mash START/A so the probe walks past the copyright and title screens exactly as a
        // player would. No cheats, no memory writes, no input automation beyond button presses.
        uint32_t buttons = 0;
        if ((f % 30) < 12) buttons |= DUALDEX_BTN_START;
        if ((f % 30) >= 15 && (f % 30) < 27) buttons |= DUALDEX_BTN_A;
        libretro_host_set_input_buttons(buttons);

        libretro_host_step_frame();

        // Sample the raw gate byte on EVERY frame, not only on state changes: the claim under
        // test is "this address is a live flag byte whose inBattle bit stays clear across the
        // observed non-battle period", and that is a per-frame property.
        {
            uint8_t byte = 0;
            g_gmin_frames_sampled++;
            if (read_u8(cfg->main_struct_gba_address + cfg->main_in_battle_byte_offset, &byte)) {
                g_gmin_byte_seen[byte] = true;
                if ((byte & (uint8_t)(1u << cfg->main_in_battle_bit)) != 0) g_saw_in_battle_true = true;
            } else {
                g_gmin_always_readable = false;
                g_gmin_frames_unreadable++;
            }
        }

        Sample current;
        sample_state(cfg, f, &current);
        if (!have_previous || sample_changed(&previous, &current)) {
            print_row(&current);
            g_observed_rows++;
            previous = current;
            have_previous = true;
        }

        // Run the production readers at a low duty cycle so the table stays readable while the
        // lifecycle still gets exercised at the transitions.
        if (f == 0 || f == 60 || (f % 300) == 299) {
            reader_failures += run_production_reader_checks(cfg, f);
        }
    }

    libretro_host_set_input_buttons(0);

    Sample final_state;
    sample_state(cfg, frames, &final_state);
    printf("\n-- final state --\n");
    print_row(&final_state);

    printf("\n-- summary --\n");
    printf("observed distinct states      : %d\n", g_observed_rows);
    printf("gMain byte frames sampled      : %d\n", g_gmin_frames_sampled);
    printf("gMain byte frames unreadable   : %d\n", g_gmin_frames_unreadable);
    printf("gMain.inBattle readable always : %s\n", g_gmin_always_readable ? "yes" : "no");
    printf("gMain.inBattle observed true  : %s\n", g_saw_in_battle_true ? "yes" : "no");
    int distinct_bytes = 0;
    printf("gMain raw byte values seen    :");
    for (int v = 0; v < 256; v++) {
        if (g_gmin_byte_seen[v]) {
            printf(" 0x%02X", v);
            distinct_bytes++;
        }
    }
    printf("\n");
    printf("distinct raw byte values      : %d\n", distinct_bytes);
    {
        int with_bit_clear = 0;
        for (int v = 0; v < 256; v++) {
            if (g_gmin_byte_seen[v] && (v & (1u << cfg->main_in_battle_bit)) == 0) with_bit_clear++;
        }
        printf("observed bytes with inBattle bit clear: %d of %d\n", with_bit_clear, distinct_bytes);
    }
    printf("reader invariant failures     : %d\n", reader_failures);

    libretro_host_unload_rom();
    printf("-- regions after unload: %zu (must be 0) --\n", libretro_host_get_gba_region_count());
    uint8_t guard = 0;
    printf("-- read after unload must be rejected: %s --\n",
           libretro_host_read_gba_address(cfg->main_struct_gba_address, &guard, 1)
               ? "RETURNED TRUE (BAD)" : "rejected (correct)");
    libretro_host_cleanup();

    return reader_failures == 0 ? 0 : 1;
}
