package com.dualdex.calculator.census

/**
 * Minimal deterministic JSON writer.
 *
 * `org.json.JSONObject` is backed by an unordered map, so it cannot produce the canonical,
 * byte-stable output the census' `--check` mode compares. This writer emits object members in
 * insertion order with a fixed two-space indent, which is the whole requirement.
 */
internal sealed class JsonValue {
    data class Str(val value: String) : JsonValue()
    data class Num(val value: Long) : JsonValue()
    data class Bool(val value: Boolean) : JsonValue()
    data class Arr(val items: List<JsonValue>) : JsonValue()
    data class Obj(val members: List<Pair<String, JsonValue>>) : JsonValue()
    object Null : JsonValue()

    fun render(): String {
        val out = StringBuilder()
        write(out, 0)
        return out.toString()
    }

    private fun write(out: StringBuilder, indent: Int) {
        when (this) {
            is Str -> out.append(quote(value))
            is Num -> out.append(value)
            is Bool -> out.append(if (value) "true" else "false")
            Null -> out.append("null")
            is Arr -> {
                if (items.isEmpty()) {
                    out.append("[]")
                    return
                }
                out.append("[\n")
                items.forEachIndexed { index, item ->
                    pad(out, indent + 1)
                    item.write(out, indent + 1)
                    if (index != items.size - 1) out.append(',')
                    out.append('\n')
                }
                pad(out, indent)
                out.append(']')
            }
            is Obj -> {
                if (members.isEmpty()) {
                    out.append("{}")
                    return
                }
                out.append("{\n")
                members.forEachIndexed { index, (name, value) ->
                    pad(out, indent + 1)
                    out.append(quote(name)).append(": ")
                    value.write(out, indent + 1)
                    if (index != members.size - 1) out.append(',')
                    out.append('\n')
                }
                pad(out, indent)
                out.append('}')
            }
        }
    }

    private fun pad(out: StringBuilder, indent: Int) {
        repeat(indent) { out.append("  ") }
    }

    private fun quote(raw: String): String {
        val out = StringBuilder("\"")
        for (c in raw) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (c < ' ') out.append("\\u%04x".format(c.code)) else out.append(c)
            }
        }
        return out.append('"').toString()
    }

    companion object {
        fun str(value: String?): JsonValue = if (value == null) Null else Str(value)
        fun num(value: Int): JsonValue = Num(value.toLong())
        fun num(value: Long): JsonValue = Num(value)
        fun bool(value: Boolean): JsonValue = Bool(value)
        fun arr(items: List<JsonValue>): JsonValue = Arr(items)
        fun strArr(items: List<String>): JsonValue = Arr(items.map { Str(it) })
        fun obj(vararg members: Pair<String, JsonValue>): JsonValue = Obj(members.toList())
        fun obj(members: List<Pair<String, JsonValue>>): JsonValue = Obj(members)
    }
}

/**
 * Turns one census run into the committed artifacts.
 *
 * Two outputs, both canonical:
 *  - `census.json`  - the full machine-readable record, deterministically ordered, including
 *                     every request the census evaluated.
 *  - `docs/HNS_CALC_CENSUS.md` - a readable summary that states the denominators, the
 *                     methodology and the ranked blockers rather than dumping rows.
 *
 * Neither output contains a generation timestamp or an absolute path, so a regeneration on a
 * different machine produces identical bytes or fails `--check`.
 */
object HnsCalcCensusReport {

    const val JSON_FILE_NAME: String = "census.json"
    const val DOC_FILE_NAME: String = "HNS_CALC_CENSUS.md"

    // ------------------------------------------------------------------------------ helpers

    private fun <T> counted(
        items: Iterable<T>,
        requestsOf: (T) -> Int,
        battlesOf: (T) -> Int
    ): List<Pair<T, Pair<Int, Int>>> =
        items.groupBy { it }
            .map { (key, group) ->
                key to (group.sumOf { requestsOf(it) } to group.sumOf { battlesOf(it) })
            }

    private data class BlockerRank(
        val key: String,
        val kind: String,
        val side: String?,
        val identity: String?,
        val requests: Int,
        val battles: Int
    )

    private fun blockerRanks(run: HnsCalcCensusEngine.CensusRun): List<BlockerRank> {
        data class Occurrence(val blocker: HnsCensusBlocker, val requestKey: String, val trainerKey: String)

        val occurrences = mutableListOf<Occurrence>()
        for (record in run.requests) {
            for (blocker in record.outcome.blockers) {
                occurrences += Occurrence(blocker, record.key, record.trainerKey)
            }
        }
        return occurrences
            .groupBy { Triple(it.blocker.kind, it.blocker.side, it.blocker.identity) }
            .map { (key, group) ->
                BlockerRank(
                    key = key.third ?: key.first,
                    kind = key.first,
                    side = key.second,
                    identity = key.third,
                    requests = group.map { it.requestKey }.distinct().size,
                    // A battle counts once per blocker, however many of its requests hit it.
                    battles = group.map { it.trainerKey }.distinct().size
                )
            }
            .sortedWith(
                compareByDescending<BlockerRank> { it.battles }
                    .thenByDescending { it.requests }
                    .thenBy { it.kind }
                    .thenBy { it.side ?: "" }
                    .thenBy { it.identity ?: "" }
            )
    }

    private fun ignoredMechanicRanks(run: HnsCalcCensusEngine.CensusRun): List<BlockerRank> {
        data class Occurrence(val mechanic: HnsCensusBlocker, val requestKey: String, val trainerKey: String)

        val occurrences = run.requests.flatMap { record ->
            record.outcome.ignoredMechanics.map { Occurrence(it, record.key, record.trainerKey) }
        }
        return occurrences
            .groupBy { Triple(it.mechanic.kind, it.mechanic.side, it.mechanic.identity) }
            .map { (key, group) ->
                BlockerRank(
                    key = key.third ?: key.first,
                    kind = key.first,
                    side = key.second,
                    identity = key.third,
                    requests = group.map { it.requestKey }.distinct().size,
                    battles = group.map { it.trainerKey }.distinct().size
                )
            }
            .sortedWith(
                compareByDescending<BlockerRank> { it.battles }
                    .thenByDescending { it.requests }
                    .thenBy { it.kind }
                    .thenBy { it.side ?: "" }
                    .thenBy { it.identity ?: "" }
            )
    }

    private data class MoveRank(val key: String, val kind: String, val requests: Int, val battles: Int)

