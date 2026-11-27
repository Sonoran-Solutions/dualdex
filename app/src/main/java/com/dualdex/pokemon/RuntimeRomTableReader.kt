package com.dualdex.pokemon

/**
 * Clean-room Gen 3 character table decoder for bounded ROM name fields.
 *
 * Supports A-Z (`0xBB..0xD4`), a-z (`0xD5..0xEE`), 0-9 (`0xA1..0xAA`), space (`0x00`),
 * Gen 3 punctuation/symbols, and the `0xFF` terminator. Any unknown glyph, control code,
 * out-of-bounds offset, or missing `0xFF` terminator fails closed (returns `null`).
 */
object Gen3TextDecoder {
    private val glyphTable: Map<Int, Char> = buildMap {
        put(0x00, ' ')
        put(0x1B, 'é')
        put(0xAB, '!')
        put(0xAC, '?')
        put(0xAD, '.')
        put(0xAE, '-')
        put(0xB0, '…')
        put(0xB1, '“')
        put(0xB2, '”')
        put(0xB3, '‘')
        put(0xB4, '’')
        put(0xB5, '♂')
        put(0xB6, '♀')
        put(0xB8, ',')
        put(0xBA, '/')
        put(0xF0, ':')
        for (i in 0..9) put(0xA1 + i, ('0'.code + i).toChar())
        for (i in 0..25) put(0xBB + i, ('A'.code + i).toChar())
        for (i in 0..25) put(0xD5 + i, ('a'.code + i).toChar())
    }

    /**
     * Decodes a bounded Gen 3 string starting at [offset] within [bytes], stopping at `0xFF`.
     * Returns `null` if [offset] or [maxLength] is invalid, no `0xFF` terminator appears within
     * [maxLength] bytes (when [requireTerminator] is true), or an unsupported byte is encountered.
     */
    fun decode(
        bytes: ByteArray,
        offset: Int,
        maxLength: Int,
        requireTerminator: Boolean = true
    ): String? {
        if (offset < 0 || maxLength <= 0 || offset >= bytes.size) return null
        val limit = minOf(maxLength, bytes.size - offset)
        val out = StringBuilder(limit)
        var terminated = false
        for (i in 0 until limit) {
            val b = bytes[offset + i].toInt() and 0xFF
            if (b == 0xFF) {
                terminated = true
                break
            }
            val ch = glyphTable[b] ?: return null
            out.append(ch)
        }
        if (requireTerminator && !terminated) return null
        return out.toString()
    }
}

enum class MoveCategoryRule {
    /** Vanilla Gen 3: `power == 0` -> STATUS; otherwise Physical vs Special by move type. */
    BY_TYPE_GEN3,

    /** CFRU / pokeemerald-expansion: explicit 1-byte category field (0=Physical, 1=Special, 2=Status). */
    BYTE_FIELD
}

data class SpeciesTableSpec(
    val baseStatsAddress: Long,
    val baseStatsStride: Int,
    val count: Int,
    val hpOffset: Int = 0,
    val atkOffset: Int = 1,
    val defOffset: Int = 2,
    val speOffset: Int = 3,
    val spaOffset: Int = 4,
    val spdOffset: Int = 5,
    val type1Offset: Int = 6,
    val type2Offset: Int = 7,
    val namesAddress: Long? = null,
    val namesStride: Int = 11,
    val inlineNameOffset: Int? = null,
    val inlineNameMaxLength: Int = 12,
    val inlineNameIsPointer: Boolean = false
)

data class MoveTableSpec(
    val movesAddress: Long,
    val movesStride: Int,
    val count: Int,
    val powerOffset: Int = 1,
    val typeOffset: Int = 2,
    val accuracyOffset: Int = 3,
    val ppOffset: Int = 4,
    val categoryRule: MoveCategoryRule = MoveCategoryRule.BY_TYPE_GEN3,
    val splitOffset: Int? = null,
    val namesAddress: Long? = null,
    val namesStride: Int = 13,
    val inlineNameOffset: Int? = null,
    val inlineNameMaxLength: Int = 17,
    val inlineNameIsPointer: Boolean = false
)

