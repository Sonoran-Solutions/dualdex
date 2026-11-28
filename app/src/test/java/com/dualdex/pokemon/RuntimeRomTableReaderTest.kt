package com.dualdex.pokemon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeRomTableReaderTest {

    private fun encodeGen3(text: String): ByteArray {
        val map = mutableMapOf(
            ' ' to 0x00,
            '\n' to 0xFE,
            'é' to 0x1B,
            '!' to 0xAB,
            '?' to 0xAC,
            '.' to 0xAD,
            '-' to 0xAE,
            '♂' to 0xB5,
            '♀' to 0xB6,
            ',' to 0xB8,
            '/' to 0xBA,
            ':' to 0xF0
        )
        for (i in 0..9) map[('0'.code + i).toChar()] = 0xA1 + i
        for (i in 0..25) {
            map[('A'.code + i).toChar()] = 0xBB + i
            map[('a'.code + i).toChar()] = 0xD5 + i
        }
        return ByteArray(text.length + 1) { idx ->
            if (idx == text.length) 0xFF.toByte()
            else (map[text[idx]] ?: error("Unsupported test char: ${text[idx]}")).toByte()
        }
    }

    private fun writeU16(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }

    private fun writeU32(buf: ByteArray, offset: Int, value: Long) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        buf[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        buf[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }

    private fun buildVanillaLayoutRom(
        speciesCount: Int = 40,
        moveCount: Int = 40,
        uprModified: Boolean = false
    ): ByteArray {
        val rom = ByteArray(1024 * 1024)
        val speciesNamesOff = 0x10000
        val baseStatsOff = 0x20000
        val moveNamesOff = 0x30000
        val movesOff = 0x40000

        for (i in 0 until speciesCount) {
            val name = when (i) {
                0 -> "?"
                1 -> if (uprModified) "Bulbazar" else "Bulbasaur"
                25 -> "Pikachu"
                else -> "Mon$i"
            }
            val enc = encodeGen3(name)
            enc.copyInto(rom, speciesNamesOff + i * 11, 0, minOf(enc.size, 11))

            if (i > 0) {
                val r = baseStatsOff + i * 28
                if (uprModified && i == 1) {
                    // UPR ZX randomized stats and types (Fire/Dragon, buffed stats)
                    rom[r + 0] = 90.toByte()
                    rom[r + 1] = 110.toByte()
                    rom[r + 2] = 85.toByte()
                    rom[r + 3] = 100.toByte()
                    rom[r + 4] = 95.toByte()
                    rom[r + 5] = 80.toByte()
                    rom[r + 6] = 0x0A.toByte() // Fire
                    rom[r + 7] = 0x10.toByte() // Dragon
                } else {
                    rom[r + 0] = 45.toByte()
                    rom[r + 1] = 49.toByte()
                    rom[r + 2] = 49.toByte()
                    rom[r + 3] = 45.toByte()
                    rom[r + 4] = 65.toByte()
                    rom[r + 5] = 65.toByte()
                    rom[r + 6] = if (i <= 3) 0x0C.toByte() else 0x0D.toByte() // Grass or Electric
                    rom[r + 7] = if (i <= 3) 0x03.toByte() else 0x0D.toByte() // Poison or single Electric
                }
            }
        }

        for (i in 0 until moveCount) {
            val name = when (i) {
                0 -> "-"
                33 -> "Tackle"
                53 -> "Flamethrower"
                else -> "Move$i"
            }
            val enc = encodeGen3(name)
            enc.copyInto(rom, moveNamesOff + i * 13, 0, minOf(enc.size, 13))

            if (i > 0) {
                val r = movesOff + i * 12
                when (i) {
                    33 -> {
                        rom[r + 1] = (if (uprModified) 50 else 35).toByte()
                        rom[r + 2] = 0x00.toByte() // Normal -> Physical in Gen 3
                        rom[r + 3] = 95.toByte()
                        rom[r + 4] = 35.toByte()
                    }
                    14 -> { // Swords Dance -> Status
                        rom[r + 1] = 0.toByte()
                        rom[r + 2] = 0x00.toByte()
                        rom[r + 3] = 0.toByte()
                        rom[r + 4] = 30.toByte()
                    }
                    else -> {
                        rom[r + 1] = 95.toByte()
                        rom[r + 2] = 0x0A.toByte() // Fire -> Special in Gen 3
                        rom[r + 3] = 100.toByte()
                        rom[r + 4] = 15.toByte()
                    }
                }
            }
        }
        return rom
    }

    private fun vanillaSpec(
        speciesCount: Int = 40,
        moveCount: Int = 40,
        sanity: SanityProbeSpec = SanityProbeSpec(
            expectedMoveAnchors = mapOf(33 to 35),
            expectedSpeciesAnchors = mapOf(1 to (PokemonType.GRASS to PokemonType.POISON))
        )
    ) = RuntimeRomTableSpec(
        id = "vanilla_test",
        species = SpeciesTableSpec(
            baseStatsAddress = RuntimeRomTableReader.ROM_BASE + 0x20000L,
            baseStatsStride = 28,
            count = speciesCount,
            namesAddress = RuntimeRomTableReader.ROM_BASE + 0x10000L,
            namesStride = 11
        ),
        moves = MoveTableSpec(
            movesAddress = RuntimeRomTableReader.ROM_BASE + 0x40000L,
            movesStride = 12,
            count = moveCount,
            categoryRule = MoveCategoryRule.BY_TYPE_GEN3,
            namesAddress = RuntimeRomTableReader.ROM_BASE + 0x30000L,
            namesStride = 13
        ),
        typeMapping = RuntimeRomTableReader.GEN3_TYPE_MAP,
        sanity = sanity
    )

    @Test
    fun gen3TextDecoderDecodesLettersDigitsSymbolsAndRejectsBadInputs() {
        val sample = encodeGen3("Mr. Mime 2♂♀-é!?")
        assertEquals("Mr. Mime 2♂♀-é!?", Gen3TextDecoder.decode(sample, 0, sample.size))

        // Unterminated field fails closed
        val unterminated = byteArrayOf(0xBB.toByte(), 0xD5.toByte(), 0xD6.toByte())
        assertNull(Gen3TextDecoder.decode(unterminated, 0, 3, requireTerminator = true))

        // Unknown glyph / control byte fails closed
        val badGlyph = byteArrayOf(0xBB.toByte(), 0xFC.toByte(), 0xFF.toByte())
        assertNull(Gen3TextDecoder.decode(badGlyph, 0, 3))

        // Out-of-bounds offset fails closed
        assertNull(Gen3TextDecoder.decode(sample, -1, 10))
        assertNull(Gen3TextDecoder.decode(sample, sample.size, 10))
    }

    @Test
    fun readsVanillaLayoutWithSeparateNameTablesAndGen3CategorySplit() {
        val rom = buildVanillaLayoutRom()
        val result = RuntimeRomTableReader.read(rom, vanillaSpec())
        assertTrue(result is RuntimeRomTableResult.Success)
        val snapshot = (result as RuntimeRomTableResult.Success).snapshot

        assertEquals(39, snapshot.speciesCount)
        assertEquals(39, snapshot.moveCount)

        val bulbasaur = snapshot.species[1]!!
        assertEquals("Bulbasaur", bulbasaur.name)
        assertEquals(PokemonType.GRASS, bulbasaur.type1)
        assertEquals(PokemonType.POISON, bulbasaur.type2)
        assertEquals(45, bulbasaur.baseHP)

        // Single type clears duplicate type2
        val pikachu = snapshot.species[25]!!
        assertEquals("Pikachu", pikachu.name)
        assertEquals(PokemonType.ELECTRIC, pikachu.type1)
        assertNull(pikachu.type2)

        // Move 33 Tackle -> Physical (Normal), power 35; Move 14 -> Status; Move 2 -> Special (Fire)
        val tackle = snapshot.moves[33]!!
        assertEquals("Tackle", tackle.name)
        assertEquals(35, tackle.power)
        assertEquals(MoveCategory.PHYSICAL, tackle.category)

        assertEquals(MoveCategory.STATUS, snapshot.moves[14]!!.category)
        assertEquals(MoveCategory.SPECIAL, snapshot.moves[2]!!.category)

        val pack = snapshot.toGameDataPack(Gen3VanillaDataPack)
        assertFalse(pack.allowGlobalFallback)
        assertTrue(pack.isSpeciesAuthoritative(1))
        assertEquals("Bulbasaur", pack.getSpeciesByName("bulbasaur")?.name)
    }

    @Test
    fun readsUprStyleInPlaceRandomizedStatsTypesAndMoves() {
        val rom = buildVanillaLayoutRom(uprModified = true)
        val uprSpec = vanillaSpec(
            sanity = SanityProbeSpec(
                expectedMoveAnchors = emptyMap(),
                expectedSpeciesAnchors = mapOf(1 to (PokemonType.GRASS to PokemonType.POISON)),
                allowRandomizerTypeStatChanges = true
            )
        )
        val result = RuntimeRomTableReader.read(rom, uprSpec)
        assertTrue(result is RuntimeRomTableResult.Success)
        val snapshot = (result as RuntimeRomTableResult.Success).snapshot
        val randomizedMon = snapshot.species[1]!!
        assertEquals("Bulbazar", randomizedMon.name)
        assertEquals(PokemonType.FIRE, randomizedMon.type1)
        assertEquals(PokemonType.DRAGON, randomizedMon.type2)
        assertEquals(90, randomizedMon.baseHP)
        assertEquals(110, randomizedMon.baseAtk)
        assertEquals(50, snapshot.moves[33]!!.power)
    }

    private fun packExpansionCombatWord(typeId: Int, category: Int, power: Int): Int =
        (typeId and 0x1F) or ((category and 0x03) shl 5) or ((power and 0x1FF) shl 7)

    private fun packExpansionAccuracyWord(accuracy: Int, target: Int = 0): Int =
        (accuracy and 0x7F) or ((target and 0x1FF) shl 7)

    @Test
    fun readsExpansionV19BitfieldLayoutWithUpstreamTypesAndPackedMoveInfo() {
        val rom = ByteArray(1024 * 1024)
        val speciesOff = 0x10000
        val speciesStride = 88
        val movesOff = 0x30000
        val movesStride = 36
        val itemsOff = 0x40000
        val itemsStride = 64
        var stringPool = 0x50000

        fun allocAlignedString(text: String): Long {
            val encoded = encodeGen3(text)
            encoded.copyInto(rom, stringPool)
            val addr = RuntimeRomTableReader.ROM_BASE + stringPool.toLong()
            stringPool = (stringPool + encoded.size + 3) and 3.inv()
            return addr
        }

        // Species row 0 (SPECIES_NONE): zero base stats + non-zero metadata
        rom[speciesOff + 6] = 1.toByte()
        rom[speciesOff + 7] = 1.toByte()
        writeU32(rom, speciesOff + 16, allocAlignedString("??????????"))

        for (i in 1 until 25) {
            val r = speciesOff + i * speciesStride
            rom[r + 0] = 60.toByte()
            rom[r + 1] = 62.toByte()
            rom[r + 2] = 63.toByte()
            rom[r + 3] = 60.toByte()
            rom[r + 4] = 80.toByte()
            rom[r + 5] = 80.toByte()
            when (i) {
                1 -> {
                    rom[r + 6] = 19.toByte() // Expansion TYPE_FAIRY = 19
                    rom[r + 7] = 20.toByte() // Expansion TYPE_STELLAR = 20
                }
                2 -> {
                    rom[r + 6] = 1.toByte() // Expansion TYPE_NORMAL = 1
                    rom[r + 7] = 0.toByte() // Expansion TYPE_NONE = 0 (monotype)
                }
                else -> {
                    rom[r + 6] = 13.toByte() // Expansion TYPE_GRASS = 13
                    rom[r + 7] = 4.toByte()  // Expansion TYPE_POISON = 4
                }
            }
            val namePtr = allocAlignedString(
                when (i) {
                    1 -> "Sylveon"
                    2 -> "Snorlax"
                    else -> "ExpMon$i"
                }
            )
            writeU32(rom, r + 16, namePtr)
        }

        // Move row 0 (MOVE_NONE): empty description pointer, name "-", power=0, pp=0
        writeU32(rom, movesOff + 0x00, allocAlignedString("-"))
        writeU32(rom, movesOff + 0x04, allocAlignedString(""))
        writeU16(rom, movesOff + 0x0A, packExpansionCombatWord(0, 2, 0))
        writeU16(rom, movesOff + 0x0C, packExpansionAccuracyWord(0, target = 0))
        rom[movesOff + 0x0E] = 0.toByte()

        for (i in 1 until 36) {
            val r = movesOff + i * movesStride
            val namePtr = allocAlignedString(
                when (i) {
                    14 -> "Swords Dance"
                    33 -> "Tackle"
                    35 -> "Light Of Ruin"
                    else -> "ExpMove$i"
                }
            )
            writeU32(rom, r + 0x00, namePtr)
            // Byte +4 is the description pointer (non-20 low byte, so reading ppOffset=4 would return wrong PP)
            val descPtr = allocAlignedString("Move desc $i")
            writeU32(rom, r + 0x04, descPtr)
            val (typeId, category, power) = when (i) {
                14 -> Triple(1, 2, 0)    // Normal, Status, power 0
                33 -> Triple(1, 0, 40)   // Normal, Physical, power 40
                35 -> Triple(19, 1, 300) // Fairy, Special, 9-bit power > 255
                else -> Triple(19, 1, 75)
            }
            writeU16(rom, r + 0x0A, packExpansionCombatWord(typeId, category, power))
            writeU16(rom, r + 0x0C, packExpansionAccuracyWord(100, target = 1))
            rom[r + 0x0E] = 20.toByte()
        }

        // Items table (v1.9.0 inline name[20] at +0x14 + description pointer at +0x0C)
        writeU32(rom, itemsOff + 0x0C, allocAlignedString(""))
        encodeGen3("????????").copyInto(rom, itemsOff + 0x14)
        for (i in 1 until 20) {
            val r = itemsOff + i * itemsStride
            writeU32(rom, r + 0x0C, allocAlignedString("Item desc\n$i"))
            val nameEnc = encodeGen3(if (i == 1) "Master Ball" else "Item$i")
            nameEnc.copyInto(rom, r + 0x14, 0, minOf(nameEnc.size, 20))
        }

        // Omit ppOffset and typeMapping to verify both are derived from EXPANSION_V1_9_BITFIELD
        val expSpec = RuntimeRomTableSpec(
            id = "expansion_v1_9_test",
            species = SpeciesTableSpec(
                baseStatsAddress = RuntimeRomTableReader.ROM_BASE + speciesOff,
                baseStatsStride = speciesStride,
                count = 25,
                inlineNameOffset = 16,
                inlineNameIsPointer = true
            ),
            moves = MoveTableSpec(
                movesAddress = RuntimeRomTableReader.ROM_BASE + movesOff,
                movesStride = movesStride,
                count = 36,
                categoryRule = MoveCategoryRule.PACKED_BITFIELD,
                inlineNameOffset = 0,
                inlineNameIsPointer = true
            ),
            sanity = SanityProbeSpec(
                expectedMoveAnchors = mapOf(33 to 40, 35 to 300),
                expectedSpeciesAnchors = mapOf(
                    1 to (PokemonType.FAIRY to PokemonType.STELLAR),
                    2 to (PokemonType.NORMAL to null)
                )
            ),
            layoutVersion = TableLayoutVersion.EXPANSION_V1_9_BITFIELD,
            layoutRevisionId = "EXPANSION_V1_9_BITFIELD",
            items = ItemTableSpec(
                itemsAddress = RuntimeRomTableReader.ROM_BASE + itemsOff,
                itemsStride = itemsStride,
                count = 20
            )
        )

        val result = RuntimeRomTableReader.read(rom, expSpec)
        assertTrue(result is RuntimeRomTableResult.Success)
        val snapshot = (result as RuntimeRomTableResult.Success).snapshot
        val s1 = snapshot.species[1]!!
        assertEquals("Sylveon", s1.name)
        assertEquals(PokemonType.FAIRY, s1.type1)
        assertEquals(PokemonType.STELLAR, s1.type2)
        val s2 = snapshot.species[2]!!
        assertEquals("Snorlax", s2.name)
        assertEquals(PokemonType.NORMAL, s2.type1)
        assertNull(s2.type2)

        val m33 = snapshot.moves[33]!!
        assertEquals("Tackle", m33.name)
        assertEquals(PokemonType.NORMAL, m33.type)
        assertEquals(40, m33.power)
        assertEquals(20, m33.pp)
        assertEquals(MoveCategory.PHYSICAL, m33.category)

        val m35 = snapshot.moves[35]!!
        assertEquals("Light Of Ruin", m35.name)
        assertEquals(PokemonType.FAIRY, m35.type)
        assertEquals(300, m35.power)
        assertEquals(20, m35.pp)
        assertEquals(MoveCategory.SPECIAL, m35.category)

        assertEquals(MoveCategory.STATUS, snapshot.moves[14]!!.category)
        assertEquals("Master Ball", snapshot.items[1])
        assertEquals("Item19", snapshot.items[19])

        // Pointer-name item layout (EXPANSION_V1_10_PTR_ITEM) reads pointer at +0x14 and rejects inline items
        assertTrue(
            RuntimeRomTableReader.read(
                rom,
                expSpec.copy(
                    layoutVersion = TableLayoutVersion.EXPANSION_V1_10_PTR_ITEM,
                    layoutRevisionId = "EXPANSION_V1_10_PTR_ITEM"
                )
            ) is RuntimeRomTableResult.Unavailable
        )

        val ptrItemsOff = 0x48000
        val ptrItemsStride = 44
        writeU32(rom, ptrItemsOff + 0x0C, allocAlignedString(""))
        writeU32(rom, ptrItemsOff + 0x14, allocAlignedString("????????"))
        for (i in 1 until 20) {
            val r = ptrItemsOff + i * ptrItemsStride
            writeU32(rom, r + 0x0C, allocAlignedString("Ptr Item desc $i"))
            writeU32(rom, r + 0x14, allocAlignedString(if (i == 1) "Ultra Ball" else "PtrItem$i"))
        }
        val ptrItemSpec = expSpec.copy(
            layoutVersion = TableLayoutVersion.EXPANSION_V1_10_PTR_ITEM,
            layoutRevisionId = "EXPANSION_V1_10_PTR_ITEM",
            items = ItemTableSpec(
                itemsAddress = RuntimeRomTableReader.ROM_BASE + ptrItemsOff,
                itemsStride = ptrItemsStride,
                count = 20
            )
        )
        val ptrItemRes = RuntimeRomTableReader.read(rom, ptrItemSpec)
        assertTrue(ptrItemRes is RuntimeRomTableResult.Success)
        assertEquals("Ultra Ball", (ptrItemRes as RuntimeRomTableResult.Success).snapshot.items[1])

        // Inconsistent specs with EXPANSION_V1_9_BITFIELD must fail closed:
        // (a) Explicit vanilla ppOffset = 4
        assertTrue(
            RuntimeRomTableReader.read(
                rom,
                expSpec.copy(moves = expSpec.moves.copy(ppOffset = 4))
            ) is RuntimeRomTableResult.Unavailable
        )
        // (b) Explicit vanilla GEN3_TYPE_MAP
        assertTrue(
            RuntimeRomTableReader.read(
                rom,
                expSpec.copy(typeMapping = RuntimeRomTableReader.GEN3_TYPE_MAP)
            ) is RuntimeRomTableResult.Unavailable
        )
        // (c) Inline item layout paired with nameIsPointer = true
        assertTrue(
            RuntimeRomTableReader.read(
                rom,
                expSpec.copy(items = expSpec.items!!.copy(nameIsPointer = true))
            ) is RuntimeRomTableResult.Unavailable
        )
    }

    @Test
    fun failsClosedOnUnknownLayoutRevisionAndMismatchedMoveSpec() {
        val rom = buildVanillaLayoutRom()

        val unknownLayoutSpec = vanillaSpec().copy(layoutRevisionId = "EXPANSION_UNKNOWN_V99")
        val unknownRes = RuntimeRomTableReader.read(rom, unknownLayoutSpec)
        assertTrue(unknownRes is RuntimeRomTableResult.Unavailable)
        assertTrue((unknownRes as RuntimeRomTableResult.Unavailable).reason.contains("Unsupported layout"))

        val mismatchedRevSpec = vanillaSpec().copy(
            layoutVersion = TableLayoutVersion.GEN3_VANILLA_BYTE,
            layoutRevisionId = "EXPANSION_V1_9_BITFIELD"
        )
        assertTrue(RuntimeRomTableReader.read(rom, mismatchedRevSpec) is RuntimeRomTableResult.Unavailable)

        val mismatchedRuleSpec = vanillaSpec().let {
            it.copy(
                layoutVersion = TableLayoutVersion.GEN3_VANILLA_BYTE,
                moves = it.moves.copy(categoryRule = MoveCategoryRule.PACKED_BITFIELD)
            )
        }
        assertTrue(RuntimeRomTableReader.read(rom, mismatchedRuleSpec) is RuntimeRomTableResult.Unavailable)
    }

    @Test
    fun failsClosedOnInlineOverflowGbaWindowSpanUnalignedRowPointerAndHardCountCap() {
        val rom = buildVanillaLayoutRom()

        // (a) Int.MAX_VALUE - 1 inline-pointer overflow returns Unavailable (never throws)
        val speciesOverflowSpec = vanillaSpec().let {
            it.copy(
                species = it.species.copy(
                    inlineNameOffset = Int.MAX_VALUE - 1,
                    inlineNameIsPointer = true
                )
            )
        }
        assertTrue(RuntimeRomTableReader.read(rom, speciesOverflowSpec) is RuntimeRomTableResult.Unavailable)

        val moveOverflowSpec = vanillaSpec().let {
            it.copy(
                moves = it.moves.copy(
                    inlineNameOffset = Int.MAX_VALUE - 1,
                    inlineNameIsPointer = true
                )
            )
        }
        assertTrue(RuntimeRomTableReader.read(rom, moveOverflowSpec) is RuntimeRomTableResult.Unavailable)

        // (b) Table spanning past 32 MiB GBA ROM window on an oversized backing array returns Unavailable
        val oversizedRom = ByteArray(33 * 1024 * 1024)
        rom.copyInto(oversizedRom)
        val pastGbaWindowSpec = vanillaSpec().let {
            it.copy(
                species = it.species.copy(
                    baseStatsAddress = RuntimeRomTableReader.ROM_BASE + (32L * 1024L * 1024L) - 16L,
                    count = 20
                )
            )
        }
        val pastWindowRes = RuntimeRomTableReader.read(oversizedRom, pastGbaWindowSpec)
        assertTrue(pastWindowRes is RuntimeRomTableResult.Unavailable)

        // (c) Unaligned 32-bit word pointer in a row returns Unavailable
        val unalignedPtrRom = ByteArray(1024 * 1024)
        val unalignedName = encodeGen3("TestMon")
        unalignedName.copyInto(unalignedPtrRom, 0x50001)
        for (i in 1 until 20) {
            val r = 0x10000 + i * 32
            for (s in 0..5) unalignedPtrRom[r + s] = 50
            unalignedPtrRom[r + 6] = 0
            unalignedPtrRom[r + 7] = 0
            writeU32(unalignedPtrRom, r + 16, RuntimeRomTableReader.ROM_BASE + 0x50001L)
        }
        val unalignedSpec = vanillaSpec().copy(
            species = SpeciesTableSpec(
                baseStatsAddress = RuntimeRomTableReader.ROM_BASE + 0x10000L,
                baseStatsStride = 32,
                count = 20,
                inlineNameOffset = 16,
                inlineNameIsPointer = true
            )
        )
        val unalignedRes = RuntimeRomTableReader.read(unalignedPtrRom, unalignedSpec)
        assertTrue(unalignedRes is RuntimeRomTableResult.Unavailable)
        assertTrue((unalignedRes as RuntimeRomTableResult.Unavailable).reason.contains("aligned"))

        // (c2) Unaligned pointer-field address containing a 4-byte-aligned target pointer returns Unavailable
        val unalignedFieldRom = buildVanillaLayoutRom()
        val alignedName = encodeGen3("AlignedMon")
        alignedName.copyInto(unalignedFieldRom, 0x50000) // 4-byte aligned target!
        for (i in 1 until 20) {
            val r = 0x18000 + i * 32
            for (s in 0..5) unalignedFieldRom[r + s] = 50
            unalignedFieldRom[r + 6] = 0
            unalignedFieldRom[r + 7] = 0
            writeU32(unalignedFieldRom, r + 17, RuntimeRomTableReader.ROM_BASE + 0x50000L)
        }
        val unalignedFieldSpec = vanillaSpec().copy(
            species = SpeciesTableSpec(
                baseStatsAddress = RuntimeRomTableReader.ROM_BASE + 0x18000L,
                baseStatsStride = 32,
                count = 20,
                inlineNameOffset = 17, // fieldOffset % 4 != 0
                inlineNameIsPointer = true
            )
        )
        val unalignedFieldRes = RuntimeRomTableReader.read(unalignedFieldRom, unalignedFieldSpec)
        assertTrue(unalignedFieldRes is RuntimeRomTableResult.Unavailable)
        assertTrue((unalignedFieldRes as RuntimeRomTableResult.Unavailable).reason.contains("aligned"))

        // Also test unaligned table base address with an aligned target pointer
        val unalignedBaseRom = buildVanillaLayoutRom()
        alignedName.copyInto(unalignedBaseRom, 0x50000)
        for (i in 1 until 20) {
            val r = 0x18001 + i * 32
            for (s in 0..5) unalignedBaseRom[r + s] = 50
            unalignedBaseRom[r + 6] = 0
            unalignedBaseRom[r + 7] = 0
            writeU32(unalignedBaseRom, r + 16, RuntimeRomTableReader.ROM_BASE + 0x50000L)
        }
        val unalignedBaseSpec = vanillaSpec().copy(
            species = SpeciesTableSpec(
                baseStatsAddress = RuntimeRomTableReader.ROM_BASE + 0x18001L, // tableBase % 4 != 0
                baseStatsStride = 32,
                count = 20,
                inlineNameOffset = 16,
                inlineNameIsPointer = true
            )
        )
        val unalignedBaseRes = RuntimeRomTableReader.read(unalignedBaseRom, unalignedBaseSpec)
        assertTrue(unalignedBaseRes is RuntimeRomTableResult.Unavailable)
        assertTrue((unalignedBaseRes as RuntimeRomTableResult.Unavailable).reason.contains("aligned"))

        // Also test unaligned row stride (row 0 aligned, row 1 unaligned) with an aligned target pointer
        val unalignedStrideRom = buildVanillaLayoutRom()
        alignedName.copyInto(unalignedStrideRom, 0x50000)
        for (i in 0 until 20) {
            val r = 0x18000 + i * 34
            if (i > 0) {
                for (s in 0..5) unalignedStrideRom[r + s] = 50
                unalignedStrideRom[r + 6] = 0
                unalignedStrideRom[r + 7] = 0
            }
            writeU32(unalignedStrideRom, r + 16, RuntimeRomTableReader.ROM_BASE + 0x50000L)
        }
        val unalignedStrideSpec = vanillaSpec().copy(
            species = SpeciesTableSpec(
                baseStatsAddress = RuntimeRomTableReader.ROM_BASE + 0x18000L,
                baseStatsStride = 34, // stride % 4 != 0
                count = 20,
                inlineNameOffset = 16,
                inlineNameIsPointer = true
            )
        )
        val unalignedStrideRes = RuntimeRomTableReader.read(unalignedStrideRom, unalignedStrideSpec)
        assertTrue(unalignedStrideRes is RuntimeRomTableResult.Unavailable)
        assertTrue((unalignedStrideRes as RuntimeRomTableResult.Unavailable).reason.contains("aligned"))

        // (d) Oversized count independent of caller SanityProbeSpec limits returns Unavailable
        val oversizedCountSpec = vanillaSpec(
            speciesCount = RuntimeRomTableReader.MAX_SUPPORTED_COUNT + 1,
            sanity = SanityProbeSpec(
                minSpeciesCount = 1,
                maxSpeciesCount = 10000,
                minMoveCount = 1,
                maxMoveCount = 10000
            )
        )
        val oversizedCountRes = RuntimeRomTableReader.read(rom, oversizedCountSpec)
        assertTrue(oversizedCountRes is RuntimeRomTableResult.Unavailable)

        val zeroCountSpec = vanillaSpec(
            speciesCount = 0,
            sanity = SanityProbeSpec(minSpeciesCount = 0, maxSpeciesCount = 100)
        )
        assertTrue(RuntimeRomTableReader.read(rom, zeroCountSpec) is RuntimeRomTableResult.Unavailable)
    }

    @Test
    fun failsClosedOnOutOfBoundsPointersTruncatedRomAndBadProbes() {
        val rom = buildVanillaLayoutRom()

        // 1. Out-of-ROM pointer (EWRAM)
        val badPtrSpec = vanillaSpec().let {
            it.copy(species = it.species.copy(baseStatsAddress = 0x02024000L))
        }
        assertTrue(RuntimeRomTableReader.read(rom, badPtrSpec) is RuntimeRomTableResult.Unavailable)

        // 2. Truncated ROM
        val truncated = rom.copyOf(0x20100)
        assertTrue(RuntimeRomTableReader.read(truncated, vanillaSpec()) is RuntimeRomTableResult.Unavailable)

        // 3. Failed move anchor power probe (move 33 expected 40, ROM has 35)
        val wrongPowerSpec = vanillaSpec(sanity = SanityProbeSpec(expectedMoveAnchors = mapOf(33 to 40)))
        val wrongPowerRes = RuntimeRomTableReader.read(rom, wrongPowerSpec)
        assertTrue(wrongPowerRes is RuntimeRomTableResult.Unavailable)
        assertTrue((wrongPowerRes as RuntimeRomTableResult.Unavailable).reason.contains("power"))

        // 4. Corrupt species names -> fails minValidDecodedNameRatio
        val corruptNamesRom = rom.copyOf()
        for (i in 0 until 40 * 11) {
            corruptNamesRom[0x10000 + i] = 0xFC.toByte() // control code / invalid
        }
        val corruptNameRes = RuntimeRomTableReader.read(corruptNamesRom, vanillaSpec())
        assertTrue(corruptNameRes is RuntimeRomTableResult.Unavailable)
        assertTrue((corruptNameRes as RuntimeRomTableResult.Unavailable).reason.contains("name ratio"))

        // 5. Unmapped type ID fails closed
        val badTypeRom = rom.copyOf()
        badTypeRom[0x20000 + 28 + 6] = 0x7F.toByte()
        assertTrue(RuntimeRomTableReader.read(badTypeRom, vanillaSpec()) is RuntimeRomTableResult.Unavailable)

        // 6. Bad pointer inside pointer-backed name field fails closed
        val badInlinePtrRom = ByteArray(1024 * 1024)
        for (i in 1 until 20) {
            val r = 0x10000 + i * 32
            badInlinePtrRom[r + 0] = 50
            badInlinePtrRom[r + 1] = 50
            badInlinePtrRom[r + 2] = 50
            badInlinePtrRom[r + 3] = 50
            badInlinePtrRom[r + 4] = 50
            badInlinePtrRom[r + 5] = 50
            badInlinePtrRom[r + 6] = 0
            badInlinePtrRom[r + 7] = 0
            writeU32(badInlinePtrRom, r + 16, 0x0B000000L) // out of ROM
        }
        val badInlineSpec = vanillaSpec().copy(
            species = SpeciesTableSpec(
                baseStatsAddress = RuntimeRomTableReader.ROM_BASE + 0x10000L,
                baseStatsStride = 32,
                count = 20,
                inlineNameOffset = 16,
                inlineNameIsPointer = true
            )
        )
        assertTrue(RuntimeRomTableReader.read(badInlinePtrRom, badInlineSpec) is RuntimeRomTableResult.Unavailable)
    }
}
