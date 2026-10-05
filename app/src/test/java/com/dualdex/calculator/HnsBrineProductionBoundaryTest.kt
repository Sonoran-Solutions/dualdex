package com.dualdex.calculator

import com.dualdex.calculator.census.HnsCalcCensusBaseline as Baseline
import com.dualdex.pokemon.hns.*
import com.dualdex.battle.DamageBlockerPresentation
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Real boundary -> production JSON -> shipped bundle; assertions use measured pinned rolls. */
class HnsBrineProductionBoundaryTest {
    private val root get() = generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
        .first { File(it, "ci.sh").isFile }
    private fun observation(attacker: Boolean, ability: String = "Insomnia", item: String? = null): BattlerRuntimeObservation {
        val abilityId = checkNotNull(HnsAbilityRegistry.classify(ability).abilityId)
        val itemId = item?.let(HnsItemRegistry::resolveIdByName) ?: 0
        return Baseline.observation(if (attacker) Baseline.Participant.ATTACKER else Baseline.Participant.DEFENDER,
            if (attacker) 0 else 1, if (attacker) 68 else 9, if (attacker) "Machamp" else "Blastoise",
            if (attacker) listOf("Fighting") else listOf("Water"), abilityId, itemId, ability,
            Hns205ItemCatalogue.get(itemId), 2).let { o -> o.copy(state = o.state.copy(
                rawAttack = 151, rawSpAttack = 151, rawDefense = 109, rawSpDefense = 109,
                healBlockObserved = true, volatileHealBlock = false,
                rawSpeed = if (attacker) 40 else 100, hp = if(attacker) 200 else 60000, maxHp = if(attacker) 200 else 60000)) }
    }
    private fun request(move: String = "Brine", crit: Boolean = false) = DamageCalculationRequest(
        attacker = CalcPokemonInput(species="Machamp", level=50, origin=CalcInputOrigin.LIVE_READ, partySlot=0),
        defender = CalcPokemonInput(species="Blastoise", level=50, origin=CalcInputOrigin.LIVE_READ, partySlot=1),
        move=CalcMoveInput(move, isCrit=crit), field=CalcFieldInput(gameType="Singles"))
    private fun build(r: DamageCalculationRequest = request(), a: BattlerRuntimeObservation = observation(true),
                      d: BattlerRuntimeObservation = observation(false)) = CalcRequestBoundary.build(
        Baseline.profile, Baseline.trust, r, Baseline.challengeSettings, a, d, activeBattle=true)
    private fun ready(o: CalcRequestOutcome) = (o as? CalcRequestOutcome.Ready) ?: error("Expected Ready: $o")
    private fun damage(o: CalcRequestOutcome): List<Int> {
        val r = ready(o)
        assertTrue(r.verdict.blockingLimitations.isEmpty())
        assertTrue(r.verdict.ignoredMechanics.isEmpty())
        assertTrue(DamageBlockerPresentation.from(r.verdict, observedDoubles=false).isEmpty())
        return runJson(checkNotNull(buildCalcRequestJson(r.request)))
    }
    private fun runResponse(json: String): JSONObject {
        val process = ProcessBuilder("node", File(root,"tools/calc-bundler/run_production_request.js").path,
            File(root,"app/src/main/assets/calc_bundle.js").path).redirectErrorStream(true).start()
        process.outputStream.bufferedWriter().use { it.write(json) }
        val text = process.inputStream.bufferedReader().readText()
        assertEquals(text,0,process.waitFor())
        return JSONObject(text)
    }
    private fun runJson(json: String): List<Int> {
        val response = runResponse(json)
        assertTrue(response.toString(), response.getBoolean("success"))
        val rolls=response.getJSONArray("damage")
        assertEquals(16,rolls.length())
        return (0..15).map(rolls::getInt)
    }
    private fun measured(id: String): List<Int> {
        val entries=JSONObject(File(root,"tools/hns-damage-oracle/corpus.json").readText()).getJSONArray("entries")
        val e=(0 until entries.length()).map(entries::getJSONObject).first { it.getJSONObject("scenario").getString("id")==id }
        val rolls=e.getJSONArray("rolls")
        return (0..15).map(rolls::getInt)
    }

