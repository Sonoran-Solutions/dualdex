package com.dualdex.calculator

import com.dualdex.calculator.census.HnsCalcCensusBaseline as Baseline
import org.junit.Assert.*
import org.junit.Test

/** Compiles unchanged on immutable pre-slice main; intentionally uses no new authority or enum. */
class HnsElectroBallStartingHeadTest {
    @Test fun `otherwise valid neutral Electro Ball admission`() {
        fun observation(a: Boolean)=Baseline.observation(
            if(a) Baseline.Participant.ATTACKER else Baseline.Participant.DEFENDER,
            if(a) 0 else 1,if(a) 68 else 9,if(a) "Machamp" else "Blastoise",
            if(a) listOf("Fighting") else listOf("Water"),15,0,"Insomnia",null,2)
        val request=DamageCalculationRequest(
            attacker=CalcPokemonInput(species="Machamp",level=50,origin=CalcInputOrigin.LIVE_READ,partySlot=0),
            defender=CalcPokemonInput(species="Blastoise",level=50,origin=CalcInputOrigin.LIVE_READ,partySlot=1),
            move=CalcMoveInput("Electro Ball"),field=CalcFieldInput(gameType="Singles"))
        val result=CalcRequestBoundary.build(Baseline.profile,Baseline.trust,request,Baseline.challengeSettings,
            observation(true),observation(false),activeBattle=true)
        if(System.getenv("DUALDEX_ELECTRO_OLD_HEAD")=="true") {
            assertTrue(result is CalcRequestOutcome.Refused)
            assertEquals(listOf(CalcLimitation.HNS_MOVE_MECHANICS_NOT_MODELLED),
                (result as CalcRequestOutcome.Refused).verdict.blockingLimitations)
        } else assertTrue(result.toString(),result is CalcRequestOutcome.Ready)
    }
}
