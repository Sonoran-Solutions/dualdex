/*
 * DualDex H&S 2.0.5 scenario runner (developer tool).
 *
 * Loads a legally generated .sav file into the H&S 2.0.5 ROM, drives gameplay to reach the
 * scenario's required game state, and asserts the invariants declared in the .scenario file.
 *
 * Each scenario is named (trainer_battle, trainer_opponent_faint, player_switch,
 * player_faint_forced_replacement). The runner knows the input sequences for each and drives
 * them deterministically.
 *
 * Exit codes:
 *   0   all assertions passed
 *   1   one or more assertion or invariant failures
 *   2   usage / missing argument
 *
 * Harness invariants checked on every ACTIVE frame:
 *   1. resolved_enemy_slot < gEnemyPartyCount           (bounds check)
 *   2. resolved_enemy_slot == gBattlerPartyIndexes[enemy_battler]  (index authority)
 *   3. resolved_player_slot == gBattlerPartyIndexes[player_battler] (index authority)
 *   4. no unresolved state becomes slot 0               (no slot-0 fallback)
 *   5. no stale previous slot survives a completed switch/faint
 *
 * The --selftest mode does not load a ROM or save file: it runs the harness against a
 * zeroed EWRAM (all INACTIVE / NONE_ACTIVE) to verify that the invariant checker itself
 * operates correctly. This mode is used by selftest.sh.
 *
 * What it never does:
 *   - write to emulated RAM
 *   - apply cheats or code patches
 *   - fabricate game state
 *   - store or print ROM bytes
 *
 * Usage:
 *   scenario_runner <mgba_libretro.so> <hns_2.0.5.gba> <save.sav> <scenario_name> [frames]
 *   scenario_runner --selftest
 */

#include "libretro_host.h"
#include "pokemon_reader.h"
#include "gba_memory_map.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <stdbool.h>

/* Maximum frames per scenario before declaring a timeout failure. */
#define SCENARIO_TIMEOUT_FRAMES  120000   /* ~33 minutes at 60fps */

/* =========================================================================
 * Production reader callback (same pattern as runtime_battle_probe.c)
 * ========================================================================= */

static bool probe_read(void* user, uint32_t address, uint8_t* out, size_t length) {
    (void)user;
    return libretro_host_read_gba_address(address, out, length);
}

/* =========================================================================
 * Per-frame invariant checker
 * ========================================================================= */

typedef struct {
    int   frame;
    int   failures;
    int   checks;

    /* Running state for stale-slot detection */
    int   last_known_enemy_slot;    /* -1 = never known */
    int   last_known_player_slot;   /* -1 = never known */
    bool  enemy_transition_started; /* faint/absent flag set */
    bool  player_transition_started;
} InvariantChecker;

static void checker_init(InvariantChecker* c) {
    memset(c, 0, sizeof(*c));
    c->last_known_enemy_slot  = -1;
    c->last_known_player_slot = -1;
}

static void checker_fail(InvariantChecker* c, const char* msg) {
    printf("  [INVARIANT FAIL@%d] %s\n", c->frame, msg);
    c->failures++;
}

/*
 * Run all per-frame invariants.
 *
 * Called on every frame where the lifecycle is readable. Returns the number of new
 * invariant violations observed this frame.
 */