data class SanityProbeSpec(
    val minSpeciesCount: Int = 16,
    val maxSpeciesCount: Int = 4096,
    val minMoveCount: Int = 16,
    val maxMoveCount: Int = 4096,
    val minNonZeroStatSpeciesRatio: Double = 0.5,
    val minValidDecodedNameRatio: Double = 0.8,
    val expectedMoveAnchors: Map<Int, Int> = emptyMap(),
    val expectedSpeciesAnchors: Map<Int, Pair<PokemonType, PokemonType?>> = emptyMap(),
    val allowRandomizerTypeStatChanges: Boolean = false
)

data class RuntimeRomTableSpec(
    val id: String,
    val species: SpeciesTableSpec,
    val moves: MoveTableSpec,
    val typeMapping: Map<Int, PokemonType> = RuntimeRomTableReader.GEN3_TYPE_MAP,
    val sanity: SanityProbeSpec = SanityProbeSpec()
)

data class RuntimeRomTableSnapshot(
    val specId: String,
    val species: Map<Int, SpeciesInfo>,
    val moves: Map<Int, MoveInfo>,
    val speciesCount: Int,
    val moveCount: Int
) {
    /**
     * Wraps the validated ROM tables in a [GameDataPack] overlay, delegating type effectiveness
     * and mechanics flags to [basePack] while serving species/moves strictly from this snapshot.
     */
    fun toGameDataPack(
        basePack: GameDataPack,
        hasFairyType: Boolean = basePack.hasFairyType,
        hasPhysicalSpecialSplit: Boolean = basePack.hasPhysicalSpecialSplit
    ): GameDataPack = object : GameDataPack {
        override val id: String = "${basePack.id}_runtime_$specId"
        override val generation: Int = basePack.generation
        override val hasFairyType: Boolean = hasFairyType
        override val hasStellarType: Boolean = basePack.hasStellarType
        override val hasPhysicalSpecialSplit: Boolean = hasPhysicalSpecialSplit
        override val allowGlobalFallback: Boolean = false

        override fun getSpecies(id: Int): SpeciesInfo? = species[id]
        override fun getMove(id: Int): MoveInfo? = moves[id]
        override fun getSpeciesByName(name: String): SpeciesInfo? =
            species.values.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
        override fun getMoveByName(name: String): MoveInfo? =
            moves.values.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
        override fun getEffectiveness(attackType: PokemonType, defType: PokemonType): Double =
            basePack.getEffectiveness(attackType, defType)
        override fun isSpeciesAuthoritative(id: Int): Boolean = id in species
        override fun isMoveAuthoritative(id: Int): Boolean = id in moves
    }
}

sealed class RuntimeRomTableResult {
    data class Success(val snapshot: RuntimeRomTableSnapshot) : RuntimeRomTableResult()
    data class Unavailable(val reason: String) : RuntimeRomTableResult()
}

/**
 * Pure JVM, fail-closed runtime extractor for species base stats/types, move data, and Gen 3 names.
 *
 * Designed for:
 * - Vanilla and Universal Pokemon Randomizer (UPR ZX) FireRed/Emerald ROMs where tables are edited in-place.
 * - Future Tier-B CFRU and `pokeemerald-expansion` profiles once their [RuntimeRomTableSpec] is pinned.
 *
 * Refuses truncated ROMs, out-of-range GBA pointers (`[0x08000000, 0x0A000000)`), malformed
 * pointer-backed names, unmapped type/category bytes, and failed sanity probes.
 */
object RuntimeRomTableReader {
    const val ROM_BASE: Long = 0x08000000L
    const val ROM_END_CAP: Long = 0x0A000000L
    const val MAX_ROM_BYTES: Int = 64 * 1024 * 1024
    private const val MAX_POINTER_NAME_LEN: Int = 32

    val GEN3_TYPE_MAP: Map<Int, PokemonType> = mapOf(
        0x00 to PokemonType.NORMAL,
        0x01 to PokemonType.FIGHTING,
        0x02 to PokemonType.FLYING,
        0x03 to PokemonType.POISON,
        0x04 to PokemonType.GROUND,
        0x05 to PokemonType.ROCK,
        0x06 to PokemonType.BUG,
        0x07 to PokemonType.GHOST,
        0x08 to PokemonType.STEEL,
        0x09 to PokemonType.NORMAL, // Gen 3 TYPE_MYSTERY ('???')
        0x0A to PokemonType.FIRE,
        0x0B to PokemonType.WATER,
        0x0C to PokemonType.GRASS,
        0x0D to PokemonType.ELECTRIC,
        0x0E to PokemonType.PSYCHIC,
        0x0F to PokemonType.ICE,
        0x10 to PokemonType.DRAGON,
        0x11 to PokemonType.DARK
    )

