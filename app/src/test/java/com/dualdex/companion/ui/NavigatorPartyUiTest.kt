package com.dualdex.companion.ui

import com.dualdex.emulator.RomDurableResumeTest
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.settings.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issues #133 (Navigator style) and #151 (in-game-style party slots). */
class NavigatorPartyUiTest {

    // ---- Pixel font ----

    @Test
    fun everyGlyphIsWellFormedAndDrawsInsideItsCell() {
        for (c in PixelFont.supportedCharacters) {
            val g = PixelFont.glyph(c)
            assertEquals("rows of '$c'", PixelFont.HEIGHT, g.rows.size)
            g.rows.forEach { row ->
                assertTrue("'$c' row '$row' must be blank or full width", row.isEmpty() || row.length == g.width)
                assertTrue("'$c' uses only '#' and '.'", row.all { it == '#' || it == '.' })
            }
            PixelFont.forEachInk(c.toString(), 0, 0) { x, y ->
                assertTrue("'$c' ink x=$x", x in 0 until g.width)
                assertTrue("'$c' ink y=$y", y in 0 until PixelFont.HEIGHT)
            }
        }
    }

    @Test
    fun capitalsAndDigitsStayAboveTheDescenderRows() {
        (('A'..'Z') + ('0'..'9')).forEach { c ->
            PixelFont.forEachInk(c.toString(), 0, 0) { _, y -> assertTrue("'$c' y=$y", y < PixelFont.CAP_HEIGHT) }
        }
    }

    @Test
    fun fontCoversNamesAndSlotTextAndFallsBackForUnknownCharacters() {
        val needed = ('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('/', '♂', '♀', '★', ' ', '.', '-', '\'')
        needed.forEach { assertTrue("missing '$it'", it in PixelFont.supportedCharacters) }
        assertEquals(PixelFont.glyph('?').rows, PixelFont.glyph('ж').rows)
    }

    @Test
    fun measureAndFitUseOnePixelSpacing() {
        assertEquals(0, PixelFont.measure(""))
        assertEquals(5, PixelFont.measure("A"))
        assertEquals(11, PixelFont.measure("HP"))
        assertEquals("TYPH", PixelFont.fit("TYPHLOSION", 23))
        assertEquals("", PixelFont.fit("TYPHLOSION", 2))
    }

    // ---- Slot rules ----

    @Test
    fun hpBarColourBandsFollowGen3Thresholds() {
        assertEquals(HpBarLevel.GREEN, PartySlotModel.hpBarLevel(100, 100))
        assertEquals(HpBarLevel.GREEN, PartySlotModel.hpBarLevel(53, 100)) // 25/48
        assertEquals(HpBarLevel.YELLOW, PartySlotModel.hpBarLevel(52, 100)) // 24/48: Gen 3 shows yellow
        assertEquals(HpBarLevel.YELLOW, PartySlotModel.hpBarLevel(50, 100)) // 24/48 is not > 24
        assertEquals(HpBarLevel.YELLOW, PartySlotModel.hpBarLevel(21, 100))
        assertEquals(HpBarLevel.RED, PartySlotModel.hpBarLevel(20, 100)) // 9/48 is not > 9
        assertEquals(HpBarLevel.RED, PartySlotModel.hpBarLevel(1, 999))
        assertEquals(HpBarLevel.EMPTY, PartySlotModel.hpBarLevel(0, 100))
        assertEquals(HpBarLevel.EMPTY, PartySlotModel.hpBarLevel(0, 0))
    }

    @Test
    fun hpBarNeverReadsEmptyWhileAlive() {
        assertEquals(1, PartySlotModel.scaledHp(1, 714, 60))
        assertEquals(0, PartySlotModel.scaledHp(0, 714, 60))
        assertEquals(60, PartySlotModel.scaledHp(714, 714, 60))
        assertEquals(60, PartySlotModel.scaledHp(999, 714, 60))
        assertEquals(30, PartySlotModel.scaledHp(357, 714, 60))
    }

    @Test
    fun statusBadgeUsesLiveBitsWithFaintedFirstAndUnknownVisible() {
        assertNull(PartySlotModel.statusLabel(mon()))
        assertEquals("SLP", PartySlotModel.statusLabel(mon(status = 3L)))
        assertEquals("PSN", PartySlotModel.statusLabel(mon(status = 1L shl 3)))
        assertEquals("BRN", PartySlotModel.statusLabel(mon(status = 1L shl 4)))
        assertEquals("FRZ", PartySlotModel.statusLabel(mon(status = 1L shl 5)))
        assertEquals("PAR", PartySlotModel.statusLabel(mon(status = 1L shl 6)))
        assertEquals("TOX", PartySlotModel.statusLabel(mon(status = (1L shl 7) or (1L shl 3))))
        assertEquals("???", PartySlotModel.statusLabel(mon(status = 1L shl 9)))
        assertEquals("FNT", PartySlotModel.statusLabel(mon(hp = 0, status = 1L shl 3)))
        assertNull(PartySlotModel.statusLabel(mon(hp = 0, egg = true)))
    }

