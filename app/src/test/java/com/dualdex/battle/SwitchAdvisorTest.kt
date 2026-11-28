package com.dualdex.battle

import com.dualdex.calculator.CalcSupport
import com.dualdex.calculator.DamageCalculationResponse
import com.dualdex.pokemon.Gen3VanillaDataPack
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.romhack.ProfileMatchMethod
import com.dualdex.romhack.RomHackProfile
import com.dualdex.romhack.RuntimeRomTrust
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

class SwitchAdvisorTest {

    private val exactHash = "a".repeat(64)
    private val verifiedFireRed: RomHackProfile = RomHackProfile.DEFAULT_FIRERED.copy(
        sha256Hashes = listOf(exactHash),
        isVerified = true,
        memoryLayoutVerified = true
    )
    private val exactTrust: RuntimeRomTrust = RuntimeRomTrust(
        matchMethod = ProfileMatchMethod.EXACT_SHA256,
        detectedSha256 = exactHash,
        activeRomSha256 = exactHash,
        profileVerified = true,
        memoryLayoutVerified = true,
        profileSha256Hashes = listOf(exactHash)
    )

    private fun mon(
        species: Int,
        nickname: String,
        moves: IntArray = intArrayOf(33, 0, 0, 0), // Tackle
        pp: IntArray = intArrayOf(35, 0, 0, 0),
        currentHp: Int = 150,
        maxHp: Int = 150,
        attack: Int = 100,
        defense: Int = 100,
        spAttack: Int = 100,
        spDefense: Int = 100,
        speed: Int = 100,
        isEgg: Boolean = false,
        isValid: Boolean = true,
        isEmpty: Boolean = false,
        statusCondition: Long = 0L
    ): ParsedPokemon = ParsedPokemon(
        isValid = isValid,
        isEmpty = isEmpty,
        pid = species.toLong() * 1000L + currentHp,
        tid = 1000,
        sid = 2000,
        nickname = nickname,
        otName = "Red",
        species = species,
        heldItem = 0,
        level = 50,
        nature = 0,
        natureName = "Hardy",
        isShiny = false,
        abilitySlot = 0,
        isEgg = isEgg,
        friendship = 255,
        experience = 100000L,
        hpIv = 31, attackIv = 31, defenseIv = 31, speedIv = 31, spAttackIv = 31, spDefenseIv = 31,
        hpEv = 0, attackEv = 0, defenseEv = 0, speedEv = 0, spAttackEv = 0, spDefenseEv = 0,
        moves = moves,
        pp = pp,
        currentHp = currentHp,
        maxHp = maxHp,
        attack = attack,
        defense = defense,
        speed = speed,
        spAttack = spAttack,
        spDefense = spDefense,
        statusCondition = statusCondition
    )

    @Test
    fun `excludes active slot eggs fainted and invalid members`() {
        val active = mon(species = 25, nickname = "Pikachu", moves = intArrayOf(85, 0, 0, 0), pp = intArrayOf(15, 0, 0, 0))
        val egg = mon(species = 1, nickname = "Egg", isEgg = true)
        val fainted = mon(species = 9, nickname = "Blastoise", currentHp = 0, maxHp = 160, moves = intArrayOf(57, 0, 0, 0), pp = intArrayOf(15, 0, 0, 0))
        val healthy = mon(species = 134, nickname = "Vaporeon", moves = intArrayOf(57, 0, 0, 0), pp = intArrayOf(15, 0, 0, 0))
        val emptySlot = mon(species = 0, nickname = "", isEmpty = true, isValid = false)
        val opponent = mon(species = 6, nickname = "Charizard", moves = intArrayOf(53, 0, 0, 0), pp = intArrayOf(15, 0, 0, 0))

        val ranked = SwitchAdvisor.rankSwitches(
            playerParty = listOf(active, egg, fainted, healthy, emptySlot),
            activePlayerSlot = 0,
            opponent = opponent,
            profile = verifiedFireRed,
            dataPack = Gen3VanillaDataPack,
            runtimeTrust = exactTrust
        ) { attacker, defender, moveId ->
            when {
                attacker.species == 134 && moveId == 57 ->
                    SwitchDamageEstimate(140, 165, defender.maxHp, CalcSupport.VERIFIED)
                attacker.species == 6 && defender.species == 134 && moveId == 53 ->
                    SwitchDamageEstimate(30, 36, defender.maxHp, CalcSupport.VERIFIED)
                else -> null
            }
        }

        assertEquals(1, ranked.size)
        assertEquals(3, ranked.single().slot)
        assertEquals("Vaporeon", ranked.single().displayName)
    }

