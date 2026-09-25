package com.dualdex.calculator.census

import com.dualdex.calculator.CalcGameTypes
import com.dualdex.pokemon.hns.BattlerRuntimeObservation
import com.dualdex.pokemon.hns.HnsBattlerRuntimeState
import com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus
import com.dualdex.pokemon.hns.HnsBattlerTypeObservation
import com.dualdex.pokemon.hns.HnsChallengeField
import com.dualdex.pokemon.hns.HnsChallengeSettingsSnapshot
import com.dualdex.pokemon.hns.HnsChallengeSettingsStatus
import com.dualdex.pokemon.hns.HnsItemData
import com.dualdex.pokemon.hns.hnsRuntimeTypeName
import com.dualdex.romhack.RomCompatibility
import com.dualdex.romhack.RomHackProfile
import com.dualdex.romhack.RuntimeRomTrust
import com.dualdex.pokemon.DeclaredAbility

/**
 * The census' **neutral baseline battle context** (issue #84).
 *
 * The census answers one question: *under a fixed, explicitly documented baseline runtime
 * state, which abilities/items/moves prevent DualDex from displaying damage for this
 * matchup?* A bare [com.dualdex.calculator.CalcCapabilityPolicy.evaluate] call with no live
 * operands cannot answer it, because the production policy correctly refuses a request whose
 * live evidence is missing. This object therefore supplies the SAME truthfully-observed
 * neutral runtime state an ordinary singles battle would have, and nothing more:
 *
 * | Operand | Baseline value | Why |
 * |---|---|---|
 * | profile / build identity | pinned H&S 2.0.5 data pack (`hns_2_0_5`), expansion engine, phys/spec split | the build the census measures |
 * | ROM trust | this file's own identity hash, exact-verified for this profile | the census measures the production policy at its real trust ceiling, not a loosened one |
 * | topology (`gBattlersCount`) | `2` for a trainer whose pinned `battleType` is SINGLES | that battle really is a singles battle |
 * | challenger settings | observed, `optionStyle = PER_MOVE_SPLIT`, Random Types/Type-Effectiveness/Abilities/Moves OFF, no base-stat equalizer, no level/IV/EV scaling | ordinary challenge configuration, stated explicitly rather than left unread |
 * | field (`gFieldStatuses`) | observed `0` | no terrain, no Ion Deluge, no room effect |
 * | weather (`gBattleWeather`) | observed `0` | clear |
 * | defender side (`gSideStatuses`) | observed `0` | no Reflect, no Light Screen |
 * | volatiles (per battler) | observed, every bit false | no Charge, Tar Shot, Glaive Rush, Substitute, Gastro Acid, screens, etc. |
 * | stat stages | observed `0 0 0 0 0 0 0 0` | neutral |
 * | badges (player attacker) | observed false | no badge boost |
 * | HP / status | defender at full HP, attacker at full HP, `status1 = 0` | neutral, and it makes a pinch ability provably inactive rather than accidentally active |
 * | gimmick | observed `GIMMICK_NONE` | no Tera/Dynamax/Z |
 * | item | the trainer/player fixture's own pinned item ID | truthful, not omitted |
 * | ability | the trainer/player fixture's own pinned effective ability ID | truthful, not omitted |
 *
 * This is a HOST-ONLY measurement harness. It is never called by the app, it changes no
 * production default, and it adds no `censusMode`/`ignoreBlockers` escape hatch to any
 * production policy: the census constructs truthful policy INPUT, it never bypasses the
 * policy itself.
 *
 * Every value here is asserted against the production boundary by
 * [HnsCalcCensusEngine]: a baseline that the real [com.dualdex.calculator.CalcRequestBoundary]
 * rejects would produce a census made of unrelated refusals, which the census' own
 * `baselinePositiveControl` check fails on.
 */
object HnsCalcCensusBaseline {

    /** The pinned build this census measures. */
    const val PINNED_COMMIT: String = "1f42b74dff0e9fe942419845d040663dd829a973"
    const val PINNED_TAG: String = "Release-v2.0.5"

