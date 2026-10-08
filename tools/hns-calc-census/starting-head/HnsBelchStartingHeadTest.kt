package com.dualdex.calculator

import com.dualdex.calculator.census.HnsCalcCensusBaseline as Baseline
import org.junit.Assert.*
import org.junit.Test

/**
 * Uses only the API available at immutable starting main cd81bcc42a7916b62a7d2649549de43aa98a0425.
 * With DUALDEX_BELCH_OLD_HEAD=true a neutral Belch must keep the move-mechanics refusal; on the
 * current head the same neutral request must refuse for the missing party-member Berry state.
 */
class HnsBelchStartingHeadTest {
    @Test fun `neutral Belch keeps its refusal reason across the Slice 16 boundary`() {
        fun observation(a: Boolean) = Baseline.observation(
            if (a) Baseline.Participant.ATTACKER else Baseline.Participant.DEFENDER,
            if (a) 0 else 1, if (a) 68 else 9, if (a) "Machamp" else "Blastoise",
            if (a) listOf("Fighting") else listOf("Water"), 15, 0, "Insomnia", null, 2)
        val request = DamageCalculationRequest(
            attacker = CalcPokemonInput(species = "Machamp", level = 50, origin = CalcInputOrigin.LIVE_READ, partySlot = 0),
            defender = CalcPokemonInput(species = "Blastoise", level = 50, origin = CalcInputOrigin.LIVE_READ, partySlot = 1),
            move = CalcMoveInput("Belch"), field = CalcFieldInput(gameType = "Singles"))
        val result = CalcRequestBoundary.build(Baseline.profile, Baseline.trust, request, Baseline.challengeSettings,
            observation(true), observation(false), activeBattle = true)
        // Compared by name so this file compiles against the starting head, which lacks the Slice 16 codes.
        val expected = if (System.getenv("DUALDEX_BELCH_OLD_HEAD") == "true") "HNS_MOVE_MECHANICS_NOT_MODELLED"
            else "HNS_BELCH_BERRY_STATE_UNKNOWN"
        assertTrue("Belch: $result", result is CalcRequestOutcome.Refused)
        assertEquals(listOf(expected),
            (result as CalcRequestOutcome.Refused).verdict.blockingLimitations.map { it.name })
    }
}
