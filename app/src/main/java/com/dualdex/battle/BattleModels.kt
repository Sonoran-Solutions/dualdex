package com.dualdex.battle

import com.dualdex.calculator.CalcFieldInput
import com.dualdex.calculator.CalcMoveInput
import com.dualdex.calculator.CalcPokemonInput
import com.dualdex.calculator.DamageCalculationRequest
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

enum class EffectivenessLabel(val displayName: String) {
    SUBSTANTIAL("Substantial (2x)"),
    SUPER_EFFECTIVE("Super Effective (4x)"),
    NOT_VERY_EFFECTIVE("Not Very Effective (0.5x)"),
    EXTREMELY_NOT_EFFECTIVE("Extremely Immune (0.25x)"),
    NO_EFFECT("No Effect (0x)"),
    NEUTRAL("Neutral (1x)")
}

object MoveEffectiveness {

    const val UNAVAILABLE = "Unverified"

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

    fun labelFor(
        moveType: PokemonType,
        defenderType1: PokemonType?,
        defenderType2: PokemonType? = null,
        steelResistsGhostDark: Boolean = false
    ): String = confidence(moveType, defenderType1, defenderType2, steelResistsGhostDark)?.displayName ?: UNAVAILABLE

    fun isStatMove(category: MoveCategory): Boolean = category == MoveCategory.STATUS

    private fun labelFromMultiplier(mult: Double): EffectivenessLabel? = when {
        mult <= 0.0 -> EffectivenessLabel.NO_EFFECT
        mult >= 4.0 -> EffectivenessLabel.SUPER_EFFECTIVE
        mult >= 2.0 -> EffectivenessLabel.SUBSTANTIAL
        mult >= 1.0 -> EffectivenessLabel.NEUTRAL
        mult >= 0.5 -> EffectivenessLabel.NOT_VERY_EFFECTIVE
        else -> EffectivenessLabel.EXTREMELY_NOT_EFFECTIVE
    }

    fun defenderTypesOf(defender: ParsedPokemon?): Pair<PokemonType?, PokemonType?> {
        if (defender == null || defender.isEmpty || !defender.isValid) return null to null
        val species = SpeciesDatabase.get(defender.species)
        val t1 = species?.type1
        val t2 = species?.type2?.takeIf { it != t1 }
        return t1 to t2
    }
}

data class MovePresentation(
    val moveId: Int,
    val name: String,
    val typeName: String,
    val category: MoveCategory,
    val basePower: Int,
    val accuracy: Int,
    val maxPp: Int,
    val currentPp: Int?,
    val description: String,
    val effectiveness: String,
    val effectivenessVerified: Boolean,
    val damageVerified: Boolean,
    val minDamage: Int,
    val maxDamage: Int,
    val damageRange: List<Int>,
    val koChanceText: String
) {
    val isStatMove: Boolean get() = category == MoveCategory.STATUS
    val hasDamage: Boolean get() = damageVerified && maxDamage > 0
    val ppDisplay: String get() = if (currentPp != null) "$currentPp/$maxPp" else "??/$maxPp"

    companion object {
        const val UNVERIFIED_LABEL = "Unverified"
    }
}

object MovePresentationFactory {

    fun descriptionFor(
        moveInfo: MoveInfo,
        attackerLevel: Int,
        defenderLevel: Int
    ): String {
        val parts = mutableListOf<String>()
        parts += if (moveInfo.category == MoveCategory.STATUS) {
            "Status move (no direct damage)"
        } else {
            "Damage via ${moveInfo.category.displayName} stat"
        }
        if (moveInfo.power > 0) parts += "BP ${moveInfo.power}"
        if (moveInfo.accuracy > 0) parts += "${moveInfo.accuracy}% acc"
        if (moveInfo.pp > 0) parts += "max ${moveInfo.pp} PP"
        if (attackerLevel > 0 && defenderLevel > 0 && attackerLevel != defenderLevel) {
            parts += "Lv $attackerLevel vs Lv $defenderLevel"
        }
        return parts.joinToString(" · ")
    }

