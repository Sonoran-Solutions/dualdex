package com.dualdex.battle

import com.dualdex.calculator.CalcSupport

enum class EffectivenessTone { STRONG, NEUTRAL, WEAK, NONE, UNKNOWN }

/**
 * What one tile of the Battle tab's 2×2 move grid shows. Pure so the grid's text, visibility and
 * tap rules are testable without a View. The full damage text lives in [MoveTileModels.fullDamageText]
 * for the long-press details sheet.
 */
data class MoveTileModel(
    val name: String,
    val typeName: String,
    val ppText: String,
    /** Null when hidden by the user's setting. */
    val damageText: String?,
    /** Null when hidden by the user's setting or when no label is available. */
    val effectivenessText: String?,
    val effectivenessTone: EffectivenessTone,
    val outOfPp: Boolean,
    /** True only when the verified input gate allows selection and the move has PP. */
    val selectable: Boolean,
    val contentDescription: String
)

object MoveTileModels {

    fun from(
        pres: MovePresentation,
        defenderMaxHp: Int?,
        showDamage: Boolean,
        showEffectiveness: Boolean,
        selectionAllowed: Boolean
    ): MoveTileModel {
        val outOfPp = pres.currentPp == 0
        val damage = if (showDamage) compactDamageText(pres, defenderMaxHp) else null
        val tone = toneOf(pres.effectiveness)
        val effectiveness = if (showEffectiveness && tone != EffectivenessTone.UNKNOWN) {
            shortEffectiveness(pres.effectiveness)
        } else {
            null
        }
        val ppSpoken = if (pres.currentPp != null && pres.maxPp != null) {
            "PP ${pres.currentPp} of ${pres.maxPp}"
        } else {
            "PP ${pres.ppDisplay}"
        }
        val description = listOfNotNull(
            pres.name,
            pres.typeName.takeIf { it.isNotBlank() },
            ppSpoken,
            effectiveness,
            damage?.replace("–", " to ")?.replace("%", " percent"),
            "no PP left".takeIf { outOfPp }
        ).joinToString(", ")
        return MoveTileModel(
            name = pres.name,
            typeName = pres.typeName,
            ppText = pres.ppDisplay,
            damageText = damage,
            effectivenessText = effectiveness,
            effectivenessTone = tone,
            outOfPp = outOfPp,
            selectable = selectionAllowed && !outOfPp,
            contentDescription = description
        )
    }

    /** One short line for the tile: a % range of the defender's max HP when it is known. */
    fun compactDamageText(pres: MovePresentation, defenderMaxHp: Int?): String {
        val estimate = pres.calculatorSupport == CalcSupport.ESTIMATED ||
            pres.damageConfidence == DamageConfidence.ESTIMATE
        val prefix = if (estimate) "~" else ""
        return when {
            pres.isStatMove -> "Status"
            pres.repeatedStrike != null -> "Multi-hit"
            pres.maxDamage > 0 && defenderMaxHp != null && defenderMaxHp > 0 ->
                "$prefix${pres.minDamage * 100 / defenderMaxHp}–${pres.maxDamage * 100 / defenderMaxHp}%"
            pres.maxDamage > 0 -> "$prefix${pres.minDamage}–${pres.maxDamage} HP"
            else -> "Damage n/a"
        }
    }

    /** The full damage line the stacked move cards used to show; kept for the details sheet. */
    fun fullDamageText(pres: MovePresentation, defenderMaxHp: Int?): String {
        val estimateSuffix = if (pres.calculatorSupport == CalcSupport.ESTIMATED) " · Estimate" else ""
        val ignoredSuffix = DamageBlockerPresentation.ignoredText(pres.damageIgnoredMechanics)
        val koSuffix = if (pres.koChanceText.isNotBlank()) " · ${pres.koChanceText}" else ""
        val scopeSuffix = pres.damageScope?.let { " · $it" } ?: ""
        return when {
            pres.isStatMove -> "Status move"
            pres.repeatedStrike != null -> pres.repeatedStrike.presentation + estimateSuffix
            pres.maxDamage > 0 && defenderMaxHp != null && defenderMaxHp > 0 -> {
                val minPct = (pres.minDamage * 100) / defenderMaxHp
                val maxPct = (pres.maxDamage * 100) / defenderMaxHp
                "${pres.minDamage}–${pres.maxDamage} HP · $minPct–$maxPct%" +
                    koSuffix + estimateSuffix + ignoredSuffix + scopeSuffix
            }
            pres.maxDamage > 0 ->
                "${pres.minDamage}–${pres.maxDamage} HP" + koSuffix + estimateSuffix + ignoredSuffix + scopeSuffix
            pres.damageConfidence == DamageConfidence.UNAVAILABLE -> pres.damageUnavailableText
            else -> pres.damageDisplayText
        }
    }

    fun toneOf(effectiveness: String): EffectivenessTone = when (effectiveness) {
        EffectivenessLabel.SUPER_EFFECTIVE.displayName,
        EffectivenessLabel.EXTREMELY_EFFECTIVE.displayName -> EffectivenessTone.STRONG
        EffectivenessLabel.NEUTRAL.displayName -> EffectivenessTone.NEUTRAL
        EffectivenessLabel.NOT_VERY_EFFECTIVE.displayName,
        EffectivenessLabel.EXTREMELY_NOT_EFFECTIVE.displayName -> EffectivenessTone.WEAK
        EffectivenessLabel.NO_EFFECT.displayName -> EffectivenessTone.NONE
        else -> EffectivenessTone.UNKNOWN
    }

    private fun shortEffectiveness(effectiveness: String): String = when (effectiveness) {
        EffectivenessLabel.EXTREMELY_EFFECTIVE.displayName -> "Super effective ×4"
        EffectivenessLabel.SUPER_EFFECTIVE.displayName -> "Super effective ×2"
        EffectivenessLabel.NEUTRAL.displayName -> "Neutral"
        EffectivenessLabel.NOT_VERY_EFFECTIVE.displayName -> "Not very ×0.5"
        EffectivenessLabel.EXTREMELY_NOT_EFFECTIVE.displayName -> "Not very ×0.25"
        EffectivenessLabel.NO_EFFECT.displayName -> "No effect"
        else -> effectiveness
    }
}
