package com.dualdex.pokemon.hns

/**
 * Typed runtime snapshot of the pinned H&S 2.0.5 `SaveBlock3.challengeSettings`
 * (issue #9). This is MEMORY-READ / OBSERVABILITY state only: it carries what was
 * observed in the running game's memory and nothing else.
 *
 * Source defaults (`new_game.c` / the challenge menu's choices) are NEVER used as
 * runtime observations: a field is either [Field.observed] from live memory or it
 * is unavailable. A source field being 0 is a legitimate observed value and is
 * never conflated with "unknown".
 */
enum class HnsChallengeSettingsStatus {
    /** The read never happened or failed closed (wrong profile, unreadable pointer, truncated window). */
    UNAVAILABLE,
    /** Every field was decoded from live memory and is inside the pinned source's value domain. */
    OBSERVED,
    /** The bytes were read, but at least one multi-bit field holds a value the pinned source never assigns. */
    OBSERVED_INVALID
}

/** The source meaning of `optionStyle` (challenge_menu.c "PHYS/SP SPLIT"). */
enum class HnsOptionStyle {
    /** 0 — `GetBattleMoveCategory` uses the move's own physical/special category. */
    PER_MOVE_SPLIT,
    /** 1 — the move's type decides the category, as in Generation III. */
    TYPE_BASED,
    UNAVAILABLE
}

/**
 * One decoded challenge field.
 *
 * @param observed true when the value was decoded from live memory. `raw == 0`
 *   with `observed == true` means "observed off", not "unknown".
 * @param raw the decoded bits from memory (meaningless when [observed] is false).
 * @param outOfDomain true when the pinned source never assigns [raw] (multi-bit
 *   fields only; 1-bit fields are always in domain).
 */
data class HnsChallengeField(
    val observed: Boolean = false,
    val raw: Int = 0,
    val outOfDomain: Boolean = false
) {
    /** The observed boolean meaning of a 1-bit field, or null when unavailable. */
    val observedFlag: Boolean?
        get() = if (observed && !outOfDomain) raw != 0 else null
}

/**
 * Snapshot of `gSaveBlock3Ptr->challengeSettings` for the exact H&S 2.0.5 build.
 *
 * Exact trust is required upstream of this model: the companion poller only reads
 * while [com.dualdex.romhack.RuntimeRomTrust.mayReadLiveMemory] holds, so a
 * recognized-but-unverified H&S ROM never receives a snapshot from this model —
 * [status] stays [HnsChallengeSettingsStatus.UNAVAILABLE] and no default is substituted.
 *
 * The multi-bit value semantics come from the pinned source (see
 * `native/src/hns_challenge_settings_layout_gen.h` and
 * docs/HNS_2_0_5_COMPATIBILITY_EVIDENCE.md): LevelCap is OFF/NORMAL/HARD,
 * TrainerScalingIVs is OFF/SCALE/HARD, MaxPartyIVs has three choices,
 * ExpMultiplier is x1.0/x1.5/x2.0/x0.0, BaseStatEqualizer indexes the
 * 0/100/255/500 BST table. `raw` preserves the source encoding rather than
 * coercing it to an arbitrary Boolean.
 */
data class HnsChallengeSettingsSnapshot(
    val status: HnsChallengeSettingsStatus = HnsChallengeSettingsStatus.UNAVAILABLE,
    val optionStyle: HnsChallengeField = HnsChallengeField(),
    val txModeFairyTypes: HnsChallengeField = HnsChallengeField(),
    val txRandomType: HnsChallengeField = HnsChallengeField(),
    val txRandomTypeEffectiveness: HnsChallengeField = HnsChallengeField(),
    val txRandomAbilities: HnsChallengeField = HnsChallengeField(),
    val txRandomMoves: HnsChallengeField = HnsChallengeField(),
    val txChallengesNoEvs: HnsChallengeField = HnsChallengeField(),
    val txChallengesBaseStatEqualizer: HnsChallengeField = HnsChallengeField(),
    val txChallengesMirror: HnsChallengeField = HnsChallengeField(),
    /** Source field name is `tx_Challenges_Mirror_Thief`. */
    val txChallengesMirrorThief: HnsChallengeField = HnsChallengeField(),
    val txChallengesTrainerScalingIvs: HnsChallengeField = HnsChallengeField(),
    val txChallengesTrainerScalingEvs: HnsChallengeField = HnsChallengeField(),
    val txChallengesMaxPartyIvs: HnsChallengeField = HnsChallengeField(),
    val txModeSturdy: HnsChallengeField = HnsChallengeField(),
    val txChallengesLevelCap: HnsChallengeField = HnsChallengeField(),
    val txChallengesExpMultiplier: HnsChallengeField = HnsChallengeField(),
    val txModeLegendaryAbilities: HnsChallengeField = HnsChallengeField()
) {
    /**
     * optionStyle with its source enum meaning: 0 = per-move physical/special
     * split, 1 = the type decides. This is a 1-bit source field, so it has no
     * out-of-domain encoding.
     */
    val optionStyleSemantics: HnsOptionStyle
        get() = when {
            optionStyle.observed && optionStyle.raw == 0 -> HnsOptionStyle.PER_MOVE_SPLIT
            optionStyle.observed && optionStyle.raw == 1 -> HnsOptionStyle.TYPE_BASED
            else -> HnsOptionStyle.UNAVAILABLE
        }

    companion object {
        /** Field order is fixed and must match `LibretroHost.nativeReadChallengeSettings`. */
        private const val FIELD_COUNT = 17

        /**
         * Decodes the flat native tuple: `[0]` status, then one triple per field
         * (observed, raw, outOfDomain). A null or short array decodes to UNAVAILABLE.
         */
        fun fromNativeArray(values: IntArray?): HnsChallengeSettingsSnapshot {
            if (values == null || values.size < 1 + 3 * FIELD_COUNT) return HnsChallengeSettingsSnapshot()
            val status = when (values[0]) {
                1 -> HnsChallengeSettingsStatus.OBSERVED
                2 -> HnsChallengeSettingsStatus.OBSERVED_INVALID
                else -> HnsChallengeSettingsStatus.UNAVAILABLE
            }
            fun field(index: Int) = HnsChallengeField(
                observed = values[1 + 3 * index] != 0,
                raw = values[2 + 3 * index],
                outOfDomain = values[3 + 3 * index] != 0
            )
            return HnsChallengeSettingsSnapshot(
                status = status,
                optionStyle = field(0),
                txModeFairyTypes = field(1),
                txRandomType = field(2),
                txRandomTypeEffectiveness = field(3),
                txRandomAbilities = field(4),
                txRandomMoves = field(5),
                txChallengesNoEvs = field(6),
                txChallengesBaseStatEqualizer = field(7),
                txChallengesMirror = field(8),
                txChallengesMirrorThief = field(9),
                txChallengesTrainerScalingIvs = field(10),
                txChallengesTrainerScalingEvs = field(11),
                txChallengesMaxPartyIvs = field(12),
                txModeSturdy = field(13),
                txChallengesLevelCap = field(14),
                txChallengesExpMultiplier = field(15),
                txModeLegendaryAbilities = field(16)
            )
        }
    }
}
