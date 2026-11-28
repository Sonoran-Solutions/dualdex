package com.dualdex.nuzlocke

import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.pokemon.PlayerLocation

/**
 * Pure JVM Nuzlocke run state machine.
 *
 * Tracks first wild encounters per area (with optional Dupes Clause), resolves catches vs misses
 * when a wild battle ends, and permanently records fainted non-egg party Pokémon. Strictly
 * read-only with respect to emulator RAM and game saves, and fails closed whenever the run is
 * disabled or live observations are unverified/transitioning.
 */
class NuzlockeTracker(
    initialState: NuzlockeRunState,
    private val clockMs: () -> Long = System::currentTimeMillis
) {
    private data class ActiveWildEncounter(
        val areaKey: String,
        val speciesId: Int,
        val initialPartyKeys: Set<String>,
        val initialOccupiedSlots: Int,
        val wasDupeSkip: Boolean
    )

    private var _state: NuzlockeRunState = initialState
    val state: NuzlockeRunState get() = _state

    private val observedAlivePartyKeys = mutableSetOf<String>()
    private var activeWildEncounter: ActiveWildEncounter? = null

    /**
     * Replaces the tracked state (e.g., on ROM switch) and drops all transient battle/session memory.
     */
    fun loadState(nextState: NuzlockeRunState) {
        _state = nextState
        resetTransientState()
    }

    /**
     * Drops transient observation tracking (active battle, alive-key cache) without altering the
     * persisted [NuzlockeRunState]. Called on core reset, state load, ROM unload, or trust drop.
     */
    fun resetTransientState() {
        observedAlivePartyKeys.clear()
        activeWildEncounter = null
    }

    /**
     * Processes one verified live observation snapshot.
     *
     * @return true if [state] changed and should be published/persisted.
     */
    fun onObservation(
        party: List<ParsedPokemon>,
        enemyParty: List<ParsedPokemon?>,
        activeEnemyIndex: Int,
        battleKind: NuzlockeBattleKind,
        inBattle: Boolean,
        location: PlayerLocation?,
        areaName: String?,
        speciesNameResolver: (Int) -> String = { "#$it" }
    ): Boolean {
        if (!_state.enabled) return false

        var changed = false
        val occupiedPartySlots = party.count { it.isValid && !it.isEmpty }
        val validParty = party.filter { it.isValid && !it.isEmpty && !it.isEgg && it.species > 0 }
        val resolvedArea = resolveAreaName(location, areaName)

        // 1. Permanent fainting: require an observed alive (>0 HP) -> fainted (0 HP) transition.
        for (mon in validParty) {
            val key = monIdentityKey(mon)
            if (key.isEmpty()) continue
            if (mon.maxHp <= 0) continue

            if (mon.currentHp > 0) {
                observedAlivePartyKeys += key
            } else {
                val wasAlive = observedAlivePartyKeys.remove(key)
                if (wasAlive && key !in _state.fallen) {
                    val speciesName = resolveSpeciesName(mon.species, speciesNameResolver)
                    val nickname = mon.nickname.ifBlank { speciesName }
                    val fallenMon = FallenMon(
                        monKey = key,
                        speciesId = mon.species,
                        speciesName = speciesName,
                        nickname = nickname,
                        level = mon.level,
                        areaName = resolvedArea ?: "Unknown Area",
                        fallenAtMs = clockMs()
                    )
                    val updatedFallen = LinkedHashMap(_state.fallen)
                    updatedFallen[key] = fallenMon
                    _state = _state.copy(fallen = updatedFallen)
                    changed = true
                }
            }
        }

        // 2. Wild encounter capture/miss resolution when an active wild battle ends.
        val pending = activeWildEncounter
        if (pending != null && !inBattle) {
            // Fail closed on empty/transient party reads if the player had a party when the battle started.
            if (validParty.isEmpty() && pending.initialPartyKeys.isNotEmpty()) {
                return changed
            }
            activeWildEncounter = null
            if (!pending.wasDupeSkip) {
                val existing = _state.encounters[pending.areaKey]
                if (existing != null &&
                    existing.outcome == EncounterOutcome.MET &&
                    existing.speciesId == pending.speciesId
                ) {
                    val currentKeys = validParty.mapNotNull { monIdentityKey(it).ifEmpty { null } }.toSet()
                    val caughtNewMon = validParty.any { mon ->
                        val key = monIdentityKey(mon)
                        key.isNotEmpty() && key !in pending.initialPartyKeys && mon.species == pending.speciesId
                    }
                    // When the party was full (>= 6 slots) a catch goes directly to the PC, and when
                    // battle-exit or party continuity cannot be established, leave the outcome PENDING
                    // for manual resolution instead of falsely marking MISSED.
                    val resolvedOutcome = when {
                        caughtNewMon -> EncounterOutcome.CAUGHT
                        pending.initialOccupiedSlots !in 1..5 -> EncounterOutcome.PENDING
                        battleKind != NuzlockeBattleKind.NONE -> EncounterOutcome.PENDING
                        !currentKeys.containsAll(pending.initialPartyKeys) -> EncounterOutcome.PENDING
                        else -> EncounterOutcome.MISSED
                    }
                    val updatedEncounters = LinkedHashMap(_state.encounters)
                    updatedEncounters[pending.areaKey] = existing.copy(outcome = resolvedOutcome)
                    _state = _state.copy(encounters = updatedEncounters)
                    changed = true
                }
            }
        }

        // 3. First wild encounter detection on entry into a verified wild single battle.
        if (inBattle &&
            activeWildEncounter == null &&
            battleKind == NuzlockeBattleKind.WILD_SINGLE &&
            location != null && location.isValid
        ) {
            val wildEnemy = enemyParty.getOrNull(activeEnemyIndex)
                ?.takeIf { it.isValid && !it.isEmpty && !it.isEgg && it.species > 0 }
            if (wildEnemy != null) {
                val areaKey = areaKeyOf(location)
                val existing = _state.encounters[areaKey]
                val areaOpen = existing == null || existing.outcome == EncounterOutcome.DUPES_SKIPPED
                if (areaOpen) {
                    val isDupe = _state.dupesClause && isDupeSpecies(wildEnemy.species, validParty)
                    val outcome = if (isDupe) EncounterOutcome.DUPES_SKIPPED else EncounterOutcome.MET
                    val encounter = AreaEncounter(
                        areaKey = areaKey,
                        areaName = resolvedArea ?: "Area $areaKey",
                        speciesId = wildEnemy.species,
                        speciesName = resolveSpeciesName(wildEnemy.species, speciesNameResolver),
                        level = wildEnemy.level,
                        outcome = outcome,
                        recordedAtMs = clockMs()
                    )
                    val updatedEncounters = LinkedHashMap(_state.encounters)
                    updatedEncounters[areaKey] = encounter
                    _state = _state.copy(encounters = updatedEncounters)
                    activeWildEncounter = ActiveWildEncounter(
                        areaKey = areaKey,
                        speciesId = wildEnemy.species,
                        initialPartyKeys = validParty.mapNotNull { monIdentityKey(it).ifEmpty { null } }.toSet(),
                        initialOccupiedSlots = occupiedPartySlots,
                        wasDupeSkip = isDupe
                    )
                    changed = true
                }
            }
        }

        return changed
    }

    fun setEnabled(enabled: Boolean): Boolean {
        if (_state.enabled == enabled) return false
        _state = _state.copy(enabled = enabled)
        resetTransientState()
        return true
    }

    fun setDupesClause(enabled: Boolean): Boolean {
        if (_state.dupesClause == enabled) return false
        _state = _state.copy(dupesClause = enabled)
        return true
    }

    fun setLevelCap(levelCap: Int?): Boolean {
        val normalized = levelCap?.takeIf { it > 0 }?.coerceAtMost(100)
        if (_state.levelCap == normalized) return false
        _state = _state.copy(levelCap = normalized)
        return true
    }

    fun overrideEncounterOutcome(areaKey: String, outcome: EncounterOutcome): Boolean {
        val existing = _state.encounters[areaKey] ?: return false
        if (existing.outcome == outcome) return false
        if (activeWildEncounter?.areaKey == areaKey) {
            activeWildEncounter = null
        }
        val updated = LinkedHashMap(_state.encounters)
        updated[areaKey] = existing.copy(outcome = outcome)
        _state = _state.copy(encounters = updated)
        return true
    }

    fun reviveFallen(monKey: String): Boolean {
        if (monKey !in _state.fallen) return false
        val updated = LinkedHashMap(_state.fallen)
        updated.remove(monKey)
        // Do not add monKey to observedAlivePartyKeys; it must be observed > 0 HP before fainting again.
        observedAlivePartyKeys.remove(monKey)
        _state = _state.copy(fallen = updated)
        return true
    }

    fun resetRun(): Boolean {
        val changed = _state.encounters.isNotEmpty() || _state.fallen.isNotEmpty()
        resetTransientState()
        if (!changed) return false
        _state = _state.copy(encounters = emptyMap(), fallen = emptyMap())
        return true
    }

    private fun isDupeSpecies(speciesId: Int, validParty: List<ParsedPokemon>): Boolean {
        if (validParty.any { it.species == speciesId }) return true
        if (_state.encounters.values.any { it.speciesId == speciesId && it.outcome == EncounterOutcome.CAUGHT }) {
            return true
        }
        if (_state.fallen.values.any { it.speciesId == speciesId }) return true
        return false
    }

    private fun resolveSpeciesName(speciesId: Int, resolver: (Int) -> String): String =
        resolver(speciesId).ifBlank { "#$speciesId" }

    private fun resolveAreaName(location: PlayerLocation?, areaName: String?): String? {
        val trimmed = areaName?.trim()
        if (!trimmed.isNullOrEmpty()) return trimmed
        return if (location != null && location.isValid) "Area ${areaKeyOf(location)}" else null
    }
}
