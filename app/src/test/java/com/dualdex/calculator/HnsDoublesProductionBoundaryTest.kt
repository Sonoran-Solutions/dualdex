package com.dualdex.calculator

import com.dualdex.calculator.census.HnsCalcCensusBaseline as Baseline
import com.dualdex.battle.DamageBlockerPresentation
import com.dualdex.pokemon.hns.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** End-to-end real boundary -> production serializer -> shipped JS. No helper damage formula. */
class HnsDoublesProductionBoundaryTest {
    private fun observation(attacker: Boolean): BattlerRuntimeObservation = Baseline.observation(
        if (attacker) Baseline.Participant.ATTACKER else Baseline.Participant.DEFENDER,
        if (attacker) 0 else 1, if (attacker) 68 else 143,
        if (attacker) "Machamp" else "Snorlax", if (attacker) listOf("Fighting") else listOf("Normal"),
        15, 0, "Insomnia", Hns205ItemCatalogue.get(0), 4).let { o -> o.copy(state = o.state.copy(
            rawAttack = 151, rawSpAttack = 151, rawDefense = 109, rawSpDefense = 109,
            rawSpeed = if (attacker) 101 else 100, hp = 60000, maxHp = 60000)) }

    private val baseA = observation(true)
    private val baseD = observation(false)
    private fun packet() = Baseline.doublesPacket(baseA.state, baseD.state)
    private fun request(move: String = "Strength", format: String = "Doubles") = DamageCalculationRequest(
        attacker = CalcPokemonInput(species = "Machamp", level = 50, origin = CalcInputOrigin.LIVE_READ, partySlot = 0),
        defender = CalcPokemonInput(species = "Snorlax", level = 50, origin = CalcInputOrigin.LIVE_READ, partySlot = 1),
        move = CalcMoveInput(move), field = CalcFieldInput(gameType = format))

    private fun build(move: String = "Strength", p: HnsDoublesRuntimeState? = packet(),
                      a: HnsBattlerRuntimeState = baseA.state, d: HnsBattlerRuntimeState = baseD.state,
                      format: String = "Doubles", defenderPacket: HnsDoublesRuntimeState? = p): CalcRequestOutcome = CalcRequestBoundary.build(
        Baseline.profile, Baseline.trust, request(move, format), Baseline.challengeSettings,
        baseA.copy(state = a.copy(doubles = p)), baseD.copy(state = d.copy(doubles = defenderPacket)), activeBattle = true)

    private fun ready(out: CalcRequestOutcome): CalcRequestOutcome.Ready =
        (out as? CalcRequestOutcome.Ready) ?: error("Expected Ready: $out")
    private fun refusal(out: CalcRequestOutcome, reason: CalcLimitation) {
        val r = out as? CalcRequestOutcome.Refused ?: error("Expected refusal: $out")
        assertTrue("Expected $reason: ${r.verdict.limitations}", reason in r.verdict.blockingLimitations)
        assertNull(r.verdict.request)
    }
    private val root: File get() = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .first { File(it, "ci.sh").isFile }
    private fun damage(out: CalcRequestOutcome): List<Int> {
        val r = ready(out)
        val process = ProcessBuilder("node", File(root,"tools/calc-bundler/run_production_request.js").path,
            File(root,"app/src/main/assets/calc_bundle.js").path).redirectErrorStream(true).start()
        process.outputStream.bufferedWriter().use { it.write(checkNotNull(buildCalcRequestJson(r.request))) }
        val text = process.inputStream.bufferedReader().readText()
        assertEquals(text, 0, process.waitFor())
        val json = JSONObject(text)
        assertTrue(text, json.getBoolean("success"))
        val rolls = json.getJSONArray("damage")
        assertEquals(16, rolls.length())
        return (0..15).map { rolls.getInt(it) }
    }
    private fun changePartner(p: HnsDoublesRuntimeState, i: Int, ability: Int, gastro: Boolean = false) =
        p.copy(battlers = p.battlers.map { if (it.index == i) it.copy(ability = ability, gastroAcid = gastro) else it })