    val EXPANSION_TYPE_MAP: Map<Int, PokemonType> = GEN3_TYPE_MAP + mapOf(
        0x12 to PokemonType.FAIRY,
        0x14 to PokemonType.STELLAR
    )

    val CFRU_TYPE_MAP: Map<Int, PokemonType> = GEN3_TYPE_MAP + mapOf(
        0x17 to PokemonType.FAIRY
    )

    fun read(romBytes: ByteArray, spec: RuntimeRomTableSpec): RuntimeRomTableResult {
        if (romBytes.isEmpty() || romBytes.size > MAX_ROM_BYTES) {
            return RuntimeRomTableResult.Unavailable("ROM size ${romBytes.size} is outside supported bounds")
        }

        val sSpec = spec.species
        val mSpec = spec.moves
        val sanity = spec.sanity

        if (sSpec.count !in sanity.minSpeciesCount..sanity.maxSpeciesCount) {
            return RuntimeRomTableResult.Unavailable(
                "Species count ${sSpec.count} outside bounds [${sanity.minSpeciesCount}, ${sanity.maxSpeciesCount}]"
            )
        }
        if (mSpec.count !in sanity.minMoveCount..sanity.maxMoveCount) {
            return RuntimeRomTableResult.Unavailable(
                "Move count ${mSpec.count} outside bounds [${sanity.minMoveCount}, ${sanity.maxMoveCount}]"
            )
        }

        validateSpeciesSpecBounds(sSpec, romBytes.size)?.let { return RuntimeRomTableResult.Unavailable(it) }
        validateMoveSpecBounds(mSpec, romBytes.size)?.let { return RuntimeRomTableResult.Unavailable(it) }

        val speciesBaseOffset = (sSpec.baseStatsAddress - ROM_BASE).toInt()
        val speciesNamesOffset = sSpec.namesAddress?.let { (it - ROM_BASE).toInt() }
        val speciesMap = LinkedHashMap<Int, SpeciesInfo>(sSpec.count)
        var nonZeroStatSpecies = 0
        var validSpeciesNames = 0
        val speciesNamesConfigured = sSpec.namesAddress != null || sSpec.inlineNameOffset != null

        for (id in 0 until sSpec.count) {
            val rowOff = speciesBaseOffset + id * sSpec.baseStatsStride
            val hp = u8(romBytes, rowOff + sSpec.hpOffset)
            val atk = u8(romBytes, rowOff + sSpec.atkOffset)
            val def = u8(romBytes, rowOff + sSpec.defOffset)
            val spe = u8(romBytes, rowOff + sSpec.speOffset)
            val spa = u8(romBytes, rowOff + sSpec.spaOffset)
            val spd = u8(romBytes, rowOff + sSpec.spdOffset)
            val t1Raw = u8(romBytes, rowOff + sSpec.type1Offset)
            val t2Raw = u8(romBytes, rowOff + sSpec.type2Offset)

            val allZeroStats = hp == 0 && atk == 0 && def == 0 && spe == 0 && spa == 0 && spd == 0
            if (id > 0 && !allZeroStats) {
                if (hp == 0 || atk == 0 || def == 0 || spe == 0 || spa == 0 || spd == 0) {
                    return RuntimeRomTableResult.Unavailable("Species $id has partial zero base stats")
                }
                nonZeroStatSpecies++
            }

            val type1 = if (allZeroStats) {
                spec.typeMapping[t1Raw] ?: PokemonType.NORMAL
            } else {
                spec.typeMapping[t1Raw]
                    ?: return RuntimeRomTableResult.Unavailable("Species $id has unmapped type1 ID $t1Raw")
            }
            val type2 = if (allZeroStats) {
                if (t2Raw == t1Raw) null else spec.typeMapping[t2Raw]
            } else {
                val mapped2 = spec.typeMapping[t2Raw]
                    ?: return RuntimeRomTableResult.Unavailable("Species $id has unmapped type2 ID $t2Raw")
                if (t2Raw == t1Raw) null else mapped2
            }

            val decodedName = readEntryName(
                romBytes = romBytes,
                id = id,
                rowOffset = rowOff,
                separateNamesOffset = speciesNamesOffset,
                separateNamesStride = sSpec.namesStride,
                inlineNameOffset = sSpec.inlineNameOffset,
                inlineNameMaxLength = sSpec.inlineNameMaxLength,
                inlineNameIsPointer = sSpec.inlineNameIsPointer
            )
            if (decodedName is NameReadResult.BadPointer) {
                return RuntimeRomTableResult.Unavailable("Species $id ${decodedName.reason}")
            }
            val nameStr = (decodedName as? NameReadResult.Ok)?.text
            if (id > 0 && !nameStr.isNullOrBlank()) {
                validSpeciesNames++
            }

            if (id > 0 && !allZeroStats) {
                speciesMap[id] = SpeciesInfo(
                    id = id,
                    name = nameStr?.takeIf { it.isNotBlank() } ?: "Species #$id",
                    type1 = type1,
                    type2 = type2,
                    baseHP = hp,
                    baseAtk = atk,
                    baseDef = def,
                    baseSpA = spa,
                    baseSpD = spd,
                    baseSpe = spe
                )
            }
        }

        val activeSpeciesSlots = (sSpec.count - 1).coerceAtLeast(1)
        val nonZeroRatio = nonZeroStatSpecies.toDouble() / activeSpeciesSlots
        if (nonZeroRatio < sanity.minNonZeroStatSpeciesRatio) {
            return RuntimeRomTableResult.Unavailable(
                "Non-zero stat species ratio $nonZeroRatio below threshold ${sanity.minNonZeroStatSpeciesRatio}"
            )
        }
        if (speciesNamesConfigured) {
            val nameRatio = validSpeciesNames.toDouble() / activeSpeciesSlots
            if (nameRatio < sanity.minValidDecodedNameRatio) {
                return RuntimeRomTableResult.Unavailable(
                    "Decoded species name ratio $nameRatio below threshold ${sanity.minValidDecodedNameRatio}"
                )
            }
        }

        if (!sanity.allowRandomizerTypeStatChanges) {
            for ((anchorId, expectedTypes) in sanity.expectedSpeciesAnchors) {
                val found = speciesMap[anchorId]
                    ?: return RuntimeRomTableResult.Unavailable("Missing expected anchor species $anchorId")
                if (found.type1 != expectedTypes.first || found.type2 != expectedTypes.second) {
                    return RuntimeRomTableResult.Unavailable(
                        "Anchor species $anchorId types (${found.type1}, ${found.type2}) != expected $expectedTypes"
                    )
                }
            }
        }

        val movesBaseOffset = (mSpec.movesAddress - ROM_BASE).toInt()
        val movesNamesOffset = mSpec.namesAddress?.let { (it - ROM_BASE).toInt() }
        val movesMap = LinkedHashMap<Int, MoveInfo>(mSpec.count)
        var validMoveNames = 0
        val moveNamesConfigured = mSpec.namesAddress != null || mSpec.inlineNameOffset != null

        for (id in 0 until mSpec.count) {
            val rowOff = movesBaseOffset + id * mSpec.movesStride
            val power = u8(romBytes, rowOff + mSpec.powerOffset)
            val typeRaw = u8(romBytes, rowOff + mSpec.typeOffset)
            val accuracy = u8(romBytes, rowOff + mSpec.accuracyOffset)
            val pp = u8(romBytes, rowOff + mSpec.ppOffset)

            if (id > 0 && accuracy > 100) {
                return RuntimeRomTableResult.Unavailable("Move $id has invalid accuracy $accuracy")
            }

            val moveType = if (id == 0) {
                spec.typeMapping[typeRaw] ?: PokemonType.NORMAL
            } else {
                spec.typeMapping[typeRaw]
                    ?: return RuntimeRomTableResult.Unavailable("Move $id has unmapped type ID $typeRaw")
            }

            val category = when (mSpec.categoryRule) {
                MoveCategoryRule.BY_TYPE_GEN3 -> {
                    if (power == 0) MoveCategory.STATUS else gen3CategoryForType(moveType)
                }
                MoveCategoryRule.BYTE_FIELD -> {
                    val splitOff = mSpec.splitOffset!!
                    when (val splitByte = u8(romBytes, rowOff + splitOff)) {
                        0 -> MoveCategory.PHYSICAL
                        1 -> MoveCategory.SPECIAL
                        2 -> MoveCategory.STATUS
                        else -> return RuntimeRomTableResult.Unavailable("Move $id has invalid split byte $splitByte")
                    }
                }
            }

            val decodedName = readEntryName(
                romBytes = romBytes,
                id = id,
                rowOffset = rowOff,
                separateNamesOffset = movesNamesOffset,
                separateNamesStride = mSpec.namesStride,
                inlineNameOffset = mSpec.inlineNameOffset,
                inlineNameMaxLength = mSpec.inlineNameMaxLength,
                inlineNameIsPointer = mSpec.inlineNameIsPointer
            )
            if (decodedName is NameReadResult.BadPointer) {
                return RuntimeRomTableResult.Unavailable("Move $id ${decodedName.reason}")
            }
            val nameStr = (decodedName as? NameReadResult.Ok)?.text
            if (id > 0 && !nameStr.isNullOrBlank()) {
                validMoveNames++
            }

            if (id > 0) {
                movesMap[id] = MoveInfo(
                    id = id,
                    name = nameStr?.takeIf { it.isNotBlank() } ?: "Move #$id",
                    type = moveType,
                    category = category,
                    power = power,
                    accuracy = accuracy,
                    pp = pp
                )
            }
        }

        if (moveNamesConfigured) {
            val activeMoveSlots = (mSpec.count - 1).coerceAtLeast(1)
            val nameRatio = validMoveNames.toDouble() / activeMoveSlots
            if (nameRatio < sanity.minValidDecodedNameRatio) {
                return RuntimeRomTableResult.Unavailable(
                    "Decoded move name ratio $nameRatio below threshold ${sanity.minValidDecodedNameRatio}"
                )
            }
        }

        for ((moveId, expectedPower) in sanity.expectedMoveAnchors) {
            val found = movesMap[moveId]
                ?: return RuntimeRomTableResult.Unavailable("Missing expected anchor move $moveId")
            if (found.power != expectedPower) {
                return RuntimeRomTableResult.Unavailable(
                    "Anchor move $moveId power ${found.power} != expected $expectedPower"
                )
            }
        }

        return RuntimeRomTableResult.Success(
            RuntimeRomTableSnapshot(
                specId = spec.id,
                species = speciesMap,
                moves = movesMap,
                speciesCount = speciesMap.size,
                moveCount = movesMap.size
            )
        )
    }

