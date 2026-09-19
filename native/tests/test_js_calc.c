/*
 * DualDex native QuickJS damage-calculator suite.
 *
 * Runs the REAL native calculator (native/src/js_calc_engine.c) against the
 * pinned QuickJS submodule (native/quickjs) and the calculator bundle the
 * application actually ships (app/src/main/assets/calc_bundle.js). No device,
 * emulator, ROM, save file, or live battle is involved. Driven by
 * `./ci.sh test` (calc_test()), which fails closed when this binary exits
 * non-zero.
 *
 * Expected-value provenance
 * -------------------------
 * Every golden number below is derived from the documented Generation III
 * mechanics - stat formula, damage formula, then the modifier chain in the
 * order the pinned @smogon/calc 0.11.0 ADV implementation applies it:
 *
 *   A/D stats (IV 31, EV 0 unless stated, nature applied then floored)
 *     -> base damage = floor(floor(floor(2*L/5 + 2) * BP * A / D) / 50)
 *     -> attack-form modifiers routed through the damage value: burn x1/2,
 *        Reflect/Light Screen x1/2 in singles (x2/3 in doubles),
 *        spread move x1/2 when the field is not singles
 *     -> +2
 *     -> critical hit x2
 *     -> STAB x1.5
 *     -> type effectiveness applied per defending type, floored each time
 *     -> 16 rolls of floor(damage * r / 100) for r = 85..100, minimum 1
 *
 * The arithmetic for each fixture is written next to it. The expectations were
 * computed independently of this engine and then cross-checked against it; the
 * full 16-roll vector is asserted for every Generation III golden fixture so a
 * rounding change cannot slip through a min/max-only comparison.
 *
 * These tests establish calculator execution and regression coverage. They do
 * NOT establish H&S 2.0.5 calculator compatibility: issue #9 owns the H&S
 * fixtures, which need the hack's own species/move/item data. The single Gen 8
 * fixture at the bottom is generic engine smoke coverage, not H&S coverage.
 *
 * Scope note (issue #29): this suite also pins the field.gameType input
 * contract. The application used to send the lowercase "singles", which
 * @smogon/calc (which compares case-sensitively against "Singles") interpreted
 * as a doubles field, halving every Generation III spread move such as Rock
 * Slide. `gen3_spread_move_lowercase_singles` reproduces the historical
 * application request and now requires the correct singles damage.
 */

#include "js_calc_engine.h"
#include "json_lite.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define ANSI_GREEN "\033[0;32m"
#define ANSI_RED   "\033[0;31m"
#define ANSI_RESET "\033[0m"

#define BUNDLE_PATH "app/src/main/assets/calc_bundle.js"

static int g_checks_passed = 0;
static int g_checks_failed = 0;
static const char* g_fixture = "(suite)";

/* ------------------------------------------------------------------ */
/* Checks: plain conditionals with counters, so nothing can be compiled
 * away (no NDEBUG-dependent assert()) and every failure names the fixture,
 * the field, the expected value, and the actual value.                 */
/* ------------------------------------------------------------------ */

static void check_condition(const char* field, int condition) {
    if (condition) {
        g_checks_passed++;
    } else {
        g_checks_failed++;
        printf(ANSI_RED "  [FAIL] %s / %s: condition not satisfied" ANSI_RESET "\n", g_fixture, field);
    }
}

static void check_int(const char* field, long expected, long actual) {
    if (expected == actual) {
        g_checks_passed++;
    } else {
        g_checks_failed++;
        printf(ANSI_RED "  [FAIL] %s / %s: expected %ld, got %ld" ANSI_RESET "\n",
               g_fixture, field, expected, actual);
    }
}

static void check_str(const char* field, const char* expected, const char* actual) {
    if (actual && strcmp(expected, actual) == 0) {
        g_checks_passed++;
    } else {
        g_checks_failed++;
        printf(ANSI_RED "  [FAIL] %s / %s: expected \"%s\", got \"%s\"" ANSI_RESET "\n",
               g_fixture, field, expected, actual ? actual : "(null)");
    }
}

/* Missing / non-string / empty values must be reported as such, not as a
 * default-looking comparison failure. */
static void check_string_present(const char* field, const jl_value* value) {
    if (jl_is_str(value) && jl_str(value)[0] != '\0') {
        g_checks_passed++;
    } else {
        g_checks_failed++;
        printf(ANSI_RED "  [FAIL] %s / %s: expected a non-empty JSON string" ANSI_RESET "\n",
               g_fixture, field);
    }
}

