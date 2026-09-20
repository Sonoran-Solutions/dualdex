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
    val rationale: String
)

/**
 * Authoritative registry of investigated held items for Heart & Soul 2.0.5
 * (issue #9, Gap C3).
 *
 * Source: `pokehns-expansion` commit `1f42b74dff0e9fe942419845d040663dd829a973`.
 *
 * Capability is decided by the NUMERIC item ID, never by a display name: the
 * registry resolves its own entries to IDs through the exact generated
 * [Hns205ItemCatalogue] at construction, and [classify] looks up that ID. A
 * participant whose runtime ID is the item but whose display name says something
 * else is still classified by the runtime ID.
 */
object HnsItemRegistry {

    private val entriesById = HashMap<Int, HnsItemEntry>()
    private val entriesByName = HashMap<String, HnsItemEntry>()

    private fun normalizeKey(raw: String): String =
        Hns205ItemCatalogue.normalize(raw)

    /**
     * Registers one audited item by its exact source symbol.
     *
     * The symbol must exist in the exact generated catalogue; a stale registry
     * entry is a construction-time failure rather than a silently-unclassified
     * item, so drift cannot turn an unsupported item into an accepted one.
     */
    private fun register(
        symbol: String,
        category: HnsItemCategory,
        rationale: String
    ) {
        val data = Hns205ItemCatalogue.getBySymbol(symbol)
            ?: error("H&S item registry references unknown source symbol $symbol; regenerate the catalogue")
        val entry = HnsItemEntry(data.itemId, data, category, rationale)
        entriesById[data.itemId] = entry
        data.sourceName?.let { entriesByName[normalizeKey(it)] = entry }
    }