    private fun hp(hp: Int=50, max: Int=100, ability: String="Insomnia", item: String?=null) = observation(false,ability,item).let {
        it.copy(state=it.state.copy(hp=hp,maxHp=max))
    }
    private fun refusal(d: BattlerRuntimeObservation, limitation: CalcLimitation) {
        val o=build(d=d) as CalcRequestOutcome.Refused
        assertTrue(o.toString(), limitation in o.verdict.blockingLimitations)
    }
    @Test fun `floor half threshold and shared HP match pinned engine`() {
        assertEquals(setOf(362),Hns205MoveEffects.fixedSingleHitBrineMoveIds)
        assertEquals(HnsMoveMechanicsCategory.FIXED_SINGLE_HIT_BRINE,HnsMoveMechanicsRegistry.classify(362).category)
        assertFalse(362 in Hns205MoveEffects.ordinaryMoveIds)
        for((name,h,m) in listOf(Triple("full",100,100),Triple("above",51,100),Triple("half",50,100),
                Triple("below",49,100),Triple("odd-above",51,101),Triple("odd-half",50,101))) {
            val out=build(d=hp(h,m))
            assertEquals(name,measured("brine-$name-player"),damage(out))
            val json=JSONObject(buildCalcRequestJson(ready(out).request)).getJSONObject("move")
            assertEquals("FIXED_SINGLE_HIT_BRINE",json.getString("hnsMoveFamily"))
            assertEquals(65,json.getJSONObject("overrides").getInt("basePower"))
            for(ability in listOf("Technician","Sheer Force"))
                assertEquals(damage(out),damage(build(a=observation(true,ability),d=hp(h,m))))
        }
        assertEquals(damage(build(d=hp(51))),damage(build(d=hp(2,3))))
        assertEquals(damage(build(d=hp())),damage(build(d=hp(1,3))))
        for(ability in listOf("Multiscale","Shadow Shield")) for(full in listOf(true,false)) {
            val name="brine-${if(full) "full" else "half"}-${ability.lowercase().replace(' ','-')}"
            assertEquals(measured(name),damage(build(d=hp(if(full) 60000 else 30000,60000,ability))))
        }
    }
    @Test fun `existing water pipeline composes with Brine`() {
        for(full in listOf(true,false)) for(name in listOf("rain","sun","rain-umbrella","water-bubble","mystic-water",
                "splash-plate","screen","life-orb","crit","normalize","technician","sheer-force","stages")) {
            val ability=when(name) { "water-bubble" -> "Water Bubble"; "normalize" -> "Normalize"; "technician" -> "Technician"; "sheer-force" -> "Sheer Force"; else -> "Insomnia" }
            val item=when(name) { "rain-umbrella" -> "Utility Umbrella"; "mystic-water" -> "Mystic Water"; "splash-plate" -> "Splash Plate"; "life-orb" -> "Life Orb"; else -> null }
            val weather=when(name) { "rain","rain-umbrella" -> 1; "sun" -> 8; else -> 0 }
            var a=observation(true,ability,item).let { it.copy(state=it.state.copy(battleWeather=weather)) }
            var d=hp(if(full) 60000 else 30000,60000).let { it.copy(state=it.state.copy(battleWeather=weather,sideStatuses=if(name=="screen") 2 else 0)) }
            if(name=="stages") {
                a=a.copy(state=a.state.copy(statStages=a.state.statStages.toMutableList().also { it[4]=1 }))
                d=d.copy(state=d.state.copy(statStages=d.state.statStages.toMutableList().also { it[5]=-1 }))
            }
            assertEquals(name,measured("brine-${if(full) "full" else "half"}-$name"),damage(build(request(crit=name=="crit"),a,d)))
        }
        for(ability in listOf("Water Absorb","Dry Skin","Storm Drain"))
            assertEquals(List(16) { 0 },damage(build(d=hp(30000,60000,ability))))
        assertTrue(build(a=observation(true,"Mold Breaker"),d=hp(30000,60000,"Water Absorb")) is CalcRequestOutcome.Refused)
        assertEquals(List(16) { 0 },damage(build(a=observation(true,"Mold Breaker"),d=hp(30000,60000,"Water Absorb","Ability Shield"))))
    }
    @Test fun `HP authority and invalid state fail closed`() {
        for((h,m) in listOf(50 to 0,50 to -1,50 to 65536,-1 to 100,0 to 100,101 to 100))
            refusal(hp(h,m),CalcLimitation.HNS_DEFENDER_HP_UNKNOWN)
        refusal(hp().let { it.copy(state=it.state.copy(hpObserved=false)) },CalcLimitation.HNS_DEFENDER_HP_UNKNOWN)
        refusal(hp().let { it.copy(state=it.state.copy(partySlot=0)) },CalcLimitation.HNS_DEFENDER_HP_UNKNOWN)
        assertTrue(CalcRequestBoundary.build(Baseline.profile,Baseline.trust.copy(activeRomSha256="unverified"),
            request(),Baseline.challengeSettings,observation(true),hp(),activeBattle=true) is CalcRequestOutcome.Refused)
        val bound=ready(build(d=hp(51))).request
        val forged=bound.copy(hnsLiveBattleState=bound.hnsLiveBattleState!!.copy(defenderHp=1,defenderMaxHp=100),
            moveOverride=CalcMoveOverride(999,"Fire","Physical"),defender=bound.defender.copy(curHP=1))
        assertEquals(damage(build(d=hp(51))),damage(build(forged,d=hp(51))))
        refusal(hp().let { it.copy(state=it.state.copy(volatileSubstitute=true)) },CalcLimitation.HNS_SUBSTITUTE_ACTIVE_NOT_MODELLED)
        for(semi in 1..6) refusal(hp().let { it.copy(state=it.state.copy(volatileSemiInvulnerable=semi)) },CalcLimitation.HNS_SEMI_INVULNERABLE_EXECUTION_NOT_MODELLED)
        for(flag in listOf("gastro","gas")) {
            val d=hp(50,100,"Water Absorb").let { it.copy(state=if(flag=="gastro") it.state.copy(volatileGastroAcid=true) else it.state.copy(volatileNeutralizingGas=true)) }
            assertTrue(flag,build(d=d) is CalcRequestOutcome.Refused)
        }
        assertTrue(build(d=hp(50,100,"Sturdy")) is CalcRequestOutcome.Ready)
        assertTrue(ready(build(d=hp(100,100,"Sturdy"))).verdict.ignoredMechanics.isNotEmpty())
        assertTrue(build(d=hp(50,100,item="Focus Sash")) is CalcRequestOutcome.Ready)
        assertTrue(ready(build(d=hp(100,100,item="Focus Sash"))).verdict.ignoredMechanics.isNotEmpty())
        assertTrue(ready(build(d=hp(50,100,item="Focus Band"))).verdict.ignoredMechanics.isNotEmpty())
        assertTrue(build(request().copy(field=CalcFieldInput(gameType="Doubles"))) is CalcRequestOutcome.Refused)
        for(move in listOf("Water Spout","Eruption","Crush Grip","Wring Out","Flail","Reversal","Hard Press","Super Fang","Endeavor","Gyro Ball","Double Slap"))
            assertTrue(move,build(request(move)) is CalcRequestOutcome.Refused)
    }
    @Test fun `Brine modifier stays before all later base power factors`() {
        val source=File(root,"tools/calc-bundler/entry.js").readText()
        val begin=source.indexOf("const basePowerModifier =")
        val brine=source.indexOf("basePowerModifier.addHalfUp(8192)",begin)
        assertTrue(brine>begin)
        for(later in listOf("doubles?.helpingHand", "const earlyGemType", "case 'Technician'", "case 'Water Bubble'", "const bp = basePowerModifier.apply(moveBasePower)"))
            assertTrue(later,source.indexOf(later,begin)>brine)
        assertEquals(damage(build(d=hp())),damage(build(a=observation(true,"Tough Claws"),d=hp())))
        assertEquals(damage(build(d=hp())),damage(build(d=hp(50,100,"Fluffy"))))
    }
    @Test fun `QuickJS requires exact family ID effect source power and live HP`() {
        val original=JSONObject(buildCalcRequestJson(ready(build(d=hp())).request))
        for(field in listOf("family","missing-family","id","effect","power","hp","max","zero","negative","too-high","format","substitute","semi","contact","sheer","flags","ability-flags")) {
            val fake=JSONObject(original.toString())
            val move=fake.getJSONObject("move");val d=fake.getJSONObject("defender")
            when(field) {
                "family" -> move.put("hnsMoveFamily","ORDINARY_PROVEN_EQUIVALENT")
                "missing-family" -> move.remove("hnsMoveFamily")
                "id" -> move.put("hnsMoveId",33)
                "effect" -> move.put("hnsMoveEffect","EFFECT_HIT")
                "power" -> move.getJSONObject("overrides").put("basePower",130)
                "hp" -> d.remove("hpAtHit")
                "max" -> d.remove("maxHpAtHit")
                "zero" -> d.put("hpAtHit",0)
                "negative" -> d.put("hpAtHit",-1)
                "too-high" -> d.put("hpAtHit",101)
                "format" -> fake.getJSONObject("field").put("gameType","Doubles")
                "substitute" -> d.put("hnsSubstitute",true)
                "semi" -> d.put("hnsSemiInvulnerableState",2)
                "contact" -> move.put("hnsMakesContact",true)
                "sheer" -> move.put("hnsSheerForceAffected",true)
                "flags" -> move.getJSONArray("hnsMoveFlags").put("ignoresTargetAbility")
                "ability-flags" -> move.getJSONArray("hnsMoveAbilityFlags").put("punchingMove")
            }
            assertFalse(field,runResponse(fake.toString()).getBoolean("success"))
        }
    }
}
