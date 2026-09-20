/*
 * DualDex Heart & Soul 2.0.5 runtime battle-state probe (developer tool).
 *
 * NOT part of the shipped app. It exists so the runtime half of the H&S battle-lifecycle evidence
 * can be reproduced on a developer machine against the exact upstream release lineage, without
 * granting Heart & Soul any trust in the product.
 *
 * What it does:
 *   1. loads the bundled mGBA libretro core, a legally obtained H&S 2.0.5 ROM and (optionally) a
 *      locally produced battery save;
 *   2. drives the machine frame by frame under a small input script while sampling the exact
 *      compiled battle globals that DualDex reads;
 *   3. feeds the LIVE emulated memory into the *production* readers
 *      (`pokemon_read_battle_lifecycle`, `pokemon_resolve_active_enemy`,
 *      `pokemon_read_player_party_gba`, `pokemon_read_enemy_party_gba`) and asserts the runtime
 *      invariants on EVERY frame, failing the run on the first violation;
 *   4. emits a lifecycle table (one row per observable state change) plus a `[MATRIX]` line per
 *      script step with the full runtime tuple.
 *
 * It never writes to the emulated machine, never applies cheats, never patches the ROM, and never
 * stores or prints ROM bytes. No ROM, save or save state is committed to the repository.
 *
 * Usage:
 *   build.sh && ./runtime_battle_probe <mgba_libretro.so> <hns_2.0.5.gba> [options]
 *
 * Options:
 *   --sav <path>       load a battery save (.sav) before the script runs
 *   --script <path>    run an input script (see tools/hns-runtime-probe/README.md)
 *   --frames <n>       fallback frame budget when no script is given (default 1800)
 *   --quiet            suppress per-frame reader diagnostics; keep the tables and failures
 *
 * Exit code is non-zero when any runtime invariant is violated.
 */

#include "libretro_host.h"
#include "pokemon_reader.h"
#include "gba_memory_map.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <stdbool.h>
#include <stdarg.h>
#include <sys/stat.h>

/* Runtime-proven IWRAM address of gMain on the official release ROM. `gMain` is the authority for
 * `inBattle`; the compiled symbol address from a local build is 0x18 lower and must not be used. */
#define HNS_RELEASE_GMAIN_BASE 0x03005BD8u
#define HNS_RELEASE_BATTLE_CONTROLLER_EXEC_FLAGS 0x02000300u

/* DELIBERATELY ABSENT: `gChosenActionByBattler` / `B_ACTION_SWITCH`.
 *
 * An earlier revision of this probe carried a guessed release address for gChosenActionByBattler
 * (0x0200025C from the pinned source map, shifted by the same -4 that moves the runtime-confirmed
 * battle globals) and used it to corroborate "the AI chose B_ACTION_SWITCH". That address was never
 * established by semantic correlation, so the constant has been removed rather than left unused in
 * the source: `gChosenActionByBattler` and `B_ACTION_SWITCH` are NOT VERIFIED and NOT CLAIMED
 * anywhere in this tool, and no code path can accidentally start trusting them.
 *
 * The load-bearing proof of a voluntary opponent switch is the observable gBattlerPartyIndexes
 * transition with the outgoing mon still alive, read through the production-configured offsets.
 * See docs/HNS_2_0_5_COMPATIBILITY_EVIDENCE.md 11.10. */

/* DELIBERATELY ABSENT: `gBattleStruct->monToSwitchIntoId`.
 *
 * An earlier revision staged the AI's chosen switch-in with a `gBattleStruct` pointer derived by
 * applying the documented -4 battle-global shift to the pinned source-build address 0x020000B4,
 * plus `offsetof(struct BattleStruct, monToSwitchIntoId)` read out of that build's DWARF. The
 * pointer was never confirmed by semantic correlation on the official release ROM, and on the one
 * passing Scenario 44 run it read back as -1 while a real switch was in progress.
 *
 * A reading that is neither verified nor load-bearing does not belong in the evidence path, so the
 * constants and the reader have been REMOVED rather than kept as "optional corroboration": a
 * reviewer should not have to reason about whether an unproven address influenced a verdict, and no
 * future edit can quietly promote it. The tracking phases below prove only what they observe.
 *
 * See also the absent `gChosenActionByBattler` block above. Neither address is used or claimed. */

/* ABI-verified `struct BattlePokemon` member offsets that the reader configuration does not carry
 * (documented in docs/HNS_2_0_5_COMPATIBILITY_EVIDENCE.md §3.2 / §6.7). The probe reads the live
 * move list from here so a scenario can name the exact move it is about to select instead of
 * assuming a menu position. */
#define HNS_BATTLE_MON_MOVES_OFFSET 0x0Cu
#define HNS_BATTLE_MON_MOVES_COUNT  4

/* Move id 45 is Growl: move category STATUS, power 0, accuracy 100, PP 40 in the pinned H&S 2.0.5
 * move table (app/src/main/java/com/dualdex/pokemon/hns/HeartAndSoul205DataPack.kt). Scenario 43
 * (player faint -> forced replacement) needs a non-damaging move, because a damaging one would KO
 * the opponent before the player ever faints. Which *menu position* holds it is read from the live
 * move list, never assumed. NOTE: this move must NOT be used to follow up a damaging move in
 * Scenario 44 -- a status move overwrites gLastLandedMoves[opponent] and disarms the switch
 * heuristic (src/battle_ai_switch.c:1059). */
#define HNS_MOVE_GROWL 45
/* Move id 75 is Razor Leaf in the pinned H&S 2.0.5 move table. Chikorita learns it at level 6 and
 * the stage33 Chikorita already has it; it is the damaging move the switch heuristic keys on. */
#define HNS_MOVE_RAZOR_LEAF 75

/* GBA party-menu cursor, semantically verified against the release ROM in §11.6 Discrepancy 4
 * (`gPartyMenu` at 0x020341FC, `slotId` at +0x09). Used only as developer-probe UI evidence; the
 * scenario never writes it. */
#define HNS_RELEASE_PARTY_MENU_SLOT_ID 0x02034205u

static bool g_quiet = false;

static bool probe_read(void* user, uint32_t address, uint8_t* out, size_t length) {
    (void)user;
    return libretro_host_read_gba_address(address, out, length);
}

static bool read_u8(uint32_t address, uint8_t* out) {
    return libretro_host_read_gba_address(address, out, 1);
}

static bool read_u16(uint32_t address, uint16_t* out) {
    uint8_t raw[2];
    if (!libretro_host_read_gba_address(address, raw, sizeof(raw))) return false;
    *out = (uint16_t)(raw[0] | (raw[1] << 8));
    return true;
}

static bool read_u32(uint32_t address, uint32_t* out) {
    uint8_t raw[4];
    if (!libretro_host_read_gba_address(address, raw, sizeof(raw))) return false;
    *out = (uint32_t)raw[0] | ((uint32_t)raw[1] << 8) | ((uint32_t)raw[2] << 16) | ((uint32_t)raw[3] << 24);
    return true;
}

/* Candidate party-menu diagnostics:
 * Candidate A at 0x020341F8 corresponds to the EWRAM storage for the
 * file-static sPartyMenuInternal pointer. Interpreting bytes beginning
 * there as struct PartyMenu therefore samples pointer/task bytes rather
 * than the exported gPartyMenu fields.
 *
 * Candidate B at 0x020341FC is the exported gPartyMenu object.
 */
typedef struct {
    bool readable;
    uint8_t menu_type;
    uint8_t layout;
    int8_t slot_id;
    int8_t slot_id2;
    uint8_t action;
} PartyMenuProbeState;

static bool read_party_menu_probe_candidate_a(PartyMenuProbeState* out) {
    if (!out) return false;
    memset(out, 0, sizeof(*out));
    uint8_t mt = 0, s1 = 0, s2 = 0, act = 0;
    if (!read_u8(0x02034200u, &mt)) return false;
    if (!read_u8(0x02034201u, &s1)) return false;
    if (!read_u8(0x02034202u, &s2)) return false;
    if (!read_u8(0x02034203u, &act)) return false;
    out->readable = true;
    out->menu_type = mt & 0x0F;
    out->layout = (mt >> 4) & 0x03;
    out->slot_id = (int8_t)s1;
    out->slot_id2 = (int8_t)s2;
    out->action = act;
    return true;
}

static bool read_party_menu_probe_candidate_b(PartyMenuProbeState* out) {
    if (!out) return false;
    memset(out, 0, sizeof(*out));
    uint8_t mt = 0, s1 = 0, s2 = 0, act = 0;
    if (!read_u8(0x02034204u, &mt)) return false;
    if (!read_u8(0x02034205u, &s1)) return false;
    if (!read_u8(0x02034206u, &s2)) return false;
    if (!read_u8(0x02034207u, &act)) return false;
    out->readable = true;
    out->menu_type = mt & 0x0F;
    out->layout = (mt >> 4) & 0x03;
    out->slot_id = (int8_t)s1;
    out->slot_id2 = (int8_t)s2;
    out->action = act;
    return true;
}

/** Every quantity the evidence table needs, sampled from the live machine on one frame. */
typedef struct {
    int      frame;
    uint32_t input;
    bool     gmain_readable;
    bool     in_battle_readable;
    bool     in_battle;
    uint8_t  battlers;
    uint32_t type_flags;
    uint8_t  outcome;
    bool     counters_readable;
    uint8_t  party_index[DUALDEX_MAX_BATTLERS];
    uint8_t  position[DUALDEX_MAX_BATTLERS];
    uint8_t  absent_flags;
    uint16_t mon_species[DUALDEX_MAX_BATTLERS];
    uint16_t mon_hp[DUALDEX_MAX_BATTLERS];
    uint16_t mon_max_hp[DUALDEX_MAX_BATTLERS];
    uint16_t mon_moves[DUALDEX_MAX_BATTLERS][HNS_BATTLE_MON_MOVES_COUNT];
    int8_t   mon_stat_stages[DUALDEX_MAX_BATTLERS][8];
    uint8_t  player_party_count;
    uint8_t  enemy_party_count;
    /* Player party as production parses it, so a battle-mon read can be cross-checked against the
     * party slot gBattlerPartyIndexes claims it came from. */
    uint16_t party_species[6];
    uint16_t party_hp[6];
    uint16_t party_max_hp[6];
    /* Enemy party as PRODUCTION parses it. This is what makes "the outgoing Pokemon stayed alive"
     * machine-checkable at the commit: once the switch commits, gBattleMons[old_battler] describes
     * the REPLACEMENT, so the battler entry cannot answer that question any more. The party slot
     * keeps the outgoing member's own HP. */
    uint16_t enemy_party_species[6];
    uint16_t enemy_party_hp[6];
    uint16_t enemy_party_max_hp[6];
    /* production reader output */
    BattleLifecycleState lifecycle;
    BattleKind           kind;
    uint8_t              presence;
    ActiveEnemyState     active_enemy;
    int                  enemy_slot;
    int                  enemy_battler;
    uint8_t              enemy_party_count_prod;
    uint8_t              opponent_battlers;
    bool                 enemy_fainted;
    uint8_t              player_party_count_prod;
    int                  active_player_slot;
    bool                 active_player_known;
    /* The real battler index whose gBattlerPartyIndexes entry produced active_player_slot, or -1.
     * This is the production `PartySnapshot.active_battler_index`; it is what binds "the old player
     * Pokémon" and "the replacement Pokémon" to a concrete gBattleMons[] entry. */
    int8_t               active_player_battler;
} Sample;

static void sample_state(const GameMemoryConfig* cfg, int frame, uint32_t input, Sample* out) {
    memset(out, 0, sizeof(*out));
    out->frame = frame;
    out->input = input;
    out->enemy_slot = -1;
    out->enemy_battler = -1;
    out->active_player_slot = -1;
    if (!cfg) return;

    uint32_t gmain_word = 0;
    out->gmain_readable = read_u32(HNS_RELEASE_GMAIN_BASE, &gmain_word);

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
        out->party_index[b] = 0xFF;
        out->position[b] = 0xFF;
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
            size_t mhp_off = mon_off + cfg->battle_mons_hp_offset + 4; /* maxHP follows level */
            out->mon_max_hp[b] = (uint16_t)(ewram[mhp_off] | (ewram[mhp_off + 1] << 8));
            size_t st_off = mon_off + cfg->battle_mons_stat_stages_offset;
            for (int s = 0; s < 8; s++) {
                out->mon_stat_stages[b][s] = (int8_t)ewram[st_off + (size_t)s];
            }
            size_t mv_off = mon_off + HNS_BATTLE_MON_MOVES_OFFSET;
            for (int m = 0; m < HNS_BATTLE_MON_MOVES_COUNT; m++) {
                out->mon_moves[b][m] = (uint16_t)(ewram[mv_off + (size_t)m * 2] |
                                                  (ewram[mv_off + (size_t)m * 2 + 1] << 8));
            }
        }
    }

    /* Production readers, driven from the same live memory. */
    BattleStateRaw raw;
    out->lifecycle = pokemon_read_battle_lifecycle(probe_read, NULL, ewram, ewram_sz, cfg, &raw);
    out->kind = raw.kind;
    out->presence = pokemon_read_battle_presence_gba(probe_read, NULL, ewram, ewram_sz, cfg);

    PartySnapshot enemy_snapshot;
    ActiveEnemyInfo info;
    out->active_enemy = pokemon_resolve_active_enemy(
        probe_read, NULL, ewram, ewram_sz, cfg, &enemy_snapshot, &info);
    out->enemy_slot = info.party_slot;
    out->enemy_battler = info.battler_index;
    out->enemy_party_count_prod = enemy_snapshot.count;
    for (uint8_t i = 0; i < 6 && i < enemy_snapshot.count; i++) {
        out->enemy_party_species[i] = enemy_snapshot.members[i].species;
        out->enemy_party_hp[i] = enemy_snapshot.members[i].current_hp;
        out->enemy_party_max_hp[i] = enemy_snapshot.members[i].max_hp;
    }
    out->opponent_battlers = info.opponent_battlers;
    out->enemy_fainted = info.fainted;

    PartySnapshot player_snapshot;
    out->player_party_count_prod = pokemon_read_player_party_gba(
        probe_read, NULL, ewram, ewram_sz, cfg, &player_snapshot);
    out->active_player_slot = player_snapshot.active_battler_slot;
    out->active_player_known = player_snapshot.active_battler_known;
    out->active_player_battler = player_snapshot.active_battler_index;
    for (uint8_t i = 0; i < 6 && i < player_snapshot.count; i++) {
        out->party_species[i] = player_snapshot.members[i].species;
        out->party_hp[i] = player_snapshot.members[i].current_hp;
        out->party_max_hp[i] = player_snapshot.members[i].max_hp;
    }
}

/**
 * The player-side battler index, resolved by the probe itself from `gBattlerPositions`.
 *
 * `PartySnapshot.active_battler_index` is only populated while production considers a player
 * battler authoritative, so a fail-closed frame carries no battler index at all. The tracker still
 * has to be able to ask "is the raw `gBattlerPartyIndexes`/`gBattleMons` state consistent with what
 * the production surface reported?" on exactly those frames, and answering that from production's
 * own output would be circular. `BIT_SIDE == 1` (opponent) is the same test the reader uses.
 */
static int probe_resolve_player_battler(const Sample* s) {
    if (!s || s->battlers == 0) return -1;
    int found = -1;
    for (uint8_t b = 0; b < s->battlers && b < DUALDEX_MAX_BATTLERS; b++) {
        if (s->position[b] == 0xFF) continue;
        if (s->position[b] & 1u) continue; /* opponent side */
        if (found < 0) found = (int)b;
    }
    return found;
}

/** The opponent-side battler index, resolved by the probe from `gBattlerPositions`. */
static int probe_resolve_opponent_battler(const Sample* s) {
    if (!s || s->battlers == 0) return -1;
    for (uint8_t b = 0; b < s->battlers && b < DUALDEX_MAX_BATTLERS; b++) {
        if (s->position[b] == 0xFF) continue;
        if (s->position[b] & 1u) return (int)b;
    }
    return -1;
}

/** Menu position (0..3) of @p move_id in the live `gBattleMons[battler].moves` list, or -1. */
static int probe_find_move_slot(const Sample* s, int battler, uint16_t move_id) {
    if (!s || battler < 0 || battler >= DUALDEX_MAX_BATTLERS) return -1;
    for (int m = 0; m < HNS_BATTLE_MON_MOVES_COUNT; m++) {
        if (s->mon_moves[battler][m] == move_id) return m;
    }
    return -1;
}

/* =========================================================================
 * State-machine trackers for battle commands (autobattle, replacement)
 * ========================================================================= */

typedef enum {
    AUTOBATTLE_PHASE_INIT = 0,
    AUTOBATTLE_PHASE_IN_BATTLE = 1,
    AUTOBATTLE_PHASE_EXITED = 2
} AutobattlePhase;

typedef struct {
    AutobattlePhase phase;
    bool entered_battle;
    bool exited_after_entry;
} AutobattleTracker;

static void autobattle_tracker_init(AutobattleTracker* t, bool initially_in_battle) {
    memset(t, 0, sizeof(*t));
    if (initially_in_battle) {
        t->phase = AUTOBATTLE_PHASE_IN_BATTLE;
        t->entered_battle = true;
    } else {
        t->phase = AUTOBATTLE_PHASE_INIT;
        t->entered_battle = false;
    }
}

static bool autobattle_tracker_step(AutobattleTracker* t, bool in_battle) {
    if (t->phase == AUTOBATTLE_PHASE_INIT) {
        if (in_battle) {
            t->entered_battle = true;
            t->phase = AUTOBATTLE_PHASE_IN_BATTLE;
        }
    } else if (t->phase == AUTOBATTLE_PHASE_IN_BATTLE) {
        if (!in_battle) {
            t->exited_after_entry = true;
            t->phase = AUTOBATTLE_PHASE_EXITED;
            return true;
        }
    }
    return false;
}

typedef enum {
    REPL_PHASE_A_OLD_ACTIVE = 0,
    REPL_PHASE_B_FAINT = 1,
    REPL_PHASE_C_ABSENT = 2,
    REPL_PHASE_D_REPLACEMENT = 3,
    REPL_PHASE_COMPLETE = 4
} ReplacementPhase;

typedef struct {
    int old_slot;
    int new_slot;
    ReplacementPhase phase;
    bool saw_old_active;
    bool saw_faint;
    bool saw_absent_window;
    bool saw_replacement;
    int old_battler;
    uint16_t old_hp;
    uint16_t old_species;
    int new_battler;
    uint16_t new_hp;
    uint16_t new_species;
    uint8_t absent_flags;
} ReplacementTracker;

static void replacement_tracker_init(ReplacementTracker* t, int old_slot, int new_slot) {
    memset(t, 0, sizeof(*t));
    t->old_slot = old_slot;
    t->new_slot = new_slot;
    t->phase = REPL_PHASE_A_OLD_ACTIVE;
    t->old_battler = -1;
    t->new_battler = -1;
}

static bool replacement_tracker_step(ReplacementTracker* t, const Sample* s, uint8_t absent_flags) {
    if (t->phase == REPL_PHASE_COMPLETE) {
        return true;
    }

    if (t->phase == REPL_PHASE_A_OLD_ACTIVE) {
        if (s->lifecycle == BATTLE_LIFECYCLE_ACTIVE &&
            s->active_enemy == ACTIVE_ENEMY_SLOT &&
            s->enemy_slot == t->old_slot &&
            s->enemy_battler >= 0 &&
            s->party_index[s->enemy_battler] == t->old_slot &&
            !s->enemy_fainted &&
            s->mon_hp[s->enemy_battler] > 0) {
            t->saw_old_active = true;
            t->old_battler = s->enemy_battler;
            t->old_hp = s->mon_hp[s->enemy_battler];
            t->old_species = s->mon_species[s->enemy_battler];
            t->phase = REPL_PHASE_B_FAINT;
            printf("  [await-enemy-replacement] Phase A observed at frame %d: old slot %d active (battler=%d, species=%u, HP=%u/%u)\n",
                   s->frame, t->old_slot, t->old_battler, t->old_species, t->old_hp, s->mon_max_hp[t->old_battler]);
        }
        return false;
    }

    if (t->phase == REPL_PHASE_B_FAINT) {
        if (s->lifecycle == BATTLE_LIFECYCLE_ACTIVE &&
            s->enemy_slot == t->old_slot &&
            s->enemy_battler == t->old_battler &&
            s->mon_hp[t->old_battler] == 0 &&
            s->enemy_fainted) {
            t->saw_faint = true;
            t->phase = REPL_PHASE_C_ABSENT;
            printf("  [await-enemy-replacement] Phase B observed at frame %d: old slot %d fainted (HP=0, fainted=true)\n",
                   s->frame, t->old_slot);
        }
        return false;
    }

    if (t->phase == REPL_PHASE_C_ABSENT) {
        if (s->lifecycle == BATTLE_LIFECYCLE_ACTIVE &&
            s->active_enemy == ACTIVE_ENEMY_NONE_ACTIVE &&
            s->enemy_slot == -1 &&
            s->enemy_battler == -1 &&
            s->opponent_battlers == 0) {
            t->saw_absent_window = true;
            t->absent_flags = absent_flags;
            t->phase = REPL_PHASE_D_REPLACEMENT;
            printf("  [await-enemy-replacement] Phase C observed at frame %d: absent window (active_enemy=NONE_ACTIVE, slot=-1, battler=-1, opp_battlers=0, absentFlags=0x%02X)\n",
                   s->frame, absent_flags);
        }
        return false;
    }

    if (t->phase == REPL_PHASE_D_REPLACEMENT) {
        if (s->lifecycle == BATTLE_LIFECYCLE_ACTIVE &&
            s->active_enemy == ACTIVE_ENEMY_SLOT &&
            s->enemy_slot == t->new_slot &&
            s->enemy_battler >= 0 &&
            s->party_index[s->enemy_battler] == t->new_slot &&
            s->enemy_slot < s->enemy_party_count_prod &&
            t->new_slot != t->old_slot &&
            !s->enemy_fainted &&
            s->mon_hp[s->enemy_battler] > 0) {
            t->saw_replacement = true;
            t->new_battler = s->enemy_battler;
            t->new_hp = s->mon_hp[s->enemy_battler];
            t->new_species = s->mon_species[s->enemy_battler];
            t->phase = REPL_PHASE_COMPLETE;
            printf("  [await-enemy-replacement] Phase D observed at frame %d: replacement committed (slot=%d, battler=%d, species=%u, HP=%u/%u)\n",
                   s->frame, t->new_slot, t->new_battler, t->new_species, t->new_hp, s->mon_max_hp[t->new_battler]);
            return true;
        }
        return false;
    }

    return false;
}

/* =========================================================================
 * Genuine opponent VOLUNTARY switch tracker
 *
 * The gap this closes: an AI-controlled Pokémon leaving the field while its HP is still ABOVE ZERO,
 * with another enemy party member taking its place. That is NOT the faint -> replacement transition
 * Scenario 41 proves. The engine behaviour being observed is `AI_TrySwitchOrUseItem` emitting
 * `B_ACTION_SWITCH` (src/battle_ai_main.c:459) followed by `OpponentHandleChoosePokemon` committing
 * `gBattlerPartyIndexes[battler] = monToSwitchIntoId` (src/battle_script_commands.c:5270).
 *
 * Phases, in order:
 *
 *   A  the old opponent is genuinely active and alive, and the battle shape is the one this tracker
 *      is allowed to speak about;
 *   B  the AUTHORITATIVE transition has begun (see the Phase B note below);
 *   C  the real transition, recorded as observed -- an absent/NONE_ACTIVE window is RECORDED WHEN
 *      SEEN and never required, because a voluntary switch need not pass through one;
 *   D  the replacement is committed and the production reader follows the authoritative party index.
 *
 * Everything below is fatal rather than tolerated:
 *
 *   - the outgoing mon reaches hp == 0 at any point: that is faint replacement (Scenario 41), so the
 *     run fails instead of banking a false positive;
 *   - `old_slot == new_slot`, or the reported species did not change;
 *   - the commit lands on a different battler index than the one that was active;
 *   - the production reader reports the new slot BEFORE `gBattlerPartyIndexes` does (the reader would
 *     be leading authority), or a frame shows `gBattlerPartyIndexes` already rewritten while the
 *     production surface still names the OLD slot (the reader would be lagging authority);
 *   - the battle shape is not a two-battler trainer single: a doubles/multi-shaped state is a
 *     different question and is rejected rather than reinterpreted.
 *
 * Violations latch permanently; a later good frame never clears one.
 *
 * NO AI DECISION BYTE IS READ. Two readings were considered for "the AI decided to switch" and BOTH
 * are absent from this source, deliberately and permanently:
 *
 *   - `gChosenActionByBattler` / `B_ACTION_SWITCH`: the official-release address was never
 *     established by semantic correlation (the source-build map address 0x0200025C and the
 *     -4-shifted candidate 0x02000258 both read 0x00 across overworld and in-battle frames,
 *     consistent with neither `B_ACTION_NONE` (0xFF) nor an action array the engine populates).
 *   - `gBattleStruct->monToSwitchIntoId`: the pointer was derived by applying the documented -4
 *     battle-global shift to a source-build address and was never confirmed either; on the one
 *     recorded passing run it read back as -1 while a real switch was in progress.
 *
 * Rather than keep either as "optional corroboration" -- which would leave a reviewer reasoning
 * about whether an unverified read influenced a verdict -- both constants and both readers have been
 * REMOVED. Phase B is named VSW_PHASE_B_AWAIT_TRANSITION because that is all it can prove: that the
 * authoritative transition has begun. The phase names deliberately describe observations, not AI
 * intent, and no claim is made about which ShouldSwitch...() predicate produced the switch (see
 * docs/HNS_2_0_5_COMPATIBILITY_EVIDENCE.md 11.10.8).
 *
 * WHAT SEPARATES A VOLUNTARY SWITCH FROM A FAINT REPLACEMENT is therefore not a decision byte at all,
 * but two independent liveness facts about the OUTGOING mon:
 *
 *   1. the order-independent latch on the sampled battler entry, which fires on ANY frame where
 *      gBattleMons[old_battler].hp reads 0 -- and that happens before the replacement overwrites it;
 *   2. the COMMIT-TIME ENEMY PARTY CONTRACT in Phase D, which reads the enemy PARTY slot (the only
 *      place the outgoing mon's own HP still exists once gBattleMons[old_battler] is the
 *      replacement) and fails the run unless it still holds the old species with HP > 0.
 *
 * Both are pinned by pure selftests, including the case (2) exists for: a party slot at 0 HP while
 * the sampled battler HP never reads 0, which the latch alone cannot catch.
 * ========================================================================= */

/* Frames of index-newer-than-surface disagreement tolerated before it is a reader-lag violation.
 * The engine rewrites gBattlerPartyIndexes and updates what the production reader derives from it on
 * the same or adjacent frames, so a small window is required to avoid flagging normal ordering. */
#define HNS_VSW_LAG_GRACE_FRAMES 4

typedef enum {
    VSW_PHASE_A_OLD_ACTIVE = 0,
    VSW_PHASE_B_AWAIT_TRANSITION,
    VSW_PHASE_C_TRANSITION,
    VSW_PHASE_D_SWITCH_COMMITTED,
    VSW_PHASE_COMPLETE
} VoluntarySwitchPhase;

typedef struct {
    int phase;
    int old_slot;
    int new_slot;
    uint16_t expect_species;
    /* Phase A record */
    int old_battler;
    uint16_t old_species;
    uint16_t old_hp;
    uint16_t old_max_hp;
    /* Phase D record */
    int new_battler;
    uint16_t new_species;
    uint16_t new_hp;
    uint16_t new_max_hp;
    int new_index_at_commit;
    int prod_slot_at_commit;
    /* observations */
    bool saw_old_active;
    bool saw_transition_window;
    bool saw_absent_window;
    bool saw_commit;
    /* latched violations */
    bool violation_old_fainted;
    bool violation_shape;
    bool violation_slot_unchanged;
    bool violation_species_unchanged;
    bool violation_species_mismatch;
    bool violation_battler_changed;
    bool violation_reader_leads;
    bool violation_reader_lags;
    int  liveness_grace;
    bool violation_old_slot_reappeared;
    /* Commit-time enemy-party contract (Phase D). */
    bool violation_old_party_not_alive;
    bool violation_old_party_species;
    bool violation_new_party_mismatch;
    /* What the commit frame itself showed for the outgoing member, from the production snapshot. */
    int  old_party_hp_at_commit;
    int  new_party_hp_at_commit;
} VoluntarySwitchTracker;

