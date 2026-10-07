package com.dualdex.calculator

import com.dualdex.calculator.census.HnsCalcCensusBaseline as Baseline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Admission-only control intentionally using APIs present at merged Slice-13 starting head. */
class HnsScaleShotStartingHeadTest {
    @Test fun `Scale Shot is refused by the existing move mechanics gate`() {
        fun observation(attacker: Boolean) = Baseline.observation(
            if (attacker) Baseline.Participant.ATTACKER else Baseline.Participant.DEFENDER,
            if (attacker) 0 else 1, if (attacker) 68 else 143,
            if (attacker) "Machamp" else "Snorlax",
            if (attacker) listOf("Fighting") else listOf("Normal"), 15, 0, "Insomnia", null, 2
        )
        val request = DamageCalculationRequest(
            attacker=CalcPokemonInput(species="Machamp",level=50,origin=CalcInputOrigin.LIVE_READ,partySlot=0),
            defender=CalcPokemonInput(species="Snorlax",level=50,origin=CalcInputOrigin.LIVE_READ,partySlot=1),
            move=CalcMoveInput("Scale Shot"),field=CalcFieldInput(gameType="Singles"))
        val result=CalcRequestBoundary.build(Baseline.profile,Baseline.trust,request,
            Baseline.challengeSettings,observation(true),observation(false),activeBattle=true)
        assertTrue("$result",result is CalcRequestOutcome.Refused)
        assertEquals(listOf(CalcLimitation.HNS_MOVE_MECHANICS_NOT_MODELLED),
            (result as CalcRequestOutcome.Refused).verdict.blockingLimitations)
    }
}