static char* read_file_to_string(const char* path) {
    FILE* f = fopen(path, "rb");
    if (!f) return NULL;
    if (fseek(f, 0, SEEK_END) != 0) { fclose(f); return NULL; }
    long sz = ftell(f);
    if (sz < 0) { fclose(f); return NULL; }
    if (fseek(f, 0, SEEK_SET) != 0) { fclose(f); return NULL; }

    char* buf = (char*)malloc((size_t)sz + 1);
    if (!buf) { fclose(f); return NULL; }
    size_t rd = fread(buf, 1, (size_t)sz, f);
    buf[rd] = '\0';
    fclose(f);
    return buf;
}

/* Copies the 16-element damage roll vector; returns the count, or -1 when the
 * response does not carry a 16-element numeric array. */
#define ROLL_COUNT 16
static int response_rolls(const jl_value* doc, int out[ROLL_COUNT]) {
    const jl_value* damage = jl_get(doc, "damage");
    if (!jl_is_arr(damage)) return -1;
    int count = jl_len(damage);
    if (count != ROLL_COUNT) return -1;
    for (int i = 0; i < count; i++) {
        const jl_value* item = jl_at(damage, i);
        if (!jl_is_num(item)) return -1;
        out[i] = (int)jl_num(item);
    }
    return count;
}

/* ------------------------------------------------------------------ */
/* Fixture table                                                       */
/* ------------------------------------------------------------------ */

/* Every fixture states its inputs explicitly (generation, level, nature, IVs,
 * EVs, ability, item, move, field) so no expectation depends on a hidden
 * default. @smogon/calc defaults omitted IVs to 31 and EVs to 0; the IV/EV
 * blocks are still spelled out where the numbers matter. */
#define IVS_MAX "\"ivs\":{\"hp\":31,\"atk\":31,\"def\":31,\"spa\":31,\"spd\":31,\"spe\":31}"
#define EVS_ZERO "\"evs\":{\"hp\":0,\"atk\":0,\"def\":0,\"spa\":0,\"spd\":0,\"spe\":0}"

/* Machamp (Hardy L50, Atk 150 / SpA 85) vs Snorlax (Hardy L50, Def 85 / SpD 130, HP 235).
 *
 *   A(atk) = floor((2*130 + 31 + 0)*50/100) + 5 = 150
 *   A(spa) = floor((2*65  + 31 + 0)*50/100) + 5 = 85
 *   D(def) = floor((2*65  + 31 + 0)*50/100) + 5 = 85
 *   D(spd) = floor((2*110 + 31 + 0)*50/100) + 5 = 130
 *   HP     = floor((2*160 + 31 + 0)*50/100) + 50 + 10 = 235
 */
#define MACHAMP_VS_SNORLAX_HEAD \
    "\"gen\":3," \
    "\"attacker\":{\"species\":\"Machamp\",\"level\":50,\"nature\":\"Hardy\"," IVS_MAX "," EVS_ZERO "}," \
    "\"defender\":{\"species\":\"Snorlax\",\"level\":50,\"nature\":\"Hardy\"," IVS_MAX "," EVS_ZERO "},"

/* Rock Slide: Rock, 75 BP, physical, and a Gen III spread move
 * (target allAdjacentFoes), which is what makes the field format observable.
 *   Singles: floor(22*75*150/85) = 2911 -> floor(2911/50) + 2 = 60; type x1
 *   Doubles: the same move is halved (floor(60/2)... applied to the pre-+2
 *            damage: floor(58/2) = 29 -> +2 = 31)
 */
#define ROCK_SLIDE_BODY "\"move\":{\"name\":\"Rock Slide\"}"

static const int ROLLS_MACHAMP_ROCK_SLIDE_SINGLES[ROLL_COUNT] =
    {51, 51, 52, 52, 53, 54, 54, 55, 55, 56, 57, 57, 58, 58, 59, 60};
static const int ROLLS_MACHAMP_ROCK_SLIDE_DOUBLES[ROLL_COUNT] =
    {26, 26, 26, 27, 27, 27, 28, 28, 28, 29, 29, 29, 30, 30, 30, 31};

