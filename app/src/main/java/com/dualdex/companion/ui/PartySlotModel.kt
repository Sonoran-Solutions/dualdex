package com.dualdex.companion.ui

import com.dualdex.pokemon.ParsedPokemon

/** Frame states of an in-game-style party slot (issue #151). */
enum class SlotFrame { NORMAL, SELECTED, FAINTED, SELECTED_FAINTED, NO_HP, NO_HP_SELECTED, EMPTY }

/** Gen 3 HP bar colour bands (`GetHPBarLevel`). */
enum class HpBarLevel { GREEN, YELLOW, RED, EMPTY }

/** Everything one slot draws. Data class so an unchanged 10 Hz poll skips the re-render. */
data class PartySlotState(
    val name: String,
    val level: Int,
    val currentHp: Int,
    val maxHp: Int,
    val status: String?,
    val gender: Char?,
    val shiny: Boolean,
    val isEgg: Boolean,
    val selected: Boolean,
) {
    val frame: SlotFrame get() = PartySlotModel.frameFor(this)
}

/**
 * How the six slots are arranged at an integer GBA pixel scale. [cellWidth] is the pixel width
 * each slot view gets; the rendered slot `(windowWidth + 2 * PAD) * scale` never exceeds it.
 */
data class PartyGridSpec(val columns: Int, val rows: Int, val scale: Int, val windowWidth: Int, val cellWidth: Int) {
    val slotPixelWidth: Int get() = (windowWidth + PartySlotModel.PAD * 2) * scale
    val slotPixelHeight: Int get() = PartySlotModel.slotViewHeight * scale
    fun totalHeight(gapPx: Int): Int = slotPixelHeight * rows + gapPx * (rows - 1)
}

/**
 * Pure presentation rules for the party slots, kept free of Android types so they are unit tested.
 * Thresholds follow the public pret decompilations' `battle_interface.c`; nothing is copied from
 * game assets.
 */
object PartySlotModel {
    /** Logical (GBA pixel) height of the slot window, and its minimum width. */
    const val WINDOW_HEIGHT = 46
    const val MIN_WINDOW_WIDTH = 80
    /** Logical padding around the window, used by the controller-focus cursor. */
    const val PAD = 2
    const val SLOT_COUNT = 6
    private const val HEALTHBAR_PIXELS = 48

    /** `GetScaledHPFraction`: never 0 while the Pokémon still has HP. */
    fun scaledHp(hp: Int, maxHp: Int, scale: Int): Int {
        if (maxHp <= 0 || hp <= 0) return 0
        val result = (hp.coerceAtMost(maxHp) * scale) / maxHp
        return if (result == 0) 1 else result
    }

    /** `GetHPBarLevel`: full is green; then >50% green, >20% yellow, otherwise red. */
    fun hpBarLevel(hp: Int, maxHp: Int): HpBarLevel {
        if (maxHp > 0 && hp >= maxHp) return HpBarLevel.GREEN
        val fraction = scaledHp(hp, maxHp, HEALTHBAR_PIXELS)
        return when {
            fraction > HEALTHBAR_PIXELS * 50 / 100 -> HpBarLevel.GREEN
            fraction > HEALTHBAR_PIXELS * 20 / 100 -> HpBarLevel.YELLOW
            fraction > 0 -> HpBarLevel.RED
            else -> HpBarLevel.EMPTY
        }
    }

    /**
     * The one HP colour authority for the Party screen: the slot bar and the detail meter both
     * map [hpBarLevel] through this, so the same Pokémon never shows two different bands.
     */
    fun hpColor(hp: Int, maxHp: Int): Int = when (hpBarLevel(hp, maxHp)) {
        HpBarLevel.GREEN -> DualDexTheme.Color.success
        HpBarLevel.YELLOW -> DualDexTheme.Color.warning
        HpBarLevel.RED, HpBarLevel.EMPTY -> DualDexTheme.Color.danger
    }

    /**
     * Status badge text. Uses the same bit layout as the calculator's live read; fainted wins.
     * A non-zero condition matching no known bit shows "???" rather than looking healthy.
     */
    fun statusLabel(mon: ParsedPokemon): String? {
        if (mon.isEgg) return null
        if (mon.maxHp > 0 && mon.currentHp <= 0) return "FNT"
        val s = mon.statusCondition
        return when {
            s == 0L -> null
            (s and (1L shl 7)) != 0L -> "TOX"
            (s and (1L shl 3)) != 0L -> "PSN"
            (s and (1L shl 4)) != 0L -> "BRN"
            (s and (1L shl 5)) != 0L -> "FRZ"
            (s and (1L shl 6)) != 0L -> "PAR"
            (s and 0x7L) != 0L -> "SLP"
            else -> "???"
        }
    }

