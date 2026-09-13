package com.dualdex.battle

import com.dualdex.calculator.CalcFieldInput
import com.dualdex.calculator.CalcMoveInput
import com.dualdex.calculator.CalcPokemonInput
import com.dualdex.calculator.DamageCalculationRequest
import com.dualdex.calculator.DamageCalculationResponse
import com.dualdex.calculator.DamageCalculator
import com.dualdex.calculator.SideConditions
import com.dualdex.calculator.StatBlock
import com.dualdex.pokemon.ItemDatabase
import com.dualdex.pokemon.MoveCategory
import com.dualdex.pokemon.MoveDatabase
import com.dualdex.pokemon.MoveInfo
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.SpeciesDatabase
import com.dualdex.pokemon.TypeChart
import com.dualdex.romhack.RomHackProfile
import java.util.concurrent.ConcurrentHashMap

/**
 * Explicit confidence states for game data and presentation attributes.
 */
enum class DataConfidence(val displayName: String) {
    VERIFIED("Verified"),
    ESTIMATE("Estimate"),
    UNAVAILABLE("Unavailable")
}

/**
 * Explicit confidence/availability model for damage calculations.
 */
enum class DamageConfidence(val displayName: String) {
    VERIFIED("Verified"),
    ESTIMATE("Estimate"),
    UNAVAILABLE("Damage unavailable for this ROM/profile")
}

/**
 * Stat stages (-6..+6) for Gen 3 battle mechanics.
 */
data class StatStages(
    val atk: Int = 0,
    val def: Int = 0,
    val spe: Int = 0,
    val spa: Int = 0,
    val spd: Int = 0,
    val acc: Int = 0,
    val eva: Int = 0
) {
    init {
        require(atk in -6..6) { "atk stage must be in -6..6, was $atk" }
        require(def in -6..6) { "def stage must be in -6..6, was $def" }
        require(spe in -6..6) { "spe stage must be in -6..6, was $spe" }
        require(spa in -6..6) { "spa stage must be in -6..6, was $spa" }
        require(spd in -6..6) { "spd stage must be in -6..6, was $spd" }
        require(acc in -6..6) { "acc stage must be in -6..6, was $acc" }
        require(eva in -6..6) { "eva stage must be in -6..6, was $eva" }
    }

    val isNeutral: Boolean
        get() = atk == 0 && def == 0 && spe == 0 && spa == 0 && spd == 0 && acc == 0 && eva == 0

    fun toBoostStatBlock(): StatBlock = StatBlock(
        hp = 0,
        atk = atk,
        def = def,
        spe = spe,
        spa = spa,
        spd = spd
    )

    fun activeStageChips(): List<Pair<String, Int>> {
        val list = mutableListOf<Pair<String, Int>>()
        if (atk != 0) list += "Atk" to atk
        if (def != 0) list += "Def" to def
        if (spa != 0) list += "SpA" to spa
        if (spd != 0) list += "SpD" to spd
        if (spe != 0) list += "Spe" to spe
        if (acc != 0) list += "Acc" to acc
        if (eva != 0) list += "Eva" to eva
        return list
    }

    companion object {
        fun formatStage(stage: Int): String = when {
            stage > 0 -> "+$stage"
            else -> stage.toString()
        }

        fun statMultiplier(stage: Int): Double = when {
            stage >= 0 -> (2.0 + stage) / 2.0
            else -> 2.0 / (2.0 - stage)
        }

        fun accuracyMultiplier(stage: Int): Double = when {
            stage >= 0 -> (3.0 + stage) / 3.0
            else -> 3.0 / (3.0 - stage)
        }

        fun fromRawArray(raw: IntArray?): StatStages {
            if (raw == null || raw.size < 7) return StatStages()
            return StatStages(
                atk = raw[0].coerceIn(-6, 6),
                def = raw[1].coerceIn(-6, 6),
                spe = raw[2].coerceIn(-6, 6),
                spa = raw[3].coerceIn(-6, 6),
                spd = raw[4].coerceIn(-6, 6),
                acc = raw[5].coerceIn(-6, 6),
                eva = raw[6].coerceIn(-6, 6)
            )
        }
    }
}

enum class WeatherType(val displayName: String, val calcName: String?) {
    NONE("Clear", null),
    SUN("Sun", "Sun"),
    RAIN("Rain", "Rain"),
    SANDSTORM("Sandstorm", "Sand"),
    HAIL("Hail", "Hail")
}

data class SideEffects(
    val reflect: Boolean = false,
    val reflectTurns: Int? = null,
    val lightScreen: Boolean = false,
    val lightScreenTurns: Int? = null,
    val tailwind: Boolean = false,
    val tailwindTurns: Int? = null,
    val spikesLayers: Int = 0,
    val safeguard: Boolean = false,
    val mist: Boolean = false
)

