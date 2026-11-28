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

    /** CFRU: explicit 1-byte category field (0=Physical, 1=Special, 2=Status). */
    BYTE_FIELD,

    /** Pinned pokeemerald-expansion v1.9+ (`MoveInfo`): 2-bit category inside packed combat `u16`. */
    PACKED_BITFIELD
}

enum class TableLayoutVersion {
    /** Vanilla Gen 3 / CFRU byte-field table layout (`power` u8, `type` u8, `accuracy` u8, `pp` u8). */
    GEN3_VANILLA_BYTE,

    /**
     * Pinned upstream `pokeemerald-expansion` v1.9+ (`include/move.h` `struct MoveInfo`) layout:
     * - `type:5`, `category:2`, `power:9` packed into little-endian `u16` at `+0x0A`
     * - `accuracy:7`, `target:9` packed into little-endian `u16` at `+0x0C`
     * - `pp` `u8` at `+0x0E`
     */
    EXPANSION_V1_9_BITFIELD
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
    val inlineNameIsPointer: Boolean = false,
    val packedCombatWordOffset: Int? = null,
    val packedAccuracyWordOffset: Int? = null
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
    val sanity: SanityProbeSpec = SanityProbeSpec(),
    val layoutVersion: TableLayoutVersion = TableLayoutVersion.GEN3_VANILLA_BYTE,
    val layoutRevisionId: String? = null
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
    const val MAX_GBA_ROM_BYTES: Long = ROM_END_CAP - ROM_BASE
    const val MAX_ROM_BYTES: Int = 64 * 1024 * 1024
    const val MAX_SUPPORTED_COUNT: Int = 4096
    private const val MAX_POINTER_NAME_LEN: Int = 32
    private const val EXPANSION_MOVE_COMBAT_WORD_OFFSET: Int = 0x0A
    private const val EXPANSION_MOVE_ACCURACY_WORD_OFFSET: Int = 0x0C

    val SUPPORTED_LAYOUT_REVISIONS: Map<String, TableLayoutVersion> = mapOf(
        "GEN3_VANILLA_BYTE" to TableLayoutVersion.GEN3_VANILLA_BYTE,
        "EXPANSION_V1_9_BITFIELD" to TableLayoutVersion.EXPANSION_V1_9_BITFIELD
    )

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

    /**
     * Pinned upstream `pokeemerald-expansion` (`include/constants/pokemon.h` `enum Type`):
     * `TYPE_NONE = 0` (omitted as invalid for move/primary type), `TYPE_NORMAL = 1`, ...,
     * `TYPE_STEEL = 9`, `TYPE_MYSTERY = 10`, `TYPE_FIRE = 11`, ..., `TYPE_FAIRY = 19`, `TYPE_STELLAR = 20`.
     */
    val EXPANSION_TYPE_MAP: Map<Int, PokemonType> = mapOf(
        0x01 to PokemonType.NORMAL,
        0x02 to PokemonType.FIGHTING,
        0x03 to PokemonType.FLYING,
        0x04 to PokemonType.POISON,
        0x05 to PokemonType.GROUND,
        0x06 to PokemonType.ROCK,
        0x07 to PokemonType.BUG,
        0x08 to PokemonType.GHOST,
        0x09 to PokemonType.STEEL,
        0x0A to PokemonType.NORMAL, // Expansion TYPE_MYSTERY ('???')
        0x0B to PokemonType.FIRE,
        0x0C to PokemonType.WATER,
        0x0D to PokemonType.GRASS,
        0x0E to PokemonType.ELECTRIC,
        0x0F to PokemonType.PSYCHIC,
        0x10 to PokemonType.ICE,
        0x11 to PokemonType.DRAGON,
        0x12 to PokemonType.DARK,
        0x13 to PokemonType.FAIRY,
        0x14 to PokemonType.STELLAR
    )

    val CFRU_TYPE_MAP: Map<Int, PokemonType> = GEN3_TYPE_MAP + mapOf(
        0x17 to PokemonType.FAIRY
    )

