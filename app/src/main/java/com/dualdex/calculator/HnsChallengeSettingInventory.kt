package com.dualdex.calculator

import com.dualdex.pokemon.hns.HnsChallengeField
import com.dualdex.pokemon.hns.HnsChallengeSettingsSnapshot

/**
 * How one Heart & Soul 2.0.5 challenge-settings field relates to the damage calculator
 * (issue #9, Gap C4a).
 */
enum class HnsChallengeSettingDisposition {
    /** Read and consumed by [CalcRequestBoundary.resolveHnsRuntimeRules] as a damage rule. */
    CONSUMED_RULE,

    /**
     * The setting only changes values at acquisition time, and the calculator consumes the final
     * observed value (ability, item, IVs, EVs, level, party), so no independent calculator rule is
     * required and no blocker is added.
     */
    CAPTURED_DOWNSTREAM,

    /**
     * The setting cannot change the damage of a request inside the currently supported ability /
     * item / move subset, so it is not an active limitation.
     */
    IRRELEVANT_TO_CURRENT_DAMAGE,

    /**
     * The setting changes a value the request does not capture, or removes move authority, so it
     * adds [blocker] when active and fails closed when unreadable.
     */
    CONDITIONAL_BLOCKER
}

/** Stable identity for each of the 17 `challengeSettings` fields DualDex reads. */
enum class HnsChallengeSettingId(val wireName: String) {
    OPTION_STYLE("optionStyle"),
    TX_MODE_FAIRY_TYPES("tx_Mode_Fairy_Types"),
    TX_RANDOM_TYPE("tx_Random_Type"),
    TX_RANDOM_TYPE_EFFECTIVENESS("tx_Random_TypeEffectiveness"),
    TX_RANDOM_ABILITIES("tx_Random_Abilities"),
    TX_RANDOM_MOVES("tx_Random_Moves"),
    TX_CHALLENGES_NO_EVS("tx_Challenges_NoEVs"),
    TX_CHALLENGES_BASE_STAT_EQUALIZER("tx_Challenges_BaseStatEqualizer"),
    TX_CHALLENGES_MIRROR("tx_Challenges_Mirror"),
    TX_CHALLENGES_MIRROR_THIEF("tx_Challenges_Mirror_Thief"),
    TX_CHALLENGES_TRAINER_SCALING_IVS("tx_Challenges_TrainerScalingIVs"),
    TX_CHALLENGES_TRAINER_SCALING_EVS("tx_Challenges_TrainerScalingEVs"),
    TX_CHALLENGES_MAX_PARTY_IVS("tx_Challenges_MaxPartyIVs"),
    TX_MODE_STURDY("tx_Mode_Sturdy"),
    TX_CHALLENGES_LEVEL_CAP("tx_Challenges_LevelCap"),
    TX_CHALLENGES_EXP_MULTIPLIER("tx_Challenges_ExpMultiplier"),
    TX_MODE_LEGENDARY_ABILITIES("tx_Mode_Legendary_Abilities")
}

/**
 * One audited row of the challenge-settings matrix.
 *
 * [blocker] is the exact limitation this setting adds when its effect is active (or, for the
 * conditional blockers, when it is unreadable and the policy relies on knowing it).
 */
data class HnsChallengeSettingAudit(
    val field: HnsChallengeSettingId,
    val sourceSemantics: String,
    val damageRelevance: String,
    val capturedDownstream: Boolean,
    val disposition: HnsChallengeSettingDisposition,
    val blocker: CalcLimitation?,
    val reason: String
)

/**
 * Complete, source-backed disposition of every `SaveBlock3.challengeSettings` field the runtime
 * reader exposes (issue #9, Gap C4a).
 *
 * [entries] has exactly one row per [HnsChallengeSettingId]; a field may not be omitted. The
 * policy ([CalcCapabilityPolicy]) implements the two conditional blockers; the remaining fields
 * are either consumed rules (Gap A), captured downstream by the authority the calculator already
 * consumes, or irrelevant to the supported damage subset.
 *
 * Source: `pokehns-expansion` commit `1f42b74dff0e9fe942419845d040663dd829a973`.
 */
object HnsChallengeSettingInventory {