    private fun limitationRanks(run: HnsCalcCensusEngine.CensusRun): List<MoveRank> {
        data class Occurrence(val limitation: String, val requestKey: String, val trainerKey: String)

        val occurrences = mutableListOf<Occurrence>()
        for (record in run.requests) {
            // A soft limitation blocks only when this request lacks complete evidence. Keep the
            // census aligned with the production verdict's request-level classification instead
            // of the enum's hard-refusal disposition.
            for (blocker in record.outcome.blockers) {
                occurrences += Occurrence(blocker.limitation.name, record.key, record.trainerKey)
            }
        }
        return occurrences
            .groupBy { it.limitation }
            .map { (name, group) ->
                MoveRank(
                    key = name,
                    kind = name,
                    requests = group.map { it.requestKey }.distinct().size,
                    battles = group.map { it.trainerKey }.distinct().size
                )
            }
            .sortedWith(compareByDescending<MoveRank> { it.battles }.thenByDescending { it.requests }.thenBy { it.key })
    }

    private data class ItemRank(
        val item: String,
        val side: String,
        val requests: Int,
        val battles: Int
    )

    private fun itemRanks(run: HnsCalcCensusEngine.CensusRun): List<ItemRank> {
        data class Occurrence(val item: String, val side: String, val requestKey: String, val trainerKey: String)

        val occurrences = mutableListOf<Occurrence>()
        for (record in run.requests) {
            for (blocker in record.outcome.blockers) {
                if (blocker.limitation != com.dualdex.calculator.CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED) {
                    continue
                }
                val item = blocker.identity ?: continue
                val side = blocker.side ?: continue
                occurrences += Occurrence(item, side, record.key, record.trainerKey)
            }
        }
        return occurrences
            .groupBy { it.item to it.side }
            .map { (key, group) ->
                ItemRank(
                    item = key.first,
                    side = key.second,
                    requests = group.map { it.requestKey }.distinct().size,
                    battles = group.map { it.trainerKey }.distinct().size
                )
            }
            .sortedWith(
                compareByDescending<ItemRank> { it.battles }
                    .thenByDescending { it.requests }
                    .thenBy { it.item }
                    .thenBy { it.side }
            )
    }

    private data class AbilityRank(
        val abilityId: Int,
        val abilityName: String,
        val side: String,
        val category: String,
        val requests: Int,
        val battles: Int,
        val relevance: String,
        val rule: String?,
        val clearedRequests: Int
    )

    /** One ability's coverage of the reviewed contextual clearance rules. */
    private data class ClearedAbility(
        val abilityId: Int,
        val abilityName: String,
        val clearedContexts: Int,
        val blockingContexts: Int,
        val rule: String?
    )

    /**
     * The Random Abilities ranking.
     *
     * A trial is counted only when production policy refused because of that ability, so the
     * counts are exactly the requests and battles that ability would break if it were installed
     * on that side in that context. Trials where the ability is proven irrelevant stay in the
     * output at relevance `PROVEN_IRRELEVANT` with zero counts, which is what keeps contextual
     * abilities visible instead of flattening them to globally supported/unsupported.
     */
    private fun abilityRanks(run: HnsCalcCensusEngine.CensusRun): List<AbilityRank> =
        run.abilityTrials
            .map { trial ->
                AbilityRank(
                    abilityId = trial.abilityId,
                    abilityName = trial.abilityName,
                    side = trial.side,
                    category = trial.category,
                    requests = trial.requestBlocks,
                    battles = trial.battleBlocks,
                    relevance = trial.abilityRelevance,
                    rule = trial.abilityRule,
                    clearedRequests = trial.clearedRequests
                )
            }
            .sortedWith(
                compareByDescending<AbilityRank> { it.battles }
                    .thenByDescending { it.requests }
                    .thenBy { it.abilityId }
                    .thenBy { it.side }
                    .thenBy { it.category }
            )

    /**
     * The abilities the shipped contextual policy actually clears in some context, ranked by how
     * much they block elsewhere. This is the readable part of the Random Abilities view: an
     * ability with no reviewed clearance rule is refused everywhere and would swamp a plain
     * "most blocking" ranking.
     */
    private fun clearedAbilityRanks(run: HnsCalcCensusEngine.CensusRun): List<ClearedAbility> =
        run.abilityTrials
            .filter { it.clearedRequests > 0 }
            .groupBy { it.abilityId to it.abilityName }
            .map { (key, rows) ->
                ClearedAbility(
                    abilityId = key.first,
                    abilityName = key.second,
                    clearedContexts = rows.count { it.clearedRequests > 0 },
                    blockingContexts = rows.count { it.requestBlocks > 0 },
                    rule = rows.mapNotNull { row ->
                        row.rules.firstOrNull { it != "unreviewed_context" }
                    }.distinct().sorted().joinToString(", ").ifEmpty { null }
                )
            }
            .sortedWith(
                compareByDescending<ClearedAbility> { it.clearedContexts }
                    .thenBy { it.blockingContexts }
                    .thenBy { it.abilityId }
            )

    /** Distinct ability identities (not side/category rows) that block at least one request. */
    private fun blockingAbilityIdentities(run: HnsCalcCensusEngine.CensusRun): Int =
        run.abilityTrials.filter { it.blockedByAbility }.map { it.abilityId }.distinct().size

    // ---------------------------------------------------------------------------- JSON