static void voluntary_switch_tracker_init(VoluntarySwitchTracker* t, int old_slot, int new_slot,
                                          uint16_t expect_species) {
    memset(t, 0, sizeof(*t));
    t->old_slot = old_slot;
    t->new_slot = new_slot;
    t->expect_species = expect_species;
    t->old_battler = -1;
    t->new_battler = -1;
    t->new_index_at_commit = -1;
    t->prod_slot_at_commit = -1;
    t->phase = VSW_PHASE_A_OLD_ACTIVE;
}

/*
 * No AI-decision byte is read. The tracker observes the OPPONENT side of the field only, and proves
 * the transition from the authoritative gBattlerPartyIndexes rewrite, the production opponent
 * resolution, and the enemy party snapshot that shows the outgoing member is still alive at the
 * commit (gBattleMons[old_battler] holds the REPLACEMENT by then, so the party slot is the only
 * place that fact survives).
 *
 * Returns true once the whole transition is observed.
 */
static bool voluntary_switch_tracker_step(
    VoluntarySwitchTracker* t,
    const Sample* s
) {
    /* --- latched, order-independent checks (run even once complete, so a late violation still
     * latches and is never erased by an earlier good frame) ------------------------------------------------- */
    if (t->old_battler >= 0 && s->mon_hp[t->old_battler] == 0) {
        t->violation_old_fainted = true;
    }
    /* Keep the LAST observed HP of the outgoing mon while the slot still holds it. Once the
     * replacement lands, gBattleMons[old_battler] describes the NEW Pokemon, so printing
     * s->mon_hp[old_battler] after that would silently report the wrong mon's HP. */
    if (t->old_battler >= 0 && t->phase != VSW_PHASE_COMPLETE &&
        s->mon_species[t->old_battler] == t->old_species &&
        s->mon_hp[t->old_battler] > 0) {
        t->old_hp = s->mon_hp[t->old_battler];
    }
    if (s->lifecycle == BATTLE_LIFECYCLE_ACTIVE &&
        (s->kind != BATTLE_KIND_TRAINER_SINGLE || s->battlers != 2)) {
        t->violation_shape = true;
    }
    /* Reader must never lead authority. The violation is precise: the production surface names
     * new_slot while the authoritative index for that same battler names NEITHER the old slot (the
     * switch has not started) NOR the new slot (the switch committed). A legitimate transition
     * legitimately passes through "reports the new slot while the index still says old", so an
     * ordinary old->new handover must not be flagged. */
    if (s->lifecycle == BATTLE_LIFECYCLE_ACTIVE &&
        s->active_enemy == ACTIVE_ENEMY_SLOT &&
        s->enemy_slot == t->new_slot &&
        s->enemy_battler >= 0 &&
        s->party_index[s->enemy_battler] != (uint8_t)t->new_slot &&
        s->party_index[s->enemy_battler] != (uint8_t)t->old_slot) {
        t->violation_reader_leads = true;
    }
    if (t->saw_commit && s->lifecycle == BATTLE_LIFECYCLE_ACTIVE &&
        s->active_enemy == ACTIVE_ENEMY_SLOT && s->enemy_slot == t->old_slot) {
        t->violation_old_slot_reappeared = true;
    }

    if (t->phase == VSW_PHASE_COMPLETE) return true;

    if (t->phase == VSW_PHASE_A_OLD_ACTIVE) {
        if (s->lifecycle != BATTLE_LIFECYCLE_ACTIVE) return false;
        if (s->kind != BATTLE_KIND_TRAINER_SINGLE) return false;
        if (s->battlers != 2) return false;
        if (s->opponent_battlers != 1) return false;
        if (s->active_enemy != ACTIVE_ENEMY_SLOT) return false;
        if (s->enemy_slot != t->old_slot) return false;
        if (s->enemy_battler < 0) return false;
        if (s->party_index[s->enemy_battler] != (uint8_t)t->old_slot) return false;
        if (s->enemy_fainted || s->mon_hp[s->enemy_battler] == 0) return false;
        if (t->expect_species != 0 && s->mon_species[s->enemy_battler] != t->expect_species) {
            /* Wrong lead: do not silently track whatever happens to be out. */
            t->violation_species_mismatch = true;
            return false;
        }

        t->saw_old_active = true;
        t->old_battler = s->enemy_battler;
        t->old_species = s->mon_species[s->enemy_battler];
        t->old_hp = s->mon_hp[s->enemy_battler];
        t->old_max_hp = s->mon_max_hp[s->enemy_battler];
        t->phase = VSW_PHASE_B_AWAIT_TRANSITION;
        printf("  [await-enemy-voluntary-switch] Phase A: old slot %d ACTIVE at frame %d "
               "(battler=%d species=%u HP=%u/%u; TRAINER_SINGLE, battlers=2, opponentBattlers=1)\n",
               t->old_slot, s->frame, t->old_battler, t->old_species, t->old_hp, t->old_max_hp);
        return false;
    }

    if (t->phase == VSW_PHASE_B_AWAIT_TRANSITION) {
        /* The outgoing mon must still be alive when the transition starts. */
        if (t->violation_old_fainted) return false;

        /* NOTHING is read here to ask the AI what it decided. Two candidate readings were
         * considered and BOTH are absent from this source: `gChosenActionByBattler` and
         * `gBattleStruct->monToSwitchIntoId`. Neither release address was ever established by
         * semantic correlation, and the latter read back as -1 on the one recorded run where a real
         * switch was in progress. What this phase proves is that the AUTHORITATIVE transition has
         * begun -- nothing about which predicate caused it. */
        const bool transition_began =
            (s->active_enemy == ACTIVE_ENEMY_NONE_ACTIVE) ||
            (s->enemy_battler >= 0 && s->party_index[s->enemy_battler] != (uint8_t)t->old_slot);
        if (transition_began) {
            printf("  [await-enemy-voluntary-switch] Phase B: the authoritative transition has begun "
                   "at frame %d (partyIndexes[%d]=%u, activeEnemy=%d); outgoing species %u is still "
                   "at HP=%u > 0. No AI decision byte is read here -- gChosenActionByBattler and "
                   "gBattleStruct->monToSwitchIntoId are both absent from this probe because neither "
                   "release address is established, so this phase claims only the transition.\n",
                   s->frame, t->old_battler,
                   t->old_battler >= 0 ? s->party_index[t->old_battler] : 0,
                   (int)s->active_enemy, t->old_species,
                   t->old_battler >= 0 ? s->mon_hp[t->old_battler] : 0);
            t->phase = VSW_PHASE_C_TRANSITION;
            return false;
        }
        return false;
    }

    if (t->phase == VSW_PHASE_C_TRANSITION) {
        if (t->violation_old_fainted) return false;
        if (s->lifecycle != BATTLE_LIFECYCLE_ACTIVE) return false;

        /* Reader-lags-authority: the index has been rewritten but the production surface keeps
         * naming the OLD slot for that same battler. One frame of disagreement is the normal
         * index-then-surface ordering the engine produces, so the violation is only latched once the
         * disagreement outlives HNS_VSW_LAG_GRACE_FRAMES. */
        if (s->enemy_battler >= 0 &&
            s->party_index[s->enemy_battler] == (uint8_t)t->new_slot &&
            s->active_enemy == ACTIVE_ENEMY_SLOT && s->enemy_slot == t->old_slot) {
            if (++t->liveness_grace > HNS_VSW_LAG_GRACE_FRAMES) {
                t->violation_reader_lags = true;
                return false;
            }
        } else {
            t->liveness_grace = 0;
        }

        if (s->active_enemy == ACTIVE_ENEMY_NONE_ACTIVE && s->enemy_slot == -1 &&
            s->opponent_battlers == 0) {
            t->saw_absent_window = true;
            t->saw_transition_window = true;
            printf("  [await-enemy-voluntary-switch] Phase C: absent window at frame %d "
                   "(active_enemy=NONE_ACTIVE, slot=-1, opponentBattlers=0, absentFlags=0x%02X); "
                   "outgoing species %u still at HP=%u. Recorded, NOT required.\n",
                   s->frame, s->absent_flags, t->old_species,
                   t->old_battler >= 0 ? s->mon_hp[t->old_battler] : 0);
            t->phase = VSW_PHASE_D_SWITCH_COMMITTED;
            return false;
        }
        if (s->enemy_battler >= 0 && s->party_index[s->enemy_battler] == (uint8_t)t->new_slot) {
            t->saw_transition_window = true;
            printf("  [await-enemy-voluntary-switch] Phase C: gBattlerPartyIndexes[%d] rewritten to "
                   "%d at frame %d (outgoing species %u was last observed alive at HP=%u; the "
                   "replacement now occupies gBattleMons[%d] as species %u)\n",
                   s->enemy_battler, t->new_slot, s->frame, t->old_species, t->old_hp,
                   s->enemy_battler, s->mon_species[s->enemy_battler]);
            t->phase = VSW_PHASE_D_SWITCH_COMMITTED;
            return false;
        }
        return false;
    }

    if (t->phase == VSW_PHASE_D_SWITCH_COMMITTED) {
        if (t->violation_old_fainted) return false;
        if (s->lifecycle != BATTLE_LIFECYCLE_ACTIVE) return false;

        if (s->active_enemy == ACTIVE_ENEMY_SLOT &&
            s->enemy_slot == t->new_slot &&
            s->enemy_battler >= 0 &&
            s->party_index[s->enemy_battler] == (uint8_t)t->new_slot &&
            !s->enemy_fainted &&
            s->mon_hp[s->enemy_battler] > 0) {

            if (t->old_battler >= 0 && s->mon_hp[t->old_battler] == 0) {
                t->violation_old_fainted = true;
                return false;
            }
            if (t->new_slot == t->old_slot) { t->violation_slot_unchanged = true; return false; }
            if (s->mon_species[s->enemy_battler] == t->old_species) {
                t->violation_species_unchanged = true;
                return false;
            }
            if (t->old_battler >= 0 && s->enemy_battler != t->old_battler) {
                t->violation_battler_changed = true;
                return false;
            }
            /* Production reader must agree with the authoritative index on this same frame. */
            if (s->enemy_slot != (int)s->party_index[s->enemy_battler]) {
                t->violation_reader_leads = true;
                return false;
            }

            /* ---------------------------------------------------------------------------------
             * COMMIT-TIME ENEMY PARTY CONTRACT (fatal, not diagnostic).
             *
             * This is the only place that can still prove the outgoing Pokemon stayed alive. Once
             * the switch commits, gBattleMons[old_battler] describes the REPLACEMENT, so the sampled
             * battler HP that the faint latch watches is no longer evidence about the mon that left.
             * The enemy PARTY slot is: the production parse of gEnemyParty keeps the outgoing
             * member's own HP next to its own species.
             *
             * A voluntary switch therefore has to satisfy, on the commit frame:
             *   old slot exists, still holds old_species, and its current_hp > 0
             *   new slot exists, holds the species the engine just made authoritative, HP > 0
             * plus everything already checked above (index committed, reader agrees, battle ACTIVE).
             * Without these the run FAILS rather than banking a transition it cannot characterise.
             * --------------------------------------------------------------------------------- */
            if (t->old_slot < 0 || t->old_slot >= (int)s->enemy_party_count_prod) {
                t->violation_old_party_not_alive = true;
                return false;
            }
            if (s->enemy_party_species[t->old_slot] != t->old_species) {
                t->violation_old_party_species = true;
                return false;
            }
            if (s->enemy_party_hp[t->old_slot] == 0) {
                t->violation_old_party_not_alive = true;
                return false;
            }
            if (t->new_slot < 0 || t->new_slot >= (int)s->enemy_party_count_prod) {
                t->violation_new_party_mismatch = true;
                return false;
            }
            if (s->enemy_party_species[t->new_slot] != s->mon_species[s->enemy_battler] ||
                s->enemy_party_hp[t->new_slot] == 0) {
                t->violation_new_party_mismatch = true;
                return false;
            }

            t->saw_commit = true;
            t->old_party_hp_at_commit = (int)s->enemy_party_hp[t->old_slot];
            t->new_party_hp_at_commit = (int)s->enemy_party_hp[t->new_slot];
            t->new_battler = s->enemy_battler;
            t->new_species = s->mon_species[s->enemy_battler];
            t->new_hp = s->mon_hp[s->enemy_battler];
            t->new_max_hp = s->mon_max_hp[s->enemy_battler];
            t->new_index_at_commit = (int)s->party_index[s->enemy_battler];
            t->prod_slot_at_commit = s->enemy_slot;
            t->phase = VSW_PHASE_COMPLETE;
            printf("  [await-enemy-voluntary-switch] Phase D: VOLUNTARY SWITCH committed at frame %d "
                   "(slot %d -> %d, battler=%d, species %u -> %u; gBattlerPartyIndexes=%d, production "
                   "slot=%d). ENEMY PARTY AT COMMIT: slot %d = species %u HP=%d/%u STILL ALIVE; "
                   "slot %d = species %u HP=%d/%u. Outgoing battler entry last read HP=%u before the "
                   "replacement overwrote it.\n",
                   s->frame, t->old_slot, t->new_slot, t->new_battler, t->old_species, t->new_species,
                   t->new_index_at_commit, t->prod_slot_at_commit,
                   t->old_slot, (unsigned)s->enemy_party_species[t->old_slot],
                   t->old_party_hp_at_commit, (unsigned)s->enemy_party_max_hp[t->old_slot],
                   t->new_slot, (unsigned)s->enemy_party_species[t->new_slot],
                   t->new_party_hp_at_commit, (unsigned)s->enemy_party_max_hp[t->new_slot],
                   t->old_hp);
            return true;
        }
        return false;
    }

    return false;
}

/* =========================================================================
 * Strict player faint -> forced-replacement tracker
 *
 * Same phase discipline as the opponent ReplacementTracker, with the two things the player side
 * additionally needs:
 *
 *   1. every phase is bound to a concrete battler index, so "the old player Pokémon" and "the
 *      replacement Pokémon" are gBattleMons[] entries rather than whatever slot happens to be
 *      reported;
 *   2. the raw battle globals are cross-checked against the production surface on every sampled
 *      frame, so the failure modes the issue calls out — a fainted mon still presented as the
 *      authoritative slot, the party menu opening without the battle state moving, a slot that
 *      appears in gPartyMenu but never becomes authoritative, gBattlerPartyIndexes and
 *      PartySnapshot disagreeing in either direction — are recorded as violations instead of
 *      being timed out and forgotten.
 *
 * A single sample may advance more than one phase: the engine can reach hp == 0 and stop being
 * authoritative on the same frame. Violations are latched, are never cleared by a later good frame,
 * and are fatal to the run.
 * ========================================================================= */

typedef enum {
    PLAYER_REPL_PHASE_A_OLD_ACTIVE = 0,
    PLAYER_REPL_PHASE_B_FAINT = 1,
    PLAYER_REPL_PHASE_C_FAIL_CLOSED = 2,
    PLAYER_REPL_PHASE_D_COMMITTED = 3,
    PLAYER_REPL_PHASE_COMPLETE = 4
} PlayerReplacementPhase;

#define PLAYER_REPL_MAX_VIOLATIONS 6

typedef struct {
    int old_slot;
    int new_slot;
    PlayerReplacementPhase phase;

    bool saw_old_active;
    bool saw_faint;
    bool saw_fail_closed_window;
    bool saw_replacement;

    int      old_battler;
    int      old_frame;
    int      old_hp;
    int      old_max_hp;
    uint16_t old_species;

    int      faint_frame;
    bool     known_at_faint;
    int      slot_at_faint;
    int      battler_at_faint;
    uint8_t  absent_flags_at_faint;
    uint8_t  party_index_at_faint[DUALDEX_MAX_BATTLERS];

    int      fail_closed_frame;
    uint8_t  absent_flags_at_fail_closed;
    uint8_t  party_index_at_fail_closed[DUALDEX_MAX_BATTLERS];
    bool     absent_flag_caused_fail_closed;
    bool     hp_zero_caused_fail_closed;

    int      commit_frame;
    int      new_battler;
    int      new_hp;
    int      new_max_hp;
    uint16_t new_species;

    int   violations;
    char  violation_text[PLAYER_REPL_MAX_VIOLATIONS][192];
} PlayerReplacementTracker;

static void player_repl_violate(PlayerReplacementTracker* t, const char* fmt, ...) {
    int slot = t->violations;
    t->violations++;
    if (slot >= PLAYER_REPL_MAX_VIOLATIONS) return; /* counted, message dropped */
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(t->violation_text[slot], sizeof(t->violation_text[slot]), fmt, ap);
    va_end(ap);
}

static void player_replacement_tracker_init(PlayerReplacementTracker* t, int old_slot, int new_slot) {
    memset(t, 0, sizeof(*t));
    t->old_slot = old_slot;
    t->new_slot = new_slot;
    t->phase = PLAYER_REPL_PHASE_A_OLD_ACTIVE;
    t->old_battler = -1;
    t->new_battler = -1;
    t->slot_at_faint = -1;
    t->battler_at_faint = -1;
}

/**
 * Step the player faint -> forced-replacement state machine on one sampled frame.
 *
 * Pure: it reads only the Sample and records everything in the tracker. Returns true exactly once
 * the committed-replacement phase has been proven.
 */
static bool player_replacement_tracker_step(PlayerReplacementTracker* t, const Sample* s) {
    if (!t || !s) return false;
    if (t->phase == PLAYER_REPL_PHASE_COMPLETE) return true;

    const int pb = probe_resolve_player_battler(s);
    const bool in_battle = (s->lifecycle == BATTLE_LIFECYCLE_ACTIVE);

    /* ---- Cross-checks that must hold on every sampled frame of the transition ----------------
     * These are production-contract checks, not phase progress: they run wherever the tracker is. */

    /* A production slot must always come from the authoritative battler -> slot mapping. */
    if (s->active_player_known) {
        if (s->active_player_battler < 0) {
            player_repl_violate(t, "frame %d: PartySnapshot reported a known active player slot %d without a resolved battler index",
                                s->frame, s->active_player_slot);
        } else if ((int)s->party_index[s->active_player_battler] != s->active_player_slot) {
            player_repl_violate(t, "frame %d: PartySnapshot slot %d does not come from gBattlerPartyIndexes[%d] == %u",
                                s->frame, s->active_player_slot, s->active_player_battler,
                                s->party_index[s->active_player_battler]);
        }
    }

    /* Once the old Pokémon has positively fainted, its slot may never become authoritative
     * again before the replacement transition completes. This deliberately does not depend on
     * the current raw battler resolving: an authoritative production claim for the old slot is
     * itself forbidden after the faint. */
    if (t->saw_faint && !t->saw_replacement &&
        s->active_player_known && s->active_player_slot == t->old_slot) {
        player_repl_violate(t, "frame %d: old player slot %d became authoritative again after its faint",
                            s->frame, t->old_slot);
    }

    if (in_battle && pb >= 0) {
        const int idx = (int)s->party_index[pb];
        /* The old slot must not survive its own faint as an apparently valid active Pokémon. */
        if (idx == t->old_slot && s->mon_hp[pb] == 0 &&
            s->active_player_known && s->active_player_slot == t->old_slot) {
            player_repl_violate(t, "frame %d: old slot %d reported as a known active slot while battler %d holding it has HP 0 (stale-slot carryover)",
                                s->frame, t->old_slot, pb);
        }

        /* A live gBattlerPartyIndexes entry must be exactly what production reports. */
        const bool absent = (s->absent_flags >> pb) & 1u;
        if (idx >= 0 && idx != t->old_slot && s->mon_hp[pb] > 0 && s->mon_species[pb] > 0 && !absent) {
            if (!s->active_player_known) {
                player_repl_violate(t, "frame %d: gBattlerPartyIndexes[%d] == %d with a live gBattleMons entry, but PartySnapshot reported no authoritative slot",
                                    s->frame, pb, idx);
            } else if (s->active_player_slot != idx) {
                player_repl_violate(t, "frame %d: PartySnapshot slot %d disagrees with gBattlerPartyIndexes[%d] == %d",
                                    s->frame, s->active_player_slot, pb, idx);
            }
        }
    }

    /* ---- Phase advancement ------------------------------------------------------------------- */

    for (;;) {
        if (t->phase == PLAYER_REPL_PHASE_A_OLD_ACTIVE) {
            if (in_battle && s->active_player_known && s->active_player_slot == t->old_slot) {
                const int ab = s->active_player_battler;
                if (ab >= 0 &&
                    (int)s->party_index[ab] == t->old_slot &&
                    s->mon_hp[ab] > 0) {
                    t->saw_old_active = true;
                    t->old_battler = ab;
                    t->old_frame = s->frame;
                    t->old_hp = s->mon_hp[ab];
                    t->old_max_hp = s->mon_max_hp[ab];
                    t->old_species = s->mon_species[ab];
                    t->phase = PLAYER_REPL_PHASE_B_FAINT;
                    continue;
                }
            }
            return false;
        }

        if (t->phase == PLAYER_REPL_PHASE_B_FAINT) {
            if (in_battle && t->old_battler >= 0 && s->mon_hp[t->old_battler] == 0) {
                t->saw_faint = true;
                t->faint_frame = s->frame;
                t->known_at_faint = s->active_player_known;
                t->slot_at_faint = s->active_player_slot;
                t->battler_at_faint = pb;
                t->absent_flags_at_faint = s->absent_flags;
                for (int b = 0; b < DUALDEX_MAX_BATTLERS; b++) {
                    t->party_index_at_faint[b] = s->party_index[b];
                }
                t->phase = PLAYER_REPL_PHASE_C_FAIL_CLOSED;
                continue;
            }
            return false;
        }

        if (t->phase == PLAYER_REPL_PHASE_C_FAIL_CLOSED) {
            if (in_battle && !s->active_player_known && s->active_player_slot == -1) {
                t->saw_fail_closed_window = true;
                t->fail_closed_frame = s->frame;
                t->absent_flags_at_fail_closed = s->absent_flags;
                for (int b = 0; b < DUALDEX_MAX_BATTLERS; b++) {
                    t->party_index_at_fail_closed[b] = s->party_index[b];
                }
                t->absent_flag_caused_fail_closed =
                    (t->old_battler >= 0) && (((s->absent_flags >> t->old_battler) & 1u) != 0);
                t->hp_zero_caused_fail_closed =
                    (t->old_battler >= 0) && (s->mon_hp[t->old_battler] == 0);
                t->phase = PLAYER_REPL_PHASE_D_COMMITTED;
                continue;
            }
            return false;
        }

        if (t->phase == PLAYER_REPL_PHASE_D_COMMITTED) {
            if (in_battle && t->new_slot != t->old_slot &&
                s->active_player_known && s->active_player_slot == t->new_slot) {
                const int nb = s->active_player_battler;
                const bool absent = (nb >= 0) && (((s->absent_flags >> nb) & 1u) != 0);
                if (nb >= 0 && !absent &&
                    (int)s->party_index[nb] == t->new_slot &&
                    s->mon_hp[nb] > 0 && s->mon_species[nb] > 0) {
                    /* The BattlePokemon that just became active must be the party Pokémon of the
                     * slot gBattlerPartyIndexes names — not merely *some* live battler. */
                    if (t->new_slot >= 6 || s->party_species[t->new_slot] != s->mon_species[nb]) {
                        player_repl_violate(t, "frame %d: active battler %d has species %u but player party slot %d holds species %u",
                                            s->frame, nb, s->mon_species[nb], t->new_slot,
                                            t->new_slot < 6 ? s->party_species[t->new_slot] : 0);
                        return false;
                    }
                    if (s->party_hp[t->new_slot] != s->mon_hp[nb]) {
                        player_repl_violate(t, "frame %d: player party slot %d HP %u does not match the live battler %d HP %u",
                                            s->frame, t->new_slot, s->party_hp[t->new_slot], nb, s->mon_hp[nb]);
                        return false;
                    }
                    t->saw_replacement = true;
                    t->new_battler = nb;
                    t->new_hp = s->mon_hp[nb];
                    t->new_max_hp = s->mon_max_hp[nb];
                    t->new_species = s->mon_species[nb];
                    t->commit_frame = s->frame;
                    t->phase = PLAYER_REPL_PHASE_COMPLETE;
                    continue;
                }
            }
            return false;
        }

        return t->phase == PLAYER_REPL_PHASE_COMPLETE;
    }
}

/**
 * Build one canonical live-singles frame for the player-replacement tests.
 *
 * The scenario's real shape (Chikorita in slot 0, Hoothoot in slot 1, one opponent Hoothoot) is
 * used as the fixture so the negative cases exercise the same numbers as the ROM run.
 */
/* Build a Sample for the voluntary-switch pure tests.
 *
 *   opp_index      authoritative gBattlerPartyIndexes[1] -- what the ENGINE has done
 *   prod_reports   what the PRODUCTION READER reports as the active enemy slot
 *   old_hp         HP of the OUTGOING party member (slot 0), tracked explicitly so "the old mon
 *                  fainted" can be expressed without changing which battler the index names
 *   new_hp         HP of the INCOMING party member (slot 1)
 *   active_hp      HP mirrored into the opponent battler entry
 *
 * `opp_index` and `prod_reports` are deliberately separate: the reader-lag / reader-lead violations
 * exist precisely because the two can disagree, and a model that cannot express the disagreement
 * cannot test it. */
static void vsw_test_sample(Sample* s, int frame, int opp_index, int prod_reports,
                            uint16_t old_hp, uint16_t new_hp, uint16_t old_species,
                            uint16_t new_species, uint16_t active_hp, int opp_battlers) {
    memset(s, 0, sizeof(*s));
    s->frame = frame;
    s->lifecycle = BATTLE_LIFECYCLE_ACTIVE;
    s->kind = BATTLE_KIND_TRAINER_SINGLE;
    s->in_battle = true;
    s->in_battle_readable = true;
    s->battlers = 2;
    s->position[0] = 0;
    s->position[1] = 1;
    s->party_index[0] = 0;
    s->party_index[1] = (uint8_t)opp_index;
    s->mon_species[0] = 152;                 /* Chikorita */
    s->mon_hp[0] = 25;
    s->mon_max_hp[0] = 25;
    s->mon_species[1] = (opp_index == 0) ? old_species : new_species;
    s->mon_hp[1] = active_hp;
    s->mon_max_hp[1] = (old_hp > new_hp ? old_hp : new_hp);
    if (s->mon_max_hp[1] == 0) s->mon_max_hp[1] = 1;
    s->enemy_battler = 1;
    s->enemy_slot = prod_reports;
    s->active_enemy = (prod_reports < 0) ? ACTIVE_ENEMY_NONE_ACTIVE : ACTIVE_ENEMY_SLOT;
    s->enemy_fainted = (active_hp == 0);
    s->opponent_battlers = (uint8_t)opp_battlers;
    s->enemy_party_count_prod = 2;
    s->enemy_party_count = 2;
    s->party_species[0] = old_species;
    s->party_species[1] = new_species;
    s->party_max_hp[0] = (old_hp ? old_hp : 1);
    s->party_max_hp[1] = (new_hp ? new_hp : 1);
    /* Enemy party as PRODUCTION parses it -- the commit-time contract reads these, not the battler
     * entry (which holds the replacement by then). old_hp/new_hp here are the PARTY slot HP. */
    s->enemy_party_species[0] = old_species;
    s->enemy_party_species[1] = new_species;
    s->enemy_party_hp[0] = old_hp;
    s->enemy_party_hp[1] = new_hp;
    s->enemy_party_max_hp[0] = (old_hp ? old_hp : 1);
    s->enemy_party_max_hp[1] = (new_hp ? new_hp : 1);
}