    init {
        // 0: ITEM_NONE — authoritative "no held item".
        register(
            symbol = "ITEM_NONE",
            category = HnsItemCategory.PROVEN_NO_ORDINARY_DAMAGE_EFFECT,
            rationale = "Authoritatively no held item; zero damage effect."
        )

        // Utility items whose entire effect is out of battle or on EXP/money/
        // friendship: no crit, power, type, stat, survival/KO, between-turn HP,
        // status or speed interaction anywhere in the pinned source.
        register(
            symbol = "ITEM_AMULET_COIN",
            category = HnsItemCategory.PROVEN_NO_ORDINARY_DAMAGE_EFFECT,
            rationale = "HOLD_EFFECT_DOUBLE_PRIZE applies only to prize money " +
                "(src/battle_main.c:3189, src/battle_hold_effects.c:48,1055). No damage interaction."
        )
        register(
            symbol = "ITEM_SOOTHE_BELL",
            category = HnsItemCategory.PROVEN_NO_ORDINARY_DAMAGE_EFFECT,
            rationale = "HOLD_EFFECT_FRIENDSHIP_UP applies only to friendship (src/pokemon.c:7777). No damage interaction."
        )
        register(
            symbol = "ITEM_CLEANSE_TAG",
            category = HnsItemCategory.PROVEN_NO_ORDINARY_DAMAGE_EFFECT,
            rationale = "HOLD_EFFECT_REPEL applies only to wild encounters (src/wild_encounter.c:1334). No damage interaction."
        )
        register(
            symbol = "ITEM_LUCKY_EGG",
            category = HnsItemCategory.PROVEN_NO_ORDINARY_DAMAGE_EFFECT,
            rationale = "HOLD_EFFECT_LUCKY_EGG multiplies earned EXP only (src/battle_script_commands.c:11982). No damage interaction."
        )
        register(
            symbol = "ITEM_EXP_SHARE",
            category = HnsItemCategory.PROVEN_NO_ORDINARY_DAMAGE_EFFECT,
            rationale = "HOLD_EFFECT_EXP_SHARE splits earned EXP only (src/battle_script_commands.c:11969-11987). " +
                "No crit/power/type/stat/survival/HP/status/speed interaction."
        )

        // Traditional type boosters: H&S ×1.2 to base power, ADV ×1.1 applied at a
        // different stage. Do not mark equivalent.
        registerTypeBooster("ITEM_CHARCOAL", "Fire")
        registerTypeBooster("ITEM_MYSTIC_WATER", "Water")
        registerTypeBooster("ITEM_MIRACLE_SEED", "Grass")
        registerTypeBooster("ITEM_MAGNET", "Electric")
        registerTypeBooster("ITEM_BLACK_GLASSES", "Dark")
        registerTypeBooster("ITEM_BLACK_BELT", "Fighting")
        registerTypeBooster("ITEM_SHARP_BEAK", "Flying")
        registerTypeBooster("ITEM_POISON_BARB", "Poison")
        registerTypeBooster("ITEM_SOFT_SAND", "Ground")
        registerTypeBooster("ITEM_HARD_STONE", "Rock")
        registerTypeBooster("ITEM_SILVER_POWDER", "Bug")
        registerTypeBooster("ITEM_SPELL_TAG", "Ghost")
        registerTypeBooster("ITEM_TWISTED_SPOON", "Psychic")
        registerTypeBooster("ITEM_NEVER_MELT_ICE", "Ice")
        registerTypeBooster("ITEM_DRAGON_FANG", "Dragon")
        registerTypeBooster("ITEM_SILK_SCARF", "Normal")

        // Choice items: H&S composes the item modifier in fixed point and applies
        // it once after stat stages and abilities; ADV applies item transforms with
        // intermediate integer floors, so the composition diverges off neutral.
        register(
            symbol = "ITEM_CHOICE_BAND",
            category = HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "×1.5 physical Attack, but modifier ordering/composition diverges " +
                "(src/battle_util.c:7177-7180,7191 vs ADV sequential integer floors)."
        )
        register(
            symbol = "ITEM_CHOICE_SPECS",
            category = HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "×1.5 special Attack with the same H&S fixed-point ordering divergence as Choice Band."
        )
        register(
            symbol = "ITEM_CHOICE_SCARF",
            category = HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "Speed only, but speed-dependent move power and move order are not modelled in this request shape."
        )

        // Species-specific stat items: H&S multipliers and conditions differ from ADV.
        register(
            symbol = "ITEM_LIGHT_BALL",
            category = HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "×2 Attack and Sp. Atk for Pikachu under B_LIGHT_BALL_ATTACK_BOOST (src/battle_util.c:7173-7176); not ADV-equivalent."
        )
        register(
            symbol = "ITEM_THICK_CLUB",
            category = HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "×2 physical Attack for Cubone/Marowak (src/battle_util.c:7165-7168); fixed-point composition not proven against ADV."
        )
        register(
            symbol = "ITEM_DEEP_SEA_TOOTH",
            category = HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "×2 Sp. Atk for Clamperl (src/battle_util.c:7169-7172); not proven ADV-equivalent."
        )
        register(
            symbol = "ITEM_DEEP_SEA_SCALE",
            category = HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "×2 Sp. Def for Clamperl on special defense (src/battle_util.c:7353-7356); not proven ADV-equivalent."
        )
        register(
            symbol = "ITEM_SOUL_DEW",
            category = HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "H&S ×1.2 Psychic/Dragon base power for Latias/Latios (B_SOUL_DEW_BOOST >= GEN_7, src/battle_util.c:6833-6838); ADV semantics differ."
        )

        // Modern direct-damage / stat items.
        register(
            symbol = "ITEM_LIFE_ORB",
            category = HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "×1.3 after the damage roll plus 1/10 max-HP recoil (src/battle_util.c:7673-7674); not modelled."
        )
        register(
            symbol = "ITEM_EXPERT_BELT",
            category = HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "×1.2 on super-effective hits (src/battle_util.c:7669-7671); not modelled."
        )
        register(
            symbol = "ITEM_MUSCLE_BAND",
            category = HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "≈×1.1 physical base power (src/battle_util.c:6813-6815); post-Gen-III item not modelled."
        )
        register(
            symbol = "ITEM_WISE_GLASSES",
            category = HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "≈×1.1 special base power (src/battle_util.c:6817-6819); post-Gen-III item not modelled."
        )
        register(
            symbol = "ITEM_EVIOLITE",
            category = HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "×1.5 Defense for a species that can still evolve (src/battle_util.c:7361-7369); evolution state is not part of the request."
        )
        register(
            symbol = "ITEM_ASSAULT_VEST",
            category = HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "×1.5 Sp. Def on special defense (src/battle_util.c:7370-7373); post-Gen-III item not modelled."
        )

        // Gems: ×1.3 base power and consumed.
        register(
            symbol = "ITEM_NORMAL_GEM",
            category = HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "×1.3 matching-type base power and consumed (GEM_BOOST_PARAM = 30, src/battle_util.c:6633-6634; src/item.c consume path); not modelled."
        )
        register(
            symbol = "ITEM_FIRE_GEM",
            category = HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "×1.3 matching-type base power and consumed; gem consumption state is not modelled."
        )

        // Resist berries: ×0.5 and consumed.
        register(
            symbol = "ITEM_OCCA_BERRY",
            category = HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "×0.5 super-effective Fire damage and consumed (src/battle_util.c:7686-7696; removal in src/battle_script_commands.c:1484-1501); consumption state is not modelled."
        )
        register(
            symbol = "ITEM_SITRUS_BERRY",
            category = HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "Between-turn HP restore affects KO/HP presentation (HOLD_EFFECT_RESTORE_PCT_HP); not modelled."
        )
        register(
            symbol = "ITEM_ORAN_BERRY",
            category = HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "Between-turn HP restore affects KO/HP presentation (HOLD_EFFECT_RESTORE_HP); not modelled."
        )

        // KO-relevant items: raw damage may be unchanged but the calculator's KO
        // output would be misleading, so they are refused rather than called harmless.
        register(
            symbol = "ITEM_FOCUS_SASH",
            category = HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "Guarantees survival at 1 HP from full HP and is consumed (src/battle_util.c:8200-8206); KO presentation would be wrong."
        )
        register(
            symbol = "ITEM_FOCUS_BAND",
            category = HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "10% survival at 1 HP (src/battle_util.c:8193-8199); KO presentation would be wrong."
        )
        register(
            symbol = "ITEM_LEFTOVERS",
            category = HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "Between-turn 1/16 max-HP heal (src/battle_hold_effects.c:642-656) affects KO presentation; not modelled."
        )
        register(
            symbol = "ITEM_SHELL_BELL",
            category = HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "Heals 1/8 of damage dealt (src/battle_hold_effects.c:536-555) affects multi-hit/HP presentation; not modelled."
        )
        register(
            symbol = "ITEM_ROCKY_HELMET",
            category = HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "Damages the contact attacker for 1/6 max HP (src/battle_hold_effects.c:245-262); not modelled."
        )
    }