    fun isAttackerVerified(attacker: ParsedPokemon): Boolean =
        !attacker.isEmpty && attacker.isValid && attacker.level > 0
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
    val isMissing: Boolean
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
            isMissing = true
        )
    }
}

object ParticipantSummaryBuilder {

    fun isAttackerVerified(attacker: ParsedPokemon): Boolean =
        !attacker.isEmpty && attacker.isValid && attacker.level > 0

    fun build(mon: ParsedPokemon?, slot: Int): BattleParticipantSummary {
        if (mon == null || mon.isEmpty || !mon.isValid) {
            return BattleParticipantSummary(
                slot = slot,
                displayName = if (mon == null) "?" else (mon.nickname.trim() ?: "?"),
                speciesName = "?",
                level = 0,
                currentHp = 0,
                maxHp = 0,
                typeNames = emptyList(),
                statNames = emptyList(),
                moveNames = emptyList(),
                isVerified = false,
                isMissing = mon == null
            )
        }
        val species = SpeciesDatabase.get(mon.species)
        val displayName = mon.nickname.trim().ifEmpty { species?.name ?: "Mon #$mon.species" }
        val types = listOfNotNull(species?.type1?.displayName, species?.type2?.takeIf { it != species.type1 }?.displayName)
        val stats = listOf(
            "HP" to mon.maxHp,
            "Atk" to mon.attack,
            "Def" to mon.defense,
            "SpA" to mon.spAttack,
            "SpD" to mon.spDefense,
            "Spe" to mon.speed
        )
        val moves = mon.moves.toList().mapIndexed { i, id ->
            if (id > 0) MoveDatabase.get(id).name else "—"
        }
        return BattleParticipantSummary(
            slot = slot,
            displayName = displayName,
            speciesName = species?.name ?: "?",
            level = mon.level,
            currentHp = mon.currentHp,
            maxHp = mon.maxHp,
            typeNames = types,
            statNames = stats,
            moveNames = moves,
            isVerified = ParticipantSummaryBuilder.isAttackerVerified(mon),
            isMissing = false
        )
    }
}

object BattlePresentationBuilder {

