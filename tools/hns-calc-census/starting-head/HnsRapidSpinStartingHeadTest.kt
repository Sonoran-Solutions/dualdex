package com.dualdex.calculator

import com.dualdex.calculator.census.HnsCalcCensusBaseline as Baseline
import org.junit.Assert.*
import org.junit.Test

/**
 * Uses only the API available at immutable starting main 225dfcdce0d98651e1a9c85bb3c311cf71e1c01b.
 * With DUALDEX_RAPID_SPIN_OLD_HEAD=true a neutral Rapid Spin must keep the move-mechanics refusal;
 * on the current head the same neutral request must be admitted with no blocking limitation.
 */
class HnsRapidSpinStartingHeadTest {
    @Test fun `neutral Rapid Spin keeps its refusal reason across the Slice 17 boundary`() {
        fun observation(a: Boolean) = Baseline.observation(
            if (a) Baseline.Participant.ATTACKER else Baseline.Participant.DEFENDER,
            if (a) 0 else 1, if (a) 68 else 9, if (a) "Machamp" else "Blastoise",
            if (a) listOf("Fighting") else listOf("Water"), 15, 0, "Insomnia", null, 2)
        val request = DamageCalculationRequest(
            attacker = CalcPokemonInput(species = "Machamp", level = 50, origin = CalcInputOrigin.LIVE_READ, partySlot = 0),
            defender = CalcPokemonInput(species = "Blastoise", level = 50, origin = CalcInputOrigin.LIVE_READ, partySlot = 1),
            move = CalcMoveInput("Rapid Spin"), field = CalcFieldInput(gameType = "Singles"))
        val result = CalcRequestBoundary.build(Baseline.profile, Baseline.trust, request, Baseline.challengeSettings,
            observation(true), observation(false), activeBattle = true)
        if (System.getenv("DUALDEX_RAPID_SPIN_OLD_HEAD") == "true") {
            // Compared by name so this file compiles against the starting head, which lacks Slice 17 codes.
            assertTrue("Rapid Spin: $result", result is CalcRequestOutcome.Refused)
            assertEquals(listOf("HNS_MOVE_MECHANICS_NOT_MODELLED"),
                (result as CalcRequestOutcome.Refused).verdict.blockingLimitations.map { it.name })
        } else {
            assertTrue("Rapid Spin must be admitted on the current head: $result", result is CalcRequestOutcome.Ready)
        }
    }
}