    @Test
    fun `empty bench returns empty list`() {
        val active = mon(species = 6, nickname = "Charizard", moves = intArrayOf(53, 0, 0, 0), pp = intArrayOf(15, 0, 0, 0))
        val fainted = mon(species = 9, nickname = "Blastoise", currentHp = 0, maxHp = 150)
        val egg = mon(species = 1, nickname = "Egg", isEgg = true)
        val opponent = mon(species = 3, nickname = "Venusaur", moves = intArrayOf(22, 0, 0, 0), pp = intArrayOf(10, 0, 0, 0))

        val ranked = SwitchAdvisor.rankSwitches(
            playerParty = listOf(active, fainted, egg),
            activePlayerSlot = 0,
            opponent = opponent,
            profile = verifiedFireRed,
            dataPack = Gen3VanillaDataPack,
            runtimeTrust = exactTrust
        ) { _, _, _ -> null }

        assertTrue(ranked.isEmpty())
    }

    @Test
    fun `ranks OHKO plus defensive resist above neutral matchup`() {
        val active = mon(species = 3, nickname = "Venusaur")
        // Blastoise: resists Fire (0.5x), OHKOs Charizard with Surf
        val blastoise = mon(
            species = 9,
            nickname = "Blastoise",
            moves = intArrayOf(57, 33, 0, 0), // Surf, Tackle
            pp = intArrayOf(15, 35, 0, 0),
            maxHp = 160,
            currentHp = 160
        )
        // Snorlax: neutral to Fire, neutral 3HKO back
        val snorlax = mon(
            species = 143,
            nickname = "Snorlax",
            moves = intArrayOf(33, 0, 0, 0), // Tackle
            pp = intArrayOf(35, 0, 0, 0),
            maxHp = 220,
            currentHp = 220
        )
        val opponent = mon(
            species = 6,
            nickname = "Charizard",
            moves = intArrayOf(53, 0, 0, 0), // Flamethrower
            pp = intArrayOf(15, 0, 0, 0),
            maxHp = 150,
            currentHp = 150
        )

        val ranked = SwitchAdvisor.rankSwitches(
            playerParty = listOf(active, snorlax, blastoise),
            activePlayerSlot = 0,
            opponent = opponent,
            profile = verifiedFireRed,
            dataPack = Gen3VanillaDataPack,
            runtimeTrust = exactTrust
        ) { attacker, defender, moveId ->
            when {
                attacker.species == 9 && moveId == 57 ->
                    SwitchDamageEstimate(160, 190, defender.maxHp, CalcSupport.VERIFIED, "guaranteed OHKO")
                attacker.species == 9 && moveId == 33 ->
                    SwitchDamageEstimate(20, 24, defender.maxHp, CalcSupport.VERIFIED)
                attacker.species == 143 && moveId == 33 ->
                    SwitchDamageEstimate(50, 60, defender.maxHp, CalcSupport.VERIFIED)
                attacker.species == 6 && defender.species == 9 && moveId == 53 ->
                    SwitchDamageEstimate(30, 36, defender.maxHp, CalcSupport.VERIFIED)
                attacker.species == 6 && defender.species == 143 && moveId == 53 ->
                    SwitchDamageEstimate(75, 90, defender.maxHp, CalcSupport.VERIFIED)
                else -> null
            }
        }

        assertEquals(2, ranked.size)
        assertEquals("Blastoise", ranked[0].displayName)
        assertEquals("Snorlax", ranked[1].displayName)
        assertEquals("Surf", ranked[0].bestMoveName)
        assertEquals(SwitchRecommendationSource.CALCULATOR, ranked[0].source)
        assertEquals(SwitchConfidenceTier.VERIFIED, ranked[0].confidenceTier)
        assertEquals("Calc", ranked[0].confidenceTier.badgeLabel)
        assertTrue("Expected OHKO reason tag, got: ${ranked[0].reasonTag}", ranked[0].reasonTag.contains("OHKO"))
        assertTrue("Expected Resists reason tag, got: ${ranked[0].reasonTag}", ranked[0].reasonTag.contains("Resists"))
        assertTrue(ranked[0].score > ranked[1].score)
    }

