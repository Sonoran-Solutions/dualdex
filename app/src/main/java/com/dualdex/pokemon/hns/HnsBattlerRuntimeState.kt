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
 *
 * The held item is governed by the same rule: [HnsBattlerRuntimeState.itemId] is the
 * engine's CURRENT item word (`gBattleMons[battler].item`), which the engine rewrites
 * on consume/knock-off/swap/steal/fling. It is NOT the party structure's stored item,
 * and the generated item catalogue is used only to NAME the observed ID.
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
    /**
     * Highest ID the pinned `enum Item` assigns (ITEMS_COUNT - 1). Sourced from the
     * exact generated H&S item catalogue so the game-state domain and the item
     * catalogue can never drift apart silently.
     */
    val ITEM_ID_MAX: Int get() = Hns205ItemCatalogue.ITEM_ID_MAX

    /**
     * Pinned `gBattleWeather` flags (`enum BattleWeather` bits). The modelled ordinary-damage
     * subset covers clear weather and **ordinary** Rain / Sun only; the primal variants
     * (`Primordial Sea` / `Desolate Land`) are part of the aggregate `B_WEATHER_RAIN` /
     * `B_WEATHER_SUN` masks but must never be collapsed onto the ordinary names, and every
     * other bit (Sandstorm/Hail/Snow/Fog/Strong Winds) is observed but refused by the policy
     * rather than silently treated as clear.
     */
    const val B_WEATHER_RAIN_NORMAL = 1 shl 0 // B_WEATHER_RAIN_NORMAL
    const val B_WEATHER_SUN_NORMAL = 1 shl 3  // B_WEATHER_SUN_NORMAL
    /** Aggregate pinned mask: includes the primal and downpour rain bits (0x7). */
    const val B_WEATHER_RAIN = 0x7 // (1<<0)|(1<<1)|(1<<2)
    /** Aggregate pinned mask: includes the primal sun bit (0x18). */
    const val B_WEATHER_SUN = 0x18 // (1<<3)|(1<<4)
    /** Only ordinary Rain and ordinary Sun are modelled; the primal bits are refused. */
    const val B_WEATHER_MODELLED = B_WEATHER_RAIN_NORMAL or B_WEATHER_SUN_NORMAL

    /** Pinned `gSideStatuses` bits. Only Reflect and Light Screen are modelled for the subset. */
    const val SIDE_STATUS_REFLECT = 1 shl 0
    const val SIDE_STATUS_LIGHTSCREEN = 1 shl 1
    const val SIDE_STATUS_MODELLED = SIDE_STATUS_REFLECT or SIDE_STATUS_LIGHTSCREEN

    /**
     * Pinned `STATUS_FIELD_ION_DELUGE` bit (`include/constants/battle.h`). It forces a Normal
     * move to Electric. The explicit supported field-status mask is exactly this bit: every
     * other `gFieldStatuses` bit (Wonder Room, Gravity, the four terrains, Mud/Water Sport,
     * Trick/Magic Room, Fairy Lock) changes damage or the defensive stat and is not modelled,
     * so it must fail closed rather than clear the Ion Deluge check.
     */
    const val STATUS_FIELD_ION_DELUGE = 1 shl 10
    const val FIELD_STATUS_SUPPORTED_MASK = STATUS_FIELD_ION_DELUGE

    /**
     * Pinned `volatiles.chargeTimer` width is 2 bits, so its raw domain is `0..3`; any
     * non-zero value doubles an Electric move (`src/battle_util.c`).
     */
    const val VOLATILE_CHARGE_TIMER_MAX = 3
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
 * @param itemId the engine's CURRENT held-item identity for the battler
 *   (`gBattleMons[battler].item`). NOT the party structure's stored item:
 *   the battle engine rewrites this word when an item is consumed, knocked off,
 *   swapped, stolen or flung. `ITEM_NONE` (0) is an authoritative "no item".
 * @param itemOutOfDomain true when the ID is outside the pinned `enum Item`
 *   domain; the raw value is still preserved.
 */