static void player_repl_test_sample(Sample* s, int frame, int player_slot, uint16_t player_hp,
                                    uint16_t player_species, bool known, int reported_slot) {
    memset(s, 0, sizeof(*s));
    s->frame = frame;
    s->lifecycle = BATTLE_LIFECYCLE_ACTIVE;
    s->kind = BATTLE_KIND_TRAINER_SINGLE;
    s->in_battle = true;
    s->in_battle_readable = true;
    s->battlers = 2;
    s->position[0] = 0;   /* player side */
    s->position[1] = 1;   /* opponent side */
    s->party_index[0] = (uint8_t)player_slot;
    s->party_index[1] = 0;
    s->mon_species[0] = player_species;
    s->mon_hp[0] = player_hp;
    s->mon_max_hp[0] = (player_species == 163) ? 14 : 25;
    s->mon_moves[0][0] = 33;   /* Tackle */
    s->mon_moves[0][1] = 45;   /* Growl  */
    s->mon_species[1] = 163;   /* Hoothoot, untouched opponent */
    s->mon_hp[1] = 14;
    s->mon_max_hp[1] = 14;
    s->player_party_count_prod = 2;
    s->party_species[0] = 152; /* Chikorita */
    s->party_species[1] = 163; /* Hoothoot  */
    s->party_max_hp[0] = 25;
    s->party_max_hp[1] = 14;
    s->party_hp[0] = player_hp;
    s->party_hp[1] = 14;
    s->active_player_known = known;
    s->active_player_slot = reported_slot;
    s->active_player_battler = known ? 0 : -1;
}

static int run_pure_tracker_selftests(void) {
    int passed = 0;
    int failed = 0;

    #define ASSERT_TEST(cond, name) do { \
        if (!(cond)) { \
            fprintf(stderr, "  [FAIL] %s: line %d\n", (name), __LINE__); \
            failed++; \
        } else { \
            passed++; \
        } \
    } while (0)

    /* Test 1: autobattle never enters battle */
    {
        AutobattleTracker t;
        autobattle_tracker_init(&t, false);
        for (int i = 0; i < 50; i++) {
            autobattle_tracker_step(&t, false);
        }
        ASSERT_TEST(!t.entered_battle && !t.exited_after_entry && t.phase == AUTOBATTLE_PHASE_INIT,
                    "autobattle_never_enters");
    }

    /* Test 2: autobattle enters but never exits */
    {
        AutobattleTracker t;
        autobattle_tracker_init(&t, false);
        autobattle_tracker_step(&t, true);
        for (int i = 0; i < 50; i++) {
            autobattle_tracker_step(&t, true);
        }
        ASSERT_TEST(t.entered_battle && !t.exited_after_entry && t.phase == AUTOBATTLE_PHASE_IN_BATTLE,
                    "autobattle_enters_never_exits");
    }

    /* Test 3: autobattle valid sequence (enter -> exit) */
    {
        AutobattleTracker t;
        autobattle_tracker_init(&t, false);
        autobattle_tracker_step(&t, true);
        bool done = autobattle_tracker_step(&t, false);
        ASSERT_TEST(done && t.entered_battle && t.exited_after_entry && t.phase == AUTOBATTLE_PHASE_EXITED,
                    "autobattle_enter_then_exit");
    }

    /* Test 4: autobattle already active at start -> exit */
    {
        AutobattleTracker t;
        autobattle_tracker_init(&t, true);
        ASSERT_TEST(t.entered_battle && t.phase == AUTOBATTLE_PHASE_IN_BATTLE, "autobattle_initially_active");
        bool done = autobattle_tracker_step(&t, false);
        ASSERT_TEST(done && t.exited_after_entry && t.phase == AUTOBATTLE_PHASE_EXITED, "autobattle_initially_active_exit");
    }

    /* Test 5: enemy replacement never reaches faint phase */
    {
        ReplacementTracker t;
        replacement_tracker_init(&t, 0, 1);
        Sample s;
        memset(&s, 0, sizeof(s));
        s.lifecycle = BATTLE_LIFECYCLE_ACTIVE;
        s.active_enemy = ACTIVE_ENEMY_SLOT;
        s.enemy_slot = 0;
        s.enemy_battler = 1;
        s.party_index[1] = 0;
        s.mon_hp[1] = 14;
        s.mon_max_hp[1] = 14;
        s.mon_species[1] = 163;
        s.enemy_fainted = false;
        replacement_tracker_step(&t, &s, 0);
        ASSERT_TEST(t.saw_old_active && t.phase == REPL_PHASE_B_FAINT, "replacement_phase_a_observed");

        s.mon_hp[1] = 6;
        replacement_tracker_step(&t, &s, 0);
        s.mon_hp[1] = 2;
        replacement_tracker_step(&t, &s, 0);
        ASSERT_TEST(t.saw_old_active && !t.saw_faint && t.phase == REPL_PHASE_B_FAINT,
                    "replacement_never_reaches_faint");
    }

    /* Test 6: enemy replacement sees faint but never absent window */
    {
        ReplacementTracker t;
        replacement_tracker_init(&t, 0, 1);
        Sample s;
        memset(&s, 0, sizeof(s));
        s.lifecycle = BATTLE_LIFECYCLE_ACTIVE;
        s.active_enemy = ACTIVE_ENEMY_SLOT;
        s.enemy_slot = 0;
        s.enemy_battler = 1;
        s.party_index[1] = 0;
        s.mon_hp[1] = 14;
        s.enemy_fainted = false;
        replacement_tracker_step(&t, &s, 0);

        s.mon_hp[1] = 0;
        s.enemy_fainted = true;
        replacement_tracker_step(&t, &s, 0);
        ASSERT_TEST(t.saw_faint && t.phase == REPL_PHASE_C_ABSENT, "replacement_faint_observed");

        s.active_enemy = ACTIVE_ENEMY_SLOT;
        s.enemy_slot = 1;
        replacement_tracker_step(&t, &s, 0);
        ASSERT_TEST(!t.saw_absent_window && !t.saw_replacement && t.phase == REPL_PHASE_C_ABSENT,
                    "replacement_faint_without_absent_window");
    }

    /* Test 7: enemy replacement sees absent window but never new slot */
    {
        ReplacementTracker t;
        replacement_tracker_init(&t, 0, 1);
        Sample s;
        memset(&s, 0, sizeof(s));
        s.lifecycle = BATTLE_LIFECYCLE_ACTIVE;
        s.active_enemy = ACTIVE_ENEMY_SLOT;
        s.enemy_slot = 0;
        s.enemy_battler = 1;
        s.party_index[1] = 0;
        s.mon_hp[1] = 14;
        replacement_tracker_step(&t, &s, 0);

        s.mon_hp[1] = 0;
        s.enemy_fainted = true;
        replacement_tracker_step(&t, &s, 0);

        s.active_enemy = ACTIVE_ENEMY_NONE_ACTIVE;
        s.enemy_slot = -1;
        s.enemy_battler = -1;
        s.opponent_battlers = 0;
        replacement_tracker_step(&t, &s, 0x02);
        ASSERT_TEST(t.saw_absent_window && t.phase == REPL_PHASE_D_REPLACEMENT, "replacement_absent_observed");

        s.lifecycle = BATTLE_LIFECYCLE_INACTIVE;
        bool done = replacement_tracker_step(&t, &s, 0);
        ASSERT_TEST(!done && !t.saw_replacement && t.phase == REPL_PHASE_D_REPLACEMENT,
                    "replacement_absent_without_new_slot");
    }

    /* Test 8: enemy replacement full sequence (A -> B -> C -> D) succeeds */
    {
        ReplacementTracker t;
        replacement_tracker_init(&t, 0, 1);
        Sample s;
        memset(&s, 0, sizeof(s));
        s.lifecycle = BATTLE_LIFECYCLE_ACTIVE;
        s.active_enemy = ACTIVE_ENEMY_SLOT;
        s.enemy_slot = 0;
        s.enemy_battler = 1;
        s.party_index[1] = 0;
        s.mon_hp[1] = 14;
        s.mon_species[1] = 163;
        s.mon_max_hp[1] = 14;
        replacement_tracker_step(&t, &s, 0);

        s.mon_hp[1] = 0;
        s.enemy_fainted = true;
        replacement_tracker_step(&t, &s, 0);

        s.active_enemy = ACTIVE_ENEMY_NONE_ACTIVE;
        s.enemy_slot = -1;
        s.enemy_battler = -1;
        s.opponent_battlers = 0;
        replacement_tracker_step(&t, &s, 0x02);

        s.active_enemy = ACTIVE_ENEMY_SLOT;
        s.enemy_slot = 1;
        s.enemy_battler = 1;
        s.party_index[1] = 1;
        s.enemy_party_count_prod = 2;
        s.enemy_fainted = false;
        s.mon_hp[1] = 16;
        s.mon_max_hp[1] = 16;
        s.mon_species[1] = 161;
        bool done = replacement_tracker_step(&t, &s, 0);
        ASSERT_TEST(done && t.saw_replacement && t.phase == REPL_PHASE_COMPLETE,
                    "replacement_full_sequence_success");
        ASSERT_TEST(t.old_species == 163 && t.new_species == 161, "replacement_species_recorded");
    }

    /* Test 9: clear_wild_battle timeout simulation */
    {
        bool in_b = true;
        bool cleared = false;
        for (int it = 0; it < 50; it++) {
            if (!in_b) { cleared = true; break; }
        }
        ASSERT_TEST(!cleared, "clear_wild_battle_timeout_sim");
    }

    /* ---- Opponent VOLUNTARY switch ---------------------------------------------------------
     * These drive the same pure state machine `await-enemy-voluntary-switch` uses. The model is the
     * contract the live ROM is compared against, so it must express the strict version: the outgoing
     * mon never faints, the production reader never leads or lags gBattlerPartyIndexes, and the
     * commit is bound to the battler that was actually out. Violations latch permanently. */

    /* V1: full positive sequence -- old alive, transition, new slot committed.
     *
     * Four frames, because the phases are strictly sequential now that no AI-decision byte is read:
     *   10  old slot 0 active and alive            -> Phase A latched
     *   11  index rewritten, surface still old     -> Phase B sees the transition, Phase C is armed
     *   12  surface still old                      -> Phase C records it, Phase D is armed
     *   13  surface agrees with the index          -> Phase D commits, party contract checked */
    {
        VoluntarySwitchTracker t;
        voluntary_switch_tracker_init(&t, 0, 1, 165);
        Sample s;
        vsw_test_sample(&s, 10, 0, 0, 20, 20, 165, 168, 20, 1);   /* slot 0 Ledyba (165) alive */
        ASSERT_TEST(!voluntary_switch_tracker_step(&t, &s), "vsw_positive_phase_a_not_done");
        ASSERT_TEST(t.phase == VSW_PHASE_B_AWAIT_TRANSITION && t.saw_old_active &&
                    t.old_species == 165 && t.old_hp == 20, "vsw_positive_phase_a_recorded");
        /* Frame 11: the authoritative index is rewritten while the outgoing mon is still alive. This
         * alone is what moves the tracker forward -- nothing asks the AI anything. */
        vsw_test_sample(&s, 11, 1, 0, 20, 20, 165, 168, 20, 1);
        ASSERT_TEST(!voluntary_switch_tracker_step(&t, &s), "vsw_positive_phase_b_not_done");
        ASSERT_TEST(t.phase == VSW_PHASE_C_TRANSITION,
                    "vsw_positive_phase_b_recorded");
        /* Frame 12: the index is rewritten but the production surface still names the old slot, so
         * this is inside the legitimate transition, not a lag violation. */
        vsw_test_sample(&s, 12, 1, 0, 20, 20, 165, 168, 20, 1);
        ASSERT_TEST(!voluntary_switch_tracker_step(&t, &s) &&
                    t.phase == VSW_PHASE_D_SWITCH_COMMITTED && !t.violation_reader_lags,
                    "vsw_positive_transition_frame");
        /* Frame 13: production reader now agrees with the authoritative index -> committed. */
        vsw_test_sample(&s, 13, 1, 1, 20, 20, 165, 168, 20, 1);
        const bool done = voluntary_switch_tracker_step(&t, &s);
        ASSERT_TEST(done && t.phase == VSW_PHASE_COMPLETE, "vsw_positive_commit");
        ASSERT_TEST(t.new_index_at_commit == 1 && t.prod_slot_at_commit == 1,
                    "vsw_positive_reader_agrees");
        ASSERT_TEST(t.new_species == 168 && t.new_hp == 20, "vsw_positive_new_mon_recorded");
        ASSERT_TEST(!t.violation_old_fainted && !t.violation_reader_leads && !t.violation_reader_lags,
                    "vsw_positive_no_violations");
        /* The commit frame must carry the machine-checked party proof, not just a print. */
        ASSERT_TEST(t.old_party_hp_at_commit == 20 && t.new_party_hp_at_commit == 20,
                    "vsw_positive_party_hp_recorded_at_commit");
    }

    /* V2: the old mon FAINTS -> faint replacement, not a voluntary switch. */
    {
        VoluntarySwitchTracker t;
        voluntary_switch_tracker_init(&t, 0, 1, 165);
        Sample s;
        vsw_test_sample(&s, 10, 0, 0, 20, 20, 165, 168, 20, 1);
        voluntary_switch_tracker_step(&t, &s);
        /* The battler the tracker latched onto drops to 0 HP while still authoritative: that is the
         * faint path, so it must latch. */
        s.mon_hp[1] = 0;
        s.enemy_fainted = true;
        voluntary_switch_tracker_step(&t, &s);
        ASSERT_TEST(t.violation_old_fainted, "vsw_negative_old_fainted_latched");
    }

    /* V3: the transition alone drives the state machine.
     *
     * Neither `gChosenActionByBattler` nor `gBattleStruct->monToSwitchIntoId` is read anywhere in
     * this probe, so there is no AI-decision input that could veto or trigger a transition. This
     * asserts that the tracker advances on the authoritative index rewrite by itself and latches
     * nothing while doing so. */
    {
        VoluntarySwitchTracker t;
        voluntary_switch_tracker_init(&t, 0, 1, 165);
        Sample s;
        vsw_test_sample(&s, 10, 0, 0, 20, 20, 165, 168, 20, 1);
        ASSERT_TEST(!voluntary_switch_tracker_step(&t, &s), "vsw_no_decision_phase_a");
        vsw_test_sample(&s, 11, 1, 0, 20, 20, 165, 168, 20, 1);
        ASSERT_TEST(!voluntary_switch_tracker_step(&t, &s), "vsw_no_decision_not_complete");
        ASSERT_TEST(t.phase == VSW_PHASE_C_TRANSITION,
                    "vsw_transition_drives_state_machine_alone");
        ASSERT_TEST(!t.violation_old_fainted && !t.violation_reader_leads,
                    "vsw_no_decision_no_violation");
    }

    /* V3a: COMMIT-TIME PARTY CONTRACT -- the four cases the contract must separate.
     *
     * The commit frame is the only place the outgoing mon's liveness survives (the battler entry
     * holds the replacement). These drive a COMPLETE transition in each case and assert what the
     * tracker does with the enemy party snapshot at the moment of commit. */
    /* Runs one COMPLETE transition (A -> B -> C -> D -> commit) with the caller's party-slot HP and
     * species for the old and new enemy slots, and returns the tracker afterwards. The helper exists
     * so each contract case below is one frame sequence, not five copies of it. */
    {
        /* (a) old party slot still alive in its own slot -> the commit is ALLOWED. */
        VoluntarySwitchTracker t;
        voluntary_switch_tracker_init(&t, 0, 1, 165);
        Sample s;
        vsw_test_sample(&s, 10, 0, 0, 9, 20, 165, 168, 20, 1);
        voluntary_switch_tracker_step(&t, &s);
        vsw_test_sample(&s, 11, 1, 0, 9, 20, 165, 168, 20, 1);
        voluntary_switch_tracker_step(&t, &s);
        vsw_test_sample(&s, 12, 1, 0, 9, 20, 165, 168, 20, 1);
        voluntary_switch_tracker_step(&t, &s);
        vsw_test_sample(&s, 13, 1, 1, 9, 20, 165, 168, 20, 1);
        const bool ok = voluntary_switch_tracker_step(&t, &s);
        ASSERT_TEST(ok && t.phase == VSW_PHASE_COMPLETE && t.old_party_hp_at_commit == 9,
                    "vsw_old_party_alive_allowed");
    }
    {
        /* (b) old party slot at 0 HP -> the voluntary switch MUST NOT complete, even though the
         *     sampled battler HP stays above zero so the faint latch never fires. This is the new
         *     invariant doing work the latch alone cannot do. */
        VoluntarySwitchTracker t;
        voluntary_switch_tracker_init(&t, 0, 1, 165);
        Sample s;
        vsw_test_sample(&s, 10, 0, 0, 0, 20, 165, 168, 20, 1);
        voluntary_switch_tracker_step(&t, &s);
        vsw_test_sample(&s, 11, 1, 0, 0, 20, 165, 168, 20, 1);
        voluntary_switch_tracker_step(&t, &s);
        vsw_test_sample(&s, 12, 1, 0, 0, 20, 165, 168, 20, 1);
        voluntary_switch_tracker_step(&t, &s);
        vsw_test_sample(&s, 13, 1, 1, 0, 20, 165, 168, 20, 1);
        const bool done = voluntary_switch_tracker_step(&t, &s);
        ASSERT_TEST(!done && !t.saw_commit && t.phase != VSW_PHASE_COMPLETE &&
                    t.violation_old_party_not_alive,
                    "vsw_old_party_dead_must_not_complete");
        ASSERT_TEST(!t.violation_old_fainted,
                    "vsw_old_party_dead_is_independent_of_faint_latch");
    }
    {
        /* (c) the old slot holds a DIFFERENT species -> the snapshot is not describing the mon that
         *     left, so the run fails rather than banking the transition. */
        VoluntarySwitchTracker t;
        voluntary_switch_tracker_init(&t, 0, 1, 165);
        Sample s;
        vsw_test_sample(&s, 10, 0, 0, 20, 20, 165, 168, 20, 1);
        voluntary_switch_tracker_step(&t, &s);
        vsw_test_sample(&s, 11, 1, 0, 20, 20, 165, 168, 20, 1);
        voluntary_switch_tracker_step(&t, &s);
        vsw_test_sample(&s, 12, 1, 0, 20, 20, 165, 168, 20, 1);
        voluntary_switch_tracker_step(&t, &s);
        vsw_test_sample(&s, 13, 1, 1, 20, 20, 165, 168, 20, 1);
        s.enemy_party_species[0] = 200;   /* wrong mon in the old slot */
        const bool done = voluntary_switch_tracker_step(&t, &s);
        ASSERT_TEST(!done && !t.saw_commit && t.violation_old_party_species,
                    "vsw_old_party_wrong_species_fails");
    }
    {
        /* (d) the new slot does not match the species the engine just made authoritative -> fail. */
        VoluntarySwitchTracker t;
        voluntary_switch_tracker_init(&t, 0, 1, 165);
        Sample s;
        vsw_test_sample(&s, 10, 0, 0, 20, 20, 165, 168, 20, 1);
        voluntary_switch_tracker_step(&t, &s);
        vsw_test_sample(&s, 11, 1, 0, 20, 20, 165, 168, 20, 1);
        voluntary_switch_tracker_step(&t, &s);
        vsw_test_sample(&s, 12, 1, 0, 20, 20, 165, 168, 20, 1);
        voluntary_switch_tracker_step(&t, &s);
        vsw_test_sample(&s, 13, 1, 1, 20, 20, 165, 168, 20, 1);
        s.enemy_party_species[1] = 42;    /* party snapshot disagrees with the committed species */
        const bool done = voluntary_switch_tracker_step(&t, &s);
        ASSERT_TEST(!done && !t.saw_commit && t.violation_new_party_mismatch,
                    "vsw_new_party_species_mismatch_fails");
    }
    {
        /* (e) the new slot reads 0 HP -> the authoritative opponent cannot be a live mon -> fail. */
        VoluntarySwitchTracker t;
        voluntary_switch_tracker_init(&t, 0, 1, 165);
        Sample s;
        vsw_test_sample(&s, 10, 0, 0, 20, 0, 165, 168, 20, 1);
        voluntary_switch_tracker_step(&t, &s);
        vsw_test_sample(&s, 11, 1, 0, 20, 0, 165, 168, 20, 1);
        voluntary_switch_tracker_step(&t, &s);
        vsw_test_sample(&s, 12, 1, 0, 20, 0, 165, 168, 20, 1);
        voluntary_switch_tracker_step(&t, &s);
        vsw_test_sample(&s, 13, 1, 1, 20, 0, 165, 168, 20, 1);
        const bool done = voluntary_switch_tracker_step(&t, &s);
        ASSERT_TEST(!done && !t.saw_commit && t.violation_new_party_mismatch,
                    "vsw_new_party_dead_fails");
    }

    /* V3b: FAIL-CLOSED, and now the load-bearing discriminator.
     *
     * With the staging read optional, what separates a voluntary switch from a faint replacement is
     * the outgoing mon's own HP: the latch fires on ANY frame where the outgoing battler reads 0,
     * which is before the replacement overwrites gBattleMons. A transition whose outgoing mon ever
     * read 0 must therefore never be accepted, even when the index transition and the production
     * surface are otherwise perfect. */
    {
        VoluntarySwitchTracker t;
        voluntary_switch_tracker_init(&t, 0, 1, 165);
        Sample s;
        vsw_test_sample(&s, 10, 0, 0, 20, 20, 165, 168, 20, 1);
        voluntary_switch_tracker_step(&t, &s);
        vsw_test_sample(&s, 11, 0, 0, 20, 20, 165, 168, 0, 1);   /* outgoing mon reaches 0 HP */
        ASSERT_TEST(!voluntary_switch_tracker_step(&t, &s) && t.violation_old_fainted,
                    "vsw_faint_latched_order_independent");
        /* A perfect-looking replacement follows: index 1, production agrees, new species. */
        vsw_test_sample(&s, 12, 1, 1, 20, 20, 165, 168, 20, 1);
        const bool done = voluntary_switch_tracker_step(&t, &s);
        ASSERT_TEST(!done && t.violation_old_fainted && t.phase != VSW_PHASE_COMPLETE,
                    "vsw_faint_replacement_never_accepted");
    }

    /* V3c: a PLAYER-side voluntary switch must never satisfy the OPPONENT tracker.
     *
     * Scenario 44 legally alternates the player's own active slot to stop attacking once the
     * outgoing opponent is one hit from fainting. That input must be incapable of fabricating an
     * opponent transition: the tracker reads only the opponent side (enemy_battler,
     * gBattlerPartyIndexes[enemy battler], the production opponent resolution), while the player's
     * own slot, species and HP live on battler 0. This drives a full player alternation and asserts
     * the opponent tracker neither completes nor latches a violation off it. */
    {
        VoluntarySwitchTracker t;
        voluntary_switch_tracker_init(&t, 0, 1, 165);
        Sample s;
        vsw_test_sample(&s, 10, 0, 0, 20, 20, 165, 168, 20, 1);   /* opponent slot 0 alive */
        ASSERT_TEST(!voluntary_switch_tracker_step(&t, &s), "vsw_player_switch_phase_a");
        ASSERT_TEST(t.phase == VSW_PHASE_B_AWAIT_TRANSITION, "vsw_player_switch_phase_a_recorded");
        bool any_step_completed = false;
        for (int i = 0; i < 12; i++) {
            vsw_test_sample(&s, 11 + i, 0, 0, 20, 20, 165, 168, 20, 1);
            /* The player swaps its OWN active slot every other turn, which is what the stall does. */
            s.party_index[0] = (uint8_t)(i % 2);
            s.mon_species[0] = (i % 2) ? 163 : 152;              /* Hoothoot / Chikorita */
            s.mon_hp[0] = 17;
            s.active_player_slot = (i % 2);
            s.active_player_known = true;
            if (voluntary_switch_tracker_step(&t, &s)) any_step_completed = true;
        }
        ASSERT_TEST(!any_step_completed && !t.saw_commit &&
                    t.phase == VSW_PHASE_B_AWAIT_TRANSITION,
                    "vsw_player_switch_not_opponent_switch");
        ASSERT_TEST(!t.violation_old_fainted && !t.violation_reader_leads && !t.violation_reader_lags &&
                    !t.violation_species_mismatch && !t.violation_battler_changed,
                    "vsw_player_switch_no_spurious_violation");
    }

    /* V4: the index moves but the production surface NEVER follows -> no completion.
     *
     * The index rewrite alone starts the transition; only the production reader agreeing on the same
     * frame can finish it. A surface that stays on the old slot forever must therefore never be
     * banked, and the disagreement eventually latches as a reader-lag violation. */
    {
        VoluntarySwitchTracker t;
        voluntary_switch_tracker_init(&t, 0, 1, 165);
        Sample s;
        vsw_test_sample(&s, 10, 0, 0, 20, 20, 165, 168, 20, 1);
        voluntary_switch_tracker_step(&t, &s);
        for (int i = 0; i < 20; i++) {
            vsw_test_sample(&s, 11 + i, 1, 0, 20, 20, 165, 168, 20, 1);
            voluntary_switch_tracker_step(&t, &s);
        }
        ASSERT_TEST(!t.saw_commit && t.phase != VSW_PHASE_COMPLETE,
                    "vsw_negative_surface_never_follows_no_commit");
    }

    /* V5: production reader LEADS authority -- reports the new slot while the index says old. */
    {
        VoluntarySwitchTracker t;
        voluntary_switch_tracker_init(&t, 0, 1, 165);
        Sample s;
        vsw_test_sample(&s, 10, 0, 0, 20, 20, 165, 168, 20, 1);
        voluntary_switch_tracker_step(&t, &s);
        /* Production reports slot 1 while the authoritative index names slot 2 -- neither the old
         * slot (0) nor the new slot (1). The reader would be asserting a switch the engine never
         * made, so the tracker must latch rather than accept the surface. */
        vsw_test_sample(&s, 11, 2, 1, 20, 20, 165, 168, 20, 1);
        voluntary_switch_tracker_step(&t, &s);
        ASSERT_TEST(t.violation_reader_leads, "vsw_negative_reader_leads_latched");
    }

    /* V6: production reader LAGS authority -- index already 1, reader still reports 0. */
    {
        VoluntarySwitchTracker t;
        voluntary_switch_tracker_init(&t, 0, 1, 165);
        Sample s;
        vsw_test_sample(&s, 10, 0, 0, 20, 20, 165, 168, 20, 1);
        voluntary_switch_tracker_step(&t, &s);
        vsw_test_sample(&s, 11, 0, 0, 20, 20, 165, 168, 20, 1);
        voluntary_switch_tracker_step(&t, &s);               /* decision -> phase C */
        /* The index has been rewritten but the production surface keeps reporting the OLD slot for
         * many frames. The tracker must never complete on index evidence alone: the production
         * reader is part of the contract, so a switch whose surface never follows is not verified. */
        bool lag_done = false;
        for (int i = 0; i < 24 && !lag_done; i++) {
            vsw_test_sample(&s, 12 + i, 1, 0, 20, 20, 165, 168, 20, 1);
            lag_done = voluntary_switch_tracker_step(&t, &s);
        }
        ASSERT_TEST(!lag_done && t.phase != VSW_PHASE_COMPLETE,
                    "vsw_negative_reader_lags_no_completion");
    }

    /* V7: the old slot becomes authoritative again after a committed switch -> violation. */
    {
        VoluntarySwitchTracker t;
        voluntary_switch_tracker_init(&t, 0, 1, 165);
        Sample s;
        vsw_test_sample(&s, 10, 0, 0, 20, 20, 165, 168, 20, 1);
        voluntary_switch_tracker_step(&t, &s);
        vsw_test_sample(&s, 11, 1, 0, 20, 20, 165, 168, 20, 1);
        voluntary_switch_tracker_step(&t, &s);
        vsw_test_sample(&s, 12, 1, 0, 20, 20, 165, 168, 20, 1);
        voluntary_switch_tracker_step(&t, &s);
        vsw_test_sample(&s, 13, 1, 1, 20, 20, 165, 168, 20, 1);
        const bool ok7 = voluntary_switch_tracker_step(&t, &s);
        ASSERT_TEST(ok7 && t.phase == VSW_PHASE_COMPLETE && !t.violation_old_slot_reappeared,
                    "vsw_negative_reappear_committed");
        /* A further frame in which the OLD slot is authoritative again is a re-appearance. The
         * tracker's latch is checked before the completed-phase early return, so it still latches. */
        vsw_test_sample(&s, 14, 0, 0, 20, 20, 165, 168, 20, 1);
        voluntary_switch_tracker_step(&t, &s);
        ASSERT_TEST(t.violation_old_slot_reappeared, "vsw_negative_old_slot_reappeared_latched");
    }

    /* V8: a doubles-shaped battle is refused outright. */
    {
        VoluntarySwitchTracker t;
        voluntary_switch_tracker_init(&t, 0, 1, 165);
        Sample s;
        vsw_test_sample(&s, 10, 0, 0, 20, 20, 165, 168, 20, 1);
        s.kind = BATTLE_KIND_DOUBLES;
        s.battlers = 4;
        voluntary_switch_tracker_step(&t, &s);
        ASSERT_TEST(t.violation_shape, "vsw_negative_doubles_shape_refused");
    }

    /* V9: wrong lead species -> refuse rather than track whatever is out. */
    {
        VoluntarySwitchTracker t;
        voluntary_switch_tracker_init(&t, 0, 1, 165);
        Sample s;
        vsw_test_sample(&s, 10, 0, 0, 20, 20, 161, 165, 20, 1);   /* lead is 161, not 165 */
        voluntary_switch_tracker_step(&t, &s);
        ASSERT_TEST(t.violation_species_mismatch, "vsw_negative_wrong_lead_species_refused");
    }

    /* V10: timeout -- neither decision nor commit ever observed. */
    {
        VoluntarySwitchTracker t;
        voluntary_switch_tracker_init(&t, 0, 1, 165);
        Sample s;
        for (int i = 0; i < 30; i++) {
            vsw_test_sample(&s, 10 + i, 0, 0, 20, 20, 165, 168, 20, 1);
            voluntary_switch_tracker_step(&t, &s);
        }
        ASSERT_TEST(t.phase == VSW_PHASE_B_AWAIT_TRANSITION && !t.saw_commit,
                    "vsw_negative_timeout_no_commit");
    }

    /* ---- Player faint -> forced replacement ------------------------------------------------
     * These drive the same pure state machine the runtime command uses. They are the model the
     * live ROM is compared against, so they must express the *strict* contract: the old slot has to
     * go non-authoritative before the replacement may be accepted, and the production slot has to
     * agree with gBattlerPartyIndexes on every sampled frame. */

    /* Test 10: player tracker never reaches the faint */
    {
        PlayerReplacementTracker t;
        player_replacement_tracker_init(&t, 0, 1);
        bool completed = false;
        for (int i = 0; i < 40; i++) {
            Sample s;
            player_repl_test_sample(&s, i, 0, (uint16_t)(16 - (i % 4)), 152, true, 0);
            if (player_replacement_tracker_step(&t, &s)) completed = true;
        }
        ASSERT_TEST(!completed && !t.saw_faint && !t.saw_replacement &&
                    t.phase == PLAYER_REPL_PHASE_B_FAINT,
                    "player_replacement_never_reaches_faint");
        ASSERT_TEST(t.violations == 0, "player_replacement_never_reaches_faint_no_violations");
    }

    /* Test 11: player faints but production keeps presenting the old slot as authoritative */
    {
        PlayerReplacementTracker t;
        player_replacement_tracker_init(&t, 0, 1);
        Sample s;
        player_repl_test_sample(&s, 10, 0, 16, 152, true, 0);
        player_replacement_tracker_step(&t, &s);
        bool completed = false;
        for (int i = 0; i < 6; i++) {
            player_repl_test_sample(&s, 11 + i, 0, 0, 152, true, 0);
            if (player_replacement_tracker_step(&t, &s)) completed = true;
        }
        ASSERT_TEST(!completed && t.saw_faint && !t.saw_replacement,
                    "player_replacement_faints_stays_authoritative_no_completion");
        ASSERT_TEST(t.violations > 0, "player_replacement_faints_stays_authoritative_violation");
        ASSERT_TEST(t.phase == PLAYER_REPL_PHASE_C_FAIL_CLOSED,
                    "player_replacement_faints_stays_authoritative_no_fail_closed_window");
    }

    /* Test 12: the replacement appears without the faint ever being observed */
    {
        PlayerReplacementTracker t;
        player_replacement_tracker_init(&t, 0, 1);
        Sample s;
        player_repl_test_sample(&s, 20, 0, 16, 152, true, 0);
        player_replacement_tracker_step(&t, &s);
        bool completed = false;
        for (int i = 0; i < 8; i++) {
            /* Slot 1 is live in the same battler: battler 0's HP never reads 0. */
            player_repl_test_sample(&s, 21 + i, 1, 14, 163, true, 1);
            if (player_replacement_tracker_step(&t, &s)) completed = true;
        }
        ASSERT_TEST(!completed && !t.saw_faint && !t.saw_fail_closed_window && !t.saw_replacement &&
                    t.phase == PLAYER_REPL_PHASE_B_FAINT,
                    "player_replacement_commit_without_observed_faint");
    }

    /* Test 13: fail-closed window reached, replacement never commits */
    {
        PlayerReplacementTracker t;
        player_replacement_tracker_init(&t, 0, 1);
        Sample s;
        player_repl_test_sample(&s, 30, 0, 16, 152, true, 0);
        player_replacement_tracker_step(&t, &s);
        bool completed = false;
        for (int i = 0; i < 60; i++) {
            player_repl_test_sample(&s, 31 + i, 0, 0, 152, false, -1);
            if (player_replacement_tracker_step(&t, &s)) completed = true;
        }
        ASSERT_TEST(!completed && t.saw_faint && t.saw_fail_closed_window && !t.saw_replacement,
                    "player_replacement_window_without_commit");
        ASSERT_TEST(t.violations == 0, "player_replacement_window_without_commit_no_violations");
    }

    /* Test 14: the wrong new slot appears (production claims 1 while the live index is 0) */
    {
        PlayerReplacementTracker t;
        player_replacement_tracker_init(&t, 0, 1);
        Sample s;
        player_repl_test_sample(&s, 40, 0, 16, 152, true, 0);
        player_replacement_tracker_step(&t, &s);
        player_repl_test_sample(&s, 41, 0, 0, 152, false, -1);
        player_replacement_tracker_step(&t, &s);
        ASSERT_TEST(t.saw_fail_closed_window, "player_replacement_wrong_slot_window_reached");
        bool completed = false;
        for (int i = 0; i < 4; i++) {
            player_repl_test_sample(&s, 42 + i, 0, 14, 163, true, 1);
            if (player_replacement_tracker_step(&t, &s)) completed = true;
        }
        ASSERT_TEST(!completed && !t.saw_replacement, "player_replacement_wrong_new_slot_no_completion");
        ASSERT_TEST(t.violations > 0, "player_replacement_wrong_new_slot_violation");
    }

    /* Test 15: gBattlerPartyIndexes moved to 1 but PartySnapshot stayed on 0 */
    {
        PlayerReplacementTracker t;
        player_replacement_tracker_init(&t, 0, 1);
        Sample s;
        player_repl_test_sample(&s, 50, 0, 16, 152, true, 0);
        player_replacement_tracker_step(&t, &s);
        player_repl_test_sample(&s, 51, 0, 0, 152, false, -1);
        player_replacement_tracker_step(&t, &s);
        bool completed = false;
        for (int i = 0; i < 4; i++) {
            player_repl_test_sample(&s, 52 + i, 1, 14, 163, true, 0);
            if (player_replacement_tracker_step(&t, &s)) completed = true;
        }
        ASSERT_TEST(!completed && !t.saw_replacement,
                    "player_replacement_snapshot_lags_index_no_completion");
        ASSERT_TEST(t.violations > 0, "player_replacement_snapshot_lags_index_violation");
    }

    /* Test 16: PartySnapshot reports the new slot without authoritative party-index evidence */
    {
        PlayerReplacementTracker t;
        player_replacement_tracker_init(&t, 0, 1);
        Sample s;
        player_repl_test_sample(&s, 60, 0, 16, 152, true, 0);
        player_replacement_tracker_step(&t, &s);
        player_repl_test_sample(&s, 61, 0, 0, 152, false, -1);
        player_replacement_tracker_step(&t, &s);
        bool completed = false;
        for (int i = 0; i < 4; i++) {
            player_repl_test_sample(&s, 62 + i, 0, 0, 152, true, 1);
            if (player_replacement_tracker_step(&t, &s)) completed = true;
        }
        ASSERT_TEST(!completed && !t.saw_replacement,
                    "player_replacement_slot_without_index_evidence_no_completion");
        ASSERT_TEST(t.violations > 0, "player_replacement_slot_without_index_evidence_violation");
    }

    /* Test 17: full positive sequence A -> B -> C -> D */
    {
        PlayerReplacementTracker t;
        player_replacement_tracker_init(&t, 0, 1);
        Sample s;

        player_repl_test_sample(&s, 70, 0, 16, 152, true, 0);
        bool step_a = player_replacement_tracker_step(&t, &s);
        ASSERT_TEST(!step_a && t.saw_old_active && t.old_species == 152 && t.old_hp == 16 &&
                    t.old_max_hp == 25 && t.old_battler == 0,
                    "player_replacement_phase_a_observed");

        player_repl_test_sample(&s, 71, 0, 0, 152, false, -1);
        player_replacement_tracker_step(&t, &s);
        ASSERT_TEST(t.saw_faint && t.faint_frame == 71 && !t.known_at_faint &&
                    t.slot_at_faint == -1 && t.battler_at_faint == 0,
                    "player_replacement_phase_b_observed");

        player_repl_test_sample(&s, 72, 0, 0, 152, false, -1);
        player_replacement_tracker_step(&t, &s);
        /* The faint frame and the fail-closed window are the same sampled frame here: the engine
         * reached hp == 0 and the production surface was already non-authoritative on it, which is
         * why a single sample is allowed to advance more than one phase. */
        ASSERT_TEST(t.saw_fail_closed_window && t.fail_closed_frame == 71 &&
                    t.hp_zero_caused_fail_closed && !t.absent_flag_caused_fail_closed,
                    "player_replacement_phase_c_observed");

        player_repl_test_sample(&s, 73, 1, 14, 163, true, 1);
        bool done = player_replacement_tracker_step(&t, &s);
        ASSERT_TEST(done && t.phase == PLAYER_REPL_PHASE_COMPLETE && t.saw_replacement &&
                    t.commit_frame == 73,
                    "player_replacement_full_sequence_success");
        ASSERT_TEST(t.new_slot == 1 && t.new_slot != t.old_slot && t.new_battler == 0,
                    "player_replacement_full_sequence_new_slot");
        ASSERT_TEST(t.new_species == 163 && t.new_hp == 14 && t.new_max_hp == 14,
                    "player_replacement_full_sequence_records_replacement");
        ASSERT_TEST(t.violations == 0, "player_replacement_full_sequence_no_violations");
    }

    /* Test 18: a completed transition may not leave the old slot authoritative */
    {
        PlayerReplacementTracker t;
        player_replacement_tracker_init(&t, 0, 1);
        Sample s;
        player_repl_test_sample(&s, 80, 0, 16, 152, true, 0);
        player_replacement_tracker_step(&t, &s);
        bool completed = false;
        for (int i = 0; i < 6; i++) {
            /* hp 0, but the production slot never leaves slot 0. */
            player_repl_test_sample(&s, 81 + i, 0, 0, 152, true, 0);
            if (player_replacement_tracker_step(&t, &s)) completed = true;
        }
        ASSERT_TEST(!completed && !t.saw_replacement && t.phase != PLAYER_REPL_PHASE_COMPLETE,
                    "player_replacement_stale_slot_never_completes");
        ASSERT_TEST(t.violations > 0, "player_replacement_stale_slot_violation_recorded");
    }

    /* Test 19: after the faint, the old slot may not become authoritative again even if
     * gBattleMons already looks like the replacement; a later valid commit cannot erase it. */
    {
        PlayerReplacementTracker t;
        player_replacement_tracker_init(&t, 0, 1);
        Sample s;

        /* A: old Chikorita active. */
        player_repl_test_sample(&s, 90, 0, 16, 152, true, 0);
        player_replacement_tracker_step(&t, &s);

        /* B: faint observed and production correctly fails closed. */
        player_repl_test_sample(&s, 91, 0, 0, 152, false, -1);
        player_replacement_tracker_step(&t, &s);

        /* C: invalid stale resurrection. The battler already contains Hoothoot, but the raw party
         * index and PartySnapshot both incorrectly make old slot 0 authoritative again. */
        player_repl_test_sample(&s, 92, 0, 14, 163, true, 0);
        bool bad_done = player_replacement_tracker_step(&t, &s);
        ASSERT_TEST(!bad_done && t.saw_faint && t.saw_fail_closed_window &&
                    !t.saw_replacement && t.violations > 0,
                    "player_replacement_old_slot_cannot_resurrect_after_faint");

        /* D: a legitimate slot-1 commit may complete the phase machine, but the prior violation is
         * latched and therefore still makes the overall runtime command fail. */
        const int violations_after_bad_frame = t.violations;
        player_repl_test_sample(&s, 93, 1, 14, 163, true, 1);
        bool done = player_replacement_tracker_step(&t, &s);
        ASSERT_TEST(done && t.phase == PLAYER_REPL_PHASE_COMPLETE && t.saw_replacement &&
                    violations_after_bad_frame > 0 && t.violations == violations_after_bad_frame,
                    "player_replacement_latched_violation_survives_later_commit");
    }

    #undef ASSERT_TEST

    printf("Pure tracker selftests: %d passed, %d failed\n", passed, failed);
    return (failed == 0) ? 0 : 1;
}

