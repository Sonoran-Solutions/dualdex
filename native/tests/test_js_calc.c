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

#include <limits.h>
#include <math.h>
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

/* Set only while a checker self-test runs, so the deliberate inner failure is
 * not printed as if a real fixture had failed. */
static int g_quiet_checks = 0;

/* ------------------------------------------------------------------ */
/* Checks: plain conditionals with counters, so nothing can be compiled
 * away (no NDEBUG-dependent assert()) and every failure names the fixture,
 * the field, the expected value, and the actual value.                 */
/* ------------------------------------------------------------------ */

static void check_condition(const char* field, int condition) {
    if (condition) {
        g_checks_passed++;
        return;
    }
    g_checks_failed++;
    if (!g_quiet_checks) {
        printf(ANSI_RED "  [FAIL] %s / %s: condition not satisfied" ANSI_RESET "\n", g_fixture, field);
    }
}

static void check_int(const char* field, long expected, long actual) {
    if (expected == actual) {
        g_checks_passed++;
        return;
    }
    g_checks_failed++;
    if (!g_quiet_checks) {
        printf(ANSI_RED "  [FAIL] %s / %s: expected %ld, got %ld" ANSI_RESET "\n",
               g_fixture, field, expected, actual);
    }
}

/* Integers a JSON response may carry through this oracle. Anything outside
 * this range could not have come from the calculator contract, and converting
 * it would be undefined. */
#define NUMBER_LIMIT_MAX 2147483647.0
#define NUMBER_LIMIT_MIN (-2147483648.0)

/* Returns NULL when the value can be compared exactly, otherwise the reason it
 * cannot. Finiteness, integrality, and representable bounds are checked BEFORE
 * any conversion, so a response that is off by a fraction - precisely what a
 * lost flooring or rounding step produces - cannot be truncated into a pass. */
static const char* number_reject_reason(const jl_value* value) {
    if (!value) return "missing";
    if (!jl_is_num(value)) return "not a JSON number";
    double number = jl_num(value);
    if (!isfinite(number)) return "not finite";
    if (number != floor(number)) return "not an integer (a truncated comparison would hide this)";
    if (number < NUMBER_LIMIT_MIN || number > NUMBER_LIMIT_MAX) return "outside the representable range";
    return NULL;
}

/* Mandatory shape validation for any asserted numeric field: the JSON number
 * type, a finite value, exact integrality, and representable bounds. Every
 * successful response must satisfy this for its damage elements, minDamage,
 * maxDamage, and range, whether or not a golden vector exists. Returns 1 when
 * the value is usable. */
static int check_number_shape(const char* field, const jl_value* value) {
    const char* reason = number_reject_reason(value);
    if (!reason) {
        g_checks_passed++;
        return 1;
    }
    g_checks_failed++;
    if (!g_quiet_checks) {
        if (value && jl_is_num(value)) {
            printf(ANSI_RED "  [FAIL] %s / %s: expected a finite integral number, got %.17g (%s)"
                   ANSI_RESET "\n", g_fixture, field, jl_num(value), reason);
        } else {
            printf(ANSI_RED "  [FAIL] %s / %s: expected a finite integral number, but the value is %s"
                   ANSI_RESET "\n", g_fixture, field, reason);
        }
    }
    return 0;
}

/* The exact numeric assertion: the expected integer is compared against the
 * ORIGINAL parsed value, never against a cast of it. Missing fields and wrong
 * types fail even when the expectation is zero, so jl_num()'s zero fallback can
 * never fake a match. */
static void check_number(const char* field, long expected, const jl_value* value) {
    if (!check_number_shape(field, value)) return;
    double actual = jl_num(value);
    if (actual == (double)expected) {
        g_checks_passed++;
        return;
    }
    g_checks_failed++;
    if (!g_quiet_checks) {
        printf(ANSI_RED "  [FAIL] %s / %s: expected %ld, got %.17g" ANSI_RESET "\n",
               g_fixture, field, expected, actual);
    }
}

static void check_double(const char* field, double expected, const jl_value* value) {
    if (!value || !jl_is_num(value)) {
        g_checks_failed++;
        if (!g_quiet_checks) {
            printf(ANSI_RED "  [FAIL] %s / %s: expected JSON number %.4f, but value is missing or not a number" ANSI_RESET "\n",
                   g_fixture, field, expected);
        }
        return;
    }
    double actual = jl_num(value);
    if (fabs(actual - expected) < 1e-6) {
        g_checks_passed++;
        return;
    }
    g_checks_failed++;
    if (!g_quiet_checks) {
        printf(ANSI_RED "  [FAIL] %s / %s: expected %.4f, got %.4f" ANSI_RESET "\n",
               g_fixture, field, expected, actual);
    }
}

static void check_str(const char* field, const char* expected, const char* actual) {
    if (actual && strcmp(expected, actual) == 0) {
        g_checks_passed++;
        return;
    }
    g_checks_failed++;
    if (!g_quiet_checks) {
        printf(ANSI_RED "  [FAIL] %s / %s: expected \"%s\", got \"%s\"" ANSI_RESET "\n",
               g_fixture, field, expected, actual ? actual : "(null)");
    }
}

/* Missing / non-string / empty values must be reported as such, not as a
 * default-looking comparison failure. */