    /**
     * The host-side build identity the census asserts through the existing trust mechanism.
     *
     * It is deliberately NOT presented as a ROM hash of a retail image: it is the identity of
     * the asserted census context, and the only thing it is used for is to pass the profile's
     * own exact-hash gate. The census measures the H&S PRODUCTION POLICY at its real
     * exact-trusted ceiling; it neither weakens [RuntimeRomTrust] nor adds a trust path.
     */
    const val CENSUS_IDENTITY_SHA256: String =
        "0000000000000000000000000000000000000000000000000000000000000084"

    const val PROFILE_ID: String = "heart_and_soul"

    /** The pinned data pack the census measures. */
    const val DATA_PACK_ID: String = "hns_2_0_5"

    /** `POKEMON_HNS` selects `src/data/trainers.h` (`src/data.c`, `IS_HNS`). */
    const val UPSTREAM_ENGINE: String = "pokeemerald-expansion"

    /**
     * The asserted profile. It is stated in code rather than parsed from
     * `app/src/main/assets/profiles/heart_and_soul.json` so the offline census has no asset
     * dependency; [HnsCalcCensusTest] asserts it agrees with the bundled profile.
     */
    val profile: RomHackProfile = RomHackProfile(
        id = PROFILE_ID,
        name = "Pokemon Heart & Soul 2.0.5 (census)",
        baseGame = "Emerald",
        gameId = 3,
        engine = UPSTREAM_ENGINE,
        hasEvs = true,
        hasIvs = true,
        hasPhysSpecSplit = true,
        steelResistsGhostDark = false,
        isVerified = true,
        memoryLayoutVerified = true,
        sha256Hashes = listOf(CENSUS_IDENTITY_SHA256),
        gameDataPackId = DATA_PACK_ID
    )

    /** Exact trust for [profile] through the production [RuntimeRomTrust] mechanism only. */
    val trust: RuntimeRomTrust = RuntimeRomTrust.from(
        compatibility = RomCompatibility.verified(profile, CENSUS_IDENTITY_SHA256),
        activeRomSha256 = CENSUS_IDENTITY_SHA256
    )

    /**
     * The ordinary challenge configuration, every field explicitly observed.
     *
     * `optionStyle = 0` is the pinned `PER_MOVE_SPLIT`. `tx_Mode_Sturdy` and
     * `tx_Mode_Legendary_Abilities` are recorded as their pinned in-game values; neither
     * changes the damage number the calculator displays (Sturdy is gated behind the Sturdy
     * ability, which the policy already refuses; the legendary ability substitution is
     * captured downstream by the observed effective ability).
     */
    val challengeSettings: HnsChallengeSettingsSnapshot = HnsChallengeSettingsSnapshot(
        status = HnsChallengeSettingsStatus.OBSERVED,
        optionStyle = observed(0),
        txModeFairyTypes = observed(1),
        txRandomType = observed(0),
        txRandomTypeEffectiveness = observed(0),
        txRandomAbilities = observed(0),
        txRandomMoves = observed(0),
        txChallengesNoEvs = observed(0),
        txChallengesBaseStatEqualizer = observed(0),
        txChallengesMirror = observed(0),
        txChallengesMirrorThief = observed(0),
        txChallengesTrainerScalingIvs = observed(0),
        txChallengesTrainerScalingEvs = observed(0),
        txChallengesMaxPartyIvs = observed(0),
        txModeSturdy = observed(1),
        txChallengesLevelCap = observed(0),
        txChallengesExpMultiplier = observed(0),
        txModeLegendaryAbilities = observed(1)
    )

    /** Which participant of the census request this observation stands for. */
    enum class Participant { ATTACKER, DEFENDER }

