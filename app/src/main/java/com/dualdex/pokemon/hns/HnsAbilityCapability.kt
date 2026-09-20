package com.dualdex.pokemon.hns

/**
 * Capability classification of an ability under Heart & Soul 2.0.5 damage calculation (Gap C2).
 *
 * Each classification is grounded in an audit of the pinned H&S source
 * (`pokehns-expansion` commit `1f42b74dff0e9fe942419845d040663dd829a973`) versus
 * the embedded calculator's Gen 3 (ADV) engine.
 */
enum class HnsAbilityCategory {
    /**
     * Proven from pinned H&S source to have no effect on damage calculation
     * (e.g. accuracy-only, status-prevention, or out-of-battle logic).
     */
    PROVEN_NO_DAMAGE_EFFECT,

    /**
     * Proven from pinned H&S source to have identical damage arithmetic, trigger conditions,
     * modifier placement, and integer/fixed-point rounding to the ADV engine.
     */
    MODELLED_EQUIVALENT,

    /**
     * Pinned H&S implements an ability-specific modifier that differs from ADV or requires
     * custom handling, successfully modelled specifically for H&S.
     */
    MODELLED_HNS_SPECIFIC,

    /**
     * Known damage-relevant ability in H&S whose mechanics are not faithfully modelled by
     * the current calculation engine (e.g. modern generation arithmetic divergence, unmodelled
     * type alteration, stat modification, etc.).
     */
    UNSUPPORTED_DAMAGE_RELEVANT,

    /**
     * Any ability not explicitly classified and audited in the registry. Fails closed.
     */
    UNCLASSIFIED;

    val isSupportedForDamage: Boolean
        get() = this == PROVEN_NO_DAMAGE_EFFECT || this == MODELLED_EQUIVALENT || this == MODELLED_HNS_SPECIFIC
}

/**
 * Registration entry for an audited H&S ability.
 */
data class HnsAbilityEntry(
    val abilityId: Int?,
    val canonicalName: String,
    val titleCaseName: String,
    val category: HnsAbilityCategory,
    val rationale: String
)

/**
 * Authoritative registry of investigated abilities for Heart & Soul 2.0.5 (Issue #9, Gap C2).
 *
 * Source: `pokehns-expansion` commit `1f42b74dff0e9fe942419845d040663dd829a973`.
 */
object HnsAbilityRegistry {

    private val entriesById = HashMap<Int, HnsAbilityEntry>()
    private val entriesByName = HashMap<String, HnsAbilityEntry>()

    private fun register(
        id: Int?,
        canonicalName: String,
        titleCaseName: String,
        category: HnsAbilityCategory,
        rationale: String
    ) {
        val entry = HnsAbilityEntry(id, canonicalName, titleCaseName, category, rationale)
        if (id != null) {
            entriesById[id] = entry
        }
        val key = normalizeKey(canonicalName)
        entriesByName[key] = entry
        val titleKey = normalizeKey(titleCaseName)
        if (titleKey != key) {
            entriesByName[titleKey] = entry
        }
    }

    private fun normalizeKey(raw: String): String =
        raw.trim().lowercase().replace("_", " ").replace("-", " ")

