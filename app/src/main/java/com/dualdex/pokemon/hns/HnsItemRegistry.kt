package com.dualdex.pokemon.hns

/**
 * Exact identity and static hold-effect data for one Heart & Soul 2.0.5 item.
 *
 * Produced by the generated [Hns205ItemCatalogue]; the numeric [itemId] is the
 * build's own `enum Item` value and is the only capability authority for live
 * H&S participants. [sourceName] is the exact name in the build's `gItemsInfo`
 * table (verbatim, e.g. "CHARCOAL"); it is identity, never a capability key.
 */
data class HnsItemData(
    val itemId: Int,
    val canonicalSymbol: String,
    val sourceName: String?,
    val holdEffect: String,
    val holdEffectParam: Int
)

/**
 * Capability classification of a held item under Heart & Soul 2.0.5 damage
 * calculation (issue #9, Gap C3).
 *
 * Each classification is grounded in an audit of the pinned H&S source
 * (`pokehns-expansion` commit `1f42b74dff0e9fe942419845d040663dd829a973`) versus
 * the embedded calculator's Gen 3 (ADV) engine.
 */
enum class HnsItemCategory {
    /**
     * Proven from pinned H&S source that the item's OWN hold effect has no effect on
     * the ORDINARY damage path: no crit, move-power, type, stat, survival/KO,
     * between-turn HP, status or speed-dependent-power interaction.
     *
     * This is deliberately NOT a claim that the item identity can never affect damage.
     * A move whose semantics read item identity/presence/absence (Fling, Natural Gift,
     * Acrobatics, Knock Off, Poltergeist, Judgment, Techno Blast, Multi-Attack) is a
     * separate, contextual interaction audited by
     * [HnsMoveItemInteractionRegistry]. Final capability is the combination of this
     * static audit with that interaction audit; see
     * `CalcCapabilityPolicy.HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED`.
     */
    PROVEN_NO_ORDINARY_DAMAGE_EFFECT,

    /**
     * Proven from pinned H&S source to have identical damage arithmetic, trigger
     * conditions, modifier placement and integer/fixed-point rounding to the ADV
     * engine.
     */
    MODELLED_EQUIVALENT,

    /**
     * Pinned H&S implements an item-specific modifier that differs from ADV and
     * is modelled specifically for H&S.
     */
    MODELLED_HNS_SPECIFIC,

    /**
     * Known damage-relevant item in H&S whose mechanics are not faithfully
     * modelled by the current calculation engine.
     */
    UNSUPPORTED_DAMAGE_RELEVANT,

    /** Any item not explicitly classified and audited. Fails closed. */
    UNCLASSIFIED;

    val isSupportedForDamage: Boolean
        get() = this == PROVEN_NO_ORDINARY_DAMAGE_EFFECT ||
            this == MODELLED_EQUIVALENT ||
            this == MODELLED_HNS_SPECIFIC
}

/** Registration entry for an audited H&S held item. */
data class HnsItemEntry(
    val itemId: Int?,
    val data: HnsItemData?,
    val category: HnsItemCategory,
    val rationale: String,
    /**
     * The reviewed family group that decided [category] (`tools/hns-items/decisions.json`), or
     * `identity_exception`; null when the ID is outside the exact domain.
     */
    val familyGroup: String? = null,
    /** Compiled gHoldEffectsInfo.onSwitchIn for the item's pinned hold-effect family. */
    val onSwitchInActivation: Boolean = false,
    /** Compiled gHoldEffectsInfo.onHpThreshold for the item's pinned hold-effect family. */
    val onHpThresholdActivation: Boolean = false
)

/**
 * One reviewed global decision: a compiled hold-effect family, or an identity exception that
 * overrides its family. Generated into [HnsItemAuditData] from `tools/hns-items/decisions.json`.
 */
data class HnsItemFamilyDecision(
    val key: String,
    val group: String,
    val category: HnsItemCategory,
    val rationale: String,
    val onSwitchInActivation: Boolean = false,
    val onHpThresholdActivation: Boolean = false
)