data class HnsBattlerRuntimeState(
    val status: HnsBattlerRuntimeStatus = HnsBattlerRuntimeStatus.UNAVAILABLE,
    val battlerIndex: Int? = null,
    val partySlot: Int? = null,
    val abilityId: Int? = null,
    val abilityOutOfDomain: Boolean = false,
    val types: List<HnsBattlerTypeObservation> = emptyList(),
    val itemId: Int? = null,
    val itemOutOfDomain: Boolean = false,
    val statsObserved: Boolean = false,
    val rawAttack: Int? = null,
    val rawDefense: Int? = null,
    val rawSpeed: Int? = null,
    val rawSpAttack: Int? = null,
    val rawSpDefense: Int? = null,
    val stagesObserved: Boolean = false,
    val statStages: List<Int> = emptyList(),
    val badgesObserved: Boolean = false,
    val badgeBoostAtk: Boolean = false,
    val badgeBoostDef: Boolean = false,
    val badgeBoostSpe: Boolean = false,
    val badgeBoostSpa: Boolean = false,
    val badgeBoostSpd: Boolean = false,
    val rawBadgesByte: Int? = null,
    /** `gAbsentBattlerFlags`: which battlers are currently absent (fainted/forced-out). 0 when not readable. */
    val absentBattlerFlags: Int = 0,
    /** true when absentBattlerFlags was actually read from live memory;
     *  distinguish 'the read produced 0' from 'the read never happened' or 'unreadable'. */
    val absentFlagsReadable: Boolean = false,
    /** `gBattlersCount`: battle-level topological count (2 for Singles, 4 for Doubles).
     *  0 when not readable. Battle-level state: both the player-side and enemy-side
     *  observations of the same active battle must carry the same value. */
    val battlersCount: Int = 0,
    /** true when battlersCount was actually read from live memory; distinguish
     *  'the read produced 0' from 'the read never happened' or 'unreadable'. */
    val battlersCountReadable: Boolean = false,
    // --- Gap C4e live damage operands -------------------------------------------------------
    /** true when the current HP / max HP pair was decoded from live `gBattleMons`. */
    val hpObserved: Boolean = false,
    /** Engine's current HP (`gBattleMons[battler].hp`). Only meaningful when [hpObserved]. */
    val hp: Int = 0,
    /** Engine's current max HP (`gBattleMons[battler].maxHP`). Only meaningful when [hpObserved]. */
    val maxHp: Int = 0,
    /** true when the current status word was decoded from live `gBattleMons`. */
    val statusObserved: Boolean = false,
    /** Engine's current status word; 0 is an observed 'no status'. */
    val status1: Int = 0,
    /** true when the damage-relevant volatile bits were decoded from live memory. */
    val volatilesObserved: Boolean = false,
    /** VOLATILE_ELECTRIFIED: Electrify forces the current move to Electric (any type). */
    val volatileElectrified: Boolean = false,
    /** VOLATILE_GLAIVE_RUSH: the defender takes double damage from any incoming move. */
    val volatileGlaiveRush: Boolean = false,
    /** VOLATILE_MINIMIZE (recorded; only relevant to move-flagged effects, excluded by the allow-list). */
    val volatileMinimize: Boolean = false,
    /** `enum SemiInvulnerableState` (recorded; only relevant to move-flagged effects). */
    val volatileSemiInvulnerable: Int = 0,
    /**
     * true when the extended volatile window (`chargeTimer`, `tarShot`) was decoded. Only a
     * tuple at least [C4E_TRANSIENT_TUPLE_LEN] ints long can carry it; a shorter tuple leaves
     * both values unobserved rather than defaulting them to neutral. Always false unless
     * [volatilesObserved] is also true (they are decoded from the same read window).
     */
    val transientVolatilesObserved: Boolean = false,
    /**
     * `volatiles.chargeTimer` raw value (pinned width 2, so `0..3`). Charge doubles an
     * Electric move while it is non-zero. Only meaningful when [transientVolatilesObserved].
     */
    val volatileChargeTimer: Int = 0,
    /**
     * `volatiles.tarShot`: the defender takes double damage from Fire moves. Only meaningful
     * when [transientVolatilesObserved].
     */
    val volatileTarShot: Boolean = false,
    /** true when `gBattleStruct->gimmick.activeGimmick[side][slot]` was decoded. */
    val gimmickObserved: Boolean = false,
    /** `enum Gimmick` active for this battler's party slot; 0 = GIMMICK_NONE. */
    val activeGimmick: Int = 0,
    /** true when the battle-global `gFieldStatuses` word was actually read. */
    val fieldStatusesReadable: Boolean = false,
    /** Battle-global status word (Ion Deluge is one bit). Only meaningful when readable. */
    val fieldStatuses: Int = 0,
    // --- Gap C4e correction: live field conditions -----------------------------------------
    /**
     * true when the battle-global `gBattleWeather` flags word was actually read. A readable word
     * of 0 is an observed clear weather, distinct from "never read".
     */
    val weatherReadable: Boolean = false,
    /** Engine's current weather flags word. Only meaningful when [weatherReadable]. */
    val battleWeather: Int = 0,
    /**
     * true when this battler's own `gSideStatuses[side]` word was actually read. The boundary
     * uses the defender observation's word for Reflect / Light Screen.
     */
    val sideStatusesReadable: Boolean = false,
    /** Engine's current status word for the observed battler's side. Only meaningful when readable. */
    val sideStatuses: Int = 0
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

    /**
     * The exact H&S item identity for the observed current item ID, resolved through
     * the generated pinned catalogue — used ONLY to NAME the observed ID, never to
     * produce it or to authorize capability.
     *
     * Returns null when no observation exists, when the ID is out of domain, or when
     * the ID has no catalogue entry. `ITEM_NONE` (0) resolves to its catalogue
     * identity, which is the authoritative "no item" observation.
     */
    fun resolveItemIdentity(): HnsItemData? {
        if (!status.isObservation || itemOutOfDomain) return null
        val id = itemId ?: return null
        return Hns205ItemCatalogue.get(id)
    }

    companion object {
        /**
         * Decode the native tuple (all-zero/unavailable and malformed tuples
         * alike decode to UNAVAILABLE — never to a defaulted observation).
         *
         * Layout (must match BATTLER_RUNTIME_STATE_TUPLE_LEN in dualdex_jni.c):
         * [0] status, [1] battler index (-1 = none), [2] party slot
         * (-1 = unknown), [3] partySlotKnown, [4] abilityObserved,
         * [5] abilityInvalid, [6] ability id, [7] typesObserved,
         * [8] typesInvalid, [9] type count, [10..12] raw type values,
         * [13] itemObserved, [14] itemInvalid, [15] item id,
         * [16] statsObserved, [17..21] raw atk/def/spe/spa/spd,
         * [22] stagesObserved, [23..30] stat stages (hp..eva),
         * [31] badgesObserved, [32..36] badge boost atk/def/spe/spa/spd,
         * [37] raw badges byte, [38] absentBattlerFlags (possibly unreliable),
         * [39] absentFlagsReadable (1 = flags was actually read, 0 = unreadable),
         * [40] battlersCount (gBattlersCount; 0 = unreadable),
         * [41] battlersCountReadable (1 = count was actually read, 0 = unreadable),
         * [42] hpObserved, [43] hp, [44] maxHp, [45] statusObserved, [46] status1,
         * [47] volatilesObserved, [48] volatileElectrified, [49] volatileGlaiveRush,
         * [50] volatileMinimize, [51] volatileSemiInvulnerable,
         * [52] gimmickObserved, [53] activeGimmick,
         * [54] fieldStatusesReadable, [55] fieldStatuses,
         * [56] weatherReadable, [57] battleWeather,
         * [58] sideStatusesReadable, [59] sideStatuses,
         * [60] volatileChargeTimer, [61] volatileTarShot.
         *
         * Centralizes the minimum array size with BATTLER_RUNTIME_STATE_TUPLE_LEN so
         * the JNI, native reader, and this decoder can never drift. [TUPLE_LEN] is
         * the pre-C4e contract (still honored for existing tests); [C4E_TUPLE_LEN]
         * additionally carries the Gap C4e live operands, [C4E_FIELD_TUPLE_LEN]
         * the live weather / defender-side status operands, and
         * [C4E_TRANSIENT_TUPLE_LEN] the Charge / Tar Shot volatile operands.
         */
        private const val TUPLE_LEN = 42
        private const val C4E_TUPLE_LEN = 56
        private const val C4E_FIELD_TUPLE_LEN = 60
        private const val C4E_TRANSIENT_TUPLE_LEN = 62

        fun fromNativeArray(raw: IntArray?): HnsBattlerRuntimeState {
            if (raw == null || raw.size < 16) return HnsBattlerRuntimeState()
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
            val itemObserved = raw[13] != 0
            val itemId = raw[15].takeIf { itemObserved }
            val itemOutOfDomain = raw[14] != 0 ||
                (itemObserved && (raw[15] < 0 || raw[15] > HnsBattlerRuntimeStateIds.ITEM_ID_MAX))
            val statsObserved = raw.size >= TUPLE_LEN && raw[16] != 0
            val rawAttack = if (statsObserved) raw[17] else null
            val rawDefense = if (statsObserved) raw[18] else null
            val rawSpeed = if (statsObserved) raw[19] else null
            val rawSpAttack = if (statsObserved) raw[20] else null
            val rawSpDefense = if (statsObserved) raw[21] else null
            val stagesObserved = raw.size >= TUPLE_LEN && raw[22] != 0
            val statStages = if (stagesObserved && raw.size >= 31) (23..30).map { raw[it] } else emptyList()
            val badgesObserved = raw.size >= TUPLE_LEN && raw[31] != 0
            val badgeBoostAtk = raw.size >= TUPLE_LEN && raw[32] != 0
            val badgeBoostDef = raw.size >= TUPLE_LEN && raw[33] != 0
            val badgeBoostSpe = raw.size >= TUPLE_LEN && raw[34] != 0
            val badgeBoostSpa = raw.size >= TUPLE_LEN && raw[35] != 0
            val badgeBoostSpd = raw.size >= TUPLE_LEN && raw[36] != 0
            val rawBadgesByte = if (badgesObserved && raw.size >= TUPLE_LEN) raw[37] else null
            /* absentBattlerFlags is only authoritative when the native reader
             * actually read the field; absentFlagsReadable [39] distinguishes
             * "the read produced 0" from "the read never happened". */
            val absentFlagsReadable = raw.size >= TUPLE_LEN && raw[39] != 0
            val absentBattlerFlags = if (absentFlagsReadable && raw.size >= TUPLE_LEN) raw[38] else 0
            /* battlersCount is battle-level: only authoritative when the native reader
             * actually read gBattlersCount; the readability bit distinguishes "the read
             * produced 0" from "the read never happened" (0 must never mean 'zero
             * battlers'). */
            val battlersCountReadable = raw.size >= TUPLE_LEN && raw[41] != 0
            val battlersCount = if (battlersCountReadable && raw.size >= TUPLE_LEN) raw[40] else 0
            // Gap C4e live operands. A field is only decoded when the tuple is long enough;
            // a short (pre-C4e) tuple leaves every new field unobserved rather than defaulting.
            val c4e = raw.size >= C4E_TUPLE_LEN
            val hpObserved = c4e && raw[42] != 0
            val statusObserved = c4e && raw[45] != 0
            val volatilesObserved = c4e && raw[47] != 0
            val gimmickObserved = c4e && raw[52] != 0
            val fieldStatusesReadable = c4e && raw[54] != 0
            // Gap C4e correction: weather / defender-side status operands. Decoded only from the
            // longer tuple; a 56-element tuple leaves them unobserved rather than defaulted.
            val c4eField = raw.size >= C4E_FIELD_TUPLE_LEN
            val weatherReadable = c4eField && raw[56] != 0
            val sideStatusesReadable = c4eField && raw[58] != 0
            // Gap C4e correction: chargeTimer / tarShot are decoded from the same volatile
            // window as electrified / glaiveRush, so they are only authoritative when the
            // window was read AND the tuple is long enough to carry them.
            val c4eTransient = raw.size >= C4E_TRANSIENT_TUPLE_LEN
            val transientVolatilesObserved = volatilesObserved && c4eTransient
            val decoded = HnsBattlerRuntimeState(
                status = status,
                battlerIndex = raw[1].takeIf { it >= 0 },
                partySlot = raw[2].takeIf { raw[3] != 0 && it in 0..5 },
                abilityId = abilityId,
                abilityOutOfDomain = abilityOutOfDomain,
                types = types,
                itemId = itemId,
                itemOutOfDomain = itemOutOfDomain,
                statsObserved = statsObserved,
                rawAttack = rawAttack,
                rawDefense = rawDefense,
                rawSpeed = rawSpeed,
                rawSpAttack = rawSpAttack,
                rawSpDefense = rawSpDefense,
                stagesObserved = stagesObserved,
                statStages = statStages,
                badgesObserved = badgesObserved,
                badgeBoostAtk = badgeBoostAtk,
                badgeBoostDef = badgeBoostDef,
                badgeBoostSpe = badgeBoostSpe,
                badgeBoostSpa = badgeBoostSpa,
                badgeBoostSpd = badgeBoostSpd,
                rawBadgesByte = rawBadgesByte,
                absentBattlerFlags = absentBattlerFlags,
                absentFlagsReadable = absentFlagsReadable,
                battlersCount = battlersCount,
                battlersCountReadable = battlersCountReadable,
                hpObserved = hpObserved,
                hp = if (hpObserved) raw[43].coerceAtLeast(0) else 0,
                maxHp = if (hpObserved) raw[44].coerceAtLeast(0) else 0,
                statusObserved = statusObserved,
                status1 = if (statusObserved) raw[46] else 0,
                volatilesObserved = volatilesObserved,
                volatileElectrified = volatilesObserved && raw[48] != 0,
                volatileGlaiveRush = volatilesObserved && raw[49] != 0,
                volatileMinimize = volatilesObserved && raw[50] != 0,
                volatileSemiInvulnerable = if (volatilesObserved) raw[51].coerceIn(0, 6) else 0,
                transientVolatilesObserved = transientVolatilesObserved,
                volatileChargeTimer = if (transientVolatilesObserved) {
                    raw[60].coerceIn(0, HnsBattlerRuntimeStateIds.VOLATILE_CHARGE_TIMER_MAX)
                } else {
                    0
                },
                volatileTarShot = transientVolatilesObserved && raw[61] != 0,
                gimmickObserved = gimmickObserved,
                activeGimmick = if (gimmickObserved) raw[53].coerceIn(0, 5) else 0,
                fieldStatusesReadable = fieldStatusesReadable,
                fieldStatuses = if (fieldStatusesReadable) raw[55] else 0,
                weatherReadable = weatherReadable,
                battleWeather = if (weatherReadable) raw[57] else 0,
                sideStatusesReadable = sideStatusesReadable,
                sideStatuses = if (sideStatusesReadable) raw[59] else 0
            )
            // Defense in depth: the native reader already reports OBSERVED_INVALID for
            // out-of-domain observations, but a tuple whose flags claim an out-of-domain
            // value while the status claims clean must degrade honestly rather than pass.
            return if (decoded.status == HnsBattlerRuntimeStatus.OBSERVED &&
                (decoded.abilityOutOfDomain || decoded.typesOutOfDomain || decoded.itemOutOfDomain)
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
 * the engine's CURRENT effective ability, types and held item, read from `gBattleMons`,
 * never reconstructed from declarations or settings. Published with the ability and item
 * identities resolved against the pinned catalogues (naming only).
 */
data class BattlerRuntimeObservation(
    val state: HnsBattlerRuntimeState,
    /** Canonical H&S ability identity for the observed ID, when the catalogue knows it. */
    val abilityIdentity: DeclaredAbility? = null,
    /** Exact H&S item identity for the observed current item ID, when the catalogue knows it. */
    val itemIdentity: HnsItemData? = null
)