data class SpeedComparison(
    val playerEffectiveSpeed: Int,
    val enemyEffectiveSpeed: Int,
    val playerMovesFirst: Boolean?,
    val isSpeedTie: Boolean,
    val explanation: String
) {
    companion object {
        fun calculate(
            playerBaseSpeed: Int,
            playerStages: StatStages = StatStages(),
            playerParalyzed: Boolean = false,
            playerTailwind: Boolean = false,
            enemyBaseSpeed: Int,
            enemyStages: StatStages = StatStages(),
            enemyParalyzed: Boolean = false,
            enemyTailwind: Boolean = false,
            trickRoom: Boolean = false,
            playerMovePriority: Int = 0,
            enemyMovePriority: Int = 0
        ): SpeedComparison {
            var pSpeed = (playerBaseSpeed * StatStages.statMultiplier(playerStages.spe)).toInt()
            if (playerParalyzed) pSpeed = (pSpeed * 0.25).toInt().coerceAtLeast(1)
            if (playerTailwind) pSpeed *= 2

            var eSpeed = (enemyBaseSpeed * StatStages.statMultiplier(enemyStages.spe)).toInt()
            if (enemyParalyzed) eSpeed = (eSpeed * 0.25).toInt().coerceAtLeast(1)
            if (enemyTailwind) eSpeed *= 2

            if (playerMovePriority != enemyMovePriority) {
                val playerFirst = playerMovePriority > enemyMovePriority
                val diff = playerMovePriority - enemyMovePriority
                val sign = if (diff > 0) "+$diff" else "$diff"
                return SpeedComparison(
                    playerEffectiveSpeed = pSpeed,
                    enemyEffectiveSpeed = eSpeed,
                    playerMovesFirst = playerFirst,
                    isSpeedTie = false,
                    explanation = if (playerFirst) "Move priority ($sign) moves first"
                    else "Opponent priority ($sign) moves first"
                )
            }

            if (pSpeed == eSpeed) {
                return SpeedComparison(
                    playerEffectiveSpeed = pSpeed,
                    enemyEffectiveSpeed = eSpeed,
                    playerMovesFirst = null,
                    isSpeedTie = true,
                    explanation = "Speed tie ($pSpeed vs $eSpeed): 50% chance to move first"
                )
            }

            val playerFaster = if (trickRoom) pSpeed < eSpeed else pSpeed > eSpeed
            val exp = if (trickRoom) {
                if (playerFaster) "Trick Room active: lower speed moves first ($pSpeed vs $eSpeed)"
                else "Trick Room active: opponent moves first ($eSpeed vs $pSpeed)"
            } else {
                if (playerFaster) "Moves first ($pSpeed vs $eSpeed)"
                else "Moves second ($pSpeed vs $eSpeed)"
            }

            return SpeedComparison(
                playerEffectiveSpeed = pSpeed,
                enemyEffectiveSpeed = eSpeed,
                playerMovesFirst = playerFaster,
                isSpeedTie = false,
                explanation = exp
            )
        }
    }
}

enum class BattleUiState(val displayName: String) {
    COMMAND_MENU("Command Menu"),
    MOVE_MENU("Move Menu"),
    PARTY_MENU("Party Menu"),
    TARGET_SELECT("Target Select"),
    BAG_MENU("Bag Menu"),
    ANIMATION_OR_TEXT("Waiting / Anim"),
    UNKNOWN("Unknown / Inactive")
}

data class BattleUiSnapshot(
    val state: BattleUiState = BattleUiState.UNKNOWN,
    val selectedActionIndex: Int = 0,
    val selectedMoveIndex: Int = 0,
    val selectedPartySlot: Int = 0,
    val isInputAccepted: Boolean = false,
    val confidence: DataConfidence = DataConfidence.VERIFIED
)

/**
 * Injected interface for damage calculations to decouple UI/presentation from JNI and enable
 * 100% deterministic unit tests on host JVMs.
 */
fun interface BattleDamageCalculator {
    fun calculate(request: DamageCalculationRequest): DamageCalculationResponse
}

object DefaultBattleDamageCalculator : BattleDamageCalculator {
    override fun calculate(request: DamageCalculationRequest): DamageCalculationResponse {
        return runCatching { DamageCalculator.calculate(request) }
            .getOrElse { DamageCalculationResponse(success = false, error = it.message) }
    }
}