    @Test
    fun `ranks calc-backed high neutral STAB damage ahead of weak super-effective coverage`() {
        val active = mon(species = 25, nickname = "Pikachu")
        // Alakazam: Psychic (neutral 1x STAB against Snorlax) deals 85-95% via calc
        val alakazam = mon(
            species = 65,
            nickname = "Alakazam",
            moves = intArrayOf(94, 0, 0, 0), // Psychic
            pp = intArrayOf(10, 0, 0, 0),
            maxHp = 140,
            currentHp = 140,
            speed = 100
        )
        // Low-stat Fighting coverage mon (2x SE against Snorlax) deals only 30-35% via calc
        val weakCoverage = mon(
            species = 68,
            nickname = "Machamp",
            moves = intArrayOf(2, 0, 0, 0), // Karate Chop (Fighting, 2x vs Normal)
            pp = intArrayOf(25, 0, 0, 0),
            maxHp = 140,
            currentHp = 140,
            speed = 100
        )
        val opponent = mon(
            species = 143,
            nickname = "Snorlax",
            moves = intArrayOf(33, 0, 0, 0), // Tackle
            pp = intArrayOf(35, 0, 0, 0),
            maxHp = 200,
            currentHp = 200,
            speed = 100
        )

        val ranked = SwitchAdvisor.rankSwitches(
            playerParty = listOf(active, weakCoverage, alakazam),
            activePlayerSlot = 0,
            opponent = opponent,
            profile = verifiedFireRed,
            dataPack = Gen3VanillaDataPack,
            runtimeTrust = exactTrust
        ) { attacker, defender, moveId ->
            when {
                attacker.species == 65 && moveId == 94 ->
                    SwitchDamageEstimate(170, 190, defender.maxHp, CalcSupport.ESTIMATED)
                attacker.species == 68 && moveId == 2 ->
                    SwitchDamageEstimate(60, 70, defender.maxHp, CalcSupport.ESTIMATED)
                attacker.species == 143 && moveId == 33 ->
                    // Equal incoming damage so offensive calc decides
                    SwitchDamageEstimate(42, 50, defender.maxHp, CalcSupport.ESTIMATED)
                else -> null
            }
        }

        assertEquals(2, ranked.size)
        assertEquals("Alakazam", ranked[0].displayName)
        assertEquals("Machamp", ranked[1].displayName)
        assertEquals(SwitchRecommendationSource.CALCULATOR, ranked[0].source)
        assertEquals(SwitchConfidenceTier.ESTIMATED, ranked[0].confidenceTier)
        assertEquals("Est.", ranked[0].confidenceTier.badgeLabel)
        assertTrue(ranked[0].offenseMaxPercent!! > 90.0)
        assertTrue(ranked[1].offenseMaxPercent!! < 40.0)
    }

    @Test
    fun `completely refused matchup produces no ranked switch`() {
        val active = mon(species = 3, nickname = "Venusaur")
        val blastoise = mon(
            species = 9,
            nickname = "Blastoise",
            moves = intArrayOf(57, 0, 0, 0),
            pp = intArrayOf(15, 0, 0, 0)
        )
        val venusaurBench = mon(
            species = 3,
            nickname = "BulbaBench",
            moves = intArrayOf(33, 0, 0, 0),
            pp = intArrayOf(35, 0, 0, 0)
        )
        val opponent = mon(
            species = 6,
            nickname = "Charizard",
            moves = intArrayOf(53, 0, 0, 0),
            pp = intArrayOf(15, 0, 0, 0)
        )

        // Return UNSUPPORTED for one and null for others to test both refusal forms.
        val ranked = SwitchAdvisor.rankSwitches(
            playerParty = listOf(active, venusaurBench, blastoise),
            activePlayerSlot = 0,
            opponent = opponent,
            profile = verifiedFireRed,
            dataPack = Gen3VanillaDataPack,
            runtimeTrust = exactTrust
        ) { attacker, defender, _ ->
            if (attacker.species == 9) {
                SwitchDamageEstimate(999, 999, defender.maxHp, CalcSupport.UNSUPPORTED)
            } else {
                null
            }
        }

        assertTrue("Completely refused calculations must not produce ranked switches", ranked.isEmpty())
    }