    fun toJson(run: HnsCalcCensusEngine.CensusRun): String {
        val denominator = HnsCalcCensusMetrics.requestsByDirection(run)
        val lead = HnsCalcCensusMetrics.leadMatchup(run)
        val tierCounts = HnsCalcCensusMetrics.tierCounts(run)

        val members = mutableListOf<Pair<String, JsonValue>>()
        members += "schemaVersion" to JsonValue.num(HnsCalcCensusEngine.SCHEMA_VERSION)
        members += "pinned" to JsonValue.obj(
            "commit" to JsonValue.str(HnsCalcCensusBaseline.PINNED_COMMIT),
            "tag" to JsonValue.str(HnsCalcCensusBaseline.PINNED_TAG),
            "source" to JsonValue.str(HnsCalcCensusEngine.TRAINER_SOURCE),
            "dataPack" to JsonValue.str(HnsCalcCensusBaseline.DATA_PACK_ID),
            "engine" to JsonValue.str(HnsCalcCensusBaseline.UPSTREAM_ENGINE),
            "profileId" to JsonValue.str(HnsCalcCensusBaseline.PROFILE_ID),
            "trustSha256" to JsonValue.str(HnsCalcCensusBaseline.CENSUS_IDENTITY_SHA256)
        )
        members += "methodology" to JsonValue.obj(
            "policyEntryPoint" to JsonValue.str(HnsCalcCensusEngine.POLICY_ENTRY_POINT),
            "damageEngineInvoked" to JsonValue.bool(false),
            "tierSemantics" to JsonValue.obj(
                "FULLY_MODELLED" to JsonValue.str(
                    "a number is displayed and no mechanic that could change it is ignored"
                ),
                "CAVEATED_ESTIMATE" to JsonValue.str(
                    "a number is displayed while named mechanics are intentionally ignored " +
                        "according to the production verdict's ignoredMechanics"
                ),
                "REFUSED" to JsonValue.str("no number is displayed")
            )
        )
        members += "eligibility" to JsonValue.obj(
            "eligible" to JsonValue.str(
                "a pinned move with base power > 0: it deals damage, so production has a real " +
                    "verdict for it, including HNS_MOVE_MECHANICS_NOT_MODELLED"
            ),
            "excluded" to JsonValue.str(
                "a pinned move with base power 0 (a status move): there is no damage number to " +
                    "display or refuse for it"
            )
        )
        members += "baseline" to JsonValue.obj(
            "gameType" to JsonValue.str(
                "per trainer 'Double Battle' setting (No -> Singles/gBattlersCount 2, " +
                    "Yes -> Doubles/4)"
            ),
            "challengeSettings" to JsonValue.str("observed; optionStyle=PER_MOVE_SPLIT; Random Types/Type Effectiveness/Abilities/Moves OFF; no base-stat equalizer; no level/IV/EV scaling"),
            "fieldStatuses" to JsonValue.num(0),
            "weather" to JsonValue.str("clear"),
            "defenderSide" to JsonValue.str("no Reflect, no Light Screen"),
            "volatiles" to JsonValue.str("observed neutral on both battlers"),
            "statStages" to JsonValue.str("all zero"),
            "badges" to JsonValue.str("attacker observed unboosted (badges are player-side only)"),
            "hp" to JsonValue.str("full HP on both battlers, status1 = 0"),
            "gimmick" to JsonValue.str("GIMMICK_NONE"),
            "ability" to JsonValue.str("the participant's own resolved effective ability"),
            "item" to JsonValue.str("the participant's own pinned held item")
        )
        members += "referenceTeams" to JsonValue.arr(
            run.referenceLeads.map { lead ->
                JsonValue.obj(
                    "id" to JsonValue.str(lead.teamId),
                    "species" to JsonValue.str(lead.species),
                    "level" to JsonValue.num(lead.level),
                    "ability" to JsonValue.str(lead.ability),
                    "abilityId" to JsonValue.num(lead.abilityId),
                    "item" to JsonValue.str(lead.item),
                    "itemId" to JsonValue.num(lead.itemId),
                    "types" to JsonValue.strArr(lead.types),
                    "moves" to JsonValue.strArr(lead.moves),
                    "notes" to JsonValue.strArr(lead.notes)
                )
            }
        )
        members += "counts" to JsonValue.obj(
            "trainerBattles" to JsonValue.num(run.trainers.size),
            "trainerBattlesSingles" to JsonValue.num(run.trainers.count { it.isSingles }),
            "trainerBattlesDoubles" to JsonValue.num(run.trainers.count { !it.isSingles }),
            "leadMatchupBattles" to JsonValue.num(lead.battlesIncluded),
            "trainerPokemon" to JsonValue.num(run.trainers.sumOf { it.party.size }),
            "eligibleRequests" to JsonValue.num(run.requests.size),
            "requestsReferenceToTrainer" to JsonValue.num(denominator.first),
            "requestsTrainerToReference" to JsonValue.num(denominator.second),
            "excludedMoves" to JsonValue.num(run.excludedMoves.size),
            "excludedMovesNonDamaging" to JsonValue.num(
                run.excludedMoves.count { it.reason == "NON_DAMAGING_MOVE" }
            ),
            // Moves whose damage shape is outside the ordinary subset are NOT excluded: they are
            // evaluated and refused by production, so they count in the denominator and in the
            // blocker ranking.
            "eligibleRequestsRefusedByMoveMechanics" to JsonValue.num(
                run.requests.count {
                    it.outcome.limitations.contains(
                        com.dualdex.calculator.CalcLimitation.HNS_MOVE_MECHANICS_NOT_MODELLED
                    )
                }
            ),
            "abilityDomainSize" to JsonValue.num(run.abilityDomain.size),
            "abilityOperandCohorts" to JsonValue.num(run.abilityCohorts.size),
            "abilityTrials" to JsonValue.num(run.abilityTrials.size),
            "abilityTrialExcludedAmbiguousAttackerSide" to
                JsonValue.num(run.abilityTrialExcludedAmbiguous.first),
            "abilityTrialExcludedAmbiguousDefenderSide" to
                JsonValue.num(run.abilityTrialExcludedAmbiguous.second)
        )
        members += "resultTiers" to JsonValue.obj(
            "FULLY_MODELLED" to JsonValue.num(tierCounts.getValue(HnsCensusResultTier.FULLY_MODELLED)),
            "CAVEATED_ESTIMATE" to JsonValue.num(tierCounts.getValue(HnsCensusResultTier.CAVEATED_ESTIMATE)),
            "REFUSED" to JsonValue.num(tierCounts.getValue(HnsCensusResultTier.REFUSED))
        )
        members += "leadMatchup" to JsonValue.obj(
            "definition" to JsonValue.str(
                "for every trainer battle, the trainer's pinned party slot 0 against each " +
                    "reference team lead, evaluated in both directions over that pair's eligible " +
                    "damaging moves; displayable only when every eligible request in the pair " +
                    "displays a number"
            ),
            "battlesTotal" to JsonValue.num(run.trainers.size),
            "battlesIncluded" to JsonValue.num(lead.battlesIncluded),
            "battlesExcludedNoEligibleMove" to JsonValue.num(lead.battlesExcludedNoEligibleMove),
            "pairsTotal" to JsonValue.num(lead.pairsTotal),
            "pairsDisplayable" to JsonValue.num(lead.pairsDisplayable),
            "eligibleRequests" to JsonValue.num(lead.requestsTotal),
            "requestsDisplayable" to JsonValue.num(lead.requestsDisplayable),
            "singlesPairsTotal" to JsonValue.num(lead.singlesPairsTotal),
            "singlesPairsDisplayable" to JsonValue.num(lead.singlesPairsDisplayable),
            "doublesPairsTotal" to JsonValue.num(lead.doublesPairsTotal),
            "doublesPairsDisplayable" to JsonValue.num(lead.doublesPairsDisplayable)
        )
        members += "baselinePositiveControl" to JsonValue.obj(
            "attackerSpecies" to JsonValue.str(run.baselinePositiveControl.attackerSpecies),
            "defenderSpecies" to JsonValue.str(run.baselinePositiveControl.defenderSpecies),
            "move" to JsonValue.str(run.baselinePositiveControl.move),
            "displayTier" to JsonValue.str(run.baselinePositiveControl.displayTier),
            "limitations" to JsonValue.strArr(run.baselinePositiveControl.limitations)
        )
        members += "blockers" to JsonValue.arr(
            blockerRanks(run).map {
                JsonValue.obj(
                    "rankingKey" to JsonValue.str(it.key),
                    "limitation" to JsonValue.str(it.kind),
                    "side" to JsonValue.str(it.side),
                    "mechanic" to JsonValue.str(it.identity),
                    "requests" to JsonValue.num(it.requests),
                    "battles" to JsonValue.num(it.battles)
                )
            }
        )
        members += "limitations" to JsonValue.arr(
            limitationRanks(run).map {
                JsonValue.obj(
                    "limitation" to JsonValue.str(it.kind),
                    "requests" to JsonValue.num(it.requests),
                    "battles" to JsonValue.num(it.battles)
                )
            }
        )
        members += "itemBlockers" to JsonValue.arr(
            itemRanks(run).map {
                JsonValue.obj(
                    "item" to JsonValue.str(it.item),
                    "side" to JsonValue.str(it.side),
                    "requests" to JsonValue.num(it.requests),
                    "battles" to JsonValue.num(it.battles)
                )
            }
        )
        members += "abilityBlockers" to JsonValue.arr(
            abilityRanks(run).filter { it.requests > 0 }.map {
                JsonValue.obj(
                    "abilityId" to JsonValue.num(it.abilityId),
                    "ability" to JsonValue.str(it.abilityName),
                    "side" to JsonValue.str(it.side),
                    "category" to JsonValue.str(it.category),
                    "requests" to JsonValue.num(it.requests),
                    "battles" to JsonValue.num(it.battles),
                    "relevance" to JsonValue.str(it.relevance),
                    "rule" to JsonValue.str(it.rule),
                    "clearedRequests" to JsonValue.num(it.clearedRequests)
                )
            }
        )
        members += "abilityTrials" to JsonValue.arr(
            run.abilityTrials.map { trial ->
                JsonValue.obj(
                    "abilityId" to JsonValue.num(trial.abilityId),
                    "ability" to JsonValue.str(trial.abilityName),
                    "side" to JsonValue.str(trial.side),
                    "category" to JsonValue.str(trial.category),
                    "relevance" to JsonValue.str(trial.abilityRelevance),
                    "rule" to JsonValue.str(trial.abilityRule),
                    "blocked" to JsonValue.bool(trial.blockedByAbility),
                    "requests" to JsonValue.num(trial.requestBlocks),
                    "battles" to JsonValue.num(trial.battleBlocks),
                    "clearedRequests" to JsonValue.num(trial.clearedRequests),
                    "rules" to JsonValue.strArr(trial.rules)
                )
            }
        )
        members += "excludedMoves" to JsonValue.arr(
            run.excludedMoves.map {
                JsonValue.obj(
                    "trainer" to JsonValue.str(it.trainerKey),
                    "partySlot" to JsonValue.num(it.trainerSlot),
                    "direction" to JsonValue.str(it.direction),
                    "move" to JsonValue.str(it.move),
                    "type" to JsonValue.str(it.moveType),
                    "category" to JsonValue.str(it.moveCategory),
                    "power" to JsonValue.num(it.movePower),
                    "reason" to JsonValue.str(it.reason)
                )
            }
        )
        members += "requests" to JsonValue.arr(
            run.requests.map { record ->
                JsonValue.obj(
                    "key" to JsonValue.str(record.key),
                    "direction" to JsonValue.str(record.direction),
                    "trainer" to JsonValue.str(record.trainerKey),
                    "partySlot" to JsonValue.num(record.trainerSlot),
                    "referenceTeam" to JsonValue.str(record.referenceTeam),
                    "attacker" to JsonValue.str(record.attackerSpecies),
                    "attackerAbility" to JsonValue.str(record.attackerAbility),
                    "attackerItem" to JsonValue.str(record.attackerItem),
                    "defender" to JsonValue.str(record.defenderSpecies),
                    "defenderAbility" to JsonValue.str(record.defenderAbility),
                    "defenderItem" to JsonValue.str(record.defenderItem),
                    "move" to JsonValue.str(record.move),
                    "moveType" to JsonValue.str(record.moveType),
                    "moveCategory" to JsonValue.str(record.moveCategory),
                    "gameType" to JsonValue.str(record.gameType),
                    "battlersCount" to JsonValue.num(record.battlersCount),
                    "tier" to JsonValue.str(record.outcome.tier.wireName),
                    "support" to JsonValue.str(record.outcome.support.name),
                    "limitations" to JsonValue.strArr(record.outcome.limitations.map { it.name }),
                    // The ranked causes of this verdict, with production's own mechanic identity.
                    // The full policy decision objects are in the optional detailed dump.
                    "causes" to JsonValue.strArr(
                        (record.outcome.blockers + record.outcome.ignoredMechanics).map { blocker ->
                            buildString {
                                append(blocker.limitation.name)
                                blocker.side?.let { append(":").append(it) }
                                blocker.identity?.let { append(":").append(it) }
                                blocker.relevance?.let { append(":").append(it) }
                            }
                        }
                    ),
                    "ignoredMechanics" to JsonValue.arr(
                        record.outcome.ignoredMechanics.map { mechanic ->
                            JsonValue.obj(
                                "kind" to JsonValue.str(mechanic.kind),
                                "limitation" to JsonValue.str(mechanic.limitation.name),
                                "side" to JsonValue.str(mechanic.side),
                                "mechanic" to JsonValue.str(mechanic.identity),
                                "relevance" to JsonValue.str(mechanic.relevance),
                                "rule" to JsonValue.str(mechanic.rule)
                            )
                        }
                    )
                )
            }
        )
        return JsonValue.Obj(members).render() + "\n"
    }