/* Machamp Strength (Normal 80) vs Snorlax: floor(22*80*150/85) = 3105 ->
 * floor(3105/50) + 2 = 64.
 * With Reflect in singles the attack form is halved before +2:
 * floor(62/2) = 31 -> +2 = 33.
 * With Reflect in doubles the library (and the games) use 2/3:
 * floor(62*2/3) = 41 -> +2 = 43. */
static const int ROLLS_STRENGTH_REFLECT_SINGLES[ROLL_COUNT] =
    {28, 28, 28, 29, 29, 29, 30, 30, 30, 31, 31, 31, 32, 32, 32, 33};
static const int ROLLS_STRENGTH_REFLECT_DOUBLES[ROLL_COUNT] =
    {36, 36, 37, 37, 38, 38, 39, 39, 39, 40, 40, 41, 41, 42, 42, 43};

/* Machamp Crunch (Dark 80) vs Snorlax: Dark is a SPECIAL type in Gen III, so
 * the SpA/SpD pair is used even though Machamp is a physical attacker.
 * floor(22*80*85/130) = 1150 -> floor(1150/50) + 2 = 25. If the engine used
 * Attack here the result would be ~42, so this fixture also pins the
 * generation's type-based physical/special split. */
static const int ROLLS_CRUNCH_SPECIAL[ROLL_COUNT] =
    {21, 21, 21, 22, 22, 22, 22, 23, 23, 23, 23, 24, 24, 24, 24, 25};

/* Machamp Karate Chop (Fighting 50, STAB) vs Snorlax, 2x super effective.
 * base floor(22*50*150/85) = 1941 -> floor(1941/50) + 2 = 40
 * STAB floor(40*1.5) = 60 -> Fighting vs Normal x2 = 120. */
static const int ROLLS_KARATE_CHOP_STAB_2X[ROLL_COUNT] =
    {102, 103, 104, 105, 106, 108, 109, 110, 111, 112, 114, 115, 116, 117, 118, 120};

/* Salamence (Adamant L50, 252 Atk EV, Choice Band) Rock Slide vs
 * Skarmory (Impish L50, 252 HP/Def EV):
 *   A = floor(((2*135 + 31 + 63)*50/100) + 5) = 187, x1.1 nature = 205,
 *       Choice Band floor(205*1.5) = 307
 *   D = floor(((2*140 + 31 + 63)*50/100) + 5) = 192, x1.1 nature = 211
 *   HP = floor((2*65 + 31 + 63)*50/100) + 50 + 10 = 172
 *   base floor(22*75*307/211) = 2400 -> floor(2400/50) + 2 = 50
 *   Rock vs Steel (0.5) x Flying (2) = 1.0 */
static const int ROLLS_CHOICE_BAND_SALAMENCE[ROLL_COUNT] =
    {42, 43, 43, 44, 44, 45, 45, 46, 46, 47, 47, 48, 48, 49, 49, 50};

/* Machamp Flamethrower (Fire 95, special) vs Snorlax, with and without the
 * defender's Thick Fat (halves the attack form for Fire/Ice in Gen III):
 *   without: floor(22*95*85/130) = 1366 -> floor(1366/50) + 2 = 29
 *   with:    A = floor(85/2) = 42 -> floor(22*95*42/130) = 675 -> 13 + 2 = 15 */
static const int ROLLS_FLAMETHROWER_NO_THICK_FAT[ROLL_COUNT] =
    {24, 24, 25, 25, 25, 26, 26, 26, 26, 27, 27, 27, 28, 28, 28, 29};
static const int ROLLS_FLAMETHROWER_THICK_FAT[ROLL_COUNT] =
    {12, 12, 13, 13, 13, 13, 13, 13, 13, 14, 14, 14, 14, 14, 14, 15};

/* Machamp Hydro Pump (Water 120, special) vs Snorlax with Light Screen:
 * floor(22*120*85/130) = 1726 -> floor(1726/50) = 34; Light Screen in singles
 * halves the pre-+2 value: floor(34/2) = 17 -> +2 = 19. */
static const int ROLLS_HYDRO_PUMP_LIGHT_SCREEN[ROLL_COUNT] =
    {16, 16, 16, 16, 16, 17, 17, 17, 17, 17, 18, 18, 18, 18, 18, 19};

