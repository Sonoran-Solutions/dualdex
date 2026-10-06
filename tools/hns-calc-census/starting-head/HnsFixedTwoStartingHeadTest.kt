package com.dualdex.calculator

import com.dualdex.calculator.census.HnsCalcCensusBaseline as Baseline
import org.junit.Assert.*
import org.junit.Test

/** Uses only APIs at immutable 32c23e8; no slice-12 state/classes. */
class HnsFixedTwoStartingHeadTest {
    @Test fun `six moves refuse solely for move mechanics on starting head`() {
        fun observation(a: Boolean)=Baseline.observation(
            if(a) Baseline.Participant.ATTACKER else Baseline.Participant.DEFENDER,
            if(a) 0 else 1,if(a) 68 else 9,if(a) "Machamp" else "Blastoise",
            if(a) listOf("Fighting") else listOf("Water"),15,0,"Insomnia",null,2)
        for(move in listOf("Bonemerang","Double Hit","Double Kick","Dual Chop","Dual Wingbeat","Twin Beam")) {
            val request=DamageCalculationRequest(
                attacker=CalcPokemonInput(species="Machamp",level=50,origin=CalcInputOrigin.LIVE_READ,partySlot=0),
                defender=CalcPokemonInput(species="Blastoise",level=50,origin=CalcInputOrigin.LIVE_READ,partySlot=1),
                move=CalcMoveInput(move),field=CalcFieldInput(gameType="Singles"))
            val result=CalcRequestBoundary.build(Baseline.profile,Baseline.trust,request,Baseline.challengeSettings,
                observation(true),observation(false),activeBattle=true)
            assertTrue(result.toString(),result is CalcRequestOutcome.Refused)
            assertEquals(listOf(CalcLimitation.HNS_MOVE_MECHANICS_NOT_MODELLED),
                (result as CalcRequestOutcome.Refused).verdict.blockingLimitations)
        }
    }
}