    @Test
    fun genderFollowsGen3RatioRules() {
        assertNull(PartySlotModel.gender(0x12L, null))
        assertNull(PartySlotModel.gender(0x12L, 255))
        assertEquals('♂', PartySlotModel.gender(0xFFL, 0))
        assertEquals('♀', PartySlotModel.gender(0xFFL, 254))
    }

    @Test
    fun genderThresholdComparesAgainstPersonalityLowByte() {
        assertEquals('♂', PartySlotModel.gender(0xAB00_001FL, 31))
        assertEquals('♀', PartySlotModel.gender(0xAB00_001EL, 31))
        assertEquals('♂', PartySlotModel.gender(0x7FL, 127))
        assertEquals('♀', PartySlotModel.gender(0x7EL, 127))
    }

    @Test
    fun frameStatesCoverSelectionFaintingAndEggs() {
        assertEquals(SlotFrame.NORMAL, state().frame)
        assertEquals(SlotFrame.SELECTED, state(selected = true).frame)
        assertEquals(SlotFrame.FAINTED, state(hp = 0).frame)
        assertEquals(SlotFrame.SELECTED_FAINTED, state(hp = 0, selected = true).frame)
        assertEquals(SlotFrame.NO_HP, state(egg = true, hp = 0).frame)
        assertEquals(SlotFrame.NO_HP_SELECTED, state(egg = true, selected = true).frame)
    }

    @Test
    fun gridUsesThreeColumnsAtFourTimesOnTheThorBottomScreen() {
        // Thor lower screen: ~1194 px of viewport width; the grid may take 55% of ~805 px.
        val spec = PartySlotModel.chooseGrid(width = 1157, maxHeight = 443, gapPx = 9)
        assertEquals(3, spec.columns)
        assertEquals(2, spec.rows)
        assertEquals(4, spec.scale)
        assertTrue(spec.windowWidth >= PartySlotModel.MIN_WINDOW_WIDTH)
        assertTrue(2 * PartySlotModel.slotViewHeight * spec.scale + 9 <= 443)
    }

    @Test
    fun gridFallsBackToTwoColumnsWhenThatScalesLarger() {
        val spec = PartySlotModel.chooseGrid(width = 560, maxHeight = 900, gapPx = 8)
        assertEquals(2, spec.columns)
        assertEquals(3, spec.scale)
    }

    @Test
    fun narrowWidthsDropToOneColumnInsteadOfClipping() {
        // Review case: 100 px used to pick 3 columns of 28 px and draw 84 px slots into them.
        val tiny = PartySlotModel.chooseGrid(width = 100, maxHeight = 50, gapPx = 8)
        assertEquals(1, tiny.columns)
        assertTrue(tiny.slotPixelWidth <= tiny.cellWidth)
    }

    @Test
    fun touchFloorHoldsAndLayoutScrollsWhenHeightIsShort() {
        // Phone split-screen fallback: 2.75x density, 48dp = 132 px -> min scale 3.
        val minScale = PartySlotModel.minScaleFor(132)
        assertEquals(3, minScale)
        val spec = PartySlotModel.chooseGrid(width = 1080, maxHeight = 300, gapPx = 11, minScale = minScale)
        assertEquals(3, spec.columns) // widest layout that holds the floor; the host scrolls it
        assertTrue(spec.scale >= minScale)
        assertTrue(spec.slotPixelHeight >= 132)
        assertTrue(spec.totalHeight(11) > 300)
    }

    @Test
    fun everyChosenGridFitsItsCellsAcrossScreenSizes() {
        val minScale = PartySlotModel.minScaleFor(110) // Thor: 48dp at 2.3x
        for (width in 84..2400 step 7) for (height in intArrayOf(0, 120, 300, 443, 900, 1600)) {
            val spec = PartySlotModel.chooseGrid(width, height, gapPx = 9, minScale = minScale)
            val label = "w=$width h=$height -> $spec"
            assertTrue(label, spec.slotPixelWidth <= spec.cellWidth)
            assertTrue(label, spec.columns * spec.cellWidth + 9 * (spec.columns - 1) <= width)
            assertTrue(label, spec.windowWidth >= PartySlotModel.MIN_WINDOW_WIDTH)
            // The touch floor is only relaxed when one column cannot hold it.
            if (width >= (PartySlotModel.MIN_WINDOW_WIDTH + 2 * PartySlotModel.PAD) * minScale) {
                assertTrue(label, spec.scale >= minScale)
            }
        }
    }

