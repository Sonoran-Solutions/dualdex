package com.dualdex.companion

import android.net.Uri
import com.dualdex.emulator.LibretroCoreCoordinator
import com.dualdex.emulator.RomIdentity
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.pokemon.LocationResolution
import com.dualdex.pokemon.LocationStrategy
import com.dualdex.pokemon.LocationUnavailableReason
import com.dualdex.pokemon.PlayerLocation
import com.dualdex.pokemon.RegionMapDatabase
import com.dualdex.pokemon.RegionMapSection
import com.dualdex.romhack.RomCompatibility
import com.dualdex.romhack.RomHackProfile
import com.dualdex.romhack.RuntimeRomTrust
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Re-export of [com.dualdex.pokemon.hns.BattlerRuntimeObservation] for backward compatibility. */
typealias BattlerRuntimeObservation = com.dualdex.pokemon.hns.BattlerRuntimeObservation

data class RomItem(
    val title: String,
    val fileName: String,
    val uri: Uri,
    val sizeFormatted: String
)

enum class CompanionTab(val title: String) {
    // Existing screen identities remain stable for direct navigation callers.
    HOME("Library"),
    PARTY("Party"),
    MAP("Map"),
    CALC("Calculator"),
    TYPES("Type Matchups"),
    DOCS("Docs"),
    CHEATS("Cheats"),
    SAVES("Saves"),
    ASSISTANT("Assistant"),
    BATTLE("Battle"),
    SETTINGS("Settings"),
    MORE("More")
}

