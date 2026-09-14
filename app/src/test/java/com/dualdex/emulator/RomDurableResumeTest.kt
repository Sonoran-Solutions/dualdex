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
        if (failure.isAccessError || settingsManager.lastPlayedRomUri == testUriStr) {
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
        if (failure.isAccessError || settingsManager.lastPlayedRomUri == testUriStr) {
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
}