    private fun registerTypeBooster(symbol: String, typeName: String) {
        register(
            symbol = symbol,
            category = HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "H&S ×1.2 (holdEffectParam 20) to $typeName base power " +
                "(src/battle_util.c:6808,6839-6843,6871); ADV applies ×1.1 at a different stage, so not equivalent."
        )
    }

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
        return entriesById[id] ?: HnsItemEntry(
            itemId = id,
            data = data,
            category = HnsItemCategory.UNCLASSIFIED,
            rationale = "Item ID $id (${data.canonicalSymbol}) is not audited in the H&S registry."
        )
    }

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
     * The engine-adapter spelling for a modelled item, or null when the item has no
     * modelled damage effect (so the engine must receive no item at all).
     *
     * No H&S damage item is modelled today, so this is always null; it exists so a
     * future modelled item cannot be forwarded by its raw H&S source name.
     *
     * This stripping is correct only for moves that do not read item state. A move
     * whose semantics DO read item identity/presence/absence must never be allowed to
     * reach this point: [HnsMoveItemInteractionRegistry] exists so the policy can
     * refuse it first, because forwarding no item would otherwise destroy exactly the
     * input the move needs and let the engine compute a plausible wrong number.
     */
    @Suppress("UNUSED_PARAMETER")
    fun engineItemName(id: Int?): String? {
        // Deliberately numeric-ID driven: a future MODELLED_* item returns its adapter spelling
        // here, and every other category returns null (the engine receives no item).
        when (classify(id).category) {
            HnsItemCategory.MODELLED_EQUIVALENT,
            HnsItemCategory.MODELLED_HNS_SPECIFIC -> return null
            else -> return null
        }
    }
}