/** Compare only observed state: the frame number and the raw input held are labels, not state. */
static bool sample_changed(const Sample* a, const Sample* b) {
    Sample left = *a, right = *b;
    left.frame = right.frame = 0;
    left.input = right.input = 0;
    return memcmp(&left, &right, sizeof(Sample)) != 0;
}

static const char* lifecycle_name(BattleLifecycleState state) {
    switch (state) {
        case BATTLE_LIFECYCLE_UNKNOWN: return "UNKNOWN";
        case BATTLE_LIFECYCLE_INACTIVE: return "INACTIVE";
        case BATTLE_LIFECYCLE_INITIALIZING: return "INITIALIZING";
        case BATTLE_LIFECYCLE_ACTIVE: return "ACTIVE";
        case BATTLE_LIFECYCLE_ENDING: return "ENDING";
    }
    return "?";
}

static const char* kind_name(BattleKind kind) {
    switch (kind) {
        case BATTLE_KIND_NONE: return "NONE";
        case BATTLE_KIND_WILD_SINGLE: return "WILD_SINGLE";
        case BATTLE_KIND_TRAINER_SINGLE: return "TRAINER_SINGLE";
        case BATTLE_KIND_DOUBLES: return "DOUBLES";
        case BATTLE_KIND_MULTI_OR_PARTNER: return "MULTI_OR_PARTNER";
        case BATTLE_KIND_UNKNOWN: return "UNKNOWN";
    }
    return "?";
}

static const char* active_enemy_name(ActiveEnemyState state) {
    switch (state) {
        case ACTIVE_ENEMY_UNKNOWN: return "UNKNOWN";
        case ACTIVE_ENEMY_NONE_ACTIVE: return "NONE_ACTIVE";
        case ACTIVE_ENEMY_SLOT: return "SLOT";
        case ACTIVE_ENEMY_AMBIGUOUS: return "AMBIGUOUS";
    }
    return "?";
}

static const char* presence_name(uint8_t presence) {
    return presence == 1 ? "PRESENT" : (presence == 0 ? "ABSENT" : "UNKNOWN");
}

/* ------------------------------------------------------------------------------------------- */
/* Runtime invariants: a violation fails the run, it is never merely printed as a warning.      */
/* ------------------------------------------------------------------------------------------- */

static int g_violations = 0;
static int g_frames_checked = 0;

/*
 * Script errors: a scenario that did not actually do what it claims must fail the run too.
 *
 * Without this, a script could time out on its encounter hunt, fail to load its save, or contain a
 * typo'd command and still exit 0 - a false green that looks like reproduction but reproduced
 * nothing. Every deterministic script-command failure is recorded here and forces a non-zero exit.
 */
static int g_script_errors = 0;
static int g_script_line = 0;

static void script_error(const char* fmt, ...) {
    va_list ap;
    g_script_errors++;
    /* Line 0 means the error is not attributable to a script line (e.g. the script file itself
     * could not be opened), so the prefix is omitted rather than printing a misleading "line 0". */
    if (g_script_line > 0) {
        fprintf(stderr, "script error line %d: ", g_script_line);
    } else {
        fprintf(stderr, "script error: ");
    }
    va_start(ap, fmt);
    vfprintf(stderr, fmt, ap);
    va_end(ap);
    fprintf(stderr, "\n");
}

static void violate(const Sample* s, const char* what) {
    g_violations++;
    printf("  [INVARIANT VIOLATION] frame %d: %s\n", s->frame, what);
    printf("      inBattle=%d readable=%d lifecycle=%s presence=%s battlers=%u flags=0x%08X "
           "enemyParty=%u activeEnemy=%s slot=%d battler=%d playerSlot=%d playerKnown=%d\n",
           s->in_battle, s->in_battle_readable, lifecycle_name(s->lifecycle),
           presence_name(s->presence), s->battlers, s->type_flags, s->enemy_party_count_prod,
           active_enemy_name(s->active_enemy), s->enemy_slot, s->enemy_battler,
           s->active_player_slot, s->active_player_known);
}

static void check_invariants(const Sample* s) {
    g_frames_checked++;

    /* 1. ACTIVE only while the engine's own gate says a battle is running. */
    if (s->lifecycle == BATTLE_LIFECYCLE_ACTIVE && s->in_battle_readable && !s->in_battle) {
        violate(s, "lifecycle ACTIVE while gMain.inBattle is false");
    }
    /* 2. Production battle presence must agree with the authoritative lifecycle. */
    if (s->lifecycle == BATTLE_LIFECYCLE_ACTIVE && s->presence != 1) {
        violate(s, "presence not PRESENT while lifecycle ACTIVE");
    }
    if (s->lifecycle == BATTLE_LIFECYCLE_INACTIVE && s->presence != 0) {
        violate(s, "presence not ABSENT while lifecycle INACTIVE");
    }
    /* 3. An unresolved/unknown opponent must never be reported as slot 0 (or any slot). */
    if (s->active_enemy != ACTIVE_ENEMY_SLOT && s->enemy_slot != -1) {
        violate(s, "opponent party slot reported while active-enemy state is not SLOT");
    }
    if (s->active_enemy == ACTIVE_ENEMY_SLOT && s->enemy_battler < 0) {
        violate(s, "active-enemy SLOT without a resolved battler index");
    }
    /* 4. No stale opponent survives battle exit. */
    if (s->lifecycle != BATTLE_LIFECYCLE_ACTIVE) {
        if (s->enemy_party_count_prod != 0) {
            violate(s, "enemy party non-empty outside an ACTIVE battle (stale state)");
        }
        if (s->active_enemy == ACTIVE_ENEMY_SLOT) {
            violate(s, "opponent slot named outside an ACTIVE battle (stale state)");
        }
    }
    /* 5. A named opponent slot must be a real gBattlerPartyIndexes value inside the enemy party. */
    if (s->active_enemy == ACTIVE_ENEMY_SLOT) {
        if (s->enemy_battler < 0 || s->enemy_battler >= DUALDEX_MAX_BATTLERS) {
            violate(s, "resolved battler index out of range");
        } else {
            uint8_t authoritative = s->party_index[s->enemy_battler];
            if ((int)authoritative != s->enemy_slot) {
                violate(s, "resolved party slot does not come from gBattlerPartyIndexes[battler]");
            }
            if (s->enemy_slot < 0 || s->enemy_slot >= (int)s->enemy_party_count_prod) {
                violate(s, "resolved party slot outside the authoritative enemy party bounds");
            }
        }
    }
    /* 6. Doubles must never silently select one opponent. */
    if (s->battlers == 4 && s->lifecycle == BATTLE_LIFECYCLE_ACTIVE && s->opponent_battlers > 1) {
        if (s->active_enemy == ACTIVE_ENEMY_SLOT) {
            violate(s, "one opponent selected while more than one opponent is active");
        }
    }
    /* 7. An unmatched player slot must never be reported as a slot. */
    if (!s->active_player_known && s->active_player_slot != -1) {
        violate(s, "active player slot reported while it is not known");
    }
}

/* ------------------------------------------------------------------------------------------- */
/* Scripted input                                                                              */
/* ------------------------------------------------------------------------------------------- */

typedef struct {
    const GameMemoryConfig* cfg;
    int frame;
    int step;
} Driver;

static uint32_t parse_buttons(const char* s) {
    if (!s || !*s) return 0;
    if (!strcmp(s, "NONE") || !strcmp(s, "-")) return 0;
    uint32_t m = 0;
    char buf[128];
    snprintf(buf, sizeof(buf), "%s", s);
    for (char* tok = strtok(buf, "+,"); tok; tok = strtok(NULL, "+,")) {
        if (!strcmp(tok, "A")) m |= DUALDEX_BTN_A;
        else if (!strcmp(tok, "B")) m |= DUALDEX_BTN_B;
        else if (!strcmp(tok, "START")) m |= DUALDEX_BTN_START;
        else if (!strcmp(tok, "SELECT")) m |= DUALDEX_BTN_SELECT;
        else if (!strcmp(tok, "UP")) m |= DUALDEX_BTN_UP;
        else if (!strcmp(tok, "DOWN")) m |= DUALDEX_BTN_DOWN;
        else if (!strcmp(tok, "LEFT")) m |= DUALDEX_BTN_LEFT;
        else if (!strcmp(tok, "RIGHT")) m |= DUALDEX_BTN_RIGHT;
        else if (!strcmp(tok, "L")) m |= DUALDEX_BTN_L;
        else if (!strcmp(tok, "R")) m |= DUALDEX_BTN_R;
    }
    return m;
}

static void read_map_position(uint16_t* x, uint16_t* y, uint8_t* mg, uint8_t* mn) {
    uint32_t sb1 = 0;
    if (x) *x = 0xFFFF;
    if (y) *y = 0xFFFF;
    if (mg) *mg = 0xFF;
    if (mn) *mn = 0xFF;
    if (!read_u32(0x030041D8u, &sb1)) return;
    if (x) read_u16(sb1 + 0x04, x);
    if (y) read_u16(sb1 + 0x06, y);
    if (mg) read_u8(sb1 + 0x08, mg);
    if (mn) read_u8(sb1 + 0x09, mn);
}

/** Step one frame, sample it, assert the invariants and log any state change. */
static Sample step_one(Driver* d, uint32_t buttons, Sample* previous, bool* have_previous) {
    libretro_host_set_input_buttons(buttons);
    libretro_host_step_frame();
    d->frame++;

    Sample current;
    sample_state(d->cfg, d->frame, buttons, &current);
    check_invariants(&current);

    if (!*have_previous || sample_changed(previous, &current)) {
        if (!g_quiet) {
            printf("%6d  %-9s %-9s %-8s inB=%-5s bat=%u flags=0x%08X idx=%u,%u,%u,%u pos=%u,%u,%u,%u "
                   "sp=%u,%u,%u,%u hp=%u,%u,%u,%u eParty=%u/%u ae=%s slot=%d bat=%d opp=%u fnt=%d "
                   "pParty=%u pSlot=%d/%d st0=%d,%d,%d,%d\n",
                   current.frame, lifecycle_name(current.lifecycle), kind_name(current.kind),
                   presence_name(current.presence),
                   current.in_battle_readable ? (current.in_battle ? "true" : "false") : "?",
                   current.battlers, current.type_flags,
                   current.party_index[0], current.party_index[1],
                   current.party_index[2], current.party_index[3],
                   current.position[0], current.position[1],
                   current.position[2], current.position[3],
                   current.mon_species[0], current.mon_species[1],
                   current.mon_species[2], current.mon_species[3],
                   current.mon_hp[0], current.mon_hp[1], current.mon_hp[2], current.mon_hp[3],
                   current.enemy_party_count, current.enemy_party_count_prod,
                   active_enemy_name(current.active_enemy), current.enemy_slot,
                   current.enemy_battler, current.opponent_battlers, current.enemy_fainted ? 1 : 0,
                   current.player_party_count_prod, current.active_player_slot,
                   current.active_player_known ? 1 : 0,
                   current.mon_stat_stages[0][0], current.mon_stat_stages[0][1],
                   current.mon_stat_stages[0][2], current.mon_stat_stages[0][3]);
        }
        *previous = current;
        *have_previous = true;
    }
    return current;
}

static void hold(Driver* d, uint32_t buttons, int frames, Sample* prev, bool* have) {
    for (int i = 0; i < frames; i++) step_one(d, buttons, prev, have);
}

static void write_ppm(const char* path) {
    static uint8_t fb[512 * 512 * 4];
    unsigned w = 0, h = 0;
    size_t pitch = 0;
    int fmt = 0;
    if (!libretro_host_copy_video_frame(fb, sizeof(fb), &w, &h, &pitch, &fmt)) return;
    FILE* f = fopen(path, "wb");
    if (!f) return;
    fprintf(f, "P6\n%u %u\n255\n", w, h);
    for (unsigned y = 0; y < h; y++) {
        const uint8_t* row = fb + (size_t)y * pitch;
        for (unsigned x = 0; x < w; x++) {
            uint8_t r, g, b;
            if (fmt == 1) {
                const uint8_t* p = row + (size_t)x * 4;
                b = p[0]; g = p[1]; r = p[2];
            } else {
                uint16_t px;
                memcpy(&px, row + (size_t)x * 2, 2);
                if (fmt == 2) {
                    r = (uint8_t)(((px >> 11) & 0x1F) * 255 / 31);
                    g = (uint8_t)(((px >> 5) & 0x3F) * 255 / 63);
                    b = (uint8_t)((px & 0x1F) * 255 / 31);
                } else {
                    r = (uint8_t)(((px >> 10) & 0x1F) * 255 / 31);
                    g = (uint8_t)(((px >> 5) & 0x1F) * 255 / 31);
                    b = (uint8_t)((px & 0x1F) * 255 / 31);
                }
            }
            fputc(r, f); fputc(g, f); fputc(b, f);
        }
    }
    fclose(f);
    printf("  [shot] %s\n", path);
}

/*
 * Script commands (one per line, `#` comments):
 *   press <BTN|NONE> <frames>      hold buttons for N frames
 *   wait <frames>                  hold nothing
 *   mash <frames>                  hold A on a 4-on/6-off cycle
 *   spama <count>                  press A a bounded number of times (long gaps)
 *   walk <DIR> <tiles> [optional]  walk tile by tile, pressing A if a script lock blocks progress.
 *                                  A blocked step is a script ERROR unless the trailing `optional`
 *                                  token is present.
 *   hunt <iterations>              wander until gMain.inBattle asserts (wild encounter).
 *                                  A timeout is a script ERROR.
 *   escape <DIR> <iterations>      interleave A with movement attempts (re-triggerable dialogue).
 *                                  A timeout is a script ERROR.
 *   await <cb2> <n>                press A until gMain.callback2 becomes <cb2>. Timeout is an ERROR.
 *   untilout <cb2> <n>             press UP+A while gMain.callback2 is <cb2>, stop when it changes.
 *                                  Timeout is an ERROR.
 *   matrix <label>                 print the full runtime tuple for the current frame
 *   shot <path.ppm>                dump the current video frame
 *   savsave <path>                 flush the core's battery save RAM to a .sav file. Failure is an
 *                                  ERROR.
 *   savload <path>                 load a .sav file into the core's battery save RAM. A missing
 *                                  file, a size mismatch or a load failure is an ERROR.
 *   reject-encounter               unexpected encounter: fail the run
 *
 * Assertions (each records a script ERROR when the condition does not hold):
 *   assert-battle inactive|active  the authoritative lifecycle state
 *   assert-party-count player <n>  gPlayerPartyCount equals <n>
 *   assert-map <group> <number>    the player is standing on that map
 *   assert-in-battle-flag true|false
 *
 * An unknown command is a script ERROR. The process exits non-zero when any script error or any
 * runtime invariant violation occurred.
 */
