package com.dualdex.retroachievements

import com.dualdex.emulator.LibretroCoreBridge
import com.dualdex.emulator.LibretroCoreCoordinator
import com.dualdex.emulator.RomDurableResumeTest
import com.dualdex.emulator.RomIdentity
import com.dualdex.emulator.RomSaveIntegrityTest
import com.dualdex.emulator.RomSessionManager
import com.dualdex.emulator.SaveStateManager
import com.dualdex.emulator.SwitchResult
import com.dualdex.emulator.storage.SaveStateFiles
import com.dualdex.settings.SettingsManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class RetroAchievementsTest {
    @Test
    fun hardcoreUsesOneGateAndBlocksActualCoordinatorMutations() {
        val bridge = RecordingCore()
        val core = LibretroCoreCoordinator(bridge = bridge)
        val identity = RomIdentity("a".repeat(64), "Test", md5 = "b".repeat(32))
        core.armRaHardcore(true)
        assertTrue(core.loadVerifiedRom("synthetic.gba", identity))

        val generation = core.stateGeneration
        val policy = core.raHardcorePolicy
        assertTrue(policy.isHardcoreActive)
        assertFalse(policy.mayLoadSaveState)
        assertFalse(policy.mayUseCheats)
        assertFalse(policy.mayUseInteractiveBattleControls)
        assertFalse(policy.mayAutoResumeState)
        assertFalse(core.loadState("synthetic.state"))
        core.cheatSet(0, true, "synthetic")
        core.cheatSet(1, false, "synthetic-disabled")
        assertEquals(0, bridge.stateLoads)
        assertEquals(0, bridge.enabledCheats)
        assertEquals(0, bridge.allCheatSets)
        assertEquals(generation, core.stateGeneration)

        core.executeExclusive { core.setRaHardcoreLocked(false) }
        assertTrue(core.raHardcorePolicy.mayLoadSaveState)
        assertTrue(core.raHardcorePolicy.mayUseCheats)
        assertTrue(core.raHardcorePolicy.mayUseInteractiveBattleControls)
        assertTrue(core.raHardcorePolicy.mayAutoResumeState)
        assertTrue(core.loadState("synthetic.state"))
        core.cheatSet(0, true, "synthetic")
        core.cheatSet(1, false, "synthetic-disabled")
        assertEquals(1, bridge.stateLoads)
        assertEquals(1, bridge.enabledCheats)
        assertEquals(2, bridge.allCheatSets)
    }

    @Test
    fun hardcoreRejectsDisabledCheatSetDuringPendingLookupAndMirrorsProtection() {
        val bridge = RecordingCore()
        val nativeProtection = mutableListOf<Boolean>()
        val core = object : LibretroCoreCoordinator(bridge = bridge) {
            override fun syncRaHardcoreProtectedNative(protectedSession: Boolean) {
                nativeProtection += protectedSession
            }
        }
        val identity = RomIdentity("a".repeat(64), "Test", md5 = "b".repeat(32))
        core.armRaHardcore(true)
        assertTrue(core.loadVerifiedRom("synthetic.gba", identity))

        // Pending Hardcore lookup immediately protects the session and mirrors to native.
        assertEquals(listOf(true), nativeProtection)
        assertFalse(core.loadState("synthetic.state"))
        core.cheatSet(0, false, "82025840 0044")
        core.cheatSet(0, true, "82025840 0044")
        core.cheatReset()
        assertEquals(0, bridge.stateLoads)
        assertEquals(0, bridge.allCheatSets)
        assertEquals(1, bridge.cheatResets)

        // Transition to Softcore and back to Hardcore mirrors every boundary change.
        core.executeExclusive { core.setRaHardcoreLocked(false) }
        assertEquals(listOf(true, false), nativeProtection)
        core.cheatSet(0, false, "82025840 0044")
        assertEquals(1, bridge.allCheatSets)

        core.executeExclusive { core.setRaHardcoreLocked(true) }
        assertEquals(listOf(true, false, true), nativeProtection)
        core.cheatSet(0, false, "82025840 0044")
        assertEquals(1, bridge.allCheatSets)

        assertTrue(core.unloadRom())
        assertEquals(listOf(true, false, true, false), nativeProtection)
    }

    @Test
    fun enablingHardcoreDuringSetLookupReconcilesWhenGameBecomesActive() = runBlocking {
        val bridge = RecordingCore()
        val core = LibretroCoreCoordinator(bridge = bridge)
        val tokenPrefs = RomDurableResumeTest.FakeSharedPreferences()
        val settingsPrefs = RomDurableResumeTest.FakeSharedPreferences()
        val tokens = RaTokenStore(tokenPrefs).apply { save(RaCredentials("player", "saved-token")) }
        val settings = SettingsManager(settingsPrefs).apply { retroAchievementsHardcore = false }
        val native = FakeRaNativeBackend()

        val service = RetroAchievementsService(core, tokens, settings, native, autoStart = false)
        // Complete saved-token login.
        native.loginStatus = 2 // NATIVE_READY
        service.drainForTest()
        assertEquals(RaAccountStatus.SIGNED_IN, service.state.value.account)

        // Launch ROM in Softcore; native enters LOADING.
        val identity = RomIdentity("c".repeat(64), "LookupGame", md5 = "d".repeat(32))
        assertTrue(core.loadVerifiedRom("lookup.gba", identity))
        native.gameStatus = 1 // NATIVE_LOADING
        service.drainForTest()
        assertEquals(RaGameStatus.LOADING, service.state.value.game)
        assertFalse(core.raHardcorePolicy.isHardcoreActive)
        assertFalse(native.hardcoreEnabled)

        // Enable Hardcore while lookup is still LOADING.
        service.setHardcore(true)
        service.drainForTest()
        assertTrue(service.state.value.hardcoreRequested)
        assertFalse(core.raHardcorePolicy.isHardcoreActive)
        assertEquals(0, bridge.resets)

        // Lookup completes and transitions to ACTIVE (still Softcore natively).
        native.gameStatus = 2 // NATIVE_ACTIVE
        service.drainForTest()

        // Reconciliation applied Hardcore: cheats cleared, core reset, coordinator + native updated.
        assertTrue(core.raHardcorePolicy.isHardcoreActive)
        assertTrue(service.state.value.hardcorePolicy.isHardcoreActive)
        assertTrue(native.hardcoreEnabled)
        assertEquals(1, bridge.cheatResets)
        assertEquals(1, bridge.resets)

        // Subsequent polls while ACTIVE do not reset again.
        service.drainForTest()
        assertEquals(1, bridge.resets)
        assertEquals(1, bridge.cheatResets)
    }

    @Test
    fun signInWithLoadedRomReconcilesRequestedHardcoreOnActive() = runBlocking {
        val bridge = RecordingCore()
        val core = LibretroCoreCoordinator(bridge = bridge)
        val tokens = RaTokenStore(RomDurableResumeTest.FakeSharedPreferences())
        val settings = SettingsManager(RomDurableResumeTest.FakeSharedPreferences()).apply {
            retroAchievementsHardcore = true
        }
        val native = FakeRaNativeBackend()

        // ROM loaded before sign-in -> starts Softcore because user is signed out.
        val identity = RomIdentity("e".repeat(64), "LoadedFirst", md5 = "f".repeat(32))
        assertTrue(core.loadVerifiedRom("loaded.gba", identity))
        assertFalse(core.raHardcorePolicy.isHardcoreActive)

        val service = RetroAchievementsService(core, tokens, settings, native, autoStart = false)
        service.signIn("player", "pw".encodeToByteArray())
        native.loginStatus = 2 // NATIVE_READY
        service.drainForTest()
        assertEquals(RaAccountStatus.SIGNED_IN, service.state.value.account)

        // Game lookup starts and completes -> reconciles to Hardcore.
        native.gameStatus = 2 // NATIVE_ACTIVE
        service.drainForTest()
        assertTrue(core.raHardcorePolicy.isHardcoreActive)
        assertTrue(service.state.value.hardcorePolicy.isHardcoreActive)
        assertTrue(native.hardcoreEnabled)
        assertEquals(1, bridge.cheatResets)
        assertEquals(1, bridge.resets)
    }

    @Test
    fun serverForcedSoftcoreDropDoesNotReconcileLoop() = runBlocking {
        val bridge = RecordingCore()
        val core = LibretroCoreCoordinator(bridge = bridge)
        val tokens = RaTokenStore(RomDurableResumeTest.FakeSharedPreferences()).apply {
            save(RaCredentials("player", "saved-token"))
        }
        val settings = SettingsManager(RomDurableResumeTest.FakeSharedPreferences()).apply {
            retroAchievementsHardcore = true
        }
        val native = FakeRaNativeBackend(rejectHardcore = true)

        val service = RetroAchievementsService(core, tokens, settings, native, autoStart = false)
        native.loginStatus = 2 // NATIVE_READY
        service.drainForTest()

        // ROM loads armed in Hardcore, but server/native forces Softcore on ACTIVE.
        val identity = RomIdentity("1".repeat(64), "ForcedSoftcore", md5 = "2".repeat(32))
        assertTrue(core.loadVerifiedRom("forced.gba", identity))
        assertTrue(core.raHardcorePolicy.isHardcoreActive)
        native.gameStatus = 2 // NATIVE_ACTIVE with hardcore=0
        service.drainForTest()

        assertFalse(core.raHardcorePolicy.isHardcoreActive)
        assertFalse(service.state.value.hardcorePolicy.isHardcoreActive)
        assertEquals("RetroAchievements switched this game to Softcore.", service.state.value.message)
        assertEquals(0, bridge.resets)
    }

    @Test
    fun skippedAutoResumeDuringPendingHardcoreLookupIsProtectedAcrossNoSetPauseAndRomSwitch() = runBlocking {
        val tempRoot = Files.createTempDirectory("dualdex_ra_autoresume_noset_").toFile()
        try {
            val romAFile = File(tempRoot, "romA.gba").apply { writeBytes(ByteArray(4096) { 0x11 }) }
            val romBFile = File(tempRoot, "romB.gba").apply { writeBytes(ByteArray(4096) { 0x22 }) }
            val romAIdentity = RomIdentity.fromFile(romAFile, "Rom A")

            val bridge = StateBytesCoreBridge(stateSize = RomSaveIntegrityTest.STATE_SIZE_256K)
            val core = LibretroCoreCoordinator(bridge = bridge)
            val ssm = SaveStateManager(customBaseDir = File(tempRoot, "saves"), customCoordinator = core)
            val sessions = RomSessionManager(
                saveStateManager = ssm,
                customRomCacheDir = File(tempRoot, "cache"),
                customCoordinator = core
            )

            val tokens = RaTokenStore(RomDurableResumeTest.FakeSharedPreferences()).apply {
                save(RaCredentials("player", "saved-token"))
            }
            val settings = SettingsManager(RomDurableResumeTest.FakeSharedPreferences()).apply {
                retroAchievementsHardcore = true
            }
            val native = FakeRaNativeBackend()
            val service = RetroAchievementsService(core, tokens, settings, native, autoStart = false)
            native.loginStatus = 2 // NATIVE_READY
            service.drainForTest()

            // Seed a pre-existing Softcore auto_resume.state for ROM A.
            val originalAutoResumeBytes = ByteArray(RomSaveIntegrityTest.STATE_SIZE_256K) { 0x5A }
            val romAAutoResume = ssm.getCanonicalFile(romAIdentity, SaveStateFiles.AUTO_RESUME).apply {
                writeBytes(originalAutoResumeBytes)
            }

            // Boot ROM A while Hardcore is armed (lookup pending) -> boot resume skipped and preserved.
            val switchA = sessions.switchRomFile(romAFile, emptyList(), preferredTitle = "Rom A")
            assertTrue(switchA is SwitchResult.Success)
            val activeA = (switchA as SwitchResult.Success).identity
            assertTrue(core.raHardcorePolicy.isHardcoreActive)
            assertEquals(0, bridge.loadedStates.size)
            assertEquals(SaveStateFiles.AUTO_RESUME, ssm.preservedAutoResumeFor(activeA)?.resumeFileName)

            // RA lookup resolves to NO_SET (releases Hardcore guard to Softcore).
            native.gameStatus = 3 // NATIVE_NO_SET
            service.drainForTest()
            assertFalse(core.raHardcorePolicy.isHardcoreActive)
            assertTrue(core.raHardcorePolicy.mayLoadSaveState)

            // Pausing the new session must NOT overwrite ROM A's preserved auto_resume.state.
            bridge.nextSavedStateByte = 0x7E
            assertFalse(ssm.saveAutoResume(activeA))
            assertArrayEquals(originalAutoResumeBytes, romAAutoResume.readBytes())
            assertTrue(ssm.hasPreservedAutoResume(activeA))

            // Switching to ROM B must NOT overwrite ROM A's preserved auto_resume.state, and must
            // reset preserved tracking so ROM B behaves cleanly on its own.
            core.armRaHardcore(false)
            val switchB = sessions.switchRomFile(romBFile, emptyList(), preferredTitle = "Rom B")
            assertTrue(switchB is SwitchResult.Success)
            val activeB = (switchB as SwitchResult.Success).identity
            assertArrayEquals(originalAutoResumeBytes, romAAutoResume.readBytes())
            assertFalse(ssm.hasPreservedAutoResume(activeA))
            assertFalse(ssm.hasPreservedAutoResume(activeB))
            assertNull(ssm.preservedAutoResume.value)

            // Ordinary non-skipped session on ROM B auto-saves normally.
            bridge.nextSavedStateByte = 0x44
            assertTrue(ssm.saveAutoResume(activeB))
            val romBAutoResume = ssm.getCanonicalFile(activeB, SaveStateFiles.AUTO_RESUME)
            assertTrue(romBAutoResume.exists())
            assertEquals(0x44.toByte(), romBAutoResume.readBytes()[0])
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun skippedAutoResumeRemainsProtectedWhenLookupResolvesHardcoreActiveAndAllowsCloseGame() = runBlocking {
        val tempRoot = Files.createTempDirectory("dualdex_ra_autoresume_hardcore_").toFile()
        try {
            val romAFile = File(tempRoot, "romA.gba").apply { writeBytes(ByteArray(4096) { 0x31 }) }
            val romAIdentity = RomIdentity.fromFile(romAFile, "Rom A")

            val bridge = StateBytesCoreBridge(stateSize = RomSaveIntegrityTest.STATE_SIZE_256K)
            val core = LibretroCoreCoordinator(bridge = bridge)
            val ssm = SaveStateManager(customBaseDir = File(tempRoot, "saves"), customCoordinator = core)
            val sessions = RomSessionManager(
                saveStateManager = ssm,
                customRomCacheDir = File(tempRoot, "cache"),
                customCoordinator = core
            )

            val tokens = RaTokenStore(RomDurableResumeTest.FakeSharedPreferences()).apply {
                save(RaCredentials("player", "saved-token"))
            }
            val settings = SettingsManager(RomDurableResumeTest.FakeSharedPreferences()).apply {
                retroAchievementsHardcore = true
            }
            val native = FakeRaNativeBackend()
            val service = RetroAchievementsService(core, tokens, settings, native, autoStart = false)
            native.loginStatus = 2 // NATIVE_READY
            service.drainForTest()

            val originalAutoResumeBytes = ByteArray(RomSaveIntegrityTest.STATE_SIZE_256K) { 0x6B }
            val romAAutoResume = ssm.getCanonicalFile(romAIdentity, SaveStateFiles.AUTO_RESUME).apply {
                writeBytes(originalAutoResumeBytes)
            }

            val switchA = sessions.switchRomFile(romAFile, emptyList(), preferredTitle = "Rom A") as SwitchResult.Success
            val activeA = switchA.identity
            native.gameStatus = 2 // NATIVE_ACTIVE
            service.drainForTest()
            assertTrue(core.raHardcorePolicy.isHardcoreActive)

            // Cannot restore while Hardcore is active, and preservation remains intact.
            assertFalse(ssm.restorePreservedAutoResume(activeA))
            assertTrue(ssm.hasPreservedAutoResume(activeA))
            assertEquals(0, bridge.loadedStates.size)

            // Pause auto-save does not overwrite the Softcore auto_resume.state.
            bridge.nextSavedStateByte = 0x7F
            assertFalse(ssm.saveAutoResume(activeA))
            assertArrayEquals(originalAutoResumeBytes, romAAutoResume.readBytes())

            // Close Game still flushes SRAM and unloads cleanly without clobbering preserved auto_resume.
            assertTrue(ssm.closeGame(activeA))
            assertNull(ssm.activeIdentity)
            assertNull(ssm.preservedAutoResume.value)
            assertArrayEquals(originalAutoResumeBytes, romAAutoResume.readBytes())
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun explicitResolutionOfPreservedAutoResumeRestoresOrKeepsCurrentAndReenablesAutoSave() = runBlocking {
        val tempRoot = Files.createTempDirectory("dualdex_ra_autoresume_resolve_").toFile()
        try {
            val romAFile = File(tempRoot, "romA.gba").apply { writeBytes(ByteArray(4096) { 0x41 }) }
            val romAIdentity = RomIdentity.fromFile(romAFile, "Rom A")

            val bridge = StateBytesCoreBridge(stateSize = RomSaveIntegrityTest.STATE_SIZE_256K)
            val core = LibretroCoreCoordinator(bridge = bridge)
            val ssm = SaveStateManager(customBaseDir = File(tempRoot, "saves"), customCoordinator = core)
            val sessions = RomSessionManager(
                saveStateManager = ssm,
                customRomCacheDir = File(tempRoot, "cache"),
                customCoordinator = core
            )

            // --- Path A: explicit restore after lookup releases to Softcore ---
            val originalBytes = ByteArray(RomSaveIntegrityTest.STATE_SIZE_256K) { 0x2C }
            val autoResumeFile = ssm.getCanonicalFile(romAIdentity, SaveStateFiles.AUTO_RESUME).apply {
                writeBytes(originalBytes)
            }
            core.armRaHardcore(true)
            val firstSession = (sessions.switchRomFile(romAFile, emptyList(), preferredTitle = "Rom A") as SwitchResult.Success).identity
            assertTrue(ssm.hasPreservedAutoResume(firstSession))
            // Release Hardcore guard (e.g. lookup resolved to NO_SET).
            core.executeExclusive { core.setRaHardcoreLocked(false) }
            assertFalse(ssm.saveAutoResume(firstSession))

            assertTrue(ssm.restorePreservedAutoResume(firstSession))
            assertEquals(1, bridge.loadedStates.size)
            assertArrayEquals(originalBytes, bridge.loadedStates.single())
            assertFalse(ssm.hasPreservedAutoResume(firstSession))
            assertNull(ssm.preservedAutoResume.value)

            // Normal auto-save is re-enabled after explicit restore.
            bridge.nextSavedStateByte = 0x55
            assertTrue(ssm.saveAutoResume(firstSession))
            assertEquals(0x55.toByte(), autoResumeFile.readBytes()[0])

            // --- Path B: explicit keep-current after lookup releases to Softcore ---
            val savedBeforeSecondBoot = autoResumeFile.readBytes()
            val secondSession = (sessions.switchRomFile(romAFile, emptyList(), preferredTitle = "Rom A") as SwitchResult.Success).identity
            assertTrue(ssm.hasPreservedAutoResume(secondSession))
            core.executeExclusive { core.setRaHardcoreLocked(false) }
            bridge.nextSavedStateByte = 0x66
            assertFalse(ssm.saveAutoResume(secondSession))
            assertArrayEquals(savedBeforeSecondBoot, autoResumeFile.readBytes())

            assertTrue(ssm.keepCurrentSessionOverPreservedAutoResume(secondSession))
            assertFalse(ssm.hasPreservedAutoResume(secondSession))
            assertTrue(ssm.saveAutoResume(secondSession))
            assertEquals(0x66.toByte(), autoResumeFile.readBytes()[0])
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun ordinarySoftcoreSessionLoadsAutoResumeAndSavesNormally() = runBlocking {
        val tempRoot = Files.createTempDirectory("dualdex_ra_autoresume_ordinary_").toFile()
        try {
            val romFile = File(tempRoot, "romOrdinary.gba").apply { writeBytes(ByteArray(4096) { 0x51 }) }
            val romIdentity = RomIdentity.fromFile(romFile, "Ordinary")
            val bridge = StateBytesCoreBridge(stateSize = RomSaveIntegrityTest.STATE_SIZE_256K)
            val core = LibretroCoreCoordinator(bridge = bridge)
            val ssm = SaveStateManager(customBaseDir = File(tempRoot, "saves"), customCoordinator = core)
            val sessions = RomSessionManager(
                saveStateManager = ssm,
                customRomCacheDir = File(tempRoot, "cache"),
                customCoordinator = core
            )

            val seededBytes = ByteArray(RomSaveIntegrityTest.STATE_SIZE_256K) { 0x19 }
            val autoResumeFile = ssm.getCanonicalFile(romIdentity, SaveStateFiles.AUTO_RESUME).apply {
                writeBytes(seededBytes)
            }

            core.armRaHardcore(false)
            val active = (sessions.switchRomFile(romFile, emptyList(), preferredTitle = "Ordinary") as SwitchResult.Success).identity
            assertEquals(1, bridge.loadedStates.size)
            assertArrayEquals(seededBytes, bridge.loadedStates.single())
            assertFalse(ssm.hasPreservedAutoResume(active))

            bridge.nextSavedStateByte = 0x77
            assertTrue(ssm.saveAutoResume(active))
            assertEquals(0x77.toByte(), autoResumeFile.readBytes()[0])
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun preferencesStoreOnlyUsernameAndTokenAndClearOnLogout() {
        val prefs = RomDurableResumeTest.FakeSharedPreferences()
        val store = RaTokenStore(prefs)
        val account = RaCredentials("player", "private-session-value")
        assertNull(store.read())
        store.save(account)
        assertEquals(mapOf("username" to "player", "token" to "private-session-value"), prefs.all)
        assertEquals("player", store.read()?.username)
        assertEquals("private-session-value", store.read()?.token)
        assertFalse(store.toString().contains(account.token))
        assertFalse(account.toString().contains(account.username))
        assertFalse(account.toString().contains(account.token))
        store.clear()
        assertNull(store.read())
        assertTrue(prefs.all.isEmpty())
    }

    @Test
    fun sessionTransitionsDiscardOldGameAndKeepDisplayPreferences() {
        val signedOut = RaSessionState(hardcoreRequested = true, showBanners = false)
        val checking = signedOut.signedIn("player").forRom(loaded = true, RaHardcorePolicy(true))
        assertEquals(RaAccountStatus.SIGNED_IN, checking.account)
        assertEquals(RaGameStatus.LOADING, checking.game)
        assertTrue(checking.hardcorePolicy.isHardcoreActive)

        val playing = checking.copy(game = RaGameStatus.ACTIVE, gameTitle = "Game", richPresence = "Exploring")
        val next = playing.forRom(loaded = false, RaHardcorePolicy())
        assertEquals(RaGameStatus.NONE, next.game)
        assertEquals("player", next.username)
        assertNull(next.gameTitle)
        assertEquals("", next.richPresence)
        val logout = playing.signedOut()
        assertEquals(signedOut, logout)
        assertNull(logout.username)
        assertFalse(logout.hardcorePolicy.isHardcoreActive)
    }

    @Test
    fun unlocksDisplayInOrderAndStaleDismissCannotHideNextBanner() {
        val queue = RaUnlockQueue()
        val first = RaUnlockBanner(1, "First", 5)
        val second = RaUnlockBanner(2, "Second", 10)
        queue.add(first)
        val shown = queue.banner.value!!
        queue.add(second)
        assertEquals(first.title, queue.banner.value?.title)
        queue.dismiss(shown)
        assertEquals(second.title, queue.banner.value?.title)
        queue.dismiss(shown)
        assertEquals(second.title, queue.banner.value?.title)
        queue.clear()
        assertNull(queue.banner.value)
    }

    @Test
    fun offlineTransportReturnsRetryableStatusWithoutLeakingCredentials() {
        val request = RaHttpRequest(
            1, "https://retroachievements.org/dorequest.php",
            "r=login2&p=private-password".encodeToByteArray(), null
        )
        val response = RaHttp.execute(request) { throw IOException("private-password") }
        assertEquals(-2, response.status)
        assertFalse(response.body.decodeToString().contains("private-password"))
        assertFalse(request.toString().contains("private-password"))
    }

    @Test
    fun transportRejectsOffOriginWithoutOpeningAConnection() {
        for (url in listOf(
            "http://retroachievements.org/dorequest.php",
            "https://retroachievements.org@other.example/dorequest.php",
            "https://retroachievements.org:8443/dorequest.php",
            "https://retroachievements.org/elsewhere"
        )) {
            val response = RaHttp.execute(RaHttpRequest(1, url, byteArrayOf(1), null)) {
                throw AssertionError("Unexpected network connection")
            }
            assertEquals(-1, response.status)
        }
    }

    private class RecordingCore : LibretroCoreBridge {
        var stateLoads = 0
        var enabledCheats = 0
        var allCheatSets = 0
        var cheatResets = 0
        var resets = 0
        override fun loadRom(romPath: String) = true
        override fun loadState(statePath: String): Boolean {
            stateLoads++
            return true
        }
        override fun cheatReset() {
            cheatResets++
        }
        override fun cheatSet(index: Int, enabled: Boolean, code: String) {
            allCheatSets++
            if (enabled) enabledCheats++
        }
        override fun resetCore() {
            resets++
        }
    }

    private class StateBytesCoreBridge(
        private val stateSize: Int,
        private val sramSize: Int = RomSaveIntegrityTest.SRAM_SIZE_128K
    ) : LibretroCoreBridge {
        var nextSavedStateByte: Byte = 0x33
        val loadedStates = mutableListOf<ByteArray>()
        override fun loadRom(romPath: String) = true
        override fun unloadRom() = true
        override fun getSaveRamSize() = sramSize.toLong()
        override fun getSaveStateSize() = stateSize.toLong()
        override fun loadSaveRam(savePath: String) = true
        override fun flushSaveRam(savePath: String): Boolean {
            File(savePath).writeBytes(ByteArray(sramSize) { 0x01 })
            return true
        }
        override fun saveState(statePath: String): Boolean {
            File(statePath).writeBytes(ByteArray(stateSize) { nextSavedStateByte })
            return true
        }
        override fun loadState(statePath: String): Boolean {
            val file = File(statePath)
            if (!file.isFile || file.length() != stateSize.toLong()) return false
            loadedStates += file.readBytes()
            return true
        }
    }

    private class FakeRaNativeBackend(private val rejectHardcore: Boolean = false) : RaNativeBackend {
        var loginStatus = 0
        var gameStatus = 0
        var hardcoreEnabled = false
        var loadedMd5: String? = null
        override fun login(username: ByteArray, secret: ByteArray, useToken: Boolean) = true
        override fun logout() {
            loginStatus = 0
            gameStatus = 0
            hardcoreEnabled = false
        }
        override fun setHardcore(enabled: Boolean) {
            hardcoreEnabled = enabled && !rejectHardcore
        }
        override fun loadGame(md5: String) {
            loadedMd5 = md5
            if (gameStatus == 0) gameStatus = 1
        }
        override fun idle() = Unit
        override fun status() = intArrayOf(loginStatus, gameStatus, if (hardcoreEnabled) 1 else 0, 0, 0)
        override fun user() = arrayOf("player".encodeToByteArray(), "saved-token".encodeToByteArray())
        override fun nextRequest(): Array<ByteArray?>? = null
        override fun complete(id: Long, status: Int, body: ByteArray) = Unit
        override fun game() = arrayOf<String?>("Synthetic Game", "Playing")
        override fun achievements() = emptyArray<String?>()
        override fun nextUnlock(): Array<String?>? = null
    }
}