    @Test
    fun `unknown species or typing is refused rather than defaulting to neutral effectiveness`() {
        val active = mon(species = 25, nickname = "Pikachu")
        val unknownBench = mon(
            species = 99999,
            nickname = "MissingNo",
            moves = intArrayOf(57, 0, 0, 0),
            pp = intArrayOf(15, 0, 0, 0)
        )
        val knownBench = mon(
            species = 9,
            nickname = "Blastoise",
            moves = intArrayOf(57, 0, 0, 0),
            pp = intArrayOf(15, 0, 0, 0)
        )
        val knownOpponent = mon(
            species = 6,
            nickname = "Charizard",
            moves = intArrayOf(53, 0, 0, 0),
            pp = intArrayOf(15, 0, 0, 0)
        )
        val unknownOpponent = mon(
            species = 99999,
            nickname = "UnknownFoe",
            moves = intArrayOf(53, 0, 0, 0),
            pp = intArrayOf(15, 0, 0, 0)
        )

        // Unknown bench candidate is omitted even if evaluator would return a number; known bench ranks.
        val rankedAgainstKnown = SwitchAdvisor.rankSwitches(
            playerParty = listOf(active, unknownBench, knownBench),
            activePlayerSlot = 0,
            opponent = knownOpponent,
            profile = verifiedFireRed,
            dataPack = Gen3VanillaDataPack,
            runtimeTrust = exactTrust
        ) { _, defender, _ ->
            SwitchDamageEstimate(80, 96, defender.maxHp, CalcSupport.VERIFIED)
        }
        assertEquals(1, rankedAgainstKnown.size)
        assertEquals("Blastoise", rankedAgainstKnown.single().displayName)

        // Unknown opponent species refuses the entire switch ranking.
        val rankedAgainstUnknown = SwitchAdvisor.rankSwitches(
            playerParty = listOf(active, knownBench),
            activePlayerSlot = 0,
            opponent = unknownOpponent,
            profile = verifiedFireRed,
            dataPack = Gen3VanillaDataPack,
            runtimeTrust = exactTrust
        ) { _, defender, _ ->
            SwitchDamageEstimate(80, 96, defender.maxHp, CalcSupport.VERIFIED)
        }
        assertTrue("Unknown opponent typing must refuse switch ranking", rankedAgainstUnknown.isEmpty())
    }