    fun read(romBytes: ByteArray, spec: RuntimeRomTableSpec): RuntimeRomTableResult {
        if (romBytes.isEmpty() || romBytes.size > MAX_ROM_BYTES) {
            return RuntimeRomTableResult.Unavailable("ROM size ${romBytes.size} is outside supported bounds")
        }

        val resolvedLayout = if (spec.layoutRevisionId != null) {
            val mapped = SUPPORTED_LAYOUT_REVISIONS[spec.layoutRevisionId]
                ?: return RuntimeRomTableResult.Unavailable("Unsupported layout version: ${spec.layoutRevisionId}")
            if (mapped != spec.layoutVersion) {
                return RuntimeRomTableResult.Unavailable(
                    "Layout revision ${spec.layoutRevisionId} disagrees with ${spec.layoutVersion}"
                )
            }
            mapped
        } else {
            spec.layoutVersion
        }

        val sSpec = spec.species
        val mSpec = spec.moves
        val sanity = spec.sanity

        if (sSpec.count !in 1..MAX_SUPPORTED_COUNT) {
            return RuntimeRomTableResult.Unavailable(
                "Species count ${sSpec.count} outside supported bounds [1, $MAX_SUPPORTED_COUNT]"
            )
        }
        if (mSpec.count !in 1..MAX_SUPPORTED_COUNT) {
            return RuntimeRomTableResult.Unavailable(
                "Move count ${mSpec.count} outside supported bounds [1, $MAX_SUPPORTED_COUNT]"
            )
        }
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

        val effectiveRomBytes = minOf(romBytes.size.toLong(), MAX_GBA_ROM_BYTES)
        validateSpeciesSpecBounds(sSpec, effectiveRomBytes)?.let { return RuntimeRomTableResult.Unavailable(it) }
        validateMoveSpecBounds(mSpec, resolvedLayout, effectiveRomBytes)?.let { return RuntimeRomTableResult.Unavailable(it) }

        val speciesBaseOffset = sSpec.baseStatsAddress - ROM_BASE
        val speciesNamesOffset = sSpec.namesAddress?.let { it - ROM_BASE }
        val speciesMap = LinkedHashMap<Int, SpeciesInfo>(sSpec.count)
        var nonZeroStatSpecies = 0
        var validSpeciesNames = 0
        val speciesNamesConfigured = sSpec.namesAddress != null || sSpec.inlineNameOffset != null

        for (id in 0 until sSpec.count) {
            val rowOffL = speciesBaseOffset + id.toLong() * sSpec.baseStatsStride.toLong()
            val rowOff = rowOffL.toInt()
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
            } else if (t2Raw == 0 && 0 !in spec.typeMapping) {
                // pokeemerald-expansion represents monotype second slots with TYPE_NONE (0) or repeated type1.
                null
            } else {
                val mapped2 = spec.typeMapping[t2Raw]
                    ?: return RuntimeRomTableResult.Unavailable("Species $id has unmapped type2 ID $t2Raw")
                if (t2Raw == t1Raw) null else mapped2
            }

            val decodedName = readEntryName(
                romBytes = romBytes,
                effectiveRomBytes = effectiveRomBytes,
                id = id,
                rowOffset = rowOffL,
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

        val movesBaseOffset = mSpec.movesAddress - ROM_BASE
        val movesNamesOffset = mSpec.namesAddress?.let { it - ROM_BASE }
        val movesMap = LinkedHashMap<Int, MoveInfo>(mSpec.count)
        var validMoveNames = 0
        val moveNamesConfigured = mSpec.namesAddress != null || mSpec.inlineNameOffset != null
        val combatWordOff = mSpec.packedCombatWordOffset ?: EXPANSION_MOVE_COMBAT_WORD_OFFSET
        val accWordOff = mSpec.packedAccuracyWordOffset ?: EXPANSION_MOVE_ACCURACY_WORD_OFFSET

        for (id in 0 until mSpec.count) {
            val rowOffL = movesBaseOffset + id.toLong() * mSpec.movesStride.toLong()
            val rowOff = rowOffL.toInt()
            val power: Int
            val typeRaw: Int
            val accuracy: Int
            val pp: Int
            val packedCategoryRaw: Int?

            when (resolvedLayout) {
                TableLayoutVersion.EXPANSION_V1_9_BITFIELD -> {
                    val combatWord = u16(romBytes, rowOff + combatWordOff)
                    typeRaw = combatWord and 0x1F
                    packedCategoryRaw = (combatWord ushr 5) and 0x03
                    power = (combatWord ushr 7) and 0x1FF
                    val accWord = u16(romBytes, rowOff + accWordOff)
                    accuracy = accWord and 0x7F
                    pp = u8(romBytes, rowOff + mSpec.ppOffset)
                }
                TableLayoutVersion.GEN3_VANILLA_BYTE -> {
                    power = u8(romBytes, rowOff + mSpec.powerOffset)
                    typeRaw = u8(romBytes, rowOff + mSpec.typeOffset)
                    accuracy = u8(romBytes, rowOff + mSpec.accuracyOffset)
                    pp = u8(romBytes, rowOff + mSpec.ppOffset)
                    packedCategoryRaw = null
                }
            }

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
                MoveCategoryRule.PACKED_BITFIELD -> {
                    when (val catBits = packedCategoryRaw!!) {
                        0 -> MoveCategory.PHYSICAL
                        1 -> MoveCategory.SPECIAL
                        2 -> MoveCategory.STATUS
                        else -> return RuntimeRomTableResult.Unavailable("Move $id has invalid packed category $catBits")
                    }
                }
            }

            val decodedName = readEntryName(
                romBytes = romBytes,
                effectiveRomBytes = effectiveRomBytes,
                id = id,
                rowOffset = rowOffL,
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

    private fun isFieldInStride(offset: Int, width: Int, stride: Int): Boolean {
        val offL = offset.toLong()
        val widthL = width.toLong()
        val strideL = stride.toLong()
        return offL >= 0L && widthL > 0L && strideL > 0L && offL <= strideL - widthL
    }

    private fun validateSpeciesSpecBounds(spec: SpeciesTableSpec, effectiveRomBytes: Long): String? {
        if (spec.baseStatsStride < 8) return "Species stride ${spec.baseStatsStride} is too small"
        val statOffsets = listOf(
            spec.hpOffset, spec.atkOffset, spec.defOffset,
            spec.speOffset, spec.spaOffset, spec.spdOffset,
            spec.type1Offset, spec.type2Offset
        )
        if (statOffsets.any { !isFieldInStride(it, 1, spec.baseStatsStride) }) {
            return "Species field offset outside stride ${spec.baseStatsStride}"
        }
        validateTableSpan(spec.baseStatsAddress, spec.count, spec.baseStatsStride, effectiveRomBytes, "species baseStats")
            ?.let { return it }

        spec.namesAddress?.let { addr ->
            if (spec.namesStride <= 0) return "Species names stride must be positive"
            validateTableSpan(addr, spec.count, spec.namesStride, effectiveRomBytes, "species names")?.let { return it }
        }
        spec.inlineNameOffset?.let { off ->
            val width = if (spec.inlineNameIsPointer) 4 else spec.inlineNameMaxLength
            if (!isFieldInStride(off, width, spec.baseStatsStride)) {
                return "Species inline name field outside stride ${spec.baseStatsStride}"
            }
        }
        return null
    }

    private fun validateMoveSpecBounds(
        spec: MoveTableSpec,
        layoutVersion: TableLayoutVersion,
        effectiveRomBytes: Long
    ): String? {
        when (layoutVersion) {
            TableLayoutVersion.GEN3_VANILLA_BYTE -> {
                if (spec.movesStride < 5) return "Move stride ${spec.movesStride} is too small"
                if (spec.categoryRule == MoveCategoryRule.PACKED_BITFIELD) {
                    return "PACKED_BITFIELD category rule requires EXPANSION_V1_9_BITFIELD layout"
                }
                if (spec.packedCombatWordOffset != null || spec.packedAccuracyWordOffset != null) {
                    return "Packed bitfield offsets require EXPANSION_V1_9_BITFIELD layout"
                }
                val offsets = mutableListOf(spec.powerOffset, spec.typeOffset, spec.accuracyOffset, spec.ppOffset)
                if (spec.categoryRule == MoveCategoryRule.BYTE_FIELD) {
                    val split = spec.splitOffset ?: return "BYTE_FIELD category rule requires splitOffset"
                    offsets.add(split)
                }
                if (offsets.any { !isFieldInStride(it, 1, spec.movesStride) }) {
                    return "Move field offset outside stride ${spec.movesStride}"
                }
            }
            TableLayoutVersion.EXPANSION_V1_9_BITFIELD -> {
                if (spec.movesStride < 16) {
                    return "Move stride ${spec.movesStride} is too small for EXPANSION_V1_9_BITFIELD"
                }
                if (spec.categoryRule != MoveCategoryRule.PACKED_BITFIELD) {
                    return "EXPANSION_V1_9_BITFIELD layout requires PACKED_BITFIELD category rule"
                }
                val combatWordOff = spec.packedCombatWordOffset ?: EXPANSION_MOVE_COMBAT_WORD_OFFSET
                val accWordOff = spec.packedAccuracyWordOffset ?: EXPANSION_MOVE_ACCURACY_WORD_OFFSET
                if (!isFieldInStride(combatWordOff, 2, spec.movesStride) ||
                    !isFieldInStride(accWordOff, 2, spec.movesStride) ||
                    !isFieldInStride(spec.ppOffset, 1, spec.movesStride)
                ) {
                    return "Move bitfield/pp offset outside stride ${spec.movesStride}"
                }
            }
        }
        validateTableSpan(spec.movesAddress, spec.count, spec.movesStride, effectiveRomBytes, "moves")
            ?.let { return it }

        spec.namesAddress?.let { addr ->
            if (spec.namesStride <= 0) return "Move names stride must be positive"
            validateTableSpan(addr, spec.count, spec.namesStride, effectiveRomBytes, "move names")?.let { return it }
        }
        spec.inlineNameOffset?.let { off ->
            val width = if (spec.inlineNameIsPointer) 4 else spec.inlineNameMaxLength
            if (!isFieldInStride(off, width, spec.movesStride)) {
                return "Move inline name field outside stride ${spec.movesStride}"
            }
        }
        return null
    }

    private fun validateTableSpan(
        gbaAddress: Long,
        count: Int,
        stride: Int,
        effectiveRomBytes: Long,
        label: String
    ): String? {
        if (count !in 1..MAX_SUPPORTED_COUNT) {
            return "Invalid $label count $count outside [1, $MAX_SUPPORTED_COUNT]"
        }
        if (stride <= 0) {
            return "Invalid $label stride $stride"
        }
        if (gbaAddress < ROM_BASE || gbaAddress >= ROM_END_CAP) {
            return "Malformed $label pointer 0x${gbaAddress.toString(16)} outside [0x08000000, 0x0A000000)"
        }
        val offset = gbaAddress - ROM_BASE
        val byteLen = count.toLong() * stride.toLong()
        if (offset < 0L || byteLen <= 0L || byteLen > effectiveRomBytes || offset > effectiveRomBytes - byteLen) {
            return "Truncated ROM: $label span [$offset, ${offset + byteLen}) exceeds ROM window $effectiveRomBytes"
        }
        return null
    }

    private sealed class NameReadResult {
        data class Ok(val text: String?) : NameReadResult()
        data class BadPointer(val reason: String) : NameReadResult()
    }

    private fun readEntryName(
        romBytes: ByteArray,
        effectiveRomBytes: Long,
        id: Int,
        rowOffset: Long,
        separateNamesOffset: Long?,
        separateNamesStride: Int,
        inlineNameOffset: Int?,
        inlineNameMaxLength: Int,
        inlineNameIsPointer: Boolean
    ): NameReadResult {
        if (separateNamesOffset != null) {
            val nameOffL = separateNamesOffset + id.toLong() * separateNamesStride.toLong()
            if (nameOffL < 0L || nameOffL > effectiveRomBytes - separateNamesStride.toLong()) {
                return NameReadResult.BadPointer("separate name offset $nameOffL out of ROM bounds")
            }
            val text = Gen3TextDecoder.decode(romBytes, nameOffL.toInt(), separateNamesStride)
            return NameReadResult.Ok(text)
        }
        if (inlineNameOffset != null) {
            val fieldOffL = rowOffset + inlineNameOffset.toLong()
            if (!inlineNameIsPointer) {
                if (fieldOffL < 0L || fieldOffL > effectiveRomBytes - inlineNameMaxLength.toLong()) {
                    return NameReadResult.BadPointer("inline name offset $fieldOffL out of ROM bounds")
                }
                val text = Gen3TextDecoder.decode(romBytes, fieldOffL.toInt(), inlineNameMaxLength)
                return NameReadResult.Ok(text)
            }
            if (fieldOffL < 0L || fieldOffL > effectiveRomBytes - 4L) {
                return NameReadResult.BadPointer("name pointer field offset $fieldOffL out of ROM bounds")
            }
            val ptr = u32(romBytes, fieldOffL.toInt())
            if (ptr == 0L) {
                return NameReadResult.Ok(null)
            }
            if ((ptr and 3L) != 0L) {
                return NameReadResult.BadPointer("name pointer 0x${ptr.toString(16)} is not 4-byte aligned")
            }
            if (ptr < ROM_BASE || ptr >= ROM_END_CAP) {
                return NameReadResult.BadPointer("name pointer 0x${ptr.toString(16)} out of GBA ROM range")
            }
            val targetOffsetL = ptr - ROM_BASE
            if (targetOffsetL < 0L || targetOffsetL >= effectiveRomBytes) {
                return NameReadResult.BadPointer("name pointer 0x${ptr.toString(16)} out of ROM bounds")
            }
            val maxLen = minOf(MAX_POINTER_NAME_LEN.toLong(), effectiveRomBytes - targetOffsetL).toInt()
            val text = Gen3TextDecoder.decode(romBytes, targetOffsetL.toInt(), maxLen)
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

    private fun u16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun u32(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xFFL) or
            ((bytes[offset + 1].toLong() and 0xFFL) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFFL) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFFL) shl 24)
}