/**
 * Authoritative global capability of every Heart & Soul 2.0.5 held item (issue #9, Gap C3).
 *
 * Source: `pokehns-expansion` commit `1f42b74dff0e9fe942419845d040663dd829a973`.
 *
 * Capability is decided by the NUMERIC item ID, never by a display name. Every ID in the exact
 * [Hns205ItemCatalogue] domain is classified by the reviewed decision for its compiled
 * `holdEffect` family, unless an identity exception overrides it (for example the e-Reader Enigma
 * Berry, whose battle hold effect is runtime data). The decisions are generated into
 * [HnsItemAuditData] by `tools/hns-items/generate_hns_item_audit.py`, whose `--check` mode (run by
 * `./ci.sh source-check`) proves every family's pinned references were reviewed.
 *
 * This is the GLOBAL answer to "what can this item's own hold effect change?". Whether a globally
 * unsupported item can change one particular request is answered separately by
 * `com.dualdex.calculator.HnsItemContextPolicy`, and whether the selected move reads item state is
 * answered by [HnsMoveItemInteractionRegistry].
 */
object HnsItemRegistry {

    /**
     * Engine-adapter spellings for MODELLED_* items, keyed by numeric ID. A MODELLED_* item without
     * an explicit adapter here is NOT supported: stripping it would silently drop a modelled effect,
     * and forwarding its raw H&S name could match an unrelated ADV item.
     */
    private val modelledEngineAdapters: Map<Int, String> = mapOf(
        476 to "Wise Glasses",
        484 to "Iron Ball",
        497 to "Air Balloon",
        499 to "Ring Target"
    )

    /**
     * H&S-only items implemented from the generated live hold-effect descriptor in the bundled
     * QuickJS pipeline. They must never be forwarded as names: several share Gen III names whose
     * built-in ADV effects would be applied a second time.
     */
    private val descriptorBackedModelledEffects = setOf(
        "HOLD_EFFECT_TYPE_POWER", "HOLD_EFFECT_PLATE", "HOLD_EFFECT_GEMS",
        "HOLD_EFFECT_MUSCLE_BAND", "HOLD_EFFECT_PUNCHING_GLOVE", "HOLD_EFFECT_SOUL_DEW",
        "HOLD_EFFECT_LUSTROUS_ORB", "HOLD_EFFECT_ADAMANT_ORB", "HOLD_EFFECT_GRISEOUS_ORB",
        "HOLD_EFFECT_OGERPON_MASK", "HOLD_EFFECT_CHOICE_BAND", "HOLD_EFFECT_CHOICE_SPECS",
        "HOLD_EFFECT_THICK_CLUB", "HOLD_EFFECT_LIGHT_BALL", "HOLD_EFFECT_DEEP_SEA_TOOTH",
        "HOLD_EFFECT_DEEP_SEA_SCALE", "HOLD_EFFECT_METAL_POWDER", "HOLD_EFFECT_EVIOLITE",
        "HOLD_EFFECT_ASSAULT_VEST", "HOLD_EFFECT_LIFE_ORB", "HOLD_EFFECT_EXPERT_BELT",
        "HOLD_EFFECT_METRONOME", "HOLD_EFFECT_RESIST_BERRY", "HOLD_EFFECT_UTILITY_UMBRELLA",
        "HOLD_EFFECT_BOOSTER_ENERGY", "HOLD_EFFECT_PRIMAL_ORB"
    )

    /**
     * Classifies a held item by its exact numeric H&S item ID.
     *
     * Null, negative and out-of-domain IDs are [HnsItemCategory.UNCLASSIFIED]; an
     * in-domain ID with no audited registration is [HnsItemCategory.UNCLASSIFIED]
     * too. Both fail closed.
     */
    fun classify(id: Int?): HnsItemEntry {
        if (id == null) {
            return HnsItemEntry(
                itemId = null,
                data = null,
                category = HnsItemCategory.UNCLASSIFIED,
                rationale = "Item ID is null."
            )
        }
        val data = Hns205ItemCatalogue.get(id)
        if (id < 0 || id > Hns205ItemCatalogue.ITEM_ID_MAX || data == null) {
            return HnsItemEntry(
                itemId = id,
                data = data,
                category = HnsItemCategory.UNCLASSIFIED,
                rationale = "Item ID $id is outside the exact H&S item domain or has no catalogue entry."
            )
        }
        val decision = HnsItemAuditData.identityExceptions[id]
            ?: HnsItemAuditData.families[data.holdEffect]
            ?: return HnsItemEntry(
                itemId = id,
                data = data,
                category = HnsItemCategory.UNCLASSIFIED,
                rationale = "Item ID $id (${data.canonicalSymbol}) has no reviewed hold-effect decision."
            )
        val activation = HnsItemAuditData.families[data.holdEffect]
        return HnsItemEntry(
            id, data, decision.category, decision.rationale, decision.group,
            activation?.onSwitchInActivation ?: false,
            activation?.onHpThresholdActivation ?: false
        )
    }