typedef struct {
    const char* name;              /* fixture id, reported on failure */
    const char* request;           /* exact JSON request sent to the engine */
    int expect_success;            /* 1: success must be true, 0: must be false */
    const char* expect_error_contains; /* required substring when expect_success == 0 */
    const char* expect_move_type;
    const char* expect_move_category;
    int expect_move_power;         /* < 0 to skip */
    int expect_defender_hp;        /* < 0 to skip */
    const int* expect_rolls;       /* 16 rolls, or NULL for min/max only */
    const char* provenance;        /* where the expected numbers come from */
} calc_fixture;

static const calc_fixture FIXTURES[] = {
    /* --- battle-format (field.gameType) contract ------------------- */
    {
        "gen3_spread_move_no_field",
        "{" MACHAMP_VS_SNORLAX_HEAD ROCK_SLIDE_BODY "}",
        1, NULL, "Rock", "Physical", 75, 235, ROLLS_MACHAMP_ROCK_SLIDE_SINGLES,
        "no field at all must behave as singles (the bundle's documented default)"
    },
    {
        "gen3_spread_move_empty_field",
        "{" MACHAMP_VS_SNORLAX_HEAD ROCK_SLIDE_BODY ",\"field\":{}}",
        1, NULL, "Rock", "Physical", 75, 235, ROLLS_MACHAMP_ROCK_SLIDE_SINGLES,
        "field present without gameType must behave as singles"
    },
    {
        "gen3_spread_move_null_game_type",
        "{" MACHAMP_VS_SNORLAX_HEAD ROCK_SLIDE_BODY ",\"field\":{\"gameType\":null}}",
        1, NULL, "Rock", "Physical", 75, 235, ROLLS_MACHAMP_ROCK_SLIDE_SINGLES,
        "explicit null means 'not specified' and must behave as singles"
    },
    {
        "gen3_spread_move_empty_game_type",
        "{" MACHAMP_VS_SNORLAX_HEAD ROCK_SLIDE_BODY ",\"field\":{\"gameType\":\"\"}}",
        1, NULL, "Rock", "Physical", 75, 235, ROLLS_MACHAMP_ROCK_SLIDE_SINGLES,
        "explicit empty string means 'not specified' and must behave as singles"
    },
    {
        "gen3_spread_move_lowercase_singles",
        "{" MACHAMP_VS_SNORLAX_HEAD ROCK_SLIDE_BODY ",\"field\":{\"gameType\":\"singles\"}}",
        1, NULL, "Rock", "Physical", 75, 235, ROLLS_MACHAMP_ROCK_SLIDE_SINGLES,
        "the lowercase spelling the application historically sent must be normalised to singles"
    },
    {
        "gen3_spread_move_canonical_singles",
        "{" MACHAMP_VS_SNORLAX_HEAD ROCK_SLIDE_BODY ",\"field\":{\"gameType\":\"Singles\"}}",
        1, NULL, "Rock", "Physical", 75, 235, ROLLS_MACHAMP_ROCK_SLIDE_SINGLES,
        "canonical singles: core golden fixture for the spread-move regression"
    },
    {
        "gen3_spread_move_lowercase_doubles",
        "{" MACHAMP_VS_SNORLAX_HEAD ROCK_SLIDE_BODY ",\"field\":{\"gameType\":\"doubles\"}}",
        1, NULL, "Rock", "Physical", 75, 235, ROLLS_MACHAMP_ROCK_SLIDE_DOUBLES,
        "lowercase doubles must keep the library's distinct doubles behaviour"
    },
    {
        "gen3_spread_move_canonical_doubles",
        "{" MACHAMP_VS_SNORLAX_HEAD ROCK_SLIDE_BODY ",\"field\":{\"gameType\":\"Doubles\"}}",
        1, NULL, "Rock", "Physical", 75, 235, ROLLS_MACHAMP_ROCK_SLIDE_DOUBLES,
        "canonical doubles: spread moves are halved (library boundary coverage, "
        "not a claim that DualDex supports live doubles battles)"
    },
    {
        "gen3_spread_move_uppercase_rejected",
        "{" MACHAMP_VS_SNORLAX_HEAD ROCK_SLIDE_BODY ",\"field\":{\"gameType\":\"SINGLES\"}}",
        0, "gameType", NULL, NULL, -1, -1, NULL,
        "only 'singles'/'Singles'/'doubles'/'Doubles' are accepted; other casings are rejected"
    },
    {
        "gen3_spread_move_unsupported_rejected",
        "{" MACHAMP_VS_SNORLAX_HEAD ROCK_SLIDE_BODY ",\"field\":{\"gameType\":\"triples\"}}",
        0, "gameType", NULL, NULL, -1, -1, NULL,
        "an unsupported format must error instead of being coerced"
    },
    {
        "gen3_spread_move_non_string_rejected",
        "{" MACHAMP_VS_SNORLAX_HEAD ROCK_SLIDE_BODY ",\"field\":{\"gameType\":5}}",
        0, "gameType", NULL, NULL, -1, -1, NULL,
        "a non-string gameType must error instead of being coerced"
    },

    /* --- Generation III mechanics --------------------------------- */
    {
        "gen3_special_type_split_crunch",
        "{" MACHAMP_VS_SNORLAX_HEAD "\"move\":{\"name\":\"Crunch\"},"
        "\"field\":{\"gameType\":\"Singles\"}}",
        1, NULL, "Dark", "Special", 80, 235, ROLLS_CRUNCH_SPECIAL,
        "Gen III type-based split: Dark is special, so SpA/SpD are used"
    },
    {
        "gen3_stab_and_type_effectiveness",
        "{" MACHAMP_VS_SNORLAX_HEAD "\"move\":{\"name\":\"Karate Chop\"},"
        "\"field\":{\"gameType\":\"Singles\"}}",
        1, NULL, "Fighting", "Physical", 50, 235, ROLLS_KARATE_CHOP_STAB_2X,
        "STAB x1.5 then 2x type effectiveness, floored in that order"
    },
    {
        "gen3_choice_band_item",
        "{\"gen\":3,"
        "\"attacker\":{\"species\":\"Salamence\",\"level\":50,\"nature\":\"Adamant\","
        "\"item\":\"Choice Band\",\"evs\":{\"hp\":0,\"atk\":252,\"def\":0,\"spa\":0,\"spd\":0,\"spe\":0},"
        "\"ivs\":{\"hp\":31,\"atk\":31,\"def\":31,\"spa\":31,\"spd\":31,\"spe\":31}},"
        "\"defender\":{\"species\":\"Skarmory\",\"level\":50,\"nature\":\"Impish\","
        "\"evs\":{\"hp\":252,\"atk\":0,\"def\":252,\"spa\":0,\"spd\":0,\"spe\":0},"
        "\"ivs\":{\"hp\":31,\"atk\":31,\"def\":31,\"spa\":31,\"spd\":31,\"spe\":31}},"
        ROCK_SLIDE_BODY ",\"field\":{\"gameType\":\"Singles\"}}",
        1, NULL, "Rock", "Physical", 75, 172, ROLLS_CHOICE_BAND_SALAMENCE,
        "Choice Band x1.5 attack, Adamant/Impish natures, Rock vs Steel x Flying = 1.0"
    },
    {
        "gen3_ability_thick_fat_fire",
        "{" MACHAMP_VS_SNORLAX_HEAD "\"move\":{\"name\":\"Flamethrower\"},"
        "\"field\":{\"gameType\":\"Singles\"}}",
        1, NULL, "Fire", "Special", 95, 235, ROLLS_FLAMETHROWER_NO_THICK_FAT,
        "defender ability not specified: Snorlax's default (Immunity) leaves Fire unmodified"
    },
    {
        "gen3_ability_thick_fat_halves_fire",
        "{\"gen\":3,"
        "\"attacker\":{\"species\":\"Machamp\",\"level\":50,\"nature\":\"Hardy\"," IVS_MAX "," EVS_ZERO "},"
        "\"defender\":{\"species\":\"Snorlax\",\"level\":50,\"nature\":\"Hardy\",\"ability\":\"Thick Fat\","
        IVS_MAX "," EVS_ZERO "},"
        "\"move\":{\"name\":\"Flamethrower\"},\"field\":{\"gameType\":\"Singles\"}}",
        1, NULL, "Fire", "Special", 95, 235, ROLLS_FLAMETHROWER_THICK_FAT,
        "Gen III Thick Fat halves the attack form for Fire/Ice"
    },
    {
        "gen3_reflect_physical_singles",
        "{" MACHAMP_VS_SNORLAX_HEAD "\"move\":{\"name\":\"Strength\"},"
        "\"field\":{\"gameType\":\"Singles\",\"defenderSide\":{\"isReflect\":true}}}",
        1, NULL, "Normal", "Physical", 80, 235, ROLLS_STRENGTH_REFLECT_SINGLES,
        "Gen III singles Reflect halves the physical attack form (x1/2)"
    },
    {
        "gen3_reflect_doubles_uses_two_thirds",
        "{" MACHAMP_VS_SNORLAX_HEAD "\"move\":{\"name\":\"Strength\"},"
        "\"field\":{\"gameType\":\"Doubles\",\"defenderSide\":{\"isReflect\":true}}}",
        1, NULL, "Normal", "Physical", 80, 235, ROLLS_STRENGTH_REFLECT_DOUBLES,
        "the same screen is weaker in doubles (x2/3): proof the screen path is format-dependent"
    },
    {
        "gen3_light_screen_special_singles",
        "{" MACHAMP_VS_SNORLAX_HEAD "\"move\":{\"name\":\"Hydro Pump\"},"
        "\"field\":{\"gameType\":\"Singles\",\"defenderSide\":{\"isLightScreen\":true}}}",
        1, NULL, "Water", "Special", 120, 235, ROLLS_HYDRO_PUMP_LIGHT_SCREEN,
        "Gen III singles Light Screen halves the special attack form (x1/2)"
    },

    /* --- generic engine coverage (NOT H&S 2.0.5 coverage) --------- */
    {
        "gen8_generic_engine_smoke",
        "{\"gen\":8,"
        "\"attacker\":{\"species\":\"Sylveon\",\"level\":50,\"nature\":\"Modest\","
        "\"evs\":{\"hp\":0,\"atk\":0,\"def\":0,\"spa\":252,\"spd\":0,\"spe\":0},"
        "\"ivs\":{\"hp\":31,\"atk\":31,\"def\":31,\"spa\":31,\"spd\":31,\"spe\":31}},"
        "\"defender\":{\"species\":\"Dragonite\",\"level\":50,\"nature\":\"Adamant\"," IVS_MAX "," EVS_ZERO "},"
        "\"move\":{\"name\":\"Moonblast\"},\"field\":{\"gameType\":\"Singles\"}}",
        1, NULL, "Fairy", "Special", 95, 166, NULL,
        "generic modern-generation coverage only; H&S 2.0.5 fixtures belong to issue #9"
    },

    /* --- error paths ---------------------------------------------- */
    {
        "invalid_json_input",
        "{not json",
        0, NULL, NULL, NULL, -1, -1, NULL,
        "malformed request must come back through the error contract"
    },
    {
        "unknown_species",
        "{\"gen\":3,"
        "\"attacker\":{\"species\":\"NotAPokemon\",\"level\":50},"
        "\"defender\":{\"species\":\"Snorlax\",\"level\":50},"
        ROCK_SLIDE_BODY ",\"field\":{\"gameType\":\"Singles\"}}",
        0, NULL, NULL, NULL, -1, -1, NULL,
        "an unresolvable species must fail rather than fabricate a result"
    },
    {
        "unknown_move",
        "{" MACHAMP_VS_SNORLAX_HEAD "\"move\":{\"name\":\"NotAMove\"},"
        "\"field\":{\"gameType\":\"Singles\"}}",
        0, NULL, NULL, NULL, -1, -1, NULL,
        "an unresolvable move must fail rather than fabricate a result"
    },
};