class CachingBattleDamageCalculator(
    private val delegate: BattleDamageCalculator = DefaultBattleDamageCalculator
) : BattleDamageCalculator {
    private val cache = ConcurrentHashMap<DamageCalculationRequest, DamageCalculationResponse>()

    override fun calculate(request: DamageCalculationRequest): DamageCalculationResponse {
        return cache.computeIfAbsent(request) { delegate.calculate(it) }
    }

    fun clear() {
        cache.clear()
    }
}

enum class EffectivenessLabel(val displayName: String) {
    SUBSTANTIAL("Substantial (2x)"),
    SUPER_EFFECTIVE("Super Effective (4x)"),
    NOT_VERY_EFFECTIVE("Not Very Effective (0.5x)"),
    EXTREMELY_NOT_EFFECTIVE("Extremely Not Effective (0.25x)"),
    NO_EFFECT("No Effect (0x)"),
    NEUTRAL("Neutral (1x)")
}

object MoveEffectiveness {

    const val UNAVAILABLE = "Unavailable"

    fun isStatMove(category: MoveCategory?): Boolean = category == MoveCategory.STATUS

    fun categoryForType(type: PokemonType): MoveCategory {
        return when (type) {
            PokemonType.NORMAL,
            PokemonType.FIGHTING,
            PokemonType.FLYING,
            PokemonType.POISON,
            PokemonType.GROUND,
            PokemonType.ROCK,
            PokemonType.BUG,
            PokemonType.GHOST,
            PokemonType.STEEL -> MoveCategory.PHYSICAL

            PokemonType.FIRE,
            PokemonType.WATER,
            PokemonType.GRASS,
            PokemonType.ELECTRIC,
            PokemonType.PSYCHIC,
            PokemonType.ICE,
            PokemonType.DRAGON,
            PokemonType.DARK,
            PokemonType.FAIRY -> MoveCategory.SPECIAL
        }
    }

    fun resolveMoveCategory(
        moveInfo: MoveInfo,
        profile: RomHackProfile
    ): MoveCategory? {
        if (!MoveDatabase.isKnown(moveInfo.id)) return null
        if (moveInfo.category == MoveCategory.STATUS) return MoveCategory.STATUS
        if (profile.hasPhysSpecSplit) return moveInfo.category
        return categoryForType(moveInfo.type)
    }

    fun confidence(
        moveType: PokemonType,
        defenderType1: PokemonType?,
        defenderType2: PokemonType? = null,
        steelResistsGhostDark: Boolean = false
    ): EffectivenessLabel? {
        if (defenderType1 == null) return null
        val mult1 = TypeChart.getEffectiveness(moveType, defenderType1, steelResistsGhostDark).toDouble()
        var mult = mult1
        if (defenderType2 != null) {
            mult *= TypeChart.getEffectiveness(moveType, defenderType2, steelResistsGhostDark).toDouble()
        }
        return labelFromMultiplier(mult)
    }

    fun evaluate(
        moveId: Int,
        category: MoveCategory?,
        defender: ParsedPokemon?,
        profile: RomHackProfile
    ): Pair<EffectivenessLabel?, DataConfidence> {
        if (moveId <= 0 || !MoveDatabase.isKnown(moveId)) {
            return null to DataConfidence.UNAVAILABLE
        }
        if (isStatMove(category)) {
            return null to DataConfidence.UNAVAILABLE
        }
        if (defender == null || defender.isEmpty || !defender.isValid) {
            return null to DataConfidence.UNAVAILABLE
        }
        val (defT1, defT2) = defenderTypesOf(defender, profile)
        if (defT1 == null) {
            return null to DataConfidence.UNAVAILABLE
        }
        val moveInfo = MoveDatabase.get(moveId)
        val label = confidence(moveInfo.type, defT1, defT2, profile.steelResistsGhostDark)
        val confidence = if (label != null && profile.isVerified) {
            DataConfidence.VERIFIED
        } else if (label != null) {
            DataConfidence.ESTIMATE
        } else {
            DataConfidence.UNAVAILABLE
        }
        return label to confidence
    }

    fun labelFor(
        moveType: PokemonType,
        defenderType1: PokemonType?,
        defenderType2: PokemonType? = null,
        steelResistsGhostDark: Boolean = false
    ): String = confidence(moveType, defenderType1, defenderType2, steelResistsGhostDark)?.displayName ?: UNAVAILABLE

    private fun labelFromMultiplier(mult: Double): EffectivenessLabel = when {
        mult <= 0.0 -> EffectivenessLabel.NO_EFFECT
        mult >= 4.0 -> EffectivenessLabel.SUPER_EFFECTIVE
        mult >= 2.0 -> EffectivenessLabel.SUBSTANTIAL
        mult >= 1.0 -> EffectivenessLabel.NEUTRAL
        mult >= 0.5 -> EffectivenessLabel.NOT_VERY_EFFECTIVE
        else -> EffectivenessLabel.EXTREMELY_NOT_EFFECTIVE
    }

