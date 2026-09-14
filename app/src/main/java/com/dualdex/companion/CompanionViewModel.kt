package com.dualdex.companion

import android.net.Uri
import com.dualdex.emulator.LibretroCoreCoordinator
import com.dualdex.emulator.RomIdentity
import com.dualdex.pokemon.ParsedPokemon
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
        get() = com.dualdex.pokemon.GameDataPackRegistry.getForProfile(
            engine = _activeProfile.value.engine,
            hasPhysSpecSplit = _activeProfile.value.hasPhysSpecSplit,
            customPackId = _activeProfile.value.gameDataPackId
        )

    private val _playerLocation = MutableStateFlow<PlayerLocation?>(null)
    val playerLocation: StateFlow<PlayerLocation?> = _playerLocation.asStateFlow()

    private val _resolvedLocation = MutableStateFlow<RegionMapSection?>(null)
    val resolvedLocation: StateFlow<RegionMapSection?> = _resolvedLocation.asStateFlow()

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

    fun setRomInfo(gameId: Int, romTitle: String, profile: RomHackProfile? = null) {
        _activeGameId.value = gameId
        _activeRomTitle.value = romTitle
        if (profile != null) {
            _activeProfile.value = profile
            _activeGameId.value = profile.gameId
        }
        // No compatibility evidence was supplied, so live memory stays disabled.
        _runtimeRomTrust.value = RuntimeRomTrust()
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
    }

    fun setProfile(profile: RomHackProfile) {
        _activeProfile.value = profile
        _activeGameId.value = profile.gameId
        _activeRomTitle.value = profile.name
        _runtimeRomTrust.value = RuntimeRomTrust()
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
            val enemyActiveSlot = coreCoordinator.getActiveEnemyBattlerSlot(gameId)
            val resolvedSlot = if (enemyActiveSlot in _enemyParty.value.indices) enemyActiveSlot else -1
            if (resolvedSlot != _activeEnemyMemberIndex.value) {
                _activeEnemyMemberIndex.value = resolvedSlot
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
            _activePlayerBattlerIndex.value = -1
            _playerStatStages.value = com.dualdex.battle.StatStages()
            _enemyStatStages.value = com.dualdex.battle.StatStages()
            _battleUiSnapshot.value = com.dualdex.battle.BattleUiSnapshot()
        }

        val loc = coreCoordinator.readPlayerLocation(gameId)
        if (loc != null && loc.isValid) {
            playerLocationStabilizer.onValidRead()
            if (loc != _playerLocation.value) {
                _playerLocation.value = loc
                val isHns = _activeProfile.value.id == "heart_and_soul" ||
                    _activeRomTitle.value.contains("HEART", ignoreCase = true)
                _resolvedLocation.value = RegionMapDatabase.resolveLocationOrNull(gameId, isHns, loc)
            }
        } else if (playerLocationStabilizer.onInvalidRead() == LiveObservationDecision.CLEAR) {
            // Location previously retained its last known value indefinitely on failed reads.
            if (_playerLocation.value != null || _resolvedLocation.value != null) {
                _playerLocation.value = null
                _resolvedLocation.value = null
            }
        }
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
        if (_isInBattle.value) _isInBattle.value = false
        if (_battlePresence.value != com.dualdex.battle.BattlePresence.UNKNOWN) {
            _battlePresence.value = com.dualdex.battle.BattlePresence.UNKNOWN
        }
        if (!_playerStatStages.value.isNeutral) _playerStatStages.value = com.dualdex.battle.StatStages()
        if (!_enemyStatStages.value.isNeutral) _enemyStatStages.value = com.dualdex.battle.StatStages()
        if (_battleUiSnapshot.value != com.dualdex.battle.BattleUiSnapshot()) {
            _battleUiSnapshot.value = com.dualdex.battle.BattleUiSnapshot()
        }
        _playerLocation.value = null
        _resolvedLocation.value = null
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
        _activeEnemyMemberIndex.value = index
    }

    fun setActivePlayerBattlerIndex(index: Int) {
        _activePlayerBattlerIndex.value = index
    }

    fun setIsInBattle(inBattle: Boolean) {
        _isInBattle.value = inBattle
        if (!inBattle) {
            _activeEnemyMemberIndex.value = -1
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
            _activePlayerBattlerIndex.value = -1
            _playerStatStages.value = com.dualdex.battle.StatStages()
            _enemyStatStages.value = com.dualdex.battle.StatStages()
            _battleUiSnapshot.value = com.dualdex.battle.BattleUiSnapshot()
        }
    }

    fun updatePlayerLocation(location: PlayerLocation?) {
        _playerLocation.value = location
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
