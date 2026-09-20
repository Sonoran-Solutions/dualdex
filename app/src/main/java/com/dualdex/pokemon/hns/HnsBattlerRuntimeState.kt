package com.dualdex.pokemon.hns

import com.dualdex.pokemon.DeclaredAbility
import com.dualdex.pokemon.GameDataPack
import com.dualdex.pokemon.ProfileOverlayDataPack
import com.dualdex.pokemon.PokemonType

/**
 * Live combat state of one authoritative active H&S 2.0.5 battler (issue #9).
 *
 * This is MEMORY-READ / OBSERVABILITY state only: it reports what the running
 * battle engine currently holds in `gBattleMons[battler]`, and nothing else.
 *
 * The three ability claims are deliberately distinct and must never be collapsed:
 *   1. the party's stored `abilityNum` (slot/index information);
 *   2. the pinned build's DECLARED ability for a species/slot
 *      ([com.dualdex.pokemon.DeclaredAbility], static content);
 *   3. [HnsBattlerRuntimeState.abilityId] — the effective ability identity the
 *      battle engine is CURRENTLY using for the battler.
 * Only claim 3 is established here. A catalogue identity (claim 2) is attached
 * to an observation purely to NAME the observed ID; it is never the source of
 * the observation and never evidence that the damage calculator models the
 * ability.
 */
enum class HnsBattlerRuntimeStatus {
    /** No observation exists for this frame: no state is retained and no default is substituted. */
    UNAVAILABLE,
    /** The battle is active but more than one opponent battler is present; nothing is named. */
    AMBIGUOUS,
    /** Every field was decoded from live `gBattleMons` state and is inside the pinned domains. */
    OBSERVED,
    /** The bytes were read, but at least one value is outside the pinned source's domain. */
    OBSERVED_INVALID;

    val isObservation: Boolean
        get() = this == OBSERVED || this == OBSERVED_INVALID

    companion object {
        fun fromNativeCode(code: Int): HnsBattlerRuntimeStatus = when (code) {
            1 -> AMBIGUOUS
            2 -> OBSERVED
            3 -> OBSERVED_INVALID
            else -> UNAVAILABLE
        }
    }
}

/**
 * One observed current type slot of the active battler.
 *
 * The raw value is authoritative and verbatim: the empty-slot sentinel
 * (`TYPE_NONE` = 0) stays 0 — never "Normal" — and a value outside the pinned
 * `enum Type` domain is reported raw with [outOfDomain], never coerced.
 */
data class HnsBattlerTypeObservation(
    val observed: Boolean = false,
    val raw: Int = 0,
    val outOfDomain: Boolean = false
) {
    /**
     * The canonical H&S type name for the observed ID, or null when the slot is
     * the empty sentinel ([HnsBattlerRuntimeStateIds.TYPE_NONE]), the value is
     * out of domain, or the slot was not observed.
     */
    val name: String?
        get() = if (!observed || outOfDomain) null else hnsRuntimeTypeName(raw)

    /** True only for a legitimate observed empty-slot sentinel. */
    val isTypeNoneSentinel: Boolean
        get() = observed && !outOfDomain && raw == HnsBattlerRuntimeStateIds.TYPE_NONE
}

/** Which authoritative active battler a live observation is requested for (matches the native enum). */
object HnsBattlerRole {
    const val PLAYER = 0
    const val OPPONENT = 1
}

/** The pinned H&S 2.0.5 battle-struct type IDs (pinned enum `Type`, packed ABI). */
object HnsBattlerRuntimeStateIds {
    /** Empty-slot sentinel: a monotype's second/third slot. Never a real type. */
    const val TYPE_NONE = 0
    /** Battle-only "typeless" value (Roost removal et al.). Real but not a species typing. */
    const val TYPE_MYSTERY = 10
    /** Highest ID the pinned `enum Type` assigns (NUMBER_OF_MON_TYPES - 1). */
    const val TYPE_ID_MAX = 20
    /** Highest ID the pinned `enum Ability` assigns (ABILITIES_COUNT - 1). */
    const val ABILITY_ID_MAX = 310
}