    /**
     * The optional full detail dump: every request with the policy's own structured decisions.
     *
     * It is not committed - it is large and purely derivative of the same derivation - but it is
     * the artifact a reviewer or a follow-up issue (#86) inspects when a ranked number needs to be
     * traced back to one exact policy decision. Generate it with
     * `./gradlew testDebugUnitTest -Pdualdex.census.full=true`.
     */
    fun toDetailJson(run: HnsCalcCensusEngine.CensusRun): String {
        val members = mutableListOf<Pair<String, JsonValue>>()
        members += "schemaVersion" to JsonValue.num(HnsCalcCensusEngine.SCHEMA_VERSION)
        members += "note" to JsonValue.str(
            "uncommitted full detail: one row per evaluated request, with the production policy's " +
                "own ability/item/field decisions"
        )
        members += "requests" to JsonValue.arr(
            run.requests.map { record ->
                JsonValue.obj(
                    "key" to JsonValue.str(record.key),
                    "trainer" to JsonValue.str(record.trainerKey),
                    "partySlot" to JsonValue.num(record.trainerSlot),
                    "referenceTeam" to JsonValue.str(record.referenceTeam),
                    "move" to JsonValue.str(record.move),
                    "tier" to JsonValue.str(record.outcome.tier.wireName),
                    "support" to JsonValue.str(record.outcome.support.name),
                    "limitations" to JsonValue.strArr(record.outcome.limitations.map { it.name }),
                    "blockers" to JsonValue.arr(
                        record.outcome.blockers.map { blocker ->
                            JsonValue.obj(
                                "limitation" to JsonValue.str(blocker.limitation.name),
                                "side" to JsonValue.str(blocker.side),
                                "mechanic" to JsonValue.str(blocker.identity),
                                "relevance" to JsonValue.str(blocker.relevance),
                                "rule" to JsonValue.str(blocker.rule)
                            )
                        }
                    ),
                    "ignoredMechanics" to JsonValue.arr(
                        record.outcome.ignoredMechanics.map { mechanic ->
                            JsonValue.obj(
                                "limitation" to JsonValue.str(mechanic.limitation.name),
                                "side" to JsonValue.str(mechanic.side),
                                "mechanic" to JsonValue.str(mechanic.identity),
                                "relevance" to JsonValue.str(mechanic.relevance),
                                "rule" to JsonValue.str(mechanic.rule)
                            )
                        }
                    ),
                    "abilityDecisions" to JsonValue.arr(
                        record.outcome.abilityDecisions.map { decision ->
                            JsonValue.obj(
                                "abilityId" to JsonValue.num(decision.abilityId ?: -1),
                                "ability" to JsonValue.str(decision.abilityName),
                                "side" to JsonValue.str(decision.side.name.lowercase()),
                                "globalCategory" to JsonValue.str(decision.globalCategory.name),
                                "relevance" to JsonValue.str(decision.relevance.name),
                                "rule" to JsonValue.str(decision.rule),
                                "source" to JsonValue.str(decision.source)
                            )
                        }
                    ),
                    "itemDecisions" to JsonValue.arr(
                        record.outcome.itemDecisions.map { decision ->
                            JsonValue.obj(
                                "itemId" to JsonValue.num(decision.itemId ?: -1),
                                "item" to JsonValue.str(decision.itemName),
                                "side" to JsonValue.str(decision.side.name.lowercase()),
                                "globalCategory" to JsonValue.str(decision.globalCategory.name),
                                "relevance" to JsonValue.str(decision.relevance.name),
                                "rule" to JsonValue.str(decision.rule),
                                "source" to JsonValue.str(decision.source)
                            )
                        }
                    ),
                    "fieldDecisions" to JsonValue.arr(
                        record.outcome.fieldDecisions.map { decision ->
                            JsonValue.obj(
                                "label" to JsonValue.str(decision.label),
                                "rawMask" to JsonValue.num(decision.rawMask),
                                "relevance" to JsonValue.str(decision.relevance.name),
                                "rule" to JsonValue.str(decision.rule),
                                "source" to JsonValue.str(decision.source)
                            )
                        }
                    )
                )
            }
        )
        return JsonValue.Obj(members).render() + "\n"
    }

