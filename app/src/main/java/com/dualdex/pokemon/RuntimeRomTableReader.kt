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
        requireTerminator: Boolean = true,
        allowNewline: Boolean = false
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
            if (allowNewline && b == 0xFE) {
                out.append('\n')
                continue
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
     * Pinned upstream `pokeemerald-expansion` v1.9.0–v1.10.0 (`include/move.h` `struct MoveInfo`,
     * `include/item.h` `struct Item`):
     * - `MoveInfo`: `type:5`, `category:2`, `power:9` at `+0x0A`; `accuracy:7`, `target:9` at `+0x0C`; `pp` at `+0x0E`
     * - `Item`: `description` ptr at `+0x0C`; inline `u8 name[20]` at `+0x14`
     */
    EXPANSION_V1_9_BITFIELD,

    /**
     * Pinned upstream `pokeemerald-expansion` v1.13.0 (`include/move.h` `struct MoveInfo`,
     * `include/item.h` `struct Item`):
     * - `MoveInfo`: `type:5`, `category:2`, `power:9` at `+0x0A`; `accuracy:7`, `target:9` at `+0x0C`; `pp` at `+0x0E`
     * - `Item`: `description` ptr at `+0x0C`; pointer-backed `const u8 *name` at `+0x14` (`pluralName` ptr at `+0x18`)
     */
    EXPANSION_V1_13_PTR_ITEM
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
    val ppOffset: Int? = null,
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

data class ItemTableSpec(
    val itemsAddress: Long,
    val itemsStride: Int,
    val count: Int,
    val nameOffset: Int? = null,
    val nameMaxLength: Int? = null,
    val nameIsPointer: Boolean? = null,
    val descPtrOffset: Int? = null
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
    val typeMapping: Map<Int, PokemonType>? = null,
    val sanity: SanityProbeSpec = SanityProbeSpec(),
    val layoutVersion: TableLayoutVersion = TableLayoutVersion.GEN3_VANILLA_BYTE,
    val layoutRevisionId: String? = null,
    val items: ItemTableSpec? = null
)

data class RuntimeRomTableSnapshot(
    val specId: String,
    val species: Map<Int, SpeciesInfo>,
    val moves: Map<Int, MoveInfo>,
    val speciesCount: Int,
    val moveCount: Int,
    val items: Map<Int, String> = emptyMap()
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
    private const val MAX_POINTER_DESC_LEN: Int = 256
    private const val VANILLA_MOVE_PP_OFFSET: Int = 4
    private const val EXPANSION_MOVE_COMBAT_WORD_OFFSET: Int = 0x0A
    private const val EXPANSION_MOVE_ACCURACY_WORD_OFFSET: Int = 0x0C
    private const val EXPANSION_MOVE_PP_OFFSET: Int = 0x0E
    private const val EXPANSION_ITEM_DESC_PTR_OFFSET: Int = 0x0C
    private const val EXPANSION_ITEM_NAME_OFFSET: Int = 0x14
    private const val EXPANSION_ITEM_INLINE_NAME_LEN: Int = 20
    private const val VANILLA_ITEM_NAME_OFFSET: Int = 0x00
    private const val VANILLA_ITEM_INLINE_NAME_LEN: Int = 14
    private const val VANILLA_ITEM_DESC_PTR_OFFSET: Int = 0x14

    val SUPPORTED_LAYOUT_REVISIONS: Map<String, TableLayoutVersion> = mapOf(
        "GEN3_VANILLA_BYTE" to TableLayoutVersion.GEN3_VANILLA_BYTE,
        "EXPANSION_V1_9_BITFIELD" to TableLayoutVersion.EXPANSION_V1_9_BITFIELD,
        "EXPANSION_V1_9_INLINE_ITEM" to TableLayoutVersion.EXPANSION_V1_9_BITFIELD,
        "EXPANSION_V1_13_PTR_ITEM" to TableLayoutVersion.EXPANSION_V1_13_PTR_ITEM
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
        val resolvedTypeMap = resolveTypeMapping(spec.typeMapping, resolvedLayout)
            ?: return RuntimeRomTableResult.Unavailable(
                "Layout $resolvedLayout requires EXPANSION_TYPE_MAP"
            )
        validateSpeciesSpecBounds(sSpec, effectiveRomBytes)?.let { return RuntimeRomTableResult.Unavailable(it) }
        val resolvedMoveOffsets = validateAndResolveMoveSpec(mSpec, resolvedLayout, effectiveRomBytes)
        if (resolvedMoveOffsets.error != null) {
            return RuntimeRomTableResult.Unavailable(resolvedMoveOffsets.error)
        }
        val resolvedItemOffsets = spec.items?.let { iSpec ->
            val res = validateAndResolveItemSpec(iSpec, resolvedLayout, effectiveRomBytes)
            if (res.error != null) return RuntimeRomTableResult.Unavailable(res.error)
            res
        }

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
                resolvedTypeMap[t1Raw] ?: PokemonType.NORMAL
            } else {
                resolvedTypeMap[t1Raw]
                    ?: return RuntimeRomTableResult.Unavailable("Species $id has unmapped type1 ID $t1Raw")
            }
            val type2 = if (allZeroStats) {
                if (t2Raw == t1Raw) null else resolvedTypeMap[t2Raw]
            } else if (t2Raw == 0 && 0 !in resolvedTypeMap) {
                // pokeemerald-expansion represents monotype second slots with TYPE_NONE (0) or repeated type1.
                null
            } else {
                val mapped2 = resolvedTypeMap[t2Raw]
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
        val combatWordOff = resolvedMoveOffsets.combatWordOffset
        val accWordOff = resolvedMoveOffsets.accWordOffset
        val movePpOff = resolvedMoveOffsets.ppOffset

        for (id in 0 until mSpec.count) {
            val rowOffL = movesBaseOffset + id.toLong() * mSpec.movesStride.toLong()
            val rowOff = rowOffL.toInt()
            val power: Int
            val typeRaw: Int
            val accuracy: Int
            val pp: Int
            val packedCategoryRaw: Int?

            when (resolvedLayout) {
                TableLayoutVersion.EXPANSION_V1_9_BITFIELD,
                TableLayoutVersion.EXPANSION_V1_13_PTR_ITEM -> {
                    val combatWord = u16(romBytes, rowOff + combatWordOff)
                    typeRaw = combatWord and 0x1F
                    packedCategoryRaw = (combatWord ushr 5) and 0x03
                    power = (combatWord ushr 7) and 0x1FF
                    val accWord = u16(romBytes, rowOff + accWordOff)
                    accuracy = accWord and 0x7F
                    pp = u8(romBytes, rowOff + movePpOff)
                }
                TableLayoutVersion.GEN3_VANILLA_BYTE -> {
                    power = u8(romBytes, rowOff + mSpec.powerOffset)
                    typeRaw = u8(romBytes, rowOff + mSpec.typeOffset)
                    accuracy = u8(romBytes, rowOff + mSpec.accuracyOffset)
                    pp = u8(romBytes, rowOff + movePpOff)
                    packedCategoryRaw = null
                }
            }

            if (id > 0 && accuracy > 100) {
                return RuntimeRomTableResult.Unavailable("Move $id has invalid accuracy $accuracy")
            }

            val moveType = if (id == 0) {
                resolvedTypeMap[typeRaw] ?: PokemonType.NORMAL
            } else {
                resolvedTypeMap[typeRaw]
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

        val resolvedItems = if (spec.items != null && resolvedItemOffsets != null) {
            when (val itemsRes = readItemsTable(
                romBytes = romBytes,
                effectiveRomBytes = effectiveRomBytes,
                iSpec = spec.items,
                offsets = resolvedItemOffsets,
                minValidDecodedNameRatio = sanity.minValidDecodedNameRatio
            )) {
                is ItemReadResult.Failure -> return RuntimeRomTableResult.Unavailable(itemsRes.reason)
                is ItemReadResult.Success -> itemsRes.items
            }
        } else {
            emptyMap()
        }

        return RuntimeRomTableResult.Success(
            RuntimeRomTableSnapshot(
                specId = spec.id,
                species = speciesMap,
                moves = movesMap,
                speciesCount = speciesMap.size,
                moveCount = movesMap.size,
                items = resolvedItems
            )
        )
    }

    private fun TableLayoutVersion.isPackedExpansion(): Boolean =
        this == TableLayoutVersion.EXPANSION_V1_9_BITFIELD ||
            this == TableLayoutVersion.EXPANSION_V1_13_PTR_ITEM

    private fun resolveTypeMapping(
        specTypeMapping: Map<Int, PokemonType>?,
        layoutVersion: TableLayoutVersion
    ): Map<Int, PokemonType>? {
        if (layoutVersion.isPackedExpansion()) {
            if (specTypeMapping != null && specTypeMapping != EXPANSION_TYPE_MAP) {
                return null
            }
            return EXPANSION_TYPE_MAP
        }
        return specTypeMapping ?: GEN3_TYPE_MAP
    }

    private data class ResolvedMoveOffsets(
        val combatWordOffset: Int = EXPANSION_MOVE_COMBAT_WORD_OFFSET,
        val accWordOffset: Int = EXPANSION_MOVE_ACCURACY_WORD_OFFSET,
        val ppOffset: Int = VANILLA_MOVE_PP_OFFSET,
        val error: String? = null
    )

    private data class ResolvedItemOffsets(
        val nameOffset: Int = 0,
        val nameMaxLength: Int = 0,
        val nameIsPointer: Boolean = false,
        val descPtrOffset: Int = 0,
        val error: String? = null
    )

    private sealed class ItemReadResult {
        data class Success(val items: Map<Int, String>) : ItemReadResult()
        data class Failure(val reason: String) : ItemReadResult()
    }

    private fun isFieldInStride(offset: Int, width: Int, stride: Int): Boolean {
        val offL = offset.toLong()
        val widthL = width.toLong()
        val strideL = stride.toLong()
        return offL >= 0L && widthL > 0L && strideL > 0L && offL <= strideL - widthL
    }

    private fun validateAlignedPointerField(
        tableAddress: Long,
        stride: Int,
        fieldOffset: Int,
        label: String
    ): String? {
        if (((tableAddress - ROM_BASE) and 3L) != 0L || (stride and 3) != 0 || (fieldOffset and 3) != 0) {
            return "$label pointer field requires 4-byte aligned table base, stride, and offset"
        }
        return null
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
            if (spec.inlineNameIsPointer) {
                validateAlignedPointerField(spec.baseStatsAddress, spec.baseStatsStride, off, "Species name")
                    ?.let { return it }
            }
        }
        return null
    }

    private fun validateAndResolveMoveSpec(
        spec: MoveTableSpec,
        layoutVersion: TableLayoutVersion,
        effectiveRomBytes: Long
    ): ResolvedMoveOffsets {
        val resolvedPpOffset: Int
        val resolvedCombatWordOff: Int
        val resolvedAccWordOff: Int
        when {
            layoutVersion == TableLayoutVersion.GEN3_VANILLA_BYTE -> {
                if (spec.movesStride < 5) {
                    return ResolvedMoveOffsets(error = "Move stride ${spec.movesStride} is too small")
                }
                if (spec.categoryRule == MoveCategoryRule.PACKED_BITFIELD) {
                    return ResolvedMoveOffsets(error = "PACKED_BITFIELD category rule requires EXPANSION_V1_9_BITFIELD layout")
                }
                if (spec.packedCombatWordOffset != null || spec.packedAccuracyWordOffset != null) {
                    return ResolvedMoveOffsets(error = "Packed bitfield offsets require EXPANSION_V1_9_BITFIELD layout")
                }
                resolvedPpOffset = spec.ppOffset ?: VANILLA_MOVE_PP_OFFSET
                resolvedCombatWordOff = EXPANSION_MOVE_COMBAT_WORD_OFFSET
                resolvedAccWordOff = EXPANSION_MOVE_ACCURACY_WORD_OFFSET
                val offsets = mutableListOf(spec.powerOffset, spec.typeOffset, spec.accuracyOffset, resolvedPpOffset)
                if (spec.categoryRule == MoveCategoryRule.BYTE_FIELD) {
                    val split = spec.splitOffset
                        ?: return ResolvedMoveOffsets(error = "BYTE_FIELD category rule requires splitOffset")
                    offsets.add(split)
                }
                if (offsets.any { !isFieldInStride(it, 1, spec.movesStride) }) {
                    return ResolvedMoveOffsets(error = "Move field offset outside stride ${spec.movesStride}")
                }
            }
            layoutVersion.isPackedExpansion() -> {
                if (spec.movesStride < 16) {
                    return ResolvedMoveOffsets(error = "Move stride ${spec.movesStride} is too small for $layoutVersion")
                }
                if (spec.categoryRule != MoveCategoryRule.PACKED_BITFIELD) {
                    return ResolvedMoveOffsets(error = "$layoutVersion layout requires PACKED_BITFIELD category rule")
                }
                if (spec.ppOffset != null && spec.ppOffset != EXPANSION_MOVE_PP_OFFSET) {
                    return ResolvedMoveOffsets(
                        error = "$layoutVersion layout requires ppOffset 0x${EXPANSION_MOVE_PP_OFFSET.toString(16)}, got ${spec.ppOffset}"
                    )
                }
                if (spec.packedCombatWordOffset != null && spec.packedCombatWordOffset != EXPANSION_MOVE_COMBAT_WORD_OFFSET) {
                    return ResolvedMoveOffsets(
                        error = "$layoutVersion layout requires packedCombatWordOffset 0x${EXPANSION_MOVE_COMBAT_WORD_OFFSET.toString(16)}"
                    )
                }
                if (spec.packedAccuracyWordOffset != null && spec.packedAccuracyWordOffset != EXPANSION_MOVE_ACCURACY_WORD_OFFSET) {
                    return ResolvedMoveOffsets(
                        error = "$layoutVersion layout requires packedAccuracyWordOffset 0x${EXPANSION_MOVE_ACCURACY_WORD_OFFSET.toString(16)}"
                    )
                }
                resolvedCombatWordOff = EXPANSION_MOVE_COMBAT_WORD_OFFSET
                resolvedAccWordOff = EXPANSION_MOVE_ACCURACY_WORD_OFFSET
                resolvedPpOffset = EXPANSION_MOVE_PP_OFFSET
                if (!isFieldInStride(resolvedCombatWordOff, 2, spec.movesStride) ||
                    !isFieldInStride(resolvedAccWordOff, 2, spec.movesStride) ||
                    !isFieldInStride(resolvedPpOffset, 1, spec.movesStride)
                ) {
                    return ResolvedMoveOffsets(error = "Move bitfield/pp offset outside stride ${spec.movesStride}")
                }
            }
            else -> return ResolvedMoveOffsets(error = "Unsupported layout version $layoutVersion")
        }
        validateTableSpan(spec.movesAddress, spec.count, spec.movesStride, effectiveRomBytes, "moves")
            ?.let { return ResolvedMoveOffsets(error = it) }

        spec.namesAddress?.let { addr ->
            if (spec.namesStride <= 0) return ResolvedMoveOffsets(error = "Move names stride must be positive")
            validateTableSpan(addr, spec.count, spec.namesStride, effectiveRomBytes, "move names")
                ?.let { return ResolvedMoveOffsets(error = it) }
        }
        spec.inlineNameOffset?.let { off ->
            val width = if (spec.inlineNameIsPointer) 4 else spec.inlineNameMaxLength
            if (!isFieldInStride(off, width, spec.movesStride)) {
                return ResolvedMoveOffsets(error = "Move inline name field outside stride ${spec.movesStride}")
            }
            if (spec.inlineNameIsPointer) {
                validateAlignedPointerField(spec.movesAddress, spec.movesStride, off, "Move name")
                    ?.let { return ResolvedMoveOffsets(error = it) }
            }
        }
        return ResolvedMoveOffsets(
            combatWordOffset = resolvedCombatWordOff,
            accWordOffset = resolvedAccWordOff,
            ppOffset = resolvedPpOffset
        )
    }

    private fun validateAndResolveItemSpec(
        spec: ItemTableSpec,
        layoutVersion: TableLayoutVersion,
        effectiveRomBytes: Long
    ): ResolvedItemOffsets {
        val resolvedNameOff: Int
        val resolvedNameLen: Int
        val resolvedIsPtr: Boolean
        val resolvedDescOff: Int
        val minStride: Int
        when (layoutVersion) {
            TableLayoutVersion.EXPANSION_V1_9_BITFIELD -> {
                minStride = 44
                if (spec.nameOffset != null && spec.nameOffset != EXPANSION_ITEM_NAME_OFFSET) {
                    return ResolvedItemOffsets(error = "EXPANSION_V1_9_BITFIELD requires item nameOffset 0x14")
                }
                if (spec.nameIsPointer != null && spec.nameIsPointer != false) {
                    return ResolvedItemOffsets(error = "EXPANSION_V1_9_BITFIELD requires inline item names (nameIsPointer=false)")
                }
                if (spec.nameMaxLength != null && spec.nameMaxLength != EXPANSION_ITEM_INLINE_NAME_LEN) {
                    return ResolvedItemOffsets(error = "EXPANSION_V1_9_BITFIELD requires item nameMaxLength 20")
                }
                if (spec.descPtrOffset != null && spec.descPtrOffset != EXPANSION_ITEM_DESC_PTR_OFFSET) {
                    return ResolvedItemOffsets(error = "EXPANSION_V1_9_BITFIELD requires item descPtrOffset 0x0C")
                }
                resolvedNameOff = EXPANSION_ITEM_NAME_OFFSET
                resolvedIsPtr = false
                resolvedNameLen = EXPANSION_ITEM_INLINE_NAME_LEN
                resolvedDescOff = EXPANSION_ITEM_DESC_PTR_OFFSET
            }
            TableLayoutVersion.EXPANSION_V1_13_PTR_ITEM -> {
                minStride = 44
                if (spec.nameOffset != null && spec.nameOffset != EXPANSION_ITEM_NAME_OFFSET) {
                    return ResolvedItemOffsets(error = "EXPANSION_V1_13_PTR_ITEM requires item nameOffset 0x14")
                }
                if (spec.nameIsPointer != null && spec.nameIsPointer != true) {
                    return ResolvedItemOffsets(error = "EXPANSION_V1_13_PTR_ITEM requires pointer-backed item names (nameIsPointer=true)")
                }
                if (spec.descPtrOffset != null && spec.descPtrOffset != EXPANSION_ITEM_DESC_PTR_OFFSET) {
                    return ResolvedItemOffsets(error = "EXPANSION_V1_13_PTR_ITEM requires item descPtrOffset 0x0C")
                }
                resolvedNameOff = EXPANSION_ITEM_NAME_OFFSET
                resolvedIsPtr = true
                resolvedNameLen = MAX_POINTER_NAME_LEN
                resolvedDescOff = EXPANSION_ITEM_DESC_PTR_OFFSET
            }
            TableLayoutVersion.GEN3_VANILLA_BYTE -> {
                minStride = 24
                resolvedNameOff = spec.nameOffset ?: VANILLA_ITEM_NAME_OFFSET
                resolvedIsPtr = spec.nameIsPointer ?: false
                resolvedNameLen = spec.nameMaxLength ?: VANILLA_ITEM_INLINE_NAME_LEN
                resolvedDescOff = spec.descPtrOffset ?: VANILLA_ITEM_DESC_PTR_OFFSET
            }
        }
        if (spec.itemsStride < minStride) {
            return ResolvedItemOffsets(error = "Item stride ${spec.itemsStride} is too small for $layoutVersion")
        }
        validateTableSpan(spec.itemsAddress, spec.count, spec.itemsStride, effectiveRomBytes, "items")
            ?.let { return ResolvedItemOffsets(error = it) }
        val nameWidth = if (resolvedIsPtr) 4 else resolvedNameLen
        if (!isFieldInStride(resolvedNameOff, nameWidth, spec.itemsStride) ||
            !isFieldInStride(resolvedDescOff, 4, spec.itemsStride)
        ) {
            return ResolvedItemOffsets(error = "Item field offset outside stride ${spec.itemsStride}")
        }
        validateAlignedPointerField(spec.itemsAddress, spec.itemsStride, resolvedDescOff, "Item description")
            ?.let { return ResolvedItemOffsets(error = it) }
        if (resolvedIsPtr) {
            validateAlignedPointerField(spec.itemsAddress, spec.itemsStride, resolvedNameOff, "Item name")
                ?.let { return ResolvedItemOffsets(error = it) }
        }
        return ResolvedItemOffsets(
            nameOffset = resolvedNameOff,
            nameMaxLength = resolvedNameLen,
            nameIsPointer = resolvedIsPtr,
            descPtrOffset = resolvedDescOff
        )
    }

    private fun readItemsTable(
        romBytes: ByteArray,
        effectiveRomBytes: Long,
        iSpec: ItemTableSpec,
        offsets: ResolvedItemOffsets,
        minValidDecodedNameRatio: Double
    ): ItemReadResult {
        val itemsBaseOffset = iSpec.itemsAddress - ROM_BASE
        val itemsMap = LinkedHashMap<Int, String>(iSpec.count)
        var validItemNames = 0

        for (id in 0 until iSpec.count) {
            val rowOffL = itemsBaseOffset + id.toLong() * iSpec.itemsStride.toLong()
            val descFieldOffL = rowOffL + offsets.descPtrOffset.toLong()
            when (val descPtrRes = readAlignedRomPointer(romBytes, effectiveRomBytes, descFieldOffL, "Item $id description")) {
                is RomPointerResult.BadPointer -> return ItemReadResult.Failure(descPtrRes.reason)
                RomPointerResult.Null -> {
                    if (id > 0) {
                        return ItemReadResult.Failure("Item $id has null description pointer")
                    }
                }
                is RomPointerResult.TargetOffset -> {
                    val maxDescLen = minOf(MAX_POINTER_DESC_LEN.toLong(), effectiveRomBytes - descPtrRes.offset).toInt()
                    val descText = Gen3TextDecoder.decode(
                        romBytes,
                        descPtrRes.offset.toInt(),
                        maxDescLen,
                        allowNewline = true
                    ) ?: return ItemReadResult.Failure("Item $id has malformed description text")
                    if (id > 0 && descText.isEmpty()) {
                        return ItemReadResult.Failure("Item $id has empty description text")
                    }
                }
            }

            val decodedName = readEntryName(
                romBytes = romBytes,
                effectiveRomBytes = effectiveRomBytes,
                id = id,
                rowOffset = rowOffL,
                separateNamesOffset = null,
                separateNamesStride = 0,
                inlineNameOffset = offsets.nameOffset,
                inlineNameMaxLength = offsets.nameMaxLength,
                inlineNameIsPointer = offsets.nameIsPointer
            )
            if (decodedName is NameReadResult.BadPointer) {
                return ItemReadResult.Failure("Item $id ${decodedName.reason}")
            }
            val nameStr = (decodedName as? NameReadResult.Ok)?.text
            if (id > 0 && !nameStr.isNullOrBlank()) {
                validItemNames++
            }
            if (id > 0) {
                itemsMap[id] = nameStr?.takeIf { it.isNotBlank() } ?: "Item #$id"
            }
        }

        val activeItemSlots = (iSpec.count - 1).coerceAtLeast(1)
        val nameRatio = validItemNames.toDouble() / activeItemSlots
        if (nameRatio < minValidDecodedNameRatio) {
            return ItemReadResult.Failure(
                "Decoded item name ratio $nameRatio below threshold $minValidDecodedNameRatio"
            )
        }
        return ItemReadResult.Success(itemsMap)
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

    private sealed class RomPointerResult {
        data class TargetOffset(val offset: Long) : RomPointerResult()
        object Null : RomPointerResult()
        data class BadPointer(val reason: String) : RomPointerResult()
    }

    private fun readAlignedRomPointer(
        romBytes: ByteArray,
        effectiveRomBytes: Long,
        fieldOffL: Long,
        label: String
    ): RomPointerResult {
        if (fieldOffL < 0L || fieldOffL > effectiveRomBytes - 4L) {
            return RomPointerResult.BadPointer("$label pointer field offset $fieldOffL out of ROM bounds")
        }
        if ((fieldOffL and 3L) != 0L) {
            return RomPointerResult.BadPointer(
                "$label pointer field offset 0x${fieldOffL.toString(16)} is not 4-byte aligned"
            )
        }
        val ptr = u32(romBytes, fieldOffL.toInt())
        if (ptr == 0L) {
            return RomPointerResult.Null
        }
        if ((ptr and 3L) != 0L) {
            return RomPointerResult.BadPointer("$label pointer 0x${ptr.toString(16)} is not 4-byte aligned")
        }
        if (ptr < ROM_BASE || ptr >= ROM_END_CAP) {
            return RomPointerResult.BadPointer("$label pointer 0x${ptr.toString(16)} out of GBA ROM range")
        }
        val targetOffsetL = ptr - ROM_BASE
        if (targetOffsetL < 0L || targetOffsetL >= effectiveRomBytes) {
            return RomPointerResult.BadPointer("$label pointer 0x${ptr.toString(16)} out of ROM bounds")
        }
        return RomPointerResult.TargetOffset(targetOffsetL)
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
            return when (val ptrRes = readAlignedRomPointer(romBytes, effectiveRomBytes, fieldOffL, "name")) {
                is RomPointerResult.BadPointer -> NameReadResult.BadPointer(ptrRes.reason)
                RomPointerResult.Null -> NameReadResult.Ok(null)
                is RomPointerResult.TargetOffset -> {
                    val maxLen = minOf(MAX_POINTER_NAME_LEN.toLong(), effectiveRomBytes - ptrRes.offset).toInt()
                    val text = Gen3TextDecoder.decode(romBytes, ptrRes.offset.toInt(), maxLen)
                    NameReadResult.Ok(text)
                }
            }
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