    fun defenderTypesOf(defender: ParsedPokemon?, profile: RomHackProfile): Pair<PokemonType?, PokemonType?> {
        if (defender == null || defender.isEmpty || !defender.isValid) return null to null
        val customOverride = profile.customSpecies[defender.species]
        if (customOverride != null) {
            val t1 = PokemonType.fromString(customOverride.type1)
            val t2 = PokemonType.fromString(customOverride.type2)?.takeIf { it != t1 }
            return t1 to t2
        }
        if (!SpeciesDatabase.isKnown(defender.species)) {
            return null to null
        }
        val species = SpeciesDatabase.get(defender.species)
        val t1 = species.type1
        val t2 = species.type2?.takeIf { it != t1 }
        return t1 to t2
    }

    @Deprecated("Use overload with profile")
    fun defenderTypesOf(defender: ParsedPokemon?): Pair<PokemonType?, PokemonType?> {
        return defenderTypesOf(defender, RomHackProfile.DEFAULT_FIRERED)
    }
}

data class MovePresentation(
    val moveId: Int,
    val name: String,
    val typeName: String,
    val category: MoveCategory?,
    val basePower: Int?,
    val accuracy: Int?,
    val maxPp: Int?,
    val currentPp: Int?,
    val description: String,
    val effectiveness: String,
    val effectivenessConfidence: DataConfidence,
    val damageConfidence: DamageConfidence,
    val minDamage: Int,
    val maxDamage: Int,
    val damageRange: List<Int>,
    val koChanceText: String,
    val isKnown: Boolean = true
) {
    val isStatMove: Boolean get() = category == MoveCategory.STATUS
    val hasDamage: Boolean get() = damageConfidence == DamageConfidence.VERIFIED && maxDamage > 0
    val effectivenessVerified: Boolean get() = effectivenessConfidence == DataConfidence.VERIFIED
    val damageVerified: Boolean get() = damageConfidence == DamageConfidence.VERIFIED

    val ppDisplay: String get() = when {
        maxPp != null && currentPp != null -> "$currentPp/$maxPp"
        maxPp != null -> "??/$maxPp"
        currentPp != null -> "$currentPp/—"
        else -> "??/—"
    }

    val powerDisplay: String get() = basePower?.takeIf { it > 0 }?.toString() ?: "—"
    val accuracyDisplay: String get() = accuracy?.takeIf { it > 0 }?.let { "$it%" } ?: "—"

    val categoryDisplay: String get() = when (category) {
        MoveCategory.PHYSICAL -> "Phys"
        MoveCategory.SPECIAL -> "Spec"
        MoveCategory.STATUS -> "Status"
        null -> "—"
    }

    val damageDisplayText: String get() = when (damageConfidence) {
        DamageConfidence.VERIFIED -> {
            if (maxDamage > 0) {
                "$minDamage-$maxDamage" + if (koChanceText.isNotBlank()) " · $koChanceText" else ""
            } else {
                "0"
            }
        }
        DamageConfidence.ESTIMATE -> {
            if (maxDamage > 0) {
                "$minDamage-$maxDamage (Estimate)" + if (koChanceText.isNotBlank()) " · $koChanceText" else ""
            } else {
                "Estimate unavailable"
            }
        }
        DamageConfidence.UNAVAILABLE -> "Damage unavailable for this ROM/profile"
    }

    companion object {
        const val UNVERIFIED_LABEL = "Unverified"
        const val UNAVAILABLE_LABEL = "Damage unavailable for this ROM/profile"
    }
}

object MovePresentationFactory {

    fun descriptionFor(
        isKnown: Boolean,
        moveInfo: MoveInfo?,
        category: MoveCategory?,
        attackerLevel: Int,
        defenderLevel: Int
    ): String {
        if (!isKnown || moveInfo == null) {
            return "Move metadata unavailable for this ROM/profile"
        }
        val parts = mutableListOf<String>()
        parts += if (category == MoveCategory.STATUS) {
            "Status move (no direct damage)"
        } else if (category != null) {
            "Damage via ${category.displayName} stat"
        } else {
            "Category unavailable"
        }
        if (moveInfo.power > 0) parts += "BP ${moveInfo.power}"
        if (moveInfo.accuracy > 0) parts += "${moveInfo.accuracy}% acc"
        if (moveInfo.pp > 0) parts += "max ${moveInfo.pp} PP"
        if (attackerLevel > 0 && defenderLevel > 0 && attackerLevel != defenderLevel) {
            parts += "Lv $attackerLevel vs Lv $defenderLevel"
        }
        return parts.joinToString(" · ")
    }