    @Test fun `Doubles authority waits for agreed observed switch-in settlement`() {
        val invalidPhases = listOf(
            true to false, // Readable pending events.
            false to false, // Unread phase.
            false to true // An unobserved settled flag is not evidence.
        )
        for ((observed, settled) in invalidPhases) {
            val a = baseA.state.copy(switchInPhaseObserved = observed, switchInEventsSettled = settled)
            val d = baseD.state.copy(switchInPhaseObserved = observed, switchInEventsSettled = settled)
            for ((attacker, defender) in listOf(a to baseD.state, baseA.state to d, a to d)) {
                val out = build(a = attacker, d = defender)
                refusal(out, CalcLimitation.HNS_DOUBLES_SWITCH_IN_UNSETTLED)
                assertTrue(DamageBlockerPresentation.from((out as CalcRequestOutcome.Refused).verdict,
                    observedDoubles = true).any { it.headline == "Doubles switch-in state unresolved" })
            }
        }
        val settled = ready(build())
        assertEquals(true, settled.request.hnsLiveBattleState!!.switchInEventsSettled)
        assertNotNull(settled.request.hnsLiveBattleState!!.doubles)
        damage(settled)
        // A previous authorized request's phase and packet cannot replace current evidence.
        val replay = CalcRequestBoundary.build(Baseline.profile, Baseline.trust, settled.request,
            Baseline.challengeSettings,
            baseA.copy(state = baseA.state.copy(doubles = packet(), switchInEventsSettled = false)),
            baseD.copy(state = baseD.state.copy(doubles = packet(), switchInEventsSettled = false)),
            activeBattle = true)
        refusal(replay, CalcLimitation.HNS_DOUBLES_SWITCH_IN_UNSETTLED)
        // Partner switch-in writers can mutate selected stats, weather or identities while the
        // full four-battler packet already looks internally consistent.
        for (index in listOf(2, 3)) for (ability in listOf(22, 2, 36)) {
            val p = changePartner(packet(), index, ability) // Intimidate, Drizzle, Trace.
            val pending = build(p = p, a = baseA.state.copy(switchInEventsSettled = false),
                d = baseD.state.copy(switchInEventsSettled = false))
            refusal(pending, CalcLimitation.HNS_DOUBLES_SWITCH_IN_UNSETTLED)
            ready(build(p = p))
        }
        // Preserve Singles' existing per-mechanic phase requirements.
        ready(build(p = null, a = baseA.state.copy(battlersCount = 2, switchInPhaseObserved = false),
            d = baseD.state.copy(battlersCount = 2, switchInPhaseObserved = false), format = "Singles"))
    }

    @Test fun `four live battlers authorize only with complete matching partner authority`() {
        val r = ready(build())
        assertEquals(4, r.request.hnsLiveBattleState!!.observedBattlersCount)
        assertEquals(1, r.request.hnsLiveBattleState!!.moveTargetCount)
        assertFalse(CalcLimitation.HNS_LIVE_BATTLE_FORMAT_NOT_MODELLED in r.verdict.limitations)
        refusal(build(p = null), CalcLimitation.HNS_DOUBLES_PARTNER_STATE_UNKNOWN)
        refusal(build(defenderPacket = packet().copy(followMeTimers = listOf(1,0))), CalcLimitation.HNS_DOUBLES_PARTNER_STATE_UNKNOWN)
        // A previously authorized/caller-owned packet cannot replace missing current observations.
        refusal(CalcRequestBoundary.build(Baseline.profile,Baseline.trust,r.request,
            Baseline.challengeSettings,baseA,baseD,activeBattle=true),CalcLimitation.HNS_DOUBLES_PARTNER_STATE_UNKNOWN)
        refusal(build(a = baseA.state.copy(battlersCountReadable = false)), CalcLimitation.HNS_LIVE_BATTLE_FORMAT_NOT_MODELLED)
        refusal(build(d = baseD.state.copy(battlersCount = 2)), CalcLimitation.HNS_LIVE_BATTLE_FORMAT_NOT_MODELLED)
        refusal(build(format = "Singles"), CalcLimitation.HNS_LIVE_BATTLE_FORMAT_NOT_MODELLED)
        refusal(build(a = baseA.state.copy(battlersCount = 2), d = baseD.state.copy(battlersCount = 2)), CalcLimitation.HNS_LIVE_BATTLE_FORMAT_NOT_MODELLED)
        refusal(build(d = baseD.state.copy(absentBattlerFlags = 8)), CalcLimitation.HNS_DOUBLES_PARTNER_STATE_UNKNOWN)
        refusal(build(a = baseA.state.copy(absentFlagsReadable = false)), CalcLimitation.HNS_DOUBLES_TARGET_COUNT_NOT_MODELLED)
        refusal(build(a = baseA.state.copy(battlerIndex = 4)), CalcLimitation.HNS_DOUBLES_TARGET_COUNT_NOT_MODELLED)
        refusal(build(p = packet().copy(battlers = packet().battlers.dropLast(1))), CalcLimitation.HNS_DOUBLES_PARTNER_STATE_UNKNOWN)
        refusal(build(p = packet().copy(battlers = packet().battlers.map { if (it.index == 2) it.copy(index = 3) else it })), CalcLimitation.HNS_DOUBLES_PARTNER_STATE_UNKNOWN)
        refusal(build(p = changePartner(packet(), 2, 999)), CalcLimitation.HNS_DOUBLES_PARTNER_STATE_UNKNOWN)
        refusal(build(a = baseA.state.copy(persistentVolatilesObserved = false)), CalcLimitation.HNS_DOUBLES_PARTNER_STATE_UNKNOWN)
    }

