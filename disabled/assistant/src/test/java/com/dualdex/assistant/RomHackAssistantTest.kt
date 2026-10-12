package com.dualdex.assistant

import com.dualdex.companion.CompanionViewModel
import com.dualdex.romhack.RomHackProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RomHackAssistantTest {

    @Test
    fun testOfflineAssistantItemLocations() = runBlocking {
        val viewModel = CompanionViewModel()

        val ghostGreyProfile = RomHackProfile(
            id = "ghost_grey",
            name = "Pokemon Ghost Grey",
            baseGame = "FireRed",
            gameId = 6,
            hasEvs = false,
            hasIvs = false,
            steelResistsGhostDark = true
        )
        viewModel.setProfile(ghostGreyProfile)

        // Test item query
        val flyRes = RomHackAssistant.askQuestion(null, "Where do I get HM02 Fly?", viewModel)
        assertTrue(flyRes.text.contains("Route 16", ignoreCase = true))
        assertTrue(flyRes.isOfflineFallback)

        // Test Ghost Grey mechanic query
        val evRes = RomHackAssistant.askQuestion(null, "Are there EVs in Ghost Grey?", viewModel)
        assertTrue(evRes.text.contains("removed", ignoreCase = true))

        // Test Ghost Grey custom species query
        val monRes = RomHackAssistant.askQuestion(null, "Tell me about Lichtoise", viewModel)
        assertTrue(monRes.text.contains("Water/Ghost", ignoreCase = true))
        assertTrue(monRes.text.contains("100 Def", ignoreCase = true))

        // Test Steel type query
        val steelRes = RomHackAssistant.askQuestion(null, "Does steel resist dark?", viewModel)
        assertTrue(steelRes.text.contains("0.5x", ignoreCase = true) || steelRes.text.contains("resistance", ignoreCase = true))
    }
}