    fun isAttackerVerified(attacker: ParsedPokemon, profile: RomHackProfile): Boolean =
        ParticipantSummaryBuilder.isParticipantVerified(attacker, profile)
}

data class BattleParticipantSummary(
    val slot: Int,
    val displayName: String,
    val speciesName: String,
    val level: Int,
    val currentHp: Int,
    val maxHp: Int,
    val typeNames: List<String>,
    val statNames: List<Pair<String, Int>>,
    val moveNames: List<String>,
    val isVerified: Boolean,
    val isMissing: Boolean,
    val confidence: DataConfidence = if (isVerified) DataConfidence.VERIFIED else DataConfidence.UNAVAILABLE,
    val statStages: StatStages = StatStages(),
    val effectiveSpeed: Int = 0
) {
    val hpDisplay: String get() = "$currentHp/$maxHp"
    val isEmpty: Boolean get() = isMissing && !isVerified

    companion object {
        val UNVERIFIED = BattleParticipantSummary(
            slot = -1,
            displayName = "?",
            speciesName = "?",
            level = 0,
            currentHp = 0,
            maxHp = 0,
            typeNames = emptyList(),
            statNames = emptyList(),
            moveNames = emptyList(),
            isVerified = false,
            isMissing = true,
            confidence = DataConfidence.UNAVAILABLE,
            statStages = StatStages(),
            effectiveSpeed = 0
        )
    }
}

object ParticipantSummaryBuilder {

    fun isParticipantVerified(mon: ParsedPokemon, profile: RomHackProfile): Boolean {
        if (mon.isEmpty || !mon.isValid || mon.level <= 0) return false
        val isKnown = profile.customSpecies.containsKey(mon.species) || SpeciesDatabase.isKnown(mon.species)
        if (!isKnown) return false
        return profile.isVerified
    }

    fun build(
        mon: ParsedPokemon?,
        slot: Int,
        profile: RomHackProfile,
        statStages: StatStages = StatStages()
    ): BattleParticipantSummary {
        if (mon == null || mon.isEmpty || !mon.isValid) {
            return BattleParticipantSummary(
                slot = slot,
                displayName = if (mon == null) "?" else mon.nickname.trim().ifEmpty { "?" },
                speciesName = "?",
                level = 0,
                currentHp = 0,
                maxHp = 0,
                typeNames = emptyList(),
                statNames = emptyList(),
                moveNames = emptyList(),
                isVerified = false,
                isMissing = mon == null,
                confidence = DataConfidence.UNAVAILABLE,
                statStages = statStages,
                effectiveSpeed = 0
            )
        }
        val custom = profile.customSpecies[mon.species]
        val isKnown = custom != null || SpeciesDatabase.isKnown(mon.species)
        val verified = isParticipantVerified(mon, profile)

        val speciesName: String
        val displayName: String
        val types: List<String>

        if (custom != null) {
            speciesName = custom.name
            displayName = mon.nickname.trim().ifEmpty { custom.name }
            types = listOfNotNull(custom.type1, custom.type2)
        } else if (SpeciesDatabase.isKnown(mon.species)) {
            val sp = SpeciesDatabase.get(mon.species)
            speciesName = sp.name
            displayName = mon.nickname.trim().ifEmpty { sp.name }
            types = listOfNotNull(sp.type1.displayName, sp.type2?.takeIf { it != sp.type1 }?.displayName)
        } else {
            // Unknown species: do NOT fabricate data or Normal typing!
            speciesName = "Unknown (#${mon.species})"
            displayName = mon.nickname.trim().ifEmpty { "Unknown (#${mon.species})" }
            types = emptyList()
        }

        val stats = listOf(
            "HP" to mon.maxHp,
            "Atk" to mon.attack,
            "Def" to mon.defense,
            "SpA" to mon.spAttack,
            "SpD" to mon.spDefense,
            "Spe" to mon.speed
        )
        val moves = mon.moves.toList().map { id ->
            if (id > 0) {
                if (MoveDatabase.isKnown(id)) MoveDatabase.get(id).name else "Unknown Move (#$id)"
            } else {
                "—"
            }
        }
        val confidence = when {
            verified -> DataConfidence.VERIFIED
            isKnown && profile.isVerified -> DataConfidence.ESTIMATE
            else -> DataConfidence.UNAVAILABLE
        }

        val isParalyzed = (mon.statusCondition and (1L shl 6)) != 0L
        var effSpeed = (mon.speed * StatStages.statMultiplier(statStages.spe)).toInt()
        if (isParalyzed) effSpeed = (effSpeed * 0.25).toInt().coerceAtLeast(1)

        return BattleParticipantSummary(
            slot = slot,
            displayName = displayName,
            speciesName = speciesName,
            level = mon.level,
            currentHp = mon.currentHp,
            maxHp = mon.maxHp,
            typeNames = types,
            statNames = stats,
            moveNames = moves,
            isVerified = verified,
            isMissing = false,
            confidence = confidence,
            statStages = statStages,
            effectiveSpeed = effSpeed
        )
    }