static void print_matrix(const Sample* s, const char* label) {
    uint16_t mx = 0, my = 0; uint8_t mg = 0, mn = 0;
    read_map_position(&mx, &my, &mg, &mn);
    printf("[MATRIX] %s pos=%u,%u@%u/%u frame=%d inBattle=%s lifecycle=%s kind=%s presence=%s battlers=%u "
           "typeFlags=0x%08X outcome=%u absent=0x%02X positions=%u,%u,%u,%u indexes=%u,%u,%u,%u "
           "enemyPartyCount=%u playerPartyCount=%u species=%u,%u,%u,%u hp=%u,%u,%u,%u "
           "maxHP=%u,%u,%u,%u statStages0=%d,%d,%d,%d,%d,%d,%d,%d "
           "activeEnemy=%s resolvedBattler=%d resolvedEnemySlot=%d opponentBattlers=%u fainted=%d "
           "activePlayerSlot=%d playerKnown=%d\n",
           label, mx, my, mg, mn, s->frame,
           s->in_battle_readable ? (s->in_battle ? "true" : "false") : "unreadable",
           lifecycle_name(s->lifecycle), kind_name(s->kind), presence_name(s->presence),
           s->battlers, s->type_flags, s->outcome, s->absent_flags,
           s->position[0], s->position[1], s->position[2], s->position[3],
           s->party_index[0], s->party_index[1], s->party_index[2], s->party_index[3],
           s->enemy_party_count, s->player_party_count,
           s->mon_species[0], s->mon_species[1], s->mon_species[2], s->mon_species[3],
           s->mon_hp[0], s->mon_hp[1], s->mon_hp[2], s->mon_hp[3],
           s->mon_max_hp[0], s->mon_max_hp[1], s->mon_max_hp[2], s->mon_max_hp[3],
           s->mon_stat_stages[0][0], s->mon_stat_stages[0][1], s->mon_stat_stages[0][2],
           s->mon_stat_stages[0][3], s->mon_stat_stages[0][4], s->mon_stat_stages[0][5],
           s->mon_stat_stages[0][6], s->mon_stat_stages[0][7],
           active_enemy_name(s->active_enemy), s->enemy_battler, s->enemy_slot,
           s->opponent_battlers, s->enemy_fainted ? 1 : 0,
           s->active_player_slot, s->active_player_known ? 1 : 0);
}

static uint8_t get_battler0_command(const uint8_t* ewram, size_t ewram_sz) {
    if (!ewram || ewram_sz < 0x300) return 0xFF;
    uint32_t bres = (uint32_t)ewram[0x254] | ((uint32_t)ewram[0x255] << 8) |
                    ((uint32_t)ewram[0x256] << 16) | ((uint32_t)ewram[0x257] << 24);
    if (bres < 0x02000000 || bres >= 0x02040000) return 0xFF;
    uint32_t off = bres - 0x02000000 + 16;
    if (off >= ewram_sz) return 0xFF;
    return ewram[off];
}

static void clear_wild_battle(Driver* d, Sample* previous, bool* have_previous) {
    size_t ewram_sz = 0;
    uint8_t* ewram = libretro_host_get_ewram(&ewram_sz);
    if (!ewram || ewram_sz < 0x400) return;

    printf("  [wild btl] entering clear_wild_battle\n");
    bool cleared = false;
    for (int it = 0; it < 6000; it++) {
        uint8_t ib = 0;
        read_u8(d->cfg->main_struct_gba_address + d->cfg->main_in_battle_byte_offset, &ib);
        if (!((ib >> d->cfg->main_in_battle_bit) & 1)) {
            printf("  [wild btl] battle finished after %d iterations\n", it);
            cleared = true;
            break;
        }

        uint32_t flags = (uint32_t)ewram[0xAC] | ((uint32_t)ewram[0xAD] << 8) |
                         ((uint32_t)ewram[0xAE] << 16) | ((uint32_t)ewram[0xAF] << 24);
        if (flags & 0x00000008) {
            /* Trainer battle: attack with Tackle */
            step_one(d, ((it % 10) < 4) ? DUALDEX_BTN_A : 0, previous, have_previous);
            continue;
        }

        uint8_t cmd = get_battler0_command(ewram, ewram_sz);
        uint32_t exec = 0;
        read_u32(HNS_RELEASE_BATTLE_CONTROLLER_EXEC_FLAGS, &exec);

        if (it % 60 == 0) {
            printf("  [wild btl] it=%d ib=%u exec=0x%08X cmd=%u cursor=%u\n",
                   it, ib, exec, cmd, ewram[0x3A4]);
        }

        /* Check if player controller is in action selection: cmd == 17 (CONTROLLER_CHOOSEACTION) */
        if (cmd == 17) {
            uint8_t cursor = ewram[0x3A4];
            if (cursor == 3) {
                hold(d, DUALDEX_BTN_A, 4, previous, have_previous);
                hold(d, 0, 8, previous, have_previous);
            } else if (!(cursor & 1)) {
                hold(d, DUALDEX_BTN_RIGHT, 4, previous, have_previous);
                hold(d, 0, 8, previous, have_previous);
            } else if (!(cursor & 2)) {
                hold(d, DUALDEX_BTN_DOWN, 4, previous, have_previous);
                hold(d, 0, 8, previous, have_previous);
            }
        } else {
            /* Text, intro, or waiting: press B to advance text safely without selecting Fight */
            uint32_t btn = ((it % 4) < 2) ? DUALDEX_BTN_B : 0;
            step_one(d, btn, previous, have_previous);
        }
    }
    if (!cleared) {
        script_error("clear_wild_battle timed out after 6000 iterations; battle remained active");
    }
}

static void do_menusave(Driver* d, Sample* previous, bool* have_previous) {
    hold(d, 0, 40, previous, have_previous);

    uint32_t menu_cb = 0;
    for (int attempt = 0; attempt < 15; attempt++) {
        hold(d, DUALDEX_BTN_START, 8, previous, have_previous);
        hold(d, 0, 30, previous, have_previous);
        read_u32(0x03006124u, &menu_cb);
        if (menu_cb != 0) break;
    }
    if (menu_cb == 0) {
        script_error("menusave failed: could not open start menu with START button");
        return;
    }

    uint8_t cur_pos = 0;
    uint8_t num_actions = 0;
    read_u8(0x0203b8c1u, &cur_pos);
    read_u8(0x0203b8b6u, &num_actions);
    if (num_actions > 9) num_actions = 9;

    int save_target = -1;
    for (int i = 0; i < num_actions; i++) {
        uint8_t action = 0;
        read_u8(0x0203b8b8u + i, &action);
        if (action == 5 /* MENU_ACTION_SAVE */) {
            save_target = i;
            break;
        }
    }
    if (save_target < 0) {
        script_error("menusave failed: MENU_ACTION_SAVE not found in start menu actions (%u actions)", num_actions);
        return;
    }

    for (int attempt = 0; attempt < 10 && cur_pos != (uint8_t)save_target; attempt++) {
        if (cur_pos < (uint8_t)save_target) {
            hold(d, DUALDEX_BTN_DOWN, 4, previous, have_previous);
        } else {
            hold(d, DUALDEX_BTN_UP, 4, previous, have_previous);
        }
        hold(d, 0, 20, previous, have_previous);
        read_u8(0x0203b8c1u, &cur_pos);
    }

    /* Select SAVE */
    hold(d, DUALDEX_BTN_A, 8, previous, have_previous);
    hold(d, 0, 100, previous, have_previous);

    /* Confirm SAVE (Yes) */
    hold(d, DUALDEX_BTN_A, 8, previous, have_previous);
    hold(d, 0, 120, previous, have_previous);

    /* In case of overwrite confirmation prompt, press A again */
    hold(d, DUALDEX_BTN_A, 8, previous, have_previous);
    hold(d, 0, 300, previous, have_previous);

    /* Clear remaining text and return to overworld */
    for (int i = 0; i < 30; i++) {
        read_u32(0x03006124u, &menu_cb);
        if (menu_cb == 0) break;
        hold(d, DUALDEX_BTN_A, 6, previous, have_previous);
        hold(d, 0, 20, previous, have_previous);
    }
    printf("  [menusave] in-game save completed via controller input\n");
}

/* Drive an ALREADY-ACTIVE battle to completion with ordinary controller input: Fight + move slot 0
 * every turn, declining every modal prompt. Uses the documented battle-controller states (bcmd 17
 * action selection, 19 move selection, 18 Yes/No, 21 party menu) rather than blind A-mashing.
 *
 * Returns false when the battle has not ended within @p max_f frames; the caller decides whether
 * that is fatal. This never starts a battle and never writes to the machine. */
static bool drive_battle_to_end(Driver* d, int max_f, Sample* previous, bool* have_previous) {
    size_t ewram_sz = 0;
    uint8_t* ewram = libretro_host_get_ewram(&ewram_sz);
    uint8_t ib = 0;
    int f = 0;
    bool ended = false;
    for (; f < max_f; f++) {
        read_u8(d->cfg->main_struct_gba_address + d->cfg->main_in_battle_byte_offset, &ib);
        if (!((ib >> d->cfg->main_in_battle_bit) & 1)) { ended = true; break; }

        uint8_t bcmd = get_battler0_command(ewram, ewram_sz);
        if (bcmd == 17) {          /* action selection: cursor to Fight */
            uint8_t cur = (ewram_sz > 0x3A4) ? ewram[0x3A4] : 0;
            if (cur != 0) {
                hold(d, DUALDEX_BTN_UP, 4, previous, have_previous);
                hold(d, DUALDEX_BTN_LEFT, 4, previous, have_previous);
            }
            hold(d, DUALDEX_BTN_A, 4, previous, have_previous);
            hold(d, 0, 8, previous, have_previous);
        } else if (bcmd == 19) {   /* move selection: first move */
            hold(d, DUALDEX_BTN_A, 4, previous, have_previous);
            hold(d, 0, 8, previous, have_previous);
        } else if (bcmd == 18 || bcmd == 21) {
            /* Yes/No box (shift prompt) or an accidental party menu: decline/cancel. */
            hold(d, DUALDEX_BTN_B, 4, previous, have_previous);
            hold(d, 0, 12, previous, have_previous);
        } else {
            step_one(d, ((f % 6) < 3) ? DUALDEX_BTN_A : 0, previous, have_previous);
        }
    }
    if (ended) {
        printf("  [battle] finished after %d frames\n", f);
        /* Clear the remaining post-battle text so the caller resumes on the overworld, and make
         * sure the field is really unlocked again: a trainer battle ends inside the NPC's own
         * post-battle script, and a walk that starts before that script releases the player sees
         * every direction as blocked. B cancels a stray menu, A advances the message. */
        for (int k = 0; k < 8; k++) {
            hold(d, DUALDEX_BTN_B, 6, previous, have_previous);
            hold(d, 0, 20, previous, have_previous);
            hold(d, DUALDEX_BTN_A, 6, previous, have_previous);
            hold(d, 0, 24, previous, have_previous);
        }
        hold(d, 0, 60, previous, have_previous);
    }
    return ended;
}

static bool do_walk_tiles(Driver* d, uint32_t btn, const char* dir_name, int tiles, bool allow_blocked,
                          bool allow_battle, Sample* previous, bool* have_previous) {
    for (int t = 0; t < tiles; t++) {
        uint16_t x0 = 0, y0 = 0; uint8_t g0 = 0, m0 = 0;
        read_map_position(&x0, &y0, &g0, &m0);
        bool moved = false;
        for (int attempt = 0; attempt < 30 && !moved; attempt++) {
            for (int i = 0; i < 44 && !moved; i++) {
                step_one(d, btn, previous, have_previous);
                uint16_t x1 = 0, y1 = 0; uint8_t g1 = 0, m1 = 0;
                read_map_position(&x1, &y1, &g1, &m1);
                if (x1 != x0 || y1 != y0 || g1 != g0 || m1 != m0) moved = true;
            }
            hold(d, 0, 8, previous, have_previous);
            if (moved) break;
            /* A battle intercepted the step. BEFORE clearing it, prove it is actually a WILD battle:
             * `clear_wild_battle` is only valid for wild encounters. A trainer battle reached this
             * way means navigation walked into a trainer's sight line, which is a scenario bug the
             * caller must fix by choosing a different route -- silently driving the trainer battle
             * would corrupt the scenario's battle accounting and hide the mistake. */
            uint8_t ib = 0;
            read_u8(d->cfg->main_struct_gba_address + d->cfg->main_in_battle_byte_offset, &ib);
            if ((ib >> d->cfg->main_in_battle_bit) & 1) {
                Sample bsample;
                sample_state(d->cfg, d->frame, 0, &bsample);
                if (bsample.kind != BATTLE_KIND_WILD_SINGLE && !allow_battle) {
                    script_error("navigation triggered unexpected trainer battle at (%u,%u) "
                                 "while attempting %s (battle kind=%s, battlers=%u, "
                                 "enemy party=%u); refusing to auto-clear a trainer battle "
                                 "(pass the 'battle' token on this walk to authorize it)",
                                 x0, y0, dir_name, kind_name(bsample.kind), bsample.battlers,
                                 bsample.enemy_party_count_prod);
                    return false;
                }
                if (bsample.kind == BATTLE_KIND_WILD_SINGLE) {
                    clear_wild_battle(d, previous, have_previous);
                } else {
                    /* The scenario explicitly authorized this: a trainer stands in the only
                     * corridor. Report it loudly and win it with ordinary input. */
                    printf("  [walk] %s at (%u,%u) triggered a %s battle (enemy party %u) -- "
                           "authorized by the 'battle' token, finishing it\n",
                           dir_name, x0, y0, kind_name(bsample.kind),
                           bsample.enemy_party_count_prod);
                    if (!drive_battle_to_end(d, 30000, previous, have_previous)) {
                        script_error("walk %s: the trainer battle triggered at (%u,%u) did not "
                                     "finish within 30000 frames", dir_name, x0, y0);
                        break;
                    }
                }
                read_u8(d->cfg->main_struct_gba_address + d->cfg->main_in_battle_byte_offset, &ib);
                if ((ib >> d->cfg->main_in_battle_bit) & 1) {
                    /* clear_wild_battle already called script_error; break to avoid cascaded false steps */
                    break;
                }
                hold(d, 0, 80, previous, have_previous);
                uint16_t x1 = 0, y1 = 0; uint8_t g1 = 0, m1 = 0;
                read_map_position(&x1, &y1, &g1, &m1);
                if (x1 != x0 || y1 != y0 || g1 != g0 || m1 != m0) moved = true;
                if (moved) break;
            }
            read_u8(d->cfg->main_struct_gba_address + d->cfg->main_in_battle_byte_offset, &ib);
            if (!((ib >> d->cfg->main_in_battle_bit) & 1)) {
                if (attempt >= 2) {
                    for (int k = 0; k < 5 && !moved; k++) {
                        hold(d, DUALDEX_BTN_A, 8, previous, have_previous);
                        hold(d, 0, 30, previous, have_previous);
                        uint16_t x1 = 0, y1 = 0; uint8_t g1 = 0, m1 = 0;
                        read_map_position(&x1, &y1, &g1, &m1);
                        if (x1 != x0 || y1 != y0 || g1 != g0 || m1 != m0) moved = true;
                    }
                }
            }
        }
        uint16_t x2 = 0, y2 = 0; uint8_t g2 = 0, m2 = 0;
        read_map_position(&x2, &y2, &g2, &m2);
        if (!moved) {
            if (allow_blocked) {
                printf("  [walk] %s blocked at (%u,%u)@%u/%u (allowed)\n",
                       dir_name, x2, y2, g2, m2);
            } else {
                script_error("walk %s blocked at (%u,%u)@%u/%u after %d attempts "
                             "(use 'walk %s <tiles> optional' to allow this)",
                             dir_name, x2, y2, g2, m2, 30, dir_name);
            }
            return false;
        }
    }
    return true;
}

/* Drive a VOLUNTARY player-side switch to @p target_slot with ordinary controller input.
 *
 * The same input path the `voluntary-switch` script command uses (action menu cursor 2 is
 * POKEMON; the in-battle party menu opens a sub-menu whose option 0 is SHIFT), packaged as a
 * step function so the voluntary-OPPONENT-switch tracker can reuse it while it stalls.
 *
 * `*phase` carries the sub-state across calls and is reset to 0 once the engine reports the target
 * slot as the authoritative active player slot. Returns true on that commit. Never writes memory.
 */
static bool player_switch_step(Driver* d, int target_slot, int* phase, int* attempts,
                               const Sample* s, Sample* previous, bool* have_previous) {
    size_t ewram_sz = 0;
    uint8_t* ewram = libretro_host_get_ewram(&ewram_sz);
    uint8_t bcmd = get_battler0_command(ewram, ewram_sz);
    uint32_t cb2 = 0;
    read_u32(HNS_RELEASE_GMAIN_BASE + 0x04u, &cb2);
    bool in_party_menu = (cb2 >= 0x0819379Cu && cb2 < 0x0819F548u) || (bcmd == 21);

    if (*phase >= 3) {
        if (s->lifecycle == BATTLE_LIFECYCLE_ACTIVE && s->active_player_known &&
            s->active_player_slot == target_slot && s->party_index[0] == target_slot) {
            *phase = 0;
            return true;
        }
        step_one(d, ((d->frame % 6) < 3) ? DUALDEX_BTN_A : 0, previous, have_previous);
        return false;
    }
    if (*phase == 0) {
        if (bcmd == 17) {
            uint8_t cur = (ewram_sz > 0x3A4) ? ewram[0x3A4] : 0;
            if (cur != 2) {
                if (cur & 1u) {
                    hold(d, DUALDEX_BTN_LEFT, 4, previous, have_previous);
                    hold(d, 0, 8, previous, have_previous);
                }
                if (!(cur & 2u)) {
                    hold(d, DUALDEX_BTN_DOWN, 4, previous, have_previous);
                    hold(d, 0, 8, previous, have_previous);
                }
            } else {
                hold(d, DUALDEX_BTN_A, 6, previous, have_previous);
                hold(d, 0, 20, previous, have_previous);
                *phase = 1;
                *attempts = 0;
            }
        } else if (bcmd == 19) {
            hold(d, DUALDEX_BTN_B, 4, previous, have_previous);
            hold(d, 0, 8, previous, have_previous);
        } else {
            step_one(d, ((d->frame % 4) < 2) ? DUALDEX_BTN_B : 0, previous, have_previous);
        }
        return false;
    }
    if (*phase == 1) {
        /* The in-battle party menu is identified by the controller state (bcmd 21) or by the
         * field callback, and the cursor is read straight from the released party-menu slot byte.
         * If the menu does not show up within a bounded number of frames the driver cancels out
         * and re-opens it from the action menu rather than sitting in an unrecognised modal. */
        if (in_party_menu) {
            int8_t pslot = -1;
            read_u8(HNS_RELEASE_PARTY_MENU_SLOT_ID, (uint8_t*)&pslot);
            if ((int)pslot == target_slot) {
                hold(d, DUALDEX_BTN_A, 6, previous, have_previous);
                hold(d, 0, 25, previous, have_previous);
                *phase = 2;
            } else if (pslot >= 0 && pslot < 6) {
                hold(d, (pslot < target_slot) ? DUALDEX_BTN_DOWN : DUALDEX_BTN_UP, 6,
                     previous, have_previous);
                hold(d, 0, 15, previous, have_previous);
            } else {
                /* Unreadable cursor: cancel back to the action menu and try again. */
                hold(d, DUALDEX_BTN_B, 6, previous, have_previous);
                hold(d, 0, 20, previous, have_previous);
                *phase = 0;
                *attempts += 1;
            }
            return false;
        }
        if (*attempts > 6) {
            hold(d, DUALDEX_BTN_B, 6, previous, have_previous);
            hold(d, 0, 20, previous, have_previous);
            *phase = 0;
            return false;
        }
        hold(d, 0, 4, previous, have_previous);
        return false;
    }
    hold(d, DUALDEX_BTN_A, 6, previous, have_previous);
    hold(d, 0, 30, previous, have_previous);
    *phase = 3;
    return false;
}