#define FIXTURE_COUNT ((int)(sizeof(FIXTURES) / sizeof(FIXTURES[0])))

static void run_fixture(const calc_fixture* fx) {
    g_fixture = fx->name;

    char* raw = js_calc_calculate(fx->request);
    if (!raw) {
        g_checks_failed++;
        printf(ANSI_RED "  [FAIL] %s: engine returned NULL (no response at all)" ANSI_RESET "\n", fx->name);
        return;
    }

    jl_value* doc = jl_parse(raw);
    if (!doc) {
        g_checks_failed++;
        printf(ANSI_RED "  [FAIL] %s: response is not valid JSON: %.160s" ANSI_RESET "\n", fx->name, raw);
        free(raw);
        return;
    }

    const jl_value* success = jl_get(doc, "success");
    if (!jl_is_bool(success)) {
        g_checks_failed++;
        printf(ANSI_RED "  [FAIL] %s / success: missing or non-boolean" ANSI_RESET "\n", fx->name);
        jl_free(doc);
        free(raw);
        return;
    }

    check_int("success", fx->expect_success, jl_bool(success) ? 1 : 0);

    if (!fx->expect_success) {
        const jl_value* error = jl_get(doc, "error");
        check_string_present("error", error);
        if (fx->expect_error_contains && jl_is_str(error)) {
            check_condition("error mentions the rejected field",
                            strstr(jl_str(error), fx->expect_error_contains) != NULL);
        }
        jl_free(doc);
        free(raw);
        return;
    }

    if (fx->expect_move_type) check_str("moveType", fx->expect_move_type, jl_str(jl_get(doc, "moveType")));
    if (fx->expect_move_category) check_str("moveCategory", fx->expect_move_category, jl_str(jl_get(doc, "moveCategory")));
    if (fx->expect_move_power >= 0) {
        const jl_value* power = jl_get(doc, "movePower");
        check_condition("movePower is numeric", jl_is_num(power));
        if (jl_is_num(power)) check_int("movePower", fx->expect_move_power, (long)jl_num(power));
    }
    if (fx->expect_defender_hp >= 0) {
        const jl_value* hp = jl_get(doc, "defenderMaxHP");
        check_condition("defenderMaxHP is numeric", jl_is_num(hp));
        if (jl_is_num(hp)) check_int("defenderMaxHP", fx->expect_defender_hp, (long)jl_num(hp));
    }

    int rolls[ROLL_COUNT];
    int count = response_rolls(doc, rolls);
    check_int("damage roll count", ROLL_COUNT, count);
    if (count == ROLL_COUNT) {
        for (int i = 0; i < ROLL_COUNT; i++) {
            if (fx->expect_rolls) {
                if (fx->expect_rolls[i] != rolls[i]) {
                    g_checks_failed++;
                    printf(ANSI_RED "  [FAIL] %s / damage[%d]: expected %d, got %d" ANSI_RESET "\n",
                           fx->name, i, fx->expect_rolls[i], rolls[i]);
                } else {
                    g_checks_passed++;
                }
            }
        }
        check_int("minDamage", fx->expect_rolls ? fx->expect_rolls[0] : rolls[0],
                  (long)jl_num(jl_get(doc, "minDamage")));
        check_int("maxDamage", fx->expect_rolls ? fx->expect_rolls[ROLL_COUNT - 1] : rolls[ROLL_COUNT - 1],
                  (long)jl_num(jl_get(doc, "maxDamage")));
        const jl_value* range = jl_get(doc, "range");
        check_condition("range is a 2-element array", jl_is_arr(range) && jl_len(range) == 2);
        if (jl_is_arr(range) && jl_len(range) == 2) {
            check_int("range[0]", rolls[0], (long)jl_num(jl_at(range, 0)));
            check_int("range[1]", rolls[ROLL_COUNT - 1], (long)jl_num(jl_at(range, 1)));
        }
    }

    jl_free(doc);
    free(raw);
}