    init {
        // 0: ABILITY_NONE
        register(
            id = 0,
            canonicalName = "NONE",
            titleCaseName = "None",
            category = HnsAbilityCategory.PROVEN_NO_DAMAGE_EFFECT,
            rationale = "Observed to have no ability; zero damage effect."
        )

        // 15: INSOMNIA
        register(
            id = 15,
            canonicalName = "INSOMNIA",
            titleCaseName = "Insomnia",
            category = HnsAbilityCategory.PROVEN_NO_DAMAGE_EFFECT,
            rationale = "battle_move_resolution.c:1202 (blocks Rest), battle_util.c:5482, 9068 (sleep immunity). Zero damage modifier."
        )

        // 37: HUGE POWER
        register(
            id = 37,
            canonicalName = "HUGE POWER",
            titleCaseName = "Huge Power",
            category = HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "Modifier ordering/composition divergence: H&S composes ability multipliers together and applies stat stages before abilities, whereas ADV applies ability modifiers sequentially before stat stages."
        )

        // 47: THICK FAT
        register(
            id = 47,
            canonicalName = "THICK FAT",
            titleCaseName = "Thick Fat",
            category = HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "Modifier ordering/composition divergence: when interacting with other ability modifiers (e.g. Guts) or non-neutral stat stages, compound fixed-point rounding differs from ADV sequential flooring."
        )

        // 51: KEEN EYE
        register(
            id = 51,
            canonicalName = "KEEN EYE",
            titleCaseName = "Keen Eye",
            category = HnsAbilityCategory.PROVEN_NO_DAMAGE_EFFECT,
            rationale = "battle_script_commands.c:7711 (prevents acc drop), battle_util.c:10451 (ignores evasion). Zero damage modifier."
        )

        // 62: GUTS
        register(
            id = 62,
            canonicalName = "GUTS",
            titleCaseName = "Guts",
            category = HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "Modifier ordering/composition divergence: when interacting with other ability modifiers (e.g. Thick Fat) or non-neutral stat stages, compound fixed-point rounding differs from ADV sequential flooring."
        )

        // 65: OVERGROW
        register(
            id = 65,
            canonicalName = "OVERGROW",
            titleCaseName = "Overgrow",
            category = HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "battle_util.c:7023 modifies Attack Stat in CalcAttackStat, whereas ADV gen3.js:232 modifies Base Power. Two integer divisions cause arithmetic divergence in 17,750 spreads."
        )

        // 66: BLAZE
        register(
            id = 66,
            canonicalName = "BLAZE",
            titleCaseName = "Blaze",
            category = HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "Same Attack Stat vs Base Power divergence as Overgrow."
        )

        // 67: TORRENT
        register(
            id = 67,
            canonicalName = "TORRENT",
            titleCaseName = "Torrent",
            category = HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "Same Attack Stat vs Base Power divergence as Overgrow."
        )

        // 68: SWARM
        register(
            id = 68,
            canonicalName = "SWARM",
            titleCaseName = "Swarm",
            category = HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "Same Attack Stat vs Base Power divergence as Overgrow."
        )

        // 74: PURE POWER
        register(
            id = 74,
            canonicalName = "PURE POWER",
            titleCaseName = "Pure Power",
            category = HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "Modifier ordering/composition divergence: stat stages and compound ability interactions differ from ADV sequential pipeline."
        )

        // Modern damage abilities explicitly audited and marked unsupported
        register(
            id = 91,
            canonicalName = "ADAPTABILITY",
            titleCaseName = "Adaptability",
            category = HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "Post-Gen-III STAB increase unmodelled by ADV."
        )
        register(
            id = 137,
            canonicalName = "TOXIC BOOST",
            titleCaseName = "Toxic Boost",
            category = HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "Post-Gen-III ability unmodelled by ADV."
        )
        register(
            id = 168,
            canonicalName = "PROTEAN",
            titleCaseName = "Protean",
            category = HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "Dynamic move-type changing ability unmodelled by ADV."
        )
        register(
            id = 255,
            canonicalName = "GORILLA TACTICS",
            titleCaseName = "Gorilla Tactics",
            category = HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "Post-Gen-III ability unmodelled by ADV."
        )
        register(
            id = 262,
            canonicalName = "TRANSISTOR",
            titleCaseName = "Transistor",
            category = HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "Post-Gen-III electric boost ability unmodelled by ADV."
        )
        register(
            id = 282,
            canonicalName = "QUARK DRIVE",
            titleCaseName = "Quark Drive",
            category = HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            rationale = "Paradox terrain/stat boost ability unmodelled by ADV."
        )
    }

    /**
     * Classifies an ability by its display name (case-insensitive).
     * Returns an unclassified entry if not found.
     */
    fun classify(name: String?): HnsAbilityEntry {
        if (name == null || name.isBlank()) {
            return HnsAbilityEntry(
                abilityId = null,
                canonicalName = "",
                titleCaseName = "",
                category = HnsAbilityCategory.UNCLASSIFIED,
                rationale = "Ability name is null or blank."
            )
        }
        val key = normalizeKey(name)
        return entriesByName[key] ?: HnsAbilityEntry(
            abilityId = null,
            canonicalName = name.trim().uppercase(),
            titleCaseName = name.trim(),
            category = HnsAbilityCategory.UNCLASSIFIED,
            rationale = "Ability $name is not classified in H&S registry."
        )
    }

    /**
     * Classifies an ability by its numeric H&S ability ID.
     */
    fun classify(id: Int?): HnsAbilityEntry {
        if (id == null) {
            return HnsAbilityEntry(
                abilityId = null,
                canonicalName = "",
                titleCaseName = "",
                category = HnsAbilityCategory.UNCLASSIFIED,
                rationale = "Ability ID is null."
            )
        }
        return entriesById[id] ?: HnsAbilityEntry(
            abilityId = id,
            canonicalName = "ABILITY_$id",
            titleCaseName = "Ability $id",
            category = HnsAbilityCategory.UNCLASSIFIED,
            rationale = "Ability ID $id is not classified in H&S registry."
        )
    }

    /**
     * Returns the canonical TitleCase name if the ability is supported or recognized.
     */
    fun canonicalTitleCaseName(name: String): String? {
        val key = normalizeKey(name)
        return entriesByName[key]?.titleCaseName
    }

    /**
     * Returns the canonical TitleCase name by numeric ability ID.
     */
    fun canonicalTitleCaseName(id: Int?): String? =
        id?.let { entriesById[it]?.titleCaseName }
}
