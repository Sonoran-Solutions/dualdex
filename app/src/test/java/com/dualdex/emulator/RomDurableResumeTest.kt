package com.dualdex.emulator

import android.content.SharedPreferences
import android.net.FakeUri
import android.net.Uri
import com.dualdex.settings.SettingsManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException

class RomDurableResumeTest {

    private lateinit var testBaseDir: File
    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var settingsManager: SettingsManager
    private lateinit var testBridge: TestCoreBridge
    private lateinit var saveStateManager: SaveStateManager

    companion object {
        const val HASH_EMERALD = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"
        const val SRAM_SIZE_128K = 131072
        const val STATE_SIZE_256K = 262144
    }

    class TestCoreBridge : LibretroCoreBridge {
        var configuredSaveRamSize: Long = SRAM_SIZE_128K.toLong()
        var configuredSaveStateSize: Long = STATE_SIZE_256K.toLong()
        var loadRomResult: Boolean = true
        var unloadRomResult: Boolean = true
        var saveStateResult: Boolean = true
        var loadStateResult: Boolean = true
        var loadSaveRamResult: Boolean = true
        var flushSaveRamResult: Boolean = true

        val events = mutableListOf<String>()

        override fun loadRom(romPath: String): Boolean {
            events.add("loadRom:$romPath")
            return loadRomResult
        }
        override fun unloadRom(): Boolean {
            events.add("unloadRom")
            return unloadRomResult
        }
        override fun resetCore() {
            events.add("resetCore")
        }
        override fun stepFrame(): Boolean = true
        override fun getSaveStateSize(): Long = configuredSaveStateSize
        override fun getSaveRamSize(): Long = configuredSaveRamSize
        override fun loadSaveRam(savePath: String): Boolean = loadSaveRamResult
        override fun flushSaveRam(savePath: String): Boolean = flushSaveRamResult
        override fun saveState(statePath: String): Boolean = saveStateResult
        override fun loadState(statePath: String): Boolean = loadStateResult
    }

    class FakeSharedPreferences : SharedPreferences {
        private val storage = mutableMapOf<String, Any?>()

