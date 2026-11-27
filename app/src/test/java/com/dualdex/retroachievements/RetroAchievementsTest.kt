package com.dualdex.retroachievements

import com.dualdex.emulator.LibretroCoreBridge
import com.dualdex.emulator.LibretroCoreCoordinator
import com.dualdex.emulator.RomDurableResumeTest
import com.dualdex.emulator.RomIdentity
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
        assertEquals(0, bridge.stateLoads)
        assertEquals(0, bridge.enabledCheats)
        assertEquals(generation, core.stateGeneration)

        core.executeExclusive { core.setRaHardcoreLocked(false) }
        assertTrue(core.raHardcorePolicy.mayLoadSaveState)
        assertTrue(core.raHardcorePolicy.mayUseCheats)
        assertTrue(core.raHardcorePolicy.mayUseInteractiveBattleControls)
        assertTrue(core.raHardcorePolicy.mayAutoResumeState)
        assertTrue(core.loadState("synthetic.state"))
        core.cheatSet(0, true, "synthetic")
        assertEquals(1, bridge.stateLoads)
        assertEquals(1, bridge.enabledCheats)
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
        override fun loadRom(romPath: String) = true
        override fun loadState(statePath: String): Boolean {
            stateLoads++
            return true
        }
        override fun cheatSet(index: Int, enabled: Boolean, code: String) {
            if (enabled) enabledCheats++
        }
    }
}
