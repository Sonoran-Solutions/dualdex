package com.dualdex.coverage

import org.json.JSONArray
import org.json.JSONObject

/** One ordered store, owned by the debug worker. Persisted data contains only the allowlisted schema. */
internal interface CoverageStore {
    fun read(): String?
    fun write(json: String)
}

internal data class CoverageRow(
    val session: Long, var matchup: CoverageMatchup, val firstSeen: Long,
    var lastSeen: Long, var seenCount: Long = 0,
    val tiers: MutableMap<CoverageTier, Long> = linkedMapOf(),
    val contexts: MutableSet<CoverageContext> = linkedSetOf(),
    val mechanics: MutableMap<String, Pair<CoverageMechanic, Long>> = linkedMapOf()
)

internal class CoverageRepository(
    private val store: CoverageStore, private val clock: () -> Long = System::currentTimeMillis,
    val cap: Int = RECORD_CAP
) {
    companion object {
        // Typical rows are a few KB: 2,000 rows keeps ordinary playtest logs to a few MB.
        const val RECORD_CAP = 2000
        const val MECHANIC_CAP = 512
    }
    private val rows = mutableListOf<CoverageRow>()
    private var counter = 0L
    private var session: Long? = null
    private var active = false
    private var evicted = 0L
    private var droppedMechanics = 0L
    var storageFailures = 0L; private set

    init {
        require(cap > 0 && cap <= RECORD_CAP)
        try { store.read()?.let(::load) } catch (_: Exception) {
            rows.clear(); counter = 0; evicted = 0; droppedMechanics = 0; storageFailures++
        }
    }

    @Synchronized fun battle(nowActive: Boolean) {
        if (nowActive && !active) session = ++counter
        if (!nowActive) session = null
        active = nowActive
    }

    @Synchronized fun record(matchup: CoverageMatchup, context: CoverageContext, observation: CoverageObservation, persistNow: Boolean = true) {
        val currentSession = session ?: return
        val now = clock()
        // Selected party slots remain stable when an unread live identity becomes readable or
        // a battler changes form. Engine indices distinguish participants without a party slot.
        fun participantKey(p: CoverageParticipant): String = when {
            p.partySlot != null -> "party:${p.partySlot}"
            p.engineIndex != null -> "engine:${p.engineIndex}"
            else -> "species:${p.speciesId}"
        }
        fun key(m: CoverageMatchup) = listOf(participantKey(m.attacker), participantKey(m.defender),
            m.moveId, if (m.moveId == null) m.moveName else null, m.direction)
        val row = rows.find { it.session == currentSession && key(it.matchup) == key(matchup) }
            ?: CoverageRow(currentSession, matchup, now, now).also {
                if (rows.size == cap) { rows.removeAt(0); evicted++ }
                rows.add(it)
            }
        // Keep the latest known display identity; losing a read must not erase a known identity.
        fun latest(old: CoverageParticipant, new: CoverageParticipant) = new.copy(
            speciesId = new.speciesId ?: old.speciesId,
            speciesName = new.speciesName.ifEmpty { old.speciesName },
            engineIndex = new.engineIndex ?: old.engineIndex)
        row.matchup = matchup.copy(attacker = latest(row.matchup.attacker, matchup.attacker),
            defender = latest(row.matchup.defender, matchup.defender))
        row.lastSeen = now; row.seenCount++
        row.tiers[observation.tier] = (row.tiers[observation.tier] ?: 0) + 1
        row.contexts.add(context)
        observation.mechanics.distinctBy { it.identity }.forEach { mechanic ->
            val old = row.mechanics[mechanic.identity]
            if (old != null || row.mechanics.size < MECHANIC_CAP) {
                row.mechanics[mechanic.identity] = mechanic to ((old?.second ?: 0) + 1)
            } else droppedMechanics++
        }
        if (persistNow) persist()
    }

    @Synchronized fun flush() = persist()

    @Synchronized fun clear() {
        rows.clear(); evicted = 0; droppedMechanics = 0; storageFailures = 0
        // Retain the monotonic session counter and current lifecycle across a clear.
        persist()
    }

    private fun persist() {
        try { store.write(stableJson(document(false, ""))) } catch (_: Exception) { storageFailures++ }
    }

    @Synchronized fun export(appVersion: String): String = stableJson(document(true, appVersion))

    private fun document(export: Boolean, version: String): JSONObject {
        val result = JSONObject().put("schemaVersion", 1).put("profile", "hns_2_0_5")
            .put("recordCap", cap).put("mechanicCapPerRow", MECHANIC_CAP)
            .put("evictedRecords", evicted).put("droppedMechanicObservations", droppedMechanics)
            .put("sessionCounter", counter).put("storageFailures", storageFailures)
            .put("records", JSONArray(rows.map(::rowJson)))
        if (export) {
            result.put("generatedAt", clock()).put("appVersion", version).put("buildType", "debug")
            result.put("summary", JSONObject().put("sessions", rows.map { it.session }.distinct().size)
                .put("logicalRows", rows.size).put("exactRows", rows.count { CoverageTier.EXACT in it.tiers })
                .put("caveatedRows", rows.count { CoverageTier.CAVEATED_ESTIMATE in it.tiers })
                .put("refusedRows", rows.count { CoverageTier.REFUSED in it.tiers }))
            val groups = rows.flatMap { row -> row.mechanics.values.filter {
                it.first.disposition == "BLOCKING" || it.first.disposition == "CAVEATED"
            }.map { Triple(row.session, it.first, it.second) } }.groupBy { it.second.identity }
            val summaries = groups.map { (identity, occurrences) ->
                mechanicJson(occurrences.first().second).put("identity", identity)
                    .put("logicalRows", occurrences.size).put("observations", occurrences.sumOf { it.third })
                    .put("sessions", occurrences.map { it.first }.distinct().size)
            }.sortedWith(compareByDescending<JSONObject> { it.getInt("logicalRows") }.thenBy { it.getString("identity") })
            result.put("blockers", JSONArray(summaries))
        }
        return result
    }

    private fun participantJson(p: CoverageParticipant) = JSONObject().put("speciesId", p.speciesId ?: JSONObject.NULL)
        .put("speciesName", p.speciesName).put("partySlot", p.partySlot ?: JSONObject.NULL)
        .put("engineIndex", p.engineIndex ?: JSONObject.NULL)
    private fun mechanicJson(m: CoverageMechanic) = JSONObject().put("kind", m.kind).put("side", m.side ?: JSONObject.NULL)
        .put("id", m.id ?: JSONObject.NULL).put("name", m.name).put("relevance", m.relevance ?: JSONObject.NULL)
        .put("rule", m.rule ?: JSONObject.NULL).put("source", m.source ?: JSONObject.NULL)
        .put("disposition", m.disposition).put("family", m.family ?: JSONObject.NULL)
    private fun rowJson(r: CoverageRow) = JSONObject().put("battleSession", r.session)
        .put("attacker", participantJson(r.matchup.attacker)).put("defender", participantJson(r.matchup.defender))
        .put("moveId", r.matchup.moveId ?: JSONObject.NULL).put("moveName", r.matchup.moveName)
        .put("direction", r.matchup.direction).put("firstSeen", r.firstSeen).put("lastSeen", r.lastSeen)
        .put("seenCount", r.seenCount).put("outcomes", JSONObject().apply {
            CoverageTier.entries.forEach { put(it.name, r.tiers[it] ?: 0) }
        }).put("contexts", JSONArray(r.contexts.sortedBy { "${it.format}/${it.battleKind}/${it.randomAbilities}" }.map {
            JSONObject().put("format", it.format.name).put("battleKind", it.battleKind.name).put("randomAbilities", it.randomAbilities.name)
        })).put("mechanics", JSONArray(r.mechanics.toSortedMap().values.map { (mechanic, count) ->
            mechanicJson(mechanic).put("observations", count)
        }))

    private fun load(json: String) {
        val root = JSONObject(json)
        require(root.getInt("schemaVersion") == 1 && root.getString("profile") == "hns_2_0_5")
        counter = root.getLong("sessionCounter").also { require(it >= 0) }
        evicted = root.getLong("evictedRecords").also { require(it >= 0) }
        droppedMechanics = root.getLong("droppedMechanicObservations").also { require(it >= 0) }
        storageFailures = root.getLong("storageFailures").also { require(it >= 0) }
        val stored = root.getJSONArray("records")
        require(stored.length() <= RECORD_CAP)
        fun participant(o: JSONObject) = CoverageParticipant(o.intOrNull("speciesId"), o.getString("speciesName"),
            o.intOrNull("partySlot"), o.intOrNull("engineIndex"))
        for (i in 0 until stored.length()) {
            val o = stored.getJSONObject(i)
            val row = CoverageRow(o.getLong("battleSession"), CoverageMatchup(participant(o.getJSONObject("attacker")),
                participant(o.getJSONObject("defender")), o.intOrNull("moveId"), o.getString("moveName"), o.getString("direction")),
                o.getLong("firstSeen"), o.getLong("lastSeen"), o.getLong("seenCount"))
            require(row.session in 1..counter && row.seenCount > 0)
            val tiers = o.getJSONObject("outcomes")
            CoverageTier.entries.forEach { t -> tiers.getLong(t.name).also { require(it >= 0); if (it > 0) row.tiers[t] = it } }
            require(row.tiers.values.sum() == row.seenCount)
            val contexts = o.getJSONArray("contexts"); require(contexts.length() <= 27)
            for (j in 0 until contexts.length()) {
                val c = contexts.getJSONObject(j)
                row.contexts.add(CoverageContext(CoverageFormat.valueOf(c.getString("format")),
                    CoverageBattleKind.valueOf(c.getString("battleKind")), CoverageToggle.valueOf(c.getString("randomAbilities"))))
            }
            val mechanics = o.getJSONArray("mechanics"); require(mechanics.length() <= MECHANIC_CAP)
            for (j in 0 until mechanics.length()) {
                val m = mechanics.getJSONObject(j)
                val mechanic = CoverageMechanic(m.getString("kind"), m.stringOrNull("side"), m.intOrNull("id"),
                    m.getString("name"), m.stringOrNull("relevance"), m.stringOrNull("rule"), m.stringOrNull("source"),
                    m.getString("disposition"), m.stringOrNull("family")).correctModelledDisposition()
                val count = m.getLong("observations").also { require(it in 1..row.seenCount) }
                val mergedCount = count + (row.mechanics[mechanic.identity]?.second ?: 0)
                require(mergedCount <= row.seenCount)
                row.mechanics[mechanic.identity] = mechanic to mergedCount
            }
            rows.add(row)
        }
        while (rows.size > cap) { rows.removeAt(0); evicted++ }
    }
}

private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else getInt(key)
private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)

/** Object keys sorted recursively; arrays have explicit chronological/identity order. */
internal fun stableJson(value: Any?): String = when (value) {
    null, JSONObject.NULL -> "null"
    is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") {
        JSONObject.quote(it) + ":" + stableJson(value.get(it))
    }
    is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { stableJson(value.get(it)) }
    is String -> JSONObject.quote(value)
    else -> value.toString()
}