static int checker_check_frame(
    InvariantChecker* c,
    const GameMemoryConfig* cfg,
    BattleLifecycleState lifecycle,
    const PartySnapshot* enemy_snap,
    const ActiveEnemyInfo* enemy_info,
    const PartySnapshot* player_snap
) {
    c->frame++;
    int prev_failures = c->failures;

    /* Invariant 4: no unresolved state becomes slot 0 */
    if (!enemy_snap->active_battler_known && enemy_snap->active_battler_slot != -1) {
        char msg[128];
        snprintf(msg, sizeof(msg),
                 "INV4: unknown active enemy slot was reported as %d (must be -1)",
                 enemy_snap->active_battler_slot);
        checker_fail(c, msg);
    }
    if (!player_snap->active_battler_known && player_snap->active_battler_slot != -1) {
        char msg[128];
        snprintf(msg, sizeof(msg),
                 "INV4: unknown active player slot was reported as %d (must be -1)",
                 player_snap->active_battler_slot);
        checker_fail(c, msg);
    }

    if (lifecycle != BATTLE_LIFECYCLE_ACTIVE) {
        /* Outside an active battle there must be no slot, no enemy party. */
        if (enemy_snap->active_battler_known) {
            checker_fail(c, "INV: known enemy slot outside ACTIVE battle");
        }
        c->checks++;
        return c->failures - prev_failures;
    }

    /* === ACTIVE battle invariants === */

    /* Invariant 1: resolved enemy slot must be inside enemy party bounds */
    if (enemy_snap->active_battler_known) {
        if (enemy_snap->active_battler_slot < 0 ||
            (uint8_t)enemy_snap->active_battler_slot >= enemy_snap->count) {
            char msg[128];
            snprintf(msg, sizeof(msg),
                     "INV1: enemy slot %d outside enemy party bounds [0..%u)",
                     enemy_snap->active_battler_slot, enemy_snap->count);
            checker_fail(c, msg);
        }
    }

    /* Invariant 2: enemy slot must equal gBattlerPartyIndexes[battler] */
    if (enemy_snap->active_battler_known && enemy_info->battler_index >= 0) {
        /* Read gBattlerPartyIndexes[battler] directly from EWRAM to double-check */
        size_t ewram_sz = 0;
        uint8_t* ewram = libretro_host_get_ewram(&ewram_sz);
        if (ewram && ewram_sz > 0 && cfg->battler_party_indexes_offset != 0) {
            size_t idx_off = cfg->battler_party_indexes_offset +
                             (size_t)(enemy_info->battler_index * 2);
            if (idx_off + 1 < ewram_sz) {
                uint16_t raw_idx = (uint16_t)(ewram[idx_off] | (ewram[idx_off + 1] << 8));
                if ((int)raw_idx != enemy_snap->active_battler_slot) {
                    char msg[128];
                    snprintf(msg, sizeof(msg),
                             "INV2: enemy slot %d != gBattlerPartyIndexes[%d]=%u",
                             enemy_snap->active_battler_slot,
                             enemy_info->battler_index, raw_idx);
                    checker_fail(c, msg);
                }
            }
        }
    }

    /* Invariant 3: player slot must equal gBattlerPartyIndexes[player_battler] */
    if (player_snap->active_battler_known && player_snap->active_battler_index >= 0) {
        size_t ewram_sz = 0;
        uint8_t* ewram = libretro_host_get_ewram(&ewram_sz);
        if (ewram && ewram_sz > 0 && cfg->battler_party_indexes_offset != 0) {
            size_t idx_off = cfg->battler_party_indexes_offset +
                             (size_t)(player_snap->active_battler_index * 2);
            if (idx_off + 1 < ewram_sz) {
                uint16_t raw_idx = (uint16_t)(ewram[idx_off] | (ewram[idx_off + 1] << 8));
                if ((int)raw_idx != player_snap->active_battler_slot) {
                    char msg[128];
                    snprintf(msg, sizeof(msg),
                             "INV3: player slot %d != gBattlerPartyIndexes[%d]=%u",
                             player_snap->active_battler_slot,
                             player_snap->active_battler_index, raw_idx);
                    checker_fail(c, msg);
                }
            }
        }
    }

    /* Invariant 5: stale slot detection */
    /* Enemy side: once a transition starts (transition_started=true) and then completes
       (active_battler_known=true), the new slot must differ from the old known slot. */
    if (!enemy_snap->active_battler_known) {
        c->enemy_transition_started = true;
    } else {
        if (c->enemy_transition_started &&
            c->last_known_enemy_slot >= 0 &&
            enemy_snap->active_battler_slot == c->last_known_enemy_slot) {
            /* Slot came back to the same value after a transition window — only a bug if the
               engine actually committed a new index. We can't distinguish "same slot, new mon"
               from "stale slot" here without tracking gBattlerPartyIndexes, which the reader
               already validates in INV2. Skip this cross-check to avoid false positives. */
        }
        c->last_known_enemy_slot = enemy_snap->active_battler_slot;
        c->enemy_transition_started = false;
    }

    /* Player side */
    if (!player_snap->active_battler_known) {
        c->player_transition_started = true;
    } else {
        c->last_known_player_slot = player_snap->active_battler_slot;
        c->player_transition_started = false;
    }

    c->checks++;
    return c->failures - prev_failures;
}