    @Test
    fun slotBarAndDetailMeterShareOneHpBand() {
        // Review case: raw 52% read green in the detail meter while the Gen 3 slot showed yellow.
        val previous = DualDexTheme.style
        try {
            for (style in CompanionVisualStyle.values()) {
                DualDexTheme.style = style
                assertEquals(DualDexTheme.Color.warning, PartySlotModel.hpColor(51, 100))
                assertEquals(DualDexTheme.Color.warning, PartySlotModel.hpColor(52, 100))
                assertEquals(DualDexTheme.Color.success, PartySlotModel.hpColor(53, 100))
                assertEquals(DualDexTheme.Color.danger, PartySlotModel.hpColor(20, 100))
                assertEquals(DualDexTheme.Color.danger, PartySlotModel.hpColor(0, 100))
                for (hp in 0..100) {
                    val expected = when (PartySlotModel.hpBarLevel(hp, 100)) {
                        HpBarLevel.GREEN -> DualDexTheme.Color.success
                        HpBarLevel.YELLOW -> DualDexTheme.Color.warning
                        HpBarLevel.RED, HpBarLevel.EMPTY -> DualDexTheme.Color.danger
                    }
                    assertEquals("hp=$hp", expected, PartySlotModel.hpColor(hp, 100))
                }
            }
        } finally {
            DualDexTheme.style = previous
        }
    }

    @Test
    fun accessibilityLabelsUseFullWordsNotColour() {
        assertEquals("Party slot 3, empty", PartySlotModel.accessibilityLabel(2, null))
        assertEquals(
            "Party slot 1, LEET, level 74, HP 0 of 210, fainted, selected",
            PartySlotModel.accessibilityLabel(0, state(name = "LEET", level = 74, hp = 0, max = 210, status = "FNT", selected = true))
        )
        assertEquals(
            "Party slot 2, Mon, level 50, HP 10 of 100, status PSN",
            PartySlotModel.accessibilityLabel(1, state(hp = 10, status = "PSN"))
        )
    }

    // ---- Style contract ----

    @Test
    fun styleSwitchSwapsSemanticTokensAndKeepsQuietValues() {
        val previous = DualDexTheme.style
        try {
            DualDexTheme.style = CompanionVisualStyle.QUIET_HANDHELD
            assertEquals(0xFF101116.toInt(), DualDexTheme.Color.background)
            assertEquals(8, DualDexTheme.Radius.control)
            assertEquals(56, DualDexTheme.Control.primaryNavigationHeight)
            DualDexTheme.style = CompanionVisualStyle.NAVIGATOR
            assertEquals(0xFF0A2E3A.toInt(), DualDexTheme.Color.background)
            assertEquals(0xFF6BE4E8.toInt(), DualDexTheme.Color.accent)
            assertEquals(0xFFFFE178.toInt(), DualDexTheme.Color.focusRing)
            // Controller focus must never look like the selected state.
            assertNotEquals(DualDexTheme.Color.accent, DualDexTheme.Color.focusRing)
            assertTrue(DualDexTheme.Control.primaryNavigationHeight >= DualDexTheme.Spacing.touchTarget)
        } finally {
            DualDexTheme.style = previous
        }
    }

    @Test
    fun visualStyleSettingDefaultsToNavigatorAndPersists() {
        val prefs = RomDurableResumeTest.FakeSharedPreferences()
        val settings = SettingsManager(prefs)
        assertEquals(CompanionVisualStyle.NAVIGATOR, settings.companionVisualStyle)
        settings.companionVisualStyle = CompanionVisualStyle.QUIET_HANDHELD
        assertEquals(CompanionVisualStyle.QUIET_HANDHELD, SettingsManager(prefs).companionVisualStyle)
        prefs.edit().putString("key_companion_visual_style", "NOT_A_STYLE").apply()
        assertEquals(CompanionVisualStyle.NAVIGATOR, settings.companionVisualStyle)
        assertFalse(prefs.all.isEmpty())
    }

    private fun state(
        name: String = "Mon",
        level: Int = 50,
        hp: Int = 100,
        max: Int = 100,
        status: String? = null,
        egg: Boolean = false,
        selected: Boolean = false,
    ) = PartySlotState(name, level, hp, max, status, null, false, egg, selected)

    private fun mon(hp: Int = 100, status: Long = 0L, egg: Boolean = false) = ParsedPokemon(
        isValid = true, isEmpty = false, pid = 1L, tid = 2, sid = 3, nickname = "Mon", otName = "OT",
        species = 1, heldItem = 0, level = 50, nature = 0, natureName = "Hardy", isShiny = false,
        abilitySlot = 0, isEgg = egg, friendship = 0, experience = 0L,
        hpIv = 0, attackIv = 0, defenseIv = 0, speedIv = 0, spAttackIv = 0, spDefenseIv = 0,
        hpEv = 0, attackEv = 0, defenseEv = 0, speedEv = 0, spAttackEv = 0, spDefenseEv = 0,
        moves = intArrayOf(0, 0, 0, 0), pp = intArrayOf(0, 0, 0, 0),
        currentHp = hp, maxHp = 100, attack = 50, defense = 50, speed = 50, spAttack = 50, spDefense = 50,
        statusCondition = status
    )
}