    /**
     * True only when [id]'s own hold effect is supported for ordinary damage: proven to have no
     * ordinary damage effect, or MODELLED_* with either a direct engine adapter or an explicit
     * source-generated hold-effect descriptor branch.
     */
    fun isSupportedForDamage(id: Int?): Boolean {
        val category = classify(id).category
        return when (category) {
            HnsItemCategory.PROVEN_NO_ORDINARY_DAMAGE_EFFECT -> true
            HnsItemCategory.MODELLED_EQUIVALENT,
            HnsItemCategory.MODELLED_HNS_SPECIFIC -> id != null && (
                modelledEngineAdapters.containsKey(id) ||
                    Hns205ItemCatalogue.get(id)?.holdEffect in descriptorBackedModelledEffects
                )
            else -> false
        }
    }

    /**
     * The pinned display name of [id] (title-cased `gItemsInfo` name, "None" for ITEM_NONE), or
     * null outside the exact domain. Never derived from a generic item table.
     */
    fun displayName(id: Int?): String? {
        if (id == null) return null
        val data = Hns205ItemCatalogue.get(id) ?: return null
        val raw = data.sourceName ?: return "None"
        val keepUpper = setOf("HP", "PP", "TM", "HM", "EXP.", "X", "S.S.", "RM.")
        return raw.split(" ").joinToString(" ") { word ->
            if (word.uppercase() in keepUpper) word.uppercase()
            else word.take(1).uppercase() + word.drop(1).lowercase()
        }
    }

    /**
     * The pinned type operand of a type-matched item (Type booster/Plate/Gem `secondaryId`, or a
     * resist berry's `holdEffectParam`) as a type name such as "FIRE", or null.
     */
    fun itemTypeName(id: Int?): String? = id?.let { HnsItemAuditData.itemTypes[it] }

    /** Classifies a held item by its source/display name, resolving through the exact catalogue. */
    fun classifyByName(name: String?): HnsItemEntry {
        if (name.isNullOrBlank()) {
            return HnsItemEntry(
                itemId = null,
                data = null,
                category = HnsItemCategory.UNCLASSIFIED,
                rationale = "Item name is null or blank."
            )
        }
        val data = Hns205ItemCatalogue.getByName(name)
            ?: return HnsItemEntry(
                itemId = null,
                data = null,
                category = HnsItemCategory.UNCLASSIFIED,
                rationale = "Item name '$name' is not present in the exact H&S item catalogue."
            )
        return classify(data.itemId)
    }

    /** The exact numeric item ID for a source/display name, or null when it does not resolve. */
    fun resolveIdByName(name: String?): Int? = Hns205ItemCatalogue.getByName(name)?.itemId

    /** True only when [id] is inside the exact generated H&S item domain. */
    fun isInDomain(id: Int): Boolean = id in 0..Hns205ItemCatalogue.ITEM_ID_MAX

    /**
     * The direct engine-adapter spelling for a modelled item, or null when its arithmetic is
     * carried by the H&S-specific hold-effect descriptor instead.
     *
     * Directly adapted items (such as Wise Glasses) return their explicit engine spelling. Items
     * modeled by H&S descriptor branches return null here and are described by the separate
     * `hnsEffectiveHoldEffect` request fields; this prevents accidentally applying a Gen III item
     * with the same display name. Every other item that reaches the engine is either globally
     * proven neutral or request-locally proven irrelevant and is sent without an item.
     *
     * This stripping is correct only for moves that do not read item state. A move whose semantics
     * DO read item identity/presence/absence must never be allowed to reach this point:
     * [HnsMoveItemInteractionRegistry] exists so the policy can refuse it first, because forwarding
     * no item would otherwise destroy exactly the input the move needs.
     */
    fun engineItemName(id: Int?): String? = when (classify(id).category) {
        HnsItemCategory.MODELLED_EQUIVALENT,
        HnsItemCategory.MODELLED_HNS_SPECIFIC -> id?.let { modelledEngineAdapters[it] }
        else -> null
    }
}
