package com.dualdex.retroachievements

import com.dualdex.emulator.LibretroCoreBridge
import com.dualdex.emulator.LibretroCoreCoordinator
import com.dualdex.emulator.RomDurableResumeTest
import com.dualdex.emulator.RomIdentity
import com.dualdex.settings.SettingsManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

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