    val entries: List<HnsChallengeSettingAudit> = listOf(
        HnsChallengeSettingAudit(
            field = HnsChallengeSettingId.OPTION_STYLE,
            sourceSemantics = "PHYS/SP SPLIT: 0 = per-move category, 1 = move type decides " +
                "(`GetBattleMoveCategory`, [src/battle_util.c:9183]).",
            damageRelevance = "Selects physical/special category, hence which attack/defence stats apply.",
            capturedDownstream = false,
            disposition = HnsChallengeSettingDisposition.CONSUMED_RULE,
            blocker = null,
            reason = "Consumed by CalcRequestBoundary as CalcHnsRuntimeRules.optionStyle (Gap A)."
        ),
        HnsChallengeSettingAudit(
            field = HnsChallengeSettingId.TX_MODE_FAIRY_TYPES,
            sourceSemantics = "ADD FAIRY TYPE: on keeps Fairy; off retypes species via sPreFairyTypes " +
                "and Fairy moves via sFairyMoveAltTypes ([src/pokemon.c:5734], :5788).",
            damageRelevance = "Changes attacker/defender types and type effectiveness.",
            capturedDownstream = false,
            disposition = HnsChallengeSettingDisposition.CONSUMED_RULE,
            blocker = null,
            reason = "Consumed by CalcRequestBoundary as CalcHnsRuntimeRules.fairyTypesEnabled (Gap C1)."
        ),
        HnsChallengeSettingAudit(
            field = HnsChallengeSettingId.TX_RANDOM_TYPE,
            sourceSemantics = "RANDOM TYPES: GetSpeciesType returns a randomized type " +
                "([src/pokemon.c:5731]).",
            damageRelevance = "Changes defender typing and type effectiveness.",
            capturedDownstream = false,
            disposition = HnsChallengeSettingDisposition.CONSUMED_RULE,
            blocker = CalcLimitation.RANDOM_TYPES_ACTIVE_NOT_MODELLED,
            reason = "Observed OFF clears; observed ON blocks with RANDOM_TYPES_ACTIVE_NOT_MODELLED."
        ),
        HnsChallengeSettingAudit(
            field = HnsChallengeSettingId.TX_RANDOM_TYPE_EFFECTIVENESS,
            sourceSemantics = "RANDOM TYPE EFFECTIVENESS: GetTypeModifier remaps the attacking type " +
                "at damage time ([src/battle_util.c:8533]).",
            damageRelevance = "Changes the type-effectiveness multiplier.",
            capturedDownstream = false,
            disposition = HnsChallengeSettingDisposition.CONSUMED_RULE,
            blocker = CalcLimitation.RANDOM_TYPE_EFFECTIVENESS_ACTIVE_NOT_MODELLED,
            reason = "Observed OFF clears; observed ON blocks with " +
                "RANDOM_TYPE_EFFECTIVENESS_ACTIVE_NOT_MODELLED."
        ),
        HnsChallengeSettingAudit(
            field = HnsChallengeSettingId.TX_RANDOM_ABILITIES,
            sourceSemantics = "RANDOM ABILITIES: rerolls the ability at acquisition " +
                "([src/pokemon.c:5585]).",
            damageRelevance = "Changes the effective ability, which may be damage-relevant.",
            capturedDownstream = true,
            disposition = HnsChallengeSettingDisposition.CAPTURED_DOWNSTREAM,
            blocker = null,
            reason = "The calculator consumes the effective ability from gBattleMons (Gap C2), so the " +
                "reroll result is the observed value. An unreadable ability already fails closed with " +
                "HNS_EFFECTIVE_ABILITY_UNREADABLE; an unmodelled one with HNS_ABILITY_EFFECT_NOT_MODELLED."
        ),
        HnsChallengeSettingAudit(
            field = HnsChallengeSettingId.TX_RANDOM_MOVES,
            sourceSemantics = "RANDOM MOVES: RANDOMIZE_LEARNSET rerolls the learned moveset at " +
                "acquisition ([src/pokemon.c:3954]).",
            damageRelevance = "Does not change a move's damage, but removes authority over which move " +
                "the active Pokemon currently knows.",
            capturedDownstream = false,
            disposition = HnsChallengeSettingDisposition.CONDITIONAL_BLOCKER,
            blocker = CalcLimitation.HNS_RANDOM_MOVES_ACTIVE_NOT_MODELLED,
            reason = "The calculator never consults the observed party moveset, so an active reroll " +
                "means the selected move is not proven to be the current learned move. Active blocks; " +
                "unreadable fails closed via CHALLENGE_SETTINGS_UNREADABLE."
        ),
        HnsChallengeSettingAudit(
            field = HnsChallengeSettingId.TX_CHALLENGES_NO_EVS,
            sourceSemantics = "NO EVs: blocks EV gain ([src/pokemon.c:7808]) and EV items " +
                "([src/party_menu.c:4941]).",
            damageRelevance = "Determines the EVs a Pokemon can have, hence its battle stats.",
            capturedDownstream = true,
            disposition = HnsChallengeSettingDisposition.CAPTURED_DOWNSTREAM,
            blocker = null,
            reason = "The calculator consumes the observed/asserted EV values directly, so the setting " +
                "changes which values exist, not how a given value is used."
        ),
        HnsChallengeSettingAudit(
            field = HnsChallengeSettingId.TX_CHALLENGES_BASE_STAT_EQUALIZER,
            sourceSemantics = "BASE STAT EQUALIZER: replaces every non-HP base stat with " +
                "0/100/255/500 by index ([src/challenge_menu.c:2359], [src/pokemon.c:3750]).",
            damageRelevance = "Changes every damage-relevant battle stat.",
            capturedDownstream = false,
            disposition = HnsChallengeSettingDisposition.CONDITIONAL_BLOCKER,
            blocker = CalcLimitation.HNS_BASE_STAT_EQUALIZER_NOT_MODELLED,
            reason = "The request carries the pinned species' ordinary base stats, so the equalizer " +
                "changes a value the request does not capture. Nonzero blocks; unreadable fails closed " +
                "via CHALLENGE_SETTINGS_UNREADABLE."
        ),
        HnsChallengeSettingAudit(
            field = HnsChallengeSettingId.TX_CHALLENGES_MIRROR,
            sourceSemantics = "MIRROR: copies the enemy party over the player's " +
                "([src/battle_main.c:667]).",
            damageRelevance = "Changes the player party's species/stats/moves.",
            capturedDownstream = true,
            disposition = HnsChallengeSettingDisposition.CAPTURED_DOWNSTREAM,
            blocker = null,
            reason = "Once applied, the copied party is the observed party the calculator reads."
        ),
        HnsChallengeSettingAudit(
            field = HnsChallengeSettingId.TX_CHALLENGES_MIRROR_THIEF,
            sourceSemantics = "MIRROR THIEF: variant of Mirror copying the enemy party " +
                "([src/battle_main.c:5823]).",
            damageRelevance = "Changes the player party's species/stats/moves.",
            capturedDownstream = true,
            disposition = HnsChallengeSettingDisposition.CAPTURED_DOWNSTREAM,
            blocker = null,
            reason = "Same as Mirror: the copied party is the observed party."
        ),
        HnsChallengeSettingAudit(
            field = HnsChallengeSettingId.TX_CHALLENGES_TRAINER_SCALING_IVS,
            sourceSemantics = "TRAINER SCALING IVs: rewrites opponent IVs " +
                "([src/battle_main.c:2145]).",
            damageRelevance = "Changes opponent battle stats.",
            capturedDownstream = true,
            disposition = HnsChallengeSettingDisposition.CAPTURED_DOWNSTREAM,
            blocker = null,
            reason = "The rewritten IVs are stored on the opponent party and are the IV values the " +
                "calculator consumes."
        ),
        HnsChallengeSettingAudit(
            field = HnsChallengeSettingId.TX_CHALLENGES_TRAINER_SCALING_EVS,
            sourceSemantics = "TRAINER SCALING EVs: rewrites opponent EVs " +
                "([src/battle_main.c:2156]).",
            damageRelevance = "Changes opponent battle stats.",
            capturedDownstream = true,
            disposition = HnsChallengeSettingDisposition.CAPTURED_DOWNSTREAM,
            blocker = null,
            reason = "The rewritten EVs are stored on the opponent party and are the EV values the " +
                "calculator consumes."
        ),
        HnsChallengeSettingAudit(
            field = HnsChallengeSettingId.TX_CHALLENGES_MAX_PARTY_IVS,
            sourceSemantics = "MAX PARTY IVs: forces player IVs to 31 (or 30/31) " +
                "([src/pokemon.c:3232]).",
            damageRelevance = "Changes player battle stats.",
            capturedDownstream = true,
            disposition = HnsChallengeSettingDisposition.CAPTURED_DOWNSTREAM,
            blocker = null,
            reason = "The forced IVs are stored on the player party and are the IV values the " +
                "calculator consumes."
        ),
        HnsChallengeSettingAudit(
            field = HnsChallengeSettingId.TX_MODE_STURDY,
            sourceSemantics = "STURDY MODE: gates Gen V+ Sturdy endure-at-1-HP " +
                "([src/battle_util.c:8186]).",
            damageRelevance = "Alters KO behaviour, not the damage number, and only through the " +
                "Sturdy ability.",
            capturedDownstream = false,
            disposition = HnsChallengeSettingDisposition.IRRELEVANT_TO_CURRENT_DAMAGE,
            blocker = null,
            reason = "Sturdy is not in the supported ability subset (ABILITY_NONE / Keen Eye / " +
                "Insomnia), so an unclassified/unsupported ability already fails closed with " +
                "HNS_ABILITY_EFFECT_NOT_MODELLED. No redundant blocker is added."
        ),
        HnsChallengeSettingAudit(
            field = HnsChallengeSettingId.TX_CHALLENGES_LEVEL_CAP,
            sourceSemantics = "LEVEL CAP: caps the reachable level ([src/caps.c:64]).",
            damageRelevance = "Determines which levels exist, hence battle stats.",
            capturedDownstream = true,
            disposition = HnsChallengeSettingDisposition.CAPTURED_DOWNSTREAM,
            blocker = null,
            reason = "The calculator consumes the observed/asserted level directly; the cap changes " +
                "which levels are reachable, not how a level is used."
        ),
        HnsChallengeSettingAudit(
            field = HnsChallengeSettingId.TX_CHALLENGES_EXP_MULTIPLIER,
            sourceSemantics = "EXP MULTIPLIER: scales earned experience ([src/caps.c:64]).",
            damageRelevance = "Determines which levels are reachable, hence battle stats.",
            capturedDownstream = true,
            disposition = HnsChallengeSettingDisposition.CAPTURED_DOWNSTREAM,
            blocker = null,
            reason = "The calculator consumes the observed/asserted level directly."
        ),
        HnsChallengeSettingAudit(
            field = HnsChallengeSettingId.TX_MODE_LEGENDARY_ABILITIES,
            sourceSemantics = "LEGENDARY ABILITIES: substitutes abilities for slot 0 " +
                "([src/pokemon.c:5551]) when ON (default ON).",
            damageRelevance = "Changes the effective ability, which may be damage-relevant.",
            capturedDownstream = true,
            disposition = HnsChallengeSettingDisposition.CAPTURED_DOWNSTREAM,
            blocker = null,
            reason = "The calculator consumes the effective ability from gBattleMons (Gap C2), so the " +
                "substituted ability is the observed value. Unreadable/unmodelled abilities fail closed."
        )
    )