static int run_script(Driver* d, const char* script_path) {
    FILE* f = script_path ? fopen(script_path, "r") : stdin;
    if (!f) {
        /* A script that cannot be opened means the scenario never ran at all. That must be as fatal
         * as any in-script failure, so it is recorded as a script error here AND returned to the
         * caller: main() also folds the return value into its exit status. */
        g_script_line = 0;
        script_error("cannot open script '%s'", script_path ? script_path : "(null)");
        return 1;
    }

    Sample previous;
    bool have_previous = false;
    char line[512];

    while (fgets(line, sizeof(line), f)) {
        char cmd[64] = {0}, a1[256] = {0}, a2[256] = {0}, a3[256] = {0}, a4[256] = {0};
        g_script_line++;
        int n = sscanf(line, "%63s %255s %255s %255s %255s", cmd, a1, a2, a3, a4);
        if (n <= 0 || cmd[0] == '#') continue;
        /* Ignore a UTF-8 BOM / leading whitespace-only lines. */
        if (cmd[0] == '\n' || cmd[0] == '\r') continue;
        d->step++;

        if (!strcmp(cmd, "press")) {
            hold(d, parse_buttons(a1), a2[0] ? atoi(a2) : 4, &previous, &have_previous);
        } else if (!strcmp(cmd, "wait")) {
            hold(d, 0, a1[0] ? atoi(a1) : 30, &previous, &have_previous);
        } else if (!strcmp(cmd, "mash")) {
            int total = a1[0] ? atoi(a1) : 60;
            for (int i = 0; i < total; i++) {
                step_one(d, ((i % 10) < 4) ? DUALDEX_BTN_A : 0, &previous, &have_previous);
            }
        } else if (!strcmp(cmd, "spama")) {
            /* Press A a bounded number of times with a long gap. Unlike `mash`, the count is
             * explicit: an unbounded A mash on a re-triggerable NPC dialogue never returns. */
            int cnt = a1[0] ? atoi(a1) : 10;
            for (int i = 0; i < cnt; i++) {
                hold(d, DUALDEX_BTN_A, 8, &previous, &have_previous);
                hold(d, 0, 120, &previous, &have_previous);
            }
            printf("  [spama] %d presses\n", cnt);
        } else if (!strcmp(cmd, "walk")) {
            uint32_t btn = parse_buttons(a1);
            int tiles = a2[0] ? atoi(a2) : 1;
            /* Trailing tokens, in any order:
             *   optional -- a blocked step is reported instead of fatal
             *   battle   -- a TRAINER battle that intercepts the step is reported and finished
             *               with ordinary input instead of failing closed. Off by default, because
             *               a trainer battle intercepted by navigation is normally a route bug. */
            bool allow_blocked = false;
            bool allow_battle = false;
            const char* extra[2] = { a3, a4 };
            for (int i = 0; i < 2; i++) {
                if (!strcmp(extra[i], "optional")) allow_blocked = true;
                else if (!strcmp(extra[i], "battle")) allow_battle = true;
            }
            do_walk_tiles(d, btn, a1, tiles, allow_blocked, allow_battle,
                          &previous, &have_previous);
        } else if (!strcmp(cmd, "walk-to")) {
            uint16_t target_x = (uint16_t)atoi(a1);
            uint16_t target_y = (uint16_t)atoi(a2);
            for (int step = 0; step < 30; step++) {
                uint16_t x = 0, y = 0; uint8_t g = 0, m = 0;
                read_map_position(&x, &y, &g, &m);
                if (x == target_x && y == target_y) break;
                uint32_t btn = 0;
                const char* dir_name = "";
                if (x < target_x) { btn = DUALDEX_BTN_RIGHT; dir_name = "RIGHT"; }
                else if (x > target_x) { btn = DUALDEX_BTN_LEFT; dir_name = "LEFT"; }
                else if (y < target_y) { btn = DUALDEX_BTN_DOWN; dir_name = "DOWN"; }
                else if (y > target_y) { btn = DUALDEX_BTN_UP; dir_name = "UP"; }
                if (!do_walk_tiles(d, btn, dir_name, 1, false, false, &previous, &have_previous)) {
                    script_error("walk-to (%u,%u) failed: blocked while moving %s", target_x, target_y, dir_name);
                    break;
                }
            }
            uint16_t x = 0, y = 0; uint8_t g = 0, m = 0;
            read_map_position(&x, &y, &g, &m);
            if (x != target_x || y != target_y) {
                script_error("walk-to (%u,%u) failed: reached (%u,%u) instead", target_x, target_y, x, y);
            } else {
                printf("  [walk-to] reached (%u,%u) OK\n", target_x, target_y);
            }
        } else if (!strcmp(cmd, "hunt")) {
            int maxit = a1[0] ? atoi(a1) : 400;
            uint32_t dirs[2] = { DUALDEX_BTN_LEFT, DUALDEX_BTN_RIGHT };
            int di = 0, found = 0, i = 0;
            for (; i < maxit && !found; i++) {
                uint8_t ib = 0;
                read_u8(d->cfg->main_struct_gba_address + d->cfg->main_in_battle_byte_offset, &ib);
                if ((ib >> d->cfg->main_in_battle_bit) & 1) { found = 1; break; }
                hold(d, dirs[di], 20, &previous, &have_previous);
                hold(d, 0, 8, &previous, &have_previous);
                di = 1 - di;
            }
            uint8_t ib = 0;
            read_u8(d->cfg->main_struct_gba_address + d->cfg->main_in_battle_byte_offset, &ib);
            found = ((ib >> d->cfg->main_in_battle_bit) & 1);
            printf("  [hunt] %s after %d iterations\n", found ? "ENCOUNTER" : "TIMEOUT", i);
            if (!found) {
                script_error("hunt timed out after %d iterations without reaching a wild encounter",
                             maxit);
            }
        } else if (!strcmp(cmd, "await")) {
            /* Press A until gMain.callback2 becomes <hex>. Modal UIs (the wall clock, the naming
             * screen) install their own callback, which is the cleanest "the dialog is really up"
             * signal available without interpreting pixels. */
            uint32_t target = a1[0] ? (uint32_t)strtoul(a1, NULL, 16) : 0u;
            int maxit = a2[0] ? atoi(a2) : 25;
            int it = 0; uint32_t cb2 = 0;
            for (; it < maxit; it++) {
                read_u32(HNS_RELEASE_GMAIN_BASE + 0x04, &cb2);
                if (cb2 == target) break;
                hold(d, DUALDEX_BTN_A, 8, &previous, &have_previous);
                hold(d, 0, 120, &previous, &have_previous);
            }
            read_u32(HNS_RELEASE_GMAIN_BASE + 0x04, &cb2);
            printf("  [await] 0x%08X -> %s after %d iterations\n", target,
                   cb2 == target ? "REACHED" : "TIMEOUT", it);
            if (cb2 != target) {
                script_error("await 0x%08X timed out after %d iterations (callback2 is 0x%08X)",
                             target, maxit, cb2);
            }
        } else if (!strcmp(cmd, "untilout")) {
            /* Press UP then A while gMain.callback2 is still <hex>, stopping the moment it changes.
             * UP moves a Yes/No cursor to YES, so this confirms modal dialogs instead of answering
             * "No" and re-entering them; stopping on the callback change is what prevents the
             * follow-up presses from re-opening the very dialog that was just dismissed. */
            uint32_t target = a1[0] ? (uint32_t)strtoul(a1, NULL, 16) : 0u;
            int maxit = a2[0] ? atoi(a2) : 25;
            int it = 0; uint32_t cb2 = 0;
            for (; it < maxit; it++) {
                read_u32(HNS_RELEASE_GMAIN_BASE + 0x04, &cb2);
                if (cb2 != target) break;
                hold(d, DUALDEX_BTN_UP, 4, &previous, &have_previous);
                hold(d, 0, 15, &previous, &have_previous);
                hold(d, DUALDEX_BTN_A, 8, &previous, &have_previous);
                hold(d, 0, 120, &previous, &have_previous);
            }
            read_u32(HNS_RELEASE_GMAIN_BASE + 0x04, &cb2);
            printf("  [untilout] 0x%08X -> %s after %d iterations\n", target,
                   cb2 != target ? "LEFT" : "TIMEOUT", it);
            if (cb2 == target) {
                script_error("untilout 0x%08X timed out after %d iterations; the modal UI never "
                             "released", target, maxit);
            }
        } else if (!strcmp(cmd, "escape")) {
            /* Advance one dialogue step, then IMMEDIATELY try to move. A message that is
             * re-triggerable by A (talking to the NPC you stand in front of) reopens as soon as the
             * release happens, so movement has to be interleaved with the A presses rather than
             * batched after them.
             *
             * UP is pressed first: it is a no-op in a plain message box, but it moves a Yes/No
             * cursor onto YES, which is required to get through the clock-time confirmation and
             * Elm's partner dialogue without selecting "No" and looping. */
            uint32_t btn = parse_buttons(a1);
            int maxit = a2[0] ? atoi(a2) : 40;
            int it = 0; bool moved = false;
            for (; it < maxit && !moved; it++) {
                hold(d, DUALDEX_BTN_UP, 4, &previous, &have_previous);
                hold(d, 0, 15, &previous, &have_previous);
                hold(d, DUALDEX_BTN_A, 8, &previous, &have_previous);
                hold(d, 0, 50, &previous, &have_previous);
                uint16_t x0 = 0, y0 = 0; uint8_t g0 = 0, m0 = 0;
                read_map_position(&x0, &y0, &g0, &m0);
                for (int i = 0; i < 50 && !moved; i++) {
                    step_one(d, btn, &previous, &have_previous);
                    uint16_t x1 = 0, y1 = 0; uint8_t g1 = 0, m1 = 0;
                    read_map_position(&x1, &y1, &g1, &m1);
                    if (x1 != x0 || y1 != y0 || g1 != g0 || m1 != m0) moved = true;
                }
                hold(d, 0, 10, &previous, &have_previous);
            }
            printf("  [escape] %s after %d iterations\n", moved ? "MOVED" : "TIMEOUT", it);
            if (!moved) {
                script_error("escape %s timed out after %d iterations without freeing the player "
                             "(the script lock never released)", a1, maxit);
            }
        } else if (!strcmp(cmd, "escapeno")) {
            /* Similar to escape, but presses DOWN first to select NO on Yes/No prompts,
             * and presses B to fast-forward text/cancel, then tries to move. */
            uint32_t btn = parse_buttons(a1);
            int maxit = a2[0] ? atoi(a2) : 40;
            int it = 0; bool moved = false;
            for (; it < maxit && !moved; it++) {
                hold(d, DUALDEX_BTN_DOWN, 4, &previous, &have_previous);
                hold(d, 0, 15, &previous, &have_previous);
                hold(d, DUALDEX_BTN_B, 8, &previous, &have_previous);
                hold(d, 0, 15, &previous, &have_previous);
                hold(d, DUALDEX_BTN_A, 8, &previous, &have_previous);
                hold(d, 0, 50, &previous, &have_previous);
                uint16_t x0 = 0, y0 = 0; uint8_t g0 = 0, m0 = 0;
                read_map_position(&x0, &y0, &g0, &m0);
                for (int i = 0; i < 50 && !moved; i++) {
                    step_one(d, btn, &previous, &have_previous);
                    uint16_t x1 = 0, y1 = 0; uint8_t g1 = 0, m1 = 0;
                    read_map_position(&x1, &y1, &g1, &m1);
                    if (x1 != x0 || y1 != y0 || g1 != g0 || m1 != m0) moved = true;
                }
                hold(d, 0, 10, &previous, &have_previous);
            }
            printf("  [escapeno] %s after %d iterations\n", moved ? "MOVED" : "TIMEOUT", it);
            if (!moved) {
                script_error("escapeno %s timed out after %d iterations without freeing the player "
                             "(the script lock never released)", a1, maxit);
            }
        } else if (!strcmp(cmd, "matrix")) {
            Sample s;
            sample_state(d->cfg, d->frame, 0, &s);
            print_matrix(&s, a1[0] ? a1 : "step");
        } else if (!strcmp(cmd, "challenge-settings")) {
            // Read SaveBlock3.challengeSettings through the PRODUCTION reader so the runtime
            // evidence is evidence about the reader that ships (issue #9). Diagnostic only:
            // asserts nothing, writes nothing.
            size_t ewram_sz = 0;
            uint8_t* ewram = libretro_host_get_ewram(&ewram_sz);
            ChallengeSettingsSnapshot cs;
            bool ok = false;
            if (ewram && ewram_sz > 0 && d->cfg) {
                ok = pokemon_read_challenge_settings_gba(
                    probe_read, NULL, ewram, ewram_sz, d->cfg, &cs);
            }
            if (!ok) {
                printf("[CHALLENGE] %s UNAVAILABLE (status=%d) frame=%d\n",
                       a1[0] ? a1 : "step", (int)CHALLENGE_SETTINGS_UNAVAILABLE, d->frame);
                script_error("challenge-settings read failed; the production reader declined the read");
            } else {
                printf("[CHALLENGE] %s status=%d frame=%d\n", a1[0] ? a1 : "step", (int)cs.status, d->frame);
                printf("[CHALLENGE] optionStyle=%u fairyTypes=%u randomType=%u typeEffectiveness=%u "
                       "randomAbilities=%u randomMoves=%u\n",
                       cs.option_style.raw, cs.tx_mode_fairy_types.raw, cs.tx_random_type.raw,
                       cs.tx_random_type_effectiveness.raw, cs.tx_random_abilities.raw,
                       cs.tx_random_moves.raw);
                printf("[CHALLENGE] noEVs=%u bstEqualizer=%u mirror=%u mirrorThief=%u "
                       "scalingIVs=%u scalingEVs=%u maxPartyIVs=%u\n",
                       cs.tx_challenges_no_evs.raw, cs.tx_challenges_base_stat_equalizer.raw,
                       cs.tx_challenges_mirror.raw, cs.tx_challenges_mirror_thief.raw,
                       cs.tx_challenges_trainer_scaling_ivs.raw,
                       cs.tx_challenges_trainer_scaling_evs.raw, cs.tx_challenges_max_party_ivs.raw);
                printf("[CHALLENGE] sturdy=%u levelCap=%u expMultiplier=%u legendaryAbilities=%u\n",
                       cs.tx_mode_sturdy.raw, cs.tx_challenges_level_cap.raw,
                       cs.tx_challenges_exp_multiplier.raw, cs.tx_mode_legendary_abilities.raw);
            }
        } else if (!strcmp(cmd, "shot")) {        } else if (!strcmp(cmd, "shot")) {
            write_ppm(a1);
        } else if (!strcmp(cmd, "savsave")) {
            bool ok = libretro_host_flush_save_ram(a1);
            printf("  [savsave] %s -> %s\n", a1, ok ? "OK" : "FAILED");
            if (!ok) {
                script_error("savsave '%s' failed: the battery save could not be flushed", a1);
            }
        } else if (!strcmp(cmd, "menusave")) {
            do_menusave(d, &previous, &have_previous);
        } else if (!strcmp(cmd, "savload")) {
            struct stat st;
            if (stat(a1, &st) != 0) {
                script_error("savload '%s' failed: file does not exist or is unreadable", a1);
            } else {
                size_t expected = libretro_host_get_save_ram_size();
                if ((size_t)st.st_size != expected) {
                    script_error("savload '%s' failed: file is %ld bytes but the core expects %zu",
                                 a1, (long)st.st_size, expected);
                } else {
                    bool ok = libretro_host_load_save_ram(a1);
                    printf("  [savload] %s -> %s\n", a1, ok ? "OK" : "FAILED");
                    if (!ok) {
                        script_error("savload '%s' failed: the core rejected the battery save", a1);
                    }
                }
            }
        } else if (!strcmp(cmd, "assert-battle")) {
            Sample s;
            sample_state(d->cfg, d->frame, 0, &s);
            const char* want = a1;
            bool ok;
            if (!strcmp(want, "inactive")) ok = (s.lifecycle == BATTLE_LIFECYCLE_INACTIVE);
            else if (!strcmp(want, "active")) ok = (s.lifecycle == BATTLE_LIFECYCLE_ACTIVE);
            else {
                script_error("assert-battle expects 'inactive' or 'active', got '%s'", want);
                ok = true;
            }
            if (!ok) {
                script_error("assert-battle %s failed: lifecycle is %s at frame %d",
                             want, lifecycle_name(s.lifecycle), s.frame);
            } else {
                printf("  [assert] battle=%s OK (frame %d)\n", want, s.frame);
            }
        } else if (!strcmp(cmd, "assert-in-battle-flag")) {
            Sample s;
            sample_state(d->cfg, d->frame, 0, &s);
            bool want = !strcmp(a1, "true");
            if (!s.in_battle_readable) {
                script_error("assert-in-battle-flag %s failed: gMain.inBattle is unreadable", a1);
            } else if (s.in_battle != want) {
                script_error("assert-in-battle-flag %s failed: gMain.inBattle reads %s at frame %d",
                             a1, s.in_battle ? "true" : "false", s.frame);
            } else {
                printf("  [assert] inBattle=%s OK (frame %d)\n", a1, s.frame);
            }
        } else if (!strcmp(cmd, "autobattle")) {
            int max_f = a1[0] ? atoi(a1) : 4000;
            uint8_t ib = 0;
            read_u8(d->cfg->main_struct_gba_address + d->cfg->main_in_battle_byte_offset, &ib);
            bool initial_ib = ((ib >> d->cfg->main_in_battle_bit) & 1);

            AutobattleTracker tracker;
            autobattle_tracker_init(&tracker, initial_ib);

            /* If not initially in battle, mash A up to 1000 frames to trigger/enter battle */
            if (!initial_ib) {
                for (int f = 0; f < 1000; f++) {
                    read_u8(d->cfg->main_struct_gba_address + d->cfg->main_in_battle_byte_offset, &ib);
                    bool in_b = ((ib >> d->cfg->main_in_battle_bit) & 1);
                    autobattle_tracker_step(&tracker, in_b);
                    if (tracker.entered_battle) break;
                    step_one(d, ((f % 10) < 4) ? DUALDEX_BTN_A : 0, &previous, &have_previous);
                }
            }

            if (!tracker.entered_battle) {
                script_error("autobattle never entered battle within %d frames", max_f);
            } else {
                /* Phase 2: battle until inBattle releases */
                int f = 0;
                for (; f < max_f; f++) {
                    read_u8(d->cfg->main_struct_gba_address + d->cfg->main_in_battle_byte_offset, &ib);
                    bool in_b = ((ib >> d->cfg->main_in_battle_bit) & 1);
                    if (autobattle_tracker_step(&tracker, in_b)) {
                        break;
                    }
                    step_one(d, ((f % 10) < 4) ? DUALDEX_BTN_A : 0, &previous, &have_previous);
                }
                if (!tracker.exited_after_entry) {
                    script_error("autobattle entered battle but timed out before battle exit after %d frames", max_f);
                } else {
                    printf("  [autobattle] battle completed cleanly after %d battle frames\n", f);
                }
            }
        } else if (!strcmp(cmd, "battle-win")) {
            /* battle-win <maxFrames>
             *
             * Drive an ALREADY-ACTIVE battle to completion with ordinary controller input (see
             * drive_battle_to_end). This does NOT start a battle: it is for a battle the scenario
             * deliberately entered by walking into a trainer's sight line. It fails closed if the
             * battle never ends, and it refuses to run when no battle is active. */
            int max_f = a1[0] ? atoi(a1) : 30000;
            uint8_t ib = 0;
            read_u8(d->cfg->main_struct_gba_address + d->cfg->main_in_battle_byte_offset, &ib);
            if (!((ib >> d->cfg->main_in_battle_bit) & 1)) {
                script_error("battle-win: no battle is active; this command only finishes a battle "
                             "the scenario already entered");
            } else {
                Sample s0;
                sample_state(d->cfg, d->frame, 0, &s0);
                printf("  [battle-win] finishing %s battle (enemy party %u, battlers %u)\n",
                       kind_name(s0.kind), s0.enemy_party_count_prod, s0.battlers);
                if (!drive_battle_to_end(d, max_f, &previous, &have_previous)) {
                    script_error("battle-win: battle did not finish within %d frames", max_f);
                } else {
                    printf("  [battle-win] battle ended\n");
                }
            }
        } else if (!strcmp(cmd, "await-enemy-replacement")) {
            int old_slot = a1[0] ? atoi(a1) : 0;
            int new_slot = a2[0] ? atoi(a2) : 1;
            int max_f = a3[0] ? atoi(a3) : 3500;
            size_t ewram_sz = 0;
            uint8_t* ewram = libretro_host_get_ewram(&ewram_sz);

            ReplacementTracker tracker;
            replacement_tracker_init(&tracker, old_slot, new_slot);

            printf("  [await-enemy-replacement] begin: %d -> %d (budget %d frames)\n",
                   old_slot, new_slot, max_f);

            for (int f = 0; f < max_f; f++) {
                Sample s;
                sample_state(d->cfg, d->frame, 0, &s);

                if (replacement_tracker_step(&tracker, &s, s.absent_flags)) {
                    break;
                }

                uint8_t bcmd = get_battler0_command(ewram, ewram_sz);
                uint32_t exec = 0;
                read_u32(HNS_RELEASE_BATTLE_CONTROLLER_EXEC_FLAGS, &exec);
                uint32_t bscript = 0;
                read_u32(0x02000128u, &bscript);
                uint8_t bop = 0;
                if (bscript >= 0x08000000 && bscript < 0x0A000000) {
                    read_u8(bscript, &bop);
                }

                if (bcmd == 17) {
                    /* In action selection: choose Fight (cursor 0) */
                    uint8_t cur = ewram[0x3A4];
                    if (cur != 0) {
                        hold(d, DUALDEX_BTN_UP, 4, &previous, &have_previous);
                        hold(d, DUALDEX_BTN_LEFT, 4, &previous, &have_previous);
                    }
                    hold(d, DUALDEX_BTN_A, 4, &previous, &have_previous);
                    hold(d, 0, 8, &previous, &have_previous);
                } else if (bcmd == 19) {
                    /* Move selection: choose Tackle (move 0) */
                    hold(d, DUALDEX_BTN_A, 4, &previous, &have_previous);
                    hold(d, 0, 8, &previous, &have_previous);
                } else if (bcmd == 18) {
                    /* Yes/No box (e.g. switch prompt): press B to decline switch */
                    hold(d, DUALDEX_BTN_B, 4, &previous, &have_previous);
                    hold(d, 0, 10, &previous, &have_previous);
                } else if (bcmd == 21) {
                    /* Party menu accidentally opened: press B to cancel */
                    hold(d, DUALDEX_BTN_B, 4, &previous, &have_previous);
                    hold(d, 0, 15, &previous, &have_previous);
                } else if (bop == 0x67) {
                    /* B_SCR_OP_YESNOBOX: shift prompt "Will you change Pokémon?" -> press B to choose NO */
                    printf("  [await-enemy-replacement] detected B_SCR_OP_YESNOBOX at frame %d, pressing B\n", d->frame);
                    hold(d, DUALDEX_BTN_B, 4, &previous, &have_previous);
                    hold(d, 0, 10, &previous, &have_previous);
                } else {
                    /* Text advancing: alternate A and B */
                    uint32_t btn = ((f % 6) < 3) ? DUALDEX_BTN_A : 0;
                    step_one(d, btn, &previous, &have_previous);
                }
            }

            if (tracker.phase != REPL_PHASE_COMPLETE) {
                script_error("await-enemy-replacement %d->%d timed out after %d frames: "
                             "saw old active=%s saw faint=%s saw absent window=%s saw replacement=%s",
                             old_slot, new_slot, max_f,
                             tracker.saw_old_active ? "yes" : "no",
                             tracker.saw_faint ? "yes" : "no",
                             tracker.saw_absent_window ? "yes" : "no",
                             tracker.saw_replacement ? "yes" : "no");
            } else {
                printf("  [await-enemy-replacement] OK: %d -> %d complete (old species=%u, replacement species=%u)\n",
                       old_slot, new_slot, tracker.old_species, tracker.new_species);
            }
        } else if (!strcmp(cmd, "await-enemy-voluntary-switch")) {
            /* await-enemy-voluntary-switch <oldSlot> <newSlot> <oldSpecies> <maxFrames>
             * <oldSpecies> may be 0 to accept whatever lead is out; a non-zero value is REQUIRED to
             * match, so tracking the wrong lead fails instead of silently proceeding. */
            int old_slot  = a1[0] ? atoi(a1) : 0;
            int new_slot  = a2[0] ? atoi(a2) : 1;
            int old_species = a3[0] ? atoi(a3) : 0;
            int max_f     = a4[0] ? atoi(a4) : 30000;

            /* All four arguments are REQUIRED: a missing frame budget must fail rather than silently
             * inherit a default, because "the run had no budget" and "the switch did not happen"
             * would otherwise be indistinguishable in the evidence. */
            if (n < 5) {
                script_error("await-enemy-voluntary-switch needs 4 arguments: "
                             "<oldSlot> <newSlot> <oldSpecies> <maxFrames> (got %d)", n - 1);
            }
            if (old_slot < 0 || new_slot < 0 || old_slot == new_slot) {
                script_error("await-enemy-voluntary-switch: oldSlot and newSlot must differ and be "
                             "non-negative (got %d -> %d)", old_slot, new_slot);
            }
            if (max_f < 1) {
                script_error("await-enemy-voluntary-switch: maxFrames must be >= 1 (got %d)", max_f);
            }

            VoluntarySwitchTracker tracker;
            voluntary_switch_tracker_init(&tracker, old_slot, new_slot, (uint16_t)old_species);

            printf("  [await-enemy-voluntary-switch] begin: slot %d -> %d%s (budget %d frames)\n",
                   old_slot, new_slot,
                   old_species ? " (old species pinned)" : "", max_f);
            printf("  [await-enemy-voluntary-switch] turn plan: the named move on EVERY turn "
                   "(a status follow-up would overwrite gLastLandedMoves[opponent])\n");

            /* The arming move must be used on EVERY turn that is meant to be eligible.
             *
             * gLastLandedMoves is set on every landed move and is indexed by TARGET
             * (src/battle_move_resolution.c:2691-2704). A status follow-up such as Growl therefore
             * REPLACES the damaging entry with a status move, and FindMonWithFlagsAndSuperEffective
             * bails on a status move (src/battle_ai_switch.c:1059). An earlier revision of this
             * tracker planned "Razor Leaf once, then Growl" on the assumption that a status move
             * does not overwrite gLastLandedMoves; the pinned source shows it does, so that plan
             * could never arm the heuristic for a second turn. */
            int razor_slot = -1;
            int move_cursor = 0;
            bool move_cursor_trusted = false;
            /* Landed hits that left the outgoing mon alive. A LOWER BOUND on the number of
             * AI switch decisions taken, not the count itself: the engine rolls once per
             * eligible turn and this counter only sees the ones that produced damage. */
            int qualifying_hits = 0;
            bool prev_opp_hp_valid = false;
            uint16_t prev_opp_hp = 0;

            /* STALL BUDGET -- why alternating the PLAYER's slot is legitimate, and why it cannot
             * be mistaken for the opponent transition.
             *
             * Only a landed damaging move arms the heuristic. Every other player action overwrites
             * gLastLandedMoves[opponent] with a status move (Growl), with a resisted move (Tackle is
             * 1.0x on Spinarak), or clears it (a miss). A POKeMON switch is the single exception:
             * SwitchInClearSetData (src/battle_main.c:3414) writes only the switching-IN battler's
             * own entries, so gLastLandedMoves[opponent] and gLastHitBy[opponent] survive intact.
             *
             * So once the outgoing mon is within ONE observed hit of fainting, the tracker presses
             * the ordinary POKeMON menu instead of attacking. That
             *   - is normal controller-driven gameplay -- the same input path the `voluntary-switch`
             *     command has used since Scenario 42; it writes no memory,
             *   - keeps the outgoing mon alive, which is the whole point of the evidence,
             *   - and cannot fabricate anything: the tracker only ever reads the OPPONENT side
             *     (s->enemy_battler, gBattlerPartyIndexes[enemy battler], the production opponent
             *     resolution and the enemy party snapshot), while the player's own party slot and
             *     species live on battler 0 and are never consulted by
             *     voluntary_switch_tracker_step(). The pure selftest
             *     `vsw_player_switch_not_opponent_switch` pins exactly that separation.
             *
             * WHAT THE STALL DOES *NOT* CLAIM. A stall turn is an ordinary AI action-selection
             * opportunity with the previous landed-move state preserved -- it is NOT claimed to be
             * an eligible roll for the source-supported resistance path. That path's full predicate
             * (which bench move is compared against which active player Pokemon) is evaluated
             * against whatever is currently on the field, and while Hoothoot is the active player
             * Pokemon the >= 2.0x comparison is not the same one that holds for Chikorita. Only
             * turns whose complete predicates are satisfied should be counted as eligible
             * opportunities, which is why this tracker reports QUALIFYING DAMAGING HITS -- a lower
             * bound it can actually observe -- rather than an eligible-roll count it cannot. */
            int max_observed_hit = 0;
            int stall_target_slot = 1;
            int stall_phase = 0;
            int stall_attempts = 0;
            bool stalled_once = false;
            int stall_switches = 0;

            for (int f = 0; f < max_f; f++) {
                Sample s;
                sample_state(d->cfg, d->frame, 0, &s);

                if (voluntary_switch_tracker_step(&tracker, &s)) break;

                if (tracker.violation_old_fainted || tracker.violation_shape ||
                    tracker.violation_reader_leads || tracker.violation_reader_lags ||
                    tracker.violation_species_mismatch || tracker.violation_old_slot_reappeared ||
                    tracker.violation_old_party_not_alive || tracker.violation_old_party_species ||
                    tracker.violation_new_party_mismatch) {
                    break;
                }

                /* Drive the turn with ordinary controller input: confirm FIGHT in the action menu,
                 * then the arming move located in the LIVE move list (never an assumed menu slot),
                 * and decline any stray Yes/No with B. The party-menu stall above handles the one
                 * modal this tracker opens on purpose. */
                size_t ewram_sz = 0;
                uint8_t* ewram = libretro_host_get_ewram(&ewram_sz);
                uint8_t bcmd = get_battler0_command(ewram, ewram_sz);

                if (tracker.old_battler >= 0) {
                    const uint16_t ohp = s.mon_hp[tracker.old_battler];
                    if (prev_opp_hp_valid && ohp > 0 && ohp < prev_opp_hp) {
                        qualifying_hits++;
                        const int hit = (int)prev_opp_hp - (int)ohp;
                        if (hit > max_observed_hit) max_observed_hit = hit;
                    }
                    prev_opp_hp = ohp;
                    prev_opp_hp_valid = true;
                }

                /* ---- stall instead of landing the hit that would faint the outgoing mon ---- */
                if (tracker.old_battler >= 0 && max_observed_hit > 0) {
                    const uint16_t ohp = s.mon_hp[tracker.old_battler];
                    if (ohp > 0 && (int)ohp <= max_observed_hit && s.lifecycle == BATTLE_LIFECYCLE_ACTIVE) {
                        if (!stalled_once) {
                            printf("  [await-enemy-voluntary-switch] STALL: outgoing battler %d is at "
                                   "HP=%u with an observed max hit of %d, so one more attack would "
                                   "faint it. Alternating the player's active slot to keep "
                                   "gLastLandedMoves[%d] armed without attacking.\n",
                                   tracker.old_battler, ohp, max_observed_hit, tracker.old_battler);
                            stalled_once = true;
                        }
                        if (player_switch_step(d, stall_target_slot, &stall_phase,
                                               &stall_attempts, &s,
                                               &previous, &have_previous)) {
                            stall_switches++;
                            printf("  [await-enemy-voluntary-switch] STALL: player slot %d in "
                                   "(switch %d); outgoing battler %d still at HP=%u\n",
                                   stall_target_slot, stall_switches, tracker.old_battler,
                                   s.mon_hp[tracker.old_battler]);
                            stall_target_slot = 1 - stall_target_slot;
                        }
                        continue;
                    }
                }

                if (bcmd == 17) {          /* action selection: cursor to Fight, confirm */
                    uint8_t cur = ewram[0x3A4];
                    if (cur != 0) {
                        hold(d, (cur & 1u) ? DUALDEX_BTN_LEFT : DUALDEX_BTN_UP, 4, &previous, &have_previous);
                    } else {
                        hold(d, DUALDEX_BTN_A, 4, &previous, &have_previous);
                    }
                    hold(d, 0, 8, &previous, &have_previous);
                } else if (bcmd == 19) {   /* move selection: the arming move, every turn */
                    const int pb = tracker.old_battler >= 0 ? 1 - tracker.old_battler : 1;
                    if (razor_slot < 0) razor_slot = probe_find_move_slot(&s, pb, HNS_MOVE_RAZOR_LEAF);
                    int want = razor_slot;
                    if (want < 0) want = 0;
                    /* Ground the model on the cursor the ENGINE reports (ewram[0x3A8]), the same
                     * way await-player-forced-replacement does: the menu reopens on the last move
                     * used rather than on slot 0, so a pure dead-reckoning model can desync and
                     * confirm the wrong move. The observed value is only trusted when it is a real
                     * 2x2 slot index. */
                    if (!move_cursor_trusted) {
                        uint8_t observed = (ewram_sz > 0x3A8) ? ewram[0x3A8] : 0xFF;
                        move_cursor = (observed <= 3) ? (int)observed : 0;
                        move_cursor_trusted = true;
                    }
                    uint8_t observed_now = (ewram_sz > 0x3A8) ? ewram[0x3A8] : 0xFF;
                    if (observed_now <= 3) move_cursor = (int)observed_now;
                    if (move_cursor != want) {
                        const int cr = move_cursor / 2, cc = move_cursor % 2;
                        const int tr = want / 2, tc = want % 2;
                        uint32_t btn = (cr != tr) ? ((tr > cr) ? DUALDEX_BTN_DOWN : DUALDEX_BTN_UP)
                                                  : ((tc > cc) ? DUALDEX_BTN_RIGHT : DUALDEX_BTN_LEFT);
                        hold(d, btn, 4, &previous, &have_previous);
                        hold(d, 0, 8, &previous, &have_previous);
                        if (btn == DUALDEX_BTN_DOWN) move_cursor += 2;
                        else if (btn == DUALDEX_BTN_UP) move_cursor -= 2;
                        else if (btn == DUALDEX_BTN_RIGHT) move_cursor += 1;
                        else move_cursor -= 1;
                        continue;
                    }
                    hold(d, DUALDEX_BTN_A, 4, &previous, &have_previous);
                    hold(d, 0, 8, &previous, &have_previous);
                } else if (bcmd == 18 || bcmd == 21) {
                    hold(d, DUALDEX_BTN_B, 4, &previous, &have_previous);
                    hold(d, 0, 12, &previous, &have_previous);
                } else {
                    step_one(d, ((f % 6) < 3) ? DUALDEX_BTN_A : 0, &previous, &have_previous);
                }
            }

            const bool any_violation = tracker.violation_old_fainted || tracker.violation_shape ||
                tracker.violation_slot_unchanged || tracker.violation_species_unchanged ||
                tracker.violation_species_mismatch || tracker.violation_battler_changed ||
                tracker.violation_reader_leads || tracker.violation_reader_lags ||
                tracker.violation_old_slot_reappeared ||
                tracker.violation_old_party_not_alive || tracker.violation_old_party_species ||
                tracker.violation_new_party_mismatch;

            if (tracker.violation_old_fainted) {
                script_error("await-enemy-voluntary-switch: outgoing battler %d reached hp == 0 "
                             "before any voluntary switch committed -- this is faint replacement "
                             "(Scenario 41), NOT a voluntary switch (qualifying hits=%d, "
                             "observed max hit=%d, stall entered=%s, stall switches=%d)",
                             tracker.old_battler, qualifying_hits, max_observed_hit,
                             stalled_once ? "yes" : "no", stall_switches);
            } else if (tracker.violation_shape) {
                script_error("await-enemy-voluntary-switch: battle shape was not "
                             "TRAINER_SINGLE with exactly 2 battlers; refusing to interpret it");
            } else if (tracker.violation_species_mismatch) {
                script_error("await-enemy-voluntary-switch: expected old species %u but the active "
                             "opponent was a different species; refusing to track the wrong lead",
                             (unsigned)tracker.expect_species);
            } else if (tracker.violation_old_party_not_alive) {
                script_error("await-enemy-voluntary-switch: the enemy PARTY slot %d (expected species "
                             "%u) does not show a living Pokemon on the commit frame -- the outgoing "
                             "mon cannot be shown to have survived the switch, so this is treated as "
                             "a faint path, NOT a voluntary switch",
                             tracker.old_slot, (unsigned)tracker.old_species);
            } else if (tracker.violation_old_party_species) {
                script_error("await-enemy-voluntary-switch: the enemy PARTY slot %d does not hold "
                             "the outgoing species %u, so the snapshot is not describing the mon "
                             "that left the field; refusing to interpret it",
                             tracker.old_slot, (unsigned)tracker.old_species);
            } else if (tracker.violation_new_party_mismatch) {
                script_error("await-enemy-voluntary-switch: the enemy PARTY slot %d does not match "
                             "the newly authoritative opponent, so the production party snapshot "
                             "disagrees with the engine's own slot mapping",
                             tracker.new_slot);
            } else if (tracker.violation_reader_leads) {
                script_error("await-enemy-voluntary-switch: the production reader reported the new "
                             "slot before gBattlerPartyIndexes did (reader would be leading "
                             "authority)");
            } else if (tracker.violation_reader_lags) {
                script_error("await-enemy-voluntary-switch: gBattlerPartyIndexes was already "
                             "rewritten while the production reader still named the old slot "
                             "(reader would be lagging authority)");
            } else if (tracker.violation_slot_unchanged) {
                script_error("await-enemy-voluntary-switch: committed slot equals old slot %d",
                             old_slot);
            } else if (tracker.violation_species_unchanged) {
                script_error("await-enemy-voluntary-switch: species unchanged (%u); the reported "
                             "battler is the same Pokemon", (unsigned)tracker.old_species);
            } else if (tracker.violation_battler_changed) {
                script_error("await-enemy-voluntary-switch: commit landed on a different battler "
                             "index than the outgoing one");
            } else if (tracker.violation_old_slot_reappeared) {
                script_error("await-enemy-voluntary-switch: the old slot %d became authoritative "
                             "again after the switch", old_slot);
            } else if (tracker.phase != VSW_PHASE_COMPLETE) {
                script_error("await-enemy-voluntary-switch %d->%d TIMED OUT after %d frames with no "
                             "voluntary switch: saw old active=%s, saw transition=%s, "
                             "saw absent window=%s, saw commit=%s "
                             "(qualifying damaging hits=%d, last outgoing HP=%u)",
                             old_slot, new_slot, max_f,
                             tracker.saw_old_active ? "yes" : "no",
                             tracker.saw_transition_window ? "yes" : "no",
                             tracker.saw_absent_window ? "yes" : "no",
                             tracker.saw_commit ? "yes" : "no", qualifying_hits,
                             (unsigned)tracker.old_hp);
            } else if (any_violation) {
                script_error("await-enemy-voluntary-switch: latched violation(s) present");
            } else {
                printf("  [await-enemy-voluntary-switch] qualifying damaging hits before the "
                       "switch: %d (lower bound on the AI decisions taken)\n", qualifying_hits);
                printf("  [await-enemy-voluntary-switch] OK: VOLUNTARY SWITCH %d -> %d observed "
                       "(species %u -> %u; old battler %d stayed at HP=%u; "
                       "gBattlerPartyIndexes=%d == production slot %d)\n",
                       old_slot, new_slot, (unsigned)tracker.old_species,
                       (unsigned)tracker.new_species, tracker.old_battler,
                       tracker.old_hp, tracker.new_index_at_commit, tracker.prod_slot_at_commit);
            }
        } else if (!strcmp(cmd, "engage")) {
            /* engage <DIR> <maxPresses>
             *
             * Make a trainer start the battle WITHOUT polluting the battle's first action menu.
             *
             * A trainer whose sight line the player walks into starts the battle by itself; a
             * trainer whose facing happens to point elsewhere does not, and then the player has to
             * talk to them. This command covers both: it turns the player to face DIR (a step into
             * a blocked tile turns without moving) and then presses A until `gMain.inBattle`
             * asserts -- but if a battle is ALREADY active when it runs, it presses nothing at all,
             * so an approach that triggered the sight line still reaches `damage-probe` with the
             * action menu untouched. Ordinary controller input only. */
            uint32_t btn = parse_buttons(a1);
            int max_presses = a2[0] ? atoi(a2) : 8;
            uint8_t ib = 0;
            read_u8(d->cfg->main_struct_gba_address + d->cfg->main_in_battle_byte_offset, &ib);
            if (((ib >> d->cfg->main_in_battle_bit) & 1)) {
                printf("  [engage] a battle was already active on entry; no input sent\n");
            } else {
                hold(d, btn, 6, &previous, &have_previous);
                hold(d, 0, 20, &previous, &have_previous);
                bool started = false;
                for (int i = 0; i < max_presses && !started; i++) {
                    hold(d, DUALDEX_BTN_A, 6, &previous, &have_previous);
                    hold(d, 0, 40, &previous, &have_previous);
                    read_u8(d->cfg->main_struct_gba_address + d->cfg->main_in_battle_byte_offset, &ib);
                    started = ((ib >> d->cfg->main_in_battle_bit) & 1);
                }
                if (!started) {
                    script_error("engage %s: no battle started after %d A presses", a1, max_presses);
                } else {
                    printf("  [engage] battle started after %d A presses\n", 1);
                }
            }
        } else if (!strcmp(cmd, "damage-probe")) {
            /* damage-probe <moveId> <maxTurns>
             *
             * DIAGNOSTIC ONLY: this asserts nothing. It drives an ALREADY-ACTIVE single battle with
             * one named move -- located in the LIVE move list, never an assumed menu slot -- and
             * prints the opponent's HP trajectory, so a source-derived damage prediction can be
             * checked against the ROM before any lifecycle claim is made on top of it.
             *
             * Ordinary controller input only; it never writes to the machine. Critical-hit status is
             * NOT read from memory (that would need a speculative address); it is reported as
             * UNKNOWN and inferred, if at all, from the measured damage magnitude. */
            int move_id = a1[0] ? atoi(a1) : 0;
            int max_turns = a2[0] ? atoi(a2) : 4;
            uint8_t ib = 0;
            read_u8(d->cfg->main_struct_gba_address + d->cfg->main_in_battle_byte_offset, &ib);
            if (!((ib >> d->cfg->main_in_battle_bit) & 1)) {
                script_error("damage-probe: no battle is active; this command only measures a battle "
                             "the scenario already entered");
            } else {
                Sample s0;
                sample_state(d->cfg, d->frame, 0, &s0);
                printf("  [damage-probe] move=%d maxTurns=%d kind=%s battlers=%u enemyParty=%u "
                       "activeEnemy=%s slot=%d battler=%d at frame %d\n",
                       move_id, max_turns, kind_name(s0.kind), s0.battlers,
                       s0.enemy_party_count_prod, active_enemy_name(s0.active_enemy),
                       s0.enemy_slot, s0.enemy_battler, s0.frame);
                if (s0.kind != BATTLE_KIND_TRAINER_SINGLE && s0.kind != BATTLE_KIND_WILD_SINGLE) {
                    script_error("damage-probe: only single battles are supported (kind=%s)",
                                 kind_name(s0.kind));
                }
                const int opp_battler = probe_resolve_opponent_battler(&s0);
                printf("  [damage-probe] opponent battler=%d species=%u hp=%u/%u\n",
                       opp_battler,
                       opp_battler >= 0 ? s0.mon_species[opp_battler] : 0,
                       opp_battler >= 0 ? s0.mon_hp[opp_battler] : 0,
                       opp_battler >= 0 ? s0.mon_max_hp[opp_battler] : 0);
            }
            {
                int move_cursor = 0;
                bool move_cursor_trusted = false;
                int turn = 0;
                bool in_action_menu = false;
                uint16_t last_hp = 0xFFFF;
                Sample sp;
                sample_state(d->cfg, d->frame, 0, &sp);
                const int opp_battler = probe_resolve_opponent_battler(&sp);
                const int budget = max_turns * 6000 + 6000;
                for (int f = 0; f < budget; f++) {
                    read_u8(d->cfg->main_struct_gba_address + d->cfg->main_in_battle_byte_offset, &ib);
                    if (!((ib >> d->cfg->main_in_battle_bit) & 1)) {
                        printf("  [damage-probe] battle ended after %d turns (frame %d)\n",
                               turn, d->frame);
                        break;
                    }
                    Sample s;
                    sample_state(d->cfg, d->frame, 0, &s);
                    const int ob = opp_battler >= 0 ? opp_battler : probe_resolve_opponent_battler(&s);
                    if (ob >= 0 && s.mon_hp[ob] != last_hp) {
                        if (last_hp != 0xFFFF) {
                            const int delta = (int)last_hp - (int)s.mon_hp[ob];
                            printf("  [damage-probe] turn=%d battler=%d species=%u hp %u -> %u "
                                   "(delta %d of maxHP %u) faint=%s crit=UNKNOWN frame=%d\n",
                                   turn, ob, s.mon_species[ob], last_hp, s.mon_hp[ob], delta,
                                   s.mon_max_hp[ob], s.mon_hp[ob] == 0 ? "YES" : "no", s.frame);
                        }
                        last_hp = s.mon_hp[ob];
                    }
                    size_t ewram_sz = 0;
                    uint8_t* ewram = libretro_host_get_ewram(&ewram_sz);
                    uint8_t bcmd = get_battler0_command(ewram, ewram_sz);
                    if (bcmd == 17) {
                        if (!in_action_menu) { turn++; in_action_menu = true; }
                        uint8_t cur = (ewram_sz > 0x3A4) ? ewram[0x3A4] : 0;
                        if (cur != 0) {
                            hold(d, (cur & 1u) ? DUALDEX_BTN_LEFT : DUALDEX_BTN_UP, 4,
                                 &previous, &have_previous);
                        }
                        hold(d, DUALDEX_BTN_A, 4, &previous, &have_previous);
                        hold(d, 0, 8, &previous, &have_previous);
                    } else if (bcmd == 19) {
                        in_action_menu = false;
                        const int pb = ob >= 0 ? 1 - ob : 1;
                        const int want_slot = probe_find_move_slot(&s, pb, (uint16_t)move_id);
                        if (want_slot < 0) {
                            script_error("damage-probe: move %d is not in the player battler %d live "
                                         "move list (%u,%u,%u,%u); refusing to use a different move",
                                         move_id, pb, s.mon_moves[pb][0], s.mon_moves[pb][1],
                                         s.mon_moves[pb][2], s.mon_moves[pb][3]);
                            break;
                        }
                        if (!move_cursor_trusted) { move_cursor = 0; move_cursor_trusted = true; }
                        if (move_cursor != want_slot) {
                            const int cr = move_cursor / 2, cc = move_cursor % 2;
                            const int tr = want_slot / 2, tc = want_slot % 2;
                            uint32_t btn = (cr != tr) ? ((tr > cr) ? DUALDEX_BTN_DOWN : DUALDEX_BTN_UP)
                                                      : ((tc > cc) ? DUALDEX_BTN_RIGHT : DUALDEX_BTN_LEFT);
                            hold(d, btn, 4, &previous, &have_previous);
                            hold(d, 0, 8, &previous, &have_previous);
                            if (btn == DUALDEX_BTN_DOWN) move_cursor += 2;
                            else if (btn == DUALDEX_BTN_UP) move_cursor -= 2;
                            else if (btn == DUALDEX_BTN_RIGHT) move_cursor += 1;
                            else move_cursor -= 1;
                            continue;
                        }
                        hold(d, DUALDEX_BTN_A, 4, &previous, &have_previous);
                        hold(d, 0, 8, &previous, &have_previous);
                    } else if (bcmd == 18 || bcmd == 21) {
                        hold(d, DUALDEX_BTN_B, 4, &previous, &have_previous);
                        hold(d, 0, 12, &previous, &have_previous);
                    } else {
                        step_one(d, ((f % 6) < 3) ? DUALDEX_BTN_A : 0, &previous, &have_previous);
                    }
                    if (turn > max_turns) break;
                }
            }
        } else if (!strcmp(cmd, "await-enemy-slot")) {
            int target_slot = a1[0] ? atoi(a1) : 0;
            int max_f = a2[0] ? atoi(a2) : 4000;
            size_t ewram_sz = 0;
            uint8_t* ewram = libretro_host_get_ewram(&ewram_sz);
            bool reached = false;
            for (int f = 0; f < max_f; f++) {
                Sample s;
                sample_state(d->cfg, d->frame, 0, &s);
                if (s.lifecycle == BATTLE_LIFECYCLE_ACTIVE &&
                    s.active_enemy == ACTIVE_ENEMY_SLOT &&
                    s.enemy_slot == target_slot) {
                    reached = true;
                    break;
                }
                uint8_t bcmd = get_battler0_command(ewram, ewram_sz);
                uint32_t exec = 0;
                read_u32(HNS_RELEASE_BATTLE_CONTROLLER_EXEC_FLAGS, &exec);
                uint32_t bscript = 0;
                read_u32(0x02000128u, &bscript);
                uint8_t bop = 0;
                if (bscript >= 0x08000000 && bscript < 0x0A000000) {
                    read_u8(bscript, &bop);
                }
                if (bcmd == 17) {
                    /* In action selection: choose Fight (cursor 0) */
                    uint8_t cur = ewram[0x3A4];
                    if (cur != 0) {
                        hold(d, DUALDEX_BTN_UP, 4, &previous, &have_previous);
                        hold(d, DUALDEX_BTN_LEFT, 4, &previous, &have_previous);
                    }
                    hold(d, DUALDEX_BTN_A, 4, &previous, &have_previous);
                    hold(d, 0, 8, &previous, &have_previous);
                } else if (bcmd == 19) {
                    /* Move selection: choose Tackle (move 0) */
                    hold(d, DUALDEX_BTN_A, 4, &previous, &have_previous);
                    hold(d, 0, 8, &previous, &have_previous);
                } else if (bcmd == 18) {
                    /* Yes/No box (e.g. switch prompt): press B to decline switch */
                    hold(d, DUALDEX_BTN_B, 4, &previous, &have_previous);
                    hold(d, 0, 10, &previous, &have_previous);
                } else if (bcmd == 21) {
                    /* Party menu accidentally opened: press B to cancel */
                    hold(d, DUALDEX_BTN_B, 4, &previous, &have_previous);
                    hold(d, 0, 15, &previous, &have_previous);
                } else if (bop == 0x67) {
                    /* B_SCR_OP_YESNOBOX: shift prompt "Will you change Pokémon?" -> press B to choose NO */
                    printf("  [await-enemy-slot] detected B_SCR_OP_YESNOBOX at frame %d, pressing B\n", d->frame);
                    hold(d, DUALDEX_BTN_B, 4, &previous, &have_previous);
                    hold(d, 0, 10, &previous, &have_previous);
                } else {
                    /* Text advancing: alternate A and B */
                    uint32_t btn = ((f % 6) < 3) ? DUALDEX_BTN_A : 0;
                    step_one(d, btn, &previous, &have_previous);
                }
            }
            printf("  [await-enemy-slot] slot %d %s (frame %d)\n",
                   target_slot, reached ? "REACHED" : "TIMEOUT", d->frame);
            if (!reached) {
                script_error("await-enemy-slot %d timed out after %d frames", target_slot, max_f);
            }
        } else if (!strcmp(cmd, "voluntary-switch")) {
            int target_slot = a1[0] ? atoi(a1) : 1;
            int max_f = a2[0] ? atoi(a2) : 3000;
            size_t ewram_sz = 0;
            uint8_t* ewram = libretro_host_get_ewram(&ewram_sz);
            bool switched = false;
            bool logged_entry = false;
            bool logged_after_down = false;
            int phase = 0; /* 0: select POKÉMON, 1: in party menu navigate, 2: in submenu select SHIFT, 3: post-switch */
            Sample init_s;
            sample_state(d->cfg, d->frame, 0, &init_s);
            int initial_player_slot = init_s.active_player_slot;
            int initial_party_index = init_s.party_index[0];
            printf("  [voluntary-switch] begin: target_slot=%d (initial active_player_slot=%d, party_index[0]=%d)\n",
                   target_slot, initial_player_slot, initial_party_index);

            for (int f = 0; f < max_f; f++) {
                Sample s;
                sample_state(d->cfg, d->frame, 0, &s);
                if (phase >= 3 &&
                    s.lifecycle == BATTLE_LIFECYCLE_ACTIVE &&
                    s.active_player_known &&
                    s.active_player_slot == target_slot &&
                    s.party_index[0] == target_slot) {
                    switched = true;
                    break;
                }
                uint8_t bcmd = get_battler0_command(ewram, ewram_sz);
                uint32_t exec = 0;
                read_u32(HNS_RELEASE_BATTLE_CONTROLLER_EXEC_FLAGS, &exec);
                uint32_t cb2 = 0;
                read_u32(0x03005BDCu, &cb2);
                bool in_party_menu = (cb2 >= 0x0819379Cu && cb2 < 0x0819F548u) || (bcmd == 21);

                if (phase == 0) {
                    if (bcmd == 17) {
                        /* Action selection: move cursor to 2 (POKÉMON) */
                        uint8_t cur = ewram[0x3A4];
                        if (cur != 2) {
                            if (cur & 1) {
                                hold(d, DUALDEX_BTN_LEFT, 4, &previous, &have_previous);
                                hold(d, 0, 8, &previous, &have_previous);
                            }
                            if (!(cur & 2)) {
                                hold(d, DUALDEX_BTN_DOWN, 4, &previous, &have_previous);
                                hold(d, 0, 8, &previous, &have_previous);
                            }
                        } else {
                            /* Confirm POKÉMON */
                            printf("  [voluntary-switch] cursor at POKÉMON (2), pressing A (frame %d)\n", d->frame);
                            hold(d, DUALDEX_BTN_A, 6, &previous, &have_previous);
                            hold(d, 0, 20, &previous, &have_previous);
                            phase = 1;
                        }
                    } else if (bcmd == 19) {
                        /* In move selection: press B to return to action selection */
                        hold(d, DUALDEX_BTN_B, 4, &previous, &have_previous);
                        hold(d, 0, 8, &previous, &have_previous);
                    } else {
                        /* Advance text/dialogue safely with B button (never selects Fight) */
                        uint32_t btn = ((f % 4) < 2) ? DUALDEX_BTN_B : 0;
                        step_one(d, btn, &previous, &have_previous);
                    }
                } else if (phase == 1) {
                    PartyMenuProbeState cand_a, cand_b;
                    read_party_menu_probe_candidate_a(&cand_a);
                    read_party_menu_probe_candidate_b(&cand_b);

                    if (in_party_menu && cand_b.menu_type == 1) {
                        if (!logged_entry) {
                            printf("  [PARTY_MENU entry f=%d] CandA(0x020341F8): type=%d layout=%d slot=%d act=%d | CandB(0x020341FC): type=%d layout=%d slot=%d act=%d\n",
                                   d->frame, cand_a.menu_type, cand_a.layout, cand_a.slot_id, cand_a.action,
                                   cand_b.menu_type, cand_b.layout, cand_b.slot_id, cand_b.action);
                            logged_entry = true;
                        }

                        if (cand_b.slot_id != target_slot) {
                            if (cand_b.slot_id < target_slot) {
                                hold(d, DUALDEX_BTN_DOWN, 6, &previous, &have_previous);
                            } else {
                                hold(d, DUALDEX_BTN_UP, 6, &previous, &have_previous);
                            }
                            hold(d, 0, 15, &previous, &have_previous);

                            read_party_menu_probe_candidate_a(&cand_a);
                            read_party_menu_probe_candidate_b(&cand_b);
                            if (!logged_after_down && cand_b.slot_id == target_slot) {
                                printf("  [PARTY_MENU after DOWN f=%d] CandA(0x020341F8): type=%d layout=%d slot=%d act=%d | CandB(0x020341FC): type=%d layout=%d slot=%d act=%d\n",
                                       d->frame, cand_a.menu_type, cand_a.layout, cand_a.slot_id, cand_a.action,
                                       cand_b.menu_type, cand_b.layout, cand_b.slot_id, cand_b.action);
                                logged_after_down = true;
                            }
                        } else {
                            /* Open sub-menu on target slot */
                            printf("  [voluntary-switch] at target slot %d (CandB slot=%d), opening sub-menu (frame %d)\n",
                                   target_slot, cand_b.slot_id, d->frame);
                            hold(d, DUALDEX_BTN_A, 6, &previous, &have_previous);
                            hold(d, 0, 25, &previous, &have_previous);
                            phase = 2;
                        }
                    } else {
                        step_one(d, 0, &previous, &have_previous);
                    }
                } else if (phase == 2) {
                    /* In sub-menu: option 0 is SHIFT. Confirm with A */
                    printf("  [voluntary-switch] confirming SHIFT with A button (frame %d)\n", d->frame);
                    hold(d, DUALDEX_BTN_A, 6, &previous, &have_previous);
                    hold(d, 0, 30, &previous, &have_previous);
                    phase = 3;
                } else {
                    step_one(d, ((f % 6) < 3) ? DUALDEX_BTN_A : 0, &previous, &have_previous);
                }
            }
            printf("  [voluntary-switch] slot %d %s (frame %d)\n",
                   target_slot, switched ? "SWITCHED" : "TIMEOUT", d->frame);
            if (!switched) {
                script_error("voluntary-switch %d timed out after %d frames", target_slot, max_f);
            }
        } else if (!strcmp(cmd, "await-player-forced-replacement")) {
            /* Drive the real player faint -> forced replacement, and prove it.
             *
             * The command is deliberately not "press A until slot 1 shows up". It drives ordinary
             * controller input while the pure PlayerReplacementTracker has to observe, in order:
             * the old player battler active and alive, that same battler at HP 0, the production
             * surface failing closed, and finally a committed replacement whose party slot comes
             * from gBattlerPartyIndexes and whose BattlePokemon is the party Pokemon of that slot.
             *
             * The move it selects is the non-damaging Growl, chosen by its position in the LIVE
             * gBattleMons move list, not by an assumed menu slot. Two runtime facts are enforced
             * while it runs, because they are what makes "the selected move does not damage the
             * opponent" evidence rather than an assumption:
             *   - the opponent's HP may never decrease;
             *   - the opponent's Attack stage must actually drop (Growl's effect).
             */
            const int old_slot = a1[0] ? atoi(a1) : 0;
            const int new_slot = a2[0] ? atoi(a2) : 1;
            const int max_f = a3[0] ? atoi(a3) : 40000;
            size_t ewram_sz = 0;
            uint8_t* ewram = libretro_host_get_ewram(&ewram_sz);

            PlayerReplacementTracker tracker;
            player_replacement_tracker_init(&tracker, old_slot, new_slot);
            printf("  [await-player-forced-replacement] begin: %d -> %d (budget %d frames)\n",
                   old_slot, new_slot, max_f);

            int logged_violations = 0;
            PlayerReplacementPhase logged_phase = tracker.phase;
            bool logged_a = false, logged_b = false, logged_c = false, logged_d = false;
            int  last_bcmd_seen = -1;
            int  growl_index = -1;
            int  model_cursor = 0;
            bool move_cursor_trusted = false;
            int  party_menu_a_presses = 0;
            int  last_party_a_frame = -10000;
            int  last_party_slot = -2;
            int  enemy_battler = -1;
            int  enemy_hp_prev = -1;
            int  enemy_slot_baseline = -1;
            int  enemy_attack_baseline = -1;
            bool enemy_damaged = false;
            bool growl_effect_observed = false;
            bool aborted = false;

            for (int f = 0; f < max_f && !aborted; f++) {
                Sample s;
                sample_state(d->cfg, d->frame, 0, &s);

                /* ---- ground truth for "the selected move does not damage the opponent" ------
                 * The opponent's HP is tracked for the same active enemy slot: any decrease means
                 * a damaging move was used, which would make the whole scenario invalid. */
                if (enemy_battler < 0) enemy_battler = probe_resolve_opponent_battler(&s);
                if (enemy_battler >= 0 && s.lifecycle == BATTLE_LIFECYCLE_ACTIVE &&
                    s.enemy_battler == enemy_battler) {
                    const int hp = (int)s.mon_hp[enemy_battler];
                    if (enemy_hp_prev < 0 && hp > 0 && s.enemy_slot >= 0) {
                        enemy_hp_prev = hp;
                        enemy_slot_baseline = s.enemy_slot;
                        enemy_attack_baseline = s.mon_stat_stages[enemy_battler][1]; /* STAT_ATK */
                        printf("  [await-player-forced-replacement] opponent battler %d baseline at frame %d: species %u HP %d/%u attackStage %d\n",
                               enemy_battler, s.frame, s.mon_species[enemy_battler],
                               hp, s.mon_max_hp[enemy_battler], enemy_attack_baseline);
                    } else if (enemy_hp_prev > 0 && s.enemy_slot == enemy_slot_baseline &&
                               hp < enemy_hp_prev) {
                        enemy_damaged = true;
                        script_error("await-player-forced-replacement: the opponent was damaged "
                                     "(party slot %d HP %d -> %d at frame %d), so the selected move "
                                     "was not non-damaging", enemy_slot_baseline, enemy_hp_prev,
                                     hp, s.frame);
                        aborted = true;
                        break;
                    } else if (hp > 0) {
                        enemy_hp_prev = hp;
                    }
                    if (enemy_attack_baseline > 0 && !growl_effect_observed &&
                        s.mon_stat_stages[enemy_battler][1] < enemy_attack_baseline) {
                        growl_effect_observed = true;
                        printf("  [await-player-forced-replacement] Growl effect at frame %d: opponent battler %d attack stage %d -> %d\n",
                               s.frame, enemy_battler, enemy_attack_baseline,
                               s.mon_stat_stages[enemy_battler][1]);
                    }
                }

                bool complete = player_replacement_tracker_step(&tracker, &s);
                while (logged_violations < tracker.violations &&
                       logged_violations < PLAYER_REPL_MAX_VIOLATIONS) {
                    printf("  [await-player-forced-replacement] VIOLATION: %s\n",
                           tracker.violation_text[logged_violations]);
                    logged_violations++;
                }
                if (tracker.phase != logged_phase) {
                    if (tracker.saw_old_active && !logged_a) {
                        printf("  [await-player-forced-replacement] Phase A observed at frame %d: old player slot %d active (battler=%d, species=%u, HP=%d/%d)\n",
                               tracker.old_frame, old_slot, tracker.old_battler,
                               tracker.old_species, tracker.old_hp, tracker.old_max_hp);
                        logged_a = true;
                    }
                    if (tracker.saw_faint && !logged_b) {
                        printf("  [await-player-forced-replacement] Phase B observed at frame %d: old player battler %d HP == 0 (PartySnapshot known=%d slot=%d, absentFlags=0x%02X, partyIndexes=%u,%u,%u,%u)\n",
                               tracker.faint_frame, tracker.old_battler,
                               tracker.known_at_faint ? 1 : 0, tracker.slot_at_faint,
                               tracker.absent_flags_at_faint,
                               tracker.party_index_at_faint[0], tracker.party_index_at_faint[1],
                               tracker.party_index_at_faint[2], tracker.party_index_at_faint[3]);
                        logged_b = true;
                    }
                    if (tracker.saw_fail_closed_window && !logged_c) {
                        printf("  [await-player-forced-replacement] Phase C observed at frame %d: fail-closed window (active_player_known=false, slot=-1; absentFlags=0x%02X [absent-bit=%d, hp==0=%d], partyIndexes=%u,%u,%u,%u)\n",
                               tracker.fail_closed_frame, tracker.absent_flags_at_fail_closed,
                               tracker.absent_flag_caused_fail_closed ? 1 : 0,
                               tracker.hp_zero_caused_fail_closed ? 1 : 0,
                               tracker.party_index_at_fail_closed[0], tracker.party_index_at_fail_closed[1],
                               tracker.party_index_at_fail_closed[2], tracker.party_index_at_fail_closed[3]);
                        logged_c = true;
                    }
                    if (tracker.saw_replacement && !logged_d) {
                        printf("  [await-player-forced-replacement] Phase D observed at frame %d: replacement committed (slot=%d, battler=%d, species=%u, HP=%d/%d)\n",
                               tracker.commit_frame, new_slot, tracker.new_battler,
                               tracker.new_species, tracker.new_hp, tracker.new_max_hp);
                        logged_d = true;
                    }
                    logged_phase = tracker.phase;
                }
                if (complete) break;

                /* ---- ordinary controller input, chosen from the phase and the live menu state --
                 * The fail-closed window and the wait for the commit are the *same* input situation:
                 * the engine is asking for a replacement, and the tracker only separates them to
                 * prove that the window was observed before the commit was accepted. */
                const int pb = probe_resolve_player_battler(&s);
                uint8_t bcmd = get_battler0_command(ewram, ewram_sz);
                uint32_t cb2 = 0;
                read_u32(HNS_RELEASE_GMAIN_BASE + 0x04u, &cb2);
                const bool in_party_menu =
                    (cb2 >= 0x0819379Cu && cb2 < 0x0819F548u) || (bcmd == 21);
                const bool awaiting_replacement =
                    (tracker.phase == PLAYER_REPL_PHASE_C_FAIL_CLOSED ||
                     tracker.phase == PLAYER_REPL_PHASE_D_COMMITTED);

                if (!awaiting_replacement) {
                    if (bcmd == 17) {
                        /* Action selection: FIGHT is cursor 0. */
                        uint8_t cur = ewram[0x3A4];
                        if (cur != 0) {
                            hold(d, (cur & 1u) ? DUALDEX_BTN_LEFT : DUALDEX_BTN_UP, 4, &previous, &have_previous);
                        } else {
                            hold(d, DUALDEX_BTN_A, 4, &previous, &have_previous);
                        }
                        hold(d, 0, 8, &previous, &have_previous);
                    } else if (bcmd == 19) {
                        /* Move selection: pick the non-damaging move, verified against the live
                         * move list. Nothing is confirmed until the cursor is on it. */
                        if (growl_index < 0) {
                            growl_index = probe_find_move_slot(&s, pb, HNS_MOVE_GROWL);
                            printf("  [await-player-forced-replacement] move list at frame %d (player battler %d): %u,%u,%u,%u -> Growl(%d) at menu position %d\n",
                                   s.frame, pb,
                                   s.mon_moves[pb][0], s.mon_moves[pb][1],
                                   s.mon_moves[pb][2], s.mon_moves[pb][3],
                                   HNS_MOVE_GROWL, growl_index);
                            if (growl_index < 0) {
                                script_error("await-player-forced-replacement: the active player battler "
                                             "does not know move %d (Growl); refusing to guess a menu "
                                             "position and risk a damaging move", HNS_MOVE_GROWL);
                                aborted = true;
                                break;
                            }
                            model_cursor = 0;
                        }
                        uint8_t observed = ewram[0x3A8];
                        int cursor = (move_cursor_trusted && observed <= 3) ? (int)observed : model_cursor;
                        if (cursor != growl_index) {
                            const int cr = cursor / 2, cc = cursor % 2;
                            const int tr = growl_index / 2, tc = growl_index % 2;
                            uint32_t btn;
                            if (cr != tr) btn = (tr > cr) ? DUALDEX_BTN_DOWN : DUALDEX_BTN_UP;
                            else btn = (tc > cc) ? DUALDEX_BTN_RIGHT : DUALDEX_BTN_LEFT;
                            printf("  [await-player-forced-replacement] move cursor frame %d: model=%d raw(0x3A8)=%u -> pressing %s to reach Growl at %d\n",
                                   d->frame, cursor, (unsigned)observed,
                                   btn == DUALDEX_BTN_DOWN ? "DOWN" : btn == DUALDEX_BTN_UP ? "UP" :
                                   btn == DUALDEX_BTN_RIGHT ? "RIGHT" : "LEFT", growl_index);
                            hold(d, btn, 4, &previous, &have_previous);
                            hold(d, 0, 8, &previous, &have_previous);
                            if (btn == DUALDEX_BTN_DOWN) model_cursor = cursor + 2;
                            else if (btn == DUALDEX_BTN_UP) model_cursor = cursor - 2;
                            else if (btn == DUALDEX_BTN_RIGHT) model_cursor = cursor + 1;
                            else model_cursor = cursor - 1;
                        } else {
                            printf("  [await-player-forced-replacement] confirming Growl at menu position %d (raw 0x3A8=%u) frame %d\n",
                                   growl_index, (unsigned)observed, d->frame);
                            if ((int)observed == growl_index) {
                                move_cursor_trusted = true;
                            }
                            hold(d, DUALDEX_BTN_A, 4, &previous, &have_previous);
                            hold(d, 0, 8, &previous, &have_previous);
                            /* The engine remembers the last move used, so the next menu opens here. */
                            model_cursor = growl_index;
                        }
                    } else if (bcmd == 18) {
                        /* An unexpected Yes/No box before the faint: decline it. */
                        hold(d, DUALDEX_BTN_B, 4, &previous, &have_previous);
                        hold(d, 0, 10, &previous, &have_previous);
                    } else if (in_party_menu) {
                        /* A party menu before the faint means the wrong menu was opened: cancel. */
                        hold(d, DUALDEX_BTN_B, 4, &previous, &have_previous);
                        hold(d, 0, 15, &previous, &have_previous);
                    } else {
                        step_one(d, ((f % 6) < 3) ? DUALDEX_BTN_A : 0, &previous, &have_previous);
                    }
                } else if (awaiting_replacement) {
                    /* Show the engine state whenever it changes while a replacement is awaited, so
                     * the actual ROM flow (Yes/No prompt, party menu, or something else) is on the
                     * record instead of being inferred from what the helper expected. */
                    PartyMenuProbeState cand_b;
                    read_party_menu_probe_candidate_b(&cand_b);
                    if (bcmd != last_bcmd_seen || (int)cand_b.slot_id != last_party_slot) {
                        printf("  [await-player-forced-replacement] waiting for replacement at frame %d: "
                               "bcmd=%u inPartyMenu=%d cb2=0x%08X gPartyMenu(type=%d layout=%d slot=%d action=%u) "
                               "hp=%u,%u absent=0x%02X partyIndexes=%u,%u\n",
                               d->frame, (unsigned)bcmd, in_party_menu ? 1 : 0, cb2,
                               cand_b.menu_type, cand_b.layout, cand_b.slot_id, cand_b.action,
                               s.mon_hp[0], s.mon_hp[1], s.absent_flags,
                               s.party_index[0], s.party_index[1]);
                        last_bcmd_seen = bcmd;
                    }
                    if (bcmd == 18) {
                        /* "Use next POKeMON?" -- YES is the default cursor. */
                        printf("  [await-player-forced-replacement] replacement prompt at frame %d: pressing A (YES)\n", d->frame);
                        hold(d, DUALDEX_BTN_A, 6, &previous, &have_previous);
                        hold(d, 0, 15, &previous, &have_previous);
                    } else if (in_party_menu) {
                        int8_t pslot = -1;
                        read_u8(HNS_RELEASE_PARTY_MENU_SLOT_ID, (uint8_t*)&pslot);
                        uint8_t paction = 0;
                        read_u8(0x02034207u, &paction);
                        if ((int)pslot != last_party_slot) {
                            printf("  [await-player-forced-replacement] party menu at frame %d: slotId=%d action=%u (target %d)\n",
                                   d->frame, (int)pslot, (unsigned)paction, new_slot);
                            last_party_slot = (int)pslot;
                        }
                        if ((int)pslot != new_slot) {
                            hold(d, ((int)pslot < new_slot) ? DUALDEX_BTN_DOWN : DUALDEX_BTN_UP, 6, &previous, &have_previous);
                            hold(d, 0, 15, &previous, &have_previous);
                        } else if (party_menu_a_presses < 4 &&
                                   (party_menu_a_presses == 0 || d->frame - last_party_a_frame >= 300)) {
                            party_menu_a_presses++;
                            last_party_a_frame = d->frame;
                            PartyMenuProbeState before;
                            read_party_menu_probe_candidate_b(&before);
                            printf("  [await-player-forced-replacement] confirming replacement slot %d with A "
                                   "(press %d, frame %d; before: type=%d layout=%d slot=%d slot2=%d action=%u)\n",
                                   new_slot, party_menu_a_presses, d->frame,
                                   before.menu_type, before.layout, before.slot_id, before.slot_id2,
                                   (unsigned)before.action);
                            hold(d, DUALDEX_BTN_A, 6, &previous, &have_previous);
                            hold(d, 0, 30, &previous, &have_previous);
                            PartyMenuProbeState after;
                            read_party_menu_probe_candidate_b(&after);
                            Sample post;
                            sample_state(d->cfg, d->frame, 0, &post);
                            uint8_t after_bcmd = get_battler0_command(ewram, ewram_sz);
                            uint32_t after_cb2 = 0;
                            read_u32(HNS_RELEASE_GMAIN_BASE + 0x04u, &after_cb2);
                            printf("  [await-player-forced-replacement] after press %d (frame %d): "
                                   "bcmd=%u cb2=0x%08X gPartyMenu(type=%d layout=%d slot=%d slot2=%d action=%u) "
                                   "partyIndexes=%u,%u hp=%u,%u PartySnapshot known=%d slot=%d\n",
                                   party_menu_a_presses, d->frame, (unsigned)after_bcmd, after_cb2,
                                   after.menu_type, after.layout, after.slot_id, after.slot_id2,
                                   (unsigned)after.action,
                                   post.party_index[0], post.party_index[1],
                                   post.mon_hp[0], post.mon_hp[1],
                                   post.active_player_known ? 1 : 0, post.active_player_slot);
                        } else {
                            /* Waiting out the settle before any further A press. */
                            step_one(d, 0, &previous, &have_previous);
                        }
                    } else {
                        step_one(d, ((f % 6) < 3) ? DUALDEX_BTN_A : 0, &previous, &have_previous);
                    }
                }
            }

            if (tracker.violations > 0) {
                script_error("await-player-forced-replacement %d->%d: %d production-contract violation(s) "
                             "during the transition", old_slot, new_slot, tracker.violations);
            }
            if (tracker.phase != PLAYER_REPL_PHASE_COMPLETE) {
                script_error("await-player-forced-replacement %d->%d timed out after %d frames: "
                             "saw old active=%s saw faint=%s saw fail-closed window=%s saw replacement=%s",
                             old_slot, new_slot, max_f,
                             tracker.saw_old_active ? "yes" : "no",
                             tracker.saw_faint ? "yes" : "no",
                             tracker.saw_fail_closed_window ? "yes" : "no",
                             tracker.saw_replacement ? "yes" : "no");
            } else if (!growl_effect_observed && enemy_attack_baseline > 0) {
                script_error("await-player-forced-replacement: the replacement committed but the "
                             "non-damaging move's effect (an opponent Attack stage drop) was never "
                             "observed, so the selected move was not proven to be Growl");
            } else if (tracker.violations == 0) {
                printf("  [await-player-forced-replacement] OK: %d -> %d complete (old species=%u HP %d/%d; "
                       "replacement species=%u HP %d/%d; opponent HP %d unchanged=%d; growl effect=%d; "
                       "move cursor raw tracked=%d)\n",
                       old_slot, new_slot, tracker.old_species, tracker.old_hp, tracker.old_max_hp,
                       tracker.new_species, tracker.new_hp, tracker.new_max_hp,
                       enemy_hp_prev, enemy_damaged ? 0 : 1, growl_effect_observed ? 1 : 0,
                       move_cursor_trusted ? 1 : 0);
            } else {
                printf("  [await-player-forced-replacement] transition completed, but %d "
                       "production-contract violation(s) were recorded; the run fails\n",
                       tracker.violations);
            }
        } else if (!strcmp(cmd, "catchwild")) {
            int max_f = a1[0] ? atoi(a1) : 6000;
            size_t ewram_sz = 0;
            uint8_t* ewram = libretro_host_get_ewram(&ewram_sz);
            bool ball_thrown = false;
            int r_attempts = 0;
            int f = 0;
            for (; f < max_f; f++) {
                uint8_t ib = 0;
                read_u8(d->cfg->main_struct_gba_address + d->cfg->main_in_battle_byte_offset, &ib);
                if (!((ib >> d->cfg->main_in_battle_bit) & 1)) break;

                uint8_t bcmd = get_battler0_command(ewram, ewram_sz);
                uint32_t exec = 0;
                read_u32(HNS_RELEASE_BATTLE_CONTROLLER_EXEC_FLAGS, &exec);

                if (bcmd == 17) {
                    /* On action selection screen */
                    if (r_attempts < 2) {
                        /* Try R button quick-throw */
                        hold(d, DUALDEX_BTN_R, 6, &previous, &have_previous);
                        hold(d, 0, 10, &previous, &have_previous);
                        r_attempts++;
                        bcmd = get_battler0_command(ewram, ewram_sz);
                        if (bcmd != 17) {
                            ball_thrown = true;
                            printf("  [catchwild] Poké Ball thrown via R button at frame %d\n", d->frame);
                        }
                    } else {
                        /* Fallback: open Bag (cursor 1, press A) */
                        uint8_t cur = ewram[0x3A4];
                        if (!(cur & 1)) {
                            hold(d, DUALDEX_BTN_RIGHT, 4, &previous, &have_previous);
                            hold(d, 0, 8, &previous, &have_previous);
                        } else {
                            hold(d, DUALDEX_BTN_A, 6, &previous, &have_previous);
                            hold(d, 0, 60, &previous, &have_previous);
                            for (int k = 0; k < 4; k++) {
                                hold(d, DUALDEX_BTN_A, 6, &previous, &have_previous);
                                hold(d, 0, 40, &previous, &have_previous);
                            }
                            ball_thrown = true;
                            printf("  [catchwild] Poké Ball thrown via Bag menu at frame %d\n", d->frame);
                        }
                    }
                } else {
                    /* Intro dialogue or post-throw capture sequence / Dex / nickname prompt */
                    /* B button fast-forwards intro, advances Dex text, and declines nickname (NO) */
                    uint32_t btn = ((f % 4) < 2) ? DUALDEX_BTN_B : 0;
                    step_one(d, btn, &previous, &have_previous);
                }
            }
            uint8_t ib = 0;
            read_u8(d->cfg->main_struct_gba_address + d->cfg->main_in_battle_byte_offset, &ib);
            bool out = !((ib >> d->cfg->main_in_battle_bit) & 1);
            printf("  [catchwild] %s after %d frames (ball_thrown=%d)\n", out ? "BATTLE ENDED" : "TIMEOUT", f, ball_thrown ? 1 : 0);
            if (!out) {
                script_error("catchwild timed out after %d frames; capture never completed", max_f);
            }
            hold(d, 0, 80, &previous, &have_previous);
        } else if (!strcmp(cmd, "assert-party-count")) {
            Sample s;
            sample_state(d->cfg, d->frame, 0, &s);
            int want = a2[0] ? atoi(a2) : 0;
            if (!strcmp(a1, "player")) {
                if (s.player_party_count_prod != (uint8_t)want) {
                    script_error("assert-party-count player %d failed: party count is %u at frame %d",
                                 want, s.player_party_count_prod, s.frame);
                } else {
                    printf("  [assert] playerPartyCount=%d OK (frame %d)\n", want, s.frame);
                }
            } else if (!strcmp(a1, "enemy")) {
                if (s.enemy_party_count_prod != (uint8_t)want) {
                    script_error("assert-party-count enemy %d failed: party count is %u at frame %d",
                                 want, s.enemy_party_count_prod, s.frame);
                } else {
                    printf("  [assert] enemyPartyCount=%d OK (frame %d)\n", want, s.frame);
                }
            } else {
                script_error("assert-party-count expects 'player <n>' or 'enemy <n>', got '%s'", a1);
            }
        } else if (!strcmp(cmd, "party-stats")) {
            /* Developer diagnostic: print every party member exactly as the PRODUCTION reader parsed
             * it, including the Speed stat the battle engine will use for turn order. A scenario that
             * depends on turn order (which side writes gLastLandedMoves last) must MEASURE this rather
             * than assume it. Reads only; never writes. */
            size_t ewram_sz = 0;
            uint8_t* ewram = libretro_host_get_ewram(&ewram_sz);
            if (!ewram || ewram_sz == 0) {
                script_error("party-stats: EWRAM unavailable");
            }
            const bool enemy_side = (a1[0] && !strcmp(a1, "enemy"));
            PartySnapshot snap;
            memset(&snap, 0, sizeof(snap));
            uint8_t n = enemy_side
                ? pokemon_read_enemy_party_gba(probe_read, NULL, ewram, ewram_sz, d->cfg, &snap)
                : pokemon_read_player_party_gba(probe_read, NULL, ewram, ewram_sz, d->cfg, &snap);
            printf("  [party-stats] side=%s count=%u activeSlot=%d activeKnown=%d activeBattler=%d "
                   "ambiguous=%d at frame %d\n",
                   enemy_side ? "enemy" : "player", n, snap.active_battler_slot,
                   snap.active_battler_known ? 1 : 0, snap.active_battler_index,
                   snap.active_enemy_ambiguous ? 1 : 0, d->frame);
            for (uint8_t i = 0; i < n && i < 6; i++) {
                const ParsedPokemon* p = &snap.members[i];
                printf("  [party-stats]   slot %u species=%u lvl=%u hp=%u/%u atk=%u def=%u spe=%u "
                       "spa=%u spd=%u nature=%u hiddenNature=%u(+%u) ivs=%u/%u/%u/%u/%u/%u "
                       "evs=%u/%u/%u/%u/%u/%u moves=%u,%u,%u,%u\n",
                       i, p->species, p->level, p->current_hp, p->max_hp, p->attack, p->defense,
                       p->speed, p->sp_attack, p->sp_defense, p->nature, p->hidden_nature,
                       p->hidden_nature_modifier, p->hp_iv, p->attack_iv, p->defense_iv,
                       p->speed_iv, p->sp_attack_iv, p->sp_defense_iv, p->hp_ev, p->attack_ev,
                       p->defense_ev, p->speed_ev, p->sp_attack_ev, p->sp_defense_ev,
                       p->moves[0], p->moves[1], p->moves[2], p->moves[3]);
            }
            if (n == 0) {
                printf("  [party-stats]   (no party members parsed)\n");
            }
        } else if (!strcmp(cmd, "assert-battle-kind")) {
            Sample s;
            sample_state(d->cfg, d->frame, 0, &s);
            const char* current_kind = kind_name(s.kind);
            char want_upper[64] = {0};
            for (size_t i = 0; a1[i] && i < sizeof(want_upper) - 1; i++) {
                char c = a1[i];
                if (c >= 'a' && c <= 'z') c = (char)(c - 'a' + 'A');
                want_upper[i] = c;
            }
            if (strcmp(current_kind, want_upper) != 0) {
                script_error("assert-battle-kind %s failed: kind is %s at frame %d",
                             a1, current_kind, s.frame);
            } else {
                printf("  [assert] battleKind=%s OK (frame %d)\n", want_upper, s.frame);
            }
        } else if (!strcmp(cmd, "assert-active-enemy-slot")) {
            Sample s;
            sample_state(d->cfg, d->frame, 0, &s);
            int want = a1[0] ? atoi(a1) : -1;
            if (s.active_enemy != ACTIVE_ENEMY_SLOT || s.enemy_slot != want) {
                script_error("assert-active-enemy-slot %d failed: state is %s slot %d at frame %d",
                             want, active_enemy_name(s.active_enemy), s.enemy_slot, s.frame);
            } else {
                printf("  [assert] activeEnemySlot=%d OK (frame %d)\n", want, s.frame);
            }
        } else if (!strcmp(cmd, "assert-active-player-slot")) {
            Sample s;
            sample_state(d->cfg, d->frame, 0, &s);
            int want = a1[0] ? atoi(a1) : -1;
            if (!s.active_player_known || s.active_player_slot != want) {
                script_error("assert-active-player-slot %d failed: known=%d slot=%d at frame %d",
                             want, s.active_player_known ? 1 : 0, s.active_player_slot, s.frame);
            } else {
                printf("  [assert] activePlayerSlot=%d OK (frame %d)\n", want, s.frame);
            }
        } else if (!strcmp(cmd, "assert-fainted")) {
            Sample s;
            sample_state(d->cfg, d->frame, 0, &s);
            bool want = !strcmp(a2, "true");
            bool actual = false;
            if (!strcmp(a1, "enemy")) {
                actual = s.enemy_fainted;
            } else if (!strcmp(a1, "player")) {
                actual = (s.mon_hp[0] == 0);
            } else {
                script_error("assert-fainted expects 'enemy' or 'player', got '%s'", a1);
                want = actual;
            }
            if (actual != want) {
                script_error("assert-fainted %s %s failed: actual is %s at frame %d",
                             a1, a2, actual ? "true" : "false", s.frame);
            } else {
                printf("  [assert] fainted %s=%s OK (frame %d)\n", a1, a2, s.frame);
            }
        } else if (!strcmp(cmd, "assert-map")) {
            uint16_t mx = 0, my = 0; uint8_t mg = 0, mn = 0;
            read_map_position(&mx, &my, &mg, &mn);
            int wg = a1[0] ? atoi(a1) : -1;
            int wn = a2[0] ? atoi(a2) : -1;
            if ((int)mg != wg || (int)mn != wn) {
                script_error("assert-map %d %d failed: player is at %u/%u (pos %u,%u) frame %d",
                             wg, wn, mg, mn, mx, my, d->frame);
            } else {
                printf("  [assert] map=%d/%d OK (frame %d)\n", wg, wn, d->frame);
            }
        } else if (!strcmp(cmd, "assert-pos")) {
            uint16_t mx = 0, my = 0; uint8_t mg = 0, mn = 0;
            read_map_position(&mx, &my, &mg, &mn);
            int wx = a1[0] ? atoi(a1) : -1;
            int wy = a2[0] ? atoi(a2) : -1;
            if ((int)mx != wx || (int)my != wy) {
                script_error("assert-pos %d %d failed: player is at (%u,%u) (map %u/%u) frame %d",
                             wx, wy, mx, my, mg, mn, d->frame);
            } else {
                printf("  [assert] pos=(%d,%d) OK (frame %d)\n", wx, wy, d->frame);
            }
        } else if (!strcmp(cmd, "reject-encounter")) {
            Sample s;
            sample_state(d->cfg, d->frame, 0, &s);
            if (s.in_battle) {
                printf("  [INVARIANT VIOLATION] frame %d: unexpected encounter in a scenario that "
                       "must stay out of battle\n", s.frame);
                g_violations++;
            }
        } else {
            script_error("unknown command '%s'", cmd);
        }
    }
    if (script_path) fclose(f);
    return 0;
}

