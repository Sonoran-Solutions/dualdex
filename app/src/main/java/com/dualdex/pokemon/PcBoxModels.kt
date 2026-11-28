package com.dualdex.pokemon

import com.dualdex.pokemon.hns.HnsAbilityRegistry

enum class PcBoxAvailability { IDLE, LOADING, UNSUPPORTED, UNREADABLE, AVAILABLE }

data class PcBoxSlot(
    val index: Int,
    val pokemon: ParsedPokemon? = null,
    val nationalDex: Int? = null,
    val abilityId: Int? = null,
    val growthRate: Int? = null
) {
    val isOccupied: Boolean get() = pokemon?.let { it.isValid && !it.isEmpty && it.species > 0 } == true
    val estimatedLevel: Int?
        get() = pokemon?.takeUnless { it.isEgg }?.let { estimatePcBoxLevel(it.experience, growthRate) }
    val canSendToCalc: Boolean get() = isOccupied && nationalDex != null && estimatedLevel != null && abilityName != null

    // Vanilla Gen III IDs 1–76 are shared with the app's canonical ability-name registry.
    val abilityName: String?
        get() = if (abilityId == 0) "None" else abilityId?.let(HnsAbilityRegistry::canonicalTitleCaseName)

    fun speciesName(pack: GameDataPack): String =
        nationalDex?.let { pack.getSpecies(it)?.name } ?: "Species #${pokemon?.species ?: 0}"
}

data class PcBoxSnapshot(
    val boxIndex: Int,
    val boxCount: Int,
    val currentBoxIndex: Int,
    val slots: List<PcBoxSlot>,
    val availability: PcBoxAvailability
) {
    fun occupiedSlots(): List<PcBoxSlot> =
        if (availability == PcBoxAvailability.AVAILABLE) slots.filter { it.isOccupied } else emptyList()

    fun findOccupiedSlots(
        query: String,
        speciesName: (PcBoxSlot) -> String
    ): List<PcBoxSlot> {
        val term = query.trim()
        return occupiedSlots().filter {
            speciesName(it).contains(term, ignoreCase = true) ||
                it.pokemon?.nickname?.contains(term, ignoreCase = true) == true
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, speciesName).thenBy { it.index })
    }

    companion object {
        const val SLOTS_PER_BOX = 30
        const val CURRENT_BOX = -1
        const val LEVEL_NOTE = "Level is derived from experience. Battle stats are calculated when used."
        private const val HEADER_WORDS = 4
        private const val SLOT_WORDS = 3
        internal const val NATIVE_METADATA_WORDS = HEADER_WORDS + SLOTS_PER_BOX * SLOT_WORDS

        fun unavailable(availability: PcBoxAvailability = PcBoxAvailability.UNSUPPORTED): PcBoxSnapshot {
            require(availability != PcBoxAvailability.AVAILABLE)
            return PcBoxSnapshot(CURRENT_BOX, 0, CURRENT_BOX, emptyList(), availability)
        }

        internal fun fromNative(metadata: IntArray, mons: Array<ParsedPokemon?>?): PcBoxSnapshot {
            if (metadata.size != NATIVE_METADATA_WORDS) return unavailable(PcBoxAvailability.UNREADABLE)
            if (metadata[0] == 0 && mons == null) return unavailable()
            val index = metadata[1]
            val count = metadata[2]
            val current = metadata[3]
            if (metadata[0] != 2 || count !in 1..255 || index !in 0 until count ||
                current !in 0 until count || mons == null || mons.size !in 1..SLOTS_PER_BOX
            ) return unavailable(PcBoxAvailability.UNREADABLE)

            val slots = mons.mapIndexed { slotIndex, mon ->
                if (mon == null || !mon.isValid || mon.isEmpty || mon.species <= 0) {
                    PcBoxSlot(slotIndex)
                } else {
                    val offset = HEADER_WORDS + slotIndex * SLOT_WORDS
                    PcBoxSlot(
                        slotIndex, mon,
                        metadata[offset].takeIf { it in 1..386 },
                        metadata[offset + 1].takeIf { it in 0..76 },
                        metadata[offset + 2].takeIf { it in 0..5 }
                    )
                }
            }
            return PcBoxSnapshot(index, count, current, slots, PcBoxAvailability.AVAILABLE)
        }
    }
}

/** The six Gen III experience curves, with the game's integer rounding at each division. */
internal fun estimatePcBoxLevel(experience: Long, growthRate: Int?): Int? {
    if (experience < 0 || growthRate == null || growthRate !in 0..5) return null
    for (level in 100 downTo 2) {
        val cube = level * level * level
        val threshold = when (growthRate) {
            0 -> cube // Medium Fast
            1 -> when { // Erratic
                level <= 50 -> (100 - level) * cube / 50
                level <= 68 -> (150 - level) * cube / 100
                level <= 98 -> ((1911 - 10 * level) / 3) * cube / 500
                else -> (160 - level) * cube / 100
            }
            2 -> when { // Fluctuating
                level <= 15 -> ((level + 1) / 3 + 24) * cube / 50
                level <= 36 -> (level + 14) * cube / 50
                else -> (level / 2 + 32) * cube / 50
            }
            3 -> 6 * cube / 5 - 15 * level * level + 100 * level - 140 // Medium Slow
            4 -> 4 * cube / 5 // Fast
            else -> 5 * cube / 4 // Slow
        }
        if (experience >= threshold) return level
    }
    return 1
}