/**
 * The canonical H&S type name for a pinned runtime type ID, or null for the
 * empty-slot sentinel.
 *
 * Names for IDs 1-9 and 11-19 reuse the repository's canonical [PokemonType]
 * display names; 10 is the pinned TYPE_MYSTERY (typeless) battle value and 20
 * is Stellar. This is NOT the vanilla Gen III GBA type-id ordering
 * ([com.dualdex.pokemon.PokemonType.fromGbaId], which starts NORMAL at 0x00)
 * and must never be applied to vanilla battle bytes.
 */
fun hnsRuntimeTypeName(id: Int): String? = when (id) {
    HnsBattlerRuntimeStateIds.TYPE_NONE -> null // sentinel: explicitly not "Normal"
    1 -> PokemonType.NORMAL.displayName
    2 -> PokemonType.FIGHTING.displayName
    3 -> PokemonType.FLYING.displayName
    4 -> PokemonType.POISON.displayName
    5 -> PokemonType.GROUND.displayName
    6 -> PokemonType.ROCK.displayName
    7 -> PokemonType.BUG.displayName
    8 -> PokemonType.GHOST.displayName
    9 -> PokemonType.STEEL.displayName
    HnsBattlerRuntimeStateIds.TYPE_MYSTERY -> "Mystery" // battle-only typeless value, named verbatim
    11 -> PokemonType.FIRE.displayName
    12 -> PokemonType.WATER.displayName
    13 -> PokemonType.GRASS.displayName
    14 -> PokemonType.ELECTRIC.displayName
    15 -> PokemonType.PSYCHIC.displayName
    16 -> PokemonType.ICE.displayName
    17 -> PokemonType.DRAGON.displayName
    18 -> PokemonType.DARK.displayName
    19 -> PokemonType.FAIRY.displayName
    20 -> PokemonType.STELLAR.displayName
    else -> null // outside the pinned domain: unknown, never mapped to a valid type
}

/**
 * The effective ability and current types of one authoritative active battler,
 * read directly from the running H&S 2.0.5 battle engine.
 *
 * Exact trust and the authoritative lifecycle/battler mapping are required
 * upstream of this model: the reader publishes only from an authoritatively
 * ACTIVE battle whose battler is resolved through the same machinery as the
 * HP/stat-stage surfaces. Every failure is [HnsBattlerRuntimeStatus.UNAVAILABLE]
 * or [HnsBattlerRuntimeStatus.AMBIGUOUS] with no field carrying a value: no
 * stale ability, no previous battler, no slot-0 default, no single-enemy guess
 * in doubles, and no state retained across battle teardown or a ROM/profile
 * switch.
 *
 * @param abilityId the engine's current effective ability identity for the
 *   battler (raw numeric runtime fact). NOT the party's `abilityNum`, NOT a
 *   declared species-slot ability, and NOT calculator capability.
 * @param abilityOutOfDomain true when the ID is outside the pinned
 *   `enum Ability` domain; the raw value is still preserved.
 */