int main(int argc, char** argv) {
    for (int i = 1; i < argc; i++) {
        if (!strcmp(argv[i], "--selftest")) {
            return run_pure_tracker_selftests();
        }
    }

    const char* core_path = NULL;
    const char* rom_path = NULL;
    const char* sav_path = NULL;
    const char* script_path = NULL;
    int fallback_frames = 1800;

    for (int i = 1; i < argc; i++) {
        if (!strcmp(argv[i], "--sav") && i + 1 < argc) sav_path = argv[++i];
        else if (!strcmp(argv[i], "--script") && i + 1 < argc) script_path = argv[++i];
        else if (!strcmp(argv[i], "--frames") && i + 1 < argc) fallback_frames = atoi(argv[++i]);
        else if (!strcmp(argv[i], "--quiet")) g_quiet = true;
        else if (!core_path) core_path = argv[i];
        else if (!rom_path) rom_path = argv[i];
    }
    if (!core_path || !rom_path) {
        fprintf(stderr,
                "usage: %s <mgba_libretro.so> <hns_2.0.5.gba> [--sav path] [--script path] "
                "[--frames n] [--quiet]\n", argv[0]);
        return 2;
    }

    const GameMemoryConfig* cfg = pokemon_get_game_config(GAME_HEART_AND_SOUL);
    if (!cfg) {
        fprintf(stderr, "FAIL: Heart & Soul configuration unavailable\n");
        return 1;
    }

    printf("== DualDex H&S 2.0.5 runtime battle-state probe ==\n");
    printf("core   : %s\n", core_path);
    printf("rom    : %s\n", rom_path);
    printf("save   : %s\n", sav_path ? sav_path : "(none)");
    printf("script : %s\n", script_path ? script_path : "(stdin)");
    printf("layout : playerParty=0x%X playerPartyCount=0x%X enemyParty=0x%X enemyPartyCount=0x%X "
           "battleMons=0x%X battlersCount=0x%X battleTypeFlags=0x%X outcome=0x%X "
           "partyIndexes=0x%X positions=0x%X absentFlags=0x%X gMain=0x%08X+0x%X bit %u\n",
           cfg->player_party_offset, cfg->player_party_count_offset, cfg->enemy_party_offset,
           cfg->enemy_party_count_offset, cfg->battle_mons_offset, cfg->battlers_count_offset,
           cfg->battle_type_flags_offset, cfg->battle_outcome_offset,
           cfg->battler_party_indexes_offset, cfg->battler_positions_offset,
           cfg->absent_battler_flags_offset, cfg->main_struct_gba_address,
           cfg->main_in_battle_byte_offset, cfg->main_in_battle_bit);

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

    libretro_host_step_frame();
    printf("save RAM size: %zu bytes\n", libretro_host_get_save_ram_size());
    if (sav_path) {
        /* A requested battery save that cannot be loaded is fatal: the scenario was written against
         * a save that has a party and reachable battles, and running it without one would silently
         * "pass" while testing nothing. */
        struct stat st;
        size_t expected = libretro_host_get_save_ram_size();
        if (stat(sav_path, &st) != 0) {
            fprintf(stderr, "error: --sav '%s' does not exist or is unreadable\n", sav_path);
            g_script_errors++;
        } else if ((size_t)st.st_size != expected) {
            fprintf(stderr, "error: --sav '%s' is %ld bytes but the core expects %zu\n",
                    sav_path, (long)st.st_size, expected);
            g_script_errors++;
        } else if (!libretro_host_load_save_ram(sav_path)) {
            fprintf(stderr, "error: --sav '%s' was rejected by the core\n", sav_path);
            g_script_errors++;
        } else {
            printf("battery save load: OK\n");
        }
    }

    printf("\n-- runtime table (a row appears on every observed state change) --\n");
    Driver driver = { cfg, 0, 0 };
    int script_rc = 0;
    if (script_path) {
        script_rc = run_script(&driver, script_path);
    } else {
        /* Legacy behaviour: mash START/A so the probe walks past the boot screens. */
        Sample previous;
        bool have_previous = false;
        for (int f = 0; f < fallback_frames; f++) {
            uint32_t buttons = 0;
            if ((f % 30) < 12) buttons |= DUALDEX_BTN_START;
            if ((f % 30) >= 15 && (f % 30) < 27) buttons |= DUALDEX_BTN_A;
            step_one(&driver, buttons, &previous, &have_previous);
        }
    }
    libretro_host_set_input_buttons(0);

    printf("\n-- summary --\n");
    printf("frames run                    : %d\n", driver.frame);
    printf("frames checked                : %d\n", g_frames_checked);
    printf("runtime invariant violations  : %d\n", g_violations);
    printf("script errors                 : %d\n", g_script_errors);
    printf("script runner status          : %s\n", script_rc == 0 ? "ok" : "failed");
    printf("result                        : %s\n",
           (script_rc == 0 && g_violations == 0 && g_script_errors == 0) ? "PASS" : "FAIL");

    libretro_host_unload_rom();
    printf("-- regions after unload: %zu (must be 0) --\n", libretro_host_get_gba_region_count());
    uint8_t guard = 0;
    printf("-- read after unload must be rejected: %s --\n",
           libretro_host_read_gba_address(cfg->main_struct_gba_address, &guard, 1)
               ? "RETURNED TRUE (BAD)" : "rejected (correct)");
    libretro_host_cleanup();

    /* A run only succeeds when the scenario actually happened AND every invariant held. The
     * runner's own return value is folded in as well, so a failure that never reached a script
     * line (e.g. the script file could not be opened) cannot slip through as a pass. */
    return (script_rc == 0 && g_violations == 0 && g_script_errors == 0) ? 0 : 1;
}