class CompanionViewModel(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    var coreCoordinator: LibretroCoreCoordinator = LibretroCoreCoordinator.defaultInstance
) {
    private val _selectedTab = MutableStateFlow(CompanionTab.HOME)
    val selectedTab: StateFlow<CompanionTab> = _selectedTab.asStateFlow()

    private val _scannedRoms = MutableStateFlow<List<RomItem>>(emptyList())
    val scannedRoms: StateFlow<List<RomItem>> = _scannedRoms.asStateFlow()

    private val _playerParty = MutableStateFlow<List<ParsedPokemon>>(emptyList())
    val playerParty: StateFlow<List<ParsedPokemon>> = _playerParty.asStateFlow()

    private val _enemyParty = MutableStateFlow<List<ParsedPokemon>>(emptyList())
    val enemyParty: StateFlow<List<ParsedPokemon>> = _enemyParty.asStateFlow()

    private val _selectedMemberIndex = MutableStateFlow(0)
    val selectedMemberIndex: StateFlow<Int> = _selectedMemberIndex.asStateFlow()

    private val _activeEnemyMemberIndex = MutableStateFlow(-1)
    val activeEnemyMemberIndex: StateFlow<Int> = _activeEnemyMemberIndex.asStateFlow()

    /**
     * Authoritative active-opponent resolution, including the cases where no opponent may be
     * named (transition, unknown, doubles). A -1 index is always accompanied by a state that
     * explains why, so the UI can be honest instead of showing a guessed enemy.
     */
    private val _activeEnemyResolution = MutableStateFlow(com.dualdex.battle.ActiveEnemyResolution())
    val activeEnemyResolution: StateFlow<com.dualdex.battle.ActiveEnemyResolution> =
        _activeEnemyResolution.asStateFlow()

    private val _activePlayerBattlerIndex = MutableStateFlow(-1)
    val activePlayerBattlerIndex: StateFlow<Int> = _activePlayerBattlerIndex.asStateFlow()

    private val _isInBattle = MutableStateFlow(false)
    val isInBattle: StateFlow<Boolean> = _isInBattle.asStateFlow()

    private val _battlePresence = MutableStateFlow(com.dualdex.battle.BattlePresence.UNKNOWN)
    val battlePresence: StateFlow<com.dualdex.battle.BattlePresence> = _battlePresence.asStateFlow()

    private val _isBattleAutoOpenEnabled = MutableStateFlow(true)
    val isBattleAutoOpenEnabled: StateFlow<Boolean> = _isBattleAutoOpenEnabled.asStateFlow()

    private val _isInteractiveBattleControlsEnabled = MutableStateFlow(false)
    val isInteractiveBattleControlsEnabled: StateFlow<Boolean> = _isInteractiveBattleControlsEnabled.asStateFlow()

    private val _activeGameId = MutableStateFlow(0)
    val activeGameId: StateFlow<Int> = _activeGameId.asStateFlow()

    private val _activeRomTitle = MutableStateFlow("")
    val activeRomTitle: StateFlow<String> = _activeRomTitle.asStateFlow()

    private val _activeRomIdentity = MutableStateFlow<RomIdentity?>(null)
    val activeRomIdentity: StateFlow<RomIdentity?> = _activeRomIdentity.asStateFlow()

    private val _activeProfile = MutableStateFlow(RomHackProfile.UNSUPPORTED)
    val activeProfile: StateFlow<RomHackProfile> = _activeProfile.asStateFlow()

    private val _runtimeRomTrust = MutableStateFlow(RuntimeRomTrust())
    val runtimeRomTrust: StateFlow<RuntimeRomTrust> = _runtimeRomTrust.asStateFlow()

    private val _playerStatStages = MutableStateFlow(com.dualdex.battle.StatStages())
    val playerStatStages: StateFlow<com.dualdex.battle.StatStages> = _playerStatStages.asStateFlow()

    private val _enemyStatStages = MutableStateFlow(com.dualdex.battle.StatStages())
    val enemyStatStages: StateFlow<com.dualdex.battle.StatStages> = _enemyStatStages.asStateFlow()

    private val _battleUiSnapshot = MutableStateFlow(com.dualdex.battle.BattleUiSnapshot())
    val battleUiSnapshot: StateFlow<com.dualdex.battle.BattleUiSnapshot> = _battleUiSnapshot.asStateFlow()

    var battleInputAdapter: com.dualdex.battle.BattleInputAdapter = com.dualdex.battle.BattleInputAdapter(
        stateReader = { _battleUiSnapshot.value }
    )

    val activeGameDataPack: com.dualdex.pokemon.GameDataPack
        get() = com.dualdex.pokemon.GameDataPackRegistry.getForProfile(_activeProfile.value)

    private val _playerLocation = MutableStateFlow<PlayerLocation?>(null)
    val playerLocation: StateFlow<PlayerLocation?> = _playerLocation.asStateFlow()

    private val _resolvedLocation = MutableStateFlow<RegionMapSection?>(null)
    val resolvedLocation: StateFlow<RegionMapSection?> = _resolvedLocation.asStateFlow()

    /**
     * Why [resolvedLocation] is null, when it is. Null means either "resolved" or
     * "not attempted yet"; [LocationUnavailableReason] lets the UI say something
     * true instead of showing a default location.
     */
    private val _locationUnavailableReason = MutableStateFlow<LocationUnavailableReason?>(null)
    val locationUnavailableReason: StateFlow<LocationUnavailableReason?> =
        _locationUnavailableReason.asStateFlow()

    /**
     * The map table selected from the active profile. This is the memory-layout
     * selection: it is never changed by which region the user is browsing.
     */
    private val _locationStrategy = MutableStateFlow(LocationStrategy.UNVERIFIED)
    val locationStrategy: StateFlow<LocationStrategy> = _locationStrategy.asStateFlow()

    /**
     * Runtime H&S 2.0.5 challenge settings (issue #9). Observation state only: it never
     * authorizes a calculator capability by itself and carries no source defaults. Non-UNAVAILABLE
     * only while the running ROM is exactly trusted, because it is read inside the same
     * [RuntimeRomTrust.mayReadLiveMemory] gate as every other live observation.
     */
    private val _challengeSettings =
        MutableStateFlow<com.dualdex.pokemon.hns.HnsChallengeSettingsSnapshot?>(null)
    val challengeSettings: StateFlow<com.dualdex.pokemon.hns.HnsChallengeSettingsSnapshot?> =
        _challengeSettings.asStateFlow()

    private val _playerBattlerState = MutableStateFlow<BattlerRuntimeObservation?>(null)
    val playerBattlerState: StateFlow<BattlerRuntimeObservation?> = _playerBattlerState.asStateFlow()

    private val _enemyBattlerState = MutableStateFlow<BattlerRuntimeObservation?>(null)
    val enemyBattlerState: StateFlow<BattlerRuntimeObservation?> = _enemyBattlerState.asStateFlow()

    private var pollingJob: Job? = null
    private val battlePresenceStabilizer = com.dualdex.battle.BattlePresenceStabilizer()

    private val playerPartyStabilizer = LiveObservationStabilizer()
    private val enemyPartyStabilizer = LiveObservationStabilizer()
    private val playerLocationStabilizer = LiveObservationStabilizer()

    fun selectTab(tab: CompanionTab) {
        _selectedTab.value = tab
    }

    fun setScannedRoms(roms: List<RomItem>) {
        _scannedRoms.value = roms
    }

    fun selectMember(index: Int) {
        if (index in 0..5) {
            _selectedMemberIndex.value = index
        }
    }

    /**
     * Re-derive the active location strategy from the typed profile.
     *
     * A strategy change invalidates every live observation first, so a location
     * read with the previous game's map table can never be presented under the
     * new game. This is the only place the strategy changes.
     */
    private fun publishLocationStrategy() {
        val next = LocationStrategy.forProfile(_activeProfile.value)
        if (_locationStrategy.value != next) {
            clearLiveMemoryObservations()
            _locationStrategy.value = next
        }
    }

    fun setRomInfo(gameId: Int, romTitle: String, profile: RomHackProfile? = null) {
        _activeGameId.value = gameId
        _activeRomTitle.value = romTitle
        if (profile != null) {
            _activeProfile.value = profile
            _activeGameId.value = profile.gameId
        }
        // No compatibility evidence was supplied, so live memory stays disabled.
        _runtimeRomTrust.value = RuntimeRomTrust()
        publishLocationStrategy()
        clearLiveMemoryObservations()
    }

    fun setRomIdentity(identity: RomIdentity?) {
        _activeRomIdentity.value = identity
        _activeRomTitle.value = identity?.displayName.orEmpty()
        _runtimeRomTrust.value = RuntimeRomTrust()
        if (identity == null) {
            _activeProfile.value = RomHackProfile.UNSUPPORTED
            _activeGameId.value = 0
        }
        publishLocationStrategy()
        clearLiveMemoryObservations()
    }

    /**
     * Publishes a completed ROM switch. Observations are cleared before the new identity is
     * published so a consumer can never see the new ROM paired with data read from the previous one.
     */
    fun setRomSession(compatibility: RomCompatibility, identity: RomIdentity) {
        clearLiveMemoryObservations()

        _activeProfile.value = compatibility.profile
        _activeGameId.value = compatibility.profile.gameId
        _activeRomIdentity.value = identity
        _activeRomTitle.value = identity.displayName
        _runtimeRomTrust.value = RuntimeRomTrust.from(compatibility, identity.sha256)

        // The strategy follows the newly published profile, and publishing it
        // drops observations taken under the previous one.
        publishLocationStrategy()

        // A ROM that is not exactly verified never authorizes memory parsing, even if a poller was
        // already running. Re-assert the cleared state after publishing the new trust value.
        if (!_runtimeRomTrust.value.mayReadLiveMemory) {
            clearLiveMemoryObservations()
        }
    }

    /**
     * Enters the clean "no ROM" state: identity, trust, and all live observations are dropped.
     * Used after a failed load that already tore the previous session down.
     */
    fun clearRomSession() {
        clearLiveMemoryObservations()
        battlePresenceStabilizer.reset()
        _runtimeRomTrust.value = RuntimeRomTrust()
        _activeRomIdentity.value = null
        _activeRomTitle.value = ""
        _activeProfile.value = RomHackProfile.UNSUPPORTED
        _activeGameId.value = 0
        publishLocationStrategy()
    }

    fun setProfile(profile: RomHackProfile) {
        _activeProfile.value = profile
        _activeGameId.value = profile.gameId
        _activeRomTitle.value = profile.name
        _runtimeRomTrust.value = RuntimeRomTrust()
        publishLocationStrategy()
        clearLiveMemoryObservations()
    }

    fun startPolling(intervalMs: Long = 200L) {
        if (pollingJob?.isActive == true) return

        pollingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    // Universal capability gate: without an exact-verified ROM the poller must not
                    // invoke a single profile-dependent reader. Whatever was observed earlier is
                    // invalidated immediately rather than left on screen as stale data.
                    pollTick()
                } catch (e: Throwable) {
                    android.util.Log.e("DualDex_Companion", "Error in memory poller: ${e.message}", e)
                }
                delay(intervalMs)
            }
        }
    }

    /**
     * Executes one polling step synchronously without delay.
     * Useful for deterministic testing of the compatibility gate and stabilizers.
     */
    internal fun pollTick() {
        if (_runtimeRomTrust.value.mayReadLiveMemory) {
            pollVerifiedRomMemory()
        } else {
            clearLiveMemoryObservations()
        }
    }

    /**
     * Reads profile-dependent memory. Only reachable while [RuntimeRomTrust.mayReadLiveMemory] holds,
     * so every offset used here belongs to the verified layout of the running ROM.
     */
    internal fun pollVerifiedRomMemory() {
        val gameId = _activeGameId.value

        val playerPartyRaw = coreCoordinator.readPartyFromCore(gameId)
        val playerList = playerPartyRaw?.filterNotNull()?.filter { !it.isEmpty && it.isValid }
        if (!playerList.isNullOrEmpty()) {
            playerPartyStabilizer.onValidRead()
            if (playerList != _playerParty.value) {
                _playerParty.value = playerList
            }
        } else if (playerPartyStabilizer.onInvalidRead() == LiveObservationDecision.CLEAR) {
            // Player party previously kept stale members here; sustained invalid reads must clear it.
            if (_playerParty.value.isNotEmpty()) {
                _playerParty.value = emptyList()
            }
            if (_selectedMemberIndex.value != 0) {
                _selectedMemberIndex.value = 0
            }
        }

        val enemyPartyRaw = coreCoordinator.readEnemyPartyFromCore(gameId)
        if (enemyPartyRaw == null) {
            // A failed read is not evidence that the battle ended: debounce it.
            if (enemyPartyStabilizer.onInvalidRead() == LiveObservationDecision.CLEAR &&
                _enemyParty.value.isNotEmpty()
            ) {
                _enemyParty.value = emptyList()
            }
        } else {
            enemyPartyStabilizer.onValidRead()
            val enemyList = enemyPartyRaw.filterNotNull().filter { !it.isEmpty && it.isValid }
            if (enemyList != _enemyParty.value) {
                _enemyParty.value = enemyList
            }
        }

        val presence = com.dualdex.battle.BattlePresence.fromNativeCode(
            coreCoordinator.readBattlePresence(gameId)
        )
        _battlePresence.value = presence
        val inBattle = battlePresenceStabilizer.update(presence)
        if (inBattle != _isInBattle.value) {
            _isInBattle.value = inBattle
        }
        if (inBattle) {
            val activeSlot = coreCoordinator.getActiveBattlerSlot(gameId)
            if (activeSlot in _playerParty.value.indices) {
                _activePlayerBattlerIndex.value = activeSlot
            } else {
                _activePlayerBattlerIndex.value = -1
            }
            // The opponent comes from authoritative battler state only. A fainted-but-not-yet-
            // replaced opponent is a transition: the slot is withheld until the announcement
            // resolves, which is a temporary UNKNOWN rather than the previous enemy.
            val enemyResolution = coreCoordinator.resolveActiveEnemy(gameId)
            val resolvedSlot = enemyResolution.partySlot
                ?.takeIf { !enemyResolution.isFainted && it in _enemyParty.value.indices }
                ?: -1
            if (resolvedSlot != _activeEnemyMemberIndex.value) {
                _activeEnemyMemberIndex.value = resolvedSlot
            }
            val publicResolution =
                if (resolvedSlot < 0 && enemyResolution.state == com.dualdex.battle.ActiveEnemyState.SLOT) {
                    enemyResolution.copy(
                        state = com.dualdex.battle.ActiveEnemyState.UNKNOWN,
                        partySlot = null
                    )
                } else {
                    enemyResolution
                }
            if (publicResolution != _activeEnemyResolution.value) {
                _activeEnemyResolution.value = publicResolution
            }

            val pStages = coreCoordinator.readBattleStatStages(gameId, 0)
            _playerStatStages.value = com.dualdex.battle.StatStages.fromRawArray(pStages)

            val eStages = coreCoordinator.readBattleStatStages(gameId, 1)
            _enemyStatStages.value = com.dualdex.battle.StatStages.fromRawArray(eStages)

            val uiCode = coreCoordinator.readBattleUiState(gameId)
            _battleUiSnapshot.value = com.dualdex.battle.BattleInteractionPolicy.evaluate(
                profile = _activeProfile.value,
                runtimeTrust = _runtimeRomTrust.value,
                userEnabled = _isInteractiveBattleControlsEnabled.value,
                inBattle = inBattle,
                uiState = com.dualdex.battle.BattleUiState.fromNativeCode(uiCode)
            )
        } else {
            if (_activeEnemyMemberIndex.value != -1) {
                _activeEnemyMemberIndex.value = -1
            }
            if (_activeEnemyResolution.value != com.dualdex.battle.ActiveEnemyResolution()) {
                _activeEnemyResolution.value = com.dualdex.battle.ActiveEnemyResolution()
            }
            _activePlayerBattlerIndex.value = -1
            _playerStatStages.value = com.dualdex.battle.StatStages()
            _enemyStatStages.value = com.dualdex.battle.StatStages()
            _battleUiSnapshot.value = com.dualdex.battle.BattleUiSnapshot()
            // Live battler ability/types die with the battle: nothing is retained
            // through teardown, and the reader cannot serve stale gBattleMons words.
            if (_playerBattlerState.value != null) _playerBattlerState.value = null
            if (_enemyBattlerState.value != null) _enemyBattlerState.value = null
        }

        val loc = coreCoordinator.readPlayerLocation(gameId)
        if (loc != null && loc.isValid) {
            playerLocationStabilizer.onValidRead()
            if (loc != _playerLocation.value) {
                publishResolvedLocation(loc)
            }
        } else if (playerLocationStabilizer.onInvalidRead() == LiveObservationDecision.CLEAR) {
            // Location previously retained its last known value indefinitely on failed reads.
            clearResolvedLocation(LocationUnavailableReason.INVALID_READ)
        }

        // Challenge settings are save-state, not battle state: they only exist for the exact
        // trusted H&S 2.0.5 layout (the native reader itself fails closed for every other game).
        // A failed read is published as UNAVAILABLE — never replaced with a source default, and
        // never carried across a ROM/profile switch because clearLiveMemoryObservations() runs on
        // every session change.
        val challengeSnapshot = coreCoordinator.readChallengeSettings(gameId)
        val nextChallenge =
            if (challengeSnapshot.status != com.dualdex.pokemon.hns.HnsChallengeSettingsStatus.UNAVAILABLE) {
                challengeSnapshot
            } else {
                null
            }
        if (nextChallenge != _challengeSettings.value) {
            _challengeSettings.value = nextChallenge
        }

        // Live battler ability + effective types + current held item (issue #9): observation
        // only. The read is gated on the authoritative lifecycle/battler machinery in the native
        // reader, so it fails closed on every trust/lifecycle failure and never retains a
        // previous battler's state. The ability ID is named against the active data pack's
        // pinned catalogue and the item ID against the exact H&S item catalogue; neither
        // catalogue is the source of the observation and neither enables calculator capability
        // here.
        publishBattlerRuntimeState(
            gameId,
            com.dualdex.pokemon.hns.HnsBattlerRole.PLAYER,
            _playerBattlerState
        )
        publishBattlerRuntimeState(
            gameId,
            com.dualdex.pokemon.hns.HnsBattlerRole.OPPONENT,
            _enemyBattlerState
        )
    }

    /** Reads one role's live battler state and publishes it with its catalogue identity. */
    private fun publishBattlerRuntimeState(
        gameId: Int,
        role: Int,
        target: MutableStateFlow<BattlerRuntimeObservation?>
    ) {
        val state = coreCoordinator.readBattlerRuntimeState(gameId, role)
        val next =
            if (state.status.isObservation) {
                BattlerRuntimeObservation(
                    state = state,
                    abilityIdentity = state.resolveAbilityIdentity(activeGameDataPack),
                    itemIdentity = state.resolveItemIdentity()
                )
            } else {
                null
            }
        if (next != target.value) {
            target.value = next
        }
    }

    /**
     * Resolve a valid raw read through the active location strategy.
     *
     * The strategy is chosen from the profile, never from the raw values, so an
     * unknown map id can only produce "unavailable" -- never another game's
     * location and never the previously shown one.
     */
    private fun publishResolvedLocation(loc: PlayerLocation) {
        _playerLocation.value = loc
        val resolution: LocationResolution =
            RegionMapDatabase.resolveLocationDetailed(_locationStrategy.value, loc)
        _resolvedLocation.value = resolution.section
        _locationUnavailableReason.value = resolution.reason
    }

    /** Drops live location state so no stale marker or name can survive. */
    private fun clearResolvedLocation(reason: LocationUnavailableReason? = null) {
        if (_playerLocation.value != null) _playerLocation.value = null
        if (_resolvedLocation.value != null) _resolvedLocation.value = null
        _locationUnavailableReason.value = reason
    }

    /**
     * Drops every memory-derived observation and re-arms the invalid-read stabilizers.
     *
     * Idempotent: assigning an equal value to a StateFlow does not re-emit, so callers may invoke
     * this on every poll tick while the compatibility gate is closed.
     */
    fun clearLiveMemoryObservations() {
        if (_playerParty.value.isNotEmpty()) _playerParty.value = emptyList()
        if (_enemyParty.value.isNotEmpty()) _enemyParty.value = emptyList()
        if (_selectedMemberIndex.value != 0) _selectedMemberIndex.value = 0
        if (_activePlayerBattlerIndex.value != -1) _activePlayerBattlerIndex.value = -1
        if (_activeEnemyMemberIndex.value != -1) _activeEnemyMemberIndex.value = -1
        if (_activeEnemyResolution.value != com.dualdex.battle.ActiveEnemyResolution()) {
            _activeEnemyResolution.value = com.dualdex.battle.ActiveEnemyResolution()
        }
        if (_isInBattle.value) _isInBattle.value = false
        if (_battlePresence.value != com.dualdex.battle.BattlePresence.UNKNOWN) {
            _battlePresence.value = com.dualdex.battle.BattlePresence.UNKNOWN
        }
        if (!_playerStatStages.value.isNeutral) _playerStatStages.value = com.dualdex.battle.StatStages()
        if (!_enemyStatStages.value.isNeutral) _enemyStatStages.value = com.dualdex.battle.StatStages()
        if (_battleUiSnapshot.value != com.dualdex.battle.BattleUiSnapshot()) {
            _battleUiSnapshot.value = com.dualdex.battle.BattleUiSnapshot()
        }
        if (_playerBattlerState.value != null) _playerBattlerState.value = null
        if (_enemyBattlerState.value != null) _enemyBattlerState.value = null
        _playerLocation.value = null
        _resolvedLocation.value = null
        _locationUnavailableReason.value = null
        _challengeSettings.value = null
        playerPartyStabilizer.reset()
        enemyPartyStabilizer.reset()
        playerLocationStabilizer.reset()
        battlePresenceStabilizer.reset()
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun setBattleAutoOpenEnabled(enabled: Boolean) {
        _isBattleAutoOpenEnabled.value = enabled
    }

    fun setInteractiveBattleControlsEnabled(enabled: Boolean) {
        _isInteractiveBattleControlsEnabled.value = enabled
    }

    fun setActiveEnemyMemberIndex(index: Int) {
        // Manual selection (parity with the manual party editor). The authoritative resolution is
        // marked SLOT so the UI treats an explicitly chosen enemy as chosen, not as tracked.
        _activeEnemyMemberIndex.value = index
        _activeEnemyResolution.value = if (index in 0..5) {
            com.dualdex.battle.ActiveEnemyResolution(
                state = com.dualdex.battle.ActiveEnemyState.SLOT,
                partySlot = index
            )
        } else {
            com.dualdex.battle.ActiveEnemyResolution()
        }
    }

    fun setActivePlayerBattlerIndex(index: Int) {
        _activePlayerBattlerIndex.value = index
    }

    fun setIsInBattle(inBattle: Boolean) {
        _isInBattle.value = inBattle
        if (!inBattle) {
            _activeEnemyMemberIndex.value = -1
            _activeEnemyResolution.value = com.dualdex.battle.ActiveEnemyResolution()
            _activePlayerBattlerIndex.value = -1
            _playerStatStages.value = com.dualdex.battle.StatStages()
            _enemyStatStages.value = com.dualdex.battle.StatStages()
            _battleUiSnapshot.value = com.dualdex.battle.BattleUiSnapshot()
        }
    }

    fun updateManualParty(party: List<ParsedPokemon>) {
        _playerParty.value = party
    }

    fun updateEnemyParty(party: List<ParsedPokemon>) {
        _enemyParty.value = party
        val inBattle = party.isNotEmpty()
        _isInBattle.value = inBattle
        if (!inBattle) {
            _activeEnemyMemberIndex.value = -1
            _activeEnemyResolution.value = com.dualdex.battle.ActiveEnemyResolution()
            _activePlayerBattlerIndex.value = -1
            _playerStatStages.value = com.dualdex.battle.StatStages()
            _enemyStatStages.value = com.dualdex.battle.StatStages()
            _battleUiSnapshot.value = com.dualdex.battle.BattleUiSnapshot()
        }
    }

    /**
     * Publish a player location directly (test/UI injection seam).
     *
     * Kept consistent with the polling path: a valid location is resolved through
     * the active strategy, and an absent or invalid one clears live location state
     * instead of leaving a stale value behind.
     */
    fun updatePlayerLocation(location: PlayerLocation?) {
        if (location == null || !location.isValid) {
            clearResolvedLocation(LocationUnavailableReason.INVALID_READ)
            return
        }
        publishResolvedLocation(location)
    }

    fun updatePlayerStatStages(stages: com.dualdex.battle.StatStages) {
        _playerStatStages.value = stages
    }

    fun updateEnemyStatStages(stages: com.dualdex.battle.StatStages) {
        _enemyStatStages.value = stages
    }

    fun updateBattleUiSnapshot(snapshot: com.dualdex.battle.BattleUiSnapshot) {
        _battleUiSnapshot.value = snapshot
    }
}