    fun build(
        moveInfo: MoveInfo,
        currentPp: Int?,
        attacker: ParsedPokemon,
        defender: ParsedPokemon?,
        steelResistsGhostDark: Boolean
    ): MovePresentation {
        val attackerLevel = attacker.level.coerceAtLeast(1)
        val defenderLevel = defender?.level ?: attackerLevel
        val (defT1, defT2) = MoveEffectiveness.defenderTypesOf(defender)
        val label = MoveEffectiveness.confidence(moveInfo.type, defT1, defT2, steelResistsGhostDark)
        val description = MovePresentationFactory.descriptionFor(moveInfo, attackerLevel, defenderLevel)
        var verified = false
        var minDamage = 0
        var maxDamage = 0
        var range: List<Int> = emptyList()
        var koChance = ""
        if (label != null && !MoveEffectiveness.isStatMove(moveInfo.category)) {
            val request = buildDamageRequest(attacker, defender, moveInfo.name, attacker.natureName)
            val response = runCatching { DamageCalculator.calculate(request) }.getOrNull()
            if (response != null && response.success && response.maxDamage > 0) {
                verified = true
                minDamage = response.minDamage
                maxDamage = response.maxDamage
                range = response.range
                koChance = response.koChanceText
            }
        }
        return MovePresentation(
            moveId = moveInfo.id,
            name = moveInfo.name,
            typeName = moveInfo.type.displayName,
            category = moveInfo.category,
            basePower = moveInfo.power,
            accuracy = moveInfo.accuracy,
            maxPp = moveInfo.pp,
            currentPp = currentPp,
            description = description,
            effectiveness = label?.displayName ?: MoveEffectiveness.UNAVAILABLE,
            effectivenessVerified = label != null,
            damageVerified = verified,
            minDamage = minDamage,
            maxDamage = maxDamage,
            damageRange = range,
            koChanceText = koChance
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

    fun decode(statusCondition: Long): StatusCondition {
        if (statusCondition and 0x7FL == 0L) return StatusCondition.HEALTHY
        return when {
            statusCondition and 0x7L != 0L -> StatusCondition.SLEEP
            statusCondition and (1L shl 3) != 0L -> StatusCondition.POISON
            statusCondition and (1L shl 4) != 0L -> StatusCondition.BURN
            statusCondition and (1L shl 5) != 0L -> StatusCondition.FREEZE
            statusCondition and (1L shl 6) != 0L -> StatusCondition.PARALYSIS
            statusCondition and (1L shl 7) != 0L -> StatusCondition.BAD_POISON
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
    val damageNotes: List<String>
) {
    val isMissingData: Boolean get() = participant.isEmpty || opponent.isEmpty
}

object FieldStatusBuilder {

    fun build(
        attacker: ParsedPokemon?,
        defender: ParsedPokemon?,
        attackerSlot: Int,
        steelResistsGhostDark: Boolean
    ): FieldStatusData {
        val participant = ParticipantSummaryBuilder.build(attacker, attackerSlot)
        val opponent = ParticipantSummaryBuilder.build(defender, -1)
        val condition = if (attacker != null) StatusConditionDecoder.decode(attacker.statusCondition) else StatusCondition.HEALTHY
        val conditionVerified = attacker != null && attacker.isValid && !attacker.isEmpty
        val notes = mutableListOf<String>()
        val moves = attacker?.moves
        if (moves != null) {
            for (id in moves) {
                if (id <= 0) continue
                val info = MoveDatabase.get(id)
                val (defT1, defT2) = MoveEffectiveness.defenderTypesOf(defender)
                val label = MoveEffectiveness.confidence(info.type, defT1, defT2, steelResistsGhostDark)
                if (label == null) {
                    notes += "${info.name}: ${MoveEffectiveness.UNAVAILABLE}"
                }
            }
        }
        val damageNotes = mutableListOf<String>()
        if (participant.isEmpty) damageNotes += "Attacker data unavailable"
        if (opponent.isEmpty) damageNotes += "Opponent data unavailable"
        return FieldStatusData(
            inBattle = attacker != null && !attacker.isEmpty,
            participant = participant,
            opponent = opponent,
            condition = condition,
            conditionVerified = conditionVerified,
            effectivenessNotes = notes,
            damageNotes = damageNotes
        )
    }
}

fun buildDamageRequest(
    attacker: ParsedPokemon,
    defender: ParsedPokemon?,
    moveName: String,
    natureName: String
): DamageCalculationRequest {
    val attackerSpecies = SpeciesDatabase.get(attacker.species)?.name ?: "-"
    val attackerInput = CalcPokemonInput(
        species = attackerSpecies,
        level = attacker.level.takeIf { it > 0 } ?: 50,
        item = if (attacker.heldItem > 0) ItemDatabase.get(attacker.heldItem).name else null,
        nature = natureName,
        curHP = attacker.currentHp.takeIf { it > 0 } ?: attacker.maxHp.takeIf { it > 0 },
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
    val defenderSpecies = defender?.let { SpeciesDatabase.get(it.species)?.name ?: "-" }
        ?: attackerSpecies
    val defenderInput = if (defender != null) {
        CalcPokemonInput(
            species = defenderSpecies,
            level = defender.level.takeIf { it > 0 } ?: 50,
            item = if (defender.heldItem > 0) ItemDatabase.get(defender.heldItem).name else null,
            nature = defender.natureName,
            curHP = defender.currentHp.takeIf { it > 0 } ?: defender.maxHp.takeIf { it > 0 },
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
            weather = null,
            terrain = null,
            defenderSide = SideConditions(isReflect = false, isLightScreen = false)
        )
    )
}