static void check_string_present(const char* field, const jl_value* value) {
    if (jl_is_str(value) && jl_str(value)[0] != '\0') {
        g_checks_passed++;
        return;
    }
    g_checks_failed++;
    if (!g_quiet_checks) {
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
 * response does not carry a 16-element numeric array. Elements are NOT cast to
 * int: each one is asserted through check_number() against the original parsed
 * value, so a fractional or out-of-range element fails instead of being
 * truncated into a pass. */
#define ROLL_COUNT 16
static int response_rolls(const jl_value* doc, double out[ROLL_COUNT]) {
    const jl_value* damage = jl_get(doc, "damage");
    if (!jl_is_arr(damage)) return -1;
    int count = jl_len(damage);
    if (count != ROLL_COUNT) return -1;
    for (int i = 0; i < count; i++) {
        const jl_value* item = jl_at(damage, i);
        if (!jl_is_num(item)) return -1;
        out[i] = jl_num(item);
    }
    return count;
}

/* The damage array of a response, or NULL when it is missing or not an array.
 * Per-element validity is the caller's business (check_number reports it with
 * the offending index). */
static const jl_value* damage_array(const jl_value* doc) {
    const jl_value* damage = jl_get(doc, "damage");
    return jl_is_arr(damage) ? damage : NULL;
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

/* Machamp Flamethrower (Fire 95, special) vs Snorlax in Rain. Rain halves a
 * Fire attack's damage, on the same pre-+2 value Light Screen halves:
 *   floor(22*95*85/130) = 1366 -> floor(1366/50) = 27; floor(27/2) = 13 -> +2 = 15
 * Deliberately the same vector as the Light Screen fixture above, since both are
 * a x1/2 attack-form modifier in singles - but reached through the weather path,
 * which the engine reads with an EXACT string compare (`weathers.includes(...)`).
 * "rain" and "RAIN" therefore produce the no-weather vector 24-29 instead, which
 * is why CalcCapabilityPolicy canonicalises the spelling before authorising. */
static const int ROLLS_FLAMETHROWER_IN_RAIN[ROLL_COUNT] =
    {12, 12, 13, 13, 13, 13, 13, 13, 13, 14, 14, 14, 14, 14, 14, 15};

/* Critical hits double the attack form AFTER the +2, so the roll vector is
 * exactly twice the non-critical Rock Slide vector:
 *   floor(22*75*150/85) = 2911 -> floor(2911/50) = 58 -> +2 = 60 -> x2 = 120
 * Rock vs Normal is 1.0, so no type step follows. The Generation III multiplier
 * is x2, not the modern x1.5 (which would give 76-90). */
static const int ROLLS_MACHAMP_ROCK_SLIDE_SINGLES_CRIT[ROLL_COUNT] =
    {102, 103, 104, 105, 106, 108, 109, 110, 111, 112, 114, 115, 116, 117, 118, 120};

/* Machamp Rock Slide while burned and holding Guts. Guts cancels the burn's
 * halving and multiplies attack by 1.5 in the same step, and the attack form is
 * floored after both:
 *   A = 150, burn + Guts -> floor(150 * 1.5) = 225
 *   floor(22*75*225/85) = 4367 -> floor(4367/50) = 87 -> +2 = 89
 * (The halving is skipped, not applied and then undone: 150/2 = 75 does not
 * appear anywhere in the result.) */
static const int ROLLS_MACHAMP_ROCK_SLIDE_GUTS_BURN[ROLL_COUNT] =
    {75, 76, 77, 78, 79, 80, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89};

typedef struct {
    const char* name;              /* fixture id, reported on failure */
    const char* request;           /* exact JSON request sent to the engine */
    int expect_success;            /* 1: success must be true, 0: must be false */
    const char* expect_error_contains; /* required substring when expect_success == 0 */
    const char* expect_move_type;
    const char* expect_move_category;
    int expect_move_power;         /* < 0 to skip */
    int expect_defender_hp;        /* < 0 to skip */
    const int* expect_rolls;       /* 16 rolls for exact equality, or NULL to check
                                    * structure and internal consistency only
                                    * (elements, min/max and range are still
                                    * validated; only golden equality is optional) */
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
    {
        "gen3_spread_move_proto_rejected",
        "{" MACHAMP_VS_SNORLAX_HEAD ROCK_SLIDE_BODY ",\"field\":{\"gameType\":\"__proto__\"}}",
        0, "gameType", NULL, NULL, -1, -1, NULL,
        "an inherited Object.prototype name must not pass the format whitelist: before the "
        "own-property-safe lookup it was accepted and silently selected the doubles path (26-31)"
    },
    {
        "gen3_spread_move_constructor_rejected",
        "{" MACHAMP_VS_SNORLAX_HEAD ROCK_SLIDE_BODY ",\"field\":{\"gameType\":\"constructor\"}}",
        0, "gameType", NULL, NULL, -1, -1, NULL,
        "inherited property names resolve to functions on a plain object lookup"
    },
    {
        "gen3_spread_move_to_string_rejected",
        "{" MACHAMP_VS_SNORLAX_HEAD ROCK_SLIDE_BODY ",\"field\":{\"gameType\":\"toString\"}}",
        0, "gameType", NULL, NULL, -1, -1, NULL,
        "inherited property names resolve to functions on a plain object lookup"
    },
    {
        "gen3_spread_move_has_own_property_rejected",
        "{" MACHAMP_VS_SNORLAX_HEAD ROCK_SLIDE_BODY ",\"field\":{\"gameType\":\"hasOwnProperty\"}}",
        0, "gameType", NULL, NULL, -1, -1, NULL,
        "inherited property names resolve to functions on a plain object lookup"
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

    /* --- critical hits -------------------------------------------- *
     * The Crit checkbox on the Calc screen is a live production input, and the multiplier is the
     * one damage constant Heart & Soul 2.0.5 deliberately keeps at its Generation III value
     * (B_CRIT_MULTIPLIER == GEN_3 in the pinned upstream source), so a drift to the modern x1.5
     * would silently understate every critical hit in both supported builds. */
    {
        "gen3_crit_doubles_the_attack_form",
        "{" MACHAMP_VS_SNORLAX_HEAD "\"move\":{\"name\":\"Rock Slide\",\"isCrit\":true},"
        "\"field\":{\"gameType\":\"Singles\"}}",
        1, NULL, "Rock", "Physical", 75, 235, ROLLS_MACHAMP_ROCK_SLIDE_SINGLES_CRIT,
        "Gen III critical hits double the attack form after +2: 102-120, not the modern 76-90"
    },

    /* --- weather is an exact-match input --------------------------- *
     * The engine reads weather with `weathers.includes(this.weather)`, so a spelling it does not
     * recognise is not an error - it behaves as no weather at all. The canonical spelling is
     * therefore part of the accepted-input contract, not cosmetic, and the capability policy
     * rewrites accepted spelling to these values before authorising a request. */
    {
        "gen3_rain_halves_a_fire_attack",
        "{" MACHAMP_VS_SNORLAX_HEAD "\"move\":{\"name\":\"Flamethrower\"},"
        "\"field\":{\"gameType\":\"Singles\",\"weather\":\"Rain\"}}",
        1, NULL, "Fire", "Special", 95, 235, ROLLS_FLAMETHROWER_IN_RAIN,
        "canonical 'Rain' is applied: a Fire attack is halved to 12-15"
    },
    {
        "gen3_lowercase_rain_is_ignored_by_the_engine",
        "{" MACHAMP_VS_SNORLAX_HEAD "\"move\":{\"name\":\"Flamethrower\"},"
        "\"field\":{\"gameType\":\"Singles\",\"weather\":\"rain\"}}",
        1, NULL, "Fire", "Special", 95, 235, ROLLS_FLAMETHROWER_NO_THICK_FAT,
        "negative control for the canonicalisation above: 'rain' is silently treated as NO weather "
        "and returns the unmodified 24-29, so a validator that accepted it case-insensitively while "
        "forwarding it verbatim would approve a request the engine reads differently"
    },

    /* --- ability input contract (issue #9) ------------------------ *
     * CalcCapabilityPolicy keeps an ability whitelist because this engine IGNORES an ability it
     * does not model instead of erroring - a silent wrong-number path. Both halves of that are
     * pinned here: a whitelisted ability must change the number, and an ability outside the
     * Generation III pipeline must NOT be doing what the caller assumes it does. */
    {
        "gen3_explicit_guts_boosts_a_statused_attacker",
        "{\"gen\":3,"
        "\"attacker\":{\"species\":\"Machamp\",\"level\":50,\"nature\":\"Hardy\",\"ability\":\"Guts\","
        "\"status\":\"brn\"," IVS_MAX "," EVS_ZERO "},"
        "\"defender\":{\"species\":\"Snorlax\",\"level\":50,\"nature\":\"Hardy\"," IVS_MAX "," EVS_ZERO "},"
        ROCK_SLIDE_BODY ",\"field\":{\"gameType\":\"Singles\"}}",
        1, NULL, "Rock", "Physical", 75, 235, ROLLS_MACHAMP_ROCK_SLIDE_GUTS_BURN,
        "an explicitly supplied whitelisted ability reaches the pipeline: Guts cancels the burn halving and adds x1.5"
    },
    {
        "gen3_unmodelled_ability_is_silently_ignored",
        "{\"gen\":3,"
        "\"attacker\":{\"species\":\"Machamp\",\"level\":50,\"nature\":\"Hardy\"," IVS_MAX "," EVS_ZERO "},"
        "\"defender\":{\"species\":\"Snorlax\",\"level\":50,\"nature\":\"Hardy\",\"ability\":\"Multiscale\","
        IVS_MAX "," EVS_ZERO "},"
        ROCK_SLIDE_BODY ",\"field\":{\"gameType\":\"Singles\"}}",
        1, NULL, "Rock", "Physical", 75, 235, ROLLS_MACHAMP_ROCK_SLIDE_SINGLES,
        "proof of the silent-degradation path the policy gates: a post-Gen-III defender ability "
        "returns the unmodified number with no error, so the boundary must refuse it rather than "
        "let it be reported as verified"
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

/* Validates a SUCCESSFUL response against a fixture.
 *
 * This is the only response-validation path: run_fixture() uses it for real
 * engine responses, and the self-tests feed it synthetic responses so that
 * every branch - including the no-golden-vector branch used by the Gen 8 smoke
 * fixture - is exercised negatively as well.
 *
 * Mandatory for every successful response:
 *   - every damage element is a finite integral number within range;
 *   - minDamage and maxDamage are present and valid;
 *   - minDamage == damage[0], maxDamage == damage[last], range == both.
 * Additional when a golden vector exists: exact equality with the golden.
 */
static void validate_success_response(const calc_fixture* fx, const jl_value* doc) {
    if (fx->expect_move_type) check_str("moveType", fx->expect_move_type, jl_str(jl_get(doc, "moveType")));
    if (fx->expect_move_category) check_str("moveCategory", fx->expect_move_category, jl_str(jl_get(doc, "moveCategory")));
    if (fx->expect_move_power >= 0) {
        check_number("movePower", fx->expect_move_power, jl_get(doc, "movePower"));
    }
    if (fx->expect_defender_hp >= 0) {
        check_number("defenderMaxHP", fx->expect_defender_hp, jl_get(doc, "defenderMaxHP"));
    }

    const jl_value* damage = damage_array(doc);
    check_condition("damage is an array", damage != NULL);
    check_int("damage roll count", ROLL_COUNT, damage ? jl_len(damage) : -1);
    if (!damage || jl_len(damage) != ROLL_COUNT) return;

    /* Every element is validated, with or without a golden vector. */
    int elements_usable = 1;
    for (int i = 0; i < ROLL_COUNT; i++) {
        char field[32];
        snprintf(field, sizeof(field), "damage[%d]", i);
        const jl_value* element = jl_at(damage, i);
        if (!check_number_shape(field, element)) {
            elements_usable = 0;
            continue;
        }
        if (fx->expect_rolls) check_number(field, fx->expect_rolls[i], element);
    }

    const double first = elements_usable ? jl_num(jl_at(damage, 0)) : 0.0;
    const double last = elements_usable ? jl_num(jl_at(damage, ROLL_COUNT - 1)) : 0.0;

    if (fx->expect_rolls) {
        check_number("minDamage", fx->expect_rolls[0], jl_get(doc, "minDamage"));
        check_number("maxDamage", fx->expect_rolls[ROLL_COUNT - 1], jl_get(doc, "maxDamage"));
    } else if (elements_usable) {
        /* No golden: min/max must still match the observed, validated bounds. */
        check_number("minDamage", (long)first, jl_get(doc, "minDamage"));
        check_number("maxDamage", (long)last, jl_get(doc, "maxDamage"));
    } else {
        /* The vector itself already failed; still require present, valid
         * min/max rather than letting missing fields pass unnoticed. */
        check_number_shape("minDamage", jl_get(doc, "minDamage"));
        check_number_shape("maxDamage", jl_get(doc, "maxDamage"));
    }

    const jl_value* range = jl_get(doc, "range");
    check_condition("range is a 2-element array", jl_is_arr(range) && jl_len(range) == 2);
    if (jl_is_arr(range) && jl_len(range) == 2) {
        const jl_value* low = jl_at(range, 0);
        const jl_value* high = jl_at(range, 1);
        if (elements_usable) {
            check_number("range[0] vs damage[0]", (long)first, low);
            check_number("range[1] vs damage[last]", (long)last, high);
        } else {
            check_number_shape("range[0]", low);
            check_number_shape("range[1]", high);
        }
    }
}

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

    validate_success_response(fx, doc);

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
    double reference[ROLL_COUNT];
    int have_reference = 0;

    for (int i = 0; i < (int)(sizeof(requests) / sizeof(requests[0])); i++) {
        g_fixture = labels[i];
        char* raw = js_calc_calculate(requests[i]);
        jl_value* doc = raw ? jl_parse(raw) : NULL;
        const jl_value* damage = doc ? damage_array(doc) : NULL;
        if (!doc || !jl_is_bool(jl_get(doc, "success")) || !jl_bool(jl_get(doc, "success")) ||
            !damage || jl_len(damage) != ROLL_COUNT) {
            g_checks_failed++;
            if (!g_quiet_checks) {
                printf(ANSI_RED "  [FAIL] singles equivalence / %s: no usable damage vector" ANSI_RESET "\n", labels[i]);
            }
            jl_free(doc);
            free(raw);
            continue;
        }
        for (int r = 0; r < ROLL_COUNT; r++) {
            check_number("equivalence roll vs independent expectation",
                         ROLLS_MACHAMP_ROCK_SLIDE_SINGLES[r], jl_at(damage, r));
        }
        /* Cross-input identity only compares values that passed the oracle. */
        double rolls[ROLL_COUNT];
        int validated = response_rolls(doc, rolls) == ROLL_COUNT;
        for (int r = 0; validated && r < ROLL_COUNT; r++) {
            if (number_reject_reason(jl_at(damage, r)) != NULL) validated = 0;
        }
        check_condition("damage vector is comparable", validated);
        if (!validated) {
            jl_free(doc);
            free(raw);
            continue;
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
/* Checker and parser self-tests                                       */
/*                                                                     */
/* The assertion helpers and the test-only JSON reader are part of the  */
/* fail-closed contract, so they are tested alongside the engine. Each  */
/* "rejected" case below MUST make its checker fail; if a future change */
/* reintroduces truncation, a zero fallback, or lenient parsing, these  */
/* cases fail ./ci.sh test instead of quietly weakening every golden.   */
/* They are opposed by "accepted" cases so a checker that rejected      */
/* everything could not pass this section either.                       */
/* ------------------------------------------------------------------ */

/* Runs a real scalar check against a synthetic response and returns 1 only
 * when it failed exactly once and passed nothing. Counters are restored so the
 * self-tests do not distort the suite totals. */
static int checker_rejects_scalar(const char* json, const char* key, long expected) {
    const int failed_before = g_checks_failed;
    const int passed_before = g_checks_passed;
    jl_value* doc = jl_parse(json);
    g_quiet_checks = 1;
    check_number(key, expected, doc ? jl_get(doc, key) : NULL);
    g_quiet_checks = 0;
    const int rejected = (g_checks_failed > failed_before) && (g_checks_passed == passed_before);
    g_checks_failed = failed_before;
    g_checks_passed = passed_before;
    jl_free(doc);
    return rejected;
}

static int checker_accepts_scalar(const char* json, const char* key, long expected) {
    const int failed_before = g_checks_failed;
    const int passed_before = g_checks_passed;
    jl_value* doc = jl_parse(json);
    g_quiet_checks = 1;
    check_number(key, expected, doc ? jl_get(doc, key) : NULL);
    g_quiet_checks = 0;
    const int accepted = (g_checks_failed == failed_before) && (g_checks_passed > passed_before);
    g_checks_failed = failed_before;
    g_checks_passed = passed_before;
    jl_free(doc);
    return accepted;
}

/* Same, for one element of an array field. */
static int checker_rejects_element(const char* json, const char* key, int index, long expected) {
    const int failed_before = g_checks_failed;
    const int passed_before = g_checks_passed;
    jl_value* doc = jl_parse(json);
    const jl_value* array = doc ? jl_get(doc, key) : NULL;
    g_quiet_checks = 1;
    check_number(key, expected, (array && jl_is_arr(array)) ? jl_at(array, index) : NULL);
    g_quiet_checks = 0;
    const int rejected = (g_checks_failed > failed_before) && (g_checks_passed == passed_before);
    g_checks_failed = failed_before;
    g_checks_passed = passed_before;
    jl_free(doc);
    return rejected;
}

/* The reviewer's reproduction: every numeric field off by +0.9. The previous
 * oracle cast these to int/long first and accepted all of them. */
#define FRACTIONAL_RESPONSE \
    "{\"success\":true," \
    "\"damage\":[51.9,51.9,52.9,52.9,53.9,54.9,54.9,55.9,55.9,56.9,57.9,57.9,58.9,58.9,59.9,60.9]," \
    "\"minDamage\":51.9,\"maxDamage\":60.9,\"range\":[51.9,60.9]," \
    "\"movePower\":75.9,\"defenderMaxHP\":235.9}"

static void check_oracle_self_tests(void) {
    g_fixture = "oracle_self_test";

    /* Fractional values must be rejected everywhere they are asserted. */
    check_condition("fractional first damage roll is rejected",
                    checker_rejects_element(FRACTIONAL_RESPONSE, "damage", 0, 51));
    check_condition("fractional last damage roll is rejected",
                    checker_rejects_element(FRACTIONAL_RESPONSE, "damage", 15, 60));
    check_condition("fractional range lower bound is rejected",
                    checker_rejects_element(FRACTIONAL_RESPONSE, "range", 0, 51));
    check_condition("fractional range upper bound is rejected",
                    checker_rejects_element(FRACTIONAL_RESPONSE, "range", 1, 60));
    check_condition("fractional minDamage is rejected",
                    checker_rejects_scalar(FRACTIONAL_RESPONSE, "minDamage", 51));
    check_condition("fractional maxDamage is rejected",
                    checker_rejects_scalar(FRACTIONAL_RESPONSE, "maxDamage", 60));
    check_condition("fractional movePower is rejected",
                    checker_rejects_scalar(FRACTIONAL_RESPONSE, "movePower", 75));
    check_condition("fractional defenderMaxHP is rejected",
                    checker_rejects_scalar(FRACTIONAL_RESPONSE, "defenderMaxHP", 235));

    /* A fractional element inside an otherwise integral vector. */
    check_condition("fractional element in an integral roll vector is rejected",
                    checker_rejects_element(
                        "{\"damage\":[51,51,52,52,53,54,54,55,55,56,57,57,58,58,59,60.9]}",
                        "damage", 15, 60));

    /* Missing fields must fail even against a zero expectation: jl_num()'s
     * zero fallback must never fake a match. */
    check_condition("missing field is rejected even when zero is expected",
                    checker_rejects_scalar("{}", "minDamage", 0));
    check_condition("missing array element is rejected",
                    checker_rejects_element("{\"damage\":[51,51]}", "damage", 5, 53));

    /* Wrong types. */
    check_condition("string where a number is expected is rejected",
                    checker_rejects_scalar("{\"minDamage\":\"51\"}", "minDamage", 51));
    check_condition("boolean where a number is expected is rejected",
                    checker_rejects_scalar("{\"minDamage\":true}", "minDamage", 1));
    check_condition("null where a number is expected is rejected",
                    checker_rejects_scalar("{\"minDamage\":null}", "minDamage", 0));
    check_condition("array where a number is expected is rejected",
                    checker_rejects_scalar("{\"minDamage\":[51]}", "minDamage", 51));
    check_condition("object where a number is expected is rejected",
                    checker_rejects_scalar("{\"minDamage\":{\"value\":51}}", "minDamage", 51));
    check_condition("non-numeric array element is rejected",
                    checker_rejects_element("{\"damage\":[\"51\",51]}", "damage", 0, 51));

    /* Out-of-range values that could not come from the engine contract. */
    check_condition("value above the representable range is rejected",
                    checker_rejects_scalar("{\"minDamage\":2147483648}", "minDamage", 2147483647));
    check_condition("value below the representable range is rejected",
                    checker_rejects_scalar("{\"minDamage\":-2147483649}", "minDamage", -2147483648));

    /* Positive controls: a checker that rejected everything would fail here. */
    check_condition("exact integral value is accepted",
                    checker_accepts_scalar("{\"minDamage\":51}", "minDamage", 51));
    check_condition("exact zero is accepted when zero is expected",
                    checker_accepts_scalar("{\"minDamage\":0}", "minDamage", 0));
    check_condition("negative integral value is accepted",
                    checker_accepts_scalar("{\"minDamage\":-1}", "minDamage", -1));
    check_condition("integral value equal to the range limit is accepted",
                    checker_accepts_scalar("{\"minDamage\":2147483647}", "minDamage", 2147483647));

    /* Wrong-length roll vectors must not satisfy the full-vector assertion. */
    jl_value* short_vector = jl_parse("{\"damage\":[51,51,52,52,53,54,54,55,55,56,57,57,58,58,59]}");
    check_condition("a 15-element damage vector is rejected",
                    short_vector && jl_len(damage_array(short_vector)) != ROLL_COUNT);
    jl_free(short_vector);
    jl_value* string_vector = jl_parse("{\"damage\":\"51\"}");
    check_condition("a non-array damage field is rejected", damage_array(string_vector) == NULL);
    jl_free(string_vector);
}

/* ------------------------------------------------------------------ */
/* Smoke-path (no golden vector) response validation                   */
/*                                                                     */
/* gen8_generic_engine_smoke carries no golden damage vector, so its    */
/* branch of validate_success_response() must still validate every      */
/* damage element, minDamage, maxDamage, and range. These self-tests    */
/* drive that exact function with synthetic responses, so the branch is */
/* covered negatively as well as the helper.                           */
/* ------------------------------------------------------------------ */

/* Mirrors the smoke fixture's metadata: no golden vector, and expectations
 * that match SYNTHETIC_SMOKE_OK. The request string is unused by
 * validate_success_response. */
static const calc_fixture SMOKE_PROBE_FIXTURE = {
    "smoke_path_self_test",
    "{}",
    1, NULL, "Fairy", "Special", 95, 166, NULL,
    "synthetic metadata for the no-golden-vector validation path"
};

/* Deliberately arbitrary values: inputs for the checker, not Gen 8 goldens.
 * min/max/range are internally consistent with the vector. */
#define SYNTHETIC_SMOKE_VECTOR \
    "10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25"
#define SYNTHETIC_SMOKE_OK \
    "{\"success\":true,\"damage\":[" SYNTHETIC_SMOKE_VECTOR "]," \
    "\"minDamage\":10,\"maxDamage\":25,\"range\":[10,25]," \
    "\"moveType\":\"Fairy\",\"moveCategory\":\"Special\",\"movePower\":95," \
    "\"defenderMaxHP\":166}"

/* Runs the real validation path (no golden vector) and reports whether it
 * rejected the response. */
static int smoke_response_rejected(const char* json) {
    jl_value* doc = jl_parse(json);
    if (!doc) return 1; /* malformed input cannot pass validation either */
    const int failed_before = g_checks_failed;
    const int passed_before = g_checks_passed;
    g_quiet_checks = 1;
    validate_success_response(&SMOKE_PROBE_FIXTURE, doc);
    g_quiet_checks = 0;
    const int rejected = (g_checks_failed > failed_before);
    g_checks_failed = failed_before;
    g_checks_passed = passed_before;
    jl_free(doc);
    return rejected;
}

static int smoke_response_accepted(const char* json) {
    jl_value* doc = jl_parse(json);
    if (!doc) return 0;
    const int failed_before = g_checks_failed;
    const int passed_before = g_checks_passed;
    g_quiet_checks = 1;
    validate_success_response(&SMOKE_PROBE_FIXTURE, doc);
    g_quiet_checks = 0;
    const int clean = (g_checks_failed == failed_before) && (g_checks_passed > passed_before);
    g_checks_failed = failed_before;
    g_checks_passed = passed_before;
    jl_free(doc);
    return clean;
}

static void check_smoke_path_self_tests(void) {
    g_fixture = "smoke_path_self_test";

    /* Positive control: an internally consistent response must pass. */
    check_condition("internally consistent response is accepted",
                    smoke_response_accepted(SYNTHETIC_SMOKE_OK));

    /* minDamage / maxDamage are mandatory and must match the observed bounds. */
    check_condition("wrong minDamage/maxDamage is rejected",
                    smoke_response_rejected(
                        "{\"success\":true,\"damage\":[" SYNTHETIC_SMOKE_VECTOR "],"
                        "\"minDamage\":999,\"maxDamage\":1000,\"range\":[10,25],"
                        "\"moveType\":\"Fairy\",\"moveCategory\":\"Special\",\"movePower\":95,"
                        "\"defenderMaxHP\":166}"));
    check_condition("missing minDamage/maxDamage is rejected",
                    smoke_response_rejected(
                        "{\"success\":true,\"damage\":[" SYNTHETIC_SMOKE_VECTOR "],"
                        "\"range\":[10,25],\"moveType\":\"Fairy\",\"moveCategory\":\"Special\","
                        "\"movePower\":95,\"defenderMaxHP\":166}"));
    check_condition("string minDamage/maxDamage is rejected",
                    smoke_response_rejected(
                        "{\"success\":true,\"damage\":[" SYNTHETIC_SMOKE_VECTOR "],"
                        "\"minDamage\":\"oops\",\"maxDamage\":\"oops\",\"range\":[10,25],"
                        "\"moveType\":\"Fairy\",\"moveCategory\":\"Special\",\"movePower\":95,"
                        "\"defenderMaxHP\":166}"));
    check_condition("null minDamage/maxDamage is rejected",
                    smoke_response_rejected(
                        "{\"success\":true,\"damage\":[" SYNTHETIC_SMOKE_VECTOR "],"
                        "\"minDamage\":null,\"maxDamage\":null,\"range\":[10,25],"
                        "\"moveType\":\"Fairy\",\"moveCategory\":\"Special\",\"movePower\":95,"
                        "\"defenderMaxHP\":166}"));

    /* Interior damage elements are validated even without a golden vector. */
    check_condition("string interior damage element is rejected",
                    smoke_response_rejected(
                        "{\"success\":true,\"damage\":[10,11,12,13,14,\"oops\",16,17,18,19,20,21,22,23,24,25],"
                        "\"minDamage\":10,\"maxDamage\":25,\"range\":[10,25],"
                        "\"moveType\":\"Fairy\",\"moveCategory\":\"Special\",\"movePower\":95,"
                        "\"defenderMaxHP\":166}"));
    check_condition("fractional interior damage element is rejected",
                    smoke_response_rejected(
                        "{\"success\":true,\"damage\":[10,11,12,13,14,15.9,16,17,18,19,20,21,22,23,24,25],"
                        "\"minDamage\":10,\"maxDamage\":25,\"range\":[10,25],"
                        "\"moveType\":\"Fairy\",\"moveCategory\":\"Special\",\"movePower\":95,"
                        "\"defenderMaxHP\":166}"));
    check_condition("missing interior damage element is rejected",
                    smoke_response_rejected(
                        "{\"success\":true,\"damage\":[10,11,12,13,14],"
                        "\"minDamage\":10,\"maxDamage\":25,\"range\":[10,25],"
                        "\"moveType\":\"Fairy\",\"moveCategory\":\"Special\",\"movePower\":95,"
                        "\"defenderMaxHP\":166}"));
    check_condition("damage vector that is not an array is rejected",
                    smoke_response_rejected(
                        "{\"success\":true,\"damage\":\"10-25\",\"minDamage\":10,\"maxDamage\":25,"
                        "\"range\":[10,25],\"moveType\":\"Fairy\",\"moveCategory\":\"Special\","
                        "\"movePower\":95,\"defenderMaxHP\":166}"));

    /* range is mandatory and must agree with the validated vector. */
    check_condition("range inconsistent with the damage vector is rejected",
                    smoke_response_rejected(
                        "{\"success\":true,\"damage\":[" SYNTHETIC_SMOKE_VECTOR "],"
                        "\"minDamage\":10,\"maxDamage\":25,\"range\":[10,99],"
                        "\"moveType\":\"Fairy\",\"moveCategory\":\"Special\",\"movePower\":95,"
                        "\"defenderMaxHP\":166}"));
    check_condition("missing range is rejected",
                    smoke_response_rejected(
                        "{\"success\":true,\"damage\":[" SYNTHETIC_SMOKE_VECTOR "],"
                        "\"minDamage\":10,\"maxDamage\":25,"
                        "\"moveType\":\"Fairy\",\"moveCategory\":\"Special\",\"movePower\":95,"
                        "\"defenderMaxHP\":166}"));

    /* The scalar metadata of the smoke branch stays validated as well. */
    check_condition("wrong movePower is rejected",
                    smoke_response_rejected(
                        "{\"success\":true,\"damage\":[" SYNTHETIC_SMOKE_VECTOR "],"
                        "\"minDamage\":10,\"maxDamage\":25,\"range\":[10,25],"
                        "\"moveType\":\"Fairy\",\"moveCategory\":\"Special\",\"movePower\":75,"
                        "\"defenderMaxHP\":166}"));
    check_condition("wrong moveType is rejected",
                    smoke_response_rejected(
                        "{\"success\":true,\"damage\":[" SYNTHETIC_SMOKE_VECTOR "],"
                        "\"minDamage\":10,\"maxDamage\":25,\"range\":[10,25],"
                        "\"moveType\":\"Normal\",\"moveCategory\":\"Special\",\"movePower\":95,"
                        "\"defenderMaxHP\":166}"));
}

static void check_parser_self_tests(void) {
    g_fixture = "parser_self_test";

    /* Invalid JSON number spellings the previous lenient parser accepted. */
    check_condition("leading zero is rejected", jl_parse("{\"minDamage\":051}") == NULL);
    check_condition("trailing decimal point is rejected", jl_parse("{\"minDamage\":51.}") == NULL);
    check_condition("bare fraction is rejected", jl_parse("{\"minDamage\":-.1}") == NULL);
    check_condition("leading plus is rejected", jl_parse("{\"minDamage\":+5}") == NULL);
    check_condition("exponent without digits is rejected", jl_parse("{\"minDamage\":1e}") == NULL);
    check_condition("lone minus is rejected", jl_parse("{\"minDamage\":-}") == NULL);
    check_condition("non-finite number is rejected", jl_parse("{\"minDamage\":1e999}") == NULL);
    check_condition("not-a-number literal is rejected", jl_parse("{\"minDamage\":NaN}") == NULL);

    /* Embedded NULs must not alias a shorter value or key. */
    check_condition("NUL inside a string value is rejected",
                    jl_parse("{\"moveType\":\"Rock\\u0000not-Rock\"}") == NULL);
    check_condition("NUL inside an object key is rejected",
                    jl_parse("{\"success\\u0000not-success\":true}") == NULL);

    /* Structural nonsense stays rejected. */
    check_condition("trailing comma is rejected", jl_parse("{\"a\":1,}") == NULL);
    check_condition("missing colon is rejected", jl_parse("{\"a\" 1}") == NULL);
    check_condition("unterminated array is rejected", jl_parse("[1,2") == NULL);
    check_condition("trailing garbage is rejected", jl_parse("{\"a\":1}garbage") == NULL);

    /* Positive controls for the supported subset. */
    jl_value* doc = jl_parse(
        "{\"a\":0,\"b\":-0.5,\"c\":1e3,\"d\":1.5e-3,\"e\":-2147483648,"
        "\"f\":\"Rock\",\"g\":[1,2,3],\"h\":{\"i\":true},\"j\":null,\"k\":\"caf\\u00e9\"}");
    check_condition("valid document is accepted", doc != NULL);
    if (doc) {
        check_number("integer zero", 0, jl_get(doc, "a"));
        check_condition("fractional value parses", jl_is_num(jl_get(doc, "b")) &&
                        jl_num(jl_get(doc, "b")) == -0.5);
        check_number("exponent form", 1000, jl_get(doc, "c"));
        check_condition("negative exponent form parses", jl_is_num(jl_get(doc, "d")) &&
                        jl_num(jl_get(doc, "d")) == 0.0015);
        check_number("minimum representable integer", -2147483648L, jl_get(doc, "e"));
        check_str("string value", "Rock", jl_str(jl_get(doc, "f")));
        check_int("array length", 3, jl_len(jl_get(doc, "g")));
        check_condition("nested boolean", jl_is_bool(jl_get(jl_get(doc, "h"), "i")) &&
                        jl_bool(jl_get(jl_get(doc, "h"), "i")) == 1);
        check_condition("escape decodes to UTF-8", jl_is_str(jl_get(doc, "k")) &&
                        strcmp(jl_str(jl_get(doc, "k")), "caf\xc3\xa9") == 0);
    }
    jl_free(doc);
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

static void check_gap_b_data_overrides(void) {
    g_fixture = "gap_b_species_base_stat_override";
    {
        /*
         * Test A: Species base-stat override is consumed.
         *
         * Attacker: Arbok L50 Hardy (IV 31, EV 0).
         * Defender: Swampert L50 Hardy (IV 31, EV 0; HP 175, Def 110).
         * Move: Sludge Bomb (Poison, 90 BP, physical in Gen 3; Ground resists -> x0.5).
         *
         * Vanilla Gen 3 Arbok base Atk: 85 -> Atk stat 105.
         *   Damage: [24, 24, 25, 25, 25, 26, 26, 26, 26, 27, 27, 27, 28, 28, 28, 29]
         *
         * H&S 2.0.5 Arbok base Atk: 95 (authoritative H&S increase) -> Atk stat 115.
         *   Damage: [27, 27, 27, 28, 28, 28, 29, 29, 29, 30, 30, 30, 31, 31, 31, 32]
         *
         * The override must strictly increase damage in accordance with the supplied base stat.
         */
        const char* req_vanilla =
            "{\"gen\":3,"
            "\"attacker\":{\"species\":\"Arbok\",\"level\":50,\"nature\":\"Hardy\"," IVS_MAX "," EVS_ZERO "},"
            "\"defender\":{\"species\":\"Swampert\",\"level\":50,\"nature\":\"Hardy\"," IVS_MAX "," EVS_ZERO "},"
            "\"move\":{\"name\":\"Sludge Bomb\"}}";

        const char* req_override =
            "{\"gen\":3,"
            "\"attacker\":{\"species\":\"Arbok\",\"level\":50,\"nature\":\"Hardy\"," IVS_MAX "," EVS_ZERO ","
            "\"overrides\":{\"baseStats\":{\"hp\":60,\"atk\":95,\"def\":69,\"spa\":65,\"spd\":79,\"spe\":80}}},"
            "\"defender\":{\"species\":\"Swampert\",\"level\":50,\"nature\":\"Hardy\"," IVS_MAX "," EVS_ZERO "},"
            "\"move\":{\"name\":\"Sludge Bomb\"}}";

        char* out_vanilla = js_calc_calculate(req_vanilla);
        char* out_override = js_calc_calculate(req_override);

        check_condition("vanilla Arbok calculation succeeds", out_vanilla != NULL);
        check_condition("overridden Arbok calculation succeeds", out_override != NULL);

        if (out_vanilla && out_override) {
            jl_value* doc_v = jl_parse(out_vanilla);
            jl_value* doc_o = jl_parse(out_override);

            check_condition("vanilla response is valid JSON", doc_v != NULL);
            check_condition("overridden response is valid JSON", doc_o != NULL);

            if (doc_v && doc_o) {
                check_number("vanilla minDamage", 24, jl_get(doc_v, "minDamage"));
                check_number("vanilla maxDamage", 29, jl_get(doc_v, "maxDamage"));

                check_number("overridden minDamage", 27, jl_get(doc_o, "minDamage"));
                check_number("overridden maxDamage", 32, jl_get(doc_o, "maxDamage"));

                check_condition("overridden base stat strictly increases min damage",
                                jl_num(jl_get(doc_o, "minDamage")) > jl_num(jl_get(doc_v, "minDamage")));
                check_condition("overridden base stat strictly increases max damage",
                                jl_num(jl_get(doc_o, "maxDamage")) > jl_num(jl_get(doc_v, "maxDamage")));
            }
            jl_free(doc_v);
            jl_free(doc_o);
        }
        free(out_vanilla);
        free(out_override);
    }

    g_fixture = "gap_b_species_type_override";
    {
        /*
         * Test B: Species type override is consumed.
         * Charizard default typing: Fire / Flying.
         * Override typing: Fire / Dragon.
         * Assert returned attackerTypes array contains ["Fire", "Dragon"].
         */
        const char* req =
            "{\"gen\":3,"
            "\"attacker\":{\"species\":\"Charizard\",\"level\":50,"
            "\"overrides\":{\"types\":[\"Fire\",\"Dragon\"]}},"
            "\"defender\":{\"species\":\"Swampert\",\"level\":50},"
            "\"move\":{\"name\":\"Flamethrower\"}}";

        char* out = js_calc_calculate(req);
        check_condition("type override calculation succeeds", out != NULL);
        if (out) {
            jl_value* doc = jl_parse(out);
            check_condition("type override response is valid JSON", doc != NULL);
            if (doc) {
                check_condition("response success is true", jl_bool(jl_get(doc, "success")) == 1);
                const jl_value* types = jl_get(doc, "attackerTypes");
                check_condition("attackerTypes is an array", jl_is_arr(types));
                if (jl_is_arr(types)) {
                    check_int("attackerTypes length", 2, jl_len(types));
                    check_str("attackerTypes[0]", "Fire", jl_str(jl_at(types, 0)));
                    check_str("attackerTypes[1]", "Dragon", jl_str(jl_at(types, 1)));
                }
            }
            jl_free(doc);
        }
        free(out);
    }

    g_fixture = "gap_b_move_base_power_override";
    {
        /*
         * Test C: Move base-power override is consumed.
         * Tackle: Gen 3 library power = 35. H&S 2.0.5 authoritative power = 40.
         * Attacker: Snorlax L50. Defender: Swampert L50.
         *
         * Vanilla (35 BP): damage [25..30], movePower = 35.
         * Overridden (40 BP): damage [28..33], movePower = 40.
         */
        const char* req_vanilla =
            "{\"gen\":3,"
            "\"attacker\":{\"species\":\"Snorlax\",\"level\":50},"
            "\"defender\":{\"species\":\"Swampert\",\"level\":50},"
            "\"move\":{\"name\":\"Tackle\"}}";

        const char* req_override =
            "{\"gen\":3,"
            "\"attacker\":{\"species\":\"Snorlax\",\"level\":50},"
            "\"defender\":{\"species\":\"Swampert\",\"level\":50},"
            "\"move\":{\"name\":\"Tackle\",\"overrides\":{\"basePower\":40}}}";

        char* out_vanilla = js_calc_calculate(req_vanilla);
        char* out_override = js_calc_calculate(req_override);

        check_condition("vanilla Tackle calculation succeeds", out_vanilla != NULL);
        check_condition("overridden Tackle calculation succeeds", out_override != NULL);

        if (out_vanilla && out_override) {
            jl_value* doc_v = jl_parse(out_vanilla);
            jl_value* doc_o = jl_parse(out_override);

            if (doc_v && doc_o) {
                check_number("vanilla movePower", 35, jl_get(doc_v, "movePower"));
                check_number("vanilla minDamage", 25, jl_get(doc_v, "minDamage"));
                check_number("vanilla maxDamage", 30, jl_get(doc_v, "maxDamage"));

                check_number("overridden movePower", 40, jl_get(doc_o, "movePower"));
                check_number("overridden minDamage", 28, jl_get(doc_o, "minDamage"));
                check_number("overridden maxDamage", 33, jl_get(doc_o, "maxDamage"));

                check_condition("overridden base power strictly increases damage",
                                jl_num(jl_get(doc_o, "minDamage")) > jl_num(jl_get(doc_v, "minDamage")));
            }
            jl_free(doc_v);
            jl_free(doc_o);
        }
        free(out_vanilla);
        free(out_override);
    }

    g_fixture = "gap_b_move_type_override";
    {
        /*
         * Test D: Move type override is consumed.
         * Tackle overridden to type Fighting (super-effective vs Normal Snorlax).
         */
        const char* req =
            "{\"gen\":3,"
            "\"attacker\":{\"species\":\"Swampert\",\"level\":50},"
            "\"defender\":{\"species\":\"Snorlax\",\"level\":50},"
            "\"move\":{\"name\":\"Tackle\",\"overrides\":{\"type\":\"Fighting\",\"basePower\":40}}}";

        char* out = js_calc_calculate(req);
        check_condition("move type override calculation succeeds", out != NULL);
        if (out) {
            jl_value* doc = jl_parse(out);
            if (doc) {
                check_str("moveType is Fighting", "Fighting", jl_str(jl_get(doc, "moveType")));
                check_number("minDamage reflects super-effective hit", 47, jl_get(doc, "minDamage"));
                check_number("maxDamage reflects super-effective hit", 56, jl_get(doc, "maxDamage"));
            }
            jl_free(doc);
        }
        free(out);
    }

    g_fixture = "gap_b_move_category_behavior";
    {
        /*
         * Test E: Move category behavior is pinned honestly at Gen 3.
         * In Gen 3, Ghost is naturally Physical.
         * Attacker: Alakazam (Modest, 135 SpA, 50 Atk).
         * Defender: Swampert (Def 110, SpD 110).
         *
         * 1. Without category override: Shadow Ball defaults to Physical (20..24 dmg).
         * 2. With category override "Special": Shadow Ball uses SpA (43..51 dmg).
         */
        const char* req_default =
            "{\"gen\":3,"
            "\"attacker\":{\"species\":\"Alakazam\",\"level\":50},"
            "\"defender\":{\"species\":\"Swampert\",\"level\":50},"
            "\"move\":{\"name\":\"Shadow Ball\"}}";

        const char* req_special =
            "{\"gen\":3,"
            "\"attacker\":{\"species\":\"Alakazam\",\"level\":50},"
            "\"defender\":{\"species\":\"Swampert\",\"level\":50},"
            "\"move\":{\"name\":\"Shadow Ball\",\"overrides\":{\"category\":\"Special\"}}}";

        char* out_default = js_calc_calculate(req_default);
        char* out_special = js_calc_calculate(req_special);

        check_condition("default Shadow Ball calculation succeeds", out_default != NULL);
        check_condition("Special Shadow Ball calculation succeeds", out_special != NULL);

        if (out_default && out_special) {
            jl_value* doc_def = jl_parse(out_default);
            jl_value* doc_spc = jl_parse(out_special);

            if (doc_def && doc_spc) {
                check_str("default category is Physical", "Physical", jl_str(jl_get(doc_def, "moveCategory")));
                check_number("default minDamage (Physical)", 20, jl_get(doc_def, "minDamage"));
                check_number("default maxDamage (Physical)", 24, jl_get(doc_def, "maxDamage"));

                check_str("overridden category is Special", "Special", jl_str(jl_get(doc_spc, "moveCategory")));
                check_number("overridden minDamage (Special)", 43, jl_get(doc_spc, "minDamage"));
                check_number("overridden maxDamage (Special)", 51, jl_get(doc_spc, "maxDamage"));
            }
            jl_free(doc_def);
            jl_free(doc_spc);
        }
        free(out_default);
        free(out_special);
    }

    g_fixture = "gap_b_request_isolation";
    {
        /*
         * Negative isolation control:
         * 1. Execute request with overrides.
         * 2. Execute equivalent request without overrides.
         * 3. Second result must use normal library data again (no table leakage or mutation).
         */
        const char* req_with_override =
            "{\"gen\":3,"
            "\"attacker\":{\"species\":\"Snorlax\",\"level\":50},"
            "\"defender\":{\"species\":\"Swampert\",\"level\":50},"
            "\"move\":{\"name\":\"Tackle\",\"overrides\":{\"basePower\":99}}}";

        const char* req_without_override =
            "{\"gen\":3,"
            "\"attacker\":{\"species\":\"Snorlax\",\"level\":50},"
            "\"defender\":{\"species\":\"Swampert\",\"level\":50},"
            "\"move\":{\"name\":\"Tackle\"}}";

        char* out1 = js_calc_calculate(req_with_override);
        char* out2 = js_calc_calculate(req_without_override);

        check_condition("first request succeeds", out1 != NULL);
        check_condition("second request succeeds", out2 != NULL);

        if (out1 && out2) {
            jl_value* doc1 = jl_parse(out1);
            jl_value* doc2 = jl_parse(out2);

            if (doc1 && doc2) {
                check_number("first request has overridden BP 99", 99, jl_get(doc1, "movePower"));
                check_number("second request returns to library default BP 35", 35, jl_get(doc2, "movePower"));
                check_number("second request returns to library default minDamage 25", 25, jl_get(doc2, "minDamage"));
            }
            jl_free(doc1);
            jl_free(doc2);
        }
        free(out1);
        free(out2);
    }

    g_fixture = "gap_b_malformed_overrides";
    {
        /* Missing required base stat */
        const char* req_missing_stat =
            "{\"gen\":3,"
            "\"attacker\":{\"species\":\"Arbok\",\"level\":50,"
            "\"overrides\":{\"baseStats\":{\"hp\":60,\"atk\":95}}},"
            "\"defender\":{\"species\":\"Swampert\",\"level\":50},"
            "\"move\":{\"name\":\"Sludge Bomb\"}}";
        char* out_ms = js_calc_calculate(req_missing_stat);
        check_condition("missing base stat returns response", out_ms != NULL);
        if (out_ms) {
            jl_value* doc = jl_parse(out_ms);
            if (doc) {
                check_condition("missing base stat is rejected with success=false", jl_bool(jl_get(doc, "success")) == 0);
                check_string_present("missing base stat error string", jl_get(doc, "error"));
            }
            jl_free(doc);
        }
        free(out_ms);

        /* Negative base power */
        const char* req_neg_bp =
            "{\"gen\":3,"
            "\"attacker\":{\"species\":\"Arbok\",\"level\":50},"
            "\"defender\":{\"species\":\"Swampert\",\"level\":50},"
            "\"move\":{\"name\":\"Sludge Bomb\",\"overrides\":{\"basePower\":-10}}}";
        char* out_nbp = js_calc_calculate(req_neg_bp);
        check_condition("negative base power returns response", out_nbp != NULL);
        if (out_nbp) {
            jl_value* doc = jl_parse(out_nbp);
            if (doc) {
                check_condition("negative base power is rejected with success=false", jl_bool(jl_get(doc, "success")) == 0);
                check_string_present("negative base power error string", jl_get(doc, "error"));
            }
            jl_free(doc);
        }
        free(out_nbp);

        /* Unknown type name */
        const char* req_unk_type =
            "{\"gen\":3,"
            "\"attacker\":{\"species\":\"Arbok\",\"level\":50,"
            "\"overrides\":{\"types\":[\"NotAType\"]}},"
            "\"defender\":{\"species\":\"Swampert\",\"level\":50},"
            "\"move\":{\"name\":\"Sludge Bomb\"}}";
        char* out_ut = js_calc_calculate(req_unk_type);
        check_condition("unknown type returns response", out_ut != NULL);
        if (out_ut) {
            jl_value* doc = jl_parse(out_ut);
            if (doc) {
                check_condition("unknown type is rejected with success=false", jl_bool(jl_get(doc, "success")) == 0);
                check_string_present("unknown type error string", jl_get(doc, "error"));
            }
            jl_free(doc);
        }
        free(out_ut);

        /* Unsupported category encoding */
        const char* req_bad_cat =
            "{\"gen\":3,"
            "\"attacker\":{\"species\":\"Arbok\",\"level\":50},"
            "\"defender\":{\"species\":\"Swampert\",\"level\":50},"
            "\"move\":{\"name\":\"Sludge Bomb\",\"overrides\":{\"category\":\"PHYSICAL\"}}}";
        char* out_bc = js_calc_calculate(req_bad_cat);
        check_condition("unsupported category returns response", out_bc != NULL);
        if (out_bc) {
            jl_value* doc = jl_parse(out_bc);
            if (doc) {
                check_condition("unsupported category is rejected with success=false", jl_bool(jl_get(doc, "success")) == 0);
                check_string_present("unsupported category error string", jl_get(doc, "error"));
            }
            jl_free(doc);
        }
        free(out_bc);

        /* Extra unknown field in override */
        const char* req_extra_field =
            "{\"gen\":3,"
            "\"attacker\":{\"species\":\"Arbok\",\"level\":50,"
            "\"overrides\":{\"unknownKey\":123}},"
            "\"defender\":{\"species\":\"Swampert\",\"level\":50},"
            "\"move\":{\"name\":\"Sludge Bomb\"}}";
        char* out_ef = js_calc_calculate(req_extra_field);
        check_condition("extra unknown field returns response", out_ef != NULL);
        if (out_ef) {
            jl_value* doc = jl_parse(out_ef);
            if (doc) {
                check_condition("extra unknown field is rejected with success=false", jl_bool(jl_get(doc, "success")) == 0);
                check_string_present("extra unknown field error string", jl_get(doc, "error"));
            }
            jl_free(doc);
        }
        free(out_ef);
    }
}

static void check_gap_c1_type_system(void) {
    printf("-- Gap C1: H&S exact type system, Fairy toggle, request isolation --\n");

    /*
     * Test A: Ghost -> Steel effectiveness.
     * Gen 3 vanilla: Ghost -> Steel is resisted (0.5x).
     * H&S 2.0.5: Ghost -> Steel is neutral (1.0x).
     * Attacker: Gengar (level 50, SpA 150), Move: Shadow Ball (overrides: category: Special).
     * Defender: Registeel (level 50, SpD 170).
     * Vanilla ADV: 20..24 dmg (eff 0.5).
     * H&S 2.0.5:  42..49 dmg (eff 1.0, exact UQ4.12 roll-first pipeline).
     */
    g_fixture = "gap_c1_ghost_steel_matchup";
    {
        const char* req_adv =
            "{\"gen\":3,"
            "\"attacker\":{\"species\":\"Gengar\",\"level\":50},"
            "\"defender\":{\"species\":\"Registeel\",\"level\":50},"
            "\"move\":{\"name\":\"Shadow Ball\",\"overrides\":{\"category\":\"Special\"}}}";

        const char* req_hns =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Gengar\",\"level\":50},"
            "\"defender\":{\"species\":\"Registeel\",\"level\":50},"
            "\"move\":{\"name\":\"Shadow Ball\",\"overrides\":{\"category\":\"Special\"}}}";

        char* out_adv = js_calc_calculate(req_adv);
        char* out_hns = js_calc_calculate(req_hns);

        check_condition("ADV Ghost->Steel succeeds", out_adv != NULL);
        check_condition("H&S Ghost->Steel succeeds", out_hns != NULL);

        if (out_adv && out_hns) {
            jl_value* doc_adv = jl_parse(out_adv);
            jl_value* doc_hns = jl_parse(out_hns);

            if (doc_adv && doc_hns) {
                check_double("ADV effectiveness is 0.5", 0.5, jl_get(doc_adv, "effectiveness"));
                check_number("ADV minDamage is 20", 20, jl_get(doc_adv, "minDamage"));
                check_number("ADV maxDamage is 24", 24, jl_get(doc_adv, "maxDamage"));

                check_double("H&S effectiveness is 1.0", 1.0, jl_get(doc_hns, "effectiveness"));
                check_number("H&S minDamage is 42", 42, jl_get(doc_hns, "minDamage"));
                check_number("H&S maxDamage is 49", 49, jl_get(doc_hns, "maxDamage"));
            }
            jl_free(doc_adv);
            jl_free(doc_hns);
        }
        free(out_adv);
        free(out_hns);
    }

    /*
     * Test B: Dark -> Steel effectiveness.
     * Gen 3 vanilla: Dark -> Steel is resisted (0.5x).
     * H&S 2.0.5: Dark -> Steel is neutral (1.0x).
     * Attacker: Houndoom (level 50, SpA 130), Move: Crunch (80 BP, Special in Gen 3).
     * Defender: Registeel (level 50, SpD 170).
     * Vanilla ADV: 17..21 dmg (eff 0.5).
     * H&S 2.0.5:  34..42 dmg (eff 1.0, exact UQ4.12 roll-first pipeline).
     */
    g_fixture = "gap_c1_dark_steel_matchup";
    {
        const char* req_adv =
            "{\"gen\":3,"
            "\"attacker\":{\"species\":\"Houndoom\",\"level\":50},"
            "\"defender\":{\"species\":\"Registeel\",\"level\":50},"
            "\"move\":{\"name\":\"Crunch\"}}";

        const char* req_hns =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Houndoom\",\"level\":50},"
            "\"defender\":{\"species\":\"Registeel\",\"level\":50},"
            "\"move\":{\"name\":\"Crunch\"}}";

        char* out_adv = js_calc_calculate(req_adv);
        char* out_hns = js_calc_calculate(req_hns);

        check_condition("ADV Dark->Steel succeeds", out_adv != NULL);
        check_condition("H&S Dark->Steel succeeds", out_hns != NULL);

        if (out_adv && out_hns) {
            jl_value* doc_adv = jl_parse(out_adv);
            jl_value* doc_hns = jl_parse(out_hns);

            if (doc_adv && doc_hns) {
                check_double("ADV effectiveness is 0.5", 0.5, jl_get(doc_adv, "effectiveness"));
                check_number("ADV minDamage is 17", 17, jl_get(doc_adv, "minDamage"));
                check_number("ADV maxDamage is 21", 21, jl_get(doc_adv, "maxDamage"));

                check_double("H&S effectiveness is 1.0", 1.0, jl_get(doc_hns, "effectiveness"));
                check_number("H&S minDamage is 34", 34, jl_get(doc_hns, "minDamage"));
                check_number("H&S maxDamage is 42", 42, jl_get(doc_hns, "maxDamage"));
            }
            jl_free(doc_adv);
            jl_free(doc_hns);
        }
        free(out_adv);
        free(out_hns);
    }

    /*
     * Test C: Fairy type super-effectiveness and category defaulting.
     * Attacker: Clefable (level 50, overrides: types: ["Fairy"], baseStats: hp 95, atk 70, def 73, spa 95, spd 90, spe 60).
     * Defender: Dragonite (level 50, Dragon/Flying, Def 115, SpD 120).
     * Move: Moonblast (overrides: type: "Fairy", basePower: 95; category omitted).
     * In H&S, Moonblast is Fairy (Special). Fairy vs Dragon/Flying is 2.0x (2.0 x 1.0).
     * Expected: minDamage 104, maxDamage 126, eff 2.0, category "Special".
     */
    g_fixture = "gap_c1_fairy_offensive_matchup";
    {
        const char* req =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Clefable\",\"level\":50,"
            "\"overrides\":{\"types\":[\"Fairy\"],\"baseStats\":{\"hp\":95,\"atk\":70,\"def\":73,\"spa\":95,\"spd\":90,\"spe\":60}}},"
            "\"defender\":{\"species\":\"Dragonite\",\"level\":50},"
            "\"move\":{\"name\":\"Moonblast\",\"overrides\":{\"type\":\"Fairy\",\"basePower\":95}}}";

        char* out = js_calc_calculate(req);
        check_condition("Fairy Moonblast succeeds", out != NULL);
        if (out) {
            jl_value* doc = jl_parse(out);
            if (doc) {
                check_str("category defaulted to Special", "Special", jl_str(jl_get(doc, "moveCategory")));
                check_double("effectiveness is 2.0", 2.0, jl_get(doc, "effectiveness"));
                check_number("minDamage is 104", 104, jl_get(doc, "minDamage"));
                check_number("maxDamage is 126", 126, jl_get(doc, "maxDamage"));
            }
            jl_free(doc);
        }
        free(out);
    }

    /*
     * Test D: Fairy type defensive immunity (0x).
     * Attacker: Dragonite (level 50).
     * Defender: Clefable (level 50, overrides: types: ["Fairy"]).
     * Move: Dragon Claw (Dragon type).
     * In H&S, Dragon -> Fairy is 0.0x immune.
     * Expected: minDamage 0, maxDamage 0, eff 0.0, success true.
     */
    g_fixture = "gap_c1_fairy_defensive_immunity";
    {
        const char* req =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Dragonite\",\"level\":50},"
            "\"defender\":{\"species\":\"Clefable\",\"level\":50,\"overrides\":{\"types\":[\"Fairy\"]}},"
            "\"move\":{\"name\":\"Dragon Claw\"}}";

        char* out = js_calc_calculate(req);
        check_condition("Dragon vs Fairy succeeds", out != NULL);
        if (out) {
            jl_value* doc = jl_parse(out);
            if (doc) {
                check_condition("success is true on immunity", jl_bool(jl_get(doc, "success")) == 1);
                check_double("effectiveness is 0.0", 0.0, jl_get(doc, "effectiveness"));
                check_number("minDamage is 0", 0, jl_get(doc, "minDamage"));
                check_number("maxDamage is 0", 0, jl_get(doc, "maxDamage"));
            }
            jl_free(doc);
        }
        free(out);
    }

    /*
     * Test E: optionStyle category coupling on retyped move.
     * Attacker: Clefable (level 50, overrides: types: ["Normal"], baseStats: hp 95, atk 70, def 73, spa 95, spd 90, spe 60).
     * Defender: Swampert (level 50).
     * Move: Dazzling Gleam retyped to Normal (BP 80).
     * Case 1: PER_MOVE_SPLIT (category = "Special") -> 48..57 dmg.
     * Case 2: TYPE_BASED (category = "Physical") -> 38..45 dmg.
     */
    g_fixture = "gap_c1_option_style_category_coupling";
    {
        const char* req_split =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Clefable\",\"level\":50,"
            "\"overrides\":{\"types\":[\"Normal\"],\"baseStats\":{\"hp\":95,\"atk\":70,\"def\":73,\"spa\":95,\"spd\":90,\"spe\":60}}},"
            "\"defender\":{\"species\":\"Swampert\",\"level\":50},"
            "\"move\":{\"name\":\"Dazzling Gleam\",\"overrides\":{\"type\":\"Normal\",\"basePower\":80,\"category\":\"Special\"}}}";

        const char* req_type_based =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Clefable\",\"level\":50,"
            "\"overrides\":{\"types\":[\"Normal\"],\"baseStats\":{\"hp\":95,\"atk\":70,\"def\":73,\"spa\":95,\"spd\":90,\"spe\":60}}},"
            "\"defender\":{\"species\":\"Swampert\",\"level\":50},"
            "\"move\":{\"name\":\"Dazzling Gleam\",\"overrides\":{\"type\":\"Normal\",\"basePower\":80,\"category\":\"Physical\"}}}";

        char* out_split = js_calc_calculate(req_split);
        char* out_tb = js_calc_calculate(req_type_based);

        check_condition("split calculation succeeds", out_split != NULL);
        check_condition("type-based calculation succeeds", out_tb != NULL);

        if (out_split && out_tb) {
            jl_value* doc_split = jl_parse(out_split);
            jl_value* doc_tb = jl_parse(out_tb);

            if (doc_split && doc_tb) {
                check_str("split category is Special", "Special", jl_str(jl_get(doc_split, "moveCategory")));
                check_number("split minDamage is 48", 48, jl_get(doc_split, "minDamage"));
                check_number("split maxDamage is 57", 57, jl_get(doc_split, "maxDamage"));

                check_str("type-based category is Physical", "Physical", jl_str(jl_get(doc_tb, "moveCategory")));
                check_number("type-based minDamage is 37", 37, jl_get(doc_tb, "minDamage"));
                check_number("type-based maxDamage is 45", 45, jl_get(doc_tb, "maxDamage"));
            }
            jl_free(doc_split);
            jl_free(doc_tb);
        }
        free(out_split);
        free(out_tb);
    }

    /*
     * Test F: Request isolation (zero state leakage or global mutation).
     * Sequence: H&S -> Vanilla -> H&S -> Vanilla
     * Verify neither mutates the shared engine state.
     */
    g_fixture = "gap_c1_request_isolation";
    {
        const char* req_hns =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Gengar\",\"level\":50},"
            "\"defender\":{\"species\":\"Registeel\",\"level\":50},"
            "\"move\":{\"name\":\"Shadow Ball\",\"overrides\":{\"category\":\"Special\"}}}";

        const char* req_adv =
            "{\"gen\":3,"
            "\"attacker\":{\"species\":\"Gengar\",\"level\":50},"
            "\"defender\":{\"species\":\"Registeel\",\"level\":50},"
            "\"move\":{\"name\":\"Shadow Ball\",\"overrides\":{\"category\":\"Special\"}}}";

        char* out1 = js_calc_calculate(req_hns);
        char* out2 = js_calc_calculate(req_adv);
        char* out3 = js_calc_calculate(req_hns);
        char* out4 = js_calc_calculate(req_adv);

        check_condition("isolation run 1 succeeds", out1 != NULL);
        check_condition("isolation run 2 succeeds", out2 != NULL);
        check_condition("isolation run 3 succeeds", out3 != NULL);
        check_condition("isolation run 4 succeeds", out4 != NULL);

        if (out1 && out2 && out3 && out4) {
            jl_value* d1 = jl_parse(out1);
            jl_value* d2 = jl_parse(out2);
            jl_value* d3 = jl_parse(out3);
            jl_value* d4 = jl_parse(out4);

            if (d1 && d2 && d3 && d4) {
                check_number("run 1 H&S minDamage", 42, jl_get(d1, "minDamage"));
                check_number("run 2 ADV minDamage", 20, jl_get(d2, "minDamage"));
                check_number("run 3 H&S minDamage", 42, jl_get(d3, "minDamage"));
                check_number("run 4 ADV minDamage", 20, jl_get(d4, "minDamage"));
            }
            jl_free(d1);
            jl_free(d2);
            jl_free(d3);
            jl_free(d4);
        }
        free(out1);
        free(out2);
        free(out3);
        free(out4);
    }

    /*
     * Test G: Unsupported typeSystem is rejected with success=false.
     */
    g_fixture = "gap_c1_unsupported_type_system";
    {
        const char* req_bad =
            "{\"gen\":3,\"typeSystem\":\"invalid_type_sys\","
            "\"attacker\":{\"species\":\"Swampert\",\"level\":50},"
            "\"defender\":{\"species\":\"Snorlax\",\"level\":50},"
            "\"move\":{\"name\":\"Tackle\"}}";

        char* out_bad = js_calc_calculate(req_bad);
        check_condition("unsupported typeSystem returns response", out_bad != NULL);
        if (out_bad) {
            jl_value* doc = jl_parse(out_bad);
            if (doc) {
                check_condition("unsupported typeSystem is rejected with success=false", jl_bool(jl_get(doc, "success")) == 0);
                check_string_present("unsupported typeSystem error string", jl_get(doc, "error"));
            }
            jl_free(doc);
        }
        free(out_bad);
    }

    /*
     * Test H: Status move category preservation (Status wins before optionStyle).
     * Charm (MOVE_CHARM, id 204) in H&S is Fairy / Status / 0 BP.
     * When Fairy ON:  type Fairy, category Status.
     * When Fairy OFF: type Normal, category Status.
     * Under TYPE_BASED optionStyle, Status moves MUST remain Status, never converted to
     * Special (Fairy ON) or Physical (Fairy OFF).
     */
    g_fixture = "gap_c1_charm_status_category_preservation";
    {
        // Case 1: Charm with Fairy ON and category Status
        const char* req_fairy_on =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Clefable\",\"level\":50},"
            "\"defender\":{\"species\":\"Swampert\",\"level\":50},"
            "\"move\":{\"name\":\"Charm\",\"overrides\":{\"type\":\"Fairy\",\"category\":\"Status\",\"basePower\":0}}}";

        // Case 2: Charm with Fairy OFF and category Status
        const char* req_fairy_off =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Clefable\",\"level\":50},"
            "\"defender\":{\"species\":\"Swampert\",\"level\":50},"
            "\"move\":{\"name\":\"Charm\",\"overrides\":{\"type\":\"Normal\",\"category\":\"Status\",\"basePower\":0}}}";

        // Case 3: Charm with Fairy ON and category omitted (must NOT default to Special)
        const char* req_fairy_on_no_cat =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Clefable\",\"level\":50},"
            "\"defender\":{\"species\":\"Swampert\",\"level\":50},"
            "\"move\":{\"name\":\"Charm\",\"overrides\":{\"type\":\"Fairy\"}}}";

        // Case 4: Vanilla ADV control
        const char* req_vanilla =
            "{\"gen\":3,"
            "\"attacker\":{\"species\":\"Clefable\",\"level\":50},"
            "\"defender\":{\"species\":\"Swampert\",\"level\":50},"
            "\"move\":{\"name\":\"Charm\"}}";

        char* out1 = js_calc_calculate(req_fairy_on);
        char* out2 = js_calc_calculate(req_fairy_off);
        char* out3 = js_calc_calculate(req_fairy_on_no_cat);
        char* out4 = js_calc_calculate(req_vanilla);

        check_condition("Charm Fairy ON succeeds", out1 != NULL);
        check_condition("Charm Fairy OFF succeeds", out2 != NULL);
        check_condition("Charm Fairy ON no category succeeds", out3 != NULL);
        check_condition("Charm vanilla control succeeds", out4 != NULL);

        if (out1 && out2 && out3 && out4) {
            jl_value* d1 = jl_parse(out1);
            jl_value* d2 = jl_parse(out2);
            jl_value* d3 = jl_parse(out3);
            jl_value* d4 = jl_parse(out4);

            if (d1 && d2 && d3 && d4) {
                check_str("Charm Fairy ON category is Status", "Status", jl_str(jl_get(d1, "moveCategory")));
                check_number("Charm Fairy ON minDamage is 0", 0, jl_get(d1, "minDamage"));
                check_number("Charm Fairy ON maxDamage is 0", 0, jl_get(d1, "maxDamage"));

                check_str("Charm Fairy OFF category is Status", "Status", jl_str(jl_get(d2, "moveCategory")));
                check_number("Charm Fairy OFF minDamage is 0", 0, jl_get(d2, "minDamage"));
                check_number("Charm Fairy OFF maxDamage is 0", 0, jl_get(d2, "maxDamage"));

                check_str("Charm Fairy ON without category remains Status", "Status", jl_str(jl_get(d3, "moveCategory")));
                check_number("Charm Fairy ON no category minDamage is 0", 0, jl_get(d3, "minDamage"));
                check_number("Charm Fairy ON no category maxDamage is 0", 0, jl_get(d3, "maxDamage"));

                check_str("Charm vanilla category is Status", "Status", jl_str(jl_get(d4, "moveCategory")));
                check_number("Charm vanilla minDamage is 0", 0, jl_get(d4, "minDamage"));
                check_number("Charm vanilla maxDamage is 0", 0, jl_get(d4, "maxDamage"));
            }
            jl_free(d1);
            jl_free(d2);
            jl_free(d3);
            jl_free(d4);
        }
        free(out1);
        free(out2);
        free(out3);
        free(out4);
    }
}

static void check_gap_c2_abilities(void) {
    /*
     * Test A: Guts physical boost with odd attack stat in ADV engine.
     * Note: While ADV computes floor(105 * 1.5) = 157, in H&S, ability multipliers are
     * combined in fixed-point and stat stages are applied before ability modifiers.
     * When interacting with other abilities (e.g. Thick Fat) or non-neutral stat stages,
     * compound rounding diverges (e.g. Guts 105 Atk vs Thick Fat: H&S 79 vs ADV 78).
     * Therefore, Kotlin CalcCapabilityPolicy marks Guts UNSUPPORTED_DAMAGE_RELEVANT for H&S
     * production calculations, pending future modifier-layer modeling.
     * Attacker: Swellow L50 Hardy (IV 31, EV 0; Base Atk 85 -> Atk stat 105, which is odd).
     * Defender: Swampert L50 Hardy (IV 31, EV 0; HP 175, Def 110).
     * Move: Wing Attack (Flying, 60 BP, Physical).
     */
    g_fixture = "gap_c2_guts_status_boost_odd_attack";
    {
        const char* req_unboosted =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Swellow\",\"level\":50,\"ability\":\"Guts\"},"
            "\"defender\":{\"species\":\"Swampert\",\"level\":50},"
            "\"move\":{\"name\":\"Wing Attack\"}}";

        const char* req_guts_brn =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Swellow\",\"level\":50,\"ability\":\"Guts\",\"status\":\"brn\"},"
            "\"defender\":{\"species\":\"Swampert\",\"level\":50},"
            "\"move\":{\"name\":\"Wing Attack\"}}";

        char* out1 = js_calc_calculate(req_unboosted);
        char* out2 = js_calc_calculate(req_guts_brn);

        check_condition("Swellow unboosted succeeds", out1 != NULL);
        check_condition("Swellow Guts+brn succeeds", out2 != NULL);

        if (out1 && out2) {
            jl_value* d1 = jl_parse(out1);
            jl_value* d2 = jl_parse(out2);
            if (d1 && d2) {
                check_number("Swellow unboosted minDamage", 33, jl_get(d1, "minDamage"));
                check_number("Swellow unboosted maxDamage", 40, jl_get(d1, "maxDamage"));

                check_number("Swellow Guts+brn minDamage", 49, jl_get(d2, "minDamage"));
                check_number("Swellow Guts+brn maxDamage", 58, jl_get(d2, "maxDamage"));
            }
            jl_free(d1);
            jl_free(d2);
        }
        free(out1);
        free(out2);
    }

    /*
     * Test B: Prevention of default species ability substitution under H&S 2.0.5.
     * Machamp's 0th ability in Gen 3 is Guts.
     * When Machamp has status "brn" and NO ability is specified (or ability is "None"):
     * Under H&S 2.0.5: ability defaults to '(other)', so Guts is NOT substituted.
     * Burn halves physical attack -> Cross Chop deals 39-46 damage in H&S UQ4.12.
     * Under Vanilla Gen 3 control: @smogon/calc substitutes Guts -> Cross Chop deals 117-138 damage.
     */
    g_fixture = "gap_c2_default_ability_substitution_prevention";
    {
        const char* req_hns_omitted =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Machamp\",\"level\":50,\"status\":\"brn\"},"
            "\"defender\":{\"species\":\"Swampert\",\"level\":50},"
            "\"move\":{\"name\":\"Cross Chop\"}}";

        const char* req_hns_none =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Machamp\",\"level\":50,\"ability\":\"None\",\"status\":\"brn\"},"
            "\"defender\":{\"species\":\"Swampert\",\"level\":50},"
            "\"move\":{\"name\":\"Cross Chop\"}}";

        const char* req_vanilla_omitted =
            "{\"gen\":3,"
            "\"attacker\":{\"species\":\"Machamp\",\"level\":50,\"status\":\"brn\"},"
            "\"defender\":{\"species\":\"Swampert\",\"level\":50},"
            "\"move\":{\"name\":\"Cross Chop\"}}";

        char* out1 = js_calc_calculate(req_hns_omitted);
        char* out2 = js_calc_calculate(req_hns_none);
        char* out3 = js_calc_calculate(req_vanilla_omitted);

        check_condition("Machamp H&S omitted succeeds", out1 != NULL);
        check_condition("Machamp H&S None succeeds", out2 != NULL);
        check_condition("Machamp Vanilla omitted succeeds", out3 != NULL);

        if (out1 && out2 && out3) {
            jl_value* d1 = jl_parse(out1);
            jl_value* d2 = jl_parse(out2);
            jl_value* d3 = jl_parse(out3);
            if (d1 && d2 && d3) {
                check_number("Machamp H&S omitted minDamage is halved by burn", 39, jl_get(d1, "minDamage"));
                check_number("Machamp H&S omitted maxDamage is halved by burn", 46, jl_get(d1, "maxDamage"));

                check_number("Machamp H&S None minDamage matches omitted", 39, jl_get(d2, "minDamage"));
                check_number("Machamp H&S None maxDamage matches omitted", 46, jl_get(d2, "maxDamage"));

                check_number("Machamp Vanilla omitted minDamage gets Guts boost", 117, jl_get(d3, "minDamage"));
                check_number("Machamp Vanilla omitted maxDamage gets Guts boost", 138, jl_get(d3, "maxDamage"));
            }
            jl_free(d1);
            jl_free(d2);
            jl_free(d3);
        }
        free(out1);
        free(out2);
        free(out3);
    }

    /*
     * Test C: Thick Fat halves incoming Fire and Ice moves; unaffected for other types.
     * Attacker: Charizard L50 Hardy. Defender: Snorlax L50 Hardy.
     * Move 1: Flamethrower (Fire, Special, 95 BP).
     * Thick Fat Snorlax: damage 27-33 (50% reduction in H&S UQ4.12).
     * Immunity Snorlax: damage 54-64.
     * Move 2: Wing Attack (Flying, Physical, 60 BP).
     * Thick Fat Snorlax: damage 42-51.
     * Immunity Snorlax: damage 42-51 (identical, control).
     */
    g_fixture = "gap_c2_thick_fat_fire_ice";
    {
        const char* req_thick_fat_fire =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Charizard\",\"level\":50},"
            "\"defender\":{\"species\":\"Snorlax\",\"level\":50,\"ability\":\"Thick Fat\"},"
            "\"move\":{\"name\":\"Flamethrower\"}}";

        const char* req_immunity_fire =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Charizard\",\"level\":50},"
            "\"defender\":{\"species\":\"Snorlax\",\"level\":50,\"ability\":\"Immunity\"},"
            "\"move\":{\"name\":\"Flamethrower\"}}";

        const char* req_thick_fat_fly =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Charizard\",\"level\":50},"
            "\"defender\":{\"species\":\"Snorlax\",\"level\":50,\"ability\":\"Thick Fat\"},"
            "\"move\":{\"name\":\"Wing Attack\"}}";

        const char* req_immunity_fly =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Charizard\",\"level\":50},"
            "\"defender\":{\"species\":\"Snorlax\",\"level\":50,\"ability\":\"Immunity\"},"
            "\"move\":{\"name\":\"Wing Attack\"}}";

        char* out1 = js_calc_calculate(req_thick_fat_fire);
        char* out2 = js_calc_calculate(req_immunity_fire);
        char* out3 = js_calc_calculate(req_thick_fat_fly);
        char* out4 = js_calc_calculate(req_immunity_fly);

        check_condition("Thick Fat Flamethrower succeeds", out1 != NULL);
        check_condition("Immunity Flamethrower succeeds", out2 != NULL);
        check_condition("Thick Fat Wing Attack succeeds", out3 != NULL);
        check_condition("Immunity Wing Attack succeeds", out4 != NULL);

        if (out1 && out2 && out3 && out4) {
            jl_value* d1 = jl_parse(out1);
            jl_value* d2 = jl_parse(out2);
            jl_value* d3 = jl_parse(out3);
            jl_value* d4 = jl_parse(out4);
            if (d1 && d2 && d3 && d4) {
                check_number("Thick Fat Fire minDamage", 27, jl_get(d1, "minDamage"));
                check_number("Thick Fat Fire maxDamage", 33, jl_get(d1, "maxDamage"));

                check_number("Immunity Fire minDamage", 54, jl_get(d2, "minDamage"));
                check_number("Immunity Fire maxDamage", 64, jl_get(d2, "maxDamage"));

                check_number("Thick Fat Flying minDamage", 42, jl_get(d3, "minDamage"));
                check_number("Thick Fat Flying maxDamage", 51, jl_get(d3, "maxDamage"));

                check_number("Immunity Flying minDamage", 42, jl_get(d4, "minDamage"));
                check_number("Immunity Flying maxDamage", 51, jl_get(d4, "maxDamage"));
            }
            jl_free(d1);
            jl_free(d2);
            jl_free(d3);
            jl_free(d4);
        }
        free(out1);
        free(out2);
        free(out3);
        free(out4);
    }

    /*
     * Test D: Proven no-damage-effect abilities (Keen Eye, Insomnia, None).
     * Attacker: Pidgeot L50 Hardy. Defender: Swampert L50 Hardy. Move: Wing Attack.
     * All three must produce identical damage bounds: [33..39].
     */
    g_fixture = "gap_c2_proven_no_damage_effect";
    {
        const char* req_keen_eye =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Pidgeot\",\"level\":50,\"ability\":\"Keen Eye\"},"
            "\"defender\":{\"species\":\"Swampert\",\"level\":50},"
            "\"move\":{\"name\":\"Wing Attack\"}}";

        const char* req_insomnia =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Pidgeot\",\"level\":50,\"ability\":\"Insomnia\"},"
            "\"defender\":{\"species\":\"Swampert\",\"level\":50},"
            "\"move\":{\"name\":\"Wing Attack\"}}";

        const char* req_none =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Pidgeot\",\"level\":50,\"ability\":\"None\"},"
            "\"defender\":{\"species\":\"Swampert\",\"level\":50},"
            "\"move\":{\"name\":\"Wing Attack\"}}";

        char* out1 = js_calc_calculate(req_keen_eye);
        char* out2 = js_calc_calculate(req_insomnia);
        char* out3 = js_calc_calculate(req_none);

        check_condition("Keen Eye succeeds", out1 != NULL);
        check_condition("Insomnia succeeds", out2 != NULL);
        check_condition("None succeeds", out3 != NULL);

        if (out1 && out2 && out3) {
            jl_value* d1 = jl_parse(out1);
            jl_value* d2 = jl_parse(out2);
            jl_value* d3 = jl_parse(out3);
            if (d1 && d2 && d3) {
                check_number("Keen Eye minDamage", 33, jl_get(d1, "minDamage"));
                check_number("Keen Eye maxDamage", 39, jl_get(d1, "maxDamage"));

                check_number("Insomnia minDamage", 33, jl_get(d2, "minDamage"));
                check_number("Insomnia maxDamage", 39, jl_get(d2, "maxDamage"));

                check_number("None minDamage", 33, jl_get(d3, "minDamage"));
                check_number("None maxDamage", 39, jl_get(d3, "maxDamage"));
            }
            jl_free(d1);
            jl_free(d2);
            jl_free(d3);
        }
        free(out1);
        free(out2);
        free(out3);
    }
}

/*
 * Gap C3: held items are never forwarded to the engine as raw H&S source names.
 *
 * The production policy (Kotlin) classifies an H&S item by its exact numeric ID and
 * omits it from the engine request: ITEM_NONE and a proven no-damage item both mean
 * "no item effect", and an unsupported damage item refuses the request before it is
 * built. These host checks pin the engine behaviour that policy depends on:
 *   - an omitted item and `"item": "None"` are the same calculation;
 *   - a name the engine does not model is silently a no-op (so omission is safe);
 *   - a name the engine DOES model changes damage (so an H&S source name must never
 *     be forwarded raw — the policy must refuse it).
 */
static void check_gap_c3_items(void) {
    g_fixture = "gap_c3_item_none_is_no_item";
    {
        const char* req_omitted =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Charizard\",\"level\":50},"
            "\"defender\":{\"species\":\"Snorlax\",\"level\":50},"
            "\"move\":{\"name\":\"Flamethrower\"}}";

        const char* req_none =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Charizard\",\"level\":50,\"item\":\"None\"},"
            "\"defender\":{\"species\":\"Snorlax\",\"level\":50},"
            "\"move\":{\"name\":\"Flamethrower\"}}";

        const char* req_unknown_name =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Charizard\",\"level\":50,\"item\":\"Amulet Coin\"},"
            "\"defender\":{\"species\":\"Snorlax\",\"level\":50},"
            "\"move\":{\"name\":\"Flamethrower\"}}";

        const char* req_modelled_name =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Charizard\",\"level\":50,\"item\":\"Charcoal\"},"
            "\"defender\":{\"species\":\"Snorlax\",\"level\":50},"
            "\"move\":{\"name\":\"Flamethrower\"}}";

        char* out_omitted = js_calc_calculate(req_omitted);
        char* out_none = js_calc_calculate(req_none);
        char* out_unknown = js_calc_calculate(req_unknown_name);
        char* out_modelled = js_calc_calculate(req_modelled_name);

        check_condition("H&S omitted item succeeds", out_omitted != NULL);
        check_condition("H&S item None succeeds", out_none != NULL);
        check_condition("H&S unknown item name succeeds", out_unknown != NULL);
        check_condition("H&S modelled item name succeeds", out_modelled != NULL);

        if (out_omitted && out_none && out_unknown && out_modelled) {
            jl_value* d_omitted = jl_parse(out_omitted);
            jl_value* d_none = jl_parse(out_none);
            jl_value* d_unknown = jl_parse(out_unknown);
            jl_value* d_modelled = jl_parse(out_modelled);
            if (d_omitted && d_none && d_unknown && d_modelled) {
                check_condition(
                    "ITEM_NONE equals an omitted item (omission is an explicit no-item)",
                    jl_num(jl_get(d_none, "minDamage")) == jl_num(jl_get(d_omitted, "minDamage")) &&
                        jl_num(jl_get(d_none, "maxDamage")) == jl_num(jl_get(d_omitted, "maxDamage")));
                check_condition(
                    "an unmodelled item name is a silent no-op",
                    jl_num(jl_get(d_unknown, "minDamage")) == jl_num(jl_get(d_omitted, "minDamage")) &&
                        jl_num(jl_get(d_unknown, "maxDamage")) == jl_num(jl_get(d_omitted, "maxDamage")));
                check_condition(
                    "a name the engine models changes damage (raw H&S names must never be forwarded)",
                    jl_num(jl_get(d_modelled, "minDamage")) > jl_num(jl_get(d_omitted, "minDamage")));
            }
            jl_free(d_omitted);
            jl_free(d_none);
            jl_free(d_unknown);
            jl_free(d_modelled);
        }
        free(out_omitted);
        free(out_none);
        free(out_unknown);
        free(out_modelled);
    }
}

/* ------------------------------------------------------------------ */
/* Gap C4a: independent H&S ordinary-damage arithmetic oracle          */
/* ------------------------------------------------------------------ */

/* This is a deliberately independent reference, transcribed from the pinned
 * H&S source and fpmath.h, NOT a second call into the QuickJS/ADV engine:
 *
 *   src/battle_util.c: CalculateBaseDamage
 *     power * atk * (2*level/5 + 2) / def / 50 + 2
 *   src/battle_util.c: DoMoveDamageCalcVars
 *     base(already +2) -> spread -> weather -> critical -> random roll
 *   src/battle_util.c: ApplyModifiersAfterDmgRoll
 *     -> STAB -> type effectiveness -> burn/frostbite -> screens/other
 *   include/fpmath.h: uq4_12_multiply_by_int_half_down(mod, v)
 *     (mod * v + 2047) / 4096, integer division
 *
 * The audit result this pins: the bare base path matches the ADV host exactly
 * for both the physical and special stat pairs (neutral stages), but H&S's
 * roll-before-STAB/type/burn/screens placement and UQ4.12 half-down composition
 * do NOT, so a request that exercises those modifiers is refused by
 * HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED.
 *
 * Non-neutral stat stages are deliberately NOT given a positive fixture: H&S
 * applies stages before its fixed-point ability/item composition while ADV
 * applies ability modifiers before stages, and the staged-stat rounding has not
 * been independently proven, so the policy blocks non-neutral stages (R2). */
static long hns_uq12(double v) { return (long)(v * 4096.0 + 0.5); }
static long hns_int_half_down(long modifier, long value) {
    return (modifier * value + 2047) / 4096;
}

static long hns_base_damage(int level, int bp, int atk, int def) {
    return (long)bp * atk * ((2 * level) / 5 + 2) / def / 50 + 2;
}

static void hns_calc_rolls(int level, int bp, int atk, int def,
                           int stab, double type_eff, int crit, int burn,
                           long weather_mod, long screen_mod,
                           long out[ROLL_COUNT]) {
    long dmg = hns_base_damage(level, bp, atk, def);
    if (weather_mod != 0) dmg = hns_int_half_down(weather_mod, dmg);
    if (crit) dmg = hns_int_half_down(hns_uq12(2.0), dmg);
    for (int i = 0; i < ROLL_COUNT; i++) {
        long x = (dmg * (85 + i)) / 100;
        if (stab) x = hns_int_half_down(hns_uq12(1.5), x);
        if (type_eff != 1.0) x = hns_int_half_down(hns_uq12(type_eff), x);
        if (burn) x = hns_int_half_down(hns_uq12(0.5), x);
        if (screen_mod != 0) x = hns_int_half_down(screen_mod, x);
        if (x == 0 && type_eff > 0.0) x = 1;
        out[i] = x;
    }
}

static void hns_ordinary_rolls(int level, int bp, int atk, int def,
                               int stab, double type_eff, int crit, int burn,
                               long out[ROLL_COUNT]) {
    hns_calc_rolls(level, bp, atk, def, stab, type_eff, crit, burn, 0, 0, out);
}

static int rolls_equal(const double* engine, const long* oracle) {
    for (int i = 0; i < ROLL_COUNT; i++) {
        if ((long)engine[i] != oracle[i]) return 0;
    }
    return 1;
}

/* Machamp (Atk 150) vs Snorlax (Def 85), Hardy L50 31 IV / 0 EV, ability ignored. */
#define C4A_MACHAMP_SNORLAX_HEAD \
    "\"gen\":3,\"typeSystem\":\"hns_2_0_5\"," \
    "\"attacker\":{\"species\":\"Machamp\",\"level\":50,\"nature\":\"Hardy\",\"ability\":\"(other)\"," IVS_MAX "," EVS_ZERO "}," \
    "\"defender\":{\"species\":\"Snorlax\",\"level\":50,\"nature\":\"Hardy\",\"ability\":\"(other)\"," IVS_MAX "," EVS_ZERO "},"

static void check_gap_c4a_arithmetic_parity(void) {
    double engine[ROLL_COUNT];
    long oracle[ROLL_COUNT];

    /* Neutral base: Rock Slide (Rock, 75) has no STAB vs Normal and is 1x. */
    g_fixture = "gap_c4a_parity_neutral_base_matches";
    {
        const char* req =
            "{" C4A_MACHAMP_SNORLAX_HEAD "\"move\":{\"name\":\"Rock Slide\"}}";
        char* out = js_calc_calculate(req);
        check_condition("neutral request produced a response", out != NULL);
        if (out != NULL) {
            jl_value* doc = jl_parse(out);
            if (doc != NULL && response_rolls(doc, engine) == ROLL_COUNT) {
                hns_ordinary_rolls(50, 75, 150, 85, 0, 1.0, 0, 0, oracle);
                check_condition(
                    "bare base damage matches the independent H&S oracle exactly",
                    rolls_equal(engine, oracle));
                check_int("neutral max roll", 60, (long)engine[ROLL_COUNT - 1]);
            } else {
                check_condition("neutral response carried 16 rolls", 0);
            }
            jl_free(doc);
            free(out);
        }
    }

    /* Neutral special path (Gap C4a R2): Alakazam Thunderbolt vs Snorlax. Thunderbolt is special
     * under H&S per-move split, has no STAB (Alakazam is Psychic) and is 1x vs Normal.
     *   SpA = floor((2*135 + 31 + 0)*50/100) + 5 = 155
     *   SpD = floor((2*110 + 31 + 0)*50/100) + 5 = 130
     *   base = floor(95*155*22/130/50) + 2 = 51
     * This broadens the positive parity proof beyond the physical case. */
    g_fixture = "gap_c4a_parity_neutral_special_matches";
    {
        const char* req =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Alakazam\",\"level\":50,\"nature\":\"Hardy\"," IVS_MAX "," EVS_ZERO "},"
            "\"defender\":{\"species\":\"Snorlax\",\"level\":50,\"nature\":\"Hardy\"," IVS_MAX "," EVS_ZERO "},"
            "\"move\":{\"name\":\"Thunderbolt\",\"overrides\":{\"basePower\":95,\"type\":\"Electric\",\"category\":\"Special\"}}}";
        char* out = js_calc_calculate(req);
        check_condition("neutral special request produced a response", out != NULL);
        if (out != NULL) {
            jl_value* doc = jl_parse(out);
            if (doc != NULL && response_rolls(doc, engine) == ROLL_COUNT) {
                hns_ordinary_rolls(50, 95, 155, 130, 0, 1.0, 0, 0, oracle);
                check_condition(
                    "neutral special base damage matches the independent H&S oracle exactly",
                    rolls_equal(engine, oracle));
                check_int("neutral special min roll", 43, (long)engine[0]);
                check_int("neutral special max roll", 51, (long)engine[ROLL_COUNT - 1]);
            } else {
                check_condition("neutral special response carried 16 rolls", 0);
            }
            jl_free(doc);
            free(out);
        }
    }

    /* STAB + 2x: In H&S 2.0.5, STAB and type effectiveness are applied after the roll in UQ4.12. */
    g_fixture = "gap_c4a_parity_stab_matches";
    {
        const char* req =
            "{" C4A_MACHAMP_SNORLAX_HEAD "\"move\":{\"name\":\"Karate Chop\"}}";
        char* out = js_calc_calculate(req);
        check_condition("STAB request produced a response", out != NULL);
        if (out != NULL) {
            jl_value* doc = jl_parse(out);
            if (doc != NULL && response_rolls(doc, engine) == ROLL_COUNT) {
                hns_ordinary_rolls(50, 50, 150, 85, 1, 2.0, 0, 0, oracle);
                check_condition(
                    "STAB/type damage matches the independent H&S oracle exactly",
                    rolls_equal(engine, oracle));
                check_int("STAB engine min", 102, (long)engine[0]);
                check_int("STAB oracle min", 102, oracle[0]);
            } else {
                check_condition("STAB response carried 16 rolls", 0);
            }
            jl_free(doc);
            free(out);
        }
    }

    /* Critical hit combined with STAB/type: H&S doubles before the roll and applies STAB/type after. */
    g_fixture = "gap_c4a_parity_crit_stab_matches";
    {
        const char* req =
            "{" C4A_MACHAMP_SNORLAX_HEAD "\"move\":{\"name\":\"Karate Chop\",\"isCrit\":true}}";
        char* out = js_calc_calculate(req);
        check_condition("crit request produced a response", out != NULL);
        if (out != NULL) {
            jl_value* doc = jl_parse(out);
            if (doc != NULL && response_rolls(doc, engine) == ROLL_COUNT) {
                hns_ordinary_rolls(50, 50, 150, 85, 1, 2.0, 1, 0, oracle);
                check_condition(
                    "critical-hit + STAB/type damage matches the independent H&S oracle exactly",
                    rolls_equal(engine, oracle));
            } else {
                check_condition("crit response carried 16 rolls", 0);
            }
            jl_free(doc);
            free(out);
        }
    }
}

static void check_gap_c4b_arithmetic_coverage(void) {
    double engine[ROLL_COUNT];
    long oracle[ROLL_COUNT];

    /* 1. Stat stages: +2 Atk on Machamp, -1 Def on Snorlax.
     * Atk 150 * (20/10) = 300
     * Def 85 * (10/15) = 56
     * Rock Slide (75 BP, neutral). */
    g_fixture = "gap_c4b_stat_stages_boosted";
    {
        const char* req =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Machamp\",\"level\":50,\"nature\":\"Hardy\",\"ability\":\"(other)\"," IVS_MAX "," EVS_ZERO ",\"statStages\":[0,2,0,0,0,0,0,0]},"
            "\"defender\":{\"species\":\"Snorlax\",\"level\":50,\"nature\":\"Hardy\",\"ability\":\"(other)\"," IVS_MAX "," EVS_ZERO ",\"statStages\":[0,0,-1,0,0,0,0,0]},"
            "\"move\":{\"name\":\"Rock Slide\"}}";
        char* out = js_calc_calculate(req);
        check_condition("boosted stages request produced a response", out != NULL);
        if (out != NULL) {
            jl_value* doc = jl_parse(out);
            if (doc != NULL && response_rolls(doc, engine) == ROLL_COUNT) {
                hns_ordinary_rolls(50, 75, 300, 56, 0, 1.0, 0, 0, oracle);
                check_condition(
                    "boosted stat stages (+2 Atk, -1 Def) match the H&S oracle exactly",
                    rolls_equal(engine, oracle));
            } else {
                check_condition("boosted stages response carried 16 rolls", 0);
            }
            jl_free(doc);
            free(out);
        }
    }

    /* 2. Crit stage-drop ignore: Atk -2, Def +2 with isCrit: true.
     * In H&S, a critical hit ignores attacker's negative stat stages and defender's positive stat stages.
     * Effective Atk is 150, effective Def is 85. Crit doubles base damage before roll. */
    g_fixture = "gap_c4b_crit_ignores_negative_stages";
    {
        const char* req =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Machamp\",\"level\":50,\"nature\":\"Hardy\",\"ability\":\"(other)\"," IVS_MAX "," EVS_ZERO ",\"statStages\":[0,-2,0,0,0,0,0,0]},"
            "\"defender\":{\"species\":\"Snorlax\",\"level\":50,\"nature\":\"Hardy\",\"ability\":\"(other)\"," IVS_MAX "," EVS_ZERO ",\"statStages\":[0,0,2,0,0,0,0,0]},"
            "\"move\":{\"name\":\"Rock Slide\",\"isCrit\":true}}";
        char* out = js_calc_calculate(req);
        check_condition("crit ignore stages request produced a response", out != NULL);
        if (out != NULL) {
            jl_value* doc = jl_parse(out);
            if (doc != NULL && response_rolls(doc, engine) == ROLL_COUNT) {
                hns_ordinary_rolls(50, 75, 150, 85, 0, 1.0, 1, 0, oracle);
                check_condition(
                    "crit correctly ignores attacker penalty and defender boost",
                    rolls_equal(engine, oracle));
            } else {
                check_condition("crit ignore stages response carried 16 rolls", 0);
            }
            jl_free(doc);
            free(out);
        }
    }

    /* 3. Badge boosts: Atk badge boost on attacker, Def badge boost on defender.
     * Atk: halfDown(4506, 150) = 165
     * Def: halfDown(4506, 85) = 94
     * Rock Slide (75 BP). */
    g_fixture = "gap_c4b_badge_boosts";
    {
        const char* req =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Machamp\",\"level\":50,\"nature\":\"Hardy\",\"ability\":\"(other)\"," IVS_MAX "," EVS_ZERO ",\"badgeBoosts\":{\"atk\":true}},"
            "\"defender\":{\"species\":\"Snorlax\",\"level\":50,\"nature\":\"Hardy\",\"ability\":\"(other)\"," IVS_MAX "," EVS_ZERO ",\"badgeBoosts\":{\"def\":true}},"
            "\"move\":{\"name\":\"Rock Slide\"}}";
        char* out = js_calc_calculate(req);
        check_condition("badge boosts request produced a response", out != NULL);
        if (out != NULL) {
            jl_value* doc = jl_parse(out);
            if (doc != NULL && response_rolls(doc, engine) == ROLL_COUNT) {
                hns_ordinary_rolls(50, 75, 165, 94, 0, 1.0, 0, 0, oracle);
                check_condition(
                    "badge boosts (Atk + Def) match the H&S oracle exactly",
                    rolls_equal(engine, oracle));
            } else {
                check_condition("badge boosts response carried 16 rolls", 0);
            }
            jl_free(doc);
            free(out);
        }
    }

    /* 4. Weather: Sun boost (1.5x, 6144) on Fire move.
     * Attacker: Charizard (SpA 129 L50 Hardy 31 IV / 0 EV).
     * Defender: Snorlax (SpD 130 L50 Hardy 31 IV / 0 EV).
     * Move: Flamethrower (95 BP, Fire, Special). */
    g_fixture = "gap_c4b_weather_sun";
    {
        const char* req =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Charizard\",\"level\":50,\"nature\":\"Hardy\",\"ability\":\"(other)\"," IVS_MAX "," EVS_ZERO "},"
            "\"defender\":{\"species\":\"Snorlax\",\"level\":50,\"nature\":\"Hardy\",\"ability\":\"(other)\"," IVS_MAX "," EVS_ZERO "},"
            "\"move\":{\"name\":\"Flamethrower\",\"overrides\":{\"basePower\":95,\"type\":\"Fire\",\"category\":\"Special\"}},"
            "\"field\":{\"weather\":\"Sun\"}}";
        char* out = js_calc_calculate(req);
        check_condition("weather request produced a response", out != NULL);
        if (out != NULL) {
            jl_value* doc = jl_parse(out);
            if (doc != NULL && response_rolls(doc, engine) == ROLL_COUNT) {
                hns_calc_rolls(50, 95, 129, 130, 1, 1.0, 0, 0, 6144, 0, oracle);
                check_condition(
                    "weather sun boost matches the H&S oracle exactly",
                    rolls_equal(engine, oracle));
            } else {
                check_condition("weather response carried 16 rolls", 0);
            }
            jl_free(doc);
            free(out);
        }
    }

    /* 5. Screens: Reflect on defender (singles: 0.5x, 2048 after roll).
     * Attacker: Machamp (Atk 150), Defender: Snorlax (Def 85).
     * Move: Rock Slide (75 BP, neutral). */
    g_fixture = "gap_c4b_screen_reflect";
    {
        const char* req =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Machamp\",\"level\":50,\"nature\":\"Hardy\",\"ability\":\"(other)\"," IVS_MAX "," EVS_ZERO "},"
            "\"defender\":{\"species\":\"Snorlax\",\"level\":50,\"nature\":\"Hardy\",\"ability\":\"(other)\"," IVS_MAX "," EVS_ZERO "},"
            "\"move\":{\"name\":\"Rock Slide\"},"
            "\"field\":{\"defenderSide\":{\"isReflect\":true}}}";
        char* out = js_calc_calculate(req);
        check_condition("reflect screen request produced a response", out != NULL);
        if (out != NULL) {
            jl_value* doc = jl_parse(out);
            if (doc != NULL && response_rolls(doc, engine) == ROLL_COUNT) {
                hns_calc_rolls(50, 75, 150, 85, 0, 1.0, 0, 0, 0, 2048, oracle);
                check_condition(
                    "reflect screen reduction matches the H&S oracle exactly",
                    rolls_equal(engine, oracle));
            } else {
                check_condition("reflect screen response carried 16 rolls", 0);
            }
            jl_free(doc);
            free(out);
        }
    }

    /* 6. Explicit rawStats: Overriding computed stats directly from memory words.
     * Attacker Atk: 200, Defender Def: 100.
     * Move: Rock Slide (75 BP, neutral). */
    g_fixture = "gap_c4b_explicit_raw_stats";
    {
        const char* req =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Machamp\",\"level\":50,\"nature\":\"Hardy\",\"ability\":\"(other)\"," IVS_MAX "," EVS_ZERO ",\"rawStats\":{\"attack\":200,\"defense\":100,\"speed\":100,\"spAttack\":100,\"spDefense\":100}},"
            "\"defender\":{\"species\":\"Snorlax\",\"level\":50,\"nature\":\"Hardy\",\"ability\":\"(other)\"," IVS_MAX "," EVS_ZERO ",\"rawStats\":{\"attack\":100,\"defense\":100,\"speed\":100,\"spAttack\":100,\"spDefense\":100}},"
            "\"move\":{\"name\":\"Rock Slide\"}}";
        char* out = js_calc_calculate(req);
        check_condition("rawStats request produced a response", out != NULL);
        if (out != NULL) {
            jl_value* doc = jl_parse(out);
            if (doc != NULL && response_rolls(doc, engine) == ROLL_COUNT) {
                hns_calc_rolls(50, 75, 200, 100, 0, 1.0, 0, 0, 0, 0, oracle);
                check_condition(
                    "explicit rawStats match the H&S oracle exactly",
                    rolls_equal(engine, oracle));
            } else {
                check_condition("rawStats response carried 16 rolls", 0);
            }
            jl_free(doc);
            free(out);
        }
    }

    /* 7. Doubles single-target move: Strength (80 BP, single-target).
     * Upstream H&S only halves damage when GetMoveTargetCount(ctx) == 2.
     * A single-target move in Doubles must NOT be halved. */
    g_fixture = "gap_c4b_doubles_single_target_not_halved";
    {
        const char* req =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Machamp\",\"level\":50,\"nature\":\"Hardy\",\"ability\":\"(other)\"," IVS_MAX "," EVS_ZERO "},"
            "\"defender\":{\"species\":\"Snorlax\",\"level\":50,\"nature\":\"Hardy\",\"ability\":\"(other)\"," IVS_MAX "," EVS_ZERO "},"
            "\"move\":{\"name\":\"Strength\"},"
            "\"field\":{\"gameType\":\"Doubles\"}}";
        char* out = js_calc_calculate(req);
        check_condition("doubles single-target request produced a response", out != NULL);
        if (out != NULL) {
            jl_value* doc = jl_parse(out);
            if (doc != NULL && response_rolls(doc, engine) == ROLL_COUNT) {
                /* Same damage as singles (not halved) */
                hns_ordinary_rolls(50, 80, 150, 85, 0, 1.0, 0, 0, oracle);
                check_condition(
                    "single-target move in doubles is not halved",
                    rolls_equal(engine, oracle));
            } else {
                check_condition("doubles single-target response carried 16 rolls", 0);
            }
            jl_free(doc);
            free(out);
        }
    }

    /* 8. Doubles spread move: Rock Slide (75 BP, multi-target allAdjacentFoes) with an explicit
     * GetMoveTargetCount of 2. H&S halves the move via halfDown(2048, dmg) only for count 2. */
    g_fixture = "gap_c4b_doubles_spread_two_targets_halved";
    {
        const char* req =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Machamp\",\"level\":50,\"nature\":\"Hardy\",\"ability\":\"(other)\"," IVS_MAX "," EVS_ZERO "},"
            "\"defender\":{\"species\":\"Snorlax\",\"level\":50,\"nature\":\"Hardy\",\"ability\":\"(other)\"," IVS_MAX "," EVS_ZERO "},"
            "\"move\":{\"name\":\"Rock Slide\"},"
            "\"field\":{\"gameType\":\"Doubles\",\"targetCount\":2}}";
        char* out = js_calc_calculate(req);
        check_condition("doubles spread move request produced a response", out != NULL);
        if (out != NULL) {
            jl_value* doc = jl_parse(out);
            if (doc != NULL && response_rolls(doc, engine) == ROLL_COUNT) {
                long base_dmg = hns_base_damage(50, 75, 150, 85);
                long spread_dmg = hns_int_half_down(2048, base_dmg);
                for (int i = 0; i < ROLL_COUNT; i++) {
                    oracle[i] = (spread_dmg * (85 + i)) / 100;
                }
                check_condition(
                    "spread move with two present targets is halved",
                    rolls_equal(engine, oracle));
            } else {
                check_condition("doubles two-target response carried 16 rolls", 0);
            }
            jl_free(doc);
            free(out);
        }
    }

    /* 9. Doubles spread move against only ONE remaining foe. GetMoveTargetCount == 1, so H&S
     * does NOT apply the x0.5 spread reduction; the damage must equal the singles value. */
    g_fixture = "gap_c4b_doubles_spread_one_target_not_halved";
    {
        const char* req =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Machamp\",\"level\":50,\"nature\":\"Hardy\",\"ability\":\"(other)\"," IVS_MAX "," EVS_ZERO "},"
            "\"defender\":{\"species\":\"Snorlax\",\"level\":50,\"nature\":\"Hardy\",\"ability\":\"(other)\"," IVS_MAX "," EVS_ZERO "},"
            "\"move\":{\"name\":\"Rock Slide\"},"
            "\"field\":{\"gameType\":\"Doubles\",\"targetCount\":1}}";
        char* out = js_calc_calculate(req);
        check_condition("doubles one-target request produced a response", out != NULL);
        if (out != NULL) {
            jl_value* doc = jl_parse(out);
            if (doc != NULL && response_rolls(doc, engine) == ROLL_COUNT) {
                hns_ordinary_rolls(50, 75, 150, 85, 0, 1.0, 0, 0, oracle);
                check_condition(
                    "spread move with one present target is NOT halved",
                    rolls_equal(engine, oracle));
            } else {
                check_condition("doubles one-target response carried 16 rolls", 0);
            }
            jl_free(doc);
            free(out);
        }
    }

    /* 10. Doubles spread move with no target count at all must fail closed: the engine must not
     * infer the spread modifier from gameType + move class. */
    g_fixture = "gap_c4b_doubles_spread_missing_target_count_refused";
    {
        const char* req =
            "{\"gen\":3,\"typeSystem\":\"hns_2_0_5\","
            "\"attacker\":{\"species\":\"Machamp\",\"level\":50,\"nature\":\"Hardy\",\"ability\":\"(other)\"," IVS_MAX "," EVS_ZERO "},"
            "\"defender\":{\"species\":\"Snorlax\",\"level\":50,\"nature\":\"Hardy\",\"ability\":\"(other)\"," IVS_MAX "," EVS_ZERO "},"
            "\"move\":{\"name\":\"Rock Slide\"},"
            "\"field\":{\"gameType\":\"Doubles\"}}";
        char* out = js_calc_calculate(req);
        check_condition("doubles spread move without target count produced a response", out != NULL);
        if (out != NULL) {
            jl_value* doc = jl_parse(out);
            check_condition(
                "missing target count must refuse rather than guess the spread modifier",
                doc != NULL && jl_is_bool(jl_get(doc, "success")) &&
                    !jl_bool(jl_get(doc, "success")) && jl_get(doc, "error") != NULL);
            jl_free(doc);
            free(out);
        }
    }
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

    printf("-- Gap B: authoritative species/move overrides, category, isolation, malformed --\n");
    check_gap_b_data_overrides();

    printf("-- Gap C1: exact type system + Fairy toggle behavior --\n");
    check_gap_c1_type_system();

    printf("-- Gap C2: authoritative effective ability input + conditional ability support --\n");
    check_gap_c2_abilities();

    printf("-- Gap C3: held-item no-op / name-safety contract --\n");
    check_gap_c3_items();

    printf("-- Gap C4a: ordinary-damage arithmetic parity vs an independent H&S oracle --\n");
    check_gap_c4a_arithmetic_parity();

    printf("-- Gap C4b: arithmetic coverage (stat stages, crit ignores, badges, weather, screens, rawStats, target count) --\n");
    check_gap_c4b_arithmetic_coverage();

    printf("-- checker and parser self-tests (the oracle must reject bad responses) --\n");
    check_oracle_self_tests();
    check_smoke_path_self_tests();
    check_parser_self_tests();

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