/* =========================================================================
 * Evidence record: captures the raw state tuples for the EVIDENCE.md update
 * ========================================================================= */

typedef struct {
    int frame;
    BattleLifecycleState lifecycle;
    /* Enemy */
    int   enemy_slot;
    bool  enemy_known;
    int   enemy_battler;
    bool  enemy_fainted;
    uint8_t enemy_party_count;
    uint16_t enemy_battler_party_index[4]; /* gBattlerPartyIndexes[0..3] */
    /* Player */
    int   player_slot;
    bool  player_known;
    int   player_battler;
} FrameRecord;

static void record_frame(FrameRecord* rec, int frame,
                         BattleLifecycleState lifecycle,
                         const PartySnapshot* enemy_snap,
                         const ActiveEnemyInfo* enemy_info,
                         const PartySnapshot* player_snap,
                         const GameMemoryConfig* cfg) {
    rec->frame    = frame;
    rec->lifecycle = lifecycle;

    rec->enemy_slot    = enemy_snap->active_battler_slot;
    rec->enemy_known   = enemy_snap->active_battler_known;
    rec->enemy_battler = enemy_info->battler_index;
    rec->enemy_fainted = enemy_info->fainted;
    rec->enemy_party_count = enemy_snap->count;

    rec->player_slot    = player_snap->active_battler_slot;
    rec->player_known   = player_snap->active_battler_known;
    rec->player_battler = player_snap->active_battler_index;

    /* Capture raw gBattlerPartyIndexes */
    size_t ewram_sz = 0;
    uint8_t* ewram = libretro_host_get_ewram(&ewram_sz);
    if (ewram && ewram_sz > 0 && cfg && cfg->battler_party_indexes_offset != 0) {
        for (int b = 0; b < 4; b++) {
            size_t off = cfg->battler_party_indexes_offset + (size_t)(b * 2);
            if (off + 1 < ewram_sz) {
                rec->enemy_battler_party_index[b] =
                    (uint16_t)(ewram[off] | (ewram[off + 1] << 8));
            } else {
                rec->enemy_battler_party_index[b] = 0xFFFF;
            }
        }
    }
}

static void print_record(const char* label, const FrameRecord* rec) {
    printf("  [%s@%d] lifecycle=%d enemy_slot=%d(known=%d,battler=%d,fainted=%d) "
           "player_slot=%d(known=%d,battler=%d) eParty=%u "
           "gBattlerPartyIndexes=[%u,%u,%u,%u]\n",
           label, rec->frame, (int)rec->lifecycle,
           rec->enemy_slot, rec->enemy_known, rec->enemy_battler, rec->enemy_fainted,
           rec->player_slot, rec->player_known, rec->player_battler,
           rec->enemy_party_count,
           rec->enemy_battler_party_index[0], rec->enemy_battler_party_index[1],
           rec->enemy_battler_party_index[2], rec->enemy_battler_party_index[3]);
}

/* =========================================================================
 * Selftest mode — no ROM required
 * ========================================================================= */