    /**
     * Gen 3 gender from the species gender-ratio byte and the personality value
     * (`GetGenderFromSpeciesAndPersonality`). Null when unknown or genderless.
     */
    fun gender(pid: Long, genderRatio: Int?): Char? = when (genderRatio) {
        null, 255 -> null
        0 -> '♂'
        254 -> '♀'
        else -> if (genderRatio > (pid and 0xFF).toInt()) '♀' else '♂'
    }

    fun frameFor(state: PartySlotState): SlotFrame {
        val fainted = !state.isEgg && state.maxHp > 0 && state.currentHp <= 0
        return when {
            state.isEgg -> if (state.selected) SlotFrame.NO_HP_SELECTED else SlotFrame.NO_HP
            fainted -> if (state.selected) SlotFrame.SELECTED_FAINTED else SlotFrame.FAINTED
            else -> if (state.selected) SlotFrame.SELECTED else SlotFrame.NORMAL
        }
    }

    /** Logical size of one slot view including the focus padding. */
    val slotViewHeight: Int get() = WINDOW_HEIGHT + PAD * 2

    /** Smallest scale whose slot height meets [minTouchPx] (the 48dp target). */
    fun minScaleFor(minTouchPx: Int): Int =
        ((minTouchPx + slotViewHeight - 1) / slotViewHeight).coerceAtLeast(1)

    /**
     * Arranges six slots in [width] pixels, aiming to fit within [maxHeight].
     *
     * Tries 3×2, 2×3, then 1×6. A layout is only eligible if a slot at [minScale] (the 48dp
     * touch floor) fits its cell's width. Among eligible layouts that also fit [maxHeight], the
     * largest scale wins (more columns on a tie). If none fits the height, the eligible layout
     * with the most columns is used at [minScale] and the host scrolls it. Only when even one
     * column cannot hold [minScale] does the scale drop below it, never below 1; the supported
     * floor is a cell of `MIN_WINDOW_WIDTH + 2 * PAD` (84) pixels. Extra width widens the window
     * (the HP bar grows) instead of letterboxing.
     */
    fun chooseGrid(width: Int, maxHeight: Int, gapPx: Int, minScale: Int = 1): PartyGridSpec {
        val minSlot = MIN_WINDOW_WIDTH + PAD * 2
        fun spec(columns: Int, scale: Int): PartyGridSpec {
            val cellWidth = ((width - gapPx * (columns - 1)) / columns).coerceAtLeast(0)
            val windowWidth = (cellWidth / scale - PAD * 2).coerceAtLeast(MIN_WINDOW_WIDTH)
            return PartyGridSpec(columns, SLOT_COUNT / columns, scale, windowWidth, cellWidth)
        }
        var bestFitting: PartyGridSpec? = null
        var widestScrolling: PartyGridSpec? = null
        for (columns in intArrayOf(3, 2, 1)) {
            val rows = SLOT_COUNT / columns
            val cellWidth = (width - gapPx * (columns - 1)) / columns
            val byWidth = cellWidth / minSlot
            if (byWidth < minScale) continue
            val byHeight = (maxHeight - gapPx * (rows - 1)) / rows / slotViewHeight
            if (byHeight >= minScale) {
                val candidate = spec(columns, minOf(byWidth, byHeight))
                if (bestFitting == null || candidate.scale > bestFitting.scale) bestFitting = candidate
            } else if (widestScrolling == null) {
                widestScrolling = spec(columns, minScale)
            }
        }
        bestFitting?.let { return it }
        widestScrolling?.let { return it }
        // Narrower than one column at the touch floor: shrink to whatever one column holds.
        return spec(1, (width / minSlot).coerceAtLeast(1))
    }

    fun accessibilityLabel(index: Int, state: PartySlotState?): String {
        val slot = "Party slot ${index + 1}"
        if (state == null) return "$slot, empty"
        if (state.isEgg) return "$slot, Egg" + if (state.selected) ", selected" else ""
        return buildString {
            append("$slot, ${state.name}, level ${state.level}, HP ${state.currentHp} of ${state.maxHp}")
            when (state.status) {
                null -> Unit
                "FNT" -> append(", fainted")
                else -> append(", status ${state.status}")
            }
            if (state.selected) append(", selected")
        }
    }
}