    private fun validateSpeciesSpecBounds(spec: SpeciesTableSpec, romSize: Int): String? {
        if (spec.baseStatsStride < 8) return "Species stride ${spec.baseStatsStride} is too small"
        val statOffsets = listOf(
            spec.hpOffset, spec.atkOffset, spec.defOffset,
            spec.speOffset, spec.spaOffset, spec.spdOffset,
            spec.type1Offset, spec.type2Offset
        )
        if (statOffsets.any { it < 0 || it >= spec.baseStatsStride }) {
            return "Species field offset outside stride ${spec.baseStatsStride}"
        }
        validateTableSpan(spec.baseStatsAddress, spec.count, spec.baseStatsStride, romSize, "species baseStats")
            ?.let { return it }

        spec.namesAddress?.let { addr ->
            if (spec.namesStride <= 0) return "Species names stride must be positive"
            validateTableSpan(addr, spec.count, spec.namesStride, romSize, "species names")?.let { return it }
        }
        spec.inlineNameOffset?.let { off ->
            val width = if (spec.inlineNameIsPointer) 4 else spec.inlineNameMaxLength
            if (off < 0 || width <= 0 || off + width > spec.baseStatsStride) {
                return "Species inline name field outside stride ${spec.baseStatsStride}"
            }
        }
        return null
    }

