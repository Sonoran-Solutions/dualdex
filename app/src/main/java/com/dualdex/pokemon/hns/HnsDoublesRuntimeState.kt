package com.dualdex.pokemon.hns

/** Minimal indexed live record. This is never a party-derived ability claim. */
data class HnsDoublesBattler(
    val index: Int, val position: Int, val partySlot: Int, val hp: Int,
    val species: Int, val ability: Int, val item: Int, val gastroAcid: Boolean,
    val neutralizingGas: Boolean, val transformed: Boolean, val semiInvulnerable: Int,
    val ruinFlags: Int, val helpingHand: Int
) {
    val effectiveAbility: Int get() = if (gastroAcid &&
        (ability !in HnsDoublesLayout.UNSUPPRESSIBLE_ABILITIES || ability == 213 && transformed)) 0 else ability
}

/** Version 1 is appended to the legacy participant tuple; old tuples have no packet. */
data class HnsDoublesRuntimeState(
    val count: Int, val absentFlags: Int, val followMeTimers: List<Int>,
    val moldBreakerActive: Boolean, val battlers: List<HnsDoublesBattler>, val pledgeMove: Boolean = false
) {
    fun alive(index: Int): Boolean = index in 0 until count &&
        absentFlags and (1 shl index) == 0 && battlers[index].hp > 0

    fun valid(): Boolean = count == 4 && absentFlags in 0..15 &&
        followMeTimers.size == 2 && followMeTimers.all { it in 0..15 } &&
        battlers.size == 4 && battlers.map { it.position }.toSet() == setOf(0, 1, 2, 3) &&
        (0..1).all { side -> battlers.filter { alive(it.index) && (it.position and 1) == side }
            .map { it.partySlot }.let { it.size == it.toSet().size } } &&
        battlers.withIndex().all { (i, b) -> b.index == i && b.partySlot in 0..5 &&
            b.hp in 0..65535 && b.species in 0 until HnsGroupDLayout.SPECIES_COUNT &&
            (absentFlags and (1 shl i) != 0 || b.species > 0) &&
            b.ability in 0..HnsBattlerRuntimeStateIds.ABILITY_ID_MAX && b.item in 0..Hns205ItemCatalogue.ITEM_ID_MAX &&
            b.semiInvulnerable in 0..6 && b.ruinFlags in 0..15 && b.helpingHand in 0..7 &&
            (b.position and 1) == (battlers[i xor 2].position and 1) }

    companion object {
        const val TUPLE_LENGTH = 162
        fun decode(raw: IntArray): HnsDoublesRuntimeState? {
            if (raw.size < TUPLE_LENGTH || raw[103] != 1 || raw[104] != 1) return null
            if (raw[109] !in 0..3) return null
            val records = (0..3).map { i ->
                val r = 110 + i * 13
                if ((7..9).any { raw[r + it] !in 0..1 }) return null
                HnsDoublesBattler(raw[r], raw[r+1], raw[r+2], raw[r+3], raw[r+4],
                    raw[r+5], raw[r+6], raw[r+7] == 1, raw[r+8] == 1, raw[r+9] == 1,
                    raw[r+10], raw[r+11], raw[r+12])
            }
            return HnsDoublesRuntimeState(raw[105], raw[106], listOf(raw[107], raw[108]),
                (raw[109] and 1) != 0, records, (raw[109] and 2) != 0).takeIf { it.valid() }
        }
    }
}
