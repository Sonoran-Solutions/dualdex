package com.dualdex.calculator

import com.dualdex.calculator.census.HnsCalcCensusBaseline as Baseline
import com.dualdex.pokemon.hns.*
import org.junit.Assert.*
import org.junit.Test

/** Compiles unchanged at the immutable starting head for the admission negative control. */
class HnsUnderwaterAdmissionTest {
    private fun observation(attacker: Boolean) = Baseline.observation(
        if(attacker) Baseline.Participant.ATTACKER else Baseline.Participant.DEFENDER,
        if(attacker) 0 else 1, if(attacker) 68 else 143,
        if(attacker) "Machamp" else "Snorlax", if(attacker) listOf("Fighting") else listOf("Normal"),
        15,0,"Insomnia",Hns205ItemCatalogue.get(0),2)
    @Test fun `both neutral underwater family admissions reach Ready`() {
        val failures=mutableListOf<String>()
        for(move in listOf("Surf","Whirlpool")) {
            val request=DamageCalculationRequest(
                attacker=CalcPokemonInput("Machamp",50,origin=CalcInputOrigin.LIVE_READ,partySlot=0),
                defender=CalcPokemonInput("Snorlax",50,origin=CalcInputOrigin.LIVE_READ,partySlot=1),
                move=CalcMoveInput(move),field=CalcFieldInput(gameType="Singles"))
            val outcome=CalcRequestBoundary.build(Baseline.profile,Baseline.trust,request,Baseline.challengeSettings,
                observation(true),observation(false),activeBattle=true)
            if(outcome !is CalcRequestOutcome.Ready) failures.add("$move: $outcome")
        }
        assertTrue(failures.joinToString("\n"),failures.isEmpty())
    }
}