    @Test fun `spread two targets one absent and liveness classes execute correct distinct arithmetic`() {
        val two = ready(build("Rock Slide"))
        assertEquals(2, two.request.hnsLiveBattleState!!.moveTargetCount)
        val absent = packet().copy(absentFlags = 8)
        val one = build("Rock Slide", absent, baseA.state.copy(absentBattlerFlags = 8), baseD.state.copy(absentBattlerFlags = 8))
        assertEquals(1, ready(one).request.hnsLiveBattleState!!.moveTargetCount)
        assertTrue(damage(two).zip(damage(one)).all { (t, o) -> t < o })
        val singles = build("Rock Slide", null, baseA.state.copy(battlersCount = 2), baseD.state.copy(battlersCount = 2), "Singles")
        assertEquals(damage(singles), damage(one))
        val nonspread = build("Strength", absent, baseA.state.copy(absentBattlerFlags = 8), baseD.state.copy(absentBattlerFlags = 8))
        assertEquals(damage(build()), damage(nonspread))
        assertEquals(3, ready(build("Petal Blizzard")).request.hnsLiveBattleState!!.moveTargetCount)
        val three = damage(build("Petal Blizzard"))
        val twoAllies = build("Petal Blizzard", absent, baseA.state.copy(absentBattlerFlags = 8), baseD.state.copy(absentBattlerFlags = 8))
        assertEquals(2,ready(twoAllies).request.hnsLiveBattleState!!.moveTargetCount)
        assertTrue(damage(twoAllies).zip(three).all { (t,a) -> t < a })
        refusal(build("Surf"),CalcLimitation.HNS_MOVE_MECHANICS_NOT_MODELLED)
        val fainted = packet().copy(battlers = packet().battlers.map { if (it.index == 3) it.copy(hp = 0) else it })
        // Pinned BOTH counts absent flags, not HP. Fainted but not absent remains count 2.
        assertEquals(2, ready(build("Rock Slide", fainted)).request.hnsLiveBattleState!!.moveTargetCount)
    }

    @Test fun `Doubles screens preserve source rounding and differ from Singles`() {
        for ((move, screen) in listOf("Strength" to 1, "Psychic" to 2)) {
            val neutral = damage(build(move))
            val doubles = damage(build(move, d = baseD.state.copy(sideStatuses = screen)))
            val singles = damage(build(move, null, baseA.state.copy(battlersCount = 2), baseD.state.copy(battlersCount = 2, sideStatuses = screen), "Singles"))
            assertTrue(neutral.zip(doubles).all { (n,s) -> n > s })
            assertTrue(doubles.zip(singles).all { (d,s) -> d > s })
            val p = packet().copy(absentFlags = 8)
            assertEquals(doubles, damage(build(move, p, baseA.state.copy(absentBattlerFlags = 8), baseD.state.copy(absentBattlerFlags = 8, sideStatuses = screen))))
        }
    }

    @Test fun `Helping Hand partner base power Plus Minus and Friend Guard reach shipped bundle`() {
        val control = damage(build())
        val hh = packet().copy(battlers = packet().battlers.map { if (it.index == 0) it.copy(helpingHand = 2) else it })
        assertTrue(damage(build(p = hh)).zip(control).all { (b,c) -> b > c })
        for ((ability, move) in listOf(217 to "Psychic", 249 to "Strength", 252 to "Iron Head")) {
            val neutral = damage(build(move))
            val p = changePartner(packet(), 2, ability)
            assertTrue(damage(build(move, p)).zip(neutral).all { (b,c) -> b > c })
            assertEquals(neutral, damage(build(move, changePartner(packet(), 2, ability, true))))
        }
        val friend = changePartner(packet(), 3, 132)
        assertTrue(damage(build(p = friend)).zip(control).all { (b,c) -> b < c })
        assertEquals(control, damage(build(p = changePartner(packet(), 3, 132, true))))
        for (id in listOf(57,58)) {
            val a = baseA.state.copy(abilityId = id)
            val p = changePartner(packet().copy(battlers = packet().battlers.map { if (it.index == 0) it.copy(ability = id) else it }), 2, 57)
            assertTrue(damage(build("Psychic", p, a)).zip(damage(build("Psychic"))).all { (b,c) -> b > c })
            assertEquals(damage(build()),damage(build("Strength",p,a)))
        }
    }