    @Test
    fun `partially refused enemy moveset withholds Safe Pivot and worst threat claims`() {
        val active = mon(species = 25, nickname = "Pikachu")
        val blastoise = mon(
            species = 9,
            nickname = "Blastoise",
            moves = intArrayOf(57, 0, 0, 0), // Surf
            pp = intArrayOf(15, 0, 0, 0),
            currentHp = 160,
            maxHp = 160
        )
        // Opponent has weak Tackle (33) AND Earthquake (89) whose calc is refused/unmodelled.
        val opponent = mon(
            species = 143,
            nickname = "Snorlax",
            moves = intArrayOf(33, 89, 0, 0),
            pp = intArrayOf(35, 10, 0, 0),
            currentHp = 200,
            maxHp = 200
        )

        val ranked = SwitchAdvisor.rankSwitches(
            playerParty = listOf(active, blastoise),
            activePlayerSlot = 0,
            opponent = opponent,
            profile = verifiedFireRed,
            dataPack = Gen3VanillaDataPack,
            runtimeTrust = exactTrust
        ) { attacker, defender, moveId ->
            when {
                attacker.species == 9 && moveId == 57 ->
                    SwitchDamageEstimate(70, 82, defender.maxHp, CalcSupport.VERIFIED)
                attacker.species == 143 && moveId == 33 ->
                    // 10-12% damage would normally earn "Safe Pivot" if Earthquake were ignored!
                    SwitchDamageEstimate(16, 19, defender.maxHp, CalcSupport.VERIFIED)
                attacker.species == 143 && moveId == 89 ->
                    null // Refused / unmodelled enemy move
                else -> null
            }
        }

        assertEquals(1, ranked.size)
        val rec = ranked.single()
        assertTrue(rec.hasUnmodelledEnemyThreats)
        assertFalse("Must not claim Safe Pivot when an enemy move is unmodelled: ${rec.reasonTag}", rec.reasonTag.contains("Safe Pivot"))
        assertFalse("Must not claim Resists when an enemy move is unmodelled: ${rec.reasonTag}", rec.reasonTag.contains("Resists"))
        assertFalse("Must not claim Immune when an enemy move is unmodelled: ${rec.reasonTag}", rec.reasonTag.contains("Immune"))
        assertNull("Must withhold worstThreatMoveName when enemy moveset is partial", rec.worstThreatMoveName)
        assertNull("Must withhold threatMinPercent when enemy moveset is partial", rec.threatMinPercent)
        assertNull("Must withhold threatMaxPercent when enemy moveset is partial", rec.threatMaxPercent)
        assertNull("Must withhold worstDefensiveMultiplier when enemy moveset is partial", rec.worstDefensiveMultiplier)
        assertEquals("Threat unknown", rec.defenseBadge)
        assertNotEquals(SwitchDefenseTone.STRONG, rec.defenseTone)
        assertEquals(SwitchConfidenceTier.ESTIMATED, rec.confidenceTier)

        // Also verify an unmapped enemy move ID withholds Safe Pivot / worst threat.
        val opponentWithUnmappedMove = opponent.copy(moves = intArrayOf(33, 99999, 0, 0))
        val rankedUnmapped = SwitchAdvisor.rankSwitches(
            playerParty = listOf(active, blastoise),
            activePlayerSlot = 0,
            opponent = opponentWithUnmappedMove,
            profile = verifiedFireRed,
            dataPack = Gen3VanillaDataPack,
            runtimeTrust = exactTrust
        ) { attacker, defender, moveId ->
            when {
                attacker.species == 9 && moveId == 57 ->
                    SwitchDamageEstimate(70, 82, defender.maxHp, CalcSupport.VERIFIED)
                attacker.species == 143 && moveId == 33 ->
                    SwitchDamageEstimate(16, 19, defender.maxHp, CalcSupport.VERIFIED)
                else -> null
            }
        }
        val recUnmapped = rankedUnmapped.single()
        assertTrue(recUnmapped.hasUnmodelledEnemyThreats)
        assertFalse(recUnmapped.reasonTag.contains("Safe Pivot"))
        assertNull(recUnmapped.worstThreatMoveName)
        assertNull(recUnmapped.threatMaxPercent)
        assertEquals("Threat unknown", recUnmapped.defenseBadge)
    }

    @Test
    fun `skips zero-PP moves and status moves when choosing best move`() {
        val active = mon(species = 25, nickname = "Pikachu")
        val blastoise = mon(
            species = 9,
            nickname = "Blastoise",
            moves = intArrayOf(57, 14, 33, 0), // Surf (0 PP!), Swords Dance (Status), Tackle (10 PP)
            pp = intArrayOf(0, 30, 10, 0)
        )
        val opponent = mon(species = 6, nickname = "Charizard", moves = intArrayOf(0, 0, 0, 0), pp = intArrayOf(0, 0, 0, 0))

        val ranked = SwitchAdvisor.rankSwitches(
            playerParty = listOf(active, blastoise),
            activePlayerSlot = 0,
            opponent = opponent,
            profile = verifiedFireRed,
            dataPack = Gen3VanillaDataPack,
            runtimeTrust = exactTrust
        ) { attacker, defender, moveId ->
            if (attacker.species == 9 && moveId == 33) {
                SwitchDamageEstimate(40, 48, defender.maxHp, CalcSupport.VERIFIED)
            } else {
                // Should never even be queried for Surf (0 PP) or Swords Dance (Status)
                throw AssertionError("Unexpected moveId evaluated: $moveId")
            }
        }

        assertEquals(1, ranked.size)
        assertEquals("Tackle", ranked[0].bestMoveName)
        assertEquals(33, ranked[0].bestMoveId)
    }