    /**
     * One authoritative, fully observed, neutral battler.
     *
     * [types] are the species' own pinned types, so the observation is the *truthful* current
     * typing of that battler rather than a caller default.
     */
    fun observation(
        participant: Participant,
        partySlot: Int,
        speciesId: Int,
        speciesName: String,
        types: List<String>,
        abilityId: Int,
        itemId: Int,
        abilityName: String?,
        item: HnsItemData?,
        gBattlersCount: Int
    ): BattlerRuntimeObservation {
        val rawTypes = types.map { rawTypeId(it) }
        return BattlerRuntimeObservation(
            state = HnsBattlerRuntimeState(
                status = HnsBattlerRuntimeStatus.OBSERVED,
                battlerIndex = if (participant == Participant.ATTACKER) 0 else 1,
                partySlot = partySlot,
                speciesId = speciesId,
                abilityId = abilityId,
                abilityOutOfDomain = false,
                types = rawTypes.map {
                    HnsBattlerTypeObservation(observed = true, raw = it, outOfDomain = false)
                },
                itemId = itemId,
                itemOutOfDomain = false,
                statsObserved = true,
                rawAttack = 1,
                rawDefense = 1,
                rawSpeed = 1,
                rawSpAttack = 1,
                rawSpDefense = 1,
                stagesObserved = true,
                statStages = List(8) { 0 },
                // Badge boosts are player-side only; the DEFENDER never carries any, and the
                // production policy only gates on the attacker's.
                badgesObserved = participant == Participant.ATTACKER,
                badgeBoostAtk = false,
                badgeBoostDef = false,
                badgeBoostSpe = false,
                badgeBoostSpa = false,
                badgeBoostSpd = false,
                rawBadgesByte = if (participant == Participant.ATTACKER) 0 else null,
                absentBattlerFlags = 0,
                absentFlagsReadable = true,
                battlersCount = gBattlersCount,
                battlersCountReadable = true,
                hpObserved = true,
                hp = NEUTRAL_MAX_HP,
                maxHp = NEUTRAL_MAX_HP,
                statusObserved = true,
                status1 = 0,
                volatilesObserved = true,
                volatileElectrified = false,
                volatileGlaiveRush = false,
                volatileMinimize = false,
                volatileSemiInvulnerable = 0,
                transientVolatilesObserved = true,
                volatileChargeTimer = 0,
                volatileTarShot = false,
                persistentVolatilesObserved = true,
                volatileForesight = false,
                volatileMiracleEye = false,
                volatileRoot = false,
                volatileSmackDown = false,
                volatileTelekinesis = false,
                volatileMagnetRise = false,
                volatileGastroAcid = false,
                volatileRoostActive = false,
                volatileSubstitute = false,
                volatileEndured = false,
                gimmickObserved = true,
                activeGimmick = 0,
                fieldStatusesReadable = true,
                fieldStatuses = 0,
                weatherReadable = true,
                battleWeather = 0,
                sideStatusesReadable = true,
                sideStatuses = 0
            ),
            abilityIdentity = abilityName?.let { DeclaredAbility.Declared(abilityId, it) }
                ?: DeclaredAbility.EmptySlot,
            itemIdentity = item
        ).also {
            check(speciesName.isNotEmpty()) { "a census participant must name a species" }
        }
    }

    /** Clear-weather, no-screen field input. The boundary rebinds these from the observation. */
    fun field(gameType: String) = com.dualdex.calculator.CalcFieldInput(gameType = gameType)

    /** Singles is the only topology the production subset models. */
    const val SINGLES_BATTLERS_COUNT: Int = 2

    /** Doubles is the topology a trainer whose pinned `battleType` is DOUBLES really has. */
    const val DOUBLES_BATTLERS_COUNT: Int = 4

    val SINGLES_GAME_TYPE: String = CalcGameTypes.SINGLES
    val DOUBLES_GAME_TYPE: String = CalcGameTypes.DOUBLES

    /**
     * The neutral full-HP value used for both participants.
     *
     * It is a positive full-HP observation, not a real stat: any positive value makes a pinch
     * ability's `hp <= maxHP/3` predicate provably false, and the policy consumes the live HP
     * only through that predicate and through `curHP`-based presentation.
     */
    const val NEUTRAL_MAX_HP: Int = 100

    private fun observed(raw: Int) = HnsChallengeField(observed = true, raw = raw, outOfDomain = false)

    /**
     * The pinned H&S `enum Type` id for a canonical type name.
     *
     * Derived by inverting the production `hnsRuntimeTypeName` mapping rather than restating the
     * pinned enum order here, so this can never drift from the reader's own type domain.
     */
    fun rawTypeId(name: String): Int {
        for (id in 1..com.dualdex.pokemon.hns.HnsBattlerRuntimeStateIds.TYPE_ID_MAX) {
            val mapped = com.dualdex.pokemon.hns.hnsRuntimeTypeName(id)
            if (mapped != null && mapped.equals(name, ignoreCase = true)) return id
        }
        error("unrepresentable census type: $name")
    }
}