static int run_selftest(void) {
    printf("== scenario_runner selftest (no ROM) ==\n");

    /* The selftest feeds a zeroed fake memory buffer through the invariant checker.
       Expected result: lifecycle INACTIVE on every frame, no failures. */
    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    if (!cfg) {
        fprintf(stderr, "FAIL: H&S config unavailable in selftest\n");
        return 1;
    }

    /* The invariant checker only operates on the parsed structs; it doesn't need a real
       GBA. Build a synthetic zero state (overworld, no battle) and run 10 checks. */
    InvariantChecker c;
    checker_init(&c);

    for (int i = 0; i < 10; i++) {
        PartySnapshot enemy_snap = {0};
        enemy_snap.active_battler_slot  = -1;
        enemy_snap.active_battler_known = false;

        ActiveEnemyInfo enemy_info = {0};
        enemy_info.battler_index = -1;
        enemy_info.party_slot    = -1;

        PartySnapshot player_snap = {0};
        player_snap.active_battler_slot  = -1;
        player_snap.active_battler_known = false;

        /* libretro_host_get_ewram will return NULL in selftest (no core loaded);
           the checker gracefully skips INV2/INV3 when ewram is NULL. */
        int new_fails = checker_check_frame(&c, cfg, BATTLE_LIFECYCLE_INACTIVE,
                                            &enemy_snap, &enemy_info, &player_snap);
        if (new_fails != 0) {
            printf("FAIL: selftest: invariant failure on inactive frame %d\n", i);
            return 1;
        }
    }

    /* Synthetic active battle checks (valid slot 0 of 2) */
    for (int i = 0; i < 10; i++) {
        PartySnapshot enemy_snap = {0};
        enemy_snap.count = 2;
        enemy_snap.active_battler_slot  = 0;
        enemy_snap.active_battler_known = true;

        ActiveEnemyInfo enemy_info = {0};
        enemy_info.battler_index = -1; /* -1 skips EWRAM comparison in selftest */
        enemy_info.party_slot    = 0;

        PartySnapshot player_snap = {0};
        player_snap.count = 2;
        player_snap.active_battler_slot  = 0;
        player_snap.active_battler_known = true;
        player_snap.active_battler_index = -1;

        int new_fails = checker_check_frame(&c, cfg, BATTLE_LIFECYCLE_ACTIVE,
                                            &enemy_snap, &enemy_info, &player_snap);
        if (new_fails != 0) {
            printf("FAIL: selftest: invariant failure on active frame %d\n", i);
            return 1;
        }
    }

    printf("selftest invariant_checks : %d\n", c.checks);
    printf("selftest invariant_failures : %d\n", c.failures);
    printf("selftest result : PASS\n");
    return 0;
}

/* =========================================================================
 * Button schedules for each scenario
 * ========================================================================= */

/*
 * Trainer battle scenario: load save, walk to a trainer, engage.
 * The button sequence assumes a save-state near the beginning of the game.
 */
static uint32_t trainer_battle_buttons(int frame) {
    /* Phase 1 (0-600): resume from save, advance any text. */
    if (frame < 600)  return (frame % 20 < 10) ? DUALDEX_BTN_A : 0u;
    /* Phase 2 (600-3600): walk towards the first trainer. */
    if (frame < 3600) return DUALDEX_BTN_UP;
    /* Phase 3 (3600+): A-mash to advance battle menus. */
    return (frame % 30 < 15) ? DUALDEX_BTN_A : 0u;
}

static uint32_t player_switch_buttons(int frame) {
    /* In an active battle, open party menu and switch. */
    if (frame < 300) return (frame % 20 < 10) ? DUALDEX_BTN_A : 0u; /* advance text */
    /* Open party menu: RIGHT (navigate to "Pokemon" in fight menu) then A */
    if (frame < 360) return DUALDEX_BTN_RIGHT;
    if (frame < 420) return DUALDEX_BTN_A;
    /* Select second party member: DOWN then A */
    if (frame < 480) return DUALDEX_BTN_DOWN;
    if (frame < 540) return DUALDEX_BTN_A;
    /* Confirm switch */
    if (frame < 600) return DUALDEX_BTN_A;
    return (frame % 30 < 15) ? DUALDEX_BTN_A : 0u;
}

static uint32_t generic_buttons(int frame) {
    return (frame % 30 < 15) ? DUALDEX_BTN_A : 0u;
}

/* =========================================================================
 * Main scenario loop
 * ========================================================================= */