    @Deprecated("Use overload with profile")
    fun build(mon: ParsedPokemon?, slot: Int): BattleParticipantSummary {
        return build(mon, slot, RomHackProfile.DEFAULT_FIRERED)
    }
}

object BattlePresentationBuilder {

    fun build(
        moveInfo: MoveInfo,
        currentPp: Int?,
        attacker: ParsedPokemon,
        defender: ParsedPokemon?,
        profile: RomHackProfile,
        calculator: BattleDamageCalculator = DefaultBattleDamageCalculator,
        attackerStages: StatStages = StatStages(),
        defenderStages: StatStages = StatStages(),
        weather: WeatherType = WeatherType.NONE,
        defenderSide: SideEffects = SideEffects()
    ): MovePresentation {
        val moveKnown = MoveDatabase.isKnown(moveInfo.id)
        val attackerLevel = attacker.level.coerceAtLeast(1)
        val defenderLevel = defender?.level ?: attackerLevel

        val category = if (moveKnown) {
            MoveEffectiveness.resolveMoveCategory(moveInfo, profile)
        } else {
            null
        }

        val description = MovePresentationFactory.descriptionFor(
            isKnown = moveKnown,
            moveInfo = moveInfo.takeIf { moveKnown },
            category = category,
            attackerLevel = attackerLevel,
            defenderLevel = defenderLevel
        )

        // Evaluate effectiveness
        val (effLabel, effConfidence) = if (moveKnown) {
            MoveEffectiveness.evaluate(moveInfo.id, category, defender, profile)
        } else {
            null to DataConfidence.UNAVAILABLE
        }

        // Evaluate damage
        var damageConfidence = DamageConfidence.UNAVAILABLE
        var minDamage = 0
        var maxDamage = 0
        var range: List<Int> = emptyList()
        var koChance = ""

        val defenderSpeciesKnown = defender != null && !defender.isEmpty && defender.isValid &&
                (profile.customSpecies.containsKey(defender.species) || SpeciesDatabase.isKnown(defender.species))
        val attackerSpeciesKnown = !attacker.isEmpty && attacker.isValid &&
                (profile.customSpecies.containsKey(attacker.species) || SpeciesDatabase.isKnown(attacker.species))

        val canCalculate = moveKnown &&
                category != MoveCategory.STATUS &&
                moveInfo.power > 0 &&
                attackerSpeciesKnown &&
                defenderSpeciesKnown &&
                defender != null &&
                profile.isSupportedVanillaGen3()

        if (canCalculate) {
            val request = buildDamageRequest(
                attacker = attacker,
                defender = defender,
                moveName = moveInfo.name,
                natureName = attacker.natureName,
                attackerStages = attackerStages,
                defenderStages = defenderStages,
                weather = weather,
                defenderSide = defenderSide
            )
            val response = calculator.calculate(request)
            if (response.success && response.maxDamage > 0) {
                damageConfidence = DamageConfidence.VERIFIED
                minDamage = response.minDamage
                maxDamage = response.maxDamage
                range = response.range
                koChance = response.koChanceText
            }
        }

        return MovePresentation(
            moveId = moveInfo.id,
            name = if (moveKnown) moveInfo.name else "Unknown Move (#${moveInfo.id})",
            typeName = if (moveKnown) moveInfo.type.displayName else MoveEffectiveness.UNAVAILABLE,
            category = category,
            basePower = if (moveKnown) moveInfo.power else null,
            accuracy = if (moveKnown) moveInfo.accuracy else null,
            maxPp = if (moveKnown) moveInfo.pp else null,
            currentPp = currentPp,
            description = description,
            effectiveness = effLabel?.displayName ?: MoveEffectiveness.UNAVAILABLE,
            effectivenessConfidence = effConfidence,
            damageConfidence = damageConfidence,
            minDamage = minDamage,
            maxDamage = maxDamage,
            damageRange = range,
            koChanceText = koChance,
            isKnown = moveKnown
        )
    }
}

enum class StatusCondition(val displayName: String) {
    HEALTHY("Healthy"),
    SLEEP("Sleep"),
    POISON("Poison"),
    BURN("Burn"),
    FREEZE("Freeze"),
    PARALYSIS("Paralysis"),
    BAD_POISON("Bad Poison")
}

