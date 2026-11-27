package com.dualdex.nuzlocke

import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.pokemon.PlayerLocation

/** Outcome of the tracked wild encounter for a single area. */
enum class EncounterOutcome(val label: String) {
    MET("Met"),
    CAUGHT("Caught"),
    MISSED("Missed"),
    DUPES_SKIPPED("Dupes")
}

/** Battle shape observed from live emulator memory (mirrors native BattleKind). */
enum class NuzlockeBattleKind {
    NONE,
    WILD_SINGLE,
    TRAINER_SINGLE,
    DOUBLES,
    MULTI_OR_PARTNER,
    UNKNOWN
}

/** Recorded first wild encounter for one map area (`mapGroup:mapNum`). */
data class AreaEncounter(
    val areaKey: String,
    val areaName: String,
    val speciesId: Int,
    val speciesName: String,
    val level: Int,
    val outcome: EncounterOutcome,
    val recordedAtMs: Long
)

/** Permanently recorded fainted party member for a Nuzlocke run. */
data class FallenMon(
    val monKey: String,
    val speciesId: Int,
    val speciesName: String,
    val nickname: String,
    val level: Int,
    val areaName: String,
    val fallenAtMs: Long
)

/** Complete persisted Nuzlocke run state scoped to a single ROM SHA-256. */
data class NuzlockeRunState(
    val romSha256: String,
    val enabled: Boolean = false,
    val dupesClause: Boolean = true,
    val levelCap: Int? = null,
    val encounters: Map<String, AreaEncounter> = emptyMap(),
    val fallen: Map<String, FallenMon> = emptyMap()
)

/** Combined 32-bit Gen III Original Trainer ID (`(sid << 16) | tid`). */
val ParsedPokemon.otId: Long
    get() = ((sid.toLong() and 0xFFFFL) shl 16) or (tid.toLong() and 0xFFFFL)

/**
 * Stable per-Pokémon identity key across party slot changes and healing, based on `pid` and `otId`.
 * Returns an empty string for eggs, empty slots, or invalid reads so callers can fail closed.
 */
fun monIdentityKey(mon: ParsedPokemon): String {
    if (!mon.isValid || mon.isEmpty || mon.isEgg || mon.species <= 0) return ""
    return "${mon.pid}:${mon.otId}"
}

/** Stable map area key (`mapGroup:mapNum`) for a valid [PlayerLocation]. */
fun areaKeyOf(location: PlayerLocation): String = "${location.mapGroup}:${location.mapNum}"