    private fun validateMoveSpecBounds(spec: MoveTableSpec, romSize: Int): String? {
        if (spec.movesStride < 5) return "Move stride ${spec.movesStride} is too small"
        val offsets = mutableListOf(spec.powerOffset, spec.typeOffset, spec.accuracyOffset, spec.ppOffset)
        if (spec.categoryRule == MoveCategoryRule.BYTE_FIELD) {
            val split = spec.splitOffset ?: return "BYTE_FIELD category rule requires splitOffset"
            offsets.add(split)
        }
        if (offsets.any { it < 0 || it >= spec.movesStride }) {
            return "Move field offset outside stride ${spec.movesStride}"
        }
        validateTableSpan(spec.movesAddress, spec.count, spec.movesStride, romSize, "moves")
            ?.let { return it }

        spec.namesAddress?.let { addr ->
            if (spec.namesStride <= 0) return "Move names stride must be positive"
            validateTableSpan(addr, spec.count, spec.namesStride, romSize, "move names")?.let { return it }
        }
        spec.inlineNameOffset?.let { off ->
            val width = if (spec.inlineNameIsPointer) 4 else spec.inlineNameMaxLength
            if (off < 0 || width <= 0 || off + width > spec.movesStride) {
                return "Move inline name field outside stride ${spec.movesStride}"
            }
        }
        return null
    }