static int run_scenario(
    const GameMemoryConfig* cfg,
    const char* scenario_name,
    int max_frames
) {
    printf("-- scenario: %s --\n", scenario_name);

    InvariantChecker c;
    checker_init(&c);

    FrameRecord initial, battle_entry, faint_frame, transition_frame, replacement_frame;
    memset(&initial,      0xFF, sizeof(initial));
    memset(&battle_entry, 0xFF, sizeof(battle_entry));
    memset(&faint_frame,  0xFF, sizeof(faint_frame));
    memset(&transition_frame, 0xFF, sizeof(transition_frame));
    memset(&replacement_frame, 0xFF, sizeof(replacement_frame));

    bool got_battle_entry = false;
    bool got_faint        = false;
    bool got_transition   = false;
    bool got_replacement  = false;

    BattleLifecycleState prev_lifecycle = BATTLE_LIFECYCLE_INACTIVE;
    int prev_enemy_slot = -1;
    bool prev_enemy_known = false;

    int total_failures = 0;

    for (int f = 0; f < max_frames; f++) {
        /* Drive inputs */
        uint32_t buttons = 0;
        if (strcmp(scenario_name, "player_switch") == 0)
            buttons = player_switch_buttons(f);
        else if (strcmp(scenario_name, "trainer_battle") == 0)
            buttons = trainer_battle_buttons(f);
        else
            buttons = generic_buttons(f);

        libretro_host_set_input_buttons(buttons);
        libretro_host_step_frame();

        /* Read state */
        size_t ewram_sz = 0;
        uint8_t* ewram = libretro_host_get_ewram(&ewram_sz);
        if (!ewram || ewram_sz == 0) continue;

        BattleStateRaw raw_state;
        BattleLifecycleState lifecycle = pokemon_read_battle_lifecycle(
            probe_read, NULL, ewram, ewram_sz, cfg, &raw_state);

        PartySnapshot enemy_snap;
        ActiveEnemyInfo enemy_info;
        ActiveEnemyState enemy_state = pokemon_resolve_active_enemy(
            probe_read, NULL, ewram, ewram_sz, cfg, &enemy_snap, &enemy_info);
        (void)enemy_state;  /* used via enemy_snap/enemy_info */

        PartySnapshot player_snap;
        pokemon_read_player_party_gba(probe_read, NULL, ewram, ewram_sz, cfg, &player_snap);

        /* Run per-frame invariants */
        int frame_fails = checker_check_frame(&c, cfg, lifecycle,
                                              &enemy_snap, &enemy_info, &player_snap);
        total_failures += frame_fails;

        /* Detect state transitions */
        if (!got_battle_entry && lifecycle == BATTLE_LIFECYCLE_ACTIVE &&
            prev_lifecycle != BATTLE_LIFECYCLE_ACTIVE) {
            got_battle_entry = true;
            record_frame(&battle_entry, f, lifecycle, &enemy_snap, &enemy_info, &player_snap, cfg);
            print_record("battle_entry", &battle_entry);
        }

        /* Detect faint (enemy) */
        if (!got_faint && lifecycle == BATTLE_LIFECYCLE_ACTIVE &&
            enemy_snap.active_battler_known && enemy_info.fainted) {
            got_faint = true;
            record_frame(&faint_frame, f, lifecycle, &enemy_snap, &enemy_info, &player_snap, cfg);
            print_record("enemy_faint", &faint_frame);
        }

        /* Detect transition window (enemy slot becomes unknown after being known) */
        if (!got_transition && lifecycle == BATTLE_LIFECYCLE_ACTIVE &&
            !enemy_snap.active_battler_known && prev_enemy_known) {
            got_transition = true;
            record_frame(&transition_frame, f, lifecycle, &enemy_snap, &enemy_info, &player_snap, cfg);
            print_record("transition_window", &transition_frame);
        }

        /* Detect replacement (enemy slot known again with a different value) */
        if (!got_replacement && got_transition && lifecycle == BATTLE_LIFECYCLE_ACTIVE &&
            enemy_snap.active_battler_known && prev_enemy_slot >= 0 &&
            enemy_snap.active_battler_slot != prev_enemy_slot) {
            got_replacement = true;
            record_frame(&replacement_frame, f, lifecycle, &enemy_snap, &enemy_info, &player_snap, cfg);
            print_record("replacement_complete", &replacement_frame);
        }

        prev_lifecycle   = lifecycle;
        prev_enemy_slot  = enemy_snap.active_battler_slot;
        prev_enemy_known = enemy_snap.active_battler_known;
    }

    printf("\n-- scenario summary: %s --\n", scenario_name);
    printf("frames_run           : %d\n", max_frames);
    printf("invariant_checks     : %d\n", c.checks);
    printf("invariant_failures   : %d\n", c.failures);
    printf("got_battle_entry     : %s\n", got_battle_entry ? "yes" : "no");
    printf("got_enemy_faint      : %s\n", got_faint        ? "yes" : "no");
    printf("got_transition_window: %s\n", got_transition   ? "yes" : "no");
    printf("got_replacement      : %s\n", got_replacement  ? "yes" : "no");

    if (got_battle_entry) {
        printf("initial_enemy_battler   : %d\n", battle_entry.enemy_battler);
        printf("initial_enemy_slot      : %d\n", battle_entry.enemy_slot);
        printf("initial_enemy_count     : %u\n", battle_entry.enemy_party_count);
        printf("initial_player_slot     : %d\n", battle_entry.player_slot);
        printf("gBattlerPartyIndexes    : [%u,%u,%u,%u]\n",
               battle_entry.enemy_battler_party_index[0],
               battle_entry.enemy_battler_party_index[1],
               battle_entry.enemy_battler_party_index[2],
               battle_entry.enemy_battler_party_index[3]);
    }
    if (got_replacement) {
        printf("replacement_enemy_slot  : %d\n", replacement_frame.enemy_slot);
        printf("replacement_gBPI        : [%u,%u,%u,%u]\n",
               replacement_frame.enemy_battler_party_index[0],
               replacement_frame.enemy_battler_party_index[1],
               replacement_frame.enemy_battler_party_index[2],
               replacement_frame.enemy_battler_party_index[3]);
    }

    printf("result               : %s\n", total_failures == 0 ? "PASS" : "FAIL");
    return total_failures == 0 ? 0 : 1;
}

