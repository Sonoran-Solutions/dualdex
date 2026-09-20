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