    private fun validateTableSpan(
        gbaAddress: Long,
        count: Int,
        stride: Int,
        romSize: Int,
        label: String
    ): String? {
        if (gbaAddress < ROM_BASE || gbaAddress >= ROM_END_CAP) {
            return "Malformed $label pointer 0x${gbaAddress.toString(16)} outside [0x08000000, 0x0A000000)"
        }
        val offset = gbaAddress - ROM_BASE
        val byteLen = count.toLong() * stride.toLong()
        if (offset + byteLen > romSize.toLong()) {
            return "Truncated ROM: $label span [$offset, ${offset + byteLen}) exceeds ROM size $romSize"
        }
        return null
    }

    private sealed class NameReadResult {
        data class Ok(val text: String?) : NameReadResult()
        data class BadPointer(val reason: String) : NameReadResult()
    }

    private fun readEntryName(
        romBytes: ByteArray,
        id: Int,
        rowOffset: Int,
        separateNamesOffset: Int?,
        separateNamesStride: Int,
        inlineNameOffset: Int?,
        inlineNameMaxLength: Int,
        inlineNameIsPointer: Boolean
    ): NameReadResult {
        if (separateNamesOffset != null) {
            val text = Gen3TextDecoder.decode(romBytes, separateNamesOffset + id * separateNamesStride, separateNamesStride)
            return NameReadResult.Ok(text)
        }
        if (inlineNameOffset != null) {
            val fieldOff = rowOffset + inlineNameOffset
            if (!inlineNameIsPointer) {
                val text = Gen3TextDecoder.decode(romBytes, fieldOff, inlineNameMaxLength)
                return NameReadResult.Ok(text)
            }
            val ptr = u32(romBytes, fieldOff)
            if (ptr == 0L) {
                return NameReadResult.Ok(null)
            }
            if (ptr < ROM_BASE || ptr >= ROM_END_CAP || (ptr - ROM_BASE) >= romBytes.size.toLong()) {
                return NameReadResult.BadPointer("name pointer 0x${ptr.toString(16)} out of ROM bounds")
            }
            val targetOffset = (ptr - ROM_BASE).toInt()
            val text = Gen3TextDecoder.decode(romBytes, targetOffset, MAX_POINTER_NAME_LEN)
            return NameReadResult.Ok(text)
        }
        return NameReadResult.Ok(null)
    }

    private fun gen3CategoryForType(type: PokemonType): MoveCategory = when (type) {
        PokemonType.NORMAL, PokemonType.FIGHTING, PokemonType.FLYING,
        PokemonType.POISON, PokemonType.GROUND, PokemonType.ROCK,
        PokemonType.BUG, PokemonType.GHOST, PokemonType.STEEL -> MoveCategory.PHYSICAL
        else -> MoveCategory.SPECIAL
    }

    private fun u8(bytes: ByteArray, offset: Int): Int = bytes[offset].toInt() and 0xFF

    private fun u32(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xFFL) or
            ((bytes[offset + 1].toLong() and 0xFFL) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFFL) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFFL) shl 24)
}