/* Equivalent singles inputs must not merely be mutually consistent: each must
 * equal the independently derived singles damage vector. */
static void check_singles_equivalence(void) {
    static const char* labels[] = {
        "no field",
        "empty field",
        "gameType null",
        "gameType empty",
        "gameType \"singles\"",
        "gameType \"Singles\"",
    };
    static const char* requests[] = {
        "{" MACHAMP_VS_SNORLAX_HEAD ROCK_SLIDE_BODY "}",
        "{" MACHAMP_VS_SNORLAX_HEAD ROCK_SLIDE_BODY ",\"field\":{}}",
        "{" MACHAMP_VS_SNORLAX_HEAD ROCK_SLIDE_BODY ",\"field\":{\"gameType\":null}}",
        "{" MACHAMP_VS_SNORLAX_HEAD ROCK_SLIDE_BODY ",\"field\":{\"gameType\":\"\"}}",
        "{" MACHAMP_VS_SNORLAX_HEAD ROCK_SLIDE_BODY ",\"field\":{\"gameType\":\"singles\"}}",
        "{" MACHAMP_VS_SNORLAX_HEAD ROCK_SLIDE_BODY ",\"field\":{\"gameType\":\"Singles\"}}",
    };
    int reference[ROLL_COUNT];
    int have_reference = 0;

    for (int i = 0; i < (int)(sizeof(requests) / sizeof(requests[0])); i++) {
        g_fixture = labels[i];
        char* raw = js_calc_calculate(requests[i]);
        jl_value* doc = raw ? jl_parse(raw) : NULL;
        int rolls[ROLL_COUNT];
        if (!doc || !jl_is_bool(jl_get(doc, "success")) || !jl_bool(jl_get(doc, "success")) ||
            response_rolls(doc, rolls) != ROLL_COUNT) {
            g_checks_failed++;
            printf(ANSI_RED "  [FAIL] singles equivalence / %s: no usable damage vector" ANSI_RESET "\n", labels[i]);
            jl_free(doc);
            free(raw);
            continue;
        }
        for (int r = 0; r < ROLL_COUNT; r++) {
            check_int("equivalence roll vs independent expectation",
                      ROLLS_MACHAMP_ROCK_SLIDE_SINGLES[r], rolls[r]);
        }
        if (!have_reference) {
            memcpy(reference, rolls, sizeof(reference));
            have_reference = 1;
        } else {
            check_condition("damage vector identical to the other singles inputs",
                            memcmp(reference, rolls, sizeof(reference)) == 0);
        }
        jl_free(doc);
        free(raw);
    }
}

