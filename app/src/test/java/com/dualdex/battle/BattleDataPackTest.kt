package com.dualdex.battle

import com.dualdex.companion.CompanionViewModel
import com.dualdex.emulator.RomIdentity
import com.dualdex.pokemon.MoveCategory
import com.dualdex.pokemon.MoveDatabase
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.unbound.UnboundDataPack
import com.dualdex.pokemon.unbound.UnboundSpeciesTable
import com.dualdex.romhack.ProfileMatchMethod
import com.dualdex.romhack.RomCompatibility
import com.dualdex.romhack.RomHackProfile
import org.junit.Assert.*
import org.junit.Test

class BattleDataPackTest {
    private val profile = RomHackProfile.DEFAULT_FIRERED.copy(
        id = "unbound", engine = "CFRU", gameDataPackId = "unbound_exact", hasPhysSpecSplit = true
    )
    private val mon = ParsedPokemon(
        isValid = true, isEmpty = false, pid = 1L, tid = 0, sid = 0, nickname = "", otName = "",
        species = 277, heldItem = 0, level = 50, nature = 0, natureName = "Hardy",
        isShiny = false, abilitySlot = 0, isEgg = false, friendship = 0, experience = 0L,
        hpIv = 31, attackIv = 31, defenseIv = 31, speedIv = 31, spAttackIv = 31, spDefenseIv = 31,
        hpEv = 0, attackEv = 0, defenseEv = 0, speedEv = 0, spAttackEv = 0, spDefenseEv = 0,
        moves = intArrayOf(434, 355, 0, 0), pp = intArrayOf(15, 15, 0, 0),
        currentHp = 100, maxHp = 100, attack = 100, defense = 100, speed = 100,
        spAttack = 100, spDefense = 100, statusCondition = 0L
    )

    @Test fun loadedPackReachesBattleBuildersWithoutAProfileHash() {
        assertTrue(profile.sha256Hashes.isEmpty())
        for (sha in listOf(UnboundSpeciesTable.ROM_SHA256, "0".repeat(64))) {
            val vm = CompanionViewModel()
            vm.setRomSession(
                RomCompatibility.recognizedUnverified(profile, sha, ProfileMatchMethod.HEADER_TITLE, "unverified"),
                RomIdentity(sha, "Renamed ROM")
            )
            val pack = vm.activeGameDataPack
            val exact = sha == UnboundSpeciesTable.ROM_SHA256
            if (exact) assertSame(UnboundDataPack, pack)
            val summary = ParticipantSummaryBuilder.build(mon, 0, profile, dataPack = pack)
            assertEquals(if (exact) "Treecko" else "Unknown (#277)", summary.speciesName)
            assertEquals(if (exact) "Flare Blitz" else "Unknown Move (#434)", summary.moveNames[0])
            assertEquals("Unknown Move (#355)", summary.moveNames[1])
            assertFalse(summary.isVerified)

            val types = MoveEffectiveness.defenderTypesOf(mon, profile, pack)
            assertEquals(if (exact) PokemonType.GRASS else null, types.first)
            // Internal ID 1102 is outside the global dex; the exact pack still resolves it.
            assertEquals(if (exact) PokemonType.GRASS else null,
                MoveEffectiveness.defenderTypesOf(mon.copy(species = 1102), profile, pack).first)
            val effectiveness = MoveEffectiveness.evaluate(434, MoveCategory.PHYSICAL, mon, profile, dataPack = pack)
            assertEquals(if (exact) EffectivenessLabel.SUPER_EFFECTIVE else null, effectiveness.first)
            assertEquals(if (exact) DataConfidence.ESTIMATE else DataConfidence.UNAVAILABLE, effectiveness.second)
            val move = BattlePresentationBuilder.build(
                MoveDatabase.get(434), 15, mon, mon, profile, dataPack = pack
            )
            assertEquals(exact, move.isKnown)
            assertEquals(if (exact) "Flare Blitz" else "Unknown Move (#434)", move.name)
            assertEquals(if (exact) "Fire" else MoveEffectiveness.UNAVAILABLE, move.typeName)
            assertEquals(if (exact) EffectivenessLabel.SUPER_EFFECTIVE.displayName else MoveEffectiveness.UNAVAILABLE,
                move.effectiveness)
            assertFalse(BattlePresentationBuilder.build(
                MoveDatabase.get(355), 15, mon, mon, profile, dataPack = pack
            ).isKnown)
            val field = FieldStatusBuilder.build(true, mon, mon, profile = profile, dataPack = pack)
            assertEquals(summary.speciesName, field.participant.speciesName)
            assertEquals(summary.speciesName, field.opponent.speciesName)
            val request = buildDamageRequest(mon, mon, "Flare Blitz", "Hardy", profile, dataPack = pack)
            assertEquals(if (exact) "Treecko" else "Pokemon #277", request.attacker.species)
            assertEquals(request.attacker.species, request.defender.species)
        }
    }
}