    @Test
    fun `tie breaks deterministically by party slot index`() {
        val active = mon(species = 25, nickname = "Pikachu")
        val twin1 = mon(species = 143, nickname = "SnorlaxA", moves = intArrayOf(33, 0, 0, 0), pp = intArrayOf(35, 0, 0, 0))
        val twin2 = mon(species = 143, nickname = "SnorlaxB", moves = intArrayOf(33, 0, 0, 0), pp = intArrayOf(35, 0, 0, 0))
        val opponent = mon(species = 6, nickname = "Charizard", moves = intArrayOf(53, 0, 0, 0), pp = intArrayOf(15, 0, 0, 0))

        val ranked = SwitchAdvisor.rankSwitches(
            playerParty = listOf(active, twin1, twin2),
            activePlayerSlot = 0,
            opponent = opponent,
            profile = verifiedFireRed,
            dataPack = Gen3VanillaDataPack,
            runtimeTrust = exactTrust
        ) { _, defender, _ ->
            SwitchDamageEstimate(50, 60, defender.maxHp, CalcSupport.VERIFIED)
        }

        assertEquals(2, ranked.size)
        assertEquals(1, ranked[0].slot)
        assertEquals("SnorlaxA", ranked[0].displayName)
        assertEquals(2, ranked[1].slot)
        assertEquals("SnorlaxB", ranked[1].displayName)
        assertEquals(ranked[0].score, ranked[1].score, 0.0001)
    }

    @Test
    fun `presenter deduplicates identical 10 Hz polls and invalidates on state change`() = runBlocking {
        val evaluations = AtomicInteger(0)
        val presenter = SwitchAdvisorPresenter(
            dispatcher = Dispatchers.Unconfined,
            evaluatorOverride = {
                evaluations.incrementAndGet()
                val evaluator: SwitchDamageEvaluator = { _, defender, _ ->
                    SwitchDamageEstimate(60, 72, defender.maxHp, CalcSupport.VERIFIED)
                }
                evaluator
            }
        )

        val active = mon(species = 25, nickname = "Pikachu")
        val bench = mon(species = 9, nickname = "Blastoise", moves = intArrayOf(57, 0, 0, 0), pp = intArrayOf(15, 0, 0, 0))
        val opponent = mon(species = 6, nickname = "Charizard", moves = intArrayOf(53, 0, 0, 0), pp = intArrayOf(15, 0, 0, 0))

        val input1 = SwitchAdvisorInput(
            playerParty = listOf(active, bench),
            activePlayerSlot = 0,
            opponent = opponent,
            activeEnemySlot = 0,
            profile = verifiedFireRed,
            dataPack = Gen3VanillaDataPack,
            runtimeTrust = exactTrust
        )

        var delivered: List<SwitchRecommendation> = emptyList()
        val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)
        presenter.requestUpdate(scope, input1) { delivered = it }
        assertEquals(1, evaluations.get())
        assertEquals(1, delivered.size)

        // Repeat identical 10 Hz poll 5 times: must hit cache and NOT re-invoke evaluator!
        repeat(5) {
            val cached = presenter.requestUpdate(scope, input1.copy(playerParty = listOf(active, bench))) {
                throw AssertionError("Cache hit should not relaunch evaluation")
            }
            assertEquals(1, cached.size)
        }
        assertEquals(1, evaluations.get())