    /** The audit row for [id], guaranteed present because [entries] covers every enum member. */
    fun entry(id: HnsChallengeSettingId): HnsChallengeSettingAudit =
        entries.first { it.field == id }

    /** The raw snapshot field this audit row describes. */
    fun fieldOf(id: HnsChallengeSettingId, snapshot: HnsChallengeSettingsSnapshot): HnsChallengeField =
        when (id) {
            HnsChallengeSettingId.OPTION_STYLE -> snapshot.optionStyle
            HnsChallengeSettingId.TX_MODE_FAIRY_TYPES -> snapshot.txModeFairyTypes
            HnsChallengeSettingId.TX_RANDOM_TYPE -> snapshot.txRandomType
            HnsChallengeSettingId.TX_RANDOM_TYPE_EFFECTIVENESS -> snapshot.txRandomTypeEffectiveness
            HnsChallengeSettingId.TX_RANDOM_ABILITIES -> snapshot.txRandomAbilities
            HnsChallengeSettingId.TX_RANDOM_MOVES -> snapshot.txRandomMoves
            HnsChallengeSettingId.TX_CHALLENGES_NO_EVS -> snapshot.txChallengesNoEvs
            HnsChallengeSettingId.TX_CHALLENGES_BASE_STAT_EQUALIZER -> snapshot.txChallengesBaseStatEqualizer
            HnsChallengeSettingId.TX_CHALLENGES_MIRROR -> snapshot.txChallengesMirror
            HnsChallengeSettingId.TX_CHALLENGES_MIRROR_THIEF -> snapshot.txChallengesMirrorThief
            HnsChallengeSettingId.TX_CHALLENGES_TRAINER_SCALING_IVS -> snapshot.txChallengesTrainerScalingIvs
            HnsChallengeSettingId.TX_CHALLENGES_TRAINER_SCALING_EVS -> snapshot.txChallengesTrainerScalingEvs
            HnsChallengeSettingId.TX_CHALLENGES_MAX_PARTY_IVS -> snapshot.txChallengesMaxPartyIvs
            HnsChallengeSettingId.TX_MODE_STURDY -> snapshot.txModeSturdy
            HnsChallengeSettingId.TX_CHALLENGES_LEVEL_CAP -> snapshot.txChallengesLevelCap
            HnsChallengeSettingId.TX_CHALLENGES_EXP_MULTIPLIER -> snapshot.txChallengesExpMultiplier
            HnsChallengeSettingId.TX_MODE_LEGENDARY_ABILITIES -> snapshot.txModeLegendaryAbilities
        }
}