    // ------------------------------------------------------------------------------ Markdown

    fun toMarkdown(run: HnsCalcCensusEngine.CensusRun, jsonFileName: String): String {
        val lead = HnsCalcCensusMetrics.leadMatchup(run)
        val tiers = HnsCalcCensusMetrics.tierCounts(run)
        val blockers = blockerRanks(run)
        val ignoredMechanics = ignoredMechanicRanks(run)
        val limitations = limitationRanks(run)
        val items = itemRanks(run)
        val abilities = abilityRanks(run)
        val cleared = clearedAbilityRanks(run)
        val provenIrrelevantRows = run.abilityTrials.count { it.abilityRelevance == "PROVEN_IRRELEVANT" }
        val relevantRows = run.abilityTrials.count { it.abilityRelevance == "RELEVANT" }
        val unknownRows = run.abilityTrials.count { it.abilityRelevance == "UNKNOWN" }
        val out = StringBuilder()

        out.append("# H&S 2.0.5 damage-calculator blocker census\n\n")
        out.append(
            "Generated by the checked-in census tool; regenerate with the command at the end of " +
                "this file. This report is MEASUREMENT INFRASTRUCTURE: it ranks which H&S " +
                "mechanics stop the calculator displaying damage, across every trainer battle the " +
                "pinned source defines. It is not gameplay advice and it changes no production " +
                "capability decision.\n\n"
        )

        out.append("## Pinned inputs\n\n")
        out.append("| | |\n|---|---|\n")
        out.append("| Upstream | `PokemonHnS-Development/pokehns-expansion` |\n")
        out.append("| Commit | `${HnsCalcCensusBaseline.PINNED_COMMIT}` |\n")
        out.append("| Tag | `${HnsCalcCensusBaseline.PINNED_TAG}` |\n")
        out.append(
            "| Trainer source | `${HnsCalcCensusEngine.TRAINER_SOURCE}` (what the pinned build's " +
                "`trainerproc` compiles for `POKEMON_HNS`) |\n"
        )
        out.append("| Data pack | `${HnsCalcCensusBaseline.DATA_PACK_ID}` |\n")
        out.append("| Trainer inventory | `tools/hns-calc-census/trainer_inventory.json` |\n\n")

        out.append("## What the census measures\n\n")
        out.append(
            "Every request below is built with the **same production request shape the app uses** " +
                "and decided by the **same production policy**: `CalcRequestBoundary.build(...)`, " +
                "which delegates to `CalcCapabilityPolicy.evaluate(...)` and, through it, " +
                "`HnsAbilityContextPolicy`, `HnsItemContextPolicy` and `HnsFieldContextPolicy`. " +
                "The census re-implements none of them and calls the JS/native damage engine " +
                "**never**. It only constructs truthful policy *input*, and it adds no " +
                "`censusMode`/`ignoreBlockers` escape hatch anywhere.\n\n"
        )

        out.append("## Baseline runtime assumptions\n\n")
        out.append(
            "A policy call with no live operands is not a useful measurement, because the " +
                "production policy correctly refuses a request whose live evidence is missing. " +
                "The census therefore supplies the fully observed neutral state an ordinary " +
                "battle has at the start of the fight, and nothing else:\n\n"
        )
        out.append("- pinned H&S 2.0.5 profile and data pack, asserted through the existing " +
            "`RuntimeRomTrust` mechanism at the real exact-trusted ceiling (no trust was weakened);\n")
        out.append(
            "- battle topology taken from each trainer's own pinned `Double Battle` setting: " +
                "`No` -> Singles / `gBattlersCount = 2`, `Yes` -> Doubles / " +
                "`gBattlersCount = 4`. The topology is stated truthfully rather than chosen to " +
                "please the policy;\n"
        )
        out.append("- challenge settings observed: `optionStyle = PER_MOVE_SPLIT`, Random Types OFF, " +
            "Random Type Effectiveness OFF, Random Abilities OFF, Random Moves OFF, no base-stat " +
            "equalizer, no level/IV/EV scaling;\n")
        out.append("- no field effect (`gFieldStatuses = 0`), clear weather, no defender screens, " +
            "every volatile bit observed false, all stat stages zero, no gimmick;\n")
        out.append("- both battlers at full HP with `status1 = 0`, so a pinch ability is provably " +
            "inactive rather than accidentally active;\n")
        out.append("- each participant's own item and effective ability from the pinned trainer " +
            "data, supplied as authoritative observations.\n\n")
        out.append(
            "A baseline the real boundary refuses would make every number below meaningless, so " +
                "the census asserts a positive control on every run: " +
                "`${run.baselinePositiveControl.attackerSpecies}` using " +
                "`${run.baselinePositiveControl.move}` against " +
                "`${run.baselinePositiveControl.defenderSpecies}` must land on " +
                "`${run.baselinePositiveControl.displayTier}` with no blocker" +
                if (run.baselinePositiveControl.limitations.isEmpty()) ".\n\n"
                else " (got ${run.baselinePositiveControl.limitations}).\n\n"
        )

        out.append("## Reference player fixture(s)\n\n")
        out.append(
            "These teams are measurement fixtures. They exist so the census has a fixed attacker " +
                "and defender whose properties are stable and explainable; they are deliberately " +
                "not gameplay recommendations. Each lead's ability is a category-neutral pinch " +
                "ability that is provably inactive at full HP, so an ability blocker reported " +
                "below belongs to the trainer Pokemon being measured, not to the fixture. Every " +
                "move is a pinned ordinary fixed-base-power move, so move-mechanics blockers " +
                "belong to the trainer data too.\n\n"
        )
        for (leadTeam in run.referenceLeads) {
            out.append("- **${leadTeam.teamId}** - ${leadTeam.species} Lv${leadTeam.level}, " +
                "ability ${leadTeam.ability}, item ${leadTeam.item ?: "none"}, " +
                "moves ${leadTeam.moves.joinToString(", ")}. ${leadTeam.notes.joinToString(" ")}\n")
        }
        out.append("\n")

        out.append("## Result tiers\n\n")
        out.append(
            "The census reports what the Battle tab would display, which is deliberately NOT the " +
                "existing `CalcSupport` enum (whose H&S ceiling is `ESTIMATED` for every request):\n\n"
        )
        out.append("- `FULLY_MODELLED` - a number is displayed and no mechanic that could change it is ignored.\n")
        out.append("- `CAVEATED_ESTIMATE` - a trustworthy base range is displayed after production " +
            "neutralizes named, identity-known mechanics that it does not model. The ignored " +
            "mechanics come from the production verdict, not a census heuristic.\n")
        out.append("- `REFUSED` - no number is displayed.\n\n")
        out.append("| Tier | Requests |\n|---|---:|\n")
        out.append("| `FULLY_MODELLED` | ${tiers.getValue(HnsCensusResultTier.FULLY_MODELLED)} |\n")
        out.append("| `CAVEATED_ESTIMATE` | ${tiers.getValue(HnsCensusResultTier.CAVEATED_ESTIMATE)} |\n")
        out.append("| `REFUSED` | ${tiers.getValue(HnsCensusResultTier.REFUSED)} |\n\n")

        out.append("## Denominators\n\n")
        out.append("| | |\n|---|---:|\n")
        out.append("| Trainer battles in the pinned source | ${run.trainers.size} |\n")
        out.append("| - singles | ${run.trainers.count { it.isSingles }} |\n")
        out.append("| - doubles | ${run.trainers.count { !it.isSingles }} |\n")
        out.append("| Trainer Pokemon | ${run.trainers.sumOf { it.party.size }} |\n")
        out.append("| Eligible damaging requests | ${run.requests.size} |\n")
        out.append("| - reference lead -> trainer | ${HnsCalcCensusMetrics.requestsByDirection(run).first} |\n")
        out.append("| - trainer -> reference lead | ${HnsCalcCensusMetrics.requestsByDirection(run).second} |\n")
        out.append("| Moves excluded, not damaging | ${run.excludedMoves.count { it.reason == "NON_DAMAGING_MOVE" }} |\n")
        out.append(
            "| Of the eligible requests, refused by `HNS_MOVE_MECHANICS_NOT_MODELLED` | " +
                "${run.requests.count { it.outcome.limitations.contains(com.dualdex.calculator.CalcLimitation.HNS_MOVE_MECHANICS_NOT_MODELLED) }} |\n\n"
        )
        out.append(
            "**Eligibility rule.** A pinned move is eligible when its own record declares a base " +
                "power greater than zero: it deals damage, so the production policy has a real " +
                "verdict for it. A move whose damage shape is outside the source-proven ordinary " +
                "subset is therefore **evaluated and refused** with " +
                "`HNS_MOVE_MECHANICS_NOT_MODELLED`, and it counts in the denominator and in the " +
                "blocker ranking. Only a move with no base power at all is excluded, because there " +
                "is no damage number to display or refuse for it; those are recorded in " +
                "`excludedMoves` with their reason and excluded from every damage metric above.\n\n"
        )

        out.append("## Lead-matchup battle coverage\n\n")
        out.append(
            "**Definition.** For every trainer battle, the *trainer lead* is the trainer's pinned " +
                "party slot 0 (the pinned source uses no party pools and no party-index " +
                "shuffling; the inventory extractor fails closed if that ever changes). The " +
                "*matchup* is that lead paired with each reference team lead, in both directions, " +
                "over the eligible damaging moves of that pair. A pair *displays* only when **every** " +
                "eligible request in it displays a number.\n\n"
        )
        val percent = if (lead.pairsTotal == 0) "n/a" else
            "%.1f%%".format(100.0 * lead.pairsDisplayable / lead.pairsTotal)
        out.append(
            "> **$percent of trainer-battle lead matchups display every eligible damaging move** " +
                "(${lead.pairsDisplayable} of ${lead.pairsTotal} reference-pair evaluations over " +
                "${lead.battlesIncluded} of ${run.trainers.size} trainer battles).\n\n"
        )
        out.append("| | |\n|---|---:|\n")
        out.append("| Trainer battles | ${run.trainers.size} |\n")
        out.append("| Battles included in the lead metric | ${lead.battlesIncluded} |\n")
        out.append("| Battles excluded: lead has no eligible damaging move | ${lead.battlesExcludedNoEligibleMove} |\n")
        out.append("| Lead pairs evaluated (battle x reference team) | ${lead.pairsTotal} |\n")
        out.append("| Lead pairs whose every eligible request displays | ${lead.pairsDisplayable} |\n")
        out.append("| Eligible requests in the lead metric | ${lead.requestsTotal} |\n")
        out.append("| Of those, displaying | ${lead.requestsDisplayable} |\n\n")
        out.append(
            "Split by the trainer's own battle format, because the production subset models " +
                "Singles only and a Doubles battle is refused by the live-battle-format gate:\n\n"
        )
        out.append("| Format | Pairs evaluated | Pairs displaying | Coverage |\n|---|---:|---:|---:|\n")
        out.append(
            "| Singles | ${lead.singlesPairsTotal} | ${lead.singlesPairsDisplayable} | " +
                "%.1f%% |\n".format(lead.singlesCoveragePercent)
        )
        out.append(
            "| Doubles | ${lead.doublesPairsTotal} | ${lead.doublesPairsDisplayable} | " +
                "%.1f%% |\n\n".format(lead.doublesCoveragePercent)
        )
        out.append(
            "A battle whose lead has no eligible damaging move is excluded rather than counted as " +
                "covered or as blocked, because there is no damage number in question for it. Its " +
                "party members are still counted in the trainer-level inventory and in the " +
                "blocker counts.\n\n"
        )

        out.append("## Ranked blockers\n\n")
        out.append(
            "Ranked by the number of distinct trainer battles affected, then by requests. A battle " +
                "is counted once per blocker however many of its requests hit that blocker. " +
                "`Mechanic` is the actual pinned identity production reported, not only a generic " +
                "limitation code.\n\n"
        )
        out.append("| # | Blocker | Limitation | Side | Battles | Requests |\n|---:|---|---|---|---:|---:|\n")
        blockers.take(25).forEachIndexed { index, rank ->
            out.append(
                "| ${index + 1} | ${rank.identity ?: rank.kind} | `${rank.kind}` | " +
                    "${rank.side ?: "-"} | ${rank.battles} | ${rank.requests} |\n"
            )
        }
        out.append("\n")

        out.append("## Ignored mechanics in caveated estimates\n\n")
        out.append(
            "These named abilities and items are neutralized by production policy before the " +
                "authorized request reaches the engine. Counts use the same distinct-battle and " +
                "request rules as the hard-blocker table.\n\n"
        )
        out.append("| Mechanic | Side | Battles | Requests |\n|---|---|---:|---:|\n")
        ignoredMechanics.take(25).forEach { rank ->
            out.append("| ${rank.identity ?: rank.kind} | ${rank.side ?: "-"} | ${rank.battles} | ${rank.requests} |\n")
        }
        if (ignoredMechanics.isEmpty()) out.append("| _none_ | - | 0 | 0 |\n")
        out.append("\n")

        out.append("### Blocker codes\n\n")
        out.append("| Limitation | Battles | Requests |\n|---|---:|---:|\n")
        limitations.take(25).forEach { rank ->
            out.append("| `${rank.kind}` | ${rank.battles} | ${rank.requests} |\n")
        }
        out.append("\n")

        out.append("### Held-item blockers\n\n")
        out.append("| Item | Side | Battles | Requests |\n|---|---|---:|---:|\n")
        items.take(20).forEach { rank ->
            out.append("| ${rank.item} | ${rank.side} | ${rank.battles} | ${rank.requests} |\n")
        }
        if (items.isEmpty()) out.append("| _none_ | - | 0 | 0 |\n")
        out.append("\n")

        out.append("## Random Abilities view\n\n")
        out.append(
            "Under Random Abilities any of the pinned domain's " +
                "${run.abilityDomain.size} abilities can be installed on any battler, so the useful " +
                "question is not which abilities trainers happen to have but which abilities " +
                "production policy blocks, on which side, for which move categories, and how many " +
                "of this census' requests that would affect.\n\n"
        )
        out.append(
            "**Method.** Every eligible request is assigned to an *operand cohort*: the requests " +
                "sharing exactly the operands an ability-relevance decision reads (direction, " +
                "attacker and defender species, the move, the effective move type and category, " +
                "STAB, format). Each ability is then installed on each side of each cohort, " +
                "replacing only that side's ability, and decided by the real production policy. " +
                "Cohorts are disjoint, so summing their weights counts each request exactly once; " +
                "battles are de-duplicated because one battle can span several cohorts. Under a " +
                "uniform ability distribution the ranking is therefore exactly a count, with no " +
                "probability weight invented. " +
                "There are ${run.abilityCohorts.size} cohorts and ${run.abilityTrials.size} ranked " +
                "ability/side/category rows.\n\n"
        )
        out.append(
            "**Attribution rule.** A trial is credited to the ability under test only when " +
                "production's own verdict both carries `HNS_ABILITY_EFFECT_NOT_MODELLED` **and** " +
                "produced an ability decision for that side whose relevance is not " +
                "`PROVEN_IRRELEVANT`. A globally harmless ability produces no decision at all, so " +
                "it can never be blamed for a block another mechanic caused, and a " +
                "`PROVEN_IRRELEVANT` decision is a proven clearance.\n\n"
        )
        out.append(
            "**The opposite battler is held harmless.** For a trial on one side, a cohort is " +
                "skipped when the opposite battler's own real ability is one the shipped catalogue " +
                "cannot prove harmless, because a block would then be attributable to both " +
                "abilities at once. This is a deliberate, disclosed narrowing: " +
                "${run.abilityTrialExcludedAmbiguous.first} requests are excluded from the " +
                "attacker-side trials and ${run.abilityTrialExcludedAmbiguous.second} from the " +
                "defender-side trials. Counting them under both abilities would inflate every " +
                "ranking, so they are reported rather than guessed.\n\n"
        )
        out.append(
            "**Reading the two columns.** *Requests blocked* is the number of this census' eligible " +
                "requests in which production policy would blame the ability under test if it were " +
                "installed on that side in that move category. It is the exact under-uniform-" +
                "distribution count for the ability itself; it deliberately does not also count the " +
                "requests where only the cohort's real opposing ability blocks (that is the " +
                "difference between asking \"is this ability the blocker\" and \"does anything " +
                "block at all\"). *Rules* lists the distinct reviewed context rules that fired, so a " +
                "contextual ability stays visible instead of being flattened into a global " +
                "supported/unsupported verdict.\n\n"
        )
        out.append("### Abilities the shipped catalogue cannot prove harmless\n\n")
        out.append(
            "**${blockingAbilityIdentities(run)} of ${run.abilityDomain.size} abilities block at " +
                "least one census request.** The other " +
                "${run.abilityDomain.size - blockingAbilityIdentities(run)} are classified " +
                "`PROVEN_NO_DAMAGE_EFFECT` (or modelled) by the shipped audit, so they produce no " +
                "ability decision at all and can never be the blocker - which is exactly why the " +
                "attribution rule above matters. Every blocking ability blocks all " +
                "${run.trainers.size} battles in its category on either side: an ability with no " +
                "reviewed clearance rule is refused in every context, and no probability weight is " +
                "invented.\n\n"
        )
        out.append(
            "| # | Ability | Side | Category | Battles blocked | Requests blocked | Rule |\n"
        )
        out.append("|---:|---|---|---|---:|---:|---|\n")
        abilities.filter { it.requests > 0 }.take(15).forEachIndexed { index, rank ->
            out.append(
                "| ${index + 1} | ${rank.abilityName} | ${rank.side} | ${rank.category} | " +
                    "${rank.battles} | ${rank.requests} | ${rank.rule ?: "-"} |\n"
            )
        }
        out.append(
            "\n(The full ranked table, one row per ability per side per category, is " +
                "`abilityBlockers` in `" + HnsCalcCensusReport.JSON_FILE_NAME + "`.)\n\n"
        )

        out.append("### Abilities with a reviewed context rule\n\n")
        out.append(
            "These are the abilities the shipped `HnsAbilityContextPolicy` can actually clear for a " +
                "specific request, at least once under this baseline. `Cleared contexts` counts " +
                "the (side, category) contexts policy proved irrelevant for; `Blocking contexts` " +
                "counts the ones where it still refuses. This is the whole list - " +
                "${cleared.size} abilities - and it is the only place the domain is not refused " +
                "wholesale, which is the argument for #86.\n\n"
        )
        out.append("| Ability | Cleared contexts | Blocking contexts | Reviewed rules that fired |\n")
        out.append("|---|---:|---:|---|\n")
        cleared.forEach { rank ->
            out.append(
                "| ${rank.abilityName} | ${rank.clearedContexts} | ${rank.blockingContexts} | " +
                    "${rank.rule ?: "-"} |\n"
            )
        }
        if (cleared.isEmpty()) out.append("| _none_ | 0 | 0 | - |\n")
        out.append("\n")

        out.append("### Side and category breakdown\n\n")
        out.append(
            "The same ability can be harmless on one side and blocking on the other, which is a " +
                "property of the reviewed contextual rules rather than of the census. " +
                "*Requests blocked* is the largest number any single ability blocks in that " +
                "side/category, not a sum over abilities.\n\n"
        )
        out.append("| Side | Category | Blocking abilities | Requests blocked (max) |\n|---|---|---:|---:|\n")
        abilities.groupBy { it.side to it.category }
            .toSortedMap(compareBy({ it.first }, { it.second }))
            .forEach { (key, ranks) ->
                out.append(
                    "| ${key.first} | ${key.second} | ${ranks.count { it.requests > 0 }} | " +
                        "${ranks.maxOfOrNull { it.requests } ?: 0} |\n"
                )
            }
        out.append("\n")

        out.append(
            "Across all " + run.abilityTrials.size + " ranked rows the strongest three-valued " +
                "ability result was `PROVEN_IRRELEVANT` in $provenIrrelevantRows rows, `RELEVANT` " +
                "in $relevantRows rows and `UNKNOWN` in $unknownRows rows. A `RELEVANT` or " +
                "`UNKNOWN` result blocks the request; only `PROVEN_IRRELEVANT` clears it. The " +
                "per-ability-per-side-per-category detail is in `" +
                HnsCalcCensusReport.JSON_FILE_NAME + "` under `abilityTrials`; the per-cohort " +
                "detail used to derive it is printed by `-Pdualdex.census.full=true`.\n\n"
        )

        out.append("## Provenance and reproduction\n\n")
        out.append(
            "The census is deterministic and offline: no ROM, no emulator, no network at report " +
                "time, no randomness, and no generation timestamp or absolute path in either " +
                "artifact. Regenerate both artifacts with:\n\n"
        )
        out.append("```bash\n")
        out.append("# 1. Re-derive the trainer inventory from the pinned checkout (needs the pinned\n")
        out.append("#    arm-none-eabi-cpp the repository already requires for source-check).\n")
        out.append("python3 tools/hns-calc-census/generate_hns_trainer_census.py \\\n")
        out.append("    --upstream-dir \"\$HNS_UPSTREAM_DIR\"\n\n")
        out.append("# 2. Run the production policy over it and rewrite the census artifacts.\n")
        out.append("./gradlew testDebugUnitTest -Pdualdex.census.generate=true\n")
        out.append("```\n\n")
        out.append(
            "Add `-Pdualdex.census.full=true` to step 2 to also write the uncommitted, " +
                "per-request detail dump `tools/hns-calc-census/census-detail.json`: one row per " +
                "evaluated request with the production policy's own ability, item and field " +
                "decisions. It is derivative, ~20 MB, and deliberately not committed; `--check` " +
                "never compares it.\n\n"
        )
        out.append(
            "The machine-readable companion of this report is `$jsonFileName`, gzip-compressed " +
                "with its gzip header pinned so the bytes are machine-independent. Both artifacts " +
                "are compared byte-for-byte by `HnsCalcCensusArtifactTest`, which `./ci.sh test` " +
                "and `./ci.sh source-check` both run, so a stale or hand-edited artifact fails the " +
                "gate.\n\n"
        )
        out.append("### Limitations of this measurement\n\n")
        out.append(
            "- The baseline is a fixed neutral state, not a simulation of turn-by-turn play: it " +
                "measures whether a mechanic blocks the *displayed* number for a matchup, not how " +
                "a battle develops.\n"
        )
        out.append(
            "- The Random Abilities counts are weighted by the census' own operand cohorts, which " +
                "are exactly the operands a relevance decision reads; they are counts over this " +
                "census' requests, not over every possible battle in the game.\n"
        )
        out.append(
            "- Doubles trainers are measured as Doubles because that is what they are. The " +
                "production subset models Singles only, so those battles are refused by the " +
                "live-battle-format gate; that is a real production limitation being measured, not " +
                "a census artefact.\n"
        )
        return out.toString()
    }
}