object StatusConditionDecoder {
    private const val SLEEP_MASK = 0x7L
    private const val POISON_MASK = 1L shl 3
    private const val BURN_MASK = 1L shl 4
    private const val FREEZE_MASK = 1L shl 5
    private const val PARALYSIS_MASK = 1L shl 6
    private const val BAD_POISON_MASK = 1L shl 7

    fun decode(statusCondition: Long): StatusCondition {
        return when {
            statusCondition and BAD_POISON_MASK != 0L -> StatusCondition.BAD_POISON
            statusCondition and SLEEP_MASK != 0L -> StatusCondition.SLEEP
            statusCondition and POISON_MASK != 0L -> StatusCondition.POISON
            statusCondition and BURN_MASK != 0L -> StatusCondition.BURN
            statusCondition and FREEZE_MASK != 0L -> StatusCondition.FREEZE
            statusCondition and PARALYSIS_MASK != 0L -> StatusCondition.PARALYSIS
            else -> StatusCondition.HEALTHY
        }
    }
}

data class FieldStatusData(
    val inBattle: Boolean,
    val participant: BattleParticipantSummary,
    val opponent: BattleParticipantSummary,
    val condition: StatusCondition,
    val conditionVerified: Boolean,
    val effectivenessNotes: List<String>,
    val damageNotes: List<String>,
    val weather: WeatherType = WeatherType.NONE,
    val playerSide: SideEffects = SideEffects(),
    val enemySide: SideEffects = SideEffects(),
    val speedComparison: SpeedComparison? = null
) {
    val isMissingData: Boolean get() = participant.isEmpty || opponent.isEmpty
}

object FieldStatusBuilder {

    fun build(
        inBattle: Boolean,
        attacker: ParsedPokemon?,
        defender: ParsedPokemon?,
        attackerSlot: Int,
        profile: RomHackProfile,
        playerStages: StatStages = StatStages(),
        enemyStages: StatStages = StatStages(),
        weather: WeatherType = WeatherType.NONE,
        playerSide: SideEffects = SideEffects(),
        enemySide: SideEffects = SideEffects()
    ): FieldStatusData {
        val participant = ParticipantSummaryBuilder.build(attacker, attackerSlot, profile, playerStages)
        val opponent = ParticipantSummaryBuilder.build(defender, -1, profile, enemyStages)
        val condition = if (attacker != null) StatusConditionDecoder.decode(attacker.statusCondition) else StatusCondition.HEALTHY
        val conditionVerified = attacker != null && attacker.isValid && !attacker.isEmpty && participant.isVerified

        val speedComp = if (attacker != null && defender != null && !attacker.isEmpty && !defender.isEmpty && attacker.isValid && defender.isValid) {
            val pParalyzed = (attacker.statusCondition and (1L shl 6)) != 0L
            val eParalyzed = (defender.statusCondition and (1L shl 6)) != 0L
            SpeedComparison.calculate(
                playerBaseSpeed = attacker.speed,
                playerStages = playerStages,
                playerParalyzed = pParalyzed,
                playerTailwind = playerSide.tailwind,
                enemyBaseSpeed = defender.speed,
                enemyStages = enemyStages,
                enemyParalyzed = eParalyzed,
                enemyTailwind = enemySide.tailwind
            )
        } else null

        val notes = mutableListOf<String>()
        val moves = attacker?.moves
        if (moves != null) {
            for (id in moves) {
                if (id <= 0) continue
                if (!MoveDatabase.isKnown(id)) {
                    notes += "Move #$id: ${MoveEffectiveness.UNAVAILABLE}"
                    continue
                }
                val info = MoveDatabase.get(id)
                val (effLabel, _) = MoveEffectiveness.evaluate(id, info.category, defender, profile)
                if (effLabel == null && !MoveEffectiveness.isStatMove(info.category)) {
                    notes += "${info.name}: ${MoveEffectiveness.UNAVAILABLE}"
                }
            }
        }

        val damageNotes = mutableListOf<String>()
        if (participant.isEmpty) damageNotes += "Attacker data unavailable"
        if (opponent.isEmpty) damageNotes += "Opponent data unavailable"
        if (!profile.isSupportedVanillaGen3()) {
            damageNotes += "Damage calculations unavailable for ${profile.name} (custom/split mechanics)"
        }

        return FieldStatusData(
            inBattle = inBattle,
            participant = participant,
            opponent = opponent,
            condition = condition,
            conditionVerified = conditionVerified,
            effectivenessNotes = notes,
            damageNotes = damageNotes,
            weather = weather,
            playerSide = playerSide,
            enemySide = enemySide,
            speedComparison = speedComp
        )
    }