        override fun getAll(): Map<String, *> = HashMap(storage)
        override fun getString(key: String?, defValue: String?): String? =
            storage[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? =
            storage[key] as? Set<String> ?: defValues
        override fun getInt(key: String?, defValue: Int): Int =
            storage[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long =
            storage[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float =
            storage[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            storage[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = storage.containsKey(key)

        override fun edit(): SharedPreferences.Editor = FakeEditor(storage)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class FakeEditor(private val storage: MutableMap<String, Any?>) : SharedPreferences.Editor {
            private val pendingChanges = mutableMapOf<String, Any?>()
            private val pendingRemoves = mutableSetOf<String>()
            private var clearFlag = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) {
                    pendingRemoves.remove(key)
                    pendingChanges[key] = value
                }
                return this
            }
            override fun putStringSet(key: String?, values: Set<String>?): SharedPreferences.Editor {
                if (key != null) {
                    pendingRemoves.remove(key)
                    pendingChanges[key] = values
                }
                return this
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) {
                    pendingRemoves.remove(key)
                    pendingChanges[key] = value
                }
                return this
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) {
                    pendingRemoves.remove(key)
                    pendingChanges[key] = value
                }
                return this
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) {
                    pendingRemoves.remove(key)
                    pendingChanges[key] = value
                }
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) {
                    pendingRemoves.remove(key)
                    pendingChanges[key] = value
                }
                return this
            }
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) {
                    pendingChanges.remove(key)
                    pendingRemoves.add(key)
                }
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                clearFlag = true
                pendingChanges.clear()
                pendingRemoves.clear()
                return this
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearFlag) {
                    storage.clear()
                }
                for (key in pendingRemoves) {
                    storage.remove(key)
                }
                for ((key, value) in pendingChanges) {
                    storage[key] = value
                }
            }
        }
    }

    @Before
    fun setUp() {
        testBaseDir = File("build/test_durable_resume_${System.currentTimeMillis()}").apply { mkdirs() }
        fakePrefs = FakeSharedPreferences()
        settingsManager = SettingsManager(fakePrefs)
        testBridge = TestCoreBridge()
        saveStateManager = SaveStateManager(
            customBaseDir = File(testBaseDir, "saves"),
            coreBridge = testBridge
        )
    }

    @After
    fun tearDown() {
        testBaseDir.deleteRecursively()
    }

    private fun createRomFile(name: String, contentByte: Byte = 0x33): File {
        val bytes = ByteArray(2048) { contentByte }
        val f = File(testBaseDir, name)
        f.writeBytes(bytes)
        return f
    }

    /**
     * 1. Direct-open URI where persistable permission succeeds:
     *    -> URI is eligible to become durable Continue state.
     */
    @Test
    fun testDirectOpen_persistablePermissionSucceeds_recordsContinueState() = runBlocking {
        val cacheDir = File(testBaseDir, "cache").apply { mkdirs() }
        val romFile = createRomFile("emerald.gba", 0x44)
        val uriStr = "content://com.android.providers.media.documents/document/1234"

        val sessionManager = RomSessionManager(
            saveStateManager = saveStateManager,
            settingsManager = settingsManager,
            customRomCacheDir = cacheDir,
            coreBridge = testBridge,
            customStreamOpener = { ByteArrayInputStream(romFile.readBytes()) }
        )
        val uri = FakeUri(uriStr)
        val res = sessionManager.switchRom(
            uri = uri,
            loadedProfiles = emptyList(),
            preferredTitle = "Pokemon Emerald",
            isDurable = true
        )

        assertTrue("ROM switch should succeed", res is SwitchResult.Success)
        assertEquals("Durable direct-open must record lastPlayedRomUri", uriStr, settingsManager.lastPlayedRomUri)
        assertEquals("Durable direct-open must record lastPlayedRomTitle", "Pokemon Emerald", settingsManager.lastPlayedRomTitle)
    }

    /**
     * 2. Provider refuses persistable permission:
     *    -> app does not crash.
     *    -> behavior is explicit/conservative: ROM loads normally, but Continue state is cleared/not stored.
     */
    @Test
    fun testDirectOpen_providerRefusesPersistablePermission_noCrashAndContinueCleared() = runBlocking {
        val cacheDir = File(testBaseDir, "cache").apply { mkdirs() }
        val romFile = createRomFile("ruby.gba", 0x66)

        // Seed an existing continue state from a previous game
        settingsManager.lastPlayedRomUri = "content://old_game"
        settingsManager.lastPlayedRomTitle = "Old Game"

        val sessionManager = RomSessionManager(
            saveStateManager = saveStateManager,
            settingsManager = settingsManager,
            customRomCacheDir = cacheDir,
            coreBridge = testBridge
        )

        // Provider refused persistable permission -> isDurable = false
        val res = sessionManager.switchRomFile(
            file = romFile,
            loadedProfiles = emptyList(),
            preferredTitle = "Pokemon Ruby",
            isDurable = false
        )

        assertTrue("ROM load must still succeed for current play session", res is SwitchResult.Success)
        assertNull("Stale or un-resumable lastPlayedRomUri must NOT be advertised", settingsManager.lastPlayedRomUri)
        assertNull("Stale lastPlayedRomTitle must NOT be advertised", settingsManager.lastPlayedRomTitle)
    }

    /**
     * 3. Continue target can still be opened:
     *    -> normal ROM loading path proceeds.
     */
    @Test
    fun testContinue_targetCanBeOpened_proceedsNormally() = runBlocking {
        val cacheDir = File(testBaseDir, "cache").apply { mkdirs() }
        val romFile = createRomFile("firered.gba", 0x77)
        settingsManager.lastPlayedRomUri = romFile.absolutePath
        settingsManager.lastPlayedRomTitle = "Pokemon FireRed"

        val sessionManager = RomSessionManager(
            saveStateManager = saveStateManager,
            settingsManager = settingsManager,
            customRomCacheDir = cacheDir,
            coreBridge = testBridge
        )

        val res = sessionManager.switchRomFile(
            file = File(settingsManager.lastPlayedRomUri!!),
            loadedProfiles = emptyList(),
            preferredTitle = settingsManager.lastPlayedRomTitle,
            isDurable = true
        )

        assertTrue("Continue should succeed", res is SwitchResult.Success)
        assertEquals("Continue state remains active", romFile.absolutePath, settingsManager.lastPlayedRomUri)
        assertEquals("Pokemon FireRed", settingsManager.lastPlayedRomTitle)
    }

    /**
     * 4. Continue target throws SecurityException:
     *    -> no crash.
     *    -> stale Continue entry is cleared/invalidated.
     *    -> useful recovery result/state is produced.
     */
    @Test
    fun testContinue_targetThrowsSecurityException_recoversGracefully() = runBlocking {
        val cacheDir = File(testBaseDir, "cache").apply { mkdirs() }
        val testUriStr = "content://revoked_provider/game.gba"
        settingsManager.lastPlayedRomUri = testUriStr
        settingsManager.lastPlayedRomTitle = "Revoked Game"

        val sessionManager = RomSessionManager(
            saveStateManager = saveStateManager,
            settingsManager = settingsManager,
            customRomCacheDir = cacheDir,
            coreBridge = testBridge,
            customStreamOpener = { throw SecurityException("Permission Denial: reading requires grant") }
        )

        // Attempting to switch using the custom stream opener
        val res = sessionManager.switchRom(
            uri = FakeUri(testUriStr),
            loadedProfiles = emptyList(),
            preferredTitle = "Revoked Game"
        )

        assertTrue("Must fail cleanly on SecurityException", res is SwitchResult.Failure)
        val failure = res as SwitchResult.Failure
        assertTrue("Must be marked as access error", failure.isAccessError)
        assertTrue("Cause must be SecurityException", failure.cause is SecurityException)

        // Simulate recovery action performed by MainActivity / Recovery handler
        val wasContinueTarget = settingsManager.lastPlayedRomUri == testUriStr
        if (wasContinueTarget && failure.isAccessError) {
            settingsManager.clearLastPlayedRom()
        }

        assertNull("Stale Continue URI must be cleared after SecurityException", settingsManager.lastPlayedRomUri)
        assertNull("Stale Continue title must be cleared", settingsManager.lastPlayedRomTitle)
        assertEquals(
            "Recovery message must match specification",
            "DualDex can no longer access this ROM. Select the ROM again to continue. Your DualDex saves are still preserved.",
            RomUriPermissionManager.RECOVERY_MESSAGE
        )
    }

    /**
     * 5. Continue target no longer exists / cannot be opened (FileNotFoundException / null stream):
     *    -> same graceful recovery behavior.
     */
    @Test
    fun testContinue_targetNotFound_recoversGracefully() = runBlocking {
        val cacheDir = File(testBaseDir, "cache").apply { mkdirs() }
        val testUriStr = "content://missing_provider/deleted_game.gba"
        settingsManager.lastPlayedRomUri = testUriStr
        settingsManager.lastPlayedRomTitle = "Deleted Game"

        val sessionManager = RomSessionManager(
            saveStateManager = saveStateManager,
            settingsManager = settingsManager,
            customRomCacheDir = cacheDir,
            coreBridge = testBridge,
            customStreamOpener = { throw FileNotFoundException("Document deleted") }
        )

        val res = sessionManager.switchRom(
            uri = FakeUri(testUriStr),
            loadedProfiles = emptyList(),
            preferredTitle = "Deleted Game"
        )

        assertTrue("Must fail cleanly on FileNotFoundException", res is SwitchResult.Failure)
        val failure = res as SwitchResult.Failure
        assertTrue("Must be marked as access error", failure.isAccessError)
        assertTrue("Cause must be FileNotFoundException", failure.cause is FileNotFoundException)

        // Clear stale continue target
        val wasContinue = settingsManager.lastPlayedRomUri == testUriStr
        if (wasContinue && failure.isAccessError) {
            settingsManager.clearLastPlayedRom()
        }

        assertNull("Stale Continue URI must be cleared after FileNotFoundException", settingsManager.lastPlayedRomUri)
        assertNull("Stale Continue title must be cleared", settingsManager.lastPlayedRomTitle)
    }

    /**
     * 6. Permission Hygiene:
     *    -> Releases redundant single ROM URI.
     *    -> Never releases protected romsFolderUri or savesFolderUri.
     *    -> Never releases tree URIs.
     *    -> Re-opening same URI does not release itself.
     */
    @Test
    fun testHygienePolicy_releaseRulesFollowedConservatively() {
        val romsFolder = "content://com.android.externalstorage.documents/tree/primary%3ARoms"
        val savesFolder = "content://com.android.externalstorage.documents/tree/primary%3ASaves"
        val protectedUris = setOf(romsFolder, savesFolder)

        val singleDoc1 = "content://com.android.providers.media.documents/document/11"
        val singleDoc2 = "content://com.android.providers.media.documents/document/22"
        val fileUri = "file:///storage/emulated/0/rom.gba"

        // Redundant single-ROM URI replaced by a different single-ROM URI -> release
        assertTrue(RomUriPermissionManager.shouldReleaseOldUri(singleDoc1, singleDoc2, protectedUris))

        // Same URI opened again -> do not release
        assertFalse(RomUriPermissionManager.shouldReleaseOldUri(singleDoc1, singleDoc1, protectedUris))

        // Protected ROMs folder tree -> NEVER release
        assertFalse(RomUriPermissionManager.shouldReleaseOldUri(romsFolder, singleDoc2, protectedUris))

        // Protected Saves folder tree -> NEVER release
        assertFalse(RomUriPermissionManager.shouldReleaseOldUri(savesFolder, singleDoc2, protectedUris))

        // Any tree URI -> NEVER release
        val otherTree = "content://com.android.documents/tree/custom%3APath"
        assertFalse(RomUriPermissionManager.shouldReleaseOldUri(otherTree, singleDoc2, protectedUris))

        // File URIs -> do not attempt SAF release
        assertFalse(RomUriPermissionManager.shouldReleaseOldUri(fileUri, singleDoc2, protectedUris))

        // Null / blank old URI -> nothing to release
        assertFalse(RomUriPermissionManager.shouldReleaseOldUri(null, singleDoc2, protectedUris))
        assertFalse(RomUriPermissionManager.shouldReleaseOldUri("", singleDoc2, protectedUris))
    }

    /**
     * 7. Folder-selected ROM flow remains unchanged and durable.
     */
    @Test
    fun testFolderSelectedRomFlow_remainsDurableAndUnchanged() = runBlocking {
        val cacheDir = File(testBaseDir, "cache").apply { mkdirs() }
        val romFile = createRomFile("leafgreen.gba", 0x99.toByte())
        settingsManager.romsFolderUri = "content://com.android.documents/tree/roms"

        val sessionManager = RomSessionManager(
            saveStateManager = saveStateManager,
            settingsManager = settingsManager,
            customRomCacheDir = cacheDir,
            coreBridge = testBridge
        )

        // Folder flow always sets isDurable = true because folder grant is held
        val res = sessionManager.switchRomFile(
            file = romFile,
            loadedProfiles = emptyList(),
            preferredTitle = "Pokemon LeafGreen",
            isDurable = true
        )

        assertTrue(res is SwitchResult.Success)
        assertEquals(romFile.absolutePath, settingsManager.lastPlayedRomUri)
        assertEquals("Pokemon LeafGreen", settingsManager.lastPlayedRomTitle)
    }

    /**
     * 8. Regression test: If opening new ROM fails (e.g. core rejection), old Continue URI permission
     *    is NOT released and old Continue target remains intact.
     */
    @Test
    fun testNewRomLoadFails_doesNotReleaseOldContinueGrant() = runBlocking {
        val cacheDir = File(testBaseDir, "cache").apply { mkdirs() }
        createRomFile("game_a.gba", 0x11)
        val romB = createRomFile("game_b.gba", 0x22)
        val uriStrA = "content://com.android.providers.media.documents/document/100"
        val uriStrB = "content://com.android.providers.media.documents/document/200"

        // ROM A is the established durable Continue target
        settingsManager.lastPlayedRomUri = uriStrA
        settingsManager.lastPlayedRomTitle = "Game A"

        // Simulate core rejection when attempting to load ROM B
        testBridge.loadRomResult = false

        val sessionManager = RomSessionManager(
            saveStateManager = saveStateManager,
            settingsManager = settingsManager,
            customRomCacheDir = cacheDir,
            coreBridge = testBridge,
            customStreamOpener = { ByteArrayInputStream(romB.readBytes()) }
        )

        // Simulate MainActivity.openRomLauncher flow:
        // 1. Capture previous durable URI candidate
        val oldLastPlayed = settingsManager.lastPlayedRomUri
        val isDurable = true // Assume takePersistableReadPermission succeeded for B
        val previousDurableUriToRelease = if (isDurable) oldLastPlayed else null

        // 2. Perform switch transaction
        val result = sessionManager.switchRom(
            uri = FakeUri(uriStrB),
            loadedProfiles = emptyList(),
            preferredTitle = "Game B",
            isDurable = isDurable
        )

        assertTrue("ROM B switch should fail due to core rejection", result is SwitchResult.Failure)
        val failure = result as SwitchResult.Failure
        assertFalse("Core rejection should not be marked as access error", failure.isAccessError)

        var releasedUri: String? = null
        // 3. MainActivity deferred release policy: ONLY release on Success!
        val outcome: SwitchResult = result
        when (outcome) {
            is SwitchResult.Success -> {
                if (previousDurableUriToRelease != null) {
                    releasedUri = previousDurableUriToRelease
                }
            }
            is SwitchResult.Failure -> {
                val wasContinueTarget = settingsManager.lastPlayedRomUri == uriStrB
                if (wasContinueTarget && outcome.isAccessError) {
                    settingsManager.clearLastPlayedRom()
                }
            }
        }

        // Assert that old URI was never released and old Continue target was preserved
        assertNull("Old Continue URI must NOT be released when new ROM load fails", releasedUri)
        assertEquals("Old Continue target URI must remain intact", uriStrA, settingsManager.lastPlayedRomUri)
        assertEquals("Old Continue target Title must remain intact", "Game A", settingsManager.lastPlayedRomTitle)
    }

    /**
     * 9. Regression test: If Continue target fails due to core rejection (non-access failure),
     *    Continue state is NOT cleared.
     */
    @Test
    fun testContinueTarget_coreRejection_doesNotClearContinueState() = runBlocking {
        val cacheDir = File(testBaseDir, "cache").apply { mkdirs() }
        val romA = createRomFile("game_a.gba", 0x33)
        val uriStrA = "content://com.android.providers.media.documents/document/100"

        settingsManager.lastPlayedRomUri = uriStrA
        settingsManager.lastPlayedRomTitle = "Game A"

        // Emulate core rejecting the ROM
        testBridge.loadRomResult = false

        val sessionManager = RomSessionManager(
            saveStateManager = saveStateManager,
            settingsManager = settingsManager,
            customRomCacheDir = cacheDir,
            coreBridge = testBridge,
            customStreamOpener = { ByteArrayInputStream(romA.readBytes()) }
        )

        val result = sessionManager.switchRom(
            uri = FakeUri(uriStrA),
            loadedProfiles = emptyList(),
            preferredTitle = "Game A"
        )

        assertTrue("Switch must fail when core rejects ROM", result is SwitchResult.Failure)
        val failure = result as SwitchResult.Failure
        assertFalse("Core rejection must NOT be flagged as access error", failure.isAccessError)

        // In MainActivity failure handling:
        val wasContinueTarget = settingsManager.lastPlayedRomUri == uriStrA
        if (wasContinueTarget && failure.isAccessError) {
            settingsManager.clearLastPlayedRom()
        }

        assertEquals("Continue URI must remain intact after non-access failure", uriStrA, settingsManager.lastPlayedRomUri)
        assertEquals("Continue Title must remain intact after non-access failure", "Game A", settingsManager.lastPlayedRomTitle)
    }

    /**
     * 10. Regression test: If Continue target fails due to local cache write failure,
     *     it is NOT classified as an access error and Continue state is preserved.
     */
    @Test
    fun testContinueTarget_cacheWriteFailure_isNotAccessError_preservesContinue() = runBlocking {
        // Use a non-directory file for cacheDir to force local cache creation failure
        val invalidCacheDir = File(testBaseDir, "not_a_dir_cache")
        invalidCacheDir.createNewFile()

        val romA = createRomFile("game_a.gba", 0x44)
        val uriStrA = "content://com.android.providers.media.documents/document/100"

        settingsManager.lastPlayedRomUri = uriStrA
        settingsManager.lastPlayedRomTitle = "Game A"

        val sessionManager = RomSessionManager(
            saveStateManager = saveStateManager,
            settingsManager = settingsManager,
            customRomCacheDir = invalidCacheDir,
            coreBridge = testBridge,
            customStreamOpener = { ByteArrayInputStream(romA.readBytes()) }
        )

        val result = sessionManager.switchRom(
            uri = FakeUri(uriStrA),
            loadedProfiles = emptyList(),
            preferredTitle = "Game A"
        )

        assertTrue("Switch must fail when local cache write fails", result is SwitchResult.Failure)
        val failure = result as SwitchResult.Failure
        assertFalse("Local cache write failure must NOT be classified as isAccessError", failure.isAccessError)

        // In MainActivity failure handling:
        val wasContinueTarget = settingsManager.lastPlayedRomUri == uriStrA
        if (wasContinueTarget && failure.isAccessError) {
            settingsManager.clearLastPlayedRom()
        }

        assertEquals("Continue URI must remain intact after local cache write failure", uriStrA, settingsManager.lastPlayedRomUri)
    }

    /**
     * 11. Issue #36: New URI permission is newly acquired + ROM switch succeeds:
     *     -> candidate grant remains active and becomes Continue target.
     *     -> old redundant individual-ROM grant is released.
     */
    @Test
    fun testDirectOpen_newGrantAcquired_switchSucceeds_candidateGrantRetainedAndOldReleased() = runBlocking {
        val cacheDir = File(testBaseDir, "cache").apply { mkdirs() }
        val romB = createRomFile("game_b.gba", 0x55)
        val uriStrA = "content://com.android.providers.media.documents/document/100"
        val uriStrB = "content://com.android.providers.media.documents/document/200"
        val romsFolder = "content://com.android.externalstorage.documents/tree/primary%3ARoms"
        val savesFolder = "content://com.android.externalstorage.documents/tree/primary%3ASaves"
        val protectedUris = setOf(romsFolder, savesFolder)

        settingsManager.lastPlayedRomUri = uriStrA
        settingsManager.lastPlayedRomTitle = "Game A"

        // Candidate B is newly acquired
        val grantB = RomUriPermissionManager.determineGrantResult(
            uriStr = uriStrB,
            isAlreadyPersisted = false,
            takePermissionSuccess = true
        )
        assertTrue("Grant for B must be durable", grantB.isDurable)
        assertTrue("Grant for B must be newly acquired", grantB.isNewlyAcquired)

        val sessionManager = RomSessionManager(
            saveStateManager = saveStateManager,
            settingsManager = settingsManager,
            customRomCacheDir = cacheDir,
            coreBridge = testBridge,
            customStreamOpener = { ByteArrayInputStream(romB.readBytes()) }
        )

        val oldLastPlayed = settingsManager.lastPlayedRomUri
        val isDurable = grantB.isDurable
        val previousDurableUriToRelease = if (isDurable) oldLastPlayed else null
        val newlyAcquiredPersistableGrant = grantB.isNewlyAcquired

        val result = sessionManager.switchRom(
            uri = FakeUri(uriStrB),
            loadedProfiles = emptyList(),
            preferredTitle = "Game B",
            isDurable = isDurable
        )

        assertTrue("ROM B switch must succeed", result is SwitchResult.Success)

        var releasedRedundantUri: String? = null
        var releasedFailedCandidateUri: String? = null

        when (result) {
            is SwitchResult.Success -> {
                if (previousDurableUriToRelease != null &&
                    RomUriPermissionManager.shouldReleaseOldUri(previousDurableUriToRelease, uriStrB, protectedUris)) {
                    releasedRedundantUri = previousDurableUriToRelease
                }
            }
            is SwitchResult.Failure -> {
                if (newlyAcquiredPersistableGrant &&
                    RomUriPermissionManager.shouldReleaseFailedCandidateUri(uriStrB, settingsManager.lastPlayedRomUri, protectedUris, newlyAcquiredPersistableGrant)) {
                    releasedFailedCandidateUri = uriStrB
                }
            }
        }

        // Candidate B's grant is retained and NOT released as failed candidate
        assertNull("Candidate B must NOT be released on success", releasedFailedCandidateUri)
        // Old redundant URI A is released
        assertEquals("Old redundant grant A must be released on success", uriStrA, releasedRedundantUri)
        // Continue state is updated to B
        assertEquals("Continue state must point to new ROM B", uriStrB, settingsManager.lastPlayedRomUri)
    }

    /**
     * 12. Issue #36: New URI permission is newly acquired + ROM switch fails:
     *     -> newly acquired candidate grant IS released for cleanup.
     *     -> previous Continue target remains completely intact and unchanged.
     */
    @Test
    fun testDirectOpen_newGrantAcquired_switchFails_candidateGrantReleasedAndContinuePreserved() = runBlocking {
        val cacheDir = File(testBaseDir, "cache").apply { mkdirs() }
        val romB = createRomFile("game_b.gba", 0x66)
        val uriStrA = "content://com.android.providers.media.documents/document/100"
        val uriStrB = "content://com.android.providers.media.documents/document/200"
        val romsFolder = "content://com.android.externalstorage.documents/tree/primary%3ARoms"
        val savesFolder = "content://com.android.externalstorage.documents/tree/primary%3ASaves"
        val protectedUris = setOf(romsFolder, savesFolder)

        settingsManager.lastPlayedRomUri = uriStrA
        settingsManager.lastPlayedRomTitle = "Game A"

        // Candidate B is newly acquired
        val grantB = RomUriPermissionManager.determineGrantResult(
            uriStr = uriStrB,
            isAlreadyPersisted = false,
            takePermissionSuccess = true
        )
        assertTrue(grantB.isDurable)
        assertTrue(grantB.isNewlyAcquired)

        // Core rejects ROM B
        testBridge.loadRomResult = false

        val sessionManager = RomSessionManager(
            saveStateManager = saveStateManager,
            settingsManager = settingsManager,
            customRomCacheDir = cacheDir,
            coreBridge = testBridge,
            customStreamOpener = { ByteArrayInputStream(romB.readBytes()) }
        )

        val oldLastPlayed = settingsManager.lastPlayedRomUri
        val isDurable = grantB.isDurable
        val previousDurableUriToRelease = if (isDurable) oldLastPlayed else null
        val newlyAcquiredPersistableGrant = grantB.isNewlyAcquired

        val result = sessionManager.switchRom(
            uri = FakeUri(uriStrB),
            loadedProfiles = emptyList(),
            preferredTitle = "Game B",
            isDurable = isDurable
        )

        assertTrue("ROM B switch must fail", result is SwitchResult.Failure)

        var releasedRedundantUri: String? = null
        var releasedFailedCandidateUri: String? = null

        when (result) {
            is SwitchResult.Success -> {
                if (previousDurableUriToRelease != null &&
                    RomUriPermissionManager.shouldReleaseOldUri(previousDurableUriToRelease, uriStrB, protectedUris)) {
                    releasedRedundantUri = previousDurableUriToRelease
                }
            }
            is SwitchResult.Failure -> {
                if (newlyAcquiredPersistableGrant &&
                    RomUriPermissionManager.shouldReleaseFailedCandidateUri(uriStrB, settingsManager.lastPlayedRomUri, protectedUris, newlyAcquiredPersistableGrant)) {
                    releasedFailedCandidateUri = uriStrB
                }
                val wasContinueTarget = settingsManager.lastPlayedRomUri == uriStrB
                if (wasContinueTarget && result.isAccessError) {
                    settingsManager.clearLastPlayedRom()
                }
            }
        }

        // Candidate B must be released
        assertEquals("Newly acquired candidate B grant must be released on failure", uriStrB, releasedFailedCandidateUri)
        // Previous Continue target A must NOT be released
        assertNull("Old Continue target A must NOT be released on failure", releasedRedundantUri)
        // Continue state A remains intact
        assertEquals("Previous Continue URI must remain unchanged", uriStrA, settingsManager.lastPlayedRomUri)
        assertEquals("Previous Continue Title must remain unchanged", "Game A", settingsManager.lastPlayedRomTitle)
    }

    /**
     * 13. Issue #36: Previous Continue A + failed candidate B:
     *     -> A is NOT selected for release.
     *     -> B IS selected for cleanup.
     */
    @Test
    fun testDirectOpen_previousContinueA_failedCandidateB_AIsNeverReleased_BIsCleanedUp() {
        val uriA = "content://com.android.providers.media.documents/document/100"
        val uriB = "content://com.android.providers.media.documents/document/200"
        val protectedUris = setOf(
            "content://com.android.externalstorage.documents/tree/primary%3ARoms",
            "content://com.android.externalstorage.documents/tree/primary%3ASaves"
        )

        // B was newly acquired
        assertTrue(
            "Candidate B must be selected for cleanup on failure",
            RomUriPermissionManager.shouldReleaseFailedCandidateUri(
                candidateUriStr = uriB,
                currentContinueUriStr = uriA,
                protectedUris = protectedUris,
                isNewlyAcquired = true
            )
        )

        // A must NEVER be selected for cleanup on failure
        assertFalse(
            "Active Continue URI A must NEVER be released as a failed candidate",
            RomUriPermissionManager.shouldReleaseFailedCandidateUri(
                candidateUriStr = uriA,
                currentContinueUriStr = uriA,
                protectedUris = protectedUris,
                isNewlyAcquired = true
            )
        )
    }

    /**
     * 14. Issue #36: Candidate URI was already persisted before this attempt:
     *     -> ROM switch fails.
     *     -> existing persisted permission is NOT revoked.
     */
    @Test
    fun testDirectOpen_candidateAlreadyPersisted_switchFails_doesNotReleaseGrant() {
        val uriA = "content://com.android.providers.media.documents/document/100"
        val uriB = "content://com.android.providers.media.documents/document/200"
        val protectedUris = setOf("content://com.android.externalstorage.documents/tree/primary%3ARoms")

        // Pre-existing grant: newlyAcquired = false
        val grant = RomUriPermissionManager.determineGrantResult(
            uriStr = uriB,
            isAlreadyPersisted = true,
            takePermissionSuccess = true
        )
        assertTrue(grant.isDurable)
        assertFalse("Pre-existing grant must NOT be marked as newly acquired", grant.isNewlyAcquired)

        // Switch fails: cleanup must NOT release it
        assertFalse(
            "Pre-existing grant must NOT be released on switch failure",
            RomUriPermissionManager.shouldReleaseFailedCandidateUri(
                candidateUriStr = uriB,
                currentContinueUriStr = uriA,
                protectedUris = protectedUris,
                isNewlyAcquired = grant.isNewlyAcquired
            )
        )
    }

    /**
     * 15. Issue #36: Candidate URI equals current Continue URI:
     *     -> reload fails (e.g. non-access failure).
     *     -> must NOT revoke its existing persisted permission.
     */
    @Test
    fun testDirectOpen_candidateEqualsContinueTarget_switchFails_doesNotReleaseGrant() {
        val uriA = "content://com.android.providers.media.documents/document/100"
        val protectedUris = setOf("content://com.android.externalstorage.documents/tree/primary%3ARoms")

        // User re-selects the same ROM A
        val grant = RomUriPermissionManager.determineGrantResult(
            uriStr = uriA,
            isAlreadyPersisted = true,
            takePermissionSuccess = true
        )
        assertFalse(grant.isNewlyAcquired)

        // shouldReleaseFailedCandidateUri must refuse to release the active Continue target
        assertFalse(
            "Active Continue URI matching candidate must never be released on failure",
            RomUriPermissionManager.shouldReleaseFailedCandidateUri(
                candidateUriStr = uriA,
                currentContinueUriStr = uriA,
                protectedUris = protectedUris,
                isNewlyAcquired = false
            )
        )

        // Defense-in-depth: even if isNewlyAcquired were true, matching continue URI refuses release
        assertFalse(
            "Even if marked newly acquired, matching active Continue URI must never be released on failure",
            RomUriPermissionManager.shouldReleaseFailedCandidateUri(
                candidateUriStr = uriA,
                currentContinueUriStr = uriA,
                protectedUris = protectedUris,
                isNewlyAcquired = true
            )
        )
    }

    /**
     * 16. Issue #36: Provider refuses persistence:
     *     -> no rollback release is attempted because no persisted grant was acquired.
     */
    @Test
    fun testDirectOpen_providerRefusesPersistence_noRollbackReleaseAttempted() {
        val uriA = "content://com.android.providers.media.documents/document/100"
        val uriB = "content://com.android.providers.media.documents/document/200"
        val protectedUris = setOf("content://com.android.externalstorage.documents/tree/primary%3ARoms")

        // Provider refuses: durable = false, newlyAcquired = false
        val grant = RomUriPermissionManager.determineGrantResult(
            uriStr = uriB,
            isAlreadyPersisted = false,
            takePermissionSuccess = false
        )
        assertFalse(grant.isDurable)
        assertFalse(grant.isNewlyAcquired)

        assertFalse(
            "When provider refuses persistence, no release must be attempted",
            RomUriPermissionManager.shouldReleaseFailedCandidateUri(
                candidateUriStr = uriB,
                currentContinueUriStr = uriA,
                protectedUris = protectedUris,
                isNewlyAcquired = grant.isNewlyAcquired
            )
        )
    }

    /**
     * 17. Issue #36: Folder/tree URIs must never be released on failure.
     */
    @Test
    fun testDirectOpen_treeOrFolderUri_neverReleasedOnFailure() {
        val romsFolder = "content://com.android.externalstorage.documents/tree/primary%3ARoms"
        val savesFolder = "content://com.android.externalstorage.documents/tree/primary%3ASaves"
        val otherTree = "content://com.android.documents/tree/some%2Ffolder"
        val protectedUris = setOf(romsFolder, savesFolder)

        assertFalse(
            "Protected ROMs folder must never be released on failure",
            RomUriPermissionManager.shouldReleaseFailedCandidateUri(
                candidateUriStr = romsFolder,
                currentContinueUriStr = null,
                protectedUris = protectedUris,
                isNewlyAcquired = true
            )
        )

        assertFalse(
            "Protected Saves folder must never be released on failure",
            RomUriPermissionManager.shouldReleaseFailedCandidateUri(
                candidateUriStr = savesFolder,
                currentContinueUriStr = null,
                protectedUris = protectedUris,
                isNewlyAcquired = true
            )
        )

        assertFalse(
            "Generic tree URI must never be released on failure",
            RomUriPermissionManager.shouldReleaseFailedCandidateUri(
                candidateUriStr = otherTree,
                currentContinueUriStr = null,
                protectedUris = protectedUris,
                isNewlyAcquired = true
            )
        )
    }

    /**
     * 18. Issue #36: File URIs must never have SAF release attempted.
     */
    @Test
    fun testDirectOpen_fileUri_noSafReleaseAttempted() {
        val fileUri = "file:///storage/emulated/0/Pokemon.gba"
        val protectedUris = setOf<String>()

        // file:// URI is inherently durable but never newly acquired via SAF
        val grant = RomUriPermissionManager.determineGrantResult(
            uriStr = fileUri,
            isAlreadyPersisted = false,
            takePermissionSuccess = true
        )
        assertTrue(grant.isDurable)
        assertFalse(grant.isNewlyAcquired)

        assertFalse(
            "File URI must not be released on failure",
            RomUriPermissionManager.shouldReleaseFailedCandidateUri(
                candidateUriStr = fileUri,
                currentContinueUriStr = null,
                protectedUris = protectedUris,
                isNewlyAcquired = grant.isNewlyAcquired
            )
        )

        // Even if isNewlyAcquired were mistakenly true, non-content URIs are rejected
        assertFalse(
            "Non-content file URI must never be released via SAF helper",
            RomUriPermissionManager.shouldReleaseFailedCandidateUri(
                candidateUriStr = fileUri,
                currentContinueUriStr = null,
                protectedUris = protectedUris,
                isNewlyAcquired = true
            )
        )
    }

    /**
     * 19. Issue #36: Exhaustive decision matrix for determineGrantResult.
     */
    @Test
    fun testRomUriPermissionManager_determineGrantResult_matrix() {
        // Null / blank URI
        assertEquals(
            PersistableGrantResult(isDurable = false, isNewlyAcquired = false),
            RomUriPermissionManager.determineGrantResult(null, isAlreadyPersisted = false, takePermissionSuccess = true)
        )
        assertEquals(
            PersistableGrantResult(isDurable = false, isNewlyAcquired = false),
            RomUriPermissionManager.determineGrantResult("", isAlreadyPersisted = false, takePermissionSuccess = true)
        )

        // file:// URI: durable = true, newlyAcquired = false regardless of takePermissionSuccess
        assertEquals(
            PersistableGrantResult(isDurable = true, isNewlyAcquired = false),
            RomUriPermissionManager.determineGrantResult("file:///test.gba", isAlreadyPersisted = false, takePermissionSuccess = true)
        )
        assertEquals(
            PersistableGrantResult(isDurable = true, isNewlyAcquired = false),
            RomUriPermissionManager.determineGrantResult("file:///test.gba", isAlreadyPersisted = true, takePermissionSuccess = false)
        )

        // content:// URI already persisted before call: durable = true, newlyAcquired = false
        val contentUri = "content://com.android.providers.media.documents/document/1"
        assertEquals(
            PersistableGrantResult(isDurable = true, isNewlyAcquired = false),
            RomUriPermissionManager.determineGrantResult(contentUri, isAlreadyPersisted = true, takePermissionSuccess = true)
        )

        // content:// URI newly persisted successfully: durable = true, newlyAcquired = true
        assertEquals(
            PersistableGrantResult(isDurable = true, isNewlyAcquired = true),
            RomUriPermissionManager.determineGrantResult(contentUri, isAlreadyPersisted = false, takePermissionSuccess = true)
        )

        // content:// URI provider refuses persistence: durable = false, newlyAcquired = false
        assertEquals(
            PersistableGrantResult(isDurable = false, isNewlyAcquired = false),
            RomUriPermissionManager.determineGrantResult(contentUri, isAlreadyPersisted = false, takePermissionSuccess = false)
        )
    }

    /**
     * 20. Issue #36: Fail-safe behavior if release throws or contentResolver is null.
     */
    @Test
    fun testRomUriPermissionManager_releaseOnFailure_safeAndNonFatal() {
        // Calling with null ContentResolver must execute safely and not throw
        RomUriPermissionManager.releasePersistableReadPermissionOnFailure(
            contentResolver = null,
            candidateUriStr = "content://test/doc",
            currentContinueUriStr = null,
            protectedUris = emptySet(),
            isNewlyAcquired = true
        )
    }
}
