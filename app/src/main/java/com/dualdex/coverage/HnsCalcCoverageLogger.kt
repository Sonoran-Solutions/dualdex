package com.dualdex.coverage

import com.dualdex.battle.BattleHnsCalculationContext
import com.dualdex.calculator.CalcRequestOutcome
import com.dualdex.pokemon.MoveInfo
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.romhack.RomHackProfile

/** Observes Battle authorization only. Implementations must never affect calculator authority. */
interface HnsCalcCoverageLogger {
    fun sessionToken(): Long? = null
    fun battle(active: Boolean)
    fun record(move: MoveInfo, defender: ParsedPokemon, profile: RomHackProfile,
               context: BattleHnsCalculationContext, outcome: CalcRequestOutcome)
}

object NoOpHnsCalcCoverageLogger : HnsCalcCoverageLogger {
    override fun battle(active: Boolean) = Unit
    override fun record(move: MoveInfo, defender: ParsedPokemon, profile: RomHackProfile,
                        context: BattleHnsCalculationContext, outcome: CalcRequestOutcome) = Unit
}

/** The release factory resolves directly to NoOp; it never constructs debug infrastructure. */
object HnsCoverage {
    val logger: HnsCalcCoverageLogger = HnsCoverageFactory.create()

    fun sessionToken(): Long? = runCatching { logger.sessionToken() }.getOrNull()
    fun battle(active: Boolean) { runCatching { logger.battle(active) } }
    fun observe(move: MoveInfo, defender: ParsedPokemon, profile: RomHackProfile,
                context: BattleHnsCalculationContext, outcome: CalcRequestOutcome,
                observer: HnsCalcCoverageLogger = logger) {
        runCatching { observer.record(move, defender, profile, context, outcome) }
    }
}