    @Deprecated("Use overload with inBattle and profile")
    fun build(
        attacker: ParsedPokemon?,
        defender: ParsedPokemon?,
        attackerSlot: Int,
        steelResistsGhostDark: Boolean
    ): FieldStatusData {
        return build(
            inBattle = attacker != null && !attacker.isEmpty,
            attacker = attacker,
            defender = defender,
            attackerSlot = attackerSlot,
            profile = RomHackProfile.DEFAULT_FIRERED.copy(steelResistsGhostDark = steelResistsGhostDark)
        )
    }
}

fun buildDamageRequest(
    attacker: ParsedPokemon,
    defender: ParsedPokemon?,
    moveName: String,
    natureName: String,
    attackerStages: StatStages = StatStages(),
    defenderStages: StatStages = StatStages(),
    weather: WeatherType = WeatherType.NONE,
    defenderSide: SideEffects = SideEffects()
): DamageCalculationRequest {
    val attackerSpecies = SpeciesDatabase.get(attacker.species).name
    val attackerStatusStr = when {
        (attacker.statusCondition and (1L shl 7)) != 0L -> "tox"
        (attacker.statusCondition and (1L shl 3)) != 0L -> "psn"
        (attacker.statusCondition and (1L shl 4)) != 0L -> "brn"
        (attacker.statusCondition and (1L shl 5)) != 0L -> "frz"
        (attacker.statusCondition and (1L shl 6)) != 0L -> "par"
        (attacker.statusCondition and 0x7L) != 0L -> "slp"
        else -> null
    }
    val attackerInput = CalcPokemonInput(
        species = attackerSpecies,
        level = attacker.level.takeIf { it > 0 } ?: 50,
        item = if (attacker.heldItem > 0) ItemDatabase.get(attacker.heldItem).name else null,
        nature = natureName,
        curHP = attacker.currentHp.takeIf { it > 0 } ?: attacker.maxHp.takeIf { it > 0 },
        status = attackerStatusStr,
        boosts = attackerStages.toBoostStatBlock(),
        ivs = StatBlock(
            hp = attacker.hpIv,
            atk = attacker.attackIv,
            def = attacker.defenseIv,
            spa = attacker.spAttackIv,
            spd = attacker.spDefenseIv,
            spe = attacker.speedIv
        ),
        evs = StatBlock(
            hp = attacker.hpEv,
            atk = attacker.attackEv,
            def = attacker.defenseEv,
            spa = attacker.spAttackEv,
            spd = attacker.spDefenseEv,
            spe = attacker.speedEv
        )
    )
    val defenderSpecies = defender?.let { SpeciesDatabase.get(it.species).name }
        ?: attackerSpecies
    val defenderInput = if (defender != null) {
        val defenderStatusStr = when {
            (defender.statusCondition and (1L shl 7)) != 0L -> "tox"
            (defender.statusCondition and (1L shl 3)) != 0L -> "psn"
            (defender.statusCondition and (1L shl 4)) != 0L -> "brn"
            (defender.statusCondition and (1L shl 5)) != 0L -> "frz"
            (defender.statusCondition and (1L shl 6)) != 0L -> "par"
            (defender.statusCondition and 0x7L) != 0L -> "slp"
            else -> null
        }
        CalcPokemonInput(
            species = defenderSpecies,
            level = defender.level.takeIf { it > 0 } ?: 50,
            item = if (defender.heldItem > 0) ItemDatabase.get(defender.heldItem).name else null,
            nature = defender.natureName,
            curHP = defender.currentHp.takeIf { it > 0 } ?: defender.maxHp.takeIf { it > 0 },
            status = defenderStatusStr,
            boosts = defenderStages.toBoostStatBlock(),
            ivs = StatBlock(
                hp = defender.hpIv,
                atk = defender.attackIv,
                def = defender.defenseIv,
                spa = defender.spAttackIv,
                spd = defender.spDefenseIv,
                spe = defender.speedIv
            ),
            evs = StatBlock(
                hp = defender.hpEv,
                atk = defender.attackEv,
                def = defender.defenseEv,
                spa = defender.spAttackEv,
                spd = defender.spDefenseEv,
                spe = defender.speedEv
            )
        )
    } else {
        CalcPokemonInput(
            species = defenderSpecies,
            level = 50,
            boosts = defenderStages.toBoostStatBlock(),
            evs = StatBlock(hp = 252, def = 252, spd = 252)
        )
    }
    return DamageCalculationRequest(
        gen = 3,
        attacker = attackerInput,
        defender = defenderInput,
        move = CalcMoveInput(name = moveName, isCrit = false),
        field = CalcFieldInput(
            gameType = "singles",
            weather = weather.calcName,
            terrain = null,
            defenderSide = SideConditions(isReflect = defenderSide.reflect, isLightScreen = defenderSide.lightScreen)
        )
    )
}
