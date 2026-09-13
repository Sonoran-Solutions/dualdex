package com.dualdex.companion

import android.net.Uri
import com.dualdex.emulator.LibretroHost
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.pokemon.PlayerLocation
import com.dualdex.pokemon.RegionMapDatabase
import com.dualdex.pokemon.RegionMapSection
import com.dualdex.romhack.RomHackProfile
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

enum class CompanionTab(val title: String, val iconEmoji: String) {
    HOME("Home", "🏠"),
    PARTY("Party", "👥"),
    MAP("Map", "🗺️"),
    CALC("Calc", "⚔️"),
    TYPES("Types", "🛡️"),
    DOCS("Docs", "📖"),
    CHEATS("Cheats", "⚡"),
    SAVES("Saves", "💾"),
    ASSISTANT("Assistant", "🤖"),
    BATTLE("Battle", "🎮"),
    SETTINGS("Settings", "⚙️")
}

class CompanionViewModel(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
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

    private val _isInBattle = MutableStateFlow(false)
    val isInBattle: StateFlow<Boolean> = _isInBattle.asStateFlow()

    private val _isBattleTabEnabled = MutableStateFlow(false)
    val isBattleTabEnabled: StateFlow<Boolean> = _isBattleTabEnabled.asStateFlow()

    private val _activeGameId = MutableStateFlow(0)
    val activeGameId: StateFlow<Int> = _activeGameId.asStateFlow()

    private val _activeRomTitle = MutableStateFlow("")
    val activeRomTitle: StateFlow<String> = _activeRomTitle.asStateFlow()

    private val _activeProfile = MutableStateFlow(RomHackProfile.DEFAULT_FIRERED)
    val activeProfile: StateFlow<RomHackProfile> = _activeProfile.asStateFlow()

    private val _playerStatStages = MutableStateFlow(com.dualdex.battle.StatStages())
    val playerStatStages: StateFlow<com.dualdex.battle.StatStages> = _playerStatStages.asStateFlow()

    private val _enemyStatStages = MutableStateFlow(com.dualdex.battle.StatStages())
    val enemyStatStages: StateFlow<com.dualdex.battle.StatStages> = _enemyStatStages.asStateFlow()

    private val _battleUiSnapshot = MutableStateFlow(com.dualdex.battle.BattleUiSnapshot())
    val battleUiSnapshot: StateFlow<com.dualdex.battle.BattleUiSnapshot> = _battleUiSnapshot.asStateFlow()

    var battleInputAdapter: com.dualdex.battle.BattleInputAdapter = com.dualdex.battle.BattleInputAdapter()

    private val _playerLocation = MutableStateFlow<PlayerLocation?>(null)
    val playerLocation: StateFlow<PlayerLocation?> = _playerLocation.asStateFlow()

    private val _resolvedLocation = MutableStateFlow<RegionMapSection>(RegionMapDatabase.JOHTO_DEFAULT)
    val resolvedLocation: StateFlow<RegionMapSection> = _resolvedLocation.asStateFlow()

    private var pollingJob: Job? = null

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
        }
    }

    fun setProfile(profile: RomHackProfile) {
        _activeProfile.value = profile
        _activeGameId.value = profile.gameId
        _activeRomTitle.value = profile.name
    }

    fun startPolling(intervalMs: Long = 200L) {
        if (pollingJob?.isActive == true) return

        pollingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val gameId = _activeGameId.value
                    val playerPartyRaw = LibretroHost.nativeReadPartyFromCore(gameId)
                    if (playerPartyRaw != null) {
                        val playerList = playerPartyRaw.filterNotNull().filter { !it.isEmpty && it.isValid }
                        if (playerList.isNotEmpty() && playerList != _playerParty.value) {
                            _playerParty.value = playerList
                        }
                    }

                    val enemyPartyRaw = LibretroHost.nativeReadEnemyPartyFromCore(gameId)
                    val enemyList = enemyPartyRaw?.filterNotNull()?.filter { !it.isEmpty && it.isValid } ?: emptyList()
                    if (enemyList != _enemyParty.value) {
                        _enemyParty.value = enemyList
                    }
                    val inBattle = enemyList.isNotEmpty()
                    if (inBattle != _isInBattle.value) {
                        _isInBattle.value = inBattle
                    }
                    if (inBattle) {
                        val activeSlot = LibretroHost.nativeGetActiveBattlerSlot(gameId)
                        if (activeSlot in 0..5 && activeSlot != _selectedMemberIndex.value) {
                            _selectedMemberIndex.value = activeSlot
                        }
                        val enemyActiveSlot = LibretroHost.nativeGetActiveEnemyBattlerSlot(gameId)
                        val resolvedSlot = if (enemyActiveSlot in 0 until enemyList.size) enemyActiveSlot else if (enemyList.isNotEmpty()) 0 else -1
                        if (resolvedSlot != _activeEnemyMemberIndex.value) {
                            _activeEnemyMemberIndex.value = resolvedSlot
                        }

                        val pStages = LibretroHost.nativeReadBattleStatStages(gameId, 0)
                        _playerStatStages.value = com.dualdex.battle.StatStages.fromRawArray(pStages)

                        val eStages = LibretroHost.nativeReadBattleStatStages(gameId, 1)
                        _enemyStatStages.value = com.dualdex.battle.StatStages.fromRawArray(eStages)

                        val uiCode = LibretroHost.nativeReadBattleUiState(gameId)
                        val uiState = when (uiCode) {
                            1 -> com.dualdex.battle.BattleUiState.COMMAND_MENU
                            2 -> com.dualdex.battle.BattleUiState.MOVE_MENU
                            3 -> com.dualdex.battle.BattleUiState.PARTY_MENU
                            4 -> com.dualdex.battle.BattleUiState.ANIMATION_OR_TEXT
                            else -> com.dualdex.battle.BattleUiState.UNKNOWN
                        }
                        _battleUiSnapshot.value = com.dualdex.battle.BattleUiSnapshot(
                            state = uiState,
                            isInputAccepted = uiState == com.dualdex.battle.BattleUiState.COMMAND_MENU ||
                                    uiState == com.dualdex.battle.BattleUiState.MOVE_MENU ||
                                    uiState == com.dualdex.battle.BattleUiState.PARTY_MENU,
                            confidence = if (_activeProfile.value.isVerified) com.dualdex.battle.DataConfidence.VERIFIED else com.dualdex.battle.DataConfidence.ESTIMATE
                        )
                    } else {
                        if (_activeEnemyMemberIndex.value != -1) {
                            _activeEnemyMemberIndex.value = -1
                        }
                        _playerStatStages.value = com.dualdex.battle.StatStages()
                        _enemyStatStages.value = com.dualdex.battle.StatStages()
                        _battleUiSnapshot.value = com.dualdex.battle.BattleUiSnapshot()
                    }

                    val loc = LibretroHost.nativeReadPlayerLocation(gameId)
                    if (loc != null && loc.isValid && loc != _playerLocation.value) {
                        _playerLocation.value = loc
                        val isHns = _activeProfile.value.id == "heart_and_soul" || _activeRomTitle.value.contains("HEART", ignoreCase = true)
                        _resolvedLocation.value = RegionMapDatabase.resolveLocation(gameId, isHns, loc)
                    }
                } catch (e: Throwable) {
                    android.util.Log.e("DualDex_Companion", "Error in memory poller: ${e.message}", e)
                }
                delay(intervalMs)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun setBattleTabEnabled(enabled: Boolean) {
        _isBattleTabEnabled.value = enabled
    }

    fun setActiveEnemyMemberIndex(index: Int) {
        _activeEnemyMemberIndex.value = index
    }

    fun setIsInBattle(inBattle: Boolean) {
        _isInBattle.value = inBattle
        if (!inBattle) {
            _activeEnemyMemberIndex.value = -1
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
        if (inBattle) {
            if (_activeEnemyMemberIndex.value !in party.indices) {
                _activeEnemyMemberIndex.value = 0
            }
        } else {
            _activeEnemyMemberIndex.value = -1
            _playerStatStages.value = com.dualdex.battle.StatStages()
            _enemyStatStages.value = com.dualdex.battle.StatStages()
            _battleUiSnapshot.value = com.dualdex.battle.BattleUiSnapshot()
        }
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
