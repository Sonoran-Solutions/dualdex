package com.dualdex.calculator

import com.dualdex.calculator.census.HnsCalcCensusBaseline as Baseline
import org.junit.Assert.*
import org.junit.Test

/** Uses only APIs present at merged Slice 12 starting head 2d57cf8. */
class HnsVariableMultiHitStartingHeadTest {
    @Test fun `twelve moves refuse solely for move mechanics on starting head`() {
        fun observation(attacker: Boolean) = Baseline.observation(
            if (attacker) Baseline.Participant.ATTACKER else Baseline.Participant.DEFENDER,
            if (attacker) 0 else 1, if (attacker) 68 else 9,
            if (attacker) "Machamp" else "Blastoise",
            if (attacker) listOf("Fighting") else listOf("Water"),
            15, 0, "Insomnia", null, 2
        )
        val moves = listOf(
            "Arm Thrust", "Bone Rush", "Bullet Seed", "Comet Punch", "Double Slap", "Fury Attack",
            "Fury Swipes", "Icicle Spear", "Pin Missile", "Rock Blast", "Spike Cannon", "Tail Slap"
        )
        for (move in moves) {
            val request = DamageCalculationRequest(
                attacker = CalcPokemonInput(species = "Machamp", level = 50, origin = CalcInputOrigin.LIVE_READ, partySlot = 0),
                defender = CalcPokemonInput(species = "Blastoise", level = 50, origin = CalcInputOrigin.LIVE_READ, partySlot = 1),
                move = CalcMoveInput(move), field = CalcFieldInput(gameType = "Singles")
            )
            val result = CalcRequestBoundary.build(Baseline.profile, Baseline.trust, request,
                Baseline.challengeSettings, observation(true), observation(false), activeBattle = true)
            assertTrue("$move: $result", result is CalcRequestOutcome.Refused)
            assertEquals(move, listOf(CalcLimitation.HNS_MOVE_MECHANICS_NOT_MODELLED),
                (result as CalcRequestOutcome.Refused).verdict.blockingLimitations)
        }
    }
}
