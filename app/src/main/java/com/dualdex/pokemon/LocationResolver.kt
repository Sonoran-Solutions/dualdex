package com.dualdex.pokemon

import com.dualdex.pokemon.hns.Hns205MapData
import com.dualdex.romhack.RomHackProfile

/**
 * Which game's row/column map table interprets a raw SaveBlock1 mapGroup/mapNum.
 *
 * This is *not* the region the player is currently in, and it is *not* the
 * region the user is browsing. Heart & Soul keeps the same native layout while
 * the player walks from Johto into Kanto, Sinjoh, Alola, an interior or a cave,
 * so entering Kanto never selects [FIRERED] and browsing a Kanto map never
 * changes which reader is used.
 */
enum class LocationStrategy(val displayName: String) {
    /** Exact verified vanilla Emerald: Hoenn town-map numbering. */
    EMERALD("Emerald"),

    /** Exact verified vanilla FireRed: Kanto town-map numbering. */
    FIRERED("FireRed"),

    /** Exact Heart & Soul 2.0.5: pinned H&S multi-region map table. */
    HEART_AND_SOUL_205("Heart & Soul 2.0.5"),

    /**
     * The running ROM is not a build DualDex holds a location table for, so
     * there is no authoritative live location and none is fabricated.
     */
    UNVERIFIED("Unverified");

    val providesLiveLocation: Boolean
        get() = this != UNVERIFIED

    companion object {
        /**
         * Select the location strategy from the typed active profile.
         *
         * Profile identity (not a profile *name* substring) decides this. An
         * unrecognised profile yields [UNVERIFIED] rather than defaulting to any
         * game's map table.
         */
        fun forProfile(profile: RomHackProfile): LocationStrategy = when (profile.id) {
            HEART_AND_SOUL_PROFILE_ID -> HEART_AND_SOUL_205
            VANILLA_EMERALD_PROFILE_ID -> EMERALD
            VANILLA_FIRERED_PROFILE_ID -> FIRERED
            else -> UNVERIFIED
        }

        const val HEART_AND_SOUL_PROFILE_ID = "heart_and_soul"
        const val VANILLA_EMERALD_PROFILE_ID = "vanilla_emerald"
        const val VANILLA_FIRERED_PROFILE_ID = "vanilla_firered"
    }
}

/**
 * Why a location could not be resolved.
 *
 * Distinguishing these lets the UI say something true instead of showing a
 * plausible-looking town: "no supported map table" and "this game's map table
 * has no such map" are different facts.
 */
enum class LocationUnavailableReason {
    /** No strategy is selected, or the strategy has no map table. */
    NO_STRATEGY,

    /** The raw read was absent or marked invalid. */
    INVALID_READ,

    /** The strategy's table has no entry for this mapGroup/mapNum pair. */
    UNKNOWN_MAP_ID,

    /** DualDex knows the map but cannot place it on a drawable region. */
    NOT_PRESENTABLE,
}

/**
 * Outcome of resolving a raw location.
 *
 * [section] is non-null exactly when [reason] is null, so a caller cannot read a
 * section without having handled the unavailable case first.
 */
data class LocationResolution(
    val section: RegionMapSection?,
    val reason: LocationUnavailableReason?
) {
    val isResolved: Boolean
        get() = section != null

    companion object {
        fun resolved(section: RegionMapSection): LocationResolution =
            LocationResolution(section, null)

        fun unavailable(reason: LocationUnavailableReason): LocationResolution =
            LocationResolution(null, reason)
    }
}

/**
 * Resolve a raw player location into a displayable map section.
 *
 * Fail-closed contract:
 *  * an unknown or out-of-range mapGroup/mapNum never yields a different, known
 *    location;
 *  * a map number that is invalid inside an otherwise valid group never
 *    inherits that group's parent town;
 *  * nothing here falls back to a default region or a previous location.
 */
object LocationResolver {

    fun resolve(strategy: LocationStrategy, location: PlayerLocation?): LocationResolution {
        if (location == null || !location.isValid) {
            return LocationResolution.unavailable(LocationUnavailableReason.INVALID_READ)
        }
        return when (strategy) {
            LocationStrategy.UNVERIFIED ->
                LocationResolution.unavailable(LocationUnavailableReason.NO_STRATEGY)

            LocationStrategy.HEART_AND_SOUL_205 -> resolveHeartAndSoul205(location)
            LocationStrategy.EMERALD -> resolveEmerald(location)
            LocationStrategy.FIRERED -> resolveFireRed(location)
        }
    }

    /**
     * Heart & Soul 2.0.5.
     *
     * Identity comes only from the generated 2.0.5 table. The raw pair is
     * validated as a pair: an undefined map number is unavailable even when its
     * group defines other maps, and a group index beyond the pinned H&S table is
     * never interpreted through another game's numbering.
     */
    private fun resolveHeartAndSoul205(location: PlayerLocation): LocationResolution {
        val map = Hns205MapData.findLocation(location.mapGroup, location.mapNum)
            ?: return LocationResolution.unavailable(LocationUnavailableReason.UNKNOWN_MAP_ID)

        val generated = Hns205MapData.findSection(map.sectionId)
            ?: return LocationResolution.unavailable(LocationUnavailableReason.UNKNOWN_MAP_ID)

        val curated = RegionMapDatabase.curatedSection(generated.sectionId)

        // A section resolved from a real map identity is understood and carries a
        // true region. It is only *drawable* when the active region map actually
        // shows it, so presentation is tracked separately from identity.
        return LocationResolution.resolved(
            RegionMapSection(
                id = generated.sectionId,
                name = curated?.name ?: generated.displayName,
                region = generated.region,
                gridX = generated.gridX,
                gridY = generated.gridY,
                width = generated.width,
                height = generated.height,
                // Presentation kind: curated metadata is human-checked, so it wins
                // for the node kind only. Identity, region and geometry always
                // come from the generated 2.0.5 table.
                nodeType = curated?.nodeType ?: generated.nodeType,
                description = curated?.description.orEmpty(),
                landmarks = curated?.landmarks.orEmpty(),
                gymLeader = curated?.gymLeader,
                badge = curated?.badge,
                connections = curated?.connections.orEmpty(),
                presentable = generated.presentable && generated.region?.hasCanvas == true
            )
        )
    }

    private fun resolveEmerald(location: PlayerLocation): LocationResolution {
        val section = RegionMapDatabase.resolveEmeraldSection(location)
            ?: return LocationResolution.unavailable(LocationUnavailableReason.UNKNOWN_MAP_ID)
        return LocationResolution.resolved(section)
    }

    private fun resolveFireRed(location: PlayerLocation): LocationResolution {
        val section = RegionMapDatabase.resolveFireRedSection(location)
            ?: return LocationResolution.unavailable(LocationUnavailableReason.UNKNOWN_MAP_ID)
        return LocationResolution.resolved(section)
    }
}