data class HnsBattlerRuntimeState(
    val status: HnsBattlerRuntimeStatus = HnsBattlerRuntimeStatus.UNAVAILABLE,
    val battlerIndex: Int? = null,
    val partySlot: Int? = null,
    val abilityId: Int? = null,
    val abilityOutOfDomain: Boolean = false,
    val types: List<HnsBattlerTypeObservation> = emptyList()
) {
    /** True when at least one observed type ID is outside the pinned `enum Type` domain. */
    val typesOutOfDomain: Boolean get() = types.any { it.outOfDomain }

    /**
     * The canonical H&S ability identity for the observed ID, resolved through
     * the active data pack's pinned ability catalogue — used ONLY to name the
     * observed ID.
     *
     * Returns null when no observation exists, when the ID is out of domain,
     * or when the active pack is not the exact H&S 2.0.5 catalogue (a foreign
     * catalogue must never name this build's IDs). An ID the catalogue does not
     * contain resolves to [DeclaredAbility.Absent]: explicit absence, never a
     * substitute.
     */
    fun resolveAbilityIdentity(pack: GameDataPack?): DeclaredAbility? {
        if (!status.isObservation || abilityOutOfDomain) return null
        val id = abilityId ?: return null
        val hnsPack = if (pack != null) unwrapToHns(pack) else null
        return hnsPack?.getAbility(id)
    }

    companion object {
        /**
         * Decode the native tuple (all-zero/unavailable and malformed tuples
         * alike decode to UNAVAILABLE — never to a defaulted observation).
         *
         * Layout: [0] status, [1] battler index (-1 = none), [2] party slot
         * (-1 = unknown), [3] partySlotKnown, [4] abilityObserved,
         * [5] abilityInvalid, [6] ability id, [7] typesObserved,
         * [8] typesInvalid, [9] type count, [10..12] raw type values.
         */
        fun fromNativeArray(raw: IntArray?): HnsBattlerRuntimeState {
            if (raw == null || raw.size < 13) return HnsBattlerRuntimeState()
            val status = HnsBattlerRuntimeStatus.fromNativeCode(raw[0])
            if (!status.isObservation) return HnsBattlerRuntimeState(status = status)
            val typesObserved = raw[7] != 0
            val count = raw[9].coerceIn(0, 3)
            val types = (0 until count).map { slot ->
                HnsBattlerTypeObservation(
                    observed = typesObserved,
                    raw = raw[10 + slot],
                    outOfDomain = typesObserved && raw[8] != 0 &&
                        raw[10 + slot] > HnsBattlerRuntimeStateIds.TYPE_ID_MAX
                )
            }
            val abilityObserved = raw[4] != 0
            val abilityId = raw[6].takeIf { abilityObserved }
            val abilityOutOfDomain = raw[5] != 0 ||
                (abilityObserved && (raw[6] < 0 || raw[6] > HnsBattlerRuntimeStateIds.ABILITY_ID_MAX))
            val decoded = HnsBattlerRuntimeState(
                status = status,
                battlerIndex = raw[1].takeIf { it >= 0 },
                partySlot = raw[2].takeIf { raw[3] != 0 && it in 0..5 },
                abilityId = abilityId,
                abilityOutOfDomain = abilityOutOfDomain,
                types = types
            )
            // Defense in depth: the native reader already reports OBSERVED_INVALID for
            // out-of-domain observations, but a tuple whose flags claim an out-of-domain
            // value while the status claims clean must degrade honestly rather than pass.
            return if (decoded.status == HnsBattlerRuntimeStatus.OBSERVED &&
                (decoded.abilityOutOfDomain || decoded.typesOutOfDomain)
            ) {
                decoded.copy(status = HnsBattlerRuntimeStatus.OBSERVED_INVALID)
            } else {
                decoded
            }
        }

        /** Unwraps profile overlays to find the exact H&S 2.0.5 catalogue, if present. */
        private fun unwrapToHns(pack: GameDataPack): HeartAndSoul205DataPack? {
            var current: GameDataPack = pack
            repeat(4) {
                when (val p = current) {
                    is HeartAndSoul205DataPack -> return p
                    is ProfileOverlayDataPack -> current = p.basePack
                    else -> return null
                }
            }
            return null
        }
    }
}

/**
 * Live battler runtime state for an authoritative active battler (issue #9):
 * the engine's CURRENT effective ability and types, read from `gBattleMons`, never
 * reconstructed from declarations or settings. Published with the ability identity
 * resolved against the active data pack's pinned catalogue (naming only).
 */
data class BattlerRuntimeObservation(
    val state: HnsBattlerRuntimeState,
    /** Canonical H&S ability identity for the observed ID, when the catalogue knows it. */
    val abilityIdentity: DeclaredAbility? = null
)
