package com.dualdex.calculator

/** Source GetBattlerTotalSpeedStat, independent of action order. Null is never neutral Speed. */
object HnsEffectiveSpeedAuthority {
    data class Result(val speed: Long?, val reason: String)
    val speedHoldEffects = setOf("HOLD_EFFECT_MACHO_BRACE", "HOLD_EFFECT_POWER_ITEM",
        "HOLD_EFFECT_IRON_BALL", "HOLD_EFFECT_CHOICE_SCARF", "HOLD_EFFECT_QUICK_POWDER")

    fun forRequest(request: DamageCalculationRequest, side: HnsAbilitySide): Result =
        resolve(HnsAbilityContextPolicy.contextForRequest(request, side, true))

    internal fun resolve(c: HnsAbilityContextPolicy.Context): Result {
        fun unknown(reason: String) = Result(null, reason)
        val live = c.liveBattleState ?: return unknown("Live state unread")
        if (live.observedBattlersCount != 2 || !c.attackerAbilityObserved || !c.defenderAbilityObserved)
            return unknown("Exact Singles effective abilities unread")
        val attacker = c.side == HnsAbilitySide.ATTACKER
        val raw = (if (attacker) live.attackerRawStats else live.defenderRawStats)?.speed
        val stage = (if (attacker) live.attackerStatStages else live.defenderStatStages)?.getOrNull(3)
        if (raw == null || raw !in 1..65535 || stage == null || stage !in -6..6)
            return unknown("Raw Speed or Speed stage outside source domain")
        val ability = (if (attacker) c.attackerAbilityId else c.defenderAbilityId)
            ?: return unknown("Effective ability unread")
        val hold = (if (attacker) c.attackerHoldEffectResolution else c.defenderHoldEffectResolution)
            ?.effectiveHoldEffect ?: return unknown("Effective hold effect unread")
        val status = if (attacker) live.attackerStatus1 else live.defenderStatus1
        if (status != 0) return unknown("Speed requires observed neutral status1")
        val side = (if (attacker) live.attackerSideStatuses else
            live.defenderSideStatuses.takeIf { live.defenderScreensObserved })
            ?: return unknown("Side statuses unread")
        if (side < 0) return unknown("Side statuses outside source domain")
        if (!live.weatherObserved || live.weatherWord and 0x19.inv() != 0)
            return unknown("Weather unread or unsupported")
        val field = live.fieldStatuses ?: return unknown("Field statuses unread")
        val weatherEffective = HnsFieldAbilityAuthority(c).weatherEffective()
            ?: return unknown("Global weather-effect authority unread")
        val badge = (if (attacker) live.attackerBadgeBoosts else live.defenderBadgeBoosts)?.spe
            ?: return unknown("Speed badge result unread")
        var speed = raw.toLong() * (if (stage >= 0) 2 + stage else 2) /
            (if (stage >= 0) 2 else 2 - stage)
        if (weatherEffective && hold != "HOLD_EFFECT_UTILITY_UMBRELLA" &&
            (ability == 33 && live.weatherWord and 1 != 0 || ability == 34 && live.weatherWord and 0x18 != 0))
            speed *= 2
        when (ability) {
            207 -> if (field and 0x100 != 0) speed *= 2 // Raw Electric Terrain; no groundedness read.
            112 -> {
                val timer = if (attacker) live.attackerSlowStartTimer else live.defenderSlowStartTimer
                if (timer == null || timer !in 0..7) return unknown("Slow Start timer unread")
                if (timer != 0) speed /= 2
            }
            281, 282 -> {
                val transformed = (if (attacker) live.attackerTransformed else live.defenderTransformed)
                    ?: return unknown("Paradox transformed state unread")
                val booster = (if (attacker) live.attackerBoosterEnergyActivated else live.defenderBoosterEnergyActivated)
                    ?: return unknown("Booster Energy activation unread")
                if (!transformed && (booster || ability == 281 && weatherEffective && live.weatherWord and 0x18 != 0 ||
                        ability == 282 && field and 0x100 != 0)) {
                    val stat = if (attacker) live.attackerParadoxBoostedStat else live.defenderParadoxBoostedStat
                    if (stat == null || stat !in 1..5) return unknown("Paradox boosted stat unread")
                    if (stat == 3) speed = speed * 150 / 100
                }
            }
            84 -> return unknown("Unburden activation is not transported")
        }
        if (badge) speed = (4506 * speed + 2047) / 4096
        when (hold) {
            "HOLD_EFFECT_MACHO_BRACE", "HOLD_EFFECT_POWER_ITEM", "HOLD_EFFECT_IRON_BALL" -> speed /= 2
            "HOLD_EFFECT_CHOICE_SCARF" -> {
                val gimmick = (if (attacker) live.attackerGimmick else live.defenderGimmick)
                    ?: return unknown("Active gimmick unread")
                if (gimmick != 4) speed = speed * 150 / 100
            }
            "HOLD_EFFECT_QUICK_POWDER" -> {
                val species = (if (attacker) live.attackerSpeciesId else live.defenderSpeciesId)
                    ?: return unknown("Battle species unread")
                if (species == 132) {
                    val transformed = (if (attacker) live.attackerTransformed else live.defenderTransformed)
                        ?: return unknown("Quick Powder transformed state unread")
                    if (!transformed) speed *= 2
                }
            }
        }
        if (side and (1 shl 4) != 0) speed *= 2
        // ponytail: status-neutral production; paralysis /4 and Quick Feet require a later execution contract.
        if (side and (1 shl 10) != 0) speed /= 4
        return Result(speed, "Pinned source-ordered effective Speed")
    }

    fun gyroBallPower(attacker: Long, defender: Long): Int =
        if (attacker == 0L) 1 else minOf(25 * defender / attacker + 1, 150).toInt()
}
