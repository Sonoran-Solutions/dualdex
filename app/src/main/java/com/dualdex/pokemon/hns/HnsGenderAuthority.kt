package com.dualdex.pokemon.hns

/** Current battle gender from the pinned GetGenderFromSpeciesAndPersonality contract. */
enum class HnsBattlerGender { MALE, FEMALE, GENDERLESS, UNKNOWN }

object HnsGenderAuthority {
    /**
     * H&S 2.0.5 `GetGenderFromSpeciesAndPersonality` reads the current BattlePokemon species
     * and personality. MON_MALE/FEMALE/GENDERLESS are 0/254/255; other ratios compare against
     * the low personality byte. `genderRatio` comes from the pinned generated species table.
     */
    fun resolve(speciesId: Int?, personality: Int?, genderRatio: Int?): HnsBattlerGender {
        if (speciesId == null || speciesId <= 0 || personality == null) return HnsBattlerGender.UNKNOWN
        val ratio = genderRatio ?: return HnsBattlerGender.UNKNOWN
        if (ratio !in 0..255) return HnsBattlerGender.UNKNOWN
        return when (ratio) {
            0 -> HnsBattlerGender.MALE
            254 -> HnsBattlerGender.FEMALE
            255 -> HnsBattlerGender.GENDERLESS
            else -> {
                val pid = personality ?: return HnsBattlerGender.UNKNOWN
                if (ratio > (pid and 0xff)) HnsBattlerGender.FEMALE else HnsBattlerGender.MALE
            }
        }
    }
}