/* ------------------------------------------------------------------ */
/* Engine lifecycle and API error paths                                */
/* ------------------------------------------------------------------ */

static void check_api_error_paths(void) {
    g_fixture = "api_calculate_before_init";
    char* out = js_calc_calculate("{\"gen\":3}");
    check_condition("returns NULL before js_calc_init", out == NULL);
    free(out);

    g_fixture = "api_init_null_bundle";
    check_condition("js_calc_init(NULL) returns false", js_calc_init(NULL) == false);

    g_fixture = "api_init_invalid_bundle";
    check_condition("js_calc_init(broken bundle) returns false",
                    js_calc_init("this is not valid javascript {{{") == false);

    g_fixture = "api_calculate_after_failed_init";
    out = js_calc_calculate("{\"gen\":3}");
    check_condition("engine is not left half-initialised by a failed bundle load", out == NULL);
    free(out);

    g_fixture = "api_calculate_null_input";
    check_condition("js_calc_calculate(NULL) returns NULL", js_calc_calculate(NULL) == NULL);
}

static int load_bundle(void) {
    char* bundle_js = read_file_to_string(BUNDLE_PATH);
    if (!bundle_js) {
        printf(ANSI_RED "Failed to read %s - the shipped calculator bundle must be present\n" ANSI_RESET,
               BUNDLE_PATH);
        return 1;
    }
    printf("Initializing QuickJS with %s (%zu bytes)...\n", BUNDLE_PATH, strlen(bundle_js));
    bool ok = js_calc_init(bundle_js);
    free(bundle_js);
    if (!ok) {
        printf(ANSI_RED "js_calc_init failed: the shipped bundle did not evaluate\n" ANSI_RESET);
        return 1;
    }
    printf(ANSI_GREEN "  bundle evaluated by the pinned QuickJS runtime\n" ANSI_RESET);
    return 0;
}