    @Test fun `exact Plus survives a separate caveated ability on the defender`() {
        val a = baseA.state.copy(abilityId = 57)
        val d = baseD.state.copy(abilityId = 5)
        val p = changePartner(packet().copy(battlers = packet().battlers.map {
            when (it.index) { 0 -> it.copy(ability=57); 1 -> it.copy(ability=5); else -> it }
        }), 2, 58)
        val r = ready(build("Psychic",p,a,d))
        assertEquals("Plus",r.request.attacker.ability)
        assertTrue(r.verdict.isCaveatedEstimate)
        assertTrue(r.verdict.ignoredMechanics.filterIsInstance<IgnoredCalcMechanic.Ability>().all { it.decision.abilityId != 57 })
        val controlPacket = p.copy(battlers = p.battlers.map { if(it.index==1) it.copy(ability=15) else it })
        assertEquals(damage(build("Psychic",controlPacket,a)),damage(r))
    }

    @Test fun `versioned native packet decoder preserves operands and rejects truncation`() {
        val p = packet()
        val raw = IntArray(HnsDoublesRuntimeState.TUPLE_LENGTH)
        raw[103] = 1; raw[104] = 1; raw[105] = 4; raw[106] = p.absentFlags
        p.battlers.forEach { b ->
            val values = listOf(b.index,b.position,b.partySlot,b.hp,b.species,b.ability,b.item,
                if(b.gastroAcid) 1 else 0,if(b.neutralizingGas) 1 else 0,if(b.transformed) 1 else 0,
                b.semiInvulnerable,b.ruinFlags,b.helpingHand)
            values.forEachIndexed { j,v -> raw[110 + b.index*13+j] = v }
        }
        assertEquals(p, HnsDoublesRuntimeState.decode(raw))
        assertNull(HnsDoublesRuntimeState.decode(raw.copyOf(161)))
        assertNull(HnsDoublesRuntimeState.decode(raw.copyOf().also { it[103] = 0 }))
        assertNull(HnsDoublesRuntimeState.decode(raw.copyOf().also { it[124] = 9 })) // invalid position
        assertNull(HnsDoublesRuntimeState.decode(raw.copyOf().also { it[117] = 2 })) // nonbinary Gastro Acid
    }

    @Test fun `redirection suppression and independent unsupported mechanics remain refused`() {
        refusal(build(p = changePartner(packet(), 3, 31)), CalcLimitation.HNS_DOUBLES_SELECTED_TARGET_UNRESOLVED)
        refusal(build(p = packet().copy(followMeTimers = listOf(0,1))), CalcLimitation.HNS_DOUBLES_SELECTED_TARGET_UNRESOLVED)
        refusal(build(p = changePartner(packet(), 2, 279)), CalcLimitation.HNS_DOUBLES_SELECTED_TARGET_UNRESOLVED)
        refusal(build(p = packet().copy(pledgeMove = true)), CalcLimitation.HNS_DOUBLES_SELECTED_TARGET_UNRESOLVED)
        val priorityPartner = changePartner(packet(), 3, 214)
        ready(build(p = priorityPartner))
        refusal(build("Quick Attack", priorityPartner), CalcLimitation.HNS_DOUBLES_SELECTED_TARGET_UNRESOLVED)
        ready(build("Quick Attack", changePartner(packet(), 3, 214, true)))
        refusal(build(p = packet().copy(moldBreakerActive = true)), CalcLimitation.HNS_DOUBLES_SUPPRESSION_UNRESOLVED)
        val gas = packet().copy(battlers = packet().battlers.map { if (it.index == 2) it.copy(ability = 256, neutralizingGas = true) else it })
        refusal(build(p = gas), CalcLimitation.HNS_DOUBLES_SUPPRESSION_UNRESOLVED)
        refusal(build("Facade"), CalcLimitation.HNS_MOVE_MECHANICS_NOT_MODELLED)
        // An old/short tuple never acquires neutral partner authority.
        assertNull(HnsDoublesRuntimeState.decode(IntArray(103)))
        assertNull(HnsDoublesRuntimeState.decode(IntArray(162).also { it[103] = 1; it[104] = 2 }))
    }
}
