package com.dualdex.issue153

import com.dualdex.battle.FoeTeamTracker
import com.dualdex.companion.BattleAutoOpenReturn
import com.dualdex.companion.CompanionTab
import com.dualdex.companion.ui.usesExpansionItems
import com.dualdex.emulator.GameStatusBarModel
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.romhack.ProfileLoader
import com.dualdex.romhack.ProfileMatchMethod
import com.dualdex.romhack.RomCompatibilityStatus
import com.dualdex.romhack.RomHackDetector
import com.dualdex.romhack.RomHackProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Issue153QuickWinsTest {

    // --- money / clock / battery / layout ------------------------------------------------------

    @Test
    fun moneyDecodesXorAndDropsImplausibleValues() {
        val key = 0xA1B2C3D4L
        assertEquals(123_456, GameStatusBarModel.decodeMoney(123_456L xor key, key))
        assertEquals(999_999, GameStatusBarModel.decodeMoney(999_999L xor key, key))
        assertNull(GameStatusBarModel.decodeMoney(1_000_000L xor key, key))
        assertNull("garbage key/raw pair", GameStatusBarModel.decodeMoney(0x12345678L, 0L))
    }

    @Test
    fun clockHonours12And24Hour() {
        assertEquals("00:05", GameStatusBarModel.formatClock(0, 5, true))
        assertEquals("12:05 AM", GameStatusBarModel.formatClock(0, 5, false))
        assertEquals("12:30 PM", GameStatusBarModel.formatClock(12, 30, false))
        assertEquals("11:59 PM", GameStatusBarModel.formatClock(23, 59, false))
    }

    @Test
    fun batteryToneRules() {
        assertEquals(GameStatusBarModel.BatteryTone.LOW, GameStatusBarModel.batteryTone(15, false))
        assertEquals(GameStatusBarModel.BatteryTone.NORMAL, GameStatusBarModel.batteryTone(16, false))
        assertEquals(GameStatusBarModel.BatteryTone.CHARGING, GameStatusBarModel.batteryTone(5, true))
    }

    @Test
    fun gameIsThreeByTwoBelowTheBar() {
        assertEquals(1080 to 720, GameStatusBarModel.gameSize(1080, 1000, 40)) // width-bound
        assertEquals(900 to 600, GameStatusBarModel.gameSize(1920, 640, 40))   // height-bound
    }

    // --- battle auto-open return -----------------------------------------------------------------

    @Test
    fun autoOpenReturnsToPreviousTabOnlyIfStillOnBattle() {
        val r = BattleAutoOpenReturn()
        r.onAutoOpened(CompanionTab.MAP)
        assertEquals(CompanionTab.MAP, r.onBattleEnded(CompanionTab.BATTLE))
        assertNull("consumed", r.onBattleEnded(CompanionTab.BATTLE))

        r.onAutoOpened(CompanionTab.PARTY)
        assertNull("user moved away mid-battle", r.onBattleEnded(CompanionTab.HOME))

        assertNull("battle tab opened manually", BattleAutoOpenReturn().onBattleEnded(CompanionTab.BATTLE))
    }

    // --- foe team --------------------------------------------------------------------------------

    @Test
    fun foesStayHiddenUntilSentOut() {
        val t = FoeTeamTracker()
        val team = listOf(mon(1), mon(4), mon(7))
        val first = t.build(team, activeSlot = 0, nextSlot = null)
        assertEquals(listOf(1, null, null), first.map { it.speciesId })
        assertTrue(first[0].active)

        // Announced next pick is revealed and flagged; it may not be the active or a fainted mon.
        val announced = t.build(team, activeSlot = 0, nextSlot = 2)
        assertEquals(listOf(1, null, 7), announced.map { it.speciesId })
        assertTrue(announced[2].next)
        assertFalse(t.build(team, activeSlot = 0, nextSlot = 0)[0].next)

        // Once seen, stays revealed even when no longer active; a fainted foe is revealed.
        val later = t.build(listOf(mon(1, hp = 0), mon(4), mon(7)), activeSlot = null, nextSlot = null)
        assertEquals(listOf(1, null, 7), later.map { it.speciesId })
        assertTrue(later[0].fainted)

        t.reset()
        assertEquals(listOf(null, null, null), t.build(team, null, null).map { it.speciesId })
    }

    @Test
    fun foeRowHiddenForWildOrUnreadParties() {
        val t = FoeTeamTracker()
        assertTrue(t.build(emptyList(), null, null).isEmpty())
        assertTrue(t.build(listOf(mon(1)), 0, null).isEmpty())
        assertNull("out-of-range slots ignored", t.build(listOf(mon(1), mon(2)), 9, 9).firstOrNull { it.revealed })
    }

    // --- held-item table -------------------------------------------------------------------------

    @Test
    fun vanillaEmeraldIsNotAnExpansionGame() {
        assertFalse(usesExpansionItems(1))
        assertFalse(usesExpansionItems(2))
        assertFalse(usesExpansionItems(6))
        assertTrue(usesExpansionItems(8))
    }

    // --- detector --------------------------------------------------------------------------------

    private val profiles: List<RomHackProfile> by lazy {
        val dir = generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
            .map { File(it, "app/src/main/assets/profiles") }
            .first { it.isDirectory }
        // Ghost Grey first: the old code picked it for any BPRE ROM because it lists BPRE.
        listOf("ghost_grey", "radical_red", "heart_and_soul", "unbound", "vanilla_firered", "vanilla_emerald")
            .map { ProfileLoader.parseProfile(File(dir, "$it.json").readText()) }
    }

    private fun header(title: String, code: String) = ByteArray(192).also {
        title.toByteArray().copyInto(it, 160)
        code.toByteArray().copyInto(it, 172)
    }

    @Test
    fun unknownBpreIsFireRedBaseNotGhostGrey() {
        val r = RomHackDetector.detectCompatibilityFromBytes(header("MYHACK", "BPRE"), "ab", profiles, "myhack.gba")
        assertEquals("vanilla_firered", r.profile.id)
        assertEquals(ProfileMatchMethod.BASE_GAME_FALLBACK, r.matchMethod)
    }

    @Test
    fun heartOrSoulInFilenameIsNotHeartAndSoul() {
        for (name in listOf("Heart Gold.gba", "soulsilver_port.gba", "heartbeat.gba")) {
            val r = RomHackDetector.detectCompatibilityFromBytes(header("ZZ", "ZZZZ"), "ab", profiles, name)
            assertEquals(name, RomCompatibilityStatus.UNSUPPORTED, r.status)
        }
    }

    @Test
    fun realHeartAndSoulNamesStillRecognized() {
        for (name in listOf("Pokemon Heart & Soul.gba", "Heart and Soul v2.0.5.gba", "pokemon_hns.gba", "HeartSoul.gba")) {
            val r = RomHackDetector.detectCompatibilityFromBytes(header("ZZ", "ZZZZ"), "ab", profiles, name)
            assertEquals(name, "heart_and_soul", r.profile.id)
            assertFalse(r.mayReadLiveMemory)
        }
        val byTitle = RomHackDetector.detectCompatibilityFromBytes(header("POKEMON HNS", "BPEE"), "ab", profiles, "")
        assertEquals("heart_and_soul", byTitle.profile.id)
        assertEquals(ProfileMatchMethod.HEADER_TITLE, byTitle.matchMethod)
    }

    private fun mon(species: Int, hp: Int = 100) = ParsedPokemon(
        isValid = true, isEmpty = false, pid = species.toLong(), tid = 1, sid = 2,
        nickname = "", otName = "", species = species, heldItem = 0, level = 50, nature = 0,
        natureName = "Hardy", isShiny = false, abilitySlot = 0, isEgg = false, friendship = 0,
        experience = 0L, hpIv = 0, attackIv = 0, defenseIv = 0, speedIv = 0, spAttackIv = 0,
        spDefenseIv = 0, hpEv = 0, attackEv = 0, defenseEv = 0, speedEv = 0, spAttackEv = 0,
        spDefenseEv = 0, moves = intArrayOf(1, 0, 0, 0), pp = intArrayOf(35, 0, 0, 0),
        currentHp = hp, maxHp = 100, attack = 100, defense = 100, speed = 100, spAttack = 100,
        spDefense = 100, statusCondition = 0L
    )
}