int main(void) {
    printf("===================================================\n");
    printf("  DualDex QuickJS damage calculator suite (host)\n");
    printf("===================================================\n");

    check_api_error_paths();

    if (load_bundle() != 0) {
        printf(ANSI_RED "calculator suite cannot run without a working engine\n" ANSI_RESET);
        return 1;
    }

    printf("-- battle-format (field.gameType) contract, Gen III mechanics, error paths --\n");
    for (int i = 0; i < FIXTURE_COUNT; i++) run_fixture(&FIXTURES[i]);

    printf("-- equivalent singles inputs --\n");
    check_singles_equivalence();

    g_fixture = "api_cleanup";
    js_calc_cleanup();
    char* after = js_calc_calculate("{\"gen\":3}");
    check_condition("js_calc_cleanup leaves the engine uninitialised", after == NULL);
    free(after);

    printf("===================================================\n");
    printf("QuickJS calculator results: %d passed, %d failed\n", g_checks_passed, g_checks_failed);
    printf("===================================================\n");

    if (g_checks_failed != 0) {
        printf(ANSI_RED "QuickJS calculator suite FAILED\n" ANSI_RESET);
        return 1;
    }
    printf(ANSI_GREEN "QuickJS calculator suite passed\n" ANSI_RESET);
    return 0;
}
