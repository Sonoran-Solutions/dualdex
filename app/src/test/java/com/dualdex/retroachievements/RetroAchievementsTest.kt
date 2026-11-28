package com.dualdex.retroachievements

import com.dualdex.emulator.LibretroCoreBridge
import com.dualdex.emulator.LibretroCoreCoordinator
import com.dualdex.emulator.LoadedRomStatus
import com.dualdex.emulator.PreserveAutoResumeResult
import com.dualdex.emulator.RomDurableResumeTest
import com.dualdex.emulator.RomIdentity
import com.dualdex.emulator.RomSaveIntegrityTest
import com.dualdex.emulator.RomSessionManager
import com.dualdex.emulator.SaveStateManager
import com.dualdex.emulator.SwitchResult
import com.dualdex.emulator.storage.AtomicSaveFile
import com.dualdex.emulator.storage.LegacyCandidate
import com.dualdex.emulator.storage.LegacySaveCatalog
import com.dualdex.emulator.storage.MigrationResult
import com.dualdex.emulator.storage.SaveStateFiles
import com.dualdex.settings.SettingsManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

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
            val preservedRecord = ssm.preservedAutoResumeFor(activeA)!!
            assertFalse(ssm.restorePreservedAutoResume(activeA, preservedRecord))
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

            val firstRecord = ssm.preservedAutoResumeFor(firstSession)!!
            assertTrue(ssm.restorePreservedAutoResume(firstSession, firstRecord))
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

            val secondRecord = ssm.preservedAutoResumeFor(secondSession)!!
            assertTrue(ssm.keepCurrentSessionOverPreservedAutoResume(secondSession, secondRecord))
            assertFalse(ssm.hasPreservedAutoResume(secondSession))
            assertTrue(ssm.saveAutoResume(secondSession))
            assertEquals(0x66.toByte(), autoResumeFile.readBytes()[0])
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun skippedAutoResumeSurvivesChangedSramCloseAndReopenInHardcoreAndSoftcore() = runBlocking {
        val tempRoot = Files.createTempDirectory("dualdex_ra_autoresume_reopen_").toFile()
        try {
            val romAFile = File(tempRoot, "romA.gba").apply { writeBytes(ByteArray(4096) { 0x61 }) }
            val romAIdentity = RomIdentity.fromFile(romAFile, "Rom A")

            val bridge = StateBytesCoreBridge(stateSize = RomSaveIntegrityTest.STATE_SIZE_256K)
            val core = LibretroCoreCoordinator(bridge = bridge)
            val ssm = SaveStateManager(customBaseDir = File(tempRoot, "saves"), customCoordinator = core)
            val sessions = RomSessionManager(
                saveStateManager = ssm,
                customRomCacheDir = File(tempRoot, "cache"),
                customCoordinator = core
            )

            // Seed older Softcore auto_resume.state and battery.sav.
            val olderMtime = System.currentTimeMillis() - 60_000L
            val originalAutoResumeBytes = ByteArray(RomSaveIntegrityTest.STATE_SIZE_256K) { 0x3A }
            val autoResumeFile = ssm.getCanonicalFile(romAIdentity, SaveStateFiles.AUTO_RESUME).apply {
                writeBytes(originalAutoResumeBytes)
                setLastModified(olderMtime)
            }
            val batteryFile = ssm.getCanonicalFile(romAIdentity, SaveStateFiles.BATTERY).apply {
                writeBytes(ByteArray(RomSaveIntegrityTest.SRAM_SIZE_128K) { 0x01 })
                setLastModified(olderMtime)
            }

            // 1. Boot in Hardcore -> auto_resume skipped and durably preserved.
            core.armRaHardcore(true)
            val session1 = (sessions.switchRomFile(romAFile, emptyList(), preferredTitle = "Rom A") as SwitchResult.Success).identity
            assertEquals(0, bridge.loadedStates.size)
            assertEquals(SaveStateFiles.AUTO_RESUME, ssm.preservedAutoResumeFor(session1)?.resumeFileName)

            // 2. Player changes SRAM and closes game -> newer battery.sav written, auto_resume untouched.
            bridge.nextSramByte = 0x02
            assertTrue(ssm.closeGame(session1))
            assertNull(ssm.activeIdentity)
            assertNull(ssm.preservedAutoResume.value)
            assertArrayEquals(originalAutoResumeBytes, autoResumeFile.readBytes())
            assertTrue("battery.sav should now be newer than preserved state", batteryFile.lastModified() > autoResumeFile.lastModified())
            assertNull(
                "Battery freshness check must still filter out the older state for automatic loads",
                SaveStateFiles.newestResumeState(ssm.getCanonicalRomDir(romAIdentity))
            )

            // 3. Reopen in Hardcore -> preserved record restored; pause must NOT overwrite auto_resume.state.
            core.armRaHardcore(true)
            val session2 = (sessions.switchRomFile(romAFile, emptyList(), preferredTitle = "Rom A") as SwitchResult.Success).identity
            assertEquals(0, bridge.loadedStates.size)
            assertEquals(SaveStateFiles.AUTO_RESUME, ssm.preservedAutoResumeFor(session2)?.resumeFileName)
            bridge.nextSavedStateByte = 0x7A
            assertFalse(ssm.saveAutoResume(session2))
            assertArrayEquals(originalAutoResumeBytes, autoResumeFile.readBytes())
            assertTrue(ssm.closeGame(session2))

            // 4. Reopen in Softcore -> automatic boot-resume does NOT load stale state, yet pause still protects it.
            core.armRaHardcore(false)
            val session3 = (sessions.switchRomFile(romAFile, emptyList(), preferredTitle = "Rom A") as SwitchResult.Success).identity
            assertEquals(0, bridge.loadedStates.size)
            val softcorePreserved = ssm.preservedAutoResumeFor(session3)
            assertNotNull(softcorePreserved)
            assertEquals(SaveStateFiles.AUTO_RESUME, softcorePreserved!!.resumeFileName)
            bridge.nextSavedStateByte = 0x7B
            assertFalse(ssm.saveAutoResume(session3))
            assertArrayEquals(originalAutoResumeBytes, autoResumeFile.readBytes())

            // 5. Explicit Restore loads the preserved state, deletes the durable marker, and re-enables auto-save.
            assertTrue(ssm.restorePreservedAutoResume(session3, softcorePreserved))
            assertEquals(1, bridge.loadedStates.size)
            assertArrayEquals(originalAutoResumeBytes, bridge.loadedStates.single())
            assertFalse(ssm.hasPreservedAutoResume(session3))
            assertFalse(ssm.getCanonicalFile(session3, SaveStateFiles.PRESERVED_AUTO_RESUME).exists())
            assertTrue(ssm.saveAutoResume(session3))
            assertEquals(0x7B.toByte(), autoResumeFile.readBytes()[0])

            // 6. Repeat skip -> changed SRAM -> close -> reopen in Softcore -> explicit Keep Current.
            assertTrue(ssm.closeGame(session3))
            autoResumeFile.setLastModified(olderMtime)
            batteryFile.setLastModified(olderMtime)
            core.armRaHardcore(true)
            val session4 = (sessions.switchRomFile(romAFile, emptyList(), preferredTitle = "Rom A") as SwitchResult.Success).identity
            assertNotNull(ssm.preservedAutoResumeFor(session4))
            bridge.nextSramByte = 0x03
            assertTrue(ssm.closeGame(session4))
            assertTrue(batteryFile.lastModified() > autoResumeFile.lastModified())

            core.armRaHardcore(false)
            val session5 = (sessions.switchRomFile(romAFile, emptyList(), preferredTitle = "Rom A") as SwitchResult.Success).identity
            assertEquals(1, bridge.loadedStates.size)
            val keepRecord = ssm.preservedAutoResumeFor(session5)!!
            assertFalse(ssm.saveAutoResume(session5))
            assertTrue(ssm.keepCurrentSessionOverPreservedAutoResume(session5, keepRecord))
            assertFalse(ssm.hasPreservedAutoResume(session5))
            assertFalse(ssm.getCanonicalFile(session5, SaveStateFiles.PRESERVED_AUTO_RESUME).exists())
            bridge.nextSavedStateByte = 0x7C
            assertTrue(ssm.saveAutoResume(session5))
            assertEquals(0x7C.toByte(), autoResumeFile.readBytes()[0])
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun stalePreservedAutoResumeRecordFromEarlierSessionCannotRestoreOrDismissReopenedSession() = runBlocking {
        val tempRoot = Files.createTempDirectory("dualdex_ra_autoresume_stale_record_").toFile()
        try {
            val romAFile = File(tempRoot, "romA.gba").apply { writeBytes(ByteArray(4096) { 0x62 }) }
            val romAIdentity = RomIdentity.fromFile(romAFile, "Rom A")

            val bridge = StateBytesCoreBridge(stateSize = RomSaveIntegrityTest.STATE_SIZE_256K)
            val core = LibretroCoreCoordinator(bridge = bridge)
            val ssm = SaveStateManager(customBaseDir = File(tempRoot, "saves"), customCoordinator = core)
            val sessions = RomSessionManager(
                saveStateManager = ssm,
                customRomCacheDir = File(tempRoot, "cache"),
                customCoordinator = core
            )

            val originalBytes = ByteArray(RomSaveIntegrityTest.STATE_SIZE_256K) { 0x4D }
            val autoResumeFile = ssm.getCanonicalFile(romAIdentity, SaveStateFiles.AUTO_RESUME).apply {
                writeBytes(originalBytes)
            }

            // Session 1 captures its displayed PreservedAutoResume record.
            core.armRaHardcore(true)
            val session1 = (sessions.switchRomFile(romAFile, emptyList(), preferredTitle = "Rom A") as SwitchResult.Success).identity
            val staleRecord = ssm.preservedAutoResumeFor(session1)!!
            assertTrue(ssm.closeGame(session1))

            // Same ROM reopened in Session 2 (new sessionGeneration) and released to Softcore.
            val session2 = (sessions.switchRomFile(romAFile, emptyList(), preferredTitle = "Rom A") as SwitchResult.Success).identity
            core.executeExclusive { core.setRaHardcoreLocked(false) }
            val currentRecord = ssm.preservedAutoResumeFor(session2)!!
            assertEquals(staleRecord.romSha256, currentRecord.romSha256)
            assertTrue(currentRecord.sessionGeneration > staleRecord.sessionGeneration)

            // Stale clicks carrying Session 1's record must neither restore nor dismiss Session 2's record.
            assertFalse(ssm.restorePreservedAutoResume(session2, staleRecord))
            assertFalse(ssm.keepCurrentSessionOverPreservedAutoResume(session2, staleRecord))
            assertEquals(0, bridge.loadedStates.size)
            assertEquals(currentRecord, ssm.preservedAutoResumeFor(session2))
            bridge.nextSavedStateByte = 0x7D
            assertFalse(ssm.saveAutoResume(session2))
            assertArrayEquals(originalBytes, autoResumeFile.readBytes())

            // Passing Session 2's current record succeeds cleanly.
            assertTrue(ssm.restorePreservedAutoResume(session2, currentRecord))
            assertEquals(1, bridge.loadedStates.size)
            assertArrayEquals(originalBytes, bridge.loadedStates.single())
            assertNull(ssm.preservedAutoResumeFor(session2))
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
    fun recoveredOlderBackupOverNewerBatteryIsNeverAutoLoadedInSoftcore() = runBlocking {
        val tempRoot = Files.createTempDirectory("dualdex_ra_autoresume_bak_freshness_").toFile()
        try {
            val romFile = File(tempRoot, "romBak.gba").apply { writeBytes(ByteArray(4096) { 0x64 }) }
            val romIdentity = RomIdentity.fromFile(romFile, "Rom Bak")
            val bridge = StateBytesCoreBridge(stateSize = RomSaveIntegrityTest.STATE_SIZE_256K)
            val core = LibretroCoreCoordinator(bridge = bridge)
            val ssm = SaveStateManager(customBaseDir = File(tempRoot, "saves"), customCoordinator = core)
            val sessions = RomSessionManager(
                saveStateManager = ssm,
                customRomCacheDir = File(tempRoot, "cache"),
                customCoordinator = core
            )

            val now = System.currentTimeMillis()
            val olderBackupMtime = now - 60_000L
            val newerBatteryBytes = ByteArray(RomSaveIntegrityTest.SRAM_SIZE_128K) { 0x09 }
            val olderBackupBytes = ByteArray(RomSaveIntegrityTest.STATE_SIZE_256K) { 0x4B }

            fun seedCorruptTargetWithOlderBackupAndNewerBattery() {
                val autoResumeFile = ssm.getCanonicalFile(romIdentity, SaveStateFiles.AUTO_RESUME).apply {
                    AtomicSaveFile.clearAutoLoadExcluded(this)
                    writeBytes(olderBackupBytes)
                    setLastModified(olderBackupMtime)
                }
                val expectedBackupMtime = autoResumeFile.lastModified()
                assertTrue(
                    "Rotating older auto_resume.state must publish verified backup provenance",
                    AtomicSaveFile.writeBytes(autoResumeFile, ByteArray(RomSaveIntegrityTest.STATE_SIZE_256K) { 0x5E })
                )
                assertTrue(AtomicSaveFile.backupProvenanceFile(autoResumeFile).exists())
                val batteryFile = ssm.getCanonicalFile(romIdentity, SaveStateFiles.BATTERY).apply {
                    writeBytes(newerBatteryBytes)
                    setLastModified(now)
                }
                autoResumeFile.apply {
                    writeBytes(ByteArray(16) { 0xEE.toByte() }) // truncated target with newer mtime
                    setLastModified(now + 5_000L)
                }
                val backupFile = File(autoResumeFile.parentFile, "${autoResumeFile.name}.bak")
                assertEquals(expectedBackupMtime, backupFile.lastModified())
                ssm.getCanonicalFile(romIdentity, SaveStateFiles.PRESERVED_AUTO_RESUME).apply {
                    writeText(SaveStateFiles.AUTO_RESUME, Charsets.UTF_8)
                }
                assertTrue(batteryFile.lastModified() > backupFile.lastModified())
            }

            // Part A: normal restoreBackup preserves .bak's verified older mtime so Softcore does not auto-load it.
            seedCorruptTargetWithOlderBackupAndNewerBattery()
            core.armRaHardcore(false)
            val session1 = (sessions.switchRomFile(romFile, emptyList(), preferredTitle = "Rom Bak") as SwitchResult.Success).identity
            assertEquals("Stale recovered backup must not auto-load over newer battery.sav", 0, bridge.loadedStates.size)
            val autoResumeFile = ssm.getCanonicalFile(romIdentity, SaveStateFiles.AUTO_RESUME)
            val batteryFile = ssm.getCanonicalFile(romIdentity, SaveStateFiles.BATTERY)
            assertArrayEquals(olderBackupBytes, autoResumeFile.readBytes())
            assertFalse(
                "Verified-timestamp recovery should not keep auto-load exclusion marker",
                AtomicSaveFile.isAutoLoadExcluded(autoResumeFile)
            )
            assertTrue(
                "Restored backup must retain older timestamp than newer battery.sav",
                autoResumeFile.lastModified() < batteryFile.lastModified()
            )
            val preserved1 = ssm.preservedAutoResumeFor(session1)
            assertNotNull("Preserved marker must remain offered for explicit restoration", preserved1)
            assertEquals(SaveStateFiles.AUTO_RESUME, preserved1!!.resumeFileName)
            bridge.nextSavedStateByte = 0x7E
            assertFalse("Pause must not overwrite preserved auto_resume.state", ssm.saveAutoResume(session1))
            assertArrayEquals(olderBackupBytes, autoResumeFile.readBytes())

            // Explicit restore still works when the player consciously requests it.
            assertTrue(ssm.restorePreservedAutoResume(session1, preserved1))
            assertEquals(1, bridge.loadedStates.size)
            assertArrayEquals(olderBackupBytes, bridge.loadedStates.single())
            assertTrue(ssm.closeGame(session1))

            // Part B: if setLastModified fails during backup recovery (freshness cannot be established),
            // Softcore refuses automatic load durably across reopens until explicit restoration.
            seedCorruptTargetWithOlderBackupAndNewerBattery()
            AtomicSaveFile.preserveTimestampHook = { _, _ -> false }
            val session2 = try {
                core.armRaHardcore(false)
                (sessions.switchRomFile(romFile, emptyList(), preferredTitle = "Rom Bak") as SwitchResult.Success).identity
            } finally {
                AtomicSaveFile.preserveTimestampHook = null
            }
            assertEquals(
                "Backup recovered without established original timestamp must not auto-load",
                1,
                bridge.loadedStates.size
            )
            assertTrue(
                "Unverified recovery must leave durable auto-load exclusion sidecar on disk",
                AtomicSaveFile.isAutoLoadExcluded(autoResumeFile)
            )
            val preserved2 = ssm.preservedAutoResumeFor(session2)
            assertNotNull(preserved2)
            assertEquals(SaveStateFiles.AUTO_RESUME, preserved2!!.resumeFileName)
            assertFalse(ssm.saveAutoResume(session2))
            assertArrayEquals(olderBackupBytes, autoResumeFile.readBytes())

            // Reopen in Softcore BEFORE explicit restoration (simulating a fresh app/session launch):
            // target is now full-size TARGET_VALID with mtime >= battery.sav, yet durable .no_autoload
            // must STILL keep it out of automatic boot load.
            assertTrue(core.unloadRom())
            ssm.activeIdentity = null
            batteryFile.setLastModified(autoResumeFile.lastModified() - 5_000L)
            assertTrue(autoResumeFile.lastModified() > batteryFile.lastModified())
            val ssmReopen = SaveStateManager(customBaseDir = File(tempRoot, "saves"), customCoordinator = core)
            val sessionsReopen = RomSessionManager(
                saveStateManager = ssmReopen,
                customRomCacheDir = File(tempRoot, "cache"),
                customCoordinator = core
            )
            core.armRaHardcore(false)
            val session2Reopen = (sessionsReopen.switchRomFile(romFile, emptyList(), preferredTitle = "Rom Bak") as SwitchResult.Success).identity
            assertEquals(
                "Reopening in Softcore before explicit restore must STILL not auto-load unverified recovery",
                1,
                bridge.loadedStates.size
            )
            assertTrue(AtomicSaveFile.isAutoLoadExcluded(autoResumeFile))
            val preserved2Reopen = ssmReopen.preservedAutoResumeFor(session2Reopen)
            assertNotNull(preserved2Reopen)
            assertEquals(SaveStateFiles.AUTO_RESUME, preserved2Reopen!!.resumeFileName)
            assertFalse(ssmReopen.saveAutoResume(session2Reopen))
            assertArrayEquals(olderBackupBytes, autoResumeFile.readBytes())
            assertTrue(ssmReopen.restorePreservedAutoResume(session2Reopen, preserved2Reopen))
            assertEquals(2, bridge.loadedStates.size)
            assertArrayEquals(olderBackupBytes, bridge.loadedStates.last())
            assertFalse(AtomicSaveFile.isAutoLoadExcluded(autoResumeFile))
        } finally {
            AtomicSaveFile.preserveTimestampHook = null
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun rotationTimestampFailureExcludesBackupFromAutoLoadAcrossReopens() = runBlocking {
        val tempRoot = Files.createTempDirectory("dualdex_ra_rotation_mtime_fail_").toFile()
        try {
            val romFile = File(tempRoot, "romRotFail.gba").apply { writeBytes(ByteArray(4096) { 0x67 }) }
            val romIdentity = RomIdentity.fromFile(romFile, "Rom Rot Fail")
            val bridge = StateBytesCoreBridge(stateSize = RomSaveIntegrityTest.STATE_SIZE_256K)
            val core = LibretroCoreCoordinator(bridge = bridge)
            val ssm = SaveStateManager(customBaseDir = File(tempRoot, "saves"), customCoordinator = core)
            val sessions = RomSessionManager(
                saveStateManager = ssm,
                customRomCacheDir = File(tempRoot, "cache"),
                customCoordinator = core
            )

            val now = System.currentTimeMillis()
            val olderStateMtime = now - 60_000L
            val batteryMtime = now - 5_000L
            val olderStateBytes = ByteArray(RomSaveIntegrityTest.STATE_SIZE_256K) { 0x3C }
            val olderSlot2Bytes = ByteArray(RomSaveIntegrityTest.STATE_SIZE_256K) { 0x3D }
            val newerBatteryBytes = ByteArray(RomSaveIntegrityTest.SRAM_SIZE_128K) { 0x0A }

            val batteryFile = ssm.getCanonicalFile(romIdentity, SaveStateFiles.BATTERY).apply {
                writeBytes(newerBatteryBytes)
                setLastModified(batteryMtime)
            }
            val autoResumeFile = ssm.getCanonicalFile(romIdentity, SaveStateFiles.AUTO_RESUME).apply {
                writeBytes(olderStateBytes)
                setLastModified(olderStateMtime)
            }
            val slot2File = ssm.getCanonicalFile(romIdentity, SaveStateFiles.slotState(2)).apply {
                writeBytes(olderSlot2Bytes)
                setLastModified(olderStateMtime)
            }
            assertTrue(batteryFile.lastModified() > autoResumeFile.lastModified())

            // Rotate BOTH copyFromStaging (auto_resume.state) and writeBytes (slot_2.state) AFTER battery.sav
            // while setLastModified fails during rotation -> .bak carries newer copy-time mtime, so neither
            // rotation may publish .bak.mtime provenance.
            val stagingFile = File(tempRoot, "incoming_auto.staging").apply {
                writeBytes(ByteArray(RomSaveIntegrityTest.STATE_SIZE_256K) { 0x71 })
            }
            AtomicSaveFile.preserveTimestampHook = { _, _ -> false }
            try {
                assertTrue(AtomicSaveFile.copyFromStaging(stagingFile, autoResumeFile))
                assertTrue(AtomicSaveFile.writeBytes(slot2File, ByteArray(RomSaveIntegrityTest.STATE_SIZE_256K) { 0x72 }))
            } finally {
                AtomicSaveFile.preserveTimestampHook = null
            }

            assertFalse(
                "Failed rotation timestamp via copyFromStaging must not publish .bak.mtime",
                AtomicSaveFile.backupProvenanceFile(autoResumeFile).exists()
            )
            assertFalse(
                "Failed rotation timestamp via writeBytes must not publish .bak.mtime",
                AtomicSaveFile.backupProvenanceFile(slot2File).exists()
            )
            val autoBak = File(autoResumeFile.parentFile, "${autoResumeFile.name}.bak")
            val slot2Bak = File(slot2File.parentFile, "${slot2File.name}.bak")
            assertTrue("Backup must remain on disk for explicit restore", autoBak.exists())
            assertTrue("Backup must remain on disk for explicit restore", slot2Bak.exists())
            assertTrue("Rotation-time backup mtime is newer than battery.sav", autoBak.lastModified() >= batteryFile.lastModified())
            assertTrue("Rotation-time backup mtime is newer than battery.sav", slot2Bak.lastModified() >= batteryFile.lastModified())

            // Truncate both canonical targets so recovery pulls from the failed-rotation backups.
            autoResumeFile.writeBytes(ByteArray(16) { 0xEE.toByte() })
            slot2File.writeBytes(ByteArray(16) { 0xEE.toByte() })
            ssm.getCanonicalFile(romIdentity, SaveStateFiles.PRESERVED_AUTO_RESUME).apply {
                writeText(SaveStateFiles.AUTO_RESUME, Charsets.UTF_8)
            }

            // Boot 1 in Softcore: even with normal setLastModified working now, recovery must NOT
            // trust the rotation-time .bak timestamps and must NOT auto-load either state.
            core.armRaHardcore(false)
            val session1 = (sessions.switchRomFile(romFile, emptyList(), preferredTitle = "Rom Rot Fail") as SwitchResult.Success).identity
            assertEquals("Failed-rotation backup must not auto-load over newer battery.sav", 0, bridge.loadedStates.size)
            assertTrue(AtomicSaveFile.isAutoLoadExcluded(autoResumeFile))
            assertTrue(AtomicSaveFile.isAutoLoadExcluded(slot2File))
            assertArrayEquals(olderStateBytes, autoResumeFile.readBytes())
            assertArrayEquals(olderSlot2Bytes, slot2File.readBytes())
            assertArrayEquals(newerBatteryBytes, batteryFile.readBytes())
            assertEquals(SaveStateFiles.AUTO_RESUME, ssm.preservedAutoResumeFor(session1)?.resumeFileName)

            // Boot 2 in Softcore BEFORE explicit restore: durable .no_autoload keeps both excluded
            // even though both recovered targets have mtimes >= battery.sav.
            assertTrue(core.unloadRom())
            ssm.activeIdentity = null
            assertTrue(autoResumeFile.lastModified() >= batteryFile.lastModified())
            assertTrue(slot2File.lastModified() >= batteryFile.lastModified())
            core.armRaHardcore(false)
            val session2 = (sessions.switchRomFile(romFile, emptyList(), preferredTitle = "Rom Rot Fail") as SwitchResult.Success).identity
            assertEquals("Second Softcore boot before explicit restore must still not auto-load", 0, bridge.loadedStates.size)
            assertTrue(AtomicSaveFile.isAutoLoadExcluded(autoResumeFile))
            assertTrue(AtomicSaveFile.isAutoLoadExcluded(slot2File))

            // Explicit restores still succeed and clear the durable exclusion markers.
            val preserved2 = ssm.preservedAutoResumeFor(session2)!!
            assertTrue(ssm.restorePreservedAutoResume(session2, preserved2))
            assertEquals(1, bridge.loadedStates.size)
            assertArrayEquals(olderStateBytes, bridge.loadedStates[0])
            assertFalse(AtomicSaveFile.isAutoLoadExcluded(autoResumeFile))

            assertTrue(ssm.loadSlot(session2, 2))
            assertEquals(2, bridge.loadedStates.size)
            assertArrayEquals(olderSlot2Bytes, bridge.loadedStates[1])
            assertFalse(AtomicSaveFile.isAutoLoadExcluded(slot2File))
        } finally {
            AtomicSaveFile.preserveTimestampHook = null
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun preFixBackupWithoutProvenanceAndSlotInfoRecoveryNeverAutoLoadAcrossReopens() = runBlocking {
        val tempRoot = Files.createTempDirectory("dualdex_ra_prefix_bak_").toFile()
        try {
            val romFile = File(tempRoot, "romPreFix.gba").apply { writeBytes(ByteArray(4096) { 0x68 }) }
            val romIdentity = RomIdentity.fromFile(romFile, "Rom PreFix")
            val bridge = StateBytesCoreBridge(stateSize = RomSaveIntegrityTest.STATE_SIZE_256K)
            val core = LibretroCoreCoordinator(bridge = bridge)
            val ssm = SaveStateManager(customBaseDir = File(tempRoot, "saves"), customCoordinator = core)
            val sessions = RomSessionManager(
                saveStateManager = ssm,
                customRomCacheDir = File(tempRoot, "cache"),
                customCoordinator = core
            )

            val now = System.currentTimeMillis()
            val newerBatteryBytes = ByteArray(RomSaveIntegrityTest.SRAM_SIZE_128K) { 0x0B }
            val preFixAutoBytes = ByteArray(RomSaveIntegrityTest.STATE_SIZE_256K) { 0x4E }
            val preFixSlot1Bytes = ByteArray(RomSaveIntegrityTest.STATE_SIZE_256K) { 0x4F }

            val batteryFile = ssm.getCanonicalFile(romIdentity, SaveStateFiles.BATTERY).apply {
                writeBytes(newerBatteryBytes)
                setLastModified(now - 5_000L)
            }
            // Pre-fix backups: NO .bak.mtime sidecar, carrying copy-time mtime newer than battery.sav.
            val autoResumeFile = ssm.getCanonicalFile(romIdentity, SaveStateFiles.AUTO_RESUME).apply {
                writeBytes(ByteArray(16) { 0xEE.toByte() })
            }
            File(autoResumeFile.parentFile, "${autoResumeFile.name}.bak").apply {
                writeBytes(preFixAutoBytes)
                setLastModified(now)
            }
            assertFalse(AtomicSaveFile.backupProvenanceFile(autoResumeFile).exists())

            val slot1File = ssm.getCanonicalFile(romIdentity, SaveStateFiles.slotState(1)).apply {
                writeBytes(ByteArray(16) { 0xEE.toByte() })
            }
            File(slot1File.parentFile, "${slot1File.name}.bak").apply {
                writeBytes(preFixSlot1Bytes)
                setLastModified(now + 1_000L)
            }
            assertFalse(AtomicSaveFile.backupProvenanceFile(slot1File).exists())

            // Trigger recovery of slot_1.state via getSlotInfo (which calls the Boolean recoverInterrupted wrapper).
            val slot1Info = ssm.getSlotInfo(romIdentity, 1)
            assertTrue("getSlotInfo should recover pre-fix slot_1.state for explicit restore", slot1Info.exists)
            assertEquals(RomSaveIntegrityTest.STATE_SIZE_256K.toLong(), slot1Info.sizeBytes)
            assertTrue(
                "Boolean recoverInterrupted wrapper via getSlotInfo must still persist durable .no_autoload",
                AtomicSaveFile.isAutoLoadExcluded(slot1File)
            )
            assertTrue(slot1File.lastModified() >= batteryFile.lastModified())

            // Boot 1 in Softcore: neither auto_resume.state nor slot_1.state may auto-load.
            core.armRaHardcore(false)
            val session1 = (sessions.switchRomFile(romFile, emptyList(), preferredTitle = "Rom PreFix") as SwitchResult.Success).identity
            assertTrue(session1.isValid)
            assertEquals("Pre-fix backups without provenance must not auto-load", 0, bridge.loadedStates.size)
            assertTrue(AtomicSaveFile.isAutoLoadExcluded(autoResumeFile))
            assertTrue(AtomicSaveFile.isAutoLoadExcluded(slot1File))
            assertArrayEquals(preFixAutoBytes, autoResumeFile.readBytes())
            assertArrayEquals(preFixSlot1Bytes, slot1File.readBytes())

            // Reopen in Softcore (Boot 2) BEFORE explicit restoration (fresh SaveStateManager) ->
            // STILL not auto-loaded even though both recovered states are newer than battery.sav.
            assertTrue(core.unloadRom())
            ssm.activeIdentity = null
            assertTrue(autoResumeFile.lastModified() >= batteryFile.lastModified())
            assertTrue(slot1File.lastModified() >= batteryFile.lastModified())
            val ssmReopen = SaveStateManager(customBaseDir = File(tempRoot, "saves"), customCoordinator = core)
            val sessionsReopen = RomSessionManager(
                saveStateManager = ssmReopen,
                customRomCacheDir = File(tempRoot, "cache"),
                customCoordinator = core
            )
            core.armRaHardcore(false)
            val session2 = (sessionsReopen.switchRomFile(romFile, emptyList(), preferredTitle = "Rom PreFix") as SwitchResult.Success).identity
            assertEquals("Reopening in Softcore before explicit restore must still not auto-load pre-fix backups", 0, bridge.loadedStates.size)
            assertTrue(AtomicSaveFile.isAutoLoadExcluded(autoResumeFile))
            assertTrue(AtomicSaveFile.isAutoLoadExcluded(slot1File))

            // Explicit restoration via loadAutoResume and loadSlot still succeeds cleanly.
            assertTrue(ssmReopen.loadAutoResume(session2))
            assertEquals(1, bridge.loadedStates.size)
            assertArrayEquals(preFixAutoBytes, bridge.loadedStates[0])
            assertFalse(AtomicSaveFile.isAutoLoadExcluded(autoResumeFile))

            assertTrue(ssmReopen.loadSlot(session2, 1))
            assertEquals(2, bridge.loadedStates.size)
            assertArrayEquals(preFixSlot1Bytes, bridge.loadedStates[1])
            assertFalse(AtomicSaveFile.isAutoLoadExcluded(slot1File))
        } finally {
            AtomicSaveFile.preserveTimestampHook = null
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun failedPreservedMarkerWritePropagatesFailureAndRefusesUnprotectedSession() = runBlocking {
        val tempRoot = Files.createTempDirectory("dualdex_ra_marker_fail_").toFile()
        try {
            val romFile = File(tempRoot, "romMarkerFail.gba").apply { writeBytes(ByteArray(4096) { 0x65 }) }
            val romIdentity = RomIdentity.fromFile(romFile, "Rom Marker Fail")
            val bridge = StateBytesCoreBridge(stateSize = RomSaveIntegrityTest.STATE_SIZE_256K)
            val core = LibretroCoreCoordinator(bridge = bridge)
            val ssm = SaveStateManager(customBaseDir = File(tempRoot, "saves"), customCoordinator = core)
            val sessions = RomSessionManager(
                saveStateManager = ssm,
                customRomCacheDir = File(tempRoot, "cache"),
                customCoordinator = core
            )

            val seededMtime = System.currentTimeMillis() - 1_000L
            val originalAutoResumeBytes = ByteArray(RomSaveIntegrityTest.STATE_SIZE_256K) { 0x5C }
            val originalBatteryBytes = ByteArray(RomSaveIntegrityTest.SRAM_SIZE_128K) { 0x01 }
            val batteryFile = ssm.getCanonicalFile(romIdentity, SaveStateFiles.BATTERY).apply {
                writeBytes(originalBatteryBytes)
                setLastModified(seededMtime)
            }
            val autoResumeFile = ssm.getCanonicalFile(romIdentity, SaveStateFiles.AUTO_RESUME).apply {
                writeBytes(originalAutoResumeBytes)
                setLastModified(seededMtime)
            }
            val markerFile = ssm.getCanonicalFile(romIdentity, SaveStateFiles.PRESERVED_AUTO_RESUME)

            // Part A: RomSessionManager refuses to proceed with a skipped-resume session when marker persistence fails.
            core.armRaHardcore(true)
            AtomicSaveFile.syncHook = { throw IOException("Simulated atomic marker write failure") }
            val switchResult = try {
                sessions.switchRomFile(romFile, emptyList(), preferredTitle = "Rom Marker Fail")
            } finally {
                AtomicSaveFile.syncHook = null
            }
            assertTrue("Expected SwitchResult.Failure when durable marker write fails, got $switchResult", switchResult is SwitchResult.Failure)
            assertNull(ssm.activeIdentity)
            assertNull(ssm.preservedAutoResume.value)
            assertEquals(LoadedRomStatus.UNLOADED, core.loadedRomSnapshot().status)
            assertFalse(markerFile.exists())
            assertArrayEquals(originalAutoResumeBytes, autoResumeFile.readBytes())
            assertArrayEquals(originalBatteryBytes, batteryFile.readBytes())

            // Part B: Direct SaveStateManager call propagates Failed, publishes no preserved record,
            // and fails closed on saveAutoResume and closeGame without flushing SRAM or unloading.
            assertTrue(core.loadVerifiedRom(romFile.absolutePath, romIdentity))
            ssm.setActiveGame(romIdentity)
            AtomicSaveFile.syncHook = { throw IOException("Simulated atomic marker write failure") }
            val preserveResult = try {
                ssm.preserveSkippedAutoResume(romIdentity)
            } finally {
                AtomicSaveFile.syncHook = null
            }
            assertTrue("Expected PreserveAutoResumeResult.Failed, got $preserveResult", preserveResult is PreserveAutoResumeResult.Failed)
            assertNull(ssm.preservedAutoResumeFor(romIdentity))
            assertFalse(markerFile.exists())

            bridge.nextSavedStateByte = 0x7A
            assertFalse("saveAutoResume must fail closed after marker write failure", ssm.saveAutoResume(romIdentity))
            assertArrayEquals(originalAutoResumeBytes, autoResumeFile.readBytes())

            bridge.nextSramByte = 0x77
            assertFalse("closeGame must not flush changed SRAM or unload unprotected session", ssm.closeGame(romIdentity))
            assertArrayEquals(originalBatteryBytes, batteryFile.readBytes())
            assertArrayEquals(originalAutoResumeBytes, autoResumeFile.readBytes())
            assertNotNull(ssm.activeIdentity)
        } finally {
            AtomicSaveFile.syncHook = null
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun concurrentSlotInspectionCannotInterleaveWithSaveSlotToOverwriteFreshSave() {
        val tempRoot = Files.createTempDirectory("dualdex_ra_slot_info_race_").toFile()
        try {
            val romFile = File(tempRoot, "romSlotRace.gba").apply { writeBytes(ByteArray(4096) { 0x67 }) }
            val romIdentity = RomIdentity.fromFile(romFile, "Rom Slot Race")
            val bridge = StateBytesCoreBridge(stateSize = RomSaveIntegrityTest.STATE_SIZE_256K)
            val core = LibretroCoreCoordinator(bridge = bridge)
            val ssm = SaveStateManager(customBaseDir = File(tempRoot, "saves"), customCoordinator = core)
            assertTrue(core.loadVerifiedRom(romFile.absolutePath, romIdentity))
            ssm.setActiveGame(romIdentity)

            val slot1File = ssm.getCanonicalFile(romIdentity, SaveStateFiles.slotState(1))
            val preFixBakBytes = ByteArray(RomSaveIntegrityTest.STATE_SIZE_256K) { 0x22 }
            File(slot1File.parentFile, "${slot1File.name}.bak").writeBytes(preFixBakBytes)
            assertFalse(slot1File.exists())

            val enteredRestore = CountDownLatch(1)
            val allowRestoreFinish = CountDownLatch(1)
            val saveCompleted = CountDownLatch(1)
            val saveSucceeded = AtomicBoolean(false)
            AtomicSaveFile.beforeRestoreCopyHook = { target ->
                if (target.name == slot1File.name) {
                    enteredRestore.countDown()
                    assertTrue(allowRestoreFinish.await(5, TimeUnit.SECONDS))
                }
            }

            val inspectThread = Thread {
                ssm.getSlotInfo(romIdentity, 1)
            }
            inspectThread.start()
            assertTrue("getSlotInfo must reach backup recovery", enteredRestore.await(5, TimeUnit.SECONDS))

            bridge.nextSavedStateByte = 0x77
            val saveThread = Thread {
                saveSucceeded.set(ssm.saveSlot(romIdentity, 1))
                saveCompleted.countDown()
            }
            saveThread.start()

            // saveSlot must stay serialized behind getSlotInfo under globalSaveLock.
            assertFalse(
                "saveSlot must not interleave with getSlotInfo recovery",
                saveCompleted.await(150, TimeUnit.MILLISECONDS)
            )

            allowRestoreFinish.countDown()
            inspectThread.join(5_000)
            saveThread.join(5_000)
            assertFalse(inspectThread.isAlive)
            assertFalse(saveThread.isAlive)
            assertTrue(saveSucceeded.get())

            // Fresh save bytes win cleanly; old backup was rotated, not copied back over fresh save.
            assertArrayEquals(ByteArray(RomSaveIntegrityTest.STATE_SIZE_256K) { 0x77 }, slot1File.readBytes())
            assertFalse(AtomicSaveFile.isAutoLoadExcluded(slot1File))
            assertArrayEquals(preFixBakBytes, File(slot1File.parentFile, "${slot1File.name}.bak").readBytes())
        } finally {
            AtomicSaveFile.beforeRestoreCopyHook = null
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun oneShotExclusionMarkerFailureDuringBootNeverRetriesRecoveryToAutoLoadOlderBackup() {
        val tempRoot = Files.createTempDirectory("dualdex_ra_oneshot_boot_").toFile()
        try {
            val romFile = File(tempRoot, "romOneShotBoot.gba").apply { writeBytes(ByteArray(4096) { 0x68 }) }
            val romIdentity = RomIdentity.fromFile(romFile, "Rom OneShot Boot")
            val bridge = StateBytesCoreBridge(stateSize = RomSaveIntegrityTest.STATE_SIZE_256K)
            val core = LibretroCoreCoordinator(bridge = bridge)
            val ssm = SaveStateManager(customBaseDir = File(tempRoot, "saves"), customCoordinator = core)
            assertTrue(core.loadVerifiedRom(romFile.absolutePath, romIdentity))
            ssm.setActiveGame(romIdentity)

            val now = System.currentTimeMillis()
            val olderBackupMtime = now - 60_000L
            val newerBatteryMtime = now - 5_000L
            val truncatedMtime = now

            val batteryFile = ssm.getCanonicalFile(romIdentity, SaveStateFiles.BATTERY).apply {
                writeBytes(ByteArray(RomSaveIntegrityTest.SRAM_SIZE_128K) { 0x55 })
                setLastModified(newerBatteryMtime)
            }
            val autoResumeFile = ssm.getCanonicalFile(romIdentity, SaveStateFiles.AUTO_RESUME).apply {
                // Nonempty truncated target with a recent timestamp.
                writeBytes(ByteArray(64) { 0x11 })
                setLastModified(truncatedMtime)
            }
            val oldBackupBytes = ByteArray(RomSaveIntegrityTest.STATE_SIZE_256K) { 0x3A }
            File(autoResumeFile.parentFile, "${autoResumeFile.name}.bak").apply {
                writeBytes(oldBackupBytes)
                setLastModified(olderBackupMtime)
            }

            // Fail ONLY the first sync (during recoverResumeCandidatesLocked's .no_autoload write);
            // a hypothetical retry inside loadStateFile would succeed if not prevented.
            val failuresRemaining = AtomicInteger(1)
            AtomicSaveFile.syncHook = {
                if (failuresRemaining.getAndDecrement() > 0) {
                    throw IOException("One-shot exclusion marker write failure")
                }
            }

            assertNull(
                "Boot resume must exclude failed recovery and never retry recovery inside loadStateFile",
                ssm.loadNewestResumeState(romIdentity)
            )
            assertTrue("No stale backup bytes may be auto-loaded", bridge.loadedStates.isEmpty())
            assertEquals(64L, autoResumeFile.length())
            assertTrue(batteryFile.exists())

            // The next automatic save must preserve the valid 256 KiB .bak instead of rotating
            // the 64-byte truncated canonical file over it.
            AtomicSaveFile.syncHook = null
            bridge.nextSavedStateByte = 0x7E
            assertTrue(ssm.saveAutoResume(romIdentity))
            assertArrayEquals(ByteArray(RomSaveIntegrityTest.STATE_SIZE_256K) { 0x7E }, autoResumeFile.readBytes())
            val bakFile = File(autoResumeFile.parentFile, "${autoResumeFile.name}.bak")
            assertArrayEquals(
                "Valid .bak must survive automatic save after failed recovery left truncated canonical target",
                oldBackupBytes,
                bakFile.readBytes()
            )
        } finally {
            AtomicSaveFile.syncHook = null
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun incompleteLegacyMigrationRetriesMissingExcludedSlotWithoutOverwritingNewerCanonicalBattery() {
        val tempRoot = Files.createTempDirectory("dualdex_ra_legacy_retry_").toFile()
        try {
            val romFile = File(tempRoot, "romLegacyRetry.gba").apply { writeBytes(ByteArray(4096) { 0x6B }) }
            val identity = RomIdentity.fromFile(romFile, "Rom Legacy Retry")
            val savesDir = File(tempRoot, "saves")
            val fallbackDir = File(savesDir, "saves_fallback")
            val oldBranchDir = File(fallbackDir, identity.legacyStorageKey).apply { mkdirs() }

            val oldBatteryBytes = ByteArray(RomSaveIntegrityTest.SRAM_SIZE_128K) { 0x11 }
            val legacySlot1Bytes = ByteArray(RomSaveIntegrityTest.STATE_SIZE_256K) { 0x4F }
            File(oldBranchDir, SaveStateFiles.BATTERY).writeBytes(oldBatteryBytes)
            val oldSlot1 = File(oldBranchDir, SaveStateFiles.slotState(1)).apply { writeBytes(legacySlot1Bytes) }
            AtomicSaveFile.autoLoadExcludedFile(oldSlot1).writeText("1")

            val bridge = StateBytesCoreBridge(stateSize = RomSaveIntegrityTest.STATE_SIZE_256K)
            val core = LibretroCoreCoordinator(bridge = bridge)
            val ssm = SaveStateManager(customBaseDir = savesDir, customCoordinator = core)
            assertTrue(core.loadVerifiedRom(romFile.absolutePath, identity))

            // Simulate a transient failure when persisting slot_1.state.no_autoload during migration,
            // while allowing battery.sav (and slot_1.state.tmp) to sync.
            val canonicalDir = ssm.getCanonicalRomDir(identity)
            val slot1NoAutoloadTmp = File(canonicalDir, "${SaveStateFiles.slotState(1)}.no_autoload.tmp")
            AtomicSaveFile.syncHook = {
                if (slot1NoAutoloadTmp.exists()) {
                    throw IOException("Simulated transient exclusion marker write failure during migration")
                }
            }
            try {
                ssm.setActiveGame(identity)
            } finally {
                AtomicSaveFile.syncHook = null
            }

            // Battery migrated; excluded slot_1 did not; old directory stayed in place for retry.
            val canonicalBattery = ssm.getCanonicalFile(identity, SaveStateFiles.BATTERY)
            val canonicalSlot1 = ssm.getCanonicalFile(identity, SaveStateFiles.slotState(1))
            assertTrue(canonicalBattery.exists())
            assertFalse(canonicalSlot1.exists())
            assertTrue("Incomplete migration must keep old directory intact", oldBranchDir.exists())

            // Simulate gameplay updating canonical battery.sav before reopening.
            val newerBatteryBytes = ByteArray(RomSaveIntegrityTest.SRAM_SIZE_128K) { 0x99.toByte() }
            canonicalBattery.writeBytes(newerBatteryBytes)

            // Reopen in a fresh SaveStateManager: migration resumes for the missing excluded slot
            // without overwriting the newer canonical battery.sav.
            val ssmReopen = SaveStateManager(customBaseDir = savesDir, customCoordinator = core)
            ssmReopen.setActiveGame(identity)

            assertArrayEquals(
                "Resumed migration must never overwrite newer canonical battery.sav",
                newerBatteryBytes,
                canonicalBattery.readBytes()
            )
            assertTrue("Missing legacy slot_1 must be migrated on reopen", canonicalSlot1.exists())
            assertArrayEquals(legacySlot1Bytes, canonicalSlot1.readBytes())
            assertTrue(
                "Retried legacy slot_1 must preserve durable .no_autoload exclusion",
                AtomicSaveFile.isAutoLoadExcluded(canonicalSlot1)
            )
            assertFalse("Completed migration must archive old directory", oldBranchDir.exists())

            // Excluded migrated slot is not auto-loaded at boot, but remains explicitly restorable.
            assertNull(ssmReopen.loadNewestResumeState(identity))
            assertTrue(bridge.loadedStates.isEmpty())
            assertTrue(ssmReopen.loadSlot(identity, 1))
            assertEquals(1, bridge.loadedStates.size)
            assertArrayEquals(legacySlot1Bytes, bridge.loadedStates[0])
        } finally {
            AtomicSaveFile.syncHook = null
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun batteryImportAndExportPreserveValidBackupWhenCanonicalTruncated() {
        val tempRoot = Files.createTempDirectory("dualdex_ra_sram_bak_preserve_").toFile()
        try {
            val romFile = File(tempRoot, "romSramBak.gba").apply { writeBytes(ByteArray(4096) { 0x6C }) }
            val identity = RomIdentity.fromFile(romFile, "Rom Sram Bak")
            val bridge = StateBytesCoreBridge(stateSize = RomSaveIntegrityTest.STATE_SIZE_256K)
            val core = LibretroCoreCoordinator(bridge = bridge)
            val ssm = SaveStateManager(customBaseDir = File(tempRoot, "saves"), customCoordinator = core)
            assertTrue(core.loadVerifiedRom(romFile.absolutePath, identity))
            ssm.setActiveGame(identity)

            val batteryFile = ssm.getCanonicalFile(identity, SaveStateFiles.BATTERY)
            val bakFile = File(batteryFile.parentFile, "${batteryFile.name}.bak")

            // Part A: importBatterySave must not rotate a 64-byte truncated battery.sav over a valid 128 KiB .bak.
            val originalImportBak = ByteArray(RomSaveIntegrityTest.SRAM_SIZE_128K) { 0x2B }
            val importedBytes = ByteArray(RomSaveIntegrityTest.SRAM_SIZE_128K) { 0x6C }
            bakFile.writeBytes(originalImportBak)
            batteryFile.writeBytes(ByteArray(64) { 0x09 })

            assertTrue(ssm.importBatterySave(identity, importedBytes.inputStream()))
            assertArrayEquals(importedBytes, batteryFile.readBytes())
            assertArrayEquals(
                "importBatterySave must preserve valid 128 KiB .bak when canonical battery.sav is truncated",
                originalImportBak,
                bakFile.readBytes()
            )

            // Part B: exportBatterySave must not rotate a 64-byte truncated battery.sav over a valid 128 KiB .bak.
            val originalExportBak = ByteArray(RomSaveIntegrityTest.SRAM_SIZE_128K) { 0x3D }
            bakFile.writeBytes(originalExportBak)
            batteryFile.writeBytes(ByteArray(64) { 0x0A })
            bridge.nextSramByte = 0x7E

            val exported = java.io.ByteArrayOutputStream()
            assertTrue(ssm.exportBatterySave(identity, exported))
            val expectedExported = ByteArray(RomSaveIntegrityTest.SRAM_SIZE_128K) { 0x7E }
            assertArrayEquals(expectedExported, exported.toByteArray())
            assertArrayEquals(expectedExported, batteryFile.readBytes())
            assertArrayEquals(
                "exportBatterySave must preserve valid 128 KiB .bak when canonical battery.sav is truncated",
                originalExportBak,
                bakFile.readBytes()
            )
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun incompleteLegacyMigrationRetryPreservesUnmarkedOlderSlotFreshnessOrExcludesIt() {
        val tempRoot = Files.createTempDirectory("dualdex_ra_legacy_unmarked_").toFile()
        try {
            // Part A: Unmarked legacy slot older than canonical battery keeps its original timestamp on retry
            // so automatic boot never loads the old state over newer live SRAM.
            val romFileA = File(tempRoot, "romUnmarkedA.gba").apply { writeBytes(ByteArray(4096) { 0x6D }) }
            val identityA = RomIdentity.fromFile(romFileA, "Rom Unmarked A")
            val savesDirA = File(tempRoot, "savesA")
            val oldDirA = File(File(savesDirA, "saves_fallback"), identityA.legacyStorageKey).apply { mkdirs() }

            val now = System.currentTimeMillis()
            val olderSlotMtime = ((now - 120_000L) / 1000L) * 1000L
            val newerBatteryMtime = ((now - 5_000L) / 1000L) * 1000L
            val legacySlot2Bytes = ByteArray(RomSaveIntegrityTest.STATE_SIZE_256K) { 0x52 }
            File(oldDirA, SaveStateFiles.BATTERY).writeBytes(ByteArray(RomSaveIntegrityTest.SRAM_SIZE_128K) { 0x12 })
            File(oldDirA, SaveStateFiles.slotState(2)).apply {
                writeBytes(legacySlot2Bytes)
                setLastModified(olderSlotMtime)
            }

            val bridgeA = StateBytesCoreBridge(stateSize = RomSaveIntegrityTest.STATE_SIZE_256K)
            val coreA = LibretroCoreCoordinator(bridge = bridgeA)
            val ssmA = SaveStateManager(customBaseDir = savesDirA, customCoordinator = coreA)
            assertTrue(coreA.loadVerifiedRom(romFileA.absolutePath, identityA))

            val canonicalDirA = ssmA.getCanonicalRomDir(identityA)
            val slot2NoAutoloadTmp = File(canonicalDirA, "${SaveStateFiles.slotState(2)}.no_autoload.tmp")
            AtomicSaveFile.syncHook = {
                if (slot2NoAutoloadTmp.exists()) {
                    throw IOException("Transient failure during unmarked slot migration")
                }
            }
            try {
                ssmA.setActiveGame(identityA)
            } finally {
                AtomicSaveFile.syncHook = null
            }
            val canonicalBatteryA = ssmA.getCanonicalFile(identityA, SaveStateFiles.BATTERY)
            val canonicalSlot2A = ssmA.getCanonicalFile(identityA, SaveStateFiles.slotState(2))
            assertTrue(canonicalBatteryA.exists())
            assertFalse(canonicalSlot2A.exists())

            canonicalBatteryA.writeBytes(ByteArray(RomSaveIntegrityTest.SRAM_SIZE_128K) { 0x77 })
            canonicalBatteryA.setLastModified(newerBatteryMtime)

            val ssmReopenA = SaveStateManager(customBaseDir = savesDirA, customCoordinator = coreA)
            ssmReopenA.setActiveGame(identityA)

            assertTrue(canonicalSlot2A.exists())
            assertEquals(olderSlotMtime, canonicalSlot2A.lastModified())
            assertNull(
                "Unmarked older legacy slot migrated on retry must not auto-load over newer canonical battery",
                ssmReopenA.loadNewestResumeState(identityA)
            )
            assertTrue(bridgeA.loadedStates.isEmpty())
            assertTrue(ssmReopenA.loadSlot(identityA, 2))
            assertArrayEquals(legacySlot2Bytes, bridgeA.loadedStates.single())

            // Part B: If timestamp preservation fails on retry, durable .no_autoload keeps the unmarked slot
            // excluded from automatic boot across reopens while allowing explicit load.
            val romFileB = File(tempRoot, "romUnmarkedB.gba").apply { writeBytes(ByteArray(4096) { 0x6E }) }
            val identityB = RomIdentity.fromFile(romFileB, "Rom Unmarked B")
            val savesDirB = File(tempRoot, "savesB")
            val oldDirB = File(File(savesDirB, "saves_fallback"), identityB.legacyStorageKey).apply { mkdirs() }
            File(oldDirB, ".migration_in_progress").writeText("1")
            File(oldDirB, SaveStateFiles.slotState(2)).apply {
                writeBytes(legacySlot2Bytes)
                setLastModified(olderSlotMtime)
            }

            val bridgeB = StateBytesCoreBridge(stateSize = RomSaveIntegrityTest.STATE_SIZE_256K)
            val coreB = LibretroCoreCoordinator(bridge = bridgeB)
            val ssmB = SaveStateManager(customBaseDir = savesDirB, customCoordinator = coreB)
            assertTrue(coreB.loadVerifiedRom(romFileB.absolutePath, identityB))
            ssmB.getCanonicalFile(identityB, SaveStateFiles.BATTERY).apply {
                writeBytes(ByteArray(RomSaveIntegrityTest.SRAM_SIZE_128K) { 0x78 })
                setLastModified(newerBatteryMtime)
            }

            AtomicSaveFile.preserveTimestampHook = { _, _ -> false }
            try {
                ssmB.setActiveGame(identityB)
            } finally {
                AtomicSaveFile.preserveTimestampHook = null
            }

            val canonicalSlot2B = ssmB.getCanonicalFile(identityB, SaveStateFiles.slotState(2))
            assertTrue(canonicalSlot2B.exists())
            assertTrue(
                "Unverified migrated slot must remain durably excluded when timestamp preservation fails",
                AtomicSaveFile.isAutoLoadExcluded(canonicalSlot2B)
            )
            assertNull(ssmB.loadNewestResumeState(identityB))
            assertTrue(bridgeB.loadedStates.isEmpty())
            assertTrue(ssmB.loadSlot(identityB, 2))
            assertArrayEquals(legacySlot2Bytes, bridgeB.loadedStates.single())
        } finally {
            AtomicSaveFile.syncHook = null
            AtomicSaveFile.preserveTimestampHook = null
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun migratingOlderBatteryBarrierAndNewerStateTogetherPreservesBootResumeSelection() {
        val tempRoot = Files.createTempDirectory("dualdex_ra_legacy_together_").toFile()
        try {
            val romFile = File(tempRoot, "romMigrateTogether.gba").apply { writeBytes(ByteArray(4096) { 0x6F }) }
            val identity = RomIdentity.fromFile(romFile, "Rom Migrate Together")
            val savesDir = File(tempRoot, "saves")
            val oldDir = File(File(savesDir, "saves_fallback"), identity.legacyStorageKey).apply { mkdirs() }

            val now = System.currentTimeMillis()
            val barrierMtime = ((now - 600_000L) / 1000L) * 1000L
            val batteryMtime = ((now - 300_000L) / 1000L) * 1000L
            val newerStateMtime = ((now - 60_000L) / 1000L) * 1000L

            val oldBatteryBytes = ByteArray(RomSaveIntegrityTest.SRAM_SIZE_128K) { 0x21 }
            val newerResumeBytes = ByteArray(RomSaveIntegrityTest.STATE_SIZE_256K) { 0x63 }

            File(oldDir, SaveStateFiles.RESUME_BARRIER).apply {
                writeText(barrierMtime.toString())
                setLastModified(barrierMtime)
            }
            File(oldDir, SaveStateFiles.BATTERY).apply {
                writeBytes(oldBatteryBytes)
                setLastModified(batteryMtime)
            }
            File(oldDir, SaveStateFiles.AUTO_RESUME).apply {
                writeBytes(newerResumeBytes)
                setLastModified(newerStateMtime)
            }

            val bridge = StateBytesCoreBridge(stateSize = RomSaveIntegrityTest.STATE_SIZE_256K)
            val core = LibretroCoreCoordinator(bridge = bridge)
            val ssm = SaveStateManager(customBaseDir = savesDir, customCoordinator = core)
            assertTrue(core.loadVerifiedRom(romFile.absolutePath, identity))

            // First attempt: fail timestamp verification on battery.sav / resume_barrier.
            // Neither file may be published to canonical storage, and oldDir must stay retryable.
            AtomicSaveFile.preserveTimestampHook = { file, mtime ->
                if (file.name.startsWith(SaveStateFiles.BATTERY) ||
                    file.name.startsWith(SaveStateFiles.RESUME_BARRIER)
                ) {
                    false
                } else {
                    file.setLastModified(mtime)
                }
            }
            try {
                ssm.setActiveGame(identity)
            } finally {
                AtomicSaveFile.preserveTimestampHook = null
            }
            val canonicalBarrier = ssm.getCanonicalFile(identity, SaveStateFiles.RESUME_BARRIER)
            val canonicalBattery = ssm.getCanonicalFile(identity, SaveStateFiles.BATTERY)
            val canonicalAuto = ssm.getCanonicalFile(identity, SaveStateFiles.AUTO_RESUME)
            assertFalse("Failed timestamp verification must not publish battery.sav", canonicalBattery.exists())
            assertFalse("Failed timestamp verification must not publish resume_barrier", canonicalBarrier.exists())
            assertTrue("Old directory must stay intact for retry", oldDir.exists())

            // Second attempt (retry): timestamp preservation succeeds; battery and barrier publish
            // with original timestamps, and boot resume loads the newer unmarked state.
            val ssmRetry = SaveStateManager(customBaseDir = savesDir, customCoordinator = core)
            ssmRetry.setActiveGame(identity)

            assertFalse("Completed migration must archive old directory", oldDir.exists())
            assertEquals(barrierMtime, canonicalBarrier.lastModified())
            assertEquals(batteryMtime, canonicalBattery.lastModified())
            assertEquals(newerStateMtime, canonicalAuto.lastModified())
            assertFalse(AtomicSaveFile.isAutoLoadExcluded(canonicalAuto))

            assertEquals(
                "Migrating an older battery + barrier alongside a newer unmarked state must preserve boot resume",
                SaveStateFiles.AUTO_RESUME,
                ssmRetry.loadNewestResumeState(identity)
            )
            assertEquals(1, bridge.loadedStates.size)
            assertArrayEquals(newerResumeBytes, bridge.loadedStates.single())
        } finally {
            AtomicSaveFile.preserveTimestampHook = null
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun legacyMigrationRejectsInternalSidecarsAndPreservesAutoLoadExclusion() {
        val tempRoot = Files.createTempDirectory("dualdex_ra_legacy_sidecar_").toFile()
        try {
            // Part A: LegacySaveCatalog ignores internal sidecars at discovery and refuses assignment,
            // and preserves .no_autoload when assigning an excluded state.
            val romFileA = File(tempRoot, "romLegacyA.gba").apply { writeBytes(ByteArray(4096) { 0x69 }) }
            val identityA = RomIdentity.fromFile(romFileA, "Rom Legacy A")
            val savesDirA = File(tempRoot, "savesA")
            val fallbackDirA = File(tempRoot, "saves_fallbackA")
            val legacyFolder = File(fallbackDirA, "legacy_RomLegacyA").apply { mkdirs() }
            val legacyStateBytes = ByteArray(RomSaveIntegrityTest.STATE_SIZE_256K) { 0x4C }
            val legacyState = File(legacyFolder, SaveStateFiles.AUTO_RESUME).apply { writeBytes(legacyStateBytes) }
            val legacyNoAutoload = AtomicSaveFile.autoLoadExcludedFile(legacyState).apply { writeText("1") }
            val legacyBak = File(legacyFolder, "${SaveStateFiles.AUTO_RESUME}.bak").apply { writeBytes(legacyStateBytes) }
            val legacyBakMtime = AtomicSaveFile.backupProvenanceFile(legacyState).apply { writeText("1000:${legacyStateBytes.size}") }

            val catalog = LegacySaveCatalog(
                context = null,
                customLegacyDir = File(tempRoot, "legacyRootA"),
                customFallbackDir = fallbackDirA
            )
            val discovered = catalog.discoverCandidates()
            assertEquals(listOf(SaveStateFiles.AUTO_RESUME), discovered.map { it.targetFileName })

            val canonicalDirA = File(savesDirA, identityA.storageKey).apply { mkdirs() }
            for (sidecar in listOf(legacyNoAutoload, legacyBak, legacyBakMtime)) {
                val bogus = LegacyCandidate(
                    sourceFile = sidecar,
                    baseName = "RomLegacyA",
                    suggestedTitle = "RomLegacyA",
                    targetFileName = sidecar.name,
                    sizeBytes = sidecar.length(),
                    isAssigned = false,
                    assignedRomHash = null
                )
                assertEquals(
                    "Internal sidecar ${sidecar.name} must never be assignable",
                    MigrationResult.FAILURE,
                    catalog.assignCandidateToRom(bogus, identityA, canonicalDirA)
                )
                assertFalse(File(canonicalDirA, sidecar.name).exists())
            }

            assertEquals(
                MigrationResult.SUCCESS,
                catalog.assignCandidateToRom(discovered.single(), identityA, canonicalDirA)
            )
            val assignedState = File(canonicalDirA, SaveStateFiles.AUTO_RESUME)
            assertArrayEquals(legacyStateBytes, assignedState.readBytes())
            assertTrue(
                "Assigned excluded state must preserve durable .no_autoload marker",
                AtomicSaveFile.isAutoLoadExcluded(assignedState)
            )

            // Part B: SaveStateManager.migrateOldBranchDirIfPresent skips internal sidecars and
            // preserves .no_autoload so migrated excluded state is never auto-loaded at boot.
            val romFileB = File(tempRoot, "romLegacyB.gba").apply { writeBytes(ByteArray(4096) { 0x6A }) }
            val identityB = RomIdentity.fromFile(romFileB, "Rom Legacy B")
            val savesDirB = File(tempRoot, "savesB")
            val fallbackDirB = File(savesDirB, "saves_fallback")
            val oldBranchDir = File(fallbackDirB, identityB.legacyStorageKey).apply { mkdirs() }
            val migratedStateBytes = ByteArray(RomSaveIntegrityTest.STATE_SIZE_256K) { 0x4E }
            val oldState = File(oldBranchDir, SaveStateFiles.AUTO_RESUME).apply { writeBytes(migratedStateBytes) }
            AtomicSaveFile.autoLoadExcludedFile(oldState).writeText("1")
            AtomicSaveFile.backupProvenanceFile(oldState).writeText("1000:${migratedStateBytes.size}")

            val bridgeB = StateBytesCoreBridge(stateSize = RomSaveIntegrityTest.STATE_SIZE_256K)
            val coreB = LibretroCoreCoordinator(bridge = bridgeB)
            val ssmB = SaveStateManager(
                customBaseDir = savesDirB,
                customCoordinator = coreB
            )
            assertTrue(coreB.loadVerifiedRom(romFileB.absolutePath, identityB))
            ssmB.setActiveGame(identityB)

            val canonicalStateB = ssmB.getCanonicalFile(identityB, SaveStateFiles.AUTO_RESUME)
            assertArrayEquals(migratedStateBytes, canonicalStateB.readBytes())
            assertFalse(AtomicSaveFile.backupProvenanceFile(canonicalStateB).exists())
            assertTrue(
                "Old-branch migration must preserve durable .no_autoload marker",
                AtomicSaveFile.isAutoLoadExcluded(canonicalStateB)
            )
            assertNull(
                "Migrated excluded state must not be auto-loaded at boot",
                ssmB.loadNewestResumeState(identityB)
            )
            assertTrue(bridgeB.loadedStates.isEmpty())

            // Explicit restore still succeeds.
            assertTrue(ssmB.loadAutoResume(identityB))
            assertEquals(1, bridgeB.loadedStates.size)
            assertArrayEquals(migratedStateBytes, bridgeB.loadedStates[0])
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
        var nextSramByte: Byte = 0x01
        val loadedStates = mutableListOf<ByteArray>()
        override fun loadRom(romPath: String) = true
        override fun unloadRom() = true
        override fun getSaveRamSize() = sramSize.toLong()
        override fun getSaveStateSize() = stateSize.toLong()
        override fun loadSaveRam(savePath: String) = true
        override fun flushSaveRam(savePath: String): Boolean {
            File(savePath).writeBytes(ByteArray(sramSize) { nextSramByte })
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