        // Opponent takes damage -> cache key changes -> exactly one recomputation.
        val damagedOpponent = opponent.copy(currentHp = 80)
        presenter.requestUpdate(scope, input1.copy(opponent = damagedOpponent)) { delivered = it }
        assertEquals(2, evaluations.get())
        assertNotNull(delivered.firstOrNull())
    }

    @Test
    fun `presenter fails closed through CalcRequestBoundary on unsupported ROM without calling calculator`() {
        val calcCalls = AtomicInteger(0)
        val stubCalc = BattleDamageCalculator {
            calcCalls.incrementAndGet()
            DamageCalculationResponse(success = true, minDamage = 100, maxDamage = 120, defenderMaxHP = 150)
        }
        val presenter = SwitchAdvisorPresenter(calculator = stubCalc)

        val unsupportedHack = RomHackProfile(
            id = "radical_red",
            name = "Pokemon Radical Red",
            baseGame = "FireRed",
            gameId = 7,
            engine = "CFRU",
            hasPhysSpecSplit = true,
            steelResistsGhostDark = false,
            isVerified = true
        )
        val active = mon(species = 25, nickname = "Pikachu")
        val blastoise = mon(species = 9, nickname = "Blastoise", moves = intArrayOf(57, 0, 0, 0), pp = intArrayOf(15, 0, 0, 0))
        val opponent = mon(species = 6, nickname = "Charizard", moves = intArrayOf(53, 0, 0, 0), pp = intArrayOf(15, 0, 0, 0))

        val ranked = presenter.evaluateSync(
            SwitchAdvisorInput(
                playerParty = listOf(active, blastoise),
                activePlayerSlot = 0,
                opponent = opponent,
                activeEnemySlot = 0,
                profile = unsupportedHack,
                dataPack = Gen3VanillaDataPack,
                runtimeTrust = exactTrust
            )
        )

        assertEquals("Unsupported ROM must never invoke calculator engine", 0, calcCalls.get())
        assertTrue("Unsupported ROM with refused calculations must produce no ranked switch", ranked.isEmpty())
    }

    private class ManualQueueDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queue.addLast(block)
        }

        fun runAll() {
            while (queue.isNotEmpty()) {
                queue.removeFirst().run()
            }
        }
    }

    @Test
    fun `presenter returning to cached key A while slow B is in flight prevents B from overwriting A`() {
        val mainDispatcher = ManualQueueDispatcher()
        val bgDispatcher = ManualQueueDispatcher()
        val presenter = SwitchAdvisorPresenter(
            dispatcher = bgDispatcher,
            evaluatorOverride = { input ->
                val opp = input.opponent
                val evaluator: SwitchDamageEvaluator = { attacker, defender, _ ->
                    // Score Blastoise (9) highest against Charizard (6), Snorlax (143) highest against Alakazam (65)
                    when {
                        opp?.species == 6 && attacker.species == 9 ->
                            SwitchDamageEstimate(160, 180, defender.maxHp, CalcSupport.VERIFIED)
                        opp?.species == 6 && attacker.species == 143 ->
                            SwitchDamageEstimate(30, 36, defender.maxHp, CalcSupport.VERIFIED)
                        opp?.species == 65 && attacker.species == 143 ->
                            SwitchDamageEstimate(160, 180, defender.maxHp, CalcSupport.VERIFIED)
                        opp?.species == 65 && attacker.species == 9 ->
                            SwitchDamageEstimate(30, 36, defender.maxHp, CalcSupport.VERIFIED)
                        else ->
                            SwitchDamageEstimate(40, 48, defender.maxHp, CalcSupport.VERIFIED)
                    }
                }
                evaluator
            }
        )

        val active = mon(species = 25, nickname = "Pikachu")
        val blastoise = mon(species = 9, nickname = "Blastoise", moves = intArrayOf(57, 0, 0, 0), pp = intArrayOf(15, 0, 0, 0))
        val snorlax = mon(species = 143, nickname = "Snorlax", moves = intArrayOf(33, 0, 0, 0), pp = intArrayOf(35, 0, 0, 0))
        val oppA = mon(species = 6, nickname = "Charizard", moves = intArrayOf(53, 0, 0, 0), pp = intArrayOf(15, 0, 0, 0))
        val oppB = mon(species = 65, nickname = "Alakazam", moves = intArrayOf(94, 0, 0, 0), pp = intArrayOf(10, 0, 0, 0))

        val inputA = SwitchAdvisorInput(
            playerParty = listOf(active, blastoise, snorlax),
            activePlayerSlot = 0,
            opponent = oppA,
            activeEnemySlot = 0,
            profile = verifiedFireRed,
            dataPack = Gen3VanillaDataPack,
            runtimeTrust = exactTrust
        )
        val inputB = inputA.copy(opponent = oppB)

        val scope = CoroutineScope(mainDispatcher + SupervisorJob())
        val deliveries = mutableListOf<List<SwitchRecommendation>>()

        // 1. Key A runs and completes -> Blastoise is #1.
        presenter.requestUpdate(scope, inputA) { deliveries += it }
        mainDispatcher.runAll()
        bgDispatcher.runAll()
        mainDispatcher.runAll()
        assertEquals(1, deliveries.size)
        assertEquals("Blastoise", deliveries.last().first().displayName)
        assertEquals("Blastoise", presenter.cachedRecommendations.first().displayName)

        // 2. Key B starts and finishes background evaluation, waiting to resume on mainDispatcher.
        val whileBRunning = presenter.requestUpdate(scope, inputB) { deliveries += it }
        assertTrue("Matchup change should clear stale displayed rows while B runs", whileBRunning.isEmpty())
        mainDispatcher.runAll()
        bgDispatcher.runAll()

        // 3. Before B's result posts on mainDispatcher, caller returns to key A (cache hit).
        val returnedToA = presenter.requestUpdate(scope, inputA) { deliveries += it }
        assertEquals(2, returnedToA.size)
        assertEquals("Blastoise", returnedToA.first().displayName)

        // 4. Now drain mainDispatcher so obsolete B tries to finish: B must NOT overwrite A!
        mainDispatcher.runAll()
        bgDispatcher.runAll()
        mainDispatcher.runAll()

        assertEquals("Obsolete B must not publish after returning to A", 1, deliveries.size)
        assertEquals("Blastoise", presenter.cachedRecommendations.first().displayName)
    }

    @Test
    fun `presenter immediately invalidates displayed rows when a bench candidate faints`() {
        val mainDispatcher = ManualQueueDispatcher()
        val bgDispatcher = ManualQueueDispatcher()
        val presenter = SwitchAdvisorPresenter(
            dispatcher = bgDispatcher,
            evaluatorOverride = {
                val evaluator: SwitchDamageEvaluator = { _, defender, _ ->
                    SwitchDamageEstimate(75, 90, defender.maxHp, CalcSupport.VERIFIED)
                }
                evaluator
            }
        )

        val active = mon(species = 25, nickname = "Pikachu")
        val blastoise = mon(species = 9, nickname = "Blastoise", moves = intArrayOf(57, 0, 0, 0), pp = intArrayOf(15, 0, 0, 0))
        val snorlax = mon(species = 143, nickname = "Snorlax", moves = intArrayOf(33, 0, 0, 0), pp = intArrayOf(35, 0, 0, 0))
        val opponent = mon(species = 6, nickname = "Charizard", moves = intArrayOf(53, 0, 0, 0), pp = intArrayOf(15, 0, 0, 0))

        val initialInput = SwitchAdvisorInput(
            playerParty = listOf(active, blastoise, snorlax),
            activePlayerSlot = 0,
            opponent = opponent,
            activeEnemySlot = 0,
            profile = verifiedFireRed,
            dataPack = Gen3VanillaDataPack,
            runtimeTrust = exactTrust
        )

        val scope = CoroutineScope(mainDispatcher + SupervisorJob())
        var delivered: List<SwitchRecommendation> = emptyList()

        presenter.requestUpdate(scope, initialInput) { delivered = it }
        mainDispatcher.runAll()
        bgDispatcher.runAll()
        mainDispatcher.runAll()
        assertEquals(2, delivered.size)
        assertTrue(delivered.any { it.displayName == "Blastoise" })

        // Blastoise faints -> stale rows containing Blastoise must clear immediately while recalculating.
        val faintedBlastoise = blastoise.copy(currentHp = 0)
        val afterFaintInput = initialInput.copy(playerParty = listOf(active, faintedBlastoise, snorlax))
        val immediate = presenter.requestUpdate(scope, afterFaintInput) { delivered = it }
        assertTrue("Fainted candidate must not remain displayed while recalculating", immediate.isEmpty())
        assertTrue(presenter.cachedRecommendations.isEmpty())

        // Once background ranking finishes, only healthy Snorlax is delivered.
        mainDispatcher.runAll()
        bgDispatcher.runAll()
        mainDispatcher.runAll()
        assertEquals(1, delivered.size)
        assertEquals("Snorlax", delivered.single().displayName)
    }
}
