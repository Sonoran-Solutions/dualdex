package com.dualdex.calculator

/**
 * The Generation III moves whose damage the shipped `@smogon/calc` ADV pipeline reduces in a
 * Doubles battle, derived from the pinned decompilations rather than typed by hand.
 *
 * Why this table exists
 * ---------------------
 * `tools/calc-bundler/entry.js` applies the Doubles spread reduction through
 * `@smogon/calc`'s ADV logic with `move.target === 'allAdjacentFoes'`. The Kotlin capability policy
 * has to know which requests take that branch, because the pipeline's reduction is **not** the
 * cartridge's: the pinned engines halve a spread move only while
 * `CountAliveMonsInBattle(BATTLE_ALIVE_DEF_SIDE) == 2`
 * (`pret/pokefirered src/pokemon.c:2553` physical, `:2604` special; identical at
 * `pret/pokeemerald src/pokemon.c:3276` and `:3327`), and the vanilla request shape carries no
 * target-presence operand. The pipeline reduces whenever the format label says Doubles, so a lone
 * remaining opponent would be reduced where the cartridge does not. The shape therefore fails
 * closed for `VANILLA_GEN3` ([CalcLimitation.VANILLA_DOUBLES_SPREAD_NOT_MODELLED]) and this table is
 * what identifies it.
 *
 * Derivation
 * ----------
 * `pret/pokeemerald @ 5eff78649e7170a877b961ef0b3da13b81a16038` (the same pinned revision the
 * golden oracle uses) declares 27 moves with `MOVE_TARGET_BOTH` or `MOVE_TARGET_FOES_AND_ALLY` in
 * `src/data/battle_moves.h`. Exactly two classes of those matter to this gate:
 *
 *  * the 17 listed below have `MOVE_TARGET_BOTH` **and** non-zero power, and are the moves the
 *    shipped pipeline halves in Doubles. Confirmed exhaustively against the shipped bundle by
 *    `native/tests/test_js_calc.c :: check_gen3_spread_move_table_matches_engine()`, which recovers
 *    each move's pre-roll damage in Singles and in Doubles and requires Doubles to be exactly
 *    `floor(singles / 2)` for every name here and for no other move in the Generation III move list;
 *  * the remaining 10 are `MOVE_TARGET_FOES_AND_ALLY` (`Earthquake`, `Explosion`, `Magnitude`,
 *    `Selfdestruct`, `Teeter Dance`) or zero-power status moves (`Growl`, `Leer`, `String Shot`,
 *    `Sweet Scent`, `Tail Whip`). The shipped pipeline does NOT reduce the `FOES_AND_ALLY` moves
 *    and the status moves deal no damage, so neither class can reach the problem this gate closes.
 *    They are deliberately absent, and the host test above fails if that ever stops being true.
 */
internal object Gen3DoublesSpreadMoves {

    /**
     * Move names as the engine matches them (its own spelling), with the pinned symbol each name
     * resolves to so a reviewer can re-derive the list from the decompilation.
     *
     * `tools/calc-goldens/gen3_move_targets.json` carries the same moves in the pinned source's own
     * upper-case spelling (`ROCK SLIDE`); the engine's name resolution is case-insensitive and
     * [isSpread] matches the same way, so the two spellings identify the same moves.
     */
    private val SPREAD_MOVES: Map<String, String> = mapOf(
        "Acid" to "MOVE_ACID",
        "Air Cutter" to "MOVE_AIR_CUTTER",
        "Blizzard" to "MOVE_BLIZZARD",
        "Bubble" to "MOVE_BUBBLE",
        "Eruption" to "MOVE_ERUPTION",
        "Heat Wave" to "MOVE_HEAT_WAVE",
        "Hyper Voice" to "MOVE_HYPER_VOICE",
        "Icy Wind" to "MOVE_ICY_WIND",
        "Muddy Water" to "MOVE_MUDDY_WATER",
        "Powder Snow" to "MOVE_POWDER_SNOW",
        "Razor Leaf" to "MOVE_RAZOR_LEAF",
        "Razor Wind" to "MOVE_RAZOR_WIND",
        "Rock Slide" to "MOVE_ROCK_SLIDE",
        "Surf" to "MOVE_SURF",
        "Swift" to "MOVE_SWIFT",
        "Twister" to "MOVE_TWISTER",
        "Water Spout" to "MOVE_WATER_SPOUT"
    )

    /** Every name in this table, for tests that must cover the whole set. */
    val names: Set<String> get() = SPREAD_MOVES.keys

    /** The pinned symbol a name was derived from, or null when the move is not in the table. */
    fun pinnedConstant(moveName: String): String? = SPREAD_MOVES[moveName]

    /**
     * True when [moveName] is a Generation III move the shipped pipeline reduces in Doubles.
     *
     * Matching is case-insensitive and ignores surrounding whitespace, because this decides whether
     * a request is refused: a spelling the engine would still classify as the same move must not be
     * able to slip past the gate. An unknown name is `false` — it is refused earlier by
     * [CalcLimitation.MOVE_NOT_IN_PINNED_DATA] when the pinned pack cannot resolve it, so claiming
     * "not spread" here cannot let an unresolvable move through.
     */
    fun isSpread(moveName: String): Boolean = SPREAD_MOVES.keys.any { it.equals(moveName.trim(), ignoreCase = true) }
}