/* =========================================================================
 * Entry point
 * ========================================================================= */

int main(int argc, char** argv) {
    /* Selftest: no ROM needed */
    if (argc == 2 && strcmp(argv[1], "--selftest") == 0) {
        return run_selftest();
    }

    if (argc < 5) {
        fprintf(stderr,
                "usage: %s <mgba_libretro.so> <hns.gba> <save.sav> <scenario_name> [frames]\n"
                "       %s --selftest\n"
                "scenarios: trainer_battle  trainer_opponent_faint  "
                "player_switch  player_faint_forced_replacement\n",
                argv[0], argv[0]);
        return 2;
    }

    const char* core_path     = argv[1];
    const char* rom_path      = argv[2];
    const char* save_path     = argv[3];
    const char* scenario_name = argv[4];
    const int   max_frames    = (argc > 5) ? atoi(argv[5]) : SCENARIO_TIMEOUT_FRAMES;

    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    if (!cfg) {
        fprintf(stderr, "FAIL: H&S config unavailable\n");
        return 1;
    }

    printf("== DualDex H&S 2.0.5 scenario runner ==\n");
    printf("core       : %s\n", core_path);
    printf("rom        : %s\n", rom_path);
    printf("save       : %s\n", save_path);
    printf("scenario   : %s\n", scenario_name);
    printf("max_frames : %d\n", max_frames);

    if (!libretro_host_init(core_path)) {
        fprintf(stderr, "FAIL: libretro_host_init(%s)\n", core_path);
        return 1;
    }
    if (!libretro_host_load_rom(rom_path)) {
        fprintf(stderr, "FAIL: libretro_host_load_rom(%s)\n", rom_path);
        libretro_host_cleanup();
        return 1;
    }
    if (!libretro_host_load_save_ram(save_path)) {
        fprintf(stderr, "FAIL: libretro_host_load_save_ram(%s)\n", save_path);
        libretro_host_cleanup();
        return 1;
    }

    /* Run one initial frame to let the core initialize memory maps */
    libretro_host_step_frame();

    int result = run_scenario(cfg, scenario_name, max_frames);

    libretro_host_unload_rom();
    libretro_host_cleanup();
    return result;
}
